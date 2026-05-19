# student-app

`frontend/apps/student-app/` 是 CKQA 仓库里当前更完整的学员端前端原型。它已经包含首页、课程、问答等页面骨架，以及 Vue Router、Pinia、Element Plus 等基础设施；账号登录注册已接入 Java 后端 `/api/v1/auth/student/*`，课程与问答等业务页仍在逐步接入真实接口。

如果你当前目标是跑通 CKQA 真实问答流程，请优先查看：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

## 当前状态

- 技术栈：Vue 3、Vite、Element Plus、Pinia、Vue Router、Sass
- 动效与体验依赖：AOS、GSAP、Lenis
- 当前角色：学员端页面与交互原型，不是正式业务前端
- 当前数据状态：登录注册走 Java `/api/v1`，其余页面仍以本地 store 和静态内容为主；后续联调可直接复用 `src/axios/index.js`
- 当前工程形态：已纳入 CKQA 根仓库直接管理，依赖锁文件以 `pnpm-lock.yaml` 为准，生成依赖和构建产物继续通过本目录 `.gitignore` 忽略

这意味着它更适合做：

- 学员端信息架构和页面流程演示
- Vue 3 + Element Plus 技术方案验证
- 后续接入问答、课程、知识图谱界面的视觉与交互基础

暂时不适合假设：

- 除认证外已经存在稳定的后端接口层
- 用户中心、社区、知识图谱等页面已经全部可用
- 可以直接把它当成 CKQA 当前正式前端入口

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `src/main.js` | Vue 应用挂载入口，注册 Pinia 与 Vue Router |
| `src/router/index.js` | 路由总入口，定义落地页、首页、问答、课程及大量预留路由 |
| `src/App.vue` | 应用壳层，承载 `RouterView` 与全局 loading |
| `src/views/layout/index.vue` | 落地页 / 介绍页原型 |
| `src/views/auth/AuthAccess.vue` | 学生登录 / 注册 JWT 页面 |
| `src/views/index.vue` | 登录后首页原型，展示热门提问、课程与知识图谱卡片 |
| `src/views/qa/` | 问答页、历史页、详情页原型 |
| `src/views/course/` | 课程列表、详情、学习页、我的课程原型 |
| `src/components/NavHeader.vue` | 顶部导航组件 |
| `src/stores/` | Pinia store，包含学生 JWT 会话与本地页面状态 |
| `vite.config.js` | Vite 配置，默认监听 `0.0.0.0:5174`，并将 `/api/v1` 代理到 Java 后端 |
| `jsconfig.json` | `@/` 路径别名配置 |

## 路由现状

当前代码里已经配置了比较完整的路由树，但真正有对应视图文件的主要是：

- `/`：介绍页 / 落地页
- `/home`：首页
- `/qa/ask`
- `/qa/history`
- `/qa/detail/:id`
- `/course/list`
- `/course/detail/:id`
- `/course/learn/:id`
- `/course/my`

同时还预留了 `knowledge`、`community`、`analysis`、`user`、`auth`、`error` 等路由段。登录和注册已经开放，其余未实现页面统一收口到显式状态页，会直接提示“未开放 / 建设中”，避免演示时误以为页面损坏或接口异常。

## 环境准备

建议环境：

- Node `^20.19.0 || >=22.12.0`
- 优先使用 `pnpm`，因为目录内已有 `pnpm-lock.yaml`

```bash
cd frontend/apps/student-app
pnpm install
cp .env.example .env
```

如果你临时使用 `npm`，脚本本身也能执行，但默认仍建议跟随现有锁文件使用 `pnpm`。

## 环境变量约定

学生端当前约定三个最小运行时变量：

- `VITE_API_BASE_URL`：请求基础地址。正式联调时应指向 Java 后端 `/api/v1`，例如 `http://127.0.0.1:8080/api/v1`；如果使用反向代理，建议代理前缀也保持为 `/api/v1`
- `VITE_API_PROXY_TARGET`：开发服务器代理目标，默认 `http://127.0.0.1:8080`。当 `VITE_API_BASE_URL=/api/v1` 时，浏览器请求同源 `/api/v1/*` 会由 Vite 转发到这个 Java 后端地址
- `VITE_API_TIMEOUT`：请求超时时间，单位毫秒，默认 `10000`

对应示例见 `.env.example`。当前 `src/axios/index.js` 会自动读取它们，并导出默认实例与 `get` / `post` / `put` / `patch` / `del` 这些最常用方法。

本地 JWT 登录测试账号来自后端迁移 `sql/migrations/20260506_jwt_auth_credentials.sql`：

| 用户名 | 密码 |
| --- | --- |
| `student.zhouzh` | `Ckqa@2026` |

## 常用命令

```bash
cd frontend/apps/student-app
pnpm dev:local
pnpm build
pnpm preview
pnpm format
node --test tests/*.test.js
```

本地联调推荐端口：

```text
http://127.0.0.1:5174
```

`pnpm dev` 与 `pnpm dev:local` 都会避开 Java 后端默认 `8080` 端口；本地调试推荐使用 `pnpm dev:local`。

## 当前实现特点

- 页面视觉层已经明显超出 Vite 默认模板，适合继续往正式学员端演进
- 路由、菜单、页面结构已经初步成型
- Pinia user store 已保存 JWT 会话并向 Axios 注入 `Authorization` 和 `X-CKQA-User-Code`
- 未实现路由现在会落到统一状态页，不再以空白页或注释组件的形式存在
- 尚未形成稳定 API 层，也没有和 `graphrag_pipeline` / `backend/ckqa-back` 建立正式契约

## 使用时要注意

- `package.json` 当前包名已经调整为 `student-app`，与目录语义保持一致
- 当前目录已由 CKQA 根仓库直接管理，不再作为独立嵌套 Git 仓库使用
- `node_modules/` 是生成依赖，不应当作文档、审计或提交的主对象
- 如果你要把它正式接入 CKQA，浏览器正式业务边界应统一走 `backend/ckqa-back` 的 Java `/api/v1`，不要把 student-app 直接接到 `graphrag_pipeline` 的 Python `/v1`

## 后续接入前建议先补齐什么

1. 以 `docs/student-backend-graphrag-api-contract.md` 为准，按 Java `/api/v1` 收口问答、课程和用户侧接口契约。
2. 在组件里逐步把示例数据切换到 `src/axios/index.js` 导出的请求方法。
3. 按已启用的登录态守卫，为课程、问答和知识图谱页面补齐真实接口数据。
4. 在真正联调前补齐 API 契约、Mock 数据和请求失败恢复策略。

当前联调契约见 [../../../docs/student-backend-graphrag-api-contract.md](../../../docs/student-backend-graphrag-api-contract.md)。
