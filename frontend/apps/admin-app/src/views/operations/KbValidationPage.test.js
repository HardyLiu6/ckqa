import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * `KbValidationPage.vue`（含 `ValidationForm.vue` / `ValidationResult.vue` 两个子组件）
 * 的结构契约测试（M7 · 任务 5.3）。
 *
 * 策略：与 admin-app 其它同构页（UserList / Role / Permission / Health）一致，采用
 * 「读 .vue 源码 + 正则断言」。本仓库的 `node --test` 栈不拉 jsdom 与 `@vue/test-utils`，
 * 因此断言聚焦在源码层面的结构契约：
 *
 * - ① 首次加载渲染 `CkEmptyState`（`v-if="runState === 'idle'"`）；
 * - ② 发起验证进入 `running` 并展示阶段进度（`<CkSplitProgress ... runState === 'running'"`）；
 * - ③ 成功态展示答复、耗时与日志（`answer / timings / CkLogStream`）；
 * - ④ 失败态展示平实错误文案且含「重新发起」按钮（`@click="handleRetry"`）；
 * - ⑤ 历史表在 mock 数据下正确渲染 10 行（`history.slice(0, 10)`，`<el-table ...>`）。
 *
 * 同时强制消费 `useKbValidationRun` 的 11 个关键字段、写权限守护 `v-if="authStore.canAccess(['qa:write'])"`
 * 至少两处，以及所有文案走 `KB_VALIDATION_COPY / FORM_COPY / RESULT_COPY / MODE_LABELS / STATE_LABELS`。
 */

const here = fileURLToPath(new URL('.', import.meta.url))

const pageSource = readFileSync(fileURLToPath(new URL('./KbValidationPage.vue', import.meta.url)), 'utf8')
const formSource = readFileSync(fileURLToPath(new URL('./ValidationForm.vue', import.meta.url)), 'utf8')
const resultSource = readFileSync(fileURLToPath(new URL('./ValidationResult.vue', import.meta.url)), 'utf8')
const copySource = readFileSync(fileURLToPath(new URL('./kb-validation-copy.js', import.meta.url)), 'utf8')

/** 三个 .vue 文件合并后的字符串，便于做"全域"断言（例如覆盖 11 个字段、至少 2 处权限守护）。 */
const combined = `${pageSource}\n${formSource}\n${resultSource}`

// ---------------------------------------------------------------------------
// 基础定位点：data-testid / aria-label
// ---------------------------------------------------------------------------

test('页面根节点与关键交互锚点具备 data-testid', () => {
  assert.match(pageSource, /data-testid="kb-validation-page"/, '页面根节点需要 data-testid="kb-validation-page"')
  assert.match(formSource, /data-testid="kb-validation-start"/, '发起按钮需要 data-testid="kb-validation-start"')
  assert.match(formSource, /data-testid="kb-validation-kb-select"/, 'KB 选择器需要 data-testid')
  assert.match(formSource, /data-testid="kb-validation-question"/, '问题输入需要 data-testid')
  assert.match(formSource, /data-testid="kb-validation-mode"/, '模式选择需要 data-testid')
  assert.match(resultSource, /data-testid="kb-validation-empty"/, '空态需要 data-testid')
  assert.match(resultSource, /data-testid="kb-validation-progress"/, '运行态进度需要 data-testid')
  assert.match(resultSource, /data-testid="kb-validation-answer"/, '成功态答复需要 data-testid')
  assert.match(resultSource, /data-testid="kb-validation-error"/, '失败态错误面板需要 data-testid')
  assert.match(resultSource, /data-testid="kb-validation-retry"/, '重新发起按钮需要 data-testid')
  assert.match(pageSource, /data-testid="kb-validation-history"/, '历史区需要 data-testid')
  assert.match(pageSource, /data-testid="kb-validation-history-table"/, '历史表需要 data-testid')
  assert.match(pageSource, /aria-label="近 10 条知识库验证历史"/, '历史表需要 aria-label')
})

// ---------------------------------------------------------------------------
// 表单区：el-select / el-input type="textarea" / el-radio-group
// ---------------------------------------------------------------------------

test('表单区使用 el-select 枚举 knowledgeBases', () => {
  assert.match(formSource, /<el-select\b[\s\S]{0,200}v-model="kbModel"/)
  assert.match(formSource, /<el-option[\s\S]{0,200}v-for="kb in knowledgeBases"/)
})

test('问题输入使用 el-input type="textarea" 且限制 500 字符', () => {
  assert.match(formSource, /<el-input\b[\s\S]{0,200}type="textarea"/)
  assert.match(formSource, /:maxlength="500"/)
  assert.match(formSource, /show-word-limit/)
})

test('模式选择使用 el-radio-group + el-radio-button 枚举 MODE_LABELS', () => {
  assert.match(formSource, /<el-radio-group\b[\s\S]{0,200}v-model="modeModel"/)
  assert.match(formSource, /<el-radio-button[\s\S]{0,200}v-for="\(label, key\) in MODE_LABELS"/)
})

test('发起按钮 disable 条件由父组件的 canSubmit 决定', () => {
  assert.match(formSource, /:disabled="!canSubmit"/, '发起按钮需要基于 canSubmit 禁用')
  assert.match(pageSource, /const canSubmit = computed\(/, '父组件需要计算 canSubmit')
  // 条件覆盖：运行中 / 未选 KB / 问题为空
  assert.match(pageSource, /runState\.value === 'running'/)
  assert.match(pageSource, /!selectedKbId\.value/)
  assert.match(pageSource, /question\.value[\s\S]{0,40}trim\(\)/)
})

// ---------------------------------------------------------------------------
// 结果区：四态切换
// ---------------------------------------------------------------------------

test('① 首次加载 runState === "idle" 时渲染 CkEmptyState', () => {
  assert.match(
    resultSource,
    /<CkEmptyState\b[\s\S]{0,200}v-if="runState === 'idle'"/,
    'CkEmptyState 必须在 runState === "idle" 分支渲染',
  )
  assert.match(
    resultSource,
    /:title="KB_VALIDATION_COPY\.empty\.title"/,
    '空态标题走 KB_VALIDATION_COPY.empty.title',
  )
  assert.match(
    resultSource,
    /:description="KB_VALIDATION_COPY\.empty\.description"/,
    '空态描述走 KB_VALIDATION_COPY.empty.description',
  )
})

test('② runState === "running" 时渲染 CkSplitProgress 阶段进度', () => {
  assert.match(
    resultSource,
    /<CkSplitProgress\b[\s\S]{0,200}v-else-if="runState === 'running'"/,
    'CkSplitProgress 必须在 runState === "running" 分支渲染',
  )
  assert.match(resultSource, /:stages="stageItems"/, 'stages 应该由 stageItems 派生')
  assert.match(resultSource, /:active-key="activeStageKey"/, '应提供 active-key 高亮当前阶段')
})

test('③ runState === "success" 时展示答复文本、耗时与日志', () => {
  assert.match(
    resultSource,
    /v-else-if="runState === 'success'"/,
    '成功态必须基于 runState === "success" 分支',
  )
  assert.match(resultSource, /runSnapshot\?\.answer/, '需要展示 runSnapshot.answer')
  assert.match(resultSource, /runSnapshot\.timings\.retrievalMs/, '需要展示 timings.retrievalMs')
  assert.match(resultSource, /runSnapshot\.timings\.generationMs/, '需要展示 timings.generationMs')
  assert.match(resultSource, /<CkLogStream\b[\s\S]{0,200}:lines="sourceLines"/, '需要以 CkLogStream 展示 sources 日志流')
})

test('④ 失败态展示平实错误文案且含「重新发起」按钮', () => {
  // 使用 v-else 兜底 "failed" 分支，避免遗漏新增状态
  assert.match(
    resultSource,
    /v-else[\s\S]{0,200}data-testid="kb-validation-error"/,
    '失败态必须作为结果区的 v-else 分支渲染',
  )
  assert.match(resultSource, /runSnapshot\?\.errorMessage/, '需要展示 runSnapshot.errorMessage')
  assert.match(
    resultSource,
    /<el-button\b[\s\S]{0,240}data-testid="kb-validation-retry"[\s\S]{0,160}@click="handleRetry"/,
    '需要「重新发起」按钮并绑定 handleRetry',
  )
  // 父组件 handleRetry 先 reset 再 start
  assert.match(pageSource, /function handleRetry\(\) \{[\s\S]{0,200}reset\(\)[\s\S]{0,200}start\(\)/, 'handleRetry 应先 reset 再 start')
  // 错误文案来自 RESULT_COPY.errorTitle / retryAction
  assert.match(resultSource, /RESULT_COPY\.errorTitle/)
  assert.match(resultSource, /RESULT_COPY\.retryAction/)
})

// ---------------------------------------------------------------------------
// 历史表：el-table + history.slice(0, 10) + 5 列
// ---------------------------------------------------------------------------

test('⑤ 历史表使用 el-table 渲染 history 前 10 条', () => {
  assert.match(pageSource, /<el-table\b[\s\S]{0,200}:data="history\.slice\(0, 10\)"/, '历史表数据来源必须是 history.slice(0, 10)')
  // 列：时间 / 知识库 / 问题 / 模式 / 状态 —— 共 5 列
  const columnMatches = pageSource.match(/<el-table-column\b/g) ?? []
  assert.ok(
    columnMatches.length >= 5,
    `历史表至少包含 5 列（时间/知识库/问题/模式/状态），实际 ${columnMatches.length}`,
  )
  assert.match(pageSource, /label="时间"/)
  assert.match(pageSource, /label="知识库"/)
  assert.match(pageSource, /label="问题"/)
  assert.match(pageSource, /label="模式"/)
  assert.match(pageSource, /label="状态"/)
  // 模式列通过 modeLabel 翻译；状态列通过 stateLabel 翻译并走 CkStatusPill
  assert.match(pageSource, /modeLabel\(row\.mode\)/)
  assert.match(pageSource, /<CkStatusPill\b[\s\S]{0,160}:label="stateLabel\(row\.state\)"/)
})

// ---------------------------------------------------------------------------
// 写权限守护：至少两处 v-if="authStore.canAccess(['qa:write'])"
// ---------------------------------------------------------------------------

test('写权限守护 v-if="authStore.canAccess([\'qa:write\'])" 至少两处', () => {
  const guardPattern = /v-if="authStore\.canAccess\(\['qa:write'\]\)"/g
  const matches = combined.match(guardPattern) ?? []
  assert.ok(
    matches.length >= 2,
    `至少需要 2 处 qa:write 守护（发起按钮 + 重新发起按钮），实际 ${matches.length}`,
  )
  assert.match(formSource, guardPattern, 'ValidationForm 内的发起按钮需要 qa:write 守护')
  assert.match(resultSource, guardPattern, 'ValidationResult 内的重新发起按钮需要 qa:write 守护')
})

// ---------------------------------------------------------------------------
// 消费 useKbValidationRun 的 11 个关键字段
// ---------------------------------------------------------------------------

test('消费 useKbValidationRun 暴露的 11 个关键字段', () => {
  const expected = [
    'knowledgeBases',
    'selectedKbId',
    'selectedIndexRunId',
    'question',
    'mode',
    'runState',
    'runSnapshot',
    'history',
    'loadKnowledgeBases',
    'start',
    'reset',
  ]
  for (const field of expected) {
    assert.match(
      pageSource,
      new RegExp(`\\b${field}\\b`),
      `KbValidationPage 应消费 useKbValidationRun 的 ${field}`,
    )
  }
  assert.match(pageSource, /const\s*\{[\s\S]*?\}\s*=\s*useKbValidationRun\(/, '应通过解构调用 useKbValidationRun()')
})

// ---------------------------------------------------------------------------
// 文案走 copy 常量（禁止裸字符串新增）
// ---------------------------------------------------------------------------

test('所有文案走 KB_VALIDATION_COPY / FORM_COPY / RESULT_COPY / MODE_LABELS / STATE_LABELS', () => {
  // KB_VALIDATION_COPY 的 eyebrow / title / subtitle / empty / historyTitle
  assert.match(pageSource, /:eyebrow="KB_VALIDATION_COPY\.eyebrow"/)
  assert.match(pageSource, /:title="KB_VALIDATION_COPY\.title"/)
  assert.match(pageSource, /:subtitle="KB_VALIDATION_COPY\.subtitle"/)
  assert.match(pageSource, /KB_VALIDATION_COPY\.historyTitle/)
  assert.match(resultSource, /KB_VALIDATION_COPY\.empty\.title/)
  assert.match(resultSource, /KB_VALIDATION_COPY\.empty\.description/)
  // FORM_COPY 消费点
  for (const key of ['title', 'kbLabel', 'questionLabel', 'modeLabel', 'submit']) {
    assert.match(formSource, new RegExp(`FORM_COPY\\.${key}`), `FORM_COPY.${key} 应被表单消费`)
  }
  // RESULT_COPY 消费点
  for (const key of ['title', 'answerTitle', 'errorTitle', 'sourcesTitle', 'timingsTitle', 'retryAction']) {
    assert.match(resultSource, new RegExp(`RESULT_COPY\\.${key}`), `RESULT_COPY.${key} 应被结果区消费`)
  }
  // MODE_LABELS 与 STATE_LABELS 均以原始常量形态出现；stateLabel() 由 kb-validation-copy 导出
  assert.match(formSource, /MODE_LABELS\b/, '表单需要遍历 MODE_LABELS')
  assert.match(pageSource, /STATE_LABELS\b/, '父组件需要引用 STATE_LABELS（哪怕仅为保留导入）')
  assert.match(pageSource, /stateLabel\(runState\)/)
  assert.match(pageSource, /stateLabel\(row\.state\)/)
  assert.match(pageSource, /modeLabel\(row\.mode\)/)
})

test('kb-validation-copy 仍然导出本测试所依赖的常量与函数', () => {
  assert.match(copySource, /export const KB_VALIDATION_COPY\b/)
  assert.match(copySource, /export const FORM_COPY\b/)
  assert.match(copySource, /export const RESULT_COPY\b/)
  assert.match(copySource, /export const MODE_LABELS\b/)
  assert.match(copySource, /export const STATE_LABELS\b/)
  assert.match(copySource, /export const STAGES\b/)
  assert.match(copySource, /export function modeLabel\b/)
  assert.match(copySource, /export function stateLabel\b/)
})

// ---------------------------------------------------------------------------
// 体量约束
// ---------------------------------------------------------------------------

test('KbValidationPage.vue 行数 ≤ 400（如超需抽子组件）', () => {
  const lineCount = pageSource.split(/\r?\n/).length
  assert.ok(
    lineCount <= 400,
    `KbValidationPage.vue 应 ≤ 400 行（任务 5.3 约束），实际 ${lineCount}；超出请抽 ValidationForm / ValidationResult 子组件`,
  )
})

// 兜底：路径保留，便于后续工具按路径扫描
test('KbValidationPage.test.js 位于 src/views/operations/', () => {
  assert.match(here, /src\/views\/operations\/?$/)
})
