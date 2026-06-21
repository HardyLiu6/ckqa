# Prompt Builder Phase 4.5：01 步种子分流真正影响 03 候选生成 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 01 步种子选择从"纯前端展示元数据"升级为"真正影响 03 候选生成底板的开关"。**本期业务逻辑仅覆盖 01-03 步**；前端展示同步延伸到 05 步来源记录（仅展示，无业务推进）：
1. 后端新增 seed 可用性探测端点，01 步 `graphrag_tuned` 选项当且仅当当前 build run 已有自动调优产物时才可选；
2. `CandidateService.generate` 接受当前 build run 的 seed，传给脚本控制 `schema_aware_directional_v2` / `schema_fewshot_distilled_v2_strict_tuple` 这两族候选用哪份 prompt 做底板；
3. 03 候选响应（`CandidateResponse.seed`）携带 seed 标识，前端 03 候选卡片 + 05 来源记录直接消费；旁路文件 `seed-info.json` 仅作为审计落盘 + GET `/candidates` 时的 seed 来源（POST `/candidates` 路径不依赖该文件读写成功）。

**04 步评分对 seed 的感知（评分表 seed 列、报告透传）由 Phase 5 落地**，本期不实施。

**Architecture:**
- **复用 `PromptTuneRuns` 缓存表**：当前 `PromptTuneService.findReadyByCacheKey(cacheKey)` 已经能定位 build run 选材对应的自动调优产物目录（`prompt-tune-cache/<cacheKey>/run_<id>/extract_graph.txt`）。Phase 4.5 不新建表，只在 03 候选生成时复用此查询。
- **底板分流靠 `--auto_tuned_prompt_dir` 参数**：脚本第 1808-1809 行已有逻辑 `auto_tuned_prompt_source.path or default_prompt_source.path`。`seed=graphrag_tuned` 时把目录指向 prompt-tune cache 命中位置；`seed=system_default` 时指向不存在路径强制 fallback。`seed=null`（兼容 Phase 4 已有 build run）时**保持脚本默认行为，不传 `--auto_tuned_prompt_dir`**——这是与 Task 4 测试和实现完全一致的兼容路径，**不**等价于 `system_default`。
- **seed 来源仍是 `buildMetadata.customPromptDraft.seed`**：现有 `PromptBuilderSaveStep` 已经把 seed 写到这里，不引入新字段。POST `/candidates` 时后端读这个字段决定底板。
- **候选响应携带 seed 标识，审计落 seed-info.json**：脚本本身不感知 seed（只感知 base_prompt 路径），Phase 4.5 在 `CandidateService` 拼装响应时**直接把生成时使用的 seed 注入 `CandidateResponse.seed`**，不依赖 seed-info.json 文件读取成功；同时把 seed 元数据写到 `<workspace>/prompt/candidates/seed-info.json` 作为审计文件 + GET `/candidates` 路径的 seed 来源。POST 路径写文件失败时仅 warn 不阻塞响应。
- **门控（Phase 4 已有 + 新增）**：03 步进入门控仍是"02 ≥ 1 completed"；新增"01 步 seed 已选定且当前可用"作为前置（前端门控为主，后端兜底校验 seed=graphrag_tuned 但 prompt-tune cache 缺失时拒绝候选生成）。
- **cache 可用性判定单一口径**：seed-availability 端点（UI 引导）和候选生成（后端兜底）都通过 `PromptTuneService.probeBySelection(kbId, courseId, materialIds)` 统一判定 prompt-tune cache 是否 success。避免两套判定漂移。
- **下游影响：04 步评分透传 seed 已移交 Phase 5**：本期不修改 `prompt_tune_extraction_eval_runs` 表 / `ExtractionEvalService` / `ExtractionEvalReportResponse`。Phase 5 实施计划中已纳入对应任务，Phase 5 负责把 seed 字段直接合到表 schema、Entity、Service 与 Report，避免本期跨阶段改 Phase 5 工件。

**Tech Stack:** Spring Boot 4.0.5 + Java 21 + MyBatis-Plus（后端）；Vue 3 + Element Plus + Vite（前端）；Python `generate_candidate_prompts.py` 不修改，只通过 CLI 参数控制底板。

---

## 范围与非范围

### 包含

1. **后端 seed 可用性端点**：`GET /knowledge-base-build-runs/{id}/seed-availability`，返回每个 seed 当前是否可选 + 简要说明。
2. **CandidateService 接受 seed**：从 build run metadata 读 seed → 传给 orchestrator 控制底板路径。
3. **CandidateGenerationOrchestrator 接受 baseOverride**：可选参数；非空时按 seed 拼接 `--auto_tuned_prompt_dir`，空时不附加该参数。`--default_prompt_dir` 不动，沿用脚本默认。
4. **`prompt/candidates/seed-info.json` 落盘**：记录本次生成的 seed / 底板路径 / 生成时间，便于审计 + Phase 5 04 步透传时复用。
5. **CandidateResponse 加 `seed` 字段**：双通道注入——POST 生成路径由 `CandidateService` 直接注入（不依赖 seed-info.json 落盘）；GET 读取路径由 `CandidateManifestReader` 从 seed-info.json 回填（缺失时 seed=null）。
6. **前端 01 步**：mounted 时 GET seed-availability，graphrag_tuned 不可用时灰显 + tooltip 引导用户先去触发自动调优；切换 seed 时若已生成候选，提示用户"种子已切换，当前候选将失效，需要重新生成"（实际不清空数据，03 步会标记 stale 并提示用户点"重新生成候选"）。
7. **前端 03 步**：candidate 卡片角标显示 seed 标签（"基于 系统默认/自动调优"）。
8. **前端 05 步**：保存表单"来源记录"区追加 seed 行。

### 不包含

- **04 步评分对 seed 的感知**：`prompt_tune_extraction_eval_runs.seed` 列、`ExtractionEvalReportResponse.seed` 字段、启动评分时写入快照——这些工作由 Phase 5 落地。
- `seed=history_draft` 分支（Phase 6 落地，本期保持灰显）。
- 自动触发自动调优：用户 01 步选 graphrag_tuned 但 cache 缺失时，引导回知识库构建向导手动触发，不在本期增加自动触发链路。
- 修改 `generate_candidate_prompts.py`:脚本本身的逻辑足够，只通过 CLI 参数控制。
- 修改 `PromptTuneWorker` / `BuildRunPromptMaterializer`：自动调优产物的写盘路径不变。

---

## 关键架构决策

### 决策 1：底板分流靠脚本 CLI 参数，不改脚本

**理由：** `generate_candidate_prompts.py` 第 1808 行 `schema_aware_base_source = auto_tuned_prompt_source.path or default_prompt_source.path` 已经有底板分流逻辑。脚本入参 `--auto_tuned_prompt_dir` 已暴露。改脚本带来更高的回归风险（fewshot 蒸馏 / schema_aware_directional 等同样链路要重新验证），不如在 orchestrator 层控制路径。

**实现：**
- `seed=system_default` → `--auto_tuned_prompt_dir <一定不存在的路径>`，强制走 default 分支。具体用 `<workspace>/prompt/candidates/_disabled_auto_tuned`，目录从不创建。
- `seed=graphrag_tuned` → 先用 `probeBySelection` 校验 cache 状态为 `success`（与 SeedAvailabilityService 同口径），随后通过 `findReadyByCacheKey(cacheKey)` 取得 prompt-tune cache 目录（含 `extract_graph.txt`），把这个目录传给 `--auto_tuned_prompt_dir`。
- `seed=null` → 保持 Phase 4 兼容行为：`baseOverride=null`，orchestrator **不**附加 `--auto_tuned_prompt_dir` 参数；脚本走自己的默认 `prompts/candidates/auto_tuned` 探测逻辑（与现有 build run 行为完全一致）。

### 决策 2：seed 数据源仍是 `buildMetadata.customPromptDraft.seed`

**理由：** 现有 `PromptBuilderSaveStep` 已经把 seed 写到这里，`PromptBuilderPage.vue` 进入页面时也从这里读。不引入新字段、不动 schema，符合最小侵入原则。

**实现：** `CandidateService.generate(buildRunId)` 内部解析 `buildRun.buildMetadata` 取 seed；**缺失时按 `null` 处理（历史兼容模式）**：传 `baseOverride=null` 让 orchestrator 不附加 `--auto_tuned_prompt_dir` 参数，与 Phase 4 行为完全一致。

**注意 `null` ≠ `system_default`**：
- `null`（缺失）：保持脚本默认 `auto_tuned_prompt_dir = prompts/candidates/auto_tuned`，目录存在则用之、不存在则脚本自己 fallback 到 default。这是 Phase 4 老 build run 的兼容路径。
- `system_default`（显式）：把目录指向 `<workspace>/prompt/candidates/_disabled_auto_tuned`（始终不存在），强制走 default 分支。即使全局有 auto_tuned 产物也忽略，这是用户的"不要 auto_tuned"声明。

二者外在行为可能相同（脚本最终都走 default），但语义不同，对未来扩展更友好（例如 Phase 6 引入 `history_draft` 时也能继续用 null 表示"未指定"）。

### 决策 3：seed 元数据双通道——响应直接注入 + 旁路 seed-info.json

**理由：** 候选 manifest.json 由脚本写，结构稳定且被 `CandidateManifestReader` 严格读取。在 manifest 加 seed 字段需要改脚本 + 改 reader + 测多遍。但完全依赖旁路文件又有副作用：写盘失败时前端 candidate.seed 会变成 null，UI 展示和审计都丢失。

**实现：**

**POST `/candidates`（生成路径）**：
1. 用脚本生成候选；
2. `CandidateService` 拼装 `CandidateResponse` 时**直接把当时计算出的 seed 注入每个候选的 `seed` 字段**（不依赖 seed-info.json 落盘）；
3. 同时把 seed 元数据写到 `<workspace>/prompt/candidates/seed-info.json` 作为审计文件；
4. 写文件失败仅 warn 日志，**不影响 POST 响应**——前端仍能拿到带 seed 的候选列表。

**GET `/candidates`（读取路径，由后续重新进入 03 步触发）**：
1. `CandidateManifestReader` 读 manifest.json 拿候选基础字段；
2. 同时读 seed-info.json 拿 seed 注入；
3. seed-info.json 缺失时 `CandidateResponse.seed = null`，前端按"种子未知"渲染（兼容 Phase 4 老 build run 的候选目录，因为它们没写过 seed-info.json）。

**seed-info.json schema：**

```json
{
  "seed": "graphrag_tuned",
  "autoTunedPromptDir": "/.../prompt-tune-cache/abc123/run_12",
  "generatedAt": "2026-05-17T12:34:56+08:00",
  "buildRunId": 18
}
```

`generatedAt` 用 `OffsetDateTime`（含时区偏移），与文档示例的 `+08:00` 一致；序列化通过 Jackson `JavaTimeModule` 配置 ISO-8601 输出。

### 决策 4：seed 可用性探测复用 `PromptTuneService.probeBySelection`

**理由：** `PromptTuneService.probeBySelection(kbId, courseId, materialIds)` 已经存在，能根据选材计算 cacheKey 并返回最新成功的 prompt-tune 缓存状态。Phase 4.5 不需要新算法，只需要把这个能力包成 seed 维度。

**实现：** 新建 `SeedAvailabilityService.evaluate(buildRunId)`，返回 3 项：
- `system_default`：始终 `available=true`
- `graphrag_tuned`：调 probeBySelection；`status=success` 时 `available=true`，否则 `available=false` 附 `reason`（pending / running / failed / not_started）
- `history_draft`：本期始终 `available=false, reason="phase_6_not_implemented"`

### 决策 5：seed 切换不主动清理 03 产物

**理由：** 03 候选生成本身是覆盖式（POST /candidates 总是重跑），用户切 seed 后下一次进 03 自然会用新 seed 重生成。本期不涉及 04 步的 seed 一致性问题（Phase 5 落地）。

**实现：**
- 前端 01 步切 seed 时：把 dirty 标志置 true + toast"种子已切换，当前候选将失效，需要重新生成"，不清空数据。
- 03 步用户进入时：**比较候选响应中 `candidates[0].seed` 与当前 build run metadata 中的 seed 是否一致**（前端能拿到的两个数据源直接对比，不需要额外读 seed-info.json）。不一致时把候选列表标记为 stale，提示用户点"重新生成候选"覆盖更新。

### 决策 6：04 步 seed 透传由 Phase 5 落地

**理由：** `prompt_tune_extraction_eval_runs` 表是 Phase 5 新建的；本期 Phase 4.5 不修改 Phase 5 工件，避免跨阶段改动带来 merge 冲突。Phase 5 实施计划中已对应增加 seed 列、Entity 字段、Service 写入快照、Report 透传任务。

**实现：** 见 Phase 5 实施计划新增任务（不在 Phase 4.5 范围内）。

---

## 已识别风险

1. **Phase 4 已有 build run 的兼容性**：现有 build run 的 buildMetadata 可能没有 seed 字段（用户从 Phase 1 升上来）。处理：`null` 时按"历史兼容模式"对待——`baseOverride=null`，orchestrator 不附加 `--auto_tuned_prompt_dir` 参数，脚本走 Phase 4 原始默认行为。**注意 `null` ≠ `system_default`**：`system_default` 是用户显式声明，会强制把 auto_tuned 路径指向不存在目录；`null` 是兼容信号，不传额外参数让脚本自决。
2. **prompt-tune cache 失效**：用户在 01 步选 graphrag_tuned 时 cache 还在，但 03 步触发候选生成时 cache 已被清理（极少见）。处理：CandidateService 在拼路径前再调 `probeBySelection` 复查一次 cache 状态——**与 SeedAvailabilityService 用同一接口**保证 UI 引导和后端兜底口径一致；发现失效抛 4109 业务码"自动调优产物已失效，请重新选择种子"。
3. **schema_aware_base_label 字段值变化**：脚本里 base_label 取值会随 base 路径变（`官方 auto_tuned Prompt` / `默认 GraphRAG Prompt`），这个字符串会被嵌到生成的 prompt 文本里。改 seed 后 prompt 文本本身就会变，这是预期行为，但意味着 03 步抽屉里"查看完整 prompt"会看到不同内容，用户应该感知。
4. **04 步评分启动时 seed 与候选不一致**：本期 Phase 4.5 不修改 04 步链路，因此**这条风险也由 Phase 5 一并处理**——Phase 5 的 evalRun 写入当时 build run 的 seed 快照即可，与 candidate seed-info 可能不一致；前端展示二者，让用户判断；阻塞这种触发会让流程过于刚性。Phase 4.5 不阻塞。
5. **前端 01 切 seed 是否触发后端写入**：现状 `PromptBuilderSaveStep` 把 seed 在 05 步保存时一并写入 `customPromptDraft`。Phase 4.5 需要让 seed 在 01 步即时生效，否则 03 候选生成读不到。处理：01 步选 seed 后立即 PUT `/custom-prompt-draft`（仅写 seed 子字段，不动 prompts），由现有 `KnowledgeBaseBuildRunService.saveCustomPromptDraft` 接受。
6. **seed-info.json 写盘失败**：磁盘满 / 权限错误等极端场景。处理：**写文件失败仅 warn 日志，不影响 POST 响应**——`CandidateService` 已在响应中直接注入 seed，不依赖 seed-info.json 落盘。但若用户后续重新进入 03 步走 GET 路径读 manifest 时，seed-info.json 缺失会导致候选 seed 字段为 null，前端按"种子未知"渲染。这是本期接受的边界（与 Phase 4 老 build run 的候选目录展示行为一致）。
7. **history_draft 灰显的可访问性**：spec 已说本期不开放，但前端不应该完全隐藏（要让用户感知到将来会有），保持灰显 + tooltip"本期未开放"。

---

## 文件结构（新增 / 修改）

### 后端（Java）

- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/SeedAvailabilityResponse.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedInfoStore.java`（写读 `seed-info.json`）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`（接受并应用 seed）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`（接受 baseOverride 参数）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`(注入 seed 字段)
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java`（加 seed 字段）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`（加 4109）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`（新增 seed-availability 端点）
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedInfoStoreTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorSeedTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceSeedTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderSeedTest.java`

> **不在本期范围**：`PromptTuneExtractionEvalRuns.java` / `ExtractionEvalService.java` /
> `ExtractionEvalReportAssembler.java` / `ExtractionEvalReportResponse.java` 的 seed 改造
> 已移交 Phase 5 实施计划。

### 前端

- 新增 `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-seed.test.js`
- 修改 `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`（加 `getSeedAvailability`）
- 修改 `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（透传 seed 到 01 步 + 切换时调 PUT custom-prompt-draft）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`（消费 availability 数据 + tooltip）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`（候选卡片角标 + stale 提示）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`（来源记录加 seed 行）
- E2E 修改 `frontend/apps/admin-app/e2e/prompt-builder-candidates.spec.js`（增 seed 角标断言）
- E2E 新增 `frontend/apps/admin-app/e2e/prompt-builder-seed.spec.js`

---

## Task 0：契约自检

> 写代码前确认 Phase 4 / Phase 5 已落地代码 / 脚本契约稳定。

**Files:**
- Read: `graphrag_pipeline/scripts/prompt_tuning/generate_candidate_prompts.py`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneService.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunPromptMaterializer.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/AiSuggestionService.java`
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`

- [ ] **Step 1：确认 generate_candidate_prompts.py 的 seed 分流锚点**

Run:
```bash
grep -nE "schema_aware_base_source|auto_tuned_prompt_source|--auto_tuned_prompt_dir|--default_prompt_dir" graphrag_pipeline/scripts/prompt_tuning/generate_candidate_prompts.py | head -20
```

Expected：含
- 第 ~1808 行 `schema_aware_base_source = auto_tuned_prompt_source.path or default_prompt_source.path`
- CLI 参数 `--auto_tuned_prompt_dir` / `--default_prompt_dir`

如果 `or` 表达式被改成别的逻辑（例如 if/else），本计划假设失效，停止执行重新核实。

- [ ] **Step 2：确认 PromptTuneService.findReadyByCacheKey / probeBySelection 可用**

Run:
```bash
grep -nE "findReadyByCacheKey|probeBySelection" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneService.java
```

Expected：两个方法都存在；`probeBySelection(knowledgeBaseId, courseId, materialIds)` 返回 `PromptTuneRunResponse`，含 `status`（`success` / `pending` / `running` / `failed` / `not_started`）。

- [ ] **Step 3：确认 BuildRun customPromptDraft.seed 字段**

Run:
```bash
grep -nE "customPromptDraft|seed" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java | head -10
```

Expected：含 `customPromptDraft` 写读路径，且支持嵌套 seed 字段（不强制要求字段存在，缺失时 null）。

- [ ] **Step 4：确认 CandidateResponse 当前字段**

Run:
```bash
grep -nE "private final" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java
```

Expected：含 Phase 4 已加的 displayNameZh / category / description / isRecommended / traits / promptSizeBytes 等字段。Phase 4.5 要追加 `seed` 字段。

- [ ] **Step 5：确认错误码命名空间空闲**

Run:
```bash
grep -E "4109|4110" backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
```

Expected：无（4109 / 4110 未占用）。

- [ ] **Step 6：确认 prompt-tune cache 路径**

```bash
ls graphrag_pipeline/runtime/kb-build-runs/prompt-tune-cache 2>/dev/null | head -5
```

如果存在样本：
```bash
ls graphrag_pipeline/runtime/kb-build-runs/prompt-tune-cache/<某 cacheKey>/run_*/extract_graph.txt 2>/dev/null | head
```

Expected：能找到至少一份 success 缓存的 extract_graph.txt 文件，作为 graphrag_tuned 候选生成时 `--auto_tuned_prompt_dir` 指向的目录的同级文件。

- [ ] **Step 7：确认 PromptTuneRuns.candidateDir 与 workspaceService.resolve 的契约**

```bash
grep -nE "candidateDir|setCandidateDir|workspaceService.resolve" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneService.java | head -10
```

确认两件事：
1. `PromptTuneRuns.candidateDir` 写入时形如 `prompt-tune-cache/<cacheKey>/run_<id>`（相对路径，不带 `extract_graph.txt`），与 `PromptTuneService.createPendingRun` 中的 `candidateDirUri` 一致。
2. `workspaceService.resolve(candidateDir)` 把这个相对路径解析到 GraphRAG build runs root 下的绝对路径，目录下含 `extract_graph.txt`。

如果以上两点成立，Task 4 中通过 `workspaceService.resolve(run.getCandidateDir())` 拿到的就是脚本期望的 `--auto_tuned_prompt_dir` 目录。

如果发现 `candidateDir` 是文件路径或绝对路径（与上文假设不符），Task 4 需要相应调整路径拼接逻辑：把"目录拼接 / 父目录提取"改为符合实际 candidateDir 形态的计算方式。

- [ ] **Step 8：自检承诺**

如果 Steps 1-7 全部 PASS，进入 Task 1。
任一项与计划假设不符（特别是 Step 1 / Step 2 / Step 7） → 停止执行重新调整计划。

> Phase 4.5 本期不修改 Phase 5 的评分表 / Service / DTO，因此不需要校验 `prompt_tune_extraction_eval_runs` 是否存在。

Task 0 不产生代码改动。

---

## Task 1：CandidateResponse + 错误码扩展

加 seed 字段，加 4109 错误码。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1：CandidateResponse 加 seed 字段**

定位 CandidateResponse 现有字段段（Phase 4 已落地的 generationTime 行附近）：

**oldStr**：
```java
    private final String basePromptSource;
    private final LocalDateTime generationTime;
```

**newStr**：
```java
    private final String basePromptSource;
    private final LocalDateTime generationTime;

    /**
     * 本组候选生成时使用的种子（system_default / graphrag_tuned / null）。
     * <p>仅 schema_aware_directional_v2 / schema_fewshot_distilled_v2_strict_tuple 实际受 seed 影响；
     * default 与 auto_tuned 候选不依赖 seed，本字段在所有候选上取相同值，
     * 表示"本次生成时整组候选所基于的种子"。</p>
     */
    private final String seed;
```

- [ ] **Step 2：加 4109 错误码**

定位 4108 与 5007 之间，在 4108 后插入：

**oldStr**：
```java
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),
```

**newStr**：
```java
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),

    /**
     * 用户选择 graphrag_tuned 但当前 build run 选材对应的自动调优产物不存在或失效。
     */
    SEED_AUTO_TUNED_UNAVAILABLE(4109, "当前选材的自动调优产物不可用，请重新选择种子或先触发自动调优"),
```

- [ ] **Step 3：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 4：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(prompt-builder): CandidateResponse 加 seed 字段 + 4109 错误码 (Phase 4.5)"
```

---

## Task 2：SeedInfoStore（写读 seed-info.json）

负责把 seed 元数据落到 build run workspace `prompt/candidates/seed-info.json`，让 reader 注入 seed、04 步透传 seed 时也能读。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedInfoStore.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedInfoStoreTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedInfoStoreTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeedInfoStoreTest {

    private final SeedInfoStore store = new SeedInfoStore(new ObjectMapper());

    @Test
    void writeAndReadRoundTrip(@TempDir Path candidatesDir) throws Exception {
        SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                .seed("graphrag_tuned")
                .autoTunedPromptDir("/tmp/cache/run_3")
                .generatedAt(java.time.OffsetDateTime.parse("2026-05-17T12:00:00+08:00"))
                .buildRunId(18L)
                .build();

        store.write(candidatesDir, info);

        Optional<SeedInfoStore.SeedInfo> read = store.read(candidatesDir);
        assertThat(read).isPresent();
        assertThat(read.get().getSeed()).isEqualTo("graphrag_tuned");
        assertThat(read.get().getBuildRunId()).isEqualTo(18L);
        // 时区偏移必须保留，否则审计文件失真
        assertThat(read.get().getGeneratedAt().getOffset())
                .isEqualTo(java.time.ZoneOffset.ofHours(8));
    }

    @Test
    void readReturnsEmptyWhenFileMissing(@TempDir Path candidatesDir) {
        assertThat(store.read(candidatesDir)).isEmpty();
    }

    @Test
    void readReturnsEmptyOnMalformedFile(@TempDir Path candidatesDir) throws Exception {
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("seed-info.json"), "not json");
        assertThat(store.read(candidatesDir)).isEmpty();
    }

    @Test
    void writeProducesIso8601StringForGeneratedAt(@TempDir Path candidatesDir) throws Exception {
        // 锁定 generatedAt 序列化契约：必须是 ISO-8601 字符串，不能退化为 timestamp 数组
        SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                .seed("system_default")
                .autoTunedPromptDir(null)
                .generatedAt(java.time.OffsetDateTime.parse("2026-05-17T12:00:00+08:00"))
                .buildRunId(18L)
                .build();

        store.write(candidatesDir, info);

        String json = Files.readString(candidatesDir.resolve("seed-info.json"));
        // 必须是带 ISO 时间偏移的字符串，不是数字数组也不是缺偏移的本地时间
        assertThat(json).contains("\"generatedAt\" : \"2026-05-17T12:00:00+08:00\"");
        assertThat(json).doesNotContain("\"generatedAt\" : [");  // 否定 timestamp 数组形态
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=SeedInfoStoreTest test 2>&1 | tail -10
```

Expected：编译失败，类不存在。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedInfoStore.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 旁路存储：把候选生成时使用的 seed / 底板路径 / 时间写到
 * {@code <workspace>/prompt/candidates/seed-info.json}。
 *
 * <p>不修改脚本输出（manifest.json 由 Python 写），只在 Java 侧补充审计元数据。
 * Reader 侧把这份信息注入 CandidateResponse.seed。</p>
 */
@Component
@RequiredArgsConstructor
public class SeedInfoStore {

    private static final Logger log = LoggerFactory.getLogger(SeedInfoStore.class);

    private static final String FILE_NAME = "seed-info.json";

    private final ObjectMapper objectMapper;

    public void write(Path candidatesDir, SeedInfo info) throws IOException {
        Files.createDirectories(candidatesDir);
        Path file = candidatesDir.resolve(FILE_NAME);
        // 显式锁定时间序列化契约：JavaTimeModule 启用 + 关闭 timestamp 模式，
        // 确保 OffsetDateTime 输出为 ISO-8601 字符串（含时区偏移），与文档示例和 A8 自检一致。
        ObjectMapper writer = objectMapper.copy()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.writeString(file, writer.writeValueAsString(info), StandardCharsets.UTF_8);
    }

    public Optional<SeedInfo> read(Path candidatesDir) {
        Path file = candidatesDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return Optional.empty();
        try {
            ObjectMapper reader = objectMapper.copy()
                    .findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.ofNullable(reader.readValue(json, SeedInfo.class));
        } catch (Exception e) {
            log.warn("解析 seed-info.json 失败 path={}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeedInfo {
        private String seed;
        private String autoTunedPromptDir;
        /**
         * 候选生成时间，含时区偏移以便审计文件跨服务器仍能正确呈现。
         * Jackson 通过 JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false 输出 ISO-8601 字符串。
         */
        private OffsetDateTime generatedAt;
        private Long buildRunId;
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=SeedInfoStoreTest test 2>&1 | tail -10
```

Expected：4 个测试 PASS（含 ISO-8601 序列化锁定测试）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedInfoStore.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedInfoStoreTest.java
git commit -m "feat(prompt-builder): 新建 SeedInfoStore 旁路存储 seed 元数据 (Phase 4.5)"
```

---

## Task 3：CandidateGenerationOrchestrator 接受 baseOverride

把 orchestrator 的 `run` 方法签名扩展，接受可选的 baseOverride 参数控制是否附加 `--auto_tuned_prompt_dir`。本期不动 `--default_prompt_dir`（沿用脚本默认）。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorSeedTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorSeedTest.java`：

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateGenerationOrchestratorSeedTest {

    private CandidateGenerationOrchestrator orchestrator;
    private ProcessRunner processRunner;
    private CkqaIntegrationProperties properties;

    private void initWithRoot(Path graphragRoot) {
        processRunner = mock(ProcessRunner.class);
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphragRoot.toString());
        properties.getGraphrag().setPython("/usr/bin/python");
        orchestrator = new CandidateGenerationOrchestrator(properties, processRunner);
    }

    @Test
    void seedSystemDefaultPassesNonExistentAutoTunedDir(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path audit = graphragRoot.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = graphragRoot.resolve("ws/prompt/candidates");
        Files.createDirectories(output);
        when(processRunner.run(any(), any(), any(), any(), any()))
                .thenReturn(new ProcessExecutionResult(0, "", "", false));

        orchestrator.run(audit, output, CandidateGenerationOrchestrator.BaseOverride.systemDefault(output));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();

        // --auto_tuned_prompt_dir 指向不存在路径，让脚本走 default 分支
        int idx = args.indexOf("--auto_tuned_prompt_dir");
        assertThat(idx).isGreaterThan(-1);
        Path passed = Path.of(args.get(idx + 1));
        assertThat(Files.exists(passed)).isFalse();
        assertThat(passed.toString()).contains("_disabled_auto_tuned");
    }

    @Test
    void seedGraphragTunedPassesProvidedDir(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path audit = graphragRoot.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = graphragRoot.resolve("ws/prompt/candidates");
        Files.createDirectories(output);
        Path autoTunedDir = graphragRoot.resolve("cache/run_3");
        Files.createDirectories(autoTunedDir);
        Files.writeString(autoTunedDir.resolve("extract_graph.txt"), "auto-tuned content");
        when(processRunner.run(any(), any(), any(), any(), any()))
                .thenReturn(new ProcessExecutionResult(0, "", "", false));

        orchestrator.run(audit, output, CandidateGenerationOrchestrator.BaseOverride.graphragTuned(autoTunedDir));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();
        int idx = args.indexOf("--auto_tuned_prompt_dir");
        assertThat(args.get(idx + 1)).isEqualTo(autoTunedDir.toAbsolutePath().toString());
    }

    @Test
    void noOverrideSkipsAutoTunedDirArg(@TempDir Path graphragRoot) throws Exception {
        // 无 baseOverride（兼容路径）：不传 --auto_tuned_prompt_dir，让脚本用默认值
        initWithRoot(graphragRoot);
        Path audit = graphragRoot.resolve("audit.json");
        Files.writeString(audit, "{}");
        Path output = graphragRoot.resolve("ws/prompt/candidates");
        Files.createDirectories(output);
        when(processRunner.run(any(), any(), any(), any(), any()))
                .thenReturn(new ProcessExecutionResult(0, "", "", false));

        orchestrator.run(audit, output, null);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(captor.capture(), any(), any(), any(), any());
        List<String> args = captor.getValue();
        assertThat(args).doesNotContain("--auto_tuned_prompt_dir");
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=CandidateGenerationOrchestratorSeedTest test 2>&1 | tail -10
```

Expected：编译失败，BaseOverride 类不存在。

- [ ] **Step 3：扩展 orchestrator**

修改 `CandidateGenerationOrchestrator.java`：

**oldStr**（构造函数下方第一段，把整个 run 方法替换）：
```java
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
        // Task 0 已定版调用方式：python -m scripts.prompt_tuning.generate_candidate_prompts
        argv.add("-m");
        argv.add("scripts.prompt_tuning.generate_candidate_prompts");
        argv.add("--audit_file");
        argv.add(auditWithGoldFile.toAbsolutePath().toString());
        argv.add("--output_dir");
        argv.add(outputDir.toAbsolutePath().toString());
        argv.add("--overwrite");
```

**newStr**：
```java
    /**
     * 同步执行候选生成。
     *
     * @param auditWithGoldFile 含 DB gold 的 audit JSON 文件
     * @param outputDir         build run workspace 下的 prompt/candidates 目录
     * @param baseOverride      可选的底板路径覆盖；null 时走 Phase 4 默认行为（兼容）
     */
    public void run(Path auditWithGoldFile, Path outputDir, BaseOverride baseOverride)
            throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());

        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.prompt_tuning.generate_candidate_prompts");
        argv.add("--audit_file");
        argv.add(auditWithGoldFile.toAbsolutePath().toString());
        argv.add("--output_dir");
        argv.add(outputDir.toAbsolutePath().toString());
        argv.add("--overwrite");
        if (baseOverride != null) {
            argv.add("--auto_tuned_prompt_dir");
            argv.add(baseOverride.autoTunedPromptDir().toAbsolutePath().toString());
        }
```

在文件末尾（最后一个 `}` 前）追加内嵌静态类：

**oldStr**（最后一行的 firstSummary 私有方法 + 类结束）：
```java
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

**newStr**：
```java
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
     * 候选生成时的底板覆盖。仅影响脚本中以 auto_tuned 优先、default 兜底的候选
     * （schema_aware_directional_v2 / schema_fewshot_distilled_v2_strict_tuple）。
     */
    public record BaseOverride(Path autoTunedPromptDir) {

        /**
         * seed=system_default：把 auto_tuned 目录指向一个一定不存在的子目录，
         * 强制脚本走 default 分支。
         *
         * @param outputDir build run workspace 下的 prompt/candidates 目录
         *                  （借用此目录拼一个绝对不会存在的子路径）
         */
        public static BaseOverride systemDefault(Path outputDir) {
            return new BaseOverride(outputDir.resolve("_disabled_auto_tuned"));
        }

        /**
         * seed=graphrag_tuned：指向 prompt-tune cache 命中目录，目录下含 extract_graph.txt。
         */
        public static BaseOverride graphragTuned(Path autoTunedPromptDir) {
            return new BaseOverride(autoTunedPromptDir);
        }
    }
}
```

> 注意：`run` 方法签名变更后，**Phase 4 的 CandidateService 旧调用点会编译失败**。继续做 Task 4 修复 service。

- [ ] **Step 4：构建（暂时编译失败可接受）**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：失败，错误指向 CandidateService.run() 调用签名不匹配。这是预期；Task 4 会修复。

- [ ] **Step 5：跑 orchestrator 自身的测试确认通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=CandidateGenerationOrchestratorSeedTest test 2>&1 | tail -10
```

Expected：3 个新测试 PASS（service 编译错不影响只编译 orchestrator + 其测试）。如果 maven 因 service 编译错跳过 orchestrator 测试编译，**先跳过这步**，等 Task 4 完成后再跑。

- [ ] **Step 6：暂不 commit**

等 Task 4 把 service 同步改完，作为一个 atomic commit 提。

---

## Task 4：CandidateService 接受并应用 seed

`CandidateService.generate(buildRunId)` 内部读 build run metadata 取 seed → 决定 baseOverride → 跑脚本 → 写 seed-info.json。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceSeedTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceSeedTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4.5 引入 seed 后的 CandidateService 行为单测。
 * 关注点：seed 解析 / baseOverride 决策 / seed-info.json 落盘 / 4109 触发。
 *
 * 不重复 Phase 4 已有的"门控 / 候选 manifest 读取"主流程测试。
 */
class CandidateServiceSeedTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneAuditSamplesService samplesStore;
    private BuildRunWorkspaceService workspaceService;
    private CandidateGenerationOrchestrator orchestrator;
    private AuditWithGoldExporter auditExporter;
    private CandidateManifestReader manifestReader;
    private CandidateMetadataLookup metadataLookup;
    private SeedInfoStore seedInfoStore;
    private PromptTuneService promptTuneService;
    private ObjectMapper objectMapper;
    private CandidateService service;
    private Path workspaceDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(CandidateGenerationOrchestrator.class);
        auditExporter = mock(AuditWithGoldExporter.class);
        manifestReader = mock(CandidateManifestReader.class);
        metadataLookup = new CandidateMetadataLookup();
        seedInfoStore = mock(SeedInfoStore.class);
        promptTuneService = mock(PromptTuneService.class);
        objectMapper = new ObjectMapper();

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates"));
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        service = new CandidateService(
                buildRunsStore, samplesStore, workspaceService,
                orchestrator, auditExporter, manifestReader, metadataLookup,
                seedInfoStore, promptTuneService, objectMapper
        );

        when(samplesStore.listByBuildRunId(any())).thenReturn(List.of(completedSample()));
        when(manifestReader.read(any())).thenReturn(List.of());  // 任意非 null 即可
    }

    @Test
    void seedSystemDefaultUsesSystemDefaultOverride() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));

        try {
            service.generate(18L);
        } catch (Exception ignore) {
            // 后续 manifest reader 抛 4105（空候选），无所谓；测前置 baseOverride
        }

        ArgumentCaptor<CandidateGenerationOrchestrator.BaseOverride> captor =
                ArgumentCaptor.forClass(CandidateGenerationOrchestrator.BaseOverride.class);
        verify(orchestrator).run(any(), any(), captor.capture());
        assertThat(captor.getValue().autoTunedPromptDir().toString()).contains("_disabled_auto_tuned");
    }

    @Test
    void seedGraphragTunedUsesCacheDirWhenReady() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));

        // 单一口径：probeBySelection 报 success
        org.ysu.ckqaback.index.dto.PromptTuneRunResponse probe =
                org.ysu.ckqaback.index.dto.PromptTuneRunResponse.builder()
                        .status("success")
                        .cacheKey("abc")
                        .build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(probe);
        // 然后从 cacheKey 取目录路径
        PromptTuneRuns hit = new PromptTuneRuns();
        hit.setId(7L);
        hit.setStatus("success");
        hit.setCandidateDir("prompt-tune-cache/abc/run_7");
        when(promptTuneService.findReadyByCacheKey("abc")).thenReturn(Optional.of(hit));

        try {
            service.generate(18L);
        } catch (Exception ignore) {}

        ArgumentCaptor<CandidateGenerationOrchestrator.BaseOverride> captor =
                ArgumentCaptor.forClass(CandidateGenerationOrchestrator.BaseOverride.class);
        verify(orchestrator).run(any(), any(), captor.capture());
        assertThat(captor.getValue().autoTunedPromptDir().toString()).contains("prompt-tune-cache/abc/run_7");
    }

    @Test
    void seedGraphragTunedThrows4109WhenProbeNotSuccess() {
        // probeBySelection 返回非 success（pending / running / failed / not_started 任一）
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(
                org.ysu.ckqaback.index.dto.PromptTuneRunResponse.builder()
                        .status("running")
                        .cacheKey("abc")
                        .build()
        );

        assertThatThrownBy(() -> service.generate(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE));
    }

    @Test
    void seedGraphragTunedThrows4109WhenProbeSuccessButCacheLookupMisses() {
        // 边界：probe 报 success 但 findReadyByCacheKey 查不到（DB 状态与目录不一致）
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(
                org.ysu.ckqaback.index.dto.PromptTuneRunResponse.builder()
                        .status("success")
                        .cacheKey("abc")
                        .build()
        );
        when(promptTuneService.findReadyByCacheKey("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE));
    }

    @Test
    void seedNullPassesNullBaseOverrideForBackwardCompat() throws Exception {
        // 关键：null seed 与 system_default 必须区分（决策 2 + 风险 1）
        // null 路径让 orchestrator 收到 null baseOverride，不附加 --auto_tuned_prompt_dir
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithMetadata("{}"));

        try { service.generate(18L); } catch (Exception ignore) {}

        verify(orchestrator).run(any(), any(), eq(null));
    }

    @Test
    void writesSeedInfoAfterScriptRuns() throws Exception {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));

        try { service.generate(18L); } catch (Exception ignore) {}

        verify(seedInfoStore).write(any(), any());
    }

    @Test
    void seedInfoWriteFailureDoesNotAbortAndStillInjectsSeedIntoResponse() throws Exception {
        // 关键回归：seed-info.json 写盘失败时，POST 响应仍要返回带 seed 的候选列表
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRunWithSeed("system_default"));
        when(manifestReader.read(any())).thenReturn(List.of(
                CandidateResponse.builder().candidateId("default").displayNameZh("默认基线").build()
        ));
        org.mockito.Mockito.doThrow(new IOException("disk full"))
                .when(seedInfoStore).write(any(), any());

        List<CandidateResponse> result = service.generate(18L);

        // 写文件失败被吞，候选列表仍返回，且 seed 字段已注入
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeed()).isEqualTo("system_default");
    }


    private static PromptTuneAuditSamples completedSample() {
        PromptTuneAuditSamples s = new PromptTuneAuditSamples();
        s.setReviewerDecision("completed");
        return s;
    }

    private static KnowledgeBaseBuildRuns buildRunWithSeed(String seed) {
        return buildRunWithMetadata(
                "{\"customPromptDraft\":{\"seed\":\"" + seed + "\"}}"
        );
    }

    private static KnowledgeBaseBuildRuns buildRunWithMetadata(String json) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(18L);
        r.setKnowledgeBaseId(5L);
        r.setCourseId("crs-1");
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        r.setBuildMetadata(json);
        r.setSelectedMaterialIds("[101]");
        return r;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=CandidateServiceSeedTest test 2>&1 | tail -10
```

Expected：编译失败（构造器签名变了）。

- [ ] **Step 3：修改 CandidateService**

整体修改思路：
1. 构造器加入 `SeedInfoStore` 和 `PromptTuneService` 两个依赖（cache 命中状态 + 目录解析都通过 PromptTuneService 完成，**不**再单独注入 `PromptTuneCacheKeyResolver`）。
2. `generate(buildRunId)` 内增加：解析 metadata → 根据 seed 决定 baseOverride → 调 orchestrator.run(...) → 写 seed-info.json（失败仅 warn）→ 拼装响应时直接注入 seed。

具体替换段（CandidateService 的 generate 方法）：

**oldStr**：
```java
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
```

**newStr**：
```java
        // Phase 4.5：根据 build run 当前 seed 决定底板覆盖
        String seed = resolveSeed(buildRun);
        CandidateGenerationOrchestrator.BaseOverride baseOverride = resolveBaseOverride(seed, buildRun, candidatesDir);

        try {
            orchestrator.run(auditWithGoldFile, candidatesDir, baseOverride);
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

        // 候选生成成功后写 seed-info.json（审计文件 + GET 路径的 seed 来源）
        // 写盘失败仅 warn 不阻断响应：本次 POST 返回的候选会通过 Java 侧 enrichSeed
        // 直接注入 seed，前端展示不受影响；下次重新进入 03 步走 GET 路径时，若文件
        // 仍缺失，候选 seed 会回落到 null（与 Phase 4 老 build run 行为一致）。
        try {
            SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                    .seed(seed)
                    .autoTunedPromptDir(baseOverride != null ? baseOverride.autoTunedPromptDir().toString() : null)
                    .generatedAt(java.time.OffsetDateTime.now())
                    .buildRunId(buildRunId)
                    .build();
            seedInfoStore.write(candidatesDir, info);
        } catch (IOException e) {
            log.warn("写 seed-info.json 失败 buildRunId={}: {}（响应已直接注入 seed，不阻断）",
                    buildRunId, e.getMessage());
        }
```

**关键：在返回响应前把 seed 注入每个候选**——本次 POST 路径不依赖 seed-info.json 是否落盘成功。

定位 generate 方法中读 manifest 的段：

**oldStr**：
```java
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            if (rawCandidateCount > candidates.size()) {
                throw new BusinessException(
                        ApiResultCode.CANDIDATE_GENERATION_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "脚本输出包含未知候选：原始 " + rawCandidateCount + "，识别 " + candidates.size()
                                + "。请检查 CandidateMetadataLookup 是否需要扩容。"
                );
            }
            log.info("候选生成完成 buildRunId={}, count={}, sampleSource=completed×{}",
                    buildRunId, candidates.size(), completedSamples.size());
            return candidates;
```

**newStr**：
```java
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            if (rawCandidateCount > candidates.size()) {
                throw new BusinessException(
                        ApiResultCode.CANDIDATE_GENERATION_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "脚本输出包含未知候选：原始 " + rawCandidateCount + "，识别 " + candidates.size()
                                + "。请检查 CandidateMetadataLookup 是否需要扩容。"
                );
            }
            // Phase 4.5：直接把当前 seed 注入响应，不依赖 seed-info.json 是否落盘成功
            List<CandidateResponse> withSeed = candidates.stream()
                    .map(c -> withInjectedSeed(c, seed))
                    .toList();
            log.info("候选生成完成 buildRunId={}, count={}, seed={}, sampleSource=completed×{}",
                    buildRunId, withSeed.size(), seed, completedSamples.size());
            return withSeed;
```

并在类内追加 helper：

```java
    /**
     * Phase 4.5：把 seed 注入候选响应。
     * <p>POST 生成路径始终以本次计算出的 seed 为准，覆盖 reader 可能从旧 seed-info.json
     * 带回的过期值。即使 reader 此时返回 null（seed-info.json 还没写或写失败），本方法
     * 也能保证响应里的每个候选都带上正确 seed。</p>
     */
    private static CandidateResponse withInjectedSeed(CandidateResponse src, String seed) {
        return CandidateResponse.builder()
                .candidateId(src.getCandidateId())
                .displayNameZh(src.getDisplayNameZh())
                .category(src.getCategory())
                .description(src.getDescription())
                .isRecommended(src.getIsRecommended())
                .traits(src.getTraits())
                .estimatedTokenPerCall(src.getEstimatedTokenPerCall())
                .promptSizeBytes(src.getPromptSizeBytes())
                .schemaUsed(src.getSchemaUsed())
                .fewshotExampleCount(src.getFewshotExampleCount())
                .fewshotStrategy(src.getFewshotStrategy())
                .basePromptSource(src.getBasePromptSource())
                .generationTime(src.getGenerationTime())
                .seed(seed)
                .build();
    }
```

在 `CandidateService` 类末尾（`candidatesDirOf` 方法之后）追加 helper：

**oldStr**：
```java
    private Path candidatesDirOf(KnowledgeBaseBuildRuns buildRun) {
        return workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt")
                .resolve("candidates");
    }
}
```

**newStr**：
```java
    private Path candidatesDirOf(KnowledgeBaseBuildRuns buildRun) {
        return workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt")
                .resolve("candidates");
    }

    /**
     * 从 build run metadata 读 customPromptDraft.seed；缺失返回 null（按"未选择"处理）。
     */
    private String resolveSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode seed = root.path("customPromptDraft").path("seed");
            if (seed.isTextual() && !seed.asText().isBlank()) return seed.asText();
        } catch (Exception e) {
            log.warn("解析 build run metadata 失败 buildRunId={}: {}", buildRun.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * 根据 seed 决定 baseOverride：
     * <ul>
     *   <li>{@code system_default} → 强制 fallback 到 default 分支</li>
     *   <li>{@code graphrag_tuned} → 指向 prompt-tune cache 命中目录；缺失抛 4109</li>
     *   <li>{@code null} 或其它（包括 history_draft，phase 6 范畴） → 返回 null，让 orchestrator 走 Phase 4 兼容路径</li>
     * </ul>
     */
    private CandidateGenerationOrchestrator.BaseOverride resolveBaseOverride(
            String seed,
            KnowledgeBaseBuildRuns buildRun,
            Path candidatesDir
    ) {
        if (seed == null) return null;

        if ("system_default".equals(seed)) {
            return CandidateGenerationOrchestrator.BaseOverride.systemDefault(candidatesDir);
        }

        if ("graphrag_tuned".equals(seed)) {
            // 单一口径：用 probeBySelection 与 SeedAvailabilityService 共享判定逻辑，
            // 确认 cache 当前确实是 success 状态。
            List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
            PromptTuneRunResponse probe = promptTuneService.probeBySelection(
                    buildRun.getKnowledgeBaseId(), buildRun.getCourseId(), materialIds
            );
            if (!"success".equals(probe.getStatus())) {
                throw new BusinessException(
                        ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE,
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "graphrag_tuned 种子的自动调优产物当前不可用（status=" + probe.getStatus()
                                + "），请回知识库构建向导触发自动调优后再试"
                );
            }
            // probe success 后取出 cacheKey，再用 findReadyByCacheKey 拿到 candidateDir 实体路径
            return promptTuneService.findReadyByCacheKey(probe.getCacheKey())
                    .map(run -> workspaceService.resolve(run.getCandidateDir()))
                    .map(CandidateGenerationOrchestrator.BaseOverride::graphragTuned)
                    .orElseThrow(() -> new BusinessException(
                            ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE,
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "graphrag_tuned 自动调优产物 cache 状态与目录不一致，请重新选择种子"
                    ));
        }

        // history_draft 或未知种子：本期当作"无 override"，由 Phase 6 重新落地
        return null;
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

> 注意 import：`resolveBaseOverride` 用到了 `com.fasterxml.jackson.core.type.TypeReference`、`org.ysu.ckqaback.index.dto.PromptTuneRunResponse` 与 `java.util.List`。如果 CandidateService 现有 imports 不含，需要补。

> 注意：构造器需要在 `@RequiredArgsConstructor` 下自动加入新字段。在类字段段加入：

**oldStr**：
```java
    private final ObjectMapper objectMapper;
```

**newStr**：
```java
    private final ObjectMapper objectMapper;
    private final SeedInfoStore seedInfoStore;
    private final PromptTuneService promptTuneService;
```

> 注意字段顺序：因为 `@RequiredArgsConstructor` 按声明顺序生成构造器，测试里 mock 顺序要对齐。如果 Phase 4 已有字段顺序不同，按现有顺序追加在末尾即可。

- [ ] **Step 4：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 5：跑 service 测试 + orchestrator 测试**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='CandidateServiceSeedTest,CandidateGenerationOrchestratorSeedTest' test 2>&1 | tail -10
```

Expected：所有测试 PASS。

- [ ] **Step 6：跑全部 candidate 相关测试不破坏 Phase 4**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='Candidate*Test' test 2>&1 | grep -E "Tests run|FAIL" | tail -5
```

Expected：原 Phase 4 的 CandidateServiceTest / CandidateManifestReaderTest 等仍 PASS。如有失败，多半是 Phase 4 测试的 mock 没传 baseOverride，更新这些测试的 verify 调用为接受三参数即可（Task 4 计划中预期：编辑 4-5 处 mock 期望）。

- [ ] **Step 7：commit Task 3 + Task 4**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateGenerationOrchestrator.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateGenerationOrchestratorSeedTest.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateServiceSeedTest.java
git commit -m "feat(prompt-builder): 03 候选生成根据 seed 决定底板，写 seed-info.json (Phase 4.5)"
```

---

## Task 5：CandidateManifestReader 注入 seed 字段

读 seed-info.json 的内容注入到每个 CandidateResponse 的 seed 字段。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderSeedTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderSeedTest.java`：

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

class CandidateManifestReaderSeedTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SeedInfoStore seedInfoStore = new SeedInfoStore(objectMapper);
    private final CandidateManifestReader reader =
            new CandidateManifestReader(new CandidateMetadataLookup(), objectMapper, seedInfoStore);

    @Test
    void injectsSeedFromSeedInfoFileWhenPresent(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {"candidates":[{"candidate_name":"default","schema_used":false}]}
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");
        Files.writeString(candidatesDir.resolve("seed-info.json"), """
                {"seed":"graphrag_tuned","autoTunedPromptDir":"/cache/run_3","buildRunId":18}
                """);

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void seedIsNullWhenSeedInfoFileMissing(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {"candidates":[{"candidate_name":"default","schema_used":false}]}
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSeed()).isNull();
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=CandidateManifestReaderSeedTest test 2>&1 | tail -10
```

Expected：编译失败（构造器多了 SeedInfoStore 参数）。

- [ ] **Step 3：修改 reader**

把构造器改为接受 `SeedInfoStore`。

**oldStr**：
```java
    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;
```

**newStr**：
```java
    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;
    private final SeedInfoStore seedInfoStore;
```

修改 `read()` 方法：在循环开始前读 seed-info.json 一次，循环中传入 seed。

**oldStr**（`read` 方法整体或核心循环）：
```java
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
```

**newStr**：
```java
        // Phase 4.5：一次读入 seed-info.json，所有候选共享 seed
        String seed = seedInfoStore.read(candidatesDir).map(SeedInfoStore.SeedInfo::getSeed).orElse(null);

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
            result.add(buildResponse(candidateId, entry, candidatesDir, seed));
        }
        return result;
    }
```

修改 `buildResponse` 接受 seed 参数：

**oldStr**：
```java
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
```

**newStr**：
```java
    private CandidateResponse buildResponse(
            String candidateId,
            Map<String, Object> entry,
            Path candidatesDir,
            String seed
    ) {
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
                .seed(seed)
                .build();
    }
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='CandidateManifestReaderTest,CandidateManifestReaderSeedTest' test 2>&1 | tail -10
```

Expected：Phase 4 已有的 reader 测试如有失败（构造器多参数），更新这些测试的 reader 实例化即可。所有测试 PASS。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/CandidateManifestReader.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/CandidateManifestReaderSeedTest.java
git commit -m "feat(prompt-builder): CandidateManifestReader 从 seed-info.json 注入 seed 字段 (Phase 4.5)"
```

---

## Task 6：SeedAvailabilityService + 端点

新增 `GET /knowledge-base-build-runs/{id}/seed-availability` 端点：返回 3 个 seed 各自的可用状态。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/SeedAvailabilityResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java`

- [ ] **Step 1：写 DTO**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/SeedAvailabilityResponse.java`：

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /knowledge-base-build-runs/{id}/seed-availability 响应。
 * 列出 01 步每个 seed 选项的可用状态。
 */
@Getter
@Builder
public class SeedAvailabilityResponse {

    /** 当前 build run metadata 中已选定的种子（可能为空，前端用于回填）。 */
    private final String currentSeed;

    private final List<SeedOption> options;

    @Getter
    @Builder
    public static class SeedOption {
        /** system_default / graphrag_tuned / history_draft。 */
        private final String key;
        /** 是否可选。 */
        private final Boolean available;
        /** 不可选时的原因 key（前端做 i18n / tooltip 文案映射）。 */
        private final String reason;
        /** 给前端展示的简短描述（如自动调优产物的状态）。 */
        private final String summary;
    }
}
```

- [ ] **Step 2：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeedAvailabilityServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsStore;
    private PromptTuneService promptTuneService;
    private SeedAvailabilityService service;

    @BeforeEach
    void setUp() {
        buildRunsStore = mock(KnowledgeBaseBuildRunsService.class);
        promptTuneService = mock(PromptTuneService.class);
        service = new SeedAvailabilityService(buildRunsStore, promptTuneService, new ObjectMapper());
    }

    @Test
    void systemDefaultAlwaysAvailable() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun("system_default"));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "system_default");
        assertThat(opt.getAvailable()).isTrue();
    }

    @Test
    void graphragTunedAvailableWhenCacheSuccess() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse hit = PromptTuneRunResponse.builder()
                .status("success")
                .cacheKey("abc")
                .cacheHit(true)
                .build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(hit);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isTrue();
    }

    @Test
    void graphragTunedUnavailableWhenCacheNotStarted() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_not_started");
    }

    @Test
    void graphragTunedUnavailableWhenCacheRunning() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse running = PromptTuneRunResponse.builder().status("running").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(running);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_running");
    }

    @Test
    void graphragTunedUnavailableWhenCachePending() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse pending = PromptTuneRunResponse.builder().status("pending").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(pending);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_pending");
    }

    @Test
    void graphragTunedUnavailableWhenCacheFailed() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        PromptTuneRunResponse failed = PromptTuneRunResponse.builder().status("failed").cacheKey("abc").build();
        when(promptTuneService.probeBySelection(any(), any(), any())).thenReturn(failed);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("auto_tuned_failed");
    }

    @Test
    void graphragTunedUnavailableWhenProbeThrows() {
        // probeBySelection 抛运行时异常（material id 损坏 / DB 故障 / 序列化失败）
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "graphrag_tuned");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("evaluation_failed");
    }

    @Test
    void historyDraftAlwaysUnavailableInThisPhase() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun(null));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.notStarted(null, "abc"));

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedAvailabilityResponse.SeedOption opt = findOption(response, "history_draft");
        assertThat(opt.getAvailable()).isFalse();
        assertThat(opt.getReason()).isEqualTo("phase_6_not_implemented");
    }

    @Test
    void exposesCurrentSeedFromMetadata() {
        when(buildRunsStore.getRequiredById(18L)).thenReturn(buildRun("graphrag_tuned"));
        when(promptTuneService.probeBySelection(any(), any(), any()))
                .thenReturn(PromptTuneRunResponse.builder().status("success").cacheKey("abc").build());

        SeedAvailabilityResponse response = service.evaluate(18L);
        assertThat(response.getCurrentSeed()).isEqualTo("graphrag_tuned");
    }

    private static KnowledgeBaseBuildRuns buildRun(String seed) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(18L);
        r.setKnowledgeBaseId(5L);
        r.setCourseId("crs-1");
        r.setSelectedMaterialIds("[101]");
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        if (seed != null) {
            r.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"" + seed + "\"}}");
        }
        return r;
    }

    private static SeedAvailabilityResponse.SeedOption findOption(
            SeedAvailabilityResponse response, String key
    ) {
        return response.getOptions().stream()
                .filter(o -> key.equals(o.getKey()))
                .findFirst().orElseThrow();
    }
}
```

- [ ] **Step 3：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=SeedAvailabilityServiceTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 4：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse.SeedOption;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算 01 步种子选项的可用状态。
 *
 * <ul>
 *   <li>{@code system_default}：始终可用</li>
 *   <li>{@code graphrag_tuned}：当且仅当当前 build run 选材的 prompt-tune 缓存为 success 时可用</li>
 *   <li>{@code history_draft}：本期始终不可用（Phase 6 落地）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SeedAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(SeedAvailabilityService.class);

    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final PromptTuneService promptTuneService;
    private final ObjectMapper objectMapper;

    public SeedAvailabilityResponse evaluate(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        String currentSeed = readCurrentSeed(buildRun);

        List<SeedOption> options = new ArrayList<>();
        options.add(buildSystemDefault());
        options.add(buildGraphragTuned(buildRun));
        options.add(buildHistoryDraft());

        return SeedAvailabilityResponse.builder()
                .currentSeed(currentSeed)
                .options(options)
                .build();
    }

    private SeedOption buildSystemDefault() {
        return SeedOption.builder()
                .key("system_default")
                .available(true)
                .reason(null)
                .summary("使用 GraphRAG 内置默认提示词作为起点")
                .build();
    }

    private SeedOption buildGraphragTuned(KnowledgeBaseBuildRuns buildRun) {
        try {
            List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
            PromptTuneRunResponse probe = promptTuneService.probeBySelection(
                    buildRun.getKnowledgeBaseId(), buildRun.getCourseId(), materialIds
            );
            String status = probe.getStatus();
            if ("success".equals(status)) {
                return SeedOption.builder()
                        .key("graphrag_tuned")
                        .available(true)
                        .reason(null)
                        .summary("当前选材的自动调优结果可用")
                        .build();
            }
            String reason = switch (status == null ? "not_started" : status) {
                case "running" -> "auto_tuned_running";
                case "pending" -> "auto_tuned_pending";
                case "failed" -> "auto_tuned_failed";
                default -> "auto_tuned_not_started";
            };
            return SeedOption.builder()
                    .key("graphrag_tuned")
                    .available(false)
                    .reason(reason)
                    .summary("当前选材尚未生成可用的自动调优产物")
                    .build();
        } catch (RuntimeException e) {
            log.warn("评估 graphrag_tuned 可用性失败 buildRunId={}: {}", buildRun.getId(), e.getMessage());
            return SeedOption.builder()
                    .key("graphrag_tuned")
                    .available(false)
                    .reason("evaluation_failed")
                    .summary("无法评估自动调优产物状态")
                    .build();
        }
    }

    private SeedOption buildHistoryDraft() {
        return SeedOption.builder()
                .key("history_draft")
                .available(false)
                .reason("phase_6_not_implemented")
                .summary("历史草稿入口将在 Phase 6 开放")
                .build();
    }

    private String readCurrentSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode seed = root.path("customPromptDraft").path("seed");
            return seed.isTextual() ? seed.asText() : null;
        } catch (Exception e) {
            log.warn("解析 build run metadata 失败 buildRunId={}: {}", buildRun.getId(), e.getMessage());
            return null;
        }
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

- [ ] **Step 5：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=SeedAvailabilityServiceTest test 2>&1 | tail -10
```

Expected：9 个测试 PASS（含 pending / failed / evaluation_failed 三条新增覆盖）。

- [ ] **Step 6：注入到 Controller + 加端点**

修改 `PromptTunePipelineController.java`：

**oldStr**（字段段）：
```java
    private final AuditSampleService auditSampleService;
    private final AiSuggestionService aiSuggestionService;
    private final CandidateService candidateService;
    private final ExtractionEvalService extractionEvalService;
```

**newStr**：
```java
    private final AuditSampleService auditSampleService;
    private final AiSuggestionService aiSuggestionService;
    private final CandidateService candidateService;
    private final ExtractionEvalService extractionEvalService;
    private final SeedAvailabilityService seedAvailabilityService;
```

补 import：

**oldStr**：
```java
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
```

**newStr**：
```java
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;
import org.ysu.ckqaback.index.SeedAvailabilityService;
```

> 注意：如果 import 已有 SeedAvailabilityService，跳过该行。

在 03 步候选段（generateCandidates 之前）插入新端点：

**oldStr**：
```java
    /**
     * 03 步：触发候选 prompt 生成。
     *
     * <p>覆盖式：每次调用都会重新执行 {@code generate_candidate_prompts.py}（脚本实测 ~66 ms），
     * 把含 DB gold 的 audit JSON 喂给脚本，输出 4 个候选到 build run workspace 下的 prompt/candidates 目录。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
```

**newStr**：
```java
    /**
     * Phase 4.5：返回 01 步 3 个种子选项各自的可用状态。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/seed-availability")
    public ApiResponse<SeedAvailabilityResponse> getSeedAvailability(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(seedAvailabilityService.evaluate(buildRunId));
    }

    /**
     * 03 步：触发候选 prompt 生成。
     *
     * <p>覆盖式：每次调用都会重新执行 {@code generate_candidate_prompts.py}（脚本实测 ~66 ms），
     * 把含 DB gold 的 audit JSON 喂给脚本，输出 4 个候选到 build run workspace 下的 prompt/candidates 目录。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
```

- [ ] **Step 7：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 8：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/SeedAvailabilityResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java
git commit -m "feat(prompt-builder): 新增 GET /seed-availability 端点 + SeedAvailabilityService (Phase 4.5)"
```

---

## Task 7：~~04 步评分透传 seed~~（已移交 Phase 5）

> **本任务已从 Phase 4.5 移除**，移到 Phase 5 实施计划中：
> `docs/superpowers/plans/2026-05-17-prompt-builder-phase5-extraction-eval.md`。
>
> 原因：04 步评分链路（`prompt_tune_extraction_eval_runs` 表 / `ExtractionEvalService` /
> `ExtractionEvalReportAssembler` / `ExtractionEvalReportResponse`）属于 Phase 5 范围，
> Phase 4.5 不应跨阶段修改这些工件。Phase 5 实施计划已增加对应 Task 把 seed 字段直接合进
> 表 schema、Entity、Service 与 Report，不再单独 ALTER TABLE。
>
> Phase 4.5 本期只完成 01-03 步骤的 seed 真正分流：
> - 01 步种子可用性探测 + 即时持久化
> - 03 步候选生成根据 seed 决定底板
> - seed-info.json 旁路落盘 + CandidateResponse.seed 字段注入
> - 03 / 05 前端 UI 展示 seed
>
> 04 步评分对 seed 的感知（`prompt_tune_extraction_eval_runs.seed` 列、报告透传）由 Phase 5 落地。

下面的内容**仅作 Phase 5 计划的迁移参考保留**，本期不实施：

<details>
<summary>原 Phase 4.5 Task 7 内容（已移交 Phase 5）</summary>

为评分表加 seed 字段；启动评分时把当前 build run 的 seed 写入 eval run；report 响应顶层透传 seed。

**Files:**
- Create: `sql/migrations/20260518_eval_runs_seed_column.sql`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java`

- [ ] **Step 1：写 SQL 迁移**

Create `sql/migrations/20260518_eval_runs_seed_column.sql`：

```sql
-- CKQA Phase 4.5：评分运行表加 seed 字段
-- Date: 2026-05-18
-- 评分启动时把 build run 当时的 seed 写到这里，便于审计
-- "本次评分基于哪个种子的候选 prompt"。

ALTER TABLE `prompt_tune_extraction_eval_runs`
  ADD COLUMN IF NOT EXISTS `seed` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL
  COMMENT '本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null）'
  AFTER `selected_candidate_ids`;
```

> 注意：MySQL 8.0 之前不支持 `ADD COLUMN IF NOT EXISTS`。如果项目用的是 5.7，改用 information_schema 判断模板（仿 Phase 5 表的外键 IF EXISTS 模式）。

- [ ] **Step 2：执行迁移**

```bash
cd infra && mysql ... < ../sql/migrations/20260518_eval_runs_seed_column.sql
```

Expected：列添加成功。

- [ ] **Step 3：Entity 加字段**

修改 `PromptTuneExtractionEvalRuns.java`：

**oldStr**：
```java
    /** 用户在 03 步勾选的候选 ID 列表，JSON 字符串形态。 */
    @TableField("selected_candidate_ids")
    private String selectedCandidateIds;
```

**newStr**：
```java
    /** 用户在 03 步勾选的候选 ID 列表，JSON 字符串形态。 */
    @TableField("selected_candidate_ids")
    private String selectedCandidateIds;

    /** 本次评分启动时 build run 的 seed 快照。 */
    @TableField("seed")
    private String seed;
```

- [ ] **Step 4：ExtractionEvalReportResponse 加顶层 seed**

**oldStr**：
```java
    private final Long evalRunId;
    private final LocalDateTime generatedAt;
    private final List<CandidateReport> candidates;
```

**newStr**：
```java
    private final Long evalRunId;
    private final LocalDateTime generatedAt;
    /** 本次评分启动时 build run 的 seed 快照。前端用于展示"本次评分基于哪个种子的候选"。 */
    private final String seed;
    private final List<CandidateReport> candidates;
```

- [ ] **Step 5：ExtractionEvalService 启动评分时写入 seed**

定位 `createPending(...)` 方法，加上 seed 解析：

**oldStr**：
```java
    private PromptTuneExtractionEvalRuns createPending(KnowledgeBaseBuildRuns buildRun, List<String> selected) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setBuildRunId(buildRun.getId());
        run.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        run.setSelectedCandidateIds(serialize(selected));
        run.setStatus("pending");
        run.setProgressStage("queued");
        run.setTriggeredByUserId(buildRun.getRequestedByUserId());
```

**newStr**：
```java
    private PromptTuneExtractionEvalRuns createPending(KnowledgeBaseBuildRuns buildRun, List<String> selected) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setBuildRunId(buildRun.getId());
        run.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        run.setSelectedCandidateIds(serialize(selected));
        run.setSeed(resolveSeedFromMetadata(buildRun));
        run.setStatus("pending");
        run.setProgressStage("queued");
        run.setTriggeredByUserId(buildRun.getRequestedByUserId());
```

加 helper（在类末尾追加）：

**oldStr**（在 `serialize` 方法之后，类闭合 `}` 之前）：
```java
    private String serialize(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化候选 ID 失败"
            );
        }
    }
}
```

**newStr**：
```java
    private String serialize(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化候选 ID 失败"
            );
        }
    }

    /** Phase 4.5：从 build run metadata 取 seed 写入 eval run 快照。 */
    private String resolveSeedFromMetadata(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode seed = root.path("customPromptDraft").path("seed");
            return seed.isTextual() ? seed.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 6：ExtractionEvalReportAssembler 透传 seed**

**oldStr**：
```java
    public ExtractionEvalReportResponse assemble(PromptTuneExtractionEvalRuns run) {
        return ExtractionEvalReportResponse.builder()
                .evalRunId(run.getId())
                .generatedAt(run.getFinishedAt())
                .candidates(parseCandidates(run.getReportJson()))
                .build();
    }
```

**newStr**：
```java
    public ExtractionEvalReportResponse assemble(PromptTuneExtractionEvalRuns run) {
        return ExtractionEvalReportResponse.builder()
                .evalRunId(run.getId())
                .generatedAt(run.getFinishedAt())
                .seed(run.getSeed())
                .candidates(parseCandidates(run.getReportJson()))
                .build();
    }
```

- [ ] **Step 7：相关测试更新**

Phase 5 的 `ExtractionEvalServiceTest` 中，断言 `createPending` 写入的字段需要新增 seed；`ExtractionEvalReportAssemblerTest` 加一个测试覆盖 seed 透传。

预期测试更新点：
- `ExtractionEvalServiceTest.triggerCreatesPendingRunAndDispatchesAfterCommit`：断言 `r.getSeed()` 与 build run metadata 中的 seed 一致。
- `ExtractionEvalReportAssemblerTest`：新增测试 `assemblesSeedFromRunEntity`，准备 run.setSeed("graphrag_tuned")，断言 response.getSeed() == "graphrag_tuned"。

- [ ] **Step 8：跑全部 eval 相关测试**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='ExtractionEval*Test' test 2>&1 | grep -E "Tests run|FAIL" | tail -5
```

Expected：所有 PASS。

- [ ] **Step 9：commit**

```bash
git add sql/migrations/20260518_eval_runs_seed_column.sql \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java
git commit -m "feat(prompt-builder): 04 步评分透传 seed 快照 + report 响应携带 (Phase 4.5)"
```

</details>

---

## Task 8：前端 API 客户端补 getSeedAvailability + customPromptDraft seed 写入

新增 GET 端点的 client；现有 PUT custom-prompt-draft 在 01 步选 seed 时即调。

**Files:**
- Modify: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`
- Test: `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-seed.test.js`

- [ ] **Step 1：写失败的测试**

Create `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-seed.test.js`：

```javascript
import { describe, it, expect } from 'vitest'
import { getSeedAvailability } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  const calls = []
  const dispatch = (method) => async (url) => {
    calls.push({ method, url })
    const next = responses.shift()
    if (!next) throw new Error('no response queued')
    if (next.code !== 200) {
      const err = new Error(next.message)
      err.code = next.code
      err.response = { data: { code: next.code, message: next.message, data: null } }
      throw err
    }
    return { data: { code: 200, message: 'ok', data: next.data, timestamp: '2026-05-18T00:00:00' } }
  }
  return { get: dispatch('GET'), post: dispatch('POST'), put: dispatch('PUT'), requests: calls }
}

describe('seed availability API client', () => {
  it('GET /seed-availability', async () => {
    const client = makeMockClient([
      { code: 200, data: {
        currentSeed: 'graphrag_tuned',
        options: [
          { key: 'system_default', available: true, reason: null, summary: '默认' },
          { key: 'graphrag_tuned', available: true, reason: null, summary: '可用' },
          { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
        ],
      } },
    ])
    const result = await getSeedAvailability(18, client)
    expect(client.requests[0].method).toBe('GET')
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/18/seed-availability')
    expect(result.options).toHaveLength(3)
    expect(result.currentSeed).toBe('graphrag_tuned')
  })

  it('对 buildRunId 做 URL encoding', async () => {
    const client = makeMockClient([{ code: 200, data: { options: [] } }])
    await getSeedAvailability('a/b', client)
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/a%2Fb/seed-availability')
  })
})
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd frontend/apps/admin-app && pnpm test prompt-tune-pipeline-seed 2>&1 | tail -10
```

Expected：FAIL。

- [ ] **Step 3：在 prompt-tune-pipeline.js 加端点**

定位 `// ----- 03 步` 之前，新增：

```javascript
// ----- Phase 4.5：01 步种子可用性 -----

export async function getSeedAvailability(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/seed-availability`,
  ))
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd frontend/apps/admin-app && pnpm test prompt-tune-pipeline-seed 2>&1 | tail -10
```

Expected：2 个测试 PASS。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js \
        frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-seed.test.js
git commit -m "feat(prompt-builder): API 加 getSeedAvailability 端点 (Phase 4.5)"
```

---

## Task 9：前端 PromptBuilderPage / SeedStep 接入可用性

01 步进入时拉 seed-availability，graphrag_tuned 不可用时禁用 + tooltip；用户切 seed 立即 PUT customPromptDraft（仅写 seed 子字段）。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`

- [ ] **Step 1：在 PromptBuilderPage 加载 seed-availability**

定位 PromptBuilderPage `onMounted` 段，在 buildRun fetch 之后增加：

**oldStr**：
```javascript
import { getBuildRun } from '../../api/knowledge-bases.js'
```

**newStr**：
```javascript
import { getBuildRun } from '../../api/knowledge-bases.js'
import { getSeedAvailability } from '../../api/prompt-tune-pipeline.js'
import { saveCustomPromptDraft } from '../../api/knowledge-bases.js'
//                                          ^^^^ 注意：现有项目 saveCustomPromptDraft 路径以
//                                          实际代码为准；如果不存在该 API helper，请以 PUT
//                                          /custom-prompt-draft 端点直接通过 axios 调用
```

> 提示：如果项目里已有 `saveCustomPromptDraft`（很可能在 knowledge-bases.js 或独立文件中），import 它；否则在 prompt-tune-pipeline.js 加一个 thin wrapper。本计划不强制具体位置，按现有约定来。

加 ref 与加载逻辑：

**oldStr**：
```javascript
const seed = ref(null)
const courseName = ref(MOCK_COURSE_NAME)
```

**newStr**：
```javascript
const seed = ref(null)
const seedAvailability = ref(null)  // Phase 4.5：3 个种子选项的可用状态
const courseName = ref(MOCK_COURSE_NAME)
```

在 onMounted 内拉 availability：

**oldStr**：
```javascript
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const draft = meta.customPromptDraft
    if (draft?.seed) seed.value = draft.seed
    dirty.value = false
  } catch (e) {
    error.value = { message: e?.message ?? '加载草稿失败' }
  } finally {
    loading.value = false
  }
```

**newStr**：
```javascript
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const draft = meta.customPromptDraft
    if (draft?.seed) seed.value = draft.seed

    // Phase 4.5：拉种子可用性，01 步据此决定哪些选项可点
    try {
      seedAvailability.value = await getSeedAvailability(buildRunId.value)
    } catch (availErr) {
      // 不阻塞主流程：拉失败时所有种子按"未知"处理，前端给保守回退
      // eslint-disable-next-line no-console
      console.warn('[seed-availability] 加载失败', availErr)
    }

    dirty.value = false
  } catch (e) {
    error.value = { message: e?.message ?? '加载草稿失败' }
  } finally {
    loading.value = false
  }
```

修改 `handleSelectSeed` 即时持久化 seed：

**oldStr**：
```javascript
function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    // Phase 1e：从历史草稿列表中选取最新一条作为 mock 演示
```

**newStr**：
```javascript
async function persistSeedToBuildRun(seedKey) {
  // Phase 4.5：把 seed 持久化到 build run metadata.customPromptDraft.seed，
  // 让 03 步后端拿到正确的种子用于决定 baseOverride。
  // 仅写 seed 子字段，不影响 prompts.extract_graph.content。
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const nextDraft = { ...(meta.customPromptDraft ?? {}), seed: seedKey }
    await saveCustomPromptDraft(buildRunId.value, nextDraft)
  } catch (err) {
    // eslint-disable-next-line no-console
    console.warn('[seed] 持久化失败，将由 05 步保存补救', err)
  }
}

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    // Phase 1e：从历史草稿列表中选取最新一条作为 mock 演示
```

并在已有的 seed 实际切换处插入 `persistSeedToBuildRun(seedKey)`。具体位置看现有实现：

**oldStr**（handleSelectSeed 末尾设置 seed.value 的地方）：
```javascript
  seed.value = seedKey
  dirty.value = true
}
```

**newStr**：
```javascript
  // Phase 4.5：种子切换 → 提示当前候选已失效（不清空数据；下次进 03 步会触发覆盖式重生成）
  const seedSwitched = seed.value && seed.value !== seedKey
  seed.value = seedKey
  dirty.value = true
  persistSeedToBuildRun(seedKey)
  if (seedSwitched) {
    ElMessage.info('种子已切换，当前候选将失效，需要重新生成')
  }
}
```

把 seedAvailability 透传给 SeedStep：

**oldStr**：
```html
        <PromptBuilderSeedStep
          v-if="activeStepKey === 'seed'"
          :seed="seed"
          :history-drafts="MOCK_HISTORY_DRAFTS"
          @select-seed="handleSelectSeed"
        />
```

**newStr**：
```html
        <PromptBuilderSeedStep
          v-if="activeStepKey === 'seed'"
          :seed="seed"
          :seed-availability="seedAvailability"
          :history-drafts="MOCK_HISTORY_DRAFTS"
          @select-seed="handleSelectSeed"
        />
```

- [ ] **Step 2：修改 SeedStep 消费 availability**

修改 `PromptBuilderSeedStep.vue`：

**oldStr**：
```javascript
const props = defineProps({
  seed: { type: String, default: null },
  graphragTunedSummary: { type: Object, default: null },
  historyDrafts: { type: Array, default: () => [] },
})
```

**newStr**：
```javascript
const props = defineProps({
  seed: { type: String, default: null },
  graphragTunedSummary: { type: Object, default: null },
  historyDrafts: { type: Array, default: () => [] },
  /**
   * Phase 4.5：来自 GET /seed-availability 的响应。
   * 形状 { currentSeed, options: [{ key, available, reason, summary }, ...] }。
   * 缺失（接口失败）时所有 seed 视为可用，由后端兜底校验。
   */
  seedAvailability: { type: Object, default: null },
})
```

修改 `isOptionDisabled`：

**oldStr**：
```javascript
const isOptionDisabled = (option) => option.key === 'history_draft'
```

**newStr**：
```javascript
function isOptionDisabled(option) {
  // Phase 4.5：先检查 availability；失败时只禁 history_draft
  const opts = props.seedAvailability?.options ?? []
  const match = opts.find((o) => o.key === option.key)
  if (match) return !match.available
  return option.key === 'history_draft'
}

function disabledReasonFor(option) {
  const opts = props.seedAvailability?.options ?? []
  const match = opts.find((o) => o.key === option.key)
  if (!match || match.available) return ''
  switch (match.reason) {
    case 'auto_tuned_not_started':
      return '请先在知识库构建向导触发自动调优'
    case 'auto_tuned_running':
    case 'auto_tuned_pending':
      return '自动调优正在执行，请稍候'
    case 'auto_tuned_failed':
      return '上次自动调优失败，请重新触发'
    case 'phase_6_not_implemented':
      return '历史草稿入口将在后续版本开放'
    case 'evaluation_failed':
      return '无法评估自动调优产物状态'
    default:
      return match.summary ?? '当前不可选'
  }
}
```

修改模板的 button 加 tooltip：

**oldStr**：
```html
      <button
        v-for="option in SEED_OPTIONS"
        :key="option.key"
        type="button"
        role="radio"
        :aria-checked="seed === option.key"
        :aria-disabled="isOptionDisabled(option)"
        :tabindex="isOptionDisabled(option) ? -1 : 0"
        class="seed-card"
        :data-selected="seed === option.key ? 'true' : 'false'"
        :data-disabled="isOptionDisabled(option) ? 'true' : 'false'"
        @click="!isOptionDisabled(option) && emit('select-seed', option.key)"
      >
        <strong>{{ option.title }}</strong>
```

**newStr**：
```html
      <button
        v-for="option in SEED_OPTIONS"
        :key="option.key"
        type="button"
        role="radio"
        :aria-checked="seed === option.key"
        :aria-disabled="isOptionDisabled(option)"
        :tabindex="isOptionDisabled(option) ? -1 : 0"
        class="seed-card"
        :data-selected="seed === option.key ? 'true' : 'false'"
        :data-disabled="isOptionDisabled(option) ? 'true' : 'false'"
        :title="isOptionDisabled(option) ? disabledReasonFor(option) : ''"
        @click="!isOptionDisabled(option) && emit('select-seed', option.key)"
      >
        <strong>{{ option.title }}</strong>
```

- [ ] **Step 3：构建确认无语法错**

```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：BUILD SUCCESS。

- [ ] **Step 4：跑前端单测**

```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```

Expected：所有 PASS。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue
git commit -m "feat(prompt-builder): 01 步消费 seed-availability + 切换时持久化到 build run (Phase 4.5)"
```

---

## Task 10：前端 03 / 05 步展示 seed + stale 比较

候选卡片角标：`基于 系统默认` / `基于 GraphRAG 自动调优`；
03 步进入时比较 `candidates[0].seed` 与当前 build run seed，不一致时标记 stale；
05 步表单"来源记录"区追加"起始种子"行。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue`（或对应组件）
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`（stale 比较 + 提示横幅）
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`

- [ ] **Step 1：在 CandidateCard 渲染 seed badge**

定位 CandidateCard 里展示 traits 的段，在合适位置增加：

```html
<span v-if="candidate.seed" class="candidate-card__seed-badge">
  基于 {{ seedLabel(candidate.seed) }}
</span>
```

并在 `<script setup>` 增加：

```javascript
function seedLabel(seed) {
  if (seed === 'system_default') return '系统默认'
  if (seed === 'graphrag_tuned') return '自动调优'
  return seed
}
```

样式根据现有 chip 风格统一即可（颜色用辅助色，避免抢占推荐徽章的紫色）。

> **注意**：本期 default 与 auto_tuned 候选不依赖 seed，但 seed-info.json 写的是 build run 级别的种子，所有 4 个候选 seed 字段相同。前端 UI 上对 default / auto_tuned 也展示 seed 角标是合理的（语义为"本组候选生成时所基于的种子"），不会产生误导。

- [ ] **Step 2：在 PromptBuilderCandidatesStep 加 stale 比较 + 提示横幅**

03 步组件接收 props.currentBuildRunSeed（由 PromptBuilderPage 透传）。比较候选的 seed 与当前 build run 的 seed：

在 `<script setup>` 增加：
```javascript
const props = defineProps({
  // 既有 props ...
  currentBuildRunSeed: { type: String, default: null },
})

const isCandidatesStaleBySeed = computed(() => {
  if (candidates.value.length === 0) return false
  const candidateSeed = candidates.value[0].seed ?? null  // 整组候选共享 seed
  // 比较两个数据源（前端能直接拿到的）：build run metadata 中的 seed vs 候选响应中的 seed
  // 注意：candidateSeed 为 null 表示 Phase 4 老 build run 候选目录（无 seed-info.json），
  //       视为"未知种子"，不强制 stale
  if (candidateSeed === null) return false
  return candidateSeed !== props.currentBuildRunSeed
})
```

模板中候选网格上方加一个横幅（仅当 `isCandidatesStaleBySeed` 为 true 时显示）：

```html
<div v-if="isCandidatesStaleBySeed" class="candidate-stale-banner">
  <span>⚠ 当前候选基于旧种子（{{ seedLabel(candidates[0]?.seed) }}）生成，
        与本次构建当前的种子（{{ seedLabel(currentBuildRunSeed) }}）不一致，
        建议点"重新生成候选"覆盖更新。</span>
  <el-button size="small" @click="handleRegenerate">立即重新生成</el-button>
</div>
```

`seedLabel` 函数复用 CandidateCard 里的同名工具，或单独定义一份。

> 复用 Phase 4 已有的 `handleRegenerate`：它本就是 POST `/candidates` 覆盖式重生成入口。

- [ ] **Step 3：PromptBuilderPage 透传 currentBuildRunSeed 到 03 步组件**

定位 `<PromptBuilderCandidatesStep` 段：

**oldStr**：
```html
        <PromptBuilderCandidatesStep
          v-else-if="activeStepKey === 'candidates'"
          :dirty="dirty"
          @start-scoring="handleEnterScoring"
          @back="gotoPrev"
        />
```

**newStr**：
```html
        <PromptBuilderCandidatesStep
          v-else-if="activeStepKey === 'candidates'"
          :dirty="dirty"
          :current-build-run-seed="seed"
          @start-scoring="handleEnterScoring"
          @back="gotoPrev"
        />
```

> 注意：`seed` ref 是 PromptBuilderPage 已有的状态，自动调优产物变化或者用户切 seed 时 ref 自动更新，03 步组件能直接看到最新值。

- [ ] **Step 4：在 PromptBuilderSaveStep 来源记录加 seed 行**

定位 SaveStep 的来源记录段（含课程 / 构建运行 / 选定种子的现有渲染）：

```html
<div><span>选定种子</span><strong>{{ seedLabel }}</strong></div>
```

确认这一行已存在；本期不动模板，只把 `seedLabel` computed 的兜底文案补上 graphrag_tuned 的"自动调优"显示。

如已存在，跳过。

- [ ] **Step 5：构建 + 跑单测**

```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```

Expected：BUILD SUCCESS + 全部测试 PASS。

- [ ] **Step 6：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue \
        frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue
git commit -m "feat(prompt-builder): 03 候选卡片 seed 角标 + stale 比较 + 05 来源记录展示 seed (Phase 4.5)"
```

---

## Task 11：真后端集成冒烟（手工 5 步）

> 本步专做"Mock e2e 不能覆盖"的事：seed 真切换到 graphrag_tuned 后 03 步实际产物的差异。

**前置：** infra docker compose 启动；后端含 Phase 4.5 改动；至少有一个 build run 已完成 02 步 ≥ 1 条审阅，且**已经触发过自动调优**生成了 prompt-tune cache。

- [ ] **Step 1：seed-availability 端点返回正确状态**

DevTools：
```javascript
fetch('/api/v1/knowledge-base-build-runs/<BID>/seed-availability', {
  headers: { Authorization: 'Bearer ' + localStorage.token }
}).then(r => r.json()).then(console.log)
```

Expected：
- `data.options` 含 3 个：system_default 必 available=true；graphrag_tuned 视该 build run 选材是否有 prompt-tune cache 为 true/false；history_draft 必 false。
- `data.currentSeed` 与 build run metadata 一致。

- [ ] **Step 2：seed=system_default 时 distilled 候选的 base_label 为"默认 GraphRAG Prompt"**

01 步选 system_default → 进 03 步触发候选生成。检查：
```bash
WS=/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/user_0/kb_<KB>/build_<BID>
grep "Base Prompt Note" $WS/prompt/candidates/schema_aware_directional_v2/prompt.txt
```

Expected：内含"默认 GraphRAG Prompt"作为底稿描述，**不含**"官方 auto_tuned Prompt"。

- [ ] **Step 3：seed=graphrag_tuned 时 distilled 候选的 base_label 为"官方 auto_tuned Prompt"**

01 步切到 graphrag_tuned（保证 prompt-tune cache 是 success）→ 03 步重新生成候选。检查：
```bash
grep "Base Prompt Note" $WS/prompt/candidates/schema_aware_directional_v2/prompt.txt
```

Expected：内含"官方 auto_tuned Prompt"。

- [ ] **Step 4：seed-info.json 真落盘 + 时区偏移正确**

```bash
cat $WS/prompt/candidates/seed-info.json
```

Expected：字段含 `seed`、`autoTunedPromptDir`、`generatedAt`、`buildRunId`；其中 `generatedAt` 形如 `"2026-05-17T12:34:56+08:00"`（含 `+08:00` 时区偏移），不是裸 `LocalDateTime` 格式。

- [ ] **Step 5：候选响应直接含 seed**

刚才点完"立即生成候选"后的 `POST /candidates` 响应里，每个候选 entry 应当含 `"seed":"system_default"` 或 `"graphrag_tuned"` 字段（不依赖 GET 路径读 seed-info.json）。

DevTools Network 面板检查 POST 响应 body：每个候选的 seed 字段非 null。

> 04 步评分透传 seed 的冒烟由 Phase 5 完成时一并验证（GET `/extraction-eval/report` 顶层 `seed` 字段）。

集成冒烟无代码改动，不 commit。

---

## Task 12：Playwright e2e 覆盖 seed 不可用 + 切换路径

新建 spec 覆盖 graphrag_tuned 灰显与 toast 行为。

**Files:**
- Create: `frontend/apps/admin-app/e2e/prompt-builder-seed.spec.js`
- 视情况复用 `e2e/helpers/prompt-builder-candidates.js`（Phase 4 已有），不再重复 helper。

- [ ] **Step 1：写 spec**

Create `frontend/apps/admin-app/e2e/prompt-builder-seed.spec.js`：

```javascript
import { test, expect } from '@playwright/test'

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1, userCode: 'A', username: 'admin', displayName: '管理员', roleCode: 'admin',
}

async function installSeedMocks(page, { availabilityState = 'graphrag_tuned_unavailable' } = {}) {
  const options = availabilityState === 'graphrag_tuned_available'
    ? [
        { key: 'system_default', available: true, reason: null, summary: '默认' },
        { key: 'graphrag_tuned', available: true, reason: null, summary: '可用' },
        { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
      ]
    : [
        { key: 'system_default', available: true, reason: null, summary: '默认' },
        { key: 'graphrag_tuned', available: false, reason: 'auto_tuned_not_started', summary: '未触发' },
        { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
      ]

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(API_PREFIX, '')
    const method = route.request().method()
    if (method === 'OPTIONS') {
      return route.fulfill({ status: 204, headers: cors() })
    }
    if (path === '/auth/me') {
      return reply(route, 200, { code: 200, data: ADMIN_USER })
    }
    if (path === '/knowledge-base-build-runs/18') {
      return reply(route, 200, { code: 200, data: { id: 18, kbId: 5, courseId: 'crs-1', buildMetadata: '{}' } })
    }
    if (path === '/knowledge-base-build-runs/18/seed-availability') {
      return reply(route, 200, { code: 200, data: { currentSeed: null, options } })
    }
    if (path === '/knowledge-base-build-runs/18/custom-prompt-draft' && method === 'PUT') {
      return reply(route, 200, { code: 200, data: null })
    }
    return reply(route, 500, { code: 5000, message: `未配置 mock: ${method} ${path}` })
  })
}

function reply(route, status, body) {
  return route.fulfill({
    status,
    headers: { ...cors(), 'content-type': 'application/json' },
    body: JSON.stringify({ ...body, timestamp: new Date().toISOString() }),
  })
}

function cors() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'access-control-allow-headers': '*',
  }
}

test.describe('01 步 seed 可用性', () => {

  test('graphrag_tuned 不可用时显示禁用样式 + tooltip', async ({ page }) => {
    await page.setViewportSize({ width: 1980, height: 720 })
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_unavailable' })

    await page.goto('/prompt-builder?buildRunId=18&step=seed')

    // 找到 graphrag_tuned 卡片，验证 data-disabled
    const card = page.locator('.seed-card', { hasText: '沿用自动调优版' })
    await expect(card).toHaveAttribute('data-disabled', 'true')
    await expect(card).toHaveAttribute('title', /先在.*触发自动调优/)
  })

  test('graphrag_tuned 可用时点击会触发 PUT 写入 draft', async ({ page }) => {
    await page.setViewportSize({ width: 1980, height: 720 })
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_available' })

    await page.goto('/prompt-builder?buildRunId=18&step=seed')

    const putReq = page.waitForRequest((req) =>
      req.url().endsWith('/custom-prompt-draft') && req.method() === 'PUT'
    )

    const card = page.locator('.seed-card', { hasText: '沿用自动调优版' })
    await expect(card).toHaveAttribute('data-disabled', 'false')
    await card.click()

    await putReq
    // 卡片切到 selected
    await expect(card).toHaveAttribute('data-selected', 'true')
  })

  test('history_draft 始终不可用', async ({ page }) => {
    await page.setViewportSize({ width: 1980, height: 720 })
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_available' })
    await page.goto('/prompt-builder?buildRunId=18&step=seed')

    const card = page.locator('.seed-card', { hasText: '我的历史草稿' })
    await expect(card).toHaveAttribute('data-disabled', 'true')
  })
})
```

- [ ] **Step 2：跑 e2e**

```bash
cd frontend/apps/admin-app && pnpm test:e2e prompt-builder-seed 2>&1 | tail -10
```

Expected：3 个测试 PASS。

> **注意**：实际 mock 数据中如果 PromptBuilderPage 进入时还会请求其它 API（如 build run 详情、用户偏好等），这里的 helper 比较精简，可能需要根据实际报错补 mock。**这是预期的迭代**，按 Playwright 报错信息逐条加 mock。

- [ ] **Step 3：commit**

```bash
git add frontend/apps/admin-app/e2e/prompt-builder-seed.spec.js
git commit -m "test(prompt-builder): e2e 覆盖 01 步 seed 可用性切换 (Phase 4.5)"
```

---

## Task 13：联合验证 + spec 标记完成

> 收尾：聚合 Task 11 真后端冒烟 + Task 12 mock e2e 的产出，确认 Phase 4.5 真正完结。

- [ ] **Step 1：跑全部测试套件**

后端：
```bash
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run|FAIL" | tail -5
```
Expected：所有 PASS。Phase 4.5 新增的测试（SeedInfoStoreTest / CandidateGenerationOrchestratorSeedTest / CandidateServiceSeedTest / CandidateManifestReaderSeedTest / SeedAvailabilityServiceTest）全部 PASS。

前端单测：
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```
Expected：所有 PASS。Phase 4.5 新增 2 个 API 单测全部 PASS。

前端 e2e：
```bash
cd frontend/apps/admin-app && pnpm test:e2e 2>&1 | tail -8
```
Expected：原有 e2e 仍 PASS + Phase 4.5 新加 3 个 PASS。

- [ ] **Step 2：自检清单走一遍**

打开 plan 末尾的"自检清单"，逐项打勾。任一项未达成停止合并。

- [ ] **Step 3：spec 标记 ✅ + 增补 Phase 4.5 段**

修改 `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md`：

定位"已完成阶段"列表的末尾（Phase 2c-pre 之后），插入：

```markdown
- **Phase 4.5（✅）：01 步种子分流真正影响 03 候选生成（仅覆盖 01-03 步）**
  - 新增 `GET /knowledge-base-build-runs/{id}/seed-availability` 端点 + `SeedAvailabilityService`，
    01 步据此决定 `graphrag_tuned` 是否可点；不可用时附 tooltip 引导回知识库构建向导触发自动调优。
  - 01 步选 seed 时立即 PUT customPromptDraft（仅 seed 子字段），让 03 步后端能读到正确的种子。
  - `CandidateService.generate` 解析 build run metadata 取 seed → `CandidateGenerationOrchestrator.run`
    接受 `BaseOverride`：`system_default` 强制 fallback 到 default 分支，`graphrag_tuned` 指向
    prompt-tune cache 命中目录，`null` 走 Phase 4 兼容路径。
  - 候选生成结果 manifest 旁路文件 `seed-info.json` 落盘；`CandidateManifestReader` 注入
    `CandidateResponse.seed` 字段；前端 03 候选卡片 + 05 来源记录均展示 seed。
  - 4109 错误码：seed=graphrag_tuned 但 prompt-tune cache 失效时拒绝候选生成，前端提示用户重选种子。
  - **04 步评分对 seed 的感知（评分表 seed 列、报告透传）已移交 Phase 5 落地，本期不实施。**
  - 测试：后端 N 测试 PASS，前端 2 个 API 单测 + 3 个 e2e PASS。
```

同时把 spec § "01 选模板（沿用现状）"段改为：

> **Phase 4.5 已落地**：3 张种子卡的可用状态由 `GET /seed-availability` 决定；
> graphrag_tuned 当且仅当本次 build run 选材已有自动调优产物时可选；
> history_draft 始终灰显（Phase 6 落地）。

- [ ] **Step 4：commit**

```bash
git add docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md
git commit -m "docs(spec): Phase 4.5 标记完成 + 01 步 spec 段补充 (Phase 4.5)"
```

- [ ] **Step 5：push**

```bash
git push origin feature/prompt-confirmation-step
```

---

## 自检清单

> 实施前主会话过一遍这个清单，对应不上的项停止执行重新调整计划。

### 一致性自检

- [ ] **A1**：seed 取值在所有 Task 中拼写一致：`system_default` / `graphrag_tuned` / `history_draft`。
- [ ] **A2**：错误码 `SEED_AUTO_TUNED_UNAVAILABLE(4109)` 在所有 Task 中数字一致，未与现有码冲突。
- [ ] **A3**：seed-info.json 路径在 Task 2 / 4 / 5 / 11 中一致：`<workspace>/prompt/candidates/seed-info.json`。
- [ ] **A4**：脚本 CLI 参数 `--auto_tuned_prompt_dir` 在 Task 0 / 3 / 11 中一致。
- [ ] **A5**：`SeedAvailabilityResponse.SeedOption.reason` 取值集合在 Task 6 service / Task 9 前端 disabledReasonFor / Task 12 e2e 一致：`auto_tuned_not_started` / `auto_tuned_running` / `auto_tuned_pending` / `auto_tuned_failed` / `phase_6_not_implemented` / `evaluation_failed`。
- [ ] **A6**：`seed=null` 语义在 Goal / 决策 2 / 风险 1 / Task 4 实现与测试中一致——表示"历史兼容模式"，传 `baseOverride=null` 让 orchestrator 不附加 `--auto_tuned_prompt_dir` 参数。**禁止**任何文档/代码把 null 等同 system_default。
- [ ] **A7**：cache 可用性判定单一口径——SeedAvailabilityService 与 CandidateService.resolveBaseOverride 都通过 `PromptTuneService.probeBySelection(...)` 判 success；CandidateService 仅在 probe success 后取 `findReadyByCacheKey` 拿目录路径。
- [ ] **A8**：`generatedAt` 时间类型在 SeedInfo entity / write 实现 / write 测试三处都是 `OffsetDateTime`（不是 `LocalDateTime`），且 Jackson 配置含 `JavaTimeModule` + 关闭 `WRITE_DATES_AS_TIMESTAMPS`。
- [ ] **A9**：seed-info.json 写盘失败语义——POST 路径不阻断、响应已注入 seed；GET 路径降级到候选 seed=null。Task 4 实现与测试覆盖此一致性。

### 上下文自检

- [ ] **B1**：Phase 4 已落地的 `CandidateService` / `CandidateGenerationOrchestrator` / `CandidateManifestReader` 均可改造（不被其它无关 caller 强依赖）。
- [ ] **B2**：`PromptTuneService.findReadyByCacheKey` 与 `probeBySelection` 已存在，方法签名稳定。
- [ ] **B3**：`PromptTuneRuns.candidateDir` 形如 `prompt-tune-cache/<cacheKey>/run_<id>`（相对路径，目录），`workspaceService.resolve(...)` 解析为绝对目录路径，目录下含 `extract_graph.txt`。Task 0 Step 7 已校验此契约。
- [ ] **B4**：`KnowledgeBaseBuildRunService.saveCustomPromptDraft` 接受嵌套 JSON / 部分字段写入；如果只接受全量替换，前端 Task 9 PUT 需要先 GET 再合并。
- [ ] **B5**：Phase 4.5 不修改 `prompt_tune_extraction_eval_runs` 表与 Phase 5 任何 Service / DTO；该表的 seed 列由 Phase 5 一并落地。

### Spec 覆盖自检

- [ ] **C1**：spec § 01 选模板段更新明确"3 张卡片可用状态由后端决定"。
- [ ] **C2**：spec § 已识别风险 #3「候选译名硬编码」之外补充"seed 与候选底板联动已在 Phase 4.5 落地"。
- [ ] **C3**：spec § 进度门控段不强制写"01 步必选 seed 才能进 02"——本期保持"未选 seed 时按历史兼容模式处理（baseOverride=null）"，与现有 user journey 不冲突。

### 依赖自检

- [ ] **D1**：Phase 4.5 不修改 `generate_candidate_prompts.py` 或其它 Python 脚本。
- [ ] **D2**：Phase 4.5 不修改 `PromptTuneWorker` / `BuildRunPromptMaterializer`。
- [ ] **D3**：Phase 4.5 改造 `CandidateService` 时**保持 Phase 4 测试不破坏**（Phase 4 测试需要小改 mock 期望）。
- [ ] **D4**：Phase 4.5 不依赖 Phase 6 任何工件。
- [ ] **D5**：Phase 4.5 不修改 Phase 5 的 `prompt_tune_extraction_eval_runs` 表 / Service / DTO；evalRun 写入 seed 快照、Report 透传 seed 由 Phase 5 一并落地。

### 测试覆盖自检

- [ ] **E1**：每个新增类（SeedInfoStore / SeedAvailabilityService）至少 3 个单测；SeedInfoStore 测试覆盖 OffsetDateTime 时区往返。
- [ ] **E2**：CandidateGenerationOrchestrator 的 BaseOverride 三种取值（systemDefault / graphragTuned / null）均有覆盖。
- [ ] **E3**：CandidateService 6 条 seed 路径覆盖：`system_default` / `graphrag_tuned + probe success + cache 命中` / `graphrag_tuned + probe success + cache 未命中（4109）` / `graphrag_tuned + probe 非 success（4109）` / `null + 兼容路径` / `seed-info 写盘失败但响应仍含 seed`。
- [ ] **E4**：CandidateManifestReader 测试覆盖 seed 注入与 seed-info.json 缺失两条路径。
- [ ] **E5**：SeedAvailabilityService 核心 reason 路径覆盖：`success` / `not_started` / `running` / `pending` / `failed` / `evaluation_failed` / `phase_6_not_implemented` / `currentSeed 回填`。
- [ ] **E6**：前端 e2e 覆盖 graphrag_tuned 不可用 / 可用 / history_draft 灰显三条路径。
- [ ] **E7**：真后端集成冒烟 4 步：seed-availability 端点 / system_default 候选 base_label / graphrag_tuned 候选 base_label / seed-info.json 落盘（OffsetDateTime 含 +08:00 偏移）。
- [ ] **E8**：CandidateService 的"null seed ≠ system_default"行为有显式断言（E3 中第 5 条）；不允许两个语义被合并到同一条测试断言中。

---

## Phase 4.5 完成判定

- 后端：现有所有测试 PASS + Phase 4.5 新增测试全部 PASS（不写死数字）
- 前端单测：现有所有测试 PASS + 2 个新 API 测试 PASS
- 前端 e2e：现有套件 PASS + Phase 4.5 新加 3 个 PASS
- 真后端集成冒烟 5 步全过（seed-availability / 候选 base_label 切换 / seed-info.json 含时区偏移 / 候选响应含 seed）
- 一致性 / 上下文 / spec 覆盖 / 依赖 / 测试 5 类自检全过（特别注意 A6 / A7 / A8 / A9 这四条新增项）
- 所有 commit message 含 `(Phase 4.5)` 标记
- spec 已增补 Phase 4.5 段并标记 ✅
- **本期范围仅覆盖 01-03 步业务逻辑**；前端展示延伸到 05 步来源记录；04 步评分对 seed 的感知由 Phase 5 落地

---

## 与 Phase 4 / Phase 5 的关系

- **Phase 4 → 4.5**：Phase 4 已经把 03 候选生成接到了真实 API，本期补"seed 真正影响候选底板"。计划中的所有改动都建立在 Phase 4 工件之上，且**不破坏 Phase 4 测试**（仅需小改 mock 期望）。
- **Phase 4.5 → Phase 5**：本期完全不改 Phase 5 工件。原计划中 Task 7（`prompt_tune_extraction_eval_runs.seed` 列、Service 写入快照、Report 透传）已**整体移交 Phase 5 实施计划**，作为 Phase 5 的内部任务一并落地，避免跨阶段改动带来 merge 冲突。
- **Phase 4.5 → Phase 6**：Phase 6 落地 history_draft 时，会替换本期"始终 unavailable"的逻辑；其它接口（seed-availability / customPromptDraft 写入）保持不动。
