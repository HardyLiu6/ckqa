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
 * <p>
 * 问答命中文档表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("qa_retrieval_hits")
public class QaRetrievalHits implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 检索日志ID
     */
    @TableField("retrieval_log_id")
    private Long retrievalLogId;

    /**
     * 命中文档键
     */
    @TableField("document_key")
    private String documentKey;

    /**
     * 命中块ID
     */
    @TableField("chunk_id")
    private String chunkId;

    /**
     * 排序位置
     */
    @TableField("rank_position")
    private Integer rankPosition;

    /**
     * 召回分数
     */
    @TableField("score")
    private BigDecimal score;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
