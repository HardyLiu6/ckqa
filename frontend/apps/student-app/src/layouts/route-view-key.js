function normalizeParams(params = {}) {
  return Object.keys(params)
    .sort()
    .reduce((normalized, key) => {
      normalized[key] = params[key]
      return normalized
    }, {})
}

export function resolveRouteViewKey(route) {
  const routeId = route?.name ? `name:${String(route.name)}` : `path:${route?.path || ''}`
  const params = JSON.stringify(normalizeParams(route?.params))

  return `${routeId}|params:${params}`
}
