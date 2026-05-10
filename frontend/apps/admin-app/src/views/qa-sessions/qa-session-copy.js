// M6：问答会话模块文案中心。
// 所有面向教师 / 教务的字段集中在此，便于统一巡检（不含
// 冒烟 / embedding / 实体抽取 / MinerU / P95 / smoke 等内部术语）。

export const QA_SESSION_PAGE_COPY = Object.freeze({
  list: {
    eyebrow: '运维',
    title: '问答会话',
    subtitle: '按课程查看学员问答记录，快速定位异常会话。',
    loadError: '问答会话列表加载失败',
    comingSoonTitle: '问答会话列表接口暂未开放',
    comingSoonDescription:
      '后端列表接口尚未就绪，可通过课程详情或构建向导生成的会话直链进入单个会话详情页。',
    emptyTitle: '暂无问答会话',
    emptyDescription: '学员发起问答或知识库验证结束后，会话会出现在这里。',
    filterSessionType: '会话类型',
    filterSessionTypeAny: '全部类型',
    filterAnomalyOnly: '仅看异常',
  },
  detail: {
    eyebrow: '运维 · 问答会话',
    loadError: '问答会话详情加载失败',
    emptyTitle: '尚未发起问答',
    emptyDescription: '会话建立后可在此查看学员与 AI 的完整对话记录。',
    pollingHint: '正在接收新消息…',
    backToList: '返回问答会话列表',
    sessionNotFound: '会话不存在或已关闭',
    retrievalPanelTitle: '检索诊断',
    retrievalPanelPlaceholderTitle: '本会话的检索诊断信息暂未启用',
    retrievalPanelPlaceholderHint:
      '等待后端开放检索轨迹字段后，这里会显示子问题拆分、命中片段、调用耗时与出处。',
    retrievalPanelLearnMore: '了解检索日志',
    viewDiagnosisCta: '查看检索过程',
    viewDiagnosisDisabled: '本回答未触发检索',
  },
  message: {
    userRole: '学员',
    assistantRole: 'AI 助教',
  },
})
