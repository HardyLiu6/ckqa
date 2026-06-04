package org.ysu.ckqaback.qa.memory;

import java.util.List;

/**
 * 长期记忆治理诊断快照，用于内部运维排查和审计。
 */
public record QaMemoryGovernanceSnapshot(
        String memoryGovernanceVersion,
        int memoryLongTermCount,
        int memoryRecentHistoryCount,
        String memoryInjectionReason,
        List<QaMemorySourceDescriptor> sources
) {
    public static final String VERSION = "memory-governance-v1";

    public QaMemoryGovernanceSnapshot {
        memoryGovernanceVersion = memoryGovernanceVersion == null ? VERSION : memoryGovernanceVersion;
        memoryLongTermCount = Math.max(0, memoryLongTermCount);
        memoryRecentHistoryCount = Math.max(0, memoryRecentHistoryCount);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static QaMemoryGovernanceSnapshot empty(String reason) {
        return new QaMemoryGovernanceSnapshot(VERSION, 0, 0, reason, List.of());
    }
}
