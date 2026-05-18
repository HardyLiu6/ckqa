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
    private static final String ARCHIVED = "archived";
    private static final String ARCHIVED_COURSE_READONLY_MESSAGE = "已归档课程不可编辑，请先撤销归档";

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

        Users actor = findActiveUserByCode(actorUserCode);

        if ("public".equalsIgnoreCase(course.getAccessPolicy())) {
            // 公开课程对读访问无门槛；如果调用方携带了有效身份，
            // 优先返回 admin-override（让写权限校验能够识别管理员身份），
            // 否则回退为 public-course（仅授权读取）。
            if (actor != null && usersService.hasRole(actor.getId(), "admin")) {
                return CourseAccessDecision.granted(courseId, actor.getId(), actor.getUserCode(), null, null, "admin-override");
            }
            return CourseAccessDecision.granted(courseId, actor == null ? null : actor.getId(),
                    actor == null ? normalize(actorUserCode) : actor.getUserCode(), null, null, "public-course");
        }

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
        Courses course = getRequiredCourse(courseId);
        assertCourseWritable(course);
        if (!StringUtils.hasText(actorUserCode)) {
            return;
        }
        CourseAccessDecision decision = resolveAccess(course, actorUserCode);
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

    /**
     * 校验课程资料读写权限（上传 / 编辑 / 删除）。
     * 与 assertCourseMembershipWritable 区分开：
     * - 该方法用于资料管理操作，授权语义不同，错误消息直观（"无课程资料管理权限"）。
     * - 管理员通过 admin-override 直接放行；
     * - 教师 / 助教持有 active membership 即可操作课程资料；
     * - 其他角色（如学生）和无 membership 的非管理员被拒绝。
     */
    public void assertCourseMaterialWritable(String courseId, String actorUserCode) {
        Courses course = getRequiredCourse(courseId);
        assertCourseWritable(course);
        if (!StringUtils.hasText(actorUserCode)) {
            return;
        }
        CourseAccessDecision decision = resolveAccess(course, actorUserCode);
        if (!decision.isGranted()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程资料管理权限");
        }
        if ("admin-override".equals(decision.getReason())) {
            return;
        }
        String role = decision.getMembershipRole();
        if (!"teacher".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程资料管理权限");
        }
    }

    public void assertCourseWritable(String courseId) {
        assertCourseWritable(getRequiredCourse(courseId));
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

    private void assertCourseWritable(Courses course) {
        if (course != null && ARCHIVED.equalsIgnoreCase(course.getStatus())) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.CONFLICT,
                    ARCHIVED_COURSE_READONLY_MESSAGE
            );
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
