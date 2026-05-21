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
 * 课程画像向量记录。
 */
@Getter
@Setter
@ToString
@TableName("course_route_profiles")
public class CourseRouteProfiles implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private String courseId;

    @TableField("profile_text")
    private String profileText;

    @TableField("profile_hash")
    private String profileHash;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField("embedding_dimensions")
    private Integer embeddingDimensions;

    @TableField("lancedb_table")
    private String lancedbTable;

    @TableField("vector_id")
    private String vectorId;

    @TableField("status")
    private String status;

    @TableField("last_embedded_at")
    private LocalDateTime lastEmbeddedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
