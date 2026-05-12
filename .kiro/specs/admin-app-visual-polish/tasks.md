# 实施计划：管理员端视觉质感优化

## 概述

基于 Design Token 驱动的视觉优化方案，在不改变路由、状态管理、业务逻辑或 API 调用行为的前提下，系统性提升 `frontend/apps/admin-app/` 的视觉质感与交互体验。实施顺序为：Token 基础层 → 组件层 → 页面集成层 → 测试验证层。

## Tasks

- [ ] 1. Token 系统扩展与基础样式
  - [ ] 1.1 扩展颜色 Token（_colors.scss）
    - 新增 `--ckqa-surface-hover`、`--ckqa-border-subtle` 语义色变量
    - 确保 Light/Dark 两套模式下均有对应值
    - 验证所有 Accent 色板在两种模式下的 WCAG AA 对比度
    - _Requirements: 1.1, 1.3_

  - [ ] 1.2 扩展间距 Token（_space.scss）
    - 将现有 SCSS 变量迁移为 CSS Custom Properties（`--ckqa-space-1` 至 `--ckqa-space-8`）
    - 保留 SCSS 变量作为兼容别名
    - _Requirements: 1.1, 2.3_

  - [ ] 1.3 扩展阴影与 Elevation Token（_shadow.scss + _elevation.scss）
    - 新增 `_elevation.scss` 文件，定义三级语义层：`--ckqa-elevation-flat`、`--ckqa-elevation-raised`、`--ckqa-elevation-floating`
    - Light/Dark 模式下提供不同阴影值
    - _Requirements: 1.4_

  - [ ] 1.4 扩展字体排版 Token（_typography.scss）
    - 新增 5 级字号层级变量：`--ckqa-text-h1` 至 `--ckqa-text-h4` + `--ckqa-text-body`
    - 每级包含 font-size、line-height、font-weight
    - _Requirements: 1.1, 2.1_

  - [ ] 1.5 更新动效 Token（_motion.scss）
    - 更新为三级时长：`--ckqa-duration-fast: 150ms`、`--ckqa-duration-normal: 250ms`、`--ckqa-duration-slow: 400ms`
    - 新增缓动曲线变量：`--ckqa-ease-standard`、`--ckqa-ease-decelerate`、`--ckqa-ease-accelerate`
    - _Requirements: 1.5_

  - [ ]* 1.6 编写 Property Test：Accent 色板 WCAG AA 对比度
    - **Property 1: Accent 色板 WCAG AA 对比度**
    - 使用 fast-check 生成 accent × mode 组合，验证对比度 ≥ 4.5:1
    - 测试文件：`src/__tests__/properties/theme-contrast.prop.test.js`
    - **Validates: Requirements 1.3**

- [ ] 2. 过渡动画系统
  - [ ] 2.1 创建全局过渡样式文件（transitions.scss）
    - 新增 `src/styles/transitions.scss`
    - 实现 `page-fade`、`slide-down`、`list-stagger`、`skeleton-fade`、`nav-collapse` 五种 Transition
    - 所有过渡在 `prefers-reduced-motion: reduce` 下降级为 `duration: 0ms`
    - 在 `main.js` 或全局样式入口中引入
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 2.2 编写 Property Test：prefers-reduced-motion 降级
    - **Property 2: prefers-reduced-motion 降级**
    - 验证所有动画在 reduced-motion 下 duration ≤ 10ms
    - 测试文件：`src/__tests__/properties/motion-a11y.prop.test.js`
    - **Validates: Requirements 4.5**

- [ ] 3. Checkpoint - 确保 Token 和动画基础层测试通过
  - 确保所有测试通过，ask the user if questions arise。

- [ ] 4. 骨架屏系统
  - [ ] 4.1 实现 SkeletonList 组件
    - 创建 `src/components/common/SkeletonList.vue`
    - Props: `rows`、`showAvatar`、`showActions`
    - 应用 shimmer 动画（1.5s 周期）、`aria-hidden="true"`
    - _Requirements: 5.1, 5.3, 5.5_

  - [ ] 4.2 实现 SkeletonDetail 组件
    - 创建 `src/components/common/SkeletonDetail.vue`
    - Props: `sections`、`showHeader`、`showSidebar`
    - 应用 shimmer 动画、`aria-hidden="true"`
    - _Requirements: 5.1, 5.3, 5.5_

  - [ ] 4.3 实现 SkeletonCardGrid 组件
    - 创建 `src/components/common/SkeletonCardGrid.vue`
    - Props: `cards`、`columns`
    - 应用 shimmer 动画、`aria-hidden="true"`
    - _Requirements: 5.1, 5.3, 5.5_

  - [ ] 4.4 实现 SkeletonTable 组件
    - 创建 `src/components/common/SkeletonTable.vue`
    - Props: `rows`、`columns`、`showHeader`
    - 应用 shimmer 动画、`aria-hidden="true"`
    - _Requirements: 5.1, 5.3, 5.5_

  - [ ]* 4.5 编写 Property Test：骨架屏无障碍属性
    - **Property 3: 骨架屏无障碍属性**
    - 使用 fast-check 生成骨架屏变体 × props 组合，验证 `aria-hidden="true"` 存在
    - 测试文件：`src/__tests__/properties/skeleton-a11y.prop.test.js`
    - **Validates: Requirements 5.5**

- [ ] 5. 状态页面组件
  - [ ] 5.1 实现 EmptyState 组件
    - 创建 `src/components/common/EmptyState.vue`
    - Props: `icon`、`title`、`description`、`actionLabel`、`actionTo`
    - Emits: `action`
    - 自动适配 Light/Dark 配色
    - _Requirements: 6.1, 6.3, 6.4_

  - [ ] 5.2 实现 OfflineBanner 组件
    - 创建 `src/components/common/OfflineBanner.vue`
    - 监听 `navigator.onLine` 和 `online`/`offline` 事件
    - 断网时展示持久性提示条，恢复时短暂展示成功 Toast
    - _Requirements: 8.1, 8.2_

  - [ ] 5.3 实现 RetryPanel 组件
    - 创建 `src/components/common/RetryPanel.vue`
    - Props: `error`、`retryCount`、`maxRetries`、`loading`
    - Emits: `retry`
    - 连续 3 次失败后展示升级提示，始终保留手动重试入口
    - _Requirements: 8.3, 8.4, 8.5_

  - [ ]* 5.4 编写 Property Test：状态页面主题适配
    - **Property 4: 状态页面主题适配**
    - 验证状态页面在 light/dark 下文字对比度 ≥ 4.5:1
    - 测试文件：`src/__tests__/properties/state-page-theme.prop.test.js`
    - **Validates: Requirements 6.3, 7.4**

  - [ ]* 5.5 编写 Property Test：重试升级提示逻辑
    - **Property 6: 重试升级提示逻辑**
    - 使用 fast-check 生成随机失败次数序列，验证 N ≥ 3 时 isEscalated = true
    - 测试文件：`src/__tests__/properties/retry-escalation.prop.test.js`
    - **Validates: Requirements 8.5**

- [ ] 6. Composables 层
  - [ ] 6.1 实现 useNetworkStatus composable
    - 创建 `src/composables/useNetworkStatus.js`
    - 提供 `isOnline`、`wasOffline` 响应式状态
    - 监听 `online`/`offline` 事件
    - _Requirements: 8.1, 8.2_

  - [ ] 6.2 实现 useRetry composable
    - 创建 `src/composables/useRetry.js`
    - 提供 `retryCount`、`isEscalated`、`loading`、`error`、`execute`、`retry` 接口
    - maxRetries 默认 3，超过后 isEscalated = true
    - _Requirements: 8.3, 8.4, 8.5_

  - [ ] 6.3 实现 useLayoutStore（响应式布局）
    - 创建 `src/stores/layout.js`
    - 管理 `sidebarMode`（full/icon/hidden）和 `isMobileMenuOpen`
    - 使用 `@vueuse/core` 的 `useMediaQuery` 响应视口变化
    - 三档断点：≥1200px full、768–1199px icon、<768px hidden
    - _Requirements: 10.1, 10.2, 10.3_

  - [ ]* 6.4 编写单元测试：useRetry 和 useNetworkStatus
    - 测试文件：`src/__tests__/unit/useRetry.test.js`、`src/__tests__/unit/useNetworkStatus.test.js`
    - 覆盖重试计数、升级触发、重置、online/offline 事件响应
    - _Requirements: 8.1–8.5_

- [ ] 7. Checkpoint - 确保组件层和 Composables 测试通过
  - 确保所有测试通过，ask the user if questions arise。

- [ ] 8. Element Plus 样式覆写与组件视觉提升
  - [ ] 8.1 覆写 Element Plus 表格样式
    - 更新 `src/styles/element-plus.scss`
    - 统一行高、边框、斑马纹配色与 Design Token 一致
    - 小屏幕下表格容器添加 `overflow-x: auto`
    - _Requirements: 3.4, 10.4_

  - [ ] 8.2 覆写 Element Plus 按钮与表单样式
    - 为 primary/secondary/danger 按钮添加 hover、active、disabled 视觉反馈
    - 包含背景色变化和 scale 微动效
    - 为表单输入框添加 focus 态 Accent 色描边和柔和外发光
    - _Requirements: 3.1, 3.5_

  - [ ] 8.3 统一卡片容器视觉
    - 为 MetricTile、DataTableShell、面板应用统一圆角和 Elevation 阴影
    - 实现 hover 态阴影提升效果（150ms 内）
    - _Requirements: 3.2, 3.3_

  - [ ]* 8.4 编写 Property Test：表格响应式水平滚动
    - **Property 8: 表格响应式水平滚动**
    - 验证视口 < 768px 时表格容器具有 `overflow-x: auto`
    - 测试文件：`src/__tests__/properties/table-responsive.prop.test.js`
    - **Validates: Requirements 10.4**

  - [ ]* 8.5 编写 Property Test：间距 Token 使用一致性
    - **Property 10: 间距 Token 使用一致性**
    - 静态分析样式文件，验证 margin/padding/gap 引用 Token 变量
    - 测试文件：`src/__tests__/properties/space-token-usage.prop.test.js`
    - **Validates: Requirements 2.3**

- [ ] 9. 响应式布局集成
  - [ ] 9.1 改造 ConsoleLayout 响应式行为
    - 集成 `useLayoutStore`，根据 `sidebarMode` 切换侧边导航状态
    - 实现汉堡菜单入口（<768px）
    - 侧边导航收起/展开应用 `nav-collapse` 过渡
    - _Requirements: 2.4, 10.1, 10.2, 10.3, 4.3_

  - [ ] 9.2 改造构建向导步骤条响应式
    - 小屏幕下步骤条切换为垂直布局或下拉选择器
    - _Requirements: 10.5_

- [ ] 10. 错误页面与未开放页面视觉优化
  - [ ] 10.1 优化 UnifiedErrorView 视觉差异化
    - 为 403/404/500 提供不同图标（使用 `@iconify/vue`）和配色强调
    - 保持 `data-error-status` 属性和 `.operation-feedback` 类名结构不变
    - 确保 Dark Mode 下视觉正常
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 12.1–12.4_

  - [ ] 10.2 优化 RouteState（未开放页面）视觉
    - 添加专属插图/图标，与错误页面视觉区分
    - 确保展示所属模块名称、规划状态和路由名称
    - 提供"返回工作台"操作按钮
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [ ]* 10.3 编写 Property Test：E2E 关键 DOM 结构不变性
    - **Property 5: E2E 关键 DOM 结构不变性**
    - 验证 `.operation-feedback[data-status]`、`.build-step-stage`、`.module-hero` 按钮 accessible name 不变
    - 测试文件：`src/__tests__/properties/e2e-dom-contract.prop.test.js`
    - **Validates: Requirements 7.5, 12.1, 12.2, 12.3, 12.4**

  - [ ]* 10.4 编写 Property Test：错误页面视觉差异化
    - **Property 9: 错误页面视觉差异化**
    - 验证不同错误码页面包含不同视觉标识元素
    - 测试文件：`src/__tests__/properties/error-page-diff.prop.test.js`
    - **Validates: Requirements 7.1**

  - [ ]* 10.5 编写 Property Test：未开放页面信息完整性
    - **Property 7: 未开放页面信息完整性**
    - 验证 upcoming 路由渲染包含模块名称、规划状态、路由名称
    - 测试文件：`src/__tests__/properties/coming-soon-info.prop.test.js`
    - **Validates: Requirements 9.2**

- [ ] 11. Checkpoint - 确保样式覆写和页面优化测试通过
  - 确保所有测试通过，ask the user if questions arise。

- [ ] 12. 骨架屏页面集成
  - [ ] 12.1 在工作台、课程列表、知识库列表页集成骨架屏
    - 工作台使用 SkeletonCardGrid
    - 课程列表、知识库列表使用 SkeletonList
    - 数据加载完成后通过 `skeleton-fade` 过渡替换
    - _Requirements: 5.2, 5.4, 5.6_

  - [ ] 12.2 在详情页（课程、资料、知识库、索引）集成骨架屏
    - 使用 SkeletonDetail
    - 数据加载完成后通过 `skeleton-fade` 过渡替换
    - _Requirements: 5.2, 5.4, 5.6_

  - [ ] 12.3 在表格页和系统健康页集成骨架屏
    - 表格区域使用 SkeletonTable
    - 系统健康页使用 SkeletonCardGrid
    - _Requirements: 5.2, 5.4, 5.6_

- [ ] 13. 空状态页面集成
  - [ ] 13.1 在列表页集成 EmptyState 组件
    - 课程列表、知识库列表、问答会话列表无数据时展示 EmptyState
    - 按钮文案明确指向下一步操作（如"新建课程"、"创建知识库"）
    - _Requirements: 6.2, 6.4_

  - [ ] 13.2 集成 OfflineBanner 和 RetryPanel
    - 在 ConsoleLayout 顶部挂载 OfflineBanner
    - 在数据请求失败的区域使用 RetryPanel 替代空白
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [ ] 14. 现有页面视觉一致性覆盖
  - [ ] 14.1 统一四种布局壳视觉风格
    - ConsoleLayout、DetailLayout、WorkflowLayout、AuthLayout 共享 Surface、Border、Shadow Token
    - _Requirements: 11.2_

  - [ ] 14.2 统一公共组件视觉风格
    - DataSourceChip、StatusBadge、MetricTile、DiagnosticLogPanel 应用更新后的 Token
    - _Requirements: 11.3_

  - [ ] 14.3 统一已实现页面 Token 应用
    - 覆盖工作台、系统健康、登录页、课程列表/详情、资料详情、解析结果详情、知识库列表/详情、构建向导、索引详情、QA 冒烟验证
    - _Requirements: 11.1, 11.4_

  - [ ] 14.4 集成路由切换过渡动画
    - 在 router-view 外层包裹 `<Transition name="page-fade">`
    - 列表数据加载完成后应用 `list-stagger` 逐条渐入
    - 操作反馈面板应用 `slide-down` 进入动画
    - _Requirements: 4.1, 4.2, 4.4_

- [ ] 15. Checkpoint - 确保页面集成和视觉一致性测试通过
  - 确保所有测试通过，ask the user if questions arise。

- [ ] 16. 外部依赖安装与 E2E 验证
  - [ ] 16.1 安装外部依赖
    - 安装 `@vueuse/core`、`@iconify/vue`、`colord`、`@formkit/auto-animate`
    - 使用精确版本号（pinned version）
    - 确认 tree-shakeable、ESM-first、gzip < 15KB
    - _Requirements: 设计文档外部库引入策略_

  - [ ] 16.2 运行现有 Playwright E2E 测试
    - 确保 `e2e/local-operation-errors.spec.js` 全部通过
    - 确保 `e2e/data-table-layout.spec.js` 全部通过
    - 验证 DOM 契约未被破坏
    - _Requirements: 12.5, 12.6_

- [ ] 17. Final checkpoint - 确保所有测试通过
  - 确保所有测试通过，ask the user if questions arise。

## Notes

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用具体需求条目，确保可追溯性
- Checkpoint 任务确保增量验证
- Property Tests 验证设计文档中定义的 10 条正确性属性
- 外部依赖安装（任务 16.1）应在实际需要时提前执行，此处放在最后是为了集中验证
- 所有视觉修改必须保持现有 Playwright E2E 测试依赖的 DOM 结构和属性不变
- Token 扩展采用"扩展而非替换"策略，现有变量保持兼容

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5", "16.1"] },
    { "id": 1, "tasks": ["1.6", "2.1"] },
    { "id": 2, "tasks": ["2.2", "4.1", "4.2", "4.3", "4.4", "6.1", "6.2", "6.3"] },
    { "id": 3, "tasks": ["4.5", "5.1", "5.2", "5.3", "6.4"] },
    { "id": 4, "tasks": ["5.4", "5.5", "8.1", "8.2", "8.3"] },
    { "id": 5, "tasks": ["8.4", "8.5", "9.1", "9.2"] },
    { "id": 6, "tasks": ["10.1", "10.2"] },
    { "id": 7, "tasks": ["10.3", "10.4", "10.5", "12.1", "12.2", "12.3"] },
    { "id": 8, "tasks": ["13.1", "13.2", "14.1", "14.2"] },
    { "id": 9, "tasks": ["14.3", "14.4"] },
    { "id": 10, "tasks": ["16.2"] }
  ]
}
```
