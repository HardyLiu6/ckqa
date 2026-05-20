// 学生端知识图谱 REST 接口封装
// 所有路径前缀对齐 docs/student-backend-graphrag-api-contract.md
// /api/v1/knowledge-bases/{id}/graph/...

import { get } from '@/axios'

/**
 * 拉取课程下的知识库摘要列表，便于挑选 activeIndexRunId != null 的可用知识库。
 *
 * @param {string} courseId
 */
export function fetchCourseKnowledgeBases(courseId) {
  return get(`/courses/${encodeURIComponent(courseId)}/knowledge-bases`)
}

/**
 * 拉取系统健康检查，前端图谱页据此判断是否要降级。
 */
export function fetchSystemHealth() {
  return get('/system/health')
}

/**
 * 拉取知识图谱总览（顶层社区 + 每社区 Top-N 实体 + 实体间 RELATED 关系）。
 *
 * @param {number|string} knowledgeBaseId
 * @param {{ level?: number, topN?: number }} params
 */
export function fetchGraphOverview(knowledgeBaseId, params = {}) {
  return get(`/knowledge-bases/${knowledgeBaseId}/graph/overview`, { params })
}

/**
 * 拉取实体邻域子图（MVP 阶段仅支持 depth=1）。
 *
 * @param {number|string} knowledgeBaseId
 * @param {string} entityId
 * @param {{ depth?: number, limit?: number }} params
 */
export function fetchEntityNeighborhood(knowledgeBaseId, entityId, params = {}) {
  return get(
    `/knowledge-bases/${knowledgeBaseId}/graph/entities/${encodeURIComponent(entityId)}/neighborhood`,
    { params },
  )
}

/**
 * 拉取实体详情（含完整 description / 所属社区路径 / chunk 数量）。
 *
 * @param {number|string} knowledgeBaseId
 * @param {string} entityId
 */
export function fetchEntityDetail(knowledgeBaseId, entityId) {
  return get(
    `/knowledge-bases/${knowledgeBaseId}/graph/entities/${encodeURIComponent(entityId)}`,
  )
}
