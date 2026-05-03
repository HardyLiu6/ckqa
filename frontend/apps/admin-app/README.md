# admin-app

`frontend/apps/admin-app/` 是 CKQA 管理员端/教师端共用平台的前端控制台，用于承载课程资料、知识库构建、问答运维、用户权限和系统审计页面。

## 当前状态

- 技术栈：Vue 3 + Vite + Vue Router + Axios + Element Plus + Pinia + Sass + Playwright
- 包管理：pnpm
- 当前代码形态：已具备运维台壳层、主题系统、路由守卫、开发态身份切换、请求层、工作台、系统健康页、课程/资料/知识库 live 页面、构建向导和 QA 冒烟验证
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

## 目录与入口

| 文件 | 作用 |
| --- | --- |
| `src/App.vue` | 根据路由元信息挂载布局 |
| `src/router/routes.js` | 页面清单、权限、状态和一级导航配置 |
| `src/router/index.js` | Vue Router、布局映射和路由守卫 |
| `src/layouts/` | `AuthLayout`、`ConsoleLayout`、`DetailLayout`、`WorkflowLayout` |
| `src/components/shell/` | 顶栏、侧边导航、主题控件和导航模型 |
| `src/components/common/` | 状态徽标、数据来源、表格壳、工作流步骤、指标和诊断面板 |
| `src/components/system/` | 系统健康矩阵 |
| `src/stores/auth.js` | 开发态认证状态、角色和权限判断 |
| `src/stores/theme.js` | `light / dark / auto` 主题模式和固定主题色 |
| `src/stores/pinia.js` | admin-app 共享 Pinia 实例 |
| `src/axios/index.js` | Axios 实例、认证头注入和错误收敛 |
| `src/api/` | Java `/api/v1` 业务 API 边界和 ApiResponse 解包 |
| `src/views/pages/module-loaders.js` | 按路由加载 live 数据并映射页面状态 |
| `e2e/local-operation-errors.spec.js` | Playwright 浏览器级故障注入验收 |
| `src/views/` | 登录、工作台、系统健康、通用页面和状态页 |
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

## 已落地的骨架能力

1. 6 个一级导航：工作台、课程与资料、知识库构建、问答运维、用户与权限、系统与审计。
2. `/app/system` 作为系统与审计聚合入口，当前默认跳转 `/app/health`。
3. 路由元信息包含 `permissions`、`status`、`routeState`、`resource` 和 `scope`。
4. 未登录访问业务页跳转 `/login`，无权限跳转 `/403`。
5. 登录页支持管理员/教师开发态身份切换，并明确标记“当前为开发态身份切换，正式登录接口待接入”。
6. 未开放页面统一显示模块、规划状态和恢复入口，避免空白路由。
7. 系统健康页只调用 Java `/api/v1/system/health`，并区分 `reachable` 与 `ready`。
8. 课程列表、课程详情、资料详情、知识库列表、知识库详情、索引运行详情和构建向导已通过 loader 接入 Java `/api/v1`。
9. 资料解析、GraphRAG 导出、索引构建和 QA 冒烟验证使用局部操作反馈，不再把所有错误挤到顶部泛化状态。
10. 工作台已使用指标块、生产链路轨道、近期任务和局部深色异常摘要。
11. 通用业务页通过 `DataSourceChip` 标记 `mock` / `live` 数据来源，table / overview / workflow 三类模板由 `module-content.js` 配置驱动。
12. 当前样式基座已经完成 Element Plus + Pinia + Sass 迁移，并通过 `src/styles/index.scss` 统一加载 token、Element Plus 覆盖与组件样式。
13. 主题系统支持 `light / dark / auto` 和固定主题色色板，偏好存入 `localStorage`。
14. Playwright E2E 会自动启动 Vite，并通过 mock `/api/v1` 注入资料、索引和 QA 失败场景验证局部反馈。

## 当前限制

1. 登录仍是开发态 mock 身份，正式登录接口待后端确认。
2. `material:upload` 仅作为预留权限点，Java 上传链路确认前不提供上传 UI。
3. 授权审计日志、索引运行列表、检索日志详情和用户详情目前是“未开放”路由。
4. 403 页面当前使用兜底权限说明；若需要精确缺失权限，需要路由守卫跳转时附带 `required` 查询参数。
5. 正式登录、细粒度 RBAC 编辑和全量审计页面仍待后续后端契约确认。
