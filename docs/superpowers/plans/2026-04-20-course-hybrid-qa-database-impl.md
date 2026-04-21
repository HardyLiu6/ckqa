# Course Hybrid QA Database Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不落地后端 API、认证流程和问答服务逻辑的前提下，先完成课程混合问答系统所需 MySQL 数据库 schema、基础种子数据和初始化说明。

**Architecture:** 继续以 `pdf_ingest/sql/ocqa.sql` 作为数据库结构唯一来源，不引入新的运行态服务文件。现有 `courses`、`pdf_files`、`parse_results`、`parse_logs` 保持兼容，仅在同一初始化脚本中增量补齐用户权限、课程成员、知识库、索引版本、问答与审计表。验证方式以 SQL 文本合同测试和 MySQL 手工导入验收为主，不接入 FastAPI、Spring Boot 或 CLI 管理逻辑。

**Tech Stack:** MySQL 8、SQL、Python 3.10+、`pytest`、`unittest`。

---

## File Map

- Modify: `pdf_ingest/sql/ocqa.sql`
  作用：作为 MySQL 初始化脚本唯一真源，扩展课程问答业务所需全部表、索引、约束与种子数据。
- Create: `pdf_ingest/tests/test_ocqa_access_schema_contract.py`
  作用：冻结用户、角色、认证身份、课程成员相关表结构合同。
- Create: `pdf_ingest/tests/test_ocqa_business_schema_contract.py`
  作用：冻结知识库、索引版本、问答会话、检索日志、授权审计相关表结构合同。
- Create: `pdf_ingest/tests/test_ocqa_bootstrap_contract.py`
  作用：冻结初始化脚本的可重复执行约束、角色与权限种子数据合同。
- Create: `pdf_ingest/tests/test_ocqa_docs_contract.py`
  作用：冻结数据库初始化说明和当前实施边界说明，避免文档回退。
- Modify: `pdf_ingest/README.md`
  作用：补充数据库初始化、验证 SQL、种子数据说明。
- Modify: `docs/superpowers/specs/2026-04-20-course-hybrid-qa-database-design.md`
  作用：追加“当前实施边界说明”，明确本轮只落数据库层，不包含后端服务实现。

### Task 1: 冻结用户权限与课程访问 schema 合同

**Files:**
- Create: `pdf_ingest/tests/test_ocqa_access_schema_contract.py`
- Modify: `pdf_ingest/sql/ocqa.sql`

- [ ] **Step 1: 写失败测试，锁定访问控制相关表和列**

```python
from pathlib import Path
import unittest

SQL_PATH = Path(__file__).resolve().parents[1] / "sql" / "ocqa.sql"


class TestOCQAAccessSchemaContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_access_tables_exist(self):
        for table_name in [
            "users",
            "roles",
            "permissions",
            "user_roles",
            "role_permissions",
            "auth_identities",
            "course_memberships",
            "course_membership_events",
        ]:
            self.assertIn(f"CREATE TABLE `{table_name}`", self.text)

    def test_courses_has_access_columns(self):
        self.assertIn("`status` enum('active','inactive','archived')", self.text)
        self.assertIn("`access_policy` enum('restricted','public')", self.text)

    def test_membership_and_identity_uniqueness_exist(self):
        self.assertIn("UNIQUE INDEX `uk_user_course`(`user_id` ASC, `course_id` ASC)", self.text)
        self.assertIn("UNIQUE INDEX `uk_provider_user`(`provider` ASC, `provider_user_id` ASC)", self.text)

    def test_membership_fk_targets_exist(self):
        self.assertIn("CONSTRAINT `fk_course_memberships_user`", self.text)
        self.assertIn("CONSTRAINT `fk_course_memberships_course`", self.text)
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_access_schema_contract.py -q
```

Expected:

```text
FAILED pdf_ingest/tests/test_ocqa_access_schema_contract.py::...
```

- [ ] **Step 3: 修改 `ocqa.sql`，补访问控制相关表**

```sql
DROP TABLE IF EXISTS `course_membership_events`;
DROP TABLE IF EXISTS `course_memberships`;
DROP TABLE IF EXISTS `auth_identities`;
DROP TABLE IF EXISTS `role_permissions`;
DROP TABLE IF EXISTS `user_roles`;
DROP TABLE IF EXISTS `permissions`;
DROP TABLE IF EXISTS `roles`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定业务ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名称',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `status` enum('active','disabled','locked','pending') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '用户状态',
  `last_login_at` timestamp NULL DEFAULT NULL COMMENT '最后登录时间',
  `extra_metadata` json DEFAULT NULL COMMENT '扩展元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_code`(`user_code` ASC) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台用户表' ROW_FORMAT = Dynamic;

CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '角色说明',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `permission_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '权限说明',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_permission_code`(`permission_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限表' ROW_FORMAT = Dynamic;

CREATE TABLE `user_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

CREATE TABLE `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_permission`(`role_id` ASC, `permission_id` ASC) USING BTREE,
  CONSTRAINT `fk_role_permissions_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_role_permissions_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

CREATE TABLE `auth_identities` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份提供方',
  `provider_user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供方用户ID',
  `identity_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '身份键',
  `credential_meta` json DEFAULT NULL COMMENT '凭据元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_user`(`provider` ASC, `provider_user_id` ASC) USING BTREE,
  CONSTRAINT `fk_auth_identities_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '认证身份扩展表' ROW_FORMAT = Dynamic;

CREATE TABLE `course_memberships` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `membership_role` enum('student','teacher','assistant') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'student' COMMENT '课程内角色',
  `status` enum('active','pending','suspended','removed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '成员状态',
  `access_source` enum('manual','imported','self_join','sync') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'manual' COMMENT '授权来源',
  `source_ref_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_ref_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源ID',
  `joined_at` timestamp NULL DEFAULT NULL COMMENT '加入时间',
  `expires_at` timestamp NULL DEFAULT NULL COMMENT '过期时间',
  `effective_from` timestamp NULL DEFAULT NULL COMMENT '生效开始时间',
  `effective_to` timestamp NULL DEFAULT NULL COMMENT '生效结束时间',
  `granted_by_user_id` bigint NULL DEFAULT NULL COMMENT '授权人',
  `revoked_by_user_id` bigint NULL DEFAULT NULL COMMENT '撤销人',
  `change_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `extra_metadata` json DEFAULT NULL COMMENT '扩展元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_course`(`user_id` ASC, `course_id` ASC) USING BTREE,
  INDEX `idx_course_memberships_course_status`(`course_id` ASC, `status` ASC) USING BTREE,
  CONSTRAINT `fk_course_memberships_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_granted_by` FOREIGN KEY (`granted_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_memberships_revoked_by` FOREIGN KEY (`revoked_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程成员关系表' ROW_FORMAT = Dynamic;

CREATE TABLE `course_membership_events` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_membership_id` bigint NOT NULL COMMENT '课程成员关系ID',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `old_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '旧状态',
  `new_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '新状态',
  `operator_user_id` bigint NULL DEFAULT NULL COMMENT '操作人',
  `change_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `event_payload` json DEFAULT NULL COMMENT '事件载荷',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_membership_events_membership_created`(`course_membership_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_membership_events_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_membership_events_operator` FOREIGN KEY (`operator_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程成员关系事件表' ROW_FORMAT = Dynamic;

DROP VIEW IF EXISTS `v_course_parse_overview`;
CREATE TABLE `courses`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID，如: os, cs61b',
  `course_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '课程描述',
  `status` enum('active','inactive','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '课程状态',
  `access_policy` enum('restricted','public') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'restricted' COMMENT '访问策略',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_courses_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;
```

- [ ] **Step 4: 运行测试，确认通过**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_access_schema_contract.py -q
```

Expected:

```text
....                                                                     [100%]
```

- [ ] **Step 5: 提交**

```bash
git add pdf_ingest/sql/ocqa.sql pdf_ingest/tests/test_ocqa_access_schema_contract.py
git commit -m "feat(pdf_ingest): add access control database schema"
```

### Task 2: 冻结知识库、索引、问答与审计 schema 合同

**Files:**
- Create: `pdf_ingest/tests/test_ocqa_business_schema_contract.py`
- Modify: `pdf_ingest/sql/ocqa.sql`

- [ ] **Step 1: 写失败测试，锁定业务事实表**

```python
from pathlib import Path
import unittest

SQL_PATH = Path(__file__).resolve().parents[1] / "sql" / "ocqa.sql"


class TestOCQABusinessSchemaContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_business_tables_exist(self):
        for table_name in [
            "knowledge_bases",
            "kb_documents",
            "index_runs",
            "index_artifacts",
            "qa_sessions",
            "qa_messages",
            "qa_retrieval_logs",
            "qa_retrieval_hits",
            "authorization_audit_logs",
        ]:
            self.assertIn(f"CREATE TABLE `{table_name}`", self.text)

    def test_knowledge_base_active_index_exists(self):
        self.assertIn("`active_index_run_id` bigint NULL DEFAULT NULL", self.text)
        self.assertIn("CONSTRAINT `fk_knowledge_bases_active_index_run`", self.text)

    def test_session_and_retrieval_indexes_exist(self):
        self.assertIn("UNIQUE INDEX `uk_session_code`(`session_code` ASC)", self.text)
        self.assertIn("INDEX `idx_retrieval_course_created`(`course_id` ASC, `created_at` ASC)", self.text)

    def test_audit_decision_columns_exist(self):
        self.assertIn("`decision` enum('allow','deny')", self.text)
        self.assertIn("`decision_reason` varchar(128)", self.text)
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_business_schema_contract.py -q
```

Expected:

```text
FAILED pdf_ingest/tests/test_ocqa_business_schema_contract.py::...
```

- [ ] **Step 3: 修改 `ocqa.sql`，补知识库与问答业务表**

```sql
DROP TABLE IF EXISTS `authorization_audit_logs`;
DROP TABLE IF EXISTS `qa_retrieval_hits`;
DROP TABLE IF EXISTS `qa_retrieval_logs`;
DROP TABLE IF EXISTS `qa_messages`;
DROP TABLE IF EXISTS `qa_sessions`;
DROP TABLE IF EXISTS `index_artifacts`;
DROP TABLE IF EXISTS `index_runs`;
DROP TABLE IF EXISTS `kb_documents`;
DROP TABLE IF EXISTS `knowledge_bases`;

CREATE TABLE `knowledge_bases` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程ID',
  `kb_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `status` enum('draft','active','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'draft' COMMENT '知识库状态',
  `active_index_run_id` bigint NULL DEFAULT NULL COMMENT '当前激活索引运行ID',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '知识库说明',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_kb_code`(`course_id` ASC, `kb_code` ASC) USING BTREE,
  CONSTRAINT `fk_knowledge_bases_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程知识库表' ROW_FORMAT = Dynamic;

CREATE TABLE `kb_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `source_type` enum('parse_result','normalized_doc','section_doc','page_doc','manual') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档来源类型',
  `source_ref_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源记录ID',
  `document_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档键',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档标题',
  `storage_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象存储路径',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_document_key`(`knowledge_base_id` ASC, `document_key` ASC) USING BTREE,
  CONSTRAINT `fk_kb_documents_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库文档映射表' ROW_FORMAT = Dynamic;

CREATE TABLE `index_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '知识库ID',
  `engine` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '索引引擎',
  `index_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '索引版本',
  `status` enum('pending','running','success','failed','archived') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '运行状态',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `run_metadata` json DEFAULT NULL COMMENT '运行元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_index_version`(`knowledge_base_id` ASC, `index_version` ASC) USING BTREE,
  CONSTRAINT `fk_index_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '索引运行表' ROW_FORMAT = Dynamic;

CREATE TABLE `index_artifacts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `index_run_id` bigint NOT NULL COMMENT '索引运行ID',
  `artifact_type` enum('graphrag_output','parquet','lancedb','report','other') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物类型',
  `storage_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产物路径',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小',
  `checksum` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '校验值',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_index_artifacts_run_type`(`index_run_id` ASC, `artifact_type` ASC) USING BTREE,
  CONSTRAINT `fk_index_artifacts_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '索引产物表' ROW_FORMAT = Dynamic;

CREATE TABLE `qa_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话编码',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程ID',
  `course_membership_id` bigint NULL DEFAULT NULL COMMENT '课程成员ID',
  `knowledge_base_id` bigint NULL DEFAULT NULL COMMENT '知识库ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会话标题',
  `status` enum('active','archived','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'active' COMMENT '会话状态',
  `last_message_at` timestamp NULL DEFAULT NULL COMMENT '最后消息时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_code`(`session_code` ASC) USING BTREE,
  INDEX `idx_sessions_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_sessions_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答会话表' ROW_FORMAT = Dynamic;

CREATE TABLE `qa_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` enum('system','user','assistant','tool') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色',
  `sequence_no` int NOT NULL COMMENT '消息序号',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息原始内容',
  `content_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '可检索文本',
  `token_count` int NULL DEFAULT NULL COMMENT 'Token数',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_sequence`(`session_id` ASC, `sequence_no` ASC) USING BTREE,
  CONSTRAINT `fk_messages_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答消息表' ROW_FORMAT = Dynamic;

CREATE TABLE `qa_retrieval_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程ID',
  `index_run_id` bigint NULL DEFAULT NULL COMMENT '索引运行ID',
  `query_mode` enum('local','global','full') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询模式',
  `query_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询文本',
  `retrieval_status` enum('success','partial','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检索状态',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_retrieval_course_created`(`course_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_retrieval_logs_session` FOREIGN KEY (`session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_retrieval_logs_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_retrieval_logs_index_run` FOREIGN KEY (`index_run_id`) REFERENCES `index_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答检索日志表' ROW_FORMAT = Dynamic;

CREATE TABLE `qa_retrieval_hits` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `retrieval_log_id` bigint NOT NULL COMMENT '检索日志ID',
  `document_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '命中文档键',
  `chunk_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '命中块ID',
  `rank_position` int NOT NULL COMMENT '排序位置',
  `score` decimal(12, 6) NULL DEFAULT NULL COMMENT '召回分数',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_hits_log_rank`(`retrieval_log_id` ASC, `rank_position` ASC) USING BTREE,
  CONSTRAINT `fk_retrieval_hits_log` FOREIGN KEY (`retrieval_log_id`) REFERENCES `qa_retrieval_logs` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答命中文档表' ROW_FORMAT = Dynamic;

CREATE TABLE `authorization_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `actor_user_id` bigint NOT NULL COMMENT '执行判定的用户ID',
  `target_course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标课程ID',
  `target_session_id` bigint NULL DEFAULT NULL COMMENT '目标会话ID',
  `course_membership_id` bigint NULL DEFAULT NULL COMMENT '命中的课程成员关系ID',
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作',
  `decision` enum('allow','deny') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判定结果',
  `decision_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '判定原因',
  `extra_metadata` json DEFAULT NULL COMMENT '补充元数据',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_auth_audit_actor_created`(`actor_user_id` ASC, `created_at` ASC) USING BTREE,
  CONSTRAINT `fk_auth_audit_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_course` FOREIGN KEY (`target_course_id`) REFERENCES `courses` (`course_id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_session` FOREIGN KEY (`target_session_id`) REFERENCES `qa_sessions` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_auth_audit_membership` FOREIGN KEY (`course_membership_id`) REFERENCES `course_memberships` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '授权判定审计表' ROW_FORMAT = Dynamic;

ALTER TABLE `knowledge_bases`
  ADD CONSTRAINT `fk_knowledge_bases_active_index_run`
  FOREIGN KEY (`active_index_run_id`) REFERENCES `index_runs` (`id`)
  ON DELETE SET NULL
  ON UPDATE RESTRICT;
```

- [ ] **Step 4: 运行测试，确认通过**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_business_schema_contract.py -q
```

Expected:

```text
....                                                                     [100%]
```

- [ ] **Step 5: 提交**

```bash
git add pdf_ingest/sql/ocqa.sql pdf_ingest/tests/test_ocqa_business_schema_contract.py
git commit -m "feat(pdf_ingest): add knowledge and qa database schema"
```

### Task 3: 冻结初始化种子与可重复执行合同

**Files:**
- Create: `pdf_ingest/tests/test_ocqa_bootstrap_contract.py`
- Modify: `pdf_ingest/sql/ocqa.sql`

- [ ] **Step 1: 写失败测试，锁定初始化脚本种子数据**

```python
from pathlib import Path
import unittest

SQL_PATH = Path(__file__).resolve().parents[1] / "sql" / "ocqa.sql"


class TestOCQABootstrapContract(unittest.TestCase):
    def setUp(self):
        self.text = SQL_PATH.read_text(encoding="utf-8")

    def test_foreign_key_checks_wrapped(self):
        self.assertIn("SET FOREIGN_KEY_CHECKS = 0;", self.text)
        self.assertIn("SET FOREIGN_KEY_CHECKS = 1;", self.text)

    def test_roles_seed_exists(self):
        self.assertIn("INSERT INTO `roles` (`role_code`, `role_name`, `description`)", self.text)
        self.assertIn("('user', '普通用户', '默认平台用户')", self.text)
        self.assertIn("('admin', '平台管理员', '拥有跨课程访问能力')", self.text)

    def test_permissions_seed_exists(self):
        self.assertIn("INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)", self.text)
        self.assertIn("('course.query', '课程问答访问', '允许访问课程问答')", self.text)
        self.assertIn("('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')", self.text)
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_bootstrap_contract.py -q
```

Expected:

```text
FAILED pdf_ingest/tests/test_ocqa_bootstrap_contract.py::...
```

- [ ] **Step 3: 修改 `ocqa.sql`，补角色、权限与角色权限种子**

```sql
INSERT INTO `roles` (`role_code`, `role_name`, `description`)
VALUES
  ('user', '普通用户', '默认平台用户'),
  ('admin', '平台管理员', '拥有跨课程访问能力')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `description` = VALUES(`description`);

INSERT INTO `permissions` (`permission_code`, `permission_name`, `description`)
VALUES
  ('course.query', '课程问答访问', '允许访问课程问答'),
  ('course.manage_members', '课程成员管理', '允许维护课程成员关系'),
  ('kb.manage_index', '知识库索引管理', '允许维护知识库和索引版本'),
  ('system.admin_override', '管理员越权访问', '允许管理员绕过课程成员限制')
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `description` = VALUES(`description`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code = 'course.query'
WHERE r.role_code = 'user'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);

INSERT INTO `role_permissions` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `roles` r
JOIN `permissions` p ON p.permission_code IN (
  'course.query',
  'course.manage_members',
  'kb.manage_index',
  'system.admin_override'
)
WHERE r.role_code = 'admin'
ON DUPLICATE KEY UPDATE `permission_id` = VALUES(`permission_id`);
```

- [ ] **Step 4: 运行测试，确认通过**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_bootstrap_contract.py -q
```

Expected:

```text
...                                                                      [100%]
```

- [ ] **Step 5: 提交**

```bash
git add pdf_ingest/sql/ocqa.sql pdf_ingest/tests/test_ocqa_bootstrap_contract.py
git commit -m "feat(pdf_ingest): seed roles and permissions in schema"
```

### Task 4: 补数据库初始化文档并明确当前实施边界

**Files:**
- Create: `pdf_ingest/tests/test_ocqa_docs_contract.py`
- Modify: `pdf_ingest/README.md`
- Modify: `docs/superpowers/specs/2026-04-20-course-hybrid-qa-database-design.md`

- [ ] **Step 1: 写失败测试，锁定初始化说明和边界说明**

```python
from pathlib import Path
import unittest

README_PATH = Path(__file__).resolve().parents[1] / "README.md"
SPEC_PATH = Path(__file__).resolve().parents[2] / "docs" / "superpowers" / "specs" / "2026-04-20-course-hybrid-qa-database-design.md"


class TestOCQADocsContract(unittest.TestCase):
    def test_readme_has_mysql_init_commands(self):
        text = README_PATH.read_text(encoding="utf-8")
        self.assertIn("mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < sql/ocqa.sql", text)
        self.assertIn("SELECT role_code, role_name FROM roles ORDER BY id;", text)
        self.assertIn("SHOW TABLES LIKE 'course_memberships';", text)

    def test_spec_mentions_db_only_boundary(self):
        text = SPEC_PATH.read_text(encoding="utf-8")
        self.assertIn("当前实施边界说明", text)
        self.assertIn("本轮实施只覆盖 MySQL 数据库构建", text)
        self.assertIn("不包含 FastAPI、Spring Boot 或其他后端服务实现", text)
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_docs_contract.py -q
```

Expected:

```text
FAILED pdf_ingest/tests/test_ocqa_docs_contract.py::...
```

- [ ] **Step 3: 修改 README 和 spec，补初始化说明与实施边界**

````md
## 数据库初始化

首次创建或重建本地数据库时，可直接执行：

```bash
cd pdf_ingest
mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < sql/ocqa.sql
```

初始化完成后，建议先检查核心种子数据和关键表是否存在：

```sql
SELECT role_code, role_name FROM roles ORDER BY id;
SELECT permission_code FROM permissions ORDER BY id;
SHOW TABLES LIKE 'course_memberships';
SHOW TABLES LIKE 'knowledge_bases';
SHOW TABLES LIKE 'qa_sessions';
```

当前这套 schema 只负责数据库结构和基础种子，不代表运行态 API 已经接入这些表。
````

````md
## 当前实施边界说明

本轮实施只覆盖 MySQL 数据库构建，包括：

1. `pdf_ingest/sql/ocqa.sql` 中新增表、索引、外键与种子数据
2. 对应的 schema 合同测试
3. 数据库初始化和验收文档

本轮不包含 FastAPI、Spring Boot 或其他后端服务实现，也不包含认证接口、课程授权接口、问答接口和运行态写库逻辑。
这些内容将在数据库 schema 稳定后，再以单独实施计划推进。
````

- [ ] **Step 4: 运行文档合同测试**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest pdf_ingest/tests/test_ocqa_docs_contract.py -q
```

Expected:

```text
..                                                                       [100%]
```

- [ ] **Step 5: 运行全部数据库合同测试**

Run:

```bash
/home/sunlight/miniconda3/envs/courseKg/bin/python -m pytest \
  pdf_ingest/tests/test_ocqa_access_schema_contract.py \
  pdf_ingest/tests/test_ocqa_business_schema_contract.py \
  pdf_ingest/tests/test_ocqa_bootstrap_contract.py \
  pdf_ingest/tests/test_ocqa_docs_contract.py -q
```

Expected:

```text
.............                                                            [100%]
```

- [ ] **Step 6: 手工导入并做数据库冒烟验证**

Run:

```bash
cd pdf_ingest
mysql -h 127.0.0.1 -P 23306 -u root -p ocqa < sql/ocqa.sql
mysql -h 127.0.0.1 -P 23306 -u root -p -D ocqa -e "SELECT role_code, role_name FROM roles ORDER BY id;"
mysql -h 127.0.0.1 -P 23306 -u root -p -D ocqa -e "SHOW TABLES LIKE 'qa_sessions';"
mysql -h 127.0.0.1 -P 23306 -u root -p -D ocqa -e "DESC course_memberships;"
mysql -h 127.0.0.1 -P 23306 -u root -p -D ocqa -e "DESC knowledge_bases;"
```

Expected:

```text
`roles` 返回至少 `user` / `admin` 两行，`qa_sessions` 表存在，`course_memberships` 与 `knowledge_bases` 字段定义符合设计
```

- [ ] **Step 7: 提交**

```bash
git add pdf_ingest/README.md \
  docs/superpowers/specs/2026-04-20-course-hybrid-qa-database-design.md \
  pdf_ingest/tests/test_ocqa_docs_contract.py
git commit -m "docs: add database bootstrap instructions and scope note"
```
