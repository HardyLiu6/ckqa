# 课程问答语义相关性闸门 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把课程问答的"无关问题拦截"从关键词黑名单替换为复用课程画像 embedding 的单课程语义相关性闸门，保守偏向、故障 fail-open。

**Architecture:** `QaQuestionDomainGuardService`（`/domain-check` 背后）保持位置不变，内部判定改为调用 `course.routing` 新增的 `CourseScopeRelevanceProvider.evaluateScopeRelevance(courseId, question)`：复用既有 `ensureProfiles` + 低层 `graphRagClient.recommend([courseId], 1)` 取余弦相似度，低于阈值才判 `out_of_scope`。追问、未接线、服务故障一律放行。前端契约不变。

**Tech Stack:** Java 21 / Spring Boot 3 / Lombok / MyBatis-Plus / JUnit 5 + Mockito + AssertJ / Maven（`backend/ckqa-back/mvnw`）。

---

## 前置

- 已在分支 `feature/qa-semantic-scope-gate`，设计稿已提交：`docs/superpowers/specs/2026-06-03-qa-semantic-scope-gate-design.md`。
- 所有命令的工作目录为 `backend/ckqa-back/`（除非特别说明）。
- 单测命令：`./mvnw -q -Dtest=<ClassName> test`（首次会拉依赖，可能较慢）。

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `src/main/java/org/ysu/ckqaback/config/QaDomainGuardProperties.java` | 闸门开关 + 阈值配置 | 创建 |
| `src/main/java/org/ysu/ckqaback/CkqaBackApplication.java` | 注册配置类 | 修改 |
| `src/main/java/org/ysu/ckqaback/course/routing/CourseScopeRelevanceProvider.java` | 单课程相关性窄接口 + `ScopeRelevance` 结果 | 创建 |
| `src/main/java/org/ysu/ckqaback/course/routing/CourseRoutingService.java` | 实现 `evaluateScopeRelevance`（复用 ensureProfiles + 低层 client） | 修改 |
| `src/main/java/org/ysu/ckqaback/qa/dto/QaQuestionDomainCheckResponse.java` | `STRATEGY` 改为 `semantic_relevance_v1` | 修改 |
| `src/main/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardService.java` | 删关键词、注入 provider+properties、改 classify/resolveScope、打日志 | 重写 |
| `src/test/java/org/ysu/ckqaback/config/QaDomainGuardPropertiesTest.java` | 默认值测试 | 创建 |
| `src/test/java/org/ysu/ckqaback/course/routing/CourseRoutingServiceTest.java` | 新增 `evaluateScopeRelevance` 测试 | 修改 |
| `src/test/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardServiceTest.java` | 重写为语义闸门测试（保留鉴权/scope 用例） | 重写 |
| `src/test/java/org/ysu/ckqaback/controller/QaRoutingControllerWebMvcTest.java` | 更新 domain-check 断言的 reasonCode/strategy 字面量 | 修改 |

前端：不改。

---

## Task 1：闸门配置类 `QaDomainGuardProperties`

**Files:**
- Create: `src/main/java/org/ysu/ckqaback/config/QaDomainGuardProperties.java`
- Modify: `src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`
- Test: `src/test/java/org/ysu/ckqaback/config/QaDomainGuardPropertiesTest.java`

- [ ] **Step 1: 写失败测试（默认值）**

创建 `src/test/java/org/ysu/ckqaback/config/QaDomainGuardPropertiesTest.java`：

```java
package org.ysu.ckqaback.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaDomainGuardPropertiesTest {

    @Test
    void shouldDefaultToEnabledWithConservativeThreshold() {
        QaDomainGuardProperties properties = new QaDomainGuardProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getOutOfScopeThreshold()).isEqualTo(0.20D);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=QaDomainGuardPropertiesTest test`
Expected: 编译失败（`QaDomainGuardProperties` 不存在）。

- [ ] **Step 3: 创建配置类**

创建 `src/main/java/org/ysu/ckqaback/config/QaDomainGuardProperties.java`：

```java
package org.ysu.ckqaback.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 课程问答语义相关性闸门配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.qa-domain-guard")
public class QaDomainGuardProperties {

    /** 闸门总开关；关闭后所有问题放行。 */
    private boolean enabled = true;

    /** 余弦相似度低于该阈值才判为无关；中文 embedding 基线偏高，0.20 为保守起步值，待校准。 */
    private double outOfScopeThreshold = 0.20D;
}
```

- [ ] **Step 4: 注册配置类**

修改 `src/main/java/org/ysu/ckqaback/CkqaBackApplication.java`：在 import 区加入

```java
import org.ysu.ckqaback.config.QaDomainGuardProperties;
```

并把 `@EnableConfigurationProperties({...})` 列表中最后一项 `CourseRoutingProperties.class` 改为：

```java
        CourseRoutingProperties.class,
        QaDomainGuardProperties.class
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=QaDomainGuardPropertiesTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/org/ysu/ckqaback/config/QaDomainGuardProperties.java \
        src/main/java/org/ysu/ckqaback/CkqaBackApplication.java \
        src/test/java/org/ysu/ckqaback/config/QaDomainGuardPropertiesTest.java
git commit -m "feat(qa): 新增课程问答语义闸门配置 QaDomainGuardProperties"
```

---

## Task 2：单课程相关性接口 + `CourseRoutingService.evaluateScopeRelevance`

**Files:**
- Create: `src/main/java/org/ysu/ckqaback/course/routing/CourseScopeRelevanceProvider.java`
- Modify: `src/main/java/org/ysu/ckqaback/course/routing/CourseRoutingService.java`
- Test: `src/test/java/org/ysu/ckqaback/course/routing/CourseRoutingServiceTest.java`

- [ ] **Step 1: 创建接口与结果类型**

创建 `src/main/java/org/ysu/ckqaback/course/routing/CourseScopeRelevanceProvider.java`：

```java
package org.ysu.ckqaback.course.routing;

/**
 * 单课程语义相关性判定端口。
 * <p>{@code evaluated=false} 表示未能算出有效相似度（未启用 / 课程缺失 / 服务故障），调用方据此 fail-open。</p>
 */
public interface CourseScopeRelevanceProvider {

    ScopeRelevance evaluateScopeRelevance(String courseId, String question);

    record ScopeRelevance(boolean evaluated, double confidence) {

        public static ScopeRelevance notEvaluated() {
            return new ScopeRelevance(false, 0d);
        }

        public static ScopeRelevance evaluated(double confidence) {
            return new ScopeRelevance(true, confidence);
        }
    }
}
```

> 说明：`ScopeRelevance` 作为接口内嵌 record，使用时为 `CourseScopeRelevanceProvider.ScopeRelevance`。

- [ ] **Step 2: 写失败测试（在已有 `CourseRoutingServiceTest` 中追加 4 个用例）**

在 `src/test/java/org/ysu/ckqaback/course/routing/CourseRoutingServiceTest.java` 的 import 区追加：

```java
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
```

> 注：`doThrow`、`any`、`anyList`、`atLeastOnce`、`never` 该文件已 import；若 `anyString` 已存在请勿重复添加。

在类内（`shouldNotExcludeCourseOnlyBecause...` 测试之后、`private CourseRoutingService service()` 之前）追加：

```java
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
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./mvnw -q -Dtest=CourseRoutingServiceTest test`
Expected: 编译失败（`CourseRoutingService` 无 `evaluateScopeRelevance` 方法）。

- [ ] **Step 4: 实现 `evaluateScopeRelevance`**

修改 `src/main/java/org/ysu/ckqaback/course/routing/CourseRoutingService.java`：

import 区追加（与 `CourseProfileTextBuilder` 同款）：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
```

类声明改为实现接口：

```java
public class CourseRoutingService implements CourseScopeRelevanceProvider {
```

在 `recommend(...)` 方法之后、`ensureProfiles(...)` 之前插入：

```java
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
```

> 要点：故障/缺失一律 `notEvaluated`（fail-open）；不写 `course_route_decisions`；`getFirst()` 与本类 `decide` 中用法一致（Java 21 `List.getFirst()`，已在本类 `CourseRoutingDecisionPolicy` 使用）。

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw -q -Dtest=CourseRoutingServiceTest test`
Expected: PASS（含原有 5 个用例 + 新增 4 个）。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/org/ysu/ckqaback/course/routing/CourseScopeRelevanceProvider.java \
        src/main/java/org/ysu/ckqaback/course/routing/CourseRoutingService.java \
        src/test/java/org/ysu/ckqaback/course/routing/CourseRoutingServiceTest.java
git commit -m "feat(course-routing): 新增单课程语义相关性判定 evaluateScopeRelevance"
```

---

## Task 3：`QaQuestionDomainGuardService` 改为语义闸门

**Files:**
- Modify: `src/main/java/org/ysu/ckqaback/qa/dto/QaQuestionDomainCheckResponse.java:13`
- Rewrite: `src/main/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardService.java`
- Rewrite: `src/test/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardServiceTest.java`
- Modify: `src/test/java/org/ysu/ckqaback/controller/QaRoutingControllerWebMvcTest.java:105-125`

- [ ] **Step 1: 重写守卫单测（先写测试）**

用以下完整内容覆盖 `src/test/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardServiceTest.java`：

```java
package org.ysu.ckqaback.qa.routing;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.config.QaDomainGuardProperties;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaQuestionDomainGuardServiceTest {

    @Test
    void shouldBlockWhenCourseRelevanceBelowThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.08D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("out_of_scope");
        assertThat(response.getReasonCode()).isEqualTo("low_course_relevance");
        assertThat(response.getStrategy()).isEqualTo("semantic_relevance_v1");
    }

    @Test
    void shouldAllowWhenCourseRelevanceAtOrAboveThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.42D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("请解释银行家算法的安全性检查");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        assertThat(response.getStrategy()).isEqualTo("semantic_relevance_v1");
    }

    @Test
    void shouldAllowFollowUpWithoutEvaluating() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("它和资源分配图有什么关系？");
        request.setCourseId("os");
        request.setHasConversationContext(true);
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(provider).should(never()).evaluateScopeRelevance(any(), any());
    }

    @Test
    void shouldAllowWhenRelevanceNotEvaluated() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(any(), any())).willReturn(ScopeRelevance.notEvaluated());
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
    }

    @Test
    void shouldAllowWhenRelevanceProviderNotWired() {
        QaQuestionDomainGuardService service = serviceWithoutProvider();

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
    }

    @Test
    void shouldValidateSessionOwnerAndSessionScope() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);

        QaSessions session = session(21L, 7L, "os", 5L);
        given(sessionsService.getRequiredById(21L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "os"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);
        request.setSessionId(21L);

        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldRejectSessionThatBelongsToAnotherUser() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, mock(KnowledgeBasesService.class));
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 8L, "os", 5L));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能访问自己的问答会话");
    }

    @Test
    void shouldRejectKnowledgeBaseOutsideRequestedCourse() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(mock(QaSessionsService.class), knowledgeBasesService);
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "db"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不属于当前课程");
    }

    @Test
    void shouldValidateRequestKnowledgeBaseWhenSessionHasNoKnowledgeBase() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 7L, "os", null));
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "os"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(knowledgeBasesService).should().getRequiredById(5L);
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldRejectRequestKnowledgeBaseOutsideSessionCourseWhenSessionHasNoKnowledgeBase() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 7L, "os", null));
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "db"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不属于当前课程");
    }

    @Test
    void shouldRequireAuthenticatedUser() {
        assertThatThrownBy(() -> serviceWithoutProvider().check(request("什么是进程？"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    private QaQuestionDomainGuardService serviceWithProvider(CourseScopeRelevanceProvider provider, double threshold) {
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(
                mock(QaSessionsService.class), mock(KnowledgeBasesService.class));
        service.setRelevanceProvider(provider);
        QaDomainGuardProperties properties = new QaDomainGuardProperties();
        properties.setOutOfScopeThreshold(threshold);
        service.setProperties(properties);
        return service;
    }

    private QaQuestionDomainGuardService serviceWithoutProvider() {
        return new QaQuestionDomainGuardService(mock(QaSessionsService.class), mock(KnowledgeBasesService.class));
    }

    private QaQuestionDomainCheckRequest request(String question) {
        QaQuestionDomainCheckRequest request = new QaQuestionDomainCheckRequest();
        request.setQuestion(question);
        return request;
    }

    private QaSessions session(Long id, Long userId, String courseId, Long knowledgeBaseId) {
        QaSessions session = new QaSessions();
        session.setId(id);
        session.setUserId(userId);
        session.setCourseId(courseId);
        session.setKnowledgeBaseId(knowledgeBaseId);
        return session;
    }

    private KnowledgeBases knowledgeBase(Long id, String courseId) {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(id);
        knowledgeBase.setCourseId(courseId);
        return knowledgeBase;
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=QaQuestionDomainGuardServiceTest test`
Expected: 编译失败（`setRelevanceProvider` / `setProperties` 不存在、`strategy` 仍为旧值）。

- [ ] **Step 3: 更新响应策略常量**

修改 `src/main/java/org/ysu/ckqaback/qa/dto/QaQuestionDomainCheckResponse.java:13`：

```java
    public static final String STRATEGY = "semantic_relevance_v1";
```

> `allowed()` 的 `reasonCode` 维持 `course_or_uncertain` 不变；前端只读 `status`/`message`，不受影响。

- [ ] **Step 4: 重写守卫服务**

用以下完整内容覆盖 `src/main/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardService.java`：

```java
package org.ysu.ckqaback.qa.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.config.QaDomainGuardProperties;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.Objects;

/**
 * 问题领域硬拦截。
 * <p>
 * 复用课程画像语义相关性判断问题是否属于当前课程资料问答范围；
 * 追问、未接线、服务故障一律放行，避免误伤真问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaQuestionDomainGuardService {

    private static final String OUT_OF_SCOPE_MESSAGE =
            "这个问题好像不在当前课程的资料范围内，换成课程里的概念、章节或知识点再试试吧。";

    private final QaSessionsService qaSessionsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private CourseAccessService courseAccessService;
    private CourseScopeRelevanceProvider relevanceProvider;
    private QaDomainGuardProperties properties = new QaDomainGuardProperties();

    @Autowired(required = false)
    public void setCourseAccessService(CourseAccessService courseAccessService) {
        this.courseAccessService = courseAccessService;
    }

    @Autowired(required = false)
    public void setRelevanceProvider(CourseScopeRelevanceProvider relevanceProvider) {
        this.relevanceProvider = relevanceProvider;
    }

    @Autowired(required = false)
    public void setProperties(QaDomainGuardProperties properties) {
        if (properties != null) {
            this.properties = properties;
        }
    }

    public QaQuestionDomainCheckResponse check(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        String courseId = resolveScopeCourseId(request, currentUser);
        return classify(courseId, request.getQuestion(), Boolean.TRUE.equals(request.getHasConversationContext()));
    }

    private QaQuestionDomainCheckResponse classify(String courseId, String question, boolean hasContext) {
        if (hasContext) {
            return QaQuestionDomainCheckResponse.allowed();
        }
        if (relevanceProvider == null) {
            return QaQuestionDomainCheckResponse.allowed();
        }
        ScopeRelevance relevance = relevanceProvider.evaluateScopeRelevance(courseId, question);
        boolean outOfScope = relevance.evaluated()
                && relevance.confidence() < properties.getOutOfScopeThreshold();
        log.info("qa_domain_guard courseId={} questionHash={} evaluated={} confidence={} threshold={} decision={}",
                courseId,
                Integer.toHexString(Objects.hashCode(question)),
                relevance.evaluated(),
                relevance.confidence(),
                properties.getOutOfScopeThreshold(),
                outOfScope ? "out_of_scope" : "allowed");
        if (outOfScope) {
            return QaQuestionDomainCheckResponse.outOfScope("low_course_relevance", OUT_OF_SCOPE_MESSAGE);
        }
        return QaQuestionDomainCheckResponse.allowed();
    }

    private String resolveScopeCourseId(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (request.getSessionId() != null) {
            QaSessions session = qaSessionsService.getRequiredById(request.getSessionId());
            if (!currentUser.id().equals(session.getUserId())) {
                throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能访问自己的问答会话");
            }
            validateSessionRequestScope(request, session);
            String courseId = effectiveSessionCourseId(request, session);
            if (session.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
                courseId = firstNonBlank(courseId, knowledgeBase.getCourseId());
            } else if (request.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
                courseId = firstNonBlank(courseId, knowledgeBase.getCourseId());
            } else if (StringUtils.hasText(courseId)) {
                validateCourseReadable(courseId, currentUser);
            }
            return courseId;
        }

        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
            validateKnowledgeBaseScope(request.getCourseId(), knowledgeBase, currentUser);
            return firstNonBlank(request.getCourseId(), knowledgeBase.getCourseId());
        }

        if (StringUtils.hasText(request.getCourseId())) {
            validateCourseReadable(request.getCourseId(), currentUser);
            return request.getCourseId();
        }
        return null;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return StringUtils.hasText(secondary) ? secondary : null;
    }

    private void validateSessionRequestScope(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(request.getCourseId())
                && StringUtils.hasText(session.getCourseId())
                && !Objects.equals(request.getCourseId(), session.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前课程");
        }
        if (request.getKnowledgeBaseId() != null
                && session.getKnowledgeBaseId() != null
                && !Objects.equals(request.getKnowledgeBaseId(), session.getKnowledgeBaseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前知识库");
        }
    }

    private String effectiveSessionCourseId(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(session.getCourseId())) {
            return session.getCourseId();
        }
        return request.getCourseId();
    }

    private void validateKnowledgeBaseScope(String courseId, KnowledgeBases knowledgeBase, AuthenticatedUser currentUser) {
        if (StringUtils.hasText(courseId) && !Objects.equals(courseId, knowledgeBase.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "知识库不属于当前课程");
        }
        validateCourseReadable(knowledgeBase.getCourseId(), currentUser);
    }

    private void validateCourseReadable(String courseId, AuthenticatedUser currentUser) {
        if (courseAccessService != null && StringUtils.hasText(courseId)) {
            courseAccessService.assertCourseReadable(courseId, currentUser.userCode());
        }
    }
}
```

- [ ] **Step 5: 运行守卫单测确认通过**

Run: `./mvnw -q -Dtest=QaQuestionDomainGuardServiceTest test`
Expected: PASS（10 个用例）。

- [ ] **Step 6: 更新控制器 WebMvc 测试字面量**

修改 `src/test/java/org/ysu/ckqaback/controller/QaRoutingControllerWebMvcTest.java` 的 `shouldCheckQuestionDomainForAuthenticatedStudent`：

把 `willReturn(QaQuestionDomainCheckResponse.outOfScope("campus_life", ...))` 中的 reasonCode 改为 `low_course_relevance`，并把两处断言改为：

```java
                .andExpect(jsonPath("$.data.reasonCode").value("low_course_relevance"))
                .andExpect(jsonPath("$.data.strategy").value("semantic_relevance_v1"));
```

即该方法对应片段最终为：

```java
        given(qaQuestionDomainGuardService.check(any(), eq(student()))).willReturn(QaQuestionDomainCheckResponse.outOfScope(
                "low_course_relevance",
                "这个问题好像不在当前课程的资料范围内，换成课程里的概念、章节或知识点再试试吧。"
        ));
```
```java
                .andExpect(jsonPath("$.data.status").value("out_of_scope"))
                .andExpect(jsonPath("$.data.reasonCode").value("low_course_relevance"))
                .andExpect(jsonPath("$.data.strategy").value("semantic_relevance_v1"));
```

- [ ] **Step 7: 运行控制器测试确认通过**

Run: `./mvnw -q -Dtest=QaRoutingControllerWebMvcTest test`
Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/org/ysu/ckqaback/qa/dto/QaQuestionDomainCheckResponse.java \
        src/main/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardService.java \
        src/test/java/org/ysu/ckqaback/qa/routing/QaQuestionDomainGuardServiceTest.java \
        src/test/java/org/ysu/ckqaback/controller/QaRoutingControllerWebMvcTest.java
git commit -m "feat(qa): 课程问答无关拦截改为单课程语义相关性闸门"
```

---

## Task 4：全量编译测试与回归确认

**Files:** 无新增改动，仅验证。

- [ ] **Step 1: 编译 + 全量测试**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS，全部测试通过（重点关注 `course.routing`、`qa.routing`、`controller` 包）。

- [ ] **Step 2: 确认没有遗留关键词引用**

Run（仓库根目录）：`grep -rn "campus_life\|creative_writing\|profile_help\|course_administrivia\|rule_domain_guard_v1" backend/ckqa-back/src`
Expected: 无输出（全部已移除/替换）。

- [ ] **Step 3: 提交（若 Step 1/2 触发任何小修）**

```bash
git add -A && git commit -m "test(qa): 语义闸门改造后全量回归通过"
```

> 若 Step 1/2 无改动则跳过本步。

---

## 手动冒烟（实现完成后，可选，需运行环境）

1. 启动后端 + GraphRAG Python（课程画像表已就绪）。
2. 已绑定某课程的会话里提问 `今天晚上吃什哦`（含错别字）→ 期望被拦截并提示"不在课程资料范围内"。
3. 提问 `请根据第 3 章解释银行家算法的安全性检查过程` → 期望放行进入检索模式。
4. 追问场景（带上下文）提任意短问 → 期望放行（不校验）。
5. 临时关闭 GraphRAG Python（制造故障）再提无关问题 → 期望 fail-open 放行（日志可见 `evaluated=false`）。

---

## 自检（写计划后回看 spec）

- **覆盖**：spec §4.1/4.2（接口+实现）→ Task 2；§4.3（守卫改造）+ §6（分支）→ Task 3；§5（配置+阈值+日志）→ Task 1 + Task 3 Step 4 的 `log.info`；§7（文件清单）→ 四个 Task 全覆盖；§8（测试）→ Task 2/3 的 TDD 用例 + Task 4 回归。前端不改 → 无任务，符合 spec。
- **占位**：无 TBD/TODO；所有创建/重写文件给出完整内容，修改给出确切位置与片段。
- **类型一致**：`CourseScopeRelevanceProvider.ScopeRelevance` 与其 `evaluated()/confidence()/notEvaluated()/evaluated(double)` 在 Task 2 定义、Task 3 与测试一致引用；`evaluateScopeRelevance(String, String)` 签名前后一致；`QaDomainGuardProperties.isEnabled()/getOutOfScopeThreshold()/setOutOfScopeThreshold()` 一致；`QaQuestionDomainCheckResponse.outOfScope(reasonCode, message)`/`allowed()`/`STRATEGY` 一致。
