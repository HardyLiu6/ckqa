package org.ysu.ckqaback.course;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseMembershipCreateRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipResponse;
import org.ysu.ckqaback.course.dto.CourseMembershipUpdateRequest;
import org.ysu.ckqaback.entity.CourseMemberships;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMembershipsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程成员管理服务。
 */
@Service
@RequiredArgsConstructor
public class CourseMembershipManagementService {

    private final CourseMembershipsService courseMembershipsService;
    private final UsersService usersService;
    private final CourseAccessService courseAccessService;

    public ApiPageData<CourseMembershipResponse> listCourseMembers(
            String courseId,
            CourseMembershipQueryRequest request,
            String actorUserCode
    ) {
        courseAccessService.assertCourseReadable(courseId, actorUserCode);
        long page = request.getPage() == null ? 1L : Math.max(1L, request.getPage());
        long size = request.getSize() == null ? 20L : Math.max(1L, request.getSize());

        List<CourseMemberships> memberships = courseMembershipsService.listByCourseId(courseId);
        Map<Long, Users> usersById = buildUsersById(memberships);
        List<CourseMemberships> filtered = memberships.stream()
                .filter(membership -> matches(membership.getMembershipRole(), request.getMembershipRole()))
                .filter(membership -> matches(membership.getStatus(), request.getStatus()))
                .filter(membership -> matchesKeyword(membership, usersById.get(membership.getUserId()), request.getKeyword()))
                .sorted(Comparator.comparing(CourseMemberships::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        long total = filtered.size();
        long pages = total == 0 ? 0L : (long) Math.ceil((double) total / size);
        long offset = (page - 1L) * size;
        if (offset >= total) {
            return new ApiPageData<>(List.of(), page, size, total, pages);
        }

        List<CourseMemberships> pageItems = filtered.subList((int) offset, (int) Math.min(offset + size, total));
        Map<Long, Users> grantedUsersById = buildGrantedUsersById(pageItems);
        List<CourseMembershipResponse> responses = pageItems.stream()
                .map(membership -> toResponse(membership, usersById.get(membership.getUserId()), grantedUsersById))
                .toList();
        return new ApiPageData<>(responses, page, size, total, pages);
    }

    @Transactional
    public CourseMembershipResponse createCourseMember(
            String courseId,
            CourseMembershipCreateRequest request,
            String actorUserCode
    ) {
        courseAccessService.assertCourseMembershipWritable(courseId, actorUserCode);
        Users memberUser = usersService.getRequiredById(request.getUserId());
        Users actor = courseAccessService.findActiveUserByCode(actorUserCode);
        LocalDateTime now = LocalDateTime.now();
        CourseMemberships membership = courseMembershipsService.listByCourseId(courseId).stream()
                .filter(item -> Objects.equals(item.getUserId(), request.getUserId()))
                .findFirst()
                .orElseGet(() -> newMembership(courseId, request.getUserId(), now));

        applyCreateRequest(membership, request, actor, now);
        if (membership.getId() == null) {
            courseMembershipsService.save(membership);
        } else {
            courseMembershipsService.updateById(membership);
        }

        return CourseMembershipResponse.fromEntity(membership, memberUser, actor, courseAccessService.isActiveMembership(membership));
    }

    @Transactional
    public CourseMembershipResponse updateCourseMember(
            String courseId,
            Long membershipId,
            CourseMembershipUpdateRequest request,
            String actorUserCode
    ) {
        CourseMemberships membership = courseMembershipsService.getById(membershipId);
        if (membership == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程成员不存在");
        }
        String requestedCourseId = trimToNull(courseId);
        if (requestedCourseId != null && !Objects.equals(membership.getCourseId(), requestedCourseId)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程成员不存在");
        }
        courseAccessService.assertCourseMembershipWritable(membership.getCourseId(), actorUserCode);

        Users actor = courseAccessService.findActiveUserByCode(actorUserCode);
        LocalDateTime now = LocalDateTime.now();
        applyUpdateRequest(membership, request, actor, now);
        courseMembershipsService.updateById(membership);

        Map<Long, Users> usersById = buildUsersById(List.of(membership));
        Map<Long, Users> grantedUsersById = buildGrantedUsersById(List.of(membership));
        return toResponse(membership, usersById.get(membership.getUserId()), grantedUsersById);
    }

    private CourseMemberships newMembership(String courseId, Long userId, LocalDateTime now) {
        CourseMemberships membership = new CourseMemberships();
        membership.setCourseId(courseId);
        membership.setUserId(userId);
        membership.setJoinedAt(now);
        membership.setEffectiveFrom(now);
        membership.setCreatedAt(now);
        return membership;
    }

    private void applyCreateRequest(CourseMemberships membership, CourseMembershipCreateRequest request, Users actor, LocalDateTime now) {
        membership.setMembershipRole(defaultIfBlank(request.getMembershipRole(), "student"));
        membership.setStatus(defaultIfBlank(request.getStatus(), "active"));
        membership.setAccessSource(defaultIfBlank(request.getAccessSource(), "manual"));
        membership.setChangeReason(trimToNull(request.getChangeReason()));
        if (membership.getEffectiveFrom() == null) {
            membership.setEffectiveFrom(now);
        }
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(now);
        }
        if (actor != null) {
            membership.setGrantedByUserId(actor.getId());
        }
        if (!"removed".equalsIgnoreCase(membership.getStatus())) {
            membership.setRevokedByUserId(null);
            membership.setEffectiveTo(null);
        }
        membership.setUpdatedAt(now);
    }

    private void applyUpdateRequest(CourseMemberships membership, CourseMembershipUpdateRequest request, Users actor, LocalDateTime now) {
        if (StringUtils.hasText(request.getMembershipRole())) {
            membership.setMembershipRole(request.getMembershipRole().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            membership.setStatus(request.getStatus().trim());
        }
        if (StringUtils.hasText(request.getAccessSource())) {
            membership.setAccessSource(request.getAccessSource().trim());
        }
        if (StringUtils.hasText(request.getChangeReason())) {
            membership.setChangeReason(request.getChangeReason().trim());
        }
        if ("removed".equalsIgnoreCase(membership.getStatus())) {
            membership.setEffectiveTo(now);
            if (actor != null) {
                membership.setRevokedByUserId(actor.getId());
            }
        } else if ("active".equalsIgnoreCase(membership.getStatus())) {
            if (membership.getEffectiveFrom() == null) {
                membership.setEffectiveFrom(now);
            }
            membership.setEffectiveTo(null);
            membership.setRevokedByUserId(null);
        }
        membership.setUpdatedAt(now);
    }

    private CourseMembershipResponse toResponse(
            CourseMemberships membership,
            Users user,
            Map<Long, Users> grantedUsersById
    ) {
        Users grantedBy = membership.getGrantedByUserId() == null ? null : grantedUsersById.get(membership.getGrantedByUserId());
        return CourseMembershipResponse.fromEntity(
                membership,
                user,
                grantedBy,
                courseAccessService.isActiveMembership(membership)
        );
    }

    private Map<Long, Users> buildUsersById(List<CourseMemberships> memberships) {
        List<Long> userIds = memberships.stream()
                .map(CourseMemberships::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return usersService.listByIds(userIds).stream()
                .collect(Collectors.toMap(Users::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Users> buildGrantedUsersById(List<CourseMemberships> memberships) {
        List<Long> userIds = memberships.stream()
                .map(CourseMemberships::getGrantedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return usersService.listByIds(userIds).stream()
                .collect(Collectors.toMap(Users::getId, Function.identity(), (left, right) -> left));
    }

    private boolean matches(String actual, String expected) {
        return !StringUtils.hasText(expected) || Objects.equals(actual, expected.trim());
    }

    private boolean matchesKeyword(CourseMemberships membership, Users user, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(membership.getCourseId(), normalized)
                || contains(membership.getMembershipRole(), normalized)
                || contains(user == null ? null : user.getUserCode(), normalized)
                || contains(user == null ? null : user.getUsername(), normalized)
                || contains(user == null ? null : user.getDisplayName(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
