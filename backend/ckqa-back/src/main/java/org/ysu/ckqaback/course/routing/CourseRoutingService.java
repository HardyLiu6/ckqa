package org.ysu.ckqaback.course.routing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.dto.CourseRoutingCandidateResponse;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendRequest;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendResponse;
import org.ysu.ckqaback.entity.CourseRouteDecisions;
import org.ysu.ckqaback.entity.CourseRouteProfiles;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingProfileUpsertRequest;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingRecommendRequest;
import org.ysu.ckqaback.service.CourseRouteDecisionsService;
import org.ysu.ckqaback.service.CourseRouteProfilesService;
import org.ysu.ckqaback.service.CoursesService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 课程画像语义路由服务。
 */
@Service
@RequiredArgsConstructor
public class CourseRoutingService implements CourseScopeRelevanceProvider {

    private final CoursesService coursesService;
    private final CourseRouteProfilesService profilesService;
    private final CourseRouteDecisionsService decisionsService;
    private final CourseProfileTextBuilder profileTextBuilder;
    private final GraphRagCourseRoutingClient graphRagClient;
    private final CourseRoutingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourseAccessService courseAccessService;

    @Autowired(required = false)
    public void setCourseAccessService(CourseAccessService courseAccessService) {
        this.courseAccessService = courseAccessService;
    }

    public CourseRoutingRecommendResponse recommend(CourseRoutingRecommendRequest request, AuthenticatedUser currentUser) {
        if (!properties.isEnabled()) {
            return logAndReturn(request, currentUser, List.of(), new CourseRoutingDecision("no_match", null, 0D, 0D));
        }

        List<Courses> readableCourses = coursesService.list().stream()
                .filter(course -> StringUtils.hasText(course.getCourseId()))
                .filter(this::isRoutableCourse)
                .filter(course -> courseAccessService == null
                        || courseAccessService.canReadCourse(course, currentUser == null ? null : currentUser.userCode()))
                .toList();
        if (readableCourses.isEmpty()) {
            return logAndReturn(request, currentUser, List.of(), new CourseRoutingDecision("no_match", null, 0D, 0D));
        }

        List<CourseRoutingCandidateResponse> candidates;
        try {
            ensureProfiles(readableCourses);

            List<String> courseIds = readableCourses.stream().map(Courses::getCourseId).toList();
            int limit = request.getLimit() == null ? properties.getTopK() : Math.min(request.getLimit(), properties.getTopK());
            var recommendResponse = graphRagClient.recommend(
                    new GraphRagCourseRoutingRecommendRequest(request.getQuestion(), courseIds, Math.max(1, limit))
            );
            var rawCandidates = recommendResponse == null || recommendResponse.candidates() == null
                    ? List.<org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingRecommendResponse.Candidate>of()
                    : recommendResponse.candidates();
            candidates = rawCandidates
                    .stream()
                    .map(candidate -> CourseRoutingCandidateResponse.of(
                            candidate.courseId(),
                            resolveCourseName(readableCourses, candidate.courseId(), candidate.courseName()),
                            candidate.confidence(),
                            candidate.reason()
                    ))
                    .toList();
        } catch (RuntimeException ex) {
            return logAndReturn(request, currentUser, List.of(), new CourseRoutingDecision("no_match", null, 0D, 0D));
        }

        CourseRoutingDecision decision = new CourseRoutingDecisionPolicy(
                properties.getScoreThreshold(),
                properties.getMarginThreshold()
        ).decide(candidates);
        return logAndReturn(request, currentUser, candidates, decision);
    }

    @Override
    public ScopeRelevance evaluateScopeRelevance(String courseId, String question) {
        if (!properties.isEnabled() || !StringUtils.hasText(courseId) || !StringUtils.hasText(question)) {
            return ScopeRelevance.notEvaluated();
        }
        try {
            Courses course = coursesService.getOne(
                    new LambdaQueryWrapper<Courses>().eq(Courses::getCourseId, courseId));
            if (course == null || !isRoutableCourse(course)) {
                return ScopeRelevance.notEvaluated();
            }
            ensureProfiles(List.of(course));
            var response = graphRagClient.recommend(
                    new GraphRagCourseRoutingRecommendRequest(question, List.of(courseId), 1));
            var candidates = response == null ? null : response.candidates();
            if (candidates == null || candidates.isEmpty()) {
                return ScopeRelevance.notEvaluated();
            }
            Double confidence = candidates.getFirst().confidence();
            if (confidence == null) {
                return ScopeRelevance.notEvaluated();
            }
            return ScopeRelevance.evaluated(confidence);
        } catch (RuntimeException ex) {
            return ScopeRelevance.notEvaluated();
        }
    }

    private void ensureProfiles(List<Courses> courses) {
        List<ProfileUpsertCandidate> upsertCandidates = new ArrayList<>();
        for (Courses course : courses) {
            CourseProfileSnapshot snapshot = profileTextBuilder.build(course);
            Optional<CourseRouteProfiles> existing = profilesService.findActiveByCourseAndModel(
                    course.getCourseId(),
                    properties.getEmbeddingModel(),
                    properties.getEmbeddingDimensions()
            );
            if (existing.isPresent()
                    && Objects.equals(existing.get().getProfileHash(), snapshot.profileHash())
                    && StringUtils.hasText(existing.get().getVectorId())) {
                continue;
            }
            upsertCandidates.add(new ProfileUpsertCandidate(course, snapshot, existing.orElse(null)));
        }

        if (upsertCandidates.isEmpty()) {
            return;
        }

        List<GraphRagCourseRoutingProfileUpsertRequest.Item> requestItems = upsertCandidates.stream()
                .map(item -> new GraphRagCourseRoutingProfileUpsertRequest.Item(
                        item.course().getCourseId(),
                        item.course().getCourseName(),
                        item.snapshot().profileText(),
                        item.snapshot().profileHash(),
                        Map.of("courseStatus", item.course().getStatus() == null ? "" : item.course().getStatus())
                ))
                .toList();
        var response = graphRagClient.upsertProfiles(requestItems);
        Map<String, GraphRagCourseRoutingProfileUpsertRequest.Item> byCourseId = new LinkedHashMap<>();
        requestItems.forEach(item -> byCourseId.put(item.courseId(), item));
        Map<String, CourseRouteProfiles> existingByCourseId = new LinkedHashMap<>();
        upsertCandidates.forEach(item -> existingByCourseId.put(item.course().getCourseId(), item.existing()));
        LocalDateTime now = LocalDateTime.now();
        List<org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingProfileUpsertResponse.Item> items =
                response == null || response.items() == null ? List.of() : response.items();
        items.forEach(item -> {
            GraphRagCourseRoutingProfileUpsertRequest.Item requestItem = byCourseId.get(item.courseId());
            CourseRouteProfiles profile = existingByCourseId.get(item.courseId());
            if (profile == null) {
                profile = new CourseRouteProfiles();
                profile.setCourseId(item.courseId());
                profile.setCreatedAt(now);
            }
            profile.setProfileText(requestItem.profileText());
            profile.setProfileHash(item.profileHash());
            profile.setEmbeddingModel(properties.getEmbeddingModel());
            profile.setEmbeddingDimensions(properties.getEmbeddingDimensions());
            profile.setLancedbTable(properties.getLancedbTable());
            profile.setVectorId(item.vectorId());
            profile.setStatus("active");
            profile.setLastEmbeddedAt(now);
            profile.setUpdatedAt(now);
            profilesService.saveOrUpdate(profile);
        });
    }

    private String resolveCourseName(List<Courses> courses, String courseId, String fallback) {
        return courses.stream()
                .filter(course -> Objects.equals(course.getCourseId(), courseId))
                .map(Courses::getCourseName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(fallback);
    }

    private boolean isRoutableCourse(Courses course) {
        if (course == null || !"active".equalsIgnoreCase(course.getStatus())) {
            return false;
        }
        if (containsConfiguredValue(properties.getExcludedCourseIds(), course.getCourseId())) {
            return false;
        }
        return !hasExcludedRoutingTag(course.getTags());
    }

    private boolean hasExcludedRoutingTag(String tagsJson) {
        if (!StringUtils.hasText(tagsJson) || properties.getExcludedCourseTags() == null
                || properties.getExcludedCourseTags().isEmpty()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(tagsJson);
            if (!root.isArray()) {
                return false;
            }
            for (JsonNode item : root) {
                if (item.isTextual() && containsConfiguredValue(properties.getExcludedCourseTags(), item.asText())) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private boolean containsConfiguredValue(List<String> configuredValues, String value) {
        if (!StringUtils.hasText(value) || configuredValues == null || configuredValues.isEmpty()) {
            return false;
        }
        String normalizedValue = value.trim();
        return configuredValues.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(configuredValue -> configuredValue.equalsIgnoreCase(normalizedValue));
    }

    private CourseRoutingRecommendResponse logAndReturn(
            CourseRoutingRecommendRequest request,
            AuthenticatedUser currentUser,
            List<CourseRoutingCandidateResponse> candidates,
            CourseRoutingDecision decision
    ) {
        CourseRouteDecisions row = new CourseRouteDecisions();
        row.setUserId(currentUser == null ? request.getUserId() : currentUser.id());
        row.setQuestionText(request.getQuestion());
        row.setQuestionHash(sha256(request.getQuestion()));
        row.setStatus(decision.status());
        row.setSelectedCourseId(decision.selectedCourseId());
        row.setConfidence(decision.confidence());
        row.setMargin(decision.margin());
        row.setCandidatesJson(toJson(candidates));
        row.setEmbeddingModel(properties.getEmbeddingModel());
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        decisionsService.save(row);
        return CourseRoutingRecommendResponse.of(
                decision.status(),
                decision.selectedCourseId(),
                decision.confidence(),
                decision.margin(),
                candidates
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("课程路由问题 hash 生成失败", ex);
        }
    }

    private record ProfileUpsertCandidate(
            Courses course,
            CourseProfileSnapshot snapshot,
            CourseRouteProfiles existing
    ) {
    }
}
