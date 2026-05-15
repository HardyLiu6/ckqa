// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
//
// Phase 1 mock 数据总入口。
// 各 sub-phase 会往本目录添加：
// - audit-samples.js   (Phase 1b)
// - candidates.js      (Phase 1c)
// - scoring-report.js  (Phase 1d)
// - prompt-texts.js    (Phase 1e)

export const MOCK_HISTORY_DRAFTS = [
  {
    id: 'draft-001',
    name: '操作系统 · 图谱感知 + 蒸馏样例 · 2026-04-12',
    description: '上学期构建沉淀的版本',
    sourceCandidateId: 'schema_fewshot_distilled_v2_strict_tuple',
    compositeScore: 0.69,
    createdAt: '2026-04-12T15:42:00',
  },
  {
    id: 'draft-002',
    name: '操作系统 · 默认基线 · 2026-03-28',
    description: '只跑了基线对照',
    sourceCandidateId: 'default',
    compositeScore: 0.41,
    createdAt: '2026-03-28T10:15:00',
  },
]

export const MOCK_COURSE_NAME = '操作系统'

// Phase 1b：标注 IDE 所需的 audit 样本 mock
export { MOCK_AUDIT_SAMPLES, MOCK_TASK_SUMMARY } from './audit-samples.js'

// Phase 1c：候选勾选所需的候选 mock
export { MOCK_CANDIDATES } from './candidates.js'
