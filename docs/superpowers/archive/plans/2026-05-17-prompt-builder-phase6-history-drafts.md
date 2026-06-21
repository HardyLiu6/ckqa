# Prompt Builder Phase 6 实施计划：05 步历史草稿入库 + 01 步种子打通 + PromptDisplay raw prismjs 高亮

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把"05 步保存"从 mock 转为真实 `POST /finalize`（写 customPromptDraft + 可选入库 prompt_drafts），把"01 步历史草稿种子"从硬禁用转为按 prompt_drafts 表实际记录数动态启用，并把 `<PromptDisplay>` raw 模式的 regex 自实现高亮换成 prismjs（spec § 第三方依赖选型 + 风险 #5 容错单测）。

**Architecture:** 后端 Phase 2a 已落地 entity / mapper / service / DTO 占位，Phase 6 仅补 service 业务方法 + 控制器 501 占位替换 + JSON 序列化校验。前端 Phase 1e 已落地 SaveStep 表单与 mock，Phase 6 替换 `handleSave` 与 `MOCK_HISTORY_DRAFTS` 为真实 API。同时把 SeedAvailability 的 `history_draft` 一刀切禁用改为按 kb 维度查 prompt_drafts.count，>0 即可用。

**Tech Stack:** Spring Boot 4 + MyBatis-Plus（后端）、Vue 3.5 + Element Plus 2.13 + Pinia + node:test（前端）、Playwright（e2e）、prismjs 1.29（已在 package.json，但当前 PromptDisplayRaw 未实际引用）、markdown-it 14（已在 package.json，本期不需要）。

---

## 范围与非范围

### 包含

1. **后端**：
   - `POST /api/v1/knowledge-base-build-runs/{id}/finalize` 端点真实化：把 04 步选定候选写入 `buildMetadata.customPromptDraft`（candidateId / promptsJson / compositeScore / sourceEvalRunId 快照）；当 `saveAsDraft=true` 时同时插一条 `prompt_drafts` 行。
   - `GET /api/v1/knowledge-bases/{kbId}/prompt-drafts` 端点真实化：返回该 kb 下按 created_at 倒序的**轻量摘要列表**（`PromptDraftSummaryResponse`，不含 promptsJson 大字段）；如未来需要正文，留 `GET /knowledge-bases/{kbId}/prompt-drafts/{id}` 详情接口给 Phase 7+。本期 01 步抽屉只用 name / score / source build / createdAt / description 这些短字段做选择决策，无需正文。
   - `SeedAvailabilityService.buildHistoryDraft` 改造：注入 `PromptDraftsService.countByKnowledgeBaseId`，count > 0 时 available=true / reason=null / summary 含数量；count = 0 时 available=false / reason="no_history_draft" / summary 友好文案。
   - 错误码：4110 `EXTRACTION_EVAL_NOT_SUCCESS`（finalize 时 04 评分尚未 success → 拒绝），4111 `INVALID_FINALIZE_CANDIDATE`（candidateId 不在评分报告 candidates 中）。

2. **前端**：
   - `prompt-tune-pipeline.js` 加 `finalizePrompt(buildRunId, payload)` / `listPromptDrafts(kbId)` 两个 API 客户端函数。
   - `PromptBuilderPage.vue`：`handleSave` 真发 `POST /finalize`，根据 `saveMode` 决定 `saveAsDraft`；onMounted 调 `listPromptDrafts(kbId)` 填充 `historyDrafts` 数组（替代 `MOCK_HISTORY_DRAFTS`）；点击 history_draft 种子卡时打开抽屉选择具体草稿（如有 ≥2 条；只有 1 条直接选）。
   - 新组件 `PromptBuilderHistoryDraftDrawer.vue`：列表展示历史草稿（name / 综合分 chip / 来源构建 / created_at），点击某条后注入到 Page 状态：仅 `seed=history_draft` + `historyDraftId`（用于将来透传）+ 可选预填 `saveDraftName / saveDraftDescription`；**不**恢复 `selectedCandidateId`，并主动清空旧的 `selectedCandidateId / saveDraftNameTouched=false`，与 spec § "history_draft 仅是种子来源、不复用旧候选 ID"约束一致。
   - `PromptBuilderSeedStep.vue`：删掉硬编码的 `Phase 1e 开放` 文案，按 availability 动态显示数量；history_draft 卡片右上角加"共 N 条"角标。
   - `PromptDisplayRaw.vue`：换成 prismjs 高亮（自定义语言定义 prompt-tune，识别段落标记、占位、注释、关系箭头）；保留行号；保留 fallback 容错单测（spec § 风险 #5）。

3. **测试**：
   - 后端：`PromptDraftsServiceImplTest`（list 排除 prompts_json + list 排序 + count 两路径）、`FinalizePromptServiceTest`（finalize 8 条：仅本次构建完整快照断言 / 含历史草稿 / 04 未 success 拒绝 4110 / candidateId 非法 4111 / reportJson 损坏 5000 / promptDraftsService.save 返回 false 抛错并验证回滚 / seed 快照继承 / 缺省 saveAsDraft）、`SeedAvailabilityServiceTest` 扩 history_draft 两条路径（count=0 / count>0）、`PromptDraftListServiceTest` 2 条（list 投影不含 promptsJson + 空数组）。
   - 前端单测：`prompt-tune-pipeline-finalize.test.js`（POST /finalize × 2 模式 + 4110/4111 业务码）、`prompt-tune-pipeline-prompt-drafts.test.js`（GET /prompt-drafts 列表 + 空数组）、`prompt-display-parser.test.js`（spec § 风险 #5 段落 < 2 / 单段超长 → fallback raw）。
   - Playwright e2e：`prompt-builder-history-drafts.spec.js`（4 个 case：empty 态种子卡禁用 / many 态打开抽屉选草稿 / 仅本次构建模式 finalize / 含历史草稿入库模式 finalize）。

### 不包含

- 历史草稿的"删除 / 改名 / 批量管理"。spec § 不做"知识库默认 prompt 全局固化"；草稿删除属于运维能力，留 Phase 7+。
- 跨知识库复用历史草稿。`prompt_drafts.knowledge_base_id` 是查询主索引，跨 kb 复用需要单独的"草稿迁移"工作流。
- markdown-it 二次落地。当前 PromptDisplay 自实现 parser 已能覆盖 prompt 段落格式（`-Section-` 标记），无需改造为 markdown-it；02 步样本原文展示也未在 Phase 2 / 4 / 5 中落地为富文本，markdown-it 留给 Phase 7+ 真正需要时再用。
- "保存范围"扩展到第三种模式（如"保存到全局默认"）。spec 明确限制为两种模式，全局固化属于 CLI / 管理界面。
- 历史草稿种子的"一键克隆 prompts.extract_graph.content 到 customPromptDraft"。本期 01 步选 history_draft 后只是 PUT seed 到 build run 标记意图；真正使用历史草稿 prompts 需要走完整的 02→03→04→05 链路（与 system_default / graphrag_tuned 完全等同）。这与 Phase 4.5 的 seed 透传语义一致。

---

## 关键架构决策

### 决策 1：finalize 写两处，prompt_drafts 与 customPromptDraft 解耦

**理由：** spec § 05 步明确 `customPromptDraft` 是 build run 维度的"本次构建已选定"，`prompt_drafts` 是 kb 维度的"历史草稿池"。两处的生命周期不同：build run 删除时不联动 prompt_drafts（外键 RESTRICT）；prompt_drafts 删除也不影响 build run 已固化的 customPromptDraft。

**实现：**
- `FinalizePromptService.finalize(buildRunId, request)`：
  1. 校验 04 评分 run：`evalRunsService.findLatestByBuildRunId(buildRunId)` 必须存在且 `status=success`，否则抛 4110。
  2. 校验 candidateId：必须在 evalRun 的 reportJson `all_candidates_ranked` 中，否则抛 4111；reportJson 解析失败（Jackson 抛错）抛 5000 INTERNAL_ERROR（与"业务入参错误"区分）。
  3. 从 reportJson 取该 candidate 的 compositeScore + 对应 candidate prompt 文件全文（`workspace/prompt/candidates/<candidateId>/prompt.txt`）。
  4. **直接写 buildMetadata（不复用 saveCustomPromptDraft）**：FinalizePromptService 用 ObjectMapper 解析 buildRun.buildMetadata，定位 `customPromptDraft` 子节点，整体替换为完整 finalize 快照——
     - `seed`：保留旧值（缺失时回退 `system_default`）
     - `selectedCandidateId`：本次选定的 candidateId
     - `prompts.extract_graph.content`：候选 prompt 全文（保留旧 modifiedAt / baseHash 字段语义不变）
     - `compositeScore`：4 位小数 BigDecimal
     - `sourceEvalRunId`：evalRun.id
     - `finalizedAt`：`OffsetDateTime.now()` 序列化为 ISO-8601 含偏移（与 Phase 4.5 seed-info.json 风格一致）
     - `updatedAt` / `seedSnapshotAt`：保留旧值，避免覆盖 Phase 4.5 的语义
     - 其他 stage 键（exportConfirmed / graphInputConfirmed / promptStrategy / promptConfirmed 等）原样保留，**只替换 customPromptDraft**。
  5. 当 `saveAsDraft=true`：同事务插一条 prompt_drafts，name / description 来自 request；`prompts_json` 为 `{"extract_graph": "<full text>"}` 序列化结果；`source_build_run_id` = buildRunId；**`promptDraftsService.save(draft)` 返回 false 时立即抛 5000 让 @Transactional 回滚刚才的 buildRun 写入**。
  6. 整体 @Transactional，prompt_drafts 写失败回滚 customPromptDraft 写入（依赖 Spring TransactionManager；service 单测验证抛错路径）。
- 不在 finalize 中推进 buildRun.currentStage（保持在 prompt 阶段或 done）；用户回到知识库构建向导后才决定是否触发 04 索引或重新进入 04 评分。

### 决策 2：SeedAvailability history_draft 由 count 驱动，不预检具体草稿

**理由：** spec § 01 步要求 history_draft "在 05 步入库后才会有内容"。最简语义就是"该 kb 下 prompt_drafts.count > 0 即可用"。具体哪条草稿、哪个 candidateId 由前端在用户点 history_draft 种子卡时再拉列表决定（避免 availability 接口拖列表数据）。

**实现：**
- `PromptDraftsService` 加 `countByKnowledgeBaseId(Long)` 方法。
- `SeedAvailabilityService` 注入 `PromptDraftsService` + KnowledgeBaseBuildRunsService（已有），在 `buildHistoryDraft(buildRun)` 中：
  - count = 0 → available=false / reason=`no_history_draft` / summary="本知识库暂无历史草稿，05 步保存并入库后会出现在这里"。
  - count > 0 → available=true / reason=null / summary="共 N 条历史草稿可选，点击进入选择"。

### 决策 3：history_draft 选择走抽屉而非下拉

**理由：** spec § 01 步原型仅示意"我的历史草稿"卡，没规定 ≥2 条时如何选。下拉适合 3-5 条短列表；草稿可能含中文 description 与综合分 chip，下拉太挤。抽屉（420px 宽，与 03 候选预览抽屉同款样式）能展示 name + score + 来源构建 + 时间 + description 截断行，更接近 Element Plus 一致风格。

**实现：**
- 新建 `PromptBuilderHistoryDraftDrawer.vue`，props=`drafts: PromptDraftResponse[]`，emit=`select-draft(draft)` 与 `update:modelValue`（v-model 双向控制开闭）。
- `PromptBuilderPage.handleSelectSeed('history_draft')`：
  - count = 0：直接弹 toast"暂无可选草稿，请先在 05 步保存并入库"，不动 seed。
  - count = 1：直接选中那条草稿，触发 `loadHistoryDraft(draft)`。
  - count ≥ 2：打开 `PromptBuilderHistoryDraftDrawer`，由用户选具体一条。
- `loadHistoryDraft(draft)`：
  - PUT `customPromptDraft.seed=history_draft`（仅 seed 子字段，沿用 Phase 4.5 的部分更新语义）。
  - 把 draft.id 写到 PromptBuilderPage 的 `historyDraftId` ref，用于后续 03 步生成候选时透传（**Phase 6 不实现透传到 candidate 生成**，本期仅 PUT seed + 前端展示，与 system_default 一样进入 02→03 完整链路）。
  - 用户继续走 02→03→04→05，最终 finalize 时新候选会写入新的 customPromptDraft；如果再次保存为草稿，prompt_drafts 中会插入新的一行。

### 决策 4：PromptDisplayRaw 改用 prismjs

**理由：** spec § 第三方依赖选型与 § 风险 #5 都明确 raw 模式应该用 prismjs。当前自实现 regex 仅识别段落标题，不识别占位符 / 关系箭头，且未来 prompt 模板格式变化需要改 regex；prismjs 的语言扩展（`Prism.languages.extend`）支持自定义 token，更易演化。bundle 大小可接受（~25KB + 1 个 token CSS）。

**实现：**
- 新建 `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-prism.js`：定义 `prompt-tune` 语言，token 含 `section`（`-Section-` 标题行）/ `placeholder`（`{var}`）/ `arrow`（`->`）/ `comment`（`#`）/ `keyword`（实体类型黑名单 hardcode 表）；加载 `prismjs/themes/prism-tomorrow.css` 的暗色主题。
- 改造 `PromptDisplayRaw.vue`：用 `Prism.highlight(text, Prism.languages['prompt-tune'], 'prompt-tune')` 整体高亮，保留行号渲染。
- 容错单测：在 `prompt-display-parser.test.js` 新增 2 条 case：sections=1 / 单段 > 4000 字符 → `parsePromptSections` 仍返非空数组但 PromptDisplay 的 `fallbackToRaw` computed 标记 true，UI 自动切到 raw。

---

## 已识别风险

1. **finalize 与 04 评分 run 的强一致性**：finalize 要求 evalRun.status=success，但用户可能在 04 评分跑完后中途切走、04 评分被新一轮 cancel 顶替。决策：finalize 取的是 `findLatestByBuildRunId`，永远拿"最新一条"，如果是 cancelled / failed 直接拒绝。前端 04→05 切换时已经强制要求选定候选，所以这条路径正常用户走不到 4110。
2. **prompts.extract_graph.content 体积**：候选 prompt 文件可达 30 KB，customPromptDraft 仍写在 build run.buildMetadata（json 列），prompt_drafts.prompts_json 同样是 json 列。两处合计 60 KB 以内，MySQL json 列上限远高于此，无风险。
3. **prompt_drafts 历史增长**：每次 finalize+saveAsDraft 都加一行，长期可能膨胀。本期不做"保留最近 N 条"清理，留 Phase 7+ 与 build run gc 一并加自动清理策略。前端 GET 列表本身只按 created_at 倒序展示，UI 上由用户自己识别"哪条是最新"，不分页（spec 没要求分页；超过 20 条体验不佳时再加 limit）。
4. **prismjs SSR 兼容**：admin-app 是纯 SPA，无 SSR，prismjs 直接 import 即可。但 vite 构建时 `prismjs/components/prism-*.js` 不能用 `import` 语句直接引入（它们 attach 到全局 Prism）。决策：本期不依赖任何内置语言，`prompt-tune` 语言完全自定义，避开 components 加载坑。
5. **history_draft seed PUT 时 04 评分残留**：若 build run 已经跑过 04 评分到 success，用户在 01 步切回 history_draft 种子，04 状态仍保留。决策：与 Phase 4.5 一致，仅 PUT seed 子字段，不清理 04 / 03 数据；Phase 4.5 已落地的 03 stale 提示横幅会引导用户重新走 03。
6. **listPromptDrafts 大字段（已落地缓解）**：单条 prompts_json 可达 30 KB，N 条时若全量返回列表响应可能 600 KB+。Phase 6 主动拆分：列表接口 `GET /knowledge-bases/{kbId}/prompt-drafts` 返回 `PromptDraftSummaryResponse`（不含 promptsJson）；如需正文走 Phase 7+ 新增的 `GET /knowledge-bases/{kbId}/prompt-drafts/{id}`。本期 UI 只用列表的 name / score / source build / createdAt / description 字段，不需要正文。
7. **finalize 不写当前 build run.qaStatus / currentStage 推进**：与现有 `confirmPrompt` / `saveCustomPromptDraft` 行为一致，05 步保存只动 metadata，不改 status / stage。这是有意为之的：05 是用户的"决定保存"动作，不等于"开始索引"，索引由后续在知识库构建向导触发。

---

## 文件结构（新增 / 修改）

### 后端（Java）

- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`（加 4110 / 4111 错误码）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java`（**去掉 promptsJson 字段**：本期作为列表摘要使用，避免 600 KB+ 列表响应；详情接口留 Phase 7+ 时新建 PromptDraftDetailResponse）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java`（加 `countByKnowledgeBaseId`）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java`（实现 `countByKnowledgeBaseId`；`listByKnowledgeBaseId` 改为只 select 摘要列，排除 prompts_json）
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImplTest.java`
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`（注入 PromptDraftsService，按 count 切换 buildHistoryDraft）
- 修改 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java`（扩 history_draft 两条路径）
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/FinalizePromptService.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/FinalizePromptServiceTest.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptDraftListService.java`（GET /prompt-drafts 投影成 PromptDraftResponse）
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/PromptDraftListServiceTest.java`
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`（替换 finalize / listPromptDrafts 两个 501 占位）
- 修改 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/PromptTunePipelineControllerWebMvcTest.java`（如不存在则保留为后续 Phase；本期 service 单测已覆盖控制器路径，WebMvc 测试可选）

### 前端

- 修改 `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`（新增 `finalizePrompt` / `listPromptDrafts`，覆盖现有 `listPromptDrafts` 桩 / `finalizePrompt` 桩）
- 测试 `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-finalize.test.js`
- 测试 `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-prompt-drafts.test.js`
- 修改 `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（去 mock + 真实 finalize / listPromptDrafts；history_draft 选择路径）
- 新增 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderHistoryDraftDrawer.vue`
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`（按 availability 动态显示数量；删 Phase 1e 文案）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`（去 MOCK_CANDIDATES / MOCK_SCORING_REPORT 改用真实 evalRun.report 数据，与 04 步 done 阶段共享 selectedCandidate 信息）
- 新增 `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-prism.js`（自定义 prismjs 语言）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue`（用 prismjs 高亮）
- 测试 `frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/prompt-display-parser.test.js`（如不存在则新建，补 fallback 容错两条 case）
- 新增 `frontend/apps/admin-app/e2e/helpers/prompt-builder-history-drafts.js`
- 新增 `frontend/apps/admin-app/e2e/prompt-builder-history-drafts.spec.js`

### Spec / 文档

- 修改 `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md`（Phase 6 标记 ✅ + Phase 4.5 注释中"history_draft 始终灰显（Phase 6 落地）"段更新为 Phase 6 已落地说明 + 风险 #5 备注）

---

## Task 0：契约自检

> 在写代码前确认所有外部依赖契约稳定，避免后续 Task 因假设错误返工。

**Files:**
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptDrafts.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/FinalizePromptRequest.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`（saveCustomPromptDraft 仅作语义参考，FinalizePromptService 本期不复用）
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java`（reportJson 字段名约定）
- Read: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（handleSave / loadHistoryDraft 现状）
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`（saveMode 字段 / payload 形态）

- [ ] **Step 1：确认 prompt_drafts 表已在 DB 中**

Run:
```bash
docker exec -i mysql mysql -uroot -p123456 ocqa -e "DESC prompt_drafts;"
```

Expected：含 `id / knowledge_base_id / name / description / seed / candidate_id / prompts_json / source_build_run_id / composite_score / created_at / updated_at` 11 列。`prompts_json` 是 `json` 类型；`composite_score` 是 `decimal(5,4)`；`seed` 是 `varchar(64)`。

- [ ] **Step 2：确认错误码命名空间空闲**

Run:
```bash
grep -E "4110|4111" backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
```

Expected 输出：无（这 2 个码未占用）。

- [ ] **Step 3：确认 FinalizePromptRequest DTO 已存在并字段对得上**

Run:
```bash
grep -nE "candidateId|saveAsDraft|draftName|draftDescription" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/FinalizePromptRequest.java
```

Expected：4 个字段都在；`candidateId` 标 `@NotBlank`，`draftName` 标 `@Size(max = 128)`。

- [ ] **Step 4：确认 PromptDraftResponse DTO 当前字段（Task 1.5 会去掉 promptsJson）**

Run:
```bash
grep -nE "private final" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java
```

Expected：当前字段含 `id / knowledgeBaseId / name / description / seed / candidateId / promptsJson / sourceBuildRunId / compositeScore / createdAt / updatedAt` 11 项。**Phase 6 把 PromptDraftResponse 重新定义为列表摘要语义，要在 Task 1.5 中删除 `promptsJson` 字段**（避免列表响应携带 30 KB × N 条正文）。

- [ ] **Step 5：确认 ExtractionEvalReportAssembler 解析的 reportJson 内 candidate 字段名**

Run:
```bash
grep -nE "all_candidates_ranked|composite_score|stringField|candidate" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java | head -10
```

Expected：confirms `all_candidates_ranked` 是数组路径，每条 entry 含 `candidate`（字符串 ID）和 `composite_score`（数值）。FinalizePromptService 复用相同 jackson 解析逻辑。

- [ ] **Step 6：自检通过承诺**

Steps 1-5 全部 PASS → 进入 Task 1。任一步与计划假设不符 → 停止执行重新调整。

**Step 7（手工）：无 commit**

---

## Task 1：错误码 + PromptDraftsService.count + 列表排除 prompts_json + DTO 摘要化

加 4110 / 4111 错误码；给 PromptDraftsService 添加 count；同时把列表查询改为不带 prompts_json 列（避免 listPromptDrafts 列表响应携带 30 KB × N 条正文，参见审阅意见 #4）；PromptDraftResponse 同步去掉 promptsJson 字段。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImplTest.java`

- [ ] **Step 1：加错误码**

定位 `INVALID_EVAL_CANDIDATE_SELECTION(4108, ...)` 段落，紧接其后插入：

**oldStr**：
```java
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),
```

**newStr**：
```java
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),

    /**
     * 05 步 finalize 时 04 评分尚未成功（pending / running / cancelled / failed）。
     */
    EXTRACTION_EVAL_NOT_SUCCESS(4110, "评分尚未成功，无法保存为草稿"),

    /**
     * 05 步 finalize 时传入的 candidateId 不在评分报告 candidates 中。
     */
    INVALID_FINALIZE_CANDIDATE(4111, "选定候选 ID 不在评分报告的候选清单中"),
```

- [ ] **Step 2：扩 PromptDraftsService 接口**

定位 `List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);` 之后插入：

**oldStr**：
```java
    /**
     * 按知识库查询历史草稿，按创建时间倒序。
     */
    List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);
}
```

**newStr**：
```java
    /**
     * 按知识库查询历史草稿（摘要列），按创建时间倒序。
     * <p><b>不</b>带 {@code prompts_json} 列：列表场景只用 name / score / source build / createdAt
     * 等短字段做选择决策；如需正文走未来的详情接口（Phase 7+ 落地）。</p>
     */
    List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 计数：该 kb 下历史草稿条数。
     * <p>用于 {@code SeedAvailabilityService} 决定 history_draft 种子卡是否可点。</p>
     */
    long countByKnowledgeBaseId(Long knowledgeBaseId);
}
```

- [ ] **Step 3：写失败的 ServiceImpl 单测**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImplTest.java`：

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.mapper.PromptDraftsMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptDraftsServiceImplTest {

    @Test
    void listByKnowledgeBaseIdInvokesSelectListWithExpectedWrapper() {
        // 验证：调 selectList 时 wrapper 含 eq(knowledgeBaseId) + orderByDesc(createdAt) + 排除 promptsJson 列
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);

        AtomicReference<Wrapper<PromptDrafts>> captured = new AtomicReference<>();
        when(mapper.selectList(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return List.of();
        });

        service.listByKnowledgeBaseId(7L);

        assertThat(captured.get()).isInstanceOf(LambdaQueryWrapper.class);
        // 排序断言：SQL 片段含 ORDER BY created_at DESC
        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<PromptDrafts> wrapper = (LambdaQueryWrapper<PromptDrafts>) captured.get();
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("created_at DESC");
        // 排除大字段断言：select 段不含 prompts_json
        String selectSql = wrapper.getSqlSelect();
        if (selectSql != null) {
            assertThat(selectSql).doesNotContain("prompts_json");
        }
    }

    @Test
    void listByKnowledgeBaseIdReturnsRowsInRepositoryOrder() {
        // 两条记录 mock 倒序返回，验证 service 透传 mapper 顺序，不做内部重排
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);

        PromptDrafts newer = new PromptDrafts();
        newer.setId(2L);
        newer.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 17, 10, 0));
        PromptDrafts older = new PromptDrafts();
        older.setId(1L);
        older.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 15, 10, 0));
        when(mapper.selectList(any())).thenReturn(List.of(newer, older));

        List<PromptDrafts> result = service.listByKnowledgeBaseId(7L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(1).getId()).isEqualTo(1L);
    }

    @Test
    void countByKnowledgeBaseIdReturnsCount() {
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectCount(any())).thenReturn(3L);

        assertThat(service.countByKnowledgeBaseId(7L)).isEqualTo(3L);
    }

    @Test
    void countByKnowledgeBaseIdReturnsZeroWhenNoDrafts() {
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectCount(any())).thenReturn(0L);

        assertThat(service.countByKnowledgeBaseId(99L)).isEqualTo(0L);
    }
}
```

- [ ] **Step 4：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=PromptDraftsServiceImplTest test 2>&1 | grep -E "Tests run|BUILD|FAIL|ERROR\b" | tail -5
```

Expected：编译失败 + 排序 / 列排除断言 FAIL（`countByKnowledgeBaseId` 接口未实现 + listByKnowledgeBaseId 还没 select 排除 prompts_json）。

- [ ] **Step 5：实现 count + listByKnowledgeBaseId 排除 prompts_json**

定位 `listByKnowledgeBaseId` 方法整体替换：

**oldStr**：
```java
    @Override
    public List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptDrafts::getCreatedAt);
        return list(wrapper);
    }
}
```

**newStr**：
```java
    @Override
    public List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId) {
        // 摘要查询：显式 select 列，排除 prompts_json（30 KB × N 条会让列表响应膨胀到 600 KB+）。
        // 详情接口留 Phase 7+ 时新增 GET /knowledge-bases/{kbId}/prompt-drafts/{id}。
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                PromptDrafts::getId,
                PromptDrafts::getKnowledgeBaseId,
                PromptDrafts::getName,
                PromptDrafts::getDescription,
                PromptDrafts::getSeed,
                PromptDrafts::getCandidateId,
                PromptDrafts::getSourceBuildRunId,
                PromptDrafts::getCompositeScore,
                PromptDrafts::getCreatedAt,
                PromptDrafts::getUpdatedAt
        );
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptDrafts::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public long countByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId);
        return count(wrapper);
    }
}
```

- [ ] **Step 6：把 PromptDraftResponse 改造为列表摘要语义（删 promptsJson）**

定位 `PromptDraftResponse.java`：

**oldStr**：
```java
    /** 多 key prompt 内容快照，JSON 字符串形态（前端按需解析）。 */
    private final String promptsJson;

    private final Long sourceBuildRunId;
```

**newStr**：
```java
    // 注意：Phase 6 把 PromptDraftResponse 重新定义为列表摘要语义。
    // promptsJson 字段已删除，避免列表响应携带 30 KB × N 条正文。
    // 详情接口将在 Phase 7+ 新增（GET /knowledge-bases/{kbId}/prompt-drafts/{id}）并定义 PromptDraftDetailResponse。

    private final Long sourceBuildRunId;
```

类级 Javadoc 同步：

**oldStr**：
```java
/**
 * GET /knowledge-bases/{kbId}/prompt-drafts 历史草稿响应。
 */
```

**newStr**：
```java
/**
 * GET /knowledge-bases/{kbId}/prompt-drafts 历史草稿<b>摘要</b>响应。
 * <p>本期作为列表场景使用，不含 {@code promptsJson} 正文（30 KB × N 条会让列表响应膨胀到 600 KB+）。
 * 如需读取草稿正文，留 Phase 7+ 新增详情接口承担。</p>
 */
```

- [ ] **Step 7：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=PromptDraftsServiceImplTest test 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

Expected：4 个测试 PASS。

- [ ] **Step 8：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImplTest.java
git commit -m "feat(prompt-builder): 加 4110/4111 错误码 + PromptDraftsService.count + 列表排除 prompts_json + PromptDraftResponse 摘要化 (Phase 6)"
```

---

## Task 2：SeedAvailabilityService 接 PromptDraftsService.count

把 history_draft 由硬编码 `available=false` 改为按 prompt_drafts 表 count 决定。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java`

- [ ] **Step 1：扩单测覆盖 history_draft 两条路径**

定位现有测试类，在 `class SeedAvailabilityServiceTest {` 内追加（前置：现有测试已 mock `KnowledgeBaseBuildRunsService` / `PromptTuneRunsService`，新增 `PromptDraftsService` mock 注入）：

**oldStr**：找现有 `@BeforeEach` setUp 段（具体内容以仓库为准）。

**newStr**：在 setUp 加 `PromptDraftsService promptDraftsService = mock(...)`，并在 service 构造调用处补传该 mock；新增两条测试方法：

```java
    @Test
    void historyDraftIsAvailableWhenCountGreaterThanZero() {
        // count > 0 → available=true / reason=null / summary 含数量
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L, 5L));
        when(promptDraftsService.countByKnowledgeBaseId(5L)).thenReturn(3L);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedOption history = response.getOptions().stream()
                .filter(o -> "history_draft".equals(o.getKey()))
                .findFirst().orElseThrow();

        assertThat(history.getAvailable()).isTrue();
        assertThat(history.getReason()).isNull();
        assertThat(history.getSummary()).contains("3");
    }

    @Test
    void historyDraftIsUnavailableWhenCountIsZero() {
        // count = 0 → available=false / reason="no_history_draft"
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L, 5L));
        when(promptDraftsService.countByKnowledgeBaseId(5L)).thenReturn(0L);

        SeedAvailabilityResponse response = service.evaluate(18L);
        SeedOption history = response.getOptions().stream()
                .filter(o -> "history_draft".equals(o.getKey()))
                .findFirst().orElseThrow();

        assertThat(history.getAvailable()).isFalse();
        assertThat(history.getReason()).isEqualTo("no_history_draft");
        assertThat(history.getSummary()).contains("暂无");
    }
```

如果 `newBuildRun` helper 不存在或签名不带 kbId，仿现有 helper 自行构造；KnowledgeBaseBuildRuns 至少要 set `id` / `knowledgeBaseId`。

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=SeedAvailabilityServiceTest test 2>&1 | grep -E "Tests run|BUILD|FAIL|ERROR\b" | tail -5
```

Expected：新增两条测试 FAIL（service 暂未注入 promptDraftsService）。

- [ ] **Step 3：改造 SeedAvailabilityService**

定位类字段段：

**oldStr**：
```java
@Service
@RequiredArgsConstructor
public class SeedAvailabilityService {
```

**newStr**（class-level Javadoc 自检 A 检查更新，留给 Step 5 一并改）：
```java
@Service
@RequiredArgsConstructor
public class SeedAvailabilityService {
```

(无变化；保留 RequiredArgsConstructor 自动构造)

定位 `private final` 字段段（具体字段名以仓库为准），在末尾加入：

**oldStr**：
```java
    private final ObjectMapper objectMapper;
```

**newStr**：
```java
    private final ObjectMapper objectMapper;
    private final org.ysu.ckqaback.service.PromptDraftsService promptDraftsService;
```

定位 `buildHistoryDraft` 方法整体替换：

**oldStr**：
```java
    private SeedOption buildHistoryDraft() {
        return SeedOption.builder()
                .key("history_draft")
                .available(false)
                .reason("phase_6_not_implemented")
                .summary("历史草稿入口将在 Phase 6 开放")
                .build();
    }
```

**newStr**：
```java
    /**
     * 历史草稿种子：当且仅当本知识库已有 ≥1 条 prompt_drafts 记录时可点。
     * <p>Phase 6 落地：count 由 {@link PromptDraftsService#countByKnowledgeBaseId} 给出；
     * 具体哪条草稿由前端在用户点 history_draft 卡片时再拉列表决定。</p>
     */
    private SeedOption buildHistoryDraft(KnowledgeBaseBuildRuns buildRun) {
        long count = promptDraftsService.countByKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        if (count <= 0) {
            return SeedOption.builder()
                    .key("history_draft")
                    .available(false)
                    .reason("no_history_draft")
                    .summary("本知识库暂无历史草稿，05 步保存并入库后会出现在这里")
                    .build();
        }
        return SeedOption.builder()
                .key("history_draft")
                .available(true)
                .reason(null)
                .summary("共 " + count + " 条历史草稿可选，点击进入选择")
                .build();
    }
```

定位 `evaluate` 方法中 `options.add(buildHistoryDraft());` 改为 `options.add(buildHistoryDraft(buildRun));`。

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=SeedAvailabilityServiceTest test 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

Expected：所有测试 PASS（含新增两条 history_draft 测试）。

- [ ] **Step 5：更新 class-level Javadoc**

**oldStr**：
```java
 *   <li>{@code history_draft}：本期始终不可用（Phase 6 落地）</li>
```

**newStr**：
```java
 *   <li>{@code history_draft}：当且仅当本 kb 已有 ≥1 条 prompt_drafts 记录时可用（Phase 6 落地）</li>
```

- [ ] **Step 6：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/SeedAvailabilityService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/SeedAvailabilityServiceTest.java
git commit -m "feat(prompt-builder): SeedAvailability history_draft 由 prompt_drafts.count 决定可用性 (Phase 6)"
```

---

## Task 3：FinalizePromptService（核心业务）

把 04 选定候选写入 customPromptDraft，可选同时入库 prompt_drafts。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/FinalizePromptService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/FinalizePromptServiceTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/FinalizePromptServiceTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptDraftsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinalizePromptServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsService;
    private PromptTuneExtractionEvalRunsService evalRunsService;
    private PromptDraftsService promptDraftsService;
    private BuildRunWorkspaceService workspaceService;
    private ObjectMapper objectMapper;
    private FinalizePromptService service;

    private Path workspaceDir;
    private AtomicReference<KnowledgeBaseBuildRuns> persistedBuildRun;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        promptDraftsService = mock(PromptDraftsService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        objectMapper = new ObjectMapper();

        service = new FinalizePromptService(
                buildRunsService,
                evalRunsService,
                promptDraftsService,
                workspaceService,
                objectMapper
        );

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/default"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/default/prompt.txt"),
                "-Goal-\nExtract entities.\n");
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        // 默认 build run；测试可在体内覆写 buildMetadata
        persistedBuildRun = new AtomicReference<>(newBuildRun(18L, 5L));
        when(buildRunsService.getRequiredById(18L)).thenAnswer(inv -> persistedBuildRun.get());
        when(buildRunsService.updateById(any(KnowledgeBaseBuildRuns.class))).thenAnswer(inv -> {
            persistedBuildRun.set(inv.getArgument(0));
            return true;
        });
    }

    @Test
    void rejectsWhenNoEvalRunExists() {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.empty());

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS.getCode()));
    }

    @Test
    void rejectsWhenLatestEvalRunNotSuccess() {
        when(evalRunsService.findLatestByBuildRunId(18L))
                .thenReturn(Optional.of(newEvalRun(7L, "running", null)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS.getCode()));
    }

    @Test
    void rejectsWhenCandidateIdNotInReport() {
        // 报告中只有 default，但用户传 phantom → 4111
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("phantom_x");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INVALID_FINALIZE_CANDIDATE.getCode());
                    assertThat(e.getMessage()).contains("phantom_x");
                });
    }

    @Test
    void raises5000WhenReportJsonIsCorrupted() {
        // reportJson 解析失败时应该抛 5000 INTERNAL_ERROR（服务端数据异常），不能误抛 4111
        when(evalRunsService.findLatestByBuildRunId(18L))
                .thenReturn(Optional.of(newEvalRun(7L, "success", "not-a-valid-json")));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INTERNAL_ERROR.getCode());
                    assertThat(e.getMessage()).containsAnyOf("解析", "JSON");
                });
    }

    @Test
    void writesCustomPromptDraftWithCompleteSnapshotWhenSaveAsDraftFalse() throws Exception {
        // saveAsDraft=false → 仅写 customPromptDraft 完整快照（不入库历史草稿）
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(false);

        service.finalizePrompt(18L, req);

        // build run.buildMetadata.customPromptDraft 含完整快照
        ArgumentCaptor<KnowledgeBaseBuildRuns> captor = ArgumentCaptor.forClass(KnowledgeBaseBuildRuns.class);
        verify(buildRunsService).updateById(captor.capture());
        JsonNode draft = objectMapper.readTree(captor.getValue().getBuildMetadata())
                .path("customPromptDraft");
        assertThat(draft.path("selectedCandidateId").asText()).isEqualTo("default");
        assertThat(draft.path("compositeScore").asText()).isEqualTo("0.7");
        assertThat(draft.path("sourceEvalRunId").asLong()).isEqualTo(7L);
        assertThat(draft.path("finalizedAt").asText()).matches("^\\d{4}-\\d{2}-\\d{2}T.*[+-]\\d{2}:\\d{2}$");
        assertThat(draft.path("prompts").path("extract_graph").path("content").asText())
                .contains("Extract entities");

        verify(promptDraftsService, never()).save(any());
    }

    @Test
    void writesCustomPromptDraftAndInsertsHistoryDraftWhenSaveAsDraftTrue() throws Exception {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("课程名 · 默认基线 · 2026-05-17");
        req.setDraftDescription("经过 20 条校准集评估，综合分 0.70");
        when(promptDraftsService.save(any())).thenReturn(true);

        service.finalizePrompt(18L, req);

        ArgumentCaptor<PromptDrafts> draftCaptor = ArgumentCaptor.forClass(PromptDrafts.class);
        verify(promptDraftsService).save(draftCaptor.capture());
        PromptDrafts saved = draftCaptor.getValue();
        assertThat(saved.getKnowledgeBaseId()).isEqualTo(5L);
        assertThat(saved.getName()).isEqualTo("课程名 · 默认基线 · 2026-05-17");
        assertThat(saved.getDescription()).contains("0.70");
        assertThat(saved.getCandidateId()).isEqualTo("default");
        assertThat(saved.getSourceBuildRunId()).isEqualTo(18L);
        assertThat(saved.getCompositeScore()).isNotNull();
        assertThat(saved.getPromptsJson()).contains("Extract entities");
    }

    @Test
    void rollsBackWhenPromptDraftSaveReturnsFalse() {
        // 审阅意见 #2：save 返回 false 不抛错时应该主动抛 5000，让 @Transactional 回滚
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));
        when(promptDraftsService.save(any())).thenReturn(false);

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("draft");

        assertThatThrownBy(() -> service.finalizePrompt(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INTERNAL_ERROR.getCode());
                    assertThat(e.getMessage()).contains("回滚");
                });
    }

    @Test
    void usesCurrentBuildRunSeedSnapshotInDraftAndCustomPromptDraft() throws Exception {
        // build run metadata 中有 seed=graphrag_tuned，draft 与 customPromptDraft 都应继承
        KnowledgeBaseBuildRuns buildRun = newBuildRun(18L, 5L);
        buildRun.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");
        persistedBuildRun.set(buildRun);

        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(newEvalRun(7L, "success", """
                {"all_candidates_ranked": [
                  {"candidate":"default","rank":1,"composite_score":0.7}
                ]}
                """)));
        when(promptDraftsService.save(any())).thenReturn(true);

        FinalizePromptRequest req = new FinalizePromptRequest();
        req.setCandidateId("default");
        req.setSaveAsDraft(true);
        req.setDraftName("draft");

        service.finalizePrompt(18L, req);

        ArgumentCaptor<PromptDrafts> draftCaptor = ArgumentCaptor.forClass(PromptDrafts.class);
        verify(promptDraftsService).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getSeed()).isEqualTo("graphrag_tuned");

        ArgumentCaptor<KnowledgeBaseBuildRuns> brCaptor = ArgumentCaptor.forClass(KnowledgeBaseBuildRuns.class);
        verify(buildRunsService).updateById(brCaptor.capture());
        JsonNode draft = objectMapper.readTree(brCaptor.getValue().getBuildMetadata())
                .path("customPromptDraft");
        assertThat(draft.path("seed").asText()).isEqualTo("graphrag_tuned");
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id, Long kbId) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(kbId);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }

    private static PromptTuneExtractionEvalRuns newEvalRun(Long id, String status, String reportJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setBuildRunId(18L);
        r.setKnowledgeBaseId(5L);
        r.setStatus(status);
        r.setReportJson(reportJson);
        return r;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=FinalizePromptServiceTest test 2>&1 | grep -E "Tests run|BUILD|FAIL|ERROR\b" | tail -5
```

Expected：编译失败，`FinalizePromptService` 类不存在。

- [ ] **Step 3：写 FinalizePromptService 实现（方案 B：直接写 metadata，不复用 saveCustomPromptDraft）**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/FinalizePromptService.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptDraftsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 05 步 finalize 业务编排：
 * <ol>
 *   <li>校验 04 评分 run（status=success）+ candidateId 在评分报告中（4110 / 4111）。</li>
 *   <li><b>直接写</b> {@code customPromptDraft}（不复用 KnowledgeBaseBuildRunService.saveCustomPromptDraft）：
 *       完整快照含 selectedCandidateId / compositeScore / sourceEvalRunId / finalizedAt / prompts.extract_graph.content。</li>
 *   <li>当 saveAsDraft=true 时同事务插一条 prompt_drafts；mybatis-plus.save 返回 false 立即抛 5000 让事务回滚。</li>
 * </ol>
 *
 * <p>错误码语义：</p>
 * <ul>
 *   <li>{@code 4110 EXTRACTION_EVAL_NOT_SUCCESS}：评分尚未成功（业务前提不满足）。</li>
 *   <li>{@code 4111 INVALID_FINALIZE_CANDIDATE}：candidateId 不在评分报告 candidates 中（业务入参错误）。</li>
 *   <li>{@code 5000 INTERNAL_ERROR}：reportJson 解析失败 / candidate prompt 文件读失败 / prompt_drafts.save 返回 false（服务端数据异常，与 4111 业务入参错误明确区分）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FinalizePromptService {

    private static final Logger log = LoggerFactory.getLogger(FinalizePromptService.class);

    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final PromptDraftsService promptDraftsService;
    private final BuildRunWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BuildRunDetailResponse finalizePrompt(Long buildRunId, FinalizePromptRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsService.getRequiredById(buildRunId);

        // 校验 1：04 评分必须 success
        Optional<PromptTuneExtractionEvalRuns> evalOpt = evalRunsService.findLatestByBuildRunId(buildRunId);
        if (evalOpt.isEmpty() || !"success".equals(evalOpt.get().getStatus())) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS,
                    HttpStatus.BAD_REQUEST,
                    evalOpt.isEmpty() ? "尚未触发评分" : "评分当前状态：" + evalOpt.get().getStatus()
            );
        }
        PromptTuneExtractionEvalRuns evalRun = evalOpt.get();

        // 解析 reportJson + 校验 candidateId（区分 4111 业务入参错误 vs 5000 服务端数据异常）
        JsonNode reportRoot = parseReportJson(evalRun.getReportJson());
        BigDecimal compositeScore = lookupCompositeScore(reportRoot, request.getCandidateId());
        if (compositeScore == null) {
            throw new BusinessException(
                    ApiResultCode.INVALID_FINALIZE_CANDIDATE,
                    HttpStatus.BAD_REQUEST,
                    "未识别的候选 ID：" + request.getCandidateId()
            );
        }

        // 读候选 prompt 全文
        Path workspace = workspaceService.resolve(buildRun.getWorkspaceUri());
        Path promptFile = workspace.resolve("prompt").resolve("candidates")
                .resolve(request.getCandidateId()).resolve("prompt.txt");
        String promptContent = readPromptText(promptFile, request.getCandidateId());

        // 直接写 customPromptDraft：完整 finalize 快照（不复用 saveCustomPromptDraft）
        String currentSeed = readCurrentSeed(buildRun);
        if (currentSeed == null) currentSeed = "system_default";
        String mergedMetadata = mergeFinalizedDraftIntoMetadata(
                buildRun.getBuildMetadata(),
                currentSeed,
                request.getCandidateId(),
                promptContent,
                compositeScore,
                evalRun.getId()
        );
        buildRun.setBuildMetadata(mergedMetadata);
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsService.updateById(buildRun);

        // 可选：插 prompt_drafts
        if (Boolean.TRUE.equals(request.getSaveAsDraft())) {
            PromptDrafts draft = new PromptDrafts();
            draft.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
            draft.setName(defaultText(request.getDraftName(),
                    "draft-" + buildRunId + "-" + request.getCandidateId()));
            draft.setDescription(request.getDraftDescription());
            draft.setSeed(currentSeed);
            draft.setCandidateId(request.getCandidateId());
            draft.setPromptsJson(serializePrompts(promptContent));
            draft.setSourceBuildRunId(buildRunId);
            draft.setCompositeScore(compositeScore);
            LocalDateTime now = LocalDateTime.now();
            draft.setCreatedAt(now);
            draft.setUpdatedAt(now);
            // mybatis-plus save 返回 false 时主动抛错，让 @Transactional 回滚 customPromptDraft 写入
            boolean saved = promptDraftsService.save(draft);
            if (!saved) {
                throw new BusinessException(
                        ApiResultCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "历史草稿入库失败（save returned false），已回滚 build run customPromptDraft 写入"
                );
            }
            log.info("历史草稿入库 kbId={} draftId={} sourceBuildRunId={}",
                    buildRun.getKnowledgeBaseId(), draft.getId(), buildRunId);
        }

        return BuildRunDetailResponse.fromEntity(buildRunsService.getRequiredById(buildRunId));
    }

    /**
     * 解析 reportJson；解析失败抛 5000（服务端数据异常，与 4111 业务入参错误区分）。
     */
    private JsonNode parseReportJson(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分报告内容为空，无法 finalize"
            );
        }
        try {
            return objectMapper.readTree(reportJson);
        } catch (JsonProcessingException e) {
            log.warn("解析 reportJson 失败: {}", e.getMessage());
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分报告 JSON 解析失败：" + e.getMessage()
            );
        }
    }

    /**
     * 在已解析的 reportRoot 中找 candidateId 对应的 composite_score。
     * 找不到返 null（caller 抛 4111）；解析阶段已经成功，这里只做白名单匹配。
     */
    private BigDecimal lookupCompositeScore(JsonNode reportRoot, String candidateId) {
        JsonNode arr = reportRoot.path("all_candidates_ranked");
        if (!arr.isArray()) return null;
        for (JsonNode entry : arr) {
            String id = entry.path("candidate").asText(null);
            if (candidateId.equals(id)) {
                JsonNode score = entry.path("composite_score");
                if (score.isNumber()) return new BigDecimal(score.asText());
                return BigDecimal.ZERO;
            }
        }
        return null;
    }

    private String readPromptText(Path promptFile, String candidateId) {
        if (!Files.exists(promptFile)) {
            // 文件不存在视为服务端数据异常（评分报告认了这个 candidate，但磁盘上没产物），抛 5000
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 prompt 文件不存在 candidate=" + candidateId
            );
        }
        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 prompt 失败: " + e.getMessage()
            );
        }
    }

    private String readCurrentSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadata).path("customPromptDraft").path("seed");
            return node.isTextual() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 把 finalize 完整快照合并进 buildMetadata.customPromptDraft，<b>只</b>替换 customPromptDraft 子节点，
     * 保留 stage / promptStrategy / promptConfirmed / exportConfirmed / graphInputConfirmed 等其他键。
     */
    private String mergeFinalizedDraftIntoMetadata(
            String existingMetadataJson,
            String seed,
            String candidateId,
            String promptContent,
            BigDecimal compositeScore,
            Long evalRunId
    ) {
        ObjectNode root;
        if (existingMetadataJson == null || existingMetadataJson.isBlank()) {
            root = objectMapper.createObjectNode();
        } else {
            try {
                JsonNode parsed = objectMapper.readTree(existingMetadataJson);
                root = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            } catch (JsonProcessingException e) {
                log.warn("旧 metadata 解析失败，按空对象重写 customPromptDraft：{}", e.getMessage());
                root = objectMapper.createObjectNode();
            }
        }

        // 保留旧 customPromptDraft 中跨阶段不变的字段（seedSnapshotAt / 旧 modifiedAt / baseHash 等），
        // 同时整体替换 prompts.extract_graph.content + 注入 finalize 快照。
        JsonNode oldDraftNode = root.path("customPromptDraft");
        ObjectNode draft = objectMapper.createObjectNode();
        draft.put("seed", seed);
        if (oldDraftNode.has("seedSnapshotAt") && oldDraftNode.path("seedSnapshotAt").isTextual()) {
            draft.put("seedSnapshotAt", oldDraftNode.path("seedSnapshotAt").asText());
        }

        // finalize 快照核心字段
        draft.put("selectedCandidateId", candidateId);
        draft.put("compositeScore", compositeScore.toPlainString());
        draft.put("sourceEvalRunId", evalRunId);
        draft.put("finalizedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        draft.put("updatedAt", LocalDateTime.now().toString());

        // prompts.extract_graph.content 整体覆盖
        ObjectNode prompts = objectMapper.createObjectNode();
        ObjectNode extractGraph = objectMapper.createObjectNode();
        extractGraph.put("content", promptContent);
        extractGraph.put("modifiedAt", LocalDateTime.now().toString());
        // 保留 baseHash 旧值（如有）
        if (oldDraftNode.path("prompts").path("extract_graph").path("baseHash").isTextual()) {
            extractGraph.put("baseHash",
                    oldDraftNode.path("prompts").path("extract_graph").path("baseHash").asText());
        }
        prompts.set("extract_graph", extractGraph);
        draft.set("prompts", prompts);

        root.set("customPromptDraft", draft);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化 customPromptDraft 失败：" + e.getMessage()
            );
        }
    }

    private String serializePrompts(String extractGraphContent) {
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("extract_graph", extractGraphContent);
        try {
            return objectMapper.writeValueAsString(prompts);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化 prompts 失败"
            );
        }
    }

    private static String defaultText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=FinalizePromptServiceTest test 2>&1 | grep -E "Tests run|BUILD|FAIL|ERROR\b" | tail -5
```

Expected：8 个测试 PASS（含 reportJson 损坏 5000 / save 返回 false 抛错并验证回滚 / seed 快照继承 / 缺省 saveAsDraft 等额外路径）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/FinalizePromptService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/FinalizePromptServiceTest.java
git commit -m "feat(prompt-builder): 新建 FinalizePromptService 写 customPromptDraft + 可选入库 prompt_drafts (Phase 6)"
```

---

## Task 4：PromptDraftListService（GET /prompt-drafts 投影）

把 PromptDrafts entity 列表投影成 PromptDraftResponse DTO。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptDraftListService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/PromptDraftListServiceTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/PromptDraftListServiceTest.java`：

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptDraftListServiceTest {

    private PromptDraftsService promptDraftsService;
    private PromptDraftListService service;

    @BeforeEach
    void setUp() {
        promptDraftsService = mock(PromptDraftsService.class);
        service = new PromptDraftListService(promptDraftsService);
    }

    @Test
    void listProjectsAllFieldsToResponse() {
        PromptDrafts draft = new PromptDrafts();
        draft.setId(1L);
        draft.setKnowledgeBaseId(7L);
        draft.setName("课程 · 默认基线 · 2026-05-17");
        draft.setDescription("评分 0.7");
        draft.setSeed("system_default");
        draft.setCandidateId("default");
        draft.setPromptsJson("{\"extract_graph\":\"-Goal-\\n...\"}");
        draft.setSourceBuildRunId(18L);
        draft.setCompositeScore(new BigDecimal("0.7000"));
        draft.setCreatedAt(LocalDateTime.of(2026, 5, 17, 10, 0));
        draft.setUpdatedAt(LocalDateTime.of(2026, 5, 17, 10, 0));
        when(promptDraftsService.listByKnowledgeBaseId(7L)).thenReturn(List.of(draft));

        List<PromptDraftResponse> result = service.list(7L);
        assertThat(result).hasSize(1);
        PromptDraftResponse first = result.get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getKnowledgeBaseId()).isEqualTo(7L);
        assertThat(first.getName()).isEqualTo("课程 · 默认基线 · 2026-05-17");
        assertThat(first.getSeed()).isEqualTo("system_default");
        assertThat(first.getCandidateId()).isEqualTo("default");
        // 注意：PromptDraftResponse 已在 Task 1 Step 6 删除 promptsJson 字段，
        // 这里不再断言 prompts 正文。
        assertThat(first.getSourceBuildRunId()).isEqualTo(18L);
        assertThat(first.getCompositeScore()).isEqualByComparingTo(new BigDecimal("0.7"));
    }

    @Test
    void listReturnsEmptyWhenNoDrafts() {
        when(promptDraftsService.listByKnowledgeBaseId(99L)).thenReturn(List.of());
        assertThat(service.list(99L)).isEmpty();
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=PromptDraftListServiceTest test 2>&1 | tail -5
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptDraftListService.java`：

```java
package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.util.List;

/**
 * 历史草稿列表投影：把 {@link PromptDrafts} entity 转成 {@link PromptDraftResponse} DTO，
 * 按 created_at 倒序（在 service 层已排序）。
 */
@Service
@RequiredArgsConstructor
public class PromptDraftListService {

    private final PromptDraftsService promptDraftsService;

    public List<PromptDraftResponse> list(Long knowledgeBaseId) {
        return promptDraftsService.listByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PromptDraftResponse toResponse(PromptDrafts entity) {
        // 注意：Phase 6 把 PromptDraftResponse 重新定义为列表摘要语义，
        // promptsJson 字段已删除（Task 1 Step 6），不再投影；
        // listByKnowledgeBaseId 也已在 mapper 层用 select 排除 prompts_json 列。
        return PromptDraftResponse.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .name(entity.getName())
                .description(entity.getDescription())
                .seed(entity.getSeed())
                .candidateId(entity.getCandidateId())
                .sourceBuildRunId(entity.getSourceBuildRunId())
                .compositeScore(entity.getCompositeScore())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -Dtest=PromptDraftListServiceTest test 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

Expected：2 个测试 PASS。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptDraftListService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/PromptDraftListServiceTest.java
git commit -m "feat(prompt-builder): 新建 PromptDraftListService 投影历史草稿列表 (Phase 6)"
```

---

## Task 5：替换 Controller 占位

把 `PromptTunePipelineController.finalizePrompt` / `listPromptDrafts` 两个 501 占位换成真实实现。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1：补 import + 注入字段**

定位 import 段，加：

**oldStr**：
```java
import org.ysu.ckqaback.index.ExtractionEvalService;
```

**newStr**：
```java
import org.ysu.ckqaback.index.ExtractionEvalService;
import org.ysu.ckqaback.index.FinalizePromptService;
import org.ysu.ckqaback.index.PromptDraftListService;
```

定位类字段段：

**oldStr**：
```java
    private final ExtractionEvalService extractionEvalService;
```

**newStr**：
```java
    private final ExtractionEvalService extractionEvalService;
    private final FinalizePromptService finalizePromptService;
    private final PromptDraftListService promptDraftListService;
```

- [ ] **Step 2：替换 finalizePrompt 占位**

**oldStr**：
```java
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/finalize")
    public ApiResponse<BuildRunDetailResponse> finalizePrompt(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody FinalizePromptRequest request
    ) {
        throw notImplemented();
    }
```

**newStr**：
```java
    /**
     * 05 步：把 04 选定候选写入 customPromptDraft，可选同时入库 prompt_drafts。
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/finalize")
    public ApiResponse<BuildRunDetailResponse> finalizePrompt(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @Valid @RequestBody FinalizePromptRequest request
    ) {
        return ApiResponseUtils.success(finalizePromptService.finalizePrompt(buildRunId, request));
    }
```

- [ ] **Step 3：替换 listPromptDrafts 占位**

**oldStr**：
```java
    @GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{kbId}/prompt-drafts")
    public ApiResponse<List<PromptDraftResponse>> listPromptDrafts(
            @PathVariable @Positive(message = "kbId必须大于0") Long kbId
    ) {
        throw notImplemented();
    }
```

**newStr**：
```java
    /**
     * 01 步：列出该知识库的历史草稿，供种子卡决定 history_draft 选项与抽屉列表。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{kbId}/prompt-drafts")
    public ApiResponse<List<PromptDraftResponse>> listPromptDrafts(
            @PathVariable("kbId") @Positive(message = "kbId必须大于0") Long knowledgeBaseId
    ) {
        return ApiResponseUtils.success(promptDraftListService.list(knowledgeBaseId));
    }
```

- [ ] **Step 4：构建 + 跑全量后端测试**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -5
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected：编译 SUCCESS；所有测试 PASS（含 Phase 6 新增 11 条测试）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(prompt-builder): 05 步 controller 真实接入 finalize + listPromptDrafts (Phase 6)"
```

---

## Task 6：前端 API 客户端真实化 finalizePrompt + listPromptDrafts

`prompt-tune-pipeline.js` 现有桩函数（mock 返回）替换为真实端点调用，并补单测。

**Files:**
- Modify: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`
- Test: `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-finalize.test.js`
- Test: `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-prompt-drafts.test.js`

- [ ] **Step 1：写 finalizePrompt 测试**

Create `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-finalize.test.js`：

```javascript
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { finalizePrompt } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    post(url, body, config) {
      this.requests.push({ method: 'POST', url, body, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('finalizePrompt POST /finalize 含 saveAsDraft=false 的最简载荷', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { id: 18 } },
  ])
  const result = await finalizePrompt(18, { candidateId: 'default', saveAsDraft: false }, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/finalize')
  assert.deepEqual(client.requests[0].body, { candidateId: 'default', saveAsDraft: false })
  assert.equal(result.id, 18)
})

test('finalizePrompt POST /finalize 含 saveAsDraft=true 与 draft 元信息', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { id: 18 } },
  ])
  await finalizePrompt(18, {
    candidateId: 'schema_aware_directional_v2',
    saveAsDraft: true,
    draftName: '课程 · 图谱感知 · 2026-05-17',
    draftDescription: '综合分 0.71',
  }, client)
  assert.deepEqual(client.requests[0].body, {
    candidateId: 'schema_aware_directional_v2',
    saveAsDraft: true,
    draftName: '课程 · 图谱感知 · 2026-05-17',
    draftDescription: '综合分 0.71',
  })
})

test('finalizePrompt 业务码 4110 时抛出（EXTRACTION_EVAL_NOT_SUCCESS）', async () => {
  const client = makeMockClient([
    { code: 4110, message: '评分尚未成功，无法保存为草稿', data: null },
  ])
  await assert.rejects(
    () => finalizePrompt(18, { candidateId: 'default' }, client),
    (err) => err.code === 4110,
  )
})

test('finalizePrompt 业务码 4111 时抛出（INVALID_FINALIZE_CANDIDATE）', async () => {
  const client = makeMockClient([
    { code: 4111, message: '选定候选 ID 不在评分报告的候选清单中', data: null },
  ])
  await assert.rejects(
    () => finalizePrompt(18, { candidateId: 'phantom' }, client),
    (err) => err.code === 4111,
  )
})
```

- [ ] **Step 2：写 listPromptDrafts 测试**

Create `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-prompt-drafts.test.js`：

```javascript
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { listPromptDrafts } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    get(url, config) {
      this.requests.push({ method: 'GET', url, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('listPromptDrafts GET /knowledge-bases/{kbId}/prompt-drafts', async () => {
  const client = makeMockClient([
    {
      code: 200,
      message: 'ok',
      data: [
        { id: 1, knowledgeBaseId: 7, name: 'draft 1', candidateId: 'default', compositeScore: 0.7 },
        { id: 2, knowledgeBaseId: 7, name: 'draft 2', candidateId: 'auto_tuned', compositeScore: 0.55 },
      ],
    },
  ])
  const result = await listPromptDrafts(7, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-bases/7/prompt-drafts')
  assert.equal(result.length, 2)
  assert.equal(result[0].name, 'draft 1')
})

test('listPromptDrafts 返回空数组', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: [] }])
  const result = await listPromptDrafts(7, client)
  assert.deepEqual(result, [])
})

test('listPromptDrafts 对 kbId 做 URL encoding', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: [] }])
  await listPromptDrafts('a/b 7', client)
  assert.equal(client.requests[0].url, '/knowledge-bases/a%2Fb%207/prompt-drafts')
})
```

- [ ] **Step 3：实现 finalizePrompt + listPromptDrafts**

定位 `// ----- 05 步：预览保存 -----` 段；当前可能已有 mock 桩，整段替换：

**oldStr**：
```javascript
// ----- 05 步：预览保存 -----

export async function finalizePrompt(buildRunId, payload, client = http) {
```

**newStr**：
```javascript
// ----- 05 步：预览保存 + 历史草稿 -----

export async function finalizePrompt(buildRunId, payload, client = http) {
```

紧接着把 `finalizePrompt` 的 body 改为真实 POST（如已是真实实现则跳过 Step 3）。最终形态：

```javascript
export async function finalizePrompt(buildRunId, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/finalize`,
    payload,
  ))
}

export async function listPromptDrafts(knowledgeBaseId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/prompt-drafts`,
  ))
}
```

如果文件中已经存在 listPromptDrafts 的 mock 桩，整体替换为以上真实实现。

- [ ] **Step 4：跑两个测试文件确认通过**

Run:
```bash
cd frontend/apps/admin-app && node --test src/api/__tests__/prompt-tune-pipeline-finalize.test.js src/api/__tests__/prompt-tune-pipeline-prompt-drafts.test.js 2>&1 | tail -10
```

Expected：7 个测试 PASS（finalize 4 + prompt-drafts 3）。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js \
        frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-finalize.test.js \
        frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-prompt-drafts.test.js
git commit -m "feat(prompt-builder): API 真实化 finalizePrompt + listPromptDrafts + 7 个端点单测 (Phase 6)"
```

---

## Task 7：PromptBuilderHistoryDraftDrawer 组件 + 01 步种子打通

新建抽屉组件让用户在 ≥2 条历史草稿时选具体一条，并把 PromptBuilderPage 的 mock 历史草稿替换为真实 API。

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderHistoryDraftDrawer.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`

- [ ] **Step 1：创建 HistoryDraftDrawer 组件**

Create `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderHistoryDraftDrawer.vue`：

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  drafts: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'select-draft'])

const sorted = computed(() =>
  [...props.drafts].sort((a, b) => {
    const ta = new Date(a.createdAt ?? 0).getTime()
    const tb = new Date(b.createdAt ?? 0).getTime()
    return tb - ta
  }),
)

function handleSelect(draft) {
  emit('select-draft', draft)
  emit('update:modelValue', false)
}

function fmtScore(score) {
  if (score == null) return '—'
  return (Number(score) * 100).toFixed(0) + '%'
}

function fmtDate(iso) {
  if (!iso) return ''
  return String(iso).replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="选择历史草稿"
    direction="rtl"
    size="420px"
    :before-close="() => emit('update:modelValue', false)"
  >
    <div v-if="sorted.length === 0" class="ann-text-muted">暂无可选草稿</div>
    <ul v-else class="history-draft-list">
      <li
        v-for="d in sorted"
        :key="d.id"
        class="history-draft-list__item"
        @click="handleSelect(d)"
      >
        <div class="history-draft-list__head">
          <strong>{{ d.name }}</strong>
          <span class="ann-pill ann-pill--gold">综合 {{ fmtScore(d.compositeScore) }}</span>
        </div>
        <div class="ann-text-tiny">候选：{{ d.candidateId }} · 来源构建 #{{ d.sourceBuildRunId }}</div>
        <div v-if="d.description" class="history-draft-list__desc">{{ d.description }}</div>
        <div class="ann-text-tiny ann-text-muted">{{ fmtDate(d.createdAt) }}</div>
      </li>
    </ul>
  </el-drawer>
</template>

<style scoped>
.history-draft-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.history-draft-list__item {
  cursor: pointer;
  padding: 12px;
  border: 1px solid var(--ckqa-border, #d6d3d1);
  border-radius: 8px;
  transition: background 0.2s;
}
.history-draft-list__item:hover {
  background: var(--ckqa-bg-soft, #fafaf9);
}
.history-draft-list__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.history-draft-list__desc {
  margin: 6px 0;
  color: var(--ckqa-text-muted, #78716c);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
```

- [ ] **Step 2：改造 PromptBuilderSeedStep 删 Phase 1e 文案**

定位 `getSeedTooltip` / `isOptionDisabled` 等函数：

**oldStr**：
```javascript
  if (option.key === 'history_draft') {
    if (props.historyDrafts.length === 0) return 'Phase 1e 开放（暂无可选草稿）'
    return `Phase 1e 开放（共 ${props.historyDrafts.length} 条草稿）`
  }
```

**newStr**：
```javascript
  if (option.key === 'history_draft') {
    if (props.historyDrafts.length === 0) {
      return '本知识库暂无历史草稿，05 步保存并入库后会出现在这里'
    }
    return `共 ${props.historyDrafts.length} 条历史草稿可选`
  }
```

定位 `isOptionDisabled` 兜底分支：

**oldStr**：
```javascript
  return option.key === 'history_draft'
```

**newStr**：
```javascript
  // history_draft：列表非空就放开（与 SeedAvailability 后端 count > 0 一致）
  if (option.key === 'history_draft') {
    return props.historyDrafts.length === 0
  }
  return false
```

定位 `phase_6_not_implemented` reason 文案分支并替换：

**oldStr**：
```javascript
    case 'phase_6_not_implemented':
      return '历史草稿入口将在后续版本开放'
```

**newStr**：
```javascript
    case 'no_history_draft':
      return '本知识库暂无历史草稿，05 步保存并入库后会出现在这里'
```

- [ ] **Step 3：改造 PromptBuilderPage 真实拉历史草稿 + 抽屉选择**

定位 import 段：

**oldStr**：
```javascript
import {
  MOCK_HISTORY_DRAFTS,
```

**newStr**：
```javascript
import { listPromptDrafts, finalizePrompt } from '../../api/prompt-tune-pipeline.js'
import PromptBuilderHistoryDraftDrawer from './prompt-builder/PromptBuilderHistoryDraftDrawer.vue'
import {
```

把整个 `MOCK_HISTORY_DRAFTS` 仅留在 mocks 文件中以便回退。Page 内不再 import；改用真实数据：

**oldStr**：
```javascript
const seed = ref(null)
```

**newStr**：
```javascript
const seed = ref(null)
const historyDrafts = ref([])
const historyDraftDrawerOpen = ref(false)
```

在 `onMounted` 或现有数据初始化函数中追加：

```javascript
async function loadHistoryDrafts() {
  if (!kbId.value) return
  try {
    historyDrafts.value = await listPromptDrafts(kbId.value)
  } catch (err) {
    historyDrafts.value = []
    // eslint-disable-next-line no-console
    console.warn('[prompt-builder] 加载历史草稿失败', err)
  }
}
// 在 mounted 调用：
// onMounted(async () => { ...; await loadHistoryDrafts(); ... })
```

定位 `handleSelectSeed` 现有 `if (seedKey === 'history_draft')` 分支，整段替换：

**oldStr**：
```javascript
  if (seedKey === 'history_draft') {
    // Phase 1e：从历史草稿列表中选取最新一条作为 mock 演示
    const latest = MOCK_HISTORY_DRAFTS[0]
```

**newStr**：
```javascript
  if (seedKey === 'history_draft') {
    if (historyDrafts.value.length === 0) {
      ElMessage.warning('本知识库暂无历史草稿，请先在 05 步保存并入库')
      return
    }
    if (historyDrafts.value.length === 1) {
      await loadHistoryDraft(historyDrafts.value[0])
      return
    }
    // ≥2 条 → 打开抽屉让用户选
    historyDraftDrawerOpen.value = true
    return
  }
  // 其他 seed：直接 PUT
  // ...保留现有 system_default / graphrag_tuned 的处理
```

新增 `loadHistoryDraft` 函数（替代旧的 mock 选取逻辑，保留旧函数中的 PUT seed + 状态填充）：

```javascript
async function loadHistoryDraft(draft) {
  // 与 spec § "history_draft 仅是种子来源、不复用旧候选 ID" 一致：
  // 选择历史草稿后只设 seed=history_draft + historyDraftId，
  // 主动清空旧 selectedCandidateId / 旧评分态，避免把别的 build run 的 candidateId 带进 finalize 触发 4111。
  seed.value = 'history_draft'
  historyDraftId.value = draft.id
  selectedCandidateId.value = ''
  // 同时清掉 03 / 04 步残留态：用户后续仍需走完整 02→03→04→05 链路
  if (typeof candidates !== 'undefined') candidates.value = []
  if (typeof evalRunStatus !== 'undefined') evalRunStatus.value = null
  if (typeof scoreReport !== 'undefined') scoreReport.value = null
  // 可选预填保存表单：name / description（用户后续可在 05 步覆盖）
  saveDraftName.value = draft.name
  saveDraftDescription.value = draft.description ?? ''
  saveDraftNameTouched.value = true
  saveDraftDescriptionTouched.value = !!draft.description
  dirty.value = true
  // PUT 仅 seed 子字段（沿用 Phase 4.5 的部分更新语义）
  try {
    await persistSeedToBuildRun('history_draft')
    ElMessage.success(`已加载历史草稿「${draft.name}」`)
  } catch (err) {
    ElMessage.error(err?.message ?? '加载历史草稿失败')
  }
}
```

> **审阅意见 #3 落地点**：以上实现**不再**写 `selectedCandidateId.value = draft.candidateId ?? ''`，并主动清空 03 / 04 步状态。本期 `historyDraftId` 仅做前端记账（用于将来透传到 03 步生成候选时作为种子上下文），**不**作为 finalize 入参；finalize 仍由 04 步评分报告中的 candidateId 决定，与 system_default / graphrag_tuned 完全等同。

`persistSeedToBuildRun` 已在 Phase 4.5 中存在，无需改造。

定位 `<PromptBuilderSeedStep` 标签段：

**oldStr**：
```html
        <PromptBuilderSeedStep
          v-if="activeStepKey === 'seed'"
          :seed="seed"
          :seed-availability="seedAvailability"
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
          :history-drafts="historyDrafts"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderHistoryDraftDrawer
          v-model="historyDraftDrawerOpen"
          :drafts="historyDrafts"
          @select-draft="loadHistoryDraft"
        />
```

定位 `handleSave` 函数，整段替换：

**oldStr**：
```javascript
async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    // Phase 1a 用 mock：500ms 延迟模拟保存请求
    await new Promise((resolve) => setTimeout(resolve, 500))
    // 注意：本期不真发请求，控制台打印 payload 供调试
    // eslint-disable-next-line no-console
    console.log('[Phase 1e mock] save payload', payload)
```

**newStr**：
```javascript
async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    const saveAsDraft = payload.saveMode === 'build_run_with_history'
    await finalizePrompt(buildRunId.value, {
      candidateId: payload.selectedCandidate,
      saveAsDraft,
      draftName: payload.name,
      draftDescription: payload.description,
    })
    // finalize 成功后刷新历史草稿，保证 01 步下次进入时数量准确
    if (saveAsDraft) {
      await loadHistoryDrafts()
    }
```

`handleSave` 后续 `dirty.value = false` 等收尾保持原样。

- [ ] **Step 4：构建 + 单测**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
cd frontend/apps/admin-app && node --test 2>&1 | tail -10
```

Expected：构建 SUCCESS；前端单测全 PASS。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderHistoryDraftDrawer.vue \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue \
        frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 01 步历史草稿真实化 + 抽屉选择 + 05 步真发 finalize (Phase 6)"
```

---

## Task 8：PromptDisplayRaw 换 prismjs 自定义语言

把 raw 模式的自实现 regex 高亮换成 prismjs。

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-prism.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue`

- [ ] **Step 1：定义自定义 prismjs 语言**

Create `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-prism.js`：

```javascript
// 自定义 prismjs 语言：prompt-tune
// 识别 prompt 文件中的常见 token：
// - section：以 `-` 开头结尾的标题行（如 `-Goal-`, `-Schema Constraints-`）
// - placeholder：`{var}` 占位符
// - arrow：`->` 关系箭头
// - comment：`#` 行注释
// - keyword：实体类型 hardcode 列表（与课程域 schema 对齐）
//
// 不依赖 prismjs/components/prism-* 任何内置语言（避免 vite SSR / 全局 attach 兼容问题）。
import Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'

const ENTITY_KEYWORDS = [
  'Course',
  'Concept',
  'Term',
  'KnowledgePoint',
  'FormulaOrDefinition',
  'AlgorithmOrMethod',
  'Theorem',
  'Property',
  'Example',
  'Topic',
]

Prism.languages['prompt-tune'] = {
  comment: {
    pattern: /(^|[^\\])#.*/,
    lookbehind: true,
  },
  section: {
    // 整行匹配 `-Section Title-` 形式
    pattern: /^-[^-\n][^\n]*?-\s*$/m,
    alias: 'keyword',
  },
  placeholder: {
    pattern: /\{[a-zA-Z_][a-zA-Z0-9_]*\}/,
    alias: 'variable',
  },
  arrow: {
    pattern: /->/,
    alias: 'operator',
  },
  keyword: new RegExp(`\\b(?:${ENTITY_KEYWORDS.join('|')})\\b`),
}

export default Prism
```

- [ ] **Step 2：改造 PromptDisplayRaw.vue 使用 prismjs**

Replace 整个 `PromptDisplayRaw.vue` 为：

```vue
<script setup>
import { computed } from 'vue'
import Prism from './prompt-display-prism.js'

const props = defineProps({
  text: { type: String, default: '' },
})

const lines = computed(() => {
  const html = Prism.highlight(props.text, Prism.languages['prompt-tune'], 'prompt-tune')
  return html.split(/\r?\n/).map((lineHtml, i) => ({
    no: i + 1,
    html: lineHtml.length === 0 ? '&nbsp;' : lineHtml,
  }))
})
</script>

<template>
  <pre class="prompt-display-raw"><code class="language-prompt-tune"><span
    v-for="line in lines"
    :key="line.no"
    class="prompt-display-raw__line"
  ><span class="prompt-display-raw__lineno">{{ line.no }}</span><span class="prompt-display-raw__content" v-html="line.html"></span></span></code></pre>
</template>

<style scoped>
.prompt-display-raw {
  margin: 0;
  padding: 12px 0;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
.prompt-display-raw__line {
  display: flex;
  align-items: flex-start;
  padding: 0 16px;
  white-space: pre-wrap;
  word-break: break-word;
}
.prompt-display-raw__lineno {
  flex: 0 0 40px;
  color: #6e7681;
  user-select: none;
  text-align: right;
  margin-right: 12px;
}
.prompt-display-raw__content {
  flex: 1;
}
/* 覆盖 prism-tomorrow 主题：保留段落 / 占位 / 关键字 / 箭头颜色 */
.prompt-display-raw :deep(.token.section) {
  color: #98c379;
  font-weight: 600;
}
.prompt-display-raw :deep(.token.placeholder) {
  color: #e5c07b;
  background: rgba(229, 192, 123, 0.12);
  padding: 0 2px;
  border-radius: 2px;
}
.prompt-display-raw :deep(.token.arrow) {
  color: #61afef;
  font-weight: 600;
}
.prompt-display-raw :deep(.token.comment) {
  color: #5c6370;
  font-style: italic;
}
.prompt-display-raw :deep(.token.keyword) {
  color: #c678dd;
}
</style>
```

- [ ] **Step 3：构建确认无语法错**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：构建 SUCCESS；prismjs 与 css 主题被正常 chunk。

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-prism.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue
git commit -m "feat(prompt-builder): PromptDisplayRaw 换 prismjs 自定义 prompt-tune 语言 + tomorrow 主题 (Phase 6)"
```

---

## Task 9：prompt-display-parser fallback 容错单测（spec § 风险 #5）

补"段落数 < 2 / 单段超长 → 回退 raw"路径的单测，验证 PromptDisplay 的 `fallbackToRaw` 行为。

**Files:**
- Test: `frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/prompt-display-parser.test.js`（如不存在则新建）

- [ ] **Step 1：检查测试目录是否存在**

Run:
```bash
ls frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/ 2>&1 | head
```

如目录不存在则一并 mkdir（fs_write 会自动建）。

- [ ] **Step 2：写测试**

Create / overwrite `frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/prompt-display-parser.test.js`：

```javascript
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parsePromptSections } from '../prompt-display-parser.js'

test('parsePromptSections 正常拆分含多段标题的 prompt', () => {
  const text = `-Goal-\nExtract entities.\n\n-Schema Constraints-\nUse the provided types.`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 2)
  assert.equal(sections[0].title, 'Goal')
  assert.equal(sections[1].title, 'Schema Constraints')
})

test('parsePromptSections 段落数 < 2 时仍返回非空数组（caller 据此走 fallback raw）', () => {
  // spec § 风险 #5：parser 检测段落数 < 2 → 触发 fallbackToRaw
  // parser 本身不 throw，只返回基础结果；UI 组件根据 length 决定是否切 raw。
  const text = `这是一段没有任何 -Section- 标题的纯文本。`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 1)
  // PromptDisplay.vue 内 fallbackToRaw computed：sections.length < 2 || 单段过长 → true
})

test('parsePromptSections 单段超长时仍返回非空数组（caller 据此走 fallback raw）', () => {
  // spec § 风险 #5：单段超长 → fallback raw
  const longBody = 'x'.repeat(8000)
  const text = `-Goal-\n${longBody}`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 1)
  assert.ok(sections[0].body.length >= 8000, '单段 body 应保留全部超长内容')
  // 调用方（PromptDisplay.vue）应根据 body.length 阈值切 raw 模式。
})

test('parsePromptSections 空字符串返回空数组或单条空段落', () => {
  const sections = parsePromptSections('')
  // parser 现状：空字符串返回 [{title:'前言', body:''}] 或 []，两者皆可
  assert.ok(Array.isArray(sections))
})
```

- [ ] **Step 3：跑测试**

Run:
```bash
cd frontend/apps/admin-app && node --test src/views/pages/prompt-builder/__tests__/prompt-display-parser.test.js 2>&1 | tail -10
```

Expected：4 个测试 PASS。

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/prompt-display-parser.test.js
git commit -m "test(prompt-builder): 补 parsePromptSections 容错单测（段落 < 2 / 单段超长）(Phase 6)"
```

---

## Task 10：Playwright e2e 覆盖 finalize 两模式 + 历史草稿种子选择

新增 helper + spec，覆盖 spec § 05 步保存范围两模式 + 01 步从历史草稿种子卡进入抽屉选择。

**Files:**
- Create: `frontend/apps/admin-app/e2e/helpers/prompt-builder-history-drafts.js`
- Create: `frontend/apps/admin-app/e2e/prompt-builder-history-drafts.spec.js`

- [ ] **Step 1：创建 helper**

Create `frontend/apps/admin-app/e2e/helpers/prompt-builder-history-drafts.js`：

```javascript
/**
 * E2E prompt-builder Phase 6 helpers。
 *
 * 模拟：
 * - listPromptDrafts 返回 N 条历史草稿（initialPhase 控制：'empty' / 'one' / 'many'）
 * - finalizePrompt 成功响应（saveAsDraft 两种模式分别记录请求体）
 * - getSeedAvailability 中 history_draft 跟随 prompt_drafts 数量动态切换
 */

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1,
  userCode: 'ADM2026001',
  username: 'admin.heqh',
  displayName: '平台管理员',
  roles: ['admin'],
  permissions: ['*'],
}

function makeDrafts(initialPhase) {
  if (initialPhase === 'empty') return []
  if (initialPhase === 'one') {
    return [
      {
        id: 1,
        knowledgeBaseId: 7,
        name: '操作系统 · 默认基线 · 2026-05-15',
        description: '综合分 0.62',
        seed: 'system_default',
        candidateId: 'default',
        promptsJson: '{"extract_graph":"-Goal-\\nExtract.\\n"}',
        sourceBuildRunId: 12,
        compositeScore: 0.62,
        createdAt: '2026-05-15T10:00:00',
        updatedAt: '2026-05-15T10:00:00',
      },
    ]
  }
  // many
  return [
    {
      id: 2,
      knowledgeBaseId: 7,
      name: '操作系统 · 图谱感知 · 2026-05-17',
      description: '综合分 0.71',
      seed: 'graphrag_tuned',
      candidateId: 'schema_aware_directional_v2',
      promptsJson: '{"extract_graph":"-Goal-\\nFancy.\\n"}',
      sourceBuildRunId: 18,
      compositeScore: 0.71,
      createdAt: '2026-05-17T10:00:00',
      updatedAt: '2026-05-17T10:00:00',
    },
    {
      id: 1,
      knowledgeBaseId: 7,
      name: '操作系统 · 默认基线 · 2026-05-15',
      description: '综合分 0.62',
      seed: 'system_default',
      candidateId: 'default',
      promptsJson: '{"extract_graph":"-Goal-\\nExtract.\\n"}',
      sourceBuildRunId: 12,
      compositeScore: 0.62,
      createdAt: '2026-05-15T10:00:00',
      updatedAt: '2026-05-15T10:00:00',
    },
  ]
}

export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * @param {object} options
 * @param {number} options.kbId
 * @param {number} options.buildRunId
 * @param {'empty' | 'one' | 'many'} options.initialPhase
 */
export async function installHistoryDraftMocks(
  page,
  { kbId = 7, buildRunId = 18, initialPhase = 'many' } = {},
) {
  const drafts = makeDrafts(initialPhase)
  const finalizeRequests = []
  const draftSnapshot = { value: drafts }

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const method = request.method()

    if (method === 'POST' && path === '/auth/admin/login') {
      return reply(route, 200, {
        code: 200,
        data: {
          accessToken: 'e2e-admin-token',
          tokenType: 'Bearer',
          expiresAt: null,
          user: ADMIN_USER,
        },
      })
    }
    if (method === 'GET' && path === '/auth/me') {
      return reply(route, 200, { code: 200, data: ADMIN_USER })
    }
    if (method === 'GET' && path === `/knowledge-base-build-runs/${buildRunId}`) {
      return reply(route, 200, {
        code: 200,
        data: {
          id: Number(buildRunId),
          knowledgeBaseId: Number(kbId),
          currentStage: 'prompt_confirmation',
          status: 'running',
          buildMetadata: JSON.stringify({
            promptStrategy: 'custom_pipeline',
            customPromptDraft: { seed: 'system_default' },
          }),
        },
      })
    }
    if (method === 'GET' && path === `/knowledge-base-build-runs/${buildRunId}/seed-availability`) {
      return reply(route, 200, {
        code: 200,
        data: {
          options: [
            { key: 'system_default', available: true, reason: null, summary: '使用 GraphRAG 内置默认' },
            { key: 'graphrag_tuned', available: false, reason: 'no_auto_tuned', summary: '本课程暂无自动调优' },
            {
              key: 'history_draft',
              available: draftSnapshot.value.length > 0,
              reason: draftSnapshot.value.length === 0 ? 'no_history_draft' : null,
              summary:
                draftSnapshot.value.length === 0
                  ? '本知识库暂无历史草稿'
                  : `共 ${draftSnapshot.value.length} 条历史草稿可选`,
            },
          ],
          currentSeed: 'system_default',
        },
      })
    }
    if (method === 'GET' && path === `/knowledge-bases/${kbId}/prompt-drafts`) {
      return reply(route, 200, { code: 200, data: draftSnapshot.value })
    }
    if (
      method === 'PUT' &&
      path === `/knowledge-base-build-runs/${buildRunId}/custom-prompt-draft`
    ) {
      return reply(route, 200, { code: 200, data: { id: buildRunId } })
    }
    if (method === 'POST' && path === `/knowledge-base-build-runs/${buildRunId}/finalize`) {
      const body = JSON.parse(request.postData() ?? '{}')
      finalizeRequests.push(body)
      // 若 saveAsDraft=true 则在快照中追加一条，模拟 listPromptDrafts 后续刷新看到新条目
      if (body.saveAsDraft) {
        draftSnapshot.value = [
          {
            id: 99,
            knowledgeBaseId: kbId,
            name: body.draftName,
            description: body.draftDescription,
            seed: 'system_default',
            candidateId: body.candidateId,
            promptsJson: '{"extract_graph":"-Goal-\\nNew.\\n"}',
            sourceBuildRunId: buildRunId,
            compositeScore: 0.71,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
          ...draftSnapshot.value,
        ]
      }
      return reply(route, 200, { code: 200, data: { id: buildRunId } })
    }

    return reply(route, 500, { code: 5000, message: `未配置 mock: ${method} ${path}`, data: null })
  })

  return { finalizeRequests, draftSnapshot }
}

export async function gotoSeedStep(page, { kbId = 7, buildRunId = 18 } = {}) {
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=seed`,
  )
  await page.getByRole('button', { name: '进入平台' }).click()
}

export async function gotoSaveStep(
  page,
  { kbId = 7, buildRunId = 18, selectedCandidate = 'default' } = {},
) {
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=save&selectedCandidate=${selectedCandidate}`,
  )
  await page.getByRole('button', { name: '进入平台' }).click()
}

function reply(route, httpStatus, body) {
  return route.fulfill({
    status: httpStatus,
    headers: jsonHeaders(),
    body: JSON.stringify({
      ...body,
      message: body.message ?? '操作成功',
      timestamp: new Date().toISOString(),
    }),
  })
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'access-control-allow-headers': '*',
  }
}

function jsonHeaders() {
  return { ...corsHeaders(), 'content-type': 'application/json' }
}
```


- [ ] **Step 2：写 spec**

Create `frontend/apps/admin-app/e2e/prompt-builder-history-drafts.spec.js`：

```javascript
import { test, expect } from '@playwright/test'
import {
  loginAsAdmin,
  installHistoryDraftMocks,
  gotoSeedStep,
  gotoSaveStep,
} from './helpers/prompt-builder-history-drafts.js'

test.describe('Phase 6 历史草稿', () => {
  test('01 步：暂无历史草稿时种子卡禁用并显示空态文案', async ({ page }) => {
    await loginAsAdmin(page)
    await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSeedStep(page)

    // 历史草稿卡可见但禁用
    const historyCard = page.getByText('我的历史草稿').first()
    await expect(historyCard).toBeVisible()
    // 文案含"暂无"
    await expect(page.getByText(/暂无/).first()).toBeVisible()
  })

  test('01 步：≥2 条草稿时点击种子卡打开抽屉，选择某条后状态注入', async ({ page }) => {
    await loginAsAdmin(page)
    await installHistoryDraftMocks(page, { initialPhase: 'many' })
    await gotoSeedStep(page)

    // 点 history_draft 卡
    await page.getByText('我的历史草稿').first().click()
    // 抽屉打开，含两条草稿
    const drawer = page.locator('.el-drawer')
    await expect(drawer).toBeVisible()
    await expect(drawer.getByText('图谱感知')).toBeVisible()
    await expect(drawer.getByText('默认基线')).toBeVisible()
    // 点第一条
    await drawer.getByText(/图谱感知 · 2026-05-17/).click()
    // 抽屉关闭，toast 出现
    await expect(page.getByText(/已加载历史草稿/)).toBeVisible()
  })

  test('05 步：仅本次构建模式 → POST /finalize 含 saveAsDraft=false', async ({ page }) => {
    await loginAsAdmin(page)
    const ctx = await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSaveStep(page)

    // 切到"仅本次构建"radio
    await page.getByLabel('仅本次构建').check()
    // 点保存
    await page.getByRole('button', { name: /保存/ }).click()

    // 等到 finalizeRequests 累计到 1 条
    await expect.poll(() => ctx.finalizeRequests.length).toBe(1)
    expect(ctx.finalizeRequests[0]).toMatchObject({
      candidateId: 'default',
      saveAsDraft: false,
    })
  })

  test('05 步：默认含历史草稿模式 → POST /finalize 含 saveAsDraft=true 与 draft 元信息', async ({ page }) => {
    await loginAsAdmin(page)
    const ctx = await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSaveStep(page)

    // 默认就是 build_run_with_history，无需切换；填写草稿名（默认已有，但显式覆盖确保确定性）
    const nameInput = page.getByLabel(/草稿名/).first()
    await nameInput.fill('e2e · 默认基线 · 2026-05-17')
    await page.getByRole('button', { name: /保存/ }).click()

    await expect.poll(() => ctx.finalizeRequests.length).toBe(1)
    expect(ctx.finalizeRequests[0]).toMatchObject({
      candidateId: 'default',
      saveAsDraft: true,
      draftName: 'e2e · 默认基线 · 2026-05-17',
    })
  })
})
```

- [ ] **Step 3：跑 e2e**

Run:
```bash
cd frontend/apps/admin-app && pnpm test:e2e prompt-builder-history-drafts 2>&1 | tail -15
```

Expected：4 个 e2e 全 PASS。

> **注意**：如果"仅本次构建" radio 文案与 SaveStep 实际 label 不一致（例如 "仅本次构建（不入库）"），用 `page.locator('input[value="build_run_only"]')` 等更稳的选择器替代 `getByLabel`。spec 跑红时同时检查：(a) `nameInput` label 是否有 `aria-label`；(b) 保存按钮 name 是否带"并"等字符。这两处 SaveStep 模板里改动频繁，按实际 DOM 调整 selector，但保留断言的业务含义不变。

- [ ] **Step 4：清理产物 + commit**

```bash
cd frontend/apps/admin-app && rm -rf test-results playwright-report
git add frontend/apps/admin-app/e2e/helpers/prompt-builder-history-drafts.js \
        frontend/apps/admin-app/e2e/prompt-builder-history-drafts.spec.js
git commit -m "test(prompt-builder): Phase 6 e2e 覆盖 finalize 两模式 + 历史草稿种子选择 (Phase 6)"
```

---

## Task 11：联合验证 + spec 标记完成

> 收尾：聚合后端 + 前端 + e2e 测试全量跑过，确认 Phase 6 真正完结，更新 spec。

- [ ] **Step 1：跑全部测试套件**

后端：
```bash
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```
Expected：所有 PASS（Phase 6 新增 16 个后端测试：PromptDraftsServiceImplTest 4 + SeedAvailabilityServiceTest 新增 2 + FinalizePromptServiceTest 8 + PromptDraftListServiceTest 2，扣除既有重新运行）。

前端单测：
```bash
cd frontend/apps/admin-app && node --test 2>&1 | tail -10
```
Expected：所有 PASS（Phase 6 新增 11 条：finalize 4 + prompt-drafts 3 + parser 4，扣除既有重新运行）。

前端 e2e：
```bash
cd frontend/apps/admin-app && pnpm test:e2e 2>&1 | tail -8
```
Expected：原有 e2e 全 PASS + Phase 6 新增 4 个 PASS。

- [ ] **Step 2：自检清单走一遍**

打开本计划末尾的"自检清单"，逐项打勾。任一项未达成停止合并。

- [ ] **Step 3：spec 标记 ✅**

修改 `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md`：

定位 `**Phase 6（⏸）**` 段，把 ⏸ 改成 ✅，并补"Phase 6 已落地"小结：

```markdown
- **Phase 6（✅）：05 步历史草稿入库 + 01 步种子打通 + PromptDisplay raw prismjs 高亮**
  - 实现 `POST /knowledge-base-build-runs/{id}/finalize` 端点：从 04 评分 run 取选定候选 prompt + composite_score；FinalizePromptService **直接写** `buildMetadata.customPromptDraft`（不复用 saveCustomPromptDraft），落库**完整 finalize 快照**：`seed` / `selectedCandidateId` / `compositeScore`（4 位小数）/ `sourceEvalRunId` / `finalizedAt`（ISO_OFFSET_DATE_TIME）/ `prompts.extract_graph.content`；当 `saveAsDraft=true` 时同事务插一条 `prompt_drafts` 行（保存范围两模式）。事务语义闭环：`promptDraftsService.save` 返 false 抛 5000 让 customPromptDraft 一并回滚（不依赖 mybatis-plus 静默 false）。
  - 实现 `GET /knowledge-bases/{kbId}/prompt-drafts` 端点：本期重新定义为**列表摘要语义**，返回该 kb 下按 created_at 倒序的 `PromptDraftResponse`（**不含 promptsJson 大字段**），由 `PromptDraftListService` 把 entity 投影成 DTO；mapper 层用 `select` 排除 `prompts_json` 列，避免 600 KB+ 列表响应；草稿详情接口（`GET /knowledge-bases/{kbId}/prompt-drafts/{id}` + `PromptDraftDetailResponse`）留 Phase 7+。
  - 错误码：4110 `EXTRACTION_EVAL_NOT_SUCCESS`（finalize 时 04 评分尚未 success 拒绝）、4111 `INVALID_FINALIZE_CANDIDATE`（candidateId 不在评分报告 candidates 中）；reportJson 解析失败抛 5000 `INTERNAL_ERROR`（区分服务端数据异常与业务入参错误）。
  - `SeedAvailabilityService.buildHistoryDraft` 真实化：注入 `PromptDraftsService.countByKnowledgeBaseId`，count > 0 时 available=true / summary 含数量；count = 0 时 reason="no_history_draft" / summary 友好文案，替代 Phase 4.5 占位 "phase_6_not_implemented"。
  - 前端 `PromptBuilderPage.handleSave` 真发 `POST /finalize`；`onMounted` 调 `listPromptDrafts(kbId)` 填充 `historyDrafts`。
  - 新组件 `PromptBuilderHistoryDraftDrawer.vue`：当 ≥2 条历史草稿时打开抽屉让用户选具体一条；count=1 时直接选取；count=0 时禁用并提示。`loadHistoryDraft` **不**恢复旧 `selectedCandidateId`，主动清空旧 03 / 04 步状态，与 spec § "history_draft 仅是种子来源、不复用旧候选 ID" 一致；`historyDraftId` 仅做前端记账，finalize 仍由 04 评分报告中的 candidateId 决定。
  - `PromptBuilderSeedStep.vue` 删硬编码 "Phase 1e 开放" 文案，按 availability 动态显示数量；reason 文案分支 `phase_6_not_implemented` 替换为 `no_history_draft`。
  - `PromptDisplayRaw.vue` 换 prismjs：自定义 `prompt-tune` 语言识别 section / placeholder / arrow / comment / keyword（课程域实体类型 hardcode 列表），引入 prism-tomorrow 暗色主题；不依赖 prismjs/components/prism-* 任何内置语言以避开 vite 全局 attach 兼容问题。
  - `parsePromptSections` 容错单测落地（spec § 风险 #5）：段落数 < 2 / 单段超长两条路径，验证 caller 据此决定 `fallbackToRaw`。
  - 测试：后端新增 16 个测试 PASS（PromptDraftsServiceImplTest 4 + SeedAvailabilityServiceTest 新增 2 + FinalizePromptServiceTest 8 + PromptDraftListServiceTest 2），前端单测新增 11 个 PASS，Playwright e2e 新增 4 个 PASS（finalize 两模式 + 历史草稿空态 / 抽屉选择）。
```

定位 spec § 风险 #5 的 "在 Phase 6 `<PromptDisplay>` 落地后补..." 备注，更新为已落地：

**oldStr**：
```markdown
   - **Phase 4 已落地缓解（候选 manifest 绝对路径压缩）**：`auto_tuned` 候选的 `base_prompt_source` 在 manifest 中是绝对路径（含 `/home/...`），后端 `CandidateManifestReader.simplifyBasePromptSource` 在透传给前端前压缩为文件名 / 相对路径，避免把服务器路径暴露到浏览器；rich 模式 parser 容错本身留到 Phase 6 `<PromptDisplay>` 落地时一并完善。
```

**newStr**：
```markdown
   - **Phase 4 已落地缓解（候选 manifest 绝对路径压缩）**：`auto_tuned` 候选的 `base_prompt_source` 在 manifest 中是绝对路径（含 `/home/...`），后端 `CandidateManifestReader.simplifyBasePromptSource` 在透传给前端前压缩为文件名 / 相对路径，避免把服务器路径暴露到浏览器。
   - **Phase 6 已落地缓解**：`PromptDisplayRaw` 换成 prismjs 自定义 `prompt-tune` 语言（section / placeholder / arrow / comment / keyword 五类 token + tomorrow 暗色主题）；`parsePromptSections` 容错单测覆盖"段落数 < 2 / 单段超长"两条 fallback raw 路径，PromptDisplay 据此切到 raw 模式并在头部条提示。
```

定位 spec § 已识别风险 #4（Phase 4.5 那段 "history_draft 始终灰显（Phase 6 落地）"）：

**oldStr**：
```markdown
> history_draft 始终灰显（Phase 6 落地）。
```

**newStr**：
```markdown
> history_draft 由 `PromptDraftsService.countByKnowledgeBaseId` 决定可用：count > 0 时可点开抽屉选具体草稿（Phase 6 已落地）。
```

- [ ] **Step 4：commit**

```bash
git add docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md
git commit -m "docs(spec): Phase 6 标记完成 + 风险 #5 / history_draft 备注 (Phase 6)"
```

- [ ] **Step 5：push**

```bash
git push origin feature/prompt-confirmation-step
```

---

## 自检清单

> 实施前主会话过一遍这个清单，对应不上的项停止执行重新调整计划。

### 一致性自检

- [ ] **A1**：错误码 4110 `EXTRACTION_EVAL_NOT_SUCCESS` / 4111 `INVALID_FINALIZE_CANDIDATE` 在所有 Task 中数字一致，未与现有码冲突（4106/4108/4109/5008 已占用）。
- [ ] **A2**：`saveAsDraft` 字段类型在前后端一致：后端 `Boolean`（可为 null），前端 `boolean`（页面层根据 `saveMode === 'build_run_with_history'` 计算）。
- [ ] **A3**：`prompts_json` 序列化口径一致：FinalizePromptService 写入时 `{"extract_graph": "<full text>"}`；PromptDraftListService 透传原值不解码；前端 `listPromptDrafts` 拿到的是 JSON 字符串，需要展示 prompt 内容时由 `<PromptDisplay>` 解析（本期前端只展示 name / candidateId / compositeScore，不读 promptsJson）。
- [ ] **A4**：seed 字段值约定一致：`system_default` / `graphrag_tuned` / `history_draft`（Phase 4.5 + Phase 6 仅用这三种；prompt_drafts.seed 列也写这三种之一，复用 build run 的 currentSeed 快照）。
- [ ] **A5**：`SeedAvailabilityService.buildHistoryDraft` 的 reason 字段统一为 `no_history_draft`（不是 `no_prompt_drafts` 或 `phase_6_not_implemented`），与 SeedStep 前端的 reason 文案分支匹配。
- [ ] **A6**：finalize 写 metadata 的"完整快照"语义统一：FinalizePromptService **直接写** `buildMetadata.customPromptDraft`（**不复用** `saveCustomPromptDraft`，因后者只支持 seed / 全量两条路径，无法兼顾"保留旧 seedSnapshotAt + baseHash + 注入 finalize 快照五字段"），用 ObjectMapper 解析后整体替换 customPromptDraft 子节点；保存阶段调用 `buildRunsService.updateById(buildRun)` 持久化（不调 buildRunControlService）。
- [ ] **A7**：count > 0 时 SeedStep 的 `historyDrafts.length` 与 SeedAvailability 的 `available=true` 双向一致：列表为空时 availability 给出 false（同源数据通过两个端点曝出，前端按 SeedAvailability 决定 disabled 即可）。

### 上下文自检

- [ ] **B1**：`PromptDrafts` entity / `PromptDraftsMapper` / `PromptDraftsService(Impl)` 已在 Phase 2a 落地，本计划仅扩 `countByKnowledgeBaseId`。
- [ ] **B2**：`PromptDraftResponse` / `FinalizePromptRequest` DTO 已在 Phase 2a 落地，本计划无需新建。
- [ ] **B3**：`KnowledgeBaseBuildRunService.saveCustomPromptDraft` 在 Phase 4.5 已支持部分更新合并（仅 seed / 全量更新）；FinalizePromptService 不走它，本期直接 `buildRunsService.updateById` 写 metadata（详见 Task 3 Step 3 与决策 1 第 4 点）。
- [ ] **B4**：`PromptTuneExtractionEvalRunsService.findLatestByBuildRunId` 已在 Phase 5 Task 1 落地，FinalizePromptService 直接复用。
- [ ] **B5**：`BuildRunWorkspaceService.resolve(workspaceUri)` 在 Phase 4 / 5 已被广泛复用。
- [ ] **B6**：前端 `prompt-tune-pipeline.js` 的 `unwrapApiResponse` / 业务错误抛出路径在 Phase 4.5 / 5 已稳定。
- [ ] **B7**：前端 `PromptBuilderPage.persistSeedToBuildRun` 已在 Phase 4.5 Task 9 落地，本计划复用它把 `seed=history_draft` PUT 到 build run。
- [ ] **B8**：前端 `markdown-it` / `prismjs` 已在 admin-app/package.json 声明（无需新增依赖）。

### Spec 覆盖自检

- [ ] **C1**：spec § 05 步保存范围 radio 两模式（仅本次构建 / 含历史草稿入库）覆盖：FinalizePromptService 按 `saveAsDraft` 决定是否插 prompt_drafts（Task 3）。
- [ ] **C2**：spec § 01 步 history_draft 种子卡"05 步入库后才会有内容"覆盖：SeedAvailabilityService 按 prompt_drafts.count 决定 available（Task 2）；前端 PromptBuilderPage onMounted 拉 listPromptDrafts 同步（Task 7）。
- [ ] **C3**：spec § PromptDisplay raw 模式"暗色 IDE 风 / 等宽字体 / 行号 / 语法高亮"覆盖：PromptDisplayRaw 用 prismjs + tomorrow 主题 + 行号（Task 8）。
- [ ] **C4**：spec § 第三方依赖 markdown-it / prismjs 选用理由覆盖：prismjs 在 raw 模式落地（Task 8）；markdown-it 本期不强制落地（决策已在范围段说明保留给 Phase 7+）。
- [ ] **C5**：spec § 风险 #5 prompt parser 容错单测覆盖（Task 9）。
- [ ] **C6**：spec § 错误处理 "05 finalize 失败 → toast + 保留页面状态"覆盖：handleSave 失败时设置 saveError 并弹 ElMessage.error（Task 7）；不刷新页面，不重置表单。

### 依赖自检

- [ ] **D1**：Phase 6 不依赖 Phase 7+ 工件（IndexedDB 离线 / 草稿删改 / 跨 kb 复用）。
- [ ] **D2**：Phase 6 不修改 Phase 5 已落地的评分链路代码（只读 evalRun.reportJson 拿 candidate composite_score）。
- [ ] **D3**：Phase 6 finalize 不主动推进 buildRun.currentStage / qaStatus；与现有 confirmPrompt / saveCustomPromptDraft 保持一致行为。

### 测试覆盖自检

- [ ] **E1**：每个新增类至少 2 个单测：FinalizePromptService 8 / PromptDraftListService 2；扩 PromptDraftsServiceImpl 4 / SeedAvailabilityService 新增 2。
- [ ] **E2**：异常路径测试：4110（finalize 04 未 success）、4111（candidateId 非法）、5000（reportJson 解析失败 / promptDraftsService.save 返回 false 触发回滚）、count=0 / count>0（SeedAvailability 两条）。
- [ ] **E3**：前端 7 个 API 单测：finalize 4（含 saveAsDraft 两模式 + 4110 + 4111）+ prompt-drafts 3（列表 / 空 / URL encoding）。
- [ ] **E4**：parser fallback 容错单测 4 条：正常多段 + 段落 < 2 + 单段超长 + 空字符串边界。
- [ ] **E5**：Playwright e2e 4 条：empty 态种子卡禁用 / many 态打开抽屉选草稿 / saveAsDraft=false 模式 / saveAsDraft=true 模式。

---

## Phase 6 完成判定

- 后端：现有所有测试 PASS + Phase 6 新增测试全部 PASS。
- 前端单测：所有 PASS（含 Phase 6 新增 11 条）。
- Playwright e2e：所有 PASS（含 Phase 6 新增 4 条）。
- spec 中 Phase 6 标记 ✅ + 风险 #5 / history_draft 备注更新。
- 28+ commit push 到 `feature/prompt-confirmation-step` 远端。
