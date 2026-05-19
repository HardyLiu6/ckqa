package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.CourseMemberships;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 课程成员关系表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface CourseMembershipsService extends IService<CourseMemberships> {

    List<CourseMemberships> listByCourseId(String courseId);

    List<CourseMemberships> listActiveTeachersByCourseIds(Collection<String> courseIds);

    /**
     * 查询指定用户在给定课程列表中的 active 成员关系。
     * <p>用于学生端列表 / 详情判定 memberStatus（member / public_visitor）。</p>
     */
    List<CourseMemberships> listActiveByUserIdAndCourseIds(Long userId, Collection<String> courseIds);
}
