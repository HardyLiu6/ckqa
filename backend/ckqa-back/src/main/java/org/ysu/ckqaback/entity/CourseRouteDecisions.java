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
 * 课程路由判定日志。
 */
@Getter
@Setter
@ToString
@TableName("course_route_decisions")
public class CourseRouteDecisions implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("question_hash")
    private String questionHash;

    @TableField("question_text")
    private String questionText;

    @TableField("status")
    private String status;

    @TableField("selected_course_id")
    private String selectedCourseId;

    @TableField("confidence")
    private Double confidence;

    @TableField("margin")
    private Double margin;

    @TableField("candidates_json")
    private String candidatesJson;

    @TableField("embedding_model")
    private String embeddingModel;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
