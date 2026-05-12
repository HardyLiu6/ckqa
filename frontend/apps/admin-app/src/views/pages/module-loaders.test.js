import test from 'node:test'
import assert from 'node:assert/strict'

import {
  loadKnowledgeBaseBuild,
  loadModulePage,
} from './module-loaders.js'
import { readFileSync } from 'node:fs'

function readSource(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

test('loadKnowledgeBaseBuild 作为命名导出可被外部直接调用', () => {
  assert.equal(typeof loadKnowledgeBaseBuild, 'function')
  assert.equal(typeof loadModulePage, 'function')
})

test('loadKnowledgeBaseBuild 的签名包含 defaultServices 兜底', () => {
  const source = readSource('./module-loaders.js')

  assert.match(
    source,
    /export async function loadKnowledgeBaseBuild\(route, query, services = defaultServices\)/,
  )
})
