/**
 * 管理员端文案常量。所有面向用户的文本应从这里引用，方便统一巡检与将来抽 i18n。
 *
 * 命名规则：`域.场景.要素`，例如 nav.section.production / status.material.parsing
 */
export const COPY = Object.freeze({
  nav: {
    sections: {
      dashboard: '工作台',
      production: '生产',
      operations: '运维',
      settings: '设置',
    },
  },
  status: {
    material: {
      pending: '待解析',
      running: '解析中',
      ready: '已就绪',
      failed: '解析失败',
    },
    knowledgeBase: {
      pending: '待构建',
      running: '构建中',
      active: '已激活',
      failed: '构建失败',
      retired: '已停用',
    },
    task: {
      pending: '已发起',
      running: '进行中',
      success: '已完成',
      cancelled: '已取消',
      failed: '异常',
    },
  },
  feedback: {
    parseRetryHint: 'PDF 解析超时，已自动重试。可手动重试或检查文件大小。',
    kbValidationLabel: '知识库验证',
    qaResponseLabel: '响应时间（高负载下）',
  },
  topbar: {
    commandPalettePlaceholder: '搜索课程 / 资料 / 知识库 / 操作',
  },
  dashboard: {
    greeting: {
      morning: '早上好',
      afternoon: '下午好',
      evening: '晚上好',
    },
    summarySentence(courseCount, materialCount, kbCount) {
      const parts = []
      if (courseCount != null) parts.push(`${courseCount} 门课程`)
      if (materialCount != null) parts.push(`${materialCount} 份资料`)
      if (kbCount != null) parts.push(`${kbCount} 个知识库`)
      if (parts.length === 0) return '欢迎回来，开始今天的工作吧。'
      return `当前看板覆盖：${parts.join(' · ')}。`
    },
    // 视觉打磨迭代（2026-05-09）新增：hero subtitle 数字化文案
    heroSubtitle: {
      withTasks(runningTaskCount, weeklyQaCount) {
        return `欢迎回来，今天有 ${runningTaskCount} 个进行中任务 和 ${weeklyQaCount} 次本周问答。`
      },
      withOnlyTasks(runningTaskCount) {
        return `欢迎回来，今天有 ${runningTaskCount} 个进行中任务；本周还没有问答。`
      },
      withOnlyQa(weeklyQaCount) {
        return `欢迎回来，暂无进行中任务；本周已有 ${weeklyQaCount} 次问答。`
      },
      empty: '欢迎回来，暂无进行中任务；本周还没有问答。',
    },
    quickActions: [
      { key: 'new-kb', label: '+ 新建知识库', to: '/app/knowledge-bases?action=create' },
      { key: 'upload', label: '↑ 上传资料', to: '/app/courses' },
      { key: 'kb-validation', label: '▷ 知识库验证', to: '/app/qa-smoke' },
      { key: 'retrieval-logs', label: '≡ 检索日志', to: '/app/retrieval-logs' },
    ],
    // 视觉打磨迭代：Style A 卡片化快捷入口（带图标 + hint），独立于旧 quickActions 以避免打断现状
    quickActionCards: [
      { id: 'create-course', label: '新建课程', hint: '从课程目录开始', icon: 'book', to: '/app/courses' },
      { id: 'upload-pdf', label: '上传资料', hint: 'PDF 解析入口', icon: 'file', to: '/app/courses' },
      { id: 'build-kb', label: '构建知识库', hint: '4 步引导式向导', icon: 'database', to: '/app/knowledge-bases?action=create' },
      { id: 'verify-kb', label: '验证知识库', hint: '抽样问答验证', icon: 'shield', to: '/app/qa-smoke' },
    ],
    sectionLabels: {
      pipeline: '生产流水线',
      activity: '近期动态',
      tasks: '进行中任务',
      quickActions: '快捷入口',
    },
    fallbackHint: '正在以分资源接口聚合数据，加载略慢。',
  },
  // ---- M7 键空间（按 design.md §8.1 落地；仅新增，保持已有 feedback.kbValidationLabel / status.* 原值不变）----
  system: {
    health: {
      eyebrow: '设置 · 系统',
      title: '系统健康',
      subtitle: '平台依赖服务的当前状态，点击刷新重新检测。',
      diagnosticsTitle: '诊断日志',
      diagnosticsEmpty: '等待健康检查返回',
      overall: {
        success: '全部服务正常',
        warning: '部分服务存在告警',
        danger: '存在不可用服务',
        blocked: '尚未完成初始化',
      },
      // 刷新按钮：`label` 默认态、`loadingLabel` 进行中态。
      refresh: {
        label: '刷新',
        loadingLabel: '刷新中',
      },
      // 服务卡片 `CkInfoTable` 的字段标签（key-value 表格的 dt 文本）。
      fields: {
        reachable: '可达性',
        ready: '就绪状态',
        path: '路径',
        message: '服务消息',
      },
      // 服务卡片 `CkInfoTable` 的布尔取值（dd 文本）。
      fieldValues: {
        reachable: '可达',
        unreachable: '不可达',
        ready: '就绪',
        notReady: '未就绪',
      },
      error: {
        title: '系统健康加载失败',
        retry: '重试',
      },
      service: {
        // `GraphRAG` 为专业术语，按 §8.2 允许保留
        mysql: { name: '操作系统数据库' },
        graphrag: { name: 'GraphRAG 问答服务' },
        pdfIngest: { name: 'PDF 解析服务' },
        minio: { name: '对象存储' },
        oneApi: { name: '模型网关' },
      },
    },
  },
  users: {
    list: {
      eyebrow: '设置 · 用户与权限',
      title: '用户',
      subtitle: '维护平台登录账号与其关联的角色。',
      empty: {
        title: '暂无用户',
        description: '管理员可在后续版本新增用户。',
      },
    },
    // 注意：此处是 COPY.users.status（用户业务态），与顶层 COPY.status（通用状态字典）不同名冲突。
    status: {
      active: '已启用',
      inactive: '已停用',
      locked: '已锁定',
    },
  },
  roles: {
    list: {
      eyebrow: '设置 · 用户与权限',
      title: '角色',
      subtitle: '平台级权限集合，用于批量授予用户能力。',
      empty: {
        title: '暂无角色',
        description: '默认提供管理员 / 教师 / 助教 / 学生四类角色。',
      },
    },
  },
  permissions: {
    list: {
      eyebrow: '设置 · 用户与权限',
      title: '权限',
      subtitle: '最小权限点清单，按资源与操作分组。',
      empty: {
        title: '暂无权限',
        description: '权限点由系统初始化生成。',
      },
    },
  },
  validation: {
    page: {
      eyebrow: '运维 · 知识库验证',
      title: '知识库验证',
      subtitle: '选择一个知识库，输入问题并发起一次抽样问答，快速检查索引是否可用。',
      empty: {
        title: '尚未发起验证',
        description: '选择知识库、填入问题后点击「发起验证」。',
      },
      stages: [
        { key: 'prepare', label: '准备输入' },
        { key: 'retrieve', label: '检索课程知识' },
        { key: 'generate', label: '生成答复' },
        { key: 'finalize', label: '整理结果' },
      ],
      // M7 · 阶段 5.1：表单区 / 结果区的静态文案，供 `views/operations/kb-validation-copy.js` 派生
      form: {
        title: '发起验证',
        kbLabel: '知识库',
        questionLabel: '问题',
        modeLabel: '验证模式',
        submit: '发起验证',
        reset: '重新发起',
      },
      result: {
        title: '验证结果',
        answerTitle: '答复',
        errorTitle: '验证失败',
        sourcesTitle: '检索依据',
        timingsTitle: '耗时',
        retryAction: '重新发起',
      },
      historyTitle: '最近 10 次验证',
    },
    mode: {
      basic: '快速',
      local: '课程内',
      global: '跨课程',
      drift: '深度',
    },
    stateLabel: {
      idle: '待开始',
      running: '验证中',
      success: '验证完成',
      failed: '验证失败',
    },
  },
  routeState: {
    comingSoon: {
      title: '敬请期待',
      description: '该入口已保留在导航结构中，后续里程碑接入业务能力。',
    },
  },
  // 课程域页面文案集中在 src/views/courses/course-page-copy.js
  // 资料域页面文案集中在 src/views/materials/material-page-copy.js
  // 知识库域页面文案集中在 src/views/knowledge-bases/kb-page-copy.js
  // 知识库构建向导文案集中在 src/views/knowledge-bases/kb-build-copy.js
  // 问答会话域页面文案集中在 src/views/qa-sessions/qa-session-copy.js
  // 这里仅做指引占位，避免 admin.js 膨胀到上千行
  course: { _docRef: './views/courses/course-page-copy.js' },
  material: { _docRef: './views/materials/material-page-copy.js' },
  knowledgeBase: { _docRef: './views/knowledge-bases/kb-page-copy.js' },
  kbBuild: { _docRef: './views/knowledge-bases/kb-build-copy.js' },
  qa: { _docRef: './views/qa-sessions/qa-session-copy.js' },
})

// ----------------------------------------------------------------------------
// M7 · 术语清洗（design.md §8.2 / §8.3）
// ----------------------------------------------------------------------------
//
// 使用场景：HealthPage 的诊断日志、KbValidationPage 的失败文案等需要把后端原始
// message 中残留的 "冒烟 / smoke / embedding / 嵌入 / 实体抽取 / P95 延迟 / MinerU"
// 等工程术语替换为面向非工程用户的平实表达。
//
// 设计要点：
// 1. `TERM_REPLACEMENT_MAP` 的 key 被当作"正则模式"而非字面量（保留 `embedding(s)?`、
//    `P95\s*延迟` 等表达式能力）；使用 `gi` 保证全局 + 大小写不敏感。
// 2. 条目顺序经过设计：较长/更具体的词先出现（如 `冒烟验证` 先于 `冒烟`），避免短词
//    先替换后吞掉长词语义，从而保证 `cleanTerms(cleanTerms(x)) === cleanTerms(x)`
//    的幂等约束。
// 3. 所有替换目标本身均不再包含任何禁用词，二次替换因此一定是不动点。
//
// 允许外部传入自定义 map（便于单测与扩展）。非字符串输入统一返回 `''`。
export const TERM_REPLACEMENT_MAP = Object.freeze({
  // 先处理完整短语，避免被后续的 "冒烟" 规则拆掉语义
  '冒烟验证': '知识库验证',
  '冒烟': '抽样',
  'smoke': 'sampling',
  // `(s)?` 保证同时覆盖 embedding / embeddings
  'embedding(s)?': '检索索引',
  '嵌入': '检索索引',
  '实体抽取': '识别课程概念',
  // `\s*` 允许 "P95延迟" 与 "P95 延迟" 两种书写
  'P95\\s*延迟': '响应时间',
  'MinerU': 'PDF 解析服务',
})

/**
 * 清洗工程术语，输出面向用户的平实文案。
 *
 * - 大小写不敏感：通过 `RegExp(..., 'gi')` 实现；
 * - 幂等：`cleanTerms(cleanTerms(x), map) === cleanTerms(x, map)`；
 * - 非字符串输入（含 `null` / `undefined` / 数字等）统一返回空串；
 * - 可注入自定义 `map`，默认使用 `TERM_REPLACEMENT_MAP`。
 *
 * @param {unknown} text 待清洗文本
 * @param {Record<string, string>} [map] 术语替换表（key 为正则模式，value 为替换串）
 * @returns {string}
 */
export function cleanTerms(text, map = TERM_REPLACEMENT_MAP) {
  if (typeof text !== 'string') return ''
  if (!map || typeof map !== 'object') return text
  let result = text
  for (const [pattern, replacement] of Object.entries(map)) {
    if (typeof pattern !== 'string' || pattern.length === 0) continue
    result = result.replace(new RegExp(pattern, 'gi'), String(replacement ?? ''))
  }
  return result
}

export default COPY
