package org.ysu.ckqaback.course.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendRequest;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.entity.CourseRouteProfiles;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingClient;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingProfileUpsertResponse;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingRecommendResponse;
import org.ysu.ckqaback.service.CourseRouteDecisionsService;
import org.ysu.ckqaback.service.CourseRouteProfilesService;
import org.ysu.ckqaback.service.CoursesService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;

class CourseRoutingServiceTest {

    private CoursesService coursesService;
    private CourseRouteProfilesService profilesService;
    private CourseRouteDecisionsService decisionsService;
    private CourseProfileTextBuilder profileTextBuilder;
    private GraphRagCourseRoutingClient graphRagClient;
    private CourseRoutingProperties properties;

    @BeforeEach
    void setUp() {
        coursesService = mock(CoursesService.class);
        profilesService = mock(CourseRouteProfilesService.class);
        decisionsService = mock(CourseRouteDecisionsService.class);
        profileTextBuilder = mock(CourseProfileTextBuilder.class);
        graphRagClient = mock(GraphRagCourseRoutingClient.class);
        properties = new CourseRoutingProperties();
    }

    @Test
    void shouldReuseProfileWhenHashIsUnchangedAndReturnMatchedDecision() {
        Courses os = course("os", "操作系统");
        Courses ds = course("ds", "数据结构");
        given(coursesService.list()).willReturn(List.of(os, ds));
        given(profileTextBuilder.build(os))
                .willReturn(new CourseProfileSnapshot("操作系统画像", "hash-os"));
        given(profileTextBuilder.build(ds))
                .willReturn(new CourseProfileSnapshot("数据结构画像", "hash-ds"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("os", "hash-os", "os:text_embedding_v4:hash-os")));
        given(profilesService.findActiveByCourseAndModel("ds", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("ds", "hash-ds", "ds:text_embedding_v4:hash-ds")));
        given(graphRagClient.recommend(any()))
                .willReturn(new GraphRagCourseRoutingRecommendResponse(List.of(
                        new GraphRagCourseRoutingRecommendResponse.Candidate("os", "操作系统", 0.74D, "课程画像相似度 0.740", "hash-os"),
                        new GraphRagCourseRoutingRecommendResponse.Candidate("ds", "数据结构", 0.62D, "课程画像相似度 0.620", "hash-ds")
                )));

        var response = service().recommend(request("什么是进程"), student());

        assertThat(response.getStatus()).isEqualTo("matched");
        assertThat(response.getSelectedCourseId()).isEqualTo("os");
        assertThat(response.getConfidence()).isEqualTo(0.74D);
        assertThat(response.getMargin()).isEqualTo(0.12D);
        then(graphRagClient).should(never()).upsertProfiles(anyList());
        then(decisionsService).should().save(any());
    }

    @Test
    void shouldUpsertProfileWhenHashChangesAndReturnCandidatesForLowMargin() {
        Courses os = course("os", "操作系统");
        Courses ds = course("ds", "数据结构");
        given(coursesService.list()).willReturn(List.of(os, ds));
        given(profileTextBuilder.build(os))
                .willReturn(new CourseProfileSnapshot("操作系统新版画像", "hash-os-v2"));
        given(profileTextBuilder.build(ds))
                .willReturn(new CourseProfileSnapshot("数据结构画像", "hash-ds"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("os", "hash-os", "os:text_embedding_v4:hash-os")));
        given(profilesService.findActiveByCourseAndModel("ds", "text-embedding-v4", 1024))
                .willReturn(Optional.empty());
        given(graphRagClient.upsertProfiles(anyList())).willReturn(new GraphRagCourseRoutingProfileUpsertResponse(List.of(
                new GraphRagCourseRoutingProfileUpsertResponse.Item("os", "操作系统", "hash-os-v2", "os:text_embedding_v4:hash-os-v2"),
                new GraphRagCourseRoutingProfileUpsertResponse.Item("ds", "数据结构", "hash-ds", "ds:text_embedding_v4:hash-ds")
        )));
        given(graphRagClient.recommend(any()))
                .willReturn(new GraphRagCourseRoutingRecommendResponse(List.of(
                        new GraphRagCourseRoutingRecommendResponse.Candidate("os", "操作系统", 0.70D, "课程画像相似度 0.700", "hash-os-v2"),
                        new GraphRagCourseRoutingRecommendResponse.Candidate("ds", "数据结构", 0.66D, "课程画像相似度 0.660", "hash-ds")
                )));

        var response = service().recommend(request("这个怎么理解"), student());

        assertThat(response.getStatus()).isEqualTo("needs_confirmation");
        assertThat(response.getSelectedCourseId()).isNull();
        assertThat(response.getCandidates()).hasSize(2);
        then(graphRagClient).should().upsertProfiles(anyList());
        then(profilesService).should(atLeastOnce()).saveOrUpdate(any());
    }

    @Test
    void shouldReturnNoMatchWhenEmbeddingServiceFails() {
        Courses os = course("os", "操作系统");
        given(coursesService.list()).willReturn(List.of(os));
        given(profileTextBuilder.build(os)).willReturn(new CourseProfileSnapshot("操作系统画像", "hash-os"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024)).willReturn(Optional.empty());
        doThrow(new IllegalStateException("embedding down")).when(graphRagClient).upsertProfiles(anyList());

        var response = service().recommend(request("什么是进程"), student());

        assertThat(response.getStatus()).isEqualTo("no_match");
        assertThat(response.getCandidates()).isEmpty();
        then(decisionsService).should().save(any());
    }

    @Test
    void shouldExcludeInternalSmokeCoursesFromStudentRoutingCandidates() {
        Courses smoke = course("smoke", "Smoke GraphRAG Isolation");
        Courses os = course("os", "操作系统");
        properties.setExcludedCourseIds(List.of("smoke"));
        given(coursesService.list()).willReturn(List.of(smoke, os));
        given(profileTextBuilder.build(os)).willReturn(new CourseProfileSnapshot("操作系统画像", "hash-os"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("os", "hash-os", "os:text_embedding_v4:hash-os")));
        given(graphRagClient.recommend(any()))
                .willReturn(new GraphRagCourseRoutingRecommendResponse(List.of(
                        new GraphRagCourseRoutingRecommendResponse.Candidate("os", "操作系统", 0.74D, "课程画像相似度 0.740", "hash-os")
                )));

        var response = service().recommend(request("什么是进程"), student());

        assertThat(response.getStatus()).isEqualTo("matched");
        then(profileTextBuilder).should(never()).build(smoke);
    }

    @Test
    void shouldNotExcludeCourseOnlyBecauseNameContainsSmokeWithoutExplicitRule() {
        Courses smokeNamedCourse = course("demo", "Smoke GraphRAG Isolation");
        given(coursesService.list()).willReturn(List.of(smokeNamedCourse));
        given(profileTextBuilder.build(smokeNamedCourse))
                .willReturn(new CourseProfileSnapshot("内部演示课程画像", "hash-demo"));
        given(profilesService.findActiveByCourseAndModel("demo", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("demo", "hash-demo", "demo:text_embedding_v4:hash-demo")));
        given(graphRagClient.recommend(any()))
                .willReturn(new GraphRagCourseRoutingRecommendResponse(List.of(
                        new GraphRagCourseRoutingRecommendResponse.Candidate(
                                "demo",
                                "Smoke GraphRAG Isolation",
                                0.74D,
                                "课程画像相似度 0.740",
                                "hash-demo"
                        )
                )));

        var response = service().recommend(request("如何验证构建运行隔离？"), student());

        assertThat(response.getStatus()).isEqualTo("matched");
        assertThat(response.getSelectedCourseId()).isEqualTo("demo");
        then(profileTextBuilder).should().build(smokeNamedCourse);
    }

    @Test
    void shouldEvaluateScopeRelevanceForSingleCourse() {
        Courses os = course("os", "操作系统");
        given(coursesService.getOne(any())).willReturn(os);
        given(profileTextBuilder.build(os)).willReturn(new CourseProfileSnapshot("操作系统画像", "hash-os"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("os", "hash-os", "os:text_embedding_v4:hash-os")));
        given(graphRagClient.recommend(any()))
                .willReturn(new GraphRagCourseRoutingRecommendResponse(List.of(
                        new GraphRagCourseRoutingRecommendResponse.Candidate("os", "操作系统", 0.42D, "课程画像相似度 0.420", "hash-os")
                )));

        ScopeRelevance relevance = service().evaluateScopeRelevance("os", "什么是进程");

        assertThat(relevance.evaluated()).isTrue();
        assertThat(relevance.confidence()).isEqualTo(0.42D);
    }

    @Test
    void shouldReturnNotEvaluatedWhenRecommendThrows() {
        Courses os = course("os", "操作系统");
        given(coursesService.getOne(any())).willReturn(os);
        given(profileTextBuilder.build(os)).willReturn(new CourseProfileSnapshot("操作系统画像", "hash-os"));
        given(profilesService.findActiveByCourseAndModel("os", "text-embedding-v4", 1024))
                .willReturn(Optional.of(profile("os", "hash-os", "os:text_embedding_v4:hash-os")));
        doThrow(new IllegalStateException("embedding down")).when(graphRagClient).recommend(any());

        ScopeRelevance relevance = service().evaluateScopeRelevance("os", "什么是进程");

        assertThat(relevance.evaluated()).isFalse();
    }

    @Test
    void shouldReturnNotEvaluatedWhenCourseMissing() {
        given(coursesService.getOne(any())).willReturn(null);

        ScopeRelevance relevance = service().evaluateScopeRelevance("ghost", "什么是进程");

        assertThat(relevance.evaluated()).isFalse();
        then(graphRagClient).should(never()).recommend(any());
    }

    @Test
    void shouldReturnNotEvaluatedWhenDisabled() {
        properties.setEnabled(false);

        ScopeRelevance relevance = service().evaluateScopeRelevance("os", "什么是进程");

        assertThat(relevance.evaluated()).isFalse();
        then(graphRagClient).should(never()).recommend(any());
    }

    private CourseRoutingService service() {
        return new CourseRoutingService(
                coursesService,
                profilesService,
                decisionsService,
                profileTextBuilder,
                graphRagClient,
                properties
        );
    }

    private CourseRoutingRecommendRequest request(String question) {
        CourseRoutingRecommendRequest request = new CourseRoutingRecommendRequest();
        request.setQuestion(question);
        request.setLimit(3);
        return request;
    }

    private Courses course(String courseId, String name) {
        Courses course = new Courses();
        course.setCourseId(courseId);
        course.setCourseName(name);
        course.setDescription(name + "描述");
        course.setStatus("active");
        course.setAccessPolicy("public");
        return course;
    }

    private CourseRouteProfiles profile(String courseId, String hash, String vectorId) {
        CourseRouteProfiles profile = new CourseRouteProfiles();
        profile.setCourseId(courseId);
        profile.setProfileHash(hash);
        profile.setVectorId(vectorId);
        profile.setStatus("active");
        return profile;
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
