package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手动调优 04 步评分运行表。
 * <p>
 * 持久化每次评分任务的状态：pending → running → success/failed/cancelling/cancelled。
 * Worker 跑完后把 {@code top_candidates.json} 内容写入 {@link #reportJson}，
 * 列表查询时排除该列以避免拉满 ~1MB 文本。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_tune_extraction_eval_runs")
public class PromptTuneExtractionEvalRuns implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("build_run_id")
    private Long buildRunId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /** 用户在 03 步勾选的候选 ID 列表，JSON 字符串形态。 */
    @TableField("selected_candidate_ids")
    private String selectedCandidateIds;

    /**
     * 本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null）。
     * <p>由 Phase 4.5 引入，用于审计"本次评分基于哪个种子的候选 prompt"。
     * 若启动评分时 build run metadata 中没有 customPromptDraft.seed 字段，写入 null。</p>
     */
    @TableField("seed")
    private String seed;

    /** pending / running / success / failed / cancelling / cancelled。 */
    @TableField("status")
    private String status;

    /** queued / extracting / scoring / done。 */
    @TableField("progress_stage")
    private String progressStage;

    /** 当前正在抽取的候选 ID（为空表示无进行中候选）。 */
    @TableField("extracting_candidate_id")
    private String extractingCandidateId;

    /** 已完成候选 ID 数组（JSON 字符串）。 */
    @TableField("finished_candidates")
    private String finishedCandidates;

    /**
     * 失败候选结构化清单（JSON 字符串）。
     * <p>schema：{@code [{"candidateId":"...","stage":"extract","reason":"..."}]}。
     * worker 在 single-candidate 抽取异常时追加，scoring 阶段再汇总；
     * report 投影时映射为 {@code ExtractionEvalReportResponse.failedCandidates}，
     * 让排行榜外可单独展示"未进入排名"区域。</p>
     */
    @TableField("candidate_failures")
    private String candidateFailures;

    /** 相对 {@code GRAPHRAG_BUILD_RUNS_ROOT} 的评分产物目录路径，形如 {@code user_X/kb_Y/build_Z/eval/<evalRunId>}。 */
    @TableField("eval_dir")
    private String evalDir;

    /** {@code top_candidates.json} 序列化内容。 */
    @TableField("report_json")
    private String reportJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("latest_logs")
    private String latestLogs;

    @TableField("triggered_by_user_id")
    private Long triggeredByUserId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
