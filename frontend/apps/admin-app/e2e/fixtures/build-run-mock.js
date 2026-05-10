// 构建向导 e2e 用的 mock snapshot 序列
// 每次 GET /knowledge-base-build-runs/:id 调用按顺序返回一个 snapshot，
// 直到序列结束后稳定在最后一项。

export function createBuildRunMockSequence(buildRunId = 27) {
  let callCount = 0
  const snapshots = [
    {
      id: buildRunId,
      knowledgeBaseId: 7,
      courseId: 'crs-20260101-120000',
      requestedByUserId: 1,
      buildVersion: 'build-v1',
      status: 'running',
      currentStage: 'material',
      activationPolicy: 'latest-build-only',
      selectedMaterialIds: '[9]',
      startedAt: '2026-05-10 14:00',
      createdAt: '2026-05-10 14:00',
      updatedAt: '2026-05-10 14:00',
    },
    {
      id: buildRunId,
      knowledgeBaseId: 7,
      status: 'running',
      currentStage: 'parse',
      updatedAt: '2026-05-10 14:01',
    },
    {
      id: buildRunId,
      knowledgeBaseId: 7,
      status: 'running',
      currentStage: 'parse',
      updatedAt: '2026-05-10 14:02',
      buildMetadata: JSON.stringify({ failureReason: '解析阶段偶发错误' }),
    },
    {
      id: buildRunId,
      knowledgeBaseId: 7,
      status: 'running',
      currentStage: 'export',
      updatedAt: '2026-05-10 14:03',
    },
  ]

  return () => {
    const idx = Math.min(callCount, snapshots.length - 1)
    callCount += 1
    return { data: snapshots[idx] }
  }
}
