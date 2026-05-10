// 知识库模块文案中心。所有面向教师 / 教务的字段集中在此，
// 便于做术语巡检（不含 embedding / 实体抽取 / MinerU / P95 / 冒烟）。

export const KB_PAGE_COPY = Object.freeze({
  list: {
    eyebrow: '知识库',
    title: '知识库',
    subtitle: '按课程管理可问答的知识库，监控构建与激活状态。',
    createCta: '新建知识库',
    loadError: '无法加载知识库',
    emptyTitle: '暂无知识库',
    emptyDescription: '创建课程知识库后，可进入构建向导生成可问答的索引。',
  },
  detail: {
    eyebrowFormat: (kbName) => `生产 · 知识库 · ${kbName}`,
    tabs: [
      { key: 'overview', label: '概览' },
      { key: 'source-materials', label: '来源资料' },
      { key: 'index-runs', label: '索引版本' },
      { key: 'validation', label: '验证记录' },
    ],
    activateCta: '激活最新索引',
    buildCta: '开始/继续构建',
    loadError: '知识库详情加载失败。',
    overviewEmpty: '暂无基础信息',
    sourceMaterialsEmpty: '尚未挂接资料，请通过构建向导选择资料。',
    indexRunsEmpty: '尚未生成索引版本，启动构建即可生成。',
    validationEmpty: '尚未产生验证记录，可在构建流程末尾发起。',
  },
  indexRunDetail: {
    eyebrow: '索引版本',
    activateCta: '激活此版本',
    artifactsTitle: '产物列表',
  },
})
