export function buildPageQuery(query = {}, page) {
  return {
    ...query,
    page,
  }
}

export function resolveMaterialQuery(query = {}, materialId) {
  return {
    ...query,
    materialId: String(materialId),
  }
}

export function resolveCleanMaterialQuery(query = {}) {
  const { materialId, ...rest } = query
  return rest
}

export function selectLatestRunningOrSuccess(indexRuns = [], resolveState) {
  return indexRuns.filter((item) => {
    const state = resolveState(item)
    return state === 'running' || state === 'success'
  }).sort((left, right) => {
    const leftTime = Date.parse(left.createdAt ?? left.startedAt ?? left.updatedAt ?? '') || 0
    const rightTime = Date.parse(right.createdAt ?? right.startedAt ?? right.updatedAt ?? '') || 0
    return rightTime - leftTime || Number(right.id ?? 0) - Number(left.id ?? 0)
  })[0] ?? {}
}

export function createRouteSnapshot(route, query = route?.query ?? {}) {
  return {
    name: route?.name,
    params: { ...(route?.params ?? {}) },
    query: { ...query },
    meta: { ...(route?.meta ?? {}) },
  }
}

export function createStaleRequestGuard() {
  let latestRequestId = 0

  return {
    next() {
      latestRequestId += 1
      return latestRequestId
    },
    isCurrent(requestId) {
      return requestId === latestRequestId
    },
  }
}
