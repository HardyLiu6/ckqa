# admin-app

`frontend/apps/admin-app/` 是 CKQA 管理员端/教师端共用平台的前端控制台，用于承载课程资料、知识库构建、问答运维、用户权限和系统审计页面。

## 当前状态

- 技术栈：Vue 3 + Vite + Vue Router + Axios + Element Plus + Pinia + Sass + Playwright
- 包管理：pnpm
- 当前代码形态：已具备运维台壳层、主题系统、JWT 登录、路由守卫、请求层、工作台、系统健康页、课程封面上传、课程资料上传、课程/资料/知识库 live 页面、资料详情解析进度、解析结果详情、构建向导、QA 冒烟验证、问答运维列表和统一错误页
- 当前角色：管理员/教师共用控制台前端；核心业务页走 Java `/api/v1`，正式业务代码不直接访问 GraphRAG Python `/v1`

如果你正在寻找当前系统的主入口，请优先回到仓库根目录和两个 Python 模块：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

本次已完成的结构设计、视觉重构、真实数据接入与样式基座重构文档已归档，可按需回看：

- [../../../docs/admin-teacher-frontend-structure.md](../../../docs/admin-teacher-frontend-structure.md)
- [../../../docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md](../../../docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md)
- [../../../docs/superpowers/archive/plans/2026-04-26-admin-app-ui-redesign-implementation-plan.md](../../../docs/superpowers/archive/plans/2026-04-26-admin-app-ui-redesign-implementation-plan.md)
- [../../../docs/superpowers/archive/specs/2026-04-28-admin-app-live-api-integration-design.md](../../../docs/superpowers/archive/specs/2026-04-28-admin-app-live-api-integration-design.md)
- [../../../docs/superpowers/archive/plans/2026-04-28-admin-app-live-api-integration-implementation-plan.md](../../../docs/superpowers/archive/plans/2026-04-28-admin-app-live-api-integration-implementation-plan.md)
- [../../../docs/superpowers/archive/specs/2026-04-29-element-plus-frontend-style-design.md](../../../docs/superpowers/archive/specs/2026-04-29-element-plus-frontend-style-design.md)
- [../../../docs/superpowers/archive/plans/2026-04-29-element-plus-frontend-style-impl.md](../../../docs/superpowers/archive/plans/2026-04-29-element-plus-frontend-style-impl.md)
- [../../../docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md](../../../docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md)
- [../../../docs/superpowers/archive/plans/2026-05-03-admin-app-kb-build-wizard-redesign-implementation-plan.md](../../../docs/superpowers/archive/plans/2026-05-03-admin-app-kb-build-wizard-redesign-implementation-plan.md)

## 目录与入口

| 文件 | 作用 |
| --- | --- |
| `src/App.vue` | 根据路由元信息挂载布局 |
| `src/router/routes.js` | 页面清单、权限、状态和一级导航配置 |
| `src/router/index.js` | Vue Router、布局映射和路由守卫 |
| `src/layouts/` | `AuthLayout`、`ConsoleLayout`、`DetailLayout`、`WorkflowLayout` |
| `src/layouts/console-breadcrumb-model.js` | 控制台面包屑模型，课程子页面会保留课程详情父级 |
| `src/components/shell/` | 顶栏、侧边导航、主题控件和导航模型 |
| `src/components/common/` | 状态徽标、数据来源、表格壳、工作流步骤、指标和诊断面板 |
| `src/components/system/` | 系统健康矩阵 |
| `src/stores/auth.js` | JWT 会话持久化、当前用户、角色和权限判断 |
| `src/stores/theme.js` | `light / dark / auto` 主题模式和固定主题色 |
| `src/stores/pinia.js` | admin-app 共享 Pinia 实例 |
| `src/axios/index.js` | Axios 实例、认证头注入和错误收敛 |
| `src/api/` | Java `/api/v1` 认证与业务 API 边界、ApiResponse 解包 |
| `src/api/qa-operations.js` | 问答运维日志、聚合统计、导出和来源复核接口封装 |
| `src/views/pages/module-loaders.js` | 按路由加载 live 数据并映射页面状态 |
| `src/views/pages/material-file-model.js` | 课程资料上传文件校验，当前与后端保持单 PDF 200MB 上限 |
| `src/views/qa/QaOperationsListView.vue` | 问答运维列表页，筛选自动生效，概览统计来自后端全库聚合 |
| `e2e/local-operation-errors.spec.js` | Playwright 浏览器级故障注入验收 |
| `src/views/` | 登录、工作台、系统健康、通用页面、未开放状态页和统一错误页 |
| `src/views/status/UnifiedErrorView.vue` | 403 / 404 / 500 统一错误页 |
| `src/main.js` | Vue 应用挂载入口 |
| `src/styles/index.scss` | 唯一全局样式入口，聚合 token、base、Element Plus 覆盖和组件样式 |

## 常用命令

```bash
cd frontend/apps/admin-app
pnpm install
pnpm dev:local
pnpm test
pnpm test:e2e
pnpm build
pnpm dev
pnpm preview
```

本地联调时通常这样启动：

```bash
cd frontend/apps/admin-app
pnpm dev:local
```

浏览器入口：

```text
http://127.0.0.1:5173/app/health
http://127.0.0.1:5173/app/courses
http://127.0.0.1:5173/app/knowledge-bases
```

## 环境变量

开发态默认从浏览器请求同源 `/api/v1`，再由 Vite 代理到 Java 后端，避免远程开发或端口转发场景下浏览器把 `127.0.0.1:8080` 解析成本机地址。

```bash
VITE_API_BASE_URL=/api/v1
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
VITE_API_TIMEOUT=15000
```

如果前端静态产物部署在独立域名下，也可以把 `VITE_API_BASE_URL` 显式设置为浏览器可访问的完整 Java 后端地址。

正式业务代码不应直接请求 GraphRAG Python `/v1`。GraphRAG Python 服务仍由 Java 后端编排。

本地 JWT 登录测试账号来自后端迁移 `sql/migrations/20260506_jwt_auth_credentials.sql`：

| 端 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员端 | `admin.heqh` | `Ckqa@2026` |
| 教师端 | `teacher.zhangwb` | `Ckqa@2026` |

## 已落地的骨架能力

1. 6 个一级导航：工作台、课程与资料、知识库构建、问答运维、用户与权限、系统与审计。
2. `/app/system` 作为系统与审计聚合入口，当前默认跳转 `/app/health`。
3. 路由元信息包含 `permissions`、`status`、`routeState`、`resource` 和 `scope`。
4. 未登录访问业务页跳转 `/login`，无权限跳转 `/403`。
5. 登录页接入 Java `/api/v1/auth/admin/login`，管理员和教师账号登录后以 JWT 访问业务接口。
6. 未开放页面统一显示模块、规划状态和恢复入口，避免空白路由。
7. 403、404、500 使用统一错误页；403 会展示当前身份、数据范围和缺失权限提示。
8. 系统健康页调用 Java `/api/v1/system/health`，并识别 `graphrag-build-runs-root` / `graphrag-ready`；更重的共享输出检查由后端 `/api/v1/system/readiness` 承担。
9. 课程列表、课程详情、资料详情、解析结果详情、知识库列表、知识库详情、索引运行详情和构建向导已通过 loader 接入 Java `/api/v1`；课程创建和详情页支持通过 Java 上传课程封面。
10. 课程资料上传走 Java `/api/v1/courses/{courseId}/materials`，当前只接受 PDF，单文件默认上限 200MB，并在选择文件时提前提示。
11. 知识库构建向导以 `buildRunId` 为运行态来源：URL 有 `buildRunId` 时加载对应 build run；没有时保持草稿选择，首次用户确认动作才创建 build run 并写回 query。
12. 构建向导动作走 build-run API：资料选择、解析检查、图谱输入同步、Prompt 确认、索引构建和 QA 冒烟验证都由 Java `/api/v1/knowledge-base-build-runs/*` 编排。
13. 资料详情会根据解析状态展示独立的“解析进度”区块；若后端返回 `parseProgress` 百分比则直接显示真实进度，没有百分比时按阶段状态给出稳妥兜底文案。
14. 解析结果详情页当前走 Java `/api/v1/pdf-files/{id}/results` live 数据，只做只读产物列表与下载/查看入口，不在前端内置复杂 JSON 编辑器。
15. 资料解析、GraphRAG 导出、索引构建和 QA 冒烟验证使用局部操作反馈，不再把所有错误挤到顶部泛化状态。
16. 工作台已使用指标块、生产链路轨道、近期任务和局部深色异常摘要。
17. 通用业务页通过 `DataSourceChip` 标记 `mock` / `live` 数据来源，table / overview / workflow 三类模板由 `module-content.js` 配置驱动。
18. 当前样式基座已经完成 Element Plus + Pinia + Sass 迁移，并通过 `src/styles/index.scss` 统一加载 token、Element Plus 覆盖与组件样式。
19. 主题系统支持 `light / dark / auto` 和固定主题色色板，偏好存入 `localStorage`。
20. 问答运维列表走 Java `/api/v1/qa-operations/logs` 与 `/qa-operations/logs/summary`；筛选条件自动生效，概览卡片按后端全库聚合统计，不再基于当前页估算。
21. Playwright E2E 会自动启动 Vite，并通过 mock `/api/v1` 注入资料、索引和 QA 失败场景验证局部反馈。

## 当前限制

1. 课程资料上传 v1 仅支持 PDF；如果后端调整 `COURSE_MATERIAL_MAX_FILE_SIZE_BYTES` 或 multipart 限制，需要同步 `src/views/pages/material-file-model.js`。
2. 授权审计日志、用户详情和完整检索日志详情仍是“未开放”或后续补齐路由；问答运维列表本身已经是 live 数据页。
3. 403 页面已支持展示缺失权限；若需要精确权限文案，需要路由守卫跳转时附带 `required` 查询参数。
4. 细粒度 RBAC 编辑和全量审计页面仍待后续后端契约确认。
