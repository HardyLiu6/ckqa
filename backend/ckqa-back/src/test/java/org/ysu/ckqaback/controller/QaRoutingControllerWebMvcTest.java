package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.auth.AuthConstants;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationResponse;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckResponse;
import org.ysu.ckqaback.qa.routing.QaModeRoutingService;
import org.ysu.ckqaback.qa.routing.QaQuestionDomainGuardService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaRoutingControllerWebMvcTest {

    private QaModeRoutingService qaModeRoutingService;
    private QaQuestionDomainGuardService qaQuestionDomainGuardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        qaModeRoutingService = Mockito.mock(QaModeRoutingService.class);
        qaQuestionDomainGuardService = Mockito.mock(QaQuestionDomainGuardService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaRoutingController(qaModeRoutingService, qaQuestionDomainGuardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldRecommendModeForAuthenticatedStudent() throws Exception {
        given(qaModeRoutingService.recommend(any(), eq(student()))).willReturn(QaModeRecommendationResponse.of(
                "hybrid_v0",
                "local",
                0.82D,
                List.of("evidence_relation_intent", "follow_up_context"),
                "已开启 Beta，问题需要证据融合，推荐混合检索。",
                "high_confidence",
                false,
                "normal",
                true,
                true,
                "rule_semantic_v1",
                Map.of("hybrid_v0", 0.92D, "local", 0.71D)
        ));

        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/recommend")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "os",
                                  "knowledgeBaseId": 5,
                                  "sessionId": 21,
                                  "question": "它和资源分配图有什么关系？请给出材料依据",
                                  "betaHybridEnabled": true,
                                  "hasConversationContext": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedMode").value("hybrid_v0"))
                .andExpect(jsonPath("$.data.fallbackMode").value("local"))
                .andExpect(jsonPath("$.data.confidenceBand").value("high_confidence"))
                .andExpect(jsonPath("$.data.manualSwitchSuggested").value(false))
                .andExpect(jsonPath("$.data.reviewPriority").value("normal"))
                .andExpect(jsonPath("$.data.reasons[0]").value("evidence_relation_intent"))
                .andExpect(jsonPath("$.data.routeScores.hybrid_v0").value(0.92));

        then(qaModeRoutingService).should().recommend(any(), eq(student()));
    }

    @Test
    void shouldRequireAuthForRecommendation() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是死锁？"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void shouldCheckQuestionDomainForAuthenticatedStudent() throws Exception {
        given(qaQuestionDomainGuardService.check(any(), eq(student()))).willReturn(QaQuestionDomainCheckResponse.outOfScope(
                "campus_life",
                "这个问题看起来不属于课程知识问答范围。"
        ));

        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/domain-check")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "os",
                                  "knowledgeBaseId": 5,
                                  "sessionId": 21,
                                  "question": "今天晚上食堂有什么菜？",
                                  "hasConversationContext": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("out_of_scope"))
                .andExpect(jsonPath("$.data.reasonCode").value("campus_life"))
                .andExpect(jsonPath("$.data.strategy").value("rule_domain_guard_v1"));

        then(qaQuestionDomainGuardService).should().check(any(), eq(student()));
    }

    @Test
    void shouldRequireAuthForDomainCheck() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/domain-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "今天晚上吃什么"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void shouldValidateQuestionLength() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/recommend")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void shouldValidateDomainCheckQuestionLength() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_ROUTING + "/domain-check")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
