export const SMART_QA_MODE = 'smart'
export const BACKEND_QA_MODES = ['basic', 'local', 'global', 'drift', 'hybrid_v0']

export const QA_MODE_OPTIONS = [
  {
    value: SMART_QA_MODE,
    label: '智能推荐',
    shortLabel: '智能',
    description: '根据问题自动选择后端真实模式',
  },
  {
    value: 'basic',
    label: '快速问答',
    shortLabel: 'Basic',
    description: '轻量回答事实、定义和常见概念',
  },
  {
    value: 'local',
    label: '精确定位',
    shortLabel: 'Local',
    description: '适合按教材章节、资料片段定位回答',
  },
  {
    value: 'global',
    label: '全局综述',
    shortLabel: 'Global',
    description: '适合课程整体脉络、主题综述',
  },
  {
    value: 'drift',
    label: '探索扩展',
    shortLabel: 'Drift',
    description: '适合关联、延伸和开放探索',
  },
  {
    value: 'hybrid_v0',
    label: '混合检索 Beta',
    shortLabel: 'Hybrid',
    description: '融合快速检索与证据校验，适合手动深查',
  },
]

const ROUTING_RULES = [
  {
    mode: 'global',
    reason: '问题在请求整体综述，使用 global 做全局主题整理。',
    patterns: [/综述|概括|总结|整体|全局|主题|脉络|知识体系|框架|overview/i],
  },
  {
    mode: 'drift',
    reason: '问题强调关联或扩展探索，使用 drift 拓展相关知识。',
    patterns: [/关联|联系|扩展|延伸|发散|迁移|类似|对比|比较|影响|应用|探索/i],
  },
  {
    mode: 'local',
    reason: '问题带有章节或课程资料定位信号，使用 local 精确检索课程资料。',
    patterns: [/第\s*\d+\s*(章|节|讲|页)|章节|教材|课件|课程资料|资料中|原文|公式|例题|图表/i],
  },
]

const HYBRID_BETA_PATTERNS = [
  /综合|融合|证据|依据|来源|佐证|交叉验证|更可靠|深查/i,
  /比较|对比|关联|关系|联系/i,
]

export function isBackendQaMode(mode) {
  return BACKEND_QA_MODES.includes(mode)
}

export function resolveQaMode(question, selectedMode = SMART_QA_MODE, options = {}) {
  if (isBackendQaMode(selectedMode)) {
    return {
      mode: selectedMode,
      fromSmart: false,
      reason: `已手动选择 ${selectedMode} 模式。`,
    }
  }

  const normalizedQuestion = String(question ?? '').trim()
  if (shouldUseHybridBeta(normalizedQuestion, options)) {
    return {
      mode: 'hybrid_v0',
      fromSmart: true,
      reason: '已开启 Beta，问题需要更强证据融合，使用混合检索 Beta。',
    }
  }

  for (const rule of ROUTING_RULES) {
    if (rule.patterns.some((pattern) => pattern.test(normalizedQuestion))) {
      return {
        mode: rule.mode,
        fromSmart: true,
        reason: rule.reason,
      }
    }
  }

  return {
    mode: 'basic',
    fromSmart: true,
    reason: '问题更像事实或定义查询，使用 basic 快速回答。',
  }
}

export function shouldUseHybridBeta(question, options = {}) {
  if (!options.allowHybridBeta) {
    return false
  }
  const normalizedQuestion = String(question ?? '').trim()
  if (!normalizedQuestion) {
    return false
  }
  const highEvidence = HYBRID_BETA_PATTERNS.some((pattern) => pattern.test(normalizedQuestion))
  const contextualFollowUp = Boolean(options.hasConversationContext)
    && /它|这个|上面|前者|后者|刚才|继续|关系|联系/.test(normalizedQuestion)
  return highEvidence || contextualFollowUp
}

export function getModeOption(mode) {
  return QA_MODE_OPTIONS.find((option) => option.value === mode) ?? QA_MODE_OPTIONS[0]
}
