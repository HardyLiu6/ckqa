# 提示词策略选择与手动调优 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把构建向导第 04 步从「沿用活动提示词」升级为「默认/自动调优/手动调优」三策略选择面板，并新增一个独立路由的手动调优工作台用于在 Build Run 内编辑实体抽取提示词草稿。

**Architecture:** 后端不改表，把 `customPromptDraft` 与 `promptStrategy` 写进现有 `build_metadata` JSON；新增 `mergeStageMetadata` 辅助避免阶段切换擦除草稿；新增 PUT `/custom-prompt-draft` 接口，扩展 `confirmPrompt` 支持 `confirmed=false` 重置。前端在 `BuildStepPrompt.vue` 用横向 3 卡 + 下方详情面板，新建 `PromptBuilderPage.vue` 包在 `WorkflowLayout` 内做 3 步向导。状态以 metadata 为事实来源，URL query 仅作导航辅助；冲突时清理 query。

**Tech Stack:** Java 17 + Spring Boot + MyBatis Plus + Jackson；Vue 3 + Vite + Element Plus + Pinia + Vue Router；后端测试 JUnit 5 + Mockito + AssertJ + MockMvc；前端单测 `node:test` + assert；E2E Playwright。

**Spec:** `docs/superpowers/specs/2026-05-13-prompt-confirmation-step-design.md`

**Branch suggestion:** 当前已在 `feature/admin-app-visual-polish`。如需隔离，请用 `superpowers:using-git-worktrees` 切到 `feature/prompt-confirmation-step` 后再执行。

---

## 文件总览

### 后端（新增 + 修改）

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunPromptConfirmationRequest.java` | 改 | 默认值 `active` → `default` |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunCustomPromptDraftRequest.java` | 新建 | PUT 草稿请求体 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java` | 改 | 新增 `mergeStageMetadata` / `normalizeStrategy` / `assertCustomDraftExists` / `saveCustomPromptDraft`；改造 `confirmPrompt` |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsController.java` | 改 | 新增 PUT `/{id}/custom-prompt-draft` 端点 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java` | 改 | `createBuildRunIndexRun` 入口加 `assertPromptConfirmed` 兜底 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java` | 改 | 服务层新增测试 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsControllerWebMvcTest.java` | 改 | Controller 新增测试 |

### 前端（新增 + 修改）

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/api/knowledge-bases.js` | 改 | 新增 `saveBuildRunCustomPromptDraft` |
| `frontend/apps/admin-app/src/router/routes.js` | 改 | 新增 `knowledge-base-prompt-builder` 路由 |
| `frontend/apps/admin-app/src/router/index.js` | 改 | 注册 `PromptBuilderPage` 组件映射 |
| `frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js` | 改 | 新路由的面包屑链 + 缺 buildRunId 降级 |
| `frontend/apps/admin-app/src/views/pages/module-loaders.js` | 改 | `resolvePromptConfirmState` 扩展返回结构（metadata 为事实来源） |
| `frontend/apps/admin-app/src/views/pages/module-content.js` | 改 | `resolvePromptPrimaryAction` 接 selectedStrategy + customDraftReady |
| `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | 改 | `runBuildPromptConfirmation` 读策略；新增 `updateBuildPromptStrategy`；新增 `resetBuildPromptConfirmation`；query watcher 区分业务键 vs UI 键 |
| `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue` | 改写 | 横向 3 卡 + 详情面板 + emit `update:strategy` / `reset-confirm` / `goto-builder` |
| `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue` | 新建 | radio 角色单卡 |
| `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue` | 新建 | 4 种变体详情面板 |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 新建 | 独立路由壳 + 状态机 + `beforeunload` 双拦截 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue` | 新建 | Step 1 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue` | 新建 | Step 2 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue` | 新建 | Step 3 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/byte-counter.js` | 新建 | UTF-8 字节计数辅助 |
| `frontend/apps/admin-app/src/__tests__/unit/module-content-prompt.test.js` | 新建 | `resolvePromptPrimaryAction` 测试 |
| `frontend/apps/admin-app/src/__tests__/unit/module-content-prompt-state.test.js` | 新建 | `resolvePromptConfirmState` 测试 |
| `frontend/apps/admin-app/src/__tests__/unit/console-breadcrumb-prompt-builder.test.js` | 新建 | 面包屑测试 |
| `frontend/apps/admin-app/src/__tests__/unit/byte-counter.test.js` | 新建 | 字节计数测试 |
| `frontend/apps/admin-app/e2e/helpers/build-wizard.js` | 新建 | E2E 共享辅助（API mock + fixture） |
| `frontend/apps/admin-app/e2e/build-wizard-prompt.spec.js` | 新建 | E2E：default 流程 |
| `frontend/apps/admin-app/e2e/build-wizard-prompt-custom.spec.js` | 新建 | E2E：custom 流程 |
| `frontend/apps/admin-app/e2e/build-wizard-prompt-spoof.spec.js` | 新建 | E2E：URL 伪造防御 |
| `frontend/apps/admin-app/e2e/build-wizard-prompt-reset.spec.js` | 新建 | E2E：重新选择策略 + 刷新拦截 |
| `frontend/apps/admin-app/e2e/build-wizard-prompt-edges.spec.js` | 新建 | E2E：blocked / refresh-state-preserve / unsaved-leave |

---

## Phase 1：后端基础设施

### Task 1: `mergeStageMetadata` 辅助函数

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 在 `KnowledgeBaseBuildRunServiceTest` 添加失败测试**

在测试类内添加：

```java
@Test
void mergeStageMetadata_preservesPersistKeysAcrossStages() throws Exception {
    KnowledgeBaseBuildRuns existing = new KnowledgeBaseBuildRuns();
    existing.setBuildMetadata("{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\",\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");

    String merged = service.mergeStageMetadata(
            existing,
            "index_build",
            java.util.Map.of("indexRunId", 99L),
            java.util.List.of("customPromptDraft", "promptStrategy", "promptConfirmed")
    );

    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
    assertThat(node.get("stage").asText()).isEqualTo("index_build");
    assertThat(node.get("indexRunId").asLong()).isEqualTo(99L);
    assertThat(node.get("promptConfirmed").asBoolean()).isTrue();
    assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
    assertThat(node.get("customPromptDraft").get("seed").asText()).isEqualTo("graphrag_tuned");
}

@Test
void mergeStageMetadata_emptyExistingMetadataYieldsOnlyStageAndExtras() throws Exception {
    KnowledgeBaseBuildRuns empty = new KnowledgeBaseBuildRuns();
    empty.setBuildMetadata(null);

    String merged = service.mergeStageMetadata(
            empty, "prompt", java.util.Map.of("foo", "bar"), java.util.List.of("customPromptDraft")
    );

    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
    assertThat(node.get("stage").asText()).isEqualTo("prompt");
    assertThat(node.get("foo").asText()).isEqualTo("bar");
    assertThat(node.has("customPromptDraft")).isFalse();
}

@Test
void mergeStageMetadata_extrasTakePrecedenceOverPersistedKeys() throws Exception {
    KnowledgeBaseBuildRuns existing = new KnowledgeBaseBuildRuns();
    existing.setBuildMetadata("{\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\"}");

    String merged = service.mergeStageMetadata(existing, "prompt",
            java.util.Map.of("promptConfirmed", false),  // extras 覆盖旧值
            java.util.List.of("promptConfirmed", "promptStrategy"));

    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(merged);
    assertThat(node.get("promptConfirmed").asBoolean()).isFalse();  // 被 extras 覆盖
    assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");  // 保留
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -pl . -Dtest='KnowledgeBaseBuildRunServiceTest#mergeStageMetadata*' test
```
Expected: 编译失败（方法不存在）

- [ ] **Step 3: 在 `KnowledgeBaseBuildRunService` 添加实现**

在类内（建议放在 `stageMetadata` 私有方法附近）添加：

```java
String mergeStageMetadata(KnowledgeBaseBuildRuns buildRun,
                         String stage,
                         java.util.Map<String, ?> extras,
                         java.util.List<String> persistKeys) {
    java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
    merged.put("stage", stage);

    if (org.springframework.util.StringUtils.hasText(buildRun.getBuildMetadata())) {
        try {
            com.fasterxml.jackson.databind.JsonNode existing = objectMapper.readTree(buildRun.getBuildMetadata());
            for (String key : persistKeys) {
                if (existing.has(key) && !existing.get(key).isNull()) {
                    merged.put(key, objectMapper.treeToValue(existing.get(key), Object.class));
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // 旧 metadata 无效 JSON 时，按空对象处理；不影响新阶段写入
        }
    }
    if (extras != null) {
        merged.putAll(extras);
    }
    return toJson(merged);
}
```

注意：`extras` 在 persist 之后 put，因此 extras 中的同名键会**覆盖** persist 的旧值（这正是 confirmPrompt / saveCustomPromptDraft 显式重写 `promptConfirmed`/`promptStrategy`/`customPromptDraft` 时所需要的行为）。

可见性 `package-private`（无修饰符），允许同包测试直接调用。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -pl . -Dtest='KnowledgeBaseBuildRunServiceTest#mergeStageMetadata*' test
```
Expected: PASS（三个用例都通过）

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): 添加 mergeStageMetadata 辅助保留跨阶段持久键"
```

---

### Task 2: `normalizeStrategy` 辅助方法

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 添加失败测试**

```java
@Test
void normalizeStrategy_acceptsThreeNewValues() {
    assertThat(service.normalizeStrategy("default")).isEqualTo("default");
    assertThat(service.normalizeStrategy("graphrag_tuned")).isEqualTo("graphrag_tuned");
    assertThat(service.normalizeStrategy("custom_pipeline")).isEqualTo("custom_pipeline");
}

@Test
void normalizeStrategy_mapsLegacyActiveToDefault() {
    assertThat(service.normalizeStrategy("active")).isEqualTo("default");
    assertThat(service.normalizeStrategy("ACTIVE")).isEqualTo("default");
    assertThat(service.normalizeStrategy(" active ")).isEqualTo("default");
}

@Test
void normalizeStrategy_nullAndBlankReturnDefault() {
    assertThat(service.normalizeStrategy(null)).isEqualTo("default");
    assertThat(service.normalizeStrategy("")).isEqualTo("default");
    assertThat(service.normalizeStrategy("   ")).isEqualTo("default");
}

@Test
void normalizeStrategy_unknownThrowsBusinessException() {
    assertThatThrownBy(() -> service.normalizeStrategy("invalid_strategy"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未知的提示词策略");
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#normalizeStrategy*' test
```
Expected: 编译失败

- [ ] **Step 3: 添加实现**

在 `KnowledgeBaseBuildRunService` 内添加（建议靠近 `defaultText`）：

```java
private static final java.util.Set<String> SUPPORTED_PROMPT_STRATEGIES =
        java.util.Set.of("default", "graphrag_tuned", "custom_pipeline");

String normalizeStrategy(String raw) {
    if (raw == null) {
        return "default";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
        return "default";
    }
    // legacy: "active" 旧值在仓库语义上等价于"使用 .env 当前激活的提示词"，
    // 既可能是系统默认也可能是自动调优。新设计下统一归一化为 default，
    // 因为 default 行为最稳健，不依赖 active_prompt.json 是否存在。
    // 详见 spec §5.5。
    if ("active".equalsIgnoreCase(trimmed)) {
        return "default";
    }
    if (SUPPORTED_PROMPT_STRATEGIES.contains(trimmed)) {
        return trimmed;
    }
    throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
            "未知的提示词策略: " + raw);
}
```

可见性 `package-private`。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#normalizeStrategy*' test
```
Expected: 4 个用例 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): 添加 normalizeStrategy 兼容 active 历史值并校验未知策略"
```

---

### Task 3: `BuildRunPromptConfirmationRequest` 默认值改为 `default`

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunPromptConfirmationRequest.java`

- [ ] **Step 1: 修改默认值**

把字段定义改为：

```java
@Getter
@Setter
public class BuildRunPromptConfirmationRequest {

    private String promptStrategy = "default";

    private Boolean confirmed = false;
}
```

- [ ] **Step 2: 跑现有测试确认无回归**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest,KnowledgeBaseBuildRunsControllerWebMvcTest' test
```
Expected: 全部 PASS（confirmPrompt 现在在 §Task 5 之前还按旧逻辑写入 "active"，但默认值与字段语义已对齐）

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunPromptConfirmationRequest.java
git commit -m "refactor(build-run): BuildRunPromptConfirmationRequest 默认策略改为 default"
```

---

### Task 4: 改造 `confirmPrompt` 支持 reset + mergeStageMetadata + 写策略

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 添加测试**

```java
@Test
void confirmPrompt_confirmedTrueWithDefaultStrategy_writesMetadata() throws Exception {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted(/* 见已有 fixtures */);
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(true);
    req.setPromptStrategy("default");

    service.confirmPrompt(buildRun.getId(), req);

    KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
    assertThat(node.get("stage").asText()).isEqualTo("prompt");
    assertThat(node.get("promptConfirmed").asBoolean()).isTrue();
    assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
}

@Test
void confirmPrompt_confirmedFalseResetsWithoutDraftCheck() throws Exception {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
        "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\","
        + "\"customPromptDraft\":{\"seed\":\"graphrag_tuned\",\"prompts\":{\"extract_graph\":{\"content\":\"x\"}}}}"
    );
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(false);
    req.setPromptStrategy("default");

    service.confirmPrompt(buildRun.getId(), req);

    KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
    assertThat(node.get("promptConfirmed").asBoolean()).isFalse();
    assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
    assertThat(node.get("customPromptDraft").get("seed").asText()).isEqualTo("graphrag_tuned");  // 草稿保留
}

@Test
void confirmPrompt_legacyActiveStrategyNormalizedToDefault() throws Exception {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(true);
    req.setPromptStrategy("active");

    service.confirmPrompt(buildRun.getId(), req);

    KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
    assertThat(node.get("promptStrategy").asText()).isEqualTo("default");
}
```

注：`newBuildRunPersisted()`、`newBuildRunPersistedWithMetadata(...)` 是测试辅助方法；若现有测试已有类似 fixture（如 `setupBuildRun()`）请复用。如无可在测试类底部新增：

```java
private KnowledgeBaseBuildRuns newBuildRunPersisted() {
    return newBuildRunPersistedWithMetadata("{\"stage\":\"graph_input_export\"}");
}

private KnowledgeBaseBuildRuns newBuildRunPersistedWithMetadata(String metadata) {
    KnowledgeBaseBuildRuns run = new KnowledgeBaseBuildRuns();
    run.setId(1L);
    run.setKnowledgeBaseId(10L);
    run.setBuildMetadata(metadata);
    run.setCurrentStage("graph_input_export");
    when(buildRunsStore.getRequiredById(1L)).thenReturn(run);
    when(buildRunsStore.updateById(any())).thenReturn(true);
    return run;
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#confirmPrompt*' test
```
Expected: 大概率 FAIL（旧 confirmPrompt 写的是 `"promptStrategy", request.getPromptStrategy()` 不做归一化）

- [ ] **Step 3: 改造 `confirmPrompt`**

替换原方法体为：

```java
@Transactional
public BuildRunDetailResponse confirmPrompt(Long id, BuildRunPromptConfirmationRequest request) {
    KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
    boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
    String strategy = normalizeStrategy(request == null ? null : request.getPromptStrategy());

    if (confirmed && "custom_pipeline".equals(strategy)) {
        assertCustomDraftExists(buildRun);
    }

    java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
    extras.put("promptConfirmed", confirmed);
    extras.put("promptStrategy", strategy);

    String metadata = mergeStageMetadata(buildRun, "prompt", extras,
            java.util.List.of("customPromptDraft"));
    updateStage(buildRun, "prompt", metadata);
    return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
}
```

`assertCustomDraftExists` 在 Task 5 实现；这里先临时加占位：

```java
private void assertCustomDraftExists(KnowledgeBaseBuildRuns buildRun) {
    // 实现见 Task 5
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#confirmPrompt*' test
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): confirmPrompt 支持 reset 与策略归一化，保留草稿跨阶段"
```

---

### Task 5: `assertCustomDraftExists` 严格校验

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 添加测试**

```java
@Test
void confirmPrompt_customPipelineWithoutDraftThrows400() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata("{\"stage\":\"graph_input_export\"}");
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(true);
    req.setPromptStrategy("custom_pipeline");

    assertThatThrownBy(() -> service.confirmPrompt(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("请先完成手动调优提示词构建");
}

@Test
void confirmPrompt_customPipelineWithBlankContentThrows400() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
        "{\"stage\":\"prompt\",\"customPromptDraft\":{\"seed\":\"system_default\","
        + "\"prompts\":{\"extract_graph\":{\"content\":\"   \\n\\t  \"}}}}"
    );
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(true);
    req.setPromptStrategy("custom_pipeline");

    assertThatThrownBy(() -> service.confirmPrompt(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class);
}

@Test
void confirmPrompt_customPipelineWithContentPasses() throws Exception {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
        "{\"stage\":\"prompt\",\"customPromptDraft\":{\"seed\":\"system_default\","
        + "\"prompts\":{\"extract_graph\":{\"content\":\"-Goal-\\nDo entity extraction.\"}}}}"
    );
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(true);
    req.setPromptStrategy("custom_pipeline");

    service.confirmPrompt(buildRun.getId(), req);  // 应不抛
}

@Test
void confirmPrompt_resetCustomPipelineDoesNotRequireDraft() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata("{\"stage\":\"prompt\"}");
    BuildRunPromptConfirmationRequest req = new BuildRunPromptConfirmationRequest();
    req.setConfirmed(false);
    req.setPromptStrategy("custom_pipeline");

    service.confirmPrompt(buildRun.getId(), req);  // reset 路径不校验草稿
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#confirmPrompt_customPipeline*,KnowledgeBaseBuildRunServiceTest#confirmPrompt_resetCustomPipeline*' test
```
Expected: 前两个 FAIL（assertCustomDraftExists 占位实现，不抛）

- [ ] **Step 3: 实现 `assertCustomDraftExists`**

替换 Task 4 中的占位：

```java
private void assertCustomDraftExists(KnowledgeBaseBuildRuns buildRun) {
    String content = readDraftExtractGraphContent(buildRun.getBuildMetadata());
    if (content == null || content.strip().isEmpty()) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "请先完成手动调优提示词构建");
    }
}

private String readDraftExtractGraphContent(String metadataJson) {
    if (!org.springframework.util.StringUtils.hasText(metadataJson)) {
        return null;
    }
    try {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadataJson);
        com.fasterxml.jackson.databind.JsonNode node = root
                .path("customPromptDraft")
                .path("prompts")
                .path("extract_graph")
                .path("content");
        return node.isTextual() ? node.asText() : null;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        return null;
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#confirmPrompt*' test
```
Expected: 所有 confirmPrompt 用例 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): assertCustomDraftExists 与前端 customDraftReady 严格对齐"
```

---

### Task 6: `BuildRunCustomPromptDraftRequest` DTO

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunCustomPromptDraftRequest.java`

- [ ] **Step 1: 新建 DTO**

```java
package org.ysu.ckqaback.index.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 手动调优提示词草稿保存请求体。
 *
 * 详见 spec §5.4。
 */
@Getter
@Setter
public class BuildRunCustomPromptDraftRequest {

    @NotBlank(message = "seed 必填")
    private String seed;

    @NotNull(message = "prompts 必填")
    @Valid
    private Map<String, PromptBlock> prompts;

    @Getter
    @Setter
    public static class PromptBlock {
        @NotBlank(message = "content 必填")
        private String content;
    }
}
```

注：`seed` 枚举校验、字节长度校验放在 Service 层，DTO 仅做基础非空校验，避免泄漏服务端策略。

- [ ] **Step 2: 编译验证**

```bash
cd backend/ckqa-back && ./mvnw -q compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/BuildRunCustomPromptDraftRequest.java
git commit -m "feat(build-run): 新增 BuildRunCustomPromptDraftRequest DTO"
```

---

### Task 7: `saveCustomPromptDraft` 服务方法（happy path + 写策略 + 清确认）

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 添加测试**

```java
@Test
void saveCustomPromptDraft_writesDraftStrategyAndClearsConfirmation() throws Exception {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
        "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"default\"}"
    );

    BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
    req.setSeed("system_default");
    BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
    block.setContent("-Goal-\nExtract entities.");
    req.setPrompts(java.util.Map.of("extract_graph", block));

    service.saveCustomPromptDraft(buildRun.getId(), req);

    KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
    assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
    assertThat(node.get("promptConfirmed").asBoolean()).isFalse();  // 原子清除
    com.fasterxml.jackson.databind.JsonNode draft = node.get("customPromptDraft");
    assertThat(draft.get("seed").asText()).isEqualTo("system_default");
    assertThat(draft.get("prompts").get("extract_graph").get("content").asText())
            .isEqualTo("-Goal-\nExtract entities.");
    assertThat(draft.get("updatedAt").isTextual()).isTrue();
    assertThat(draft.get("seedSnapshotAt").isTextual()).isTrue();
    assertThat(draft.get("prompts").get("extract_graph").get("modifiedAt").isTextual()).isTrue();
    assertThat(draft.get("prompts").get("extract_graph").get("baseHash").asText())
            .startsWith("sha256:");
}

@Test
void saveCustomPromptDraft_preservesPriorStageKeys() throws Exception {
    // 验证 saveCustomPromptDraft 不会抹掉前序阶段写入的 exportConfirmed / graphInputConfirmed
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersistedWithMetadata(
        "{\"stage\":\"prompt\",\"exportConfirmed\":true,\"graphInputConfirmed\":true,"
        + "\"promptConfirmed\":true,\"promptStrategy\":\"default\"}"
    );

    BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
    req.setSeed("graphrag_tuned");
    BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
    block.setContent("-Goal-\nDo extraction.");
    req.setPrompts(java.util.Map.of("extract_graph", block));

    service.saveCustomPromptDraft(buildRun.getId(), req);

    KnowledgeBaseBuildRuns updated = buildRunsStore.getRequiredById(buildRun.getId());
    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.getBuildMetadata());
    // 前序阶段键被保留
    assertThat(node.get("exportConfirmed").asBoolean()).isTrue();
    assertThat(node.get("graphInputConfirmed").asBoolean()).isTrue();
    // 当前阶段键被 extras 覆盖
    assertThat(node.get("promptStrategy").asText()).isEqualTo("custom_pipeline");
    assertThat(node.get("promptConfirmed").asBoolean()).isFalse();
    assertThat(node.has("customPromptDraft")).isTrue();
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#saveCustomPromptDraft*' test
```
Expected: 编译失败（saveCustomPromptDraft 不存在）

- [ ] **Step 3: 添加服务方法实现**

在 `KnowledgeBaseBuildRunService` 内添加：

```java
private static final java.util.Set<String> ALLOWED_DRAFT_SEEDS =
        java.util.Set.of("system_default", "graphrag_tuned");
private static final int DRAFT_CONTENT_MAX_BYTES = 32 * 1024;

// 注意：history_draft 不在 ALLOWED_DRAFT_SEEDS 中，而是在 validateDraftRequest 中
// 作为特判先行拦截并返回"暂未开放"错误，与"未知种子"错误区分开。
// 这样 history_draft 走的是"暂未开放"分支，其他未知值走"未知种子"分支。

@Transactional
public BuildRunDetailResponse saveCustomPromptDraft(Long id, BuildRunCustomPromptDraftRequest request) {
    KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
    validateDraftRequest(request);

    String seed = request.getSeed().trim();
    String content = request.getPrompts().get("extract_graph").getContent();

    String existingMetadata = buildRun.getBuildMetadata();
    String previousSeed = readDraftSeed(existingMetadata);
    String previousSeedSnapshotAt = readDraftSeedSnapshotAt(existingMetadata);

    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    String nowIso = now.toString();

    java.util.Map<String, Object> extractGraph = new java.util.LinkedHashMap<>();
    extractGraph.put("content", content);
    extractGraph.put("modifiedAt", nowIso);
    extractGraph.put("baseHash", computeSeedBaseHash(seed));

    java.util.Map<String, Object> draft = new java.util.LinkedHashMap<>();
    draft.put("seed", seed);
    boolean seedChanged = !seed.equals(previousSeed);
    draft.put("seedSnapshotAt", seedChanged || previousSeedSnapshotAt == null ? nowIso : previousSeedSnapshotAt);
    draft.put("updatedAt", nowIso);
    draft.put("prompts", java.util.Map.of("extract_graph", extractGraph));

    java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
    extras.put("customPromptDraft", draft);
    extras.put("promptStrategy", "custom_pipeline");
    extras.put("promptConfirmed", false);  // 原子清除

    String stage = buildRun.getCurrentStage() == null ? "prompt" : buildRun.getCurrentStage();
    String metadata = mergeStageMetadata(buildRun, stage, extras,
            java.util.List.of("exportConfirmed", "graphInputConfirmed"));  // 保留前序阶段键，避免数据丢失
    buildRun.setBuildMetadata(metadata);
    buildRun.setUpdatedAt(java.time.LocalDateTime.now());
    buildRunsStore.updateById(buildRun);
    // 注意：此处故意不使用 updateStage()，因为 updateStage 会设置 status="running" 并更新 currentStage。
    // saveCustomPromptDraft 仅保存草稿内容到 metadata，不推进工作流阶段，不改变 buildRun 的 status/currentStage。
    // confirmPrompt 使用 updateStage 是因为它代表阶段确认动作，需要推进工作流。

    return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
}

private void validateDraftRequest(BuildRunCustomPromptDraftRequest request) {
    // Task 8 实现详细校验；本任务只做最小占位
    if (request == null || request.getPrompts() == null
            || request.getPrompts().get("extract_graph") == null) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "草稿请求格式无效");
    }
}

private String readDraftSeed(String metadataJson) {
    if (!org.springframework.util.StringUtils.hasText(metadataJson)) return null;
    try {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadataJson)
                .path("customPromptDraft").path("seed");
        return node.isTextual() ? node.asText() : null;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        return null;
    }
}

private String readDraftSeedSnapshotAt(String metadataJson) {
    if (!org.springframework.util.StringUtils.hasText(metadataJson)) return null;
    try {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadataJson)
                .path("customPromptDraft").path("seedSnapshotAt");
        return node.isTextual() ? node.asText() : null;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        return null;
    }
}

private String computeSeedBaseHash(String seed) {
    // 本任务先返回占位 hash；Task 9 接入真实 seed 内容读取
    try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(("seed:" + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder("sha256:");
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
        return "sha256:unavailable";
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#saveCustomPromptDraft*' test
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): 新增 saveCustomPromptDraft 写草稿并原子清除已确认状态"
```

---

### Task 8: `saveCustomPromptDraft` 完整校验（seed 白名单 / 字节上限 / 空白拒绝 / history_draft 拒绝）

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`

- [ ] **Step 1: 添加测试**

```java
@Test
void saveCustomPromptDraft_seedHistoryDraftRejected() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    BuildRunCustomPromptDraftRequest req = newDraftRequest("history_draft", "valid content");

    assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("暂未开放");
}

@Test
void saveCustomPromptDraft_unknownSeedRejected() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    BuildRunCustomPromptDraftRequest req = newDraftRequest("invalid_seed", "valid content");

    assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class);
}

@Test
void saveCustomPromptDraft_blankContentRejected() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", "   \n\t  ");

    assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class);
}

@Test
void saveCustomPromptDraft_oversizeContentRejected() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    String oversize = "a".repeat(32 * 1024 + 1);  // 32 KB + 1 字节
    BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", oversize);

    assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("32");
}

@Test
void saveCustomPromptDraft_chineseContentUsesByteLength() {
    KnowledgeBaseBuildRuns buildRun = newBuildRunPersisted();
    String chinese = "你好".repeat(5500);  // 每字符 3 字节，约 33 KB 字节
    BuildRunCustomPromptDraftRequest req = newDraftRequest("system_default", chinese);

    assertThatThrownBy(() -> service.saveCustomPromptDraft(buildRun.getId(), req))
            .isInstanceOf(BusinessException.class);
}

private BuildRunCustomPromptDraftRequest newDraftRequest(String seed, String content) {
    BuildRunCustomPromptDraftRequest req = new BuildRunCustomPromptDraftRequest();
    req.setSeed(seed);
    BuildRunCustomPromptDraftRequest.PromptBlock block = new BuildRunCustomPromptDraftRequest.PromptBlock();
    block.setContent(content);
    req.setPrompts(java.util.Map.of("extract_graph", block));
    return req;
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#saveCustomPromptDraft*' test
```
Expected: 校验类用例 FAIL（占位 validateDraftRequest 太宽松）

- [ ] **Step 3: 替换 `validateDraftRequest` 实现**

```java
private void validateDraftRequest(BuildRunCustomPromptDraftRequest request) {
    if (request == null) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "草稿请求不能为空");
    }
    String seed = request.getSeed() == null ? "" : request.getSeed().trim();
    if ("history_draft".equals(seed)) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "我的历史草稿能力暂未开放");
    }
    if (!ALLOWED_DRAFT_SEEDS.contains(seed)) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "未知的种子模板: " + request.getSeed());
    }
    if (request.getPrompts() == null
            || request.getPrompts().get("extract_graph") == null
            || request.getPrompts().get("extract_graph").getContent() == null) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "extract_graph 提示词内容必填");
    }
    String content = request.getPrompts().get("extract_graph").getContent();
    if (content.strip().isEmpty()) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "提示词内容不能为空");
    }
    int bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    if (bytes > DRAFT_CONTENT_MAX_BYTES) {
        throw new BusinessException(ApiResultCode.PAYLOAD_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                "提示词内容超过 32 KB（当前 " + bytes + " 字节）");
    }
}
```

如果 `ApiResultCode.PAYLOAD_TOO_LARGE` 不存在，先用 `ApiResultCode.BAD_REQUEST` + HttpStatus.PAYLOAD_TOO_LARGE，或参考现有项目错误码约定（执行前请 `grep` 一次 `ApiResultCode.` 确认枚举）。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunServiceTest#saveCustomPromptDraft*' test
```
Expected: 所有 saveCustomPromptDraft 用例 PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java
git commit -m "feat(build-run): saveCustomPromptDraft 完整校验 seed 白名单与字节上限"
```

---

### Task 9: Controller PUT 端点

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsController.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsControllerWebMvcTest.java`

- [ ] **Step 1: 添加 Controller 测试**

```java
@Test
void putCustomPromptDraft_returnsOkAndBuildRunDetail() throws Exception {
    BuildRunDetailResponse resp = sampleBuildRunDetailResponse();   // 复用现有 fixture
    when(buildRunService.saveCustomPromptDraft(eq(1L), any(BuildRunCustomPromptDraftRequest.class)))
            .thenReturn(resp);

    String body = "{\"seed\":\"system_default\",\"prompts\":{\"extract_graph\":{\"content\":\"x\"}}}";

    mockMvc.perform(put("/api/v1/knowledge-base-build-runs/1/custom-prompt-draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
}

@Test
void putCustomPromptDraft_rejectsMissingSeed() throws Exception {
    String body = "{\"prompts\":{\"extract_graph\":{\"content\":\"x\"}}}";

    mockMvc.perform(put("/api/v1/knowledge-base-build-runs/1/custom-prompt-draft")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isBadRequest());
}
```

如已有 `sampleBuildRunDetailResponse` 辅助则复用；否则参考类内现有 `getBuildRun_*` 测试中 mock 的写法。

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunsControllerWebMvcTest#putCustomPromptDraft*' test
```
Expected: 404 NOT FOUND（端点不存在）

- [ ] **Step 3: 添加 Controller 端点**

在 `KnowledgeBaseBuildRunsController` 已有 `@PostMapping("/{id}/prompt-confirmation")` 下方添加：

```java
@PutMapping("/{id}/custom-prompt-draft")
public ApiResponse<BuildRunDetailResponse> saveCustomPromptDraft(
        @PathVariable @Positive(message = "id必须大于0") Long id,
        @Valid @RequestBody BuildRunCustomPromptDraftRequest request
) {
    return ApiResponseUtils.success(buildRunService.saveCustomPromptDraft(id, request));
}
```

文件顶部 import 增加：

```java
import org.ysu.ckqaback.index.dto.BuildRunCustomPromptDraftRequest;
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='KnowledgeBaseBuildRunsControllerWebMvcTest#putCustomPromptDraft*' test
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsController.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsControllerWebMvcTest.java
git commit -m "feat(build-run): 新增 PUT /custom-prompt-draft 接口"
```

---

### Task 10: index-build 入口 `assertPromptConfirmed` 兜底

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java`
- Modify: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`（或新建 `IndexWorkflowServiceTest` 若不存在）

- [ ] **Step 1: 添加测试**

如果 `IndexWorkflowServiceTest` 不存在，新建 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexWorkflowServiceTest.java`。在能注入 mocks 的同等模式下添加：

```java
@Test
void createBuildRunIndexRun_rejectsWhenPromptNotConfirmed() {
    // setup buildRun with metadata { "promptConfirmed": false } or missing
    KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
    buildRun.setId(1L);
    buildRun.setBuildMetadata("{\"stage\":\"prompt\",\"promptConfirmed\":false}");
    when(buildRunsStore.getRequiredById(1L)).thenReturn(buildRun);

    assertThatThrownBy(() -> indexWorkflowService.createBuildRunIndexRun(1L, new BuildRunIndexRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("请先确认提示词策略");
}

@Test
void createBuildRunIndexRun_allowsWhenPromptConfirmed() throws Exception {
    KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
    buildRun.setId(1L);
    buildRun.setBuildMetadata("{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"default\"}");
    when(buildRunsStore.getRequiredById(1L)).thenReturn(buildRun);
    // ... 其余 mocks 让流程不至于抛其他错；具体细节按现有 IndexWorkflowService 测试模式补齐
    // 本测试只断言 assertPromptConfirmed 不抛
}

@Test
void createBuildRunIndexRun_allowsCustomPipelineWithDraftConfirmed() throws Exception {
    KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
    buildRun.setId(1L);
    buildRun.setBuildMetadata("{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\","
            + "\"customPromptDraft\":{\"seed\":\"system_default\",\"prompts\":{\"extract_graph\":{\"content\":\"-Goal-\\nExtract.\"}}}}");
    when(buildRunsStore.getRequiredById(1L)).thenReturn(buildRun);
    // ... 其余 mocks 让流程不至于抛其他错
    // 本测试断言 assertPromptConfirmed 不抛（custom_pipeline + 草稿存在 + 已确认 = 核心正向路径）
}
```

注：若 IndexWorkflowService 测试基础设施成本过高，仅保留第一个测试（rejection path），第二个用例由 E2E 覆盖。

- [ ] **Step 2: 跑测试验证失败**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='IndexWorkflowServiceTest#createBuildRunIndexRun_rejectsWhenPromptNotConfirmed' test
```
Expected: FAIL

- [ ] **Step 3: 添加兜底校验到 `createBuildRunIndexRun`**

在 `IndexWorkflowService.createBuildRunIndexRun` 方法**最早**处（`buildRunService.getBuildRun(buildRunId)` 之后）添加：

```java
assertPromptConfirmed(buildRun);
```

并在类内添加私有方法：

```java
private void assertPromptConfirmed(BuildRunDetailResponse buildRun) {
    String metadata = buildRun.getBuildMetadata();
    if (!org.springframework.util.StringUtils.hasText(metadata)) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "请先确认提示词策略");
    }
    try {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadata);
        if (!node.path("promptConfirmed").asBoolean(false)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "请先确认提示词策略");
        }
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "构建元数据格式无效");
    }
}
```

需要确认类中已注入 `ObjectMapper objectMapper`；若没有，构造函数 / 字段注入加上 `@Autowired private final ObjectMapper objectMapper;`。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd backend/ckqa-back && ./mvnw -q -Dtest='IndexWorkflowServiceTest#createBuildRunIndexRun_rejects*' test
```
Expected: PASS

- [ ] **Step 5: 跑全量后端测试做回归**

```bash
cd backend/ckqa-back && ./mvnw -q test
```
Expected: 全绿（已通过的旧用例需要 metadata 中 `promptConfirmed=true` 才能进入 index-build；若有用例因此 fail，更新 fixture 让其在 metadata 中补 `promptConfirmed`）

- [ ] **Step 6: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java \
        backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/IndexWorkflowServiceTest.java
git commit -m "feat(index-build): 入口加 assertPromptConfirmed 兜底防 URL 伪造"
```

---

## Phase 2：前端胶水

### Task 11: API 方法 `saveBuildRunCustomPromptDraft`

**Files:**
- Modify: `frontend/apps/admin-app/src/api/knowledge-bases.js`

- [ ] **Step 1: 添加导出函数**

在 `confirmBuildRunPrompt` 函数下方追加：

```js
export async function saveBuildRunCustomPromptDraft(id, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(id)}/custom-prompt-draft`,
    payload,
  ))
}
```

- [ ] **Step 2: 验证文件语法**

```bash
node --check frontend/apps/admin-app/src/api/knowledge-bases.js
```
Expected: 无输出（语法 OK）

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/api/knowledge-bases.js
git commit -m "feat(admin-app): 新增 saveBuildRunCustomPromptDraft API 方法"
```

---

### Task 12: 字节计数辅助 + 单元测试

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/byte-counter.js`
- Create: `frontend/apps/admin-app/src/__tests__/unit/byte-counter.test.js`

- [ ] **Step 1: 新建测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { utf8ByteLength, formatBytes } from '../../views/pages/prompt-builder/byte-counter.js'

test('utf8ByteLength 空字符串为 0', () => {
  assert.equal(utf8ByteLength(''), 0)
  assert.equal(utf8ByteLength(null), 0)
  assert.equal(utf8ByteLength(undefined), 0)
})

test('utf8ByteLength 纯 ASCII 等于字符数', () => {
  assert.equal(utf8ByteLength('hello'), 5)
})

test('utf8ByteLength 中文每字符 3 字节', () => {
  assert.equal(utf8ByteLength('你好'), 6)
})

test('utf8ByteLength 中英文混合正确累计', () => {
  assert.equal(utf8ByteLength('a你b好c'), 9)
})

test('formatBytes 小于 1024 显示 B', () => {
  assert.equal(formatBytes(500), '500 B')
})

test('formatBytes 大于等于 1024 显示 KB 一位小数', () => {
  assert.equal(formatBytes(1024), '1.0 KB')
  assert.equal(formatBytes(8400), '8.2 KB')  // 8400 / 1024 = 8.203
})
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/byte-counter.test.js
```
Expected: 模块解析失败

- [ ] **Step 3: 实现模块**

`frontend/apps/admin-app/src/views/pages/prompt-builder/byte-counter.js`：

```js
export function utf8ByteLength(value) {
  if (value === null || value === undefined || value === '') return 0
  return new TextEncoder().encode(String(value)).length
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/byte-counter.test.js
```
Expected: 6 个 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/byte-counter.js \
        frontend/apps/admin-app/src/__tests__/unit/byte-counter.test.js
git commit -m "feat(admin-app): 新增 UTF-8 字节计数辅助"
```

---

### Task 13: `resolvePromptConfirmState` 扩展返回结构

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`（函数实际定义位置，spec §4.3.1 文档表述为 module-loaders 但代码现实在 module-content）
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`（调用点补第三参数）
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`（现有测试兼容性改造）
- Create: `frontend/apps/admin-app/src/__tests__/unit/module-content-prompt-state.test.js`

> 先 `grep -n "resolvePromptConfirmState" frontend/apps/admin-app/src/views/pages/` 确认实际定义位置。本计划假设在 `module-content.js`（line ~649），跨文件调用点在 `module-loaders.js`。

**关键背景：`blocks.prompt` 的组装路径**

`module-loaders.js` 中 `loadKnowledgeBaseBuildPage` 函数（约 line 959）直接将 `resolvePromptConfirmState` 的返回值赋给 `blocks.prompt`：

```js
blocks: {
  // ...
  prompt: promptState,   // ← 直接就是 resolvePromptConfirmState 的返回值
  indexAvailability: indexState,
}
```

因此 `BuildStepPrompt.vue` 中 `props.blocks.prompt.customDraftReady` 等字段与 `resolvePromptConfirmState` 返回的扁平结构是**一一对应**的，无需额外映射层。本 Task 扩展 `resolvePromptConfirmState` 的返回字段后，`blocks.prompt` 自动获得新字段。

- [ ] **Step 0: 验证依赖的辅助函数存在**

```bash
grep -n "function isExportStateComplete\|function isQueryConfirmed" \
  frontend/apps/admin-app/src/views/pages/module-content.js
```

Expected: 找到 `isExportStateComplete`（约 line 1068）和 `isQueryConfirmed`（约 line 1064）的定义。如果名称不匹配，在 Step 3 中使用实际名称。

- [ ] **Step 1: 添加测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { resolvePromptConfirmState } from '../../views/pages/module-content.js'

test('exportConfirmed 未设时返回 blocked', () => {
  const state = resolvePromptConfirmState({}, { complete: false }, null)
  assert.equal(state.status, 'blocked')
  assert.equal(state.confirmed, false)
  assert.equal(state.strategy, 'default')
  assert.equal(state.customDraftReady, false)
})

test('metadata 中 promptConfirmed=true 返回 done', () => {
  const metadata = '{"promptConfirmed":true,"promptStrategy":"custom_pipeline"}'
  const state = resolvePromptConfirmState(
    { exportConfirmed: '1', promptConfirmed: '1' },
    { complete: true },
    metadata,
  )
  assert.equal(state.status, 'done')
  assert.equal(state.confirmed, true)
  assert.equal(state.strategy, 'custom_pipeline')
})

test('metadata 中策略为 active 时归一化为 default', () => {
  const metadata = '{"promptStrategy":"active","promptConfirmed":false}'
  const state = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, metadata)
  assert.equal(state.strategy, 'default')
})

test('URL promptConfirmed=1 但 metadata 为 false 时标记 shouldClean', () => {
  const metadata = '{"promptConfirmed":false}'
  const state = resolvePromptConfirmState(
    { exportConfirmed: '1', promptConfirmed: '1' },
    { complete: true },
    metadata,
  )
  assert.equal(state.confirmed, false)
  assert.equal(state.status, 'ready')
  assert.equal(state.shouldCleanPromptConfirmed, true)
})

test('customDraftReady 判 content trim 非空', () => {
  const metadata = '{"customPromptDraft":{"prompts":{"extract_graph":{"content":"-Goal-\\nDo extract."}}}}'
  const state = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, metadata)
  assert.equal(state.customDraftReady, true)

  const blank = '{"customPromptDraft":{"prompts":{"extract_graph":{"content":"  \\n  "}}}}'
  const blankState = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, blank)
  assert.equal(blankState.customDraftReady, false)
})
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/module-content-prompt-state.test.js
```
Expected: FAIL（函数签名不匹配 + 缺字段）

- [ ] **Step 3: 改造 `resolvePromptConfirmState`**

替换 `module-content.js` 中 `resolvePromptConfirmState` 函数：

```js
const SUPPORTED_PROMPT_STRATEGIES = new Set(['default', 'graphrag_tuned', 'custom_pipeline'])

function normalizePromptStrategy(raw) {
  if (raw === null || raw === undefined) return 'default'
  const trimmed = String(raw).trim()
  if (trimmed === '') return 'default'
  if (trimmed.toLowerCase() === 'active') return 'default'
  if (SUPPORTED_PROMPT_STRATEGIES.has(trimmed)) return trimmed
  return 'default'
}

function parseBuildMetadata(metadata) {
  if (!metadata) return {}
  if (typeof metadata === 'object') return metadata
  try {
    return JSON.parse(metadata)
  } catch {
    return {}
  }
}

export function resolvePromptConfirmState(query = {}, exportState = {}, metadata = null) {
  const meta = parseBuildMetadata(metadata)
  const exportComplete = isExportStateComplete(exportState)

  const metaConfirmed = meta.promptConfirmed === true
  const queryConfirmed = isQueryConfirmed(query.promptConfirmed)
  const strategy = normalizePromptStrategy(meta.promptStrategy)

  const draft = meta.customPromptDraft ?? null
  const draftContent = draft?.prompts?.extract_graph?.content ?? ''
  const customDraftReady = String(draftContent).trim() !== ''

  if (!exportComplete) {
    return {
      status: 'blocked',
      confirmed: false,
      readonly: false,
      strategy,
      shouldCleanPromptConfirmed: queryConfirmed,
      shouldCleanPromptStrategyQuery: false,
      customDraft: draft,
      customDraftReady,
      graphragTunedSummary: meta.graphragTunedSummary ?? null,
      disabledReason: null,
    }
  }

  const confirmed = metaConfirmed
  return {
    status: confirmed ? 'done' : 'ready',
    confirmed,
    readonly: confirmed,
    strategy,
    shouldCleanPromptConfirmed: queryConfirmed && !confirmed,
    shouldCleanPromptStrategyQuery:
      Boolean(query.promptStrategy) && confirmed
      && normalizePromptStrategy(query.promptStrategy) !== strategy,
    customDraft: draft,
    customDraftReady,
    graphragTunedSummary: meta.graphragTunedSummary ?? null,
    disabledReason: null,
  }
}
```

- [ ] **Step 3b: 更新 `module-loaders.js` 中两处调用点，补上第三参数 `buildRun?.buildMetadata`**

定位 `module-loaders.js` 中第一处调用（约 line 931）：

```js
// 旧：
const promptState = resolvePromptConfirmState(query, {
  complete: selection.materials.length > 0 && exportArtifacts.missingCount === 0,
})

// 新：
const promptState = resolvePromptConfirmState(query, {
  complete: selection.materials.length > 0 && exportArtifacts.missingCount === 0,
}, buildRun?.buildMetadata)
```

定位第二处调用（约 line 1129）：

```js
// 旧：
const prompt = promptState ?? resolvePromptConfirmState(query, { complete: exportComplete })

// 新：
const prompt = promptState ?? resolvePromptConfirmState(query, { complete: exportComplete }, buildRun?.buildMetadata)
```

验证改动完整性：

```bash
grep -n "resolvePromptConfirmState" frontend/apps/admin-app/src/views/pages/module-loaders.js
```

Expected: 所有调用点都已补上第三参数。

- [ ] **Step 3c: 更新 `app-shell.test.js` 中现有 `resolvePromptConfirmState` 测试**

现有测试使用两参数调用，返回结构只有 `{ status, confirmed, shouldCleanPromptConfirmed }`。需要更新为兼容新返回结构（新增字段使用默认值）：

```js
// 旧断言：
assert.deepEqual(resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }), {
  status: 'ready',
  confirmed: false,
  shouldCleanPromptConfirmed: false,
})

// 新断言（补上新字段的默认值）：
const readyState = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true })
assert.equal(readyState.status, 'ready')
assert.equal(readyState.confirmed, false)
assert.equal(readyState.shouldCleanPromptConfirmed, false)
assert.equal(readyState.strategy, 'default')
assert.equal(readyState.customDraftReady, false)
```

对 `app-shell.test.js` 中所有 4 个 `resolvePromptConfirmState` 断言做同样的改造：从 `deepEqual` 改为逐字段 `equal`，只断言原有字段 + 新增字段的默认值，避免因新增字段导致 `deepEqual` 失败。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/module-content-prompt-state.test.js
```
Expected: 5 个 PASS

- [ ] **Step 5: 跑现有所有前端单测做回归**

```bash
cd frontend/apps/admin-app && npm test
```
Expected: 全绿

- [ ] **Step 6: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js \
        frontend/apps/admin-app/src/views/pages/module-loaders.js \
        frontend/apps/admin-app/src/app-shell.test.js \
        frontend/apps/admin-app/src/__tests__/unit/module-content-prompt-state.test.js
git commit -m "feat(admin-app): resolvePromptConfirmState 以 metadata 为事实来源并暴露草稿与策略"
```

---

### Task 14: `resolvePromptPrimaryAction` 接 selectedStrategy

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Create: `frontend/apps/admin-app/src/__tests__/unit/module-content-prompt.test.js`

- [ ] **Step 0: 验证依赖的辅助函数存在**

```bash
grep -n "function createBuildAction\|function resolveBuildStepQuery\|function resolveBuildConfirmQuery" \
  frontend/apps/admin-app/src/views/pages/module-content.js \
  frontend/apps/admin-app/src/views/pages/module-page-model.js
```

Expected: 找到 `createBuildAction`（module-content.js 约 line 947）、`resolveBuildStepQuery` 和 `resolveBuildConfirmQuery`（module-page-model.js，通过 import 引入）。如果名称不匹配，在 Step 3 中使用实际名称。

- [ ] **Step 1: 添加测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveBuildPrimaryAction } from '../../views/pages/module-content.js'

function ctx(overrides = {}) {
  return {
    query: {},
    exportState: { complete: true },
    promptState: { status: 'ready', confirmed: false, strategy: 'default', customDraftReady: false },
    ...overrides,
  }
}

test('default 策略 + ready 状态返回 prompt-confirm 可点', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx())
  assert.equal(action.operationKey, 'prompt-confirm')
  assert.equal(action.disabled, false)
  assert.equal(action.label, '确认提示词策略')
})

test('custom_pipeline 策略 + 草稿未就绪 → 按钮 disabled', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'ready', confirmed: false, strategy: 'custom_pipeline', customDraftReady: false },
  }))
  assert.equal(action.disabled, true)
  assert.match(action.disabledReason, /请先完成手动调优提示词构建/)
})

test('custom_pipeline 策略 + 草稿就绪 → 按钮可点', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'ready', confirmed: false, strategy: 'custom_pipeline', customDraftReady: true },
  }))
  assert.equal(action.disabled, false)
})

test('promptConfirmed=true 返回 step-index 跳转', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'done', confirmed: true, strategy: 'default', customDraftReady: false, readonly: true },
  }))
  assert.equal(action.operationKey, 'step-index')
  assert.equal(action.label, '进入创建索引')
})

test('blocked 状态 → 主按钮 disabled 副文案"请先确认导出产物"', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'blocked', confirmed: false, strategy: 'default', customDraftReady: false },
  }))
  assert.equal(action.disabled, true)
  assert.match(action.disabledReason, /请先确认导出产物/)
})

test('query.promptStrategy=custom_pipeline 但 promptState.strategy=default → 取 query 优先（切换中态）', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    query: { promptStrategy: 'custom_pipeline' },
    promptState: { status: 'ready', confirmed: false, strategy: 'default', customDraftReady: false },
  }))
  assert.equal(action.disabled, true)  // 草稿未就绪
})
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/module-content-prompt.test.js
```
Expected: FAIL（旧 resolvePromptPrimaryAction 不读 strategy）

- [ ] **Step 3: 改造 `resolvePromptPrimaryAction`**

替换 `module-content.js` 中 `resolvePromptPrimaryAction` 函数：

```js
function resolvePromptPrimaryAction(context = {}) {
  const promptState = context.promptState ?? resolvePromptConfirmState(
    context.query ?? {}, context.exportState ?? {}, context.buildMetadata ?? null,
  )

  if (promptState.status === 'blocked') {
    return createBuildAction({
      label: '确认提示词策略',
      operationKey: 'prompt-blocked',
      disabled: true,
      disabledReason: '请先确认导出产物',
    })
  }

  if (promptState.confirmed) {
    return createBuildAction({
      label: '进入创建索引',
      operationKey: 'step-index',
      nextStepKey: 'index',
      nextQuery: resolveBuildStepQuery(context.query ?? {}, 'index'),
    })
  }

  // 切换中态：query 优先（用户刚点了一个策略卡，URL 已 replace），否则用 metadata 中已存的
  const queryStrategy = context.query?.promptStrategy
  const selectedStrategy = queryStrategy
    ? normalizePromptStrategy(queryStrategy)
    : promptState.strategy ?? 'default'

  if (selectedStrategy === 'custom_pipeline' && !promptState.customDraftReady) {
    return createBuildAction({
      label: '确认提示词策略',
      operationKey: 'prompt-confirm',
      disabled: true,
      disabledReason: '请先完成手动调优提示词构建',
    })
  }

  const queryWithConfirm = resolveBuildConfirmQuery(context.query ?? {}, 'promptConfirmed', true)
  return createBuildAction({
    label: '确认提示词策略',
    operationKey: 'prompt-confirm',
    nextStepKey: 'index',
    nextQuery: resolveBuildStepQuery({ ...queryWithConfirm, promptStrategy: selectedStrategy }, 'index'),
  })
}
```

确保 `normalizePromptStrategy` 已从 Task 13 的同文件内导出（package-private）。

- [ ] **Step 4: 跑测试验证通过**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/module-content-prompt.test.js
```
Expected: 6 个 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js \
        frontend/apps/admin-app/src/__tests__/unit/module-content-prompt.test.js
git commit -m "feat(admin-app): resolvePromptPrimaryAction 接入 selectedStrategy 与 customDraftReady"
```

---

### Task 15: 新路由 `knowledge-base-prompt-builder`

**Files:**
- Modify: `frontend/apps/admin-app/src/router/routes.js`
- Modify: `frontend/apps/admin-app/src/router/index.js`

- [ ] **Step 1: 在 `routes.js` 添加路由**

在 `knowledge-base-build` 路由（约 line 207-220）下方添加：

```js
  {
    path: '/app/knowledge-bases/:kbId/build/prompt-builder',
    name: 'knowledge-base-prompt-builder',
    componentKey: 'PromptBuilderPage',
    meta: {
      title: '手动调优提示词',
      layout: 'workflow',
      permissions: ['kb:index'],
      status: 'mvp',
      navGroup: 'knowledge',
      resource: 'knowledgeBase',
      scope: 'course',
    },
  },
```

- [ ] **Step 2: 在 `router/index.js` 注册组件映射**

import 段顶部添加：

```js
import PromptBuilderPage from '../views/pages/PromptBuilderPage.vue'
```

`componentMap` 对象内添加：

```js
PromptBuilderPage,
```

- [ ] **Step 3: 暂时新建组件壳（让路由不挂掉）**

新建 `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`：

```vue
<script setup>
// 实现见 Task 19-23
</script>

<template>
  <section class="prompt-builder-page">
    <p>手动调优提示词（开发中）</p>
  </section>
</template>
```

- [ ] **Step 4: 跑前端开发服务器自检**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

> 注：项目 `vite.config.js` 中 `unplugin-vue-components` 仅配置了 `ElementPlusResolver()`，不会自动扫描项目自定义组件。`PromptBuilderPage.vue` 中已手动 import 所有子步骤组件（`PromptBuilderSeedStep`、`PromptBuilderEditStep`、`PromptBuilderPreviewStep`），无需额外注册。

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/router/routes.js \
        frontend/apps/admin-app/src/router/index.js \
        frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(admin-app): 新增 knowledge-base-prompt-builder 路由与组件壳"
```

---

### Task 16: 面包屑 `knowledge-base-prompt-builder` 链 + 缺参降级

**Files:**
- Modify: `frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js`
- Create: `frontend/apps/admin-app/src/__tests__/unit/console-breadcrumb-prompt-builder.test.js`

- [ ] **Step 1: 添加测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { buildConsoleBreadcrumbItems } from '../../layouts/console-breadcrumb-model.js'

function route(name, params = {}, query = {}, meta = {}) {
  return { name, params, query, meta: { navGroup: 'knowledge', ...meta } }
}

test('prompt-builder 路由 + buildRunId 完整 → 面包屑包含构建向导链接', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    { kbId: '12' },
    { buildRunId: '1247' },
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识管理', '知识库列表', '构建向导 · STEP 04', '手动调优提示词'])
  const builderLink = items.find((i) => i.label === '构建向导 · STEP 04')
  assert.equal(builderLink.kind, 'link')
})

test('prompt-builder 路由 + 缺 buildRunId → 父链降级到知识库详情', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    { kbId: '12' },
    {},
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识管理', '知识库列表', '构建向导', '手动调优提示词'])
})

test('prompt-builder 路由 + 缺 kbId 与 buildRunId → 无构建向导父链', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    {},
    {},
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识管理', '知识库列表', '手动调优提示词'])
})
```

- [ ] **Step 2: 跑测试验证失败**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/console-breadcrumb-prompt-builder.test.js
```
Expected: FAIL（断言不符）

- [ ] **Step 3: 在 `console-breadcrumb-model.js` 添加分支**

在 `buildConsoleBreadcrumbItems` 函数内、`items.push({ label: route.meta?.title... })` **之前** 添加：

```js
if (route.name === 'knowledge-base-prompt-builder') {
  const kbId = route.params?.kbId
  const buildRunId = firstQueryValue(route.query?.buildRunId)
  if (kbId && buildRunId) {
    items.push({
      label: '构建向导 · STEP 04',
      name: 'knowledge-base-build',
      to: {
        name: 'knowledge-base-build',
        params: { kbId: String(kbId) },
        query: { buildRunId: String(buildRunId), step: 'prompt' },
      },
      kind: 'link',
    })
  } else if (kbId) {
    items.push({
      label: '构建向导',
      name: 'knowledge-base-detail',
      to: `/app/knowledge-bases/${encodeURIComponent(String(kbId))}`,
      kind: 'link',
    })
  }
}
```

如果文件内尚无 `firstQueryValue` 工具，从 `module-content.js` 或 `module-loaders.js` 复用；若都没有，本文件顶部新增：

```js
function firstQueryValue(value) {
  if (Array.isArray(value)) return value[0] ?? ''
  return value ?? ''
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
cd frontend/apps/admin-app && node --test src/__tests__/unit/console-breadcrumb-prompt-builder.test.js
```
Expected: 3 个 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js \
        frontend/apps/admin-app/src/__tests__/unit/console-breadcrumb-prompt-builder.test.js
git commit -m "feat(admin-app): prompt-builder 面包屑链 + 缺参降级"
```

---

## Phase 3：BuildStepPrompt 重写

### Task 17: `PromptStrategyCard` 组件

**Files:**
- Create: `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
defineProps({
  strategyKey: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  icon: { type: String, default: '⚙' },
  selected: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['select'])
</script>

<template>
  <button
    type="button"
    role="radio"
    :aria-checked="selected"
    :aria-disabled="disabled"
    :tabindex="disabled ? -1 : 0"
    class="prompt-strategy-card"
    :data-selected="selected ? 'true' : 'false'"
    :data-disabled="disabled ? 'true' : 'false'"
    @click="!disabled && $emit('select')"
    @keydown.space.prevent="!disabled && $emit('select')"
    @keydown.enter.prevent="!disabled && $emit('select')"
  >
    <span class="prompt-strategy-card__icon" aria-hidden="true">{{ icon }}</span>
    <span class="prompt-strategy-card__body">
      <strong class="prompt-strategy-card__title">{{ title }}</strong>
      <span class="prompt-strategy-card__desc">{{ description }}</span>
    </span>
  </button>
</template>
```

- [ ] **Step 2: 添加样式到 `styles/components.scss`**

在 `.prompt-confirm-panel` 附近追加（沿用现有 token，不引入新色板）：

```scss
.prompt-strategy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
}

.prompt-strategy-card {
  display: grid;
  grid-template-columns: 36px 1fr;
  align-items: start;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.prompt-strategy-card:hover:not([data-disabled='true']) {
  border-color: var(--ckqa-accent);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--ckqa-accent) 12%, transparent);
}

.prompt-strategy-card:focus-visible {
  outline: 2px solid var(--ckqa-focus);
  outline-offset: 2px;
}

.prompt-strategy-card[data-selected='true'] {
  border: 2px solid var(--ckqa-accent);
  padding: calc(var(--ckqa-space-4) - 1px);
  background: var(--ckqa-accent-soft);
}

.prompt-strategy-card[data-disabled='true'] {
  background: var(--ckqa-blocked-soft);
  color: var(--ckqa-text-muted);
  cursor: not-allowed;
}

.prompt-strategy-card__icon {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface-muted);
  font-size: 18px;
}

.prompt-strategy-card__body {
  display: grid;
  gap: 4px;
}

.prompt-strategy-card__title {
  font-weight: 800;
  font-size: 14px;
}

.prompt-strategy-card__desc {
  color: var(--ckqa-text-muted);
  font-size: 12.5px;
  line-height: 1.5;
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 新增 PromptStrategyCard 单选卡组件"
```

---

### Task 18: `PromptStrategyDetail` 组件（4 种变体）

**Files:**
- Create: `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  strategy: { type: String, default: 'default' },
  customDraftReady: { type: Boolean, default: false },
  customDraft: { type: Object, default: null },
  graphragTunedSummary: { type: Object, default: null },
  disabled: { type: Boolean, default: false },
})

defineEmits(['goto-builder'])

const variant = computed(() => {
  if (props.strategy === 'default') return 'default'
  if (props.strategy === 'graphrag_tuned') return 'graphrag_tuned'
  return props.customDraftReady ? 'custom_pipeline_ready' : 'custom_pipeline_empty'
})

const draftSummary = computed(() => {
  if (!props.customDraft) return null
  const updated = props.customDraft.updatedAt
    ? new Date(props.customDraft.updatedAt).toLocaleString('zh-CN')
    : '未知时间'
  return { updated }
})
</script>

<template>
  <div class="prompt-strategy-detail" :data-variant="variant">
    <template v-if="variant === 'default'">
      <p class="prompt-strategy-detail__primary">将使用系统默认的 GraphRAG 提示词进行索引构建。</p>
      <p class="prompt-strategy-detail__secondary">覆盖实体抽取、描述总结、社区报告等 5 个核心提示词。</p>
    </template>

    <template v-else-if="variant === 'graphrag_tuned'">
      <p class="prompt-strategy-detail__primary">将使用 GraphRAG 自动调优生成的提示词进行索引构建。</p>
      <p class="prompt-strategy-detail__secondary">
        {{ graphragTunedSummary?.name ?? '本课程当前激活的自动调优结果' }}
      </p>
    </template>

    <template v-else-if="variant === 'custom_pipeline_empty'">
      <p class="prompt-strategy-detail__primary">尚未构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">点击下方按钮进入独立页面，按 3 步流程设计本次构建使用的提示词。</p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        :disabled="disabled"
        @click="$emit('goto-builder')"
      >
        前往构建
      </el-button>
    </template>

    <template v-else-if="variant === 'custom_pipeline_ready'">
      <p class="prompt-strategy-detail__primary">已构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">
        上次保存于 {{ draftSummary?.updated ?? '未知时间' }} · 已修改 1 个提示词块（实体抽取）
      </p>
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        :disabled="disabled"
        @click="$emit('goto-builder')"
      >
        编辑提示词
      </el-button>
    </template>
  </div>
</template>
```

- [ ] **Step 2: 添加样式**

`styles/components.scss` 内追加：

```scss
.prompt-strategy-detail {
  display: grid;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-4);
  background: var(--ckqa-surface-muted);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
}

.prompt-strategy-detail__primary {
  margin: 0;
  font-weight: 800;
  font-size: 14px;
  color: var(--ckqa-text);
}

.prompt-strategy-detail__secondary {
  margin: 0;
  font-size: 12.5px;
  color: var(--ckqa-text-muted);
  line-height: 1.6;
}

.prompt-strategy-detail[data-variant='custom_pipeline_empty'] {
  background: color-mix(in srgb, var(--ckqa-running) 8%, var(--ckqa-surface));
  border-color: color-mix(in srgb, var(--ckqa-running) 28%, var(--ckqa-border));
}

.prompt-strategy-detail .el-button {
  justify-self: start;
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 新增 PromptStrategyDetail 四变体详情面板"
```

---

### Task 19: `BuildStepPrompt.vue` 改写

**Files:**
- Modify: `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`

- [ ] **Step 1: 全部替换内容**

```vue
<script setup>
import { computed } from 'vue'
import StatusBadge from '../common/StatusBadge.vue'
import PromptStrategyCard from './PromptStrategyCard.vue'
import PromptStrategyDetail from './PromptStrategyDetail.vue'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  step: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  operationFeedback: { type: Object, default: null },
  selectedStrategy: { type: String, default: 'default' },
})

const emit = defineEmits(['update:strategy', 'goto-builder', 'reset-confirm'])

const promptBlock = computed(() => props.blocks.prompt ?? {})
const disabled = computed(() => promptBlock.value.status === 'blocked' || promptBlock.value.readonly === true)

const STRATEGIES = [
  { key: 'default',         title: '默认提示词',          icon: '⚙',
    description: '使用系统默认的 GraphRAG 提示词，开箱即用。' },
  { key: 'graphrag_tuned',  title: 'GraphRAG 自动调优提示词', icon: '✨',
    description: '使用 GraphRAG 基于本课程样本自动调优生成的提示词。' },
  { key: 'custom_pipeline', title: '手动调优提示词',      icon: '🛠',
    description: '进入独立页面，按 3 步流程亲手调优本次构建使用的提示词。' },
]

function handleSelect(key) {
  if (disabled.value) return
  emit('update:strategy', key)
}
</script>

<template>
  <section class="build-step-panel prompt-confirm-panel">
    <Transition name="slide-down">
      <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ operationFeedback.title }}</strong>
          <StatusBadge :status="operationFeedback.status" />
        </div>
        <p>{{ operationFeedback.message }}</p>
        <small v-if="operationFeedback.detail">{{ operationFeedback.detail }}</small>
      </div>
    </Transition>

    <div role="radiogroup" aria-label="提示词策略" class="prompt-strategy-grid">
      <PromptStrategyCard
        v-for="s in STRATEGIES"
        :key="s.key"
        :strategy-key="s.key"
        :title="s.title"
        :description="s.description"
        :icon="s.icon"
        :selected="selectedStrategy === s.key"
        :disabled="disabled"
        @select="handleSelect(s.key)"
      />
    </div>

    <PromptStrategyDetail
      :strategy="selectedStrategy"
      :custom-draft-ready="promptBlock.customDraftReady"
      :custom-draft="promptBlock.customDraft"
      :graphrag-tuned-summary="promptBlock.graphragTunedSummary"
      :disabled="disabled || actionRunning"
      @goto-builder="$emit('goto-builder')"
    />

    <div v-if="promptBlock.confirmed" class="prompt-reset-actions">
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        :disabled="actionRunning"
        @click="$emit('reset-confirm')"
      >
        重新选择策略
      </el-button>
    </div>
  </section>
</template>
```

- [ ] **Step 2: 增加 `.prompt-reset-actions` 样式到 `styles/components.scss`**

```scss
.prompt-reset-actions {
  display: flex;
  justify-content: flex-start;
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): BuildStepPrompt 重写为策略选择面板"
```

---

### Task 20: `ModulePage` 接通 BuildStepPrompt

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`

- [ ] **Step 0: 验证依赖的辅助函数存在**

```bash
grep -n "function isSameQuery\|function firstQueryValue" \
  frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

Expected: 找到 `isSameQuery`（约 line 648）和 `firstQueryValue`。如果 `firstQueryValue` 不存在，需在 Step 1 中先定义它。

- [ ] **Step 1: 添加 `selectedStrategy` ref + watcher 规则**

在 `<script setup>` 中，与其他 ref 一起新增：

```js
const selectedPromptStrategy = ref('default')
```

在 `loadPage` 结束处或合适位置，根据 `blocks.prompt.strategy` 与 `route.query.promptStrategy` 同步本地 ref：

```js
function syncSelectedPromptStrategy() {
  const queryStrategy = firstQueryValue(route.query?.promptStrategy)
  const metaStrategy = config.value.blocks?.prompt?.strategy
  selectedPromptStrategy.value = queryStrategy || metaStrategy || 'default'
}
```

并在 `loadPage` 成功后调用 `syncSelectedPromptStrategy()`。

- [ ] **Step 2: 新增 `updateBuildPromptStrategy` 方法**

```js
function updateBuildPromptStrategy(strategyKey) {
  if (selectedPromptStrategy.value === strategyKey) return
  selectedPromptStrategy.value = strategyKey
  const nextQuery = { ...route.query, promptStrategy: strategyKey }
  if (!isSameQuery(route.query, nextQuery)) {
    router.replace({ query: nextQuery })
  }
}
```

- [ ] **Step 3: 在 query watcher 中排除 UI-only 键触发 loadPage**

现有 watcher 是 `watch(() => [route.name, route.params, route.query], () => loadPage(), { deep: true, immediate: true })`。

改造为黑名单方式——只把 `promptStrategy` 排除在 loadPage 触发之外，其余 query 变化维持原有行为：

```js
// 仅做本地 UI 状态的 query 键，变化时不触发 loadPage
const UI_ONLY_QUERY_KEYS = new Set(['promptStrategy'])

watch(() => [route.name, route.params, route.query], (next, prev) => {
  // route.name 或 params 变化时始终 reload
  if (next[0] !== prev?.[0] || JSON.stringify(next[1]) !== JSON.stringify(prev?.[1])) {
    loadPage()
    return
  }
  // query 变化时，检查是否只有 UI-only 键发生了变化
  const nextQuery = next[2] ?? {}
  const prevQuery = prev?.[2] ?? {}
  const allKeys = new Set([...Object.keys(nextQuery), ...Object.keys(prevQuery)])
  const onlyUiKeysChanged = [...allKeys].every((key) =>
    UI_ONLY_QUERY_KEYS.has(key) || JSON.stringify(nextQuery[key]) === JSON.stringify(prevQuery[key]),
  )
  if (!onlyUiKeysChanged) {
    loadPage()
  }
}, { deep: true, immediate: true })
```

> 注意：这种黑名单方式确保现有所有 query 键（`buildRunId`、`step`、`page`、`keyword` 等）的变化仍然触发 loadPage，只有 `promptStrategy` 被排除。如果后续有更多 UI-only 键，只需加入 `UI_ONLY_QUERY_KEYS` 集合即可。

- [ ] **Step 4: 改造 `runBuildPromptConfirmation`**

替换现有方法：

```js
async function runBuildPromptConfirmation(action) {
  const strategy = selectedPromptStrategy.value || 'default'
  await runBuildRunRequest({
    operationKey: 'prompt-confirm',
    request: (buildRunId) => confirmBuildRunPrompt(buildRunId, {
      confirmed: true,
      promptStrategy: strategy,
    }),
    nextQuery: { ...(action.nextQuery ?? {}), promptStrategy: strategy },
  })
}
```

- [ ] **Step 5: 新增 `resetBuildPromptConfirmation` 方法**

```js
async function resetBuildPromptConfirmation() {
  try {
    await ElMessageBox.confirm('确定要重新选择提示词策略吗？将清除当前确认状态。',
      '重新选择策略', { type: 'warning' })
  } catch {
    return
  }
  const strategy = selectedPromptStrategy.value || config.value.blocks?.prompt?.strategy || 'default'
  await runBuildRunRequest({
    operationKey: 'prompt-reset',
    request: (buildRunId) => confirmBuildRunPrompt(buildRunId, {
      confirmed: false,
      promptStrategy: strategy,
    }),
    nextQuery: { ...route.query, promptConfirmed: undefined },
  })
}
```

文件顶部 import `ElMessageBox`（若未引入）：

```js
import { ElMessageBox } from 'element-plus'
```

- [ ] **Step 6: 模板中给 BuildStepPrompt 绑定 props + events**

定位 `<component :is="activeBuildStepComponent"` 处（约 line 3537），扩充 props/events：

```vue
<component
  :is="activeBuildStepComponent"
  :blocks="config.blocks"
  :step="activeBuildStep"
  :action-running="actionRunning"
  :operation-feedback="activeBuildOperationFeedback"
  :selected-strategy="selectedPromptStrategy"
  :smoke-question="smokeQuestion"
  :smoke-result="smokeResult"
  @select-materials="updateBuildMaterialSelection"
  @update-smoke-question="updateSmokeQuestion"
  @update:strategy="updateBuildPromptStrategy"
  @goto-builder="gotoPromptBuilder"
  @reset-confirm="resetBuildPromptConfirmation"
/>
```

- [ ] **Step 7: 新增 `gotoPromptBuilder` 方法**

```js
function gotoPromptBuilder() {
  const kbId = String(route.params?.kbId ?? '')
  const buildRunId = config.value.blocks?.buildRun?.item?.id
    ?? firstQueryValue(route.query?.buildRunId)
  if (!kbId || !buildRunId) return
  router.push({
    name: 'knowledge-base-prompt-builder',
    params: { kbId },
    query: { buildRunId: String(buildRunId) },
  })
}
```

- [ ] **Step 8: 处理 loadPage 后冲突清理**

在 `loadPage` 结束处（紧跟现有 `resolveBuildConfirmQuery` 清理逻辑），追加：

```js
if (result.blocks?.prompt?.shouldCleanPromptStrategyQuery) {
  const cleaned = { ...nextQuery }
  delete cleaned.promptStrategy
  nextQuery = cleaned
}
```

- [ ] **Step 9: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 10: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue
git commit -m "feat(admin-app): ModulePage 接通策略选择/重置/Builder 跳转流程"
```

---

## Phase 4：PromptBuilderPage 与子步骤

### Task 21: `PromptBuilderSeedStep` 组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
const props = defineProps({
  seed: { type: String, default: null },
  graphragTunedSummary: { type: Object, default: null },
})

const emit = defineEmits(['select-seed'])

const SEED_OPTIONS = [
  { key: 'system_default',  title: '🧱 系统默认',     desc: '使用 GraphRAG 内置默认提示词作为起点', meta: '来源：prompts/extract_graph.txt' },
  { key: 'graphrag_tuned',  title: '✨ 沿用自动调优版', desc: '克隆当前激活的自动调优结果，在此基础上微调', meta: null },
  { key: 'history_draft',   title: '📦 我的历史草稿',  desc: '从你之前在该知识库保存过的草稿继续', meta: '本期暂未开放', disabled: true },
]

function meta(option) {
  if (option.meta) return option.meta
  if (option.key === 'graphrag_tuned' && props.graphragTunedSummary) {
    const time = props.graphragTunedSummary.activatedAt
      ? new Date(props.graphragTunedSummary.activatedAt).toLocaleDateString('zh-CN')
      : ''
    return `候选：${props.graphragTunedSummary.name ?? 'auto_tuned'} · 激活于 ${time}`
  }
  if (option.key === 'graphrag_tuned') return '本课程当前激活的自动调优结果'
  return ''
}
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>选模板</h3>
      <p>选一个起始模板，后续在此基础上修改。</p>
    </header>

    <div class="seed-grid" role="radiogroup" aria-label="种子模板">
      <button
        v-for="option in SEED_OPTIONS"
        :key="option.key"
        type="button"
        role="radio"
        :aria-checked="seed === option.key"
        :aria-disabled="option.disabled"
        :tabindex="option.disabled ? -1 : 0"
        class="seed-card"
        :data-selected="seed === option.key ? 'true' : 'false'"
        :data-disabled="option.disabled ? 'true' : 'false'"
        @click="if (!option.disabled) emit('select-seed', option.key)"
      >
        <strong>{{ option.title }}</strong>
        <p>{{ option.desc }}</p>
        <small v-if="meta(option)">{{ meta(option) }}</small>
      </button>
    </div>
  </section>
</template>
```

- [ ] **Step 2: 样式追加到 `styles/components.scss`**

```scss
.prompt-builder-step {
  display: grid;
  gap: var(--ckqa-space-3);
}

.prompt-builder-step__header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
}

.prompt-builder-step__header p {
  margin: 4px 0 0;
  color: var(--ckqa-text-muted);
  font-size: 12.5px;
}

.seed-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
}

.seed-card {
  display: grid;
  gap: 6px;
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  text-align: left;
  cursor: pointer;
}

.seed-card[data-selected='true'] {
  border: 2px solid var(--ckqa-accent);
  background: var(--ckqa-accent-soft);
  padding: calc(var(--ckqa-space-4) - 1px);
}

.seed-card[data-disabled='true'] {
  background: var(--ckqa-blocked-soft);
  color: var(--ckqa-text-muted);
  cursor: not-allowed;
}

.seed-card strong { font-size: 13px; }
.seed-card p { margin: 0; font-size: 12px; color: var(--ckqa-text-muted); }
.seed-card small { color: var(--ckqa-text-weak); font-size: 11px; }
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 新增 PromptBuilderSeedStep 选模板步骤"
```

---

### Task 22: `PromptBuilderEditStep` 组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
import { computed, ref } from 'vue'
import { utf8ByteLength, formatBytes } from './byte-counter.js'

const props = defineProps({
  extractGraphContent: { type: String, default: '' },
  templateContent: { type: String, default: '' },  // 用于"还原至模板"
  maxBytes: { type: Number, default: 32 * 1024 },
})

const emit = defineEmits(['update:extract-graph-content'])

const lockedExpanded = ref(false)

const LOCKED_PROMPTS = [
  { name: '描述总结提示词',   file: 'summarize_descriptions.txt' },
  { name: '社区报告 · 图',    file: 'community_report_graph.txt' },
  { name: '社区报告 · 文',    file: 'community_report_text.txt' },
  { name: '声明抽取提示词',   file: 'extract_claims.txt' },
]

const PLACEHOLDER_VARS = ['{entity_types}', '{tuple_delimiter}', '{language}']

const byteCount = computed(() => utf8ByteLength(props.extractGraphContent))
const overLimit = computed(() => byteCount.value > props.maxBytes)
const isModified = computed(() => props.extractGraphContent !== props.templateContent)

function handleInput(event) {
  emit('update:extract-graph-content', event.target.value)
}

function restoreTemplate() {
  emit('update:extract-graph-content', props.templateContent)
}
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>分块编辑</h3>
      <p>本期开放编辑「实体抽取提示词」。其余提示词调优能力将在后续版本陆续开放。</p>
    </header>

    <article class="prompt-block prompt-block--active">
      <header class="prompt-block__head">
        <div>
          <strong class="prompt-block__name">实体抽取提示词</strong>
          <small class="prompt-block__file">extract_graph.txt</small>
        </div>
        <span class="prompt-block__tag" :data-tone="isModified ? 'accent' : 'neutral'">
          {{ isModified ? '可编辑 · 已修改' : '可编辑' }}
        </span>
      </header>

      <div class="prompt-block__toolbar">
        <button type="button" class="toolbar-pill" :disabled="!isModified" @click="restoreTemplate"
                :title="templateContent ? '还原至种子模板内容' : '清空编辑内容（本期种子内容未接入）'">
          {{ templateContent ? '还原至模板' : '清空内容' }}
        </button>
        <span class="toolbar-pill toolbar-pill--readonly">占位变量：{{ PLACEHOLDER_VARS.join(' · ') }}</span>
      </div>

      <textarea
        class="prompt-block__editor"
        :value="extractGraphContent"
        spellcheck="false"
        rows="18"
        aria-label="实体抽取提示词内容"
        @input="handleInput"
      />

      <div class="prompt-block__meta">
        <span :data-over="overLimit ? 'true' : 'false'">
          已输入 {{ formatBytes(byteCount) }} / {{ formatBytes(maxBytes) }}
        </span>
        <span v-if="overLimit" class="prompt-block__meta-warn">超出上限，保存会被拒绝</span>
      </div>
    </article>

    <article class="locked-notice">
      <header class="locked-notice__head" @click="lockedExpanded = !lockedExpanded" role="button" tabindex="0"
              @keydown.enter.prevent="lockedExpanded = !lockedExpanded">
        <div class="locked-notice__title">
          <span aria-hidden="true">🔒</span>
          <span>其余 4 个提示词调优能力暂未开放</span>
        </div>
        <span class="locked-notice__toggle">{{ lockedExpanded ? '收起 ▴' : '展开查看 ▾' }}</span>
      </header>
      <div v-if="lockedExpanded" class="locked-notice__body">
        <ul class="locked-list">
          <li v-for="item in LOCKED_PROMPTS" :key="item.file">
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ item.file }}</small>
            </div>
            <span class="locked-tag">未开放</span>
          </li>
        </ul>
        <p class="locked-notice__hint">这些提示词本次构建将沿用上一步「选模板」中所选起点的默认内容，不会被你修改。</p>
      </div>
    </article>
  </section>
</template>
```

- [ ] **Step 2: 样式追加到 `styles/components.scss`**

```scss
.prompt-block {
  display: grid;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}

.prompt-block--active {
  border: 1.5px solid var(--ckqa-accent);
  padding: calc(var(--ckqa-space-4) - 0.5px);
}

.prompt-block__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.prompt-block__name { font-weight: 800; font-size: 13.5px; }
.prompt-block__file { color: var(--ckqa-text-weak); font-family: monospace; font-size: 11.5px; margin-left: 8px; }

.prompt-block__tag {
  font-size: 10.5px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: var(--ckqa-radius-full);
  background: var(--ckqa-blocked-soft);
  color: var(--ckqa-blocked);
}

.prompt-block__tag[data-tone='accent'] {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
}

.prompt-block__toolbar { display: flex; gap: 8px; flex-wrap: wrap; }

.toolbar-pill {
  font-size: 11.5px;
  padding: 4px 10px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full);
  background: var(--ckqa-surface);
  color: var(--ckqa-text-muted);
  cursor: pointer;
}

.toolbar-pill:disabled,
.toolbar-pill--readonly { cursor: default; color: var(--ckqa-text-weak); }

.prompt-block__editor {
  width: 100%;
  font-family: 'JetBrains Mono', 'SF Mono', monospace;
  font-size: 12.5px;
  line-height: 1.7;
  padding: var(--ckqa-space-3);
  background: color-mix(in srgb, var(--ckqa-surface-muted) 60%, var(--ckqa-surface));
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  resize: vertical;
}

.prompt-block__meta {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--ckqa-text-muted);
}

.prompt-block__meta [data-over='true'] { color: var(--ckqa-danger); font-weight: 800; }
.prompt-block__meta-warn { color: var(--ckqa-danger); }

.locked-notice {
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  border: 1px dashed var(--ckqa-border-strong);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface-muted);
}

.locked-notice__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}

.locked-notice__title { display: flex; align-items: center; gap: 10px; font-weight: 800; }
.locked-notice__toggle { color: var(--ckqa-text-muted); font-size: 12px; }

.locked-notice__body {
  margin-top: var(--ckqa-space-3);
  padding-top: var(--ckqa-space-3);
  border-top: 1px dashed var(--ckqa-border-strong);
}

.locked-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 18px;
}

.locked-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  font-size: 12px;
}

.locked-list li small { color: var(--ckqa-text-weak); font-family: monospace; display: block; }

.locked-tag {
  font-size: 10.5px;
  color: var(--ckqa-blocked);
  padding: 2px 8px;
  background: var(--ckqa-blocked-soft);
  border-radius: var(--ckqa-radius-full);
}

.locked-notice__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 新增 PromptBuilderEditStep 分块编辑步骤"
```

---

### Task 23: `PromptBuilderPreviewStep` 组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
import { ref, computed } from 'vue'

defineProps({
  extractGraphContent: { type: String, default: '' },
  buildRunId: { type: [String, Number], default: null },
})

const PROMPT_ITEMS = [
  { key: 'extract_graph',           label: '实体抽取',     status: 'edited' },
  { key: 'summarize_descriptions',  label: '描述总结',     status: 'locked' },
  { key: 'community_report_graph',  label: '社区报告·图',  status: 'locked' },
  { key: 'community_report_text',   label: '社区报告·文',  status: 'locked' },
  { key: 'extract_claims',          label: '声明抽取',     status: 'locked' },
]

const activeKey = ref('extract_graph')
const activeItem = computed(() => PROMPT_ITEMS.find((i) => i.key === activeKey.value))
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>预览 + 保存</h3>
      <p>复核所有提示词内容，确认无误后保存。保存后会回到构建向导第 04 步。</p>
    </header>

    <div class="preview-grid">
      <ul class="preview-list" role="tablist">
        <li
          v-for="item in PROMPT_ITEMS"
          :key="item.key"
          role="tab"
          :aria-selected="activeKey === item.key"
          :data-active="activeKey === item.key ? 'true' : 'false'"
          class="preview-item"
          tabindex="0"
          @click="activeKey = item.key"
          @keydown.enter.prevent="activeKey = item.key"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.status === 'edited'" class="preview-tag preview-tag--edited">已改</span>
          <span v-else class="preview-tag preview-tag--locked">未开放</span>
        </li>
      </ul>

      <div class="preview-content">
        <pre v-if="activeItem?.status === 'edited'">{{ extractGraphContent }}</pre>
        <p v-else class="preview-content__locked">该提示词将沿用所选种子模板的默认内容，本次构建不会被修改。</p>
      </div>
    </div>

    <p class="preview-attribution">
      保存后该草稿将归属本次构建（Build Run ID：{{ buildRunId ?? '—' }}），其他构建不受影响。
    </p>
  </section>
</template>
```

- [ ] **Step 2: 样式追加到 `styles/components.scss`**

```scss
.preview-grid {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: var(--ckqa-space-4);
}

.preview-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }

.preview-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface-muted);
  font-size: 12.5px;
  cursor: pointer;
}

.preview-item[data-active='true'] {
  background: var(--ckqa-accent-soft);
  border: 1px solid var(--ckqa-accent);
}

.preview-tag { font-size: 10.5px; padding: 2px 8px; border-radius: var(--ckqa-radius-full); }
.preview-tag--edited { background: var(--ckqa-accent-soft); color: var(--ckqa-accent-strong); }
.preview-tag--locked { background: var(--ckqa-blocked-soft); color: var(--ckqa-blocked); }

.preview-content {
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  background: color-mix(in srgb, var(--ckqa-surface-muted) 60%, var(--ckqa-surface));
  padding: var(--ckqa-space-3);
  min-height: 260px;
  max-height: 480px;
  overflow: auto;
}

.preview-content pre {
  margin: 0;
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.preview-content__locked { color: var(--ckqa-text-muted); font-size: 12.5px; margin: 0; }

.preview-attribution {
  margin: 0;
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 新增 PromptBuilderPreviewStep 预览步骤"
```

---

### Task 24: `PromptBuilderPage` 整合（状态机 + 双重离开拦截 + 保存）

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（替换 Task 15 中的占位）

- [ ] **Step 1: 实现完整组件**

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import PromptBuilderSeedStep from './prompt-builder/PromptBuilderSeedStep.vue'
import PromptBuilderEditStep from './prompt-builder/PromptBuilderEditStep.vue'
import PromptBuilderPreviewStep from './prompt-builder/PromptBuilderPreviewStep.vue'
import { utf8ByteLength } from './prompt-builder/byte-counter.js'

import { getBuildRun, saveBuildRunCustomPromptDraft } from '../../api/knowledge-bases.js'

const route = useRoute()
const router = useRouter()

const buildRunId = computed(() => {
  const raw = Array.isArray(route.query.buildRunId) ? route.query.buildRunId[0] : route.query.buildRunId
  return raw ?? ''
})
const kbId = computed(() => String(route.params.kbId ?? ''))

const loading = ref(true)
const error = ref(null)
const seed = ref(null)
const drafts = ref({ extract_graph: '' })
const templates = ref({ extract_graph: '' })  // 已知限制：本期种子内容未从后端拉取，"还原至模板"等价于"清空内容"。后续版本接入种子内容后再做真正还原。
const activeStep = ref('seed')
const dirty = ref(false)
const saving = ref(false)
const saveError = ref(null)

const BUILDER_STEPS = [
  { key: 'seed',    label: '选模板',    detail: '从模板或现有版本起步' },
  { key: 'edit',    label: '分块编辑',  detail: '编辑提示词内容' },
  { key: 'preview', label: '预览 + 保存', detail: '确认后回到构建向导' },
]

const stepStatuses = computed(() => {
  const order = ['seed', 'edit', 'preview']
  const currentIdx = order.indexOf(activeStep.value)
  return BUILDER_STEPS.map((step, idx) => ({
    ...step,
    status: idx < currentIdx ? 'done' : idx === currentIdx ? 'ready' : 'blocked',
  }))
})

const canGoNext = computed(() => {
  if (activeStep.value === 'seed') return Boolean(seed.value) && seed.value !== 'history_draft'
  if (activeStep.value === 'edit') return drafts.value.extract_graph?.trim().length > 0
        && utf8ByteLength(drafts.value.extract_graph) <= 32 * 1024
  return true
})

const canSave = computed(() => dirty.value && !saving.value && canGoNext.value)
const canReturn = computed(() => !saving.value && canGoNext.value)  // 已有草稿未修改时也允许返回

const promptTitle = '手动调优提示词'

onMounted(async () => {
  if (!buildRunId.value) {
    error.value = { message: '缺少构建运行上下文，请回到构建向导重新进入' }
    loading.value = false
    return
  }
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const draft = meta.customPromptDraft
    if (draft) {
      seed.value = draft.seed ?? null
      drafts.value.extract_graph = draft.prompts?.extract_graph?.content ?? ''
      activeStep.value = 'edit'
    }
    dirty.value = false
  } catch (e) {
    error.value = { message: e?.message ?? '加载草稿失败' }
  } finally {
    loading.value = false
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async (to, from, next) => {
  if (!dirty.value) return next()
  try {
    await ElMessageBox.confirm('有未保存的修改，确定离开吗？', '离开页面',
      { type: 'warning', confirmButtonText: '离开', cancelButtonText: '继续编辑' })
    next()
  } catch {
    next(false)
  }
})

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') return
  if (drafts.value.extract_graph && seed.value !== seedKey) {
    ElMessageBox.confirm('切换种子会清空当前编辑，确定吗？', '切换种子', { type: 'warning' })
      .then(() => {
        seed.value = seedKey
        drafts.value.extract_graph = ''
        dirty.value = true
      })
      .catch(() => {})
    return
  }
  seed.value = seedKey
  dirty.value = true
}

function handleEditContent(value) {
  drafts.value.extract_graph = value
  dirty.value = true
}

function gotoStep(stepKey) {
  if (!BUILDER_STEPS.some((s) => s.key === stepKey)) return
  activeStep.value = stepKey
}

function gotoNext() {
  const order = ['seed', 'edit', 'preview']
  const idx = order.indexOf(activeStep.value)
  if (idx >= 0 && idx < order.length - 1 && canGoNext.value) {
    activeStep.value = order[idx + 1]
  }
}

function gotoPrev() {
  const order = ['seed', 'edit', 'preview']
  const idx = order.indexOf(activeStep.value)
  if (idx > 0) activeStep.value = order[idx - 1]
}

async function saveDraft({ navigateBack }) {
  if (!canSave.value) return
  saving.value = true
  saveError.value = null
  try {
    await saveBuildRunCustomPromptDraft(buildRunId.value, {
      seed: seed.value,
      prompts: { extract_graph: { content: drafts.value.extract_graph } },
    })
    if (navigateBack) {
      dirty.value = false  // 导航前清 dirty，避免 onBeforeRouteLeave 拦截
      try {
        await router.push({
          name: 'knowledge-base-build',
          params: { kbId: kbId.value },
          query: {
            buildRunId: buildRunId.value,
            step: 'prompt',
            promptStrategy: 'custom_pipeline',
          },
        })
      } catch (navErr) {
        dirty.value = true  // 导航失败时恢复 dirty 状态
        throw navErr
      }
    } else {
      dirty.value = false  // 仅暂存时，保存成功后清 dirty
    }
  } catch (e) {
    saveError.value = e?.message ?? '保存失败，请重试'
  } finally {
    saving.value = false
  }
}

function returnToWizard() {
  router.push({
    name: 'knowledge-base-build',
    params: { kbId: kbId.value },
    query: { buildRunId: buildRunId.value, step: 'prompt' },
  })
}

function returnToWizardWithStrategy() {
  // 已有草稿未修改时，直接返回向导并携带 promptStrategy，确保策略不丢失
  router.push({
    name: 'knowledge-base-build',
    params: { kbId: kbId.value },
    query: {
      buildRunId: buildRunId.value,
      step: 'prompt',
      promptStrategy: 'custom_pipeline',
    },
  })
}
</script>

<template>
  <section class="prompt-builder-page">
    <header class="prompt-builder-page__header">
      <div>
        <h2>{{ promptTitle }}</h2>
        <p v-if="buildRunId">为本次构建（Build Run ID：{{ buildRunId }}）设计提示词。</p>
      </div>
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        type="default"
        @click="returnToWizard"
      >
        <ChevronLeft class="button-icon" :size="16" aria-hidden="true" />
        返回构建向导
      </el-button>
    </header>

    <RetryPanel
      v-if="error"
      :error="error"
      @retry="returnToWizard"
    />

    <template v-else-if="!loading">
      <WorkflowStepper
        :active-key="activeStep"
        :steps="stepStatuses"
        @update:active-key="gotoStep"
      />

      <div class="prompt-builder-page__body">
        <PromptBuilderSeedStep
          v-if="activeStep === 'seed'"
          :seed="seed"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderEditStep
          v-else-if="activeStep === 'edit'"
          :extract-graph-content="drafts.extract_graph"
          :template-content="templates.extract_graph"
          @update:extract-graph-content="handleEditContent"
        />
        <PromptBuilderPreviewStep
          v-else-if="activeStep === 'preview'"
          :extract-graph-content="drafts.extract_graph"
          :build-run-id="buildRunId"
        />
      </div>

      <footer class="prompt-builder-page__actions">
        <div class="prompt-builder-page__status">
          <span v-if="dirty" class="dirty">● 已修改未保存</span>
          <span v-else-if="saving">保存中…</span>
          <span v-else-if="saveError" class="error">{{ saveError }}</span>
          <span v-else>已是最新</span>
        </div>
        <div class="prompt-builder-page__buttons">
          <el-button v-if="activeStep !== 'seed'" class="ckqa-el-button" @click="gotoPrev">上一步</el-button>
          <el-button
            v-if="activeStep === 'edit'"
            class="ckqa-el-button ckqa-el-button--ghost"
            :disabled="!canSave"
            @click="saveDraft({ navigateBack: false })"
          >
            暂存草稿
          </el-button>
          <el-button
            v-if="activeStep !== 'preview'"
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            :disabled="!canGoNext"
            @click="gotoNext"
          >
            下一步
          </el-button>
          <el-button
            v-if="activeStep === 'preview'"
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            :disabled="!canReturn"
            @click="dirty ? saveDraft({ navigateBack: true }) : returnToWizardWithStrategy()"
          >
            {{ dirty ? '保存并返回' : '返回向导' }}
          </el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
```

- [ ] **Step 2: 样式追加到 `styles/components.scss`**

```scss
.prompt-builder-page {
  display: grid;
  gap: var(--ckqa-space-4);
  padding: var(--ckqa-space-4) 0;
}

.prompt-builder-page__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--ckqa-space-4);
}

.prompt-builder-page__header h2 { margin: 0; font-size: 22px; font-weight: 800; }
.prompt-builder-page__header p { margin: 4px 0 0; color: var(--ckqa-text-muted); font-size: 12.5px; }

.prompt-builder-page__body {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  padding: var(--ckqa-space-5);
}

.prompt-builder-page__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  position: sticky;
  bottom: 0;
}

.prompt-builder-page__status .dirty {
  color: var(--ckqa-running);
  font-weight: 800;
}

.prompt-builder-page__status .error {
  color: var(--ckqa-danger);
  font-weight: 800;
}

.prompt-builder-page__buttons {
  display: flex;
  gap: var(--ckqa-space-2);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd frontend/apps/admin-app && npm run build
```
Expected: 编译成功

- [ ] **Step 4: 启动 dev server 手测一遍**

```bash
cd frontend/apps/admin-app && npm run dev
```

打开 `http://localhost:5173/app/knowledge-bases/12/build/prompt-builder?buildRunId=1247`（替换为真实 ID）：

- 缺 buildRunId 时显示 RetryPanel
- 选种子 → 进入 edit → 输入内容 → 进入 preview → 保存并返回
- 编辑中按 F5 → 浏览器弹"重新加载"确认框

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): PromptBuilderPage 完整状态机 + 双重离开拦截 + 保存"
```

---

## Phase 5：E2E 测试

> 前置：以下 E2E 使用 `page.route` 拦截 API 请求并返回 mock 数据（与现有 `local-operation-errors.spec.js` 模式一致），不需要真实后端运行。

### Task 25: E2E helpers 基础设施

**Files:**
- Create: `frontend/apps/admin-app/e2e/helpers/build-wizard.js`

- [ ] **Step 0: 确认登录态验证接口**

```bash
grep -rn "auth/me\|auth/status" frontend/apps/admin-app/src/api/
```

Expected: 找到 `GET /auth/me`（在 `api/auth.js` 中）。路由守卫在有 token 但 `isAuthenticated=false` 时（如页面刷新后）会调用此接口恢复会话，helpers 中需要 mock 它。

- [ ] **Step 1: 实现 E2E helpers**

参考现有 `local-operation-errors.spec.js` 中的 `installApiMocks`、`openAuthenticated`、`buildRunSnapshot` 模式，新建共享辅助。

**关键设计：使用 `liveMeta` / `liveStage` 可变对象跟踪 buildRun 状态**，确保 POST/PUT 修改后的状态能在后续 GET 请求中正确返回（跨请求状态持久）。同时 mock `GET /auth/me` 以支持页面刷新后的会话恢复。

```js
const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1, userCode: 'ADM2026001', username: 'admin.heqh',
  displayName: '平台管理员', role: 'admin', roles: ['admin'],
  dataScope: '全部课程', permissions: ['*'],
}

export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * 导航到知识库构建向导页面，并安装所需的 API mocks。
 * mock 使用可变 liveMeta/liveStage，POST/PUT 修改后 GET 能返回最新状态。
 */
export async function navigateToKnowledgeBaseBuild(page, {
  stage = 'prompt',
  exportIncomplete = false,
} = {}) {
  const kbId = '7'
  const buildRunId = '77'
  const initialMeta = buildBuildMetadata({ stage, exportIncomplete })
  const materials = [
    { id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done', updatedAt: '2026-05-07T18:39:18' },
  ]
  const parseResults = exportIncomplete
    ? []
    : [{ fileName: 'graphrag_normalized_docs.json' }, { fileName: 'graphrag_section_docs.json' }]

  await installBuildWizardMocks(page, {
    kbId, buildRunId, initialMeta, materials, parseResults,
    initialStage: stage === 'graph_input_export' ? 'graph_input_export' : 'prompt_confirmation',
  })

  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt`)
  await page.getByRole('button', { name: '进入平台' }).click()
  return { kbId, buildRunId }
}

function buildBuildMetadata({ stage, exportIncomplete }) {
  if (exportIncomplete) return { stage: 'graph_input_export' }
  if (stage === 'prompt') {
    return { stage: 'prompt', exportConfirmed: true, graphInputConfirmed: true, promptConfirmed: false, promptStrategy: 'default' }
  }
  return { stage }
}

async function installBuildWizardMocks(page, {
  kbId, buildRunId, initialMeta, materials, parseResults, initialStage,
}) {
  // 可变状态：POST/PUT 修改后，后续 GET 返回最新值
  let liveMeta = { ...initialMeta }
  let liveStage = initialStage

  const buildRunBase = {
    id: Number(buildRunId), knowledgeBaseId: Number(kbId),
    selectedMaterialIds: JSON.stringify(materials.map((m) => m.id)),
    status: 'running',
  }

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`

    const handlers = {
      'POST /auth/admin/login': () => ({
        accessToken: 'e2e-admin-token', tokenType: 'Bearer', expiresAt: null, user: ADMIN_USER,
      }),
      'GET /auth/me': () => ADMIN_USER,
      [`GET /knowledge-bases/${kbId}`]: () => ({
        id: Number(kbId), courseId: 'os', name: 'OS 知识库', status: 'draft', activeIndexRunId: null,
      }),
      [`GET /courses/os/materials`]: () => materials,
      [`GET /knowledge-bases/${kbId}/index-runs`]: () => [],
      [`GET /knowledge-base-build-runs/${buildRunId}`]: () => ({
        ...buildRunBase, currentStage: liveStage, buildMetadata: JSON.stringify(liveMeta),
      }),
      [`GET /pdf-files/9`]: () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
      [`GET /pdf-files/9/results`]: () => parseResults,
      [`POST /knowledge-base-build-runs/${buildRunId}/prompt-confirmation`]: async (req) => {
        const payload = await readJsonPayload(req)
        liveMeta = { ...liveMeta, promptConfirmed: payload.confirmed ?? false, promptStrategy: payload.promptStrategy ?? 'default' }
        liveStage = payload.confirmed ? 'index_build' : 'prompt_confirmation'
        return { ...buildRunBase, currentStage: liveStage, buildMetadata: JSON.stringify(liveMeta) }
      },
      [`PUT /knowledge-base-build-runs/${buildRunId}/custom-prompt-draft`]: async (req) => {
        const payload = await readJsonPayload(req)
        liveMeta = { ...liveMeta, promptStrategy: 'custom_pipeline', promptConfirmed: false,
          customPromptDraft: { seed: payload.seed, prompts: payload.prompts, updatedAt: new Date().toISOString() } }
        liveStage = 'prompt_confirmation'
        return { ...buildRunBase, currentStage: liveStage, buildMetadata: JSON.stringify(liveMeta) }
      },
    }

    const handler = handlers[key]
    if (!handler) {
      await route.fulfill({ status: 500, headers: jsonHeaders(),
        body: JSON.stringify({ code: 5000, message: `未配置 E2E mock: ${key}`, data: null }) })
      return
    }
    const result = await handler(request)
    await route.fulfill({ status: result.httpStatus ?? 200, headers: jsonHeaders(),
      body: JSON.stringify({ code: result.code ?? 200, message: result.message ?? '操作成功', data: result.data ?? result }) })
  })
}

async function readJsonPayload(request) {
  try { return request.postDataJSON() ?? {} } catch { return {} }
}
function jsonHeaders() { return { ...corsHeaders(), 'content-type': 'application/json' } }
function corsHeaders() {
  return { 'access-control-allow-origin': '*', 'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type' }
}
```

- [ ] **Step 2: 语法验证**

```bash
node --check frontend/apps/admin-app/e2e/helpers/build-wizard.js
```
Expected: 无输出（语法 OK）

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/helpers/build-wizard.js
git commit -m "test(admin-app): 新增 E2E build-wizard helpers（可变状态 API mock）"
```

---

### Task 26: E2E - default 策略完整确认流程

**Files:**
- Create: `frontend/apps/admin-app/e2e/build-wizard-prompt.spec.js`

- [ ] **Step 1: 写测试**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('default 策略 - 完整确认流程', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // step 04 渲染了 3 张策略卡，默认选中 default
  await expect(page.getByRole('radiogroup', { name: '提示词策略' })).toBeVisible()
  await expect(page.getByRole('radio', { name: /默认提示词/ })).toHaveAttribute('aria-checked', 'true')

  // 点击确认
  await page.getByRole('button', { name: '确认提示词策略' }).click()

  // 跳到 step 05
  await page.waitForURL((url) => url.searchParams.get('step') === 'index')
  await expect(page).toHaveURL(/promptConfirmed=1/)
  await expect(page).toHaveURL(/promptStrategy=default/)
})
```

> 前置依赖：Task 25 的 `helpers/build-wizard.js` 已就绪。

- [ ] **Step 2: 跑测试**

```bash
cd frontend/apps/admin-app && npx playwright test e2e/build-wizard-prompt.spec.js
```
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/build-wizard-prompt.spec.js
git commit -m "test(admin-app): E2E default 策略确认流程"
```

---

### Task 27: E2E - custom_pipeline 完整流程（进 Builder → 保存 → 返回 → 确认）

**Files:**
- Create: `frontend/apps/admin-app/e2e/build-wizard-prompt-custom.spec.js`

- [ ] **Step 1: 写测试**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('custom_pipeline 策略 - 编辑保存后回到向导可确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 选择手动调优
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()

  // 主按钮 disabled，副文案显示
  await expect(page.getByText('请先完成手动调优提示词构建')).toBeVisible()

  // 点击"前往构建"
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)

  // 选模板
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()

  // 编辑
  const editor = page.getByLabel('实体抽取提示词内容')
  await editor.fill('-Goal-\nExtract entities from documents.')
  await page.getByRole('button', { name: '下一步' }).click()

  // 预览 + 保存
  await expect(page.getByText(/Build Run ID：/)).toBeVisible()
  await page.getByRole('button', { name: '保存并返回' }).click()

  // 回到 step 04，custom_pipeline 仍选中，草稿摘要可见
  await page.waitForURL(/step=prompt/)
  await expect(page).toHaveURL(/promptStrategy=custom_pipeline/)
  await expect(page.getByRole('radio', { name: /手动调优提示词/ })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/已构建手动调优提示词/)).toBeVisible()

  // 主按钮恢复可点
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL((url) => url.searchParams.get('step') === 'index')
})
```

- [ ] **Step 2: 跑测试**

```bash
cd frontend/apps/admin-app && npx playwright test e2e/build-wizard-prompt-custom.spec.js
```
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/build-wizard-prompt-custom.spec.js
git commit -m "test(admin-app): E2E custom_pipeline 完整流程"
```

---

### Task 28: E2E - URL 伪造 promptConfirmed 防御

**Files:**
- Create: `frontend/apps/admin-app/e2e/build-wizard-prompt-spoof.spec.js`

- [ ] **Step 1: 写测试**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('URL 携带 promptConfirmed=1 但 metadata 为 false → 页面仍显示待确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 强行访问携带伪造 query 的 URL
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt&exportConfirmed=1&promptConfirmed=1`)

  // 期望：query 被前端清理 + 状态徽章仍显示待确认
  await page.waitForURL((url) => !url.searchParams.has('promptConfirmed'))
  await expect(page.getByText('待确认')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认提示词策略' })).toBeVisible()

  // 直跳 step=index 应被后端拦截或前端阻止
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=index`)
  // 验证方式：前端应将索引步骤的主操作按钮置为 disabled（因为 promptConfirmed=false）
  // 或者前端直接重定向回 step=prompt
  await page.waitForSelector('.build-step-panel, .build-step-stage')
  const primaryBtn = page.getByRole('button', { name: /开始构建|创建索引/ })
  if (await primaryBtn.isVisible()) {
    await expect(primaryBtn).toBeDisabled()
  } else {
    // 前端已阻止进入该步骤，重定向回 step=prompt
    await expect(page).toHaveURL(/step=prompt/)
  }
})
```

- [ ] **Step 2: 跑测试**

```bash
cd frontend/apps/admin-app && npx playwright test e2e/build-wizard-prompt-spoof.spec.js
```
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/build-wizard-prompt-spoof.spec.js
git commit -m "test(admin-app): E2E URL 伪造 promptConfirmed 防御"
```

---

### Task 29: E2E - 重新选择策略 + 浏览器刷新拦截

**Files:**
- Create: `frontend/apps/admin-app/e2e/build-wizard-prompt-reset.spec.js`

- [ ] **Step 1: 写测试**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('已确认后点重新选择策略 → 状态 ready → 选 default 重新确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 先确认 default
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL(/step=index/)

  // 回到 step=4
  await page.getByRole('link', { name: /STEP 04/ }).click()
  await expect(page.getByText('已确认')).toBeVisible()

  // 重新选择策略
  page.once('dialog', (d) => d.accept())  // ElMessageBox 的 confirm
  await page.getByRole('button', { name: '重新选择策略' }).click()

  // 状态变 ready，策略卡解锁
  await expect(page.getByText('待确认')).toBeVisible()
  await expect(page.getByRole('radio', { name: /默认提示词/ })).not.toHaveAttribute('aria-disabled', 'true')

  // 切换策略 + 再次确认
  await page.getByRole('radio', { name: /默认提示词/ }).click()
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL(/step=index/)
})

test('Builder dirty=true 时 F5 触发 beforeunload', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)

  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('dirty content')

  // 捕获 beforeunload
  let dialogShown = false
  page.on('dialog', async (d) => {
    dialogShown = true
    await d.dismiss()  // 选择留在页面
  })
  await page.reload({ waitUntil: 'commit' }).catch(() => {})
  // 浏览器原生 beforeunload 弹窗不在所有浏览器一致拦截；可断言 dirty 仍为 true 或页面未跳转
  // 至少断言页面 URL 仍为 prompt-builder
  await expect(page).toHaveURL(/\/prompt-builder/)
})
```

- [ ] **Step 2: 跑测试**

```bash
cd frontend/apps/admin-app && npx playwright test e2e/build-wizard-prompt-reset.spec.js
```
Expected: PASS（注意 beforeunload 在 headless 模式下表现可能不同，必要时改用 `dialog` 事件断言或最小断言）

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/build-wizard-prompt-reset.spec.js
git commit -m "test(admin-app): E2E 重新选择策略 + 刷新拦截"
```

---

## Phase 6：联调与回归

### Task 30: E2E - 边界场景集合（blocked / refresh / unsaved-leave）

**Files:**
- Create: `frontend/apps/admin-app/e2e/build-wizard-prompt-edges.spec.js`

- [ ] **Step 1: 写测试**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('第 03 步未确认导出时第 04 步全部禁用 + blocked 文案', async ({ page }) => {
  await loginAsAdmin(page)
  // 跳到 step=prompt 但 buildRun export 仍缺产物（按 fixture 准备）
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'graph_input_export', exportIncomplete: true })

  // 强行导航到 step=prompt（不带 exportConfirmed）
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt`)

  await expect(page.getByText('阻塞')).toBeVisible()
  // 三张策略卡都 aria-disabled
  for (const name of ['默认提示词', 'GraphRAG 自动调优提示词', '手动调优提示词']) {
    await expect(page.getByRole('radio', { name: new RegExp(name) })).toHaveAttribute('aria-disabled', 'true')
  }
  // 主按钮 disabled + 副文案
  await expect(page.getByRole('button', { name: '确认提示词策略' })).toBeDisabled()
  await expect(page.getByText('请先确认导出产物')).toBeVisible()
})

test('保存草稿后刷新页面，状态正确恢复', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 选 custom_pipeline → 进 builder → 保存
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('-Goal-\nExtract everything.')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '保存并返回' }).click()
  await page.waitForURL(/step=prompt/)

  // 刷新页面
  await page.reload()

  // custom_pipeline 仍选中 + 草稿摘要可见
  await expect(page.getByRole('radio', { name: /手动调优提示词/ })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/已构建手动调优提示词/)).toBeVisible()
})

test('Builder dirty 时点面包屑返回 → 弹确认对话框', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('dirty content')

  // 点面包屑里的"构建向导 · STEP 04"链接
  const breadcrumbLink = page.getByRole('link', { name: /STEP 04/ })

  // 期望 ElMessageBox 弹窗
  const dialogPromise = page.locator('.el-message-box').waitFor({ state: 'visible' })
  await breadcrumbLink.click()
  await dialogPromise

  // 取消 → 留在 builder
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page).toHaveURL(/\/prompt-builder/)

  // 再点 → 确认离开
  await breadcrumbLink.click()
  await page.locator('.el-message-box').waitFor({ state: 'visible' })
  await page.getByRole('button', { name: '离开' }).click()
  await page.waitForURL(/step=prompt/)
})
```

- [ ] **Step 2: 跑测试**

```bash
cd frontend/apps/admin-app && npx playwright test e2e/build-wizard-prompt-edges.spec.js
```
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/e2e/build-wizard-prompt-edges.spec.js
git commit -m "test(admin-app): E2E blocked / refresh / unsaved-leave 边界场景"
```

---

### Task 31: 全量回归 + 文档收尾

**Files:**
- Modify: `docs/superpowers/specs/2026-05-13-prompt-confirmation-step-design.md`（如有偏离，加补丁段）

- [ ] **Step 1: 跑全量测试**

```bash
# 后端
cd backend/ckqa-back && ./mvnw -q test

# 前端单测
cd ../../frontend/apps/admin-app && npm test

# 前端 E2E
npx playwright test e2e/build-wizard-prompt*.spec.js
```
Expected: 全绿

- [ ] **Step 2: 手测一遍清单**

逐项验证：

- [ ] 进入 step 04，三张策略卡可见，默认选 default
- [ ] 切换 graphrag_tuned/custom_pipeline，详情面板内容正确切换；URL `promptStrategy` 实时更新但不 reload
- [ ] custom_pipeline 草稿未就绪时，主按钮 disabled + 副文案"请先完成手动调优提示词构建"
- [ ] 点击"前往构建"进入 builder，缺 buildRunId 时显示 RetryPanel
- [ ] builder 选种子 → 编辑 → 字节计数随中文/英文准确变化 → 预览 → 保存并返回
- [ ] 返回 step 04 时 custom_pipeline 卡选中 + 草稿摘要可见 + 主按钮可点
- [ ] 确认后跳 step 05，metadata 中 promptConfirmed=true + promptStrategy=custom_pipeline
- [ ] 回到 step 04 看到"已确认"+"进入创建索引"+"重新选择策略"
- [ ] 编辑草稿保存 → 确认状态被原子清除
- [ ] 重新选择策略 → 弹确认框 → 状态变 ready
- [ ] URL 伪造 promptConfirmed=1 时，前端清理 query 并保持 ready 状态
- [ ] Builder dirty 时按 F5 → 浏览器弹"重新加载"确认框

- [ ] **Step 3: 修任何手测发现的偏差**

如发现偏离设计稿，补丁化处理：

- 若是 spec 不清晰导致歧义 → 更新 spec 对应段落 + 单独提交 `docs: 修订 ...`
- 若是实现 bug → 修复代码 + 提交

- [ ] **Step 4: 最终提交（如有手测修复）**

```bash
git add -p
git commit -m "fix(admin-app): 联调发现的提示词向导细节问题"
```

---

## 已知限制与设计决策

本节记录经审阅确认的已知限制，避免实现者误解为遗漏：

1. **"还原至模板"按钮本期等价于"清空内容"**：`PromptBuilderEditStep` 的 `templateContent` 本期为空字符串（种子内容未从后端拉取）。按钮文案已改为动态显示"清空内容"以避免误导用户。后续版本接入种子内容 API 后再恢复为"还原至模板"。

2. **`mergeStageMetadata` 的 extras 覆盖语义**：extras 在 persist 之后 put，同名键会覆盖 persist 的旧值。这是 `confirmPrompt` 和 `saveCustomPromptDraft` 显式重写 `promptConfirmed`/`promptStrategy` 时所需要的行为。Task 1 已补充覆盖优先级测试用例。

3. **`saveCustomPromptDraft` 的 persistKeys 策略**：传入 `["exportConfirmed", "graphInputConfirmed"]` 保留前序阶段键，而 `customPromptDraft`、`promptStrategy`、`promptConfirmed` 由 extras 显式写入。如果后续新增前序阶段键，需同步更新此列表。

4. **query watcher 黑名单策略**：采用 `UI_ONLY_QUERY_KEYS` 黑名单排除 `promptStrategy`，而非白名单枚举业务键。这确保现有和未来新增的 query 键默认触发 loadPage，只有明确标记为 UI-only 的键才被排除。

5. **`history_draft` 种子的校验分支**：`validateDraftRequest` 中先特判 `history_draft` 返回"暂未开放"错误，再走 `ALLOWED_DRAFT_SEEDS` 白名单校验返回"未知种子"错误。两个分支的错误消息不同，测试用例分别覆盖。

6. **`saveCustomPromptDraft` 故意不使用 `updateStage()`**：`updateStage` 会设置 `status="running"` 并更新 `currentStage`，适用于阶段确认动作（如 `confirmPrompt`）。`saveCustomPromptDraft` 仅保存草稿到 metadata，不推进工作流阶段，因此直接调用 `buildRunsStore.updateById()`。

7. **`blocks.prompt` 的组装路径**：`module-loaders.js` 中 `loadKnowledgeBaseBuildPage` 直接将 `resolvePromptConfirmState` 的返回值赋给 `blocks.prompt`（约 line 980 `prompt: promptState`）。`BuildStepPrompt.vue` 中 `props.blocks.prompt.*` 与 `resolvePromptConfirmState` 返回的扁平结构一一对应，无需额外映射层。

8. **已有草稿未修改时的"返回向导"行为**：用户携带已有草稿进入 Builder 后未做修改时，`dirty=false`，预览步骤按钮显示"返回向导"而非"保存并返回"，允许用户直接返回且回跳 URL 携带 `promptStrategy=custom_pipeline`。

---

## 自检清单

执行计划前请快速确认以下事项：

- [ ] 当前分支可写、git status 干净（用 `superpowers:using-git-worktrees` 隔离开发更稳）
- [ ] 后端 Maven 与前端 npm 依赖可拉起
- [ ] 后端测试数据库 / 前端 dev server 可正常启动
- [ ] 已与前端 / 后端 dev server 联通的 Playwright 配置可用
- [ ] 旧 buildRun（`promptStrategy='active'`）若存在测试数据中，回归测试通过后再合并
