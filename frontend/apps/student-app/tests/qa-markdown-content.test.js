import test from 'node:test'
import assert from 'node:assert/strict'
import createDOMPurify from 'dompurify'
import { JSDOM } from 'jsdom'

import { renderQaMarkdown } from '../src/views/qa/qa-markdown-renderer.js'

function render(markdown) {
  const window = new JSDOM('').window
  const purifier = createDOMPurify(window)
  return renderQaMarkdown(markdown, { purifier })
}

test('问答 Markdown 渲染支持学习材料常用格式', () => {
  const html = render(`
# 死锁

**必要条件**包括：
- 互斥
- 占有并等待

> 资源分配图可以辅助判断。

\`P1\`

\`\`\`text
wait(P1)
\`\`\`
`)

  assert.match(html, /<h1>死锁<\/h1>/)
  assert.match(html, /<strong>必要条件<\/strong>/)
  assert.match(html, /<ul>/)
  assert.match(html, /<blockquote>/)
  assert.match(html, /<code>P1<\/code>/)
  assert.match(html, /<pre><code class="language-text">wait\(P1\)\n<\/code><\/pre>/)
})

test('来源标记渲染为受控 pill 而不是神秘编号原文', () => {
  const html = render('这些条件通常一起出现。[来源 1、2]')

  assert.match(html, /class="qa-source-marker"/)
  assert.match(html, /data-source-refs="1,2"/)
  assert.match(html, />来源 1、2<\/span>/)
  assert.doesNotMatch(html, /\[来源/)
})

test('sanitizer 移除脚本、事件属性和危险协议链接', () => {
  const html = render(`
<script>alert(1)</script>
<img src=x onerror="alert(1)">
[危险链接](javascript:alert(1))
[安全链接](https://example.com/course)
`)

  assert.doesNotMatch(html, /<script/i)
  assert.doesNotMatch(html, /onerror/i)
  assert.doesNotMatch(html, /javascript:/i)
  assert.match(html, /href="https:\/\/example.com\/course"/)
  assert.match(html, /rel="noopener noreferrer"/)
})
