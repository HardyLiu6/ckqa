package org.ysu.ckqaback.qa.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.ConnectionReadTimeoutException;
import org.ysu.ckqaback.graph.GraphCypher;
import org.ysu.ckqaback.integration.config.Neo4jProperties;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QaTopicEntityBindingServiceTest {

    @Test
    void shouldSkipWhenTopicBlank() {
        QaTopicEntityBindingService service = new QaTopicEntityBindingService(null, new Neo4jProperties());

        QaTopicEntityBindingResult result = service.bind("  ", 3L, 17L);

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.strategy()).isEqualTo("none");
        assertThat(result.applied()).isFalse();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.fallbackReason()).isEqualTo("topic_empty");
    }

    @Test
    void shouldFallbackWhenDriverMissingOrNeo4jDisabled() {
        Neo4jProperties disabled = new Neo4jProperties();
        disabled.setEnabled(false);

        QaTopicEntityBindingResult missingDriver = new QaTopicEntityBindingService(null, new Neo4jProperties())
                .bind("死锁", 3L, 17L);
        QaTopicEntityBindingResult disabledNeo4j = new QaTopicEntityBindingService(mock(Driver.class), disabled)
                .bind("死锁", 3L, 17L);

        assertThat(missingDriver.status()).isEqualTo("fallback");
        assertThat(missingDriver.fallbackReason()).isEqualTo("neo4j_driver_unavailable");
        assertThat(missingDriver.applied()).isFalse();
        assertThat(disabledNeo4j.status()).isEqualTo("fallback");
        assertThat(disabledNeo4j.fallbackReason()).isEqualTo("neo4j_disabled");
        assertThat(disabledNeo4j.applied()).isFalse();
    }

    @Test
    void shouldPreferExactCandidatesAndCapQaBindingBudget() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result queryResult = mock(Result.class);
        Record record = mock(Record.class);
        Neo4jProperties properties = new Neo4jProperties();
        properties.setReadTimeoutMillis(5000L);

        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(queryResult);
        given(queryResult.list()).willReturn(List.of(record));
        given(record.get("entityId")).willReturn(Values.value("entity-deadlock"));
        given(record.get("name")).willReturn(Values.value("死锁"));
        given(record.get("type")).willReturn(Values.value("concept"));
        given(record.get("humanReadableId")).willReturn(Values.value("E-42"));
        given(record.get("score")).willReturn(Values.value(1.0D));
        given(record.get("matchReason")).willReturn(Values.value("exact_name"));
        given(record.get("source")).willReturn(Values.value("active_neo4j"));

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, properties)
                .bind("死锁", 3L, 17L);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<TransactionConfig> transactionConfigCaptor = ArgumentCaptor.forClass(TransactionConfig.class);
        verify(session).run(queryCaptor.capture(), transactionConfigCaptor.capture());

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.applied()).isTrue();
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.selectedId()).isEqualTo("entity-deadlock");
        assertThat(result.selectedName()).isEqualTo("死锁");
        assertThat(result.selectedType()).isEqualTo("concept");
        assertThat(result.topScore()).isEqualTo(1.0D);
        assertThat(result.candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.source()).isEqualTo("active_neo4j"));
        assertThat(result.candidatesJson()).contains("\"id\":\"entity-deadlock\"");
        assertThat(result.candidatesJson()).contains("\"name\":\"死锁\"");
        assertThat(result.candidatesJson()).contains("\"humanReadableId\":\"E-42\"");
        assertThat(result.candidatesJson()).contains("\"source\":\"active_neo4j\"");
        assertThat(result.candidatesJson()).doesNotContain("description", "snippet", "memoryText", "full_content");
        assertThat(queryCaptor.getValue().text()).isEqualTo(GraphCypher.TOPIC_ENTITY_EXACT_CANDIDATES);
        assertThat(transactionConfigCaptor.getValue().timeout()).isEqualTo(Duration.ofMillis(500L));
    }

    @Test
    void shouldFallbackToContainsCandidatesWhenExactMatchIsEmpty() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result exactResult = mock(Result.class);
        Result containsResult = mock(Result.class);
        Record record = mock(Record.class);

        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(exactResult, containsResult);
        given(exactResult.list()).willReturn(List.of());
        given(containsResult.list()).willReturn(List.of(record));
        given(record.get("entityId")).willReturn(Values.value("entity-rag"));
        given(record.get("name")).willReturn(Values.value("资源分配图"));
        given(record.get("type")).willReturn(Values.value("concept"));
        given(record.get("humanReadableId")).willReturn(Values.value("E-99"));
        given(record.get("score")).willReturn(Values.value(0.88D));
        given(record.get("matchReason")).willReturn(Values.value("contains_name"));
        given(record.get("source")).willReturn(Values.value("active_neo4j"));

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, new Neo4jProperties())
                .bind("资源分配", 3L, 17L);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(session, times(2)).run(queryCaptor.capture(), any(TransactionConfig.class));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.selectedId()).isEqualTo("entity-rag");
        assertThat(result.candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.source()).isEqualTo("active_neo4j"));
        assertThat(queryCaptor.getAllValues())
                .extracting(Query::text)
                .containsExactly(
                        GraphCypher.TOPIC_ENTITY_EXACT_CANDIDATES,
                        GraphCypher.TOPIC_ENTITY_CONTAINS_CANDIDATES
                );
    }

    @Test
    void shouldUseRemainingBudgetForContainsFallback() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result exactResult = mock(Result.class);
        Result containsResult = mock(Result.class);
        Record record = mock(Record.class);
        Neo4jProperties properties = new Neo4jProperties();
        properties.setReadTimeoutMillis(2000L);
        AtomicLong nowNanos = new AtomicLong(0L);

        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(exactResult, containsResult);
        given(exactResult.list()).willAnswer(invocation -> {
            nowNanos.set(Duration.ofMillis(300L).toNanos());
            return List.of();
        });
        given(containsResult.list()).willReturn(List.of(record));
        given(record.get("entityId")).willReturn(Values.value("entity-rag"));
        given(record.get("name")).willReturn(Values.value("资源分配图"));
        given(record.get("type")).willReturn(Values.value("concept"));
        given(record.get("humanReadableId")).willReturn(Values.value("E-99"));
        given(record.get("score")).willReturn(Values.value(0.88D));
        given(record.get("matchReason")).willReturn(Values.value("contains_name"));
        given(record.get("source")).willReturn(Values.value("active_neo4j"));

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, properties, nowNanos::get)
                .bind("资源分配", 3L, 17L);

        ArgumentCaptor<TransactionConfig> transactionConfigCaptor = ArgumentCaptor.forClass(TransactionConfig.class);
        verify(session, times(2)).run(any(Query.class), transactionConfigCaptor.capture());

        assertThat(result.status()).isEqualTo("success");
        assertThat(transactionConfigCaptor.getAllValues())
                .extracting(TransactionConfig::timeout)
                .containsExactly(Duration.ofMillis(500L), Duration.ofMillis(200L));
    }

    @Test
    void shouldFallbackWhenExactLookupConsumesBudgetWithoutCandidates() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result exactResult = mock(Result.class);
        Neo4jProperties properties = new Neo4jProperties();
        properties.setReadTimeoutMillis(2000L);
        AtomicLong nowNanos = new AtomicLong(0L);

        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(exactResult);
        given(exactResult.list()).willAnswer(invocation -> {
            nowNanos.set(Duration.ofMillis(700L).toNanos());
            return List.of();
        });

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, properties, nowNanos::get)
                .bind("资源分配", 3L, 17L);

        verify(session, times(1)).run(any(Query.class), any(TransactionConfig.class));
        assertThat(result.status()).isEqualTo("fallback");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.fallbackReason()).isEqualTo("lookup_budget_exhausted");
        assertThat(result.candidateCount()).isZero();
    }

    @Test
    void shouldUseActiveStrategyWhenNoCandidatesFound() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result exactResult = mock(Result.class);
        Result containsResult = mock(Result.class);

        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(exactResult, containsResult);
        given(exactResult.list()).willReturn(List.of());
        given(containsResult.list()).willReturn(List.of());

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, new Neo4jProperties())
                .bind("资源分配", 3L, 17L);

        assertThat(result.status()).isEqualTo("fallback");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.fallbackReason()).isEqualTo("no_candidates");
        assertThat(result.candidateCount()).isZero();
    }

    @Test
    void shouldTruncateSelectedAndCandidateFieldsToStorageSafeLengths() {
        String longId = "i".repeat(160);
        String longName = "名".repeat(300);
        String longType = "t".repeat(90);
        String longHumanReadableId = "h".repeat(300);
        String longMatchReason = "m".repeat(180);
        String longSource = "s".repeat(180);

        QaTopicEntityBindingCandidate rawCandidate = new QaTopicEntityBindingCandidate(
                longId,
                longName,
                longType,
                longHumanReadableId,
                0.91D,
                longMatchReason,
                longSource
        );

        QaTopicEntityBindingResult result = QaTopicEntityBindingResult.success(List.of(rawCandidate), 9L);

        assertThat(result.selectedId()).hasSize(128);
        assertThat(result.selectedName()).hasSize(255);
        assertThat(result.selectedType()).hasSize(64);
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).hasSize(128);
            assertThat(candidate.name()).hasSize(255);
            assertThat(candidate.type()).hasSize(64);
            assertThat(candidate.humanReadableId()).hasSize(255);
            assertThat(candidate.matchReason()).hasSize(128);
            assertThat(candidate.source()).hasSize(64);
        });
        assertThat(result.candidatesJson()).contains("\"id\":\"" + "i".repeat(128) + "\"");
        assertThat(result.candidatesJson()).doesNotContain(longId, longHumanReadableId, longMatchReason, longSource);
    }

    @Test
    void shouldReturnFallbackWhenNeo4jLookupTimesOut() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class)))
                .willThrow(ConnectionReadTimeoutException.INSTANCE);

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, new Neo4jProperties())
                .bind("死锁", 3L, 17L);

        assertThat(result.status()).isEqualTo("fallback");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.applied()).isFalse();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.fallbackReason()).isEqualTo("lookup_timeout");
    }

    @Test
    void shouldReturnFallbackWhenOuterLookupBudgetTimesOut() throws Exception {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result exactResult = mock(Result.class);
        Neo4jProperties properties = new Neo4jProperties();
        properties.setReadTimeoutMillis(50L);
        ExecutorService lookupExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "qa-topic-binding-test-timeout");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean cancelObserved = new AtomicBoolean(false);

        try {
            given(driver.session(any(SessionConfig.class))).willReturn(session);
            given(session.run(any(Query.class), any(TransactionConfig.class))).willReturn(exactResult);
            given(exactResult.list()).willAnswer(invocation -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    cancelObserved.set(true);
                    interrupted.countDown();
                    throw new RuntimeException(ex);
                }
                return List.of();
            });

            QaTopicEntityBindingResult result = new QaTopicEntityBindingService(
                    driver,
                    properties,
                    System::nanoTime,
                    lookupExecutor
            ).bind("死锁", 3L, 17L);

            assertThat(result.status()).isEqualTo("fallback");
            assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
            assertThat(result.fallbackReason()).isEqualTo("lookup_timeout");
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cancelObserved.get()).isTrue();
        } finally {
            lookupExecutor.shutdownNow();
        }
    }

    @Test
    void shouldFallbackWhenLookupExecutorIsSaturated() {
        Driver driver = mock(Driver.class);
        Neo4jProperties properties = new Neo4jProperties();
        ExecutorService lookupExecutor = mock(ExecutorService.class);
        given(lookupExecutor.submit(org.mockito.ArgumentMatchers.<Callable<QaTopicEntityBindingResult>>any()))
                .willThrow(new RejectedExecutionException("executor saturated"));

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(
                driver,
                properties,
                System::nanoTime,
                lookupExecutor
        ).bind("死锁", 3L, 17L);

        assertThat(result.status()).isEqualTo("fallback");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.applied()).isFalse();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.fallbackReason()).isEqualTo("lookup_executor_saturated");
    }

    @Test
    void shouldReturnFailureForNonTimeoutNeo4jException() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        given(driver.session(any(SessionConfig.class))).willReturn(session);
        given(session.run(any(Query.class), any(TransactionConfig.class)))
                .willThrow(new ClientException("Neo.ClientError.Statement.SyntaxError", "invalid query"));

        QaTopicEntityBindingResult result = new QaTopicEntityBindingService(driver, new Neo4jProperties())
                .bind("死锁", 3L, 17L);

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.strategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(result.applied()).isFalse();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.fallbackReason()).contains("invalid query");
    }
}
