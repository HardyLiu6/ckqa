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
  assert.match(qaPageSource, /source\.snippet/)
  assert.match(qaPageSource, /<div class="msg-text">\{\{ msg\.content \}\}<\/div>/)
})
