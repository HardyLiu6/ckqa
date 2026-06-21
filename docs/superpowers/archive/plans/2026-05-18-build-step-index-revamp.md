# 构建向导索引步骤改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把构建向导第 05 步「索引构建」从 KB 全量索引列表改造为 build_run 隔离视图：列表按 `buildRunId` 过滤、首次构建用居中大按钮触发、运行中显示来自真实 GraphRAG `process.log` 的细粒度阶段与百分比、完成/失败态支持带二次确认的重建入口。

**Architecture:**
- **后端**：`IndexRunResponse` 新增 `buildRunId` 字段；`ProcessRunner` 改成行级 tee（运行时就能读到 `process.log`，不再等进程退出）；新增 `IndexProgressParser` 解析日志得到 `{ totalWorkflows, currentWorkflowIndex, currentWorkflowKey, completedWorkflowKeys, subProgress, percentage }`；`BuildRunDetailResponse` 在 `currentStage == "index"` 且 `status == "running"` 时附带 `indexProgress`。
- **前端**：`module-loaders.js` 新增 `buildRunIndexRunsBlock`（按 `buildRunId` 过滤，按 `startedAt` 倒排）；`BuildStepIndex.vue` 重写为三态视图（idle / running / done|failed），idle 用居中大按钮触发，running 用 `snapshot.indexProgress` 渲染真实阶段名与百分比，done/failed 用 `ElMessageBox` 确认后重建；`LONG_TASK_LIMITS.index` 扩到 130min、轮询间隔降到 5s。

**Tech Stack:** Java 21 + Spring Boot 3 + JUnit 5；Vue 3 + Element Plus + node:test；GraphRAG 3.0.9（10 个 workflow，其中 `extract_covariates` 默认关闭）。

---

## 真实日志格式参考

来自 [graphrag_pipeline/.../build_20/index/logs/process.log](graphrag_pipeline/runtime/kb-build-runs/user_12/kb_5/build_20/index/logs/process.log) 的真实结构：

```
Starting pipeline with workflows: load_input_documents, create_base_text_units, create_final_documents, extract_graph, finalize_graph, extract_covariates, create_communities, create_final_text_units, create_community_reports, generate_text_embeddings
Starting workflow: load_input_documents

Workflow complete: load_input_documents
... (dataframe dump)
Starting workflow: create_base_text_units
  1 / 393   2 / 393   3 / 393   ...   393 / 393 ...
Workflow complete: create_base_text_units
Starting workflow: create_final_documents
...
```

经 ProcessRunner tee 处理后每行带 `[stdout] ` 前缀（详见 Task 2）。

**解析口径：**
- 第一行 `Starting pipeline with workflows: a, b, c, ...`：拿到本次实际执行的有序 workflow 列表（已自动剔除被关闭的 workflow，比如某些 KB 不配置 `extract_covariates`）。
- `Starting workflow: X` / `Workflow complete: X`：推进当前阶段。
- 当前 workflow 行内最后一个 `(\d+) / (\d+)`：子进度（`current / total`）。

**百分比计算（后端，单一来源真理）：**
按真实统计耗时（来自多次 `stats.json`，详见下表）维护静态权重表。当前 workflow 在 pipeline 列表中的下标 `i`、子进度比例 `r ∈ [0, 1]`：

```
percentage = round( sum(weight[0..i-1]) + weight[i] * r )
```

| workflow | 真实占比(参考) | weight |
|---|---|---|
| load_input_documents | <0.1% | 1 |
| create_base_text_units | <0.1% | 2 |
| create_final_documents | <0.1% | 1 |
| extract_graph | 16% | 22 |
| finalize_graph | <0.1% | 2 |
| extract_covariates | 0%（默认关闭） | 1 |
| create_communities | <0.1% | 2 |
| create_final_text_units | <0.1% | 2 |
| create_community_reports | 82% | 50 |
| generate_text_embeddings | 2% | 17 |
| **合计** | | **100** |

未在表内的 workflow 一律按 weight=2 兜底。pipeline 实际工作流若不包含某项，对应权重不计入分母——前端不需要知道权重，只读后端给出的 `percentage`。

---

## 文件结构

| 操作 | 路径 | 职责 |
|------|------|------|
| Modify | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java` | 把 stdout/stderr 读取改成行级 tee，日志在子进程运行期间可读 |
| Modify | `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java` | 新增"运行中日志可读"断言 |
| Create | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexProgressParser.java` | 解析 process.log 文本，输出 `IndexProgress` |
| Create | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexProgress.java` | 进度 DTO |
| Create | `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexProgressParserTest.java` | 基于真实日志片段的解析器测试 |
| Create | `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-running.log` | 真实日志片段（进行中） |
| Create | `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-finalstage.log` | 真实日志片段（已进入 generate_text_embeddings） |
| Modify | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java` | 新增 `buildRunId` 字段 |
| Modify | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunDetailResponse.java` | 新增 `indexProgress` 字段 |
| Modify | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java` | `getBuildRun` 注入 `indexProgress`（仅在 index 阶段 running） |
| Modify | `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexArtifactRegistryService.java` | 暴露 `processLogPath(workspaceRoot)` 静态/包私有方法供 service 复用 |
| Modify | `frontend/apps/admin-app/src/views/pages/long-task-state.js` | `LONG_TASK_LIMITS.index` 调整为 5s 轮询、130min 超时 |
| Modify | `frontend/apps/admin-app/src/views/pages/module-loaders.js` | `mapIndexRunItem` 保留 `buildRunId/status/startedAt`；`loadKnowledgeBaseBuild` 增加 `buildRunIndexRuns` block |
| Modify | `frontend/apps/admin-app/src/views/pages/module-content.js` | 新增 `INDEX_STAGE_LABELS` 字典（workflow key → 中文标签） |
| Rewrite | `frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue` | 三态视图 + 居中按钮 + 真实进度条 + 重建确认弹窗 |
| Modify | `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | 事件接线 + 索引守卫放宽 + 客户端 elapsed 时间戳与每秒 tick |
| Modify | `frontend/apps/admin-app/src/views/pages/module-page-model.js` | `resolveOperationFeedback` 透传 `elapsedSeconds` 与 `indexProgress` |
| Modify | `frontend/apps/admin-app/src/app-shell.test.js` | 更新断言 + 新增 `mapIndexRunItem` 过滤测试 |

---

### Task 1: 后端 IndexRunResponse 新增 buildRunId 字段

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java`

[IndexRuns.java:45-46](backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexRuns.java#L45-L46) 实体已有 `buildRunId` 列，本任务只补 DTO 暴露。

- [ ] **Step 1: 编辑 IndexRunResponse.java，完整替换为下述内容**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.IndexRuns;

import java.time.LocalDateTime;

/**
 * 索引运行响应体。
 */
@Getter
public class IndexRunResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final Long buildRunId;
    private final String engine;
    private final String indexVersion;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final String runMetadata;

    private IndexRunResponse(
            Long id,
            Long knowledgeBaseId,
            Long buildRunId,
            String engine,
            String indexVersion,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String runMetadata
    ) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.buildRunId = buildRunId;
        this.engine = engine;
        this.indexVersion = indexVersion;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.runMetadata = runMetadata;
    }

    public static IndexRunResponse of(
            Long id,
            Long knowledgeBaseId,
            Long buildRunId,
            String engine,
            String indexVersion,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String runMetadata
    ) {
        return new IndexRunResponse(id, knowledgeBaseId, buildRunId, engine, indexVersion, status, startedAt, finishedAt, runMetadata);
    }

    public static IndexRunResponse fromEntity(IndexRuns indexRun) {
        return of(
                indexRun.getId(),
                indexRun.getKnowledgeBaseId(),
                indexRun.getBuildRunId(),
                indexRun.getEngine(),
                indexRun.getIndexVersion(),
                indexRun.getStatus(),
                indexRun.getStartedAt(),
                indexRun.getFinishedAt(),
                indexRun.getRunMetadata()
        );
    }
}
```

- [ ] **Step 2: 编译，预期报错"`of` 参数个数不匹配"**

Run: `cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -40`
Expected: 列出所有调用 `IndexRunResponse.of(...)` 的位置（含 main 与 test）。逐个跳到下一步修。

- [ ] **Step 3: 全仓搜索 `IndexRunResponse.of(`，逐处补 `buildRunId` 参数**

Run: `grep -rn "IndexRunResponse\.of(" backend/ckqa-back/src --include="*.java"`

每处调用都在第二个位置（紧跟 `knowledgeBaseId`）补 `buildRunId` 值；测试 fixture 里没有现成 `buildRunId` 时传 `null` 即可。例如：

```java
// before
IndexRunResponse.of(1L, 10L, "graphrag", "v1", "running", now, null, "{}")
// after
IndexRunResponse.of(1L, 10L, 42L, "graphrag", "v1", "running", now, null, "{}")
```

- [ ] **Step 4: 重新编译，确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 运行后端测试**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest='*IndexRun*,*BuildRun*' 2>&1 | tail -30`
Expected: 全部通过。失败请按上面套路补 fixture。

- [ ] **Step 6: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java \
        backend/ckqa-back/src/test
git commit -m "feat(backend): IndexRunResponse 暴露 buildRunId 字段"
```

---

### Task 2: 后端 ProcessRunner 改为行级 tee，让 process.log 运行时可读

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java`

**为什么必须改：** 当前实现是 `readAllBytes()` + 进程退出后 `Files.writeString(...)`，子进程跑了 1–2 小时期间 `process.log` 文件根本不存在。Phase B 进度解析必须能在跑期间读到日志。

**改动语义：** stdout/stderr 仍然分别捕获并保留 `[stdout]`/`[stderr]` 行前缀；区别在于改成 BufferedReader 按行读，每读到一行立即向日志文件 append。两条流共用一个 ReentrantLock 序列化写入，避免穿插半行。`writeLogFile` 不再使用，删除。

- [ ] **Step 1: 写失败的测试——验证进程运行期间 process.log 已经出现头几行**

在 `ProcessRunnerTest.java` 末尾追加（保留现有类结构、imports 自动补齐）：

```java
    @Test
    void shouldStreamLogLinesWhileProcessRuns() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Path logFile = tempDir.resolve("process.log");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ProcessExecutionResult> future = executor.submit(() ->
                    runner.run(
                            java.util.List.of("bash", "-lc", "echo head; sleep 2; echo tail"),
                            tempDir,
                            Map.of(),
                            Duration.ofSeconds(10),
                            ProcessContext.builder().operation("index").logFile(logFile).build()
                    )
            );

            // 在子进程还在 sleep 时（启动后约 0.5s）就该看到 head 行
            Thread.sleep(800L);
            String midRun = Files.exists(logFile) ? Files.readString(logFile) : "";
            assertThat(midRun).contains("[stdout] head");
            assertThat(midRun).doesNotContain("[stdout] tail");

            ProcessExecutionResult result = future.get();
            assertThat(result.getExitCode()).isZero();
            String full = Files.readString(logFile);
            assertThat(full).contains("[stdout] head").contains("[stdout] tail");
        } finally {
            executor.shutdownNow();
        }
    }
```

- [ ] **Step 2: 运行新测试确认 FAIL**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest=ProcessRunnerTest#shouldStreamLogLinesWhileProcessRuns 2>&1 | tail -20`
Expected: 断言失败，因为 0.8s 时还没写日志（旧实现）。

- [ ] **Step 3: 改造 ProcessRunner.java 实现行级 tee**

完整替换文件内容：

```java
package org.ysu.ckqaback.integration.process;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一执行外部命令并跟踪活跃子进程。
 * <p>stdout/stderr 按行边读边 tee 写入 {@link ProcessContext#getLogFile()}，
 * 因此长任务（如 graphrag index）跑到一半时也能从日志文件读到进度。</p>
 */
@Component
public class ProcessRunner implements DisposableBean {

    private final ConcurrentMap<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final ExecutorService streamReaderExecutor = Executors.newCachedThreadPool();

    public ProcessExecutionResult run(
            List<String> command,
            Path workDir,
            Map<String, String> environment,
            Duration timeout,
            ProcessContext context
    ) throws IOException, InterruptedException {
        long key = sequence.getAndIncrement();
        long startedAt = System.nanoTime();

        Path logFile = context == null ? null : context.getLogFile();
        if (logFile != null) {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 同一 indexRun 重跑时，旧日志要先清掉，避免解析到上一次的 Workflow complete
            Files.writeString(
                    logFile,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        }
        ReentrantLock logLock = new ReentrantLock();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.environment().putAll(environment);

        Process process = builder.start();
        activeProcesses.put(key, process);

        Future<String> stdoutFuture = streamReaderExecutor.submit(() ->
                drainAndTee(process.getInputStream(), "stdout", logFile, logLock));
        Future<String> stderrFuture = streamReaderExecutor.submit(() ->
                drainAndTee(process.getErrorStream(), "stderr", logFile, logLock));

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            return ProcessExecutionResult.builder()
                    .command(command)
                    .exitCode(finished ? process.exitValue() : -1)
                    .stdout(stdout)
                    .stderr(stderr)
                    .elapsedSeconds(Duration.ofNanos(System.nanoTime() - startedAt).toSeconds())
                    .timedOut(!finished)
                    .terminatedByShutdown(shutdownRequested.get())
                    .build();
        } catch (ExecutionException ex) {
            throw new IOException("读取进程输出失败", ex);
        } finally {
            activeProcesses.remove(key);
        }
    }

    /**
     * 同步读取一条输入流：每读到一行，加锁追加到日志文件并累加到返回字符串。
     */
    private String drainAndTee(InputStream stream, String streamName, Path logFile, ReentrantLock lock) throws IOException {
        StringBuilder accumulator = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                accumulator.append(line).append(System.lineSeparator());
                if (logFile == null) {
                    continue;
                }
                String logLine = "[" + streamName + "] " + line + System.lineSeparator();
                lock.lock();
                try (BufferedWriter writer = Files.newBufferedWriter(
                        logFile,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND)) {
                    writer.write(logLine);
                } finally {
                    lock.unlock();
                }
            }
        }
        return accumulator.toString();
    }

    int activeProcessCount() {
        return activeProcesses.size();
    }

    @Override
    public void destroy() {
        shutdownRequested.set(true);
        activeProcesses.values().forEach(process -> {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
        streamReaderExecutor.shutdownNow();
    }
}
```

- [ ] **Step 4: 运行所有 ProcessRunner 相关测试，确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest=ProcessRunnerTest 2>&1 | tail -20`
Expected: 全部通过（原来 3 个 + 新增 1 个 = 4 个）。

- [ ] **Step 5: 跑 GraphRagIndexOrchestrator 测试做回归**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest=GraphRagIndexOrchestratorTest`
Expected: PASS。该测试用 mock ProcessRunner，不会被本次改动影响，但跑一遍兜底。

- [ ] **Step 6: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java
git commit -m "refactor(process): ProcessRunner 行级 tee 日志，运行期可读"
```

---

### Task 3: 创建 IndexProgress DTO

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexProgress.java`

- [ ] **Step 1: 写 DTO 文件**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GraphRAG 索引实时进度。仅在 BuildRun.currentStage="index" 且 status="running"
 * 时由后端从 process.log 解析后填入 {@link BuildRunDetailResponse}。
 *
 * <p>设计为 100% 来自日志解析：前端无需理解权重表，直接拿 {@link #percentage}
 * 渲染进度条，拿 {@link #currentWorkflowKey} 渲染当前阶段名。</p>
 */
@Getter
@Builder
public class IndexProgress {
    /** 本次 pipeline 实际执行的有序 workflow 列表（已剔除被关闭的工作流）。 */
    private final List<String> pipelineWorkflows;
    /** 当前正在执行的 workflow 在 pipelineWorkflows 中的下标，从 0 开始；全部完成时等于 size-1。 */
    private final int currentWorkflowIndex;
    /** 当前正在执行的 workflow key；全部完成时为 pipelineWorkflows 最后一个。 */
    private final String currentWorkflowKey;
    /** 已完成的 workflow key 列表，按完成顺序。 */
    private final List<String> completedWorkflowKeys;
    /** 当前 workflow 的子进度；不存在时为 null。 */
    private final SubProgress subProgress;
    /** 加权百分比 [0, 100]。 */
    private final int percentage;

    @Getter
    @Builder
    public static class SubProgress {
        private final int current;
        private final int total;
    }
}
```

- [ ] **Step 2: 编译**

Run: `cd backend/ckqa-back && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexProgress.java
git commit -m "feat(backend): 新增 IndexProgress DTO"
```

---

### Task 4: 创建 IndexProgressParser + 单元测试（TDD）

**Files:**
- Create: `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-running-extract-graph.log`
- Create: `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-empty.log`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexProgressParserTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexProgressParser.java`

- [ ] **Step 1: 准备 fixture——"正在跑 extract_graph 第 128 / 533"**

写入 `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-running-extract-graph.log`：

```
[stdout] Starting pipeline with workflows: load_input_documents, create_base_text_units, create_final_documents, extract_graph, finalize_graph, create_communities, create_final_text_units, create_community_reports, generate_text_embeddings
[stdout] Starting workflow: load_input_documents
[stdout] Workflow complete: load_input_documents
[stdout] Starting workflow: create_base_text_units
[stdout]   1 / 393   2 / 393   393 / 393 ......
[stdout] Workflow complete: create_base_text_units
[stdout] Starting workflow: create_final_documents
[stdout] Workflow complete: create_final_documents
[stdout] Starting workflow: extract_graph
[stdout]   1 / 533   2 / 533   100 / 533 .......  127 / 533 ............  128 / 533 ............
```

- [ ] **Step 2: 准备 fixture——空日志**

写入 `backend/ckqa-back/src/test/resources/fixtures/graphrag/process-empty.log`（保持文件存在但内容为空）。直接：

Run: `: > backend/ckqa-back/src/test/resources/fixtures/graphrag/process-empty.log`

- [ ] **Step 3: 写解析器测试**

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.index.dto.IndexProgress;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IndexProgressParserTest {

    private final IndexProgressParser parser = new IndexProgressParser();

    @Test
    void shouldReturnEmptyWhenLogIsEmpty() {
        Optional<IndexProgress> progress = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-empty.log"));
        assertThat(progress).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenLogPathMissing() {
        Optional<IndexProgress> progress = parser.parse(Path.of("/nonexistent/path/process.log"));
        assertThat(progress).isEmpty();
    }

    @Test
    void shouldParseRunningExtractGraphSnapshot() {
        Optional<IndexProgress> maybe = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-running-extract-graph.log"));
        assertThat(maybe).isPresent();
        IndexProgress progress = maybe.get();

        assertThat(progress.getPipelineWorkflows()).containsExactly(
                "load_input_documents", "create_base_text_units", "create_final_documents",
                "extract_graph", "finalize_graph", "create_communities",
                "create_final_text_units", "create_community_reports", "generate_text_embeddings");
        assertThat(progress.getCurrentWorkflowKey()).isEqualTo("extract_graph");
        assertThat(progress.getCurrentWorkflowIndex()).isEqualTo(3);
        assertThat(progress.getCompletedWorkflowKeys()).containsExactly(
                "load_input_documents", "create_base_text_units", "create_final_documents");
        assertThat(progress.getSubProgress()).isNotNull();
        assertThat(progress.getSubProgress().getCurrent()).isEqualTo(128);
        assertThat(progress.getSubProgress().getTotal()).isEqualTo(533);
        // 累计权重：1+2+1=4；当前 extract_graph 子进度 128/533≈0.240，weight 22 * 0.240 ≈ 5.28
        // 总和约 9 → round 后 9
        assertThat(progress.getPercentage()).isBetween(8, 10);
    }

    @Test
    void shouldClampPercentageBetweenZeroAndNinetyNine() {
        // 当 percentage 公式溢出（例如某次 weights 之和大于 100），裁到 99 防止 UI 100% 又还在跑
        // 此用例由实现保证；这里只构造一个真实 fixture 上界场景验证不会大于 99
        Optional<IndexProgress> maybe = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-running-extract-graph.log"));
        assertThat(maybe).isPresent();
        assertThat(maybe.get().getPercentage()).isBetween(0, 99);
    }
}
```

- [ ] **Step 4: 运行测试，确认 FAIL（IndexProgressParser 不存在）**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest=IndexProgressParserTest 2>&1 | tail -20`
Expected: 编译失败 / `cannot find symbol IndexProgressParser`。

- [ ] **Step 5: 写 IndexProgressParser 实现**

```java
package org.ysu.ckqaback.index;

import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.IndexProgress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 graphrag {@code process.log} 得到实时进度。
 *
 * <p>日志由 {@code ProcessRunner.drainAndTee} 写入，每行带 {@code [stdout] } 或
 * {@code [stderr] } 前缀。本解析器只关注 stdout 行。</p>
 */
@Component
public class IndexProgressParser {

    private static final Pattern PIPELINE_LINE = Pattern.compile(
            "Starting pipeline with workflows:\\s*(.+)\\s*$");
    private static final Pattern START_WORKFLOW = Pattern.compile(
            "Starting workflow:\\s*(\\S+)\\s*$");
    private static final Pattern COMPLETE_WORKFLOW = Pattern.compile(
            "Workflow complete:\\s*(\\S+)\\s*$");
    private static final Pattern SUB_PROGRESS = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    private static final Map<String, Integer> WEIGHTS = Map.of(
            "load_input_documents", 1,
            "create_base_text_units", 2,
            "create_final_documents", 1,
            "extract_graph", 22,
            "finalize_graph", 2,
            "extract_covariates", 1,
            "create_communities", 2,
            "create_final_text_units", 2,
            "create_community_reports", 50,
            "generate_text_embeddings", 17
    );
    private static final int DEFAULT_WEIGHT = 2;

    public Optional<IndexProgress> parse(Path logFile) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return Optional.empty();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            return Optional.empty();
        }

        List<String> pipelineWorkflows = null;
        List<String> completed = new ArrayList<>();
        String currentWorkflow = null;
        String currentWorkflowSubProgressLine = null;

        for (String raw : lines) {
            String line = stripPrefix(raw);
            if (line == null) {
                continue;
            }
            Matcher pipelineMatcher = PIPELINE_LINE.matcher(line);
            if (pipelineMatcher.find()) {
                pipelineWorkflows = parseWorkflowList(pipelineMatcher.group(1));
                continue;
            }
            Matcher startMatcher = START_WORKFLOW.matcher(line);
            if (startMatcher.find()) {
                currentWorkflow = startMatcher.group(1);
                currentWorkflowSubProgressLine = null;
                continue;
            }
            Matcher completeMatcher = COMPLETE_WORKFLOW.matcher(line);
            if (completeMatcher.find()) {
                String done = completeMatcher.group(1);
                if (!completed.contains(done)) {
                    completed.add(done);
                }
                if (done.equals(currentWorkflow)) {
                    currentWorkflowSubProgressLine = null;
                }
                continue;
            }
            // 同一 workflow 内累积的 "N / M" 行：用最新一行的最后一个匹配
            if (currentWorkflow != null && SUB_PROGRESS.matcher(line).find()) {
                currentWorkflowSubProgressLine = line;
            }
        }

        if (pipelineWorkflows == null || pipelineWorkflows.isEmpty()) {
            return Optional.empty();
        }
        if (currentWorkflow == null) {
            currentWorkflow = pipelineWorkflows.get(0);
        }
        int currentIndex = Math.max(0, pipelineWorkflows.indexOf(currentWorkflow));

        IndexProgress.SubProgress sub = extractLastSubProgress(currentWorkflowSubProgressLine);
        int percentage = computePercentage(pipelineWorkflows, currentIndex, completed, sub);

        return Optional.of(IndexProgress.builder()
                .pipelineWorkflows(pipelineWorkflows)
                .currentWorkflowIndex(currentIndex)
                .currentWorkflowKey(currentWorkflow)
                .completedWorkflowKeys(completed)
                .subProgress(sub)
                .percentage(percentage)
                .build());
    }

    private String stripPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("[stdout] ")) {
            return raw.substring("[stdout] ".length());
        }
        if (raw.startsWith("[stderr] ")) {
            return null; // 进度只看 stdout
        }
        return raw;
    }

    private List<String> parseWorkflowList(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private IndexProgress.SubProgress extractLastSubProgress(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = SUB_PROGRESS.matcher(line);
        int current = 0;
        int total = 0;
        while (matcher.find()) {
            current = Integer.parseInt(matcher.group(1));
            total = Integer.parseInt(matcher.group(2));
        }
        if (total <= 0) {
            return null;
        }
        return IndexProgress.SubProgress.builder().current(current).total(total).build();
    }

    private int computePercentage(
            List<String> pipelineWorkflows,
            int currentIndex,
            List<String> completed,
            IndexProgress.SubProgress sub
    ) {
        // 分母：本次 pipeline 实际涉及的 workflow 权重总和
        int totalWeight = pipelineWorkflows.stream()
                .mapToInt(w -> WEIGHTS.getOrDefault(w, DEFAULT_WEIGHT))
                .sum();
        if (totalWeight <= 0) {
            return 0;
        }
        // 分子：已完成的权重 + 当前 workflow 的子进度比例 * 权重
        Map<String, Boolean> doneMap = new LinkedHashMap<>();
        completed.forEach(c -> doneMap.put(c, Boolean.TRUE));

        int accumulated = 0;
        for (int i = 0; i < pipelineWorkflows.size(); i++) {
            String key = pipelineWorkflows.get(i);
            int weight = WEIGHTS.getOrDefault(key, DEFAULT_WEIGHT);
            if (i < currentIndex || doneMap.containsKey(key)) {
                accumulated += weight;
            } else if (i == currentIndex && sub != null && sub.getTotal() > 0) {
                double ratio = Math.min(1.0, (double) sub.getCurrent() / sub.getTotal());
                accumulated += (int) Math.round(weight * ratio);
                break;
            } else {
                break;
            }
        }
        double pct = 100.0 * accumulated / totalWeight;
        int rounded = (int) Math.round(pct);
        if (rounded < 0) return 0;
        if (rounded > 99) return 99;
        return rounded;
    }
}
```

- [ ] **Step 6: 运行测试，全部通过**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest=IndexProgressParserTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexProgressParser.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexProgress.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexProgressParserTest.java \
        backend/ckqa-back/src/test/resources/fixtures/graphrag/
git commit -m "feat(backend): IndexProgressParser 解析 process.log 实时进度"
```

---

### Task 5: 在 BuildRunDetailResponse 中注入 indexProgress

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunDetailResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`

**思路：** `KnowledgeBaseBuildRunService.getBuildRun(id)` 是前端轮询唯一入口。在该方法返回前，若 `currentStage == "index"` 且 `status == "running"`，调用 `IndexProgressParser.parse(workspaceRoot/index/logs/process.log)` 把结果塞到 response。其他场景一律置 null。

- [ ] **Step 1: 给 BuildRunDetailResponse 增加 indexProgress 字段**

修改 [BuildRunDetailResponse.java](backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunDetailResponse.java)：

在字段列表底部、`updatedAt` 之后追加：

```java
    private final IndexProgress indexProgress;
```

并新增 imports：

```java
import org.ysu.ckqaback.index.dto.IndexProgress;
```

（注意：BuildRunDetailResponse 已经在 `dto` 包内，直接 `IndexProgress` 即可，import 不需要写全路径——除非 IDE 风格要求显式 import 同包内成员，按团队风格处理。）

`fromEntity` 不接收 indexProgress，需要新增一个 `withIndexProgress(IndexProgress)` 工厂方法：

```java
    public static BuildRunDetailResponse fromEntity(KnowledgeBaseBuildRuns buildRun) {
        return fromEntity(buildRun, null);
    }

    public static BuildRunDetailResponse fromEntity(KnowledgeBaseBuildRuns buildRun, IndexProgress indexProgress) {
        return BuildRunDetailResponse.builder()
                .id(buildRun.getId())
                .knowledgeBaseId(buildRun.getKnowledgeBaseId())
                .courseId(buildRun.getCourseId())
                .requestedByUserId(buildRun.getRequestedByUserId())
                .buildVersion(buildRun.getBuildVersion())
                .status(buildRun.getStatus())
                .currentStage(buildRun.getCurrentStage())
                .qaStatus(buildRun.getQaStatus())
                .activationPolicy(buildRun.getActivationPolicy())
                .selectedMaterialIds(buildRun.getSelectedMaterialIds())
                .activeIndexRunId(buildRun.getActiveIndexRunId())
                .workspaceUri(buildRun.getWorkspaceUri())
                .buildMetadata(buildRun.getBuildMetadata())
                .startedAt(buildRun.getStartedAt())
                .finishedAt(buildRun.getFinishedAt())
                .createdAt(buildRun.getCreatedAt())
                .updatedAt(buildRun.getUpdatedAt())
                .indexProgress(indexProgress)
                .build();
    }
```

- [ ] **Step 2: KnowledgeBaseBuildRunService.getBuildRun 注入 indexProgress**

定位 [KnowledgeBaseBuildRunService.getBuildRun](backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java) 方法（按方法签名 `public BuildRunDetailResponse getBuildRun(Long id)` 搜索）。

注入两个新依赖到构造函数：

```java
    private final IndexProgressParser indexProgressParser;
    private final BuildRunWorkspaceService workspaceService; // 若已存在则复用
```

（若 `workspaceService` 已经在类里就不重复加。）

修改 `getBuildRun(...)` 在 build 实体取到之后、转 DTO 之前，加入：

```java
        IndexProgress progress = null;
        if ("running".equalsIgnoreCase(buildRun.getStatus())
                && "index".equalsIgnoreCase(buildRun.getCurrentStage())
                && buildRun.getWorkspaceUri() != null) {
            try {
                Path workspaceRoot = workspaceService.resolve(buildRun.getWorkspaceUri());
                Path logPath = workspaceRoot.resolve("index").resolve("logs").resolve("process.log");
                progress = indexProgressParser.parse(logPath).orElse(null);
            } catch (Exception ex) {
                // 解析失败不阻断前端轮询；返回 null 即可，前端会回退到"准备中"
                progress = null;
            }
        }
        return BuildRunDetailResponse.fromEntity(buildRun, progress);
```

补 imports：

```java
import java.nio.file.Path;
import org.ysu.ckqaback.index.dto.IndexProgress;
```

- [ ] **Step 3: 编译并跑相关测试**

Run: `cd backend/ckqa-back && ./mvnw -q test -Dtest='*BuildRun*,*IndexProgress*'`
Expected: PASS。若现有 `getBuildRun` 测试因为新增了行为而失败，按 mock 套路给 `indexProgressParser` 返回 `Optional.empty()`，给 `workspaceService.resolve(...)` 返回 tempDir。

- [ ] **Step 4: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunDetailResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test
git commit -m "feat(backend): BuildRunDetailResponse 在 index running 时携带 indexProgress"
```

---

### Task 6: 前端长任务超时扩容 + 轮询提速

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/long-task-state.js`

GraphRAG 真实耗时 1–2h，原 30min deadline 会在跑到一半时误判失败。轮询从 30s 缩到 5s 让进度条体感顺滑。

- [ ] **Step 1: 调整常量**

修改 [long-task-state.js](frontend/apps/admin-app/src/views/pages/long-task-state.js) 顶部：

```javascript
export const LONG_TASK_LIMITS = {
  parse: { intervalMs: 10000, timeoutMs: 900000 },
  export: { intervalMs: 5000, timeoutMs: 300000 },
  // GraphRAG 索引真实耗时 1-2 小时；轮询 5s 一次让进度条平滑推进，deadline 给 130min 冗余
  index: { intervalMs: 5000, timeoutMs: 7800000 },
}
```

- [ ] **Step 2: 跑前端测试看是否有断言依赖旧值**

Run: `cd frontend/apps/admin-app && pnpm test 2>&1 | grep -iE 'long_task|LIMITS|index'` 
Expected: 没有命中——若有，按新值更新断言。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/long-task-state.js
git commit -m "chore(frontend): 索引长任务 5s 轮询、130min 超时"
```

---

### Task 7: 前端 mapIndexRunItem 暴露 buildRunId / status / startedAt，新增 buildRunIndexRuns block

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`

- [ ] **Step 1: 改 mapIndexRunItem**

找到 [module-loaders.js:2156](frontend/apps/admin-app/src/views/pages/module-loaders.js#L2156)，整段替换：

```javascript
function mapIndexRunItem(indexRun = {}) {
  const id = indexRun.id ?? indexRun.indexRunId
  return {
    id,
    buildRunId: indexRun.buildRunId ?? null,
    status: indexRun.status ?? null,
    startedAt: indexRun.startedAt ?? null,
    finishedAt: indexRun.finishedAt ?? null,
    title: id ? `索引运行 #${id}` : '索引运行',
    meta: indexRun.status ?? '-',
    detail: indexRun.createdAt ?? indexRun.startedAt ?? indexRun.updatedAt ?? '',
    to: id ? `/app/index-runs/${id}` : '',
  }
}
```

- [ ] **Step 2: 在 loadKnowledgeBaseBuild 里新增 buildRunIndexRunsBlock**

定位 [loadKnowledgeBaseBuild](frontend/apps/admin-app/src/views/pages/module-loaders.js#L920)。在 `indexRunsBlock = createSettledListBlock(...)` 这一行（约 [line 933](frontend/apps/admin-app/src/views/pages/module-loaders.js#L933)）之后插入：

```javascript
  // 按 buildRunId 过滤出本次构建的索引运行（成功 + 失败重试都在内），按 startedAt 倒排
  // 继承 indexRunsBlock 的错误态——接口失败不会被吞成 empty
  const buildRunIndexRunsBlock = (() => {
    if (indexRunsBlock.state !== 'success') {
      return { ...indexRunsBlock, items: [] }
    }
    const filtered = (indexRunsBlock.items ?? [])
      .filter((item) => item.buildRunId != null && String(item.buildRunId) === String(buildRunId))
      .sort((a, b) => {
        const ta = a.startedAt ? new Date(a.startedAt).getTime() : 0
        const tb = b.startedAt ? new Date(b.startedAt).getTime() : 0
        const taSafe = Number.isNaN(ta) ? 0 : ta
        const tbSafe = Number.isNaN(tb) ? 0 : tb
        return tbSafe - taSafe
      })
    return {
      state: filtered.length > 0 ? 'success' : 'empty',
      items: filtered,
    }
  })()
```

然后在返回对象的 `blocks: {...}` 内，紧跟 `indexRuns: indexRunsBlock,` 后插一行：

```javascript
      buildRunIndexRuns: buildRunIndexRunsBlock,
```

- [ ] **Step 3: 构建验证**

Run: `cd frontend/apps/admin-app && pnpm build 2>&1 | tail -10`
Expected: `✓ built`

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-loaders.js
git commit -m "feat(frontend): 索引运行列表按 buildRunId 过滤为独立 block"
```

---

### Task 8: 前端 INDEX_STAGE_LABELS 字典

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`

只放 workflow key → 中文标签的映射；不放权重表（百分比来自后端）。

- [ ] **Step 1: 在 module-content.js 末尾追加**

```javascript
/**
 * GraphRAG workflow key 到中文标签的字典。
 * 用于 BuildStepIndex 渲染当前阶段名；百分比与阶段顺序均来自后端 indexProgress，不在前端做权重计算。
 */
export const INDEX_STAGE_LABELS = {
  load_input_documents: '加载输入文档',
  create_base_text_units: '创建文本单元',
  create_final_documents: '生成文档索引',
  extract_graph: '抽取知识图谱',
  finalize_graph: '图谱后处理',
  extract_covariates: '抽取协变量',
  create_communities: '构建社区',
  create_final_text_units: '生成最终文本单元',
  create_community_reports: '生成社区报告',
  generate_text_embeddings: '生成文本嵌入',
}

export function resolveIndexStageLabel(key) {
  return INDEX_STAGE_LABELS[key] ?? key ?? '准备中'
}
```

- [ ] **Step 2: 构建验证**

Run: `cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5`
Expected: `✓ built`

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js
git commit -m "feat(frontend): 新增 INDEX_STAGE_LABELS 字典"
```

---

### Task 9: 重写 BuildStepIndex.vue 三态视图

**Files:**
- Rewrite: `frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue`

**状态机：**
- **idle**：本次 build_run 还没有任何索引运行 → 居中大按钮"开始构建索引"。按钮禁用条件 `step?.status !== 'ready' || actionRunning`。
- **running**：`actionRunning` 或 `latestRun.status ∈ {running, pending}` → 用真实 `indexProgress` 渲染百分比 + 阶段名 + 已完成阶段列表 + 子进度（"N / M"）。
- **failed**：`latestRun.status === 'failed'` 且不在 actionRunning → 错误提示 + "重试索引构建"次级按钮（带 ElMessageBox 确认）。
- **done**：`latestRun.status === 'done|success'` 且不在 actionRunning → 索引运行列表 + "重新构建索引"次级按钮（带 ElMessageBox 确认）。主操作"进入问答验证"由外层主按钮承担，本组件不重复实现。

**美观要点（沿用 ckqa-* token，参考 [Visual Design Consistency](.claude/projects/-home-sunlight-Projects-ckqa/memory/feedback_visual_design_consistency.md)）：**
- idle 大按钮居中、上下大量留白、配渐变图标圆盘。
- running 用 Element Plus 的 `el-progress`，并自定义 inner 渐变。
- 完成阶段：绿色对勾点；当前阶段：accent 色脉动点；待运行：灰色实心点。

- [ ] **Step 1: 完整替换 BuildStepIndex.vue**

```vue
<script setup>
import { computed } from 'vue'
import { Database, Hammer, RefreshCw, AlertTriangle } from 'lucide-vue-next'
import { ElButton, ElMessageBox, ElProgress } from 'element-plus'
import StatusBadge from '../common/StatusBadge.vue'
import { resolveIndexStageLabel } from '../../views/pages/module-content.js'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  step: { type: Object, default: null },
})

const emit = defineEmits(['start-index', 'rebuild-index'])

// 本次 build_run 的索引运行列表（已按 startedAt desc 排序）
const indexRuns = computed(() => props.blocks.buildRunIndexRuns?.items ?? [])
const latestRun = computed(() => indexRuns.value[0] ?? null)
const hasRuns = computed(() => indexRuns.value.length > 0)

const latestStatus = computed(() => latestRun.value?.status ?? null)
const isRunning = computed(() =>
  props.actionRunning
  || latestStatus.value === 'running'
  || latestStatus.value === 'pending'
)
const isFailed = computed(() => !props.actionRunning && latestStatus.value === 'failed')
const isDone = computed(() =>
  !props.actionRunning
  && (latestStatus.value === 'done' || latestStatus.value === 'success')
)
const isIdle = computed(() => !hasRuns.value && !props.actionRunning)

// 真实进度，来自后端 BuildRunDetailResponse.indexProgress（透传到 operationFeedback.indexProgress）
const progress = computed(() => props.operationFeedback?.indexProgress ?? null)

const percentage = computed(() => progress.value?.percentage ?? 0)
const currentStageLabel = computed(() => resolveIndexStageLabel(progress.value?.currentWorkflowKey))
const subProgressText = computed(() => {
  const sub = progress.value?.subProgress
  if (!sub || !sub.total) return ''
  return `${sub.current} / ${sub.total}`
})

const stages = computed(() => {
  const list = progress.value?.pipelineWorkflows ?? []
  const completed = new Set(progress.value?.completedWorkflowKeys ?? [])
  const currentKey = progress.value?.currentWorkflowKey
  return list.map((key) => ({
    key,
    label: resolveIndexStageLabel(key),
    state: completed.has(key) ? 'done' : key === currentKey ? 'active' : 'pending',
  }))
})

const elapsedSeconds = computed(() => props.operationFeedback?.elapsedSeconds ?? 0)

function formatElapsed(seconds) {
  if (!seconds || seconds <= 0) return '0 秒'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h} 时 ${m} 分 ${s} 秒`
  if (m > 0) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

function handleStart() {
  emit('start-index')
}

async function handleRebuild() {
  try {
    await ElMessageBox.confirm(
      '重新构建索引将消耗 GraphRAG 计算资源（通常需要 1–2 小时），确定要继续吗？',
      '确认重新构建索引',
      { confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning' },
    )
    emit('rebuild-index')
  } catch {
    // 用户取消，不做任何动作
  }
}
</script>

<template>
  <section class="build-step-index">
    <!-- 非 running 态时的操作反馈横幅 -->
    <Transition name="slide-down">
      <div v-if="operationFeedback && !isRunning" class="operation-feedback" :data-status="operationFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ operationFeedback.title }}</strong>
          <StatusBadge :status="operationFeedback.status" />
        </div>
        <p>{{ operationFeedback.message }}</p>
        <small v-if="operationFeedback.detail">{{ operationFeedback.detail }}</small>
      </div>
    </Transition>

    <!-- 空态：居中大按钮 -->
    <div v-if="isIdle" class="build-step-index__idle">
      <div class="build-step-index__idle-icon">
        <Database :size="48" />
      </div>
      <h3 class="build-step-index__idle-title">本次构建尚未触发索引运行</h3>
      <p class="build-step-index__idle-desc">
        GraphRAG 将基于已确认的图谱输入和提示词策略构建知识图谱索引，<br>
        过程包含实体抽取、社区发现、报告生成等多个阶段，通常需要 1–2 小时。
      </p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary build-step-index__start-btn"
        type="primary"
        size="large"
        :disabled="step?.status !== 'ready' || actionRunning"
        @click="handleStart"
      >
        <Hammer class="button-icon" :size="18" />
        开始构建索引
      </el-button>
    </div>

    <!-- 构建中：进度条 -->
    <div v-else-if="isRunning" class="build-step-index__running">
      <div class="build-step-index__running-header">
        <Hammer :size="20" class="build-step-index__running-icon" />
        <span class="build-step-index__running-title">索引构建中</span>
        <span v-if="elapsedSeconds > 0" class="build-step-index__running-elapsed">
          已用时 {{ formatElapsed(elapsedSeconds) }}
        </span>
      </div>

      <el-progress
        :percentage="percentage"
        :stroke-width="12"
        :show-text="true"
        :format="() => `${percentage}%`"
        class="build-step-index__progress"
      />

      <div class="build-step-index__stage-info">
        <span class="build-step-index__stage-label">当前阶段：{{ currentStageLabel }}</span>
        <span v-if="subProgressText" class="build-step-index__stage-sub">{{ subProgressText }}</span>
      </div>

      <ol v-if="stages.length > 0" class="build-step-index__stages">
        <li
          v-for="stage in stages"
          :key="stage.key"
          class="build-step-index__stage-item"
          :class="`is-${stage.state}`"
        >
          <span class="build-step-index__stage-dot"></span>
          <span class="build-step-index__stage-name">{{ stage.label }}</span>
        </li>
      </ol>
      <p v-else class="build-step-index__stage-hint">
        正在初始化 GraphRAG，稍候将显示阶段进度…
      </p>
    </div>

    <!-- 已完成或失败：列表 + 重建/重试 -->
    <div v-else class="build-step-index__done">
      <ol class="build-task-list">
        <li v-for="item in indexRuns" :key="item.id" class="build-task-row">
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.detail }}</small>
          </div>
          <StatusBadge :status="item.meta" />
        </li>
      </ol>

      <div v-if="isFailed" class="build-step-index__failed-hint">
        <AlertTriangle :size="16" />
        <span>上次索引构建失败，可点击下方按钮重试。</span>
      </div>

      <div class="build-step-index__rebuild-area">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          :disabled="!['done', 'failed'].includes(step?.status) || actionRunning"
          :loading="actionRunning"
          @click="handleRebuild"
        >
          <RefreshCw class="button-icon" :size="14" />
          {{ isFailed ? '重试索引构建' : '重新构建索引' }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.build-step-index {
  display: grid;
  gap: 20px;
}

/* 空态：居中大按钮 */
.build-step-index__idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
  gap: 18px;
  border-radius: 16px;
  background: radial-gradient(ellipse at top, rgba(99, 102, 241, 0.05), transparent 70%);
}
.build-step-index__idle-icon {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(56, 189, 248, 0.12));
  border: 1px solid rgba(99, 102, 241, 0.2);
  color: var(--ckqa-accent, #6366f1);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12);
}
.build-step-index__idle-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__idle-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ckqa-text-muted, #64748b);
  max-width: 440px;
}
.build-step-index__start-btn {
  margin-top: 8px;
  min-width: 200px;
  height: 48px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.25);
}

/* 构建中 */
.build-step-index__running {
  padding: 28px 24px;
  border-radius: 14px;
  background: rgba(99, 102, 241, 0.03);
  border: 1px solid rgba(99, 102, 241, 0.12);
}
.build-step-index__running-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.build-step-index__running-icon {
  color: var(--ckqa-accent, #6366f1);
  animation: pulse-icon 2s ease-in-out infinite;
}
@keyframes pulse-icon {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.build-step-index__running-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__running-elapsed {
  margin-left: auto;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
  font-variant-numeric: tabular-nums;
}
.build-step-index__progress {
  margin-bottom: 14px;
}
.build-step-index__progress :deep(.el-progress-bar__outer) {
  border-radius: 6px;
}
.build-step-index__progress :deep(.el-progress-bar__inner) {
  border-radius: 6px;
  background: linear-gradient(90deg, var(--ckqa-accent, #6366f1), #38bdf8);
  transition: width 0.4s ease;
}
.build-step-index__stage-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
}
.build-step-index__stage-label {
  font-weight: 600;
  color: var(--ckqa-accent, #6366f1);
}
.build-step-index__stage-sub {
  font-variant-numeric: tabular-nums;
}
.build-step-index__stage-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ckqa-text-muted, #94a3b8);
  font-style: italic;
}

/* 阶段列表 */
.build-step-index__stages {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.build-step-index__stage-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
}
.build-step-index__stage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e2e8f0;
  flex-shrink: 0;
  transition: background 0.3s ease, box-shadow 0.3s ease;
}
.build-step-index__stage-item.is-done .build-step-index__stage-dot {
  background: #10b981;
}
.build-step-index__stage-item.is-active .build-step-index__stage-dot {
  background: var(--ckqa-accent, #6366f1);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
  animation: pulse-dot 1.5s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2); }
  50%      { box-shadow: 0 0 0 5px rgba(99, 102, 241, 0.1); }
}
.build-step-index__stage-item.is-done { color: #10b981; }
.build-step-index__stage-item.is-active {
  color: var(--ckqa-accent, #6366f1);
  font-weight: 600;
}

/* 完成 / 失败 */
.build-step-index__done {
  display: grid;
  gap: 16px;
}
.build-step-index__failed-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 12px;
}
.build-step-index__rebuild-area {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd frontend/apps/admin-app && pnpm build 2>&1 | tail -10`
Expected: `✓ built`

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue
git commit -m "feat(frontend): 重写 BuildStepIndex 为三态视图，进度条来自真实 GraphRAG 日志"
```

---

### Task 10: ModulePage 事件接线 + 守卫放宽 + 客户端 elapsed 时间戳

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/module-page-model.js`

**关键决策：**
- `elapsedSeconds` 用客户端按钮按下时间戳作为基线，**不要用** `snapshot.startedAt`——后者是 buildRun.startedAt（可能比索引开始早数小时）。详见 [KnowledgeBaseBuildRunService.markIndexStarted](backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java#L290) 里的 `if (startedAt == null)` 守卫。
- 加 1s setInterval tick，让进度条 / 已用时在两次 5s 轮询之间也能流畅推进。
- `resolveOperationFeedback` 把 `indexProgress` 与 `elapsedSeconds` 一起透传给 BuildStepIndex。

- [ ] **Step 1: 模板里给 component 加事件监听**

定位 [ModulePage.vue:3981](frontend/apps/admin-app/src/views/pages/ModulePage.vue#L3981) `<component :is="activeBuildStepComponent" .../>`。在现有 props/events 之外补两个事件：

```vue
        @start-index="runKnowledgeBaseIndex"
        @rebuild-index="runKnowledgeBaseIndex"
```

- [ ] **Step 2: 在 ModulePage 顶部 ref 区新增时间戳与 tick**

在 `actionState` / `actionSnapshot` 附近（约 [line 178-179](frontend/apps/admin-app/src/views/pages/ModulePage.vue#L178)）下方追加：

```javascript
// 索引按钮按下的时间戳；仅在客户端使用，刷新页面后丢失（届时由 BuildStepIndex 自身做兜底，见 Task 9 中的 elapsedSeconds=0 路径）
const indexStartedAtMs = ref(0)
// 每秒触发一次让 elapsed/百分比 computed 重新计算
const elapsedTickToken = ref(0)
let elapsedTickTimer = null
function startElapsedTick() {
  stopElapsedTick()
  elapsedTickTimer = setInterval(() => {
    elapsedTickToken.value += 1
  }, 1000)
}
function stopElapsedTick() {
  if (elapsedTickTimer) {
    clearInterval(elapsedTickTimer)
    elapsedTickTimer = null
  }
}
```

确认顶部 import 包含 `onBeforeUnmount`，并在 setup 末尾注册：

```javascript
onBeforeUnmount(() => stopElapsedTick())
```

- [ ] **Step 3: 改 runKnowledgeBaseIndex 守卫 + 注入 elapsedSeconds**

完整替换 [runKnowledgeBaseIndex](frontend/apps/admin-app/src/views/pages/ModulePage.vue#L2590)：

```javascript
async function runKnowledgeBaseIndex() {
  const indexStep = config.value.workflowSteps?.find((step) => step.key === 'index')

  // 允许 ready（首次）和 done/failed（重建）触发；正在跑则直接 return
  if (actionRunning.value) {
    return
  }
  if (!['ready', 'done', 'failed'].includes(indexStep?.status)) {
    return
  }

  let buildRunId
  try {
    buildRunId = (await ensureBuildRun()).id
  } catch (error) {
    activeOperationKey.value = 'index-build'
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'index-build'
  actionSnapshot.value = null
  indexStartedAtMs.value = Date.now()
  startElapsedTick()

  activeLongTaskController = createLongTaskController({
    trigger: ({ signal }) => createBuildRunIndexRun(buildRunId, {}, { post: (url, payload) => http.post(url, payload, { signal }) }),
    poll: ({ signal }) => getBuildRun(buildRunId, { get: (url) => http.get(url, { signal }) }),
    isSuccess: isBuildRunIndexSuccess,
    isFailed: (snapshot) => normalizeRunState(snapshot?.status) === 'failed',
    onState: (state, snapshot) => {
      actionState.value = state
      const enriched = snapshot && typeof snapshot === 'object' ? { ...snapshot } : {}
      if (indexStartedAtMs.value > 0) {
        enriched.elapsedSeconds = Math.max(0, Math.round((Date.now() - indexStartedAtMs.value) / 1000))
      }
      actionSnapshot.value = enriched
      if (['success', 'failed'].includes(state)) {
        stopElapsedTick()
      }
    },
    onSuccess: async () => {
      await navigateAfterBuildRunAction(buildRunId, resolveBuildStepQuery(route.query, 'qa_check'))
    },
    limits: LONG_TASK_LIMITS.index,
  })
  startActiveLongTask(activeLongTaskController)
}
```

- [ ] **Step 4: 让 operationFeedback 依赖 tick 并实时算 elapsed**

定位 [ModulePage.vue:503-506](frontend/apps/admin-app/src/views/pages/ModulePage.vue#L503-L506) 的 `operationFeedback = computed(...)`，整段替换：

```javascript
const operationFeedback = computed(() => {
  void elapsedTickToken.value
  const baseSnapshot = actionSnapshot.value && typeof actionSnapshot.value === 'object'
    ? { ...actionSnapshot.value }
    : null
  if (baseSnapshot && indexStartedAtMs.value > 0 && activeOperationKey.value === 'index-build') {
    baseSnapshot.elapsedSeconds = Math.max(0, Math.round((Date.now() - indexStartedAtMs.value) / 1000))
  }
  return resolveOperationFeedback(
    activeOperationKey.value,
    actionState.value,
    baseSnapshot,
  )
})
```

- [ ] **Step 5: resolveOperationFeedback 透传 elapsedSeconds + indexProgress**

修改 [module-page-model.js 中的 resolveOperationFeedback](frontend/apps/admin-app/src/views/pages/module-page-model.js) 返回对象，在末尾追加两个字段：

```javascript
  return {
    scope: operation.scope,
    title,
    message,
    detail: operation.detail,
    meta: resolveOperationMeta(apiError),
    status: normalizedState === 'confirming' ? 'running' : normalizedState,
    elapsedSeconds: snapshot?.elapsedSeconds ?? null,
    indexProgress: snapshot?.indexProgress ?? null,
  }
```

其他逻辑保持不变。

- [ ] **Step 6: 构建验证**

Run: `cd frontend/apps/admin-app && pnpm build 2>&1 | tail -10`
Expected: `✓ built`

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue \
        frontend/apps/admin-app/src/views/pages/module-page-model.js
git commit -m "feat(frontend): 索引按钮事件接线、守卫放宽、客户端 elapsed 计时"
```

---

### Task 11: 更新 app-shell.test.js 断言

**Files:**
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 先跑测试观察现存失败项**

Run: `cd frontend/apps/admin-app && pnpm test 2>&1 | tail -60`
Expected: 失败集中在 `mapIndexRunItem` 形状变更、`buildRunIndexRuns` block 不存在断言、BuildStepIndex 渲染相关字符串匹配。

- [ ] **Step 2: 给 mapIndexRunItem 补一个过滤场景测试**

在 app-shell.test.js 内合适位置（同类 `mapIndexRunItem` 已有断言旁）追加：

```javascript
test('mapIndexRunItem 暴露 buildRunId / status / startedAt', () => {
  const item = mapIndexRunItem({
    id: 7,
    buildRunId: 42,
    status: 'running',
    startedAt: '2026-05-18T10:00:00',
    createdAt: '2026-05-18T09:59:50',
  })
  assert.equal(item.id, 7)
  assert.equal(item.buildRunId, 42)
  assert.equal(item.status, 'running')
  assert.equal(item.startedAt, '2026-05-18T10:00:00')
  assert.equal(item.meta, 'running')
})
```

确认顶部已 import `mapIndexRunItem`；若没有，按需补。

- [ ] **Step 3: 修复其它失败项**

依次按 Step 1 输出修：
1. 任何 `assert.equal(blocks.indexRuns, ...)` 类断言保持原样不动，但若有断言 `blocks` 不含 `buildRunIndexRuns`，删除该负向断言。
2. BuildStepIndex.vue 源码匹配（如 `assert.match(buildStepIndexSource, /xxx/)`）按本计划新内容更新预期片段，或干脆放宽到 `/开始构建索引/`、`/重新构建索引/`、`/重试索引构建/`。
3. 若有断言 `LONG_TASK_LIMITS.index.timeoutMs === 1800000` 之类，按 Task 6 的新值更新。

- [ ] **Step 4: 全部测试通过**

Run: `cd frontend/apps/admin-app && pnpm test`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/app-shell.test.js
git commit -m "test(frontend): 索引步骤改造相关断言更新"
```

---

### Task 12: 端到端验证

- [ ] **Step 1: 后端干净编译 + 测试**

Run: `cd backend/ckqa-back && ./mvnw -q clean test 2>&1 | tail -20`
Expected: BUILD SUCCESS, 0 failures。

- [ ] **Step 2: 前端构建 + 测试**

Run: `cd frontend/apps/admin-app && pnpm build && pnpm test`
Expected: `✓ built` 与全部 PASS。

- [ ] **Step 3: 手测——浏览器联调（按可触达条件）**

如果当前环境能起服务，按下列步骤过一次：

1. 起后端：`cd backend/ckqa-back && ./mvnw spring-boot:run`。
2. 起前端：`cd frontend/apps/admin-app && pnpm dev`。
3. 浏览器进入一个已确认提示词、未跑过索引的 build_run，跳到第 05 步：
   - 空态：能看到居中"开始构建索引"按钮，灰底卡片 + 渐变图标圆盘。
   - 点按钮：进入 running 视图，进度条与阶段列表出现；首条阶段在 ~5–10s 内变为"已完成"（load_input_documents 真实很快）。
   - 进度条文字始终是后端返回的整数百分比；已用时按秒推进。
   - 30 分钟以上仍能持续显示进度（不再误判失败）。
4. 跑完后页面自动跳转到 qa_check 步；回退到 05 步看到列表 + "重新构建索引"次级按钮，点击弹出确认弹窗。

如果环境跑不起来，跳到 Step 4。

- [ ] **Step 4: 若验证产生额外修复，补 commit**

```bash
git add -A
git commit -m "fix: 索引步骤改造端到端验证修复"
```

若无修复，跳过此步。

---

## 设计决策备忘

1. **过滤口径：** 只展示 `buildRunId === 当前 buildRun.id` 的索引运行，按 `startedAt` 倒排。本步骤是 build_run 隔离视图，混入 KB 历史只会让用户困惑。
2. **错误态传播：** `buildRunIndexRunsBlock` 继承 `indexRunsBlock` 的错误态（`error`/`loading`），不会把接口失败吞成 `empty`。
3. **真实进度来自后端：** 百分比、阶段名、子进度均由后端 `IndexProgressParser` 从 `process.log` 解析得到。前端只渲染、不重新计算。权重表如未来需要调整，单点修改 `IndexProgressParser.WEIGHTS`。
4. **运行时日志可读：** ProcessRunner 改成行级 tee（stdout/stderr 共用 ReentrantLock 同步写）。删除了进程退出后再写一遍的 `writeLogFile`，单写入路径，更容易推理。
5. **elapsedSeconds 基线（critical）：** 客户端按钮按下时间戳为主。**禁止**使用 `BuildRunDetailResponse.startedAt`——它是 buildRun 创建时间，远早于索引开始（参考 `markIndexStarted` 的 `if (startedAt == null)` 守卫）。刷新页面后客户端时间戳丢失时，elapsed 显示 0 秒；进度条仍能从 `indexProgress.percentage` 渲染正确百分比，已用时仅作为辅助。
6. **每秒 tick：** ModulePage 持有 1s setInterval（`elapsedTickToken`），保证两次 5s 轮询之间 elapsed/百分比 computed 也能刷新。任务结束清掉，组件卸载时清掉。
7. **长任务超时扩容（critical）：** `LONG_TASK_LIMITS.index` 调整为 5s 轮询、130min 超时。30min 旧值会在索引未结束时误报失败。
8. **后端重建可重入性：** `createBuildRunIndexRun` 的前置校验只有 `assertPromptConfirmed` 和 KB 维度 `findActiveRunningByKnowledgeBaseId`。已完成/失败的 build_run 可重新触发；同 KB 下另一个 buildRun 正在跑会 409，前端 ElMessageBox 确认后失败时 UI 会显示后端错误信息。
9. **按钮状态机对齐：** 空态主按钮 `disabled = step?.status !== 'ready' || actionRunning`；重建次级按钮 `disabled = !['done','failed'].includes(step?.status) || actionRunning`。与 `runKnowledgeBaseIndex` 守卫完全一致，不存在"能点没反应"。
10. **空态文案：** "本次构建尚未触发索引运行" + 阶段说明 + 1–2h 预估；居中大按钮 + 渐变图标圆盘视觉对齐 ckqa-* 设计 token。
11. **索引 startedAt 为 IndexRun 实体的 startedAt：** [IndexRuns.java](backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexRuns.java#L45-L46) 实体已有 `buildRunId`，Task 1 仅扩 DTO；后端日志解析不依赖 startedAt，仅作为前端列表展示用。
12. **stderr 输出保留：** ProcessRunner 改造保留了 `getStderr()` 的语义（错误总结仍走 `defaultErrorSummary(indexResult.getStderr(), ...)`）；解析器只读 `[stdout] ` 行，错误诊断和进度解析分别独立。
