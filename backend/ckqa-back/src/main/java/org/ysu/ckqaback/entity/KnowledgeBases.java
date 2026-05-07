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
 * 课程知识库表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("knowledge_bases")
public class KnowledgeBases implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 课程ID
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 知识库编码
     */
    @TableField("kb_code")
    private String kbCode;

    /**
     * 知识库名称
     */
    @TableField("name")
    private String name;

    /**
     * 知识库状态
     */
    @TableField("status")
    private String status;

    /**
     * 课程归档联动前的知识库状态，仅用于撤销课程归档时恢复。
     */
    @TableField("course_archive_previous_status")
    private String courseArchivePreviousStatus;

    /**
     * 当前激活索引运行ID
     */
    @TableField("active_index_run_id")
    private Long activeIndexRunId;

    /**
     * 知识库说明
     */
    @TableField("description")
    private String description;

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
