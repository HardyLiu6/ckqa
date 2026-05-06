package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 课程访问授权判定。
 * <p>
 * 登录注册尚未接入时，管理端通过稳定 userCode 请求头传入开发态身份。
 * 没有请求头的调用保持开发兼容，不主动拦截旧联调命令。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CourseAccessService {

    public static final String ACTOR_USER_CODE_HEADER = "X-CKQA-User-Code";

    private final CoursesService coursesService;
    private final CourseMembershipsService courseMembershipsService;
    private final UsersService usersService;

    public CourseAccessDecision resolveAccess(String courseId, String actorUserCode) {
        Courses course = getRequiredCourse(courseId);
        return resolveAccess(course, actorUserCode);
    }

    public CourseAccessDecision resolveAccess(Courses course, String actorUserCode) {
        String courseId = course == null ? null : course.getCourseId();
        if (course == null) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if ("public".equalsIgnoreCase(course.getAccessPolicy())) {
            return CourseAccessDecision.granted(courseId, null, normalize(actorUserCode), null, null, "public-course");
        }

        Users actor = findActiveUserByCode(actorUserCode);
        if (actor == null) {
            return CourseAccessDecision.denied(courseId, null, normalize(actorUserCode), "actor-not-found");
        }

        if (usersService.hasRole(actor.getId(), "admin")) {
            return CourseAccessDecision.granted(courseId, actor.getId(), actor.getUserCode(), null, null, "admin-override");
        }

        return courseMembershipsService.listByCourseId(courseId).stream()
                .filter(membership -> Objects.equals(membership.getUserId(), actor.getId()))
                .filter(this::isActiveMembership)
                .findFirst()
                .map(membership -> CourseAccessDecision.granted(
                        courseId,
                        actor.getId(),
                        actor.getUserCode(),
                        membership.getId(),
                        membership.getMembershipRole(),
                        "active-membership"
                ))
                .orElseGet(() -> CourseAccessDecision.denied(courseId, actor.getId(), actor.getUserCode(), "no-active-membership"));
    }

    public boolean canReadCourse(Courses course, String actorUserCode) {
        if (!StringUtils.hasText(actorUserCode)) {
            return true;
        }
        return resolveAccess(course, actorUserCode).isGranted();
    }

    public void assertCourseReadable(String courseId, String actorUserCode) {
        if (!StringUtils.hasText(actorUserCode)) {
            return;
        }
        CourseAccessDecision decision = resolveAccess(courseId, actorUserCode);
        if (!decision.isGranted()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程访问权限");
        }
    }

    public void assertCourseMembershipWritable(String courseId, String actorUserCode) {
        if (!StringUtils.hasText(actorUserCode)) {
            return;
        }
        CourseAccessDecision decision = resolveAccess(courseId, actorUserCode);
        if (!decision.isGranted()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程成员管理权限");
        }
        if ("admin-override".equals(decision.getReason())) {
            return;
        }
        String role = decision.getMembershipRole();
        if (!"teacher".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程成员管理权限");
        }
    }

    public Users findActiveUserByCode(String userCode) {
        String normalized = normalize(userCode);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Users user = usersService.getOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getUserCode, normalized)
                .last("LIMIT 1"));
        if (user == null || !"active".equalsIgnoreCase(user.getStatus())) {
            return null;
        }
        return user;
    }

    public boolean isActiveMembership(CourseMemberships membership) {
        if (membership == null || !"active".equalsIgnoreCase(membership.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (membership.getEffectiveFrom() != null && membership.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (membership.getEffectiveTo() != null && !membership.getEffectiveTo().isAfter(now)) {
            return false;
        }
        return membership.getExpiresAt() == null || membership.getExpiresAt().isAfter(now);
    }

    private Courses getRequiredCourse(String courseId) {
        Courses course = coursesService.getOne(new LambdaQueryWrapper<Courses>()
                .eq(Courses::getCourseId, courseId)
                .last("LIMIT 1"));
        if (course == null) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return course;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
