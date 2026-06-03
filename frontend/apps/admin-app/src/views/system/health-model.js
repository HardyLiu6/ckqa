// 健康检查项的"人话"映射：后端返回 mysql / graphrag-build-runs-root 这类
// 工程标识，这里统一翻译成面向教师 / 教务的中文名称、用途说明与组件类别，
// 避免把内部技术名词直接抛给使用者。原始标识仍保留在「原始检查数据」中供排查。
const SERVICE_META = {
  mysql: { label: '业务数据库', detail: '保存课程、用户与问答记录', kind: '数据库' },
  redis: { label: '缓存服务', detail: '加速会话与高频数据读取', kind: '缓存' },
  neo4j: { label: '知识图谱库', detail: '支撑实体与关系的图谱检索', kind: '图数据库' },
  'pdf-ingest-root': { label: '课件解析目录', detail: 'PDF 课件导入与解析的存储位置', kind: '存储目录' },
  'graphrag-root': { label: '知识库目录', detail: '知识库构建的工作根目录', kind: '存储目录' },
  'graphrag-build-runs-root': { label: '构建记录目录', detail: '保存知识库构建任务的运行记录', kind: '存储目录' },
  'graphrag-api': { label: '问答引擎', detail: '负责知识检索与答案生成', kind: '在线服务' },
  'graphrag-ready': { label: '问答模型', detail: '生成答案所需的模型服务', kind: '在线服务' },
  'graphrag-output': { label: '知识库索引', detail: '问答检索所需的索引产物', kind: '索引产物' },
}

const TONE_STATUS_LABEL = {
  success: '正常',
  warning: '未就绪',
  danger: '不可达',
}

function serviceTone(reachable, ready) {
  if (!reachable) return 'danger'
  if (!ready) return 'warning'
  return 'success'
}

function serviceHint(reachable, ready) {
  if (!reachable) return '无法连接，请检查该组件是否正常运行'
  if (!ready) return '已连接，但尚未完成准备，部分功能可能不可用'
  return '运行正常'
}

function humanizeKey(key) {
  if (!key) return '未知组件'
  return String(key)
    .replace(/[-_]+/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

function resolveMeta(key) {
  return SERVICE_META[key] || { label: humanizeKey(key), detail: '系统组件', kind: '组件' }
}

// 兼容两种后端契约：当前 Java 返回 { up, items: [{ name, reachable, ready, message }] }；
// 同时保留对早期 { status, services: { key: {...} } } 形态的兜底解析。
function extractItems(payload) {
  if (Array.isArray(payload?.items)) {
    return payload.items.map((item = {}) => ({ ...item, key: item.name }))
  }
  if (payload?.services && typeof payload.services === 'object') {
    return Object.entries(payload.services).map(([key, value]) => ({ key, ...(value || {}) }))
  }
  return []
}

function resolveOverall(counts) {
  if (counts.total === 0) {
    return { tone: 'blocked', label: '暂无数据', hint: '尚未获取到任何检查结果' }
  }
  if (counts.danger > 0) {
    return { tone: 'danger', label: '存在故障', hint: `${counts.danger} 项组件无法连接，需尽快处理` }
  }
  if (counts.warning > 0) {
    return { tone: 'warning', label: '部分降级', hint: `${counts.warning} 项组件尚未就绪，请关注` }
  }
  return { tone: 'success', label: '全部正常', hint: '所有核心组件运行正常' }
}

export function normalizeHealthResponse(payload = {}) {
  const services = extractItems(payload).map((item) => {
    const key = item.key || item.name || ''
    const meta = resolveMeta(key)
    const reachable = Boolean(item.reachable)
    const ready = Boolean(item.ready)
    const tone = serviceTone(reachable, ready)

    return {
      key,
      label: meta.label,
      detail: meta.detail,
      kind: meta.kind,
      reachable,
      ready,
      tone,
      statusLabel: TONE_STATUS_LABEL[tone],
      hint: serviceHint(reachable, ready),
      message: item.message || '',
    }
  })

  const counts = {
    total: services.length,
    ok: services.filter((service) => service.tone === 'success').length,
    warning: services.filter((service) => service.tone === 'warning').length,
    danger: services.filter((service) => service.tone === 'danger').length,
  }
  const overall = resolveOverall(counts)

  return {
    up: typeof payload.up === 'boolean' ? payload.up : counts.danger === 0,
    counts,
    overallTone: overall.tone,
    overallLabel: overall.label,
    overallHint: overall.hint,
    checkedAt: payload.checkedAt || '',
    services,
    raw: payload,
  }
}
