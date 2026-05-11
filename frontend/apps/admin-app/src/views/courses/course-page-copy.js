// 课程页面文案集中：课程列表 / 课程详情容器 / 成员 Tab / 资料 Tab 的固定文案
export const COURSE_PAGE_COPY = Object.freeze({
  list: {
    title: '课程',
    subtitle: '管理已有课程，进入详情后可以维护成员、资料和知识库。',
    emptyTitle: '还没有课程',
    emptyDescription: '点击右上角"新建课程"开始第一门课的搭建。',
    createCta: '新建课程',
    loadError: '课程列表加载失败，请稍后重试。',
  },
  detail: {
    eyebrowFormat: (courseName) => `生产 · 课程 · ${courseName}`,
    tabs: [
      { key: 'overview', label: '概览' },
      { key: 'members', label: '成员' },
      { key: 'materials', label: '资料' },
      { key: 'knowledge-bases', label: '知识库' },
    ],
    loadError: '课程详情加载失败。',
  },
  overview: {
    emptyTitle: '暂无概览信息',
  },
  members: {
    addCta: '添加成员',
    archivedHint: '课程已归档，成员管理为只读。',
    emptyTitle: '还没有成员',
    emptyDescription: '点击右上角"添加成员"邀请教师 / 助教加入。',
  },
  materials: {
    uploadCta: '上传资料',
    archivedHint: '课程已归档，资料管理为只读。',
    emptyTitle: '还没有资料',
    emptyDescription: '点击"上传资料"开始第一个 PDF 的解析。',
  },
  knowledgeBases: {
    emptyTitle: '还没有知识库',
    emptyDescription: '为本课程的资料生成第一个检索索引。',
  },
})
