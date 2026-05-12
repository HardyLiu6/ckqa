# 需求文档：管理员端视觉质感优化

## 简介

本需求覆盖管理员/教师端前端（`frontend/apps/admin-app/`）的视觉表现层优化。目标是在不改变页面路由、状态管理、业务逻辑或 API 调用行为的前提下，提升整体视觉质感与交互体验，并完善骨架屏（Skeleton）、错误页面等状态页面体系。

技术栈：Vue 3 + Vite + Element Plus + Pinia。范围仅限 `frontend/apps/admin-app/`，不涉及学生端 `student-app`，也不涉及后端 Java `/api/v1` 或 GraphRAG `/v1` 契约。

## 术语表

| 英文术语 | 中文释义 | 说明 |
| --- | --- | --- |
| Design Token | 设计令牌 | 主题系统中的最小可复用视觉变量（颜色、间距、圆角、阴影等） |
| Skeleton | 骨架屏 | 数据加载前展示的灰色占位块，模拟最终布局结构 |
| Empty State | 空状态 | 列表或容器无数据时的占位展示，含插图与引导文案 |
| Error State | 错误态 | 请求失败或异常时的局部反馈展示 |
| Toast | 轻提示 | 短暂出现的全局消息通知（成功、警告、错误） |
| Spinner | 加载指示器 | 旋转动画，表示操作正在进行中 |
| Transition | 过渡动画 | 元素进入/离开/切换时的视觉过渡效果 |
| Surface | 表面层 | 卡片、面板等容器的背景层级 |
| Elevation | 层级阴影 | 通过阴影表达 UI 元素的 Z 轴层级关系 |
| Responsive | 响应式 | 根据视口宽度自适应调整布局与排版 |
| Dark Mode | 暗色模式 | 深色背景配色方案，减少强光环境下的视觉疲劳 |
| Accent | 主题强调色 | 品牌色/交互色，用于按钮、链接、选中态等 |
| Typography | 字体排版 | 字号、行高、字重等文字层级体系 |
| Motion | 动效 | 包含过渡时长、缓动曲线等动画参数 |
| Breakpoint | 断点 | 响应式布局切换的视口宽度阈值 |
| Offline State | 断网状态 | 浏览器网络连接断开时的用户提示 |
| Retry | 重试 | 请求失败后允许用户手动重新发起请求 |
| Operation Feedback | 操作反馈 | 局部操作（解析、导出、构建等）成功或失败后的内联提示面板 |
| Admin_App | 管理员端前端 | 位于 `frontend/apps/admin-app/` 的 Vue 3 应用 |
| Theme_System | 主题系统 | 管理 Design Token、暗色模式切换和强调色选择的子系统 |
| Skeleton_System | 骨架屏系统 | 提供列表、详情、卡片、表格等场景骨架屏组件的子系统 |
| State_Page_System | 状态页面系统 | 统一管理 403/404/500/断网/空状态/未开放等非正常态页面的子系统 |
| Playwright_E2E | Playwright 端到端测试 | 现有的浏览器故障注入自动化测试套件 |

## 需求

### 需求 1：Design Token 体系优化

**用户故事：** 作为前端开发者，我希望 Design Token 体系更加完善和精细，以便在所有页面中获得一致且高质感的视觉表现。

#### 验收标准

1. THE Theme_System SHALL 提供至少包含颜色、间距、圆角、阴影、字体层级、动效时长六个维度的 Design Token 变量集
2. WHEN 用户切换 Dark Mode 时，THE Theme_System SHALL 在 200ms 内完成所有 Token 变量的切换且无闪烁
3. THE Theme_System SHALL 确保所有 Accent 色板（indigo、blue、teal、violet、amber）在 Light 和 Dark 两种模式下均满足 WCAG AA 对比度要求（文字对比度 ≥ 4.5:1）
4. THE Theme_System SHALL 提供至少三级 Elevation 阴影层级（flat、raised、floating），用于区分卡片、弹窗、下拉菜单等容器层级
5. THE Theme_System SHALL 提供统一的 Motion Token（duration-fast: 150ms、duration-normal: 250ms、duration-slow: 400ms 及对应缓动曲线），供所有过渡动画引用

### 需求 2：全局排版与间距规范

**用户故事：** 作为管理员/教师用户，我希望页面文字层级清晰、间距舒适，以便在长时间使用中减少视觉疲劳。

#### 验收标准

1. THE Admin_App SHALL 使用不超过 5 级字号层级（h1–h4 加正文），且每级字号、行高、字重均通过 Typography Token 定义
2. THE Admin_App SHALL 在所有页面的内容区域使用统一的水平内边距（桌面端 ≥ 24px，移动端 ≥ 16px）
3. THE Admin_App SHALL 确保卡片与卡片之间、面板与面板之间的间距通过 Space Token 统一控制，不出现硬编码像素值
4. WHEN 视口宽度小于 768px 时，THE Admin_App SHALL 将侧边导航收起为汉堡菜单，主内容区占满可用宽度

### 需求 3：组件视觉质感提升

**用户故事：** 作为管理员/教师用户，我希望按钮、卡片、表格等常用组件具有现代感和一致性，以便获得专业的使用体验。

#### 验收标准

1. THE Admin_App SHALL 为所有主要按钮（primary、secondary、danger）提供 hover、active、disabled 四种状态的视觉反馈，包含背景色变化和 scale 微动效
2. THE Admin_App SHALL 为所有卡片容器（MetricTile、DataTableShell、面板）应用统一的圆角（使用 radius Token）和 Elevation 阴影
3. WHEN 用户将鼠标悬停在可交互卡片上时，THE Admin_App SHALL 在 150ms 内展示 hover 态阴影提升效果
4. THE Admin_App SHALL 为 Element Plus 表格组件覆写样式，使其行高、边框、斑马纹配色与 Design Token 体系一致
5. THE Admin_App SHALL 为所有表单输入框提供 focus 态的 Accent 色描边和柔和外发光效果

### 需求 4：页面过渡与微交互动画

**用户故事：** 作为管理员/教师用户，我希望页面切换和操作反馈有流畅的动画过渡，以便感知系统响应且不觉得突兀。

#### 验收标准

1. WHEN 路由切换时，THE Admin_App SHALL 对主内容区应用淡入过渡动画（duration 使用 Motion Token duration-normal）
2. WHEN 列表数据加载完成时，THE Admin_App SHALL 对列表项应用逐条渐入动画（stagger delay ≤ 50ms/条）
3. WHEN 用户展开或收起侧边导航时，THE Admin_App SHALL 对导航宽度变化应用平滑过渡（duration 使用 Motion Token duration-normal）
4. WHEN 操作反馈面板（operation-feedback）出现时，THE Admin_App SHALL 对其应用从上方滑入的进入动画
5. THE Admin_App SHALL 确保所有动画在用户开启 `prefers-reduced-motion` 时自动降级为即时切换（duration: 0ms）

### 需求 5：骨架屏系统完善

**用户故事：** 作为管理员/教师用户，我希望在数据加载期间看到与最终布局一致的骨架屏，以便感知页面正在加载而非卡死。

#### 验收标准

1. THE Skeleton_System SHALL 提供至少四种骨架屏变体：列表骨架、详情骨架、卡片网格骨架、表格骨架
2. WHEN 页面数据请求尚未返回时，THE Admin_App SHALL 展示与目标页面布局匹配的骨架屏，而非空白页面或全屏 Spinner
3. THE Skeleton_System SHALL 为骨架屏占位块应用从左到右的渐变闪烁动画（shimmer），动画周期为 1.5–2 秒
4. WHEN 数据加载完成时，THE Admin_App SHALL 将骨架屏平滑过渡为真实内容（使用淡入替换，duration ≤ 300ms）
5. THE Skeleton_System SHALL 确保骨架屏组件设置 `aria-hidden="true"` 且对屏幕阅读器不可见
6. THE Admin_App SHALL 在以下页面使用骨架屏：工作台（Dashboard）、课程列表、课程详情、资料详情、知识库列表、知识库详情、索引详情、系统健康

### 需求 6：空状态页面体系

**用户故事：** 作为管理员/教师用户，我希望在列表为空时看到友好的引导提示，以便知道下一步该做什么。

#### 验收标准

1. THE State_Page_System SHALL 提供统一的空状态组件，包含插图区域、主标题、描述文案和可选的操作按钮
2. WHEN 课程列表、知识库列表、问答会话列表等列表页无数据时，THE Admin_App SHALL 展示对应模块的空状态页面，而非空白表格
3. THE State_Page_System SHALL 为空状态插图提供 Light/Dark 两套配色适配
4. WHEN 空状态页面包含操作按钮时，THE Admin_App SHALL 确保按钮文案明确指向下一步操作（如"新建课程"、"创建知识库"）

### 需求 7：统一错误页面体系

**用户故事：** 作为管理员/教师用户，我希望在遇到 403/404/500 等错误时看到清晰的错误说明和恢复路径，以便快速理解问题并采取行动。

#### 验收标准

1. THE State_Page_System SHALL 为 403、404、500 三种错误码提供视觉差异化的错误页面（不同插图或图标、不同配色强调）
2. THE State_Page_System SHALL 在 500 错误页面提供"刷新页面"和"系统健康"两个恢复操作入口
3. THE State_Page_System SHALL 在 403 错误页面展示当前身份、数据范围和缺失权限信息
4. THE State_Page_System SHALL 确保所有错误页面在 Dark Mode 下视觉表现正常且文字可读
5. THE Admin_App SHALL 保持错误页面的 `data-error-status` 属性和 `.operation-feedback` 类名结构不变，确保现有 Playwright_E2E 故障注入测试继续通过

### 需求 8：断网与请求失败重试

**用户故事：** 作为管理员/教师用户，我希望在网络断开或请求失败时得到明确提示并能一键重试，以便不必手动刷新整个页面。

#### 验收标准

1. WHEN 浏览器检测到网络断开（`navigator.onLine === false`）时，THE Admin_App SHALL 在页面顶部展示持久性断网提示条（不自动消失）
2. WHEN 网络恢复（`online` 事件触发）时，THE Admin_App SHALL 自动隐藏断网提示条并展示短暂的"网络已恢复"成功提示
3. WHEN API 请求返回 5xx 或网络超时时，THE Admin_App SHALL 在对应数据区域展示局部错误态，包含错误摘要和"重试"按钮
4. WHEN 用户点击"重试"按钮时，THE Admin_App SHALL 重新发起原始请求并在请求期间展示加载指示器
5. IF 连续 3 次重试均失败，THEN THE Admin_App SHALL 展示"请检查网络连接或联系管理员"的升级提示，并保留手动重试入口

### 需求 9："未开放"占位页优化

**用户故事：** 作为管理员/教师用户，我希望访问尚未开放的功能页面时看到专业的占位说明，以便知道该功能已规划但尚未实现。

#### 验收标准

1. THE State_Page_System SHALL 为"未开放"占位页提供专属插图或图标，与错误页面视觉区分
2. THE State_Page_System SHALL 在"未开放"占位页展示所属模块名称、规划状态和路由名称
3. THE State_Page_System SHALL 为"未开放"占位页提供"返回工作台"操作按钮
4. WHEN 用户访问 `status: 'upcoming'` 路由时，THE Admin_App SHALL 展示"未开放"占位页而非空白页面或 404

### 需求 10：响应式布局优化

**用户故事：** 作为在不同设备上使用管理台的用户，我希望页面在各种屏幕尺寸下都能正常使用，以便在平板或小屏笔记本上也能高效操作。

#### 验收标准

1. WHILE 视口宽度 ≥ 1200px 时，THE Admin_App SHALL 展示完整侧边导航和多列卡片布局
2. WHILE 视口宽度在 768px–1199px 之间时，THE Admin_App SHALL 将侧边导航收窄为图标模式，卡片布局调整为两列
3. WHILE 视口宽度 < 768px 时，THE Admin_App SHALL 隐藏侧边导航并提供顶部汉堡菜单入口，卡片布局调整为单列
4. THE Admin_App SHALL 确保所有表格在小屏幕下提供水平滚动而非内容截断
5. THE Admin_App SHALL 确保构建向导（Build Wizard）步骤条在小屏幕下切换为垂直布局或下拉选择器

### 需求 11：现有页面视觉一致性覆盖

**用户故事：** 作为管理员/教师用户，我希望所有已实现的页面都遵循统一的视觉规范，以便在不同模块间切换时体验一致。

#### 验收标准

1. THE Admin_App SHALL 确保以下已实现页面均应用更新后的 Design Token：工作台、系统健康、登录页、课程列表、课程详情、资料详情、解析结果详情、知识库列表、知识库详情、构建向导、索引详情、QA 冒烟验证
2. THE Admin_App SHALL 确保 ConsoleLayout、DetailLayout、WorkflowLayout、AuthLayout 四种布局壳的视觉风格统一（共享相同的 Surface、Border、Shadow Token）
3. THE Admin_App SHALL 确保 DataSourceChip、StatusBadge、MetricTile、DiagnosticLogPanel 等公共组件的视觉风格与更新后的 Token 体系一致
4. THE Admin_App SHALL 确保构建向导各步骤（资料选择、解析检查、导出输入、Prompt 确认、索引构建、QA 验证）的视觉风格统一

### 需求 12：Playwright E2E 兼容性保障

**用户故事：** 作为前端开发者，我希望视觉优化不破坏现有的 Playwright 故障注入 E2E 测试，以便持续保障错误处理逻辑的正确性。

#### 验收标准

1. THE Admin_App SHALL 保持 `.operation-feedback` 元素的 `data-status` 属性（`failed`、`running`）不变
2. THE Admin_App SHALL 保持 `.operation-feedback` 元素内的错误文案结构（包含错误描述、业务码/HTTP 状态码）不变
3. THE Admin_App SHALL 保持 `.build-step-stage` 类名和内部 heading 结构不变
4. THE Admin_App SHALL 保持 `.module-hero` 区域内按钮的 accessible name 不变
5. WHEN 视觉优化完成后，THE Admin_App SHALL 确保 `e2e/local-operation-errors.spec.js` 和 `e2e/data-table-layout.spec.js` 全部测试用例通过
6. THE Admin_App SHALL 不修改任何页面的路由路径、query 参数结构或 API 调用行为
