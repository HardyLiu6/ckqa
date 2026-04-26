# admin-app

`frontend/apps/admin-app/` 是 CKQA 管理员端/教师端共用平台的前端骨架，用于承载课程资料、知识库构建、问答运维、用户权限和系统审计页面。

## 当前状态

- 技术栈：Vue 3 + Vite + Vue Router + Axios
- 包管理：pnpm
- 当前代码形态：已具备后台布局、路由守卫、开发态身份切换、请求层和 MVP 页面骨架
- 当前角色：管理端结构骨架；业务数据仍等待 Java `/api/v1` 聚合接口逐步接入

如果你正在寻找当前系统的主入口，请优先回到仓库根目录和两个 Python 模块：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

## 目录与入口

| 文件 | 作用 |
| --- | --- |
| `src/App.vue` | 根据路由元信息挂载布局 |
| `src/router/routes.js` | 页面清单、权限、状态和一级导航配置 |
| `src/router/index.js` | Vue Router、布局映射和路由守卫 |
| `src/layouts/` | `AuthLayout`、`ConsoleLayout`、`DetailLayout`、`WorkflowLayout` |
| `src/stores/auth.js` | 开发态认证状态、角色和权限判断 |
| `src/axios/index.js` | Axios 实例、认证头注入和错误收敛 |
| `src/views/` | 登录、工作台、系统健康、通用页面和状态页 |
| `src/main.js` | Vue 应用挂载入口 |
| `src/style.css` | 全局样式 |

## 常用命令

```bash
cd frontend/apps/admin-app
pnpm install
pnpm dev
pnpm test
pnpm build
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
5. 登录页支持管理员/教师开发态身份切换。
6. 未开放页面统一显示“未开放”，避免空白路由。
7. 系统健康页只调用 Java `/api/v1/system/health`。

## 当前限制

1. 登录仍是开发态 mock 身份，正式登录接口待后端确认。
2. 多数业务页面是结构骨架，真实列表、表单和详情数据仍需后续接入。
3. `material:upload` 仅作为预留权限点，Java 上传链路确认前不提供上传 UI。
4. 授权审计日志、索引运行列表、检索日志详情和用户详情目前是“未开放”路由。
