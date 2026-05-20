// 密码强度评估
// 评分规则尽量与服务端口径保持一致：
// - 长度 >= 8 视为基础合格
// - 同时包含数字、字母（大写或小写）、符号会逐级加分
// - 重复字符（aaaa）或连续序列（abcd / 1234）会扣 1 分
// 评分范围 0..4，对应 weak / fair / good / strong 四档

const COMMON_WEAK_PASSWORDS = new Set([
  '12345678',
  '11111111',
  '00000000',
  'abcdefgh',
  'password',
  'qwerty12',
  'iloveyou',
  'Abcd1234',
  'Ckqa1234',
])

const SEQUENCES = ['0123456789', 'abcdefghijklmnopqrstuvwxyz', 'qwertyuiop', 'asdfghjkl', 'zxcvbnm']

function hasSequence(value) {
  const lower = value.toLowerCase()
  for (const seq of SEQUENCES) {
    for (let i = 0; i <= seq.length - 4; i += 1) {
      const chunk = seq.slice(i, i + 4)
      if (lower.includes(chunk) || lower.includes(reverseString(chunk))) {
        return true
      }
    }
  }
  return false
}

function reverseString(value) {
  return value.split('').reverse().join('')
}

function hasRepeats(value) {
  return /(.)\1{2,}/.test(value)
}

const LEVEL_LABEL = {
  0: '强度过低',
  1: '强度偏弱',
  2: '强度一般',
  3: '强度良好',
  4: '强度优秀',
}

const LEVEL_TIP = {
  0: '至少 8 位，建议组合字母、数字与符号',
  1: '建议补充数字、大小写字母或符号',
  2: '加入大小写字母或符号会更安全',
  3: '基本达标，再加一点变化更好',
  4: '已经足够安全，可放心使用',
}

const LEVEL_TONE = {
  0: 'weak',
  1: 'weak',
  2: 'fair',
  3: 'good',
  4: 'strong',
}

export function evaluatePasswordStrength(value) {
  const password = typeof value === 'string' ? value : ''

  if (!password) {
    return {
      score: 0,
      level: 'weak',
      label: LEVEL_LABEL[0],
      tip: LEVEL_TIP[0],
    }
  }

  let score = 0

  if (password.length >= 8) score += 1
  if (password.length >= 12) score += 1
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
  if (/\d/.test(password)) score += 1
  if (/[^a-zA-Z\d]/.test(password)) score += 1

  if (hasRepeats(password) || hasSequence(password)) {
    score -= 1
  }

  if (COMMON_WEAK_PASSWORDS.has(password)) {
    score = Math.min(score, 1)
  }

  if (score < 0) score = 0
  if (score > 4) score = 4

  return {
    score,
    level: LEVEL_TONE[score],
    label: LEVEL_LABEL[score],
    tip: LEVEL_TIP[score],
  }
}

export const __TEST_HELPERS__ = {
  hasRepeats,
  hasSequence,
}
