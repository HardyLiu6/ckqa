# Superpowers 归档索引

本目录用于存放已经执行完成、当前不再作为活跃工作输入的 `design` / `plan` 文档。

## 当前归档

- `specs/2026-04-22-ckqa-back-async-qa-task-design.md`
  - Java `/api/v1` 异步 QA 任务化设计稿，已落地为 `qa_retrieval_logs + /v1/query-tasks` 编排闭环。
- `plans/2026-04-22-ckqa-back-async-qa-task-implementation.md`
  - 异步 QA 实施计划，已完成并合并到 `main`。
- `plans/2026-04-23-course-materials-material-objects-impl.md`
  - `course_materials + material_objects` 课程资料模型实施计划，已完成 schema、CLI、Java 编排与兼容收口。
- `specs/2026-04-26-admin-app-ui-redesign-design.md`
  - 管理员端前端美化重构设计稿，已完成落地。
- `plans/2026-04-26-admin-app-ui-redesign-implementation-plan.md`
  - 管理员端前端美化重构实施计划，已执行完成。
- `specs/2026-04-28-admin-app-live-api-integration-design.md`
  - 管理端真实数据接入设计稿，已完成并合并到 `main`。
- `plans/2026-04-28-admin-app-live-api-integration-implementation-plan.md`
  - 管理端真实数据接入实施计划，已完成课程、资料、知识库、QA 冒烟验证和 Playwright 故障注入验收。
- `specs/2026-04-29-element-plus-frontend-style-design.md`
  - 管理端 Element Plus + Pinia + Sass 样式重构设计稿，已完成并落到当前 admin-app 样式基座。
- `plans/2026-04-29-element-plus-frontend-style-impl.md`
  - 管理端 Element Plus + Pinia + Sass 样式重构实施计划，已完成依赖、Pinia、SCSS 分层与组件样式迁移。
- `specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md`
  - 管理端知识库构建向导主舞台式重设计稿，已落地为六步资料选择、解析、导出、提示词确认、索引和 QA 验证流程。
- `plans/2026-05-03-admin-app-kb-build-wizard-redesign-implementation-plan.md`
  - 知识库构建向导实施计划，已完成 URL 选择态、多资料 loader、顶部进度轨、步骤子组件、主操作和浏览器故障注入验收。

## 使用约定

1. 活跃设计稿与实施计划默认保留在 `docs/superpowers/specs/` 和 `docs/superpowers/plans/`。
2. 当对应工作已经完成、且仓库内已有更贴近当前实现的入口文档时，应移动到本归档目录。
3. 归档文档保留历史内容，但可以在文件顶部补充简短归档说明和当前活跃入口链接。
