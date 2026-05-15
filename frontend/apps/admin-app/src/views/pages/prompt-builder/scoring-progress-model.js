export const TOTAL_SAMPLES_PER_CANDIDATE = 20
export const SCORING_DURATION_MS = 1000

export function buildInitialProgress(candidateIds) {
  return candidateIds.map((id, i) => ({
    candidateId: id,
    status: i === 0 ? 'extracting' : 'queued',
    extractDone: 0,
    scoringStartedAtMs: null,
  }))
}

export function advanceProgress(progress, elapsedMs, { tickRate = 4 } = {}) {
  const next = progress.map((p) => ({ ...p }))
  let remainingMs = elapsedMs

  for (let i = 0; i < next.length; i++) {
    const p = next[i]
    if (p.status === 'done') continue
    if (p.status === 'queued') {
      if (remainingMs <= 0) return next
      p.status = 'extracting'
    }

    if (p.status === 'extracting') {
      const extractRemaining = TOTAL_SAMPLES_PER_CANDIDATE - p.extractDone
      const msToExtract = Math.ceil((extractRemaining / tickRate) * 1000)
      if (remainingMs >= msToExtract) {
        p.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
        remainingMs -= msToExtract
        p.status = 'scoring'
        p.scoringStartedAtMs = elapsedMs - remainingMs
      } else {
        p.extractDone = Math.min(
          TOTAL_SAMPLES_PER_CANDIDATE,
          p.extractDone + Math.floor((remainingMs / 1000) * tickRate)
        )
        return next
      }
    }

    if (p.status === 'scoring') {
      if (remainingMs >= SCORING_DURATION_MS) {
        remainingMs -= SCORING_DURATION_MS
        p.status = 'done'
      } else {
        return next
      }
    }
  }
  return next
}

export function isAllDone(progress) {
  return progress.every((p) => p.status === 'done')
}
