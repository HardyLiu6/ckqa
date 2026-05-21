# student-app

`frontend/apps/student-app/` 是 CKQA 仓库里的学员端前端原型。它已经包含首页、课程、问答、知识图谱等页面骨架，以及 Vue Router、Pinia、Element Plus 等基础设施；账号登录注册、课程只读入口、问答会话/任务事件流、学习记忆、模式推荐和知识图谱浏览已开始接入 Java 后端 `/api/v1`。

如果你当前目标是跑通 CKQA 真实问答流程，请优先查看：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

## 当前状态

- 技术栈：Vue 3、Vite、Element Plus、Pinia、Vue Router、Sass
- 动效与体验依赖：AOS、GSAP、Lenis
- 当前角色：学员端页面与交互原型，不是完整正式业务前端
- 当前数据状态：登录注册、课程列表/详情只读入口、问答会话、任务事件流/轮询兜底、学习记忆、无显式课程时的课程画像路由、模式推荐和知识图谱浏览已走 Java `/api/v1`；社区、学习分析和部分用户中心页面仍是占位或本地原型
- 当前工程形态：已纳入 CKQA 根仓库直接管理，依赖锁文件以 `pnpm-lock.yaml` 为准，生成依赖和构建产物继续通过本目录 `.gitignore` 忽略

这意味着它更适合做：

- 学员端信息架构和页面流程演示
- Vue 3 + Element Plus 技术方案验证
- 后续继续补齐课程、问答、知识图谱和学习分析的正式学生端体验

暂时不适合假设：

- 全部页面都已经完成正式后端闭环
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
| `src/api/auth.js` | 学生登录、注册、邮箱验证码、当前用户与头像上传接口 |
| `src/api/courses.js` | 课程列表、课程详情、课程资料、知识库、章节与学习进度接口封装 |
| `src/api/qa.js` | 问答会话、消息、任务详情、任务事件流、模式推荐、混合检索预热、学习记忆与反馈接口封装 |
| `src/api/graph.js` | 知识图谱健康检查、知识库选择、总览、实体邻域和实体详情接口封装 |
| `src/views/qa/` | 问答页、历史页、详情页原型 |
| `src/views/qa/qa-route-query-model.js` | 规范化 `courseId/sessionId/mode/topic` 路由 query，支持刷新与侧栏跳转恢复上下文 |
| `src/views/course/` | 课程列表、详情、学习页、我的课程原型 |
| `src/views/knowledge/KnowledgeGraph.vue` | 知识图谱浏览页，按课程选择可用知识库并跳转问答时携带课程上下文 |
| `src/components/NavHeader.vue` | 顶部导航组件 |
| `src/layouts/moduleSideNavLoaders.js` | 模块副导航懒加载与顶栏预加载共用的 loader |
| `src/layouts/route-view-key.js` | 控制模块视图 key，避免 query-only 变化导致整页重挂 |
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

同时还预留了 `community`、`analysis`、`user`、`auth`、`error` 等路由段。登录、注册、问答和知识图谱主浏览已经开放；其余未实现页面统一收口到显式状态页，会直接提示“未开放 / 建设中”，避免演示时误以为页面损坏或接口异常。

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
- 真实问答页优先使用 Java `/api/v1/qa-sessions/{sessionId}/tasks/{taskId}/events` SSE 任务事件流；后端可桥接 Python GraphRAG 原生 streaming `delta`，不可用时自动回退到 task 轮询或最终答案分段
- 问答页在没有 URL、手动选择或历史会话 `courseId` 时，会先调用 Java `/api/v1/course-routing/recommend`；高置信 `matched` 自动选课，分数够但分差不足的 `needs_confirmation` 展示候选课程，确认后保留当前 QA mode 并继续发送原问题；明显非课程问题会得到 `no_match`，前端提示先选择课程，不直接发送正式问答
- 问答页会把 `courseId`、`sessionId`、`mode`、`topic` 写入路由 query；从图谱节点、问答侧栏、刷新页面返回时可以恢复课程与会话上下文
- 顶栏会在首屏稳定后预加载课程、问答、知识图谱模块和副导航，G6 图谱画布也改为进入图谱页后再延迟加载
- 未实现路由现在会落到统一状态页，不再以空白页或注释组件的形式存在
- 已形成面向学生端问答的 Java `/api/v1` 契约；浏览器不直连 `graphrag_pipeline` Python `/v1`

## 使用时要注意

- `package.json` 当前包名已经调整为 `student-app`，与目录语义保持一致
- 当前目录已由 CKQA 根仓库直接管理，不再作为独立嵌套 Git 仓库使用
- `node_modules/` 是生成依赖，不应当作文档、审计或提交的主对象
- 如果你要把它正式接入 CKQA，浏览器正式业务边界应统一走 `backend/ckqa-back` 的 Java `/api/v1`，不要把 student-app 直接接到 `graphrag_pipeline` 的 Python `/v1`

## 后续接入前建议先补齐什么

1. 以 `docs/student-backend-graphrag-api-contract.md` 为准，继续补齐课程学习、用户中心、社区和学习分析接口契约。
2. 把仍依赖本地 store 或静态内容的页面逐步迁移到 `src/api/*` 封装，并补齐失败恢复策略。
3. 为课程、问答和知识图谱的真实接口补充更完整的浏览器级 Mock/E2E 验收。
4. 继续优化构建体积，重点关注字体、Element Plus 与图谱相关 chunk。

当前联调契约见 [../../../docs/student-backend-graphrag-api-contract.md](../../../docs/student-backend-graphrag-api-contract.md)。
