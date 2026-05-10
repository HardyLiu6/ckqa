/**
 * 列表页 composable 共享内部工具：状态容器 + load 包装。
 *
 * 本模块供 `useRoleListPage / usePermissionListPage`（以及将来 `useUserListPage`）
 * 复用，避免"两份几乎一样的 load / state 代码"。
 *
 * 对外不做导出：仅 composable 文件内部消费。任何面向页面组件的 API 都在各自
 * `use*ListPage.js` 中暴露。
 */

import { ref, reactive } from 'vue'

/**
 * 初始化列表页的 Ref 容器。
 *
 * - `state`：`'idle' | 'loading' | 'success' | 'empty' | 'error'`，驱动 UI 骨架/空态/错误态
 * - `rows / pagination / error`：列表数据本体
 * - `keyword / extra`：查询参数；`extra` 用于承载 permission 的 `resource` 等额外筛选
 * - `dataSourceHint`：`'api' | 'aggregated'`，由 service 返回的 `source` 字段驱动；
 *    默认为 `'api'`，只有主接口确认不可用、回退到"用户视图聚合"时才变为 `'aggregated'`。
 */
export function createListPageState({
  initialPage = 1,
  initialSize = 20,
  initialKeyword = '',
  extra = {},
} = {}) {
  const state = ref('idle')
  const rows = ref([])
  const pagination = reactive({
    page: initialPage,
    size: initialSize,
    total: 0,
    pages: 0,
  })
  const error = ref(null)
  const page = ref(initialPage)
  const size = ref(initialSize)
  const keyword = ref(initialKeyword)
  const extraRefs = {}
  for (const [key, value] of Object.entries(extra)) {
    extraRefs[key] = ref(value)
  }
  const dataSourceHint = ref('api')

  return {
    state,
    rows,
    pagination,
    error,
    page,
    size,
    keyword,
    dataSourceHint,
    extra: extraRefs,
  }
}

/**
 * 执行一次分页加载，配合 `createStaleRequestGuard` 防并发抢占。
 *
 * 调用方通过 `fetchPage()` 返回一个 Promise，其 resolved 值必须是
 * `{ items, pagination, source }`（同 `normalizePageData` 扩展形状）。
 * 抛出的错误会被归一化为 `{ status, message, code, raw }` 后写到 `api.error.value`。
 */
export async function runListPageLoad({ api, guard, fetchPage }) {
  const requestId = guard.next()
  api.state.value = 'loading'
  api.error.value = null
  try {
    const result = await fetchPage()
    if (!guard.isCurrent(requestId)) return

    const items = Array.isArray(result?.items) ? result.items : []
    api.rows.value = items
    api.pagination.page = Number(result?.pagination?.page ?? api.page.value)
    api.pagination.size = Number(result?.pagination?.size ?? api.size.value)
    api.pagination.total = Number(result?.pagination?.total ?? items.length)
    api.pagination.pages = Number(
      result?.pagination?.pages
        ?? (api.pagination.size > 0 ? Math.ceil(api.pagination.total / api.pagination.size) : 0),
    )
    api.dataSourceHint.value = result?.source === 'aggregated' ? 'aggregated' : 'api'
    api.state.value = items.length === 0 ? 'empty' : 'success'
  } catch (raw) {
    if (!guard.isCurrent(requestId)) return
    api.state.value = 'error'
    api.error.value = normalizeError(raw)
  }
}

function normalizeError(raw) {
  if (raw && typeof raw === 'object') {
    const message = raw.message ?? raw.raw?.message ?? '请求失败'
    return {
      message,
      status: raw.status,
      code: raw.code,
      raw,
    }
  }
  return { message: String(raw ?? '请求失败'), raw }
}
