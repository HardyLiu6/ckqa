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
 * 问答来源人工标注表。
 */
@Getter
@Setter
@ToString
@TableName("qa_source_reviews")
public class QaSourceReviews implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("retrieval_hit_id")
    private Long retrievalHitId;

    @TableField("retrieval_log_id")
    private Long retrievalLogId;

    @TableField("reviewer_user_id")
    private Long reviewerUserId;

    @TableField("relevance")
    private String relevance;

    @TableField("citation_quality")
    private String citationQuality;

    @TableField("note")
    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
