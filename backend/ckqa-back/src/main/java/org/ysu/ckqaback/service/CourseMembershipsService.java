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
}
