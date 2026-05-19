package org.ysu.ckqaback.integration.graphrag;

import java.util.List;

/**
 * Python Hybrid v0 warmup/readiness 返回结果。
 */
public record GraphRagHybridReadinessResult(
        boolean ready,
        String status,
        String message,
        String dataDirUri,
        boolean cached,
        boolean textUnitsReady,
        List<String> missing
) {
}
