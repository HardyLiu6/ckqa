// M6：问答会话域 e2e mock 工厂。
// 模拟后端 GET /qa-sessions（分页）、GET /qa-sessions/:id、GET /qa-sessions/:id/messages。

export const DEFAULT_SESSIONS = [
  {
    id: 11,
    sessionCode: 'SES-011',
    title: '动态规划入门问答',
    courseId: 'crs-ds',
    userId: 1001,
    userDisplayName: '张同学',
    sessionType: 'formal',
    status: 'completed',
    messageCount: 4,
    hasAnomaly: false,
    lastMessageAt: '2026-05-10 14:20',
    createdAt: '2026-05-10 14:00',
  },
  {
    id: 12,
    sessionCode: 'SES-012',
    title: '操作系统知识库构建验证',
    courseId: 'crs-os',
    userId: 2002,
    userDisplayName: '李老师',
    sessionType: 'smoke',
    status: 'completed',
    messageCount: 2,
    hasAnomaly: true,
    anomalyReason: '模型响应超时，已自动重试',
    lastMessageAt: '2026-05-09 22:10',
    createdAt: '2026-05-09 22:00',
  },
  {
    id: 13,
    sessionCode: 'SES-013',
    title: '机器学习期末复习',
    courseId: 'crs-ml',
    userId: 1005,
    userDisplayName: '王同学',
    sessionType: 'formal',
    status: 'running',
    messageCount: 3,
    hasAnomaly: false,
    lastMessageAt: '2026-05-10 15:00',
    createdAt: '2026-05-10 14:50',
  },
]

export function makeQaSessionListHandler(sessions = DEFAULT_SESSIONS) {
  return (request) => {
    const url = new URL(request.url())
    const sessionType = url.searchParams.get('sessionType')
    const hasAnomaly = url.searchParams.get('hasAnomaly')
    let items = sessions.slice()
    if (sessionType) items = items.filter((s) => s.sessionType === sessionType)
    if (hasAnomaly === 'true' || hasAnomaly === '1') {
      items = items.filter((s) => s.hasAnomaly)
    }
    const size = Number(url.searchParams.get('size') ?? 20)
    const page = Number(url.searchParams.get('page') ?? 1)
    return {
      data: {
        items,
        current: page,
        size,
        total: items.length,
        pages: Math.max(1, Math.ceil(items.length / Math.max(1, size))),
      },
    }
  }
}

export function makeQaSessionDetailHandler(sessions = DEFAULT_SESSIONS) {
  return (request) => {
    const match = request.url().match(/qa-sessions\/(\d+)/)
    const id = Number(match?.[1])
    const session = sessions.find((s) => s.id === id)
    if (!session) {
      return { httpStatus: 404, code: 4040, message: '问答会话不存在', data: null }
    }
    return { data: session }
  }
}

export const DEFAULT_MESSAGES_BY_SESSION = {
  11: [
    { id: 1101, sessionId: 11, role: 'user', sequenceNo: 0, content: '什么是动态规划？', createdAt: '2026-05-10 14:00' },
    { id: 1102, sessionId: 11, role: 'assistant', sequenceNo: 1, content: '动态规划是把复杂问题拆成更小的子问题…', createdAt: '2026-05-10 14:01', taskStatus: 'completed' },
    { id: 1103, sessionId: 11, role: 'user', sequenceNo: 2, content: '举个例子呢？', createdAt: '2026-05-10 14:10' },
    { id: 1104, sessionId: 11, role: 'assistant', sequenceNo: 3, content: '经典例子包括斐波那契数列、背包问题…', createdAt: '2026-05-10 14:11', taskStatus: 'completed' },
  ],
  12: [
    { id: 1201, sessionId: 12, role: 'user', sequenceNo: 0, content: '请用一句话概括当前知识库的主要内容。', createdAt: '2026-05-09 22:00' },
    { id: 1202, sessionId: 12, role: 'assistant', sequenceNo: 1, content: '当前知识库覆盖操作系统进程 / 内存 / 文件系统三大主题。', createdAt: '2026-05-09 22:05', taskStatus: 'completed' },
  ],
}

export function makeQaMessagesHandler(messagesBySession = DEFAULT_MESSAGES_BY_SESSION) {
  return (request) => {
    const match = request.url().match(/qa-sessions\/(\d+)\/messages/)
    const id = Number(match?.[1])
    return { data: messagesBySession[id] ?? [] }
  }
}
