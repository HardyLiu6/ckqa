package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 课程表
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Getter
@Setter
@ToString
@TableName("courses")
public class Courses implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 逻辑删除标记：0-未删除，1-已删除
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    /**
     * 课程ID，如: os, cs61b
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 课程名称
     */
    @TableField("course_name")
    private String courseName;

    /**
     * 课程描述
     */
    @TableField("description")
    private String description;

    /**
     * 课程分类（自由输入），如：人工智能/前端开发
     */
    @TableField("category")
    private String category;

    /**
     * 课程标签 JSON 数组（最多 20 个，DTO 校验）
     */
    @TableField("tags")
    private String tags;

    /**
     * 学习目标 JSON 数组（最多 12 条，DTO 校验）
     */
    @TableField("objectives")
    private String objectives;

    /**
     * 适合人群 JSON 数组（最多 10 条，DTO 校验）
     */
    @TableField("audience")
    private String audience;

    /**
     * 难度级别：beginner / intermediate / advanced
     */
    @TableField("difficulty")
    private String difficulty;

    /**
     * 预计学习时长（小时），完整 LMS 上线前由教师手填
     */
    @TableField("estimated_hours")
    private Integer estimatedHours;

    /**
     * 课程封面访问地址
     */
    @TableField("cover_url")
    private String coverUrl;

    /**
     * 课程状态
     */
    @TableField("status")
    private String status;

    /**
     * 访问策略
     */
    @TableField("access_policy")
    private String accessPolicy;

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
