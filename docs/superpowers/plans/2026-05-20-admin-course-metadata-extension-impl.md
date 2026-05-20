# PR 2 实施计划：admin-app 课程元数据扩展

> 起草日期：2026-05-20
> 关联文档：
> - `docs/2026-05-19-course-info-architecture-plan.md`（PR 1 设计稿，已落地）
> - `sql/migrations/20260519_course_metadata_phase1.sql`（PR 1 字段已落库）
> 工作分支：`feat/2026-05-20-admin-course-metadata` 或独立分支
> 工作目录：建议另起 worktree `.worktrees/admin-course-metadata/`

## 0. 目标

PR 1 已经把 `category / tags / objectives / audience / difficulty / estimated_hours` 六个字段落到 `courses` 表 + 后端 DTO + 学生端展示。现在还缺：

1. **admin-app 课程创建 / 编辑表单**支持这六个字段输入
2. **admin-app 课程详情页**"概览"tab 展示这些字段
3. **admin-app 课程列表**卡片 / 表格里展示分类、难度、标签
4. 路由表预留章节管理入口（占位）

本 PR 不做：

- 章节 / 课时管理后台（PR 3）
- 学习进度后台（PR 3）
- 学生自助加入课程（长期计划）

## 1. 现状摸查

### admin-app 课程页架构

- 入口：`frontend/apps/admin-app/src/views/pages/ModulePage.vue`（动态组件壳）
- 配置：`frontend/apps/admin-app/src/views/pages/module-content.js`，按 `route.name` 提供配置
  - `courses`：列表页（`variant: 'table'`）
  - `course-detail`：详情页（`variant: 'overview'`，**当前 `dataSource: 'mock'`**）
- API：`frontend/apps/admin-app/src/api/courses.js` 提供 `listCourses / createCourse / updateCourse / getCourse`
- 路由：`/app/courses`、`/app/courses/:courseId`、`/app/courses/:courseId/members`、`/app/courses/:courseId/materials`

### 后端契约（PR 1 已就绪）

- `CourseCreateRequest` / `CourseUpdateRequest`：六个字段已加 `@Size` 校验（tags ≤ 20、objectives ≤ 12、audience ≤ 10）
- `CourseDetailResponse`：透出全部六个字段
- `CourseSummaryResponse`：透出 `category / tags / difficulty / estimatedHours`（列表卡片需要的）

**结论**：PR 2 只动 admin-app 前端，后端零改动。

## 2. 实施任务

### T1. admin-app 课程创建 / 编辑表单字段扩展

#### T1.1 字段映射

| 字段 | UI 组件 | 校验 | 默认值 |
| --- | --- | --- | --- |
| `category` | `el-autocomplete`（带已有 category 历史值建议） | `<= 64 字符` | 空 |
| `tags` | `el-input-tag` 或自定义 token 输入 | `<= 20 个，每项 <= 32 字符` | 空数组 |
| `objectives` | 多行 textarea + "添加目标"按钮，每条独立编辑 | `<= 12 条，每条 <= 200 字符` | 空数组 |
| `audience` | 同 objectives | `<= 10 条，每条 <= 100 字符` | 空数组 |
| `difficulty` | `el-select` 三选一 | `beginner / intermediate / advanced / null` | null |
| `estimatedHours` | `el-input-number` | `1 <= n <= 999`，可空 | null |

#### T1.2 表单组件位置

ModulePage.vue 现在内联了创建表单（`<el-dialog>` + 表单字段直接写在模板里）。建议抽取：

- 新建 `frontend/apps/admin-app/src/views/pages/courses/CourseFormDrawer.vue`
- 把现有的 `courseName / description / coverUrl / status / accessPolicy / teacherUserId` + 新增的六个字段都放进来
- ModulePage.vue 在创建 / 编辑课程时打开这个 drawer

如果觉得抽取风险大，可以**第一版不抽取**，直接在 ModulePage.vue 现有 dialog 里加字段，后续再重构。**推荐先不抽取**，PR 2 范围更小。

#### T1.3 已有 category 自动补全

- `CourseLookupService.listCourses` 已透出 `category`，前端 ModulePage 已经 listCourses 拿到了所有课程
- `el-autocomplete` 的 `fetch-suggestions` 用 `[...new Set(courses.map(c => c.category).filter(Boolean))]` 即可

#### T1.4 多条文本输入组件

`objectives` / `audience` 用列表式输入更好：

```vue
<div v-for="(item, idx) in form.objectives" :key="idx" class="multi-row">
  <el-input v-model="form.objectives[idx]" :maxlength="200" />
  <el-button @click="form.objectives.splice(idx, 1)">删除</el-button>
</div>
<el-button :disabled="form.objectives.length >= 12" @click="form.objectives.push('')">
  添加学习目标（{{ form.objectives.length }}/12）
</el-button>
```

### T2. admin-app 课程详情概览展示新字段

#### T2.1 切换 dataSource

`module-content.js` 中：

```diff
'course-detail': {
   variant: 'overview',
-  dataSource: 'mock',
+  dataSource: 'live',
```

#### T2.2 详情页概览区扩展

ModulePage.vue 的 course-detail 区当前只展示 `courseName / description / status / 教师 / 资料数 / 知识库数`。新增展示：

- 分类标签（彩色 chip）
- 难度（"入门 / 进阶 / 高级"）
- 预计学时（"约 X 学时"）
- 标签云（el-tag 列表）
- 学习目标（有序列表）
- 适合人群（无序列表）

字段为空时整段不展示，不要留空白卡片。

### T3. 课程列表卡片展示

`courses` 列表表格目前 7 列：`课程 / 授课教师 / 状态 / 资料进度 / 知识库 / 最近索引 / 更新时间`。

**不改表格列结构**（避免破坏现有 e2e），但在"课程"列的副标题位置加：

- 分类（小型 chip）
- 难度（小字标签）

### T4. 路由占位：章节管理

`module-content.js` 加新路由配置（占位）：

```js
'course-chapters': {
  variant: 'overview',
  dataSource: 'mock',
  eyebrow: 'Course Chapters',
  summary: '章节管理功能即将开放，PR 3 落地。',
  primaryAction: null,
  secondaryAction: null,
  facts: ['即将开放'],
  timeline: [
    { label: '功能定义', state: 'ready', detail: '已在 PR 1 设计稿规划' },
    { label: 'DB 表结构', state: 'pending', detail: 'course_chapters / course_lessons 待创建' },
    { label: '后台管理', state: 'pending', detail: 'PR 3 落地' },
  ],
},
```

routes.js 加：

```js
{
  path: '/app/courses/:courseId/chapters',
  name: 'course-chapters',
  componentKey: 'ModulePage',
  meta: { title: '课程章节', layout: 'detail', permissions: ['course:read'], status: 'preview', navGroup: 'courses', resource: 'courseChapters', scope: 'course' },
},
```

详情页加"管理章节"链接跳过去（disabled 状态 + tooltip "PR 3 上线"）。

### T5. 测试与验收

#### T5.1 单测

ModulePage.vue 已有现成测试集（`frontend/apps/admin-app/src/app-shell.test.js`），但不涉及表单字段细节。本 PR 不强制加单测，但要保证：

- `pnpm test:unit` 全部通过（无回归）
- `pnpm build` 通过

#### T5.2 e2e

`frontend/apps/admin-app/tests/e2e/` 下现有测试：

- 创建课程流程：用例里要补传新字段（可选传，不强制）
- 验证不会因为后端返回新字段而前端解析失败

PR 2 阶段建议**不动 e2e**（保持 backward-compat），PR 3 一起补。

#### T5.3 联调走查

- 创建课程时填全部六个字段 → 数据库读取应该和填的一致
- 列表页应展示分类 chip
- 详情页概览应展示完整字段
- 字段为空的老课程 → 详情页不该有空白卡

### T6. 后端补强（可选）

PR 1 后端已经完整支持。但建议本 PR 顺手补一个：

- `GET /api/v1/courses/categories`（去重的 category 列表）

理由：admin-app 的 `el-autocomplete` 不该在前端做 `[...new Set]`，应该由后端聚合。**优先级低**，可以放 PR 3。

## 3. 不在范围内

- 章节 / 课时表（PR 3）
- 学习进度表（PR 3）
- 评分评价（三期）
- 学生自助加入课程（长期计划）
- 教师档案 bio / avatar（独立 PR）

## 4. 时序与依赖

```
T1 ─┬─► T5（联调验收）
T2 ─┤
T3 ─┤
T4 ─┘
```

四个 task 互相独立，可以并行做。建议顺序：T1 → T2 → T3 → T4 → T5。

## 5. 提交策略

- 每个 task 一个独立 commit，便于 review 拆分
- 提交前缀：`feat(admin-course)` / `style(admin-course)` / `chore(admin-course)`
- PR 标题：`feat(admin-course): 课程元数据字段扩展（分类/标签/目标/人群/难度/学时）`

## 6. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| ModulePage.vue 已经很大（600+ 行），加表单字段会超重 | 第一版不抽取组件；如代码量超过 800 行再拆 CourseFormDrawer.vue |
| 老课程没有这些字段 → 详情页空白 | 字段为空时整段隐藏，不渲染空 placeholder |
| `el-input-tag` 不存在于 Element Plus 标准库 | 用 `el-select` `multiple + filterable + allow-create` 替代，效果一致 |
| `objectives / audience` 上限校验前端漏了 | 加按钮 disabled + length 提示，与后端 `@Size` 双重校验 |
| 路由 `course-chapters` 默认权限 `course:read` 学生也能进 | meta 加 `status: 'preview'`，路由守卫显式拦截 |

## 7. 完工定义（DoD）

- [ ] admin-app 创建课程时可填六个字段，提交后数据库可见
- [ ] admin-app 编辑课程时表单回显已有字段
- [ ] admin-app 课程列表卡片显示分类 + 难度
- [ ] admin-app 课程详情页"概览"tab 展示完整字段（空字段隐藏）
- [ ] 路由 `/app/courses/:courseId/chapters` 可访问，显示"功能即将开放"占位页
- [ ] `pnpm build`、`pnpm test:unit` 通过
- [ ] 浏览器 DevTools 中所有请求前缀 `/api/v1/courses/...`，无 mock 残留
- [ ] PR 描述贴：表单截图、列表截图、详情截图、空字段降级截图

## 8. 估时（参考）

- T1 表单扩展：3~4 小时（含组件选型 + 联调）
- T2 详情展示：1~2 小时
- T3 列表卡片：1 小时
- T4 路由占位：30 分钟
- T5 联调走查：1 小时
- 总计：约 6~9 小时

## 9. 后续衔接

PR 2 完成后，下一步是 **PR 3：完整 LMS 章节/课时/进度**。届时：

- 创建 `course_chapters` / `course_lessons` / `course_lesson_progress` 三表
- admin-app `course-chapters` 路由从占位页换成真实管理界面
- 学生端 `CourseLearn.vue` 接入真实章节内容
- 视频播放器 + 进度心跳上报

详细设计见 `docs/2026-05-19-course-info-architecture-plan.md` §6。
