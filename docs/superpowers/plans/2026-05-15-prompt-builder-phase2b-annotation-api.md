# 手动调优提示词向导 · Phase 2b 标注 API 后端实现 + 前端接入

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 02 步从 mock 数据切到真实后端：本期**真实实现 3 个端点** `generateAuditSet` / `listAuditSamples` / `updateAuditSample`（含跨构建历史标注复用），并把 `PromptBuilderPrepareStep.vue` 接入这些 API，去掉 `MOCK_AUDIT_SAMPLES` 依赖。另外 2 个端点 `triggerPromptTuneSamples` 与 `requestAuditSampleAiSuggestions` **本期继续保留为 501 占位**——前者的设计语义会与 `generateAuditSet` 重叠（前端始终走 02.1+02.2 串跑），后者（智能能力 A）按 spec § 实施分期留到 Phase 3。

**Architecture:** 后端新增四层：`AuditPipelineOrchestrator`（同步包装两个 Python 脚本调用，复用 `PromptTuneOrchestrator.fetchInputs`）→ `AuditSampleService`（无事务业务编排：触发流水线、查询、更新）→ `AuditSamplePersistenceService`（事务边界：解析 audit_extraction_set.json、删旧写新、历史复用合并）→ `AuditSampleResponseMapper`（Entity ↔ DTO 转换）。Controller 直接调 `AuditSampleService`。前端把 `PromptBuilderPrepareStep.vue` 内的本地 reactive 状态改为"加载/保存到后端"模式，新增 `prepare-step-api.js` 抽出形态转换纯函数。

**Tech Stack:** 已有依赖；不引入新库。后端：Spring Boot + MyBatis-Plus + Jackson + 已有的 `ProcessRunner`；前端：Vue 3 + Element Plus + 已有的 `prompt-tune-pipeline.js`（2a 桩）。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 02 构建准备材料、§ 数据模型与后端契约。

**前置：** Phase 1b（前端标注 IDE mock 版）+ Phase 2a（DB 迁移 + Java 骨架 + 前端 API 桩）已完成。**特别地，本期假设 2a 已经落地 `PromptTuneAuditSamplesService.listByBuildRunId(Long)` 与 `PromptTuneAuditSamplesService.findCompletedByStableKeys(Long, List<String>)` 两个方法**——它们由 2a Task 4 引入，Task 0 会做一次校验。

## 范围说明（与 2a plan 的对齐）

2a 计划的备注里把"02 步标注 API（含 ai-suggestions）"统一归到 2b。但 spec § 实施分期把智能能力 A（AI 预填）显式放在 Phase 3。本计划遵循 spec：**ai-suggestions 端点继续保留为 501 占位**（PromptTunePipelineController 中只在注释里写"Phase 3 落地"），不在本期实现。智能能力 D（历史标注复用）的**底层数据合并由后端 silently 完成**（写库时填 `reused_from_build_run_id`），前端 banner UI 同样留到 Phase 3。

本期前端只做"加载样本 / 保存标注"两条主路径接入，进度门控（high 全完成才能进 03）已经在 Phase 1c 路由层做了 placeholder，不动；IndexedDB 离线暂存（spec § 错误处理）留到 Phase 7。

## v2 修订要点（与初稿差异）

针对初稿审阅意见的修订（影响后续 Task 实现细节）：

1. **事务边界**：Python 子进程不能包在 `@Transactional` 内，否则 DB 连接占用 ≤5 分钟。`regenerateAuditSet` 拆成"无事务编排"+`AuditSamplePersistenceService.replaceForBuildRun(...)` 单独事务方法。
2. **`triggerSampleGeneration` 语义**：删除原"仅跑 02.1"的真实实现；该端点继续 501 占位（前端不调用），避免和 `regenerateAuditSet` 行为重叠。orchestrator 不需要拆方法。
3. **force 保护**：`POST /audit-set` 新增 `?force=true` 查询参数；后端检测到本 build run 存在非 `pending` 状态样本时，未带 `force` 直接返回 `409 BUILD_RUN_HAS_ANNOTATED_SAMPLES`。
4. **PATCH 语义**：`AuditSampleUpdateRequest` 字段中 `null` = 显式清空，缺字段 = 不更新。前端 `localSampleToUpdatePayload` 改用"标记是否要清空"的 sentinel；后端 `applyUpdate` 用 Jackson `@JsonInclude(ALWAYS)` + 三态判定（缺字段 / null / 有值）。
5. **`reviewerDecision` 白名单**：DTO 加 `@Pattern` 枚举校验，仅允许 `pending|in_progress|completed|skipped`。`@Pattern` 默认允许 null——显式传 null 时 service 层回退为 `"pending"`。
6. **Task 0**：新增"Phase 2a 产物校验"，跑 grep 确认 service 自定义方法、controller 占位、表存在性、ENUM 类型、Python 脚本参数。
7. **Task 11（原 Task 8 前）盘点模板依赖**：在替换 `<script setup>` 前先 grep 模板中引用的所有 `@xxx` / `v-xxx` / `:xxx` / `{{ xxx }}`，与新 script 的 export 对照，确认不漏。
8. **`listByBuildRunId` 排序**：依赖 2a 实现里已有的 `audit_priority ASC + source_sample_id ASC`；Task 0 新增 ENUM 类型校验步骤，确认 MySQL 列确实是 `enum('high','medium','low')` 而非 varchar。
9. **Jackson 三态反序列化测试**（v2.1 新增）：Task 4 新增 `AuditSampleUpdateRequestTest`，用 `ObjectMapper.readValue` 验证 `{}`、`{"skipReason":null}`、`{"skipReason":"value"}` 三种 JSON 输入的 `hasField` 行为。
10. **前端 `allowForcePrompt` 对齐**（v2.1 修正）：首次自动加载时遇到 4103 弹确认（`allowForcePrompt: true`），用户可选择覆盖或取消。
11. **`generateAuditSet` 签名向后兼容**（v2.1 新增）：第二参数如果是 axios-like 对象（有 `.post` 方法），自动视为旧 `client` 参数，避免已有测试炸开。

## 自检：spec 覆盖清单

- ✅ POST `/audit-set`（带 force）/ GET `/audit-samples` / PUT `/audit-samples/{sampleId}` 三端点真实实现 → Task 5 / 6 / 7
- ⏸ POST `/prompt-tune-samples` 保持 501 占位（与 `generateAuditSet` 行为重叠，无独立用例）→ Task 7
- ⏸ POST `/audit-samples/{sampleId}/ai-suggestions` 保持 501 占位 → Phase 3
- ✅ 历史标注稳定键复用（`gold_stable_key` + `reused_from_build_run_id`）→ Task 5 step 4
- ✅ 错误码 `AUDIT_SAMPLE_NOT_FOUND` / `AUDIT_PIPELINE_FAILED` / `BUILD_RUN_HAS_ANNOTATED_SAMPLES` 落地 → Task 1
- ✅ 前端去 mock，调真实 API → Task 9 / 10
- ⏸ ♻ 历史复用 banner UI 渲染 → Phase 3
- ⏸ IndexedDB 离线暂存 → Phase 7

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java` | 修改 | 新增 `AUDIT_SAMPLE_NOT_FOUND` / `AUDIT_PIPELINE_FAILED` / `BUILD_RUN_HAS_ANNOTATED_SAMPLES` 错误码 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditPipelineOrchestrator.java` | 新建 | 同步包装 `build_prompt_tuning_samples.py` / `build_audit_extraction_set.py`（一个 `runFullPipeline` 方法） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleResponseMapper.java` | 新建 | Entity → DTO 转换工具类（JSON 反序列化、reusedFrom 装配、headingPath 拼接） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSamplePersistenceService.java` | 新建 | **单独事务边界**：删除本 build run 旧样本、写新样本、读取最新样本（不含 Python 子进程调用） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleService.java` | 新建 | 02 步业务编排：触发流水线（无事务）、调 persistence service（事务）、查询、更新 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java` | 修改 | 新增 `@Pattern` 校验 reviewerDecision；保持三态 PATCH 语义 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java` | 修改 | 替换 3 个 501 占位（audit-set / audit-samples GET / audit-samples PUT），引入 `force` 参数；`prompt-tune-samples` 与 `ai-suggestions` 仍 501 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleResponseMapperTest.java` | 新建 | Mapper 单测：JSON 解析、reusedFrom 装配、空字段容错 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleServiceTest.java` | 新建 | Service 单测：稳定键复用合并、updateSample 三态字段合并、force 防误删校验 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/prepare-step-api.js` | 新建 | 形态转换纯函数：API 响应 → 本地 sample；本地编辑 → API payload（含 `null=清空` 三态） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue` | 修改 | 替换 mock：进入时拉真实样本，标注变更时持久化；保留模板依赖完整 |
| `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js` | 修改 | `generateAuditSet` 增加可选 `{ force }` 参数 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prepare-step-api.test.js` | 新建 | 形态转换纯函数测试，覆盖三态语义 |

---

## Task 0：Phase 2a 产物前置校验

在写任何代码前先 grep 确认 2a 产物落地、签名匹配，避免 Task 1+ 写到一半发现依赖缺失。**本任务不写代码、不 commit**，只做防御性确认。

- [ ] **Step 1: 校验 2a 数据库迁移已执行**

Run: `mysql -uroot -p ocqa -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('prompt_tune_audit_samples', 'prompt_drafts');"`

Expected: 输出 2 行；如缺表，先回到 2a Task 1 跑迁移 `mysql -uroot -p ocqa < sql/migrations/20260515_prompt_tune_pipeline.sql`。

- [ ] **Step 2: 校验 2a Java 骨架已落地**

Run:

```bash
ls backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneAuditSamples.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneAuditSamplesMapper.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneAuditSamplesService.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleResponse.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PipelineStepResponse.java \
   backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
```

Expected: 8 个文件全部存在，无 "No such file" 错误。

- [ ] **Step 3: 校验 `PromptTuneAuditSamplesService` 已暴露本期需要的两个查询方法**

Run:

```bash
grep -n 'listByBuildRunId\|findCompletedByStableKeys' \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneAuditSamplesService.java \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java
```

Expected:

- 接口文件中各出现 1 次方法签名（`List<PromptTuneAuditSamples> listByBuildRunId(Long buildRunId);` / `findCompletedByStableKeys(Long, List<String>)`）。
- 实现文件中各出现 1 次方法体。

如果只有接口、缺实现，本期会编译失败；如果两者都缺，**先回到 2a Task 4 把这两个方法补齐再回来**。

- [ ] **Step 4: 校验 `audit_priority` 是 MySQL ENUM 类型**

`listByBuildRunId` 当前实现依赖 MySQL `enum('high','medium','low')` 内部索引值（high=1 < medium=2 < low=3）的 ASC 顺序。如果 2a 实际把列建成了 `varchar`，ASC 会变成字典序 `high → low → medium`，业务语义就错了。

Run: `mysql -uroot -p ocqa -e "SHOW COLUMNS FROM prompt_tune_audit_samples LIKE 'audit_priority';"`

Expected: `Type` 列严格匹配 `enum('high','medium','low')`。

如果不是 ENUM（例如显示 `varchar(10)`），**必须**先改 `PromptTuneAuditSamplesServiceImpl.listByBuildRunId` 改用 CASE 显式排序，再继续本 plan：

```java
// 不依赖 MySQL ENUM 顺序，显式 CASE 排序
LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRunId);
return baseMapper.selectList(wrapper.select().last(
        "ORDER BY CASE audit_priority " +
        "  WHEN 'high' THEN 1 " +
        "  WHEN 'medium' THEN 2 " +
        "  WHEN 'low' THEN 3 " +
        "  ELSE 9 END, source_sample_id ASC"
));
```

（这个改动在 2a 范畴；如发生，作为 2a 的额外修订单独 commit。）

- [ ] **Step 5: 校验 `listByBuildRunId` 的排序约定**

Run: `grep -n 'orderByAsc\|orderByDesc\|ORDER BY' backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java`

Expected: 看到对 audit_priority + source_sample_id 的明确排序（要么 `orderByAsc(... getAuditPriority)` + `orderByAsc(... getSourceSampleId)`，要么 Step 4 中的 CASE 表达式）。

- [ ] **Step 6: 校验 `build_audit_extraction_set.py` 支持 `--preserve_existing_gold`**

`AuditPipelineOrchestrator.runFullPipeline` 会固定下发该参数。如果脚本不支持，到冒烟阶段才会失败。

Run:

```bash
python graphrag_pipeline/scripts/build_audit_extraction_set.py --help 2>&1 | grep -E 'preserve_existing_gold|--no-preserve_existing_gold'
```

Expected: 至少匹配一行 `--preserve_existing_gold` 或其 `BooleanOptionalAction` 自动生成的反向 `--no-preserve_existing_gold`。如果两者都缺，**先回到 graphrag_pipeline 检查脚本版本**——本 plan 假设 2a 之前已落地的脚本含此参数（参考 `build_audit_extraction_set.py:1259-1263` 的 `BooleanOptionalAction`）。

- [ ] **Step 7: 校验 controller 占位与前端 API 桩已落地**

Run:

```bash
grep -n 'PIPELINE_NOT_IMPLEMENTED\|notImplemented' \
  backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java | head -10
grep -c '^export async function' frontend/apps/admin-app/src/api/prompt-tune-pipeline.js
```

Expected: 第一个命令至少 5 行；第二个命令输出 `13`。

- [ ] **Step 8: 校验 `KnowledgeBaseBuildRunsService` 提供 `listByIds`**

Run: `grep -n 'extends IService' backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/KnowledgeBaseBuildRunsService.java`

Expected: 看到接口继承 MyBatis-Plus `IService<KnowledgeBaseBuildRuns>`。`listByIds(Collection<? extends Serializable>)` 来自父接口的默认方法。

- [ ] **Step 9: 通过则进入 Task 1**

如以上任何一步失败，**停止本计划**，先补齐缺失项；否则进入下一 Task。

---

## Task 1：扩展 ApiResultCode 错误码

新增三个本期 02 步会用到的错误码。`PIPELINE_NOT_IMPLEMENTED` 已经在 2a 落地，本期沿用。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1: 在 `PROMPT_TUNE_RUN_NOT_FOUND(4050, ...)` 之后新增 `AUDIT_SAMPLE_NOT_FOUND(4051, ...)`**

定位锚点：

```java
    /**
     * 提示词自动调优记录不存在。
     */
    PROMPT_TUNE_RUN_NOT_FOUND(4050, "提示词自动调优记录不存在"),
```

在该枚举值之后插入：

```java
    /**
     * 标注样本不存在。
     */
    AUDIT_SAMPLE_NOT_FOUND(4051, "标注样本不存在"),
```

- [ ] **Step 2: 在 `KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING(4099, ...)` 之后新增 `BUILD_RUN_HAS_ANNOTATED_SAMPLES(4103, ...)`**

`4100`/`4101`/`4102` 已被 `COURSE_MATERIAL_*` 与 `PROMPT_TUNE_ALREADY_RUNNING` 占用，本错误码取 `4103`。

定位锚点：

```java
    /**
     * 当前调优缓存键已有运行中的调优任务。
     */
    PROMPT_TUNE_ALREADY_RUNNING(4102, "相同选材的自动调优正在执行，请稍候"),
```

在该枚举值之后插入：

```java
    /**
     * 当前 build run 存在已被人工标注的样本，强制重新生成会清空当前进度。
     */
    BUILD_RUN_HAS_ANNOTATED_SAMPLES(4103, "当前构建已有人工标注，确认覆盖请重试并设置 force=true"),
```

- [ ] **Step 3: 在 `INDEX_RUN_EXECUTION_FAILED(5004, ...)` 之后新增 `AUDIT_PIPELINE_FAILED(5005, ...)`**

定位锚点：

```java
    /**
     * 索引任务执行失败。
     */
    INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败"),
```

在该枚举值之后插入：

```java
    /**
     * 标注流水线执行失败（build_prompt_tuning_samples / build_audit_extraction_set）。
     */
    AUDIT_PIPELINE_FAILED(5005, "标注流水线执行失败"),
```

- [ ] **Step 4: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(api): 新增 AUDIT_SAMPLE_NOT_FOUND / AUDIT_PIPELINE_FAILED / BUILD_RUN_HAS_ANNOTATED_SAMPLES 错误码 (Phase 2b)"
```

---

## Task 2：AuditPipelineOrchestrator 同步流水线包装

包装两个 Python 脚本为单一方法 `runFullPipeline`：先跑 02.1 `build_prompt_tuning_samples.py`，再跑 02.2 `build_audit_extraction_set.py`。本 orchestrator **不感知数据库 / 事务**，只做：

1. 准备工作目录 + 复用 `PromptTuneOrchestrator.fetchInputs` 拉 MinIO 资料。
2. 串行调两个脚本，任一失败立刻抛 `BusinessException(AUDIT_PIPELINE_FAILED, INTERNAL_SERVER_ERROR, "...")`。
3. 返回两个产物文件路径与各自耗时。

**调用方约束：** 必须在 **无 Spring 事务** 的方法中调用本方法（否则 DB 连接会被外部进程占据 ≤5 分钟）。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditPipelineOrchestrator.java`

- [ ] **Step 1: 创建 AuditPipelineOrchestrator**

```java
package org.ysu.ckqaback.index;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 02 步 audit 流水线编排器。
 * <p>
 * 同步串行调用两个 Python 脚本：
 * <ol>
 *   <li>{@code scripts/build_prompt_tuning_samples.py} 从 normalized 文档生成调优样本集；</li>
 *   <li>{@code scripts/build_audit_extraction_set.py} 从样本集分层抽样得到 audit 校准集。</li>
 * </ol>
 * <p><strong>事务边界约束：</strong> 调用方必须确保本方法在 <em>无 Spring 事务</em>
 * 的上下文中执行，否则 DB 连接会被外部 Python 进程占据数分钟。
 * 失败统一抛 {@link BusinessException}（{@link ApiResultCode#AUDIT_PIPELINE_FAILED}）。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AuditPipelineOrchestrator.class);

    /**
     * 02.1 / 02.2 脚本最长执行时长。两个脚本本身为 CPU 密集型纯 Python，
     * 实测 80 条样本 + 5 条校准集合计 ~15 秒；预留 5 分钟避免极端 PDF 卡死。
     */
    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(5);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;
    private final PromptTuneOrchestrator promptTuneOrchestrator;

    /**
     * 把 build run 选材拉到 audit-input 目录，再依次跑两个脚本。
     *
     * @return 两个脚本各自的执行摘要，调用方据此渲染折叠条
     */
    public AuditPipelineResult runFullPipeline(
            KnowledgeBases knowledgeBase,
            List<Long> materialIds,
            Path workspaceDir
    ) throws IOException, InterruptedException {
        Files.createDirectories(workspaceDir);

        Path auditInputDir = workspaceDir.resolve("input");
        Path samplesFile = workspaceDir.resolve("prompt_tuning_samples.json");
        Path auditSetFile = workspaceDir.resolve("audit_extraction_set.json");

        // 复用 PromptTuneOrchestrator.fetchInputs 把 minio 上的 normalized_docs.json 拉下来。
        // 它会自动 cleanDirectory，避免历史残留污染。
        List<ProcessExecutionResult> fetchResults = promptTuneOrchestrator.fetchInputs(
                null,
                knowledgeBase,
                materialIds,
                auditInputDir
        );
        ProcessExecutionResult firstFetchFailure = fetchResults.stream()
                .filter(r -> r.isTimedOut() || r.getExitCode() != 0)
                .findFirst()
                .orElse(null);
        if (firstFetchFailure != null) {
            String msg = firstFetchFailure.isTimedOut()
                    ? "拉取调优输入资料超时"
                    : firstSummary(firstFetchFailure.getStderr(), "拉取调优输入资料失败");
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    msg
            );
        }

        // ---- 02.1 build_prompt_tuning_samples.py ----
        ProcessExecutionResult samplesResult = runScript(
                "scripts/build_prompt_tuning_samples.py",
                List.of(
                        "--input_dir", auditInputDir.toAbsolutePath().toString(),
                        "--output_file", samplesFile.toAbsolutePath().toString()
                ),
                workspaceDir
        );
        if (samplesResult.isTimedOut() || samplesResult.getExitCode() != 0) {
            String msg = samplesResult.isTimedOut()
                    ? "调优样本集脚本执行超时"
                    : firstSummary(samplesResult.getStderr(), "调优样本集脚本执行失败");
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    msg
            );
        }

        // ---- 02.2 build_audit_extraction_set.py ----
        ProcessExecutionResult auditSetResult = runScript(
                "scripts/build_audit_extraction_set.py",
                List.of(
                        "--input_file", samplesFile.toAbsolutePath().toString(),
                        "--output_file", auditSetFile.toAbsolutePath().toString(),
                        "--preserve_existing_gold"
                ),
                workspaceDir
        );
        if (auditSetResult.isTimedOut() || auditSetResult.getExitCode() != 0) {
            String msg = auditSetResult.isTimedOut()
                    ? "校准集采样脚本执行超时"
                    : firstSummary(auditSetResult.getStderr(), "校准集采样脚本执行失败");
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    msg
            );
        }

        return AuditPipelineResult.builder()
                .samplesFile(samplesFile)
                .auditSetFile(auditSetFile)
                .samplesElapsedSeconds(samplesResult.getElapsedSeconds())
                .auditSetElapsedSeconds(auditSetResult.getElapsedSeconds())
                .build();
    }

    private ProcessExecutionResult runScript(
            String scriptRelativePath,
            List<String> extraArgs,
            Path logDir
    ) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add(scriptRelativePath);
        argv.addAll(extraArgs);

        Map<String, String> env = new LinkedHashMap<>();
        Path logFile = logDir.resolve(Path.of(scriptRelativePath).getFileName().toString() + ".log");
        Files.createDirectories(logDir);

        log.info("运行 audit 流水线脚本: {} {}", scriptRelativePath, extraArgs);
        return processRunner.run(
                argv,
                Path.of(properties.getGraphrag().getRoot()),
                env,
                STEP_TIMEOUT,
                ProcessContext.builder()
                        .operation("audit-pipeline-" + scriptRelativePath)
                        .logFile(logFile)
                        .build()
        );
    }

    private static String firstSummary(String stderr, String fallback) {
        if (stderr == null || stderr.isBlank()) {
            return fallback;
        }
        for (String line : stderr.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return fallback;
    }

    /**
     * 流水线执行结果摘要。
     */
    @Getter
    @Builder
    public static class AuditPipelineResult {
        /** 02.1 产物文件绝对路径。 */
        private final Path samplesFile;
        /** 02.2 产物文件绝对路径，含 audit_samples 数组。 */
        private final Path auditSetFile;
        private final long samplesElapsedSeconds;
        private final long auditSetElapsedSeconds;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditPipelineOrchestrator.java
git commit -m "feat(index): 新增 AuditPipelineOrchestrator 包装 02.1/02.2 脚本 (Phase 2b)"
```

---

## Task 3：AuditSampleResponseMapper Entity ↔ DTO 转换 + 单测

把 `PromptTuneAuditSamples` 转成 `AuditSampleResponse`：

1. JSON 字符串字段（`gold_entities` / `gold_relations` / `hit_signals`）→ 结构化 List。
2. `heading_path` 在 DB 里以 ` > ` 分隔的字符串存储（与 build_audit_extraction_set 输出口径对齐），DTO 中保留为同样的字符串（前端按 ` > ` 切分）。
3. `reused_from_build_run_id` + 关联 build run 的展示名 → `ReusedFromInfo` 嵌套对象。

**先写测试，再写实现。**

**Files:**
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleResponseMapperTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleResponseMapper.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSampleResponseMapperTest {

    private AuditSampleResponseMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new AuditSampleResponseMapper(objectMapper);
    }

    @Test
    void parsesJsonArrayFieldsIntoStructuredList() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities("[{\"id\":\"e1\",\"name\":\"进程\",\"type\":\"Concept\"}]");
        entity.setGoldRelations("[{\"id\":\"r1\",\"sourceEntityId\":\"e1\",\"targetEntityId\":\"e2\",\"type\":\"defined_by\"}]");
        entity.setHitSignals("[\"definition_signal\",\"formula_signal\"]");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).hasSize(1);
        assertThat(response.getGoldEntities().get(0)).containsEntry("name", "进程");
        assertThat(response.getGoldRelations()).hasSize(1);
        assertThat(response.getHitSignals()).containsExactly("definition_signal", "formula_signal");
    }

    @Test
    void emptyJsonFieldsBecomeEmptyLists() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities(null);
        entity.setGoldRelations("");
        entity.setHitSignals("[]");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).isEmpty();
        assertThat(response.getGoldRelations()).isEmpty();
        assertThat(response.getHitSignals()).isEmpty();
    }

    @Test
    void malformedJsonFieldsFallbackToEmptyList() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities("not a json");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).isEmpty();
    }

    @Test
    void reusedFromIsNullWhenReusedFromBuildRunIdAbsent() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReusedFromBuildRunId(null);

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getReusedFrom()).isNull();
    }

    @Test
    void reusedFromIsPopulatedWhenReusedFromBuildRunIdPresent() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReusedFromBuildRunId(99L);
        LocalDateTime now = LocalDateTime.of(2026, 5, 15, 10, 0);
        entity.setCreatedAt(now);

        AuditSampleResponse response = mapper.toResponse(entity, "操作系统 · 上学期构建");

        assertThat(response.getReusedFrom()).isNotNull();
        assertThat(response.getReusedFrom().getBuildRunId()).isEqualTo(99L);
        assertThat(response.getReusedFrom().getBuildRunName()).isEqualTo("操作系统 · 上学期构建");
        assertThat(response.getReusedFrom().getReusedAt()).isEqualTo(now);
    }

    @Test
    void reviewerConfidenceAndDecisionPassThrough() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReviewerDecision("completed");
        entity.setReviewerConfidence(new BigDecimal("0.85"));
        entity.setSkipReason(null);

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getReviewerDecision()).isEqualTo("completed");
        assertThat(response.getReviewerConfidence()).isEqualByComparingTo("0.85");
    }

    private PromptTuneAuditSamples newSampleEntity() {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(1L);
        e.setBuildRunId(10L);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-os-2-1");
        e.setText("进程是程序的一次执行过程。");
        e.setHeadingPath("第二章 进程管理 > 2.1 进程的定义");
        e.setPageStart(34);
        e.setPageEnd(34);
        e.setDocumentType("textbook");
        e.setAuditPriority("high");
        e.setAuditReason("覆盖 Concept + FormulaOrDefinition");
        e.setReviewerDecision("pending");
        e.setGoldStableKey("doc1|34|34|abc123");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSampleResponseMapperTest test`

Expected: 编译失败 `cannot find symbol: AuditSampleResponseMapper`。

- [ ] **Step 3: 实现 mapper**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;

import java.util.List;
import java.util.Map;

/**
 * 把 {@link PromptTuneAuditSamples} 数据库实体转成
 * {@link AuditSampleResponse} 前端响应。
 * <p>
 * 主要责任：
 * <ul>
 *   <li>把 JSON 字符串字段（gold_entities / gold_relations / hit_signals）
 *       反序列化为结构化 List；</li>
 *   <li>处理 reused_from_build_run_id → ReusedFromInfo 装配；</li>
 *   <li>对损坏 / 空字段降级为空列表，不向上抛异常以免一条坏样本拖垮全表。</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AuditSampleResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(AuditSampleResponseMapper.class);

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_OF_STRINGS =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 把 entity 转成 DTO。
     *
     * @param entity            数据库行，不能为 null
     * @param reusedBuildRunName 复用来源 build run 的展示名；当 entity.reusedFromBuildRunId 为空时此参数被忽略
     */
    public AuditSampleResponse toResponse(PromptTuneAuditSamples entity, String reusedBuildRunName) {
        AuditSampleResponse.ReusedFromInfo reusedFrom = null;
        if (entity.getReusedFromBuildRunId() != null) {
            reusedFrom = AuditSampleResponse.ReusedFromInfo.builder()
                    .buildRunId(entity.getReusedFromBuildRunId())
                    .buildRunName(reusedBuildRunName)
                    .reusedAt(entity.getCreatedAt())
                    .build();
        }
        return AuditSampleResponse.builder()
                .id(entity.getId())
                .buildRunId(entity.getBuildRunId())
                .sourceSampleId(entity.getSourceSampleId())
                .text(entity.getText())
                .headingPath(entity.getHeadingPath())
                .pageStart(entity.getPageStart())
                .pageEnd(entity.getPageEnd())
                .documentType(entity.getDocumentType())
                .auditPriority(entity.getAuditPriority())
                .auditReason(entity.getAuditReason())
                .hitSignals(parseStringList(entity.getHitSignals()))
                .goldEntities(parseMapList(entity.getGoldEntities()))
                .goldRelations(parseMapList(entity.getGoldRelations()))
                .annotationNotes(entity.getAnnotationNotes())
                .reviewerDecision(entity.getReviewerDecision())
                .reviewerConfidence(entity.getReviewerConfidence())
                .skipReason(entity.getSkipReason())
                .goldStableKey(entity.getGoldStableKey())
                .reusedFrom(reusedFrom)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, LIST_OF_MAPS);
            return parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            log.warn("解析 audit 样本 JSON 字段失败，已降级为空列表: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, LIST_OF_STRINGS);
            return parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            log.warn("解析 audit 样本字符串列表字段失败，已降级为空列表: {}", ex.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSampleResponseMapperTest test`

Expected: 6 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleResponseMapper.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleResponseMapperTest.java
git commit -m "feat(index): 新增 AuditSampleResponseMapper Entity↔DTO 转换 (Phase 2b)"
```

---

## Task 4：扩展 AuditSampleUpdateRequest 三态 PATCH 语义

把 PATCH 端点字段约束补齐：

- 字段缺失（JSON 中不存在该 key） = 不更新
- 字段为 `null` = 显式清空
- 字段有值 = 更新为该值

Jackson 反序列化时，"缺失"和"显式 null"在普通 POJO 字段上都会表现为 `null`，无法区分。本计划采用**手写 setter + `presentFields` 记录字段是否出现**的方案，从而保留 `@Valid` 校验，同时实现三态 PATCH。

同时新增 `@Pattern` 校验 `reviewerDecision` 白名单。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java`

- [ ] **Step 1: 重写 AuditSampleUpdateRequest**

替换文件内容为：

```java
package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PUT /audit-samples/{sampleId} 请求体。
 *
 * <h3>三态 PATCH 语义</h3>
 * <ul>
 *   <li>字段未出现在请求 JSON → 不更新（{@link #hasField(String)} 返回 false）；</li>
 *   <li>字段值为 {@code null} → 显式清空（hasField=true，值为 null）；</li>
 *   <li>字段值非空 → 更新为该值。</li>
 * </ul>
 *
 * <p>实现方式：已知字段全部手写 setter，每个 setter 在写入业务字段的同时把字段名记到
 * {@link #presentFields}；未知字段由 {@link JsonAnySetter} 兜底（仅记录字段名，
 * 业务上忽略）。这样 Jackson 反序列化能严格区分"缺字段"与"显式 null"两种态。</p>
 */
@Getter
public class AuditSampleUpdateRequest {

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "goldEntities",
            "goldRelations",
            "annotationNotes",
            "reviewerDecision",
            "reviewerConfidence",
            "skipReason"
    );

    private List<Map<String, Object>> goldEntities;
    private List<Map<String, Object>> goldRelations;
    private String annotationNotes;

    /** pending / in_progress / completed / skipped。 */
    @Pattern(
            regexp = "^(pending|in_progress|completed|skipped)$",
            message = "reviewerDecision 仅允许 pending / in_progress / completed / skipped"
    )
    private String reviewerDecision;

    @DecimalMin(value = "0.00", message = "置信度不得低于 0")
    @DecimalMax(value = "1.00", message = "置信度不得高于 1")
    private BigDecimal reviewerConfidence;

    private String skipReason;

    /** Jackson 反序列化期间记录"被传入的字段名"集合，用于三态判定。 */
    @JsonIgnore
    private final Set<String> presentFields = new HashSet<>();

    /**
     * Jackson 在遇到未知字段时回调；本类用它仅记录字段名，业务字段一概忽略。
     * 已知字段不会触发此方法，由下面手写的 setter 直接处理。
     */
    @JsonAnySetter
    public void recordUnknownField(String name, Object value) {
        presentFields.add(name);
    }

    // ---------- 已知字段的手写 setter：负责写入业务字段 + 记录字段出现 ----------

    public void setGoldEntities(List<Map<String, Object>> value) {
        this.goldEntities = value;
        presentFields.add("goldEntities");
    }

    public void setGoldRelations(List<Map<String, Object>> value) {
        this.goldRelations = value;
        presentFields.add("goldRelations");
    }

    public void setAnnotationNotes(String value) {
        this.annotationNotes = value;
        presentFields.add("annotationNotes");
    }

    public void setReviewerDecision(String value) {
        this.reviewerDecision = value;
        presentFields.add("reviewerDecision");
    }

    public void setReviewerConfidence(BigDecimal value) {
        this.reviewerConfidence = value;
        presentFields.add("reviewerConfidence");
    }

    public void setSkipReason(String value) {
        this.skipReason = value;
        presentFields.add("skipReason");
    }

    public boolean hasField(String name) {
        return presentFields.contains(name) && KNOWN_FIELDS.contains(name);
    }
}
```

- [ ] **Step 2: 新增 Jackson 反序列化三态测试**

在 Task 4 同一 commit 中新增测试文件 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequestTest.java`：

```java
package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSampleUpdateRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jacksonDistinguishesMissingFieldFromExplicitNull() throws Exception {
        AuditSampleUpdateRequest missing = mapper.readValue("{}", AuditSampleUpdateRequest.class);
        assertThat(missing.hasField("skipReason")).isFalse();
        assertThat(missing.hasField("reviewerDecision")).isFalse();

        AuditSampleUpdateRequest explicitNull =
                mapper.readValue("{\"skipReason\":null}", AuditSampleUpdateRequest.class);
        assertThat(explicitNull.hasField("skipReason")).isTrue();
        assertThat(explicitNull.getSkipReason()).isNull();

        AuditSampleUpdateRequest value =
                mapper.readValue("{\"skipReason\":\"no_relevant\"}", AuditSampleUpdateRequest.class);
        assertThat(value.hasField("skipReason")).isTrue();
        assertThat(value.getSkipReason()).isEqualTo("no_relevant");
    }

    @Test
    void multipleFieldsMixedPresence() throws Exception {
        String json = "{\"reviewerDecision\":\"completed\",\"reviewerConfidence\":0.9}";
        AuditSampleUpdateRequest req = mapper.readValue(json, AuditSampleUpdateRequest.class);

        assertThat(req.hasField("reviewerDecision")).isTrue();
        assertThat(req.getReviewerDecision()).isEqualTo("completed");
        assertThat(req.hasField("reviewerConfidence")).isTrue();
        assertThat(req.hasField("goldEntities")).isFalse();
        assertThat(req.hasField("skipReason")).isFalse();
    }

    @Test
    void unknownFieldsAreRecordedButIgnored() throws Exception {
        String json = "{\"unknownField\":123,\"reviewerDecision\":\"pending\"}";
        AuditSampleUpdateRequest req = mapper.readValue(json, AuditSampleUpdateRequest.class);

        assertThat(req.hasField("reviewerDecision")).isTrue();
        // unknownField 被 @JsonAnySetter 记录到 presentFields，但 hasField 只认 KNOWN_FIELDS
        assertThat(req.hasField("unknownField")).isFalse();
    }
}
```

- [ ] **Step 3: 跑测试，确认通过**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSampleUpdateRequestTest test`

Expected: 3 个测试 PASS。

- [ ] **Step 4: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequestTest.java
git commit -m "feat(dto): AuditSampleUpdateRequest 三态 PATCH + reviewerDecision 白名单 + Jackson 测试 (Phase 2b)"
```

---

## Task 5：AuditSamplePersistenceService 持久化层（事务边界） + 单测

把"删旧 + 写新 + 读取最新"封装成 `AuditSamplePersistenceService`，`replaceForBuildRun(...)` 方法标记为 `@Transactional`——本服务不调用 Python 子进程，事务时间可控（毫秒到秒级）。

`AuditSampleService`（Task 6）会在事务外调用 Python 流水线，再调本 service 完成 DB 写入。

`mergeReusedAnnotations` 的纯逻辑也放在本 service（属于"持久化前的字段填充"），方便单测。

**Files:**
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSamplePersistenceServiceTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSamplePersistenceService.java`

- [ ] **Step 1: 写失败测试**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditSamplePersistenceServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private AuditSamplePersistenceService persistence;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        persistence = new AuditSamplePersistenceService(samplesStore, new ObjectMapper());
    }

    @Test
    void mergeReusedAnnotationsCopiesGoldFieldsWhenStableKeyHits() {
        PromptTuneAuditSamples newSample = sample(null, 20L, "doc1|34|34|abc", "pending");
        newSample.setGoldEntities("[]");

        PromptTuneAuditSamples historical = sample(99L, 5L, "doc1|34|34|abc", "completed");
        historical.setGoldEntities("[{\"id\":\"e1\",\"name\":\"进程\"}]");
        historical.setGoldRelations("[]");
        historical.setAnnotationNotes("人工备注");

        when(samplesStore.findCompletedByStableKeys(100L, List.of("doc1|34|34|abc")))
                .thenReturn(List.of(historical));

        persistence.mergeReusedAnnotations(100L, 20L, List.of(newSample));

        assertThat(newSample.getReusedFromBuildRunId()).isEqualTo(5L);
        assertThat(newSample.getGoldEntities()).contains("进程");
        assertThat(newSample.getReviewerDecision()).isEqualTo("completed");
        assertThat(newSample.getAnnotationNotes()).isEqualTo("人工备注");
    }

    @Test
    void mergeReusedAnnotationsIgnoresHistoryFromSameBuildRun() {
        PromptTuneAuditSamples newSample = sample(null, 20L, "doc1|34|34|abc", "pending");
        PromptTuneAuditSamples sameBuildRun = sample(7L, 20L, "doc1|34|34|abc", "completed");

        when(samplesStore.findCompletedByStableKeys(100L, List.of("doc1|34|34|abc")))
                .thenReturn(List.of(sameBuildRun));

        persistence.mergeReusedAnnotations(100L, 20L, List.of(newSample));

        assertThat(newSample.getReusedFromBuildRunId()).isNull();
    }

    @Test
    void mergeReusedAnnotationsLeavesNewSampleWhenNoHistoryHit() {
        PromptTuneAuditSamples newSample = sample(null, 20L, "doc1|34|34|abc", "pending");
        when(samplesStore.findCompletedByStableKeys(anyLong(), anyList())).thenReturn(List.of());

        persistence.mergeReusedAnnotations(100L, 20L, List.of(newSample));

        assertThat(newSample.getReusedFromBuildRunId()).isNull();
    }

    @Test
    void mergeReusedAnnotationsSkipsSamplesWithoutStableKey() {
        PromptTuneAuditSamples newSample = sample(null, 20L, null, "pending");

        persistence.mergeReusedAnnotations(100L, 20L, List.of(newSample));

        verify(samplesStore, never()).findCompletedByStableKeys(anyLong(), anyList());
        assertThat(newSample.getReusedFromBuildRunId()).isNull();
    }

    @Test
    void replaceForBuildRunParsesAuditJsonRemovesOldRowsAndSavesNewRows() throws Exception {
        Path file = java.nio.file.Files.createTempFile("audit_extraction_set", ".json");
        java.nio.file.Files.writeString(file, """
            {
              "audit_samples": [
                {
                  "source_sample_id": "s1",
                  "text": "进程是程序的一次执行过程。",
                  "heading_path": ["第二章", "2.1 进程"],
                  "page_start": 1,
                  "page_end": 1,
                  "audit_priority": "high",
                  "hit_signals": ["definition_signal"],
                  "gold_entities": [],
                  "gold_relations": [],
                  "reviewer_decision": "pending",
                  "gold_stable_key": "doc|1|1|abc"
                }
              ]
            }
            """);

        org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns buildRun = new org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns();
        buildRun.setId(20L);
        buildRun.setKnowledgeBaseId(100L);

        when(samplesStore.findCompletedByStableKeys(anyLong(), anyList())).thenReturn(List.of());
        when(samplesStore.saveBatch(anyList())).thenReturn(true);
        PromptTuneAuditSamples returned = new PromptTuneAuditSamples();
        returned.setId(1L);
        returned.setBuildRunId(20L);
        when(samplesStore.listByBuildRunId(20L)).thenReturn(List.of(returned));

        List<PromptTuneAuditSamples> result = persistence.replaceForBuildRun(buildRun, file);

        verify(samplesStore).remove(any());
        verify(samplesStore).saveBatch(org.mockito.ArgumentMatchers.argThat(rows -> rows.size() == 1));
        verify(samplesStore).listByBuildRunId(20L);
        assertThat(result).hasSize(1);

        java.nio.file.Files.deleteIfExists(file);
    }

    private PromptTuneAuditSamples sample(Long id, Long buildRunId, String stableKey, String decision) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setBuildRunId(buildRunId);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-x");
        e.setText("文本");
        e.setHeadingPath("章 > 节");
        e.setPageStart(1);
        e.setPageEnd(1);
        e.setAuditPriority("medium");
        e.setReviewerDecision(decision);
        e.setGoldStableKey(stableKey);
        return e;
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSamplePersistenceServiceTest test`

Expected: 编译失败 `cannot find symbol: AuditSamplePersistenceService`。

- [ ] **Step 3: 实现 service**

```java
package org.ysu.ckqaback.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 02 步标注样本持久化层。
 * <p>
 * 责任聚焦于 DB 操作 + 跨 build run 历史标注合并的<strong>纯内存逻辑</strong>，
 * 不调用任何外部进程；类上 {@code @Transactional} 是安全的，事务时间可控。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuditSamplePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AuditSamplePersistenceService.class);

    private final PromptTuneAuditSamplesService samplesStore;
    private final ObjectMapper objectMapper;

    /**
     * 解析 audit_extraction_set.json，删除本 build run 已有样本，落新样本。
     * 返回新样本（重新查表以获取 DB 自增 id）。
     * <p>本方法 <strong>不</strong> 在 controller 线程中调用 Python 子进程。</p>
     */
    @Transactional
    public List<PromptTuneAuditSamples> replaceForBuildRun(KnowledgeBaseBuildRuns buildRun, Path auditSetFile) {
        Map<String, Object> payload;
        try {
            String json = Files.readString(auditSetFile, StandardCharsets.UTF_8);
            payload = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "解析校准集 JSON 失败: " + ex.getMessage()
            );
        }

        Object samplesNode = payload.get("audit_samples");
        if (!(samplesNode instanceof List<?> rawList)) {
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "校准集 JSON 缺少 audit_samples 数组"
            );
        }

        // 清理本 build run 现有样本
        LambdaQueryWrapper<PromptTuneAuditSamples> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRun.getId());
        samplesStore.remove(deleteWrapper);

        List<PromptTuneAuditSamples> newRows = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            newRows.add(buildEntityFromAuditJson(buildRun, map));
        }

        // 历史复用合并
        mergeReusedAnnotations(buildRun.getKnowledgeBaseId(), buildRun.getId(), newRows);

        if (!newRows.isEmpty()) {
            samplesStore.saveBatch(newRows);
        }

        // 重新查表以获取 DB 自增 id（saveBatch 后 entity.id 可能未填充）
        return samplesStore.listByBuildRunId(buildRun.getId());
    }

    /**
     * 跨 build run 历史标注复用合并。
     * <p>
     * 命中条件：
     * <ul>
     *   <li>{@code gold_stable_key} 非空；</li>
     *   <li>同一 knowledgeBaseId 下其他 build run 中已 completed 的同 stable_key 样本；</li>
     *   <li>不能命中"自身 build run"。</li>
     * </ul>
     * </p>
     */
    public void mergeReusedAnnotations(Long knowledgeBaseId, Long currentBuildRunId, List<PromptTuneAuditSamples> samples) {
        List<String> stableKeys = samples.stream()
                .map(PromptTuneAuditSamples::getGoldStableKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if (stableKeys.isEmpty()) {
            return;
        }
        List<PromptTuneAuditSamples> historical =
                samplesStore.findCompletedByStableKeys(knowledgeBaseId, stableKeys);
        if (historical == null || historical.isEmpty()) {
            return;
        }
        Map<String, PromptTuneAuditSamples> bestByKey = new HashMap<>();
        for (PromptTuneAuditSamples row : historical) {
            if (Objects.equals(row.getBuildRunId(), currentBuildRunId)) {
                continue;
            }
            String key = row.getGoldStableKey();
            PromptTuneAuditSamples existing = bestByKey.get(key);
            if (existing == null || isMoreRecent(row, existing)) {
                bestByKey.put(key, row);
            }
        }
        for (PromptTuneAuditSamples target : samples) {
            String key = target.getGoldStableKey();
            if (key == null || key.isBlank()) continue;
            PromptTuneAuditSamples src = bestByKey.get(key);
            if (src == null) continue;
            target.setGoldEntities(src.getGoldEntities());
            target.setGoldRelations(src.getGoldRelations());
            target.setAnnotationNotes(src.getAnnotationNotes());
            target.setReviewerDecision(src.getReviewerDecision());
            target.setReviewerConfidence(src.getReviewerConfidence());
            target.setReusedFromBuildRunId(src.getBuildRunId());
        }
    }

    /**
     * 检查本 build run 是否已经存在非 pending 样本。Task 6 用于 force 防误删。
     */
    public boolean hasNonPendingSamples(Long buildRunId) {
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRunId)
                .ne(PromptTuneAuditSamples::getReviewerDecision, "pending");
        return samplesStore.count(wrapper) > 0;
    }

    private PromptTuneAuditSamples buildEntityFromAuditJson(KnowledgeBaseBuildRuns buildRun, Map<String, Object> raw) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setBuildRunId(buildRun.getId());
        e.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        e.setSourceSampleId(asString(raw.get("source_sample_id")));
        e.setText(asString(raw.get("text")));
        e.setHeadingPath(joinHeadingPath(raw.get("heading_path")));
        e.setPageStart(asInteger(raw.get("page_start")));
        e.setPageEnd(asInteger(raw.get("page_end")));
        e.setDocumentType(asString(raw.get("document_type")));
        e.setAuditPriority(coalescePriority(asString(raw.get("audit_priority"))));
        e.setAuditReason(asString(raw.get("audit_reason")));
        e.setHitSignals(serializeJson(raw.get("hit_signals")));
        e.setGoldEntities(serializeJson(coalesceList(raw.get("gold_entities"))));
        e.setGoldRelations(serializeJson(coalesceList(raw.get("gold_relations"))));
        e.setAnnotationNotes(asString(raw.get("annotation_notes")));
        Object decision = raw.get("reviewer_decision");
        e.setReviewerDecision(decision == null || asString(decision).isBlank() ? "pending" : asString(decision));
        Object confidence = raw.get("reviewer_confidence");
        if (confidence instanceof Number n) {
            e.setReviewerConfidence(BigDecimal.valueOf(n.doubleValue()));
        } else if (confidence instanceof String s && !s.isBlank()) {
            try {
                e.setReviewerConfidence(new BigDecimal(s));
            } catch (NumberFormatException ignored) {
                // build_audit_extraction_set 默认置空字符串，忽略
            }
        }
        e.setGoldStableKey(asString(raw.get("gold_stable_key")));
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private String serializeJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化 JSON 字段失败: {}", ex.getMessage());
            return "[]";
        }
    }

    private static boolean isMoreRecent(PromptTuneAuditSamples a, PromptTuneAuditSamples b) {
        LocalDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
        LocalDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
        if (ta == null) return false;
        if (tb == null) return true;
        return ta.isAfter(tb);
    }

    private static Object coalesceList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String coalescePriority(String priority) {
        if (priority == null) return "medium";
        return switch (priority) {
            case "high", "medium", "low" -> priority;
            default -> "medium";
        };
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String joinHeadingPath(Object headingPath) {
        if (headingPath instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining(" > "));
        }
        return asString(headingPath);
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSamplePersistenceServiceTest test`

Expected: 5 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSamplePersistenceService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSamplePersistenceServiceTest.java
git commit -m "feat(index): 新增 AuditSamplePersistenceService 持久化层与历史合并 (Phase 2b)"
```

---

## Task 6：AuditSampleService 业务编排（无事务）+ 单测

负责的事：

1. `regenerateAuditSet(buildRunId, force)`：**无事务**——查 build run、跑 Python 流水线，再调 `AuditSamplePersistenceService.replaceForBuildRun(...)`（事务）写库。`force=false` 时若本 build run 有非 pending 样本，抛 `BUILD_RUN_HAS_ANNOTATED_SAMPLES`。
2. `listSamples(buildRunId)`：直接查 DB + 装配 `reusedFrom`。
3. `updateSample(buildRunId, sampleId, request)`：按 PATCH 三态语义合并字段。
4. `triggerSampleGeneration(buildRunId)`：**保留 501**（v2 删除其真实实现，避免与 `regenerateAuditSet` 行为重叠；本 service 不提供该方法）。

**Files:**
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleServiceTest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleService.java`

- [ ] **Step 1: 写失败测试**

测试集中在两个边界：`updateSample` 的三态字段合并（缺失 / null / 有值），与 `regenerateAuditSet` 的 force 防误删校验。Python 子进程端到端的真实路径留给 Task 8 集成验证。

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditSampleServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private KnowledgeBaseBuildRunsService buildRunsStore;
    private KnowledgeBasesService knowledgeBasesService;
    private AuditPipelineOrchestrator orchestrator;
    private BuildRunWorkspaceService workspaceService;
    private AuditSamplePersistenceService persistence;
    private AuditSampleResponseMapper responseMapper;
    private AuditSampleService service;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        knowledgeBasesService = mock(KnowledgeBasesService.class);
        orchestrator = mock(AuditPipelineOrchestrator.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        persistence = mock(AuditSamplePersistenceService.class);
        responseMapper = new AuditSampleResponseMapper(new ObjectMapper());
        service = new AuditSampleService(
                samplesStore,
                buildRunsStore,
                knowledgeBasesService,
                orchestrator,
                workspaceService,
                persistence,
                responseMapper,
                new ObjectMapper()
        );
    }

    @Test
    void updateSampleAppliesValueWhenFieldPresent() {
        PromptTuneAuditSamples existing = newSampleEntity(1L, 10L);
        existing.setReviewerDecision("in_progress");
        when(samplesStore.getById(1L)).thenReturn(existing);
        when(samplesStore.updateById(any(PromptTuneAuditSamples.class))).thenReturn(true);

        AuditSampleUpdateRequest req = new AuditSampleUpdateRequest();
        req.setReviewerDecision("completed");
        req.setReviewerConfidence(new BigDecimal("0.92"));

        AuditSampleResponse response = service.updateSample(10L, 1L, req);

        assertThat(response.getReviewerDecision()).isEqualTo("completed");
        assertThat(response.getReviewerConfidence()).isEqualByComparingTo("0.92");
    }

    @Test
    void updateSampleClearsFieldWhenNullProvidedAndFieldPresent() {
        PromptTuneAuditSamples existing = newSampleEntity(1L, 10L);
        existing.setSkipReason("旧理由");
        when(samplesStore.getById(1L)).thenReturn(existing);
        when(samplesStore.updateById(any(PromptTuneAuditSamples.class))).thenReturn(true);

        AuditSampleUpdateRequest req = new AuditSampleUpdateRequest();
        req.setSkipReason(null);  // 显式 null = 清空

        service.updateSample(10L, 1L, req);

        assertThat(existing.getSkipReason()).isNull();
    }

    @Test
    void updateSampleLeavesFieldWhenAbsent() {
        PromptTuneAuditSamples existing = newSampleEntity(1L, 10L);
        existing.setSkipReason("保留");
        existing.setAnnotationNotes("保留备注");
        when(samplesStore.getById(1L)).thenReturn(existing);
        when(samplesStore.updateById(any(PromptTuneAuditSamples.class))).thenReturn(true);

        AuditSampleUpdateRequest req = new AuditSampleUpdateRequest();
        // 仅触发 reviewerDecision 字段，不动 skipReason / annotationNotes
        req.setReviewerDecision("completed");

        service.updateSample(10L, 1L, req);

        assertThat(existing.getSkipReason()).isEqualTo("保留");
        assertThat(existing.getAnnotationNotes()).isEqualTo("保留备注");
    }

    @Test
    void updateSampleRejectsWrongBuildRunId() {
        PromptTuneAuditSamples existing = newSampleEntity(1L, 10L);
        when(samplesStore.getById(1L)).thenReturn(existing);

        AuditSampleUpdateRequest req = new AuditSampleUpdateRequest();
        req.setReviewerDecision("completed");

        assertThatThrownBy(() -> service.updateSample(/*buildRunId*/ 999L, 1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND.getCode());
    }

    @Test
    void updateSampleThrowsWhenSampleMissing() {
        when(samplesStore.getById(1L)).thenReturn(null);

        AuditSampleUpdateRequest req = new AuditSampleUpdateRequest();
        req.setReviewerDecision("completed");

        assertThatThrownBy(() -> service.updateSample(10L, 1L, req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void regenerateAuditSetRejectsWhenAnnotatedSamplesExistAndNotForced() {
        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setId(20L);
        buildRun.setKnowledgeBaseId(100L);
        buildRun.setSelectedMaterialIds("[1,2]");
        buildRun.setWorkspaceUri("user_1/kb_100/build_20");
        when(buildRunsStore.getRequiredById(20L)).thenReturn(buildRun);
        when(persistence.hasNonPendingSamples(20L)).thenReturn(true);

        assertThatThrownBy(() -> service.regenerateAuditSet(20L, /*force*/ false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.BUILD_RUN_HAS_ANNOTATED_SAMPLES.getCode());
    }

    private PromptTuneAuditSamples newSampleEntity(Long id, Long buildRunId) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setBuildRunId(buildRunId);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-x");
        e.setText("文本");
        e.setHeadingPath("章 > 节");
        e.setPageStart(1);
        e.setPageEnd(1);
        e.setAuditPriority("medium");
        e.setReviewerDecision("pending");
        return e;
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSampleServiceTest test`

Expected: 编译失败 `cannot find symbol: AuditSampleService` 或构造方法签名不匹配。

- [ ] **Step 3: 实现 service**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 02 步标注业务编排（无事务）。
 * <p>
 * <strong>事务边界：</strong> 本 service <em>不</em> 加 {@code @Transactional}。
 * Python 子进程相关的方法（{@link #regenerateAuditSet}）显式在事务外完成 IO，
 * 仅把"删旧 + 写新"委派给 {@link AuditSamplePersistenceService} 完成。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuditSampleService {

    private static final Logger log = LoggerFactory.getLogger(AuditSampleService.class);

    private final PromptTuneAuditSamplesService samplesStore;
    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final KnowledgeBasesService knowledgeBasesService;
    private final AuditPipelineOrchestrator orchestrator;
    private final BuildRunWorkspaceService workspaceService;
    private final AuditSamplePersistenceService persistence;
    private final AuditSampleResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 触发完整 02.1 + 02.2 流水线，并把生成的 audit 样本落库到本 build run。
     *
     * @param force 为 false 时，若本 build run 已有 reviewer_decision != 'pending' 的样本，
     *              直接抛 {@link ApiResultCode#BUILD_RUN_HAS_ANNOTATED_SAMPLES} 拒绝执行；
     *              为 true 时直接覆盖现有样本（"删旧建新"）。
     */
    public List<AuditSampleResponse> regenerateAuditSet(Long buildRunId, boolean force) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);

        if (!force && persistence.hasNonPendingSamples(buildRunId)) {
            throw new BusinessException(
                    ApiResultCode.BUILD_RUN_HAS_ANNOTATED_SAMPLES,
                    HttpStatus.CONFLICT
            );
        }

        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(buildRun.getKnowledgeBaseId());
        List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
        if (materialIds.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "本次构建尚未选择任何资料"
            );
        }
        Path workspaceDir = workspaceService.resolve(buildRun.getWorkspaceUri()).resolve("prompt").resolve("audit");

        AuditPipelineOrchestrator.AuditPipelineResult pipelineResult;
        try {
            pipelineResult = orchestrator.runFullPipeline(knowledgeBase, materialIds, workspaceDir);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "标注流水线执行异常: " + ex.getMessage()
            );
        }

        // 事务在 persistence service 内部完成（@Transactional 在该类）。
        List<PromptTuneAuditSamples> persisted =
                persistence.replaceForBuildRun(buildRun, pipelineResult.getAuditSetFile());
        return assembleResponses(persisted);
    }

    /**
     * 列出本 build run 的所有 audit 样本，按 audit_priority 升序（high 优先）+ source_sample_id。
     * <p>排序由 {@link PromptTuneAuditSamplesService#listByBuildRunId} 完成（2a 已实现）。</p>
     */
    public List<AuditSampleResponse> listSamples(Long buildRunId) {
        buildRunsStore.getRequiredById(buildRunId);
        List<PromptTuneAuditSamples> rows = samplesStore.listByBuildRunId(buildRunId);
        return assembleResponses(rows);
    }

    /**
     * 更新单条 audit 样本。三态语义见 {@link AuditSampleUpdateRequest}。
     */
    public AuditSampleResponse updateSample(Long buildRunId, Long sampleId, AuditSampleUpdateRequest request) {
        PromptTuneAuditSamples sample = samplesStore.getById(sampleId);
        if (sample == null || !Objects.equals(sample.getBuildRunId(), buildRunId)) {
            throw new BusinessException(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        applyUpdate(sample, request);
        sample.setUpdatedAt(LocalDateTime.now());
        samplesStore.updateById(sample);
        return assembleResponse(sample);
    }

    // ============================================================
    // 内部实现
    // ============================================================

    private void applyUpdate(PromptTuneAuditSamples sample, AuditSampleUpdateRequest request) {
        // 三态：hasField=false 不动；hasField=true + 值为 null 清空；hasField=true + 有值 更新。
        if (request.hasField("goldEntities")) {
            sample.setGoldEntities(serializeJson(request.getGoldEntities()));
        }
        if (request.hasField("goldRelations")) {
            sample.setGoldRelations(serializeJson(request.getGoldRelations()));
        }
        if (request.hasField("annotationNotes")) {
            sample.setAnnotationNotes(request.getAnnotationNotes());
        }
        if (request.hasField("reviewerDecision")) {
            // @Pattern 只校验非 null 值；显式传 null 时按"清空决策"处理为 pending
            String value = request.getReviewerDecision();
            sample.setReviewerDecision(value == null ? "pending" : value);
        }
        if (request.hasField("reviewerConfidence")) {
            sample.setReviewerConfidence(request.getReviewerConfidence());
        }
        if (request.hasField("skipReason")) {
            sample.setSkipReason(request.getSkipReason());
        }
    }

    private List<AuditSampleResponse> assembleResponses(List<PromptTuneAuditSamples> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Long> reusedIds = rows.stream()
                .map(PromptTuneAuditSamples::getReusedFromBuildRunId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> nameById = new LinkedHashMap<>();
        if (!reusedIds.isEmpty()) {
            List<KnowledgeBaseBuildRuns> reusedBuildRuns = buildRunsStore.listByIds(reusedIds);
            for (KnowledgeBaseBuildRuns b : reusedBuildRuns) {
                nameById.put(b.getId(), formatBuildRunDisplayName(b));
            }
        }
        return rows.stream()
                .map(row -> responseMapper.toResponse(row, nameById.get(row.getReusedFromBuildRunId())))
                .toList();
    }

    private AuditSampleResponse assembleResponse(PromptTuneAuditSamples row) {
        return assembleResponses(List.of(row)).get(0);
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ex) {
            log.warn("解析 selectedMaterialIds JSON 失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private String serializeJson(Object value) {
        if (value == null) return "[]";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("序列化 JSON 字段失败: {}", ex.getMessage());
            return "[]";
        }
    }

    private static String formatBuildRunDisplayName(KnowledgeBaseBuildRuns b) {
        if (b.getBuildVersion() != null && !b.getBuildVersion().isBlank()) {
            return b.getBuildVersion();
        }
        return "构建 #" + b.getId() + (b.getCreatedAt() != null ? " · " + b.getCreatedAt() : "");
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AuditSampleServiceTest test`

Expected: 6 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditSampleService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditSampleServiceTest.java
git commit -m "feat(index): 新增 AuditSampleService 业务编排（事务边界外）(Phase 2b)"
```

---

## Task 7：替换 Controller 占位为真实实现

把 2a 中的 3 个 501 占位（`generateAuditSet` / `listAuditSamples` / `updateAuditSample`）改为调用 `AuditSampleService`，并支持 `?force=true` 查询参数。`triggerPromptTuneSamples` 与 `requestAuditSampleAiSuggestions` **保持 501 不动**。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1: 注入 `AuditSampleService` 并 import `ApiResponseUtils` / `RequestParam`**

打开文件，确认或追加 imports：

```java
import org.springframework.web.bind.annotation.RequestParam;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.index.AuditSampleService;
```

在已有 `private final ...` 字段块（如果没有则在类签名之下，方法之前新增）中加入：

```java
    private final AuditSampleService auditSampleService;
```

`@RequiredArgsConstructor` 会负责注入。

- [ ] **Step 2: 替换 02 步前 4 个端点的方法体**

定位锚点：`// 02 步：构建准备材料` 注释下的 5 个方法。

**`triggerPromptTuneSamples` 保持 501（v2 删除其真实实现）**，仅在 javadoc 中说明：

```java
    /**
     * 触发"仅 02.1 生成调优样本集"。Phase 2b v2 不实现：
     * 现有前端始终通过 {@link #generateAuditSet} 串跑 02.1+02.2，
     * 该端点暂保留 501 占位以维持 API 兼容；如未来需要拆分单跑 02.1，
     * 在 service 层新增独立方法即可。
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/prompt-tune-samples")
    public ApiResponse<PipelineStepResponse> triggerPromptTuneSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }
```

`generateAuditSet` 替换为：

```java
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-set")
    public ApiResponse<List<AuditSampleResponse>> generateAuditSet(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        return ApiResponseUtils.success(auditSampleService.regenerateAuditSet(buildRunId, force));
    }
```

`listAuditSamples` 替换为：

```java
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples")
    public ApiResponse<List<AuditSampleResponse>> listAuditSamples(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(auditSampleService.listSamples(buildRunId));
    }
```

`updateAuditSample` 替换为：

```java
    @PutMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}")
    public ApiResponse<AuditSampleResponse> updateAuditSample(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("sampleId") @Positive(message = "sampleId必须大于0") Long sampleId,
            @Valid @RequestBody AuditSampleUpdateRequest request
    ) {
        return ApiResponseUtils.success(auditSampleService.updateSample(buildRunId, sampleId, request));
    }
```

- [ ] **Step 3: 更新 `requestAuditSampleAiSuggestions` 的 javadoc**

定位 5 个 02 步端点中第 5 个：

```java
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<Map<String, Object>> requestAuditSampleAiSuggestions(
```

在该方法前补充 javadoc：

```java
    /**
     * AI 预填实体/关系候选（智能能力 A）。Phase 3 落地，本期保留 501 占位。
     */
```

- [ ] **Step 4: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend/ckqa-back && mvn -q test`

Expected: 包含本期新增 mapper / persistence / service 测试在内全部 PASS，无回归。

- [ ] **Step 6: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(controller): 替换 02 步 3 端点占位为真实实现 + force 参数 (Phase 2b)"
```

---

## Task 8：手工冒烟验证后端 API（强烈推荐）

后端三件套写完后跑一次端到端冒烟，验证三态 PATCH、force 防误删、历史复用都按预期工作。

**前置：** infra（MySQL / MinIO / GraphRAG conda env）都可用；本机已有一个 `currentStage = 'prompt'` 的 build run，且 `selected_material_ids` 是 MinIO 中已存好的资料。

- [ ] **Step 1: 启动后端**

Run: `cd backend/ckqa-back && mvn -q spring-boot:run`

放后台或新终端启动。等 "Started CkqaBackApplication" 日志。

- [ ] **Step 2: 触发 audit-set 流水线（首次，无 force）**

替换 `<JWT>` 与 `<BUILD_RUN_ID>`：

```bash
curl -s -X POST "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-set" \
  -H "Authorization: Bearer <JWT>" | jq '{code, message, count: (.data | length)}'
```

Expected: `code = 200`，`count` 大于 0。

- [ ] **Step 3: 列出样本，记录第一个 sampleId**

```bash
curl -s "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-samples" \
  -H "Authorization: Bearer <JWT>" | jq '.data[0] | {id, sourceSampleId, auditPriority, reviewerDecision}'
```

记下 `id` 字段值，下面用 `<SAMPLE_ID>` 代替。

- [ ] **Step 4: 标注一条样本（PATCH 三态：传入 + 显式 null）**

```bash
curl -s -X PUT "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-samples/<SAMPLE_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "goldEntities": [{"id":"e1","name":"测试实体","type":"Concept"}],
    "reviewerDecision": "completed",
    "reviewerConfidence": 0.9,
    "skipReason": null
  }' | jq '.data | {reviewerDecision, goldEntitiesLen: (.goldEntities | length), skipReason}'
```

Expected: `reviewerDecision = "completed"`，`goldEntitiesLen = 1`，`skipReason = null`。

- [ ] **Step 5: 验证缺失字段不更新（PATCH 三态：缺失）**

```bash
curl -s -X PUT "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-samples/<SAMPLE_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"reviewerConfidence": 0.85}' | jq '.data | {reviewerDecision, reviewerConfidence}'
```

Expected: `reviewerDecision` 仍为 `"completed"`（未在请求中出现，应保留），`reviewerConfidence = 0.85`。

- [ ] **Step 6: 验证非法 reviewerDecision 被拒**

```bash
curl -s -X PUT "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-samples/<SAMPLE_ID>" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"reviewerDecision": "donee"}' | jq '{code, message}'
```

Expected: 返回 4001（VALIDATION_ERROR），message 含"reviewerDecision 仅允许..."。

- [ ] **Step 7: 验证 force 防误删**

```bash
# 没带 force，本 build run 已有 completed 样本，应被拒
curl -s -X POST "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-set" \
  -H "Authorization: Bearer <JWT>" | jq '{code, message}'
```

Expected: `code = 4103`，`message = "当前构建已有人工标注，确认覆盖请重试并设置 force=true"`。

- [ ] **Step 8: 强制重新生成**

```bash
curl -s -X POST "http://localhost:8080/api/v1/knowledge-base-build-runs/<BUILD_RUN_ID>/audit-set?force=true" \
  -H "Authorization: Bearer <JWT>" | jq '{code, count: (.data | length)}'
```

Expected: `code = 200`，count 大于 0。注意：先前的 `<SAMPLE_ID>` 已被删除，新批次有新 id；reusedFrom 字段如果是同一份资料，可能为该 build run id 之前的"自身上一次"——但因为 mergeReusedAnnotations 排除自身 build run，这里 `reusedFrom` 应仍为 null。

- [ ] **Step 9: 验证 DB 行**

```bash
mysql -uroot -p ocqa -e "SELECT COUNT(*) AS total, SUM(CASE WHEN reviewer_decision = 'pending' THEN 1 ELSE 0 END) AS pending FROM prompt_tune_audit_samples WHERE build_run_id = <BUILD_RUN_ID>;"
```

Expected: `total > 0`，`pending = total`（force 重新生成后所有样本都回到 pending）。

- [ ] **Step 10: 关闭后端**

按 Ctrl+C，或 kill 后台进程。

- [ ] **Step 11: 不需要 commit**

至此后端冒烟全部通过。

---

## Task 9：前端 API 桩 generateAuditSet 增加 force 参数

`prompt-tune-pipeline.js` 中 `generateAuditSet(buildRunId)` 增加可选第二参数 `{ force }`。

**Files:**
- Modify: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`

- [ ] **Step 1: 替换 generateAuditSet 函数**

定位锚点：

```javascript
export async function generateAuditSet(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-set`,
  ))
}
```

替换为（增加向后兼容检测：如果第二参数是 axios-like 对象，视为旧 `client` 参数）：

```javascript
export async function generateAuditSet(buildRunId, options = {}, client = http) {
  // 向后兼容：旧签名 generateAuditSet(id, mockClient) 中第二参数是 axios 实例
  if (options && typeof options.post === 'function') {
    client = options
    options = {}
  }
  const { force = false } = options
  const url = `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-set`
                + (force ? '?force=true' : '')
  return unwrapApiResponse(await client.post(url))
}
```

这样旧测试里 `generateAuditSet(id, mockClient)` 不会炸。

Run: `grep -rn "generateAuditSet(" frontend/apps/admin-app/src --include='*.vue' --include='*.js'`

确认无编译错误即可。

- [ ] **Step 2: 编译验证（前端 lint）**

Run: `cd frontend/apps/admin-app && npx eslint src/api/prompt-tune-pipeline.js`

Expected: 无错误。

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js
git commit -m "feat(api): generateAuditSet 增加 force 参数 (Phase 2b)"
```

---

## Task 10：前端形态转换纯函数 + 单测

把"API 响应 ↔ 组件本地 sample shape"和"本地编辑 → API payload"抽到 `prepare-step-api.js` 纯函数。

**关键变化（v2）：** `localSampleToUpdatePayload` 支持三态——通过 `clearFields: ['xxx']` 显式声明要清空的字段，对应到 JSON 中的 `null`。这样 PATCH 语义对齐后端。

**响应字段映射：**

| API (`AuditSampleResponse`) | 组件本地 (`sample`) | 备注 |
| --- | --- | --- |
| `id` | `id` | API 返回 number；组件用 string 化方便 mock 比较 |
| `sourceSampleId` | `sourceSampleId` | 1:1 |
| `text` | `text` | 1:1 |
| `headingPath` (string ` > ` 分隔) | `headingPath` (Array<string>) | 前端按 ` > ` split，去空 |
| `pageStart` / `pageEnd` | 同名 | 1:1 |
| `auditPriority` | `auditPriority` | 1:1，`high`/`medium`/`low` |
| `auditReason` | `auditReason` | 1:1 |
| `hitSignals` | `hitSignals` | 1:1 |
| `goldEntities` | `goldEntities` | 元素结构 1:1 |
| `goldRelations` | `goldRelations` | 1:1 |
| `annotationNotes` | `annotationNotes` | 1:1 |
| `reviewerDecision` | `status` | `pending`→`not_started`，`in_progress`→`in_progress`，`completed`→`done`，`skipped`→`skipped` |
| `reviewerConfidence` | `reviewerConfidence` | 1:1 |
| `skipReason` | `skipReason` | 1:1 |
| `reusedFrom` | `reusedFrom` | 1:1 |
| - | `aiSuggestedEntities` / `aiSuggestedRelations` | API 不返回；前端置空数组 |

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prepare-step-api.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/prepare-step-api.js`

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  apiSampleToLocal,
  localSampleToUpdatePayload,
  REVIEWER_DECISION_TO_STATUS,
  STATUS_TO_REVIEWER_DECISION,
} from '../../views/pages/prompt-builder/prepare-step-api.js'

describe('prepare-step-api · apiSampleToLocal', () => {
  const baseApi = {
    id: 42,
    sourceSampleId: 'sample-os-2-1',
    text: '进程是程序的一次执行过程。',
    headingPath: '第二章 进程管理 > 2.1 进程的定义',
    pageStart: 34,
    pageEnd: 34,
    auditPriority: 'high',
    auditReason: '覆盖 Concept',
    hitSignals: ['definition_signal'],
    goldEntities: [{ id: 'e1', name: '进程', type: 'Concept' }],
    goldRelations: [],
    annotationNotes: '',
    reviewerDecision: 'in_progress',
    reviewerConfidence: 0.7,
    skipReason: null,
    goldStableKey: 'doc1|34|34|abc',
    reusedFrom: null,
  }

  it('splits headingPath on " > " into array', () => {
    const local = apiSampleToLocal(baseApi)
    assert.deepEqual(local.headingPath, ['第二章 进程管理', '2.1 进程的定义'])
  })

  it('maps reviewerDecision to status', () => {
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: 'pending' }).status, 'not_started')
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: 'in_progress' }).status, 'in_progress')
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: 'completed' }).status, 'done')
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: 'skipped' }).status, 'skipped')
  })

  it('falls back to not_started when reviewerDecision unknown', () => {
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: 'weird' }).status, 'not_started')
    assert.equal(apiSampleToLocal({ ...baseApi, reviewerDecision: null }).status, 'not_started')
  })

  it('always provides empty aiSuggestedEntities / aiSuggestedRelations arrays', () => {
    const local = apiSampleToLocal(baseApi)
    assert.deepEqual(local.aiSuggestedEntities, [])
    assert.deepEqual(local.aiSuggestedRelations, [])
  })

  it('coerces id to string for stable Vue :key usage', () => {
    const local = apiSampleToLocal(baseApi)
    assert.equal(typeof local.id, 'string')
    assert.equal(local.id, '42')
  })

  it('handles null gold arrays as empty arrays', () => {
    const local = apiSampleToLocal({ ...baseApi, goldEntities: null, goldRelations: null, hitSignals: null })
    assert.deepEqual(local.goldEntities, [])
    assert.deepEqual(local.goldRelations, [])
    assert.deepEqual(local.hitSignals, [])
  })

  it('handles empty headingPath', () => {
    assert.deepEqual(apiSampleToLocal({ ...baseApi, headingPath: '' }).headingPath, [])
    assert.deepEqual(apiSampleToLocal({ ...baseApi, headingPath: null }).headingPath, [])
  })

  it('preserves reusedFrom when present', () => {
    const local = apiSampleToLocal({
      ...baseApi,
      reusedFrom: { buildRunId: 99, buildRunName: '上学期', reusedAt: '2026-04-12T14:23:54' },
    })
    assert.equal(local.reusedFrom.buildRunName, '上学期')
  })
})

describe('prepare-step-api · localSampleToUpdatePayload (三态)', () => {
  const localSample = {
    id: '42',
    goldEntities: [{ id: 'e1', name: '进程', type: 'Concept' }],
    goldRelations: [{ id: 'r1', sourceEntityId: 'e1', targetEntityId: 'e2', type: 'defined_by' }],
    annotationNotes: '人工备注',
    status: 'done',
    reviewerConfidence: 0.95,
    skipReason: 'no_relevant',
  }

  it('produces payload with reviewerDecision mapped from status', () => {
    const payload = localSampleToUpdatePayload(localSample, { fields: ['status'] })
    assert.equal(payload.reviewerDecision, 'completed')
    assert.equal('goldEntities' in payload, false)
  })

  it('only includes whitelisted fields for update', () => {
    const payload = localSampleToUpdatePayload(localSample, {
      fields: ['goldEntities', 'goldRelations'],
    })
    assert.deepEqual(Object.keys(payload).sort(), ['goldEntities', 'goldRelations'])
  })

  it('clearFields produces null in payload (三态 clear)', () => {
    const payload = localSampleToUpdatePayload(localSample, {
      fields: [],
      clearFields: ['skipReason', 'reviewerConfidence'],
    })
    assert.equal(payload.skipReason, null)
    assert.equal(payload.reviewerConfidence, null)
  })

  it('field absent when neither in fields nor clearFields (三态 absent)', () => {
    const payload = localSampleToUpdatePayload(localSample, { fields: ['status'] })
    assert.equal('skipReason' in payload, false)
    assert.equal('reviewerConfidence' in payload, false)
  })

  it('explicit value preserved when in fields with non-null sample value', () => {
    const payload = localSampleToUpdatePayload(localSample, { fields: ['skipReason'] })
    assert.equal(payload.skipReason, 'no_relevant')
  })
})

describe('prepare-step-api · status mapping tables', () => {
  it('REVIEWER_DECISION_TO_STATUS and STATUS_TO_REVIEWER_DECISION are inverses', () => {
    for (const [decision, status] of Object.entries(REVIEWER_DECISION_TO_STATUS)) {
      assert.equal(STATUS_TO_REVIEWER_DECISION[status], decision)
    }
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-prepare-step-api.test.js`

Expected: 模块不存在，编译失败。

- [ ] **Step 3: 实现纯函数模块**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/prepare-step-api.js
//
// 02 步前端 ↔ 后端形态转换。

export const REVIEWER_DECISION_TO_STATUS = Object.freeze({
  pending: 'not_started',
  in_progress: 'in_progress',
  completed: 'done',
  skipped: 'skipped',
})

export const STATUS_TO_REVIEWER_DECISION = Object.freeze({
  not_started: 'pending',
  in_progress: 'in_progress',
  done: 'completed',
  skipped: 'skipped',
})

/**
 * 把 GET /audit-samples 返回的单条记录转成组件本地 sample shape。
 */
export function apiSampleToLocal(api) {
  if (!api) return null
  return {
    id: String(api.id),
    sourceSampleId: api.sourceSampleId ?? '',
    text: api.text ?? '',
    headingPath: splitHeadingPath(api.headingPath),
    pageStart: api.pageStart ?? null,
    pageEnd: api.pageEnd ?? null,
    auditPriority: api.auditPriority ?? 'medium',
    auditReason: api.auditReason ?? '',
    hitSignals: Array.isArray(api.hitSignals) ? api.hitSignals : [],
    goldEntities: Array.isArray(api.goldEntities) ? api.goldEntities : [],
    goldRelations: Array.isArray(api.goldRelations) ? api.goldRelations : [],
    aiSuggestedEntities: [],
    aiSuggestedRelations: [],
    annotationNotes: api.annotationNotes ?? '',
    status: REVIEWER_DECISION_TO_STATUS[api.reviewerDecision] ?? 'not_started',
    reviewerConfidence: api.reviewerConfidence ?? null,
    skipReason: api.skipReason ?? null,
    goldStableKey: api.goldStableKey ?? null,
    reusedFrom: api.reusedFrom ?? null,
  }
}

/**
 * 把本地 sample 转成 PUT /audit-samples/{id} 的请求体（三态语义）。
 *
 * @param sample 本地 sample
 * @param options.fields 要从 sample 取值更新的字段（"有值" 态）
 * @param options.clearFields 要显式清空的字段（payload 中显式写 null，"清空" 态）
 *
 * 任何不在 fields 和 clearFields 中的字段都不会出现在 payload，对应"不更新" 态。
 *
 * 允许字段：goldEntities / goldRelations / annotationNotes / status /
 *           reviewerConfidence / skipReason
 * （`status` 会自动映射为 `reviewerDecision`）
 */
export function localSampleToUpdatePayload(sample, { fields = [], clearFields = [] } = {}) {
  if (!sample) return {}
  const payload = {}

  for (const field of fields) {
    switch (field) {
      case 'goldEntities':
      case 'goldRelations':
      case 'annotationNotes':
      case 'reviewerConfidence':
      case 'skipReason':
        payload[field] = sample[field] ?? null
        break
      case 'status':
        payload.reviewerDecision = sample.status
          ? STATUS_TO_REVIEWER_DECISION[sample.status] ?? 'pending'
          : 'pending'
        break
      default:
        // 忽略不在白名单的字段
        break
    }
  }

  for (const field of clearFields) {
    switch (field) {
      case 'goldEntities':
      case 'goldRelations':
      case 'annotationNotes':
      case 'reviewerConfidence':
      case 'skipReason':
        payload[field] = null
        break
      case 'status':
        payload.reviewerDecision = null  // 后端会回退为 pending
        break
      default:
        break
    }
  }

  return payload
}

function splitHeadingPath(value) {
  if (!value || typeof value !== 'string') return []
  return value.split(' > ').map((s) => s.trim()).filter((s) => s.length > 0)
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-prepare-step-api.test.js`

Expected: 14 个 it PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/prepare-step-api.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prepare-step-api.test.js
git commit -m "feat(prompt-builder): 新增 02 步 API 形态转换纯函数（含三态 PATCH）(Phase 2b)"
```

---

## Task 11：模板依赖盘点（Task 12 前置必做）

替换 `<script setup>` 整块代码很容易遗漏模板依赖。本任务**强制**先列出模板里所有 `@xxx`（事件） / `v-xxx`（指令） / `:xxx`（绑定） / `{{ xxx }}`（插值）所引用的标识符，再与新 script 的导出对照。

**Files:**
- Read-only: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

- [ ] **Step 1: grep 模板内所有 @xxx 事件绑定**

Run:

```bash
grep -nE '@[a-zA-Z][-a-zA-Z]*=' frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
```

Expected（参考 Phase 1b 实现）：至少应看到 `@click="..."` `@select-sample="..."` `@finish-sample` `@skip-sample` `@accept-entity` `@reject-entity` `@delete-entity` `@accept-relation` `@reject-relation` `@delete-relation` `@sort-suggestions-by-confidence`。

- [ ] **Step 2: grep 模板内所有 :xxx 属性绑定与 v-xxx 指令**

Run:

```bash
grep -nE ':[a-zA-Z][-a-zA-Z]*="|v-(if|else-if|else|for|show|model|bind|on)' \
  frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
```

记录所有引用到的 JS 标识符，例如 `samples`, `activeSample`, `activeSampleId`, `taskSummary`, `tasksExpanded`, `ideOpen`, `doneCount`, `totalCount`, `progressPercent`。

- [ ] **Step 3: grep 模板内所有 {{ xxx }} 插值**

Run:

```bash
grep -nE '\{\{[^}]+\}\}' frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
```

记录所有引用的标识符。

- [ ] **Step 4: 对照新 `<script setup>` 的导出清单**

Task 12 中提供的新 `<script setup>` 必须暴露以下标识符（v2 版完整清单）：

变量 / 计算属性：

- `samples` / `activeSample` / `activeSampleId`
- `taskSummary` / `tasksExpanded`
- `ideOpen` / `loading` / `errorMessage`
- `doneCount` / `totalCount` / `progressPercent`

方法：

- `loadOrGenerateSamples`（重试用）
- `openIde` / `closeIde`
- `handleSelectSample`
- `handleAcceptEntity` / `handleRejectEntity` / `handleDeleteEntity`
- `handleAcceptRelation` / `handleRejectRelation` / `handleDeleteRelation`
- `handleFinishSample` / `handleSkipSample`
- `sortSuggestionsByConfidence`

- [ ] **Step 5: 用 diff 对照 Step 1-3 的输出与 Step 4 的清单**

人工对照。如果 Step 1-3 出现了 Step 4 清单**没有**的标识符，**必须**回到 Task 12 在新 script 中补充——否则替换后会触发 Vue runtime 报错"x is not defined"。常见漏点：

- 模板里可能调用了 `handleAddEntity` / `handleEditEntity` / `handleUpdateNotes` / `handleCreateRelation` 之类的扩展方法。Phase 1b 实现没用到，但如果其他人在中间加过，要保留。
- 模板里如果用了 `canProceed` / `highPriorityDone` 等门控计算属性，**本期不引入**，留到后续 phase。如果模板已有调用，先把这些属性以"返回 false"的占位计算属性形式补回去，避免编译失败。

- [ ] **Step 6: 不需要 commit**

本任务仅做依赖盘点，不改代码。

---

## Task 12：把 PromptBuilderPrepareStep.vue 接入真实 API

**前置：** Task 11 已完成依赖盘点，确认新 script 覆盖完整。

主要改动：

1. 用 `route.query.buildRunId` 解析当前 build run。
2. 进入页面时 `await listAuditSamples(buildRunId)`，结果经 `apiSampleToLocal` 映射后填到 `samples`。**不再使用 `MOCK_AUDIT_SAMPLES`**。
3. 列表为空（首次进入）→ 调 `generateAuditSet(buildRunId, { force: false })`。如返回 `BUILD_RUN_HAS_ANNOTATED_SAMPLES`（4103）→ 弹 Element Plus `ElMessageBox` 二次确认 → 用户确认后用 `force: true` 再调一次；用户取消则不生成。**注意：** 正常首次加载时 `listAuditSamples` 应该能查到已有样本（非空列表），不会走到 `generateAuditSet`。4103 分支是防御性处理，覆盖"样本表被外部清空但 build run 仍有历史标注记录"等边缘场景。
4. 标注变更时按字段白名单 PUT 持久化，失败回滚 + toast。

**关于 `err.code` 的读取方式：** 本项目 `unwrapApiResponse`（`src/api/client.js`）在业务失败时 throw 的对象结构为 `{ message, code, data, status, raw }`，其中 `code` 就是后端 `ApiResponse.code`。因此前端直接用 `err?.code === 4103` 即可，不需要额外 unwrap。

为限制本期改动幅度，**保留** Teleport 全屏 IDE 覆盖层 / 任务折叠条 / 进度条等 UI 不变，只改数据流。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

- [ ] **Step 1: 替换 `<script setup>` 整块**

打开文件，把现有 `<script setup>` 整段替换为下面的版本（HTML 模板部分按 Task 12 Step 2 修改；最末尾 `<style>` 块如果有也保持不动）：

```vue
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AnnotationSampleList from './AnnotationSampleList.vue'
import AnnotationWorkArea from './AnnotationWorkArea.vue'
import {
  listAuditSamples,
  generateAuditSet,
  updateAuditSample,
} from '../../../api/prompt-tune-pipeline.js'
import { apiSampleToLocal, localSampleToUpdatePayload } from './prepare-step-api.js'

defineEmits(['back'])

const route = useRoute()

const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 }
const BUSINESS_CODE_HAS_ANNOTATED_SAMPLES = 4103

const samples = ref([])
const taskSummary = ref(null)
const tasksExpanded = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const ideOpen = ref(false)
const activeSampleId = ref('')

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

const activeSample = computed(() =>
  samples.value.find((s) => s.id === activeSampleId.value) ?? null
)

const doneCount = computed(() => samples.value.filter((s) => s.status === 'done').length)
const totalCount = computed(() => samples.value.length)
const progressPercent = computed(() =>
  totalCount.value > 0 ? Math.round((doneCount.value / totalCount.value) * 100) : 0
)

onMounted(loadOrGenerateSamples)

async function loadOrGenerateSamples() {
  if (!buildRunId.value) {
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    let apiSamples = await listAuditSamples(buildRunId.value)
    if (!Array.isArray(apiSamples) || apiSamples.length === 0) {
      ElMessage.info('正在生成校准集，约 15 秒...')
      apiSamples = await safeGenerateAuditSet({ allowForcePrompt: true })
    }
    if (Array.isArray(apiSamples)) applyApiSamples(apiSamples)
  } catch (err) {
    errorMessage.value = err?.message ?? '加载校准集失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

/**
 * 调 generateAuditSet。命中 4103（已有标注）时，根据 allowForcePrompt 决定是否弹确认重试。
 */
async function safeGenerateAuditSet({ allowForcePrompt }) {
  try {
    return await generateAuditSet(buildRunId.value, { force: false })
  } catch (err) {
    if (err?.code === BUSINESS_CODE_HAS_ANNOTATED_SAMPLES && allowForcePrompt) {
      try {
        await ElMessageBox.confirm(
          '当前构建已有人工标注，重新生成会清空当前进度。是否确认覆盖？',
          '提示',
          { confirmButtonText: '确认覆盖', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return null  // 用户取消
      }
      return await generateAuditSet(buildRunId.value, { force: true })
    }
    throw err
  }
}

function applyApiSamples(apiSamples) {
  const localSamples = (apiSamples ?? [])
    .map(apiSampleToLocal)
    .filter(Boolean)
    .sort((a, b) =>
      (PRIORITY_ORDER[a.auditPriority] ?? 9) - (PRIORITY_ORDER[b.auditPriority] ?? 9)
    )
  samples.value = localSamples
  taskSummary.value = computeTaskSummary(localSamples)
  const initialActive =
    localSamples.find((s) => s.status === 'in_progress') ??
    localSamples.find((s) => s.status === 'not_started') ??
    localSamples[0]
  activeSampleId.value = initialActive?.id ?? ''
}

function computeTaskSummary(localSamples) {
  const buckets = { high: 0, medium: 0, low: 0 }
  for (const s of localSamples) {
    buckets[s.auditPriority] = (buckets[s.auditPriority] ?? 0) + 1
  }
  return {
    samplesBuilt: { count: localSamples.length, types: 0, durationSec: null },
    auditSampled: {
      high: buckets.high,
      medium: buckets.medium,
      low: buckets.low,
      total: localSamples.length,
      durationSec: null,
    },
  }
}

function openIde() {
  ideOpen.value = true
  document.body.style.overflow = 'hidden'
}

function closeIde() {
  ideOpen.value = false
  document.body.style.overflow = ''
}

function handleSelectSample(id) {
  activeSampleId.value = id
}

function findSample(sampleId) {
  return samples.value.find((s) => s.id === sampleId) ?? null
}

async function persistFields(sample, { fields = [], clearFields = [] } = {}) {
  if (!buildRunId.value || !sample) return
  const payload = localSampleToUpdatePayload(sample, { fields, clearFields })
  if (Object.keys(payload).length === 0) return
  try {
    const updated = await updateAuditSample(buildRunId.value, sample.id, payload)
    Object.assign(sample, apiSampleToLocal(updated))
  } catch (err) {
    ElMessage.error(err?.message ?? '保存失败，请重试')
    throw err
  }
}

async function handleAcceptEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const previousStatus = sample.status
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  sample.goldEntities.push({ ...picked, source: 'accepted' })
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已采纳')
  } catch {
    sample.status = previousStatus
    sample.aiSuggestedEntities.splice(idx, 0, picked)
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== picked.id)
  }
}

function handleRejectEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedEntities = sample.aiSuggestedEntities.filter((e) => e.id !== entityId)
}

async function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const removed = sample.goldEntities.find((e) => e.id === entityId)
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
  try {
    await persistFields(sample, { fields: ['goldEntities'] })
  } catch {
    if (removed) sample.goldEntities.push(removed)
  }
}

async function handleAcceptRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedRelations.splice(idx, 1)
  sample.goldRelations.push({ ...picked, source: 'accepted' })
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
    ElMessage.success('已采纳')
  } catch {
    sample.aiSuggestedRelations.splice(idx, 0, picked)
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== picked.id)
  }
}

function handleRejectRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedRelations = sample.aiSuggestedRelations.filter((r) => r.id !== relationId)
}

async function handleDeleteRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const removed = sample.goldRelations.find((r) => r.id === relationId)
  sample.goldRelations = sample.goldRelations.filter((r) => r.id !== relationId)
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
  } catch {
    if (removed) sample.goldRelations.push(removed)
  }
}

async function handleFinishSample(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  if (sample.goldEntities.length === 0) {
    ElMessage.warning('至少标注 1 个实体后才能完成；如确实无可抽取实体，请点"跳过"')
    return
  }
  const previousStatus = sample.status
  sample.status = 'done'
  try {
    await persistFields(sample, { fields: ['status'] })
    const nextSample = samples.value.find((s) => s.status === 'not_started')
    if (nextSample) {
      activeSampleId.value = nextSample.id
      ElMessage.success('已完成')
    } else {
      ElMessage.success('已完成 · 所有样本已处理完毕，可前往下一步')
    }
  } catch {
    sample.status = previousStatus
  }
}

async function handleSkipSample(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  const previousStatus = sample.status
  sample.status = 'skipped'
  try {
    await persistFields(sample, { fields: ['status'] })
    const nextSample = samples.value.find((s) => s.status === 'not_started')
    if (nextSample) {
      activeSampleId.value = nextSample.id
      ElMessage.info('已跳过')
    } else {
      ElMessage.success('已跳过 · 所有样本已处理完毕，可前往下一步')
    }
  } catch {
    sample.status = previousStatus
  }
}

function sortSuggestionsByConfidence() {
  if (!activeSample.value) return
  activeSample.value.aiSuggestedEntities.sort(
    (a, b) => (b.confidence ?? 0) - (a.confidence ?? 0)
  )
}
</script>
```

- [ ] **Step 2: 模板内 taskSummary 兼容修改**

模板里现有的 `taskSummary.samplesBuilt.durationSec` / `taskSummary.auditSampled.durationSec` 在 API 接入后会是 `null`。

定位锚点：

```html
        <div>
          <span>02.1 调优样本集</span>
          <strong>{{ taskSummary.samplesBuilt.count }} 条 · {{ taskSummary.samplesBuilt.types }} 类型 · 用时 {{ taskSummary.samplesBuilt.durationSec }} 秒</strong>
        </div>
        <div>
          <span>02.2 校准集采样</span>
          <strong>分层抽样 {{ taskSummary.auditSampled.total }} 条 · high {{ taskSummary.auditSampled.high }} / medium {{ taskSummary.auditSampled.medium }} / low {{ taskSummary.auditSampled.low }} · 用时 {{ taskSummary.auditSampled.durationSec }} 秒</strong>
        </div>
```

替换为：

```html
        <div v-if="taskSummary">
          <span>02.1 调优样本集</span>
          <strong>
            {{ taskSummary.samplesBuilt.count }} 条
            <template v-if="taskSummary.samplesBuilt.types">
              · {{ taskSummary.samplesBuilt.types }} 类型
            </template>
            <template v-if="taskSummary.samplesBuilt.durationSec != null">
              · 用时 {{ taskSummary.samplesBuilt.durationSec }} 秒
            </template>
          </strong>
        </div>
        <div v-if="taskSummary">
          <span>02.2 校准集采样</span>
          <strong>
            分层抽样 {{ taskSummary.auditSampled.total }} 条 · high {{ taskSummary.auditSampled.high }} / medium {{ taskSummary.auditSampled.medium }} / low {{ taskSummary.auditSampled.low }}
            <template v-if="taskSummary.auditSampled.durationSec != null">
              · 用时 {{ taskSummary.auditSampled.durationSec }} 秒
            </template>
          </strong>
        </div>
```

- [ ] **Step 3: 在 02.3 概览卡前补充 loading / error 状态**

定位锚点：模板中 `<!-- 02.3 校准集概览卡 + 进入标注入口 -->` 注释。

在该注释**之前**插入：

```html
    <!-- 加载 / 错误状态 -->
    <div v-if="loading" class="prepare-loading">
      <span>正在加载校准集...</span>
    </div>
    <div v-else-if="errorMessage" class="prepare-error">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="loadOrGenerateSamples">重试</el-button>
    </div>
```

如果文件末尾没有 `<style scoped>` 块，新增：

```html
<style scoped>
.prepare-loading {
  padding: 32px;
  text-align: center;
  color: var(--ckqa-text-muted, #78716c);
}
.prepare-error {
  padding: 24px;
  text-align: center;
  color: var(--ckqa-danger, #dc2626);
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
</style>
```

- [ ] **Step 4: 验证文件无诊断错误**

打开 IDE 应该没有红色波浪线。常见问题：

- `import { useRoute } from 'vue-router'` 报错 → 该项目已安装 vue-router；从 `package.json` 确认。
- `prompt-tune-pipeline.js` 路径错 → 当前文件在 `views/pages/prompt-builder/`，`prompt-tune-pipeline.js` 在 `src/api/`，相对路径 `../../../api/prompt-tune-pipeline.js`。

- [ ] **Step 5: 跑前端单元测试**

Run: `cd frontend/apps/admin-app && pnpm test`

Expected: 包括 prepare-step-api 测试在内全部 PASS，无回归。

- [ ] **Step 6: 跑前端构建验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: 构建成功，无 TS / ESLint 错误。

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
git commit -m "feat(prompt-builder): 02 步接入真实 audit 样本 API + force 二次确认 (Phase 2b)"
```

---

## Task 13：清理 mock 引用 + 端到端验证

`MOCK_AUDIT_SAMPLES` / `MOCK_TASK_SUMMARY` 在本期之后只剩开发期的可选用途。本任务确认 `PromptBuilderPrepareStep.vue` 不再 import 它们；保留 `mocks/audit-samples.js` 文件本身（其他场景如 Storybook / 演示模式仍可引用）。

**Files:**
- Verify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`
- Verify: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js`

- [ ] **Step 1: 确认 PromptBuilderPrepareStep.vue 不再 import mock**

Run: `grep -n "MOCK_AUDIT_SAMPLES\|MOCK_TASK_SUMMARY" frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

Expected: 无输出。

- [ ] **Step 2: 确认其他生产代码未引用**

Run: `grep -rn "MOCK_AUDIT_SAMPLES" frontend/apps/admin-app/src --include='*.vue' --include='*.js'`

Expected: 仅在 `mocks/audit-samples.js`（声明）与可能存在的 `__tests__/` 中出现；不应再有 `views/pages/` 引用。

- [ ] **Step 3: 启动 infra**

Run: `cd infra && docker compose up -d`

Expected: 各服务 Up（`docker compose ps` 验证）。

- [ ] **Step 4: 启动后端**

Run: `cd backend/ckqa-back && mvn -q spring-boot:run`

放后台或新终端启动。

- [ ] **Step 5: 启动前端 dev server**

由于 `pnpm dev` 是长驻进程，**不要用 bash 工具直接跑**。手动在新终端：

```bash
cd frontend/apps/admin-app
pnpm dev
```

或使用 `control_bash_process` 后台启动。

- [ ] **Step 6: 浏览器手工验证**

1. 登录 admin-app。
2. 进入 / 创建一个 build run，推进到 `currentStage = 'prompt'`。
3. 进入提示词构建器，到 02 步。
4. 第一次进入：应自动显示"正在生成校准集..."提示，等 15-20 秒后显示样本列表。
5. 点开 02 步标注 IDE，标注一条样本（添加实体、点完成）。
6. 刷新页面：标注状态应保留。
7. 跳过一条：状态变为"已跳过"。
8. 在 MySQL 验证落库：

```bash
mysql -uroot -p ocqa -e "SELECT id, reviewer_decision, JSON_LENGTH(gold_entities) FROM prompt_tune_audit_samples WHERE build_run_id = <BUILD_RUN_ID>;"
```

- [ ] **Step 7: 关闭服务**

按 Ctrl+C，或在后台进程 `kill`。

- [ ] **Step 8: 自检 spec 覆盖**

参照本 plan 顶部的"自检：spec 覆盖清单"，确认每条对应 task 已 ✅。

- [ ] **Step 9: 不需要 commit**

至此 Phase 2b 完结。下一步进入 Phase 2c（03 步候选生成 API）。

---

## 已识别风险

1. **Python 子进程在 Spring controller 线程内同步执行**：`generateAuditSet` 接口最多耗时 5 分钟（`STEP_TIMEOUT`），期间 servlet 线程被占用但**事务已不再覆盖整个外部进程**（v2 修订把 DB 写入隔离到 `AuditSamplePersistenceService`）。生产环境若并发请求多需要把它改为异步 worker（仿照 `PromptTuneWorker`）。本期不做：spec § 错误处理把 02.1/02.2 失败的处理放在前端 UI 层。
2. **`saveBatch` 后 entity.id 可能未填充**：MyBatis-Plus 默认 `useGeneratedKeys = true`，但批量场景下行为依赖 driver。`replaceForBuildRun` 在 saveBatch 后再 `listByBuildRunId` 重新查表，绕开了这个不确定性。
3. **历史复用合并跨 build run 但同知识库**：用户在不同知识库下的同一份 PDF 标注**不会**被复用——这是有意为之，避免污染。
4. **Force 防误删的并发竞争**：`hasNonPendingSamples` 与 `replaceForBuildRun` 不在同一事务中，理论上"读到 false 但写入前用户刚标注过"。本期接受这个窗口，并发风险极小（用户操作时间尺度 ≫ 接口处理时间）。
5. **Task 12 整块替换 `<script setup>` 仍有遗漏风险**：Task 11 的 grep 盘点是必要的二道防线。如果 Task 11 与 12 的暴露清单没对齐，编译会立即报错（Vue / vue-tsc 都会爆出 "is not defined"），不会 silent fail。

