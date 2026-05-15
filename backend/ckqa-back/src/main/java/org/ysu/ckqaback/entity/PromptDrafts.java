package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动调优历史草稿表。
 * <p>
 * 存储 05 步保存的历史草稿，供 01 步种子选择复用。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_drafts")
public class PromptDrafts implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /** system_default / graphrag_tuned / prompt_draft:N */
    @TableField("seed")
    private String seed;

    @TableField("candidate_id")
    private String candidateId;

    /** JSON：多 key prompt 内容快照 */
    @TableField("prompts_json")
    private String promptsJson;

    @TableField("source_build_run_id")
    private Long sourceBuildRunId;

    @TableField("composite_score")
    private BigDecimal compositeScore;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
