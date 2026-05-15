export const TOTAL_AUDIT_SAMPLES = 20
const SECONDS_PER_CALL = 13

export function toggleCandidate(selectedIds, candidateId) {
  const set = new Set(selectedIds)
  if (set.has(candidateId)) set.delete(candidateId)
  else set.add(candidateId)
  return Array.from(set)
}

export function selectAll(candidates) {
  return candidates.map((c) => c.candidateId)
}

export function selectNone() {
  return []
}

export function selectBaselineOnly(candidates) {
  return candidates.filter((c) => c.category === 'baseline').map((c) => c.candidateId)
}

export function computeSummary(selectedIds, candidates) {
  const set = new Set(selectedIds)
  const selected = candidates.filter((c) => set.has(c.candidateId))
  const candidateCount = selected.length
  const totalCalls = candidateCount * TOTAL_AUDIT_SAMPLES
  const estimatedTokens = selected.reduce(
    (sum, c) => sum + (c.estimatedTokenPerCall ?? 0) * TOTAL_AUDIT_SAMPLES,
    0
  )
  const estimatedMinutes = totalCalls === 0 ? 0 : Math.ceil(totalCalls * SECONDS_PER_CALL / 60)
  return { candidateCount, totalCalls, estimatedTokens, estimatedMinutes }
}

export function formatTokens(n) {
  if (!n) return '0'
  if (n < 1000) return String(n)
  return `~${Math.round(n / 1000)}k`
}
