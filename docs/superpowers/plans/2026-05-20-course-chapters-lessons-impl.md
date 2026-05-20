# 课程章节/课时管理与学生端联动 实施计划（PR 3）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 CKQA 现有 `courses` 体系上，落地"课程章节 + 课时"两层结构，admin-app 提供完整的章节/课时管理后台，student-app 课程详情页和学习页接入真实数据，移除占位文案。

**Architecture:** 后端新增 `course_chapters` / `course_lessons` 两表 + REST CRUD 接口（沿用 `/api/v1/courses/{courseId}/chapters` 占位接口扩展为真实接口）。admin-app 把 `course-chapters` 路由从占位页换成真实管理界面（章节列表 + 课时编辑抽屉）。student-app 的 `CourseDetail.vue` 章节 tab 与 `CourseLearn.vue` 目录侧栏从 mock 改为接口数据，预留学习进度心跳接口（PR 4 落地，本 PR 仅打桩）。本期不做视频播放器集成、学习进度持久化、评价。

**Tech Stack:**
- 后端：Spring Boot 4.0.5 / MyBatis-Plus 3.5.16 / MySQL 8 / Java 21
- 前端 admin：Vue 3 + Vite + Element Plus + Pinia
- 前端 student：Vue 3 + Vite + Element Plus + Pinia
- DB：迁移文件 `sql/migrations/20260520_course_chapters_lessons.sql`

---

## 0. 前置约定

- 工作目录：`/home/sunlight/Projects/ckqa/.worktrees/student-knowledge-graph`
- 工作分支：`feat/2026-05-19-student-knowledge-graph`（接续之前 PR）
- 用户身份：`LiuJunDa <3364863955@qq.com>`，所有提交都用此身份
- 测试命令（后端）：`./mvnw '-Dtest=!IndexProgressParserTest' test`
- 测试命令（admin-app）：`pnpm test`
- 测试命令（student-app）：`pnpm test`
- 提交风格：`feat(course-lms)` / `feat(admin-chapters)` / `feat(student-chapters)` 等
- 中文优先：注释、文档、UI 文案全部中文；技术名词（API/PR/MyBatis-Plus 等）保留英文

## 1. File Structure（一览）

### 后端新增/修改

| 文件 | 责任 |
| --- | --- |
| `sql/migrations/20260520_course_chapters_lessons.sql` | 新增 chapters/lessons 表 |
| `sql/ocqa.sql` | 同步主 schema |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseChapters.java` | 章节实体 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java` | 课时实体 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseChaptersMapper.java` | 章节 mapper |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseLessonsMapper.java` | 课时 mapper |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseChapterService.java` | 章节读取/写入业务逻辑 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLessonService.java` | 课时读取/写入业务逻辑 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/ChapterDto.java` | 章节响应/请求 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/LessonDto.java` | 课时响应/请求 DTO |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseChaptersResponse.java` | （改造）从占位结构改为真实结构 |
| `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java` | （改造）`/chapters` 接口接真实数据 + 新增管理路由 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseChapterServiceTest.java` | 章节 service 单测 |
| `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseLessonServiceTest.java` | 课时 service 单测 |

### admin-app 新增/修改

| 文件 | 责任 |
| --- | --- |
| `frontend/apps/admin-app/src/api/chapters.js` | 新增章节/课时 API 封装 |
| `frontend/apps/admin-app/src/views/pages/courses/ChapterManagementPanel.vue` | 章节管理主面板（路由 course-chapters 渲染入口） |
| `frontend/apps/admin-app/src/views/pages/courses/ChapterEditDialog.vue` | 章节增/编辑对话框 |
| `frontend/apps/admin-app/src/views/pages/courses/LessonEditDrawer.vue` | 课时增/编辑抽屉 |
| `frontend/apps/admin-app/src/views/pages/module-content.js` | （改造）course-chapters 配置改为 live + dynamicComponent |
| `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | （改造）course-chapters 路由渲染 ChapterManagementPanel |
| `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | （改造）课程详情页加"管理章节"入口 |

### student-app 新增/修改

| 文件 | 责任 |
| --- | --- |
| `frontend/apps/student-app/src/api/courses.js` | （扩展）listChapters 实际请求结构对接 |
| `frontend/apps/student-app/src/stores/course.js` | （扩展）章节 state + loadChapters |
| `frontend/apps/student-app/src/views/course/CourseDetail.vue` | （改造）章节 tab 渲染真实数据，去除"功能预览"alert |
| `frontend/apps/student-app/src/views/course/CourseLearn.vue` | （改造）侧边栏目录从 mock 切换为真实数据 |

---

## Task 1：数据库迁移（章节 + 课时表）

**Files:**
- Create: `sql/migrations/20260520_course_chapters_lessons.sql`
- Modify: `sql/ocqa.sql`（在 `courses` 表后追加）

- [ ] **Step 1: 写迁移脚本**

文件 `sql/migrations/20260520_course_chapters_lessons.sql`：

```sql
-- ============================================================================
-- 课程章节/课时表（PR 3）
-- 起草日期：2026-05-20
-- 关联文档：docs/2026-05-19-course-info-architecture-plan.md §6.1
--
-- 用途：
--   为 admin-app 章节管理后台与 student-app 学习页提供持久化存储。
--   学习进度（course_lesson_progress）留待 PR 4 视频播放器联动一起加。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `course_chapters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联 courses.course_id',
  `chapter_order` int NOT NULL COMMENT '同一课程内 1 起步，唯一',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_course_order`(`course_id`, `chapter_order`, `is_deleted`),
  KEY `idx_chapter_course`(`course_id`),
  CONSTRAINT `fk_chapters_course` FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程章节';

CREATE TABLE IF NOT EXISTS `course_lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chapter_id` bigint NOT NULL COMMENT '关联 course_chapters.id',
  `lesson_order` int NOT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `lesson_type` enum('video','document','quiz','live','external') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'video',
  `content_uri` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '视频/文档外链或 MinIO 引用',
  `duration_minutes` int NULL,
  `is_free_preview` tinyint(1) NOT NULL DEFAULT 0,
  `material_id` bigint NULL COMMENT '关联 course_materials.id，可空',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_chapter_order`(`chapter_id`, `lesson_order`, `is_deleted`),
  KEY `idx_lesson_chapter`(`chapter_id`),
  CONSTRAINT `fk_lessons_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapters`(`id`),
  CONSTRAINT `fk_lessons_material` FOREIGN KEY (`material_id`) REFERENCES `course_materials`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程课时';
```

> 设计说明：唯一键加上 `is_deleted` 列，避免逻辑删除导致重排序时撞约束。

- [ ] **Step 2: 应用迁移到本地 MySQL**

```bash
docker exec -i mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ocqa < sql/migrations/20260520_course_chapters_lessons.sql
```

预期：无错误输出。

- [ ] **Step 3: 验证表结构**

```bash
docker exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ocqa -e "SHOW CREATE TABLE course_chapters\G; SHOW CREATE TABLE course_lessons\G"
```

预期：两张表存在，包含上述全部字段、索引和外键。

- [ ] **Step 4: 同步 sql/ocqa.sql**

把 Step 1 的两段 `CREATE TABLE` 追加到 `sql/ocqa.sql` 的 `courses` 表定义之后，作为初始化 schema 的一部分。

- [ ] **Step 5: 写少量测试数据（kb5 课程）**

```sql
-- sql/migrations/20260520_course_chapters_lessons_demo.sql（仅本机演示，不入版本控制）
INSERT INTO course_chapters(course_id, chapter_order, title, description) VALUES
  ('crs-20260506-r4slkr', 1, '第一章 操作系统概述', '系统调用、内核态/用户态'),
  ('crs-20260506-r4slkr', 2, '第二章 进程与线程', '进程模型、调度、同步');

INSERT INTO course_lessons(chapter_id, lesson_order, title, lesson_type, duration_minutes) VALUES
  ((SELECT id FROM course_chapters WHERE course_id='crs-20260506-r4slkr' AND chapter_order=1), 1, '什么是操作系统', 'video', 18),
  ((SELECT id FROM course_chapters WHERE course_id='crs-20260506-r4slkr' AND chapter_order=1), 2, '系统调用机制', 'video', 22),
  ((SELECT id FROM course_chapters WHERE course_id='crs-20260506-r4slkr' AND chapter_order=2), 1, '进程的概念', 'video', 25),
  ((SELECT id FROM course_chapters WHERE course_id='crs-20260506-r4slkr' AND chapter_order=2), 2, 'CPU 调度算法', 'video', 32);
```

执行：

```bash
docker exec -i mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" ocqa < /tmp/chapters_demo.sql
```

- [ ] **Step 6: Commit**

```bash
git add sql/migrations/20260520_course_chapters_lessons.sql sql/ocqa.sql
git commit -m "feat(db): 新增 course_chapters / course_lessons 表（PR 3 章节课时）" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 2：后端实体与 Mapper

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseChapters.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseChaptersMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseLessonsMapper.java`

- [ ] **Step 1: 创建 CourseChapters 实体**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseChapters.java`：

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程章节实体（PR 3）。
 */
@Getter
@Setter
@TableName("course_chapters")
public class CourseChapters implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private String courseId;

    @TableField("chapter_order")
    private Integer chapterOrder;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 CourseLessons 实体**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java`：

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程课时实体（PR 3）。
 */
@Getter
@Setter
@TableName("course_lessons")
public class CourseLessons implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("lesson_order")
    private Integer lessonOrder;

    @TableField("title")
    private String title;

    /** 取值：video / document / quiz / live / external */
    @TableField("lesson_type")
    private String lessonType;

    @TableField("content_uri")
    private String contentUri;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("is_free_preview")
    private Boolean isFreePreview;

    @TableField("material_id")
    private Long materialId;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建两个 Mapper 接口**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseChaptersMapper.java`：

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.CourseChapters;

@Mapper
public interface CourseChaptersMapper extends BaseMapper<CourseChapters> {
}
```

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseLessonsMapper.java`：

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.CourseLessons;

@Mapper
public interface CourseLessonsMapper extends BaseMapper<CourseLessons> {
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS，无编译错误。

- [ ] **Step 5: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseChapters.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseChaptersMapper.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseLessonsMapper.java
git commit -m "feat(course-lms): 新增 CourseChapters/CourseLessons 实体与 mapper" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 3：后端 DTO 定义

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/ChapterDto.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/LessonDto.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseChaptersResponse.java`

- [ ] **Step 1: 创建 ChapterDto（含请求/响应静态嵌套）**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/ChapterDto.java`：

```java
package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节 DTO 集合（PR 3）。
 */
public final class ChapterDto {

    private ChapterDto() {}

    /** 章节响应（含课时列表） */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private Long id;
        private String courseId;
        private Integer chapterOrder;
        private String title;
        private String description;
        private List<LessonDto.Response> lessons;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /** 章节创建请求 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "章节标题不能为空")
        @Size(max = 255, message = "章节标题最长 255 个字符")
        private String title;

        @Size(max = 2000, message = "章节描述最长 2000 个字符")
        private String description;

        /** 不传则追加到最后 */
        @Min(value = 1, message = "章节序号从 1 起步")
        private Integer chapterOrder;
    }

    /** 章节更新请求（部分更新） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255, message = "章节标题最长 255 个字符")
        private String title;

        @Size(max = 2000, message = "章节描述最长 2000 个字符")
        private String description;
    }

    /** 章节排序请求（一次性设置所有章节顺序） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderRequest {
        /** 按目标顺序传入的章节 id 列表，索引 0 → chapter_order=1 */
        @Size(min = 1, max = 200, message = "章节排序至少 1 个，最多 200 个")
        private List<Long> chapterIds;
    }
}
```

- [ ] **Step 2: 创建 LessonDto**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/LessonDto.java`：

```java
package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课时 DTO 集合（PR 3）。
 */
public final class LessonDto {

    private LessonDto() {}

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private Long id;
        private Long chapterId;
        private Integer lessonOrder;
        private String title;
        /** video / document / quiz / live / external */
        private String lessonType;
        private String contentUri;
        private Integer durationMinutes;
        private Boolean isFreePreview;
        private Long materialId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "课时标题不能为空")
        @Size(max = 255)
        private String title;

        @NotBlank(message = "课时类型不能为空")
        @Pattern(regexp = "^(video|document|quiz|live|external)$", message = "课时类型非法")
        private String lessonType;

        @Size(max = 1024)
        private String contentUri;

        @Min(value = 0, message = "课时时长不能为负")
        @Max(value = 600, message = "课时时长不超过 600 分钟")
        private Integer durationMinutes;

        private Boolean isFreePreview;

        private Long materialId;

        /** 不传则追加到本章节最后 */
        @Min(value = 1)
        private Integer lessonOrder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255)
        private String title;

        @Pattern(regexp = "^(video|document|quiz|live|external)$", message = "课时类型非法")
        private String lessonType;

        @Size(max = 1024)
        private String contentUri;

        @Min(value = 0)
        @Max(value = 600)
        private Integer durationMinutes;

        private Boolean isFreePreview;

        private Long materialId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderRequest {
        @Size(min = 1, max = 200)
        private List<Long> lessonIds;
    }
}
```

- [ ] **Step 3: 改造 CourseChaptersResponse 兼容真实数据**

替换 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseChaptersResponse.java` 全文：

```java
package org.ysu.ckqaback.course.dto;

import lombok.Getter;

import java.util.List;

/**
 * 课程章节列表响应。
 * <p>
 * PR 3 起从占位接口扩展为真实接口。仍保留 {@code featureStatus} 字段以兼容
 * student-app 旧逻辑：
 * <ul>
 *   <li>{@code featureStatus = "ready"}：返回真实章节数据</li>
 *   <li>{@code featureStatus = "coming-soon"}：兜底（极少使用，仅在功能未启用时）</li>
 * </ul>
 * </p>
 */
@Getter
public class CourseChaptersResponse {

    private final List<ChapterDto.Response> chapters;
    private final String featureStatus;
    private final String message;

    private CourseChaptersResponse(List<ChapterDto.Response> chapters, String featureStatus, String message) {
        this.chapters = chapters;
        this.featureStatus = featureStatus;
        this.message = message;
    }

    public static CourseChaptersResponse ready(List<ChapterDto.Response> chapters) {
        return new CourseChaptersResponse(chapters, "ready", null);
    }

    public static CourseChaptersResponse comingSoon() {
        return new CourseChaptersResponse(List.of(), "coming-soon", "课程章节功能即将开放，敬请期待");
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/ChapterDto.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/LessonDto.java \
        backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/dto/CourseChaptersResponse.java
git commit -m "feat(course-lms): 新增 ChapterDto/LessonDto，CourseChaptersResponse 支持真实结构" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 4：CourseChapterService（章节读写业务）

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseChapterService.java`

- [ ] **Step 1: 写 CourseChapterService 主体**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseChapterService.java`：

```java
package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.exception.BusinessException;
import org.ysu.ckqaback.api.error.ApiErrorCode;
import org.ysu.ckqaback.course.dto.ChapterDto;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 章节读写业务。
 * <p>章节本身的列表查询会同时返回每个章节的课时，便于 admin-app/student-app 一次拿到树形数据。</p>
 */
@Service
public class CourseChapterService {

    private final CourseChaptersMapper chaptersMapper;
    private final CourseLessonsMapper lessonsMapper;
    private final CourseLookupService courseLookupService;

    public CourseChapterService(
            CourseChaptersMapper chaptersMapper,
            CourseLessonsMapper lessonsMapper,
            CourseLookupService courseLookupService
    ) {
        this.chaptersMapper = chaptersMapper;
        this.lessonsMapper = lessonsMapper;
        this.courseLookupService = courseLookupService;
    }

    /** 列出某课程的章节（含课时）。按 chapter_order / lesson_order 升序。 */
    public List<ChapterDto.Response> listChapters(String courseId) {
        // 校验课程存在
        courseLookupService.getCourseDetail(courseId);

        LambdaQueryWrapper<CourseChapters> chapterQuery = new LambdaQueryWrapper<>();
        chapterQuery.eq(CourseChapters::getCourseId, courseId)
                .orderByAsc(CourseChapters::getChapterOrder);
        List<CourseChapters> chapters = chaptersMapper.selectList(chapterQuery);
        if (chapters.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> chapterIds = chapters.stream().map(CourseChapters::getId).toList();
        LambdaQueryWrapper<CourseLessons> lessonQuery = new LambdaQueryWrapper<>();
        lessonQuery.in(CourseLessons::getChapterId, chapterIds)
                .orderByAsc(CourseLessons::getLessonOrder);
        List<CourseLessons> lessons = lessonsMapper.selectList(lessonQuery);

        Map<Long, List<LessonDto.Response>> lessonsByChapter = new HashMap<>();
        for (CourseLessons lesson : lessons) {
            lessonsByChapter.computeIfAbsent(lesson.getChapterId(), k -> new ArrayList<>())
                    .add(toLessonResponse(lesson));
        }

        return chapters.stream()
                .map(c -> ChapterDto.Response.builder()
                        .id(c.getId())
                        .courseId(c.getCourseId())
                        .chapterOrder(c.getChapterOrder())
                        .title(c.getTitle())
                        .description(c.getDescription())
                        .lessons(lessonsByChapter.getOrDefault(c.getId(), Collections.emptyList()))
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public ChapterDto.Response createChapter(String courseId, ChapterDto.CreateRequest request) {
        courseLookupService.getCourseDetail(courseId);

        Integer order = request.getChapterOrder();
        if (order == null) {
            order = nextChapterOrder(courseId);
        } else {
            ensureChapterOrderAvailable(courseId, order, null);
        }

        CourseChapters entity = new CourseChapters();
        entity.setCourseId(courseId);
        entity.setChapterOrder(order);
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(request.getDescription());
        chaptersMapper.insert(entity);

        return toChapterResponse(entity, Collections.emptyList());
    }

    @Transactional
    public ChapterDto.Response updateChapter(String courseId, Long chapterId, ChapterDto.UpdateRequest request) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            chapter.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            chapter.setDescription(request.getDescription());
        }
        chaptersMapper.updateById(chapter);
        return toChapterResponse(chapter, listLessonsOf(chapter.getId()));
    }

    @Transactional
    public void deleteChapter(String courseId, Long chapterId) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        // 级联软删除该章节下的所有课时
        LambdaUpdateWrapper<CourseLessons> lessonUpdate = new LambdaUpdateWrapper<>();
        lessonUpdate.eq(CourseLessons::getChapterId, chapter.getId())
                .set(CourseLessons::getIsDeleted, true);
        lessonsMapper.update(null, lessonUpdate);
        chaptersMapper.deleteById(chapter.getId());
    }

    @Transactional
    public List<ChapterDto.Response> reorderChapters(String courseId, List<Long> orderedIds) {
        courseLookupService.getCourseDetail(courseId);
        // 校验：所有 id 必须属于该课程，且数量与现有章节一致
        List<CourseChapters> existing = chaptersMapper.selectList(
                new LambdaQueryWrapper<CourseChapters>().eq(CourseChapters::getCourseId, courseId)
        );
        if (existing.size() != orderedIds.size()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "章节排序数量与现有章节不一致");
        }
        for (Long id : orderedIds) {
            boolean belongs = existing.stream().anyMatch(c -> Objects.equals(c.getId(), id));
            if (!belongs) {
                throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "章节不属于该课程：id=" + id);
            }
        }
        // 两阶段：先把所有 chapter_order 加上一个大偏移避免唯一键冲突，再写最终值
        int offset = existing.size() + 100;
        for (CourseChapters chapter : existing) {
            chaptersMapper.update(null,
                    new LambdaUpdateWrapper<CourseChapters>()
                            .eq(CourseChapters::getId, chapter.getId())
                            .set(CourseChapters::getChapterOrder, chapter.getChapterOrder() + offset));
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            chaptersMapper.update(null,
                    new LambdaUpdateWrapper<CourseChapters>()
                            .eq(CourseChapters::getId, orderedIds.get(i))
                            .set(CourseChapters::getChapterOrder, i + 1));
        }
        return listChapters(courseId);
    }

    // ============ helpers ============

    CourseChapters requireChapter(String courseId, Long chapterId) {
        CourseChapters chapter = chaptersMapper.selectById(chapterId);
        if (chapter == null || !Objects.equals(chapter.getCourseId(), courseId)) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND, "章节不存在或不属于该课程");
        }
        return chapter;
    }

    private int nextChapterOrder(String courseId) {
        Integer max = chaptersMapper.selectList(
                new LambdaQueryWrapper<CourseChapters>()
                        .eq(CourseChapters::getCourseId, courseId)
                        .orderByDesc(CourseChapters::getChapterOrder)
                        .last("LIMIT 1")
        ).stream().findFirst().map(CourseChapters::getChapterOrder).orElse(0);
        return max + 1;
    }

    private void ensureChapterOrderAvailable(String courseId, int order, Long ignoreId) {
        LambdaQueryWrapper<CourseChapters> q = new LambdaQueryWrapper<>();
        q.eq(CourseChapters::getCourseId, courseId).eq(CourseChapters::getChapterOrder, order);
        if (ignoreId != null) {
            q.ne(CourseChapters::getId, ignoreId);
        }
        if (chaptersMapper.selectCount(q) > 0) {
            throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "章节序号已被占用：" + order);
        }
    }

    private List<LessonDto.Response> listLessonsOf(Long chapterId) {
        return lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>()
                        .eq(CourseLessons::getChapterId, chapterId)
                        .orderByAsc(CourseLessons::getLessonOrder)
        ).stream().map(this::toLessonResponse).collect(Collectors.toList());
    }

    private ChapterDto.Response toChapterResponse(CourseChapters c, List<LessonDto.Response> lessons) {
        return ChapterDto.Response.builder()
                .id(c.getId())
                .courseId(c.getCourseId())
                .chapterOrder(c.getChapterOrder())
                .title(c.getTitle())
                .description(c.getDescription())
                .lessons(lessons)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private LessonDto.Response toLessonResponse(CourseLessons l) {
        return LessonDto.Response.builder()
                .id(l.getId())
                .chapterId(l.getChapterId())
                .lessonOrder(l.getLessonOrder())
                .title(l.getTitle())
                .lessonType(l.getLessonType())
                .contentUri(l.getContentUri())
                .durationMinutes(l.getDurationMinutes())
                .isFreePreview(l.getIsFreePreview())
                .materialId(l.getMaterialId())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
```

> 注意：使用 `BusinessException` 与 `ApiErrorCode` 沿用 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/exception/`、`api/error/` 中已有的枚举。如果 `RESOURCE_NOT_FOUND` / `VALIDATION_FAILED` 名称略有不同，按实际枚举名替换。

- [ ] **Step 2: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。如果 `ApiErrorCode` 枚举里没有 `RESOURCE_NOT_FOUND` / `VALIDATION_FAILED`，先 grep 现有错误码，挑最接近的（如 `NOT_FOUND` / `BAD_REQUEST`），保证语义一致。

- [ ] **Step 3: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseChapterService.java
git commit -m "feat(course-lms): 新增 CourseChapterService（章节列表/增/改/删/排序）" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 5：CourseLessonService（课时读写业务）

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLessonService.java`

- [ ] **Step 1: 写 CourseLessonService**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLessonService.java`：

```java
package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.exception.BusinessException;
import org.ysu.ckqaback.api.error.ApiErrorCode;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;

import java.util.List;
import java.util.Objects;

/**
 * 课时增/改/删/排序业务。
 */
@Service
public class CourseLessonService {

    private final CourseChaptersMapper chaptersMapper;
    private final CourseLessonsMapper lessonsMapper;

    public CourseLessonService(CourseChaptersMapper chaptersMapper, CourseLessonsMapper lessonsMapper) {
        this.chaptersMapper = chaptersMapper;
        this.lessonsMapper = lessonsMapper;
    }

    @Transactional
    public LessonDto.Response createLesson(String courseId, Long chapterId, LessonDto.CreateRequest request) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        Integer order = request.getLessonOrder();
        if (order == null) {
            order = nextLessonOrder(chapter.getId());
        } else {
            ensureLessonOrderAvailable(chapter.getId(), order, null);
        }

        CourseLessons entity = new CourseLessons();
        entity.setChapterId(chapter.getId());
        entity.setLessonOrder(order);
        entity.setTitle(request.getTitle().trim());
        entity.setLessonType(request.getLessonType());
        entity.setContentUri(request.getContentUri());
        entity.setDurationMinutes(request.getDurationMinutes());
        entity.setIsFreePreview(Boolean.TRUE.equals(request.getIsFreePreview()));
        entity.setMaterialId(request.getMaterialId());
        lessonsMapper.insert(entity);

        return toResponse(entity);
    }

    @Transactional
    public LessonDto.Response updateLesson(String courseId, Long chapterId, Long lessonId, LessonDto.UpdateRequest request) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        CourseLessons lesson = requireLesson(chapter.getId(), lessonId);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            lesson.setTitle(request.getTitle().trim());
        }
        if (request.getLessonType() != null) {
            lesson.setLessonType(request.getLessonType());
        }
        if (request.getContentUri() != null) {
            lesson.setContentUri(request.getContentUri());
        }
        if (request.getDurationMinutes() != null) {
            lesson.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getIsFreePreview() != null) {
            lesson.setIsFreePreview(request.getIsFreePreview());
        }
        if (request.getMaterialId() != null) {
            lesson.setMaterialId(request.getMaterialId());
        }
        lessonsMapper.updateById(lesson);
        return toResponse(lesson);
    }

    @Transactional
    public void deleteLesson(String courseId, Long chapterId, Long lessonId) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        CourseLessons lesson = requireLesson(chapter.getId(), lessonId);
        lessonsMapper.deleteById(lesson.getId());
    }

    @Transactional
    public List<LessonDto.Response> reorderLessons(String courseId, Long chapterId, List<Long> orderedIds) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        List<CourseLessons> existing = lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>().eq(CourseLessons::getChapterId, chapter.getId())
        );
        if (existing.size() != orderedIds.size()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "课时排序数量与现有课时不一致");
        }
        for (Long id : orderedIds) {
            boolean belongs = existing.stream().anyMatch(l -> Objects.equals(l.getId(), id));
            if (!belongs) {
                throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "课时不属于该章节：id=" + id);
            }
        }
        // 同样的两阶段策略避免唯一键冲突
        int offset = existing.size() + 100;
        for (CourseLessons l : existing) {
            lessonsMapper.update(null,
                    new LambdaUpdateWrapper<CourseLessons>()
                            .eq(CourseLessons::getId, l.getId())
                            .set(CourseLessons::getLessonOrder, l.getLessonOrder() + offset));
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            lessonsMapper.update(null,
                    new LambdaUpdateWrapper<CourseLessons>()
                            .eq(CourseLessons::getId, orderedIds.get(i))
                            .set(CourseLessons::getLessonOrder, i + 1));
        }
        return lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>()
                        .eq(CourseLessons::getChapterId, chapter.getId())
                        .orderByAsc(CourseLessons::getLessonOrder)
        ).stream().map(this::toResponse).toList();
    }

    // ============ helpers ============

    private CourseChapters requireChapter(String courseId, Long chapterId) {
        CourseChapters chapter = chaptersMapper.selectById(chapterId);
        if (chapter == null || !Objects.equals(chapter.getCourseId(), courseId)) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND, "章节不存在或不属于该课程");
        }
        return chapter;
    }

    private CourseLessons requireLesson(Long chapterId, Long lessonId) {
        CourseLessons lesson = lessonsMapper.selectById(lessonId);
        if (lesson == null || !Objects.equals(lesson.getChapterId(), chapterId)) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND, "课时不存在或不属于该章节");
        }
        return lesson;
    }

    private int nextLessonOrder(Long chapterId) {
        Integer max = lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>()
                        .eq(CourseLessons::getChapterId, chapterId)
                        .orderByDesc(CourseLessons::getLessonOrder)
                        .last("LIMIT 1")
        ).stream().findFirst().map(CourseLessons::getLessonOrder).orElse(0);
        return max + 1;
    }

    private void ensureLessonOrderAvailable(Long chapterId, int order, Long ignoreId) {
        LambdaQueryWrapper<CourseLessons> q = new LambdaQueryWrapper<>();
        q.eq(CourseLessons::getChapterId, chapterId).eq(CourseLessons::getLessonOrder, order);
        if (ignoreId != null) {
            q.ne(CourseLessons::getId, ignoreId);
        }
        if (lessonsMapper.selectCount(q) > 0) {
            throw new BusinessException(ApiErrorCode.VALIDATION_FAILED, "课时序号已被占用：" + order);
        }
    }

    private LessonDto.Response toResponse(CourseLessons l) {
        return LessonDto.Response.builder()
                .id(l.getId())
                .chapterId(l.getChapterId())
                .lessonOrder(l.getLessonOrder())
                .title(l.getTitle())
                .lessonType(l.getLessonType())
                .contentUri(l.getContentUri())
                .durationMinutes(l.getDurationMinutes())
                .isFreePreview(l.getIsFreePreview())
                .materialId(l.getMaterialId())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/course/CourseLessonService.java
git commit -m "feat(course-lms): 新增 CourseLessonService（课时增/改/删/排序）" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 6：Controller 路由扩展

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java`

- [ ] **Step 1: 调整 import 块**

在 `CoursesController.java` 顶部 import 中加入（与现有 import 合并去重）：

```java
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ysu.ckqaback.course.CourseChapterService;
import org.ysu.ckqaback.course.CourseLessonService;
import org.ysu.ckqaback.course.dto.ChapterDto;
import org.ysu.ckqaback.course.dto.LessonDto;
```

- [ ] **Step 2: 注入两个新 service**

在 `CoursesController` 类的构造方法（或字段+构造）中加入 `CourseChapterService` 与 `CourseLessonService`，与现有 `courseLookupService` 等保持一致风格。

例如，如果当前是字段注入或 Lombok `@RequiredArgsConstructor`，按现有模式追加：

```java
private final CourseChapterService chapterService;
private final CourseLessonService lessonService;
```

构造方法把两个参数加上即可。

- [ ] **Step 3: 改造 listChapters 方法返回真实数据**

替换 `listChapters` 方法实现：

```java
/**
 * 课程章节列表（学生端 + admin-app 共用）。
 */
@GetMapping("/{courseId}/chapters")
public ApiResponse<CourseChaptersResponse> listChapters(@PathVariable String courseId) {
    var chapters = chapterService.listChapters(courseId);
    return ApiResponseUtils.success(CourseChaptersResponse.ready(chapters));
}
```

- [ ] **Step 4: 在 `CoursesController` 中追加章节管理路由**

在文件末尾闭合大括号前追加以下方法块：

```java
// ============ 章节管理（admin-app）============

@PostMapping("/{courseId}/chapters")
public ApiResponse<ChapterDto.Response> createChapter(
        @PathVariable String courseId,
        @Valid @RequestBody ChapterDto.CreateRequest request
) {
    return ApiResponseUtils.success(chapterService.createChapter(courseId, request));
}

@PutMapping("/{courseId}/chapters/{chapterId}")
public ApiResponse<ChapterDto.Response> updateChapter(
        @PathVariable String courseId,
        @PathVariable Long chapterId,
        @Valid @RequestBody ChapterDto.UpdateRequest request
) {
    return ApiResponseUtils.success(chapterService.updateChapter(courseId, chapterId, request));
}

@DeleteMapping("/{courseId}/chapters/{chapterId}")
public ApiResponse<Void> deleteChapter(
        @PathVariable String courseId,
        @PathVariable Long chapterId
) {
    chapterService.deleteChapter(courseId, chapterId);
    return ApiResponseUtils.success(null);
}

@PostMapping("/{courseId}/chapters/reorder")
public ApiResponse<List<ChapterDto.Response>> reorderChapters(
        @PathVariable String courseId,
        @Valid @RequestBody ChapterDto.ReorderRequest request
) {
    return ApiResponseUtils.success(chapterService.reorderChapters(courseId, request.getChapterIds()));
}

// ============ 课时管理 ============

@PostMapping("/{courseId}/chapters/{chapterId}/lessons")
public ApiResponse<LessonDto.Response> createLesson(
        @PathVariable String courseId,
        @PathVariable Long chapterId,
        @Valid @RequestBody LessonDto.CreateRequest request
) {
    return ApiResponseUtils.success(lessonService.createLesson(courseId, chapterId, request));
}

@PutMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
public ApiResponse<LessonDto.Response> updateLesson(
        @PathVariable String courseId,
        @PathVariable Long chapterId,
        @PathVariable Long lessonId,
        @Valid @RequestBody LessonDto.UpdateRequest request
) {
    return ApiResponseUtils.success(lessonService.updateLesson(courseId, chapterId, lessonId, request));
}

@DeleteMapping("/{courseId}/chapters/{chapterId}/lessons/{lessonId}")
public ApiResponse<Void> deleteLesson(
        @PathVariable String courseId,
        @PathVariable Long chapterId,
        @PathVariable Long lessonId
) {
    lessonService.deleteLesson(courseId, chapterId, lessonId);
    return ApiResponseUtils.success(null);
}

@PostMapping("/{courseId}/chapters/{chapterId}/lessons/reorder")
public ApiResponse<List<LessonDto.Response>> reorderLessons(
        @PathVariable String courseId,
        @PathVariable Long chapterId,
        @Valid @RequestBody LessonDto.ReorderRequest request
) {
    return ApiResponseUtils.success(lessonService.reorderLessons(courseId, chapterId, request.getLessonIds()));
}
```

`import java.util.List;` 已经存在则不重复加。

- [ ] **Step 5: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 6: 启动后端 + curl 抽烟**

```bash
# 假设后端已经在 8080 端口运行
COURSE=crs-20260506-r4slkr

# 1) 列章节（应返回 Step 5 demo 数据中插入的两个章节）
curl -s "http://localhost:8080/api/v1/courses/${COURSE}/chapters" | jq .

# 2) 新建一章
curl -s -X POST "http://localhost:8080/api/v1/courses/${COURSE}/chapters" \
  -H 'Content-Type: application/json' \
  -d '{"title":"第三章 内存管理","description":"虚拟内存与分页"}' | jq .

# 3) 用上一步返回的 id 新建一课时
CH_ID=<刚返回的章节 id>
curl -s -X POST "http://localhost:8080/api/v1/courses/${COURSE}/chapters/${CH_ID}/lessons" \
  -H 'Content-Type: application/json' \
  -d '{"title":"分页机制","lessonType":"video","durationMinutes":20}' | jq .
```

每次都应返回 `code: 200` 与对应业务对象。

- [ ] **Step 7: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/CoursesController.java
git commit -m "feat(course-lms): CoursesController 新增章节/课时 CRUD + reorder 路由" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 7：后端 Service 单元测试

**Files:**
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseChapterServiceTest.java`
- Create: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseLessonServiceTest.java`

> 单测使用 Mockito 直接 mock mapper / lookupService，无需真实数据库。仅验证关键不变量：序号自增、所属校验、reorder 两阶段更新。

- [ ] **Step 1: 写 CourseChapterServiceTest**

`backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseChapterServiceTest.java`：

```java
package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ysu.ckqaback.api.exception.BusinessException;
import org.ysu.ckqaback.course.dto.ChapterDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseChapterServiceTest {

    @Mock CourseChaptersMapper chaptersMapper;
    @Mock CourseLessonsMapper lessonsMapper;
    @Mock CourseLookupService courseLookupService;

    @InjectMocks
    CourseChapterService chapterService;

    @BeforeEach
    void setupCourseExists() {
        // courseLookupService.getCourseDetail 直接返回任意非 null 即可，测试不关心其内容
        lenient().when(courseLookupService.getCourseDetail(any())).thenReturn(null);
    }

    @Test
    void createChapter_appendsAtEnd_whenOrderNull() {
        when(chaptersMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildChapter(1L, "c1", 1), buildChapter(2L, "c1", 2)));
        when(chaptersMapper.insert(any(CourseChapters.class))).thenReturn(1);

        ChapterDto.CreateRequest req = new ChapterDto.CreateRequest();
        req.setTitle("第三章");

        ChapterDto.Response resp = chapterService.createChapter("c1", req);
        assertEquals(3, resp.getChapterOrder());
    }

    @Test
    void deleteChapter_failsWhenNotBelongCourse() {
        when(chaptersMapper.selectById(99L)).thenReturn(buildChapter(99L, "other", 1));
        assertThrows(BusinessException.class,
                () -> chapterService.deleteChapter("c1", 99L));
    }

    private CourseChapters buildChapter(Long id, String courseId, int order) {
        CourseChapters c = new CourseChapters();
        c.setId(id);
        c.setCourseId(courseId);
        c.setChapterOrder(order);
        c.setTitle("章节" + order);
        return c;
    }
}
```

- [ ] **Step 2: 写 CourseLessonServiceTest（最小集合）**

`backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/CourseLessonServiceTest.java`：

```java
package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ysu.ckqaback.api.exception.BusinessException;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLessonServiceTest {

    @Mock CourseChaptersMapper chaptersMapper;
    @Mock CourseLessonsMapper lessonsMapper;

    @InjectMocks
    CourseLessonService lessonService;

    @Test
    void createLesson_failsWhenChapterNotBelongCourse() {
        CourseChapters chapter = new CourseChapters();
        chapter.setId(7L);
        chapter.setCourseId("other-course");
        when(chaptersMapper.selectById(7L)).thenReturn(chapter);

        LessonDto.CreateRequest req = new LessonDto.CreateRequest();
        req.setTitle("test");
        req.setLessonType("video");

        assertThrows(BusinessException.class,
                () -> lessonService.createLesson("c1", 7L, req));
    }

    @Test
    void createLesson_appendsAtEnd_whenOrderNull() {
        CourseChapters chapter = new CourseChapters();
        chapter.setId(7L);
        chapter.setCourseId("c1");
        when(chaptersMapper.selectById(7L)).thenReturn(chapter);

        when(lessonsMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildLesson(1L, 7L, 1), buildLesson(2L, 7L, 2), buildLesson(3L, 7L, 3)));
        when(lessonsMapper.insert(any(CourseLessons.class))).thenReturn(1);

        LessonDto.CreateRequest req = new LessonDto.CreateRequest();
        req.setTitle("第四节");
        req.setLessonType("video");

        LessonDto.Response resp = lessonService.createLesson("c1", 7L, req);
        assertEquals(4, resp.getLessonOrder());
    }

    private CourseLessons buildLesson(Long id, Long chapterId, int order) {
        CourseLessons l = new CourseLessons();
        l.setId(id);
        l.setChapterId(chapterId);
        l.setLessonOrder(order);
        l.setTitle("课时" + order);
        l.setLessonType("video");
        return l;
    }
}
```

- [ ] **Step 3: 跑测试**

```bash
./mvnw '-Dtest=CourseChapterServiceTest,CourseLessonServiceTest' test
```

预期：所有测试 PASS。

- [ ] **Step 4: 跑全量后端测试**

```bash
./mvnw '-Dtest=!IndexProgressParserTest' test
```

预期：BUILD SUCCESS，无新失败。

- [ ] **Step 5: Commit**

```bash
git add backend/ckqa-back/src/test/java/org/ysu/ckqaback/course/
git commit -m "test(course-lms): 新增章节/课时 service 单元测试" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 8：admin-app API 封装

**Files:**
- Create: `frontend/apps/admin-app/src/api/chapters.js`

- [ ] **Step 1: 写 API 模块**

`frontend/apps/admin-app/src/api/chapters.js`：

```js
// 课程章节/课时 API（PR 3）
// 所有接口都走 Java /api/v1，返回业务码 200 即成功
import { apiClient } from './client.js'

const base = (courseId) => `/courses/${encodeURIComponent(courseId)}/chapters`

export async function listChapters(courseId) {
  const { data } = await apiClient.get(base(courseId))
  return data?.data ?? { chapters: [], featureStatus: 'coming-soon' }
}

export async function createChapter(courseId, payload) {
  const { data } = await apiClient.post(base(courseId), payload)
  return data?.data
}

export async function updateChapter(courseId, chapterId, payload) {
  const { data } = await apiClient.put(`${base(courseId)}/${chapterId}`, payload)
  return data?.data
}

export async function deleteChapter(courseId, chapterId) {
  await apiClient.delete(`${base(courseId)}/${chapterId}`)
}

export async function reorderChapters(courseId, chapterIds) {
  const { data } = await apiClient.post(`${base(courseId)}/reorder`, { chapterIds })
  return data?.data ?? []
}

export async function createLesson(courseId, chapterId, payload) {
  const { data } = await apiClient.post(`${base(courseId)}/${chapterId}/lessons`, payload)
  return data?.data
}

export async function updateLesson(courseId, chapterId, lessonId, payload) {
  const { data } = await apiClient.put(`${base(courseId)}/${chapterId}/lessons/${lessonId}`, payload)
  return data?.data
}

export async function deleteLesson(courseId, chapterId, lessonId) {
  await apiClient.delete(`${base(courseId)}/${chapterId}/lessons/${lessonId}`)
}

export async function reorderLessons(courseId, chapterId, lessonIds) {
  const { data } = await apiClient.post(`${base(courseId)}/${chapterId}/lessons/reorder`, { lessonIds })
  return data?.data ?? []
}
```

> 如果当前 admin-app 没有 `client.js` 而是 `axios.js` 或别的文件，开发执行时先 `grep -l "apiClient\|axios" frontend/apps/admin-app/src/api/`，把 import 路径替换为现有的客户端入口。

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/api/chapters.js
git commit -m "feat(admin-chapters): 新增章节/课时 API 封装" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 9：admin-app 章节管理主面板

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/courses/ChapterManagementPanel.vue`

- [ ] **Step 1: 写组件主体**

`frontend/apps/admin-app/src/views/pages/courses/ChapterManagementPanel.vue`：

```vue
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listChapters as apiListChapters,
  createChapter,
  updateChapter,
  deleteChapter,
  createLesson,
  updateLesson,
  deleteLesson,
} from '../../../api/chapters.js'
import ChapterEditDialog from './ChapterEditDialog.vue'
import LessonEditDrawer from './LessonEditDrawer.vue'

const route = useRoute()
const courseId = computed(() => String(route.params.courseId || ''))

const chapters = ref([])
const loading = ref(false)
const editingChapter = ref(null) // { id?, title, description }
const chapterDialogVisible = ref(false)
const editingLesson = ref(null) // { chapterId, lesson? }
const lessonDrawerVisible = ref(false)

async function refresh() {
  if (!courseId.value) return
  loading.value = true
  try {
    const resp = await apiListChapters(courseId.value)
    chapters.value = resp.chapters || []
  } catch (err) {
    ElMessage.error('加载章节失败：' + (err?.response?.data?.message || err.message))
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

function handleAddChapter() {
  editingChapter.value = { title: '', description: '' }
  chapterDialogVisible.value = true
}

function handleEditChapter(chapter) {
  editingChapter.value = { id: chapter.id, title: chapter.title, description: chapter.description }
  chapterDialogVisible.value = true
}

async function handleChapterSubmit(payload) {
  try {
    if (payload.id) {
      await updateChapter(courseId.value, payload.id, { title: payload.title, description: payload.description })
      ElMessage.success('章节更新成功')
    } else {
      await createChapter(courseId.value, { title: payload.title, description: payload.description })
      ElMessage.success('章节创建成功')
    }
    chapterDialogVisible.value = false
    await refresh()
  } catch (err) {
    ElMessage.error('保存失败：' + (err?.response?.data?.message || err.message))
  }
}

async function handleDeleteChapter(chapter) {
  try {
    await ElMessageBox.confirm(`确定删除章节 "${chapter.title}" 及其所有课时？此操作不可撤销。`, '删除章节', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteChapter(courseId.value, chapter.id)
    ElMessage.success('章节已删除')
    await refresh()
  } catch (err) {
    if (err === 'cancel') return
    ElMessage.error('删除失败：' + (err?.response?.data?.message || err.message))
  }
}

function handleAddLesson(chapter) {
  editingLesson.value = { chapterId: chapter.id, lesson: null }
  lessonDrawerVisible.value = true
}

function handleEditLesson(chapter, lesson) {
  editingLesson.value = { chapterId: chapter.id, lesson }
  lessonDrawerVisible.value = true
}

async function handleLessonSubmit(payload) {
  const { chapterId, id, ...rest } = payload
  try {
    if (id) {
      await updateLesson(courseId.value, chapterId, id, rest)
      ElMessage.success('课时更新成功')
    } else {
      await createLesson(courseId.value, chapterId, rest)
      ElMessage.success('课时创建成功')
    }
    lessonDrawerVisible.value = false
    await refresh()
  } catch (err) {
    ElMessage.error('保存失败：' + (err?.response?.data?.message || err.message))
  }
}

async function handleDeleteLesson(chapter, lesson) {
  try {
    await ElMessageBox.confirm(`确定删除课时 "${lesson.title}"？`, '删除课时', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteLesson(courseId.value, chapter.id, lesson.id)
    ElMessage.success('课时已删除')
    await refresh()
  } catch (err) {
    if (err === 'cancel') return
    ElMessage.error('删除失败：' + (err?.response?.data?.message || err.message))
  }
}

const lessonTypeLabel = (type) => ({
  video: '视频',
  document: '文档',
  quiz: '测验',
  live: '直播',
  external: '外链',
}[type] || type)
</script>

<template>
  <section class="chapter-management" v-loading="loading">
    <header class="chapter-management__header">
      <div>
        <h2>课程章节管理</h2>
        <p class="hint">维护当前课程的章节与课时结构。课时支持视频、文档、测验、直播、外链五种类型。</p>
      </div>
      <el-button type="primary" @click="handleAddChapter">新增章节</el-button>
    </header>

    <el-empty v-if="!loading && chapters.length === 0" description="暂无章节，点击右上角新增" />

    <article v-for="chapter in chapters" :key="chapter.id" class="chapter-card">
      <div class="chapter-card__head">
        <div>
          <span class="chapter-card__order">第 {{ chapter.chapterOrder }} 章</span>
          <strong class="chapter-card__title">{{ chapter.title }}</strong>
        </div>
        <div class="chapter-card__actions">
          <el-button size="small" @click="handleAddLesson(chapter)">新增课时</el-button>
          <el-button size="small" @click="handleEditChapter(chapter)">编辑章节</el-button>
          <el-button size="small" type="danger" plain @click="handleDeleteChapter(chapter)">删除</el-button>
        </div>
      </div>
      <p v-if="chapter.description" class="chapter-card__desc">{{ chapter.description }}</p>

      <ul class="lesson-list">
        <li v-for="lesson in chapter.lessons" :key="lesson.id" class="lesson-row">
          <span class="lesson-row__order">{{ lesson.lessonOrder }}.</span>
          <span class="lesson-row__title">{{ lesson.title }}</span>
          <el-tag size="small" effect="plain">{{ lessonTypeLabel(lesson.lessonType) }}</el-tag>
          <span v-if="lesson.durationMinutes" class="lesson-row__meta">{{ lesson.durationMinutes }} 分钟</span>
          <el-tag v-if="lesson.isFreePreview" size="small" type="success">免费试看</el-tag>
          <span class="lesson-row__spacer" />
          <el-button size="small" link @click="handleEditLesson(chapter, lesson)">编辑</el-button>
          <el-button size="small" link type="danger" @click="handleDeleteLesson(chapter, lesson)">删除</el-button>
        </li>
        <li v-if="!chapter.lessons || chapter.lessons.length === 0" class="lesson-row lesson-row--empty">
          暂无课时
        </li>
      </ul>
    </article>

    <ChapterEditDialog
      v-model:visible="chapterDialogVisible"
      :data="editingChapter"
      @submit="handleChapterSubmit"
    />
    <LessonEditDrawer
      v-model:visible="lessonDrawerVisible"
      :data="editingLesson"
      @submit="handleLessonSubmit"
    />
  </section>
</template>

<style scoped>
.chapter-management { display: grid; gap: 16px; }
.chapter-management__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.chapter-management__header h2 { margin: 0 0 4px; font-size: 18px; }
.chapter-management__header .hint { margin: 0; color: var(--ckqa-text-muted, #64748b); font-size: 13px; }

.chapter-card {
  border: 1px solid var(--ckqa-border, #e2e8f0);
  border-radius: 10px;
  padding: 16px 20px;
  background: var(--ckqa-surface, #fff);
}
.chapter-card__head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.chapter-card__order { color: var(--ckqa-text-muted, #64748b); margin-right: 8px; }
.chapter-card__title { font-size: 16px; }
.chapter-card__desc { margin: 8px 0 12px; color: var(--ckqa-text-muted, #64748b); font-size: 13px; }
.chapter-card__actions { display: flex; gap: 8px; }

.lesson-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.lesson-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px;
  background: var(--ckqa-surface-muted, #f8fafc);
  border-radius: 6px;
  font-size: 13px;
}
.lesson-row__order { color: var(--ckqa-text-muted, #64748b); width: 24px; }
.lesson-row__title { font-weight: 500; }
.lesson-row__meta { color: var(--ckqa-text-muted, #64748b); font-size: 12px; }
.lesson-row__spacer { flex: 1; }
.lesson-row--empty { justify-content: center; color: var(--ckqa-text-muted, #94a3b8); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/courses/ChapterManagementPanel.vue
git commit -m "feat(admin-chapters): 新增章节管理主面板" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 10：admin-app 章节编辑对话框 + 课时编辑抽屉

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/courses/ChapterEditDialog.vue`
- Create: `frontend/apps/admin-app/src/views/pages/courses/LessonEditDrawer.vue`

- [ ] **Step 1: 写章节编辑对话框**

`frontend/apps/admin-app/src/views/pages/courses/ChapterEditDialog.vue`：

```vue
<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  data: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:visible', 'submit'])

const form = ref({ id: null, title: '', description: '' })

watch(() => props.data, (val) => {
  form.value = {
    id: val?.id ?? null,
    title: val?.title ?? '',
    description: val?.description ?? '',
  }
}, { immediate: true })

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isEdit = computed(() => !!form.value.id)

function submit() {
  if (!form.value.title.trim()) return
  emit('submit', { ...form.value })
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑章节' : '新增章节'" width="520px" :close-on-click-modal="false">
    <el-form label-width="80px" label-position="left">
      <el-form-item label="章节标题" required>
        <el-input v-model="form.title" maxlength="255" show-word-limit placeholder="例如：第三章 内存管理" />
      </el-form-item>
      <el-form-item label="章节描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="可选，简述本章重点" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!form.title.trim()" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
```

- [ ] **Step 2: 写课时编辑抽屉**

`frontend/apps/admin-app/src/views/pages/courses/LessonEditDrawer.vue`：

```vue
<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** { chapterId: number, lesson: object | null } */
  data: { type: Object, default: () => ({ chapterId: null, lesson: null }) },
})
const emit = defineEmits(['update:visible', 'submit'])

const lessonTypes = [
  { value: 'video', label: '视频' },
  { value: 'document', label: '文档' },
  { value: 'quiz', label: '测验' },
  { value: 'live', label: '直播' },
  { value: 'external', label: '外链' },
]

const form = ref(makeEmpty(null))

function makeEmpty(chapterId) {
  return {
    id: null,
    chapterId,
    title: '',
    lessonType: 'video',
    contentUri: '',
    durationMinutes: null,
    isFreePreview: false,
    materialId: null,
  }
}

watch(() => props.data, (val) => {
  if (val?.lesson) {
    form.value = {
      id: val.lesson.id,
      chapterId: val.chapterId,
      title: val.lesson.title || '',
      lessonType: val.lesson.lessonType || 'video',
      contentUri: val.lesson.contentUri || '',
      durationMinutes: val.lesson.durationMinutes ?? null,
      isFreePreview: !!val.lesson.isFreePreview,
      materialId: val.lesson.materialId ?? null,
    }
  } else {
    form.value = makeEmpty(val?.chapterId ?? null)
  }
}, { immediate: true })

const drawerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isEdit = computed(() => !!form.value.id)

function submit() {
  if (!form.value.title.trim()) return
  emit('submit', { ...form.value })
}
</script>

<template>
  <el-drawer v-model="drawerVisible" :title="isEdit ? '编辑课时' : '新增课时'" size="480px">
    <el-form label-width="90px" label-position="left">
      <el-form-item label="课时标题" required>
        <el-input v-model="form.title" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="课时类型" required>
        <el-select v-model="form.lessonType" style="width: 100%">
          <el-option v-for="opt in lessonTypes" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="内容地址">
        <el-input v-model="form.contentUri" maxlength="1024" placeholder="视频/文档外链或 MinIO 引用" />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number v-model="form.durationMinutes" :min="0" :max="600" controls-position="right" style="width: 100%" />
      </el-form-item>
      <el-form-item label="免费试看">
        <el-switch v-model="form.isFreePreview" />
      </el-form-item>
      <el-form-item label="关联资料 ID">
        <el-input-number v-model="form.materialId" :min="1" controls-position="right" style="width: 100%" placeholder="可选，关联 course_materials.id" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="drawerVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!form.title.trim()" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/courses/ChapterEditDialog.vue \
        frontend/apps/admin-app/src/views/pages/courses/LessonEditDrawer.vue
git commit -m "feat(admin-chapters): 新增章节对话框与课时抽屉" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 11：admin-app 路由接入章节面板

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`（course-chapters 配置）
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`（在 course-chapters 路由下渲染 ChapterManagementPanel）
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`（课程详情页 → 加"管理章节"快捷入口）

- [ ] **Step 1: 把 course-chapters 改为 live 数据源**

修改 `module-content.js` 中的 `'course-chapters'` 配置块，把 `dataSource: 'mock'` 改为 `'live'`，summary 改为：

```js
'course-chapters': {
  variant: 'overview',
  dataSource: 'live',
  eyebrow: 'Course Chapters',
  summary: '维护当前课程的章节与课时结构。',
  primaryAction: null,
  secondaryAction: null,
  facts: [],
  timeline: [],
},
```

- [ ] **Step 2: 在 ModulePage.vue 顶部 import 处加上 ChapterManagementPanel**

在 ModulePage.vue 的 `<script setup>` 中追加：

```js
import ChapterManagementPanel from './courses/ChapterManagementPanel.vue'
```

- [ ] **Step 3: 在 ModulePage.vue 模板中插入 course-chapters 渲染分支**

在模板里 `<template v-else-if="config.variant === 'overview' && courseBlock">` 之前（根据现有 v-else-if 顺序），加：

```vue
<template v-else-if="route.name === 'course-chapters'">
  <ChapterManagementPanel />
</template>
```

> 这个分支必须放在更通用的 `variant === 'overview'` 分支之前，否则会被吞掉。

- [ ] **Step 4: 在课程详情页加"管理章节"快捷入口**

定位 `ModulePage.vue` 课程详情区域（约 4280 行附近 `courseBlock?.item?.courseName` 渲染处），在 `course-detail-actions` 区域追加一个 router-link 按钮：

```vue
<el-button
  class="ckqa-el-button"
  type="primary"
  plain
  :icon="BookOpen"
  @click="$router.push(`/app/courses/${encodeURIComponent(courseBlock.item.courseId)}/chapters`)"
>
  管理章节
</el-button>
```

如果当前已经导入过 BookOpen 图标，复用即可；否则在 import 块加 `import { BookOpen } from 'lucide-vue-next'`。

- [ ] **Step 5: 构建验证**

```bash
pnpm build
```

预期：构建成功。

- [ ] **Step 6: 浏览器抽烟**

```
打开 admin-app dev：http://127.0.0.1:5174
登录 → 进入 /app/courses → 点击 kb5 课程 → 看到"管理章节"按钮
点击 → 跳到 /app/courses/crs-20260506-r4slkr/chapters
应能：
- 看到 Step 1.5 注入的 demo 章节
- 点击"新增章节"→ 弹窗 → 提交后列表刷新
- 在某章节点击"新增课时"→ 抽屉 → 提交后列表刷新
- 点击编辑/删除均能生效
```

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js \
        frontend/apps/admin-app/src/views/pages/ModulePage.vue
git commit -m "feat(admin-chapters): course-chapters 路由接入真实管理面板" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 12：student-app store 与 API 接入真实章节数据

**Files:**
- Modify: `frontend/apps/student-app/src/api/courses.js`
- Modify: `frontend/apps/student-app/src/stores/course.js`

- [ ] **Step 1: 检查 student-app/src/api/courses.js 中的 listChapters**

```bash
grep -n "listChapters\|/chapters" frontend/apps/student-app/src/api/courses.js
```

确保 `listChapters(courseId)` 已经存在并返回 `client.get(...)`，且把 `featureStatus`、`chapters` 都向上透传。

如果当前实现是：

```js
return client.get(`/courses/${encodeURIComponent(courseId)}/chapters`)
```

不需要改。如果实现已经把 `featureStatus === 'coming-soon'` 当作错误丢弃，调整为：

```js
async listChapters(courseId) {
  const resp = await client.get(`/courses/${encodeURIComponent(courseId)}/chapters`)
  return resp?.data ?? { chapters: [], featureStatus: 'coming-soon' }
}
```

- [ ] **Step 2: 在 stores/course.js 中加 chapters 状态**

修改 `frontend/apps/student-app/src/stores/course.js`，在 store 内追加：

```js
const chapters = ref([])
const chaptersFeatureStatus = ref('coming-soon')
const chaptersLoading = ref(false)
const chaptersError = ref(null)

async function loadChapters(courseId) {
  chaptersLoading.value = true
  chaptersError.value = null
  try {
    const resp = await coursesApi.listChapters(courseId)
    chapters.value = resp?.chapters || []
    chaptersFeatureStatus.value = resp?.featureStatus || 'ready'
  } catch (err) {
    chaptersError.value = err
    chapters.value = []
    chaptersFeatureStatus.value = 'coming-soon'
  } finally {
    chaptersLoading.value = false
  }
}
```

并在 `return` 里暴露这四个 state 和 `loadChapters`。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/api/courses.js frontend/apps/student-app/src/stores/course.js
git commit -m "feat(student-chapters): course store 暴露 chapters 状态与 loadChapters" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 13：student-app CourseDetail 章节 tab 接入真实数据

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/CourseDetail.vue`

- [ ] **Step 1: 在 `<script setup>` 中调用 loadChapters**

定位 `CourseDetail.vue` 的 `<script setup>`，在已有的 `onMounted` 或 `watch(route.params.id, ...)` 旁加：

```js
import { storeToRefs } from 'pinia'
import { useCourseStore } from '@/stores/course.js'

const courseStore = useCourseStore()
const { chapters, chaptersFeatureStatus, chaptersLoading } = storeToRefs(courseStore)

onMounted(() => {
  if (route.params.id) {
    courseStore.loadChapters(route.params.id)
  }
})

watch(() => route.params.id, (newId) => {
  if (newId) courseStore.loadChapters(newId)
})
```

如果 `useCourseStore` 已被 import 复用，跳过 import 行。

- [ ] **Step 2: 替换章节 tab 模板**

把现有的章节占位块（约 162-176 行）：

```vue
<el-tab-pane label="课程章节" name="chapters">
  <el-alert type="info" ... />
  <div class="chapter-placeholder">
    <el-empty description="本期暂未开放章节内容" :image-size="120" />
  </div>
</el-tab-pane>
```

替换为：

```vue
<el-tab-pane label="课程章节" name="chapters">
  <div v-loading="chaptersLoading" class="chapters-tab">
    <el-alert
      v-if="chaptersFeatureStatus !== 'ready'"
      type="info"
      :closable="false"
      show-icon
      title="功能预览"
      description="课程章节功能尚未开放，请稍后再试。"
    />
    <el-empty
      v-else-if="!chapters.length"
      description="该课程暂未配置章节内容"
      :image-size="120"
    />
    <el-collapse v-else accordion>
      <el-collapse-item v-for="ch in chapters" :key="ch.id" :name="ch.id">
        <template #title>
          <span class="chapter-row">
            <strong class="chapter-row__order">第 {{ ch.chapterOrder }} 章</strong>
            <span class="chapter-row__title">{{ ch.title }}</span>
            <span class="chapter-row__meta">{{ (ch.lessons || []).length }} 课时</span>
          </span>
        </template>
        <p v-if="ch.description" class="chapter-row__desc">{{ ch.description }}</p>
        <ul class="lesson-mini-list">
          <li v-for="lesson in ch.lessons" :key="lesson.id" class="lesson-mini">
            <span class="lesson-mini__order">{{ lesson.lessonOrder }}.</span>
            <span class="lesson-mini__title">{{ lesson.title }}</span>
            <el-tag size="small" effect="plain">{{ lessonTypeLabel(lesson.lessonType) }}</el-tag>
            <span v-if="lesson.durationMinutes" class="lesson-mini__meta">{{ lesson.durationMinutes }} 分钟</span>
            <el-tag v-if="lesson.isFreePreview" size="small" type="success">免费试看</el-tag>
          </li>
        </ul>
      </el-collapse-item>
    </el-collapse>
  </div>
</el-tab-pane>
```

并在 `<script setup>` 中加：

```js
function lessonTypeLabel(t) {
  return ({ video: '视频', document: '文档', quiz: '测验', live: '直播', external: '外链' })[t] || t
}
```

- [ ] **Step 3: 加上必要样式**

在 `<style scoped>` 末尾追加：

```css
.chapters-tab { display: grid; gap: 12px; }
.chapter-row { display: flex; align-items: center; gap: 12px; flex: 1; }
.chapter-row__order { color: var(--el-color-primary); }
.chapter-row__title { flex: 1; font-weight: 500; }
.chapter-row__meta { color: #94a3b8; font-size: 13px; }
.chapter-row__desc { margin: 0 0 8px; color: #64748b; font-size: 13px; }
.lesson-mini-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 4px; }
.lesson-mini { display: flex; align-items: center; gap: 8px; padding: 6px 12px; background: #f8fafc; border-radius: 6px; font-size: 13px; }
.lesson-mini__order { color: #94a3b8; min-width: 24px; }
.lesson-mini__title { flex: 1; }
.lesson-mini__meta { color: #64748b; font-size: 12px; }
```

- [ ] **Step 4: pnpm build 验证**

```bash
pnpm --filter student-app build
```

或者直接进入目录：

```bash
pnpm build  # cwd: frontend/apps/student-app
```

预期：构建成功。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/student-app/src/views/course/CourseDetail.vue
git commit -m "feat(student-chapters): CourseDetail 章节 tab 接入真实数据" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 14：student-app CourseLearn 侧边栏接入真实章节

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/CourseLearn.vue`

- [ ] **Step 1: 移除 chapters mock 数组**

在 `CourseLearn.vue` 中找到 `const chapters = ref([ { id: 1, ... mock 数据 } ])`（约 304 行起）整段删除。

- [ ] **Step 2: 注入 store 并加载章节**

在 `<script setup>` 顶部追加：

```js
import { storeToRefs } from 'pinia'
import { useCourseStore } from '@/stores/course.js'
import { onMounted, watch } from 'vue'

const courseStore = useCourseStore()
const { chapters, chaptersLoading } = storeToRefs(courseStore)

onMounted(() => {
  if (route.params.id) courseStore.loadChapters(route.params.id)
})
watch(() => route.params.id, (id) => {
  if (id) courseStore.loadChapters(id)
})
```

`route` 已在原有代码中通过 `useRoute()` 获取，复用即可。

- [ ] **Step 3: 把 currentLesson 初始化与 totalLessons 计算切换为 chapters store 数据**

把：

```js
const currentLesson = ref(chapters.value[0].lessons[0])
```

替换为：

```js
const currentLesson = ref(null)

watch(chapters, (list) => {
  if (list?.length && !currentLesson.value) {
    currentLesson.value = list[0]?.lessons?.[0] || null
  }
}, { immediate: true })
```

`totalLessons` / `completedCount` 等 reduce 表达式保持原样即可，因为它们读 `chapters.value`。

- [ ] **Step 4: 兼容字段差异**

mock 数据里 `chapter.order` 在 store 数据是 `chapter.chapterOrder`；mock 里 `lesson.duration` 在新数据是 `lesson.durationMinutes`。

把模板中：

```vue
<span class="chapter-order">第{{ chapter.order }}章</span>
```

改为：

```vue
<span class="chapter-order">第{{ chapter.chapterOrder }}章</span>
```

把：

```vue
<span class="lesson-duration">{{ lesson.duration }}分钟</span>
```

改为：

```vue
<span class="lesson-duration">{{ lesson.durationMinutes ?? '-' }}分钟</span>
```

如果 mock 用的是 `lesson.order`，把它改成 `lesson.lessonOrder`。

- [ ] **Step 5: 加载态与空态**

在 `chapters-wrapper` 外层包一个简单 v-loading：

```vue
<div class="chapters-wrapper" v-loading="chaptersLoading">
  <el-empty v-if="!chaptersLoading && chapters.length === 0" description="暂无章节内容" />
  <el-collapse v-else v-model="expandedChapters">
    ...
  </el-collapse>
</div>
```

- [ ] **Step 6: pnpm build**

```bash
pnpm build  # cwd: frontend/apps/student-app
```

预期：构建成功。

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/student-app/src/views/course/CourseLearn.vue
git commit -m "feat(student-chapters): CourseLearn 侧边栏切换为真实章节数据" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 15：联调验收 + 推送 + PR

- [ ] **Step 1: 完整后端测试**

```bash
./mvnw '-Dtest=!IndexProgressParserTest' test
```

预期：BUILD SUCCESS，无新失败。

- [ ] **Step 2: admin-app + student-app 测试 + 构建**

```bash
# admin-app
pnpm test    # cwd: frontend/apps/admin-app
pnpm build   # cwd: frontend/apps/admin-app

# student-app
pnpm build   # cwd: frontend/apps/student-app
```

预期：测试 0 失败，构建成功。

- [ ] **Step 3: 端到端走查（人工）**

```
1. admin-app
   - /app/courses → kb5 课程详情 → "管理章节"按钮跳到 /app/courses/.../chapters
   - 新增 / 编辑 / 删除章节均能生效
   - 新增 / 编辑 / 删除课时均能生效
2. student-app
   - /course/detail/<kb5 courseId> → 章节 tab 显示真实章节，无"功能预览"提示
   - 进入 /course/learn/... → 侧边栏显示真实章节，能切换课时
3. DevTools Network
   - 所有相关请求前缀 /api/v1/courses/.../chapters[/.../lessons[...]]
   - 无残留 mock 数据
```

- [ ] **Step 4: 推送分支**

```bash
git push
```

- [ ] **Step 5: PR 描述模板**

PR 标题：

```
feat(course-lms): 课程章节/课时管理与学生端联动（PR 3）
```

PR 描述应包含：

- 背景：链接 `docs/2026-05-19-course-info-architecture-plan.md` §6.1
- 改动概述：
  - DB：新增 course_chapters / course_lessons 两张表
  - 后端：新增 service + DTO + 路由（章节/课时 CRUD + reorder）
  - admin-app：course-chapters 路由接入真实管理面板
  - student-app：CourseDetail 章节 tab 与 CourseLearn 侧边栏切换为真实数据
- 不在范围：学习进度持久化、视频播放器集成、章节拖拽排序 UI（仅有后端 reorder 接口，前端按钮留待 PR 4）
- 测试：贴 `./mvnw test` / `pnpm test` 输出末段
- 截图：admin 章节管理面板 / 学生端章节 tab / 学生端学习页侧栏

---

## 16. 完工定义（DoD）

- [ ] DB 迁移 `20260520_course_chapters_lessons.sql` 应用成功，主 `sql/ocqa.sql` 同步
- [ ] `GET /api/v1/courses/{id}/chapters` 返回真实章节（kb5 至少 2 章）
- [ ] `POST/PUT/DELETE` 章节与课时接口均可用
- [ ] admin-app `/app/courses/:courseId/chapters` 渲染真实管理面板
- [ ] 课程详情页"管理章节"快捷入口可见
- [ ] student-app 课程详情页章节 tab 渲染真实章节，无"功能预览"alert
- [ ] student-app 学习页侧边栏目录使用真实章节
- [ ] `./mvnw '-Dtest=!IndexProgressParserTest' test` 全部通过
- [ ] `pnpm test`（admin-app）全部通过
- [ ] `pnpm build`（admin-app + student-app）通过
- [ ] DevTools 无残留 mock 章节数据

## 17. 不在范围（PR 3 不做，留给后续）

- 学习进度持久化（`course_lesson_progress` 表 + `/progress/me` 真实接口）→ PR 4
- 视频播放器集成（HLS/MP4 + 心跳上报）→ PR 4
- 章节/课时拖拽排序 UI（后端 reorder 接口已就绪）→ 单独迭代
- 课时关联 `course_materials` 的资料挑选器（当前只支持手填 material_id）→ 单独迭代
- 评分评价 → 三期

## 18. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| `ApiErrorCode` 现有枚举命名差异 | 实施前先 `grep -rn "enum.*ApiErrorCode\|public static.*=.*new BusinessException"`，挑最接近的语义 |
| 软删除逻辑 + 唯一键冲突 | 唯一索引把 `is_deleted` 列纳入，删除后再创建同 order 不会冲突 |
| reorder 期间唯一键冲突 | 两阶段更新（先加大偏移，再写最终值），单事务内可见 |
| student-app `useCourseStore` 已存在但内部 API 风格不一致 | Step 12.2 时先 `cat frontend/apps/student-app/src/stores/course.js` 检查，按现有 setup 风格写（pinia setup vs option store） |
| Element Plus 版本差异：`el-drawer` 默认 size 单位 | 使用字符串 `"480px"` 与 admin-app 既有抽屉保持一致 |

