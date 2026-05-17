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
     * GraphRAG 原始来源编号
     */
    @TableField("source_ref")
    private String sourceRef;

    /**
     * 来源文件名
     */
    @TableField("source_file")
    private String sourceFile;

    /**
     * 章节路径
     */
    @TableField("heading_path")
    private String headingPath;

    /**
     * 起始页
     */
    @TableField("page_start")
    private Integer pageStart;

    /**
     * 结束页
     */
    @TableField("page_end")
    private Integer pageEnd;

    /**
     * 来源片段
     */
    @TableField("snippet")
    private String snippet;

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
