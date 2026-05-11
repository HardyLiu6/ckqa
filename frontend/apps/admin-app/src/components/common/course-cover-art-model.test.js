import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PALETTES,
  pickPalette,
  pickGlyph,
  hashString,
} from './course-cover-art-model.js'

test('PALETTES 提供 6 套调色板，每套都有 bgFrom/bgTo/plateRing/accent', () => {
  assert.equal(PALETTES.length, 6)
  for (const palette of PALETTES) {
    assert.ok(palette.bgFrom?.startsWith('#'))
    assert.ok(palette.bgTo?.startsWith('#'))
    assert.ok(palette.plateRing?.startsWith('#'))
    assert.ok(palette.accent?.startsWith('#'))
  }
})

test('hashString 对相同输入产出相同 32-bit 整数', () => {
  assert.equal(hashString('crs-123'), hashString('crs-123'))
  assert.notEqual(hashString('crs-123'), hashString('crs-124'))
})

test('hashString 空 / null 输入返回 0', () => {
  assert.equal(hashString(''), 0)
  assert.equal(hashString(null), 0)
  assert.equal(hashString(undefined), 0)
})

test('pickPalette 对同 seed 稳定，分布在 PALETTES 范围内', () => {
  const first = pickPalette('crs-20260101-120000')
  const second = pickPalette('crs-20260101-120000')
  assert.equal(first, second)
  assert.ok(PALETTES.includes(first))
})

test('pickPalette seed 缺省回退到默认调色板，不抛错', () => {
  assert.equal(pickPalette(null), PALETTES[0])
  assert.equal(pickPalette(''), PALETTES[0])
})

test('pickGlyph 中文取首字', () => {
  assert.equal(pickGlyph('操作系统2026春'), '操')
  assert.equal(pickGlyph('  公开访问演示课  '), '公')
})

test('pickGlyph ASCII 取前两字符并大写', () => {
  assert.equal(pickGlyph('Smoke GraphRAG Isolation 20260101'), 'SM')
  assert.equal(pickGlyph('osCourse'), 'OS')
  assert.equal(pickGlyph('a'), 'A')
})

test('pickGlyph 混合：首字符是中文时只取 1 字', () => {
  assert.equal(pickGlyph('课 Course'), '课')
})

test('pickGlyph 空 / null 回退到「课」', () => {
  assert.equal(pickGlyph(''), '课')
  assert.equal(pickGlyph(null), '课')
  assert.equal(pickGlyph(undefined), '课')
  assert.equal(pickGlyph('   '), '课')
})
