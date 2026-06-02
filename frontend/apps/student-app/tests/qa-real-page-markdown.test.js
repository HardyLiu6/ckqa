import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const qaPageSource = readFileSync(resolve(__dirname, '../src/views/qa/index.vue'), 'utf8')

test('真实问答页只对 assistant 消息使用受控 Markdown 组件', () => {
  assert.match(qaPageSource, /import QaMarkdownContent from '\.\/QaMarkdownContent\.vue'/)
  assert.match(qaPageSource, /<QaMarkdownContent :content="msg\.content" \/>/)
  assert.match(qaPageSource, /class="source-cards"/)
  assert.match(qaPageSource, /class="source-type"/)
  assert.match(qaPageSource, /sourceTypeLabel\(source\)/)
  assert.match(qaPageSource, /graphrag_report/)
  assert.match(qaPageSource, /global_fallback_text_unit/)
  assert.match(qaPageSource, /source\.snippet/)
  assert.match(qaPageSource, /<div class="msg-text">\{\{ msg\.content \}\}<\/div>/)
})

test('真实问答页恢复历史会话时会重新接入运行中任务', () => {
  assert.match(qaPageSource, /resumeRunningTaskFromMessages/)
  assert.match(qaPageSource, /getQaTask\(sessionId, runningUserMessage\.taskId\)/)
  assert.match(qaPageSource, /startTaskStream\(sessionId, runningUserMessage\.taskId, pendingTask\.value\)/)
  assert.match(qaPageSource, /partialResponseText/)
  assert.match(qaPageSource, /mergePartialStreamText/)
  assert.match(qaPageSource, /afterEventSeq/)
  assert.match(qaPageSource, /eventSeq <=/)
  assert.match(qaPageSource, /Math\.min\(2, resolvePollingDelaySeconds/)
})

test('真实问答页切换到轮询时继续补齐部分回答并更新连接状态', () => {
  const fallbackBlock = qaPageSource.match(/const fallbackToPolling = [\s\S]*?\n  \}/)?.[0] ?? ''
  assert.match(fallbackBlock, /streaming: false/)
  assert.match(qaPageSource, /事件流连接已关闭，问答仍在后台运行/)
  assert.match(qaPageSource, /事件流连接中断，正在改用轮询继续接收/)
  const pollBlock = qaPageSource.match(/async function pollTask[\s\S]*?async function refreshAssistantAfterEmptySuccess/)?.[0] ?? ''
  assert.match(pollBlock, /mergePartialStreamText\(currentStreamText, partialStreamText\)/)
  assert.doesNotMatch(pollBlock, /streamText: currentStreamText \|\| partialStreamText/)
})

test('真实问答页会把流式 fallback 原因转成用户可理解的等待提示', () => {
  assert.match(qaPageSource, /function taskPendingCopy\(task\)/)
  assert.match(qaPageSource, /streamingFallbackReason/)
  assert.match(qaPageSource, /readableStreamingFallbackMessage/)
  assert.match(qaPageSource, /事件流暂不可用，正在改用轮询继续接收回答/)
  assert.match(qaPageSource, /\{\{ taskPendingCopy\(pendingTask\) \}\}/)
})

test('真实问答页运行中提示不直接透出后端工程文案', () => {
  const pendingCopyBlock = qaPageSource.match(/function taskPendingCopy\(task\)[\s\S]*?\n\}/)?.[0] ?? ''
  assert.match(pendingCopyBlock, /readableRunningTaskMessage/)
  assert.doesNotMatch(pendingCopyBlock, /return task\.routeReason \|\| task\.timeoutMessage/)
  assert.doesNotMatch(pendingCopyBlock, /低频轮询|实测|被标记/)
})

test('真实问答页用中文业务状态展示失败任务', () => {
  assert.match(qaPageSource, /qaMessageTaskStatusLabel\(msg\)/)
  assert.match(qaPageSource, /qaTaskStatusHeadline\(task\)/)
  assert.doesNotMatch(qaPageSource, /\{\{ msg\.taskStatus \}\}/)
  assert.match(qaPageSource, /taskStatus: errorPayload\.taskStatus \|\| 'failed'/)
  assert.doesNotMatch(qaPageSource, /taskStatus: pendingTask\.value\?\.taskStatus \|\| 'failed'/)
  assert.match(qaPageSource, /readableTaskFailureMessage/)
  assert.match(qaPageSource, /:class="\{ 'is-loading': !isTerminalTaskStatus\(pendingTask\.taskStatus\) \}"/)
  assert.match(qaPageSource, /<WarningFilled v-else \/>/)
})

test('真实问答页会展示可折叠的检索过程日志', () => {
  assert.match(qaPageSource, /import QaRetrievalTrace from '\.\/QaRetrievalTrace\.vue'/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:events="msg\.progressEvents"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:task-status="msg\.taskStatus"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:has-answer="Boolean\(msg\.content\)"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:source-count="msg\.sources\?\.length \|\| 0"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:events="pendingProcessEvents"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*:task-status="pendingTask\.taskStatus"/)
  assert.match(qaPageSource, /:default-open="!pendingTask\.streamText"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*<QaMarkdownContent[\s\S]*:content="pendingTask\.streamText"/)
  assert.match(qaPageSource, /<QaRetrievalTrace[\s\S]*<QaMarkdownContent[\s\S]*:streaming="Boolean\(pendingTask\.streaming\)"/)
  assert.match(qaPageSource, /progress\(payload\)/)
  assert.doesNotMatch(qaPageSource, /步骤 \{\{ index \+ 1 \}\}/)
  assert.doesNotMatch(qaPageSource, /streamed chunk count=/)
})

test('检索过程组件使用阶段归并模型而不是直接按数组最后一项展示', () => {
  const traceSource = readFileSync(resolve(__dirname, '../src/views/qa/QaRetrievalTrace.vue'), 'utf8')
  assert.match(traceSource, /buildRetrievalTimeline/)
  assert.match(traceSource, /buildRetrievalTraceSummary/)
  assert.match(traceSource, /taskStatus/)
  assert.match(traceSource, /effectiveLive/)
  assert.match(traceSource, /setInterval/)
  assert.match(traceSource, /clearInterval/)
  assert.match(traceSource, /retrieval-timeline/)
  assert.match(traceSource, /class="timeline-evidence-label"/)
  assert.match(traceSource, /<strong class="trace-evidence-title">/)
  assert.match(traceSource, /summary\.text/)
  assert.doesNotMatch(traceSource, /item\.evidence\.slice\(0, 3\)/)
  assert.doesNotMatch(traceSource, /latestRetrievalTraceEvent/)
  assert.doesNotMatch(traceSource, /visibleEvents\.value\.at\(-1\)/)
})

test('Markdown 组件支持流式渲染并按帧合并频繁更新', () => {
  const markdownSource = readFileSync(resolve(__dirname, '../src/views/qa/QaMarkdownContent.vue'), 'utf8')
  assert.match(markdownSource, /streaming/)
  assert.match(markdownSource, /requestAnimationFrame/)
  assert.match(markdownSource, /renderQaMarkdown\(props\.content, \{ streaming: props\.streaming \}\)/)
})

test('真实问答页不会因状态快照游标丢弃较早的检索过程事件', () => {
  const progressBlock = qaPageSource.match(/progress\(payload\) \{[\s\S]*?\n    \},\n    delta\(payload\)/)?.[0] ?? ''
  assert.match(progressBlock, /mergeProgressEvents\(pendingTask\.value\?\.progressEvents, \[payload\], \{ stampReceivedAt: true \}\)/)
  assert.doesNotMatch(progressBlock, /eventSeq > 0 && eventSeq <= lastEventSeq/)
})

test('真实问答页流式输出时尊重用户滚动位置', () => {
  assert.match(qaPageSource, /@scroll="handleMainScroll"/)
  assert.match(qaPageSource, /followLatestAnswerIfPinned/)
  assert.match(qaPageSource, /handleJumpToLatest/)
  assert.match(qaPageSource, /回到最新回答/)
  const deltaBlock = qaPageSource.match(/delta\(payload\) \{[\s\S]*?\n    \},\n    sources\(payload\)/)?.[0] ?? ''
  assert.match(deltaBlock, /followLatestAnswerIfPinned\(\)/)
  assert.doesNotMatch(deltaBlock, /scrollToBottom\(\)/)
})

test('真实问答页在回答成功后刷新长期学习记忆列表', () => {
  assert.match(qaPageSource, /await loadMemoryState\(selectedCourseId\.value, selectedKnowledgeBaseId\.value\)/)
  assert.match(qaPageSource, /学习记忆/)
  assert.match(qaPageSource, /Local 问答会按问题动态使用学习记忆/)
  assert.match(qaPageSource, /memoryEnabled/)
})
