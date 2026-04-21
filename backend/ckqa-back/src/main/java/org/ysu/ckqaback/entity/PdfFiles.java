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
 * PDF文件表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("pdf_files")
public class PdfFiles implements Serializable {

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
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件MD5哈希值
     */
    @TableField("file_md5")
    private String fileMd5;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * MinIO存储桶名称
     */
    @TableField("minio_bucket")
    private String minioBucket;

    /**
     * MinIO对象键（路径）
     */
    @TableField("minio_object_key")
    private String minioObjectKey;

    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;

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
