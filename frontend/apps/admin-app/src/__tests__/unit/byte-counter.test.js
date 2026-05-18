import test from 'node:test'
import assert from 'node:assert/strict'
import { utf8ByteLength, formatBytes } from '../../views/pages/prompt-builder/byte-counter.js'

test('utf8ByteLength 空字符串为 0', () => {
  assert.equal(utf8ByteLength(''), 0)
  assert.equal(utf8ByteLength(null), 0)
  assert.equal(utf8ByteLength(undefined), 0)
})

test('utf8ByteLength 纯 ASCII 等于字符数', () => {
  assert.equal(utf8ByteLength('hello'), 5)
})

test('utf8ByteLength 中文每字符 3 字节', () => {
  assert.equal(utf8ByteLength('你好'), 6)
})

test('utf8ByteLength 中英文混合正确累计', () => {
  assert.equal(utf8ByteLength('a你b好c'), 9)
})

test('formatBytes 小于 1024 显示 B', () => {
  assert.equal(formatBytes(500), '500 B')
})

test('formatBytes 大于等于 1024 显示 KB 一位小数', () => {
  assert.equal(formatBytes(1024), '1.0 KB')
  assert.equal(formatBytes(8400), '8.2 KB')
})
