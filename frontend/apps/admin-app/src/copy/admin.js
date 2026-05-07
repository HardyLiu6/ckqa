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
})

export default COPY
