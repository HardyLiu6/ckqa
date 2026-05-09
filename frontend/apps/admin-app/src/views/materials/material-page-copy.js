// 资料详情页面文案集中：详情容器 + 4 Tab 的固定文案
export const MATERIAL_PAGE_COPY = Object.freeze({
  detail: {
    eyebrowFormat: (courseName) => `生产 · 资料 · ${courseName}`,
    tabs: [
      { key: 'parse-progress', label: '解析进度' },
      { key: 'parse-results', label: '解析结果' },
      { key: 'kb-references', label: '知识库引用' },
      { key: 'audit-log', label: '操作日志' },
    ],
    parseTimeoutBanner: 'PDF 解析超时，已自动重试一次。可手动重试或检查文件大小。',
    moreActions: {
      retryParse: '重新解析',
      replace: '替换文件',
      delete: '删除',
      copyToCourse: '复制到其他课程',
    },
    loadError: '资料详情加载失败。',
  },
  progress: {
    emptyTitle: '尚未开始解析',
    emptyDescription: '上传完成后会自动进入解析队列，这里会实时显示阶段进度。',
    logPlaceholder: '等待解析事件…',
    connectionLost: '连接中断，请刷新重试。',
  },
  results: {
    subtabs: [
      { key: 'markdown', label: 'Markdown 预览' },
      { key: 'chunks', label: '切分块' },
      { key: 'images', label: '图片' },
      { key: 'pdf', label: '原始 PDF' },
    ],
    emptyTitle: '尚无解析结果',
    emptyDescription: '解析完成后这里会显示 Markdown 预览、切分块、图片与原始 PDF。',
  },
  kbReferences: {
    emptyTitle: '本资料未被任何知识库引用',
    emptyDescription: '加入到知识库构建后会显示在这里。',
  },
  auditLog: {
    emptyTitle: '还没有操作记录',
    emptyDescription: '上传 / 重新解析 / 删除 / 复制等动作会记录在这里。',
  },
})
