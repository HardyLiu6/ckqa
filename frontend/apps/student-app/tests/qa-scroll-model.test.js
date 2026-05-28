import test from 'node:test'
import assert from 'node:assert/strict'

import {
  QA_AUTO_SCROLL_BOTTOM_THRESHOLD,
  isNearScrollBottom,
  resolveAutoScrollAfterUserScroll,
  shouldAutoFollowNewContent,
} from '../src/views/qa/qa-scroll-model.js'

test('贴近底部时允许流式内容自动跟随', () => {
  const metrics = { scrollTop: 904, clientHeight: 600, scrollHeight: 1500 }

  assert.equal(QA_AUTO_SCROLL_BOTTOM_THRESHOLD, 96)
  assert.equal(isNearScrollBottom(metrics), true)
  assert.equal(shouldAutoFollowNewContent({ autoScrollPinned: true, metrics }), true)
})

test('用户向上滚动后流式内容不再强制拉回底部', () => {
  const metrics = { scrollTop: 500, clientHeight: 600, scrollHeight: 1500 }

  assert.equal(isNearScrollBottom(metrics), false)
  assert.deepEqual(resolveAutoScrollAfterUserScroll(metrics), {
    autoScrollPinned: false,
    showJumpToLatest: true,
  })
  assert.equal(shouldAutoFollowNewContent({ autoScrollPinned: false, metrics }), false)
})

test('点击回到最新回答后恢复自动跟随', () => {
  const metrics = { scrollTop: 904, clientHeight: 600, scrollHeight: 1500 }

  assert.deepEqual(resolveAutoScrollAfterUserScroll(metrics), {
    autoScrollPinned: true,
    showJumpToLatest: false,
  })
  assert.equal(shouldAutoFollowNewContent({ autoScrollPinned: true, metrics }), true)
})
