import test from 'node:test'
import assert from 'node:assert/strict'

import {
  computeRailTop,
  getRailHeight,
  shouldShowRail,
  RAIL_HEIGHT_EXPANDED,
  RAIL_HEIGHT_COLLAPSED,
  RAIL_WIDTH,
} from './active-rail-model.js'

test('RAIL_HEIGHT_EXPANDED 为 36', () => {
  assert.equal(RAIL_HEIGHT_EXPANDED, 36)
})

test('RAIL_HEIGHT_COLLAPSED 为 40', () => {
  assert.equal(RAIL_HEIGHT_COLLAPSED, 40)
})

test('RAIL_WIDTH 为 3', () => {
  assert.equal(RAIL_WIDTH, 3)
})

test('getRailHeight 展开态返回 36', () => {
  assert.equal(getRailHeight(false), 36)
})

test('getRailHeight 折叠态返回 40', () => {
  assert.equal(getRailHeight(true), 40)
})

test('computeRailTop 展开态让 rail 在 item 垂直居中', () => {
  // list top=100, item top=160, item height=36
  // itemCenter = 160 + 18 = 178, railCenter 需要在 178，因 rail 高度 36，rail top = 178 - 100 - 18 = 60
  const result = computeRailTop({ top: 100 }, { top: 160, height: 36 }, false)
  assert.equal(result, 60)
})

test('computeRailTop 折叠态使用更高的 rail（40px）居中', () => {
  // list top=50, item top=130, item height=40
  // itemCenter = 130 + 20 = 150, rail top = 150 - 50 - 20 = 80
  const result = computeRailTop({ top: 50 }, { top: 130, height: 40 }, true)
  assert.equal(result, 80)
})

test('computeRailTop 无 active item 时返回 0', () => {
  assert.equal(computeRailTop({ top: 100 }, null, false), 0)
})

test('computeRailTop 列表 rect 缺失时返回 0', () => {
  assert.equal(computeRailTop(null, { top: 100, height: 36 }, false), 0)
})

test('computeRailTop 不会产生负值（item 在 list 上方时夹到 0）', () => {
  const result = computeRailTop({ top: 500 }, { top: 100, height: 36 }, false)
  assert.equal(result, 0)
})

test('shouldShowRail 有 active rect 且高度大于 0 时返回 true', () => {
  assert.equal(shouldShowRail({ top: 100, height: 36 }), true)
})

test('shouldShowRail 无 active rect 时返回 false', () => {
  assert.equal(shouldShowRail(null), false)
})

test('shouldShowRail 高度为 0 时返回 false（避免首次挂载闪烁）', () => {
  assert.equal(shouldShowRail({ top: 100, height: 0 }), false)
})
