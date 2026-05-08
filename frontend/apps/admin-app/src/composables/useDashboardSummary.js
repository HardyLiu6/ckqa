import { reactive, readonly } from 'vue'

import { fetchDashboardSummary, fetchFallbackSummary } from '../api/dashboard.js'

export async function resolveSummaryStrategy({ primary, fallback }) {
  try {
    const summary = await primary()
    return { summary, error: null, usingFallback: false }
  } catch (primaryError) {
    try {
      const summary = await fallback()
      return { summary, error: null, usingFallback: true, primaryError }
    } catch (error) {
      return { summary: null, error, usingFallback: true }
    }
  }
}

export function useDashboardSummary({ scopeStore }) {
  const state = reactive({
    summary: null,
    loading: false,
    error: null,
    usingFallback: false,
    lastRefreshedAt: 0,
  })

  async function refresh() {
    state.loading = true
    state.error = null
    const params = scopeStore.requestParams()
    const result = await resolveSummaryStrategy({
      primary: () => fetchDashboardSummary(params),
      fallback: () => fetchFallbackSummary(params),
    })
    state.summary = result.summary
    state.error = result.error
    state.usingFallback = result.usingFallback
    state.lastRefreshedAt = Date.now()
    state.loading = false
  }

  return { state: readonly(state), refresh }
}
