import test from 'node:test'
import assert from 'node:assert/strict'

import { computeMagneticOffset } from '../src/utils/magnetic.js'

test('鼠标在吸附半径外：返回 (0,0)', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 500, y: 500 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.deepEqual(offset, { x: 0, y: 0 })
})

test('鼠标在吸附半径内：向鼠标位移、最大 maxShift', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 150, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.ok(offset.x > 0 && offset.x <= 8)
  assert.equal(offset.y, 0)
})

test('鼠标刚好在中心：返回 (0,0)，不报除零错误', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 100, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.deepEqual(offset, { x: 0, y: 0 })
})

test('位移永远不会超过 maxShift', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 100 + 79, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.ok(Math.abs(offset.x) <= 8)
  assert.ok(Math.abs(offset.y) <= 8)
})
