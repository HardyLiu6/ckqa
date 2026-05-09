import test from 'node:test'
import assert from 'node:assert/strict'

import {
  useMaterialLifecycle,
  resolveMaterialExportPayload,
  createMaterialExportTaskOptions,
  createParallelParseTaskOptions,
  createExportMissingTaskOptions,
} from './useMaterialLifecycle.js'

test('useMaterialLifecycle 工厂函数返回聚合的生命周期动作集', () => {
  const lifecycle = useMaterialLifecycle()
  assert.equal(typeof lifecycle.resolveMaterialExportPayload, 'function')
  assert.equal(typeof lifecycle.createMaterialExportTaskOptions, 'function')
  assert.equal(typeof lifecycle.createParallelParseTaskOptions, 'function')
  assert.equal(typeof lifecycle.createExportMissingTaskOptions, 'function')
})

test('useMaterialLifecycle 返回的函数引用与独立导出一致（确保单一来源）', () => {
  const lifecycle = useMaterialLifecycle()
  assert.equal(lifecycle.resolveMaterialExportPayload, resolveMaterialExportPayload)
  assert.equal(lifecycle.createMaterialExportTaskOptions, createMaterialExportTaskOptions)
  assert.equal(lifecycle.createParallelParseTaskOptions, createParallelParseTaskOptions)
  assert.equal(lifecycle.createExportMissingTaskOptions, createExportMissingTaskOptions)
})

test('useMaterialLifecycle 接受任意 context 参数（保留扩展空间）', () => {
  assert.doesNotThrow(() => {
    useMaterialLifecycle({ scopeStore: { requestParams: () => ({}) } })
  })
  assert.doesNotThrow(() => useMaterialLifecycle())
})
