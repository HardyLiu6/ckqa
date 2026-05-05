# GraphRAG Build Run Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement isolated, user-scoped knowledge-base build runs so concurrent GraphRAG builds never overwrite each other's input, output, logs, QA smoke state, or active-index metadata.

**Architecture:** Java `/api/v1` remains the browser boundary and owns the build-run state machine, workspace layout, artifact registry, active-index switching, and health/readiness semantics. Python GraphRAG remains a CLI-backed internal service; indexing uses per-run environment variables, and query tasks receive a backend-only `dataDirUri` resolved under `GRAPHRAG_BUILD_RUNS_ROOT`. The admin-app consumes build-run CRUD/action APIs and stops treating URL/sessionStorage as the source of truth.

**Tech Stack:** Spring Boot 4.0.5, Java 21, MyBatis-Plus, MySQL 8 JSON columns, GraphRAG 3.0.9 CLI, FastAPI, Python unittest/pytest, Vue 3 + Vite + Element Plus, Node test runner.

---

## Source Spec

- Design: `docs/superpowers/specs/2026-05-05-graphrag-index-run-isolation-design.md`
- Current formal browser boundary: Java `/api/v1`
- Current GraphRAG implementation boundary: GraphRAG 3.0.9 CLI, not `graphrag.api.build_index()`

## Implementation Guardrails

1. Do not move pdf_ingest source artifacts or MinIO namespaces; they remain upstream truth.
2. Do not expose server absolute paths to admin-app or student-app.
3. Do not make MinIO the hot query path for GraphRAG index artifacts in this phase.
4. Do not introduce Spring Statemachine, Redis locks, queue workers, or per-index Python API processes.
5. Do not remove legacy `POST /api/v1/knowledge-bases/{id}/index-runs`; it must internally bridge into a compatibility build run.
6. Keep implementation compatible with existing `course_materials + material_objects` and legacy `/pdf-files` route naming.
7. Use `GRAPHRAG_STORAGE_DIR/lancedb` as the effective GraphRAG CLI LanceDB path; `GRAPHRAG_LANCEDB_URI` is only API-runtime/health compatibility.

## File Map

### SQL

- Modify: `sql/ocqa.sql`
  - Add `knowledge_base_build_runs`.
  - Add `index_runs.build_run_id`.
  - Expand `index_artifacts`.
  - Add indexes and foreign keys in safe order.
- Create: `sql/migrations/20260505_kb_build_runs.sql`
  - Idempotent migration for existing local DB.

### Java Backend

- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexRuns.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexArtifacts.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/KnowledgeBaseBuildRuns.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/KnowledgeBaseBuildRunsMapper.java`
- Create: `backend/ckqa-back/src/main/resources/mapper/KnowledgeBaseBuildRunsMapper.xml`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/KnowledgeBaseBuildRunsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/KnowledgeBaseBuildRunsServiceImpl.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexArtifactRegistryService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ActiveIndexRunService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexWorkflowService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseLookupService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagIndexOrchestrator.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/graphrag/GraphRagTaskClient.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessContext.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/process/ProcessRunner.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthService.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/system/SystemHealthController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/IndexRunsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/IndexArtifactsController.java`

### Java DTOs

- Create under `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/`:
  - `BuildRunCreateRequest.java`
  - `BuildRunUpdateRequest.java`
  - `BuildRunMaterialSelectionRequest.java`
  - `BuildRunParseCheckRequest.java`
  - `BuildRunGraphInputRequest.java`
  - `BuildRunPromptConfirmationRequest.java`
  - `BuildRunIndexRequest.java`
  - `BuildRunQaSmokeRequest.java`
  - `BuildRunGcRequest.java`
  - `BuildRunGcResponse.java`
  - `BuildRunSummaryResponse.java`
  - `BuildRunDetailResponse.java`
  - `BuildRunStageSummaryResponse.java`
  - `IndexArtifactResponse.java`
  - `ActiveIndexRunRequest.java`
  - `ActiveIndexRunResponse.java`

### Python GraphRAG

- Modify: `graphrag_pipeline/utils/fetch_from_minio.py`
- Modify: `graphrag_pipeline/utils/query_task_manager.py`
- Modify: `graphrag_pipeline/utils/main.py`
- Modify: `graphrag_pipeline/utils/api_runtime_config.py`
- Modify tests:
  - `graphrag_pipeline/tests/test_fetch_from_minio.py`
  - `graphrag_pipeline/tests/test_fetch_from_minio_paths.py`
  - `graphrag_pipeline/tests/test_query_task_manager.py`
  - `graphrag_pipeline/tests/test_query_task_api.py`
  - `graphrag_pipeline/tests/test_main_cli_mode.py`
  - `graphrag_pipeline/tests/test_api_runtime_config.py`

### Admin App

- Modify: `frontend/apps/admin-app/src/api/knowledge-bases.js`
- Modify: `frontend/apps/admin-app/src/api/system.js`
- Modify: `frontend/apps/admin-app/src/views/pages/module-page-model.js`
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Modify focused build steps:
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepMaterial.vue`
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepParse.vue`
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepExport.vue`
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue`
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepQaCheck.vue`
- Modify: `frontend/apps/admin-app/src/views/system/health-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
- Modify E2E only if route behavior changes:
  - `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`

### Docs

- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `backend/ckqa-back/README.md`
- Modify: `graphrag_pipeline/README.md`
- Modify: `frontend/apps/admin-app/README.md`
- Modify: `docs/student-backend-graphrag-api-contract.md`

---

## Task 1: Schema And Entity Foundation

**Files:**
- Create: `sql/migrations/20260505_kb_build_runs.sql`
- Modify: `sql/ocqa.sql`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/KnowledgeBaseBuildRuns.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/KnowledgeBaseBuildRunsMapper.java`
- Create: `backend/ckqa-back/src/main/resources/mapper/KnowledgeBaseBuildRunsMapper.xml`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/KnowledgeBaseBuildRunsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/KnowledgeBaseBuildRunsServiceImpl.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexRuns.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/IndexArtifacts.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [x] **Step 1: Write the migration**

Create `sql/migrations/20260505_kb_build_runs.sql` with idempotent DDL:

```sql
-- CKQA GraphRAG build-run isolation schema
-- Date: 2026-05-05

CREATE TABLE IF NOT EXISTS `knowledge_base_build_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID快照',
  `requested_by_user_id` bigint NULL DEFAULT NULL COMMENT '发起用户ID',
  `build_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '构建版本',
  `status` enum('pending','running','success','failed','interrupted','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '流水线状态',
  `current_stage` enum('material_selection','parse','graph_input_export','prompt','index','qa_smoke','done') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'material_selection' COMMENT '当前阶段',
  `qa_status` enum('pending','running','success','failed','skipped') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'skipped' COMMENT '问答验证状态',
  `activation_policy` enum('manual','index_success') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'index_success' COMMENT '自动激活策略',
  `selected_material_ids` json DEFAULT NULL COMMENT '本次构建资料选择快照',
  `active_index_run_id` bigint NULL DEFAULT NULL COMMENT '当前由该构建承载的激活索引运行',
  `workspace_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区路径',
  `build_metadata` json DEFAULT NULL COMMENT '构建元数据',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_kb_build_version` (`knowledge_base_id`, `build_version`) USING BTREE,
  KEY `idx_kb_build_status` (`knowledge_base_id`, `status`) USING BTREE,
  KEY `idx_kb_build_user_status` (`requested_by_user_id`, `status`) USING BTREE,
  KEY `idx_kb_build_created` (`knowledge_base_id`, `created_at`, `id`) USING BTREE,
  KEY `idx_kb_build_active_index` (`knowledge_base_id`, `active_index_run_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库构建流水线表';

SET @has_fk_kb_build_runs_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_kb_build_runs_kb'
);
SET @sql := IF(@has_fk_kb_build_runs_kb = 0,
  'ALTER TABLE `knowledge_base_build_runs` ADD CONSTRAINT `fk_kb_build_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_build_run_id := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_runs' AND COLUMN_NAME = 'build_run_id'
);
SET @sql := IF(@has_build_run_id = 0,
  'ALTER TABLE `index_runs` ADD COLUMN `build_run_id` bigint NULL DEFAULT NULL COMMENT ''所属知识库构建流水线ID'' AFTER `knowledge_base_id`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_index_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'index_runs'
    AND INDEX_NAME = 'idx_index_runs_build_run'
);
SET @sql := IF(@has_idx_index_runs_build_run = 0,
  'CREATE INDEX `idx_index_runs_build_run` ON `index_runs` (`build_run_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_index_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_index_runs_build_run'
);
SET @sql := IF(@has_fk_index_runs_build_run = 0,
  'ALTER TABLE `index_runs` ADD CONSTRAINT `fk_index_runs_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `index_artifacts`
  MODIFY COLUMN `artifact_type` enum('input_json','output_dir','parquet','lancedb','report','cache','manifest','log','qa_smoke','graphrag_output','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型';

SET @has_display_name := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'display_name'
);
SET @sql := IF(@has_display_name = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `display_name` varchar(255) NULL DEFAULT NULL COMMENT ''前端展示名'' AFTER `artifact_type`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_storage_scope := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'storage_scope'
);
SET @sql := IF(@has_storage_scope = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `storage_scope` enum(''local'',''minio'') NOT NULL DEFAULT ''local'' COMMENT ''存储位置类型'' AFTER `storage_uri`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_artifact_status := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'index_artifacts' AND COLUMN_NAME = 'artifact_status'
);
SET @sql := IF(@has_artifact_status = 0,
  'ALTER TABLE `index_artifacts` ADD COLUMN `artifact_status` enum(''ready'',''partial'',''missing'',''deleted'') NOT NULL DEFAULT ''ready'' COMMENT ''产物状态'' AFTER `storage_scope`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

Do not add another `qa_sessions.session_type` migration in this task: `sql/ocqa.sql` and `sql/migrations/20260429_qa_session_type.sql` already define `enum('formal','smoke')`.

- [x] **Step 2: Update `sql/ocqa.sql`**

Add `knowledge_base_build_runs` between `knowledge_bases` and `index_runs`, add `build_run_id` to `index_runs`, expand `index_artifacts`, and add the `fk_index_runs_build_run` constraint before adding `knowledge_bases.active_index_run_id` foreign key to avoid circular FK creation issues.

- [x] **Step 3: Add Java entity and mapper**

Create `KnowledgeBaseBuildRuns.java`:

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("knowledge_base_build_runs")
public class KnowledgeBaseBuildRuns implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("course_id")
    private String courseId;
    @TableField("requested_by_user_id")
    private Long requestedByUserId;
    @TableField("build_version")
    private String buildVersion;
    @TableField("status")
    private String status;
    @TableField("current_stage")
    private String currentStage;
    @TableField("qa_status")
    private String qaStatus;
    @TableField("activation_policy")
    private String activationPolicy;
    @TableField("selected_material_ids")
    private String selectedMaterialIds;
    @TableField("active_index_run_id")
    private Long activeIndexRunId;
    @TableField("workspace_uri")
    private String workspaceUri;
    @TableField("build_metadata")
    private String buildMetadata;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

Create `KnowledgeBaseBuildRunsMapper.java` extending `BaseMapper<KnowledgeBaseBuildRuns>`. Create `KnowledgeBaseBuildRunsMapper.xml` with a standard `BaseResultMap` matching the entity fields.

- [x] **Step 4: Extend existing entities**

Modify `IndexRuns.java`:

```java
@TableField("build_run_id")
private Long buildRunId;
```

Modify `IndexArtifacts.java`:

```java
@TableField("display_name")
private String displayName;

@TableField("storage_scope")
private String storageScope;

@TableField("artifact_status")
private String artifactStatus;
```

- [x] **Step 5: Add service wrapper and error code**

Create `KnowledgeBaseBuildRunsService` extending `IService<KnowledgeBaseBuildRuns>` with:

```java
KnowledgeBaseBuildRuns getRequiredById(Long id);
java.util.List<KnowledgeBaseBuildRuns> listByKnowledgeBaseId(Long knowledgeBaseId);
java.util.Optional<KnowledgeBaseBuildRuns> findActivePendingOrRunning(Long knowledgeBaseId);
void clearActiveIndexRunMarkers(Long knowledgeBaseId);
```

Add `KNOWLEDGE_BASE_BUILD_RUN_NOT_FOUND(4049, "知识库构建流水线不存在")` and `KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING(4099, "当前知识库已有构建流水线未完成")` to `ApiResultCode`.

- [x] **Step 6: Run backend compile**

Run:

```bash
cd backend/ckqa-back
./mvnw -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [x] **Step 7: Commit**

```bash
git add sql/ocqa.sql sql/migrations/20260505_kb_build_runs.sql backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper backend/ckqa-back/src/main/resources/mapper backend/ckqa-back/src/main/java/org/ysu/ckqaback/service backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(backend): add knowledge base build run schema"
```

---

## Task 2: Build Run Control Plane

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java`
- Create DTO files listed in the File Map.
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/KnowledgeBasesController.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/integration/config/CkqaIntegrationProperties.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunServiceTest.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBaseBuildRunsControllerWebMvcTest.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/controller/KnowledgeBasesControllerWebMvcTest.java`

- [x] **Step 1: Add config properties**

Extend `CkqaIntegrationProperties.GraphRagProperties`:

```java
private String buildRunsRoot;
private boolean concurrentBuildsEnabled = true;
private String autoActivationPolicy = "latest-build-only";
private final RetentionProperties retention = new RetentionProperties();

@Getter
@Setter
public static class RetentionProperties {
    private int keepSuccessBuildRuns = 3;
    private int keepFailedBuildRuns = 3;
    private boolean autoCleanupEnabled = false;
}
```

- [x] **Step 2: Write failing workspace tests**

Create `KnowledgeBaseBuildRunServiceTest` with tests:

```java
@Test
void shouldCreateWorkspaceUriUnderConfiguredRoot() {
    BuildRunWorkspaceService workspaceService = new BuildRunWorkspaceService("/tmp/ckqa-build-runs");

    String uri = workspaceService.workspaceUri(2L, 5L, 27L);
    Path path = workspaceService.resolve(uri);

    assertThat(uri).isEqualTo("user_2/kb_5/build_27");
    assertThat(path).isEqualTo(Path.of("/tmp/ckqa-build-runs/user_2/kb_5/build_27"));
}

@Test
void shouldRejectWorkspacePathTraversal() {
    BuildRunWorkspaceService workspaceService = new BuildRunWorkspaceService("/tmp/ckqa-build-runs");

    assertThatThrownBy(() -> workspaceService.resolve("../outside"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("构建工作区路径非法");
}
```

Run:

```bash
cd backend/ckqa-back
./mvnw -Dtest=KnowledgeBaseBuildRunServiceTest test
```

Expected: FAIL because the service does not exist.

- [x] **Step 3: Implement workspace service**

Implement `BuildRunWorkspaceService` with methods:

```java
public String workspaceUri(Long userId, Long knowledgeBaseId, Long buildRunId) {
    long safeUserId = userId == null ? 0L : userId;
    return "user_" + safeUserId + "/kb_" + knowledgeBaseId + "/build_" + buildRunId;
}

public Path resolve(String workspaceUri) {
    Path resolved = buildRunsRoot.resolve(workspaceUri).normalize();
    if (!resolved.startsWith(buildRunsRoot)) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "构建工作区路径非法");
    }
    return resolved;
}

public void createLayout(String workspaceUri) throws IOException {
    Path root = resolve(workspaceUri);
    for (String child : List.of("selection", "parse", "graph-input", "prompt", "index/input", "index/output", "index/cache", "index/reports", "index/logs", "qa-smoke")) {
        Files.createDirectories(root.resolve(child));
    }
}
```

- [x] **Step 4: Add DTOs**

Implement request DTOs with Jakarta validation:

```java
@Getter
@Setter
public class BuildRunCreateRequest {
    private List<@Positive Long> materialIds = List.of();
    private String jsonFile = "section_docs.json";
    private String promptStrategy = "active";
    private Boolean activateOnSuccess = true;
}
```

Use equivalent DTOs for stage actions:

```java
public class BuildRunParseCheckRequest { private Boolean parseMissing = false; }
public class BuildRunGraphInputRequest { private String jsonFile = "section_docs.json"; private Boolean exportMissing = true; }
public class BuildRunPromptConfirmationRequest { private String promptStrategy = "active"; private Boolean confirmed = false; }
public class BuildRunIndexRequest { private Boolean activateOnSuccess = true; private Boolean forceRebuild = false; }
public class BuildRunQaSmokeRequest { private String question; private String mode = "basic"; }
public class BuildRunGcRequest { private Boolean deleteWorkspace = false; private Boolean dryRun = true; }
public class ActiveIndexRunRequest { @NotNull @Positive private Long indexRunId; }
```

- [x] **Step 5: Implement build run service create/list/detail/archive/gc**

Implement `KnowledgeBaseBuildRunService` with these public methods:

```java
BuildRunDetailResponse createBuildRun(Long knowledgeBaseId, BuildRunCreateRequest request);
ApiPageData<BuildRunSummaryResponse> listBuildRuns(Long knowledgeBaseId, String status, Long page, Long size);
BuildRunDetailResponse getBuildRun(Long id);
BuildRunDetailResponse updateBuildRun(Long id, BuildRunUpdateRequest request);
BuildRunGcResponse gcBuildRuns(Long knowledgeBaseId, BuildRunGcRequest request);
BuildRunDetailResponse createCompatibilityBuildRun(Long knowledgeBaseId);
boolean isLatestBuildRun(Long buildRunId);
void markIndexSuccessDone(Long buildRunId, String qaStatus);
BuildRunDetailResponse updateMaterialSelection(Long id, BuildRunMaterialSelectionRequest request);
BuildRunDetailResponse checkParse(Long id, BuildRunParseCheckRequest request);
BuildRunDetailResponse syncGraphInput(Long id, BuildRunGraphInputRequest request);
BuildRunDetailResponse confirmPrompt(Long id, BuildRunPromptConfirmationRequest request);
```

Rules:

1. Generate `buildVersion` as `kb{id}-{yyyyMMddHHmmssSSS}-{random4}`.
2. If `concurrentBuildsEnabled=false`, reject another `pending/running` build run for the same KB with `KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING`.
3. Save `selected_material_ids` as JSON array string.
4. Create workspace layout and `selection/selected_materials.json` immediately after the DB row has an ID.
5. Update `build_metadata` and `current_stage` in the same `@Transactional` method.
6. `createCompatibilityBuildRun` creates a build run for the legacy index endpoint with empty material selection, `jsonFile=section_docs.json`, `activationPolicy=index_success`, and `requestedByUserId=null`.
7. `isLatestBuildRun` returns true only when the target build run has no newer same-knowledge-base build run by `(created_at, id)`.
8. `markIndexSuccessDone` sets `status=success`, `current_stage=done`, `qa_status` to the supplied value, `finished_at=now`, and records the index terminal summary in `build_metadata` in the same transaction.

- [x] **Step 6: Add controllers**

Add `KnowledgeBaseBuildRunsController`:

```java
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/knowledge-base-build-runs")
public class KnowledgeBaseBuildRunsController {
    private final KnowledgeBaseBuildRunService buildRunService;

    @GetMapping("/{id}")
    public ApiResponse<BuildRunDetailResponse> getBuildRun(@PathVariable @Positive Long id) {
        return ApiResponseUtils.success(buildRunService.getBuildRun(id));
    }

    @PutMapping("/{id}/material-selection")
    public ApiResponse<BuildRunDetailResponse> updateMaterialSelection(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunMaterialSelectionRequest request) {
        return ApiResponseUtils.success(buildRunService.updateMaterialSelection(id, request));
    }

    @PostMapping("/{id}/parse-check")
    public ApiResponse<BuildRunDetailResponse> checkParse(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunParseCheckRequest request) {
        return ApiResponseUtils.success(buildRunService.checkParse(id, request));
    }

    @PostMapping("/{id}/graph-input")
    public ApiResponse<BuildRunDetailResponse> syncGraphInput(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunGraphInputRequest request) {
        return ApiResponseUtils.success(buildRunService.syncGraphInput(id, request));
    }

    @PostMapping("/{id}/prompt-confirmation")
    public ApiResponse<BuildRunDetailResponse> confirmPrompt(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunPromptConfirmationRequest request) {
        return ApiResponseUtils.success(buildRunService.confirmPrompt(id, request));
    }
}
```

Add to `KnowledgeBasesController`:

```java
@PostMapping("/{id}/build-runs")
public ApiResponse<BuildRunDetailResponse> createBuildRun(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunCreateRequest request) {
    return ApiResponseUtils.success(buildRunService.createBuildRun(id, request));
}

@GetMapping("/{id}/build-runs")
public ApiResponse<ApiPageData<BuildRunSummaryResponse>> listBuildRuns(@PathVariable @Positive Long id, @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") Long page, @RequestParam(defaultValue = "20") Long size) {
    return ApiResponseUtils.success(buildRunService.listBuildRuns(id, status, page, size));
}

@PostMapping("/{id}/build-runs/gc")
public ApiResponse<BuildRunGcResponse> gcBuildRuns(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunGcRequest request) {
    return ApiResponseUtils.success(buildRunService.gcBuildRuns(id, request));
}
```

- [x] **Step 7: WebMvc tests**

Add tests for:

```java
mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASES + "/5/build-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"materialIds\":[3,4],\"jsonFile\":\"section_docs.json\",\"activateOnSuccess\":true}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.currentStage").value("material_selection"));

mockMvc.perform(post(ApiPaths.API_V1 + "/knowledge-base-build-runs/27/parse-check")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parseMissing\":true}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.currentStage").value("parse"));

given(buildRunService.createBuildRun(eq(5L), any(BuildRunCreateRequest.class)))
        .willThrow(new BusinessException(ApiResultCode.KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING, HttpStatus.CONFLICT));

mockMvc.perform(post(ApiPaths.KNOWLEDGE_BASES + "/5/build-runs")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"materialIds\":[3]}"))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.code").value(ApiResultCode.KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING.getCode()));
```

- [x] **Step 8: Run tests and commit**

Run:

```bash
cd backend/ckqa-back
./mvnw -Dtest=KnowledgeBaseBuildRunServiceTest,KnowledgeBaseBuildRunsControllerWebMvcTest,KnowledgeBasesControllerWebMvcTest test
```

Commit:

```bash
git add backend/ckqa-back/src/main/java backend/ckqa-back/src/test/java
git commit -m "feat(backend): add knowledge base build run control plane"
```

---

## Task 3: Python GraphRAG Input And Query Isolation

**Files:**
- Modify: `graphrag_pipeline/utils/fetch_from_minio.py`
- Modify: `graphrag_pipeline/utils/query_task_manager.py`
- Modify: `graphrag_pipeline/utils/main.py`
- Modify: `graphrag_pipeline/utils/api_runtime_config.py`
- Modify tests listed in the Python file map.

- [x] **Step 1: Add failing `--output-file` tests**

In `test_fetch_from_minio.py`, add:

```python
def test_output_file_overrides_local_filename(tmp_path, monkeypatch):
    source = [{"id": "doc-1", "text": "hello", "title": "t"}]

    class FakeClient:
        def get_object(self, bucket, key):
            return source

    result = fetch_and_prepare(
        course_id="os",
        input_dir=tmp_path,
        clean=False,
        json_filename="section_docs.json",
        material_id=3,
        output_filename="material_3.section_docs.json",
        client=FakeClient(),
    )

    assert result["output_file"].endswith("material_3.section_docs.json")
    assert (tmp_path / "material_3.section_docs.json").exists()
    assert not (tmp_path / "section_docs.json").exists()
```

Run:

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/test_fetch_from_minio.py -q
```

Expected: FAIL because `output_filename` does not exist.

- [x] **Step 2: Implement `--output-file`**

Modify `fetch_and_prepare` to accept `output_filename: str | None = None`. Use:

```python
local_filename = output_filename or Path(json_filename).name
if Path(local_filename).name != local_filename:
    raise ValueError("--output-file 只允许文件名，不允许包含目录")
output_path = input_dir / local_filename
```

Add CLI argument:

```python
parser.add_argument("--output-file", default=None, help="写入 input-dir 的本地文件名，不改变 MinIO 源文件名")
```

Preserve the existing `--json-file` / `--jsonl-file` option and continue passing it to `fetch_and_prepare` as `json_filename`. The Java workflow depends on using `--json-file page_docs.json` or `--json-file normalized_docs.json` for validation inputs while `--output-file` controls only the local filename.

- [x] **Step 3: Add query context tests**

In `test_query_task_manager.py`, add a context-aware test:

```python
async def test_uses_task_specific_data_dir_uri(self):
    created_envs = []

    manager = QueryTaskManager(
        heartbeat_interval_seconds=0.05,
        command_factory=lambda request: [sys.executable, "-c", "print('ok')"],
        env_factory=lambda request: {"GRAPHRAG_STORAGE_DIR": str(request.data_dir)},
        cwd=_PROJECT_ROOT,
        build_runs_root=_PROJECT_ROOT / "runtime" / "kb-build-runs",
    )

    snapshot = await manager.create_task("basic", "问题", index_run_id=18, data_dir_uri="user_2/kb_5/build_27/index/output")
    await asyncio.sleep(0.15)

    finished = manager.get_snapshot(snapshot.python_task_id)
    assert finished.index_run_id == 18
    assert finished.data_dir_uri == "user_2/kb_5/build_27/index/output"
```

Run:

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/test_query_task_manager.py -q
```

Expected: FAIL because the manager does not accept task context.

- [x] **Step 4: Implement task context**

Add dataclass in `query_task_manager.py`:

```python
@dataclass(frozen=True, slots=True)
class QueryTaskRequest:
    mode: str
    prompt: str
    index_run_id: int | None
    data_dir_uri: str | None
    data_dir: Path | None
```

Extend `QueryTaskSnapshot` with `index_run_id: int | None = None` and `data_dir_uri: str | None = None`.

Change constructor types:

```python
command_factory: Callable[[QueryTaskRequest], list[str]]
env_factory: Callable[[QueryTaskRequest], dict[str, str]]
```

Add `build_runs_root: str | Path | None = None` and resolve `data_dir_uri` with:

```python
def _resolve_data_dir(self, data_dir_uri: str | None) -> Path | None:
    if not data_dir_uri:
        return None
    if self._build_runs_root is None:
        raise ValueError("GRAPHRAG_BUILD_RUNS_ROOT 未配置")
    resolved = (self._build_runs_root / data_dir_uri).resolve()
    root = self._build_runs_root.resolve()
    if root not in (resolved, *resolved.parents):
        raise ValueError("dataDirUri 超出允许的构建根目录")
    return resolved
```

Update `api_runtime_config.py` so the API runtime owns the build-runs-root default:

```python
@dataclass(frozen=True)
class ApiRuntimeConfig:
    output_dir: Path
    lancedb_uri: str
    build_runs_root: Path
    api_host: str
    api_port: int
```

In `load_api_runtime_config`, resolve:

```python
build_runs_root = _resolve_repo_path(
    env.get("GRAPHRAG_BUILD_RUNS_ROOT"),
    PROJECT_ROOT / "runtime" / "kb-build-runs",
)
```

Pass `CONFIG.build_runs_root` into `QueryTaskManager(build_runs_root=CONFIG.build_runs_root)` in `main.py`.

- [x] **Step 5: Update FastAPI request model**

In `main.py`:

```python
class QueryTaskCreateRequest(BaseModel):
    mode: Literal["local", "global", "drift", "basic"] = Field(default="local")
    prompt: str
    indexRunId: int | None = None
    dataDirUri: str | None = None
```

Update submit endpoint:

```python
snapshot = await task_manager.create_task(
    request.mode,
    request.prompt,
    index_run_id=request.indexRunId,
    data_dir_uri=request.dataDirUri,
)
```

Update command factory:

```python
def _build_query_cmd(request: QueryTaskRequest) -> list[str]:
    cmd = ["graphrag", "query", "--root", "."]
    if request.data_dir is not None:
        cmd.extend(["--data", str(request.data_dir)])
    cmd.extend(["--method", request.mode, request.prompt])
    return cmd
```

Update env factory:

```python
def _build_query_env(request: QueryTaskRequest) -> dict[str, str]:
    env = os.environ.copy()
    output_dir = request.data_dir or OUTPUT_DIR
    env["GRAPHRAG_OUTPUT_DIR"] = str(output_dir)
    env["GRAPHRAG_STORAGE_DIR"] = str(output_dir)
    return env
```

- [x] **Step 6: Add path rejection tests**

In `test_query_task_api.py`, add:

```python
def test_query_task_rejects_path_escape():
    with self.assertRaises(ValueError):
        asyncio.run(manager.create_task("basic", "问题", index_run_id=18, data_dir_uri="../outside"))
```

Also test request JSON with `dataDirUri` reaches the fake manager.

- [x] **Step 7: Run Python tests and commit**

Run:

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/test_fetch_from_minio.py tests/test_fetch_from_minio_paths.py tests/test_query_task_manager.py tests/test_query_task_api.py tests/test_main_cli_mode.py tests/test_api_runtime_config.py
```

Commit:

```bash
git add graphrag_pipeline/utils graphrag_pipeline/tests
git commit -m "feat(graphrag): isolate build input and query data dirs"
```

---

## Task 4: Java Index Workflow, Artifacts, Activation, And Logs

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/IndexArtifactRegistryService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ActiveIndexRunService.java`
- Modify: `IndexWorkflowService.java`
- Modify: `GraphRagIndexOrchestrator.java`
- Modify: `ProcessContext.java`
- Modify: `ProcessRunner.java`
- Modify: `IndexRunsService.java` and implementation.
- Modify: `IndexArtifactsService.java` and implementation.
- Modify: `KnowledgeBasesService.java` and implementation.
- Modify: controllers and DTOs.
- Tests: `IndexWorkflowServiceTest`, `ProcessRunnerTest`, `IndexArtifactsControllerWebMvcTest`, `IndexRunsControllerWebMvcTest`.

- [x] **Step 1: Write failing log tee test**

In `ProcessRunnerTest`, add:

```java
@Test
void shouldWriteStdoutAndStderrToLogFile() throws Exception {
    Path logFile = tempDir.resolve("process.log");
    ProcessExecutionResult result = processRunner.run(
            List.of("bash", "-lc", "echo out; echo err >&2"),
            tempDir,
            Map.of(),
            Duration.ofSeconds(5),
            ProcessContext.builder().operation("index").logFile(logFile).build()
    );

    assertThat(result.getExitCode()).isZero();
    assertThat(Files.readString(logFile)).contains("out").contains("err");
}
```

- [x] **Step 2: Implement ProcessContext log file support**

Add `Path logFile` to `ProcessContext`. In `ProcessRunner`, stream stdout/stderr to both in-memory strings and the file. Prefix lines with stream names:

```text
[stdout] indexing started
[stderr] warning message
```

Do not remove existing `stdout` / `stderr` capture because current error handling depends on it.

- [x] **Step 3: Implement artifact scanner**

`IndexArtifactRegistryService.scanAndRegister(IndexRuns run, Path workspaceRoot, String workspaceUri)` must:

1. Delete existing artifact rows for the run before re-registering.
2. Register:
   - `input_json` for `index/input/*.json`
   - `output_dir` for `index/output`
   - `lancedb` for `index/output/lancedb`
   - `parquet` for `index/output/*.parquet`
   - `report` for `index/reports/*`
   - `log` for `index/logs/process.log`
   - `manifest` for `manifest.json`
3. Store relative `storage_uri`.
4. Set `artifact_status=ready` only when the file or directory exists.

Add test:

```java
@Test
void shouldRegisterLancedbAndProcessLogArtifacts() throws Exception {
    Files.createDirectories(workspace.resolve("index/output/lancedb"));
    Files.createDirectories(workspace.resolve("index/logs"));
    Files.writeString(workspace.resolve("index/logs/process.log"), "ok");

    List<IndexArtifacts> artifacts = registry.scanAndRegister(run, workspace, "user_2/kb_5/build_27");

    assertThat(artifacts).extracting(IndexArtifacts::getArtifactType)
            .contains("lancedb", "log", "output_dir");
}
```

- [x] **Step 4: Implement ActiveIndexRunService**

Provide:

```java
ActiveIndexRunResponse activate(Long knowledgeBaseId, Long indexRunId, boolean manual);
```

Rules:

1. `index_run.status` must be `success`.
2. `index_run.knowledge_base_id` must match the path `knowledgeBaseId`.
3. Required artifacts `output_dir` and `lancedb` must be `ready`.
4. Use one transaction.
5. Lock/update the target `knowledge_bases` row.
6. Clear only build run rows where `knowledge_base_id=? AND active_index_run_id IS NOT NULL`.
7. Set the target build run marker only when `build_run_id` is not null.

- [x] **Step 5: Extend GraphRagIndexOrchestrator**

Replace shared fetch/index methods with build-run-aware methods:

```java
ProcessExecutionResult fetchMaterialInput(IndexRuns run, KnowledgeBases kb, Long materialId, Path graphInputDir, String jsonFile, String outputFile);
ProcessExecutionResult runIndex(IndexRuns run, Path workspaceRoot);
```

Fetch command:

```java
List.of(python, "utils/fetch_from_minio.py", kb.getCourseId(),
        "--material-id", String.valueOf(materialId),
        "--json-file", jsonFile,
        "--input-dir", graphInputDir.toString(),
        "--output-file", outputFile)
```

Index env:

```java
Map.of(
  "GRAPHRAG_INPUT_DIR", workspaceRoot.resolve("index/input").toString(),
  "GRAPHRAG_OUTPUT_DIR", workspaceRoot.resolve("index/output").toString(),
  "GRAPHRAG_STORAGE_DIR", workspaceRoot.resolve("index/output").toString(),
  "GRAPHRAG_REPORTING_DIR", workspaceRoot.resolve("index/reports").toString(),
  "GRAPHRAG_CACHE_DIR", workspaceRoot.resolve("index/cache").toString()
)
```

- [x] **Step 6: Move legacy create-index endpoint through compatibility build run**

In `IndexWorkflowService.createIndexRun(Long knowledgeBaseId)`, internally call:

```java
BuildRunDetailResponse buildRun = buildRunService.createCompatibilityBuildRun(knowledgeBaseId);
return createBuildRunIndexRun(buildRun.getId(), new BuildRunIndexRequest(true, false));
```

Add new:

```java
IndexRunResponse createBuildRunIndexRun(Long buildRunId, BuildRunIndexRequest request);
```

- [x] **Step 7: Build index input safely**

In the workflow:

1. Copy `graph-input/material_{id}.section_docs.json` to `index/input/material_{id}.section_docs.json`.
2. If the multi-JSON GraphRAG check in Task 7 Step 4 real integration smoke fails, merge arrays into `index/input/build_{buildRunId}.section_docs.json`.
3. Never read or clean `graphrag_pipeline/input`.

- [x] **Step 8: Add automatic activation policy tests**

Add tests:

```java
@Test
void shouldSkipAutoActivationWhenBuildRunIsNotLatest() {
    given(buildRunService.isLatestBuildRun(27L)).willReturn(false);

    IndexRunResponse response = workflow.createBuildRunIndexRun(27L, new BuildRunIndexRequest(true, false));

    then(activeIndexRunService).should(never()).activate(anyLong(), anyLong(), eq(false));
    assertThat(response.getRunMetadata()).contains("skipped_newer_build_exists");
}

@Test
void shouldMarkBuildRunDoneAndQaSkippedAfterSuccessfulIndex() {
    workflow.createBuildRunIndexRun(28L, new BuildRunIndexRequest(true, false));

    then(buildRunService).should().markIndexSuccessDone(28L, "skipped");
}
```

- [x] **Step 9: Add controller endpoints**

Add:

```java
@PostMapping("/{id}/index-runs")
public ApiResponse<IndexRunResponse> createBuildRunIndexRun(@PathVariable @Positive Long id, @Valid @RequestBody BuildRunIndexRequest request)
```

Add in `KnowledgeBasesController`:

```java
@PostMapping("/{id}/active-index-run")
public ApiResponse<ActiveIndexRunResponse> activateIndexRun(@PathVariable @Positive Long id, @Valid @RequestBody ActiveIndexRunRequest request)
```

Add artifact endpoints:

```java
@GetMapping("/index-runs/{id}/artifacts")
public ApiResponse<List<IndexArtifactResponse>> listIndexRunArtifacts(@PathVariable @Positive Long id)

@GetMapping("/index-artifacts/{id}")
public ApiResponse<IndexArtifactResponse> getIndexArtifact(@PathVariable @Positive Long id)

@DeleteMapping("/index-artifacts/{id}")
public ApiResponse<IndexArtifactResponse> deleteIndexArtifact(@PathVariable @Positive Long id)
```

- [x] **Step 10: Run backend tests and commit**

Run:

```bash
cd backend/ckqa-back
./mvnw -Dtest=IndexWorkflowServiceTest,ProcessRunnerTest,IndexRunsControllerWebMvcTest,IndexArtifactsControllerWebMvcTest,KnowledgeBasesControllerWebMvcTest test
```

Commit:

```bash
git add backend/ckqa-back/src/main/java backend/ckqa-back/src/test/java
git commit -m "feat(backend): isolate graphrag index workflow"
```

---

## Task 5: Query Routing, QA Smoke, And Readiness

**Files:**
- Modify: `GraphRagTaskClient.java`
- Modify: `QaWorkflowService.java`
- Modify: QA DTOs as needed.
- Modify: `SystemHealthService.java`
- Modify: `SystemHealthController.java`
- Tests: `GraphRagTaskClientTest`, `QaWorkflowServiceTest`, `SystemHealthServiceTest`, `SystemHealthControllerWebMvcTest`.

- [x] **Step 1: Extend GraphRagTaskClient request**

Add backend-only fields to the Python request payload:

```json
{
  "mode": "basic",
  "prompt": "问题",
  "indexRunId": 18,
  "dataDirUri": "user_2/kb_5/build_27/index/output"
}
```

Test in `GraphRagTaskClientTest`:

```java
assertThat(capturedRequest.getBody()).contains("\"indexRunId\":18");
assertThat(capturedRequest.getBody()).contains("\"dataDirUri\":\"user_2/kb_5/build_27/index/output\"");
assertThat(capturedRequest.getBody()).doesNotContain("/home/");
```

- [x] **Step 2: Resolve active index context for QA**

In `QaWorkflowService`, before submitting a GraphRAG task:

1. Load `KnowledgeBases.activeIndexRunId`.
2. Load target `IndexRuns`.
3. Find ready `output_dir` artifact or derive it from run metadata.
4. Pass `indexRunId` and `dataDirUri` to `GraphRagTaskClient`.
5. If missing, throw `KNOWLEDGE_BASE_NOT_READY`.

- [x] **Step 3: Implement build-run QA smoke action**

In `KnowledgeBaseBuildRunService.runQaSmoke(Long buildRunId, BuildRunQaSmokeRequest request)`:

1. Set `current_stage=qa_smoke`, `qa_status=running`.
2. Create or reuse a `qa_sessions` row with `session_type='smoke'`.
3. Submit one QA message through existing QA workflow using the build run's active or latest success index.
4. Persist `qa-smoke/request.json`.
5. On terminal result, persist `qa-smoke/response.json`, set `current_stage=done`, and set `qa_status=success` or `failed`.

Keep `build_run.status=success` when QA fails.

Schema note: `session_type='smoke'` is already supported by `sql/ocqa.sql` and `sql/migrations/20260429_qa_session_type.sql`; this task should reuse that existing enum value instead of adding another QA migration.

- [x] **Step 4: Split health and readiness**

Keep `GET /api/v1/system/health` lightweight:

1. `graphrag-root`
2. `graphrag-build-runs-root`
3. `graphrag-api`
4. `graphrag-ready` meaning service chain reachable

Add:

```java
@GetMapping(ApiPaths.API_V1 + "/system/readiness")
public ApiResponse<SystemHealthResponse> readiness()
```

and optional:

```java
@GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{id}/readiness")
```

- [x] **Step 5: Run tests and commit**

Run:

```bash
cd backend/ckqa-back
./mvnw -Dtest=GraphRagTaskClientTest,QaWorkflowServiceTest,SystemHealthServiceTest,SystemHealthControllerWebMvcTest test
```

Commit:

```bash
git add backend/ckqa-back/src/main/java backend/ckqa-back/src/test/java
git commit -m "feat(backend): route qa through active build artifacts"
```

---

## Task 6: Admin App Build Run Integration

**Files:**
- Modify: `frontend/apps/admin-app/src/api/knowledge-bases.js`
- Modify: `frontend/apps/admin-app/src/api/system.js`
- Modify: `module-page-model.js`
- Modify: `module-loaders.js`
- Modify: `module-content.js`
- Modify: build step Vue files.
- Modify: `health-model.js`
- Modify: `app-shell.test.js`

- [x] **Step 1: Add API client tests**

In `app-shell.test.js`, add assertions with a fake client:

```js
test('knowledge-base api exposes build-run endpoints', async () => {
  const calls = []
  const client = {
    post: async (url, payload) => {
      calls.push(['post', url, payload])
      return { data: { code: 200, message: 'ok', data: { id: 27 } } }
    },
    get: async (url, config) => {
      calls.push(['get', url, config])
      return { data: { code: 200, message: 'ok', data: { items: [] } } }
    },
  }

  assert.deepEqual(await createBuildRun(5, { materialIds: [3] }, client), { id: 27 })
  assert.deepEqual(calls[0], ['post', '/knowledge-bases/5/build-runs', { materialIds: [3] }])
})
```

- [x] **Step 2: Implement API functions**

Change the top imports in `knowledge-bases.js` to:

```js
import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'
```

Add:

```js
export async function createBuildRun(knowledgeBaseId, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/build-runs`, payload))
}

export async function getBuildRun(id, client = http) {
  return unwrapApiResponse(await client.get(`/knowledge-base-build-runs/${encodeURIComponent(id)}`))
}

export async function syncBuildRunGraphInput(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/graph-input`, payload))
}

export async function createBuildRunIndexRun(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/index-runs`, payload))
}

export async function runBuildRunQaSmoke(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/qa-smoke`, payload))
}
```

Add the remaining functions in the same module:

```js
export async function listKnowledgeBaseBuildRuns(knowledgeBaseId, params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get(`/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/build-runs`, { params })))
}

export async function updateBuildRun(id, payload, client = http) {
  return unwrapApiResponse(await client.patch(`/knowledge-base-build-runs/${encodeURIComponent(id)}`, payload))
}

export async function deleteBuildRun(id, options = {}, client = http) {
  return unwrapApiResponse(await client.delete(`/knowledge-base-build-runs/${encodeURIComponent(id)}`, { params: options }))
}

export async function updateBuildRunMaterialSelection(id, payload, client = http) {
  return unwrapApiResponse(await client.put(`/knowledge-base-build-runs/${encodeURIComponent(id)}/material-selection`, payload))
}

export async function checkBuildRunParse(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/parse-check`, payload))
}

export async function confirmBuildRunPrompt(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/prompt-confirmation`, payload))
}

export async function activateIndexRun(knowledgeBaseId, indexRunId, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/active-index-run`, { indexRunId }))
}

export async function listIndexRunArtifacts(indexRunId, client = http) {
  return unwrapApiResponse(await client.get(`/index-runs/${encodeURIComponent(indexRunId)}/artifacts`))
}

export async function getIndexArtifact(id, client = http) {
  return unwrapApiResponse(await client.get(`/index-artifacts/${encodeURIComponent(id)}`))
}

export async function deleteIndexArtifact(id, client = http) {
  return unwrapApiResponse(await client.delete(`/index-artifacts/${encodeURIComponent(id)}`))
}
```

- [x] **Step 3: Move build wizard source of truth to buildRunId**

In `module-page-model.js`, add:

```js
export function resolveBuildRunIdQuery(query = {}) {
  const raw = query.buildRunId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}
```

In `module-loaders.js`:

1. If `buildRunId` exists, call `getBuildRun(buildRunId)`.
2. If there is no `buildRunId`, do not call `createBuildRun` during page load; show the wizard in `not_started` state with the current material selection as a draft.
3. Call `createBuildRun` only from the explicit user action that starts or confirms the build, then write the returned `buildRunId` into the route query.
4. Keep sessionStorage only as selection fallback, not state truth.

- [x] **Step 4: Update workflow actions**

Route primary actions:

1. material confirm -> `updateBuildRunMaterialSelection`
2. parse -> `checkBuildRunParse`
3. export -> `syncBuildRunGraphInput`
4. prompt -> `confirmBuildRunPrompt`
5. index -> `createBuildRunIndexRun`
6. QA -> `runBuildRunQaSmoke`

Make labels business-facing:

- `material_selection`: `资料选择`
- `parse`: `解析检查`
- `graph_input_export`: `图谱输入`
- `prompt`: `Prompt确认`
- `index`: `索引构建`
- `qa_smoke`: `问答验证`
- `done`: `完成`

- [x] **Step 5: Health/readiness UI**

Update `health-model.js` to treat:

1. `graphrag-root`
2. `graphrag-build-runs-root`
3. `graphrag-api`
4. `graphrag-ready`

Do not require shared `output/lancedb` for healthy status.

- [x] **Step 6: Run admin tests and commit**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Commit:

```bash
git add frontend/apps/admin-app/src frontend/apps/admin-app/e2e
git commit -m "feat(admin-app): drive knowledge-base builds by build runs"
```

---

## Task 7: Documentation, Regression, And Real Smoke

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `backend/ckqa-back/README.md`
- Modify: `graphrag_pipeline/README.md`
- Modify: `frontend/apps/admin-app/README.md`
- Modify: `docs/student-backend-graphrag-api-contract.md`

- [x] **Step 1: Update docs**

Document:

1. `GRAPHRAG_BUILD_RUNS_ROOT`
2. `GRAPHRAG_ALLOW_CONCURRENT_KB_BUILDS`
3. `GRAPHRAG_AUTO_ACTIVATION_POLICY`
4. `runtime/kb-build-runs/` ignored runtime path
5. Build-run API workflow
6. `system/health` vs `system/readiness`
7. Shared legacy `graphrag_pipeline/output` as CLI-only debug path

- [x] **Step 2: Add `.gitignore` runtime path**

Modify `.gitignore`:

```gitignore
graphrag_pipeline/runtime/kb-build-runs/
```

- [x] **Step 3: Run module tests**

Run:

```bash
cd graphrag_pipeline
conda run -n graphrag-oneapi python -m pytest tests/
```

Run:

```bash
cd backend/ckqa-back
./mvnw test
./mvnw -DskipTests compile
```

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Run repo drift check:

```bash
python scripts/audit_repo_drift.py --strict
git diff --check
```

- [x] **Step 4: Real integration smoke**

With MySQL, MinIO, One API, Neo4j and GraphRAG API available:

1. Create one knowledge base.
2. Create two build runs for the same knowledge base using different `materialIds`.
3. Confirm distinct workspaces:

```text
runtime/kb-build-runs/user_0/kb_{id}/build_{runA}
runtime/kb-build-runs/user_0/kb_{id}/build_{runB}
```

4. Run `parse-check`, `graph-input`, `prompt-confirmation`, and `index-runs`.
5. Confirm `index/input`, `index/output`, `index/output/lancedb`, and `index/logs/process.log` differ.
6. Confirm only latest build run auto-activates.
7. Manually activate the other success run.
8. Submit QA smoke and verify Java passes `dataDirUri` to Python.
9. Run GC dry-run and verify active/running build runs are skipped.

- [x] **Step 5: Final commit**

```bash
git add README.md AGENTS.md backend/ckqa-back/README.md graphrag_pipeline/README.md frontend/apps/admin-app/README.md docs/student-backend-graphrag-api-contract.md .gitignore
git commit -m "docs: document isolated graphrag build runs"
```

---

## Self-Review Checklist

- [x] SQL creates `knowledge_base_build_runs` and keeps FK ordering safe.
- [x] Legacy `POST /knowledge-bases/{id}/index-runs` still works through a compatibility build run.
- [x] No browser API accepts or returns server absolute paths.
- [x] GraphRAG CLI uses `GRAPHRAG_STORAGE_DIR/lancedb`, not `GRAPHRAG_LANCEDB_URI`, for indexing/querying.
- [x] `build_metadata` writes are transactional with status/stage changes.
- [x] Automatic activation skips older concurrent build runs.
- [x] Manual activation uses `indexRunId` and clears only `active_index_run_id IS NOT NULL` build run rows.
- [x] QA smoke failure does not mark the build run failed.
- [x] Health no longer requires shared `output/lancedb`.
- [x] Admin app uses `buildRunId` as the source of truth.
- [x] Runtime workspaces are ignored by Git.

---

## Closeout Evidence

Completed on 2026-05-05 in branch `feature/graphrag-build-run-isolation`.

- Real infrastructure check: `docker compose --env-file infra/.env -f infra/docker-compose.yml ps` showed `mysql`, `minio`, `neo4j`, and `one-api` running.
- Real build-run smoke:
  - KB 4 active index was manually switched to `indexRunId=7`.
  - Build run 3 QA smoke completed with `taskId=7`, `taskStatus=success`, `currentStage=done`, and `qaStatus=success`.
  - `graphrag_pipeline/runtime/kb-build-runs/user_9/kb_4/build_3/qa-smoke/response.json` was written with the successful assistant response.
- Real artifact checks:
  - `GET /api/v1/index-runs/7/artifacts` returned ready `output_dir`, parquet, `lancedb`, and log artifacts for `build_3`.
  - `GET /api/v1/index-runs/8/artifacts` returned ready `output_dir`, parquet, `lancedb`, and log artifacts for `build_4`.
- Real GC check: `POST /api/v1/knowledge-bases/4/build-runs/gc` with `{"dryRun":true,"deleteWorkspace":false}` returned `deletedBuildRunCount=0`, `deletedWorkspaceCount=0`, `dryRun=true`.
- Regressions fixed during smoke:
  - `QaWorkflowService` now dispatches QA workers after transaction commit, preventing worker reads before `qa_retrieval_logs` commits.
  - `QaTaskWorker` now records an early task lookup failure through the existing failed-task path when possible.
  - GraphRAG query tasks invoke `sys.executable -m graphrag query` so the managed API uses the same Python environment that started it.
  - `prompts/extract_graph.txt` now uses GraphRAG 3.0.9 literal delimiters instead of removed placeholder variables.
- Verification:
  - `backend/ckqa-back`: `./mvnw test` -> 139 tests passed.
  - `graphrag_pipeline`: `/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/` -> 181 tests passed.
  - `graphrag_pipeline`: `/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python scripts/audit_repo_drift.py --strict` -> passed.
  - `frontend/apps/admin-app`: `pnpm test` -> 1 test passed.
  - `frontend/apps/admin-app`: `pnpm build` -> passed, with the existing Vite chunk-size warning.
  - Repository: `git diff --check` -> passed.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-05-graphrag-build-run-isolation-implementation-plan.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using executing-plans, with checkpoints for review.
