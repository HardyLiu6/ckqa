# 手动调优提示词向导 · Phase 3 标注 IDE 智能能力

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 02 步标注 IDE 从"手动标注"升级到"智能辅助标注"——实现 spec § 02 步的 4 个智能能力：A AI 预填实体/关系（紫色 ✨ 横幅 + 候选审阅）、D 历史复用 banner UI（绿色 ♻ 横幅）、原文拖选添加实体（含 spanStart/spanEnd 高亮）、关系自动反向（A→B 不合法时自动调换为 B→A）。

**Architecture:** 后端新增 `AiSuggestionService` + `SingleSampleExtractionOrchestrator`（包装 `run_native_extraction.py --limit 1`），把现有 `requestAuditSampleAiSuggestions` 端点从 501 改为真实实现，同步返回候选实体/关系。前端 `AnnotationWorkArea.vue` 在 AI 横幅上加"生成候选"按钮调本端点；候选写入 `aiSuggestedEntities` / `aiSuggestedRelations` 局部状态（不持久化到 DB——AI 候选属于"待审"，被采纳后才进 goldEntities）。原文拖选用 `window.getSelection()` 计算 offset，已确认实体 spanStart/spanEnd 在原文卡上渲染紫色高亮。`RelationEditor` 在过滤结果为空时尝试反向 schema 查询，命中则提示"已自动调整方向"。Phase 2b 已经在 `reusedFrom` 字段中返回数据，绿色 ♻ banner UI 在 Phase 1b 模板中已存在——本期只需补**测试覆盖**与**点击"使用"/"忽略"的交互**。

**Tech Stack:** 已有依赖；不引入新库。后端：复用 `AuditPipelineOrchestrator` 的 Python 子进程模式 + `ProcessRunner`；前端：Vue 3.5 + Element Plus + 已有 `relation-types-model.js`。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 02 构建准备材料的"智能能力（02 步）"小节、§ 已识别风险 #1（AI 预填污染缓解）。

**前置：** Phase 2c-pre 已完成（02 步实体/关系手动新建可用）。

## v2 修订要点（与初稿差异）

针对初稿审阅意见的修订（影响后续 Task 实现细节）：

1. **AI 候选关系字段语义清晰化**：候选来源用独立字段 `suggestionSource: 'ai_suggested'` 标记，**不**覆盖 GraphRAG `source/target` 这种关系领域字段。原 `source/target`（实体名）改名为 `originalSource/originalTarget`。
2. **"隐藏复用提示"语义对齐**：按钮文案从"不使用此预填"改为"隐藏复用提示"——只清 `sample.reusedFrom`，不动 gold 数据。这避免了"按钮叫不使用但实际不会删除预填"的语义冲突。
3. **AnnotationTextCard 浮动按钮关闭逻辑**：从父级 `@click` 改为文档级 `mousedown` 监听。避免 mouseup 之后浏览器触发的 click 冒泡到父级把刚显示的按钮立刻关掉。
4. **AI 预填 prompt 路径明确为默认**：本期固定使用 `<GRAPHRAG_ROOT>/prompts/extract_graph.txt`，**不**读 customPromptDraft（留到 Phase 6 一起做）。范围说明里写明这一点。
5. **SingleSampleExtractionOrchestrator 用确定性 run id + 固定输出路径**：从 `<GRAPHRAG_ROOT>/results/extraction_eval/<runId>/ai_suggestion.json` 读取，不解析 stdout。`runId = ai_suggestion_<buildRunId>_<sampleId>_<timestamp>`。
6. **handleCreateEntity 透传 spanStart/spanEnd**：拖选添加的实体 span 字段会落到 goldEntities，原文渲染才能正确高亮。
7. **handleAcceptRelation 用 aiEntityNameToGoldId 映射优先匹配**：用户采纳 AI 实体时记录 originalName → goldEntityId；采纳关系时优先查 map，fallback 按 name 找 sample.goldEntities。
8. **aiSuggestionLoading 改为按 sampleId 维度**：从 `ref(false)` 改为 `ref(null)`，存当前正在生成的 sampleId，支持多样本并发不互锁。
9. **computeSelectionRange 优先精确判断**：`text.slice(selectionStart, selectionStart + len) === selectedText` 时直接用 selectionStart，避免重复字符串歧义。
10. **Task 2 增加输入 JSON 结构校验**：grep 确认 `run_native_extraction.py` 期望的 samples-file 结构（`audit_samples` vs `samples` vs 数组）。
11. **样式迁移免做**：`.annotation-text-card*` 系列样式定义在全局 `components.scss`（不是 scoped），抽组件不会丢样式。Task 7 加 grep 确认这一点。
12. **自检清单 Task 编号修正**：所有"→ Task N"对齐到当前 Task 0-12 编号体系。

## 范围说明

**本期做：**

- 智能能力 A：AI 预填实体/关系
  - 后端 `POST /audit-samples/{sampleId}/ai-suggestions` 端点真实实现
  - Python 单样本抽取脚本包装（复用 `run_native_extraction.py --limit 1`）
  - 前端紫色 ✨ 横幅"生成候选 / 按置信度排序"按钮
  - AI 候选实体/关系卡的 source `ai_suggested` / 紫色虚线边框（Phase 1b 视觉已就位）
  - 用户审阅交互：每条单独"采纳" / "拒绝"（**严禁**"全部采纳"）
- 智能能力 D：历史复用 banner UI 完善
  - Phase 1b 模板已有 `<div v-if="sample.reusedFrom">`，本期补"隐藏复用提示"按钮 + 单测
- 原文拖选添加实体
  - 选区 → 浮动按钮"添加为实体"→ 预填 EntityEditor 的实体名 + spanStart/spanEnd
  - 已确认实体（含 spanStart/spanEnd 字段的）在原文卡渲染紫色高亮
- 关系自动反向
  - `RelationEditor` 在 `availableRelationTypes.length === 0` 且反向有合法关系时，弹横幅"两端类型仅支持反向 B → A，是否调换"+ "调换"按钮

**本期不做：**

- 离线 IndexedDB 暂存（Phase 7）
- 样本级乐观锁、build run 归档（Phase 8）
- 完整候选生成流水线（Phase 4）
- 评分排行榜（Phase 5）
- AI 预填使用 build run 的 `customPromptDraft`（需要把 draft 字符串落到临时文件 + 路径选择逻辑，留到 Phase 6）。本期 AI 预填**固定**使用 GraphRAG 当前激活的 `prompts/extract_graph.txt`。

## 自检：spec 覆盖清单

- ✅ A. AI 预填实体/关系（紫色横幅 + 候选审阅）→ Task 1（错误码）/ 2（脚本包装）/ 3（DTO）/ 4（业务编排）/ 5（端点）/ 10（前端横幅）/ 11（前端 handler）
- ✅ A. spec § 风险 #1 缓解：候选必须**逐条审阅**，按钮文案"按置信度排序"（不是"全部采纳"）→ Task 10（横幅文案约束）
- ✅ D. 历史复用 banner UI + "隐藏复用提示"按钮 → Task 10（横幅）/ Task 11（handler）
- ✅ 原文拖选添加实体（含 spanStart/spanEnd）→ Task 6（纯函数）/ Task 7（原文卡组件）/ Task 8（编辑器接收预填）/ Task 11 Step 4（handleCreateEntity 落库 span）
- ✅ 已确认实体在原文渲染紫色高亮 → Task 6 + Task 7（splitTextByEntitySpans 渲染分段）
- ✅ 关系自动反向（A→B 无关系时尝试 B→A）→ Task 9（tryReverseRelation + RelationEditor 调换按钮）
- ⏸ AI 预填的端到端污染回归测试（spec § 风险 #1 PR review 验证）→ 留到 Phase 9

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java` | 修改 | 新增 `AI_SUGGESTION_FAILED` 错误码 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SingleSampleExtractionOrchestrator.java` | 新建 | 单样本 GraphRAG 抽取脚本子进程包装（复用 `run_native_extraction.py --limit 1`） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java` | 新建 | 业务层：取样本 → 拼临时输入 JSON → 调 orchestrator → 解析输出 → 返回候选 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AiSuggestionResponse.java` | 新建 | AI 候选响应 DTO（`entities[]` + `relations[]`） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java` | 修改 | 把 `requestAuditSampleAiSuggestions` 从 501 改为真实实现 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java` | 新建 | service 单测（解析 GraphRAG 输出 / 错误降级） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/text-selection-model.js` | 新建 | 原文选区 → spanStart/spanEnd 计算 + 高亮分段纯函数 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-text-selection-model.test.js` | 新建 | 选区/高亮纯函数单测 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationTextCard.vue` | 新建 | 原文卡组件（带选区监听 + 高亮渲染），从 AnnotationWorkArea 抽出 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue` | 修改 | 接收 `prefilledName` / `prefilledSpan` props |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue` | 修改 | 反向关系检测 + "调换方向"按钮 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue` | 修改 | 接 AI 候选 emit、原文卡替换、reusedFrom 加"隐藏复用提示"按钮 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue` | 修改 | 新增 `handleRequestAiSuggestions` / `handleDismissReusedFrom`，AI 候选写到 sample.aiSuggestedEntities/aiSuggestedRelations |
| `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js` | 已存在桩 | `requestAuditSampleAiSuggestions(buildRunId, sampleId)` 已就位 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-editor-reverse.test.js` | 新建 | RelationEditor 自动反向纯逻辑测试（抽出 `tryReverseRelation` 纯函数） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js` | 修改 | 新增 `tryReverseRelation` 函数（schema 反向查询） |

---

## Task 0：现有产物形态确认

无代码改动，只 grep 确认 Phase 2b/2c-pre 现有形态符合本计划假设。**Step 失败时停下，不要继续。**

- [ ] **Step 1: 校验 reusedFrom 在响应中已经返回**

Run:

```bash
grep -n 'reusedFrom\|ReusedFromInfo' backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleResponse.java
grep -n 'reusedFrom' frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue
```

Expected: 后端有 `ReusedFromInfo` 嵌套类；前端 AnnotationWorkArea.vue 模板里有 `<div v-if="sample.reusedFrom">` 横幅。

- [ ] **Step 2: 校验 aiSuggestedEntities / aiSuggestedRelations 字段已就位**

Run:

```bash
grep -n 'aiSuggestedEntities\|aiSuggestedRelations' frontend/apps/admin-app/src/views/pages/prompt-builder/prepare-step-api.js
```

Expected: 看到 `aiSuggestedEntities: []` 和 `aiSuggestedRelations: []`，由 `apiSampleToLocal` 默认置空数组。

- [ ] **Step 3: 校验前端 API 桩已就位**

Run:

```bash
grep -n 'requestAuditSampleAiSuggestions' frontend/apps/admin-app/src/api/prompt-tune-pipeline.js
```

Expected: 函数声明已存在，POST 到 `/knowledge-base-build-runs/.../audit-samples/.../ai-suggestions`。

- [ ] **Step 4: 校验 Python 抽取脚本可用**

Run:

```bash
python graphrag_pipeline/scripts/extraction_eval/run_native_extraction.py --help 2>&1 | grep -E 'samples-file|--prompt|--limit'
```

Expected: 看到 `--samples-file`、`--prompt`、`--limit` 三个参数。

- [ ] **Step 5: 校验 controller 占位仍是 501**

Run:

```bash
grep -A 6 'requestAuditSampleAiSuggestions' backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java | head -10
```

Expected: 看到 `throw notImplemented()` —— 表示当前是 Phase 2a 的占位，本期会替换。

- [ ] **Step 6: 通过则进入 Task 1**

如果以上步骤全通过，进入 Task 1。否则按提示调整环境/代码后重试。

---

## Task 1：扩展 ApiResultCode 错误码

新增一个本期会用到的错误码。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1: 在 `AUDIT_PIPELINE_FAILED(5005, ...)` 之后追加 `AI_SUGGESTION_FAILED(5006, ...)`**

定位锚点：

```java
    /**
     * 标注流水线执行失败（build_prompt_tuning_samples / build_audit_extraction_set）。
     */
    AUDIT_PIPELINE_FAILED(5005, "标注流水线执行失败"),
```

在该枚举值之后插入：

```java
    /**
     * AI 预填候选生成失败（单样本 GraphRAG 抽取超时或异常）。
     */
    AI_SUGGESTION_FAILED(5006, "AI 候选生成失败"),
```

- [ ] **Step 2: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(api): 新增 AI_SUGGESTION_FAILED 错误码 (Phase 3)"
```

---

## Task 2：SingleSampleExtractionOrchestrator 单样本抽取脚本包装

包装 `python scripts/extraction_eval/run_native_extraction.py --limit 1`，专为 AI 预填的单样本场景。和 `AuditPipelineOrchestrator` 同样模式：构造 argv → ProcessRunner.run → 解析结果文件。

**输入：** 单条样本（实体名、文本、元数据）+ 默认 prompt 路径（用 `prompts/extract_graph.txt` 当前激活版本）。
**输出：** `ExtractionResult`（含 `entities[]`、`relations[]`，每条带 `confidence` 字段）。

**关键设计：** 调用前先构造一个**临时 audit_samples.json**（只含一条样本），跑完后从输出 JSON 读结果。临时文件放在该 build run workspace 的 `prompt/audit/ai-suggestion/` 子目录。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SingleSampleExtractionOrchestrator.java`

- [ ] **Step 1: 创建 SingleSampleExtractionOrchestrator**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单样本 GraphRAG 抽取脚本包装。
 * <p>
 * 用于 02 步 AI 预填功能：把一条 audit 样本喂给 {@code run_native_extraction.py --limit 1}，
 * 得到候选实体和候选关系。和 {@link AuditPipelineOrchestrator} 同模式：
 * 同步串行调用 Python 子进程，调用方需保证不在 {@code @Transactional} 中。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SingleSampleExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SingleSampleExtractionOrchestrator.class);

    /**
     * 单样本抽取的最长执行时长。一次 LLM 调用通常 10-30 秒，预留 2 分钟兜底网络抖动。
     */
    private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(2);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对单条样本执行 GraphRAG 抽取，返回候选实体/关系。
     *
     * @param sampleJson         一条样本对象（含 source_sample_id / text / heading_path / page_*）
     * @param entityTypes        逗号分隔的实体类型列表，如 "Course,Concept,Term,..."
     * @param promptFile         GraphRAG 抽取 prompt 文件绝对路径（通常 prompts/extract_graph.txt）
     * @param workspaceDir       临时工作目录，用于存放输入/输出/日志
     * @param runId              自定义 run id，决定输出文件路径
     *                           ({@code <GRAPHRAG_ROOT>/results/extraction_eval/<runId>/ai_suggestion.json})
     */
    public ExtractionResult runSingleExtract(
            Map<String, Object> sampleJson,
            String entityTypes,
            Path promptFile,
            Path workspaceDir,
            String runId
    ) throws IOException, InterruptedException {
        Files.createDirectories(workspaceDir);

        // 1. 构造临时 audit_samples.json（包一层 audit_samples 数组）
        Path samplesFile = workspaceDir.resolve("single_sample.json");
        Map<String, Object> samplesPayload = new LinkedHashMap<>();
        samplesPayload.put("audit_samples", List.of(sampleJson));
        Files.writeString(
                samplesFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(samplesPayload),
                StandardCharsets.UTF_8
        );

        // 2. 构造命令行
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("scripts/extraction_eval/run_native_extraction.py");
        argv.add("--samples-file");
        argv.add(samplesFile.toAbsolutePath().toString());
        argv.add("--prompt");
        argv.add(promptFile.toAbsolutePath().toString());
        argv.add("--candidate-name");
        argv.add("ai_suggestion");
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--entity-types");
        argv.add(entityTypes);
        argv.add("--limit");
        argv.add("1");
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("ai_suggestion.log");
        ProcessContext context = ProcessContext.builder()
                .operation("ai-suggestion-extract")
                .logFile(logFile)
                .build();

        log.info("运行 AI 单样本抽取: samplesFile={}, prompt={}, runId={}", samplesFile, promptFile, runId);

        ProcessExecutionResult result = processRunner.run(
                argv,
                scriptRoot,
                Map.of(),
                EXTRACT_TIMEOUT,
                context
        );

        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 候选生成超时（>" + EXTRACT_TIMEOUT.toSeconds() + "s）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选生成失败: " + firstSummary(result.getStderr(), "未知错误")
            );
        }

        // 3. 读输出文件——固定路径 <GRAPHRAG_ROOT>/results/extraction_eval/<runId>/ai_suggestion.json
        // 不解析 stdout，避免脚本日志格式变更带来脆弱性。
        Path outputFile = scriptRoot
                .resolve("results")
                .resolve("extraction_eval")
                .resolve(runId)
                .resolve("ai_suggestion.json");
        if (!Files.exists(outputFile)) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选输出文件不存在：" + outputFile
            );
        }

        return parseExtractionOutput(outputFile);
    }

    private ExtractionResult parseExtractionOutput(Path outputFile) throws IOException {
        String json = Files.readString(outputFile, StandardCharsets.UTF_8);
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
        // run_native_extraction 输出结构：
        // { "samples": [ { "id": "...", "extraction": { "entities": [...], "relations": [...] } } ] }
        Object samplesNode = root.get("samples");
        if (!(samplesNode instanceof List<?> samplesList) || samplesList.isEmpty()) {
            return ExtractionResult.builder()
                    .entities(List.of())
                    .relations(List.of())
                    .build();
        }
        Object first = samplesList.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return ExtractionResult.builder().entities(List.of()).relations(List.of()).build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> firstSample = (Map<String, Object>) firstMap;
        Object extraction = firstSample.get("extraction");
        if (!(extraction instanceof Map<?, ?> extractionMap)) {
            return ExtractionResult.builder().entities(List.of()).relations(List.of()).build();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>)
                extractionMap.getOrDefault("entities", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>)
                extractionMap.getOrDefault("relations", List.of());

        return ExtractionResult.builder()
                .entities(entities)
                .relations(relations)
                .build();
    }

    private static String firstSummary(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return fallback;
    }

    /**
     * 单样本抽取结果。
     */
    @Getter
    @Builder
    public static class ExtractionResult {
        private final List<Map<String, Object>> entities;
        private final List<Map<String, Object>> relations;
    }
}
```

⚠️ **依赖 `run_native_extraction.py` 的输出路径约定**：脚本接受 `--run-id` 参数，输出文件路径为 `<GRAPHRAG_ROOT>/results/extraction_eval/<runId>/<candidate-name>.json`。不读 stdout，直接按确定性路径读取，避免日志格式变更带来脆弱性。Task 2 Step 2 校验这个路径模式存在。

- [ ] **Step 2: 验证 `run_native_extraction.py` 接受 `--run-id` 并按 `<runId>/<candidate-name>.json` 落盘**

Run:

```bash
grep -nE '\-\-run-id|run_id|results/extraction_eval|/.+\.json' graphrag_pipeline/scripts/extraction_eval/run_native_extraction.py | head -20
```

Expected:

- 看到 `--run-id` 参数声明（`parser.add_argument("--run-id", ...)`）
- 看到形如 `Path(...) / "results" / "extraction_eval" / run_id / f"{candidate_name}.json"` 的路径拼接逻辑

如果发现实际路径模式不同（比如脚本直接用 `args.run_id` 作为子目录名但 `candidate_name` 拼接方式不同），调整 `SingleSampleExtractionOrchestrator` 中读取输出的路径（在 service 文件中找到 `outputFile = scriptRoot.resolve(...)` 那段）。

如果脚本根本不接受 `--run-id`，**停下**：在 graphrag_pipeline 中先补这个参数，或改用 `args.candidate_name`-only 的固定路径模式。

- [ ] **Step 3: 验证脚本期望的 samples-file JSON 结构**

`SingleSampleExtractionOrchestrator` 构造的输入 JSON 形如 `{"audit_samples": [{"source_sample_id": ..., "text": ..., ...}]}`，需要确认脚本读这个结构。

Run:

```bash
grep -nE 'audit_samples|json\.load|samples_file|source_sample_id' graphrag_pipeline/scripts/extraction_eval/run_native_extraction.py | head -10
```

Expected: 看到脚本读取 `audit_samples` 数组并取每条的 `source_sample_id` / `text` / `heading_path` 等字段。

如果脚本读的是 `{"samples": [...]}` 或直接 `[...]`，调整 `SingleSampleExtractionOrchestrator.runSingleExtract` 中的 `samplesPayload.put("audit_samples", ...)` 改为对应键名（或去掉外包）。

- [ ] **Step 4: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SingleSampleExtractionOrchestrator.java
git commit -m "feat(index): 新增 SingleSampleExtractionOrchestrator 单样本 AI 抽取包装 (Phase 3)"
```

---

## Task 3：AiSuggestionResponse DTO

简单的响应 DTO。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AiSuggestionResponse.java`

- [ ] **Step 1: 创建 DTO**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * AI 候选生成响应。
 * <p>
 * 候选实体/关系由 GraphRAG 单样本抽取得到。前端把它们落到 sample.aiSuggestedEntities /
 * aiSuggestedRelations，等待用户**逐条审阅**——spec § 风险 #1 明确禁止"全部采纳"操作。
 * </p>
 */
@Getter
@Builder
public class AiSuggestionResponse {

    /**
     * 候选实体列表，每项至少含 {@code name} / {@code type} / {@code description} / {@code confidence}。
     * GraphRAG 输出可能含额外字段（如 {@code source_id}），原样透传。
     */
    private final List<Map<String, Object>> entities;

    /**
     * 候选关系列表，每项至少含 {@code source} / {@code target} / {@code type} / {@code description} / {@code confidence}。
     */
    private final List<Map<String, Object>> relations;
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AiSuggestionResponse.java
git commit -m "feat(dto): 新增 AiSuggestionResponse (Phase 3)"
```

---

## Task 4：AiSuggestionService 业务编排 + 单测

负责的事：

1. 取 audit 样本（含 buildRun 校验）
2. 把 sample 数据组装成 GraphRAG 期望的格式
3. 决定 prompt 文件路径：**本期固定使用 `<GRAPHRAG_ROOT>/prompts/extract_graph.txt`**——即 GraphRAG 当前激活的 prompt（可能是默认的，也可能是 GraphRAG 自动调优生成的）。**不读取** build run 的 `customPromptDraft`：因为 customPromptDraft 当前以 JSON 字符串形式存在 `build_metadata` 中，把它落到临时文件 + 选 prompt 文件的逻辑要等 Phase 6 `<PromptDisplay>` 一起做。如果用户手动调优后想用调优草稿做 AI 预填，需要先在 05 步保存草稿并通过 `confirmPrompt` 写入到 GraphRAG 的 prompt 路径，然后才能在 02 步生效。
4. 调 orchestrator
5. 把 GraphRAG 输出的 entities / relations 转成前端期望格式（保留 confidence，加 `source: 'ai_suggested'`）

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java`

- [ ] **Step 1: 写失败测试**

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
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSuggestionServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private KnowledgeBaseBuildRunsService buildRunsStore;
    private SingleSampleExtractionOrchestrator orchestrator;
    private BuildRunWorkspaceService workspaceService;
    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        orchestrator = mock(SingleSampleExtractionOrchestrator.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        service = new AiSuggestionService(
                samplesStore, buildRunsStore, orchestrator, workspaceService, new ObjectMapper()
        );
    }

    @Test
    void rejectsWhenSampleNotFound() {
        when(samplesStore.getById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.generate(10L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND.getCode());
    }

    @Test
    void rejectsWhenBuildRunIdMismatches() {
        PromptTuneAuditSamples sample = newSample(99L, /*buildRunId*/ 5L);
        when(samplesStore.getById(99L)).thenReturn(sample);

        assertThatThrownBy(() -> service.generate(/*pathBuildRunId*/ 10L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND.getCode());
    }

    @Test
    void marksAllEntitiesWithAiSuggestedSource() throws Exception {
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        KnowledgeBaseBuildRuns buildRun = newBuildRun(10L);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(java.nio.file.Path.of("/tmp/ws"));

        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of(
                                Map.of("name", "进程", "type", "Concept", "confidence", 0.92),
                                Map.of("name", "线程", "type", "Concept", "confidence", 0.78)
                        ))
                        .relations(List.of(
                                Map.of("source", "进程", "target", "线程", "type", "contains", "confidence", 0.65)
                        ))
                        .build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        AiSuggestionResponse response = service.generate(10L, 99L);

        assertThat(response.getEntities()).hasSize(2);
        // suggestionSource 是新增字段；name/type 等领域字段透传不变
        assertThat(response.getEntities()).allSatisfy(e -> {
            assertThat(e).containsEntry("suggestionSource", "ai_suggested");
            assertThat(e).containsKey("name");
            assertThat(e).containsKey("type");
        });
        assertThat(response.getRelations()).hasSize(1);
        assertThat(response.getRelations().get(0))
                .containsEntry("suggestionSource", "ai_suggested");
    }

    @Test
    void mapsRelationFieldsToFrontendShape() throws Exception {
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(newBuildRun(10L));
        when(workspaceService.resolve(any())).thenReturn(java.nio.file.Path.of("/tmp/ws"));

        // GraphRAG 输出的 relation 字段是 source/target（实体名字符串）
        // service 把这两个字段移到 originalSource/originalTarget，并加 suggestionSource 标记
        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of())
                        .relations(List.of(
                                Map.of("source", "进程", "target", "线程",
                                       "type", "contains", "description", "结构包含",
                                       "confidence", 0.7)
                        ))
                        .build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        AiSuggestionResponse response = service.generate(10L, 99L);

        Map<String, Object> rel = response.getRelations().get(0);
        // 关系领域字段保持干净：source/target 已被移除（前端的 source 字段语义专留给"关系起点 id"）
        assertThat(rel).doesNotContainKey("source");
        assertThat(rel).doesNotContainKey("target");
        // 原 source/target 改名保留
        assertThat(rel).containsEntry("originalSource", "进程");
        assertThat(rel).containsEntry("originalTarget", "线程");
        // 候选来源用独立字段标记
        assertThat(rel).containsEntry("suggestionSource", "ai_suggested");
        // 其他字段透传
        assertThat(rel).containsEntry("type", "contains");
        assertThat(rel).containsEntry("description", "结构包含");
    }

    private PromptTuneAuditSamples newSample(Long id, Long buildRunId) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setBuildRunId(buildRunId);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-x");
        e.setText("进程是程序的一次执行过程。");
        e.setHeadingPath("第二章 > 2.1");
        e.setPageStart(34);
        e.setPageEnd(34);
        e.setAuditPriority("high");
        e.setReviewerDecision("pending");
        return e;
    }

    private KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns b = new KnowledgeBaseBuildRuns();
        b.setId(id);
        b.setKnowledgeBaseId(100L);
        b.setWorkspaceUri("user_1/kb_100/build_" + id);
        return b;
    }
}
```

⚠️ 测试中 `originalSource` / `originalTarget` 是**故意反映关键设计决策**：GraphRAG 抽出的 relation 用实体名字符串引用（不是 id），前端无法直接映射到 sample.goldEntities 里的 id。本期的妥协：

- 候选来源用**独立字段** `suggestionSource: 'ai_suggested'` 标记，**不**覆盖 GraphRAG 的 `source` / `target`（它们在关系领域里是"起点 / 终点"语义，不应被复用为"候选来源"）
- 原 GraphRAG `source` / `target`（实体名）改名为 `originalSource` / `originalTarget`
- 前端"采纳关系"时优先查"已采纳 AI 实体本地映射表"（Task 11 实现的 `aiEntityNameToGoldId`）取 entity id；映射表未命中时退化按 `name` 匹配 sample.goldEntities。如果两端都找不到 → 关系不能采纳，提示"请先采纳两端实体"

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AiSuggestionServiceTest test`

Expected: 编译失败 `cannot find symbol: AiSuggestionService`。

- [ ] **Step 3: 实现 service**

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 预填业务编排（Phase 3）。
 * <p>
 * 不使用 {@code @Transactional}：本服务唯一的"写"操作是日志/中间文件，
 * 不直接修改业务表。AI 候选不持久化到 DB（属于待审状态，被采纳后才进 goldEntities）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    /** 与前端 schema hardcode 同步的 11 种实体类型，作为 GraphRAG 抽取目标。 */
    private static final String DEFAULT_ENTITY_TYPES =
            "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,"
            + "AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform";

    private final PromptTuneAuditSamplesService samplesStore;
    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final SingleSampleExtractionOrchestrator orchestrator;
    private final BuildRunWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public AiSuggestionResponse generate(Long buildRunId, Long sampleId) {
        PromptTuneAuditSamples sample = samplesStore.getById(sampleId);
        if (sample == null || !Objects.equals(sample.getBuildRunId(), buildRunId)) {
            throw new BusinessException(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);

        Map<String, Object> sampleJson = sampleToGraphRagInput(sample);
        Path workspaceDir = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("ai-suggestion").resolve("sample-" + sampleId);

        Path promptFile = resolvePromptFile(buildRun);

        // 确定性 run id：buildRunId + sampleId + 时间戳，避免不同请求互相覆盖输出文件
        String runId = "ai_suggestion_" + buildRunId + "_" + sampleId + "_" + System.currentTimeMillis();

        SingleSampleExtractionOrchestrator.ExtractionResult result;
        try {
            result = orchestrator.runSingleExtract(
                    sampleJson, DEFAULT_ENTITY_TYPES, promptFile, workspaceDir, runId
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选生成异常: " + e.getMessage()
            );
        }

        return AiSuggestionResponse.builder()
                .entities(markEntitiesAsAiSuggested(result.getEntities()))
                .relations(markRelationsAsAiSuggested(result.getRelations()))
                .build();
    }

    /**
     * 把 PromptTuneAuditSamples 实体的字段重组成 audit_extraction_set.json 中样本的格式。
     * GraphRAG 脚本期望 source_sample_id / text / heading_path / page_start / page_end 等字段。
     */
    private Map<String, Object> sampleToGraphRagInput(PromptTuneAuditSamples sample) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("source_sample_id", sample.getSourceSampleId());
        json.put("text", sample.getText());
        // heading_path 在 DB 中是 " > " 分隔字符串；GraphRAG 期望 List<String>
        if (sample.getHeadingPath() != null && !sample.getHeadingPath().isBlank()) {
            json.put("heading_path", List.of(sample.getHeadingPath().split(" > ")));
        } else {
            json.put("heading_path", List.of());
        }
        json.put("page_start", sample.getPageStart() != null ? sample.getPageStart() : 0);
        json.put("page_end", sample.getPageEnd() != null ? sample.getPageEnd() : 0);
        json.put("audit_priority", sample.getAuditPriority());
        json.put("audit_reason", sample.getAuditReason());
        return json;
    }

    /**
     * 决定使用哪个 prompt 文件：本期固定使用 GraphRAG 默认 {@code prompts/extract_graph.txt}。
     * <p>
     * Build run 的 customPromptDraft 暂未读取——它当前以 JSON 字符串形式存在 build_metadata 中，
     * 把它落到临时文件的逻辑随 Phase 6 一起做。
     * </p>
     */
    private Path resolvePromptFile(KnowledgeBaseBuildRuns buildRun) {
        return Path.of(properties.getGraphrag().getRoot()).resolve("prompts").resolve("extract_graph.txt");
    }

    private final CkqaIntegrationProperties properties = null;  // 占位，Step 4 修订

    private List<Map<String, Object>> markEntitiesAsAiSuggested(List<Map<String, Object>> entities) {
        return entities.stream()
                .map(e -> {
                    Map<String, Object> copy = new LinkedHashMap<>(e);
                    copy.put("suggestionSource", "ai_suggested");
                    return copy;
                })
                .toList();
    }

    /**
     * GraphRAG 抽出的 relation 用实体名字符串作为 source/target，
     * 但前端期望 sourceEntityId/targetEntityId 指向已确认实体的 id。
     * <p>
     * AI 候选无法预知前端 id，本期妥协：
     * <ul>
     *   <li>用独立字段 {@code suggestionSource} = {@code "ai_suggested"} 标记候选来源
     *       （<strong>不</strong> 覆盖 GraphRAG 的 source/target——避免污染关系领域字段）</li>
     *   <li>原始 source/target（实体名）改名为 {@code originalSource}/{@code originalTarget}</li>
     *   <li>前端采纳时优先查"已采纳 AI 实体本地映射表"匹配 entity id，
     *       未命中时退化按"同名"匹配 sample.goldEntities</li>
     * </ul>
     * </p>
     */
    private List<Map<String, Object>> markRelationsAsAiSuggested(List<Map<String, Object>> relations) {
        return relations.stream()
                .map(r -> {
                    Map<String, Object> copy = new LinkedHashMap<>(r);
                    Object originalSource = copy.remove("source");
                    Object originalTarget = copy.remove("target");
                    if (originalSource != null) copy.put("originalSource", originalSource);
                    if (originalTarget != null) copy.put("originalTarget", originalTarget);
                    copy.put("suggestionSource", "ai_suggested");
                    return copy;
                })
                .toList();
    }
}
```

- [ ] **Step 4: 修订 service 让 `properties` 由构造器注入**

上一步代码占位的 `properties` 字段需要移到构造器。修改类签名：

把 `@RequiredArgsConstructor` 保留，把 `private final CkqaIntegrationProperties properties = null;` 删掉，改为正常字段：

```java
    private final CkqaIntegrationProperties properties;
```

并把它放在其他 final 字段一起（与 `@RequiredArgsConstructor` 配合自动注入）。同时在测试 setUp() 中给 mock：

```java
@BeforeEach
void setUp() {
    samplesStore = mock(PromptTuneAuditSamplesService.class);
    buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
    orchestrator = mock(SingleSampleExtractionOrchestrator.class);
    workspaceService = mock(BuildRunWorkspaceService.class);

    CkqaIntegrationProperties properties = mock(CkqaIntegrationProperties.class);
    CkqaIntegrationProperties.Graphrag graphrag = mock(CkqaIntegrationProperties.Graphrag.class);
    when(properties.getGraphrag()).thenReturn(graphrag);
    when(graphrag.getRoot()).thenReturn("/tmp/graphrag-test");

    service = new AiSuggestionService(
            samplesStore, buildRunsStore, orchestrator, workspaceService, properties, new ObjectMapper()
    );
}
```

并在 service 构造参数顺序中插入 `properties`（在 ObjectMapper 前）。

- [ ] **Step 5: 跑测试，确认通过**

Run: `cd backend/ckqa-back && mvn -q -Dtest=AiSuggestionServiceTest test`

Expected: 4 个测试 PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java
git commit -m "feat(index): 新增 AiSuggestionService AI 预填业务编排 (Phase 3)"
```

---

## Task 5：替换 Controller 占位为真实实现

把 `requestAuditSampleAiSuggestions` 从 501 占位改为调 `AiSuggestionService`。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1: 注入 AiSuggestionService 字段**

定位锚点（现有字段块）：

```java
    private final AuditSampleService auditSampleService;
```

在该行**之后**追加：

```java
    private final AiSuggestionService aiSuggestionService;
```

`@RequiredArgsConstructor` 会自动注入。

- [ ] **Step 2: 添加 import**

```java
import org.ysu.ckqaback.index.AiSuggestionService;
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
```

- [ ] **Step 3: 替换 requestAuditSampleAiSuggestions 方法体**

定位锚点：

```java
    /**
     * AI 预填实体/关系候选（智能能力 A）。Phase 3 落地，本期保留 501 占位。
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<Map<String, Object>> requestAuditSampleAiSuggestions(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @Positive(message = "sampleId必须大于0") Long sampleId
    ) {
        throw notImplemented();
    }
```

替换为：

```java
    /**
     * AI 预填实体/关系候选（Phase 3 智能能力 A）。
     * <p>
     * 同步调用 GraphRAG 单样本抽取（典型耗时 10-30 秒），返回候选实体/关系。
     * 候选不入 DB——前端落到 sample.aiSuggestedEntities/aiSuggestedRelations 局部状态，
     * 用户逐条审阅，被采纳后才进 goldEntities/goldRelations。
     * </p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<AiSuggestionResponse> requestAuditSampleAiSuggestions(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("sampleId") @Positive(message = "sampleId必须大于0") Long sampleId
    ) {
        return ApiResponseUtils.success(aiSuggestionService.generate(buildRunId, sampleId));
    }
```

注意：返回类型从 `Map<String, Object>` 改为 `AiSuggestionResponse`。原 import 中 `import java.util.Map;` 如果还有其他端点（如 `listRelationSchemas`）使用，则保留；否则可删。

- [ ] **Step 4: 编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: BUILD SUCCESS。

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend/ckqa-back && mvn -q test -Dtest='!CkqaBackApplicationTests'`

Expected: 包含 Task 4 新增的 AiSuggestionServiceTest 在内全部 PASS，无回归。

- [ ] **Step 6: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(controller): 替换 ai-suggestions 端点占位为真实实现 (Phase 3)"
```

---

## Task 6：text-selection-model 选区/高亮纯函数 + 单测

把"原文选区 → spanStart/spanEnd"和"文本 + 实体高亮 → 渲染分段"两个纯函数抽出来，方便测试。

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-text-selection-model.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/text-selection-model.js`

**导出形态：**

- `computeSelectionRange(text, selectedText, selectionStart)` → `{ spanStart, spanEnd } | null`
  - 如果 `selectedText` 为空或不在 `text` 中找到，返回 null
  - `selectionStart` 是浏览器选区的起始 offset（**当模板渲染了 HTML 标签后选区 offset 不等于 text offset**——所以传入参数是已经从 DOM 计算好的纯文本 offset）
  - 简化版本：直接 `text.indexOf(selectedText, selectionStart - 10)`，从 selectionStart 附近开始找匹配，避免重复字符串歧义

- `splitTextByEntitySpans(text, entitiesWithSpans)` → `Array<{ type: 'plain' | 'highlight', text, entityId? }>`
  - 输入：原文 + 含 spanStart/spanEnd 的实体数组
  - 输出：交替的"纯文本片段"和"高亮片段"序列，按 spanStart 排序，重叠时按"先到先得"
  - 高亮片段带 entityId 用于后续点击/tooltip

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  computeSelectionRange,
  splitTextByEntitySpans,
} from '../../views/pages/prompt-builder/text-selection-model.js'

describe('computeSelectionRange', () => {
  const text = '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。'

  it('返回选中文本的 spanStart/spanEnd', () => {
    const range = computeSelectionRange(text, '进程', 0)
    assert.deepEqual(range, { spanStart: 0, spanEnd: 2 })
  })

  it('从 selectionStart 附近匹配，避免重复字符串歧义', () => {
    // text 中"进程"出现一次（位置 0），但如果文中有重复
    const dupText = '进程 A 与进程 B 是不同的进程实例'
    const range = computeSelectionRange(dupText, '进程', 5)
    // 期望从 selectionStart=5 附近找最近匹配
    assert.equal(range.spanStart >= 0, true)
    assert.equal(dupText.slice(range.spanStart, range.spanEnd), '进程')
  })

  it('selectionStart 位置正好命中时直接用该位置（精确判断优先）', () => {
    const dupText = '进程 A 与进程 B 是不同的进程实例'
    // selectionStart=5 位置（"进"）正好就是 selectedText 起点
    const range = computeSelectionRange(dupText, '进程', 5)
    assert.deepEqual(range, { spanStart: 5, spanEnd: 7 })
  })

  it('选中文本不在原文中时返回 null', () => {
    const range = computeSelectionRange(text, '不存在的文本', 0)
    assert.equal(range, null)
  })

  it('selectedText 为空字符串时返回 null', () => {
    assert.equal(computeSelectionRange(text, '', 0), null)
    assert.equal(computeSelectionRange(text, '   ', 0), null)
  })

  it('selectedText 自动 trim', () => {
    const range = computeSelectionRange(text, '  进程  ', 0)
    assert.deepEqual(range, { spanStart: 0, spanEnd: 2 })
  })
})

describe('splitTextByEntitySpans', () => {
  const text = '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。'

  it('无 span 实体时返回单个 plain 段', () => {
    const segments = splitTextByEntitySpans(text, [])
    assert.equal(segments.length, 1)
    assert.deepEqual(segments[0], { type: 'plain', text })
  })

  it('单个实体把原文切成 [plain, highlight, plain] 三段', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 0, spanEnd: 2 },  // "进程"
    ])
    assert.equal(segments.length, 2)
    assert.deepEqual(segments[0], { type: 'highlight', text: '进程', entityId: 'e1' })
    assert.equal(segments[1].type, 'plain')
    assert.equal(segments[1].text.startsWith('是程序'), true)
  })

  it('实体跨度位于中间时切成 [plain, highlight, plain]', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 5, spanEnd: 11 },  // "一次执行过程"
    ])
    assert.equal(segments.length, 3)
    assert.equal(segments[0].type, 'plain')
    assert.equal(segments[1].type, 'highlight')
    assert.equal(segments[1].text, '一次执行过程')
    assert.equal(segments[2].type, 'plain')
  })

  it('多个不重叠实体按 spanStart 排序后切分', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e2', spanStart: 16, spanEnd: 20 },  // "资源分配"
      { id: 'e1', spanStart: 0, spanEnd: 2 },    // "进程"
    ])
    // 期望顺序：highlight(进程) - plain - highlight(资源分配) - plain
    const types = segments.map((s) => s.type)
    assert.deepEqual(types, ['highlight', 'plain', 'highlight', 'plain'])
    assert.equal(segments[0].entityId, 'e1')
    assert.equal(segments[2].entityId, 'e2')
  })

  it('重叠实体时先到先得，后到的被忽略', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 0, spanEnd: 4 },   // "进程是程"
      { id: 'e2', spanStart: 2, spanEnd: 6 },   // "程序的一" —— 与 e1 重叠
    ])
    // e1 先（spanStart 较小），e2 因重叠被丢弃
    const highlights = segments.filter((s) => s.type === 'highlight')
    assert.equal(highlights.length, 1)
    assert.equal(highlights[0].entityId, 'e1')
  })

  it('忽略缺少 spanStart/spanEnd 的实体（手动添加未拖选）', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', name: '手动添加' },  // 无 span
      { id: 'e2', spanStart: 0, spanEnd: 2 },
    ])
    const highlights = segments.filter((s) => s.type === 'highlight')
    assert.equal(highlights.length, 1)
    assert.equal(highlights[0].entityId, 'e2')
  })

  it('span 越界时被忽略', () => {
    const segments = splitTextByEntitySpans('短文本', [
      { id: 'e1', spanStart: 100, spanEnd: 200 },
    ])
    assert.equal(segments.length, 1)
    assert.equal(segments[0].type, 'plain')
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-text-selection-model.test.js`

Expected: 测试 FAIL（模块不存在）。

- [ ] **Step 3: 实现纯函数模块**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/text-selection-model.js
//
// 原文拖选 / 实体高亮的纯函数。Phase 3 智能能力——拖选添加实体的字符 offset 计算与高亮分段。

/**
 * 给定原文 + 选中文本 + 选区起始位置（DOM 推算），计算实体的 spanStart / spanEnd。
 *
 * 实现策略：
 * 1. 优先精确判断——如果 text.slice(selectionStart, selectionStart + len) 正好是 selectedText，
 *    直接用 selectionStart 作为 spanStart（最常见场景，避免重复字符串歧义）
 * 2. 退化为从 (selectionStart - 10) 附近 indexOf——容忍前端推算的 selectionStart 有少量偏差
 * 3. 仍找不到时从 0 找——兜底
 * 4. 全部失败返回 null
 *
 * @param {string} text 原文
 * @param {string} selectedText 选中的子串（自动 trim）
 * @param {number} selectionStart 浏览器选区起始 offset（相对于纯文本，调用方需要先剥 HTML）
 * @returns {{spanStart: number, spanEnd: number} | null}
 */
export function computeSelectionRange(text, selectedText, selectionStart) {
  if (!text || typeof selectedText !== 'string') return null
  const trimmed = selectedText.trim()
  if (!trimmed) return null

  // 1. 精确判断：selectionStart 位置正好就是 selectedText
  const safeStart = Math.max(0, selectionStart ?? 0)
  if (text.slice(safeStart, safeStart + trimmed.length) === trimmed) {
    return { spanStart: safeStart, spanEnd: safeStart + trimmed.length }
  }

  // 2. 从 selectionStart 附近找
  const fromHint = Math.max(0, safeStart - 10)
  let idx = text.indexOf(trimmed, fromHint)

  // 3. 仍找不到时从 0 找
  if (idx < 0) idx = text.indexOf(trimmed)
  if (idx < 0) return null

  return { spanStart: idx, spanEnd: idx + trimmed.length }
}

/**
 * 把原文按"实体 spans"切成交替段（plain / highlight）。
 *
 * @param {string} text 原文
 * @param {Array<{id, spanStart?, spanEnd?}>} entitiesWithSpans 含 spanStart/spanEnd 的实体
 * @returns {Array<{type: 'plain' | 'highlight', text: string, entityId?: string}>}
 */
export function splitTextByEntitySpans(text, entitiesWithSpans) {
  if (!text) return []

  // 过滤合法 span：必须有 spanStart/spanEnd，且在 text 范围内
  const validSpans = (entitiesWithSpans ?? [])
    .filter((e) =>
      Number.isInteger(e.spanStart) &&
      Number.isInteger(e.spanEnd) &&
      e.spanStart >= 0 &&
      e.spanEnd <= text.length &&
      e.spanStart < e.spanEnd
    )
    .sort((a, b) => a.spanStart - b.spanStart)

  // 解决重叠：按 spanStart 排序后，新 span 起点必须 >= 上一个的终点
  const nonOverlapping = []
  let lastEnd = 0
  for (const span of validSpans) {
    if (span.spanStart >= lastEnd) {
      nonOverlapping.push(span)
      lastEnd = span.spanEnd
    }
  }

  if (nonOverlapping.length === 0) {
    return [{ type: 'plain', text }]
  }

  const segments = []
  let cursor = 0
  for (const span of nonOverlapping) {
    if (cursor < span.spanStart) {
      segments.push({ type: 'plain', text: text.slice(cursor, span.spanStart) })
    }
    segments.push({
      type: 'highlight',
      text: text.slice(span.spanStart, span.spanEnd),
      entityId: span.id,
    })
    cursor = span.spanEnd
  }
  if (cursor < text.length) {
    segments.push({ type: 'plain', text: text.slice(cursor) })
  }
  return segments
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-text-selection-model.test.js`

Expected: 12 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/text-selection-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-text-selection-model.test.js
git commit -m "feat(prompt-builder): 新增 text-selection-model 选区/高亮纯函数 (Phase 3)"
```

---

## Task 7：AnnotationTextCard.vue 抽出原文卡组件 + 选区监听 + 高亮渲染

把 `AnnotationWorkArea.vue` 中现有的"原文卡"那段 inline HTML 抽到独立组件 `AnnotationTextCard.vue`，新增功能：

1. 渲染时把 `props.text` 按 `splitTextByEntitySpans(text, entities)` 切成 plain/highlight 段
2. 选中文本时弹"添加为实体"浮动按钮
3. 点击按钮 emit `request-add-entity: { name, spanStart, spanEnd }`

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationTextCard.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationTextCard.vue -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  computeSelectionRange,
  splitTextByEntitySpans,
} from './text-selection-model.js'

const props = defineProps({
  /** 原文 */
  text: { type: String, default: '' },
  /** 已确认实体（用于高亮）；含 spanStart/spanEnd 的会被渲染 */
  entities: { type: Array, default: () => [] },
})

const emit = defineEmits(['request-add-entity'])

const segments = computed(() => splitTextByEntitySpans(props.text, props.entities))

// 选区浮动按钮状态
const floatingButton = ref({
  visible: false,
  x: 0,
  y: 0,
  selectedText: '',
  spanStart: 0,
  spanEnd: 0,
})

const textRef = ref(null)
const floatingBtnRef = ref(null)

function handleMouseUp(event) {
  const sel = window.getSelection()
  if (!sel || sel.toString().trim().length === 0) {
    floatingButton.value.visible = false
    return
  }
  const selectedText = sel.toString()
  // 计算选区相对原文的 offset：
  // 选区可能跨多个 highlight/plain 段，但所有段都在 textRef 内。
  // 通过创建 range pre-clone 测量 textRef 内的 textContent 长度差得到 offset。
  const range = sel.getRangeAt(0)
  const preRange = range.cloneRange()
  preRange.selectNodeContents(textRef.value)
  preRange.setEnd(range.startContainer, range.startOffset)
  const selectionStart = preRange.toString().length

  const computed = computeSelectionRange(props.text, selectedText, selectionStart)
  if (!computed) {
    floatingButton.value.visible = false
    return
  }

  // 按钮位置：紧贴选区右下角
  const rect = range.getBoundingClientRect()
  floatingButton.value = {
    visible: true,
    x: rect.right + 4,
    y: rect.bottom + 4,
    selectedText: selectedText.trim(),
    spanStart: computed.spanStart,
    spanEnd: computed.spanEnd,
  }
}

function handleAddEntityClick() {
  emit('request-add-entity', {
    name: floatingButton.value.selectedText,
    spanStart: floatingButton.value.spanStart,
    spanEnd: floatingButton.value.spanEnd,
  })
  floatingButton.value.visible = false
  // 清除选区，避免再次点 mouseup 触发
  window.getSelection()?.removeAllRanges()
}

/**
 * 文档级 mousedown 监听：点击发生在浮动按钮和原文卡之外时关闭按钮。
 * 用 mousedown 而非 click，避免被浏览器选区清除前的 click 误触。
 */
function handleDocumentMousedown(event) {
  if (!floatingButton.value.visible) return
  const target = event.target
  const isInsideText = textRef.value?.contains(target)
  const isInsideButton = floatingBtnRef.value?.contains(target)
  if (!isInsideText && !isInsideButton) {
    floatingButton.value.visible = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleDocumentMousedown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentMousedown)
})
</script>

<template>
  <article class="annotation-text-card">
    <header class="annotation-text-card__head">
      <span class="ann-text-tiny">原文</span>
    </header>
    <div ref="textRef" class="annotation-text-card__body" @mouseup="handleMouseUp">
      <template v-for="(seg, idx) in segments" :key="idx">
        <span v-if="seg.type === 'highlight'" class="annotation-text-card__highlight">{{ seg.text }}</span>
        <template v-else>{{ seg.text }}</template>
      </template>
    </div>
    <Teleport to="body">
      <button
        v-if="floatingButton.visible"
        ref="floatingBtnRef"
        class="annotation-text-card__floating-btn"
        :style="{ position: 'fixed', left: floatingButton.x + 'px', top: floatingButton.y + 'px' }"
        @click="handleAddEntityClick"
      >
        + 添加为实体
      </button>
    </Teleport>
  </article>
</template>

<style scoped>
.annotation-text-card__highlight {
  background: #ddd6fe;
  color: #6d28d9;
  border-radius: 2px;
  padding: 0 2px;
  font-weight: 500;
}
.annotation-text-card__floating-btn {
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10000;
}
.annotation-text-card__floating-btn:hover {
  background: #4f46e5;
}
</style>
```

⚠️ **关于现有样式：** `.annotation-text-card` / `.annotation-text-card__head` / `.annotation-text-card__body` 这些基础样式定义在**全局** `frontend/apps/admin-app/src/styles/components.scss`（不是 AnnotationWorkArea.vue 的 scoped style 里），所以抽到子组件后样式仍然生效。Task 7 Step 1 实现时**不需要**迁移这些样式；本 vue 文件里的 `<style scoped>` 只放新增的高亮和浮动按钮样式。

实施前先 grep 确认这一点：

```bash
grep -n 'annotation-text-card' frontend/apps/admin-app/src/styles/components.scss
```

Expected: 至少 3 行匹配（`.annotation-text-card` 主样式 + `__head` + `__body`）。如果 grep 没结果，说明样式在某处 scoped block，需要先迁移到 `AnnotationTextCard.vue` 的 `<style scoped>` 中再继续。

⚠️ **关键实现点：**
- 用 `Teleport to="body"` 把浮动按钮挂到 body 上，避免被父级 overflow:hidden 截断。
- 关闭按钮的逻辑用**文档级 `mousedown` 监听**而非父级 `click`——这样：
  - mouseup 选中文本时，紧随其后的 click 不会冒泡到父级把刚显示的按钮立刻关掉
  - 点击文档其他位置时，mousedown 会先于 click 触发，按钮被关闭
  - 选区清除（浏览器自动）发生在 mousedown 之后，不影响判断
- 选区 offset 计算用 `range.cloneRange() + selectNodeContents(textRef) + toString().length`——这是标准的 DOM 选区到字符 offset 转换技术。

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationTextCard.vue
git commit -m "feat(prompt-builder): 新增 AnnotationTextCard 原文卡组件 + 选区监听 (Phase 3)"
```

---

## Task 8：EntityEditor.vue 接收预填字段

让 `EntityEditor` 支持外部预填实体名（拖选场景），同时保存 spanStart/spanEnd 一起 emit。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue`

- [ ] **Step 1: 添加 props 和初始化逻辑**

定位锚点：

```javascript
const props = defineProps({
  /** 当前样本中已有的实体列表，用于重名检测 */
  existingEntities: { type: Array, default: () => [] },
})
```

替换为：

```javascript
const props = defineProps({
  /** 当前样本中已有的实体列表，用于重名检测 */
  existingEntities: { type: Array, default: () => [] },
  /** 拖选预填的实体名（来自原文选区） */
  prefilledName: { type: String, default: '' },
  /** 拖选预填的字符位置 [spanStart, spanEnd]，提交时透传到 payload */
  prefilledSpan: {
    type: Object,
    default: null,
    validator: (val) => val === null || (
      Number.isInteger(val.spanStart) && Number.isInteger(val.spanEnd)
    ),
  },
})
```

- [ ] **Step 2: 初始化时把 prefilledName 写入 name ref**

定位锚点：

```javascript
const name = ref('')
```

替换为：

```javascript
const name = ref(props.prefilledName ?? '')
```

并在 `<script setup>` 中追加 watch 让 prefilledName 变化时同步：

```javascript
import { computed, ref, watch } from 'vue'

// ... 其他代码

watch(() => props.prefilledName, (val) => {
  if (val) name.value = val
})
```

注意：如果原 import 里没有 `watch`，需要补到 import 行。

- [ ] **Step 3: 修改 handleSubmit 把 spanStart/spanEnd 透传**

定位锚点：

```javascript
function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  emit('submit', {
    name: trimmedName.value,
    type: type.value,
    description: description.value.trim() || undefined,
  })
  reset()
}
```

替换为：

```javascript
function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  const payload = {
    name: trimmedName.value,
    type: type.value,
    description: description.value.trim() || undefined,
  }
  if (props.prefilledSpan) {
    payload.spanStart = props.prefilledSpan.spanStart
    payload.spanEnd = props.prefilledSpan.spanEnd
  }
  emit('submit', payload)
  reset()
}
```

- [ ] **Step 4: 编译验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: 构建成功。

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue
git commit -m "feat(prompt-builder): EntityEditor 支持 prefilledName/prefilledSpan (Phase 3)"
```

---

## Task 9：RelationEditor 自动反向 + 单测

`relation-types-model.js` 加 `tryReverseRelation` 函数：当 (sourceType, targetType) 无合法关系时，尝试 (targetType, sourceType) 是否有合法关系。

`RelationEditor` 在 `availableRelationTypes.length === 0` 且 `tryReverseRelation` 命中时显示反向横幅 + "调换方向"按钮。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js`
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-editor-reverse.test.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue`

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  filterRelationTypesByEndpoints,
  tryReverseRelation,
} from '../../views/pages/prompt-builder/relation-types-model.js'

describe('tryReverseRelation', () => {
  it('正向无合法关系但反向有时返回反向 hint', () => {
    // FormulaOrDefinition → Concept 在 schema 中没有 defined_by（defined_by 是 Concept→FormulaOrDefinition）
    // 但反向有 defined_by 命中
    const reverse = tryReverseRelation({ sourceType: 'FormulaOrDefinition', targetType: 'Concept' })
    // 因为 related_to 全集兜底，正向其实命中 related_to —— 所以这个 case 不适合
    // 改为测一个明确 only 反向的方向：
    // contains: source 是容器（Course/Chapter/Section），target 是内容
    // 反向 contains: Concept → Chapter 没有 contains，但 belongs_to: Concept → Chapter 有
    // 所以 Concept → Chapter 正向有 belongs_to，不需要反向

    // 真正"正向只剩 related_to、反向有更具体关系"的场景：
    // ToolOrPlatform → Concept：正向无定向关系（只有 related_to 兜底）
    // 反向 Concept → ToolOrPlatform：appears_in 命中（target_types 含 ToolOrPlatform）
    const result = tryReverseRelation({ sourceType: 'ToolOrPlatform', targetType: 'Concept' })
    assert.equal(result.hasReverse, true)
    assert.equal(result.reverseTypes.length > 0, true)
    assert.equal(
      result.reverseTypes.some((r) => r.name === 'appears_in'),
      true
    )
  })

  it('正向已有特定关系时返回 hasReverse=false', () => {
    // Course → Chapter 正向有 contains
    const result = tryReverseRelation({ sourceType: 'Course', targetType: 'Chapter' })
    assert.equal(result.hasReverse, false)
  })

  it('source/target 缺失时返回 hasReverse=false', () => {
    assert.equal(tryReverseRelation({ sourceType: '', targetType: 'Concept' }).hasReverse, false)
    assert.equal(tryReverseRelation({ sourceType: 'Concept', targetType: null }).hasReverse, false)
  })

  it('过滤 related_to 后正向真的为空时才返回反向', () => {
    // 由于 related_to 是全集，filterRelationTypesByEndpoints 总会返回它
    // tryReverseRelation 必须排除 related_to 后判断"正向是否为空"
    // 测试：Term → Course 正向只有 related_to（无 belongs_to/appears_in 命中？）
    // Term 的 belongs_to.target_types = ['Course', 'Chapter', 'Section']  → 命中
    // 所以 Term → Course 正向有 belongs_to，不该反向
    const result1 = tryReverseRelation({ sourceType: 'Term', targetType: 'Course' })
    assert.equal(result1.hasReverse, false)

    // 真正 only related_to 的场景：Course → Course
    // contains 不允许 Course→Course
    // 但所有其他关系两端都不重合 Course
    // related_to 兜底
    // 这种自反 case 在 RelationEditor 里被 isSelfLoop 提前拦下，tryReverseRelation 不会被调用
    // 所以测试改为验证：当 forward 仅含 related_to 时，反向还能找到更具体关系才提示
    // 实际能制造的 case：ToolOrPlatform → Term
    // Term 没有任何关系把 ToolOrPlatform 当 source；ToolOrPlatform 也没有
    // 反向 Term → ToolOrPlatform：appears_in 命中
    const result2 = tryReverseRelation({ sourceType: 'ToolOrPlatform', targetType: 'Term' })
    assert.equal(result2.hasReverse, true)
  })
})
```

⚠️ 这个测试假设 `tryReverseRelation` 的核心逻辑是：

```
1. 计算 forwardTypes = filterRelationTypesByEndpoints(s, t)
2. 如果 forwardTypes 中存在 name !== 'related_to' 的项，返回 { hasReverse: false }
   （因为正向已有更具体关系，不需要提示反向）
3. 否则计算 reverseTypes = filterRelationTypesByEndpoints(t, s)
4. 如果 reverseTypes 中存在 name !== 'related_to' 的项，返回 { hasReverse: true, reverseTypes }
5. 否则返回 { hasReverse: false }
```

也就是说，"正向只剩 related_to 兜底"+"反向有更具体关系"才提示反向。

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-relation-editor-reverse.test.js`

Expected: 测试 FAIL（`tryReverseRelation` 未导出）。

- [ ] **Step 3: 在 relation-types-model.js 末尾追加 tryReverseRelation**

定位锚点：现有文件最后一行 `}`（`describeRelationType` 函数的结尾）。

在文件末尾追加：

```javascript
/**
 * 检测正向无更具体关系（仅剩 related_to 兜底或为空）但反向有更具体关系的情况，
 * 用于 RelationEditor 提示用户调换 source/target。
 *
 * @param {{sourceType: string, targetType: string}} params
 * @returns {{hasReverse: boolean, reverseTypes?: Array}}
 */
export function tryReverseRelation({ sourceType, targetType }) {
  if (!sourceType || !targetType) return { hasReverse: false }

  const forward = filterRelationTypesByEndpoints({ sourceType, targetType })
  const forwardSpecific = forward.filter((r) => r.name !== 'related_to')
  if (forwardSpecific.length > 0) {
    // 正向已有 related_to 之外的更具体关系，不需要反向
    return { hasReverse: false }
  }

  const reverse = filterRelationTypesByEndpoints({ sourceType: targetType, targetType: sourceType })
  const reverseSpecific = reverse.filter((r) => r.name !== 'related_to')
  if (reverseSpecific.length === 0) {
    return { hasReverse: false }
  }

  return { hasReverse: true, reverseTypes: reverseSpecific }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-relation-editor-reverse.test.js`

Expected: 4 个测试 PASS。

- [ ] **Step 5: 修改 RelationEditor.vue 使用 tryReverseRelation**

定位锚点：

```javascript
import { filterRelationTypesByEndpoints } from './relation-types-model.js'
```

替换为：

```javascript
import { filterRelationTypesByEndpoints, tryReverseRelation } from './relation-types-model.js'
```

在 `<script setup>` 末尾（reset 函数之后）追加：

```javascript
const reverseHint = computed(() => {
  if (!sourceEntity.value || !targetEntity.value) return null
  if (isSelfLoop.value) return null
  if (availableRelationTypes.value.length > 0) {
    // 已有正向合法关系（含 related_to），不显示反向提示
    return null
  }
  return tryReverseRelation({
    sourceType: sourceEntity.value.type,
    targetType: targetEntity.value.type,
  })
})

function swapDirection() {
  const tmp = sourceEntityId.value
  sourceEntityId.value = targetEntityId.value
  targetEntityId.value = tmp
  // type 会被 watch(availableRelationTypes) 自动清空
}
```

⚠️ 修订 `availableRelationTypes` 判断逻辑：当前代码会包含 `related_to` 兜底，所以 `availableRelationTypes.length === 0` 几乎不触发。但 reverseHint 在"正向只剩 related_to"时也应该提示。修订：

```javascript
const reverseHint = computed(() => {
  if (!sourceEntity.value || !targetEntity.value) return null
  if (isSelfLoop.value) return null
  // 当正向只有 related_to 兜底（没有更具体关系），且反向有更具体关系时才提示
  return tryReverseRelation({
    sourceType: sourceEntity.value.type,
    targetType: targetEntity.value.type,
  })
})
```

`tryReverseRelation` 内部已经处理"正向有更具体关系时返回 hasReverse=false"。

在模板中"无 schema 关系"提示之**后**追加：

定位锚点：

```html
      <p v-if="sourceEntity && targetEntity && !isSelfLoop && availableRelationTypes.length === 0" class="relation-editor__hint relation-editor__hint--error">
        ⚠ 两端类型「{{ sourceEntity.type }} → {{ targetEntity.type }}」之间没有 schema 合法关系，请检查实体类型是否正确。
      </p>
```

在该 `</p>` 后追加：

```html
      <p v-if="reverseHint?.hasReverse" class="relation-editor__hint relation-editor__hint--reverse">
        💡 当前方向「{{ sourceEntity.type }} → {{ targetEntity.type }}」仅有 related_to 兜底关系；
        反向「{{ targetEntity.type }} → {{ sourceEntity.type }}」可使用更具体关系
        ({{ reverseHint.reverseTypes.map((r) => r.name).join('/') }})。
        <button type="button" class="relation-editor__swap-btn" @click="swapDirection">调换方向</button>
      </p>
```

并补样式：

定位锚点：

```css
.relation-editor__hint--error {
  color: #b45309;
}
```

之后追加：

```css
.relation-editor__hint--reverse {
  color: #6366f1;
}
.relation-editor__swap-btn {
  margin-left: 8px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
}
.relation-editor__swap-btn:hover {
  background: #4f46e5;
}
```

- [ ] **Step 6: 编译验证 + 跑测试**

Run:
```bash
cd frontend/apps/admin-app && pnpm build
cd frontend/apps/admin-app && pnpm test
```

Expected: 构建成功，包含 4 个新增 reverse 测试在内全部 PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-editor-reverse.test.js
git commit -m "feat(prompt-builder): RelationEditor 支持自动反向提示 + 调换方向 (Phase 3)"
```

---

## Task 10：AnnotationWorkArea.vue 接 AI 候选 + reusedFrom "隐藏复用提示" + AnnotationTextCard 替换

修改 `AnnotationWorkArea.vue`：

1. 把现有的内联原文卡 `<article class="annotation-text-card">` 替换为 `<AnnotationTextCard>` 组件
2. 在 AI 横幅里加"生成候选"按钮 emit `request-ai-suggestions`
3. 在 reusedFrom 横幅里加"隐藏复用提示"按钮 emit `dismiss-reused-from`（仅清 `reusedFrom`，不动 gold 数据）
4. 把 `<EntityEditor>` 接收 prefilledName/prefilledSpan
5. 监听 `<AnnotationTextCard>` 的 `request-add-entity` 事件，打开 EntityEditor 并预填

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue`

- [ ] **Step 1: 替换 import 和 defineEmits**

定位锚点：

```javascript
import AnnotationEntityCard from './AnnotationEntityCard.vue'
import AnnotationRelationCard from './AnnotationRelationCard.vue'
import EntityEditor from './EntityEditor.vue'
import RelationEditor from './RelationEditor.vue'
```

之后追加：

```javascript
import AnnotationTextCard from './AnnotationTextCard.vue'
```

`defineEmits` 数组追加 `'request-ai-suggestions'` 和 `'dismiss-reused-from'`：

```javascript
defineEmits([
  'finish-sample',
  'skip-sample',
  'accept-entity',
  'reject-entity',
  'delete-entity',
  'accept-relation',
  'reject-relation',
  'delete-relation',
  'sort-suggestions-by-confidence',
  'create-entity',
  'create-relation',
  'request-ai-suggestions',
  'dismiss-reused-from',
])
```

- [ ] **Step 2: 在 `<script setup>` 末尾追加预填实体相关 ref**

```javascript
const entityEditorPrefill = ref({ name: '', span: null })

function handleRequestAddEntity({ name, spanStart, spanEnd }) {
  entityEditorPrefill.value = { name, span: { spanStart, spanEnd } }
  showEntityEditor.value = true
}
```

- [ ] **Step 3: 改 reusedFrom 横幅，加"隐藏复用提示"按钮**

定位锚点：

```html
      <!-- D 智能：历史复用横幅 -->
      <div v-if="sample.reusedFrom" class="annotation-banner annotation-banner--reuse">
        <span class="annotation-banner__icon">♻</span>
        <div class="annotation-banner__text">
          发现来自
          <strong>{{ sample.reusedFrom.buildRunName }}</strong>
          的标注，已为你预填。
        </div>
      </div>
```

替换为：

```html
      <!-- D 智能：历史复用横幅 -->
      <div v-if="sample.reusedFrom" class="annotation-banner annotation-banner--reuse">
        <span class="annotation-banner__icon">♻</span>
        <div class="annotation-banner__text">
          发现来自
          <strong>{{ sample.reusedFrom.buildRunName }}</strong>
          的标注，已为你预填。
        </div>
        <div class="annotation-banner__actions">
          <button class="ann-btn ann-btn--soft" @click="$emit('dismiss-reused-from', sample.id)">
            隐藏复用提示
          </button>
        </div>
      </div>
```

- [ ] **Step 4: 改 AI 横幅，加"生成候选"按钮 + 进度状态**

定位锚点：

```html
      <!-- A 智能：AI 预填横幅 -->
      <div v-if="aiCount > 0" class="annotation-banner annotation-banner--ai">
        <span class="annotation-banner__icon">✨</span>
        <div class="annotation-banner__text">
          AI 助手已生成 <strong>{{ aiCount }} 个候选实体</strong>，请逐条审阅。
        </div>
        <div class="annotation-banner__actions">
          <button class="ann-btn ann-btn--soft" @click="$emit('sort-suggestions-by-confidence')">按置信度排序</button>
        </div>
      </div>
```

替换为：

```html
      <!-- A 智能：AI 预填横幅 -->
      <div class="annotation-banner annotation-banner--ai">
        <span class="annotation-banner__icon">✨</span>
        <template v-if="aiCount > 0">
          <div class="annotation-banner__text">
            AI 助手已生成 <strong>{{ aiCount }} 个候选实体</strong>，请逐条审阅。
          </div>
          <div class="annotation-banner__actions">
            <button class="ann-btn ann-btn--soft" @click="$emit('sort-suggestions-by-confidence')">按置信度排序</button>
            <button class="ann-btn ann-btn--soft" @click="$emit('request-ai-suggestions', sample.id)">重新生成</button>
          </div>
        </template>
        <template v-else>
          <div class="annotation-banner__text">
            可用 AI 助手抽取一遍，作为标注起点（约 10-30 秒，所有候选都需逐条审阅）。
          </div>
          <div class="annotation-banner__actions">
            <button class="ann-btn ann-btn--accent" @click="$emit('request-ai-suggestions', sample.id)">
              生成候选
            </button>
          </div>
        </template>
      </div>
```

⚠️ **注意 spec § 风险 #1 的硬性约束**：横幅按钮文案必须是"按置信度排序"和"生成候选"，**不允许出现"全部采纳"**。

- [ ] **Step 5: 替换原文卡为 AnnotationTextCard 组件**

定位锚点：

```html
      <!-- 原文卡 -->
      <article class="annotation-text-card">
        <header class="annotation-text-card__head">
          <span class="ann-text-tiny">原文</span>
        </header>
        <div class="annotation-text-card__body">
          {{ sample.text }}
        </div>
      </article>
```

替换为：

```html
      <!-- 原文卡（含选区监听 + 实体高亮） -->
      <AnnotationTextCard
        :text="sample.text"
        :entities="sample.goldEntities"
        @request-add-entity="handleRequestAddEntity"
      />
```

- [ ] **Step 6: 修改 `<EntityEditor>` 标签，传 prefill props**

定位锚点：

```html
        <EntityEditor
          v-if="showEntityEditor"
          :existing-entities="mergedEntities"
          @submit="(payload) => { $emit('create-entity', payload); showEntityEditor = false }"
          @cancel="showEntityEditor = false"
        />
```

替换为：

```html
        <EntityEditor
          v-if="showEntityEditor"
          :existing-entities="mergedEntities"
          :prefilled-name="entityEditorPrefill.name"
          :prefilled-span="entityEditorPrefill.span"
          @submit="(payload) => {
            $emit('create-entity', payload)
            showEntityEditor = false
            entityEditorPrefill = { name: '', span: null }
          }"
          @cancel="() => {
            showEntityEditor = false
            entityEditorPrefill = { name: '', span: null }
          }"
        />
```

- [ ] **Step 7: 编译验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: 构建成功。

- [ ] **Step 8: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue
git commit -m "feat(prompt-builder): AnnotationWorkArea 接入 AI 候选/原文拖选/复用 banner 交互 (Phase 3)"
```

---

## Task 11：PromptBuilderPrepareStep.vue 处理 AI 候选 + 复用关闭

新增两个 handler：

1. `handleRequestAiSuggestions(sampleId)`：调 `requestAuditSampleAiSuggestions(buildRunId, sampleId)`，把响应的 entities 写到 `sample.aiSuggestedEntities`、relations 写到 `sample.aiSuggestedRelations`
2. `handleDismissReusedFrom(sampleId)`：把 `sample.reusedFrom` 设为 null（仅前端隐藏，不影响 DB）

修改 `handleAcceptRelation` 让它能把 GraphRAG 的 originalSource/originalTarget（实体名）在采纳时匹配到 sample.goldEntities 里的 id。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

- [ ] **Step 1: 添加 import**

定位锚点：

```javascript
import {
  listAuditSamples,
  generateAuditSet,
  updateAuditSample,
} from '../../../api/prompt-tune-pipeline.js'
```

替换为：

```javascript
import {
  listAuditSamples,
  generateAuditSet,
  updateAuditSample,
  requestAuditSampleAiSuggestions,
} from '../../../api/prompt-tune-pipeline.js'
```

- [ ] **Step 2: 新增 ref 和 handler**

在 `<script setup>` 中（合适位置，建议放在 `loading` ref 之后）追加：

```javascript
// 按 sample id 维度记录 AI 抽取 loading 状态，支持多样本并发
const aiSuggestionLoadingSampleId = ref(null)

// 已采纳 AI 候选实体的本地映射：originalName -> goldEntityId
// AI 关系候选用实体名引用 source/target，采纳关系时优先查这个 map 取 id
const aiEntityNameToGoldId = ref(new Map())
```

在 `sortSuggestionsByConfidence` 函数之后追加：

```javascript
async function handleRequestAiSuggestions(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  if (aiSuggestionLoadingSampleId.value === sampleId) return  // 防重复点击同一样本
  aiSuggestionLoadingSampleId.value = sampleId
  ElMessage.info('AI 候选生成中，约 10-30 秒...')
  try {
    const response = await requestAuditSampleAiSuggestions(buildRunId.value, sample.id)
    // response 形态：{ entities: [...], relations: [...] }
    // 实体：每条带 suggestionSource: 'ai_suggested'，name/type/confidence 等领域字段保留
    // 关系：每条带 suggestionSource: 'ai_suggested'、originalSource/originalTarget（实体名）、type
    //
    // 注意 unwrapApiResponse 的形态：CKQA api/client.js 在业务成功时直接返回 body.data，
    // 所以 response 就是 AiSuggestionResponse 内容（{entities, relations}），不需要再 .data
    sample.aiSuggestedEntities = (response?.entities ?? []).map((e, idx) => ({
      ...e,
      id: e.id ?? `ai_e_${Date.now()}_${idx}`,  // 临时本地 id（不持久化）
    }))
    sample.aiSuggestedRelations = (response?.relations ?? []).map((r, idx) => ({
      ...r,
      id: r.id ?? `ai_r_${Date.now()}_${idx}`,
    }))
    ElMessage.success(`生成完成：${sample.aiSuggestedEntities.length} 个实体、${sample.aiSuggestedRelations.length} 个关系候选`)
  } catch (err) {
    ElMessage.error(err?.message ?? 'AI 候选生成失败')
  } finally {
    if (aiSuggestionLoadingSampleId.value === sampleId) {
      aiSuggestionLoadingSampleId.value = null
    }
  }
}

function handleDismissReusedFrom(sampleId) {
  const sample = findSample(sampleId)
  if (sample) {
    // 仅隐藏复用提示横幅，不删除已预填的 gold 数据。
    // 用户如需删除某条预填实体/关系，使用 EntityCard/RelationCard 上的删除按钮。
    sample.reusedFrom = null
  }
}
```

- [ ] **Step 3: 修改 handleAcceptEntity 把 originalName → goldEntityId 记入映射表**

定位锚点：现有 `handleAcceptEntity` 函数。

```javascript
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
```

替换为：

```javascript
async function handleAcceptEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const previousStatus = sample.status
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  // 注意 source 字段语义：AI 候选用 suggestionSource 标记来源；落到 goldEntities 时
  // source 字段表示"采纳来源"。剥掉 suggestionSource 保持 entity shape 干净。
  const acceptedEntity = { ...picked, source: 'accepted' }
  delete acceptedEntity.suggestionSource
  sample.goldEntities.push(acceptedEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'

  // 记录 originalName → 新 entity id 映射，供后续采纳 AI 关系候选使用
  const originalName = picked.name
  if (originalName) {
    aiEntityNameToGoldId.value.set(originalName, acceptedEntity.id)
  }

  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已采纳')
  } catch {
    sample.status = previousStatus
    sample.aiSuggestedEntities.splice(idx, 0, picked)
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== acceptedEntity.id)
    if (originalName) aiEntityNameToGoldId.value.delete(originalName)
  }
}
```

- [ ] **Step 4: 修改 handleCreateEntity 把 spanStart/spanEnd 落到 goldEntities**

定位锚点：现有 `handleCreateEntity` 函数。

```javascript
async function handleCreateEntity(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newEntity = {
    id: generateEntityId(),
    name: payload.name,
    type: payload.type,
    description: payload.description,
    source: 'manual',
  }
  sample.goldEntities.push(newEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已添加实体')
  } catch {
    sample.status = previousStatus
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== newEntity.id)
  }
}
```

替换为（增加 spanStart/spanEnd 透传）：

```javascript
async function handleCreateEntity(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newEntity = {
    id: generateEntityId(),
    name: payload.name,
    type: payload.type,
    description: payload.description,
    source: 'manual',
  }
  // 拖选场景：把 spanStart/spanEnd 写入 entity，原文卡才能渲染紫色高亮
  if (Number.isInteger(payload.spanStart) && Number.isInteger(payload.spanEnd)) {
    newEntity.spanStart = payload.spanStart
    newEntity.spanEnd = payload.spanEnd
  }
  sample.goldEntities.push(newEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已添加实体')
  } catch {
    sample.status = previousStatus
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== newEntity.id)
  }
}
```

- [ ] **Step 5: 修改 handleAcceptRelation 用 aiEntityNameToGoldId 优先匹配**

定位锚点：现有 `handleAcceptRelation` 函数。

```javascript
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
```

替换为：

```javascript
async function handleAcceptRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const picked = sample.aiSuggestedRelations[idx]

  // AI 候选关系用 originalSource/originalTarget（实体名字符串），需要找 entity id：
  // 1. 优先查 aiEntityNameToGoldId（用户已采纳的 AI 实体记录的 originalName→id 映射）
  // 2. fallback 按 name 查 sample.goldEntities（手动添加的实体）
  let sourceEntityId = picked.sourceEntityId
  let targetEntityId = picked.targetEntityId
  if (!sourceEntityId && picked.originalSource) {
    sourceEntityId = aiEntityNameToGoldId.value.get(picked.originalSource)
        ?? sample.goldEntities.find((e) => e.name === picked.originalSource)?.id
  }
  if (!targetEntityId && picked.originalTarget) {
    targetEntityId = aiEntityNameToGoldId.value.get(picked.originalTarget)
        ?? sample.goldEntities.find((e) => e.name === picked.originalTarget)?.id
  }
  if (!sourceEntityId || !targetEntityId) {
    ElMessage.warning(
      `请先采纳两端实体（缺少：${!sourceEntityId ? picked.originalSource : ''}${
        !sourceEntityId && !targetEntityId ? '、' : ''
      }${!targetEntityId ? picked.originalTarget : ''}）`
    )
    return
  }

  sample.aiSuggestedRelations.splice(idx, 1)
  const newRelation = {
    id: picked.id,
    sourceEntityId,
    targetEntityId,
    type: picked.type,
    evidence: picked.evidence,
    description: picked.description,
    source: 'accepted',
  }
  sample.goldRelations.push(newRelation)
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
    ElMessage.success('已采纳')
  } catch {
    sample.aiSuggestedRelations.splice(idx, 0, picked)
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
  }
}
```

- [ ] **Step 6: 在模板中绑定新 emit**

定位锚点：

```html
          <AnnotationWorkArea
            :sample="activeSample"
            @finish-sample="handleFinishSample"
            @skip-sample="handleSkipSample"
            @accept-entity="handleAcceptEntity"
            @reject-entity="handleRejectEntity"
            @delete-entity="handleDeleteEntity"
            @accept-relation="handleAcceptRelation"
            @reject-relation="handleRejectRelation"
            @delete-relation="handleDeleteRelation"
            @sort-suggestions-by-confidence="sortSuggestionsByConfidence"
            @create-entity="handleCreateEntity"
            @create-relation="handleCreateRelation"
          />
```

替换为：

```html
          <AnnotationWorkArea
            :sample="activeSample"
            @finish-sample="handleFinishSample"
            @skip-sample="handleSkipSample"
            @accept-entity="handleAcceptEntity"
            @reject-entity="handleRejectEntity"
            @delete-entity="handleDeleteEntity"
            @accept-relation="handleAcceptRelation"
            @reject-relation="handleRejectRelation"
            @delete-relation="handleDeleteRelation"
            @sort-suggestions-by-confidence="sortSuggestionsByConfidence"
            @create-entity="handleCreateEntity"
            @create-relation="handleCreateRelation"
            @request-ai-suggestions="handleRequestAiSuggestions"
            @dismiss-reused-from="handleDismissReusedFrom"
          />
```

- [ ] **Step 7: 编译验证 + 跑测试**

Run:
```bash
cd frontend/apps/admin-app && pnpm build
cd frontend/apps/admin-app && pnpm test
```

Expected: 构建成功，所有测试 PASS。

- [ ] **Step 8: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
git commit -m "feat(prompt-builder): 02 步接入 AI 候选 + 拖选 span 落库 + 关系 originalName 映射 (Phase 3)"
```

---

## Task 12：端到端手工验证

启动本地环境，浏览器走完整流程。

**前置：** infra docker compose 已启动，后端在跑。

- [ ] **Step 1: 启动前端 dev server**

由于 `pnpm dev` 是长驻进程，**不要用 bash 工具直接跑**。手动在新终端：

```bash
cd frontend/apps/admin-app
pnpm dev
```

- [ ] **Step 2: 浏览器手工验证**

1. 登录 admin-app，进入提示词构建器 02 步。
2. 进入标注 IDE，选一条 priority=high 的样本。
3. **AI 预填**：点紫色横幅"生成候选"按钮。预期：
   - toast "AI 候选生成中，约 10-30 秒..."
   - 等 10-30 秒后，候选实体卡片以紫色虚线边框显示
   - 横幅文案变为"AI 助手已生成 N 个候选实体，请逐条审阅"
   - 横幅按钮变为"按置信度排序"和"重新生成"
   - **不应出现"全部采纳"按钮**（spec § 风险 #1 缓解）
4. 点击一个候选实体的"采纳"按钮 → 实体进入已确认列表（紫色边框消失，变成普通卡片）。
5. 点击"按置信度排序" → 候选实体重新按 confidence 倒序排列。
6. **拖选添加实体**：在原文中用鼠标选中一段文字，松开鼠标。预期：
   - 选区右下角弹出紫色"+ 添加为实体"按钮
   - 点击按钮 → EntityEditor 弹出，"实体名"字段已预填选中的文字
   - 修改类型，点添加 → 新实体落到列表，**spanStart/spanEnd 写入数据**
7. **原文高亮**：刷新页面后，已确认实体如果带 spanStart/spanEnd（手动添加未拖选的没有），应在原文中以紫色背景高亮显示。
8. **关系自动反向**：到关系区，点"+ 添加关系"。
   - 选源实体类型 ToolOrPlatform 的实体（如果有的话；没有就先添加一个）
   - 选目标实体类型 Concept 的实体
   - 关系类型下拉应只有 `related_to`
   - 紫色提示："当前方向 ToolOrPlatform → Concept 仅有 related_to 兜底关系；反向 Concept → ToolOrPlatform 可使用 appears_in。[调换方向]"
   - 点"调换方向"按钮 → source/target 自动调换，关系类型下拉刷新出现 `appears_in`
9. **历史复用 banner**：找一条带 ♻ 横幅的样本（需要这条样本在其他 build run 中已经标注过且有相同 stable_key）。
   - 横幅显示"发现来自 <buildRunName> 的标注，已为你预填"
   - 点"隐藏复用提示"按钮 → 横幅消失（这只是隐藏来源说明，已预填的实体/关系仍保留——本期"删除预填"需要用户手动删除每条；如果将来需要"一键清空预填"，等可识别 reused 来源字段的 source 标识落地后再做）
10. MySQL 验证：

```bash
mysql -uroot -p ocqa -e "SELECT id, JSON_EXTRACT(gold_entities, '\$[0].spanStart') AS first_span FROM prompt_tune_audit_samples WHERE build_run_id = <BUILD_RUN_ID> AND JSON_LENGTH(gold_entities) > 0 LIMIT 3;"
```

应能看到拖选添加的实体的 spanStart 字段。

- [ ] **Step 3: 关闭 dev server**

按 Ctrl+C 或 kill 后台进程。

- [ ] **Step 4: 不需要 commit**

至此 Phase 3 完结。02 步标注 IDE 现在具备 AI 预填、拖选添加、原文高亮、关系反向、历史复用 banner 5 项智能能力。

---

## 已识别风险

1. **AI 预填的"采纳关系"匹配仍有边界**（v3 已加 aiEntityNameToGoldId 映射缓解）：
   - 用户先采纳 AI 实体（记录到 map）→ 再采纳 AI 关系（查 map 取 id）的主路径已稳定。
   - 但用户**修改了 AI 候选实体的名字**再采纳时，`acceptedEntity.name !== picked.name`，这时记录的 key 是 `picked.name`，关系候选的 originalSource 也是 `picked.name`，所以仍能命中。
   - **失败场景**：用户**先**手动添加了一个同名实体（在采纳 AI 实体之前），然后采纳 AI 关系时 `aiEntityNameToGoldId` 还没记录，fallback 按 name 找 sample.goldEntities → 命中手动添加的那个，**关系会绑到错的 entity id**。本期接受——同名场景在 Phase 2c-pre 已经有重名警告，发生概率低。

2. **GraphRAG 单样本抽取耗时不可预测**：`SingleSampleExtractionOrchestrator.EXTRACT_TIMEOUT = 2 分钟`，但实际 LLM 调用如果 backoff 重试会更慢。本期同步等，超时返回 504。Phase 8 异步化时一起改造。

3. **拖选 spanStart/spanEnd 在文本被改的情况下会失效**：用户拖选时计算的 offset 是相对**当前 sample.text** 的；如果 audit 集后续被 force 重新生成，sample.text 可能变（即使 stable_key 相同），spanStart/spanEnd 指向的内容就错位。本期接受——重新生成本身就会清空 reviewer_decision，用户会重新审阅，spanStart/spanEnd 错位也会被发现。

4. **AnnotationTextCard 的选区按钮位置在长文本滚动时不会跟随**：`Teleport to="body"` + `position: fixed` 导致按钮位置固定在视窗。如果用户在按钮出现后滚动了原文卡，按钮不会跟随。缓解：监听 scroll 事件关闭按钮。本期不做（出现频率低）。

5. **`tryReverseRelation` 测试用例对当前 schema 敏感**：测试假设了 schema 中"ToolOrPlatform → Concept 只剩 related_to、反向 appears_in 命中"等关系。如果未来 `relation-types-model.js` 调整 source_types/target_types，测试可能失败。**v3 评估后保留**：当前 schema 与 graphrag_pipeline 配置同步，schema 改动会同步前端 hardcode；测试失败比 schema 漂移导致 UI 异常更早被发现，是好事。

6. **AI 横幅在没有 sample 时仍渲染**：当前模板用 `<div class="annotation-banner annotation-banner--ai">` 无条件包裹，应该套在 `<template v-else>` 内（sample 存在分支）。Task 10 的代码段已经隐含在 `<template v-else>` 中（外层 `v-if="!sample"` 和 `<template v-else>` 已存在）。验证时确认。

7. **"隐藏复用提示"按钮的语义边界**：v3 修订后按钮文案改为"隐藏复用提示"，仅清前端 `sample.reusedFrom`，不删除已预填的 gold 数据。如果用户期望"完全清除来自其他 build run 的预填"，需要逐条删除——这个边界本期接受。Phase 3+ 如要"一键清空"，需要在 `gold_entities` / `gold_relations` 元素上加可识别的 `source: 'reused'` 标记（当前不区分 reused 来源 vs 用户自填）。

8. **AI 抽取使用的 prompt 是 GraphRAG 当前激活版本而非 customPromptDraft**：用户在 05 步保存的 customPromptDraft 不会自动用于 AI 预填——需要先通过 `confirmPrompt` 把草稿写到 GraphRAG 的 prompt 路径。本期接受——customPromptDraft 落到磁盘的逻辑随 Phase 6 `<PromptDisplay>` 一起做。

