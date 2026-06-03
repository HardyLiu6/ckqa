package org.ysu.ckqaback.qa.context;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.ConnectionReadTimeoutException;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.graph.GraphCypher;
import org.ysu.ckqaback.integration.config.Neo4jProperties;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 将本轮解析出的语义主题弱绑定到 Neo4j 中的 GraphRAG 实体候选。
 * <p>
 * 该服务只生成运维诊断信息，不影响 QA 主流程，也不会把实体候选传给 Python GraphRAG。
 * </p>
 */
@Slf4j
@Service
public class QaTopicEntityBindingService {

    private static final int DEFAULT_LIMIT = 5;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 5000L;
    private static final long MAX_QA_BINDING_BUDGET_MILLIS = 500L;
    private static final int LOOKUP_EXECUTOR_MAX_THREADS = 2;
    private static final long LOOKUP_EXECUTOR_KEEP_ALIVE_SECONDS = 30L;
    private static final AtomicInteger LOOKUP_THREAD_SEQUENCE = new AtomicInteger(0);

    private final Driver driver;
    private final Neo4jProperties properties;
    private final LongSupplier nanoTimeSupplier;
    private final ExecutorService lookupExecutor;
    private final boolean ownsLookupExecutor;

    @Autowired
    public QaTopicEntityBindingService(@Nullable Driver driver, Neo4jProperties properties) {
        this(driver, properties, System::nanoTime, createLookupExecutor(), true);
    }

    QaTopicEntityBindingService(@Nullable Driver driver,
                                Neo4jProperties properties,
                                LongSupplier nanoTimeSupplier) {
        this(driver, properties, nanoTimeSupplier, createLookupExecutor(), true);
    }

    QaTopicEntityBindingService(@Nullable Driver driver,
                                Neo4jProperties properties,
                                LongSupplier nanoTimeSupplier,
                                ExecutorService lookupExecutor) {
        this(driver, properties, nanoTimeSupplier, lookupExecutor, false);
    }

    private QaTopicEntityBindingService(@Nullable Driver driver,
                                        Neo4jProperties properties,
                                        LongSupplier nanoTimeSupplier,
                                        ExecutorService lookupExecutor,
                                        boolean ownsLookupExecutor) {
        this.driver = driver;
        this.properties = properties;
        this.nanoTimeSupplier = nanoTimeSupplier == null ? System::nanoTime : nanoTimeSupplier;
        this.lookupExecutor = lookupExecutor == null ? createLookupExecutor() : lookupExecutor;
        this.ownsLookupExecutor = lookupExecutor == null || ownsLookupExecutor;
    }

    /**
     * 当前绑定范围仅是 active_neo4j 诊断视角。
     * knowledgeBaseId / indexRunId 只参与日志与后续扩展，不代表已经实现 per-index KG 过滤。
     */
    public QaTopicEntityBindingResult bind(String topic, Long knowledgeBaseId, Long indexRunId) {
        long startedNanos = nowNanos();
        if (!StringUtils.hasText(topic)) {
            return QaTopicEntityBindingResult.skipped("topic_empty", elapsedMs(startedNanos));
        }
        if (properties == null || !properties.isEnabled()) {
            return QaTopicEntityBindingResult.fallback("neo4j_disabled", elapsedMs(startedNanos));
        }
        if (driver == null) {
            return QaTopicEntityBindingResult.fallback("neo4j_driver_unavailable", elapsedMs(startedNanos));
        }

        String normalizedTopic = topic.trim();
        long totalBudgetMillis = totalBudgetMillis();
        Future<QaTopicEntityBindingResult> lookupFuture;
        try {
            lookupFuture = lookupExecutor.submit(() -> lookupActiveNeo4j(
                    normalizedTopic,
                    knowledgeBaseId,
                    indexRunId,
                    startedNanos,
                    totalBudgetMillis
            ));
        } catch (RejectedExecutionException ex) {
            log.warn("QA 主题实体弱绑定执行器已饱和 knowledgeBaseId={} indexRunId={} reason={}",
                    knowledgeBaseId, indexRunId, ex.getMessage());
            return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_executor_saturated", elapsedMs(startedNanos));
        }

        try {
            return lookupFuture.get(totalBudgetMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            lookupFuture.cancel(true);
            log.warn("QA 主题实体弱绑定外层等待超时降级 knowledgeBaseId={} indexRunId={} budgetMs={}",
                    knowledgeBaseId, indexRunId, totalBudgetMillis);
            return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_timeout", elapsedMs(startedNanos));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lookupFuture.cancel(true);
            log.warn("QA 主题实体弱绑定等待被中断 knowledgeBaseId={} indexRunId={}",
                    knowledgeBaseId, indexRunId);
            return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_interrupted", elapsedMs(startedNanos));
        } catch (ExecutionException ex) {
            return handleLookupFailure(ex.getCause(), knowledgeBaseId, indexRunId, startedNanos);
        }
    }

    @PreDestroy
    public void shutdownLookupExecutor() {
        if (ownsLookupExecutor && lookupExecutor != null) {
            lookupExecutor.shutdownNow();
        }
    }

    private QaTopicEntityBindingResult lookupActiveNeo4j(String normalizedTopic,
                                                         Long knowledgeBaseId,
                                                         Long indexRunId,
                                                         long startedNanos,
                                                         long totalBudgetMillis) {
        long lookupStartedNanos = nowNanos();
        try (Session session = openSession()) {
            List<QaTopicEntityBindingCandidate> candidates = fetchCandidates(
                    session,
                    GraphCypher.TOPIC_ENTITY_EXACT_CANDIDATES,
                    normalizedTopic,
                    remainingBudgetMillis(lookupStartedNanos, totalBudgetMillis)
            );
            if (!candidates.isEmpty()) {
                if (isAmbiguous(candidates)) {
                    return QaTopicEntityBindingResult.ambiguous(candidates, elapsedMs(startedNanos));
                }
                return QaTopicEntityBindingResult.success(candidates, elapsedMs(startedNanos));
            }

            long containsBudgetMillis = remainingBudgetMillis(lookupStartedNanos, totalBudgetMillis);
            if (containsBudgetMillis <= 0L) {
                return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_budget_exhausted", elapsedMs(startedNanos));
            }

            candidates = fetchCandidates(
                    session,
                    GraphCypher.TOPIC_ENTITY_CONTAINS_CANDIDATES,
                    normalizedTopic,
                    containsBudgetMillis
            );
            if (candidates.isEmpty()) {
                return QaTopicEntityBindingResult.activeNeo4jFallback("no_candidates", elapsedMs(startedNanos));
            }
            if (isAmbiguous(candidates)) {
                return QaTopicEntityBindingResult.ambiguous(candidates, elapsedMs(startedNanos));
            }
            return QaTopicEntityBindingResult.success(candidates, elapsedMs(startedNanos));
        } catch (Neo4jException ex) {
            return handleLookupFailure(ex, knowledgeBaseId, indexRunId, startedNanos);
        } catch (Exception ex) {
            return handleLookupFailure(ex, knowledgeBaseId, indexRunId, startedNanos);
        }
    }

    private List<QaTopicEntityBindingCandidate> fetchCandidates(
            Session session,
            String cypher,
            String topic,
            long timeoutMillis
    ) {
        if (timeoutMillis <= 0L) {
            return List.of();
        }
        Query query = new Query(cypher, Values.parameters("topic", topic, "limit", DEFAULT_LIMIT));
        return session.run(query, buildTransactionConfig(timeoutMillis))
                .list()
                .stream()
                .map(this::toCandidate)
                .filter(candidate -> StringUtils.hasText(candidate.id()) || StringUtils.hasText(candidate.name()))
                .sorted(Comparator
                        .comparing(QaTopicEntityBindingCandidate::score, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(QaTopicEntityBindingCandidate::name, Comparator.nullsLast(String::compareTo))
                        .thenComparing(QaTopicEntityBindingCandidate::id, Comparator.nullsLast(String::compareTo)))
                .limit(DEFAULT_LIMIT)
                .toList();
    }

    private Session openSession() {
        SessionConfig config = SessionConfig.builder()
                .withDatabase(properties.getDatabase())
                .build();
        return driver.session(config);
    }

    private QaTopicEntityBindingResult handleLookupFailure(Throwable throwable,
                                                           Long knowledgeBaseId,
                                                           Long indexRunId,
                                                           long startedNanos) {
        if (isInterruptedFailure(throwable)) {
            log.debug("QA 主题实体弱绑定后台查询被中断 knowledgeBaseId={} indexRunId={}",
                    knowledgeBaseId, indexRunId);
            return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_interrupted", elapsedMs(startedNanos));
        }
        if (throwable instanceof Neo4jException neo4jException) {
            if (isTimeoutException(neo4jException)) {
                log.warn("QA 主题实体弱绑定超时降级 knowledgeBaseId={} indexRunId={} type={} reason={}",
                        knowledgeBaseId, indexRunId, neo4jException.getClass().getSimpleName(), neo4jException.getMessage());
                return QaTopicEntityBindingResult.activeNeo4jFallback("lookup_timeout", elapsedMs(startedNanos));
            }
            log.warn("QA 主题实体弱绑定查询失败 knowledgeBaseId={} indexRunId={} type={} reason={}",
                    knowledgeBaseId, indexRunId, neo4jException.getClass().getSimpleName(), neo4jException.getMessage());
            return QaTopicEntityBindingResult.failed(shorten("query_failed:" + neo4jException.getMessage()), elapsedMs(startedNanos));
        }
        String reason = throwable == null ? null : throwable.getMessage();
        log.warn("QA 主题实体弱绑定查询失败 knowledgeBaseId={} indexRunId={} reason={}",
                knowledgeBaseId, indexRunId, reason);
        return QaTopicEntityBindingResult.failed(shorten("query_failed:" + reason), elapsedMs(startedNanos));
    }

    private boolean isInterruptedFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private TransactionConfig buildTransactionConfig(long timeoutMillis) {
        return TransactionConfig.builder()
                .withTimeout(Duration.ofMillis(Math.max(1L, timeoutMillis)))
                .build();
    }

    private long totalBudgetMillis() {
        long configuredReadTimeoutMillis = properties == null
                ? DEFAULT_READ_TIMEOUT_MILLIS
                : properties.getReadTimeoutMillis();
        return Math.max(1L, Math.min(configuredReadTimeoutMillis, MAX_QA_BINDING_BUDGET_MILLIS));
    }

    private long remainingBudgetMillis(long startedNanos, long totalBudgetMillis) {
        return Math.max(0L, totalBudgetMillis - elapsedMs(startedNanos));
    }

    private QaTopicEntityBindingCandidate toCandidate(Record record) {
        return new QaTopicEntityBindingCandidate(
                readString(record.get("entityId")),
                readString(record.get("name")),
                readString(record.get("type")),
                readString(record.get("humanReadableId")),
                readDouble(record.get("score")),
                readString(record.get("matchReason")),
                readString(record.get("source"))
        );
    }

    private boolean isAmbiguous(List<QaTopicEntityBindingCandidate> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return false;
        }
        Double top = candidates.get(0).score();
        Double second = candidates.get(1).score();
        return top != null && second != null && Math.abs(top - second) < 0.0001D && top < 1.0D;
    }

    private String readString(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            String text = value.asString();
            return StringUtils.hasText(text) ? text : null;
        } catch (Exception ex) {
            Object raw = value.asObject();
            return raw == null ? null : String.valueOf(raw);
        }
    }

    private Double readDouble(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asDouble();
        } catch (Exception ex) {
            return null;
        }
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(nowNanos() - startedNanos);
    }

    private long nowNanos() {
        return nanoTimeSupplier.getAsLong();
    }

    private boolean isTimeoutException(Neo4jException exception) {
        if (exception == null) {
            return false;
        }
        if (exception instanceof ConnectionReadTimeoutException) {
            return true;
        }
        if (containsTimeoutHint(exception.getClass().getSimpleName())) {
            return true;
        }
        if (containsTimeoutHint(exception.code())) {
            return true;
        }
        return containsTimeoutHint(exception.getMessage());
    }

    private boolean containsTimeoutHint(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("timedout")
                || normalized.contains("transactiontimedout")
                || normalized.contains("deadline exceeded")
                || normalized.contains("execution time limit");
    }

    private String shorten(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static ExecutorService createLookupExecutor() {
        ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
        return new ThreadPoolExecutor(
                0,
                LOOKUP_EXECUTOR_MAX_THREADS,
                LOOKUP_EXECUTOR_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = defaultThreadFactory.newThread(runnable);
                    thread.setName("qa-topic-binding-" + LOOKUP_THREAD_SEQUENCE.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
