# 手动调优提示词向导 · Phase 2a 数据库迁移与 Java 骨架

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为手动调优提示词流水线新增数据库表（`prompt_tune_audit_samples` + `prompt_drafts`）、Java Entity/Mapper/Service 骨架、Controller 空壳（所有新端点注册但返回 501），以及前端 API 层函数声明。本期结束后后端能编译通过、新表能建出来、前端能 import 新 API 函数（虽然后端还没实现）。

**Architecture:** 遵循现有仓库模式——SQL 迁移脚本幂等（`CREATE TABLE IF NOT EXISTS` + 条件 ALTER）、Entity 用 Lombok + MyBatis-Plus 注解、Mapper 继承 `BaseMapper`、Service 继承 `IService`/`ServiceImpl`、Controller 挂在 `ApiPaths` 常量路由下。新增一个 `PromptTunePipelineController` 承载所有手动调优流水线端点，与现有 `KnowledgeBaseBuildRunsController` 分离（职责不同：后者管构建生命周期，前者管调优流水线细节）。

**Tech Stack:** Java 21 + Spring Boot 4.0.5 + MyBatis-Plus 3.5.16 + MySQL 8。前端仅新增 API 函数声明（不改组件）。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 数据模型与后端契约

**前置：** Phase 1（前端 5 步向导 mock 版）已完成。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `sql/migrations/20260515_prompt_tune_pipeline.sql` | 新建 | 新增 `prompt_tune_audit_samples` + `prompt_drafts` 两张表 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneAuditSamples.java` | 新建 | 标注样本 Entity |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptDrafts.java` | 新建 | 历史草稿 Entity |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneAuditSamplesMapper.java` | 新建 | 标注样本 Mapper |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptDraftsMapper.java` | 新建 | 历史草稿 Mapper |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneAuditSamplesService.java` | 新建 | 标注样本 Service 接口 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java` | 新建 | 历史草稿 Service 接口 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java` | 新建 | 标注样本 Service 实现 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java` | 新建 | 历史草稿 Service 实现 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleResponse.java` | 新建 | 标注样本响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java` | 新建 | 标注保存请求 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java` | 新建 | 历史草稿响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java` | 新建 | 候选响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java` | 新建 | 评分进度响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java` | 新建 | 评分报告响应 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRequest.java` | 新建 | 评分启动请求 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/FinalizePromptRequest.java` | 新建 | 保存选定候选请求 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PipelineStepResponse.java` | 新建 | 通用流水线步骤响应（触发脚本后返回） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java` | 新建 | 手动调优流水线 Controller（501 占位） |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java` | 修改 | 新增路由常量 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java` | 修改 | 新增错误码 |
| `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js` | 新建 | 前端 API 函数声明 |

---

## Task 1：数据库迁移脚本

新增两张表：`prompt_tune_audit_samples`（存储 02 步标注数据）和 `prompt_drafts`（存储 05 步历史草稿）。

**Files:**
- Create: `sql/migrations/20260515_prompt_tune_pipeline.sql`

- [ ] **Step 1: 创建迁移脚本**

```sql
-- CKQA 手动调优提示词流水线表
-- Date: 2026-05-15
-- 新增：prompt_tune_audit_samples（标注样本）、prompt_drafts（历史草稿）

-- ----------------------------
-- Table: prompt_tune_audit_samples
-- 存储 02 步标注 IDE 中每条 audit 样本的 gold 标注数据。
-- 一条样本属于一次 build run，通过 gold_stable_key 支持跨构建复用。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `prompt_tune_audit_samples` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `build_run_id` bigint NOT NULL COMMENT '所属构建流水线ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `source_sample_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源样本ID（对应 prompt_tuning_samples 中的 id）',
  `text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样本原文',
  `heading_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '章节路径',
  `page_start` int NULL DEFAULT NULL COMMENT '起始页码',
  `page_end` int NULL DEFAULT NULL COMMENT '结束页码',
  `document_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档类型',
  `audit_priority` enum('high','medium','low') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium' COMMENT '标注优先级',
  `audit_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '被选为 audit 样本的原因',
  `hit_signals` json NULL DEFAULT NULL COMMENT '命中信号列表（如 definition_signal, formula_signal）',
  `gold_entities` json NULL DEFAULT NULL COMMENT '用户标注的实体列表',
  `gold_relations` json NULL DEFAULT NULL COMMENT '用户标注的关系列表',
  `annotation_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标注备注',
  `reviewer_decision` enum('pending','in_progress','completed','skipped') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '审阅状态',
  `reviewer_confidence` decimal(3,2) NULL DEFAULT NULL COMMENT '审阅者置信度（0-1）',
  `skip_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '跳过原因',
  `gold_stable_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '稳定来源键（source_doc_id + page_start + page_end + text_hash），用于跨构建复用',
  `reused_from_build_run_id` bigint NULL DEFAULT NULL COMMENT '复用来源构建ID（历史标注复用时填写）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_samples_build_run` (`build_run_id`) USING BTREE,
  KEY `idx_audit_samples_kb_priority` (`knowledge_base_id`, `audit_priority`) USING BTREE,
  KEY `idx_audit_samples_stable_key` (`gold_stable_key`(128)) USING BTREE,
  KEY `idx_audit_samples_decision` (`build_run_id`, `reviewer_decision`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优标注样本表';

-- 外键：build_run 删除时标注数据保留（RESTRICT），便于审计
SET @has_fk_audit_samples_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_audit_samples_build_run'
);
SET @sql := IF(@has_fk_audit_samples_build_run = 0,
  'ALTER TABLE `prompt_tune_audit_samples` ADD CONSTRAINT `fk_audit_samples_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_audit_samples_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_audit_samples_kb'
);
SET @sql := IF(@has_fk_audit_samples_kb = 0,
  'ALTER TABLE `prompt_tune_audit_samples` ADD CONSTRAINT `fk_audit_samples_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- Table: prompt_drafts
-- 存储 05 步保存的历史草稿，供 01 步种子选择复用。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `prompt_drafts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '草稿名',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '说明',
  `seed` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '起始种子（system_default / graphrag_tuned / prompt_draft:N）',
  `candidate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源候选标识符',
  `prompts_json` json NOT NULL COMMENT '多 key prompt 内容快照',
  `source_build_run_id` bigint NULL DEFAULT NULL COMMENT '来自哪次构建',
  `composite_score` decimal(5,4) NULL DEFAULT NULL COMMENT '评分时的综合分',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_prompt_drafts_kb` (`knowledge_base_id`) USING BTREE,
  KEY `idx_prompt_drafts_kb_created` (`knowledge_base_id`, `created_at` DESC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优历史草稿表';

SET @has_fk_prompt_drafts_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_drafts_kb'
);
SET @sql := IF(@has_fk_prompt_drafts_kb = 0,
  'ALTER TABLE `prompt_drafts` ADD CONSTRAINT `fk_prompt_drafts_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_prompt_drafts_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_prompt_drafts_build_run'
);
SET @sql := IF(@has_fk_prompt_drafts_build_run = 0,
  'ALTER TABLE `prompt_drafts` ADD CONSTRAINT `fk_prompt_drafts_build_run` FOREIGN KEY (`source_build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: 在本地 MySQL 执行迁移验证**

Run: `mysql -u root -p ocqa < sql/migrations/20260515_prompt_tune_pipeline.sql`

Expected: 无错误输出，两张表创建成功。

- [ ] **Step 3: 验证表结构**

Run: `mysql -u root -p -e "DESCRIBE prompt_tune_audit_samples; DESCRIBE prompt_drafts;" ocqa`

Expected: 两张表字段与上述 DDL 一致。

- [ ] **Step 4: 提交**

```bash
git add sql/migrations/20260515_prompt_tune_pipeline.sql
git commit -m "feat(sql): 新增 prompt_tune_audit_samples 与 prompt_drafts 表 (Phase 2a)"
```

---

## Task 2：Entity 类

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneAuditSamples.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptDrafts.java`

- [ ] **Step 1: 创建 PromptTuneAuditSamples Entity**

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动调优标注样本表。
 * <p>
 * 存储 02 步标注 IDE 中每条 audit 样本的 gold 标注数据。
 * 通过 {@link #goldStableKey} 支持跨构建复用已有标注。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_tune_audit_samples")
public class PromptTuneAuditSamples implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("build_run_id")
    private Long buildRunId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("source_sample_id")
    private String sourceSampleId;

    @TableField("text")
    private String text;

    @TableField("heading_path")
    private String headingPath;

    @TableField("page_start")
    private Integer pageStart;

    @TableField("page_end")
    private Integer pageEnd;

    @TableField("document_type")
    private String documentType;

    /** high / medium / low */
    @TableField("audit_priority")
    private String auditPriority;

    @TableField("audit_reason")
    private String auditReason;

    /** JSON 数组：命中信号列表 */
    @TableField("hit_signals")
    private String hitSignals;

    /** JSON 数组：用户标注的实体列表 */
    @TableField("gold_entities")
    private String goldEntities;

    /** JSON 数组：用户标注的关系列表 */
    @TableField("gold_relations")
    private String goldRelations;

    @TableField("annotation_notes")
    private String annotationNotes;

    /** pending / in_progress / completed / skipped */
    @TableField("reviewer_decision")
    private String reviewerDecision;

    @TableField("reviewer_confidence")
    private BigDecimal reviewerConfidence;

    @TableField("skip_reason")
    private String skipReason;

    @TableField("gold_stable_key")
    private String goldStableKey;

    @TableField("reused_from_build_run_id")
    private Long reusedFromBuildRunId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 PromptDrafts Entity**

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动调优历史草稿表。
 * <p>
 * 存储 05 步保存的历史草稿，供 01 步种子选择复用。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_drafts")
public class PromptDrafts implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /** system_default / graphrag_tuned / prompt_draft:N */
    @TableField("seed")
    private String seed;

    @TableField("candidate_id")
    private String candidateId;

    /** JSON：多 key prompt 内容快照 */
    @TableField("prompts_json")
    private String promptsJson;

    @TableField("source_build_run_id")
    private Long sourceBuildRunId;

    @TableField("composite_score")
    private BigDecimal compositeScore;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneAuditSamples.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptDrafts.java
git commit -m "feat(entity): 新增 PromptTuneAuditSamples 与 PromptDrafts Entity (Phase 2a)"
```

---

## Task 3：Mapper 接口

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneAuditSamplesMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptDraftsMapper.java`

- [ ] **Step 1: 创建 PromptTuneAuditSamplesMapper**

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

/**
 * 手动调优标注样本表 Mapper。
 */
@Mapper
public interface PromptTuneAuditSamplesMapper extends BaseMapper<PromptTuneAuditSamples> {
}
```

- [ ] **Step 2: 创建 PromptDraftsMapper**

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptDrafts;

/**
 * 手动调优历史草稿表 Mapper。
 */
@Mapper
public interface PromptDraftsMapper extends BaseMapper<PromptDrafts> {
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneAuditSamplesMapper.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptDraftsMapper.java
git commit -m "feat(mapper): 新增 PromptTuneAuditSamplesMapper 与 PromptDraftsMapper (Phase 2a)"
```

---

## Task 4：Service 接口与实现

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneAuditSamplesService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java`

- [ ] **Step 1: 创建 PromptTuneAuditSamplesService 接口**

```java
package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.util.List;

/**
 * 手动调优标注样本 Service。
 */
public interface PromptTuneAuditSamplesService extends IService<PromptTuneAuditSamples> {

    /**
     * 按 build run 查询所有标注样本，按 audit_priority 倒序。
     */
    List<PromptTuneAuditSamples> listByBuildRunId(Long buildRunId);

    /**
     * 按 knowledge_base_id + gold_stable_key 查找已完成的历史标注（用于跨构建复用）。
     */
    List<PromptTuneAuditSamples> findCompletedByStableKeys(Long knowledgeBaseId, List<String> stableKeys);
}
```

- [ ] **Step 2: 创建 PromptDraftsService 接口**

```java
package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptDrafts;

import java.util.List;

/**
 * 手动调优历史草稿 Service。
 */
public interface PromptDraftsService extends IService<PromptDrafts> {

    /**
     * 按知识库查询历史草稿，按创建时间倒序。
     */
    List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId);
}
```

- [ ] **Step 3: 创建 PromptTuneAuditSamplesServiceImpl**

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.mapper.PromptTuneAuditSamplesMapper;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.util.List;

/**
 * 手动调优标注样本 Service 实现。
 */
@Service
public class PromptTuneAuditSamplesServiceImpl
        extends ServiceImpl<PromptTuneAuditSamplesMapper, PromptTuneAuditSamples>
        implements PromptTuneAuditSamplesService {

    @Override
    public List<PromptTuneAuditSamples> listByBuildRunId(Long buildRunId) {
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRunId)
                .orderByDesc(PromptTuneAuditSamples::getAuditPriority)
                .orderByAsc(PromptTuneAuditSamples::getSourceSampleId);
        return list(wrapper);
    }

    @Override
    public List<PromptTuneAuditSamples> findCompletedByStableKeys(Long knowledgeBaseId, List<String> stableKeys) {
        if (stableKeys == null || stableKeys.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneAuditSamples::getKnowledgeBaseId, knowledgeBaseId)
                .eq(PromptTuneAuditSamples::getReviewerDecision, "completed")
                .in(PromptTuneAuditSamples::getGoldStableKey, stableKeys);
        return list(wrapper);
    }
}
```

- [ ] **Step 4: 创建 PromptDraftsServiceImpl**

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.mapper.PromptDraftsMapper;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.util.List;

/**
 * 手动调优历史草稿 Service 实现。
 */
@Service
public class PromptDraftsServiceImpl
        extends ServiceImpl<PromptDraftsMapper, PromptDrafts>
        implements PromptDraftsService {

    @Override
    public List<PromptDrafts> listByKnowledgeBaseId(Long knowledgeBaseId) {
        LambdaQueryWrapper<PromptDrafts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptDrafts::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(PromptDrafts::getCreatedAt);
        return list(wrapper);
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneAuditSamplesService.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptDraftsService.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneAuditSamplesServiceImpl.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptDraftsServiceImpl.java
git commit -m "feat(service): 新增标注样本与历史草稿 Service (Phase 2a)"
```

---

## Task 5：DTO 类（9 个）

新建 9 个 DTO 用于流水线 API 请求/响应。

**约定：**
- 字段一律 **camelCase**（与现有 `BuildRunDetailResponse` / `BuildRunSummaryResponse` 一致）；Jackson 默认输出 camelCase JSON，不加 `@JsonProperty`。
- 响应 DTO 使用 `@Getter @Builder`（不可变），请求 DTO 使用 `@Getter @Setter`（Jackson 反序列化注入）。
- JSON 字段（`gold_entities` / `gold_relations` / `hit_signals` 等）在 DTO 层用 `List<Map<String, Object>>` / `List<String>` 等结构化类型暴露；Entity 层仍保留 `String` 存原始 JSON，由后续 Service 反序列化。
- 本期不实现 Entity↔DTO 转换方法，DTO 只占类型位置。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PipelineStepResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/FinalizePromptRequest.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java`

- [ ] **Step 1: 创建 PipelineStepResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 通用流水线步骤触发响应。
 * <p>
 * 用于 POST /prompt-tune-samples、POST /candidates、POST /extraction-eval 等
 * 异步触发型端点，反馈本次触发的状态摘要。
 * </p>
 */
@Getter
@Builder
public class PipelineStepResponse {

    /** 关联的构建流水线 ID。 */
    private final Long buildRunId;

    /** queued / running / done / failed。 */
    private final String status;

    /** 该步骤产物计数（如样本数、候选数等），无产物时可为 null。 */
    private final Integer producedCount;

    /** 该步骤耗时（秒），同步执行时填充，异步触发可为 null。 */
    private final Integer elapsedSeconds;

    /** 触发结果说明或失败摘要。 */
    private final String message;

    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
}
```

- [ ] **Step 2: 创建 AuditSampleResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 02 步标注样本响应。
 */
@Getter
@Builder
public class AuditSampleResponse {

    private final Long id;
    private final Long buildRunId;
    private final String sourceSampleId;
    private final String text;
    private final String headingPath;
    private final Integer pageStart;
    private final Integer pageEnd;
    private final String documentType;

    /** high / medium / low。 */
    private final String auditPriority;
    private final String auditReason;

    /** 命中信号列表，如 ["definition_signal", "formula_signal"]。 */
    private final List<String> hitSignals;

    /** 用户标注的实体列表，元素 schema 见 spec § 02 步。 */
    private final List<Map<String, Object>> goldEntities;

    /** 用户标注的关系列表，元素 schema 见 spec § 02 步。 */
    private final List<Map<String, Object>> goldRelations;

    private final String annotationNotes;

    /** pending / in_progress / completed / skipped。 */
    private final String reviewerDecision;
    private final BigDecimal reviewerConfidence;
    private final String skipReason;
    private final String goldStableKey;

    /** 当本条样本是从历史标注复用而来时填充，否则为 null。 */
    private final ReusedFromInfo reusedFrom;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class ReusedFromInfo {
        private final Long buildRunId;
        private final String buildRunName;
        private final LocalDateTime reusedAt;
    }
}
```

- [ ] **Step 3: 创建 AuditSampleUpdateRequest**

```java
package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PUT /audit-samples/{sampleId} 请求体。
 * <p>
 * 所有字段均可选——只下发本次需要更新的字段；后端按存在性合并。
 * </p>
 */
@Getter
@Setter
public class AuditSampleUpdateRequest {

    private List<Map<String, Object>> goldEntities;
    private List<Map<String, Object>> goldRelations;
    private String annotationNotes;

    /** pending / in_progress / completed / skipped。 */
    private String reviewerDecision;

    @DecimalMin(value = "0.00", message = "置信度不得低于 0")
    @DecimalMax(value = "1.00", message = "置信度不得高于 1")
    private BigDecimal reviewerConfidence;

    private String skipReason;
}
```

- [ ] **Step 4: 创建 CandidateResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 03 步候选提示词响应。
 * <p>
 * 字段来自 `graphrag_pipeline/scripts/prompt_tuning/manifest.json`。
 * </p>
 */
@Getter
@Builder
public class CandidateResponse {

    /** 稳定标识符，如 schema_fewshot_distilled_v2_strict_tuple。 */
    private final String candidateId;

    /** 中文译名；后端优先从 manifest 读取，缺失时前端有 hardcode fallback。 */
    private final String displayNameZh;

    /** baseline / auto_tuned / schema_aware / schema_fewshot。 */
    private final String category;

    /** 是否为推荐候选（manifest.notes 标注或上一次评分历史决定）。 */
    private final Boolean isRecommended;

    /** 特性标签，如 ["schema_injected", "directional_card", "few_shot_distilled"]。 */
    private final List<String> traits;

    private final Integer estimatedTokenPerCall;
    private final Integer promptSizeBytes;
    private final String schemaUsed;
    private final Integer fewshotExampleCount;
    private final String fewshotStrategy;
    private final String basePromptSource;
    private final LocalDateTime generationTime;
}
```

- [ ] **Step 5: 创建 ExtractionEvalRequest**

```java
package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * POST /extraction-eval 请求体。
 */
@Getter
@Setter
public class ExtractionEvalRequest {

    /** 用户在 03 步勾选的候选 ID 列表，至少 1 个。 */
    @NotEmpty(message = "selectedCandidates 不能为空")
    private List<String> selectedCandidates;
}
```

- [ ] **Step 6: 创建 ExtractionEvalStatusResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /extraction-eval/status 评分进度响应。
 */
@Getter
@Builder
public class ExtractionEvalStatusResponse {

    private final Overall overall;
    private final List<CandidateProgress> candidates;

    @Getter
    @Builder
    public static class Overall {
        private final Integer totalCalls;
        private final Integer finishedCalls;
        private final Integer elapsedSeconds;
        private final Integer estimatedRemainingSeconds;
        private final Integer tokensUsed;
        private final Integer estimatedTotalTokens;
    }

    @Getter
    @Builder
    public static class CandidateProgress {
        private final String candidateId;
        private final Stage extract;
        private final Stage score;

        /** queued / extracting / scoring / done / failed。 */
        private final String status;
    }

    @Getter
    @Builder
    public static class Stage {
        private final Integer finished;
        private final Integer total;
        private final String currentSampleId;
    }
}
```

- [ ] **Step 7: 创建 ExtractionEvalReportResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /extraction-eval/report 评分排行榜响应。
 */
@Getter
@Builder
public class ExtractionEvalReportResponse {

    private final List<CandidateReport> candidates;

    @Getter
    @Builder
    public static class CandidateReport {
        private final String candidateId;
        private final Integer rank;
        private final BigDecimal compositeScore;
        private final BigDecimal parseSuccessRate;
        private final BigDecimal recall;
        private final BigDecimal precision;
        private final BigDecimal f1;
        private final BigDecimal entityCountAvg;
        private final BigDecimal relationCountAvg;
        private final Integer tokensUsed;
        private final Integer elapsedSeconds;
        private final List<Gate> gates;
        private final List<FailedSample> failedSamples;
    }

    @Getter
    @Builder
    public static class Gate {
        /** parse_success / audit_recall / audit_precision / relation_direction。 */
        private final String name;
        private final BigDecimal threshold;
        private final BigDecimal value;
        private final Boolean passed;
    }

    @Getter
    @Builder
    public static class FailedSample {
        private final String sampleId;
        private final String reason;
    }
}
```

- [ ] **Step 8: 创建 FinalizePromptRequest**

```java
package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /finalize 请求体。
 * <p>
 * 用户从 04 排行榜选定一个候选并落库到 customPromptDraft，可选同时入库 prompt_drafts。
 * </p>
 */
@Getter
@Setter
public class FinalizePromptRequest {

    @NotBlank(message = "candidateId 必填")
    private String candidateId;

    /** true 时同时入库 prompt_drafts；默认 false。 */
    private Boolean saveAsDraft;

    @Size(max = 128, message = "草稿名最长 128 字符")
    private String draftName;

    private String draftDescription;
}
```

- [ ] **Step 9: 创建 PromptDraftResponse**

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GET /knowledge-bases/{kbId}/prompt-drafts 历史草稿响应。
 */
@Getter
@Builder
public class PromptDraftResponse {

    private final Long id;
    private final Long knowledgeBaseId;
    private final String name;
    private final String description;

    /** system_default / graphrag_tuned / prompt_draft:N。 */
    private final String seed;
    private final String candidateId;

    /** 多 key prompt 内容快照，JSON 字符串形态（前端按需解析）。 */
    private final String promptsJson;

    private final Long sourceBuildRunId;
    private final BigDecimal compositeScore;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
```

- [ ] **Step 10: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PipelineStepResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/AuditSampleUpdateRequest.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/CandidateResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRequest.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/FinalizePromptRequest.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/PromptDraftResponse.java
git commit -m "feat(dto): 新增手动调优流水线 9 个 DTO (Phase 2a)"
```

---

## Task 6：ApiResultCode 扩展

新增 1 个错误码 `PIPELINE_NOT_IMPLEMENTED`，供本期 Controller 501 占位使用。后续 2b–2e 各自实现时再按需追加 `AUDIT_SAMPLE_NOT_FOUND` / `CANDIDATE_NOT_FOUND` / `EXTRACTION_EVAL_NOT_READY` 等业务错误码（不在 2a 预添，避免冗余）。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

- [ ] **Step 1: 在 `ApiResultCode` 末尾、`INDEX_RUN_EXECUTION_FAILED(5004, ...)` 之后追加新条目**

定位锚点：现有最后一个枚举值 `INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败");`，将其末尾分号改为逗号，并新增：

```java
    /**
     * 接口尚未实现（占位）。
     */
    PIPELINE_NOT_IMPLEMENTED(5099, "接口尚未实现");
```

完整修改示例：

```java
    INDEX_RUN_EXECUTION_FAILED(5004, "索引任务执行失败"),

    /**
     * 接口尚未实现（占位）。
     */
    PIPELINE_NOT_IMPLEMENTED(5099, "接口尚未实现");
```

- [ ] **Step 2: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(api): 新增 PIPELINE_NOT_IMPLEMENTED 占位错误码 (Phase 2a)"
```

---

## Task 7：ApiPaths 扩展

新增 `RELATION_SCHEMAS` 常量供 `GET /api/v1/relation-schemas` 端点使用。其余 12 个新端点都已经在 `KNOWLEDGE_BASE_BUILD_RUNS` / `KNOWLEDGE_BASES` 下，复用现有常量。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java`

- [ ] **Step 1: 在 `ApiPaths` 类中按字母序插入 `RELATION_SCHEMAS`**

定位锚点：现有 `QA_SESSIONS` 之后、`ROLE_PERMISSIONS` 之前（字母序）。

```java
    public static final String QA_SESSIONS = API_V1 + "/qa-sessions";
    public static final String RELATION_SCHEMAS = API_V1 + "/relation-schemas";
    public static final String ROLE_PERMISSIONS = API_V1 + "/role-permissions";
```

- [ ] **Step 2: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiPaths.java
git commit -m "feat(api): 新增 RELATION_SCHEMAS 路由常量 (Phase 2a)"
```

---

## Task 8：PromptTunePipelineController（501 占位）

新建单一 controller 承载手动调优流水线全部 13 个端点。由于 13 个端点跨 3 个基础路径（`/knowledge-base-build-runs`、`/knowledge-bases`、`/relation-schemas`），controller 类**不使用** class 级 `@RequestMapping`，每个方法用 `ApiPaths.XXX + "/..."` 拼接完整路径。

所有方法体抛 `new BusinessException(ApiResultCode.PIPELINE_NOT_IMPLEMENTED, HttpStatus.NOT_IMPLEMENTED)`，全局异常处理器会转换为 HTTP 501 响应。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1: 创建 PromptTunePipelineController**

```java
package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.index.dto.PipelineStepResponse;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;

import java.util.List;
import java.util.Map;

/**
 * 手动调优提示词流水线控制器（Phase 2a 占位）。
 * <p>
 * 本期所有端点统一返回 HTTP 501，2b–2e 分阶段落地具体实现：
 * <ul>
 *   <li>2b：02 步标注 API（audit-set / audit-samples / 更新 / ai-suggestions）</li>
 *   <li>2c：03 步候选 API（candidates / 候选预览相关）</li>
 *   <li>2d：04 步评分 API（extraction-eval 三件套）</li>
 *   <li>2e：05 步保存 + 历史草稿（finalize / prompt-drafts）+ relation-schemas</li>
 * </ul>
 * </p>
 */
@Validated
@RestController
@RequiredArgsConstructor
public class PromptTunePipelineController {

    // ------------------------------------------------------------
    // 02 步：构建准备材料
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/prompt-tune-samples")
    public ApiResponse<PipelineStepResponse> triggerPromptTuneSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-set")
    public ApiResponse<List<AuditSampleResponse>> generateAuditSet(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples")
    public ApiResponse<List<AuditSampleResponse>> listAuditSamples(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @PutMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}")
    public ApiResponse<AuditSampleResponse> updateAuditSample(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @Positive(message = "sampleId必须大于0") Long sampleId,
            @Valid @RequestBody AuditSampleUpdateRequest request
    ) {
        throw notImplemented();
    }

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/audit-samples/{sampleId}/ai-suggestions")
    public ApiResponse<Map<String, Object>> requestAuditSampleAiSuggestions(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @PathVariable @Positive(message = "sampleId必须大于0") Long sampleId
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 03 步：生成候选提示词
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<PipelineStepResponse> generateCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/candidates")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 04 步：抽取评分
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval")
    public ApiResponse<PipelineStepResponse> startExtractionEval(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody ExtractionEvalRequest request
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/status")
    public ApiResponse<ExtractionEvalStatusResponse> getExtractionEvalStatus(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/report")
    public ApiResponse<ExtractionEvalReportResponse> getExtractionEvalReport(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 05 步：预览保存
    // ------------------------------------------------------------

    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/finalize")
    public ApiResponse<BuildRunDetailResponse> finalizePrompt(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody FinalizePromptRequest request
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASES + "/{kbId}/prompt-drafts")
    public ApiResponse<List<PromptDraftResponse>> listPromptDrafts(
            @PathVariable @Positive(message = "kbId必须大于0") Long kbId
    ) {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 通用辅助：02 步标注下拉过滤数据
    // ------------------------------------------------------------

    @GetMapping(ApiPaths.RELATION_SCHEMAS)
    public ApiResponse<Map<String, Object>> listRelationSchemas() {
        throw notImplemented();
    }

    // ------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------

    private static BusinessException notImplemented() {
        return new BusinessException(ApiResultCode.PIPELINE_NOT_IMPLEMENTED, HttpStatus.NOT_IMPLEMENTED);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(controller): 新增 PromptTunePipelineController（501 占位）(Phase 2a)"
```

---

## Task 9：前端 API 函数声明

新建 `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`，导出 13 个对应后端端点的 axios 调用函数。本期不接入到 Vue 组件，后续 2b–2e 各步骤分别消费。

**Files:**
- Create: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`

- [ ] **Step 1: 创建 prompt-tune-pipeline.js**

```js
import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

// ----- 02 步：构建准备材料 -----

export async function triggerPromptTuneSamples(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/prompt-tune-samples`,
  ))
}

export async function generateAuditSet(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-set`,
  ))
}

export async function listAuditSamples(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples`,
  ))
}

export async function updateAuditSample(buildRunId, sampleId, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples/${encodeURIComponent(sampleId)}`,
    payload,
  ))
}

export async function requestAuditSampleAiSuggestions(buildRunId, sampleId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples/${encodeURIComponent(sampleId)}/ai-suggestions`,
  ))
}

// ----- 03 步：生成候选提示词 -----

export async function generateCandidates(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates`,
  ))
}

export async function listCandidates(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates`,
  ))
}

// ----- 04 步：抽取评分 -----

export async function startExtractionEval(buildRunId, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval`,
    payload,
  ))
}

export async function getExtractionEvalStatus(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/status`,
  ))
}

export async function getExtractionEvalReport(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/report`,
  ))
}

// ----- 05 步：预览保存 -----

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

// ----- 通用：02 步标注关系下拉数据 -----

export async function listRelationSchemas(client = http) {
  return unwrapApiResponse(await client.get('/relation-schemas'))
}
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js
git commit -m "feat(api): 新增手动调优流水线前端 API 桩 (Phase 2a)"
```

---

## Task 10：全量编译/构建验证

- [ ] **Step 1: 后端编译验证**

Run: `cd backend/ckqa-back && mvn -q -DskipTests compile`

Expected: `BUILD SUCCESS`，无编译错误。若出现 `cannot find symbol` 类错误，检查 import 与 ApiResultCode 是否同步落地。

- [ ] **Step 2: 前端 lint / 解析验证**

Run: `cd frontend/apps/admin-app && npm run lint -- src/api/prompt-tune-pipeline.js`

Expected: 无 lint 错误。若项目未配置单文件 lint，可用 `npx eslint src/api/prompt-tune-pipeline.js`。

- [ ] **Step 3: 前端 API 函数数量自检**

Run: `grep -c "^export async function" frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`

Expected: 输出 `13`（与后端 13 个端点一一对应）。

- [ ] **Step 4: 表存在性复核**

Run: `mysql -uroot -p ocqa -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('prompt_tune_audit_samples', 'prompt_drafts');"`

Expected: 两行结果（与 Task 1 一致；如尚未在本地 MySQL 跑过迁移可重跑）。

- [ ] **Step 5: 无新增 commit，仅记录验证完成**

本步骤不产生新文件改动，无需 commit。验证通过后，Phase 2a 完结：

- 数据库就绪：两张表已建。
- 后端骨架就绪：Entity / Mapper / Service / DTO / Controller 全部能编译通过；13 个端点返回 HTTP 501。
- 前端 API 桩就绪：13 个函数已声明，可被任意组件 import；调用会因 501 自动走 axios 错误处理（本期组件层不消费）。

下一步：进入 Phase 2b（02 步标注 API + 前端接入）。

---
