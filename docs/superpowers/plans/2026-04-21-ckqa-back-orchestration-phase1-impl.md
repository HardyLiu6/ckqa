# CKQA Back Orchestration Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `backend/ckqa-back` 在不重写 Python 主链路的前提下，提供一期可用的统一编排 API，打通 PDF 解析、GraphRAG 导出、索引构建、问答代理与系统健康检查。

**Architecture:** 保持 `pdf_ingest` 与 `graphrag_pipeline` 的现有入口不变，在 Java 后端增加“业务工作流服务 + 集成适配层”两层：工作流服务负责资源校验、状态流转、幂等与日志语义；集成适配层负责外部进程执行、GraphRAG HTTP 调用、数据库锁和陈旧任务恢复。底层表仍使用 MyBatis-Plus 生成的实体与 ServiceImpl，避免大改现有骨架。

**Tech Stack:** Java 21, Spring Boot 4.0.5, MyBatis-Plus 3.5.16, Spring MVC, Jackson, Maven, JUnit 5, Mockito, MockMvc

---

## 文件结构与职责

### 共享集成基础设施

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
  负责绑定 `ckqa.integration.*` 配置，包括 Python 路径、项目根目录、GraphRAG API 地址和超时阈值。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessContext.java`
  负责描述当前子进程的业务上下文，例如操作类型、`pdfFileId`、`indexRunId`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessExecutionResult.java`
  负责封装子进程执行结果：命令、退出码、耗时、stdout、stderr、超时标记。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java`
  负责同步执行外部命令、跟踪活跃子进程、在 Bean 销毁时清理。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/locks/DatabaseNamedLockService.java`
  负责通过 MySQL `GET_LOCK` / `RELEASE_LOCK` 为 `export-graphrag` 提供数据库级互斥。

### PDF 编排

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java`
  负责组装 `mineru_parser.py parse` 与 `mineru_parser.py export-graphrag` 命令。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java`
  负责 PDF 详情、解析触发、导出触发、导出互斥、导出幂等判断。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfOperationResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ExportGraphRagRequest.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PdfFilesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PdfFilesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PdfFilesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/ParseResultsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/ParseResultsServiceImpl.java`

### 索引编排与恢复

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunMetadata.java`
  负责固定 `run_metadata` 的核心字段结构。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagIndexOrchestrator.java`
  负责执行 `fetch_from_minio.py` 与 `python -m graphrag index --root .`。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunStartupRecovery.java`
  负责应用启动时扫描并恢复超时未完成的 `running` 记录。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java`
  负责索引任务创建、陈旧任务恢复、状态回写、激活索引更新。
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/IndexRunsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/KnowledgeBasesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/KnowledgeBasesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/IndexRunsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/IndexRunsServiceImpl.java`
- Modify: `pdf_ingest/sql/ocqa.sql`

### 课程入口与健康检查

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/KnowledgeBaseSummaryResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthController.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/dto/SystemHealthItemResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/dto/SystemHealthResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java`

### 问答代理

- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/CreateQaSessionRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/CreateQaMessageRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaSessionResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaSessionsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaSessionsServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaMessagesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaMessagesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java`

### 配置、错误码与文档

- Modify: `backend/ckqa-back/pom.xml`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- Modify: `backend/ckqa-back/src/main/resources/application.properties`
- Modify: `backend/ckqa-back/.env.example`
- Modify: `backend/ckqa-back/README.md`

### 测试文件

- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/IndexRunsControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBasesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/system/SystemHealthServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/system/SystemHealthControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClientTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java`

### Task 1: 搭建共享集成基础设施

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java`
- Modify: `backend/ckqa-back/pom.xml`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessContext.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessExecutionResult.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/locks/DatabaseNamedLockService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- Modify: `backend/ckqa-back/src/main/resources/application.properties`
- Modify: `backend/ckqa-back/.env.example`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.integration.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    void shouldTerminateTrackedProcessWhenRunnerDestroyed() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Future<ProcessExecutionResult> future = Executors.newSingleThreadExecutor().submit(() ->
                runner.run(
                        java.util.List.of("bash", "-lc", "sleep 30"),
                        Path.of("."),
                        Map.of(),
                        Duration.ofSeconds(40),
                        ProcessContext.builder().operation("index").indexRunId(9L).build()
                )
        );

        while (runner.activeProcessCount() == 0) {
            Thread.sleep(20L);
        }

        runner.destroy();
        ProcessExecutionResult result = future.get();

        assertThat(result.isTerminatedByShutdown()).isTrue();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(runner.activeProcessCount()).isZero();
    }

    @Test
    void shouldDrainStdoutAndStderrWithoutDeadlock() throws Exception {
        ProcessRunner runner = new ProcessRunner();

        ProcessExecutionResult result = runner.run(
                java.util.List.of(
                        "bash",
                        "-lc",
                        "for i in $(seq 1 5000); do echo out-$i; echo err-$i 1>&2; done"
                ),
                Path.of("."),
                Map.of(),
                Duration.ofSeconds(10),
                ProcessContext.builder().operation("probe").build()
        );

        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getStdout()).contains("out-5000");
        assertThat(result.getStderr()).contains("err-5000");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=ProcessRunnerTest test`

Expected: FAIL，因为 `ProcessRunner`、`ProcessExecutionResult` 与 `ProcessContext` 还不存在。

- [ ] **Step 3: 写最小实现**

先加配置绑定与运行时基础设施：

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.integration")
public class CkqaIntegrationProperties {

    private final PythonProcessProperties pdfIngest = new PythonProcessProperties();
    private final GraphRagProperties graphrag = new GraphRagProperties();
    private final TimeoutProperties timeout = new TimeoutProperties();

    @Getter
    @Setter
    public static class PythonProcessProperties {
        private String python;
        private String root;
    }

    @Getter
    @Setter
    public static class GraphRagProperties extends PythonProcessProperties {
        private String apiBaseUrl;
    }

    @Getter
    @Setter
    public static class TimeoutProperties {
        private long parseSeconds = 900L;
        private long exportSeconds = 300L;
        private long fetchSeconds = 300L;
        private long indexSeconds = 1800L;
        private long querySeconds = 120L;
        private long indexStaleSeconds = 2400L;
    }
}
```

```java
@Getter
@Builder
public class ProcessContext {
    private final String operation;
    private final Long pdfFileId;
    private final Long indexRunId;
}
```

```java
@Getter
@Builder
public class ProcessExecutionResult {
    private final List<String> command;
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final long elapsedSeconds;
    private final boolean timedOut;
    private final boolean terminatedByShutdown;
}
```

```java
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

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        activeProcesses.put(key, process);
        Future<String> stdoutFuture = streamReaderExecutor.submit(() ->
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        Future<String> stderrFuture = streamReaderExecutor.submit(() ->
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

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
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
        streamReaderExecutor.shutdownNow();
    }
}
```

在 `CkqaBackApplication` 上启用配置：

```java
@EnableConfigurationProperties(CkqaIntegrationProperties.class)
@SpringBootApplication
public class CkqaBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CkqaBackApplication.class, args);
    }
}
```

`application.properties` 追加：

```properties
ckqa.integration.pdf-ingest.python=${PDF_INGEST_PYTHON:}
ckqa.integration.pdf-ingest.root=${PDF_INGEST_ROOT:}
ckqa.integration.graphrag.python=${GRAPHRAG_PYTHON:}
ckqa.integration.graphrag.root=${GRAPHRAG_ROOT:}
ckqa.integration.graphrag.api-base-url=${GRAPHRAG_API_BASE_URL:http://127.0.0.1:8012}
ckqa.integration.timeout.index-stale-seconds=${INDEX_STALE_SECONDS:2400}
```

`.env.example` 追加：

```bash
PDF_INGEST_PYTHON=/path/to/courseKg/bin/python
PDF_INGEST_ROOT=/home/sunlight/Projects/ckqa/pdf_ingest
GRAPHRAG_PYTHON=/path/to/graphrag-oneapi/bin/python
GRAPHRAG_ROOT=/home/sunlight/Projects/ckqa/graphrag_pipeline
GRAPHRAG_API_BASE_URL=http://127.0.0.1:8012
INDEX_STALE_SECONDS=2400
```

数据库命名锁服务最小实现：

```java
@Service
@RequiredArgsConstructor
public class DatabaseNamedLockService {

    private final JdbcTemplate jdbcTemplate;

    public boolean acquire(String lockName, int timeoutSeconds) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName,
                timeoutSeconds
        );
        return Integer.valueOf(1).equals(value);
    }

    public void release(String lockName) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
    }
}
```

`pom.xml` 在 Task 1 一并调整，按当前仓库的 Spring Boot 4 基线处理，不直接照搬 Boot 3 命名：

1. 保留现有 `spring-boot-starter-webmvc`
2. 显式增加 `spring-boot-starter-jdbc`，为 `JdbcTemplate` 与命名锁提供稳定依赖来源
3. 保留现有 `spring-boot-starter-validation`
4. 保留现有 `mybatis-plus-spring-boot4-starter`
5. 保留现有 `mysql-connector-j`、`lombok`、`spring-boot-starter-test`
6. 增加 `spring-boot-configuration-processor` 作为可选注解处理器支持
7. 一期默认不引入 `spring-boot-starter-actuator`
8. 一期不额外显式声明 `jackson-databind`，因为 `spring-boot-starter-webmvc` 已提供 JSON 序列化支持

`ProcessRunner` 的类级 Javadoc 还需要补一条实现说明：应用正常停机过程中，子进程输出读取线程可能被中断，`run()` 侧出现“读取进程输出失败”属于可接受的停机路径表现，不作为业务失败语义解读。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=ProcessRunnerTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add pom.xml \
  src/main/java/org/ysu/ckqaback/CkqaBackApplication.java \
  src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java \
  src/main/java/org/ysu/ckqaback/integration/process/ProcessContext.java \
  src/main/java/org/ysu/ckqaback/integration/process/ProcessExecutionResult.java \
  src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java \
  src/main/java/org/ysu/ckqaback/integration/locks/DatabaseNamedLockService.java \
  src/main/resources/application.properties \
  .env.example \
  src/test/java/org/ysu/ckqaback/integration/process/ProcessRunnerTest.java
git commit -m "feat: add orchestration integration foundation"
```

### Task 2: 实现 PDF 查询与解析触发

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/PdfOperationResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PdfFilesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PdfFilesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PdfFilesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/ParseResultsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/ParseResultsServiceImpl.java`

- [ ] **Step 1: 写失败测试**

```java
class PdfWorkflowServiceTest {

    @Test
    void shouldRejectParseWhenClaimParseStartFails() {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");
        pdfFile.setParseStatus("processing");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(false);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PDF当前状态不允许再次触发解析");
    }

    @Test
    void shouldFallbackToFailedWhenParseProcessReturnsError() throws Exception {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");
        pdfFile.setParseStatus("pending");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile, pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(true);
        given(orchestrator.parse(pdfFile)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(1)
                .stdout("")
                .stderr("spawn failed")
                .elapsedSeconds(1L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build());

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF解析执行失败");

        then(pdfFilesService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    @Test
    void shouldFallbackToFailedWhenParseCommandThrowsException() throws Exception {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(true);
        willThrow(new IOException("spawn failed")).given(orchestrator).parse(pdfFile);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spawn failed");

        then(pdfFilesService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }
}
```

```java
class PdfFilesControllerWebMvcTest {

    @Test
    void shouldReturnPdfDetail() throws Exception {
        PdfFileResponse response = PdfFileResponse.of(7L, "os", "book.pdf", "done", null, null, null);
        given(pdfWorkflowService.getPdfFile(7L)).willReturn(response);

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.parseStatus").value("done"));
    }

    @Test
    void shouldTriggerParse() throws Exception {
        PdfOperationResponse response = PdfOperationResponse.success(7L, "os", "book.pdf", "processing", "解析任务已启动");
        given(pdfWorkflowService.startParse(7L)).willReturn(response);

        mockMvc.perform(post(ApiPaths.PDF_FILES + "/7/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("解析任务已启动"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=PdfWorkflowServiceTest,PdfFilesControllerWebMvcTest test`

Expected: FAIL，因为 `PdfWorkflowService`、DTO 和 `PdfFilesController` 方法都还不存在。

- [ ] **Step 3: 写最小实现**

新增错误码：

```java
PDF_FILE_NOT_FOUND(4045, "PDF文件不存在"),
PDF_PARSE_STATE_CONFLICT(4093, "PDF当前状态不允许再次触发解析"),
PDF_PARSE_EXECUTION_FAILED(5003, "PDF解析执行失败");
```

`PdfFilesService` 增加方法：

```java
PdfFiles getRequiredById(Long id);

boolean claimParseStart(Long id);

boolean markParseFailedIfStillProcessing(Long id, String errorMessage);

List<PdfFiles> listByCourseId(String courseId);
```

`PdfFilesServiceImpl` 中用原子更新抢占解析：

```java
@Override
public boolean claimParseStart(Long id) {
    LambdaUpdateWrapper<PdfFiles> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(PdfFiles::getId, id)
            .in(PdfFiles::getParseStatus, "pending", "failed")
            .set(PdfFiles::getParseStatus, "processing")
            .setSql("parse_started_at = NOW(), parse_finished_at = NULL, parse_error_msg = NULL");
    return baseMapper.update(null, wrapper) == 1;
}

@Override
public boolean markParseFailedIfStillProcessing(Long id, String errorMessage) {
    LambdaUpdateWrapper<PdfFiles> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(PdfFiles::getId, id)
            .eq(PdfFiles::getParseStatus, "processing")
            .set(PdfFiles::getParseStatus, "failed")
            .setSql("parse_finished_at = NOW()")
            .set(PdfFiles::getParseErrorMsg, errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
    return baseMapper.update(null, wrapper) == 1;
}
```

`PdfIngestOrchestrator`：

```java
@Service
@RequiredArgsConstructor
public class PdfIngestOrchestrator {

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    public ProcessExecutionResult parse(PdfFiles pdfFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.getPdfIngest().getPython(),
                "scripts/pdf_processor/mineru_parser.py",
                "parse",
                pdfFile.getCourseId(),
                "--file-id",
                String.valueOf(pdfFile.getId())
        );
        return processRunner.run(
                command,
                Path.of(properties.getPdfIngest().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getParseSeconds()),
                ProcessContext.builder().operation("parse").pdfFileId(pdfFile.getId()).build()
        );
    }
}
```

`PdfWorkflowService`：

```java
@Service
@RequiredArgsConstructor
public class PdfWorkflowService {

    private final PdfFilesService pdfFilesService;
    private final ParseResultsService parseResultsService;
    private final PdfIngestOrchestrator pdfIngestOrchestrator;

    public PdfFileResponse getPdfFile(Long id) {
        return PdfFileResponse.fromEntity(pdfFilesService.getRequiredById(id));
    }

    public List<ParseResultResponse> listParseResults(Long pdfFileId) {
        return parseResultsService.listByPdfFileId(pdfFileId).stream()
                .map(ParseResultResponse::fromEntity)
                .toList();
    }

    public PdfOperationResponse startParse(Long pdfFileId) throws IOException, InterruptedException {
        PdfFiles pdfFile = pdfFilesService.getRequiredById(pdfFileId);
        if (!pdfFilesService.claimParseStart(pdfFileId)) {
            throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT);
        }
        ProcessExecutionResult result;
        try {
            result = pdfIngestOrchestrator.parse(pdfFile);
        } catch (IOException ex) {
            pdfFilesService.markParseFailedIfStillProcessing(pdfFileId, ex.getMessage());
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pdfFilesService.markParseFailedIfStillProcessing(pdfFileId, "解析任务被中断");
            throw ex;
        }
        if (result.isTimedOut() || result.getExitCode() != 0) {
            pdfFilesService.markParseFailedIfStillProcessing(
                    pdfFileId,
                    result.isTimedOut() ? "解析命令执行超时" : result.getStderr()
            );
            throw new BusinessException(
                    ApiResultCode.PDF_PARSE_EXECUTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF解析执行失败"
            );
        }
        PdfFiles refreshed = pdfFilesService.getRequiredById(pdfFileId);
        return PdfOperationResponse.success(
                refreshed.getId(),
                refreshed.getCourseId(),
                refreshed.getFileName(),
                refreshed.getParseStatus(),
                "解析任务已启动"
        );
    }
}
```

这里要明确一期兜底策略：如果 Python 进程已正常启动并自行写回 `failed`，Java 的条件更新不会覆盖它；只有在 Java 侧提前失败、超时或非零退出且数据库仍停留在 `processing` 时，Java 才负责补写失败状态，避免产生僵尸 `processing`。

`PdfFilesController` 最小方法：

```java
@RequiredArgsConstructor
@RestController
@RequestMapping(ApiPaths.PDF_FILES)
public class PdfFilesController {

    private final PdfWorkflowService pdfWorkflowService;

    @GetMapping("/{id}")
    public ApiResponse<PdfFileResponse> getPdfFile(@PathVariable @Positive Long id) {
        return ApiResponseUtils.success(pdfWorkflowService.getPdfFile(id));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<List<ParseResultResponse>> listParseResults(@PathVariable @Positive Long id) {
        return ApiResponseUtils.success(pdfWorkflowService.listParseResults(id));
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<PdfOperationResponse> parse(@PathVariable @Positive Long id) throws IOException, InterruptedException {
        return ApiResponseUtils.success(pdfWorkflowService.startParse(id));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=PdfWorkflowServiceTest,PdfFilesControllerWebMvcTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add src/main/java/org/ysu/ckqaback/api/ApiResultCode.java \
  src/main/java/org/ysu/ckqaback/controller/PdfFilesController.java \
  src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java \
  src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java \
  src/main/java/org/ysu/ckqaback/pdf/dto/PdfFileResponse.java \
  src/main/java/org/ysu/ckqaback/pdf/dto/ParseResultResponse.java \
  src/main/java/org/ysu/ckqaback/pdf/dto/PdfOperationResponse.java \
  src/main/java/org/ysu/ckqaback/service/PdfFilesService.java \
  src/main/java/org/ysu/ckqaback/service/impl/PdfFilesServiceImpl.java \
  src/main/java/org/ysu/ckqaback/service/ParseResultsService.java \
  src/main/java/org/ysu/ckqaback/service/impl/ParseResultsServiceImpl.java \
  src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java \
  src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java
git commit -m "feat: add pdf parse orchestration endpoints"
```

### Task 3: 实现 GraphRAG 导出接口与导出互斥

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PdfFilesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/ParseResultsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/ParseResultsServiceImpl.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/pdf/dto/ExportGraphRagRequest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void shouldRejectExportWhenNamedLockNotAcquired() {
    PdfFiles pdfFile = new PdfFiles();
    pdfFile.setId(9L);
    pdfFile.setCourseId("os");
    pdfFile.setFileName("slides.pdf");
    pdfFile.setParseStatus("done");

    given(pdfFilesService.getRequiredById(9L)).willReturn(pdfFile);
    given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(false);

    ExportGraphRagRequest request = new ExportGraphRagRequest("section", false, false);

    assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("当前已有导出任务在执行");
}
```

```java
@Test
void shouldTriggerExport() throws Exception {
    PdfOperationResponse response = PdfOperationResponse.success(9L, "os", "slides.pdf", "done", "GraphRAG导出完成");
    given(pdfWorkflowService.exportGraphRag(eq(9L), any())).willReturn(response);

    mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "mode": "section",
                              "withPageDocs": true,
                              "force": false
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.message").value("GraphRAG导出完成"));
}

@Test
void shouldDefaultModeToSectionWhenModeMissing() throws Exception {
    PdfOperationResponse response = PdfOperationResponse.success(9L, "os", "slides.pdf", "done", "已存在完整导出结果");
    given(pdfWorkflowService.exportGraphRag(eq(9L), any())).willReturn(response);

    mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "withPageDocs": false,
                              "force": false
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.message").value("已存在完整导出结果"));

    then(pdfWorkflowService).should().exportGraphRag(eq(9L), argThat(req -> "section".equals(req.getMode())));
}

@Test
void shouldRejectNullModeWhenExportRequestContainsNullMode() throws Exception {
    mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "mode": null,
                              "withPageDocs": false,
                              "force": false
                            }
                            """))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=PdfWorkflowServiceTest,PdfFilesControllerWebMvcTest test`

Expected: FAIL，因为导出请求 DTO、导出接口和命名锁逻辑还不存在。

- [ ] **Step 3: 写最小实现**

导出请求 DTO：

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExportGraphRagRequest {

    @NotBlank
    private String mode = "section";

    private boolean withPageDocs;

    private boolean force;
}
```

这里需要在实现备注中明确 Jackson 语义：

1. 请求体完全省略 `mode` 时，字段初始化默认值 `"section"` 生效。
2. 请求体显式传 `"mode": null` 时，Jackson 会把字段设为 `null`，随后由 `@NotBlank` 触发校验失败。

因此这两种情况都要写测试，避免联调时对“默认值是否生效”产生误解。

`ParseResultsService` 增加完整导出判断：

```java
List<ParseResults> listGraphRagOutputs(Long pdfFileId);

boolean hasCompleteGraphRagExport(Long pdfFileId, String mode, boolean withPageDocs);
```

核心判断逻辑：

```java
@Override
public boolean hasCompleteGraphRagExport(Long pdfFileId, String mode, boolean withPageDocs) {
    Set<String> existingNames = listGraphRagOutputs(pdfFileId).stream()
            .map(ParseResults::getFileName)
            .collect(Collectors.toSet());

    Set<String> expected = new LinkedHashSet<>();
    expected.add("graphrag_normalized_docs.json");
    expected.add("page".equals(mode) ? "graphrag_page_docs.json" : "graphrag_section_docs.json");
    if (withPageDocs) {
        expected.add("graphrag_page_docs.json");
    }
    return existingNames.containsAll(expected);
}
```

`PdfIngestOrchestrator` 追加导出方法：

```java
public ProcessExecutionResult exportGraphRag(PdfFiles pdfFile, ExportGraphRagRequest request)
        throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of(
            properties.getPdfIngest().getPython(),
            "scripts/pdf_processor/mineru_parser.py",
            "export-graphrag",
            pdfFile.getCourseId(),
            "--file-id",
            String.valueOf(pdfFile.getId()),
            "--mode",
            request.getMode()
    ));
    if (request.isWithPageDocs()) {
        command.add("--with-page-docs");
    }
    if (request.isForce()) {
        command.add("--force");
    }
    return processRunner.run(
            command,
            Path.of(properties.getPdfIngest().getRoot()),
            Map.of(),
            Duration.ofSeconds(properties.getTimeout().getExportSeconds()),
            ProcessContext.builder().operation("export-graphrag").pdfFileId(pdfFile.getId()).build()
    );
}
```

`PdfWorkflowService`：

```java
public PdfOperationResponse exportGraphRag(Long pdfFileId, ExportGraphRagRequest request)
        throws IOException, InterruptedException {
    PdfFiles pdfFile = pdfFilesService.getRequiredById(pdfFileId);
    if (!"done".equals(pdfFile.getParseStatus())) {
        throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT, "PDF解析完成后才能导出GraphRAG输入");
    }

    String lockName = "pdf-export:" + pdfFileId;
    if (!databaseNamedLockService.acquire(lockName, 1)) {
        throw new BusinessException(ApiResultCode.PDF_EXPORT_LOCKED, HttpStatus.CONFLICT, "当前已有导出任务在执行");
    }
    try {
        if (!request.isForce() && parseResultsService.hasCompleteGraphRagExport(pdfFileId, request.getMode(), request.isWithPageDocs())) {
            return PdfOperationResponse.success(pdfFileId, pdfFile.getCourseId(), pdfFile.getFileName(), pdfFile.getParseStatus(), "已存在完整导出结果");
        }
        pdfIngestOrchestrator.exportGraphRag(pdfFile, request);
        return PdfOperationResponse.success(pdfFileId, pdfFile.getCourseId(), pdfFile.getFileName(), pdfFile.getParseStatus(), "GraphRAG导出完成");
    } finally {
        databaseNamedLockService.release(lockName);
    }
}
```

控制器追加：

```java
@PostMapping("/{id}/export-graphrag")
public ApiResponse<PdfOperationResponse> exportGraphRag(
        @PathVariable @Positive Long id,
        @Valid @RequestBody ExportGraphRagRequest request
) throws IOException, InterruptedException {
    return ApiResponseUtils.success(pdfWorkflowService.exportGraphRag(id, request));
}
```

新增错误码：

```java
PDF_EXPORT_LOCKED(4094, "当前已有导出任务在执行");
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=PdfWorkflowServiceTest,PdfFilesControllerWebMvcTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add src/main/java/org/ysu/ckqaback/pdf/PdfWorkflowService.java \
  src/main/java/org/ysu/ckqaback/pdf/dto/ExportGraphRagRequest.java \
  src/main/java/org/ysu/ckqaback/integration/pdf/PdfIngestOrchestrator.java \
  src/main/java/org/ysu/ckqaback/controller/PdfFilesController.java \
  src/main/java/org/ysu/ckqaback/service/ParseResultsService.java \
  src/main/java/org/ysu/ckqaback/service/impl/ParseResultsServiceImpl.java \
  src/main/java/org/ysu/ckqaback/api/ApiResultCode.java \
  src/test/java/org/ysu/ckqaback/pdf/PdfWorkflowServiceTest.java \
  src/test/java/org/ysu/ckqaback/controller/PdfFilesControllerWebMvcTest.java
git commit -m "feat: add graphrag export orchestration"
```

### Task 4: 实现索引任务、陈旧任务恢复与索引元数据

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/IndexRunsControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBasesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunMetadata.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagIndexOrchestrator.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunStartupRecovery.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/IndexRunsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/KnowledgeBasesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/KnowledgeBasesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/IndexRunsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/IndexRunsServiceImpl.java`
- Modify: `pdf_ingest/sql/ocqa.sql`

- [ ] **Step 1: 写失败测试**

```java
class IndexWorkflowServiceTest {

    @Test
    void shouldRecoverStaleRunningRunBeforeCreatingNewOne() {
        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        GraphRagIndexOrchestrator orchestrator = mock(GraphRagIndexOrchestrator.class);

        IndexWorkflowService workflowService = new IndexWorkflowService(indexRunsService, knowledgeBasesService, orchestrator, new ObjectMapper(), Duration.ofSeconds(2400));

        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(5L);
        kb.setCourseId("os");
        kb.setName("操作系统");

        IndexRuns stale = new IndexRuns();
        stale.setId(12L);
        stale.setKnowledgeBaseId(5L);
        stale.setStatus("running");

        given(knowledgeBasesService.getRequiredById(5L)).willReturn(kb);
        given(indexRunsService.recoverStaleRunningRuns(5L, Duration.ofSeconds(2400))).willReturn(List.of(stale));
        given(indexRunsService.findActiveRunningByKnowledgeBaseId(5L)).willReturn(Optional.empty());

        workflowService.createIndexRun(5L);

        then(indexRunsService).should().recoverStaleRunningRuns(5L, Duration.ofSeconds(2400));
    }

    @Test
    void shouldFailRunWhenFetchInputReturnsNonZeroExitCode() throws Exception {
        IndexRunsService indexRunsService = mock(IndexRunsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        GraphRagIndexOrchestrator orchestrator = mock(GraphRagIndexOrchestrator.class);

        IndexWorkflowService workflowService = new IndexWorkflowService(indexRunsService, knowledgeBasesService, orchestrator, new ObjectMapper(), Duration.ofSeconds(2400));

        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(5L);
        kb.setCourseId("os");

        IndexRuns run = new IndexRuns();
        run.setId(18L);
        run.setKnowledgeBaseId(5L);

        given(knowledgeBasesService.getRequiredById(5L)).willReturn(kb);
        given(indexRunsService.recoverStaleRunningRuns(5L, Duration.ofSeconds(2400))).willReturn(List.of());
        given(indexRunsService.findActiveRunningByKnowledgeBaseId(5L)).willReturn(Optional.empty());
        given(indexRunsService.createPendingRun(eq(5L), anyString())).willReturn(run);
        given(orchestrator.fetchInput(run, kb)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "utils/fetch_from_minio.py"))
                .exitCode(2)
                .stdout("")
                .stderr("minio fetch failed")
                .elapsedSeconds(3L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build());

        assertThatThrownBy(() -> workflowService.createIndexRun(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("索引输入拉取失败");

        then(orchestrator).should(never()).runIndex(any());
        then(indexRunsService).should().markFailed(eq(18L), contains("minio fetch failed"));
    }
}
```

```java
class IndexRunsControllerWebMvcTest {

    @Test
    void shouldCreateIndexRun() throws Exception {
        IndexRunResponse response = IndexRunResponse.of(18L, 5L, "graphrag", "graphrag-20260421153000", "running", null, null, "{}");
        given(indexWorkflowService.createIndexRun(5L)).willReturn(response);

        mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASES + "/5/index-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.status").value("running"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=IndexWorkflowServiceTest,IndexRunsControllerWebMvcTest,KnowledgeBasesControllerWebMvcTest test`

Expected: FAIL，因为索引工作流、Controller 方法和 DTO 尚不存在。

- [ ] **Step 3: 写最小实现**

固定索引版本与元数据：

```java
@Getter
@Builder
public class IndexRunMetadata {
    private final String command;
    private final Long elapsedSeconds;
    private final Integer exitCode;
    private final String errorSummary;
    private final Boolean staleTimeoutRecovered;
}
```

`IndexRunsService` 增加方法：

```java
IndexRuns getRequiredById(Long id);

Optional<IndexRuns> findActiveRunningByKnowledgeBaseId(Long knowledgeBaseId);

List<IndexRuns> listByKnowledgeBaseId(Long knowledgeBaseId);

List<IndexRuns> recoverStaleRunningRuns(Long knowledgeBaseId, Duration staleThreshold);

List<IndexRuns> recoverStaleRunningRuns(Duration staleThreshold);

IndexRuns createPendingRun(Long knowledgeBaseId, String indexVersion);

void markRunning(Long id);

void markSuccess(Long id, String metadataJson);

void markFailed(Long id, String metadataJson);
```

`IndexRunsServiceImpl` 关键逻辑：

```java
@Override
public Optional<IndexRuns> findActiveRunningByKnowledgeBaseId(Long knowledgeBaseId) {
    return Optional.ofNullable(
            lambdaQuery()
                    .eq(IndexRuns::getKnowledgeBaseId, knowledgeBaseId)
                    .eq(IndexRuns::getStatus, "running")
                    .last("LIMIT 1")
                    .one()
    );
}

@Override
public List<IndexRuns> recoverStaleRunningRuns(Long knowledgeBaseId, Duration staleThreshold) {
    LocalDateTime deadline = LocalDateTime.now().minusSeconds(staleThreshold.toSeconds());
    List<IndexRuns> staleRuns = lambdaQuery()
            .eq(IndexRuns::getKnowledgeBaseId, knowledgeBaseId)
            .eq(IndexRuns::getStatus, "running")
            .lt(IndexRuns::getStartedAt, deadline)
            .list();
    staleRuns.forEach(run -> markFailed(run.getId(), staleMetadataJson()));
    return staleRuns;
}

@Override
public List<IndexRuns> recoverStaleRunningRuns(Duration staleThreshold) {
    LocalDateTime deadline = LocalDateTime.now().minusSeconds(staleThreshold.toSeconds());
    List<IndexRuns> staleRuns = lambdaQuery()
            .eq(IndexRuns::getStatus, "running")
            .lt(IndexRuns::getStartedAt, deadline)
            .list();
    staleRuns.forEach(run -> markFailed(run.getId(), staleMetadataJson()));
    return staleRuns;
}
```

`GraphRagIndexOrchestrator`：

```java
@Service
@RequiredArgsConstructor
public class GraphRagIndexOrchestrator {

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    public ProcessExecutionResult fetchInput(IndexRuns run, KnowledgeBases knowledgeBase)
            throws IOException, InterruptedException {
        Path root = Path.of(properties.getGraphrag().getRoot());

        return processRunner.run(
                List.of(
                        properties.getGraphrag().getPython(),
                        "utils/fetch_from_minio.py",
                        knowledgeBase.getCourseId(),
                        "--clean"
                ),
                root,
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getFetchSeconds()),
                ProcessContext.builder().operation("fetch-input").indexRunId(run.getId()).build()
        );
    }

    public ProcessExecutionResult runIndex(IndexRuns run) throws IOException, InterruptedException {
        Path root = Path.of(properties.getGraphrag().getRoot());
        return processRunner.run(
                List.of(
                        properties.getGraphrag().getPython(),
                        "-m",
                        "graphrag",
                        "index",
                        "--root",
                        "."
                ),
                root,
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getIndexSeconds()),
                ProcessContext.builder().operation("index").indexRunId(run.getId()).build()
        );
    }
}
```

`IndexWorkflowService` 关键方法：

```java
public IndexRunResponse createIndexRun(Long knowledgeBaseId) throws IOException, InterruptedException {
    KnowledgeBases kb = knowledgeBasesService.getRequiredById(knowledgeBaseId);
    indexRunsService.recoverStaleRunningRuns(knowledgeBaseId, staleThreshold);

    if (indexRunsService.findActiveRunningByKnowledgeBaseId(knowledgeBaseId).isPresent()) {
        throw new BusinessException(ApiResultCode.INDEX_RUN_ALREADY_RUNNING, HttpStatus.CONFLICT);
    }

    String indexVersion = "graphrag-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
    IndexRuns run = indexRunsService.createPendingRun(knowledgeBaseId, indexVersion);
    indexRunsService.markRunning(run.getId());

    try {
        ProcessExecutionResult fetchResult = graphRagIndexOrchestrator.fetchInput(run, kb);
        if (fetchResult.isTimedOut() || fetchResult.getExitCode() != 0) {
            indexRunsService.markFailed(run.getId(), objectMapper.writeValueAsString(IndexRunMetadata.builder()
                    .command("python utils/fetch_from_minio.py")
                    .elapsedSeconds(fetchResult.getElapsedSeconds())
                    .exitCode(fetchResult.getExitCode())
                    .errorSummary(fetchResult.isTimedOut() ? "索引输入拉取超时" : fetchResult.getStderr())
                    .staleTimeoutRecovered(false)
                    .build()));
            throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引输入拉取失败");
        }

        ProcessExecutionResult indexResult = graphRagIndexOrchestrator.runIndex(run);
        if (indexResult.isTimedOut() || indexResult.getExitCode() != 0) {
            indexRunsService.markFailed(run.getId(), objectMapper.writeValueAsString(IndexRunMetadata.builder()
                    .command("python -m graphrag index --root .")
                    .elapsedSeconds(indexResult.getElapsedSeconds())
                    .exitCode(indexResult.getExitCode())
                    .errorSummary(indexResult.isTimedOut() ? "索引命令执行超时" : indexResult.getStderr())
                    .staleTimeoutRecovered(false)
                    .build()));
            throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引构建失败");
        }

        String metadata = objectMapper.writeValueAsString(IndexRunMetadata.builder()
                .command("python -m graphrag index --root .")
                .elapsedSeconds(indexResult.getElapsedSeconds())
                .exitCode(indexResult.getExitCode())
                .errorSummary(null)
                .staleTimeoutRecovered(false)
                .build());
        indexRunsService.markSuccess(run.getId(), metadata);
        knowledgeBasesService.updateActiveIndexRunId(knowledgeBaseId, run.getId());
        return IndexRunResponse.fromEntity(indexRunsService.getRequiredById(run.getId()));
    } catch (Exception ex) {
        if (!"failed".equals(indexRunsService.getRequiredById(run.getId()).getStatus())) {
            indexRunsService.markFailed(run.getId(), failedMetadataJson(ex.getMessage()));
        }
        throw ex;
    }
}
```

这里需要强调实现意图：`fetch_from_minio` 和 `graphrag index` 在一期不能被包装成一个“无论前一步是否成功都继续跑”的黑盒方法。输入拉取失败时必须立刻终止流程并写回失败元数据，否则会用旧输入或空输入误建索引。

启动扫描组件：

```java
@Component
@RequiredArgsConstructor
public class IndexRunStartupRecovery implements ApplicationRunner {

    private final IndexRunsService indexRunsService;
    private final CkqaIntegrationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        indexRunsService.recoverStaleRunningRuns(Duration.ofSeconds(properties.getTimeout().getIndexStaleSeconds()));
    }
}
```

Controller 追加：

```java
@PostMapping("/{id}/index-runs")
public ApiResponse<IndexRunResponse> createIndexRun(@PathVariable @Positive Long id) throws IOException, InterruptedException {
    return ApiResponseUtils.success(indexWorkflowService.createIndexRun(id));
}

@GetMapping("/{id}/index-runs")
public ApiResponse<List<IndexRunResponse>> listIndexRuns(@PathVariable @Positive Long id) {
    return ApiResponseUtils.success(indexWorkflowService.listIndexRuns(id));
}
```

```java
@GetMapping("/{id}")
public ApiResponse<IndexRunResponse> getIndexRun(@PathVariable @Positive Long id) {
    return ApiResponseUtils.success(indexWorkflowService.getIndexRun(id));
}
```

`pdf_ingest/sql/ocqa.sql` 增加索引：

```sql
CREATE INDEX `idx_index_runs_kb_status` ON `index_runs` (`knowledge_base_id`, `status`);
CREATE INDEX `idx_index_runs_status_started` ON `index_runs` (`status`, `started_at`);
```

新增错误码：

```java
KNOWLEDGE_BASE_NOT_FOUND(4046, "知识库不存在"),
INDEX_RUN_NOT_FOUND(4047, "索引任务不存在"),
INDEX_RUN_ALREADY_RUNNING(4095, "当前知识库已有索引任务在运行"),
INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败");
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=IndexWorkflowServiceTest,IndexRunsControllerWebMvcTest,KnowledgeBasesControllerWebMvcTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunMetadata.java \
  src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagIndexOrchestrator.java \
  src/main/java/org/ysu/ckqaback/integration/graphrag/IndexRunStartupRecovery.java \
  src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java \
  src/main/java/org/ysu/ckqaback/index/dto/IndexRunResponse.java \
  src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java \
  src/main/java/org/ysu/ckqaback/controller/IndexRunsController.java \
  src/main/java/org/ysu/ckqaback/service/KnowledgeBasesService.java \
  src/main/java/org/ysu/ckqaback/service/impl/KnowledgeBasesServiceImpl.java \
  src/main/java/org/ysu/ckqaback/service/IndexRunsService.java \
  src/main/java/org/ysu/ckqaback/service/impl/IndexRunsServiceImpl.java \
  src/main/java/org/ysu/ckqaback/api/ApiResultCode.java \
  src/test/java/org/ysu/ckqaback/index/IndexWorkflowServiceTest.java \
  src/test/java/org/ysu/ckqaback/controller/IndexRunsControllerWebMvcTest.java \
  src/test/java/org/ysu/ckqaback/controller/KnowledgeBasesControllerWebMvcTest.java \
  ../../pdf_ingest/sql/ocqa.sql
git commit -m "feat: add index orchestration and stale run recovery"
```

### Task 5: 实现课程入口与系统健康检查

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/system/SystemHealthServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/system/SystemHealthControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLookupService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/KnowledgeBaseSummaryResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthController.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/dto/SystemHealthItemResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/dto/SystemHealthResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`

- [ ] **Step 1: 写失败测试**

```java
class CoursesControllerWebMvcTest {

    @Test
    void shouldListCoursePdfFiles() throws Exception {
        given(courseLookupService.listCoursePdfFiles("os")).willReturn(List.of(
                CoursePdfFileSummaryResponse.of(7L, "book.pdf", "done")
        ));

        mockMvc.perform(get(ApiPaths.COURSES + "/os/pdf-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("book.pdf"));
    }
}
```

```java
class SystemHealthServiceTest {

    @Test
    void shouldReturnUnreachableItemWhenGraphRagApiThrowsException() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        given(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).willReturn(1);

        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPdfIngest().setRoot("/tmp/pdf_ingest");
        properties.getGraphrag().setRoot("/tmp/graphrag");
        properties.getGraphrag().setApiBaseUrl("http://127.0.0.1:8012");

        RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_DEEP_STUBS);
        given(builder.build().get().uri("http://127.0.0.1:8012/health").retrieve().toBodilessEntity())
                .willThrow(new RuntimeException("connect refused"));

        SystemHealthService service = new SystemHealthService(jdbcTemplate, properties, builder);
        SystemHealthResponse response = service.check();

        assertThat(response.items()).anySatisfy(item -> {
            assertThat(item.name()).isEqualTo("graphrag-api");
            assertThat(item.reachable()).isFalse();
        });
    }
}
```

```java
class SystemHealthControllerWebMvcTest {

    @Test
    void shouldReturnDetailedHealthStatus() throws Exception {
        SystemHealthResponse response = SystemHealthResponse.up(
                SystemHealthItemResponse.of("mysql", true, true, "ok"),
                SystemHealthItemResponse.of("graphrag-api", true, false, "v1/models unavailable")
        );
        given(systemHealthService.check()).willReturn(response);

        mockMvc.perform(get(ApiPaths.SYSTEM_HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("mysql"))
                .andExpect(jsonPath("$.data.items[1].ready").value(false));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=CoursesControllerWebMvcTest,SystemHealthControllerWebMvcTest test`

Expected: FAIL，因为课程入口服务、健康检查 DTO 和 `/api/v1/system/health` 路径都还不存在。

- [ ] **Step 3: 写最小实现**

`ApiPaths` 追加：

```java
public static final String SYSTEM_HEALTH = API_V1 + "/system/health";
```

课程查询服务：

```java
@Service
@RequiredArgsConstructor
public class CourseLookupService {

    private final PdfFilesService pdfFilesService;
    private final KnowledgeBasesService knowledgeBasesService;

    public List<CoursePdfFileSummaryResponse> listCoursePdfFiles(String courseId) {
        return pdfFilesService.listByCourseId(courseId).stream()
                .map(CoursePdfFileSummaryResponse::fromEntity)
                .toList();
    }

    public List<KnowledgeBaseSummaryResponse> listKnowledgeBases(String courseId) {
        return knowledgeBasesService.listByCourseId(courseId).stream()
                .map(KnowledgeBaseSummaryResponse::fromEntity)
                .toList();
    }
}
```

健康检查最小实现：

```java
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final CkqaIntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;

    public SystemHealthResponse check() {
        SystemHealthItemResponse mysql = checkMySql();
        SystemHealthItemResponse pdfPath = checkPath("pdf-ingest-root", properties.getPdfIngest().getRoot());
        SystemHealthItemResponse graphPath = checkPath("graphrag-root", properties.getGraphrag().getRoot());
        SystemHealthItemResponse apiReachable = checkApiReachable();
        SystemHealthItemResponse apiReady = checkApiReady();

        return new SystemHealthResponse(
                List.of(mysql, pdfPath, graphPath, apiReachable, apiReady).stream().allMatch(SystemHealthItemResponse::reachable),
                List.of(mysql, pdfPath, graphPath, apiReachable, apiReady)
        );
    }

    private SystemHealthItemResponse checkMySql() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return SystemHealthItemResponse.of("mysql", true, Integer.valueOf(1).equals(value), "SELECT 1");
        } catch (Exception ex) {
            return SystemHealthItemResponse.of("mysql", false, false, ex.getMessage());
        }
    }

    private SystemHealthItemResponse checkPath(String name, String rawPath) {
        try {
            if (!StringUtils.hasText(rawPath)) {
                return SystemHealthItemResponse.of(name, false, false, "path not configured");
            }
            Path path = Path.of(rawPath);
            boolean exists = Files.exists(path);
            return SystemHealthItemResponse.of(name, exists, exists, exists ? "path exists" : "path missing");
        } catch (Exception ex) {
            return SystemHealthItemResponse.of(name, false, false, ex.getMessage());
        }
    }

    private SystemHealthItemResponse checkApiReachable() {
        try {
            restClientBuilder.build().get().uri(properties.getGraphrag().getApiBaseUrl() + "/health").retrieve().toBodilessEntity();
            return SystemHealthItemResponse.of("graphrag-api", true, true, "HTTP /health reachable");
        } catch (Exception ex) {
            return SystemHealthItemResponse.of("graphrag-api", false, false, ex.getMessage());
        }
    }

    private SystemHealthItemResponse checkApiReady() {
        try {
            restClientBuilder.build().get().uri(properties.getGraphrag().getApiBaseUrl() + "/v1/models").retrieve().toEntity(String.class);
            return SystemHealthItemResponse.of("graphrag-ready", true, true, "HTTP /v1/models reachable");
        } catch (Exception ex) {
            return SystemHealthItemResponse.of("graphrag-ready", false, false, ex.getMessage());
        }
    }
}
```

一期这里的硬约束是：任何单个依赖检查失败都只能降级成某个子项的 `reachable=false` / `ready=false`，不能让 `/api/v1/system/health` 本身抛 500。

Controller：

```java
@RequiredArgsConstructor
@RestController
@RequestMapping(ApiPaths.COURSES)
public class CoursesController {

    private final CourseLookupService courseLookupService;

    @GetMapping("/{courseId}/pdf-files")
    public ApiResponse<List<CoursePdfFileSummaryResponse>> listCoursePdfFiles(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listCoursePdfFiles(courseId));
    }

    @GetMapping("/{courseId}/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseSummaryResponse>> listKnowledgeBases(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listKnowledgeBases(courseId));
    }
}
```

```java
@RequiredArgsConstructor
@RestController
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    @GetMapping(ApiPaths.SYSTEM_HEALTH)
    public ApiResponse<SystemHealthResponse> health() {
        return ApiResponseUtils.success(systemHealthService.check());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=CoursesControllerWebMvcTest,SystemHealthControllerWebMvcTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add src/main/java/org/ysu/ckqaback/api/ApiPaths.java \
  src/main/java/org/ysu/ckqaback/controller/CoursesController.java \
  src/main/java/org/ysu/ckqaback/course/CourseLookupService.java \
  src/main/java/org/ysu/ckqaback/course/dto/CoursePdfFileSummaryResponse.java \
  src/main/java/org/ysu/ckqaback/course/dto/KnowledgeBaseSummaryResponse.java \
  src/main/java/org/ysu/ckqaback/system/SystemHealthService.java \
  src/main/java/org/ysu/ckqaback/system/SystemHealthController.java \
  src/main/java/org/ysu/ckqaback/system/dto/SystemHealthItemResponse.java \
  src/main/java/org/ysu/ckqaback/system/dto/SystemHealthResponse.java \
  src/test/java/org/ysu/ckqaback/system/SystemHealthServiceTest.java \
  src/test/java/org/ysu/ckqaback/controller/CoursesControllerWebMvcTest.java \
  src/test/java/org/ysu/ckqaback/system/SystemHealthControllerWebMvcTest.java
git commit -m "feat: add course lookup and system health endpoints"
```

### Task 6: 实现问答会话、消息与 GraphRAG 代理

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClientTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/CreateQaSessionRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/CreateQaMessageRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaSessionResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaSessionsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaSessionsServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaMessagesService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaMessagesServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java`

- [ ] **Step 1: 写失败测试**

```java
class GraphRagQueryClientTest {

    @Test
    void shouldMapLocalModeToLocalModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": { "role": "assistant", "content": "答案" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagQueryClient client = new GraphRagQueryClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(120));
        GraphRagChatResult result = client.query("local", "什么是进程");

        assertThat(result.content()).isEqualTo("答案");
    }
}
```

```java
class QaWorkflowServiceTest {

    @Test
    void shouldPersistFailedRetrievalWithoutAssistantMessage() {
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(graphRagQueryClient.query("local", "什么是线程")).willThrow(new RuntimeException("upstream 500"));

        assertThatThrownBy(() -> workflowService.sendMessage(5L, new CreateQaMessageRequest("local", "什么是线程")))
                .isInstanceOf(RuntimeException.class);

        then(qaRetrievalLogsService).should().createFailureLog(eq(5L), eq("os"), any(), eq("local"), eq("什么是线程"), contains("upstream 500"));
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), anyString());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GraphRagQueryClientTest,QaWorkflowServiceTest,QaSessionsControllerWebMvcTest test`

Expected: FAIL，因为 GraphRAG 客户端、QA 工作流和 Controller 方法都还不存在。

- [ ] **Step 3: 写最小实现**

GraphRAG 客户端：

```java
@Service
public class GraphRagQueryClient {

    private final RestClient restClient;

    public GraphRagQueryClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        this(builder, properties.getGraphrag().getApiBaseUrl(), Duration.ofSeconds(properties.getTimeout().getQuerySeconds()));
    }

    GraphRagQueryClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    public GraphRagChatResult query(String mode, String prompt) {
        String model = switch (mode) {
            case "global" -> "graphrag-global-search:latest";
            case "full" -> "full-model:latest";
            default -> "graphrag-local-search:latest";
        };

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        Map<?, ?> choice = ((List<Map<?, ?>>) response.get("choices")).get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return new GraphRagChatResult((String) message.get("content"));
    }
}
```

这里保留一个 package-private 次构造函数，专门给 `GraphRagQueryClientTest` 直接注入 `baseUrl` 与 `Duration`，避免测试为了构造 `CkqaIntegrationProperties` 而引入无关样板代码，同时保证生产代码仍通过配置属性驱动。

`QaSessionsService` 最小方法：

```java
QaSessions getRequiredById(Long id);

QaSessions createSession(CreateQaSessionRequest request);
```

`QaMessagesService` 最小方法：

```java
QaMessages appendUserMessage(Long sessionId, String content);

QaMessages appendAssistantMessage(Long sessionId, String content);

List<QaMessages> listBySessionId(Long sessionId);
```

`QaRetrievalLogsService` 最小方法：

```java
QaRetrievalLogs createSuccessLog(Long sessionId, String courseId, Long indexRunId, String mode, String queryText);

QaRetrievalLogs createFailureLog(Long sessionId, String courseId, Long indexRunId, String mode, String queryText, String errorMessage);
```

`QaWorkflowService` 关键流程：

```java
public QaRoundResponse sendMessage(Long sessionId, CreateQaMessageRequest request) {
    QaSessions session = qaSessionsService.getRequiredById(sessionId);
    if (!"active".equals(session.getStatus())) {
        throw new BusinessException(ApiResultCode.QA_SESSION_NOT_ACTIVE, HttpStatus.CONFLICT);
    }

    KnowledgeBases kb = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
    if (kb.getActiveIndexRunId() == null) {
        throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
    }

    QaMessages userMessage = qaMessagesService.appendUserMessage(sessionId, request.getContent());
    try {
        GraphRagChatResult result = graphRagQueryClient.query(request.getMode(), request.getContent());
        QaRetrievalLogs log = qaRetrievalLogsService.createSuccessLog(sessionId, session.getCourseId(), kb.getActiveIndexRunId(), request.getMode(), request.getContent());
        QaMessages assistantMessage = qaMessagesService.appendAssistantMessage(sessionId, result.content());
        return QaRoundResponse.of(QaMessageResponse.fromEntity(userMessage), QaMessageResponse.fromEntity(assistantMessage), log.getRetrievalStatus());
    } catch (RuntimeException ex) {
        qaRetrievalLogsService.createFailureLog(sessionId, session.getCourseId(), kb.getActiveIndexRunId(), request.getMode(), request.getContent(), ex.getMessage());
        throw ex;
    }
}
```

`CreateQaMessageRequest`：

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateQaMessageRequest {
    @NotBlank
    private String mode = "local";

    @NotBlank
    private String content;
}
```

`QaSessionsController`：

```java
@RequiredArgsConstructor
@RestController
@RequestMapping(ApiPaths.QA_SESSIONS)
public class QaSessionsController {

    private final QaWorkflowService qaWorkflowService;

    @PostMapping
    public ApiResponse<QaSessionResponse> createSession(@Valid @RequestBody CreateQaSessionRequest request) {
        return ApiResponseUtils.success(qaWorkflowService.createSession(request));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<QaRoundResponse> sendMessage(@PathVariable @Positive Long id, @Valid @RequestBody CreateQaMessageRequest request) {
        return ApiResponseUtils.success(qaWorkflowService.sendMessage(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<QaSessionResponse> getSession(@PathVariable @Positive Long id) {
        return ApiResponseUtils.success(qaWorkflowService.getSession(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<QaMessageResponse>> listMessages(@PathVariable @Positive Long id) {
        return ApiResponseUtils.success(qaWorkflowService.listMessages(id));
    }
}
```

新增错误码：

```java
QA_SESSION_NOT_FOUND(4048, "问答会话不存在"),
QA_SESSION_NOT_ACTIVE(4096, "问答会话已关闭"),
KNOWLEDGE_BASE_NOT_READY(4097, "知识库当前没有可用索引");
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend/ckqa-back && ./mvnw -q -Dtest=GraphRagQueryClientTest,QaWorkflowServiceTest,QaSessionsControllerWebMvcTest test`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd backend/ckqa-back
git add src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagChatResult.java \
  src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClient.java \
  src/main/java/org/ysu/ckqaback/qa/QaWorkflowService.java \
  src/main/java/org/ysu/ckqaback/qa/dto/CreateQaSessionRequest.java \
  src/main/java/org/ysu/ckqaback/qa/dto/CreateQaMessageRequest.java \
  src/main/java/org/ysu/ckqaback/qa/dto/QaSessionResponse.java \
  src/main/java/org/ysu/ckqaback/qa/dto/QaMessageResponse.java \
  src/main/java/org/ysu/ckqaback/qa/dto/QaRoundResponse.java \
  src/main/java/org/ysu/ckqaback/controller/QaSessionsController.java \
  src/main/java/org/ysu/ckqaback/service/QaSessionsService.java \
  src/main/java/org/ysu/ckqaback/service/impl/QaSessionsServiceImpl.java \
  src/main/java/org/ysu/ckqaback/service/QaMessagesService.java \
  src/main/java/org/ysu/ckqaback/service/impl/QaMessagesServiceImpl.java \
  src/main/java/org/ysu/ckqaback/service/QaRetrievalLogsService.java \
  src/main/java/org/ysu/ckqaback/service/impl/QaRetrievalLogsServiceImpl.java \
  src/main/java/org/ysu/ckqaback/api/ApiResultCode.java \
  src/test/java/org/ysu/ckqaback/integration/graphrag/GraphRagQueryClientTest.java \
  src/test/java/org/ysu/ckqaback/qa/QaWorkflowServiceTest.java \
  src/test/java/org/ysu/ckqaback/controller/QaSessionsControllerWebMvcTest.java
git commit -m "feat: add qa session and graphrag proxy endpoints"
```

### Task 7: 更新文档与完成全量验证

**Files:**
- Modify: `backend/ckqa-back/README.md`
- Modify: `backend/ckqa-back/.env.example`
- Modify: `backend/ckqa-back/src/main/resources/application.properties`
- Modify: `docs/superpowers/specs/2026-04-21-ckqa-back-orchestration-phase1-design.md` only if implementation forces a naming adjustment

- [ ] **Step 1: 写失败检查清单**

在本任务开始前，先列出必须全部通过的验证命令：

```bash
cd backend/ckqa-back && ./mvnw -q test
cd backend/ckqa-back && ./mvnw -q -DskipTests compile
cd backend/ckqa-back && ./mvnw -q spring-boot:run
```

并记录联调 API：

```text
GET  /api/v1/system/health
GET  /api/v1/courses/{courseId}/pdf-files
POST /api/v1/pdf-files/{id}/parse
POST /api/v1/pdf-files/{id}/export-graphrag
POST /api/v1/knowledge-bases/{id}/index-runs
POST /api/v1/qa-sessions
POST /api/v1/qa-sessions/{id}/messages
```

- [ ] **Step 2: 更新 README 与环境变量说明**

README 至少补这 4 段：

```markdown
## 一期编排接口

- `POST /api/v1/pdf-files/{id}/parse`
- `POST /api/v1/pdf-files/{id}/export-graphrag`
- `POST /api/v1/knowledge-bases/{id}/index-runs`
- `POST /api/v1/qa-sessions/{id}/messages`

## 外部依赖配置

- `PDF_INGEST_PYTHON`
- `PDF_INGEST_ROOT`
- `GRAPHRAG_PYTHON`
- `GRAPHRAG_ROOT`
- `GRAPHRAG_API_BASE_URL`
- `INDEX_STALE_SECONDS`

## 启动顺序

1. 启动 MySQL
2. 启动 `graphrag_pipeline/utils/main.py`
3. 启动 `backend/ckqa-back`

## 回归验证

执行 `./mvnw -q test` 与手工 API 联调。
```

- [ ] **Step 3: 运行全量验证**

Run: `cd backend/ckqa-back && ./mvnw -q test`
Expected: 所有单元测试与 MockMvc 测试 PASS

Run: `cd backend/ckqa-back && ./mvnw -q -DskipTests compile`
Expected: 编译 PASS

Run: `cd backend/ckqa-back && ./mvnw -q spring-boot:run`
Expected: 应用启动成功，日志中没有 Bean 注入失败或配置绑定异常

手工联调最小顺序：

1. `GET /api/v1/system/health` 返回 `reachable/ready` 子项结构
2. `GET /api/v1/courses/os/pdf-files` 返回已有 PDF 列表
3. `POST /api/v1/pdf-files/{id}/parse` 返回统一响应
4. `POST /api/v1/pdf-files/{id}/export-graphrag` 能返回导出完成或“已存在完整导出结果”
5. `POST /api/v1/knowledge-bases/{id}/index-runs` 返回 `running` 或 `success`
6. `POST /api/v1/qa-sessions` 与 `POST /api/v1/qa-sessions/{id}/messages` 能写入数据库记录

- [ ] **Step 4: 最终提交**

```bash
cd /home/sunlight/Projects/ckqa
git add backend/ckqa-back/README.md \
  backend/ckqa-back/.env.example \
  backend/ckqa-back/src/main/resources/application.properties \
  docs/superpowers/specs/2026-04-21-ckqa-back-orchestration-phase1-design.md
git commit -m "docs: finalize ckqa-back orchestration phase1 docs"
```
