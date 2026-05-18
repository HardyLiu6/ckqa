# Tasks - 提示词策略选择与手动调优

## Tasks

- [x] 1. `mergeStageMetadata` 辅助函数
  - 在 KnowledgeBaseBuildRunService 中添加 mergeStageMetadata 方法，支持跨阶段保留指定键并允许 extras 覆盖。
  - _Requirements: FR-1_

- [x] 2. `normalizeStrategy` 辅助方法
  - 添加 normalizeStrategy 方法，兼容 active 历史值，校验未知策略抛异常。
  - _Requirements: FR-1_

- [x] 3. DTO 默认值改为 default
  - BuildRunPromptConfirmationRequest 的 promptStrategy 默认值从 "active" 改为 "default"。
  - _Requirements: FR-1_

- [x] 4. 改造 confirmPrompt 支持 reset + 策略归一化
  - confirmPrompt 方法接入 normalizeStrategy 和 mergeStageMetadata，支持 confirmed=false 重置。
  - _Requirements: FR-1, FR-2_
  - _Dependencies: 1, 2_

- [ ] 5. Wave A - 后端校验与前端基础（无跨任务依赖）
  - [ ] 5.1 assertCustomDraftExists 严格校验
    - custom_pipeline 策略确认时校验草稿内容非空。
    - _Requirements: FR-3_
  - [ ] 5.2 BuildRunCustomPromptDraftRequest DTO
    - 新建 PUT 草稿请求体 DTO，含 seed + prompts 基础非空校验。
    - _Requirements: FR-3_
  - [ ] 5.3 index-build 入口 assertPromptConfirmed 兜底
    - IndexWorkflowService.createBuildRunIndexRun 入口加 promptConfirmed 校验。
    - _Requirements: FR-4_
  - [ ] 5.4 API 方法 saveBuildRunCustomPromptDraft
    - 前端 knowledge-bases.js 新增 PUT 草稿 API 方法。
    - _Requirements: FR-3_
  - [ ] 5.5 字节计数辅助 + 单元测试
    - 新建 byte-counter.js 模块（utf8ByteLength + formatBytes）及测试。
    - _Requirements: FR-5_
  - [ ] 5.6 resolvePromptConfirmState 扩展返回结构
    - 扩展返回 strategy、customDraftReady、customDraft 等字段，以 metadata 为事实来源。更新 module-loaders.js 调用点和 app-shell.test.js。
    - _Requirements: FR-1, FR-2, FR-3_
  - [ ] 5.7 新路由 knowledge-base-prompt-builder
    - routes.js 新增路由 + router/index.js 注册组件映射 + 组件壳。
    - _Requirements: FR-5_
  - [ ] 5.8 面包屑 knowledge-base-prompt-builder
    - console-breadcrumb-model.js 新增 prompt-builder 路由的面包屑链 + 缺参降级。
    - _Requirements: FR-5_
  - [ ] 5.9 PromptStrategyCard 组件
    - radio 角色单选卡组件 + 样式。
    - _Requirements: FR-2_
  - [ ] 5.10 PromptStrategyDetail 组件
    - 4 种变体详情面板（default / graphrag_tuned / custom_empty / custom_ready）。
    - _Requirements: FR-2_
  - [ ] 5.11 PromptBuilderSeedStep 组件
    - 选模板步骤（3 种子卡片 + history_draft disabled）。
    - _Requirements: FR-5_
  - [ ] 5.12 PromptBuilderPreviewStep 组件
    - 预览步骤（tab 列表 + 只读预览 + 归属说明）。
    - _Requirements: FR-5_
  - _Dependencies: 4_

- [ ] 6. Wave B - 后端草稿服务 + 前端组合组件
  - [ ] 6.1 saveCustomPromptDraft 服务方法
    - 保存草稿到 metadata，原子清除 promptConfirmed，保留前序阶段键。
    - _Requirements: FR-3_
  - [ ] 6.2 resolvePromptPrimaryAction 接 selectedStrategy
    - 主按钮根据策略和草稿就绪状态决定 disabled/label。
    - _Requirements: FR-2_
  - [ ] 6.3 BuildStepPrompt.vue 改写
    - 横向 3 卡 + 详情面板 + 重新选择策略按钮。
    - _Requirements: FR-2_
  - [ ] 6.4 PromptBuilderEditStep 组件
    - 分块编辑步骤（textarea + 字节计数 + 锁定提示词折叠面板）。
    - _Requirements: FR-5_
  - _Dependencies: 5_

- [ ] 7. Wave C - 后端端点 + 前端页面整合
  - [ ] 7.1 saveCustomPromptDraft 完整校验
    - validateDraftRequest 完整实现：history_draft 暂未开放、未知种子拒绝、空白/超限拒绝。
    - _Requirements: FR-3_
  - [ ] 7.2 Controller PUT 端点
    - 新增 PUT /{id}/custom-prompt-draft 端点。
    - _Requirements: FR-3_
  - [ ] 7.3 ModulePage 接通 BuildStepPrompt
    - selectedStrategy ref、query watcher 黑名单、策略切换/确认/重置/跳转 Builder。
    - _Requirements: FR-2, FR-4_
  - [ ] 7.4 PromptBuilderPage 整合
    - 状态机 + 双重离开拦截 + 保存/返回逻辑。
    - _Requirements: FR-5_
  - _Dependencies: 6_

- [ ] 8. Wave D - E2E 基础设施
  - [ ] 8.1 E2E helpers 基础设施
    - helpers/build-wizard.js 共享辅助（可变状态 API mock + GET /auth/me）。
    - _Requirements: FR-ALL_
  - _Dependencies: 7_

- [ ] 9. Wave E - E2E 测试
  - [ ] 9.1 E2E - default 策略完整确认流程
    - 选 default → 确认 → 跳 step=index。
    - _Requirements: FR-1, FR-2_
  - [ ] 9.2 E2E - custom_pipeline 完整流程
    - 选 custom → 进 Builder → 保存 → 返回 → 确认。
    - _Requirements: FR-3, FR-5_
  - [ ] 9.3 E2E - URL 伪造防御
    - 伪造 promptConfirmed=1 时前端清理 + 索引按钮 disabled。
    - _Requirements: FR-4_
  - [ ] 9.4 E2E - 重新选择策略 + 刷新拦截
    - 已确认后重新选择 + Builder dirty 时 beforeunload。
    - _Requirements: FR-2, FR-5_
  - [ ] 9.5 E2E - 边界场景集合
    - blocked / refresh-state-preserve / unsaved-leave。
    - _Requirements: FR-ALL_
  - _Dependencies: 8_

- [ ] 10. Wave F - 全量回归
  - [ ] 10.1 全量回归 + 文档收尾
    - 全量测试 + 手测清单 + 偏差修复。
    - _Requirements: FR-ALL_
  - _Dependencies: 9_
