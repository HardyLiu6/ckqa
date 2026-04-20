# student-app

`frontend/apps/student-app/` 是 CKQA 仓库里当前更完整的学员端前端原型。它已经包含首页、课程、问答等页面骨架，以及 Vue Router、Pinia、Element Plus 等基础设施，但目前仍然是独立原型，并没有接入仓库主链路。

如果你当前目标是跑通 CKQA 真实问答流程，请优先查看：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

## 当前状态

- 技术栈：Vue 3、Vite、Element Plus、Pinia、Vue Router、Sass
- 动效与体验依赖：AOS、GSAP、Lenis
- 当前角色：学员端页面与交互原型，不是正式业务前端
- 当前数据状态：以本地 store 和静态内容为主，`src/axios/index.js` 目前为空
- 当前工程形态：已纳入 CKQA 根仓库直接管理，依赖锁文件以 `pnpm-lock.yaml` 为准，生成依赖和构建产物继续通过本目录 `.gitignore` 忽略

这意味着它更适合做：

- 学员端信息架构和页面流程演示
- Vue 3 + Element Plus 技术方案验证
- 后续接入问答、课程、知识图谱界面的视觉与交互基础

暂时不适合假设：

- 已经存在稳定的后端接口层
- 登录、用户中心、社区、知识图谱等页面已经全部可用
- 可以直接把它当成 CKQA 当前正式前端入口

## 真实入口与关键文件

| 文件 | 作用 |
| --- | --- |
| `src/main.js` | Vue 应用挂载入口，注册 Pinia 与 Vue Router |
| `src/router/index.js` | 路由总入口，定义落地页、首页、问答、课程及大量预留路由 |
| `src/App.vue` | 应用壳层，承载 `RouterView` 与全局 loading |
| `src/views/layout/index.vue` | 落地页 / 介绍页原型 |
| `src/views/index.vue` | 登录后首页原型，展示热门提问、课程与知识图谱卡片 |
| `src/views/qa/` | 问答页、历史页、详情页原型 |
| `src/views/course/` | 课程列表、详情、学习页、我的课程原型 |
| `src/components/NavHeader.vue` | 顶部导航组件 |
| `src/stores/` | Pinia store，目前以本地状态和示例数据为主 |
| `vite.config.js` | Vite 配置，默认监听 `0.0.0.0:8080` |
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

同时还预留了 `knowledge`、`community`、`analysis`、`user`、`auth`、`error` 等路由段，但不少路由的 `component` 仍然被注释掉，只保留了菜单与 meta 信息。审计或演示时要把这些路由视为“占位结构”，不要误判为已经交付。

## 环境准备

建议环境：

- Node `^20.19.0 || >=22.12.0`
- 优先使用 `pnpm`，因为目录内已有 `pnpm-lock.yaml`

```bash
cd frontend/apps/student-app
pnpm install
```

如果你临时使用 `npm`，脚本本身也能执行，但默认仍建议跟随现有锁文件使用 `pnpm`。

## 常用命令

```bash
cd frontend/apps/student-app
pnpm dev
pnpm build
pnpm preview
pnpm format
```

默认开发端口来自 `vite.config.js`：

```text
http://0.0.0.0:8080
```

## 当前实现特点

- 页面视觉层已经明显超出 Vite 默认模板，适合继续往正式学员端演进
- 路由、菜单、页面结构已经初步成型
- Pinia store 以示例数据为主，适合前端原型联调前阶段
- 尚未形成稳定 API 层，也没有和 `graphrag_pipeline` / `backend/ckqa-back` 建立正式契约

## 使用时要注意

- `package.json` 当前包名已经调整为 `student-app`，与目录语义保持一致
- 当前目录已由 CKQA 根仓库直接管理，不再作为独立嵌套 Git 仓库使用
- `node_modules/` 是生成依赖，不应当作文档、审计或提交的主对象
- 如果你要把它正式接入 CKQA，需要先明确是直连 `graphrag_pipeline`，还是通过 `backend/ckqa-back` 做统一编排

## 后续接入前建议先补齐什么

1. 明确问答、课程、用户体系的接口契约。
2. 为 `src/axios/index.js` 建立最小可用请求层。
3. 清理“只有路由、没有页面实现”的占位路由，或者显式改成禁用入口。
4. 在真正联调前补齐 API 契约、环境变量和请求失败处理策略。
