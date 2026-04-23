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
 * 资料对象表
 * </p>
 *
 * @author codex
 * @since 2026-04-23
 */
@Getter
@Setter
@ToString
@TableName("material_objects")
public class MaterialObjects implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 原始文件名
     */
    @TableField("original_file_name")
    private String originalFileName;

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
     * MIME类型
     */
    @TableField("mime_type")
    private String mimeType;

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
