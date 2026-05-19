package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.mapper.CourseMembershipsMapper;
import org.ysu.ckqaback.service.CourseMembershipsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 课程成员关系表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class CourseMembershipsServiceImpl extends ServiceImpl<CourseMembershipsMapper, CourseMemberships> implements CourseMembershipsService {

    @Override
    public List<CourseMemberships> listByCourseId(String courseId) {
        return list(new LambdaQueryWrapper<CourseMemberships>()
                .eq(CourseMemberships::getCourseId, courseId)
                .orderByAsc(CourseMemberships::getId));
    }

    @Override
    public List<CourseMemberships> listActiveTeachersByCourseIds(Collection<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<CourseMemberships>()
                .in(CourseMemberships::getCourseId, courseIds)
                .eq(CourseMemberships::getMembershipRole, "teacher")
                .eq(CourseMemberships::getStatus, "active")
                .orderByAsc(CourseMemberships::getCourseId)
                .orderByAsc(CourseMemberships::getId));
    }

    @Override
    public List<CourseMemberships> listActiveByUserIdAndCourseIds(Long userId, Collection<String> courseIds) {
        if (userId == null || courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<CourseMemberships>()
                .eq(CourseMemberships::getUserId, userId)
                .in(CourseMemberships::getCourseId, courseIds)
                .eq(CourseMemberships::getStatus, "active")
                .orderByAsc(CourseMemberships::getCourseId));
    }
}
