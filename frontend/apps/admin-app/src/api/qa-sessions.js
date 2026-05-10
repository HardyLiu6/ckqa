import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'
import { getQaSession } from './qa.js'

// M6：把问答会话域的 list / messages / session detail 集中在此，方便页面只导一处。
// getQaSession 来自 api/qa.js，维持与其他 qa 操作同源。
export { getQaSession }

// 查询会话列表。契约：
// Query:
//   page / size / keyword / courseId / knowledgeBaseId / sessionType('formal'|'smoke') /
//   hasAnomaly(boolean) / startAt / endAt
// Response: 后端 IPage<QaSessionResponse>，此处统一走 normalizePageData 归一为 { items, pagination }
export async function listQaSessions(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/qa-sessions', { params })))
}

// 查询会话下的消息流。返回 QaMessageResponse[]，顺序由后端按 sequenceNo 升序。
export async function listQaMessages(sessionId, client = http) {
  return unwrapApiResponse(
    await client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`),
  )
}
