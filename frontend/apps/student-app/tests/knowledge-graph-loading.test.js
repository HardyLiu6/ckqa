import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const graphCanvasSource = readFileSync(
  resolve(__dirname, '../src/components/knowledge/GraphCanvas.vue'),
  'utf8',
)
const knowledgeGraphSource = readFileSync(
  resolve(__dirname, '../src/views/knowledge/KnowledgeGraph.vue'),
  'utf8',
)

test('图谱画布延迟加载 G6，避免图谱页路由 chunk 过重', () => {
  assert.doesNotMatch(graphCanvasSource, /import\s+\{\s*Graph\s*\}\s+from\s+['"]@antv\/g6['"]/)
  assert.match(graphCanvasSource, /import\(['"]@antv\/g6['"]\)/)
})

test('图谱画布只在加载或就绪状态挂载', () => {
  assert.match(knowledgeGraphSource, /<div\s+v-if="showCanvas"\s+class="canvas-wrap">/)
  assert.doesNotMatch(knowledgeGraphSource, /<div\s+v-show="showCanvas"\s+class="canvas-wrap">/)
})

test('图谱节点问答入口会携带课程上下文和 topic', () => {
  assert.match(
    knowledgeGraphSource,
    /const\s+askQuestionCourseId\s*=\s*computed\(\(\)\s*=>\s*store\.selectedCourseId\s*\|\|\s*preferredCourseId\.value\)/,
  )
  assert.match(knowledgeGraphSource, /const\s+query\s*=\s*\{\s*topic:\s*entity\.name\s*\}/)
  assert.match(
    knowledgeGraphSource,
    /if\s*\(askQuestionCourseId\.value\)\s*\{\s*query\.courseId\s*=\s*askQuestionCourseId\.value\s*\}/,
  )
  assert.match(
    knowledgeGraphSource,
    /router\.push\(\{\s*path:\s*['"]\/qa\/ask['"],\s*query\s*\}\)/,
  )
})

test('图谱页响应 query-only courseId 变化并重新加载课程图谱', () => {
  assert.match(
    knowledgeGraphSource,
    /watch\(\s*preferredCourseId,\s*async\s*\(courseId,\s*previousCourseId\)/,
  )
  assert.match(
    knowledgeGraphSource,
    /if\s*\(!courseId\s*\|\|\s*courseId\s*===\s*previousCourseId\)\s*\{\s*return\s*\}/,
  )
  assert.match(
    knowledgeGraphSource,
    /await\s+loadGraphForCourse\(courseId\)/,
  )
})
