import {
  resolveBuildConfirmQuery,
  resolveBuildStepQuery,
} from './module-page-model.js'

export const BUILD_STEP_LABELS = {
  material: '选择课程资料',
  parse: '解析状态检查',
  export: '导出图谱输入',
  prompt: '提示词调优',
  index: '创建索引',
  qa_check: '问答效果验证',
}

export const BUILD_STEP_KEYS = Object.keys(BUILD_STEP_LABELS)

const REQUIRED_EXPORT_FILES = [
  'graphrag_normalized_docs.json',
  'graphrag_section_docs.json',
  'graphrag_page_docs.json',
]

const defaultTimeline = [
  { label: '数据同步', state: 'ready', detail: '等待真实接口接入' },
  { label: '权限检查', state: 'ready', detail: '已按路由元信息保护' },
  { label: '业务操作', state: 'pending', detail: '等待服务返回真实数据' },
]

const configs = {
  courses: {
    variant: 'table',
    dataSource: 'live',
    eyebrow: 'Course Operations',
    summary: '从课程进入资料、知识库与问答闭环，管理员可管理全局课程，教师只看授权课程。',
    primaryAction: {
      label: '新建课程',
      permission: 'course:write',
      disabled: false,
      title: '创建课程',
    },
    secondaryAction: null,
    filters: [
      { key: 'status', label: '课程状态', columnIndex: 1, options: ['全部', 'active', 'archived'] },
    ],
    columns: ['课程', '状态', '资料', '知识库', '最近索引', '更新时间'],
    rows: [],
  },
  'course-detail': {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'Course Detail',
    summary: '详情页统一承载概览、资料、知识库、课程成员、问答会话与操作日志。',
    primaryAction: { label: '管理课程资料', permission: 'material:write' },
    secondaryAction: { label: '课程成员', permission: 'membership:read' },
    facts: ['概览', '课程资料', '知识库', '课程成员', '问答会话', '操作日志'],
    timeline: defaultTimeline,
  },
  'material-detail': {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'Material Lifecycle',
    summary: '资料详情只处理解析、查看解析结果、导出 GraphRAG 输入和跳转构建向导。',
    primaryAction: { label: '触发解析', permission: 'material:write' },
    secondaryAction: { label: '导出输入', permission: 'material:write' },
    facts: ['课程资料 ID', '资料对象 ID', '文件名', 'MD5', '解析状态', 'MinerU 批次 ID'],
    timeline: [
      { label: '上传/登记', state: 'ready', detail: '沿用已有 pdf_ingest 与数据库记录' },
      { label: 'MinerU 解析', state: 'pending', detail: '等待用户触发或查看状态' },
      { label: 'GraphRAG 导出', state: 'pending', detail: '生成 section_docs/page_docs' },
    ],
  },
  'parse-results': {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'Parse Artifacts',
    summary: '首版只做只读产物查看与下载入口，不内置复杂 JSON 编辑器。',
    primaryAction: { label: '查看产物', permission: 'material:read' },
    secondaryAction: { label: '下载 JSON', permission: 'material:read' },
    facts: ['content_list_json', 'model_json', 'layout_json', 'markdown', 'origin_pdf', 'GraphRAG 投影'],
    timeline: defaultTimeline,
  },
  'knowledge-bases': {
    variant: 'table',
    dataSource: 'live',
    eyebrow: 'Knowledge Base',
    summary: '管理课程知识库实例，重点看激活索引、最近构建状态和进入构建向导。',
    primaryAction: {
      label: '新建知识库',
      permission: 'kb:write',
      disabled: false,
      title: '创建知识库',
    },
    secondaryAction: null,
    filters: [
      { key: 'status', label: '知识库状态', columnIndex: 2, options: ['全部', 'draft', 'active', 'archived'] },
    ],
    columns: ['知识库', '所属课程', '状态', '激活索引', '最近运行', '更新时间'],
    rows: [],
  },
  'knowledge-base-detail': {
    variant: 'overview',
    dataSource: 'live',
    eyebrow: 'Knowledge Base Detail',
    summary: '知识库详情突出当前激活版本、文档映射、索引运行、问答验证与运行日志。',
    primaryAction: { label: '进入构建向导', permission: 'kb:write' },
    secondaryAction: { label: '查看索引运行', permission: 'kb:read' },
    facts: ['概览', '文档映射', '索引运行', '问答验证', '运行日志'],
    timeline: [
      { label: '文档映射', state: 'ready', detail: '等待真实文档列表接入' },
      { label: '索引运行', state: 'pending', detail: '从构建向导创建' },
      { label: '问答可用性', state: 'pending', detail: '由当前激活索引决定是否可问答' },
    ],
  },
  'knowledge-base-build': {
    variant: 'workflow',
    dataSource: 'live',
    eyebrow: 'Build Wizard',
    summary: '构建向导用带前置条件的可跳步流程展示真实链路，不把失败日志藏起来。',
    primaryAction: { label: '继续下一步', permission: 'kb:write' },
    secondaryAction: { label: '查看最近任务', permission: 'kb:read' },
    workflowSteps: [
      {
        key: 'material',
        label: '选择课程资料',
        state: 'done',
        status: 'done',
        detail: '选择已解析或待解析资料',
        conditions: ['课程已创建', '当前用户具备课程资料读取权限'],
        actionLabel: '确认课程资料',
        logLabel: '查看资料记录',
      },
      {
        key: 'parse',
        label: '解析状态检查',
        state: 'done',
        status: 'done',
        detail: '资料需达到 done 后才能建索引',
        conditions: ['资料对象已上传', 'MinerU 解析状态为 done'],
        actionLabel: '刷新解析状态',
        logLabel: '查看解析日志',
      },
      {
        key: 'export',
        label: '导出 GraphRAG 输入',
        state: 'ready',
        status: 'ready',
        detail: '生成 section_docs.json / page_docs.json',
        conditions: ['解析结果存在', '标准化文档通过导出校验'],
        actionLabel: '导出输入文件',
        logLabel: '查看导出记录',
      },
      {
        key: 'index',
        label: '创建索引运行',
        state: 'blocked',
        status: 'blocked',
        detail: '等待导出输入确认',
        conditions: ['GraphRAG 导出产物存在', 'output/lancedb 可写'],
        actionLabel: '开始构建索引',
        logLabel: '查看索引日志',
      },
      {
        key: 'smoke',
        label: '问答冒烟验证',
        state: 'blocked',
        status: 'blocked',
        detail: '验证会话进入问答列表并可过滤',
        conditions: ['索引运行成功', 'Java /api/v1 问答入口可用'],
        actionLabel: '发起冒烟验证',
        logLabel: '查看验证会话',
      },
    ],
  },
  'index-run-detail': {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'Index Run',
    summary: '让管理员和教师快速判断一次索引任务是否成功、失败在哪里。',
    primaryAction: { label: '重试任务', permission: 'kb:write' },
    secondaryAction: { label: '查看日志', permission: 'kb:read' },
    facts: ['知识库 ID', '引擎', '索引版本', '状态', '开始/结束时间', '索引产物', '失败信息'],
    timeline: defaultTimeline,
  },
  'qa-sessions': {
    variant: 'table',
    dataSource: 'mock',
    eyebrow: 'QA Operations',
    summary: '问答运维列表必须区分正式问答与构建向导产生的冒烟验证会话。',
    primaryAction: { label: '查看正式问答', permission: 'qa:read' },
    secondaryAction: { label: '包含冒烟验证', permission: 'qa:read' },
    filters: [
      { key: 'sessionType', label: '会话类型', columnIndex: 5, options: ['全部', '正式问答', '冒烟验证'] },
      { key: 'status', label: '任务状态', columnIndex: 4, options: ['全部', 'success', 'running', 'failed'] },
    ],
    columns: ['会话', '用户', '课程', '知识库', '状态', '类型'],
    rows: [
      ['期末复习问题', 'student-a', '操作系统', 'OS 知识库', 'success', '正式问答'],
      ['构建后冒烟验证', 'teacher-a', '操作系统', 'OS 知识库', 'success', '冒烟验证'],
      ['索引切换验证', 'teacher-b', '计算机网络', 'CN 课程库', 'running', '冒烟验证'],
    ],
  },
  'qa-session-detail': {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'QA Session Detail',
    summary: '详情页关注消息、任务状态、查询模式、心跳时间与关联检索日志。',
    primaryAction: { label: '查看任务状态', permission: 'qa:read' },
    secondaryAction: { label: '检索日志', permission: 'qa:read' },
    facts: ['消息列表', '任务状态', 'local/global/drift/basic', '心跳时间', '完成时间', '错误信息'],
    timeline: defaultTimeline,
  },
  users: {
    variant: 'table',
    dataSource: 'mock',
    eyebrow: 'Users',
    summary: '用户列表服务于管理端 RBAC，首版保持简单主体和角色可见性。',
    primaryAction: { label: '新建用户', permission: 'user:write' },
    secondaryAction: { label: '分配角色', permission: 'role:write' },
    filters: [
      { key: 'role', label: '角色', columnIndex: 3, options: ['全部', 'admin', 'teacher', 'assistant', 'auditor'] },
    ],
    columns: ['用户名', '展示名称', '状态', '角色', '最近登录'],
    rows: [
      ['admin', '平台管理员', 'active', 'admin', '今天'],
      ['teacher-a', '王老师', 'active', 'teacher', '昨天'],
      ['assistant-a', '课程助教', 'active', 'assistant', '本周'],
    ],
  },
  roles: {
    variant: 'overview',
    dataSource: 'mock',
    eyebrow: 'RBAC Matrix',
    summary: '首版不做复杂权限编辑器，只保留角色列表、权限分组、勾选矩阵和保存确认。',
    primaryAction: { label: '保存矩阵', permission: 'role:write' },
    secondaryAction: { label: '变更确认', permission: 'audit:read' },
    facts: ['course:*', 'material:*', 'kb:*', 'qa:*', 'membership:*', 'user:*', 'role:*', 'audit:*'],
    timeline: defaultTimeline,
  },
  'course-memberships': {
    variant: 'table',
    dataSource: 'mock',
    eyebrow: 'Course Memberships',
    summary: '课程成员决定教师和助教的数据范围，是前端权限显示之外的第二层边界。',
    primaryAction: { label: '添加成员', permission: 'membership:write' },
    secondaryAction: { label: '调整角色', permission: 'membership:write' },
    filters: [
      { key: 'courseRole', label: '课程内角色', columnIndex: 2, options: ['全部', 'student', 'teacher', 'assistant'] },
    ],
    columns: ['用户', '课程', '课程内角色', '状态', '授权来源'],
    rows: [
      ['teacher-a', '操作系统', 'teacher', 'active', '手动授权'],
      ['assistant-a', '操作系统', 'assistant', 'active', '教师邀请'],
      ['student-a', '数据结构', 'student', 'active', '课程导入'],
    ],
  },
}

const fallbackConfig = {
  variant: 'overview',
  dataSource: 'mock',
  eyebrow: 'CKQA',
  summary: '该页面已进入路由结构，等待后续业务能力接入。',
  primaryAction: { label: '返回工作台', to: '/app/dashboard' },
  secondaryAction: { label: '查看结构文档' },
  facts: ['路由', '权限', '状态'],
  timeline: defaultTimeline,
}

export function getModulePageConfig(routeName) {
  return configs[routeName] ?? fallbackConfig
}

export function getRowCells(row = []) {
  if (Array.isArray(row)) {
    return row
  }

  return Array.isArray(row?.cells) ? row.cells : []
}

export function filterRowsByFilters(rows = [], filters = [], values = {}) {
  return rows.filter((row) =>
    filters.every((filter) => {
      const selected = values[filter.key] ?? '全部'

      if (selected === '全部') {
        return true
      }

      if (!Number.isInteger(filter.columnIndex)) {
        return true
      }

      return String(getRowCells(row)[filter.columnIndex] ?? '') === String(selected)
    }),
  )
}

export function resolveActiveWorkflowStep(steps = [], activeKey = '') {
  return steps.find((step) => step.key === activeKey) ?? steps[0] ?? null
}

export function isWorkflowPrimaryActionDisabled(step) {
  return step?.status === 'blocked'
}

export function resolveBuildProgress(steps = []) {
  const total = steps.length
  const counts = {
    done: 0,
    running: 0,
    failed: 0,
    ready: 0,
    blocked: 0,
  }

  for (const step of steps) {
    const status = normalizeWorkflowStatus(step?.status ?? step?.state)

    if (Object.prototype.hasOwnProperty.call(counts, status)) {
      counts[status] += 1
    }
  }

  const percent = total > 0 ? Math.round((counts.done / total) * 100) : 0
  const detail = [
    ['running', '个步骤执行中'],
    ['ready', '个步骤可执行'],
    ['blocked', '个步骤阻塞'],
    ['failed', '个步骤失败'],
  ]
    .filter(([key]) => counts[key] > 0)
    .map(([key, label]) => `${counts[key]} ${label}`)
    .join(' · ')

  return {
    done: counts.done,
    total,
    percent,
    counts,
    summary: `已完成 ${counts.done}/${total} · ${percent}%`,
    detail,
  }
}

export function resolveBuildDefaultStepKey(steps = []) {
  const actionableStep = steps.find((step) =>
    ['failed', 'running', 'ready'].includes(normalizeWorkflowStatus(step?.status ?? step?.state)),
  )

  return actionableStep?.key ?? steps.at(-1)?.key ?? 'material'
}

export function resolveBuildStepNavigation(steps = [], activeKey = '') {
  const keys = steps.length > 0 ? steps.map((step) => step.key) : BUILD_STEP_KEYS
  const activeIndex = Math.max(0, keys.indexOf(activeKey))
  const previousKey = activeIndex > 0 ? keys[activeIndex - 1] : ''

  if (!previousKey) {
    return {
      previousKey: '',
      previousLabel: '',
      disabled: true,
    }
  }

  const previous = steps.find((step) => step.key === previousKey)

  return {
    previousKey,
    previousLabel: `返回第 ${String(activeIndex).padStart(2, '0')} 步：${previous?.label ?? BUILD_STEP_LABELS[previousKey] ?? previousKey}`,
    disabled: false,
  }
}

export function resolveMaterialConfirmTarget(materials = []) {
  return materials.length > 0 && materials.every((material) => isParseComplete(material))
    ? 'export'
    : 'parse'
}

export function resolveParseTaskRows(materials = []) {
  return materials.map((material) => {
    const status = normalizeParseStatus(material.parseState ?? material.parseStatus ?? material.status)
    const rawProgress = Number(material.parseProgress ?? material.progress)
    const hasProgress = Number.isFinite(rawProgress)
    const percentByStatus = {
      done: 100,
      pending: 0,
      running: hasProgress ? rawProgress : 50,
      failed: hasProgress ? rawProgress : 0,
    }
    const detailByStatus = {
      done: '解析完成',
      pending: '等待解析',
      running: '解析进行中',
      failed: material.failureReason ?? material.errorMessage ?? material.message ?? '解析失败',
    }

    return {
      id: String(material.id ?? material.materialId ?? material.pdfFileId ?? ''),
      title: resolveMaterialTitle(material),
      status,
      percent: clampPercent(percentByStatus[status] ?? 0),
      detail: detailByStatus[status] ?? '状态未知',
    }
  })
}

export function resolveExportArtifactRows(materials = [], parseResultsByMaterialId = {}) {
  const rows = materials.map((material) => {
    const id = String(material.id ?? material.materialId ?? material.pdfFileId ?? '')
    const parseResults = parseResultsByMaterialId instanceof Map
      ? parseResultsByMaterialId.get(id) ?? parseResultsByMaterialId.get(material.id)
      : parseResultsByMaterialId[id] ?? parseResultsByMaterialId[material.id]
    const fileNames = new Set(resolveParseResultFiles(parseResults))
    const requiredFiles = REQUIRED_EXPORT_FILES.map((fileName) => ({
      fileName,
      status: fileNames.has(fileName) ? 'complete' : 'missing',
    }))
    const status = requiredFiles.every((file) => file.status === 'complete') ? 'complete' : 'missing'

    return {
      id,
      title: resolveMaterialTitle(material),
      status,
      requiredFiles,
    }
  })

  return {
    completeCount: rows.filter((row) => row.status === 'complete').length,
    missingCount: rows.filter((row) => row.status === 'missing').length,
    rows,
  }
}

export function resolvePromptConfirmState(query = {}, exportState = {}) {
  const exportComplete = isExportStateComplete(exportState)
  const confirmed = exportComplete && isQueryConfirmed(query.promptConfirmed)

  if (!exportComplete) {
    return {
      status: 'blocked',
      confirmed: false,
      shouldCleanPromptConfirmed: isQueryConfirmed(query.promptConfirmed),
    }
  }

  return {
    status: confirmed ? 'done' : 'ready',
    confirmed,
    shouldCleanPromptConfirmed: false,
  }
}

export function resolveIndexAvailabilityState(knowledgeBase = {}, indexRuns = [], options = {}) {
  const latestIndexRun = resolveLatestIndexRun(knowledgeBase, indexRuns)
  const activeIndexRunId = firstPresent(
    knowledgeBase.activeIndexRunId,
    knowledgeBase.activeIndexId,
    knowledgeBase.activeIndex?.id,
  )
  const latestIndexRunId = firstPresent(
    knowledgeBase.latestIndexRunId,
    latestIndexRun?.id,
    latestIndexRun?.indexRunId,
  )
  const latestIndexRunStatus = normalizeIndexStatus(
    knowledgeBase.latestIndexRunStatus ?? latestIndexRun?.status ?? latestIndexRun?.state,
  )
  const activeMatchesLatest = Boolean(activeIndexRunId && latestIndexRunId)
    && String(activeIndexRunId) === String(latestIndexRunId)

  if (latestIndexRunStatus === 'success' && activeMatchesLatest) {
    return { status: 'done', availability: 'available' }
  }

  if (latestIndexRunStatus === 'running') {
    return { status: 'running', availability: 'building' }
  }

  if (latestIndexRunStatus === 'success') {
    if (options.syncPollTimedOut) {
      return {
        status: 'running',
        availability: 'sync-timeout',
        warning: '可用状态同步超时',
        primaryAction: { label: '手动刷新', operationKey: 'index-refresh', disabled: false },
      }
    }

    return {
      status: 'running',
      availability: 'syncing',
      warning: '等待后端激活最新索引',
      primaryAction: { label: '刷新可用状态', operationKey: 'index-refresh', disabled: false },
    }
  }

  if (latestIndexRunStatus === 'failed') {
    return { status: 'failed', availability: 'failed' }
  }

  return { status: 'ready', availability: 'no-run' }
}

export function resolveBuildPrimaryAction(step, context = {}) {
  const stepKey = typeof step === 'string' ? step : step?.key

  if (stepKey === 'material') {
    return resolveMaterialPrimaryAction(context)
  }

  if (stepKey === 'parse') {
    return resolveParsePrimaryAction(context)
  }

  if (stepKey === 'export') {
    return resolveExportPrimaryAction(context)
  }

  if (stepKey === 'prompt') {
    return resolvePromptPrimaryAction(context)
  }

  if (stepKey === 'index') {
    return resolveIndexPrimaryAction(context)
  }

  if (stepKey === 'qa_check') {
    return resolveQaCheckPrimaryAction(context)
  }

  return createBuildAction({
    label: '继续下一步',
    operationKey: 'step-material',
    nextStepKey: 'material',
    nextQuery: resolveBuildStepQuery(context.query ?? {}, 'material'),
  })
}

function resolveMaterialPrimaryAction(context = {}) {
  const materialIds = normalizeMaterialIds(context.materialIds ?? context.selectedMaterialIds)

  if (materialIds.length === 0) {
    return createBuildAction({
      label: '确认勾选',
      operationKey: 'material-confirm',
      disabled: true,
      disabledReason: '请先选择课程资料',
    })
  }

  const nextStepKey = context.parseSummary
    ? (resolveParseSummaryHasWork(context.parseSummary) ? 'parse' : 'export')
    : resolveMaterialConfirmTarget(context.materials ?? [])
  const queryWithConfirm = resolveBuildConfirmQuery(context.query ?? {}, 'materialConfirmed', true)

  return createBuildAction({
    label: '确认勾选',
    operationKey: 'material-confirm',
    nextStepKey,
    nextQuery: resolveBuildStepQuery(queryWithConfirm, nextStepKey),
  })
}

function resolveParsePrimaryAction(context = {}) {
  const rows = context.parseRows ?? resolveParseTaskRows(context.materials ?? [])
  const parseSummary = context.parseSummary

  if (parseSummary && Number(parseSummary.running ?? 0) > 0) {
    return createBuildAction({ label: '刷新解析进度', operationKey: 'parse-refresh' })
  }

  if (parseSummary && (Number(parseSummary.pending ?? 0) > 0 || Number(parseSummary.failed ?? 0) > 0)) {
    return createBuildAction({ label: '并行解析未完成资料', operationKey: 'parse-batch' })
  }

  const statuses = rows.map((row) => normalizeParseStatus(row.status))
  if (statuses.includes('running')) {
    return createBuildAction({ label: '刷新解析进度', operationKey: 'parse-refresh' })
  }

  if (statuses.some((status) => ['pending', 'failed'].includes(status))) {
    return createBuildAction({ label: '并行解析未完成资料', operationKey: 'parse-batch' })
  }

  return createBuildAction({
    label: '检查图谱输入',
    operationKey: 'step-export',
    nextStepKey: 'export',
    nextQuery: resolveBuildStepQuery(context.query ?? {}, 'export'),
  })
}

function resolveExportPrimaryAction(context = {}) {
  const parseRows = context.parseRows ?? resolveParseTaskRows(context.materials ?? [])
  const parseSummary = context.parseSummary

  if (parseSummary && resolveParseSummaryHasWork(parseSummary)) {
    return createBuildAction({
      label: '导出图谱输入',
      operationKey: 'export-blocked',
      disabled: true,
      disabledReason: '请先完成所有资料解析',
    })
  }

  const parseStatuses = parseRows.map((row) => normalizeParseStatus(row.status))

  if (parseStatuses.some((status) => ['running', 'pending', 'failed'].includes(status))) {
    return createBuildAction({
      label: '导出图谱输入',
      operationKey: 'export-blocked',
      disabled: true,
      disabledReason: '请先完成所有资料解析',
    })
  }

  const exportState = context.exportState ?? {}
  const exportSummary = context.exportSummary
  const exportIncomplete = exportSummary
    ? Number(exportSummary.missing ?? 0) > 0
    : !isExportStateComplete(exportState)

  if (exportIncomplete) {
    return createBuildAction({ label: '导出缺失输入', operationKey: 'export-missing' })
  }

  if (!isQueryConfirmed(context.query?.exportConfirmed)) {
    const queryWithConfirm = resolveBuildConfirmQuery(context.query ?? {}, 'exportConfirmed', true)
    const { promptConfirmed, ...queryWithoutPromptConfirm } = queryWithConfirm

    return createBuildAction({
      label: '确认图谱输入',
      operationKey: 'export-confirm',
      nextStepKey: 'prompt',
      nextQuery: resolveBuildStepQuery(queryWithoutPromptConfirm, 'prompt'),
    })
  }

  return createBuildAction({
    label: '进入提示词调优',
    operationKey: 'step-prompt',
    nextStepKey: 'prompt',
    nextQuery: resolveBuildStepQuery(context.query ?? {}, 'prompt'),
  })
}

function resolvePromptPrimaryAction(context = {}) {
  const promptState = context.promptState ?? resolvePromptConfirmState(context.query ?? {}, context.exportState ?? {})

  if (promptState.status === 'blocked') {
    return createBuildAction({
      label: '确认提示词策略',
      operationKey: 'prompt-blocked',
      disabled: true,
      disabledReason: '请先确认导出产物',
    })
  }

  if (!promptState.confirmed) {
    const queryWithConfirm = resolveBuildConfirmQuery(context.query ?? {}, 'promptConfirmed', true)

    return createBuildAction({
      label: '确认提示词策略',
      operationKey: 'prompt-confirm',
      nextStepKey: 'index',
      nextQuery: resolveBuildStepQuery(queryWithConfirm, 'index'),
    })
  }

  return createBuildAction({
    label: '进入创建索引',
    operationKey: 'step-index',
    nextStepKey: 'index',
    nextQuery: resolveBuildStepQuery(context.query ?? {}, 'index'),
  })
}

function resolveIndexPrimaryAction(context = {}) {
  const indexState = context.indexState ?? resolveIndexAvailabilityState(
    context.knowledgeBase ?? {},
    context.indexRuns ?? [],
    context.options ?? {},
  )

  if (indexState.status === 'running') {
    return createBuildAction(indexState.primaryAction ?? { label: '刷新状态', operationKey: 'index-refresh' })
  }

  if (indexState.status === 'done') {
    return createBuildAction({
      label: '进入问答验证',
      operationKey: 'step-qa_check',
      nextStepKey: 'qa_check',
      nextQuery: resolveBuildStepQuery(context.query ?? {}, 'qa_check'),
    })
  }

  return createBuildAction({ label: '开始构建索引', operationKey: 'index-build' })
}

function resolveQaCheckPrimaryAction(context = {}) {
  const activeIndexRunId = firstPresent(
    context.activeIndexRunId,
    context.knowledgeBase?.activeIndexRunId,
    context.knowledgeBase?.activeIndexId,
  )

  if (!activeIndexRunId) {
    return createBuildAction({
      label: '发起问答验证',
      operationKey: 'qa-smoke',
      disabled: true,
      disabledReason: '缺少激活索引',
    })
  }

  return createBuildAction({
    label: context.qaCheckState?.status === 'done' ? '重新验证效果' : '发起问答验证',
    operationKey: 'qa-smoke',
  })
}

function createBuildAction({
  label,
  operationKey,
  disabled = false,
  disabledReason = '',
  nextStepKey = '',
  nextQuery,
}) {
  return {
    label,
    operationKey,
    disabled,
    disabledReason,
    nextStepKey,
    nextQuery,
  }
}

function normalizeWorkflowStatus(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'done'
  }

  if (['running', 'processing', 'building', 'syncing'].includes(normalized)) {
    return 'running'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  if (['ready', 'pending', 'todo'].includes(normalized)) {
    return 'ready'
  }

  if (normalized === 'blocked') {
    return 'blocked'
  }

  return normalized || 'blocked'
}

function normalizeParseStatus(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'done'
  }

  if (['running', 'processing'].includes(normalized)) {
    return 'running'
  }

  if (['pending', 'todo', 'ready'].includes(normalized)) {
    return 'pending'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return normalized || 'pending'
}

function normalizeIndexStatus(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'success'
  }

  if (['running', 'processing', 'building', 'syncing', 'pending'].includes(normalized)) {
    return 'running'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return normalized
}

function isParseComplete(material) {
  return normalizeParseStatus(material.parseState ?? material.parseStatus ?? material.status) === 'done'
}

function resolveMaterialTitle(material = {}) {
  return material.title ?? material.displayName ?? material.fileName ?? material.name ?? `资料 ${material.id ?? material.materialId ?? ''}`.trim()
}

function resolveParseResultFiles(parseResults = []) {
  const items = Array.isArray(parseResults)
    ? parseResults
    : parseResults.items ?? parseResults.results ?? parseResults.files ?? []

  return items
    .map((item) => typeof item === 'string' ? item : item.fileName ?? item.name ?? item.path ?? item.objectName ?? '')
    .map((fileName) => String(fileName).split('/').at(-1))
    .filter(Boolean)
}

function resolveLatestIndexRun(knowledgeBase = {}, indexRuns = []) {
  if (knowledgeBase.latestIndexRun) {
    return knowledgeBase.latestIndexRun
  }

  return indexRuns[0] ?? null
}

function normalizeMaterialIds(ids = []) {
  const values = Array.isArray(ids) ? ids : String(ids ?? '').split(',')

  return values.map((id) => String(id).trim()).filter(Boolean)
}

function isQueryConfirmed(value) {
  return String(value ?? '') === '1' || value === true
}

function isExportStateComplete(exportState = {}) {
  return exportState?.complete === true
    || exportState?.status === 'complete'
    || exportState?.status === 'done'
}

function resolveParseSummaryHasWork(summary = {}) {
  return Number(summary.pending ?? 0) > 0
    || Number(summary.failed ?? 0) > 0
    || Number(summary.running ?? 0) > 0
}

function firstPresent(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function clampPercent(value) {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(100, Math.max(0, Math.round(value)))
}
