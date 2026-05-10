// ---------------------------------------------------------------------------
// `src/copy/admin.js` 的属性测试与具体用例
// ---------------------------------------------------------------------------
//
// 本文件落地 design.md §13.2 / §13.3（也对应 tasks.md 2.3 / requirements §6）
// 规定的两条属性：
//
//   P2 · 文案禁用术语：`COPY` 对象树里所有字符串叶子均不命中正则
//        `/冒烟|embedding|实体抽取|P95|MinerU/i`（专业术语的白名单以
//        `FORBIDDEN_TERM_WHITELIST` 列出）。
//
//   P3 · cleanTerms 幂等 + 清洗彻底：对任意 `noise + term + noise` 输入，
//        `cleanTerms(cleanTerms(x)) === cleanTerms(x)`，且输出不再命中
//        `new RegExp(term, 'i')`。
//
// 另外按 tasks.md 2.3 补充两个锚定的具体用例，让修改 `TERM_REPLACEMENT_MAP`
// 的人能一眼看到"典型输入 → 典型输出"的样子。
//
// **Validates: Requirements P2, P3, CC-1, CC-5, design §13.2, design §13.3**
// ---------------------------------------------------------------------------

import test from 'node:test'
import assert from 'node:assert/strict'
import fc from 'fast-check'

import { COPY, TERM_REPLACEMENT_MAP, cleanTerms } from './admin.js'

// ---------------------------------------------------------------------------
// P2 · 禁用术语巡检
// ---------------------------------------------------------------------------

// 覆盖 design §8.2 的 UI 可见禁用词集合；`i` 标志对应"大小写不敏感"的要求。
const FORBIDDEN_PATTERN = /冒烟|embedding|实体抽取|P95|MinerU/i

// 允许保留禁用词样式的明确白名单。按 design §8.2，下列场景允许放行：
// - COPY.system.health.service.graphrag.name = 'GraphRAG 问答服务'
//   （`GraphRAG` 不在禁用正则内，天然允许；这里登记是为了未来若引入例如
//    `GraphRAG embedding 存储` 这类含 `embedding` 的专业串时，能以路径为 key
//    显式放行。）
// - 如确需扩展，请在此处以"对象树完整路径 → 放行原因"的形式登记。
const FORBIDDEN_TERM_WHITELIST = new Set([
  // 目前 COPY 里没有必须放行的禁用词字符串，保持空。
])

/**
 * 递归收集 `COPY` 对象树里所有字符串 leaf，返回 `{ path, value }[]`。
 *
 * - 进入普通对象与数组（数组下标也计入路径）；
 * - 跳过函数 leaf（如 `COPY.dashboard.summarySentence` 等方法）；
 * - 跳过 `null / undefined / number / boolean` 等非字符串 leaf。
 */
function collectStringLeaves(node, path = []) {
  if (typeof node === 'string') {
    return [{ path: path.join('.'), value: node }]
  }
  if (Array.isArray(node)) {
    return node.flatMap((item, index) =>
      collectStringLeaves(item, [...path, String(index)]),
    )
  }
  if (node !== null && typeof node === 'object') {
    return Object.entries(node).flatMap(([key, value]) =>
      collectStringLeaves(value, [...path, key]),
    )
  }
  return []
}

test('P2 属性：COPY 字符串叶子不命中禁用术语 /冒烟|embedding|实体抽取|P95|MinerU/i', () => {
  const leaves = collectStringLeaves(COPY)
  assert.ok(leaves.length > 0, 'COPY 的字符串叶子集合不应为空')

  // `fc.constantFrom` 在有限集合上做随机采样；把 numRuns 设成至少 leaves.length * 4，
  // 保证即使随机策略有偏，绝大多数 leaf 都会被至少采样一次。
  fc.assert(
    fc.property(fc.constantFrom(...leaves), (leaf) => {
      if (FORBIDDEN_TERM_WHITELIST.has(leaf.path)) return true
      assert.doesNotMatch(
        leaf.value,
        FORBIDDEN_PATTERN,
        `COPY.${leaf.path} 命中禁用术语："${leaf.value}"`,
      )
      return true
    }),
    { numRuns: Math.max(leaves.length, 50) },
  )
})

// ---------------------------------------------------------------------------
// P3 · cleanTerms 幂等 + 清洗彻底
// ---------------------------------------------------------------------------

const TERM_KEYS = Object.keys(TERM_REPLACEMENT_MAP)

test('P3 属性：cleanTerms(noise + term + noise) 幂等，且输出不再命中 /term/i', () => {
  fc.assert(
    fc.property(
      fc.string({ maxLength: 32 }),
      fc.constantFrom(...TERM_KEYS),
      (noise, term) => {
        const input = noise + term + noise
        const once = cleanTerms(input, TERM_REPLACEMENT_MAP)
        const twice = cleanTerms(once, TERM_REPLACEMENT_MAP)

        // ① 幂等：二次 cleanTerms 的结果等于一次
        assert.equal(
          twice,
          once,
          `cleanTerms 幂等性被打破：input="${input}" once="${once}" twice="${twice}"`,
        )

        // ② 清洗彻底：输出里不再命中该禁用词的正则形式
        assert.doesNotMatch(
          once,
          new RegExp(term, 'i'),
          `cleanTerms 输出仍命中 /${term}/i：input="${input}" output="${once}"`,
        )
      },
    ),
    { numRuns: 60 },
  )
})

// ---------------------------------------------------------------------------
// 具体用例：锚定 design §8.3 的典型输入 / 输出
// ---------------------------------------------------------------------------

test('cleanTerms 将「冒烟验证」替换为「知识库验证」', () => {
  assert.equal(
    cleanTerms('冒烟验证已提交', TERM_REPLACEMENT_MAP),
    '知识库验证已提交',
  )
})

test('cleanTerms 将「MinerU」替换为「PDF 解析服务」', () => {
  assert.equal(
    cleanTerms('MinerU 超时', TERM_REPLACEMENT_MAP),
    'PDF 解析服务 超时',
  )
})
