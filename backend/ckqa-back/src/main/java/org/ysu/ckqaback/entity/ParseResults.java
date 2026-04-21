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
 * 解析结果表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("parse_results")
public class ParseResults implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的PDF文件ID
     */
    @TableField("pdf_file_id")
    private Long pdfFileId;

    /**
     * 课程ID
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 结果类型
     */
    @TableField("result_type")
    private String resultType;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * MinIO存储桶
     */
    @TableField("minio_bucket")
    private String minioBucket;

    /**
     * MinIO对象键
     */
    @TableField("minio_object_key")
    private String minioObjectKey;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
