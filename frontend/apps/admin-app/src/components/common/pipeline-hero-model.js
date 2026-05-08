export const PIPELINE_STAGES = Object.freeze([
  { key: 'courses', title: '01 课程', hint: '已开课程', route: '/app/courses' },
  { key: 'materials', title: '02 资料', hint: '资料就绪与待解析', route: '/app/materials' },
  { key: 'knowledgeBases', title: '03 知识库', hint: '构建中 / 总数', route: '/app/knowledge-bases', runningKey: 'knowledgeBaseRunningCount' },
  { key: 'activation', title: '04 激活', hint: '已激活的知识库', route: '/app/knowledge-bases?activated=1' },
  { key: 'qa', title: '05 问答', hint: '本周问答量 + 响应', route: '/app/qa-sessions' },
])

const NUMBER_FORMATTER = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })
function compact(n) {
  if (typeof n !== 'number' || Number.isNaN(n)) return '—'
  if (n < 1000) return String(n)
  return NUMBER_FORMATTER.format(n).toLowerCase()
}

const RESOLVERS = {
  courses(summary) {
    return {
      primary: compact(summary.courseCount),
      secondary: '',
      runningCount: 0,
    }
  },
  materials(summary) {
    const total = summary.materialCount
    const ready = summary.materialReadyCount
    const pending = summary.materialPendingCount
    const primary = total != null && ready != null ? `${total}/${ready}` : compact(total)
    const secondary = pending != null ? `${pending} 待解析` : ''
    return { primary, secondary, runningCount: 0 }
  },
  knowledgeBases(summary) {
    const running = summary.knowledgeBaseRunningCount || 0
    const total = summary.knowledgeBaseCount || 0
    const primary = running > 0 ? `${running}/${total} 构建中` : compact(total)
    const secondary = (summary.knowledgeBaseRunningPercents || [])
      .map((p) => `${p}%`)
      .join(' / ')
    return { primary, secondary, runningCount: running }
  },
  activation(summary) {
    const primary = compact(summary.activeKbCount)
    const secondary = summary.activeKbVersion ? `最新 ${summary.activeKbVersion}` : ''
    return { primary, secondary, runningCount: 0 }
  },
  qa(summary) {
    const primary = compact(summary.qaSessionCount)
    const secondary = summary.qaResponseTimeMs != null
      ? `响应 ${summary.qaResponseTimeMs}ms（高负载下）`
      : ''
    return { primary, secondary, runningCount: 0 }
  },
}

export function resolveStageMetric(stage, summary) {
  if (!summary) return { primary: '—', secondary: '', runningCount: 0 }
  const fn = RESOLVERS[stage.key]
  return fn ? fn(summary) : { primary: '—', secondary: '', runningCount: 0 }
}

export function isStageActive(stage, summary) {
  if (!summary) return false
  if (summary.activeKey === stage.key) return true
  if (stage.runningKey && summary[stage.runningKey] > 0) return true
  return false
}

export function buildPipelineNavTarget(stage, scopeParams) {
  const [path, query = ''] = stage.route.split('?')
  const queryObject = { ...Object.fromEntries(new URLSearchParams(query)) }
  if (stage.key === 'knowledgeBases') queryObject.status = 'running'
  if (scopeParams?.courseId) queryObject.courseId = scopeParams.courseId
  return { path, query: queryObject }
}
