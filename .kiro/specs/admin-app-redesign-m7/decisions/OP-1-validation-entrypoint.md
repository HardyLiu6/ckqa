# OP-1 · 知识库验证入口

### 背景

`requirements.md §1.4` 与 `design.md §16` 提出的开放问题 OP-1：知识库验证页
（`KbValidationPage`）的后端入口应走哪一条链路？候选方案：

- 方案 ①：继续复用 `POST /api/v1/knowledge-base-build-runs/{buildRunId}/qa-smoke`，
  前端按"所选 KB → 对应的 `activeBuildRunId / latestBuildRunId`"解析到一次具体的
  构建运行后再触发验证。
- 方案 ②：后端新增"按 KB 直接触发验证"的入口（形如
  `POST /api/v1/knowledge-bases/{kbId}/validation-runs`），前端不再需要自行解析
  `buildRunId`。

### 决策

截至 M7 开发期：

1. 后端仅提供方案 ①（`POST /api/v1/knowledge-base-build-runs/{buildRunId}/qa-smoke`），
   **尚未提供**方案 ② 的"按 KB 直接触发验证"新接口。
2. **M7 默认采用方案 ①**。`useKbValidationRun.start` 内部先通过
   `resolveBuildRunId(kb, selectedKbId)` 解析 `latestBuildRunId / activeBuildRunId /
   buildRunId`，任一命中即作为 `runBuildRunQaSmoke` 的参数。
3. 若所选 KB 上以上三个字段均缺失，`resolveBuildRunId` 退化为 `selectedKbId` 占位
   —— 这只是为了让整条 composable / UI 链路在测试与 mock 环境下可跑通；
   **正式集成时** 若后端返回的字段缺失，`runBuildRunQaSmoke` 会返回业务错
   并由页面侧的错误面板提示用户（`runState = 'failed'` + `errorMessage`）。

### 影响面

- 文件：`src/composables/useKbValidationRun.js`。方案 ① 的取值优先级由
  `resolveBuildRunId` 函数封装；`start()` 的对外签名（`{ question, mode,
  selectedKbId }`）保持不变，UI 层零感知。
- UI：`src/views/operations/KbValidationPage.vue` 及其子组件完全通过
  composable 对外签名交互，切换方案时无需改动。
- 测试：`src/composables/useKbValidationRun.test.js` 覆盖"`latestBuildRunId`
  优先"、"退化到 `selectedKbId`"以及"`triggerOverride` 可覆盖默认 trigger"三条用例。

### 切换步骤（未来落地方案 ② 时）

1. 后端给出新接口，例如 `api/knowledge-bases.js` 导出 `runKbValidation(kbId, payload)`。
2. 在调用方传入 `useKbValidationRun({ triggerOverride: ({ question, mode, selectedKbId }) =>
   runKbValidation(selectedKbId, { question, mode }) })`；composable 内部检测到
   `triggerOverride` 存在即取而代之，`buildRunId` 的解析逻辑被跳过。
3. `resolveBuildRunId` 与原 `runBuildRunQaSmoke` 实现保留作为方案 ① 的回退路径，
   等观察期结束后再统一移除。
4. 对外签名、历史条目形状、`runState` / `runSnapshot` 的语义均不改变，UI 层与
   E2E 用例无需同步修改。

---

维护者：`admin-app-redesign-m7` spec 任务 5.5 · 2026-05-08
