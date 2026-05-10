// M8 Task 4 / Task 5：暗色视觉快照 + axe 扫描共用 mock 集合。
//
// 设计原则：
// - 每个页面对应一个 `page<Key>Mocks` 对象，可塞进 `loginAsAdmin({ mocks })`。
// - 数据保持稳定（1~3 条），避免基线截图因时间戳/排序漂移。
// - 同时尽量让 DOM 节点齐全，让 axe 扫描有内容覆盖。
// - 直接复用 e2e/fixtures/qa-session-mock.js 与 build-run-mock.js 已有数据。
//
// 与既有 spec 共用：course-flow / kb-detail / kb-build / qa-session-* 各自的
// 内部 mock 仍保留，本文件只负责"统一 10 页"巡检矩阵。

import {
  DEFAULT_SESSIONS,
  DEFAULT_MESSAGES_BY_SESSION,
  makeQaSessionListHandler,
  makeQaSessionDetailHandler,
  makeQaMessagesHandler,
} from './qa-session-mock.js'
import { createBuildRunMockSequence } from './build-run-mock.js'

const COURSE_ID = 'crs-20260101-120000'
const MATERIAL_ID = 'mat-20260101-120500'
const KB_ID = '7'
const KB_ID_NUM = Number(KB_ID)
const RUN_ID = 88
const QA_SESSION_ID = 11

// ---------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------
const dashboardSummary = () => ({
  courseCount: 12,
  materialCount: 428,
  materialReadyCount: 412,
  materialPendingCount: 16,
  knowledgeBaseCount: 9,
  knowledgeBaseRunningCount: 2,
  knowledgeBaseRunningPercents: [65, 32],
  activeKbCount: 1,
  activeKbVersion: 'v3',
  qaSessionCount: 1234,
  qaResponseTimeMs: 312,
  activeKey: 'knowledgeBases',
})

export const dashboardMocks = {
  'GET /dashboard/summary': dashboardSummary,
  'GET /index-runs': () => ({
    items: [
      {
        id: 'r-running',
        kbName: 'KB-OS v2',
        status: 'running',
        progress: 0.6,
        updatedAt: '2026-05-10 14:00',
        startedAt: '2026-05-10 13:30',
      },
    ],
  }),
  'GET /material-parse-tasks': () => ({ items: [] }),
}

// ---------------------------------------------------------------------------
// Course list / detail
// ---------------------------------------------------------------------------
const courseRow = {
  courseId: COURSE_ID,
  courseName: '操作系统',
  description: '基础理论与常见调度算法',
  status: 'active',
  materialCount: 3,
  knowledgeBaseCount: 1,
  updatedAt: '2026-05-01 10:30',
}

const courseListHandler = () => ({ data: { items: [courseRow], total: 1, page: 1, size: 20 } })
const courseDetailHandler = () => ({
  data: { ...courseRow, parsedMaterialCount: 2, activeKnowledgeBaseCount: 1 },
})
const emptyListHandler = () => ({ data: { items: [], total: 0, page: 1, size: 20 } })

export const courseListMocks = {
  'GET /courses': courseListHandler,
}

export const courseDetailMocks = {
  'GET /courses': courseListHandler,
  [`GET /courses/${COURSE_ID}`]: courseDetailHandler,
  [`GET /courses/${COURSE_ID}/materials`]: emptyListHandler,
  [`GET /courses/${COURSE_ID}/knowledge-bases`]: emptyListHandler,
  [`GET /courses/${COURSE_ID}/members`]: emptyListHandler,
}

// ---------------------------------------------------------------------------
// Material detail
// ---------------------------------------------------------------------------
const materialDetail = () => ({
  data: {
    id: MATERIAL_ID,
    materialId: MATERIAL_ID,
    courseId: COURSE_ID,
    courseName: '操作系统',
    fileName: '第3章 调度算法.pdf',
    displayName: '第3章 调度算法.pdf',
    parseStatus: 'running',
    parseStatusLabel: '解析中',
    fileSize: 12_345_678,
    uploadTime: '2026-05-01 09:00',
    createdAt: '2026-05-01 09:00',
    updatedAt: '2026-05-01 10:00',
  },
})

export const materialDetailMocks = {
  [`GET /materials/${MATERIAL_ID}`]: materialDetail,
  [`GET /pdf-files/${MATERIAL_ID}`]: materialDetail,
  [`GET /pdf-files/${MATERIAL_ID}/parse-results`]: emptyListHandler,
  [`GET /courses/${COURSE_ID}`]: () => ({
    data: { courseId: COURSE_ID, courseName: '操作系统', status: 'active' },
  }),
  [`GET /courses/${COURSE_ID}/materials/${MATERIAL_ID}`]: materialDetail,
}

// ---------------------------------------------------------------------------
// KB list / detail / build wizard / index run detail
// ---------------------------------------------------------------------------
const kbRow = {
  id: KB_ID_NUM,
  name: '操作系统知识库',
  description: '操作系统课程的检索索引',
  courseId: COURSE_ID,
  status: 'active',
  activeIndexRunId: RUN_ID,
  latestIndexRunId: RUN_ID,
  latestIndexRunStatus: 'success',
  updatedAt: '2026-05-06 15:00',
}

const kbListHandler = () => ({
  data: { items: [kbRow], current: 1, size: 20, total: 1, pages: 1 },
})

const kbDetailHandler = () => ({
  data: {
    ...kbRow,
    createdAt: '2026-05-01 10:00',
    updatedAt: '2026-05-06 15:00',
  },
})

const kbForBuildHandler = () => ({
  data: {
    ...kbRow,
    status: 'active',
    activeIndexRunId: null,
    createdAt: '2026-05-01 10:00',
    updatedAt: '2026-05-10 14:00',
  },
})

const indexRunsHandler = () => ({
  data: [
    {
      id: RUN_ID,
      indexVersion: 'graphrag-202605061500',
      status: 'success',
      startedAt: '2026-05-06 14:50',
      finishedAt: '2026-05-06 14:58',
      operatorName: 'admin.heqh',
      active: true,
    },
  ],
})

const courseMaterialsHandler = () => ({
  data: [
    {
      id: 9,
      courseId: COURSE_ID,
      displayName: '第3章 调度算法.pdf',
      fileName: '第3章 调度算法.pdf',
      materialType: 'textbook',
      parseStatus: 'done',
      fileSize: 1024,
    },
  ],
})

const courseSummaryHandler = () => ({
  data: { courseId: COURSE_ID, courseName: '操作系统', status: 'active' },
})

const indexRunDetailHandler = () => ({
  data: {
    id: RUN_ID,
    knowledgeBaseId: KB_ID_NUM,
    indexVersion: 'graphrag-202605061500',
    status: 'success',
    startedAt: '2026-05-06 14:50',
    finishedAt: '2026-05-06 14:58',
    operatorName: 'admin.heqh',
    operatorDisplayName: '平台管理员',
    active: true,
    config: {
      embeddingModel: 'bge-large-zh',
      chunkSize: 512,
      chunkOverlap: 64,
    },
  },
})

export const kbListMocks = {
  'GET /knowledge-bases': kbListHandler,
}

export const kbDetailMocks = {
  'GET /knowledge-bases': kbListHandler,
  [`GET /knowledge-bases/${KB_ID}`]: kbDetailHandler,
  [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
  [`GET /courses/${COURSE_ID}/materials`]: () => ({ data: [] }),
}

export const kbBuildMocks = {
  [`GET /knowledge-bases/${KB_ID}`]: kbForBuildHandler,
  [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
  'GET /knowledge-bases': () => ({ data: { items: [], current: 1, size: 20, total: 0, pages: 0 } }),
  [`GET /courses/${COURSE_ID}/materials`]: courseMaterialsHandler,
  [`GET /courses/${COURSE_ID}`]: courseSummaryHandler,
}

const STATIC_BUILD_RUN_ID = 27
const buildRunHandler = createBuildRunMockSequence(STATIC_BUILD_RUN_ID)

export const indexRunDetailMocks = {
  [`GET /knowledge-bases/${KB_ID}`]: kbDetailHandler,
  [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
  [`GET /knowledge-bases/${KB_ID}/index-runs/${RUN_ID}`]: indexRunDetailHandler,
  [`GET /index-runs/${RUN_ID}`]: indexRunDetailHandler,
  [`GET /index-runs/${RUN_ID}/artifacts`]: () => ({ data: [] }),
  [`GET /knowledge-base-build-runs/${STATIC_BUILD_RUN_ID}`]: buildRunHandler,
}

// ---------------------------------------------------------------------------
// QA sessions
// ---------------------------------------------------------------------------
export const qaSessionListMocks = {
  'GET /qa-sessions': makeQaSessionListHandler(DEFAULT_SESSIONS),
}

export const qaSessionDetailMocks = {
  [`GET /qa-sessions/${QA_SESSION_ID}`]: makeQaSessionDetailHandler(DEFAULT_SESSIONS),
  [`GET /qa-sessions/${QA_SESSION_ID}/messages`]: makeQaMessagesHandler(DEFAULT_MESSAGES_BY_SESSION),
}

// ---------------------------------------------------------------------------
// 页面矩阵：m8-visual-core / m8-axe-core 共用
// ---------------------------------------------------------------------------
export const PAGES = [
  { key: 'dashboard', path: '/app/dashboard', mocks: dashboardMocks, ready: '[data-testid="dashboard-page"]' },
  { key: 'course-list', path: '/app/courses', mocks: courseListMocks, ready: '[data-testid="course-list-page"]' },
  { key: 'course-detail', path: `/app/courses/${COURSE_ID}`, mocks: courseDetailMocks, ready: '[data-testid="course-detail-page"]' },
  { key: 'material-detail', path: `/app/materials/${MATERIAL_ID}`, mocks: materialDetailMocks, ready: '[data-testid="material-detail-page"]' },
  { key: 'kb-list', path: '/app/knowledge-bases', mocks: kbListMocks, ready: '[data-testid="kb-list-page"]' },
  { key: 'kb-detail', path: `/app/knowledge-bases/${KB_ID}`, mocks: kbDetailMocks, ready: '[data-testid="kb-detail-page"]' },
  { key: 'kb-build', path: `/app/knowledge-bases/${KB_ID}/build`, mocks: kbBuildMocks, ready: '[data-testid="kb-build-wizard-page"]' },
  { key: 'index-run-detail', path: `/app/index-runs/${RUN_ID}`, mocks: indexRunDetailMocks, ready: '[data-testid="index-run-detail-page"]' },
  { key: 'qa-session-list', path: '/app/qa-sessions', mocks: qaSessionListMocks, ready: '[data-testid="qa-session-list-page"]' },
  { key: 'qa-session-detail', path: `/app/qa-sessions/${QA_SESSION_ID}`, mocks: qaSessionDetailMocks, ready: '[data-testid="qa-session-detail-page"]' },
]
