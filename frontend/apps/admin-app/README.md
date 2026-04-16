# admin-app

`frontend/apps/admin-app/` 是 CKQA 仓库里的一个独立前端原型，目前主要用于页面占位、技术栈试跑和后续管理端方向的预留。

## 当前状态

- 技术栈：Vue 3 + Vite
- 当前代码形态：接近 Vite/Vue 起步页，尚未接入 CKQA 主链路
- 当前角色：原型与实验场，不是正式业务前端

如果你正在寻找当前系统的主入口，请优先回到仓库根目录和两个 Python 模块：

- [../../../README.md](../../../README.md)
- [../../../pdf_ingest/README.md](../../../pdf_ingest/README.md)
- [../../../graphrag_pipeline/README.md](../../../graphrag_pipeline/README.md)

## 目录与入口

| 文件 | 作用 |
| --- | --- |
| `src/App.vue` | 当前页面根组件 |
| `src/components/HelloWorld.vue` | 原型主内容组件 |
| `src/main.js` | Vue 应用挂载入口 |
| `src/style.css` | 全局样式 |

## 常用命令

```bash
cd frontend/apps/admin-app
npm install
npm run dev
npm run build
npm run preview
```

## 现阶段适合做什么

- 快速试做管理台页面
- 验证前端技术方案和组件风格
- 为未来后台系统预留展示层

## 现阶段不适合假设什么

- 还没有统一接口层
- 还没有鉴权方案
- 还没有和 `pdf_ingest` / `graphrag_pipeline` / `backend` 建立稳定契约
- 不能把它当成已经接入主流程的管理端

## 如果后续要正式接入

建议先明确三件事：

1. 前端是直接请求 `graphrag_pipeline`，还是通过 `backend/ckqa-back` 统一转发。
2. 用户体系、鉴权和权限边界放在哪一层。
3. 页面核心任务是课程管理、导出验收，还是问答运维。
