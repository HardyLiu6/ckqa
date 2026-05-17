package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /extraction-eval/status 评分进度响应。
 *
 * <p>从 {@code prompt_tune_extraction_eval_runs} 实时投影：</p>
 * <ul>
 *   <li>status / progressStage / errorMessage 直接来自 DB</li>
 *   <li>candidates[] 按 selectedCandidateIds 顺序，结合 finishedCandidates / extractingCandidateId 拼装每条状态</li>
 *   <li>overall.elapsedSeconds = now - startedAt（毫秒级）；estimatedRemainingSeconds 用候选数和已用时近似</li>
 * </ul>
 */
@Getter
@Builder
public class ExtractionEvalStatusResponse {

    /** 评分任务在数据库的主键 ID。 */
    private final Long evalRunId;

    /** pending / running / success / failed / cancelling / cancelled。 */
    private final String status;

    /** queued / extracting / scoring / done。 */
    private final String progressStage;

    /** 失败时的错误摘要。 */
    private final String errorMessage;

    /** 推荐前端轮询间隔（毫秒），任务终态时返回 null。 */
    private final Integer recommendedPollingIntervalMillis;

    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime lastHeartbeatAt;

    private final Overall overall;
    private final List<CandidateProgress> candidates;

    @Getter
    @Builder
    public static class Overall {
        /** 已完成的大模型抽取调用次数（已完成候选数 × 20）。 */
        private final Integer finishedCalls;
        /** 总调用次数（候选数 × 20）。 */
        private final Integer totalCalls;
        /** 起始到现在已用秒数。 */
        private final Integer elapsedSeconds;
        /** 估算剩余秒数（基于已完成候选 + 单候选平均耗时）。 */
        private final Integer estimatedRemainingSeconds;
        /** 已消耗 token 估算值（候选数 × 估算每候选 token），可空。 */
        private final Integer tokensUsed;
        private final Integer estimatedTotalTokens;
    }

    @Getter
    @Builder
    public static class CandidateProgress {
        private final String candidateId;
        private final String displayNameZh;
        /**
         * queued / extracting / scoring / done。
         * <p>本期不在进度 DTO 暴露单候选 failed 状态：单候选失败时 worker 仅追加
         * 结构化 candidate_failures 列与 latestLogs 行（参见 ExtractionEvalWorker.recordFailure），continue 到下一候选；
         * 失败信息最终在排行榜阶段通过 report 的 failedCandidates 数组呈现。</p>
         */
        private final String status;
        private final Stage extract;
        private final Stage score;
    }

    @Getter
    @Builder
    public static class Stage {
        private final Integer finished;
        private final Integer total;
        /** 当前阶段处理的样本 id（保留字段，本期候选粒度时返回 null）。 */
        private final String currentSampleId;
        /**
         * finished 是否为「按 elapsed 估算的中间值」。
         * <p>worker 当前只在候选边界写 DB（runSingleCandidateExtract 阻塞跑 ~8 min），
         * 中间没有真实样本级回写。前端拿到 0/20 二值会"看似不动"。
         * 这里在 service 层基于 elapsedSeconds 做估算补全，并打 estimated=true 提醒 UI 加上 "估算" 标签，
         * 不要让用户以为后端卡住。终态（done/failed/queued）时 estimated 不设置或为 false。</p>
         */
        private final Boolean estimated;
    }
}
