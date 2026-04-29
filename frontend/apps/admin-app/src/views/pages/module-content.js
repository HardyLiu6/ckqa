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
