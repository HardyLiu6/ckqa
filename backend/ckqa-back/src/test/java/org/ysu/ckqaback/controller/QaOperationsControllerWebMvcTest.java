package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.auth.AuthConstants;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.entity.QaSourceReviews;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.qa.QaOperationsService;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaOperationsControllerWebMvcTest {

    private QaOperationsService qaOperationsService;
    private QaSourceReviewsService sourceReviewsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        qaOperationsService = Mockito.mock(QaOperationsService.class);
        sourceReviewsService = Mockito.mock(QaSourceReviewsService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaOperationsController(qaOperationsService, sourceReviewsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldListQaOperationLogsWithFilters() throws Exception {
        given(qaOperationsService.pageLogs(any(), eq(authenticatedAdmin())))
                .willReturn(new ApiPageData<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "hybrid_v0")
                        .param("feedbackTag", "source_irrelevant")
                        .param("routingConfidenceBand", "low_confidence")
                        .param("reviewPriority", "low_confidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        then(qaOperationsService).should().pageLogs(any(), eq(authenticatedAdmin()));
    }

    @Test
    void shouldUpsertSourceReviewForOpsUser() throws Exception {
        given(sourceReviewsService.upsertReview(eq(7L), any(), eq(authenticatedAdmin())))
                .willReturn(reviewResponse());

        mockMvc.perform(put(ApiPaths.QA_OPERATIONS + "/source-reviews/7")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "relevance": "partially_relevant",
                                  "citationQuality": "weak_support",
                                  "note": "来源只覆盖了部分结论"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrievalHitId").value(7))
                .andExpect(jsonPath("$.data.relevance").value("partially_relevant"))
                .andExpect(jsonPath("$.data.citationQuality").value("weak_support"));
    }

    @Test
    void shouldRejectInvalidOperationFilter() throws Exception {
        mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "auto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    private QaSourceReviewResponse reviewResponse() {
        QaSourceReviews review = new QaSourceReviews();
        review.setId(3L);
        review.setRetrievalHitId(7L);
        review.setRetrievalLogId(9L);
        review.setReviewerUserId(1L);
        review.setRelevance("partially_relevant");
        review.setCitationQuality("weak_support");
        review.setNote("来源只覆盖了部分结论");
        review.setCreatedAt(LocalDateTime.of(2026, 5, 18, 10, 0));
        review.setUpdatedAt(LocalDateTime.of(2026, 5, 18, 10, 0));
        return QaSourceReviewResponse.fromEntity(review);
    }

    private AuthenticatedUser authenticatedAdmin() {
        return new AuthenticatedUser(1L, "admin", "admin", "管理员", List.of("admin"), List.of("*"));
    }
}
