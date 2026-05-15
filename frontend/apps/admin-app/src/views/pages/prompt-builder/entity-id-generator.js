// 实体/关系本地 ID 生成 + 重名检测。
// "e_/r_ + 13 位时间戳 + 6 位随机"格式，比 UUID 短。

function randomSuffix() {
  return Math.random().toString(36).slice(2, 8).padEnd(6, '0').slice(0, 6)
}

export function generateEntityId() {
  return `e_${Date.now()}_${randomSuffix()}`
}

export function generateRelationId() {
  return `r_${Date.now()}_${randomSuffix()}`
}

/**
 * 在已有实体列表中查找 name + type 完全相同的实体（用于重名警告）。
 * 名称比较前 trim；空名称或空类型直接返回 null。
 */
export function findDuplicateEntity(entities, name, type) {
  if (!entities || !Array.isArray(entities)) return null
  const trimmed = (name ?? '').trim()
  if (!trimmed || !type) return null
  return entities.find((e) => (e.name ?? '').trim() === trimmed && e.type === type) ?? null
}
