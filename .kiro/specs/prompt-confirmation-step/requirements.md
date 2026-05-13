# 需求文档

## 简介

知识库构建向导第04步"Prompt 确认"的前端页面设计与前后端连接实现。该步骤的核心功能从原来的"确认沿用活动提示词"升级为"让用户选定提示词策略"。用户可从三种策略中选择一种：默认提示词、GraphRAG 自动调优提示词、我的流水线设计提示词。第三种策略支持跳转到独立页面进行提示词构建，构建完成后可返回第04步继续确认流程。

当前状态：后端接口 `POST /api/v1/knowledge-base-build-runs/{id}/prompt-confirmation` 已就绪，前端 `BuildStepPrompt.vue` 组件已存在但仅为最小占位实现（只展示标题、说明文案和状态徽章），缺少策略选择面板、已选资料摘要、确认按钮和操作反馈。

## 术语表

- **Build_Wizard**：知识库构建向导，承载从资料选择到问答验证的6步连续流程
- **Build_Run**：一次完整的知识库构建流水线实例，由 Java 后端管理生命周期
- **Prompt_Strategy**：提示词策略，支持三种类型：`default`（系统默认提示词）、`graphrag_tuned`（GraphRAG 自动调优提示词）、`custom_pipeline`（用户自定义流水线设计提示词）
- **Strategy_Selector**：策略选择器组件，以单选按钮组或卡片选择形式展示三种策略选项
- **BuildStepPrompt**：构建向导第04步对应的 Vue 组件
- **Module_Page**：`ModulePage.vue`，构建向导的宿主页面，负责步骤路由、操作分发和状态刷新
- **Prompt_Confirmation_API**：`POST /api/v1/knowledge-base-build-runs/{id}/prompt-confirmation` 接口
- **Status_Badge**：状态徽章组件，用于展示步骤当前状态（待确认/已确认/阻塞）
- **Custom_Prompt_Builder**：自定义提示词构建页面，独立于构建向导的全屏页面，用户在此页面设计和编辑自定义流水线提示词
- **Custom_Prompt_Draft**：用户在 Custom_Prompt_Builder 中构建的提示词草稿数据，保存后可在第04步中作为已选策略使用

## 需求

### 需求 1：策略选择面板展示

**用户故事：** 作为教师/管理员，我想在构建向导第04步看到清晰的提示词策略选择面板，以便从多种策略中选定本次索引构建使用的提示词。

#### 验收标准

1. WHEN 用户进入构建向导第04步，THE BuildStepPrompt SHALL 展示"提示词策略选择"区域标题和 Strategy_Selector 组件，Strategy_Selector 包含三个策略选项卡片
2. WHEN 用户进入构建向导第04步，THE Strategy_Selector SHALL 展示以下三个策略选项，每个选项包含策略名称和简要说明：
   - 选项一：名称为"默认提示词"，说明为"使用系统默认的提示词"
   - 选项二：名称为"GraphRAG 自动调优提示词"，说明为"使用 GraphRAG 自动调优生成的提示词"
   - 选项三：名称为"我的流水线设计提示词"，说明为"使用自定义的流水线设计提示词"
3. WHEN 用户进入构建向导第04步且尚未选择策略，THE Strategy_Selector SHALL 默认选中"默认提示词"选项
4. WHEN 用户进入构建向导第04步，THE BuildStepPrompt SHALL 展示当前 Build_Run 已选资料数量摘要（格式为"已选资料 N 个"，N 取自 Build_Run 资料选择步骤确认的资料 ID 列表长度，N 为 0 时仍显示"已选资料 0 个"）
5. IF 提示词策略尚未确认且第04步处于 `ready` 状态，THEN THE Status_Badge SHALL 显示"待确认"标签且状态为 `ready`
6. IF 提示词策略已确认（`promptConfirmed=1`），THEN THE Status_Badge SHALL 显示"已确认"标签且状态为 `done`
7. WHILE 图谱输入步骤未完成（`exportConfirmed` 查询参数未设为 `1`），THE BuildStepPrompt SHALL 将 Status_Badge 显示为 `blocked` 状态，且 Strategy_Selector 和确认按钮均处于禁用状态（可见但不可交互）

### 需求 2：策略选择交互

**用户故事：** 作为教师/管理员，我想通过点击策略卡片来切换选中的提示词策略，以便灵活选择最适合本次构建的提示词方案。

#### 验收标准

1. WHEN 用户点击 Strategy_Selector 中的任一策略选项卡片，THE Strategy_Selector SHALL 将该选项标记为选中状态（高亮显示），同时取消其他选项的选中状态
2. WHEN 用户选中"默认提示词"选项，THE BuildStepPrompt SHALL 在策略选项下方展示策略详情区域，内容为"将使用系统内置的 GraphRAG 默认提示词进行索引构建"
3. WHEN 用户选中"GraphRAG 自动调优提示词"选项，THE BuildStepPrompt SHALL 在策略选项下方展示策略详情区域，内容为"将使用 GraphRAG 自动调优流程生成的提示词进行索引构建"
4. WHEN 用户选中"我的流水线设计提示词"选项且用户尚未构建自定义提示词，THE BuildStepPrompt SHALL 在策略选项下方展示策略详情区域，包含提示文案"尚未构建自定义提示词"和一个"前往构建"按钮
5. WHEN 用户选中"我的流水线设计提示词"选项且用户已完成自定义提示词构建，THE BuildStepPrompt SHALL 在策略选项下方展示策略详情区域，包含已构建提示词的摘要信息和一个"编辑提示词"按钮
6. WHILE 第04步处于 `blocked` 状态，THE Strategy_Selector SHALL 禁用所有策略选项卡片的点击交互，保持视觉上的禁用样式

### 需求 3：自定义提示词构建页面跳转

**用户故事：** 作为教师/管理员，我想在选择"我的流水线设计提示词"策略后能跳转到独立页面构建提示词，构建完成后能正常返回第04步继续确认流程。

#### 验收标准

1. WHEN 用户在第04步点击"前往构建"或"编辑提示词"按钮，THE Build_Wizard SHALL 通过 Vue Router 导航到 Custom_Prompt_Builder 页面，URL 中携带当前 Build_Run ID 作为路由参数
2. WHEN 用户进入 Custom_Prompt_Builder 页面，THE Custom_Prompt_Builder SHALL 展示提示词编辑界面，页面顶部包含面包屑导航显示"知识库构建 > 提示词设计"，以及一个"返回构建向导"按钮
3. WHEN 用户在 Custom_Prompt_Builder 页面点击"返回构建向导"按钮，THE Custom_Prompt_Builder SHALL 通过 Vue Router 导航回构建向导第04步页面，恢复用户离开前的策略选择状态
4. WHEN 用户在 Custom_Prompt_Builder 页面完成提示词构建并点击"保存"按钮，THE Custom_Prompt_Builder SHALL 将提示词草稿数据持久化（保存到后端或本地状态），并展示保存成功提示
5. IF 用户在 Custom_Prompt_Builder 页面有未保存的修改并尝试离开页面（点击返回或浏览器后退），THEN THE Custom_Prompt_Builder SHALL 弹出确认对话框提示"有未保存的修改，确定离开吗？"，用户确认后才执行导航
6. WHEN 用户从 Custom_Prompt_Builder 页面返回第04步，THE BuildStepPrompt SHALL 检查自定义提示词是否已保存，若已保存则自动选中"我的流水线设计提示词"选项并在详情区域展示已构建提示词摘要
7. THE Custom_Prompt_Builder SHALL 作为独立路由页面存在，不嵌套在构建向导步骤组件内部，拥有独立的页面布局和导航结构

### 需求 4：确认提示词策略按钮交互

**用户故事：** 作为教师/管理员，我想通过点击"确认提示词策略"按钮完成本步骤确认，以便继续进入索引构建步骤。

#### 验收标准

1. WHEN 提示词策略处于 `ready` 状态且用户已选定策略并点击"确认提示词策略"按钮，THE Module_Page SHALL 调用 Prompt_Confirmation_API 发送 `{ confirmed: true, promptStrategy: "<selected_strategy>" }` 请求（`<selected_strategy>` 为 `default`、`graphrag_tuned` 或 `custom_pipeline` 之一），请求超时时间为 15 秒
2. WHEN Prompt_Confirmation_API 返回成功响应，THE Build_Wizard SHALL 将 URL 查询参数 `promptConfirmed` 设为 `1` 并自动跳转到第05步（索引构建）
3. WHEN Prompt_Confirmation_API 返回成功响应，THE BuildStepPrompt SHALL 将 Status_Badge 状态更新为 `done` 并显示"已确认"标签
4. IF 用户选中"我的流水线设计提示词"但尚未完成自定义提示词构建，THEN THE 确认按钮 SHALL 处于禁用状态，按钮下方展示提示文案"请先完成自定义提示词构建"
5. IF Prompt_Confirmation_API 返回错误响应，THEN THE Module_Page SHALL 在操作面板中展示后端响应体 `message` 字段内容作为错误提示，保持当前步骤不跳转，且确认按钮恢复为可点击状态
6. WHILE 确认请求正在进行中，THE 确认按钮 SHALL 进入 disabled 状态并显示旋转加载指示器，替换按钮原始文案
7. IF 确认请求超时或网络连接失败，THEN THE Module_Page SHALL 在操作面板中展示错误提示指明网络异常，保持当前步骤不跳转，且确认按钮恢复为可点击状态

### 需求 5：步骤状态与前置条件联动

**用户故事：** 作为教师/管理员，我想让 Prompt 确认步骤正确反映前置条件状态，以便理解当前流程进度。

#### 验收标准

1. WHILE 图谱输入（第03步）的 `exportConfirmed` 查询参数未设为 `1`，THE 构建向导步骤条 SHALL 将第04步标记为 `blocked` 状态
2. IF `exportConfirmed` 查询参数已设为 `1` 且 `promptConfirmed` 查询参数未设为 `1`，THEN THE 构建向导步骤条 SHALL 将第04步标记为 `ready` 状态
3. WHEN Prompt 已确认（`promptConfirmed=1`），THE 构建向导步骤条 SHALL 将第04步标记为 `done` 状态
4. WHEN 用户在第04步已确认后点击步骤条导航回第03步并重新执行导出操作导致 `exportConfirmed` 被清除，THE Build_Wizard SHALL 同时清除 `promptConfirmed` 查询参数并将第04步重置为 `ready` 状态
5. WHILE 第04步处于 `blocked` 状态，THE 第05步（索引构建）SHALL 处于 `blocked` 状态，其步骤条按钮保持可点击但主操作按钮禁用且不可提交
6. WHILE 第04步处于 `blocked` 或 `ready` 状态，THE 第05步（索引构建）SHALL 处于 `blocked` 状态

### 需求 6：Build Run 阶段同步

**用户故事：** 作为教师/管理员，我想让 Prompt 确认操作正确更新 Build Run 的后端阶段记录，以便在刷新页面或重新进入向导时恢复正确状态。

#### 验收标准

1. WHEN Prompt_Confirmation_API 调用成功，THE 后端 SHALL 将 Build_Run 的 `currentStage` 更新为 `prompt` 并将 `status` 更新为 `running`，同时记录用户选定的 `promptStrategy` 值
2. WHEN 用户刷新页面或重新进入向导且 Build_Run 的 `currentStage` 为 `prompt`、`index`、`index_build`、`qa_smoke` 或 `done` 之一，THE Build_Wizard SHALL 根据后端返回的 `currentStage` 恢复第04步为已完成（`done`）状态，并在 Strategy_Selector 中回显用户之前选定的策略
3. WHEN 用户刷新页面或重新进入向导且 Build_Run 的 `currentStage` 为 `prompt` 之前的阶段（`material_selection`、`parse`、`parse_check`、`graph_input`、`graph_input_export`），THE Build_Wizard SHALL 将第04步显示为未完成状态（`blocked` 或 `ready`，取决于前置步骤完成情况）
4. THE Build_Wizard SHALL 将后端 `currentStage` 值 `prompt_confirmation` 和 `prompt` 均映射到前端步骤 key `prompt`
5. WHEN 用户刷新页面或重新进入向导，THE Build_Wizard SHALL 从后端响应中读取 `promptStrategy` 字段并在 Strategy_Selector 中恢复对应的选中状态

### 需求 7：已确认状态下的回看与重确认

**用户故事：** 作为教师/管理员，我想在 Prompt 已确认后仍能回看该步骤信息，以便确认当前策略或在需要时重新确认。

#### 验收标准

1. WHEN 用户在 Prompt 已确认后点击步骤条回到第04步，THE BuildStepPrompt SHALL 展示已确认状态面板，包含已选策略名称（"默认提示词"、"GraphRAG 自动调优提示词"或"我的流水线设计提示词"之一）、已选资料数量摘要、以及状态为 `done` 的 Status_Badge（显示"已确认"）
2. WHEN Prompt 已确认且用户回看第04步，THE 主操作按钮 SHALL 显示为"进入创建索引"而非"确认提示词策略"
3. WHEN 用户在已确认状态下点击"进入创建索引"按钮，THE Build_Wizard SHALL 跳转到第05步（索引构建）且不重复调用 Prompt_Confirmation_API
4. WHILE 第04步处于已确认状态，THE Strategy_Selector SHALL 显示用户之前选定的策略为选中状态，但所有策略选项卡片处于只读模式（不可切换）
5. IF 用户从已确认状态回退到第03步重新操作导出，THEN THE Build_Wizard SHALL 清除 `promptConfirmed` 查询参数并将第04步重置为 `ready` 状态，Strategy_Selector 恢复为可交互模式，用户需重新选择并确认提示词策略
