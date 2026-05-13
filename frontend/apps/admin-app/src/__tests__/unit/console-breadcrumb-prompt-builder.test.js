import test from 'node:test'
import assert from 'node:assert/strict'
import { buildConsoleBreadcrumbItems } from '../../layouts/console-breadcrumb-model.js'

function route(name, params = {}, query = {}, meta = {}) {
  return { name, params, query, meta: { navGroup: 'knowledge', ...meta } }
}

test('prompt-builder 路由 + buildRunId 完整 → 面包屑包含构建向导链接', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    { kbId: '12' },
    { buildRunId: '1247' },
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识库构建', '知识库列表', '构建向导 · STEP 04', '手动调优提示词'])
  const builderLink = items.find((i) => i.label === '构建向导 · STEP 04')
  assert.equal(builderLink.kind, 'link')
})

test('prompt-builder 路由 + 缺 buildRunId → 父链降级到知识库详情', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    { kbId: '12' },
    {},
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识库构建', '知识库列表', '构建向导', '手动调优提示词'])
})

test('prompt-builder 路由 + 缺 kbId 与 buildRunId → 无构建向导父链', () => {
  const items = buildConsoleBreadcrumbItems(route(
    'knowledge-base-prompt-builder',
    {},
    {},
    { title: '手动调优提示词' },
  ))
  const labels = items.map((i) => i.label)
  assert.deepEqual(labels, ['知识库构建', '知识库列表', '手动调优提示词'])
})
