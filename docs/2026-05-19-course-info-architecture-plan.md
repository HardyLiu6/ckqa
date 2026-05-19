# 课程信息架构升级规划（PR 1：最小化版本 + LMS 二期设计稿）

> 起草日期：2026-05-19
> 关联分支：`feat/2026-05-19-student-knowledge-graph`
> 关联文档：
> - `docs/2026-05-19-student-knowledge-graph-page-plan.md`（图谱页规划）
> - `docs/student-backend-graphrag-api-contract.md`（既有契约）

## 0. 目标

学生端 `student-app` 当前课程列表 / 详情完全是 mock 数据，无法对应真实的 `courses` schema。本次围绕"脱 mock + 知识图谱独立入口 + 为 LMS 二期预留接口"做一次最小化升级，并把完整 LMS 设计稿落到本文档。

PR 1 范围：

1. 知识图谱页 `/knowledge/graph` 加课程选择器（不再强依赖 `?courseId=` query）
2. `courses` 表加 `category / tags / objectives / audience / difficulty / estimated_hours` 六个字段
3. 后端 DTO（`CourseDetailResponse / CourseSummaryResponse / CourseCreateRequest / CourseUpdateRequest`）补齐字段
4. 学生端新增 `api/courses.js`、`stores/course.js`，课程列表 / 详情页脱 mock
5. 章节、学习进度、评价以「未开放」占位（保留视觉稿的 mock 视图但加横幅说明）

PR 1 不做（留给后续）：

- 章节 / 课时 / 学习进度 / 评价的 DB 表
- 学生端"自助加入课程"流程
- admin-app 课程表单字段扩展（PR 2）

## 1. 决策点（已确认）

| 决策 | 选择 |
| --- | --- |
| `category` 字段类型 | `varchar(64)` 自由输入，加普通索引；后期需要时再升级到独立分类表 |
| 学生端"加入课程"入口 | 不在本次范围；写入长期计划，沿用 admin-app 教师批量加入 + `access_policy=public` 公开课程 |
| 标签 / 目标 / 人群上限 | `tags ≤ 20`、`objectives ≤ 12`、`audience ≤ 10`；只在 DTO `@Size` 校验，**不在 DB 强制**，后期改约束不动 schema |
| PR 1 范围 | DB + 后端 DTO + 知识图谱选择器 + 学生端课程页脱 mock，作为单个 PR 推进 |

## 2. 数据库变更（PR 1 落地）

新增迁移 `sql/migrations/20260519_course_metadata_phase1.sql`：

```sql
ALTER TABLE `courses`
  ADD COLUMN `category` varchar(64) NULL COMMENT '课程分类（自由输入），如：人工智能/前端开发' AFTER `description`,
  ADD COLUMN `tags` json NULL COMMENT '课程标签数组（最多 20 个，DTO 校验）' AFTER `category`,
  ADD COLUMN `objectives` json NULL COMMENT '学习目标数组（最多 12 条，DTO 校验）' AFTER `tags`,
  ADD COLUMN `audience` json NULL COMMENT '适合人群数组（最多 10 条，DTO 校验）' AFTER `objectives`,
  ADD COLUMN `difficulty` enum('beginner','intermediate','advanced') NULL COMMENT '难度级别' AFTER `audience`,
  ADD COLUMN `estimated_hours` int NULL COMMENT '预计学习时长（小时），完整 LMS 上线前由教师手填' AFTER `difficulty`,
  ADD INDEX `idx_courses_category`(`category` ASC);
```

设计要点：

- JSON 字段存数组，避免立刻新增关联表；后期升级时 `tags` 拆独立表只是数据搬迁，schema 不破坏。
- `difficulty` 用 enum，便于前端做下拉枚举展示；自由扩展靠 `category`。
- 上限只在 DTO `@Size` 校验。

## 3. 后端 DTO 与 Service 变更（PR 1 落地）

### 3.1 新增字段（六个新字段都加到下列 DTO）

- `CourseCreateRequest`：`category? / tags? / objectives? / audience? / difficulty? / estimatedHours?`，全部可选；`@Size` 校验数组上限。
- `CourseUpdateRequest`：同上，全部可选（用 PATCH 语义，传啥更新啥；当前 PUT 端点保留全量替换不变）。
- `CourseDetailResponse`：`category / tags / objectives / audience / difficulty / estimatedHours`，按字段透出；JSON 字段反序列化为 `List<String>`。
- `CourseSummaryResponse`：仅透出 `category / tags / difficulty`（列表卡片需要），不带 `objectives / audience`（详情才用）。

### 3.2 实体层

- `entity/Courses.java`：加 6 个字段，`tags / objectives / audience` 用 MyBatis-Plus 的 `@TableField(typeHandler = JacksonTypeHandler.class)` + 类型 `List<String>`。

### 3.3 Service

- `CourseCommandService.createCourse / updateCourse`：把新字段写库；JSON 字段为空时存 `null`。
- `CourseLookupService.listCourses / getCourseDetail`：透出新字段。

### 3.4 预留二期接口（占位响应，PR 1 落地占位）

| 路径 | 用途 | PR 1 返回 |
| --- | --- | --- |
| `GET /api/v1/courses/{courseId}/chapters` | 课程章节列表 | `{ "chapters": [], "featureStatus": "coming-soon", "message": "课程章节功能即将开放" }` |
| `GET /api/v1/courses/{courseId}/progress/me` | 当前学生学习进度 | `{ "enrolled": <实际值>, "lessonProgress": [], "featureStatus": "coming-soon" }` |

实现要点：返回前先做基本校验（课程存在 / 学生加入），但不查任何业务表，直接返回占位结构。这样学生端调用收到结构化数据而不是 404。

## 4. 知识图谱页课程选择器（PR 1 落地）

### 4.1 当前问题

`/knowledge/graph` 强依赖 `?courseId=` query，无 query 时只显示"缺少课程上下文"。

### 4.2 改造

进入页面流程：

1. 调 `GET /api/v1/courses?size=20` 拉学生可见课程（已加入 + 公开）。
2. 默认选中规则：
   - URL `?courseId=` 指定的课程（保留深度链接）
   - 否则选第一个 `activeIndexRunId != null` 的知识库对应的课程
   - 否则空选 → 提示"请选择课程"
3. 顶栏左侧加 `el-select` 课程下拉。
4. 切换课程 → `router.replace({ query: { courseId } })` 同步 URL → 触发 `loadOverview`。
5. 课程列表为空 → 渲染"未加入任何课程"状态卡，CTA 跳 `/course/list`。

### 4.3 改动文件

- `frontend/apps/student-app/src/api/courses.js`（新增）：`listCourses()` 封装。
- `frontend/apps/student-app/src/stores/graph.js`：增加 `availableCourses / loadCourses()`。
- `frontend/apps/student-app/src/views/knowledge/KnowledgeGraph.vue`：加下拉 + 状态切换。

## 5. 学生端课程页脱 mock（PR 1 落地）

### 5.1 新增 / 改动文件

- `src/api/courses.js`（新增）：`listCourses / getCourseDetail / listCourseMaterials / listCourseChapters / getMyCourseProgress`。
- `src/stores/course.js`（重构）：去掉 mock 数组，改为接口驱动。
- `views/course/index.vue`：用真实接口，分类筛选 = 后端 `category`，搜索 = 关键字参数。
- `views/course/CourseDetail.vue`：
  - 顶部信息（封面 / 名称 / 描述 / 教师 / 分类 / 标签 / 目标 / 人群 / 难度 / 预计学时）→ 真实接口
  - 中间"课程目录"tab → 改为展示 `course_materials` 资料列表（学生端只读）
  - 章节 / 课时 / 学习进度 → 折叠卡 + "功能即将开放"占位
  - 评价 tab → 移除（二期再加）
- `views/course/MyCourse.vue`：调 `listCourses?membershipFilter=mine` 拿已加入课程；目前若后端没有该筛选，先调 `listCourses` 全部，再用 `course_memberships.user_id == 当前学生` 在 store 端过滤；正式接口在 PR 2 加。
- `views/course/CourseLearn.vue`：保留现有视觉稿，加横幅"功能预览，正式数据待接入"。

### 5.2 学生端「我加入了哪些课程」获取方式

PR 1 阶段方案：

- 复用现有 `GET /api/v1/courses`，后端会按 `actorUserCode` 自动过滤为"该学生可见的课程"（即 `course_memberships.status = 'active'` + `access_policy = 'public'` 的并集）。
- 在前端按 `course.memberStatus`（如果后端 DTO 暴露的话；当前没暴露——见下）区分"已加入 vs 仅可见"。

需要后端配合：`CourseSummaryResponse` 加一个 `memberStatus` 字段（`'member' / 'public_visitor'`），`CourseLookupService.listCourses(request, userCode)` 时填充。这是 PR 1 的小幅扩展，不增加迁移。

## 6. 完整 LMS 设计稿（二期 / 三期落地）

### 6.1 章节 / 课时（二期）

```sql
CREATE TABLE `course_chapters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` varchar(64) NOT NULL,
  `chapter_order` int NOT NULL COMMENT '同一课程内 1 起步',
  `title` varchar(255) NOT NULL,
  `description` text NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_course_order`(`course_id`, `chapter_order`),
  CONSTRAINT `fk_chapters_course` FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`)
);

CREATE TABLE `course_lessons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chapter_id` bigint NOT NULL,
  `lesson_order` int NOT NULL,
  `title` varchar(255) NOT NULL,
  `lesson_type` enum('video','document','quiz','live','external') NOT NULL DEFAULT 'video',
  `content_uri` varchar(1024) NULL COMMENT '视频/文档外链或 MinIO 引用',
  `duration_minutes` int NULL,
  `is_free_preview` tinyint(1) NOT NULL DEFAULT 0,
  `material_id` bigint NULL COMMENT '关联的 course_materials.id，可空',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_chapter_order`(`chapter_id`, `lesson_order`),
  CONSTRAINT `fk_lessons_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapters`(`id`),
  CONSTRAINT `fk_lessons_material` FOREIGN KEY (`material_id`) REFERENCES `course_materials`(`id`) ON DELETE SET NULL
);
```

### 6.2 学习进度（二期）

```sql
CREATE TABLE `course_lesson_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `course_id` varchar(64) NOT NULL,
  `progress_percent` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `last_position_seconds` int NULL COMMENT '视频播放最后位置',
  `is_completed` tinyint(1) NOT NULL DEFAULT 0,
  `completed_at` timestamp NULL,
  `last_viewed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_progress_user_lesson`(`user_id`, `lesson_id`),
  KEY `idx_progress_user_course`(`user_id`, `course_id`),
  CONSTRAINT `fk_progress_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_progress_lesson` FOREIGN KEY (`lesson_id`) REFERENCES `course_lessons`(`id`)
);
```

### 6.3 评分评价（三期）

```sql
CREATE TABLE `course_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `course_id` varchar(64) NOT NULL,
  `rating` tinyint UNSIGNED NOT NULL COMMENT '1-5',
  `content` text NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_course`(`user_id`, `course_id`),
  CONSTRAINT `fk_reviews_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_reviews_course` FOREIGN KEY (`course_id`) REFERENCES `courses`(`course_id`)
);
```

## 7. admin-app 联动（PR 2 落地）

`frontend/apps/admin-app/src/views/pages/module-content.js` 中：

- `course-detail.dataSource: 'mock'` → `'live'`
- 课程创建 / 编辑表单组件（在 `ModulePage.vue` 内联或抽出 `CourseFormDrawer.vue`）增加：
  - 分类（`el-input` 自由输入或带历史值的 `el-autocomplete`）
  - 标签（`el-input-tag` 上限 20）
  - 学习目标（多行 textarea，按行切分；上限 12 行）
  - 适合人群（同上，上限 10 行）
  - 难度（`el-select`：beginner / intermediate / advanced）
  - 预计学时（`el-input-number` 1~999）
- 课程详情页"概览"tab 加只读字段展示。
- 路由表预留 `/app/courses/:courseId/chapters` → 暂时挂 `RouteState` 风格"未开放"。

## 8. 长期计划（备忘）

- 学生自助加入课程（`POST /api/v1/courses/{courseId}/memberships/me`），需要 `access_policy=public` 课程允许；`restricted` 课程仍由教师邀请。
- 章节 / 课时管理后台。
- 视频播放器与 `progress_percent` 心跳上报。
- 评分评价。
- 推荐课程（同分类规则推荐 → 协同过滤）。
- 课程封面默认占位图升级（避免现有 dicebear / picsum 外链依赖）。

## 9. 验收口径（PR 1）

- DB 迁移 `20260519_course_metadata_phase1.sql` 应用成功，`SHOW CREATE TABLE courses` 含 6 个新字段
- `POST /api/v1/courses` 创建时可附带新字段，`GET /api/v1/courses/{id}` 回传完整字段
- `GET /api/v1/courses/{id}/chapters` 返回 `featureStatus: 'coming-soon'`
- `/knowledge/graph` 不带 query 进入时显示课程选择器，选课后能加载图谱
- `/course/list` 显示真实数据库课程，分类 / 标签 / 教师正确展示
- `/course/detail/:id` 显示真实数据，章节区域显式标"功能即将开放"
- `pnpm build` 通过；`./mvnw '-Dtest=!IndexProgressParserTest' test` 全部通过
- 浏览器 DevTools 中所有课程相关请求前缀为 `/api/v1/courses/...`，无 mock 残留

## 10. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| `courses.tags` 等 JSON 字段在不同 MySQL 版本反序列化差异 | MyBatis-Plus 的 `JacksonTypeHandler` 已经在仓库其他表使用过（`extra_metadata`），沿用同一模式 |
| 现有 admin-app 课程详情页是 mock，本次只动学生端 + DB | PR 2 单独迭代 admin 表单，本 PR 不破坏 admin 现状 |
| 学生端"已加入 / 仅可见"区分 | 后端 `CourseSummaryResponse` 加 `memberStatus` 字段，前端按此分组显示 |
| 课程章节 mock 视图保留可能让用户误以为已上线 | 顶部加显眼 `ElAlert` "功能预览，正式数据待接入" |
