package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;
import org.ysu.ckqaback.entity.PromptTuneRuns;

import java.time.LocalDateTime;

/**
 * 提示词自动调优状态响应。
 */
@Getter
@Builder
public class PromptTuneRunResponse {

    /**
     * 调优记录 ID；尚未触发时为空。
     */
    private final Long id;

    /**
     * 触发该调优的最近 build run ID。
     */
    private final Long buildRunId;

    /**
     * 知识库 ID。
     */
    private final Long knowledgeBaseId;

    /**
     * 缓存键：sorted(materialId:fileMd5) 的 sha256 hex。
     */
    private final String cacheKey;

    /**
     * not_started / pending / running / success / failed / cancelled。
     */
    private final String status;

    /**
     * queued / fetch_input / prompt_tune / done。
     */
    private final String progressStage;

    /**
     * 调用方是否复用了此前的 success 缓存（true：本次未触发新进程）。
     */
    private final Boolean cacheHit;

    /**
     * 最近一次心跳时间，用于前端检测 stale。
     */
    private final LocalDateTime lastHeartbeatAt;

    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime createdAt;

    /**
     * 实体抽取提示词内容 SHA-256（success 时填充）。
     */
    private final String promptSha256;

    /**
     * 末尾若干行 stdout 拼接，最多 8KB。
     */
    private final String latestLogs;

    /**
     * 失败摘要。
     */
    private final String errorMessage;

    /**
     * 建议前端轮询间隔（毫秒）。
     */
    private final Integer recommendedPollingIntervalMillis;

    public static PromptTuneRunResponse fromEntity(PromptTuneRuns run, boolean cacheHit) {
        return PromptTuneRunResponse.builder()
                .id(run.getId())
                .buildRunId(run.getBuildRunId())
                .knowledgeBaseId(run.getKnowledgeBaseId())
                .cacheKey(run.getCacheKey())
                .status(run.getStatus())
                .progressStage(run.getProgressStage())
                .cacheHit(cacheHit)
                .lastHeartbeatAt(run.getLastHeartbeatAt())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .createdAt(run.getCreatedAt())
                .promptSha256(run.getPromptSha256())
                .latestLogs(run.getLatestLogs())
                .errorMessage(run.getErrorMessage())
                .recommendedPollingIntervalMillis(pollingIntervalFor(run.getStatus()))
                .build();
    }

    public static PromptTuneRunResponse notStarted(Long buildRunId, String cacheKey) {
        return PromptTuneRunResponse.builder()
                .buildRunId(buildRunId)
                .cacheKey(cacheKey)
                .status("not_started")
                .progressStage("idle")
                .cacheHit(false)
                .recommendedPollingIntervalMillis(0)
                .build();
    }

    private static Integer pollingIntervalFor(String status) {
        if ("running".equals(status) || "pending".equals(status)) {
            // 调优是分钟级任务，3 秒轮询一次足够、且不会给后端造成压力。
            return 3000;
        }
        return 0;
    }
}
