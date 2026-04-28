export function buildPageQuery(query = {}, page) {
  return {
    ...query,
    page,
  }
}

export function createRouteSnapshot(route, query = route?.query ?? {}) {
  return {
    name: route?.name,
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
