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
 * 知识库构建流水线表
 * </p>
 *
 * @author codex
 * @since 2026-05-05
 */
@Getter
@Setter
@ToString
@TableName("knowledge_base_build_runs")
public class KnowledgeBaseBuildRuns implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 课程ID快照
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 发起用户ID
     */
    @TableField("requested_by_user_id")
    private Long requestedByUserId;

    /**
     * 构建版本
     */
    @TableField("build_version")
    private String buildVersion;

    /**
     * 流水线状态
     */
    @TableField("status")
    private String status;

    /**
     * 当前阶段
     */
    @TableField("current_stage")
    private String currentStage;

    /**
     * 问答验证状态
     */
    @TableField("qa_status")
    private String qaStatus;

    /**
     * 自动激活策略
     */
    @TableField("activation_policy")
    private String activationPolicy;

    /**
     * 本次构建资料选择快照
     */
    @TableField("selected_material_ids")
    private String selectedMaterialIds;

    /**
     * 当前由该构建承载的激活索引运行
     */
    @TableField("active_index_run_id")
    private Long activeIndexRunId;

    /**
     * 相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区路径
     */
    @TableField("workspace_uri")
    private String workspaceUri;

    /**
     * 构建元数据
     */
    @TableField("build_metadata")
    private String buildMetadata;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
