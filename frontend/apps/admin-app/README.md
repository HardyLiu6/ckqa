# admin-app

`frontend/apps/admin-app/` 是 CKQA 管理员端/教师端共用平台的前端骨架，用于承载课程资料、知识库构建、问答运维、用户权限和系统审计页面。

## 当前状态

- 技术栈：Vue 3 + Vite + Vue Router + Axios
- 包管理：pnpm
- 当前代码形态：已具备运维台壳层、主题系统、路由守卫、开发态身份切换、请求层、工作台、系统健康页和通用业务页模板
- 当前角色：管理员/教师共用控制台前端；系统健康页已走 Java `/api/v1`，多数业务页仍以示例数据等待后续接口接入

如果你正在寻找当前系统的主入口，请优先回到仓库根目录和两个 Python 模块：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

本次已完成的结构设计与视觉重构稿已归档，可按需回看：

- [../../../docs/admin-teacher-frontend-structure.md](../../../docs/admin-teacher-frontend-structure.md)
- [../../../docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md](../../../docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md)
- [../../../docs/superpowers/archive/plans/2026-04-26-admin-app-ui-redesign-implementation-plan.md](../../../docs/superpowers/archive/plans/2026-04-26-admin-app-ui-redesign-implementation-plan.md)

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
| `src/axios/index.js` | Axios 实例、认证头注入和错误收敛 |
| `src/views/` | 登录、工作台、系统健康、通用页面和状态页 |
| `src/main.js` | Vue 应用挂载入口 |
| `src/style.css` | 样式入口，导入 `styles/tokens.css`、`base.css`、`components.css` |

## 常用命令

```bash
cd frontend/apps/admin-app
pnpm install
pnpm test
pnpm build
pnpm dev
pnpm preview
```

## 环境变量

默认请求 Java 后端：

```bash
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
VITE_API_TIMEOUT=15000
```

正式业务代码不应直接请求 GraphRAG Python `/v1`。GraphRAG Python 服务仍由 Java 后端编排。

## 已落地的骨架能力

1. 6 个一级导航：工作台、课程与资料、知识库构建、问答运维、用户与权限、系统与审计。
2. `/app/system` 作为系统与审计聚合入口，当前默认跳转 `/app/health`。
3. 路由元信息包含 `permissions`、`status`、`routeState`、`resource` 和 `scope`。
4. 未登录访问业务页跳转 `/login`，无权限跳转 `/403`。
5. 登录页支持管理员/教师开发态身份切换，并明确标记“当前为开发态身份切换，正式登录接口待接入”。
6. 未开放页面统一显示模块、规划状态和恢复入口，避免空白路由。
7. 系统健康页只调用 Java `/api/v1/system/health`，并区分 `reachable` 与 `ready`。
8. 工作台已使用指标块、生产链路轨道、近期任务和局部深色异常摘要。
9. 通用业务页通过 `DataSourceChip` 标记 `mock` / `live` 数据来源，table / overview / workflow 三类模板由 `module-content.js` 配置驱动。
10. 主题系统支持 `light / dark / auto` 和固定主题色色板，偏好存入 `localStorage`。

## 当前限制

1. 登录仍是开发态 mock 身份，正式登录接口待后端确认。
2. 多数业务页面仍为示例数据，真实列表、表单和详情数据仍需后续接入 Java `/api/v1` 聚合接口。
3. `material:upload` 仅作为预留权限点，Java 上传链路确认前不提供上传 UI。
4. 授权审计日志、索引运行列表、检索日志详情和用户详情目前是“未开放”路由。
5. 403 页面当前使用兜底权限说明；若需要精确缺失权限，需要路由守卫跳转时附带 `required` 查询参数。
