# admin-app

`frontend/apps/admin-app/` 是 CKQA 仓库里的一个独立 Vue 3 + Vite 管理端原型，目前还没有正式接入 `pdf_ingest` / `graphrag_pipeline` / `backend` 的主链路。

## 当前状态

- 技术栈：Vue 3 + Vite
- 角色：管理端占位、页面原型和前端实验场
- 集成状态：尚未形成统一的接口层、鉴权方案或工作区结构

## 常用命令

```bash
cd frontend/apps/admin-app
npm install
npm run dev
npm run build
npm run preview
```

## 说明

- `node_modules/` 是依赖目录，不是源码。
- 如果后续需要把这个前端真正接进 CKQA 主流程，建议先明确它是直接对接 `graphrag_pipeline`，还是通过 `backend/ckqa-back` 做统一 API 网关。
