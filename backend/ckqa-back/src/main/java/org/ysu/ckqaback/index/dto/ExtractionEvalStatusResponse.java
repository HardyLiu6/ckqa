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

    /**
     * 失败终态下，是否可以仅重跑「评分汇总」而不重新抽取（Phase 5.1）。
     * <p>触发条件：status=failed、progress_stage=scoring、抽取产物（sharedExtractDir/&lt;runId&gt;_*.json）
     * 仍在磁盘上。前端据此把单按钮「重试」拆成「仅重跑评分」+「重新抽取」二选一，避免动辄重跑 30+ 分钟抽取。
     * 其他终态或抽取产物已被清理 / 缺失时为 false（含 null），调用方走旧的 trigger 全量重跑路径。</p>
     */
    private final Boolean recoverableScoringOnly;

    /**
     * 当前 buildRun 下最近一条「抽取已完成、可仅补跑评分」的 evalRunId。
     * <p>覆盖范围比 {@code recoverableScoringOnly} 宽：包含 failed 和 cancelled 终态，且不要求是
     * 最新一次任务。例如最新 run 是 cancelled，但更早的 run 抽取阶段已完成（progress_stage=scoring,
     * finished_candidates 非空），就让前端在失败页提供「按 evalRun #X 的产物补跑评分」按钮，
     * 让用户不必重跑 30+ 分钟抽取就能拿到上次的评估结果。null 表示无可复用产物。</p>
     */
    private final Long recoverableScoringEvalRunId;

    /**
     * 当前 buildRun 下最近一条 status=success 的 evalRunId（与本次任务可能不同）。
     * <p>用于失败 / 取消 / 中止终态下让前端入口"查看上次评分结果"——即便当前最新任务异常，
     * 历史 success 报告仍可通过 GET /extraction-eval/report?evalRunId=X 访问。无历史 success
     * 记录时为 null，前端不显示该入口。</p>
     */
    private final Long lastSuccessfulEvalRunId;

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
