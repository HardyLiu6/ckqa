# Prompt Builder Phase 4：03 步候选 Prompt 生成 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 03 步从纯 mock 切到真实 API：后端实现 `POST /candidates` 触发 `generate_candidate_prompts.py`、`GET /candidates` 读 manifest 并按 build run 隔离输出；前端把 `MOCK_CANDIDATES` 替换成真实接口数据，加载 / 错误 / 空态三态健全。

**Architecture:**
- 候选输出按 build run 隔离，目录约定 `<workspace>/prompt/candidates/`，避免不同 build run 的 distilled fewshot 互相覆盖。
- 同步执行模式（脚本实测 < 100 ms），仿 `AuditPipelineOrchestrator` 用 `processRunner.run` 包装。
- manifest 字段扩展走"后端拼装"（路径 B）：`displayNameZh / category / isRecommended / traits` 在后端 `CandidateMetadataLookup` 静态硬编码，与算法产物解耦；`promptSizeBytes / schemaUsed / fewshotExampleCount / fewshotStrategy / basePromptSource / generationTime` 直接从 manifest 透传；`estimatedTokenPerCall` 由后端按 `promptSizeBytes / 4 + 200` 推算。
- 关键修复：02 步导出的 `audit_extraction_set.json` 不含 gold 字段（gold 存在 DB），Phase 4 在生成候选前需要把 DB gold 合并写到 `<workspace>/prompt/candidates/audit_with_gold.json` 再传给 Python 脚本，才能让 distilled fewshot 命中真实标注。
- 同步修复 Phase 3 遗留：`AiSuggestionService.resolvePromptFile` 改为优先读 build run workspace，没有时 fallback 到仓库根 frozen_v1。

**Tech Stack:** Spring Boot 4.0.5 + Java 21 + MyBatis-Plus（后端）；Vue 3 + Element Plus + Vite（前端）；Python `generate_candidate_prompts.py`（候选生成脚本，无需修改）。

---

## 范围与非范围

### 包含
1. 候选生成 + 列表 + 抽屉预览 prompt 文本三个端点的真实落地。
2. manifest 元数据扩展（`displayNameZh` / `isRecommended` / `traits` / `category` / `estimatedTokenPerCall`）后端拼装。
3. 抽屉读 prompt 文件正文（懒加载，不放进列表响应）。
4. 03 步进入门控（02 步至少 1 条 `completed`）。
5. DB gold 合并到 audit JSON 后再传给脚本（关键 bug 修复）。
6. Phase 3 fix：`AiSuggestionService.resolvePromptFile` 改读 build run workspace + fallback 仓库根。
7. 全选 / 反选 / 仅选基线快捷操作（前端已实现，对接后端数据）。

### 不包含
- 自定义 fewshot 配置入口（spec 已留口子，Phase 6+ 做）。
- 候选间评分对比（Phase 5）。
- 04 步评分页面（Phase 5）。
- 异步任务化（同步够用，~66 ms）。
- "重新生成候选"按钮的 UI 化（POST 已是覆盖式，但 UI 上不暴露按钮，由"刷新页面"或"返回 02 改完再回来"自然触发）。
- 历史 build run 的 candidate manifest 迁移（仓库根的 frozen_v1 manifest 保留作为 AI 抽取 fallback，不删）。

---

## 关键架构决策

### 决策 1：执行模式 — 同步

**理由：** 实测 `python -m scripts.prompt_tuning.generate_candidate_prompts --overwrite` 耗时 66 ms，纯字符串拼接 + schema 模板 + TF-IDF MMR fewshot 选择，无 LLM 调用。异步基建（任务表 + worker + 轮询）的实现成本是同步的 5-10 倍，没有等价收益。

**实现：** 仿 02 步 `AuditPipelineOrchestrator` 用 `processRunner.run` 同步等，超时上限 60 秒（远超实测 66 ms）。

### 决策 2：候选输出位置 — 按 build run 隔离

**理由：** `schema_fewshot_distilled_v2_strict_tuple` 候选含从 audit gold 提取的 fewshot 微示例（`_build_distilled_micro_example_lines`）。不同 build run 的 audit gold 不一致 → prompt 内容不一致。全局共享会互相覆盖、破坏评分可追溯性。

**实现：**
- 输出路径：`<GRAPHRAG_BUILD_RUNS_ROOT>/user_<uid>/kb_<kbid>/build_<rid>/prompt/candidates/`
- 目录结构：
  ```
  prompt/candidates/
  ├── manifest.json                                       ← 后端读这个
  ├── audit_with_gold.json                                ← 合并 DB gold 后写出，作为 --audit_file 传脚本
  ├── default/{prompt.txt, README.md}
  ├── auto_tuned/{prompt.txt, README.md}
  ├── schema_aware_directional_v2/{prompt.txt, README.md}
  └── schema_fewshot_distilled_v2_strict_tuple/{prompt.txt, README.md}
  ```
- 仓库根 `prompts/candidates/` 降级为出厂示例 + AI 抽取 fallback（保留不删）。

### 决策 3：manifest 字段扩展 — 后端拼装（路径 B）

**理由：** `displayNameZh / isRecommended / traits / category` 是前端展示层元数据（产品/UX 决策），与算法产物解耦。Build run 隔离场景下，路径 A（改 Python 脚本写 manifest）会让历史 build run 的 manifest 锁死老译名，违背 SoC。

**实现：** 新建 `CandidateMetadataLookup` 类，4 个候选的 `displayNameZh / category / isRecommended / traits / description` 全部硬编码为静态 Map / Set。Phase 7+ 后端化 schema 时迁移到配置层。

**字段来源对照表：**

| 字段 | 来源 |
|---|---|
| `candidateId` | manifest 透传 (`candidate_name`) |
| `displayNameZh` | `CandidateMetadataLookup` 硬编码 Map |
| `category` | `CandidateMetadataLookup` 硬编码 Map |
| `description` | `CandidateMetadataLookup` 硬编码 Map |
| `isRecommended` | `CandidateMetadataLookup` 硬编码 Set |
| `traits` | `CandidateMetadataLookup` 硬编码 Map<id, List<{key,label}>> |
| `estimatedTokenPerCall` | 后端按 `promptSizeBytes / 4 + 200` 推算 |
| `promptSizeBytes` | manifest 透传 (`prompt_size_bytes` 字段；缺失时由后端读文件大小补） |
| `schemaUsed` | manifest 透传 (`schema_used`，bool) |
| `fewshotExampleCount` | manifest 透传 (`fewshot_example_count`) |
| `fewshotStrategy` | manifest 透传 (`fewshot_strategy`) |
| `basePromptSource` | manifest 透传 (`base_prompt_source`，绝对路径时简化为相对路径或文件名) |
| `generationTime` | manifest 透传 (`generation_time`) |

### 决策 4：推荐徽章 — 硬编码

**理由：** 用户已确认"固定就行"。Phase 5 评分历史落地后再考虑"基于历史评分动态推荐"，本期 YAGNI。

**实现：** `CandidateMetadataLookup.RECOMMENDED_CANDIDATES = Set.of("schema_fewshot_distilled_v2_strict_tuple")`。

### 决策 5：POST `/candidates` 是覆盖式

**理由：** 用户在 02 步加了新 gold 标注后，distilled 候选的 fewshot 应该更新。脚本只跑 66 ms，重跑成本低，避免"我标了新东西但 03 步还显示旧候选"的困惑。

**实现：** Python 脚本调用时一律传 `--overwrite`。

### 决策 6：GET `/candidates` 纯只读

**理由：** 保持 GET/POST 语义清晰。前端能根据空态主动引导用户。

**实现：** Build run workspace 下 manifest 不存在时返回空数组 + 业务码 `CANDIDATES_NOT_GENERATED(4105)`。前端据此显示空态 + "立即生成"按钮（点击后调 POST）。

### 决策 7：03 步进入门控

**理由：** 没有任何 audit gold 时，`schema_fewshot_distilled_v2_strict_tuple` 候选会退化（无 fewshot 例子可挑）。

**实现：** 02 步至少 1 条 `completed` 才能进 03。门控放前端（`PromptBuilderCandidatesStep.onMounted` 检查 `GET /audit-samples` 看 completed 数）。不满足时显示空态："请先完成 02 步至少 1 条样本审阅" + "返回 02"按钮，不调 POST。

### 决策 8：抽屉预览 prompt 文本

**理由：** prompt 文件 ~30 KB，列表接口里全部塞进去会让 GET `/candidates` 响应膨胀到 ~120 KB（4 候选）。抽屉是按需打开的，懒加载更合理。

**实现：** 新增 `GET /candidates/{candidateId}/prompt`，后端读 `<workspace>/prompt/candidates/<candidateId>/prompt.txt` 返回纯文本。`candidateId` 必须 ∈ 4 个白名单值，避免路径穿越。

---

## 已识别风险

1. **manifest.json 字段在不同候选间不一致**：现有 4 个候选有的有 `production_status` 有的没有；`schema_used` 在 default 里是 `false`，schema_aware 里是 `true`。后端读取时**必须容错**，缺字段返回 null 而不是抛异常。
2. **`promptSizeBytes` 在 manifest 中缺失**：本仓库现存的 manifest 没有这个字段（spec § "manifest 字段透传" 提到了但脚本未写）。后端 fallback：读 `<candidate_dir>/prompt.txt` 文件大小作为 `promptSizeBytes`。
3. **`auto_tuned` 候选的 `base_prompt_source` 是绝对路径**：经过你的环境路径（`/home/sunlight/...`）。后端不应直接透传给前端展示，**展示时必须简化为相对路径或仅显示文件名**，避免暴露服务器路径。
4. **DB gold 合并 JSON 的字段命名一致性**：DB 里 entity 字段是 `name/type/description/source/spanStart/spanEnd`；脚本期望的 audit JSON 中 entity 字段是 `id/name/type/description/...`（详见脚本 `_build_distilled_micro_example_items`）。**合并时必须保持字段对齐**，否则 distilled fewshot 命中失败回退到 manual。Task 4 单测用真实 DB gold 形态覆盖。
5. **首次进入 03 步**用户体验：候选不存在时 `POST /candidates` 跑 ~100 ms，前端要给加载提示但不必长 spinner（百毫秒内完成）。
6. **`candidateId` 白名单**：本期硬编码 4 个，后续若 manifest 出现第 5 个候选，POST 抛 `CANDIDATE_GENERATION_FAILED` 提示"未知候选"。Task 8 单测覆盖。

---

## 文件结构（新增 / 修改）

### 后端
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateMetadataLookup.java`（4 候选硬编码元数据）
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`（读 manifest.json 容错）
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`（包装 Python 脚本同步执行 + 合并 DB gold）
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`（业务编排：generate / list / loadPromptText）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java`（加 description 字段）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`（加 4104 / 4105 / 5007）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`（替换 501 占位）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java`（fallback 到 build run workspace candidate）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`（layout 加 `prompt/candidates`）
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateMetadataLookupTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java`（新增 fallback 路径单测）

### 前端
- 修改 `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`（加 `getCandidatePromptText`）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`（去 mock + 加门控 + 加载错误三态）
- 修改 `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（透传 buildRunId）
- 测试 `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-candidates.test.js`

---

## Task 0：契约自检

> 在写代码前确认所有外部依赖契约稳定，避免后续 Task 因假设错误返工。

**Files:**
- Read: `graphrag_pipeline/scripts/prompt_tuning/generate_candidate_prompts.py`
- Read: `graphrag_pipeline/prompts/candidates/manifest.json`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditPipelineOrchestrator.java`
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue`
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/candidates.js`

- [ ] **Step 1：确认 Python 脚本接口**

Run:
```bash
cd graphrag_pipeline && python -m scripts.prompt_tuning.generate_candidate_prompts --help 2>&1 | head -40
```

Expected：脚本输出帮助文档，含 `--samples_file / --audit_file / --output_dir / --overwrite / --report_file` 等参数。确认参数名与本计划 Task 5 中的命令行调用一致。

- [ ] **Step 2：确认 manifest.json 顶层结构**

Run:
```bash
python -c "import json; d=json.load(open('graphrag_pipeline/prompts/candidates/manifest.json')); print('top:', list(d.keys())); print('candidate keys:', list(d['candidates'][0].keys()))"
```

Expected 输出含：
- 顶层：`task / schema_version / language / output_dir / generated_at / last_updated_at / notes / inputs / candidates`
- 候选条目：`candidate_name / source_type / base_prompt_source / schema_used / fewshot_used / fewshot_example_count / fewshot_strategy / generation_time / files / notes`

如果实际字段缺少 `prompt_size_bytes`，确认 Task 3 中 `CandidateManifestReader` 的 fallback 逻辑（读 prompt.txt 文件大小补）。

- [ ] **Step 3：确认 CandidateResponse DTO 当前字段**

Run:
```bash
grep -E "private final" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java
```

Expected：`candidateId / displayNameZh / category / isRecommended / traits / estimatedTokenPerCall / promptSizeBytes / schemaUsed / fewshotExampleCount / fewshotStrategy / basePromptSource / generationTime`。

注意：当前 `traits` 是 `List<String>`，但前端 `CandidateCard` 期望 `List<{key, label}>`。Task 1 需要把 `traits` 类型改成包装对象。

- [ ] **Step 4：确认 02 步 audit JSON 落盘路径**

Run:
```bash
ls graphrag_pipeline/runtime/kb-build-runs/user_0/kb_5/build_18/prompt/audit/ 2>/dev/null
```

Expected 输出含 `audit_extraction_set.json` 和 `prompt_tuning_samples.json`。如果该 build_run 不存在（环境差异），改用任意已跑过 02 步的 build_run。

- [ ] **Step 5：确认错误码命名空间空闲**

Run:
```bash
grep -E "4104|4105|5007|5008" backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
```

Expected 输出：无（这 4 个码未占用）。

- [ ] **Step 6：自检通过承诺**

如果 Steps 1-5 全部 PASS，本计划架构假设成立，进入 Task 1。

如果 Step 1 / 2 / 3 / 5 任一项与计划假设不符，**停止执行**，把不符细节贴出来重新调整计划。

**Step 7（手工）：无 commit**

Task 0 不产生代码改动。

---

## Task 1：扩展 CandidateResponse DTO

新增 `description` 字段（前端 CandidateCard 渲染用），把 `traits` 类型从 `List<String>` 改为 `List<TraitInfo>`。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/dto/CandidateResponseTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/dto/CandidateResponseTest.java`:

```java
package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesAllFields() throws Exception {
        CandidateResponse response = CandidateResponse.builder()
                .candidateId("default")
                .displayNameZh("默认基线")
                .category("baseline")
                .description("基线 · 课程域微调")
                .isRecommended(false)
                .traits(List.of(
                        CandidateResponse.TraitInfo.builder().key("baseline").label("课程基线").build(),
                        CandidateResponse.TraitInfo.builder().key("no_schema").label("无 schema 注入").build()
                ))
                .estimatedTokenPerCall(3000)
                .promptSizeBytes(2300)
                .schemaUsed(false)
                .fewshotExampleCount(0)
                .fewshotStrategy(null)
                .basePromptSource("default_adapted")
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"candidateId\":\"default\"");
        assertThat(json).contains("\"displayNameZh\":\"默认基线\"");
        assertThat(json).contains("\"description\":\"基线 · 课程域微调\"");
        assertThat(json).contains("\"key\":\"baseline\"");
        assertThat(json).contains("\"label\":\"课程基线\"");
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateResponseTest test 2>&1 | tail -10
```

Expected：编译失败 / 测试 FAIL，因为 `description` 字段和 `TraitInfo` 内类未定义。

- [ ] **Step 3：修改 CandidateResponse**

Replace `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java` 全文为：

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 03 步候选提示词响应。
 * <p>
 * 字段来源混合：算法产物字段从 {@code graphrag_pipeline/prompts/candidates/manifest.json} 透传，
 * 前端展示层字段（displayNameZh / description / isRecommended / traits / category）由
 * {@code CandidateMetadataLookup} 后端硬编码注入。
 * </p>
 */
@Getter
@Builder
public class CandidateResponse {

    /** 稳定标识符，如 schema_fewshot_distilled_v2_strict_tuple。 */
    private final String candidateId;

    /** 中文译名（后端硬编码 Map）。 */
    private final String displayNameZh;

    /** baseline / auto_tuned / schema_aware / schema_fewshot（后端硬编码 Map）。 */
    private final String category;

    /** 一句话描述（如 "基线 · 课程域微调"），后端硬编码 Map。 */
    private final String description;

    /** 是否为推荐候选（manifest.notes 标注或上一次评分历史决定，本期硬编码）。 */
    private final Boolean isRecommended;

    /** 特性标签数组，后端硬编码 Map<id, List<TraitInfo>>。 */
    private final List<TraitInfo> traits;

    private final Integer estimatedTokenPerCall;
    private final Integer promptSizeBytes;
    private final Boolean schemaUsed;
    private final Integer fewshotExampleCount;
    private final String fewshotStrategy;
    private final String basePromptSource;
    private final LocalDateTime generationTime;

    @Getter
    @Builder
    public static class TraitInfo {
        /** 稳定 key，用于前端渲染 chip 时取色等。 */
        private final String key;
        /** 中文 label，前端直接显示。 */
        private final String label;
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateResponseTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/dto/CandidateResponseTest.java
git commit -m "feat(prompt-builder): CandidateResponse 加 description 字段 + traits 改为 TraitInfo 对象 (Phase 4)"
```

---

## Task 2：新建 CandidateMetadataLookup（4 候选硬编码元数据）

后端展示层元数据的"硬编码 Map"集中放在这一个类，方便后续 Phase 7 schema 后端化时迁移。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateMetadataLookup.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateMetadataLookupTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateMetadataLookupTest.java`:

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMetadataLookupTest {

    private final CandidateMetadataLookup lookup = new CandidateMetadataLookup();

    @Test
    void recognizesAllFourKnownCandidates() {
        for (String id : List.of(
                "default",
                "auto_tuned",
                "schema_aware_directional_v2",
                "schema_fewshot_distilled_v2_strict_tuple"
        )) {
            assertThat(lookup.isKnown(id)).as("known: %s", id).isTrue();
            assertThat(lookup.displayNameZh(id)).as("displayNameZh: %s", id).isNotBlank();
            assertThat(lookup.category(id)).as("category: %s", id).isNotBlank();
            assertThat(lookup.description(id)).as("description: %s", id).isNotBlank();
            assertThat(lookup.traits(id)).as("traits: %s", id).isNotEmpty();
        }
    }

    @Test
    void onlyDistilledV2StrictTupleIsRecommended() {
        assertThat(lookup.isRecommended("schema_fewshot_distilled_v2_strict_tuple")).isTrue();
        assertThat(lookup.isRecommended("default")).isFalse();
        assertThat(lookup.isRecommended("auto_tuned")).isFalse();
        assertThat(lookup.isRecommended("schema_aware_directional_v2")).isFalse();
    }

    @Test
    void unknownCandidateReturnsNullsAndEmpty() {
        assertThat(lookup.isKnown("unknown_candidate")).isFalse();
        assertThat(lookup.displayNameZh("unknown_candidate")).isNull();
        assertThat(lookup.category("unknown_candidate")).isNull();
        assertThat(lookup.description("unknown_candidate")).isNull();
        assertThat(lookup.isRecommended("unknown_candidate")).isFalse();
        assertThat(lookup.traits("unknown_candidate")).isEmpty();
    }

    @Test
    void categoryMatchesSpecValues() {
        // spec § "候选译名映射"
        assertThat(lookup.category("default")).isEqualTo("baseline");
        assertThat(lookup.category("auto_tuned")).isEqualTo("auto_tuned");
        assertThat(lookup.category("schema_aware_directional_v2")).isEqualTo("schema_aware");
        assertThat(lookup.category("schema_fewshot_distilled_v2_strict_tuple")).isEqualTo("schema_fewshot");
    }

    @Test
    void traitsContainStableKeysAndChineseLabels() {
        List<CandidateResponse.TraitInfo> distilled = lookup.traits("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(distilled).extracting(CandidateResponse.TraitInfo::getKey)
                .contains("schema_injected", "few_shot_distilled", "strict_tuple");
        assertThat(distilled).allSatisfy(t -> {
            assertThat(t.getKey()).isNotBlank();
            assertThat(t.getLabel()).isNotBlank();
        });
    }

    @Test
    void knownCandidateIdsExposesAllFour() {
        assertThat(lookup.knownCandidateIds())
                .containsExactlyInAnyOrder(
                        "default",
                        "auto_tuned",
                        "schema_aware_directional_v2",
                        "schema_fewshot_distilled_v2_strict_tuple"
                );
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateMetadataLookupTest test 2>&1 | tail -10
```

Expected：编译失败，因为 `CandidateMetadataLookup` 类不存在。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateMetadataLookup.java`:

```java
package org.ysu.ckqaback.index;

import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.CandidateResponse.TraitInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 03 步候选提示词的"前端展示层元数据"硬编码查表。
 *
 * <p>本期硬编码 4 个候选；Phase 7+ 引入 {@code GET /relation-schemas} 时
 * 一并迁移到 manifest 配置或 schema 配置层。详见 spec § 风险 #3
 * "候选译名硬编码与候选数量增长"。</p>
 *
 * <p><strong>不</strong>包含算法产物字段（schemaUsed / fewshotExampleCount /
 * generationTime 等），那些从 manifest.json 直接透传，由 {@link CandidateManifestReader} 负责。</p>
 */
@Component
public class CandidateMetadataLookup {

    private static final Map<String, String> DISPLAY_NAME_ZH = Map.of(
            "default", "默认基线",
            "auto_tuned", "GraphRAG 自动调优",
            "schema_aware_directional_v2", "图谱感知",
            "schema_fewshot_distilled_v2_strict_tuple", "图谱感知 + 蒸馏样例"
    );

    private static final Map<String, String> CATEGORY = Map.of(
            "default", "baseline",
            "auto_tuned", "auto_tuned",
            "schema_aware_directional_v2", "schema_aware",
            "schema_fewshot_distilled_v2_strict_tuple", "schema_fewshot"
    );

    private static final Map<String, String> DESCRIPTION = Map.of(
            "default", "基线 · 课程域微调",
            "auto_tuned", "GraphRAG 官方 prompt-tune 自动产物",
            "schema_aware_directional_v2", "注入 schema + 方向卡 + 失败族守卫",
            "schema_fewshot_distilled_v2_strict_tuple", "注入 schema + few-shot 蒸馏 + 严格 tuple 约束"
    );

    private static final Set<String> RECOMMENDED_CANDIDATES = Set.of(
            "schema_fewshot_distilled_v2_strict_tuple"
    );

    private static final Map<String, List<TraitInfo>> TRAITS = Map.of(
            "default", List.of(
                    trait("baseline",   "课程基线"),
                    trait("no_schema",  "无 schema 注入"),
                    trait("no_fewshot", "无 few-shot")
            ),
            "auto_tuned", List.of(
                    trait("auto_tuned", "自动调优"),
                    trait("no_schema",  "无 schema 注入"),
                    trait("no_fewshot", "无 few-shot")
            ),
            "schema_aware_directional_v2", List.of(
                    trait("schema_injected",  "schema 注入"),
                    trait("directional_card", "方向卡"),
                    trait("failure_guard",    "失败族守卫")
            ),
            "schema_fewshot_distilled_v2_strict_tuple", List.of(
                    trait("schema_injected",    "schema 注入"),
                    trait("few_shot_distilled", "few-shot 蒸馏"),
                    trait("strict_tuple",       "严格 tuple")
            )
    );

    public boolean isKnown(String candidateId) {
        return DISPLAY_NAME_ZH.containsKey(candidateId);
    }

    public String displayNameZh(String candidateId) {
        return DISPLAY_NAME_ZH.get(candidateId);
    }

    public String category(String candidateId) {
        return CATEGORY.get(candidateId);
    }

    public String description(String candidateId) {
        return DESCRIPTION.get(candidateId);
    }

    public boolean isRecommended(String candidateId) {
        return RECOMMENDED_CANDIDATES.contains(candidateId);
    }

    public List<TraitInfo> traits(String candidateId) {
        return TRAITS.getOrDefault(candidateId, List.of());
    }

    public Set<String> knownCandidateIds() {
        return DISPLAY_NAME_ZH.keySet();
    }

    private static TraitInfo trait(String key, String label) {
        return TraitInfo.builder().key(key).label(label).build();
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateMetadataLookupTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 6, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateMetadataLookup.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateMetadataLookupTest.java
git commit -m "feat(prompt-builder): 新建 CandidateMetadataLookup 硬编码 4 候选展示层元数据 (Phase 4)"
```

---

## Task 3：新建 CandidateManifestReader

读 `<workspace>/prompt/candidates/manifest.json`，反序列化成 `CandidateResponse`。负责字段透传 + 容错 + token 估算 + base_prompt_source 简化。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderTest.java`:

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateManifestReaderTest {

    private final CandidateManifestReader reader =
            new CandidateManifestReader(new CandidateMetadataLookup(), new ObjectMapper());

    @Test
    void readsManifestWithFourCandidates(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "task": "candidate_prompt_generation",
                  "schema_version": "v1",
                  "candidates": [
                    {
                      "candidate_name": "default",
                      "source_type": "default_adapted",
                      "base_prompt_source": "prompts/extract_graph.txt",
                      "schema_used": false,
                      "fewshot_used": false,
                      "fewshot_example_count": 0,
                      "fewshot_strategy": null,
                      "generation_time": "2026-05-16T15:42:59+08:00"
                    },
                    {
                      "candidate_name": "schema_fewshot_distilled_v2_strict_tuple",
                      "source_type": "schema_fewshot_distilled_v2_strict_tuple",
                      "base_prompt_source": "prompts/candidates/auto_tuned/extract_graph.txt",
                      "schema_used": true,
                      "fewshot_used": false,
                      "fewshot_example_count": 0,
                      "fewshot_strategy": "distilled_negative_direction_rules_with_strict_tuple_guard",
                      "generation_time": "2026-05-16T15:42:59+08:00"
                    }
                  ]
                }
                """);
        // 写两份 prompt.txt 让 fallback promptSizeBytes 能读到
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x".repeat(2300));
        Files.createDirectories(candidatesDir.resolve("schema_fewshot_distilled_v2_strict_tuple"));
        Files.writeString(candidatesDir.resolve("schema_fewshot_distilled_v2_strict_tuple/prompt.txt"), "y".repeat(9200));

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        assertThat(candidates).hasSize(2);

        CandidateResponse first = candidates.get(0);
        assertThat(first.getCandidateId()).isEqualTo("default");
        assertThat(first.getDisplayNameZh()).isEqualTo("默认基线");
        assertThat(first.getCategory()).isEqualTo("baseline");
        assertThat(first.getDescription()).isEqualTo("基线 · 课程域微调");
        assertThat(first.getIsRecommended()).isFalse();
        assertThat(first.getSchemaUsed()).isFalse();
        assertThat(first.getFewshotExampleCount()).isEqualTo(0);
        // promptSizeBytes manifest 缺失 → 从 prompt.txt 文件大小 fallback
        assertThat(first.getPromptSizeBytes()).isEqualTo(2300);
        // estimatedTokenPerCall = 2300 / 4 + 200 = 775
        assertThat(first.getEstimatedTokenPerCall()).isEqualTo(775);

        CandidateResponse second = candidates.get(1);
        assertThat(second.getCandidateId()).isEqualTo("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(second.getIsRecommended()).isTrue();
        assertThat(second.getPromptSizeBytes()).isEqualTo(9200);
        // 9200 / 4 + 200 = 2500
        assertThat(second.getEstimatedTokenPerCall()).isEqualTo(2500);
        assertThat(second.getFewshotStrategy()).isEqualTo("distilled_negative_direction_rules_with_strict_tuple_guard");
    }

    @Test
    void simplifiesAbsoluteBasePromptSourcePath(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    {
                      "candidate_name": "auto_tuned",
                      "base_prompt_source": "/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/prompt-tune-cache/abc/run_5/auto_tuned/extract_graph.txt",
                      "schema_used": false
                    }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("auto_tuned"));
        Files.writeString(candidatesDir.resolve("auto_tuned/prompt.txt"), "z".repeat(3100));

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 绝对路径只显示文件名，避免暴露服务器路径（spec § 风险 5）
        assertThat(candidates.get(0).getBasePromptSource()).isEqualTo("extract_graph.txt");
    }

    @Test
    void preservesRelativeBasePromptSource(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    {
                      "candidate_name": "default",
                      "base_prompt_source": "prompts/extract_graph.txt",
                      "schema_used": false
                    }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 相对路径原样保留
        assertThat(candidates.get(0).getBasePromptSource()).isEqualTo("prompts/extract_graph.txt");
    }

    @Test
    void skipsUnknownCandidateIds(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    { "candidate_name": "default", "schema_used": false },
                    { "candidate_name": "unknown_extra", "schema_used": true }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 未在 CandidateMetadataLookup 中的候选直接跳过（避免渲染半成品 UI）
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getCandidateId()).isEqualTo("default");
    }

    @Test
    void returnsEmptyWhenManifestMissing(@TempDir Path tmp) throws Exception {
        // 候选目录不存在或 manifest.json 缺失 → 返回空列表，让上层判断"是否需要触发生成"
        assertThat(reader.read(tmp.resolve("nonexistent"))).isEmpty();

        Files.createDirectories(tmp.resolve("empty"));
        assertThat(reader.read(tmp.resolve("empty"))).isEmpty();
    }

    @Test
    void throwsOnMalformedManifest(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), "{ this is not json }");

        assertThatThrownBy(() -> reader.read(candidatesDir))
                .hasMessageContaining("manifest");
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateManifestReaderTest test 2>&1 | tail -10
```

Expected：编译失败，类不存在。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`:

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 读取 build run workspace 下的 {@code prompt/candidates/manifest.json}，
 * 反序列化为 {@link CandidateResponse} 列表。
 *
 * <p>负责：</p>
 * <ul>
 *   <li>透传 manifest 的算法产物字段（schema_used / fewshot_example_count 等）</li>
 *   <li>从 {@link CandidateMetadataLookup} 注入展示层字段（displayNameZh 等）</li>
 *   <li>计算 estimatedTokenPerCall（promptSizeBytes / 4 + 200）</li>
 *   <li>简化 base_prompt_source（绝对路径只取文件名，避免暴露服务器路径）</li>
 *   <li>candidate 不在白名单时跳过，不抛异常</li>
 *   <li>manifest 文件缺失时返回空列表（让上层判断是否需要触发生成）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CandidateManifestReader {

    private static final Logger log = LoggerFactory.getLogger(CandidateManifestReader.class);

    /** 单次抽取输入文本预估 token（中文按 1 token ≈ 1 字符），加在 prompt 长度上估总 token。 */
    private static final int INPUT_TOKEN_OVERHEAD = 200;

    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    /**
     * 读 candidatesDir 下的 manifest.json 转 CandidateResponse 列表。
     *
     * @param candidatesDir build_run workspace 下的 prompt/candidates 目录
     * @return 候选列表；目录或 manifest 不存在时返回空列表
     * @throws IOException manifest 文件存在但格式损坏
     */
    public List<CandidateResponse> read(Path candidatesDir) throws IOException {
        Path manifestFile = candidatesDir.resolve("manifest.json");
        if (!Files.exists(manifestFile)) {
            return List.of();
        }

        Map<String, Object> root;
        try {
            String json = Files.readString(manifestFile);
            root = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IOException("解析 candidate manifest 失败: " + manifestFile, e);
        }

        Object candidatesNode = root.get("candidates");
        if (!(candidatesNode instanceof List<?> rawList)) {
            return List.of();
        }

        List<CandidateResponse> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) rawMap;
            String candidateId = stringField(entry, "candidate_name");
            if (candidateId == null || !metadataLookup.isKnown(candidateId)) {
                if (candidateId != null) {
                    log.warn("Candidate {} 不在白名单，跳过", candidateId);
                }
                continue;
            }
            result.add(buildResponse(candidateId, entry, candidatesDir));
        }
        return result;
    }

    private CandidateResponse buildResponse(String candidateId, Map<String, Object> entry, Path candidatesDir) {
        int promptSizeBytes = resolvePromptSizeBytes(entry, candidateId, candidatesDir);
        int estimatedTokenPerCall = promptSizeBytes / 4 + INPUT_TOKEN_OVERHEAD;
        return CandidateResponse.builder()
                .candidateId(candidateId)
                .displayNameZh(metadataLookup.displayNameZh(candidateId))
                .category(metadataLookup.category(candidateId))
                .description(metadataLookup.description(candidateId))
                .isRecommended(metadataLookup.isRecommended(candidateId))
                .traits(metadataLookup.traits(candidateId))
                .estimatedTokenPerCall(estimatedTokenPerCall)
                .promptSizeBytes(promptSizeBytes)
                .schemaUsed(boolField(entry, "schema_used"))
                .fewshotExampleCount(intField(entry, "fewshot_example_count"))
                .fewshotStrategy(stringField(entry, "fewshot_strategy"))
                .basePromptSource(simplifyBasePromptSource(stringField(entry, "base_prompt_source")))
                .generationTime(parseTime(stringField(entry, "generation_time")))
                .build();
    }

    private int resolvePromptSizeBytes(Map<String, Object> entry, String candidateId, Path candidatesDir) {
        Integer fromManifest = intField(entry, "prompt_size_bytes");
        if (fromManifest != null && fromManifest > 0) return fromManifest;
        // Fallback：读 prompt.txt 文件大小
        Path promptFile = candidatesDir.resolve(candidateId).resolve("prompt.txt");
        if (Files.exists(promptFile)) {
            try {
                return Math.toIntExact(Files.size(promptFile));
            } catch (IOException e) {
                log.warn("读取 {} 文件大小失败：{}", promptFile, e.getMessage());
            }
        }
        return 0;
    }

    private static String simplifyBasePromptSource(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // 绝对路径（含/home/…）：只显示文件名，避免暴露服务器路径
        if (raw.startsWith("/")) {
            int slash = raw.lastIndexOf('/');
            return slash >= 0 && slash < raw.length() - 1 ? raw.substring(slash + 1) : raw;
        }
        return raw;
    }

    private static String stringField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof String s ? s : null;
    }

    private static Integer intField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value instanceof Number num) return num.intValue();
        return null;
    }

    private static Boolean boolField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof Boolean b ? b : null;
    }

    private static LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // manifest 用 ISO-8601 含时区（"+08:00"）
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception e) {
            // 兼容无时区格式
            try {
                return LocalDateTime.parse(raw);
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateManifestReaderTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 6, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderTest.java
git commit -m "feat(prompt-builder): 新建 CandidateManifestReader 读 manifest 并拼装展示层字段 (Phase 4)"
```

---

## Task 4：新建 AuditWithGoldExporter

把 build run 的 audit 样本 + DB 中的 gold 合并写出到 `<workspace>/prompt/candidates/audit_with_gold.json`，作为 `generate_candidate_prompts.py` 的 `--audit_file` 输入。这是 Phase 4 的关键 bug 修复——02 步导出的 audit JSON `gold_entities/gold_relations` 全空。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditWithGoldExporter.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditWithGoldExporterTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditWithGoldExporterTest.java`:

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditWithGoldExporterTest {

    private final AuditWithGoldExporter exporter = new AuditWithGoldExporter(new ObjectMapper());

    @Test
    void mergesGoldFromSamplesIntoAuditJson(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples a = newSample(1L, "sample-001",
                "进程是 CPU 调度的基本单位",
                "[{\"id\":\"e1\",\"name\":\"进程\",\"type\":\"Concept\"}]",
                "[]"
        );
        PromptTuneAuditSamples b = newSample(2L, "sample-002",
                "操作系统的核心子系统",
                "[]",
                "[]"
        );

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(a, b), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");

        assertThat(samples).hasSize(2);

        Map<String, Object> first = samples.get(0);
        assertThat(first.get("source_sample_id")).isEqualTo("sample-001");
        assertThat(first.get("text")).isEqualTo("进程是 CPU 调度的基本单位");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldEntities = (List<Map<String, Object>>) first.get("gold_entities");
        assertThat(goldEntities).hasSize(1);
        assertThat(goldEntities.get(0).get("name")).isEqualTo("进程");
        assertThat(goldEntities.get(0).get("type")).isEqualTo("Concept");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldRelations = (List<Map<String, Object>>) first.get("gold_relations");
        assertThat(goldRelations).isEmpty();

        // 第二条样本 gold 为空数组，仍要序列化为空 list
        Map<String, Object> second = samples.get(1);
        assertThat(second.get("gold_entities")).isEqualTo(List.of());
        assertThat(second.get("gold_relations")).isEqualTo(List.of());
    }

    @Test
    void preservesHeadingPathAsArray(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", "[]", "[]");
        sample.setHeadingPath("第二章 进程的描述与控制 > 2.1 进程的概念");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        // 脚本期望 heading_path 是 List<String>，DB 存的是 " > " 分隔字符串
        assertThat(samples.get(0).get("heading_path")).isEqualTo(
                List.of("第二章 进程的描述与控制", "2.1 进程的概念")
        );
    }

    @Test
    void writesEmptyAuditSamplesArrayWhenInputEmpty(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        assertThat(root.get("audit_samples")).isEqualTo(List.of());
    }

    @Test
    void preservesPageRangeAndPriority(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", "[]", "[]");
        sample.setPageStart(34);
        sample.setPageEnd(35);
        sample.setAuditPriority("high");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        assertThat(samples.get(0).get("page_start")).isEqualTo(34);
        assertThat(samples.get(0).get("page_end")).isEqualTo(35);
        assertThat(samples.get(0).get("audit_priority")).isEqualTo("high");
    }

    @Test
    void recoversFromMalformedGoldJsonAsEmpty(@TempDir Path tmp) throws Exception {
        // DB 数据损坏（gold_entities 不是合法 JSON）也不应崩溃，降级为空列表
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text",
                "{ malformed", "[]");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        assertThat(samples.get(0).get("gold_entities")).isEqualTo(List.of());
    }

    private static PromptTuneAuditSamples newSample(
            Long id, String sourceSampleId, String text, String goldEntitiesJson, String goldRelationsJson
    ) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setSourceSampleId(sourceSampleId);
        e.setText(text);
        e.setHeadingPath("第一章 引论");
        e.setPageStart(1);
        e.setPageEnd(1);
        e.setAuditPriority("medium");
        e.setAuditReason("test reason");
        e.setGoldEntities(goldEntitiesJson);
        e.setGoldRelations(goldRelationsJson);
        e.setReviewerDecision("completed");
        return e;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=AuditWithGoldExporterTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditWithGoldExporter.java`:

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 build run 的 audit 样本（含 DB 中的 gold_entities / gold_relations）
 * 序列化成 {@code generate_candidate_prompts.py} 期望的 JSON 形态。
 *
 * <p>这是 Phase 4 的关键修复：02 步落盘的 {@code audit_extraction_set.json} 中
 * gold 字段全是空数组（gold 数据存在 DB 而非 JSON），如果直接拿那份 JSON 喂给
 * 候选生成脚本，{@code schema_fewshot_distilled} 候选的 fewshot 命中失败，
 * 退化到 manual 静态示例。本类把 DB 中的真实 gold 合并回 JSON 再传给脚本。</p>
 */
@Component
@RequiredArgsConstructor
public class AuditWithGoldExporter {

    private static final Logger log = LoggerFactory.getLogger(AuditWithGoldExporter.class);

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 把样本列表写到目标文件。
     *
     * @param samples 当前 build run 的 audit 样本（来自 PromptTuneAuditSamplesService.listByBuildRunId）
     * @param outputFile 输出 JSON 文件路径，目录由调用方保证存在
     */
    public void export(List<PromptTuneAuditSamples> samples, Path outputFile) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> auditSamples = new ArrayList<>();
        for (PromptTuneAuditSamples s : samples) {
            auditSamples.add(toJsonForm(s));
        }
        root.put("audit_samples", auditSamples);

        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(
                outputFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8
        );
    }

    private Map<String, Object> toJsonForm(PromptTuneAuditSamples s) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("source_sample_id", s.getSourceSampleId());
        json.put("text", s.getText());
        json.put("heading_path", parseHeadingPath(s.getHeadingPath()));
        json.put("page_start", s.getPageStart() != null ? s.getPageStart() : 0);
        json.put("page_end", s.getPageEnd() != null ? s.getPageEnd() : 0);
        json.put("document_type", s.getDocumentType());
        json.put("audit_priority", s.getAuditPriority());
        json.put("audit_reason", s.getAuditReason());
        json.put("gold_entities", parseJsonArrayOrEmpty(s.getGoldEntities(), s.getId(), "gold_entities"));
        json.put("gold_relations", parseJsonArrayOrEmpty(s.getGoldRelations(), s.getId(), "gold_relations"));
        json.put("annotation_notes", s.getAnnotationNotes() != null ? s.getAnnotationNotes() : "");
        json.put("reviewer_decision", s.getReviewerDecision() != null ? s.getReviewerDecision() : "");
        return json;
    }

    private static List<String> parseHeadingPath(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(" > "));
    }

    private List<Map<String, Object>> parseJsonArrayOrEmpty(String raw, Long sampleId, String fieldName) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<Map<String, Object>> result = objectMapper.readValue(raw, LIST_MAP_TYPE);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("解析 sample {} {} JSON 失败，降级为空列表：{}", sampleId, fieldName, e.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=AuditWithGoldExporterTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 5, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AuditWithGoldExporter.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AuditWithGoldExporterTest.java
git commit -m "feat(prompt-builder): 新建 AuditWithGoldExporter 把 DB gold 合并到 audit JSON (Phase 4)"
```

---

## Task 5：扩展错误码

加 4104 / 4105 / 5007 三个业务码。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1：修改 ApiResultCode.java**

定位 `AI_SUGGESTION_FAILED(5006, ...)` 之后，在 `PIPELINE_NOT_IMPLEMENTED` 之前插入：

```java
    /**
     * AI 预填候选生成失败（单样本 GraphRAG 抽取超时或异常）。
     */
    AI_SUGGESTION_FAILED(5006, "AI 候选生成失败"),

    /**
     * 03 步候选 prompt 生成脚本执行失败（generate_candidate_prompts.py 退出非零或超时）。
     */
    CANDIDATE_GENERATION_FAILED(5007, "候选 Prompt 生成失败"),

    /**
     * 03 步进入门控失败：02 步未完成至少 1 条样本审阅。
     */
    CANDIDATE_REQUIRES_AUDIT_COMPLETED(4104, "请先完成 02 步至少 1 条样本审阅再进入 03 步"),

    /**
     * 03 步候选未生成：build run workspace 下 manifest.json 不存在。
     */
    CANDIDATES_NOT_GENERATED(4105, "本次构建尚未生成候选 Prompt，请先调用生成接口"),

    /**
     * 接口尚未实现（占位）。
     */
    PIPELINE_NOT_IMPLEMENTED(5099, "接口尚未实现");
```

- [ ] **Step 2：编译确认**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -5
```

Expected：编译通过。

- [ ] **Step 3：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(prompt-builder): 新增错误码 4104/4105/5007 (Phase 4)"
```

---

## Task 6：扩展 BuildRunWorkspaceService.createLayout 加 prompt/candidates

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/BuildRunWorkspaceServiceTest.java`

- [ ] **Step 1：写失败的测试（新增 test 文件或追加到现有 test）**

如果 `BuildRunWorkspaceServiceTest.java` 不存在，创建：

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BuildRunWorkspaceServiceTest {

    @Test
    void createLayoutIncludesPromptCandidatesDir(@TempDir Path tmp) throws Exception {
        BuildRunWorkspaceService service = new BuildRunWorkspaceService(tmp.toString());
        String workspaceUri = service.workspaceUri(0L, 5L, 18L);
        service.createLayout(workspaceUri);

        Path workspace = service.resolve(workspaceUri);
        assertThat(Files.isDirectory(workspace.resolve("prompt").resolve("candidates"))).isTrue();
        // 现有目录也保留
        assertThat(Files.isDirectory(workspace.resolve("prompt"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("index/output"))).isTrue();
    }
}
```

如果文件已存在，追加上面的 `@Test` 方法。

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=BuildRunWorkspaceServiceTest test 2>&1 | grep "Tests run"
```

Expected：`createLayoutIncludesPromptCandidatesDir` FAIL（候选目录不存在）。

- [ ] **Step 3：修改 BuildRunWorkspaceService**

定位 `createLayout` 方法的字符串数组，在 `"prompt"` 后插入 `"prompt/candidates"`：

```java
    public void createLayout(String workspaceUri) throws IOException {
        Path root = resolve(workspaceUri);
        for (String directory : new String[]{
                "selection",
                "parse",
                "graph-input",
                "prompt",
                "prompt/candidates",
                "index/input",
                "index/output",
                "index/cache",
                "index/reports",
                "index/logs",
                "qa-smoke"
        }) {
            Files.createDirectories(root.resolve(directory));
        }
    }
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=BuildRunWorkspaceServiceTest test 2>&1 | grep "Tests run"
```

Expected：所有测试 PASS。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/BuildRunWorkspaceServiceTest.java
git commit -m "feat(prompt-builder): workspace layout 加 prompt/candidates 子目录 (Phase 4)"
```

---

## Task 7：新建 CandidateGenerationOrchestrator

包装 `generate_candidate_prompts.py` 同步执行；输入是合并好 gold 的 audit JSON 文件；输出是 build run workspace 下的 candidates 目录。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorTest.java`:

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateGenerationOrchestratorTest {

    private CkqaIntegrationProperties properties;
    private ProcessRunner processRunner;
    private CandidateGenerationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = mock(CkqaIntegrationProperties.class);
        CkqaIntegrationProperties.GraphRagProperties graphrag = mock(CkqaIntegrationProperties.GraphRagProperties.class);
        when(properties.getGraphrag()).thenReturn(graphrag);
        when(graphrag.getRoot()).thenReturn("/tmp/graphrag-test");
        when(graphrag.getPython()).thenReturn(null);
        CkqaIntegrationProperties.GraphRagProperties.ManagedApi managedApi =
                mock(CkqaIntegrationProperties.GraphRagProperties.ManagedApi.class);
        when(graphrag.getManagedApi()).thenReturn(managedApi);
        when(managedApi.getCondaEnv()).thenReturn(null);

        processRunner = mock(ProcessRunner.class);
        orchestrator = new CandidateGenerationOrchestrator(properties, processRunner);
    }

    @Test
    void invokesPythonScriptWithExpectedArgs() throws Exception {
        Path auditFile = Path.of("/tmp/ws/prompt/candidates/audit_with_gold.json");
        Path outputDir = Path.of("/tmp/ws/prompt/candidates");

        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(0)
                        .stdout("")
                        .stderr("")
                        .elapsedSeconds(0L)
                        .timedOut(false)
                        .build()
        );

        orchestrator.run(auditFile, outputDir);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argvCaptor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(
                argvCaptor.capture(),
                eq(Path.of("/tmp/graphrag-test")),
                eq(Map.of()),
                any(Duration.class),
                any(ProcessContext.class)
        );

        List<String> argv = argvCaptor.getValue();
        // 命令含核心参数
        assertThat(argv).contains(
                "scripts/run_generate_candidate_prompts.py",  // 顶层包装入口（与 02.3 同模式）
                "--audit_file", auditFile.toAbsolutePath().toString(),
                "--output_dir", outputDir.toAbsolutePath().toString(),
                "--overwrite"
        );
    }

    @Test
    void throwsWhenScriptTimesOut() throws Exception {
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(-1)
                        .stdout("")
                        .stderr("")
                        .elapsedSeconds(60L)
                        .timedOut(true)
                        .build()
        );

        assertThatThrownBy(() -> orchestrator.run(
                Path.of("/tmp/audit.json"), Path.of("/tmp/out")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.GATEWAY_TIMEOUT)
                .hasMessageContaining("超时");
    }

    @Test
    void throwsWhenScriptFails() throws Exception {
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                ProcessExecutionResult.builder()
                        .command(List.of())
                        .exitCode(1)
                        .stdout("")
                        .stderr("Traceback (most recent call last):\n...\nRuntimeError: schema not found")
                        .elapsedSeconds(0L)
                        .timedOut(false)
                        .build()
        );

        assertThatThrownBy(() -> orchestrator.run(
                Path.of("/tmp/audit.json"), Path.of("/tmp/out")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATE_GENERATION_FAILED.getCode());
    }
}
```

**注意**：测试假设有一个顶层包装脚本 `scripts/run_generate_candidate_prompts.py`，类似 Phase 3 的 `scripts/run_native_extraction.py`。但 generate_candidate_prompts.py 不在 extraction_eval 子包，是直接放在 `scripts/prompt_tuning/` 包内并使用绝对 import，可以直接以 `python -m scripts.prompt_tuning.generate_candidate_prompts` 模块模式调用。**这个 Step 1 的测试需要确认调用方式**——见下面的 Step 1.5。

- [ ] **Step 1.5：确认 Python 脚本调用方式**

Run:
```bash
cd graphrag_pipeline && python -m scripts.prompt_tuning.generate_candidate_prompts --help 2>&1 | head -3
```

如果输出帮助文档，说明可以直接用 `-m` 模式（不需要顶层包装）。这种情况下，测试中的命令应该改成：
```java
assertThat(argv).contains(
        "-m", "scripts.prompt_tuning.generate_candidate_prompts",
        "--audit_file", ...
);
```

如果 `-m` 模式失败，回退到查找顶层包装脚本（形如 `scripts/run_generate_candidate_prompts.py`）。如果都没有，**新建顶层包装**（仿 `scripts/run_native_extraction.py`，4 行 `_compat_wrapper.export_module` 委托即可）。

修正测试和实现中的 argv 构造与确认结果一致。

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateGenerationOrchestratorTest test 2>&1 | tail -10
```

Expected：编译失败，类不存在。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`:

```java
package org.ysu.ckqaback.index;

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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步包装 {@code generate_candidate_prompts.py}，为某个 build run 生成 4 个候选 prompt
 * 到其 workspace 下的 {@code prompt/candidates/} 目录。
 *
 * <p>实测脚本耗时 ~66 ms，无 LLM 调用，纯字符串拼接 + schema 模板。
 * 用 {@link Duration} 60 秒兜底网络/文件 IO 抖动；正常路径几乎瞬完。</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CandidateGenerationOrchestrator.class);

    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(60);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    /**
     * 同步执行候选生成。
     *
     * @param auditWithGoldFile 含 DB gold 的 audit JSON 文件，作为 --audit_file 传入脚本
     * @param outputDir         build run workspace 下的 prompt/candidates 目录（manifest 等输出位置）
     */
    public void run(Path auditWithGoldFile, Path outputDir) throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());

        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        // Task 1.5 确认调用方式后调整这里：
        // 选项 A：python -m scripts.prompt_tuning.generate_candidate_prompts ...
        // 选项 B：python scripts/run_generate_candidate_prompts.py ...（新建顶层包装）
        argv.add("scripts/run_generate_candidate_prompts.py");
        argv.add("--audit_file");
        argv.add(auditWithGoldFile.toAbsolutePath().toString());
        argv.add("--output_dir");
        argv.add(outputDir.toAbsolutePath().toString());
        argv.add("--overwrite");

        Path logFile = outputDir.resolve("generate_candidate_prompts.log");
        ProcessContext context = ProcessContext.builder()
                .operation("candidate-generation")
                .logFile(logFile)
                .build();

        log.info("生成候选 Prompt: auditFile={}, outputDir={}", auditWithGoldFile, outputDir);

        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), GENERATE_TIMEOUT, context
        );

        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "候选 Prompt 生成超时（>" + GENERATE_TIMEOUT.toSeconds() + "s）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 Prompt 生成失败: " + firstSummary(result.getStderr(), "未知错误")
            );
        }
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
}
```

- [ ] **Step 3.5：如选了顶层包装入口，新建脚本**

如果 Step 1.5 选择"顶层包装"路径，新建 `graphrag_pipeline/scripts/run_generate_candidate_prompts.py`：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""兼容入口：委托到 scripts.prompt_tuning.generate_candidate_prompts。"""

from __future__ import annotations

from _compat_wrapper import export_module

main = export_module("scripts.prompt_tuning.generate_candidate_prompts", globals())


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateGenerationOrchestratorTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorTest.java
# 如果新建了顶层包装脚本：
git add graphrag_pipeline/scripts/run_generate_candidate_prompts.py
git commit -m "feat(prompt-builder): 新建 CandidateGenerationOrchestrator 同步包装候选生成脚本 (Phase 4)"
```

---

## Task 8：新建 CandidateService（业务编排）

提供 `generate(buildRunId)` / `list(buildRunId)` / `loadPromptText(buildRunId, candidateId)` 三个业务方法。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceTest.java`:

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneAuditSamplesService samplesStore;
    private BuildRunWorkspaceService workspaceService;
    private CandidateGenerationOrchestrator orchestrator;
    private AuditWithGoldExporter auditExporter;
    private CandidateManifestReader manifestReader;
    private CandidateMetadataLookup metadataLookup;
    private CandidateService service;

    @BeforeEach
    void setUp() {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(CandidateGenerationOrchestrator.class);
        auditExporter = mock(AuditWithGoldExporter.class);
        manifestReader = mock(CandidateManifestReader.class);
        metadataLookup = new CandidateMetadataLookup();

        service = new CandidateService(
                buildRunsStore,
                samplesStore,
                workspaceService,
                orchestrator,
                auditExporter,
                manifestReader,
                metadataLookup
        );
    }

    @Test
    void generateExportsAuditWithGoldThenCallsScript(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        KnowledgeBaseBuildRuns buildRun = newBuildRun(buildRunId);
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(tmp);

        PromptTuneAuditSamples sample = new PromptTuneAuditSamples();
        sample.setId(1L);
        sample.setSourceSampleId("sample-001");
        sample.setText("text");
        sample.setReviewerDecision("completed");
        when(samplesStore.listByBuildRunId(buildRunId)).thenReturn(List.of(sample));

        // 模拟 manifest 读出 1 个候选
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build()
        ));

        List<CandidateResponse> result = service.generate(buildRunId);

        // 调用顺序：先导出 audit_with_gold.json，再跑 orchestrator
        verify(auditExporter).export(
                any(),
                any(Path.class)
        );
        verify(orchestrator).run(any(Path.class), any(Path.class));
        verify(manifestReader).read(any(Path.class));
        assertThat(result).hasSize(1);
    }

    @Test
    void listReadsManifestWithoutTriggeringGenerate(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        KnowledgeBaseBuildRuns buildRun = newBuildRun(buildRunId);
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(tmp);

        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").build(),
                CandidateResponse.builder().candidateId("auto_tuned").build()
        ));

        List<CandidateResponse> result = service.list(buildRunId);

        // list 不应触发 export 或 orchestrator
        verify(auditExporter, org.mockito.Mockito.never()).export(any(), any());
        verify(orchestrator, org.mockito.Mockito.never()).run(any(), any());
        assertThat(result).hasSize(2);
    }

    @Test
    void listThrows4105WhenManifestEmpty(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);
        when(manifestReader.read(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.list(buildRunId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED.getCode());
    }

    @Test
    void loadPromptTextReturnsFileContent(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        // 准备 prompt.txt
        Path promptFile = tmp.resolve("prompt/candidates/default/prompt.txt");
        Files.createDirectories(promptFile.getParent());
        Files.writeString(promptFile, "-Goal-\nextract entities\n");

        String text = service.loadPromptText(buildRunId, "default");

        assertThat(text).contains("-Goal-").contains("extract entities");
    }

    @Test
    void loadPromptTextRejectsUnknownCandidateId() {
        when(buildRunsStore.getRequiredById(any())).thenReturn(newBuildRun(18L));

        // 未在 lookup 白名单的 candidateId 必须被拒绝（防路径穿越）
        assertThatThrownBy(() -> service.loadPromptText(18L, "../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.loadPromptText(18L, "unknown_candidate"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void loadPromptTextThrowsWhenFileMissing(@TempDir Path tmp) throws Exception {
        Long buildRunId = 18L;
        when(buildRunsStore.getRequiredById(buildRunId)).thenReturn(newBuildRun(buildRunId));
        when(workspaceService.resolve(any())).thenReturn(tmp);

        // 候选 id 合法但文件未生成
        assertThatThrownBy(() -> service.loadPromptText(buildRunId, "default"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED.getCode());
    }

    private KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns b = new KnowledgeBaseBuildRuns();
        b.setId(id);
        b.setKnowledgeBaseId(100L);
        b.setWorkspaceUri("user_0/kb_100/build_" + id);
        return b;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateServiceTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`:

```java
package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 03 步候选 prompt 业务编排。
 *
 * <p>三个核心方法：</p>
 * <ul>
 *   <li>{@link #generate(Long)}：导出 DB gold → 调脚本 → 读 manifest → 返回候选列表</li>
 *   <li>{@link #list(Long)}：纯只读，从 manifest 读取；不存在抛 4105</li>
 *   <li>{@link #loadPromptText(Long, String)}：抽屉懒加载 prompt 全文</li>
 * </ul>
 *
 * <p>不使用 {@code @Transactional}：本服务唯一的"写"操作是文件系统（audit_with_gold.json
 * 和候选目录），不修改业务表。</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final PromptTuneAuditSamplesService samplesStore;
    private final BuildRunWorkspaceService workspaceService;
    private final CandidateGenerationOrchestrator orchestrator;
    private final AuditWithGoldExporter auditExporter;
    private final CandidateManifestReader manifestReader;
    private final CandidateMetadataLookup metadataLookup;

    /**
     * 同步生成候选 prompt（覆盖式）：导出含 DB gold 的 audit JSON → 调 Python 脚本 → 读 manifest 返回。
     */
    public List<CandidateResponse> generate(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        Path candidatesDir = candidatesDirOf(buildRun);

        List<PromptTuneAuditSamples> samples = samplesStore.listByBuildRunId(buildRunId);

        Path auditWithGoldFile = candidatesDir.resolve("audit_with_gold.json");
        try {
            Files.createDirectories(candidatesDir);
            auditExporter.export(samples, auditWithGoldFile);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "导出 audit_with_gold.json 失败: " + e.getMessage()
            );
        }

        try {
            orchestrator.run(auditWithGoldFile, candidatesDir);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 Prompt 生成异常: " + e.getMessage()
            );
        }

        try {
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            log.info("候选生成完成 buildRunId={}, count={}", buildRunId, candidates.size());
            return candidates;
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
    }

    /**
     * 纯只读：从 build run workspace 下的 manifest.json 读出候选列表。
     * Manifest 不存在或为空时抛 4105，让前端引导用户调 generate。
     */
    public List<CandidateResponse> list(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        Path candidatesDir = candidatesDirOf(buildRun);
        try {
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            if (candidates.isEmpty()) {
                throw new BusinessException(
                        ApiResultCode.CANDIDATES_NOT_GENERATED,
                        HttpStatus.NOT_FOUND
                );
            }
            return candidates;
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
    }

    /**
     * 抽屉懒加载：读 prompt.txt 全文。candidateId 必须 ∈ 白名单（防路径穿越）。
     */
    public String loadPromptText(Long buildRunId, String candidateId) {
        if (!metadataLookup.isKnown(candidateId)) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "未知的候选标识：" + candidateId
            );
        }
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        Path promptFile = candidatesDirOf(buildRun)
                .resolve(candidateId)
                .resolve("prompt.txt");
        if (!Files.exists(promptFile)) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATES_NOT_GENERATED,
                    HttpStatus.NOT_FOUND,
                    "候选 prompt 文件不存在：" + candidateId
            );
        }
        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取 prompt 文件失败: " + e.getMessage()
            );
        }
    }

    private Path candidatesDirOf(KnowledgeBaseBuildRuns buildRun) {
        return workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt")
                .resolve("candidates");
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=CandidateServiceTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 6, Failures: 0, Errors: 0`。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceTest.java
git commit -m "feat(prompt-builder): 新建 CandidateService 业务编排（generate/list/loadPromptText）(Phase 4)"
```

---

## Task 9：Controller 替换 501 占位 + 加 prompt 端点

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1：替换 generateCandidates 占位**

定位：

```java
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<PipelineStepResponse> generateCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }
```

替换为：

```java
    /**
     * 03 步：触发候选 prompt 生成。
     *
     * <p>覆盖式：每次调用都会重新执行 {@code generate_candidate_prompts.py}（脚本实测 ~66 ms），
     * 把含 DB gold 的 audit JSON 喂给脚本，输出 4 个候选到 build run workspace 下的 prompt/candidates 目录。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> generateCandidates(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(candidateService.generate(buildRunId));
    }
```

注意返回类型从 `PipelineStepResponse` 改为 `List<CandidateResponse>`，与 listCandidates 一致。

- [ ] **Step 2：替换 listCandidates 占位**

定位：

```java
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }
```

替换为：

```java
    /**
     * 03 步：纯只读列出当前 build run 已生成的候选。
     * <p>未生成时抛 4105，前端据此显示空态 + "立即生成"按钮。</p>
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(candidateService.list(buildRunId));
    }
```

- [ ] **Step 3：新增 prompt 文本端点**

在 listCandidates 之后插入：

```java
    /**
     * 03 步：抽屉懒加载某个候选的 prompt 文件全文。
     * candidateId 必须 ∈ 4 个白名单值。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates/{candidateId}/prompt")
    public ApiResponse<String> getCandidatePromptText(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @PathVariable("candidateId") String candidateId
    ) {
        return ApiResponseUtils.success(candidateService.loadPromptText(buildRunId, candidateId));
    }
```

- [ ] **Step 4：在 controller 顶部注入 CandidateService**

定位 `@RequiredArgsConstructor` 处类字段定义区域，加：

```java
    private final CandidateService candidateService;
```

如果已有别的 service 注入示例（如 `private final AuditSampleService auditSampleService;`），仿其风格。

- [ ] **Step 5：编译和单跑现有测试**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -5
```

Expected：编译通过。

Run:
```bash
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run|FAIL" | tail -3
```

Expected：所有 Test PASS（包括之前 7 个 Task 的）。

- [ ] **Step 6：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(prompt-builder): 03 步候选 controller 替换 501 占位 + 加 prompt 文本端点 (Phase 4)"
```

---

## Task 10：Phase 3 fix — AiSuggestionService.resolvePromptFile 改读 build run workspace

修复 Phase 3 遗留的 prompt 路径硬编码：当 build run 已经跑过 03 步（`<workspace>/prompt/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt` 存在）时，AI 单样本抽取使用 build run 自己的版本；不存在时 fallback 到仓库根 frozen_v1（保持向后兼容）。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java`

- [ ] **Step 1：写失败的测试（追加到现有 AiSuggestionServiceTest）**

定位 `usesSchemaAwarePromptForAiSuggestion` 测试，紧接其后追加：

```java
    @Test
    void prefersBuildRunCandidateWhenAvailable(@TempDir Path tmp) throws Exception {
        // 当 build_run workspace 下有 schema_fewshot_distilled_v2_strict_tuple 候选时，
        // AI 抽取应该用本 build_run 的 prompt（含本次 audit gold 的 fewshot）
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        KnowledgeBaseBuildRuns buildRun = newBuildRun(10L);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(buildRun);
        when(workspaceService.resolve(any())).thenReturn(tmp);

        // 准备 build_run 自己的候选 prompt
        Path buildRunCandidate = tmp.resolve("prompt/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt");
        Files.createDirectories(buildRunCandidate.getParent());
        Files.writeString(buildRunCandidate, "build run specific prompt");

        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of()).relations(List.of()).build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        service.generate(10L, 99L);

        org.mockito.ArgumentCaptor<java.nio.file.Path> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(java.nio.file.Path.class);
        verify(orchestrator).runSingleExtract(any(), any(), promptCaptor.capture(), any(), any());

        // 用了 build_run 自己的 candidate 路径
        assertThat(promptCaptor.getValue()).isEqualTo(buildRunCandidate);
    }

    @Test
    void fallsBackToRepoRootCandidateWhenBuildRunMissing() throws Exception {
        // build_run 还没跑过 03 步，候选文件不存在 → fallback 到仓库根 frozen_v1
        PromptTuneAuditSamples sample = newSample(99L, 10L);
        when(samplesStore.getById(99L)).thenReturn(sample);
        when(buildRunsStore.getRequiredById(10L)).thenReturn(newBuildRun(10L));
        // workspace 指向不存在的目录
        when(workspaceService.resolve(any())).thenReturn(java.nio.file.Path.of("/nonexistent-workspace"));

        SingleSampleExtractionOrchestrator.ExtractionResult result =
                SingleSampleExtractionOrchestrator.ExtractionResult.builder()
                        .entities(List.of()).relations(List.of()).build();
        when(orchestrator.runSingleExtract(any(), any(), any(), any(), any())).thenReturn(result);

        service.generate(10L, 99L);

        org.mockito.ArgumentCaptor<java.nio.file.Path> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(java.nio.file.Path.class);
        verify(orchestrator).runSingleExtract(any(), any(), promptCaptor.capture(), any(), any());

        // setUp 里 graphrag.root = "/tmp/graphrag-test"
        assertThat(promptCaptor.getValue()).isEqualTo(java.nio.file.Path.of(
                "/tmp/graphrag-test",
                "prompts",
                "candidates",
                "schema_fewshot_distilled_v2_strict_tuple",
                "prompt.txt"
        ));
    }
```

测试需要 `import org.junit.jupiter.api.io.TempDir;`、`import java.nio.file.Files;`、`import java.nio.file.Path;` 已存在 → 确认。

- [ ] **Step 2：跑测试确认 prefersBuildRunCandidateWhenAvailable FAIL**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=AiSuggestionServiceTest test 2>&1 | grep -E "Tests run|FAIL" | head -5
```

Expected：原 9 个通过，新加的 `prefersBuildRunCandidateWhenAvailable` FAIL（因为现在 `resolvePromptFile` 永远返回仓库根路径，但测试期望返回 buildRunCandidate）；`fallsBackToRepoRootCandidateWhenBuildRunMissing` PASS（行为已正确）。

- [ ] **Step 3：修改 AiSuggestionService.resolvePromptFile**

定位现有方法：

```java
    private Path resolvePromptFile(KnowledgeBaseBuildRuns buildRun) {
        return Path.of(properties.getGraphrag().getRoot())
                .resolve("prompts")
                .resolve("candidates")
                .resolve("schema_fewshot_distilled_v2_strict_tuple")
                .resolve("prompt.txt");
    }
```

替换为：

```java
    /** 候选标识符常量，与 CandidateMetadataLookup / CandidateService 保持一致。 */
    private static final String DEFAULT_CANDIDATE_ID = "schema_fewshot_distilled_v2_strict_tuple";

    /**
     * 决定使用哪个 prompt 文件：
     * <ol>
     *   <li>build run workspace 下的 candidate（{@code <workspace>/prompt/candidates/{cid}/prompt.txt}）
     *       存在 → 用它（含本次 audit gold 的 fewshot 蒸馏，更准确）</li>
     *   <li>否则 fallback 到仓库根 frozen_v1（{@code <GRAPHRAG_ROOT>/prompts/candidates/{cid}/prompt.txt}）
     *       —— 用户还没跑 03 步时的兼容路径</li>
     * </ol>
     *
     * <p>Phase 6 后改为读 {@code prompts/final/active_prompt.json} 拿到当前激活的 candidate 路径。
     * 详见 spec § Phase 6。</p>
     */
    private Path resolvePromptFile(KnowledgeBaseBuildRuns buildRun) {
        Path buildRunCandidate = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("candidates")
                .resolve(DEFAULT_CANDIDATE_ID).resolve("prompt.txt");
        if (java.nio.file.Files.exists(buildRunCandidate)) {
            return buildRunCandidate;
        }
        return Path.of(properties.getGraphrag().getRoot())
                .resolve("prompts").resolve("candidates")
                .resolve(DEFAULT_CANDIDATE_ID).resolve("prompt.txt");
    }
```

注意不要新增 import（用 `java.nio.file.Files` fqn）；`workspaceService` 已是注入字段。

- [ ] **Step 4：跑测试确认全过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=AiSuggestionServiceTest test 2>&1 | grep "Tests run"
```

Expected：`Tests run: 11, Failures: 0, Errors: 0`（原 9 + 新 2）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/AiSuggestionServiceTest.java
git commit -m "fix(prompt-builder): AI 抽取优先用 build run 自己的候选，不存在则 fallback 仓库根 (Phase 4)"
```

---

## Task 11：前端 API 接口加 getCandidatePromptText

补一个新的 fn 让抽屉懒加载 prompt 文本。

**Files:**
- Modify: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`
- Test: `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-candidates.test.js`

- [ ] **Step 1：写失败的测试**

Create `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-candidates.test.js`:

```javascript
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  generateCandidates,
  listCandidates,
  getCandidatePromptText,
} from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    post(url, body, config) {
      this.requests.push({ method: 'POST', url, body, config })
      return Promise.resolve({ data: responses.shift() })
    },
    get(url, config) {
      this.requests.push({ method: 'GET', url, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('generateCandidates POST /candidates 并解包 ApiResponse', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: [{ candidateId: 'default' }, { candidateId: 'auto_tuned' }] },
  ])
  const result = await generateCandidates(18, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates')
  assert.deepEqual(result, [{ candidateId: 'default' }, { candidateId: 'auto_tuned' }])
})

test('listCandidates GET /candidates 并解包 ApiResponse', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: [{ candidateId: 'default' }] },
  ])
  const result = await listCandidates(18, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates')
  assert.deepEqual(result, [{ candidateId: 'default' }])
})

test('getCandidatePromptText GET /candidates/{candidateId}/prompt', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: '-Goal-\nextract entities' },
  ])
  const text = await getCandidatePromptText(18, 'default', client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates/default/prompt')
  assert.equal(text, '-Goal-\nextract entities')
})

test('getCandidatePromptText 对 candidateId 做 URL encoding', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: 'text' },
  ])
  await getCandidatePromptText(18, 'schema_fewshot_distilled_v2_strict_tuple', client)
  // candidateId 不含特殊字符，但 URL encoding 应当总是被调用
  assert.equal(
    client.requests[0].url,
    '/knowledge-base-build-runs/18/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt'
  )
})

test('listCandidates 业务码 4105 时抛出（CANDIDATES_NOT_GENERATED）', async () => {
  const client = makeMockClient([
    { code: 4105, message: '本次构建尚未生成候选 Prompt', data: null },
  ])
  await assert.rejects(
    () => listCandidates(18, client),
    (err) => err.code === 4105
  )
})
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | grep -E "fail|prompt-tune-pipeline-candidates" | head -5
```

Expected：`getCandidatePromptText` 测试 FAIL（函数未导出）。

- [ ] **Step 3：在 prompt-tune-pipeline.js 加 getCandidatePromptText**

定位 `listCandidates` 之后，新增：

```javascript
export async function getCandidatePromptText(buildRunId, candidateId, client = http) {
  // 抽屉懒加载某个候选的 prompt 全文（~30KB）。candidateId 服务端会校验白名单。
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates/${encodeURIComponent(candidateId)}/prompt`,
  ))
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | grep -E "tests|pass|fail" | tail -5
```

Expected：所有 5 个新测试 PASS，原 320 个测试不破坏。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-candidates.test.js
git commit -m "feat(prompt-builder): API 加 getCandidatePromptText 抽屉懒加载 prompt 文本 (Phase 4)"
```

---

## Task 12：PromptBuilderCandidatesStep 替换 mock + 三态 + 进入门控

把 mock 替换成真实 API；处理"loading / error / empty / ready"四态；加 02 步进入门控。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`
- Read for buildRunId source: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue` (作为 buildRunId 来源参考)

- [ ] **Step 1：完整替换 PromptBuilderCandidatesStep.vue**

**整体替换文件内容**为：

```vue
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CandidateCard from './CandidateCard.vue'
import PromptDisplay from './PromptDisplay.vue'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  formatTokens,
} from './candidates-selection-model.js'
import {
  generateCandidates,
  listCandidates,
  getCandidatePromptText,
  listAuditSamples,
} from '../../../api/prompt-tune-pipeline.js'

const props = defineProps({
  dirty: { type: Boolean, default: false },
})

const emit = defineEmits(['start-scoring', 'back'])

const route = useRoute()

const BUSINESS_CODE_CANDIDATES_NOT_GENERATED = 4105

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

// 四态：loading / error / blocked-by-gate / empty / ready
const phase = ref('loading')
const errorMessage = ref('')
const candidates = ref([])
const selectedIds = ref([])

// 抽屉懒加载状态
const drawerOpen = ref(false)
const drawerCandidate = ref(null)
const drawerPromptText = ref('')
const drawerLoading = ref(false)

const summary = computed(() => computeSummary(selectedIds.value, candidates.value))

onMounted(loadCandidates)

async function loadCandidates() {
  if (!buildRunId.value) {
    phase.value = 'error'
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }
  phase.value = 'loading'
  errorMessage.value = ''
  try {
    // 1. 进入门控：02 步至少 1 条 completed
    const samples = await listAuditSamples(buildRunId.value)
    const completedCount = (samples ?? []).filter(
      (s) => s.reviewerDecision === 'completed'
    ).length
    if (completedCount === 0) {
      phase.value = 'blocked-by-gate'
      return
    }

    // 2. 拉候选；4105 表示未生成 → 进入 empty 态
    try {
      const list = await listCandidates(buildRunId.value)
      candidates.value = list
      selectedIds.value = selectAll(list)
      phase.value = 'ready'
    } catch (err) {
      if (err?.code === BUSINESS_CODE_CANDIDATES_NOT_GENERATED) {
        phase.value = 'empty'
      } else {
        throw err
      }
    }
  } catch (err) {
    phase.value = 'error'
    errorMessage.value = err?.message ?? '加载候选失败'
  }
}

async function handleGenerate() {
  if (!buildRunId.value) return
  phase.value = 'loading'
  try {
    const list = await generateCandidates(buildRunId.value)
    candidates.value = list
    selectedIds.value = selectAll(list)
    phase.value = 'ready'
    ElMessage.success(`已生成 ${list.length} 个候选 Prompt`)
  } catch (err) {
    phase.value = 'error'
    errorMessage.value = err?.message ?? '候选生成失败'
  }
}

function handleToggle(id) {
  selectedIds.value = toggleCandidate(selectedIds.value, id)
}

function handleSelectAll()  { selectedIds.value = selectAll(candidates.value) }
function handleSelectNone() { selectedIds.value = selectNone() }
function handleSelectBaseline() {
  selectedIds.value = selectBaselineOnly(candidates.value)
  ElMessage.info(`已仅选基线（${selectedIds.value.length} 个）`)
}

async function handleViewPrompt(id) {
  drawerCandidate.value = candidates.value.find((c) => c.candidateId === id) ?? null
  drawerOpen.value = true
  drawerLoading.value = true
  drawerPromptText.value = ''
  try {
    drawerPromptText.value = await getCandidatePromptText(buildRunId.value, id)
  } catch (err) {
    ElMessage.error(err?.message ?? '读取 Prompt 文本失败')
  } finally {
    drawerLoading.value = false
  }
}

function handleStart() {
  if (summary.value.candidateCount === 0) {
    ElMessage.warning('请至少选择 1 个候选')
    return
  }
  emit('start-scoring', selectedIds.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-candidates">
    <header class="prompt-builder-step__header">
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>生成候选提示词</h3>
        <p>勾选要进入 04 步评分的候选 · 默认全选 · 长 prompt 候选 token 消耗显著高于基线</p>
      </div>
    </header>

    <!-- loading -->
    <div v-if="phase === 'loading'" class="candidate-state-card">
      <span>正在加载候选 Prompt...</span>
    </div>

    <!-- error -->
    <div v-else-if="phase === 'error'" class="candidate-state-card candidate-state-card--error">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="loadCandidates">重试</el-button>
    </div>

    <!-- blocked-by-gate（02 步 0 条 completed） -->
    <div v-else-if="phase === 'blocked-by-gate'" class="candidate-state-card candidate-state-card--blocked">
      <p>请先在 02 步完成至少 1 条样本的审阅，才能进入候选 Prompt 生成。</p>
      <el-button @click="$emit('back')">返回 02 步标注</el-button>
    </div>

    <!-- empty（02 步已完成但候选未生成） -->
    <div v-else-if="phase === 'empty'" class="candidate-state-card candidate-state-card--empty">
      <p>本次构建尚未生成候选 Prompt。点击下方按钮立即生成（约 1 秒）。</p>
      <el-button type="primary" @click="handleGenerate">立即生成候选</el-button>
    </div>

    <!-- ready -->
    <template v-else>
      <!-- 合并摘要 + 操作为一栏 -->
      <div class="candidate-action-bar">
        <div class="candidate-action-bar__left">
          <el-tag v-if="dirty" type="warning" size="small" effect="light">已修改未保存</el-tag>
          <el-tag v-else type="success" size="small" effect="light">已是最新</el-tag>
          <span class="candidate-action-bar__stats">
            已选 <strong>{{ summary.candidateCount }}</strong> / {{ candidates.length }} 个候选 ·
            <strong>{{ summary.totalCalls }}</strong> 次调用 ·
            <strong>{{ formatTokens(summary.estimatedTokens) }}</strong> tokens ·
            约 <strong>{{ summary.estimatedMinutes }}</strong> 分钟
          </span>
        </div>
        <div class="candidate-action-bar__right">
          <div class="candidate-quick-actions">
            <button @click="handleSelectAll">全选</button>
            <button @click="handleSelectNone">清空</button>
            <button @click="handleSelectBaseline">仅选基线</button>
          </div>
          <el-button type="primary" :disabled="summary.candidateCount === 0" @click="handleStart">
            开始抽取评分 →
          </el-button>
        </div>
      </div>

      <div class="candidate-grid">
        <CandidateCard
          v-for="candidate in candidates"
          :key="candidate.candidateId"
          :candidate="candidate"
          :selected="selectedIds.includes(candidate.candidateId)"
          @toggle="handleToggle"
          @view-prompt="handleViewPrompt"
        />
      </div>

      <el-drawer
        v-model="drawerOpen"
        :title="drawerCandidate ? `${drawerCandidate.displayNameZh}（${drawerCandidate.candidateId}）` : ''"
        direction="rtl"
        size="520px"
      >
        <div class="candidate-prompt-drawer">
          <div v-if="drawerLoading" class="ann-text-muted">加载 Prompt 文本中...</div>
          <PromptDisplay
            v-else-if="drawerPromptText"
            :text="drawerPromptText"
            default-mode="rich"
          />
          <div v-else class="ann-text-muted">未找到该候选的提示词文本</div>
        </div>
      </el-drawer>
    </template>
  </section>
</template>

<style scoped>
.candidate-state-card {
  padding: 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  color: var(--ckqa-text-muted, #78716c);
}
.candidate-state-card--error { color: var(--ckqa-danger, #dc2626); }
.candidate-state-card--blocked { color: var(--ckqa-warning, #d97706); }
.candidate-state-card--empty { color: var(--ckqa-text); }
</style>
```

- [ ] **Step 2：构建确认无语法错**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：构建成功。

- [ ] **Step 3：跑全部前端测试**

Run:
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```

Expected：所有测试 PASS（含 Task 11 新增的 5 个）。

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue
git commit -m "feat(prompt-builder): 03 步替换 mock 接入真实 API + 四态 UI + 02 步进入门控 (Phase 4)"
```

---

## Task 13：PromptBuilderPage 透传 buildRunId 进 03 步路由

确认从 02 步进入 03 步时 buildRunId 在 URL query 中持续可见。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1：检查现状**

Run:
```bash
grep -n "gotoStep" frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue | head -10
```

确认 `gotoStep('candidates')` 时是否会丢 query 参数。如果当前 router push 形如 `router.push({ name: 'PromptBuilder', query: { step: 'candidates' } })`，会丢 buildRunId。

- [ ] **Step 2：修复 query 透传**

如果发现 query 透传问题，在 `gotoStep` 函数中保留现有 query：

```javascript
function gotoStep(stepKey) {
  router.push({
    query: { ...route.query, step: stepKey },
  })
}
```

如果现有代码已经在透传 `...route.query`，跳过此 task。

- [ ] **Step 3：构建确认**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：构建成功。

- [ ] **Step 4：commit（如果有改动）**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "fix(prompt-builder): 03 步路由切换保留 buildRunId 等 query 参数 (Phase 4)"
```

如果 Step 1 检查发现没问题，跳过 commit。

---

## Task 14：端到端手工验证

启动本地环境，浏览器走完整 03 步流程。

**前置：** infra docker compose 已启动，后端在跑（已重启加载 Phase 4 改动），02 步至少有 1 个 build run 完成 ≥1 条 audit 样本审阅。

- [ ] **Step 1：启动前端 dev server**

由于 `pnpm dev` 是长驻进程，**不要用 bash 工具直接跑**。手动在新终端：

```bash
cd frontend/apps/admin-app
pnpm dev
```

- [ ] **Step 2：进入门控验证（blocked-by-gate）**

1. 在 admin-app 新建一个**空** build run（不进入 02 步标注），URL 含 `?buildRunId=<id>&step=candidates`。
2. 直接访问 03 步 URL。

预期：页面显示空态卡片"请先在 02 步完成至少 1 条样本的审阅"+ "返回 02 步标注"按钮。

- [ ] **Step 3：未生成态验证（empty）**

1. 在已有 build run 中 02 步完成 1 条样本审阅。
2. 进入 03 步。

预期：页面显示空态"本次构建尚未生成候选 Prompt" + "立即生成候选"按钮（紫色）。

- [ ] **Step 4：生成候选 + ready 态**

点击"立即生成候选"按钮。

预期：
- toast 一闪 success "已生成 4 个候选 Prompt"
- 页面切到 ready 态，显示 4 张候选卡片
- 每张卡片显示中文译名、ID 副标题、特性 chips、token 估算条（绿/黄/红）
- `schema_fewshot_distilled_v2_strict_tuple` 卡片左上角紫色 ✦ 推荐角标
- 顶部摘要条显示"已选 4 / 4 · 80 次调用 · ~Yk tokens · 约 Z 分钟"

- [ ] **Step 5：build run 隔离验证**

1. 在 build run A 完成 02 步 + 03 步候选生成。
2. 检查 `<workspace_A>/prompt/candidates/manifest.json` 存在。
3. 在 build run B 完成 02 步（标注内容**不同**）+ 03 步候选生成。
4. 检查 `<workspace_B>/prompt/candidates/manifest.json` 存在，且与 A 不同。

Run:
```bash
diff /home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/user_0/kb_<KB>/build_<A>/prompt/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt /home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/user_0/kb_<KB>/build_<B>/prompt/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt
```

Expected：diff 输出在 fewshot 微示例处不同（含各自 build run 的 audit gold）。

- [ ] **Step 6：抽屉预览**

点击候选卡片底部"查看完整提示词 →"按钮。

预期：
- 右侧抽屉滑出（520px 宽）
- 标题显示候选译名 + ID
- 抽屉内 PromptDisplay 渲染 prompt 文本（rich 模式默认）
- 关闭抽屉、再次打开不同候选 → 显示新候选的 prompt（懒加载）

- [ ] **Step 7：快捷动作验证**

依次点击：
- "全选" → 4 个全勾选
- "清空" → 0 个勾选，"开始抽取评分"按钮 disabled
- "仅选基线" → 只勾 default

Expected：摘要条数字实时更新。

- [ ] **Step 8：覆盖式生成验证**

1. 02 步**新增** 1 条 gold 标注（之前没标过的样本）。
2. 返回 03 步页面（不强制刷新，直接 router.push 切回 candidates）。
3. 当前 ready 态会显示**旧候选**——这是预期（list 是只读，不会自动重跑）。
4. 用户必须刷新页面 / 通过页面上某个"重新生成"按钮触发重跑。**本期没暴露 UI**，可以通过 DevTools 直接调 POST 测试：

```javascript
// 在浏览器 console
fetch('/api/v1/knowledge-base-build-runs/<ID>/candidates', { method: 'POST', headers: { Authorization: 'Bearer ' + token } })
  .then(r => r.json())
  .then(console.log)
```

预期：响应 200 + 4 个候选；磁盘上 `schema_fewshot_distilled_v2_strict_tuple/prompt.txt` 内容更新（包含新 gold 的 fewshot 微示例）。

- [ ] **Step 9：错误态验证**

模拟后端崩溃或网络断开，刷新 03 步页面。

预期：error 卡片显示中文错误 + "重试"按钮；点重试触发 loadCandidates。

- [ ] **Step 10：Phase 3 fallback 验证**

1. 在已经跑过 03 步的 build run 中，进入 02 步点"生成候选"（AI 单样本抽取）。
2. 后端日志应显示 `prompt=<workspace>/prompt/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt`，**不是** `prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt`（仓库根）。

Run:
```bash
grep "prompt=" /var/log/ckqa-backend.log | tail -3
```

或直接看 IDE console 日志。

预期：路径前缀含 `runtime/kb-build-runs/user_<uid>/kb_<kbid>/build_<rid>`。

3. 在还未跑 03 步的另一个 build run 中点"生成 AI 候选"。

预期：路径回退到仓库根 `<GRAPHRAG_ROOT>/prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt`。

- [ ] **Step 11：关闭 dev server**

按 Ctrl+C。

- [ ] **Step 12：不需要 commit**

Phase 4 完结。

---

## 自检清单

> 实施前主会话过一遍这个清单，对应不上的项停止执行重新调整计划。

### 一致性自检

- [ ] **A1**：所有 Task 中提到的 `candidateId` 4 个值（`default` / `auto_tuned` / `schema_aware_directional_v2` / `schema_fewshot_distilled_v2_strict_tuple`）拼写一致，无遗漏字符。
- [ ] **A2**：`CandidateMetadataLookup` 4 个 Map 的 keySet 完全相等。
- [ ] **A3**：`CandidateResponse.TraitInfo` 字段（key + label）与前端 `CandidateCard` 期望一致。
- [ ] **A4**：错误码 `CANDIDATE_GENERATION_FAILED(5007)` / `CANDIDATE_REQUIRES_AUDIT_COMPLETED(4104)` / `CANDIDATES_NOT_GENERATED(4105)` 在所有 Task 中数字一致，未与现有码冲突。
- [ ] **A5**：build run 候选目录路径在所有 Task 中一致：`<workspace>/prompt/candidates/<candidateId>/prompt.txt`。
- [ ] **A6**：DB gold 合并的 audit JSON 路径一致：`<workspace>/prompt/candidates/audit_with_gold.json`。
- [ ] **A7**：脚本调用方式（顶层包装 vs `-m` 模式）在 Task 7 与 Task 14 验证步骤一致。

### 上下文自检

- [ ] **B1**：`AuditPipelineOrchestrator` 已经存在并跑通，本计划的 `CandidateGenerationOrchestrator` 仿其架构（同步 + processRunner + ProcessContext + Duration timeout）。
- [ ] **B2**：`PromptTuneAuditSamplesService.listByBuildRunId(buildRunId)` 方法已存在，返回 `List<PromptTuneAuditSamples>`。
- [ ] **B3**：`KnowledgeBaseBuildRunsService.getRequiredById(buildRunId)` 已存在，返回 `KnowledgeBaseBuildRuns` 或抛 NotFound。
- [ ] **B4**：`BuildRunWorkspaceService.resolve(workspaceUri)` 已存在，返回绝对 Path。
- [ ] **B5**：前端 `listAuditSamples(buildRunId)` 已存在（Phase 2b 落地），返回的样本对象含 `reviewerDecision` 字段。

### Spec 覆盖自检

- [ ] **C1**：spec § 03 步 "顶部摘要条"覆盖：summary 显示 candidateCount / totalCalls / estimatedTokens / estimatedMinutes。
- [ ] **C2**：spec § 03 步 "候选网格"覆盖：CandidateCard 已存在 + 接受真实 API 数据。
- [ ] **C3**：spec § 03 步 "推荐徽章"覆盖：CandidateMetadataLookup 硬编码 `schema_fewshot_distilled_v2_strict_tuple`。
- [ ] **C4**：spec § 03 步 "快捷动作行"覆盖：全选 / 反选 / 仅选基线（前端 candidates-selection-model 已实现，Phase 4 不动）。
- [ ] **C5**：spec § 03 步 "查看完整 prompt 抽屉"覆盖：抽屉 + PromptDisplay + 懒加载。
- [ ] **C6**：spec § 错误处理 "03 候选生成失败 → 整页错误状态，附'重试 / 返回 02'"覆盖：phase=error 态 + 重试按钮。
- [ ] **C7**：spec § 已识别风险 #3 "候选译名硬编码"明确路径 B 落地（CandidateMetadataLookup）。
- [ ] **C8**：spec § 已识别风险 #5 "auto_tuned 绝对路径"覆盖：CandidateManifestReader.simplifyBasePromptSource 简化绝对路径。

### 依赖自检

- [ ] **D1**：Phase 3 已落地的 `AiSuggestionService.resolvePromptFile` 当前硬编码到仓库根，Task 10 fix 后改读 build run workspace。
- [ ] **D2**：Phase 3 落地的 `ai_suggested_entities` / `ai_suggested_relations` 持久化字段不被 Phase 4 影响（独立 column）。
- [ ] **D3**：Phase 4 不依赖 Phase 5 的任何工件（评分表、worker 等）。

### 测试覆盖自检

- [ ] **E1**：每个新增类（CandidateMetadataLookup / CandidateManifestReader / AuditWithGoldExporter / CandidateGenerationOrchestrator / CandidateService）至少 5 个单测。
- [ ] **E2**：异常路径测试：脚本超时、脚本失败、manifest 损坏、未知 candidateId、文件缺失。
- [ ] **E3**：Phase 3 fix 加 2 个单测：build run 候选优先 + fallback 仓库根。
- [ ] **E4**：前端 5 个 API 单测：generateCandidates / listCandidates / getCandidatePromptText / URL encoding / 4105 业务码抛错。

---

## Phase 4 完成判定

- 后端单测：原 326 → 326 + ~30（5 新类 × ~6 测试）= 约 356 PASS
- 前端单测：原 320 → 320 + 5 = 325 PASS
- 浏览器手工验证 14 步全过
- 一致性 / 上下文 / spec 覆盖 / 依赖 / 测试 5 类自检全过
- 所有 commit message 含 `(Phase 4)` 标记
- spec § Phase 4 段标记为 ✅ 已完成

