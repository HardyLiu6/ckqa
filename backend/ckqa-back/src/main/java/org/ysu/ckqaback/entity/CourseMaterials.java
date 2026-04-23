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
 * 课程资料关系表
 * </p>
 *
 * @author codex
 * @since 2026-04-23
 */
@Getter
@Setter
@ToString
@TableName("course_materials")
public class CourseMaterials implements Serializable {

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
     * 资料对象ID
     */
    @TableField("material_object_id")
    private Long materialObjectId;

    /**
     * 课程内展示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 资料类型
     */
    @TableField("material_type")
    private String materialType;

    /**
     * 解析状态
     */
    @TableField("parse_status")
    private String parseStatus;

    /**
     * 解析开始时间
     */
    @TableField("parse_started_at")
    private LocalDateTime parseStartedAt;

    /**
     * 解析完成时间
     */
    @TableField("parse_finished_at")
    private LocalDateTime parseFinishedAt;

    /**
     * 解析错误信息
     */
    @TableField("parse_error_msg")
    private String parseErrorMsg;

    /**
     * MinerU批次ID
     */
    @TableField("mineru_batch_id")
    private String mineruBatchId;

    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;

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
