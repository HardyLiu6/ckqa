# 课程章节/课时管理与学生端联动 实施计划（PR 3）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 CKQA 现有 `courses` 体系上，落地"课程章节 + 课时"两层结构，admin-app 提供完整的章节/课时管理后台，student-app 课程详情页和学习页接入真实数据，移除占位文案。

**Architecture:** 后端新增 `course_chapters` / `course_lessons` 两表 + REST CRUD 接口（沿用 `/api/v1/courses/{courseId}/chapters` 占位接口扩展为真实接口）。admin-app 把 `course-chapters` 路由从占位页换成真实管理界面（章节列表 + 课时编辑抽屉），通过 `componentKey` 直接挂 `ChapterManagementPanel`，绕开 `ModulePage` 的通用包裹器。student-app 的 `CourseDetail.vue` 章节 tab 与 `CourseLearn.vue` 目录侧栏从 mock 改为接口数据。本期不做视频播放器集成、学习进度持久化、评价；也不在本 PR 打桩心跳接口（待 PR 4 时一起加）。

> **审阅修订（2026-05-20）：** 本计划已基于现仓库实际代码经历一轮审阅，主要修正点：
> 1. 后端异常类与错误码改用真实存在的 `org.ysu.ckqaback.exception.BusinessException` + `org.ysu.ckqaback.api.ApiResultCode`，并新增 `CHAPTER_NOT_FOUND` / `LESSON_NOT_FOUND` 枚举；
> 2. 唯一索引仍包含 `is_deleted`，service 在软删前先 `UPDATE chapter_order = -id`，避免反复软删同 order 时撞键；
> 3. admin-app API 层改用 `import { http } from '../axios/index.js'` + `unwrapApiResponse`；
> 4. student-app 复用既有 `coursesApi.listCourseChapters`，axios 拦截器已经解封 payload，不再做二次 `?.data ?? ...`；
> 5. admin-app 路由改为在 `router/index.js` `componentMap` 注册 `ChapterManagementPanel`，把 `course-chapters` 的 `componentKey` 直接换成 `ChapterManagementPanel`，并把 `status` 升为 `mvp`、写权限收紧为 `course:write`；
> 6. `CourseLearn.vue` 兼容 `lesson.completed` 字段缺失（默认 `false`）；
> 7. 课时 `materialId` 增加应用层校验（必须存在且属于同课程）。

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
| `frontend/apps/admin-app/src/views/pages/module-content.js` | （改造）course-chapters 配置改为 live + 简化为引导文案 |
| `frontend/apps/admin-app/src/router/index.js` | （改造）`componentMap` 注册 `ChapterManagementPanel` |
| `frontend/apps/admin-app/src/router/routes.js` | （改造）`course-chapters` 的 `componentKey` 改为 `ChapterManagementPanel`，`status` 升为 `mvp`，权限收紧为 `course:write` |
| `frontend/apps/admin-app/src/views/pages/ModulePage.vue` | （改造）课程详情页加"管理章节"快捷入口 |

### student-app 新增/修改

| 文件 | 责任 |
| --- | --- |
| `frontend/apps/student-app/src/api/courses.js` | （扩展）listCourseChapters 沿用现有命名，无需改名 |
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

> **设计说明**（基于 application.properties 中 `mybatis-plus.global-config.db-config.logic-delete-value=1` / `logic-not-delete-value=0` 的现状）：
>
> - 唯一键虽然把 `is_deleted` 列纳入，但 `is_deleted` 是 0/1 二态，**重复软删同一 `(course_id, chapter_order)` 仍会撞键**。因此 service 在软删前必须先把 `chapter_order` 改写为一个独占值（实施中使用 `-id`，详见 Task 4 Step 1 的 `deleteChapter` 实现）。`course_lessons` 同理。
> - `course_lessons.material_id` 加了外键 + `ON DELETE SET NULL`，但**应用层仍要校验 materialId 必须属于同一课程**，避免管理员误关联他课资料；详见 Task 5。

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

> 注意：demo 数据**不入版本控制**，因此不要写到 `sql/migrations/`。统一用 `/tmp/chapters_demo.sql`，避免被 git 误捕获。

```sql
-- /tmp/chapters_demo.sql（仅本机演示，不入版本控制）
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

## Task 1.5：扩充 ApiResultCode 错误码（章节/课时不存在）

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`

> 现仓库的 `ApiResultCode` 枚举里只有 `COURSE_NOT_FOUND` 等少数几个 not-found，没有 `CHAPTER_NOT_FOUND` / `LESSON_NOT_FOUND`，service 抛业务异常时需要新增。`BusinessException` 构造签名是 `(ApiResultCode, HttpStatus)` 或 `(ApiResultCode, HttpStatus, String)`，不接受 `(ApiErrorCode, message)`。后续 Task 4/5 一律用真实签名，参数校验失败复用既有 `VALIDATION_ERROR`。

- [ ] **Step 1: 在 `COURSE_NOT_FOUND` 附近追加两个枚举**

```java
    /**
     * 课程章节不存在。
     */
    CHAPTER_NOT_FOUND(4054, "课程章节不存在"),

    /**
     * 课程课时不存在。
     */
    LESSON_NOT_FOUND(4055, "课程课时不存在"),
```

> 4054 / 4055 紧跟 `GRAPH_ENTITY_NOT_FOUND(4053)`。

- [ ] **Step 2: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
git commit -m "feat(course-lms): ApiResultCode 新增 CHAPTER_NOT_FOUND / LESSON_NOT_FOUND" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 2：后端实体与 Mapper

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseChapters.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseChaptersMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/CourseLessonsMapper.java`

- [ ] **Step 1: 创建 CourseChapters 实体**

> 现仓库未注册 `MetaObjectHandler`，因此 `@TableField(fill = ...)` 不会真的填值。表 DDL 已经用 `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` 兜底，实体里不再加 `fill` 注解，以免误导后续维护者。

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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 CourseLessons 实体**

`backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/CourseLessons.java`：

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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.ChapterDto;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.exception.BusinessException;
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
        // 校验课程存在（不存在时 lookup service 自身会抛 COURSE_NOT_FOUND）
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

        // 级联软删除该章节下的所有课时（同样先把 lesson_order 置为 -id 释放唯一键）
        List<CourseLessons> lessons = lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>().eq(CourseLessons::getChapterId, chapter.getId())
        );
        for (CourseLessons lesson : lessons) {
            lessonsMapper.update(null,
                    new LambdaUpdateWrapper<CourseLessons>()
                            .eq(CourseLessons::getId, lesson.getId())
                            .set(CourseLessons::getLessonOrder, -lesson.getId().intValue()));
        }
        LambdaUpdateWrapper<CourseLessons> lessonUpdate = new LambdaUpdateWrapper<>();
        lessonUpdate.eq(CourseLessons::getChapterId, chapter.getId())
                .set(CourseLessons::getIsDeleted, true);
        lessonsMapper.update(null, lessonUpdate);

        // 章节本身：软删前先把 chapter_order 置为 -id（保证 (course_id, chapter_order, is_deleted=1)
        // 的唯一键不会在反复软删同一名次时发生冲突）
        chaptersMapper.update(null,
                new LambdaUpdateWrapper<CourseChapters>()
                        .eq(CourseChapters::getId, chapter.getId())
                        .set(CourseChapters::getChapterOrder, -chapter.getId().intValue()));
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
            throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "章节排序数量与现有章节不一致");
        }
        for (Long id : orderedIds) {
            boolean belongs = existing.stream().anyMatch(c -> Objects.equals(c.getId(), id));
            if (!belongs) {
                throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "章节不属于该课程：id=" + id);
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

    /** 包级可见以便 CourseLessonService 在需要时复用。 */
    CourseChapters requireChapter(String courseId, Long chapterId) {
        CourseChapters chapter = chaptersMapper.selectById(chapterId);
        if (chapter == null || !Objects.equals(chapter.getCourseId(), courseId)) {
            throw new BusinessException(ApiResultCode.CHAPTER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "章节不存在或不属于该课程");
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
            throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "章节序号已被占用：" + order);
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

> 注意：`BusinessException` 真实签名是 `(ApiResultCode, HttpStatus)` 或 `(ApiResultCode, HttpStatus, message)`，不要漏 HttpStatus。`CHAPTER_NOT_FOUND` / `LESSON_NOT_FOUND` 由 Task 1.5 新增。

- [ ] **Step 2: 编译验证**

```bash
./mvnw -DskipTests compile
```

预期：BUILD SUCCESS。

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;
import org.ysu.ckqaback.mapper.CourseMaterialsMapper;

import java.util.List;
import java.util.Objects;

/**
 * 课时增/改/删/排序业务。
 */
@Service
public class CourseLessonService {

    private final CourseChaptersMapper chaptersMapper;
    private final CourseLessonsMapper lessonsMapper;
    private final CourseMaterialsMapper materialsMapper;

    public CourseLessonService(
            CourseChaptersMapper chaptersMapper,
            CourseLessonsMapper lessonsMapper,
            CourseMaterialsMapper materialsMapper
    ) {
        this.chaptersMapper = chaptersMapper;
        this.lessonsMapper = lessonsMapper;
        this.materialsMapper = materialsMapper;
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

        if (request.getMaterialId() != null) {
            ensureMaterialBelongsToCourse(courseId, request.getMaterialId());
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
            ensureMaterialBelongsToCourse(courseId, request.getMaterialId());
            lesson.setMaterialId(request.getMaterialId());
        }
        lessonsMapper.updateById(lesson);
        return toResponse(lesson);
    }

    @Transactional
    public void deleteLesson(String courseId, Long chapterId, Long lessonId) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        CourseLessons lesson = requireLesson(chapter.getId(), lessonId);

        // 软删前先把 lesson_order 置为 -id，释放 (chapter_id, lesson_order, is_deleted=1) 唯一键
        lessonsMapper.update(null,
                new LambdaUpdateWrapper<CourseLessons>()
                        .eq(CourseLessons::getId, lesson.getId())
                        .set(CourseLessons::getLessonOrder, -lesson.getId().intValue()));
        lessonsMapper.deleteById(lesson.getId());
    }

    @Transactional
    public List<LessonDto.Response> reorderLessons(String courseId, Long chapterId, List<Long> orderedIds) {
        CourseChapters chapter = requireChapter(courseId, chapterId);
        List<CourseLessons> existing = lessonsMapper.selectList(
                new LambdaQueryWrapper<CourseLessons>().eq(CourseLessons::getChapterId, chapter.getId())
        );
        if (existing.size() != orderedIds.size()) {
            throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "课时排序数量与现有课时不一致");
        }
        for (Long id : orderedIds) {
            boolean belongs = existing.stream().anyMatch(l -> Objects.equals(l.getId(), id));
            if (!belongs) {
                throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "课时不属于该章节：id=" + id);
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
            throw new BusinessException(ApiResultCode.CHAPTER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "章节不存在或不属于该课程");
        }
        return chapter;
    }

    private CourseLessons requireLesson(Long chapterId, Long lessonId) {
        CourseLessons lesson = lessonsMapper.selectById(lessonId);
        if (lesson == null || !Objects.equals(lesson.getChapterId(), chapterId)) {
            throw new BusinessException(ApiResultCode.LESSON_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "课时不存在或不属于该章节");
        }
        return lesson;
    }

    private void ensureMaterialBelongsToCourse(String courseId, Long materialId) {
        // 实际仓库里的资料实体类名以 grep `class CourseMaterials` 为准；如果命名是 CourseMaterial（无 s），
        // 实施时按现有命名替换 import 与字段访问，保证与 backend 当前的 mapper 一致。
        CourseMaterials material = materialsMapper.selectById(materialId);
        if (material == null || !Objects.equals(material.getCourseId(), courseId)) {
            throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "关联资料不存在或不属于该课程：materialId=" + materialId);
        }
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
            throw new BusinessException(ApiResultCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "课时序号已被占用：" + order);
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

> **实施提示**：在写 service 之前先用 `grep -rn "class CourseMaterials\b\|class CourseMaterial\b" backend/ckqa-back/src/main/java` 确认资料实体类与 mapper 的真实命名（PR 2 中存在 `CourseMaterials` 与 `CourseMaterialsMapper`，但若命名有差异，请按实际值替换）。

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
import org.ysu.ckqaback.exception.BusinessException;
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
        // service 用 orderByDesc + LIMIT 1 + findFirst()，所以 mock 必须按 desc 顺序返回，
        // 否则 findFirst 拿到的是最小 order，断言会失败。
        when(chaptersMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildChapter(2L, "c1", 2), buildChapter(1L, "c1", 1)));
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
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.course.dto.LessonDto;
import org.ysu.ckqaback.entity.CourseChapters;
import org.ysu.ckqaback.entity.CourseLessons;
import org.ysu.ckqaback.mapper.CourseChaptersMapper;
import org.ysu.ckqaback.mapper.CourseLessonsMapper;
import org.ysu.ckqaback.mapper.CourseMaterialsMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLessonServiceTest {

    @Mock CourseChaptersMapper chaptersMapper;
    @Mock CourseLessonsMapper lessonsMapper;
    @Mock CourseMaterialsMapper materialsMapper;

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

        // 同样必须按 desc 顺序，因为 service 用 orderByDesc + findFirst 取最大值
        when(lessonsMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildLesson(3L, 7L, 3), buildLesson(2L, 7L, 2), buildLesson(1L, 7L, 1)));
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
// 使用与其它 admin-app 接口一致的写法：从 axios/index.js 拿 http 实例 + 用 client.js 的
// unwrapApiResponse 解封统一响应体（不要再 ?.data?.data）。
import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

const base = (courseId) => `/courses/${encodeURIComponent(courseId)}/chapters`

export async function listChapters(courseId, client = http) {
  return unwrapApiResponse(await client.get(base(courseId)))
}

export async function createChapter(courseId, payload, client = http) {
  return unwrapApiResponse(await client.post(base(courseId), payload))
}

export async function updateChapter(courseId, chapterId, payload, client = http) {
  return unwrapApiResponse(await client.put(`${base(courseId)}/${chapterId}`, payload))
}

export async function deleteChapter(courseId, chapterId, client = http) {
  return unwrapApiResponse(await client.delete(`${base(courseId)}/${chapterId}`))
}

export async function reorderChapters(courseId, chapterIds, client = http) {
  return unwrapApiResponse(await client.post(`${base(courseId)}/reorder`, { chapterIds }))
}

export async function createLesson(courseId, chapterId, payload, client = http) {
  return unwrapApiResponse(await client.post(`${base(courseId)}/${chapterId}/lessons`, payload))
}

export async function updateLesson(courseId, chapterId, lessonId, payload, client = http) {
  return unwrapApiResponse(
    await client.put(`${base(courseId)}/${chapterId}/lessons/${lessonId}`, payload),
  )
}

export async function deleteLesson(courseId, chapterId, lessonId, client = http) {
  return unwrapApiResponse(
    await client.delete(`${base(courseId)}/${chapterId}/lessons/${lessonId}`),
  )
}

export async function reorderLessons(courseId, chapterId, lessonIds, client = http) {
  return unwrapApiResponse(
    await client.post(`${base(courseId)}/${chapterId}/lessons/reorder`, { lessonIds }),
  )
}
```

> 后端 `listChapters` 返回结构是 `CourseChaptersResponse { chapters, featureStatus, message }`，因此前端 `listChapters` 解封后拿到的是这个对象，访问 `result.chapters` / `result.featureStatus`。

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
    // 后端返回 CourseChaptersResponse: { chapters, featureStatus, message }
    chapters.value = resp?.chapters || []
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
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`（course-chapters 配置：保留 `live` + `dataSource` 字段，但本路由实际不再走 ModulePage）
- Modify: `frontend/apps/admin-app/src/router/index.js`（在 `componentMap` 注册 `ChapterManagementPanel`）
- Modify: `frontend/apps/admin-app/src/router/routes.js`（把 `course-chapters` 的 `componentKey` 改成 `'ChapterManagementPanel'`，`status` 升为 `'mvp'`，权限收紧为 `['course:write']`）
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`（仅在课程详情页加"管理章节"快捷入口）

> **方案修订理由**：现仓库里 `ModulePage.vue` 已超 4000 行，新增 `v-else-if="route.name === 'course-chapters'"` 分支需要在多处插入并依赖准确的 v-else-if 顺序，对 ChapterManagementPanel 来说也会被外层 hero、DataSourceChip、刷新按钮等通用脚手架包裹，UI 不合理。
> 我们改成直接在 `router/index.js` 的 `componentMap` 注册一个新组件，然后在 `routes.js` 把 `course-chapters` 的 `componentKey` 切换过去——这是 admin-app 已经在用的现成模式（参考 `RouteState`、`PromptBuilderPage` 等）。

- [ ] **Step 1: 把 course-chapters 的 module-content 配置精简**

修改 `frontend/apps/admin-app/src/views/pages/module-content.js` 中的 `'course-chapters'` 配置块：

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

> 即使该路由不再走 ModulePage，保留这一配置块仍有意义：导航/搜索/统计模块仍可能枚举它（与 admin-app 现有模式保持一致）。

- [ ] **Step 2: 在 `router/index.js` 注册 ChapterManagementPanel**

修改 `frontend/apps/admin-app/src/router/index.js`，在已有 import 之后追加：

```js
import ChapterManagementPanel from '../views/pages/courses/ChapterManagementPanel.vue'
```

并把它加入 `componentMap`：

```js
const componentMap = {
  // ...原有...
  ModulePage,
  // ...
  ChapterManagementPanel,
  // ...
}
```

- [ ] **Step 3: 把 `course-chapters` 路由配置切换到新组件**

修改 `frontend/apps/admin-app/src/router/routes.js` 中的 `course-chapters` 块：

```js
{
  path: '/app/courses/:courseId/chapters',
  name: 'course-chapters',
  componentKey: 'ChapterManagementPanel',
  meta: {
    title: '课程章节',
    layout: 'detail',
    permissions: ['course:write'],
    status: 'mvp',
    navGroup: 'courses',
    resource: 'courseChapters',
    scope: 'course',
  },
},
```

> 本步要点：
> - `componentKey: 'ChapterManagementPanel'` 直接渲染管理面板，不走 ModulePage 包装。
> - `status: 'mvp'`，避免 navigation-model 把它当未开放路由。
> - `permissions: ['course:write']` 把"删除/新增"等破坏性操作收紧到写权限角色（admin / teacher）。

- [ ] **Step 4: 修正 admin-app 路由相关测试**

现仓库 `frontend/apps/admin-app/src/app-shell.test.js` 与同目录其它单测可能断言过 `course-chapters` 的 `meta.status === 'preview'` 或 `componentKey === 'ModulePage'`。先用 grep 排查：

```bash
grep -n "course-chapters\|courseChapters" frontend/apps/admin-app/src
```

把以上断言更新为新的 `'mvp'` / `'ChapterManagementPanel'` / `['course:write']` 组合。如果未发现相关断言，跳过本步。

- [ ] **Step 5: 在课程详情页加"管理章节"快捷入口**

定位 `ModulePage.vue` 课程详情区域（`courseBlock?.item?.courseName` 渲染处的 actions 行），追加一个跳转按钮：

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

如已存在 BookOpen 图标 import 直接复用；否则 `import { BookOpen } from 'lucide-vue-next'`。建议在按钮上额外加一层权限判断（`v-if="hasPermission('course:write')"` 或 admin-app 现有的 RBAC helper），与 `permissions: ['course:write']` 对齐。

- [ ] **Step 6: 构建验证**

```bash
pnpm build  # cwd: frontend/apps/admin-app
```

预期：构建成功。

- [ ] **Step 7: 浏览器抽烟**

```
打开 admin-app dev：http://127.0.0.1:5174
登录 → 进入 /app/courses → 点击 kb5 课程 → 看到"管理章节"按钮
点击 → 跳到 /app/courses/crs-20260506-r4slkr/chapters
应能：
- 看到 Step 1.5 注入的 demo 章节（无 ModulePage 的 hero/DataSourceChip 装饰）
- 点击"新增章节"→ 弹窗 → 提交后列表刷新
- 在某章节点击"新增课时"→ 抽屉 → 提交后列表刷新
- 点击编辑/删除均能生效
```

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js \
        frontend/apps/admin-app/src/router/index.js \
        frontend/apps/admin-app/src/router/routes.js \
        frontend/apps/admin-app/src/views/pages/ModulePage.vue
git commit -m "feat(admin-chapters): course-chapters 路由接入真实管理面板" --author="LiuJunDa <3364863955@qq.com>"
```

---

## Task 12：student-app store 与 API 接入真实章节数据

**Files:**
- Modify: `frontend/apps/student-app/src/api/courses.js`（保留现有 `listCourseChapters` 命名，无需重命名）
- Modify: `frontend/apps/student-app/src/stores/course.js`

> **关键：** 现仓库 student-app 已有 `coursesApi.listCourseChapters(courseId)`，并且 `axios/index.js` 的拦截器 `resolveResponsePayload` 已经把 ApiResponse 解封成 payload。因此 store 拿到的 `resp` 就是 `CourseChaptersResponse { chapters, featureStatus, message }`，不要再写 `resp?.data ?? ...`。

- [ ] **Step 1: 检查 student-app/src/api/courses.js 中的 listCourseChapters**

```bash
grep -n "listCourseChapters\|/chapters" frontend/apps/student-app/src/api/courses.js
```

确认仍然是：

```js
listCourseChapters(courseId) {
  return client.get(`/courses/${encodeURIComponent(courseId)}/chapters`)
},
```

不需要修改。本期只把注释里的"占位接口"改一下：

```js
/** 课程章节列表（PR 3 起接真实数据；返回 {chapters, featureStatus, message}） */
listCourseChapters(courseId) {
  return client.get(`/courses/${encodeURIComponent(courseId)}/chapters`)
},
```

- [ ] **Step 2: 在 stores/course.js 中加 chapters 状态**

修改 `frontend/apps/student-app/src/stores/course.js`，import 区追加：

```js
import { listCourseChapters } from '@/api/courses.js'
```

如果文件已经 import 过 `coursesApi` 或 `createCoursesApi`，直接复用即可。

在 `useCourseStore` 的 setup 函数体内追加：

```js
const chapters = ref([])
const chaptersFeatureStatus = ref('coming-soon')
const chaptersLoading = ref(false)
const chaptersError = ref(null)

async function loadChapters(courseId) {
  if (!courseId) return
  chaptersLoading.value = true
  chaptersError.value = null
  try {
    // axios/index.js 的响应拦截器已经把 ApiResponse 解封成 payload，
    // 所以这里 resp 就是后端 CourseChaptersResponse 本体。
    const resp = await listCourseChapters(courseId)
    chapters.value = Array.isArray(resp?.chapters) ? resp.chapters : []
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

并在 `return { ... }` 里把 `chapters / chaptersFeatureStatus / chaptersLoading / chaptersError / loadChapters` 一并暴露。

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

> 注意 student-app 路由是 `/course/detail/:id`，参数名是 `id`，**不是 courseId**。下面的代码与现有路由保持一致。

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

mock 数据里 `chapter.order` / `lesson.order` / `lesson.duration` / `lesson.completed` 在新结构里分别变成 `chapter.chapterOrder` / `lesson.lessonOrder` / `lesson.durationMinutes` /（暂无对应字段）。

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

把模板中所有读取 `lesson.order` 的位置改成 `lesson.lessonOrder`。

**`lesson.completed` 兼容**：CourseLearn 的 mock 里有 `completed` 字段，会驱动"已完成/未完成"样式与 `completedCount` 计数。新接口暂未提供该字段（PR 4 才接学习进度），因此在 `<script setup>` 顶部加一个 helper：

```js
function isLessonCompleted(lesson) {
  // PR 3 暂无学习进度持久化，所有课时均视为未完成
  return lesson?.completed === true
}
```

把模板中 `lesson.completed` 全部替换为 `isLessonCompleted(lesson)`，把 reduce 计数：

```js
const completedCount = computed(() =>
  chapters.value.reduce(
    (acc, ch) => acc + (ch.lessons || []).filter(isLessonCompleted).length,
    0,
  ),
)
```

`totalLessons` 同样用 `(ch.lessons || []).length` 兜底空数组。

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
# 如果是分支首次推送，加 -u 关联 upstream
git push -u origin feat/2026-05-19-student-knowledge-graph
# 后续推送 git push 即可
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

### 17.1 视频与 `content_uri` 的边界

- 现仓库 **没有任何视频实际落到 MinIO**，`CourseLearn.vue` 里的"播放器"只是 `VideoPlay` 图标 + 静态文案占位，没有 `<video>` 元素和真正的 src。
- DDL 把 `course_lessons.content_uri` 注释成 *"视频/文档外链或 MinIO 引用"*，是**两条路都兼容**的占位设计，PR 3 不挑边：
  - PR 3 后端把 `content_uri` 当**普通字符串**存库，不区分外链 vs MinIO key、不验证可达性、不做签名 URL、不做转码或 HLS 分片。
  - admin-app `LessonEditDrawer` 把 `contentUri` 字段开放给运营手填（视频外链 / MinIO `bucket/key` / 未来的 OneAPI 视频地址都可以）。
  - 推荐演进：PR 4 优先支持纯外链（B 站 / 七牛 / 阿里云 VOD / CDN 直链），用原生 `<video>` 或 `hls.js` 播；MinIO 自托管 + 转码 + 短期签名 URL 的路径放到三期再决定。
  - 未来若引入多种视频来源策略，建议补一个 `content_origin` 枚举列（`external_url` / `minio` / `oneapi` / ...），届时迁移可加列加默认值，不破坏 PR 3 的 schema。

### 17.2 `is_free_preview` 在 PR 3 的实际效果

- DDL 已经把 `is_free_preview tinyint(1)` 加进 `course_lessons`，admin-app 抽屉也能填这个开关。
- 但 PR 3 不据此放宽 student-app 的访问策略：未加入课程的学生**仍然走现有"未加入课程"分支**，不会因为某个 lesson `is_free_preview = true` 就解锁试看入口。
- 这里只是把字段先入库占好位，等 PR 4 视频鉴权 + 试看入口（公共课 / 试听章节 / 短期 token）一起做时再点亮。

### 17.3 学习进度粒度与 PR 3 的派生表现

- 设计粒度是 **用户 × 课时**（参考 `docs/2026-05-19-course-info-architecture-plan.md` §6.2 `course_lesson_progress`），不是按章节、不是按课程：
  - 一个用户对一个 lesson 至多一行记录（`UNIQUE(user_id, lesson_id)`），心跳走 upsert。
  - `progress_percent` 由前端按 `last_position_seconds / duration` 推送；文档 / quiz / live / external 共用同一张表，多半只用 `is_completed` 这个布尔位。
  - 章节进度、课程进度都是**派生指标**，按 `course_id` 聚合 reduce，不再单独建表。
  - 心跳节奏（建议 15~30s 一次或暂停 / 切换 lesson 时强制提交）由 PR 4 决定。
- PR 3 的派生表现：
  - 后端不返回任何完成态字段；前端 `isLessonCompleted(lesson)` 永远拿到 `lesson.completed === true` 这个永远为假的判断。
  - `CourseLearn.vue` 侧边栏进度条会**一直停在 `0/N`**，sidebar 上的 ✅ 高亮也不会出现。
  - 这是**预期行为**，不是 bug；PR 4 接 `course_lesson_progress` + 心跳 API 后再点亮 UI。

## 18. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| `BusinessException` / `ApiResultCode` 命名差异 | 已审阅核实：异常类位于 `org.ysu.ckqaback.exception.BusinessException`，错误码为 `ApiResultCode`，构造签名带 `HttpStatus`；`CHAPTER_NOT_FOUND` / `LESSON_NOT_FOUND` 由 Task 1.5 新增 |
| 软删除逻辑 + 唯一键冲突 | `is_deleted` 是 0/1 二态，仅靠唯一键无法防止反复软删撞键。service 层在删除前先把 order 置为 `-id` 释放原 order，详见 Task 4 / Task 5 |
| reorder 期间唯一键冲突 | 两阶段更新（先加大偏移，再写最终值），单事务内可见。同时保持"同一时刻仅一个 admin 在编辑"的弱假设；如未来出现并发冲突再引入乐观锁 |
| `material_id` 引用错课程 | service 层 `ensureMaterialBelongsToCourse` 显式校验，避免依赖 FK 抛 SQL 异常 |
| student-app `useCourseStore` 内部 API 风格不一致 | 现仓库使用 `defineStore('course', () => {...})` 的 setup 写法，本计划保持一致；实施前先 `cat frontend/apps/student-app/src/stores/course.js` 确认 |
| Element Plus 版本差异：`el-drawer` 默认 size 单位 | 使用字符串 `"480px"` 与 admin-app 既有抽屉保持一致 |
| `CourseChaptersResponse` 兼容包装可能成为噪声 | PR 3 内保留 `featureStatus`/`message` 字段以兼容 student-app 已有占位渲染；后续 PR 视情况清理 |
| admin-app shell 测试断言 status/componentKey | 修改路由后需要在 `app-shell.test.js` 等单测里同步断言（详见 Task 11 Step 4） |
