const defaultTimeline = [
  { label: '数据同步', state: 'ready', detail: '等待真实接口接入' },
  { label: '权限检查', state: 'ready', detail: '已按路由元信息保护' },
  { label: '业务操作', state: 'pending', detail: '等待服务返回真实数据' },
]

const configs = {
  courses: {
    variant: 'table',
    eyebrow: 'Course Operations',
    summary: '从课程进入资料、知识库与问答闭环，管理员可管理全局课程，教师只看授权课程。',
    primaryAction: '新建课程',
    secondaryAction: '筛选状态',
    filters: [
      { key: 'status', label: '课程状态', options: ['全部', 'active', 'archived'] },
      { key: 'scope', label: '数据范围', options: ['全部', '我的课程'] },
    ],
    columns: ['课程', '课程 ID', '资料', '知识库', '最近索引'],
    rows: [
      ['操作系统', 'os', '8', '2', 'active'],
      ['数据结构', 'ds', '6', '1', 'draft'],
      ['计算机网络', 'cn', '10', '1', 'indexing'],
    ],
  },
  'course-detail': {
    variant: 'overview',
    eyebrow: 'Course Detail',
    summary: '详情页统一承载概览、资料、知识库、课程成员、问答会话与操作日志。',
    primaryAction: '管理课程资料',
    secondaryAction: '课程成员',
    facts: ['概览', '课程资料', '知识库', '课程成员', '问答会话', '操作日志'],
    timeline: defaultTimeline,
  },
  'material-detail': {
    variant: 'overview',
    eyebrow: 'Material Lifecycle',
    summary: '资料详情只处理解析、查看解析结果、导出 GraphRAG 输入和跳转构建向导。',
    primaryAction: '触发解析',
    secondaryAction: '导出输入',
    facts: ['课程资料 ID', '资料对象 ID', '文件名', 'MD5', '解析状态', 'MinerU 批次 ID'],
    timeline: [
      { label: '上传/登记', state: 'ready', detail: '沿用已有 pdf_ingest 与数据库记录' },
      { label: 'MinerU 解析', state: 'pending', detail: '等待用户触发或查看状态' },
      { label: 'GraphRAG 导出', state: 'pending', detail: '生成 section_docs/page_docs' },
    ],
  },
  'parse-results': {
    variant: 'overview',
    eyebrow: 'Parse Artifacts',
    summary: '首版只做只读产物查看与下载入口，不内置复杂 JSON 编辑器。',
    primaryAction: '查看产物',
    secondaryAction: '下载 JSON',
    facts: ['content_list_json', 'model_json', 'layout_json', 'markdown', 'origin_pdf', 'GraphRAG 投影'],
    timeline: defaultTimeline,
  },
  'knowledge-bases': {
    variant: 'table',
    eyebrow: 'Knowledge Base',
    summary: '管理课程知识库实例，重点看激活索引、最近构建状态和进入构建向导。',
    primaryAction: '新建知识库',
    secondaryAction: '查看激活版本',
    filters: [
      { key: 'status', label: '知识库状态', options: ['全部', 'draft', 'active', 'archived'] },
    ],
    columns: ['知识库', '所属课程', '状态', '激活索引', '最近运行'],
    rows: [
      ['OS 知识库', '操作系统', 'active', '#2', 'success'],
      ['DS 课程库', '数据结构', 'draft', '-', 'pending'],
      ['CN 课程库', '计算机网络', 'active', '#5', 'success'],
    ],
  },
  'knowledge-base-detail': {
    variant: 'overview',
    eyebrow: 'Knowledge Base Detail',
    summary: '知识库详情突出当前激活版本、文档映射、索引运行、问答验证与运行日志。',
    primaryAction: '进入构建向导',
    secondaryAction: '激活成功索引',
    facts: ['概览', '文档映射', '索引运行', '问答验证', '运行日志'],
    timeline: [
      { label: '文档映射', state: 'ready', detail: '等待真实文档列表接入' },
      { label: '索引运行', state: 'pending', detail: '从构建向导创建' },
      { label: '版本激活', state: 'pending', detail: '仅允许成功索引激活' },
    ],
  },
  'knowledge-base-build': {
    variant: 'workflow',
    eyebrow: 'Build Wizard',
    summary: '构建向导用带前置条件的可跳步流程展示真实链路，不把失败日志藏起来。',
    primaryAction: '继续下一步',
    secondaryAction: '查看最近任务',
    workflowSteps: [
      { key: 'material', label: '选择课程资料', state: 'done', detail: '选择已解析或待解析资料' },
      { key: 'parse', label: '解析状态检查', state: 'done', detail: '资料需达到 done 后才能建索引' },
      { key: 'export', label: '导出 GraphRAG 输入', state: 'ready', detail: '生成 section_docs.json / page_docs.json' },
      { key: 'index', label: '创建索引运行', state: 'blocked', detail: '等待导出输入确认' },
      { key: 'activate', label: '激活索引版本', state: 'blocked', detail: '仅成功索引可激活' },
      { key: 'smoke', label: '问答冒烟验证', state: 'blocked', detail: '验证会话进入问答列表并可过滤' },
    ],
  },
  'index-run-detail': {
    variant: 'overview',
    eyebrow: 'Index Run',
    summary: '让管理员和教师快速判断一次索引任务是否成功、失败在哪里。',
    primaryAction: '重试任务',
    secondaryAction: '查看日志',
    facts: ['知识库 ID', '引擎', '索引版本', '状态', '开始/结束时间', '索引产物', '失败信息'],
    timeline: defaultTimeline,
  },
  'qa-sessions': {
    variant: 'table',
    eyebrow: 'QA Operations',
    summary: '问答运维列表必须区分正式问答与构建向导产生的冒烟验证会话。',
    primaryAction: '查看正式问答',
    secondaryAction: '包含冒烟验证',
    filters: [
      { key: 'sessionType', label: '会话类型', options: ['全部', '正式问答', '冒烟验证'] },
      { key: 'status', label: '任务状态', options: ['全部', 'success', 'running', 'failed'] },
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
    eyebrow: 'QA Session Detail',
    summary: '详情页关注消息、任务状态、查询模式、心跳时间与关联检索日志。',
    primaryAction: '查看任务状态',
    secondaryAction: '检索日志',
    facts: ['消息列表', '任务状态', 'local/global/drift/basic', '心跳时间', '完成时间', '错误信息'],
    timeline: defaultTimeline,
  },
  users: {
    variant: 'table',
    eyebrow: 'Users',
    summary: '用户列表服务于管理端 RBAC，首版保持简单主体和角色可见性。',
    primaryAction: '新建用户',
    secondaryAction: '分配角色',
    filters: [
      { key: 'role', label: '角色', options: ['全部', 'admin', 'teacher', 'assistant', 'auditor'] },
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
    eyebrow: 'RBAC Matrix',
    summary: '首版不做复杂权限编辑器，只保留角色列表、权限分组、勾选矩阵和保存确认。',
    primaryAction: '保存矩阵',
    secondaryAction: '变更确认',
    facts: ['course:*', 'material:*', 'kb:*', 'qa:*', 'membership:*', 'user:*', 'role:*', 'audit:*'],
    timeline: defaultTimeline,
  },
  'course-memberships': {
    variant: 'table',
    eyebrow: 'Course Memberships',
    summary: '课程成员决定教师和助教的数据范围，是前端权限显示之外的第二层边界。',
    primaryAction: '添加成员',
    secondaryAction: '调整角色',
    filters: [
      { key: 'courseRole', label: '课程内角色', options: ['全部', 'student', 'teacher', 'assistant'] },
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
  eyebrow: 'CKQA',
  summary: '该页面已进入路由结构，等待后续业务能力接入。',
  primaryAction: '返回工作台',
  secondaryAction: '查看结构文档',
  facts: ['路由', '权限', '状态'],
  timeline: defaultTimeline,
}

export function getModulePageConfig(routeName) {
  return configs[routeName] ?? fallbackConfig
}
