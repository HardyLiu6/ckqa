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
 * <p>
 * GraphRAG 提示词自动调优运行表。
 * </p>
 *
 * <p>缓存按 cache_key（基于 selected_materials 的 PDF MD5 集合派生 sha256）查找；
 * 同一 cache_key 命中 success 即视为已经调优过，可直接复用 {@link #candidateDir}
 * 下的 {@code extract_graph.txt}。</p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_tune_runs")
public class PromptTuneRuns implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 触发本次调优的构建流水线 ID。
     * <p>缓存复用时新 build run 不会写新行，因此外键允许为空。</p>
     */
    @TableField("build_run_id")
    private Long buildRunId;

    @TableField("course_id")
    private String courseId;

    /**
     * 本次调优选用的资料 ID 列表（json 字符串）。
     */
    @TableField("selected_material_ids")
    private String selectedMaterialIds;

    /**
     * 缓存键：sorted(materialId:fileMd5) 的 sha256 hex。
     */
    @TableField("cache_key")
    private String cacheKey;

    /**
     * pending / running / success / failed / cancelled。
     */
    @TableField("status")
    private String status;

    /**
     * queued / fetch_input / prompt_tune / done。
     */
    @TableField("progress_stage")
    private String progressStage;

    /**
     * 相对 {@code GRAPHRAG_BUILD_RUNS_ROOT} 的产物目录路径。
     */
    @TableField("candidate_dir")
    private String candidateDir;

    @TableField("prompt_sha256")
    private String promptSha256;

    @TableField("latest_logs")
    private String latestLogs;

    @TableField("error_message")
    private String errorMessage;

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
