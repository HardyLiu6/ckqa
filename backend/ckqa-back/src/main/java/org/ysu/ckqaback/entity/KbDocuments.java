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
 * 知识库文档映射表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("kb_documents")
public class KbDocuments implements Serializable {

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
     * 文档来源类型
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 来源记录ID
     */
    @TableField("source_ref_id")
    private String sourceRefId;

    /**
     * 文档键
     */
    @TableField("document_key")
    private String documentKey;

    /**
     * 文档标题
     */
    @TableField("title")
    private String title;

    /**
     * 对象存储路径
     */
    @TableField("storage_uri")
    private String storageUri;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
