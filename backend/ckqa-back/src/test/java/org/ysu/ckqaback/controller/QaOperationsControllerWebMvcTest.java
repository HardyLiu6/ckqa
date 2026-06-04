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
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;
import org.ysu.ckqaback.qa.dto.QaOperationsSummaryResponse;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.export.QaOperationLogXlsxExporter;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new QaOperationsController(
                        qaOperationsService,
                        sourceReviewsService,
                        new QaOperationLogXlsxExporter()))
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
                        .param("reviewPriority", "low_confidence")
                        .param("keyword", "操作系统"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        then(qaOperationsService).should().pageLogs(any(), eq(authenticatedAdmin()));
    }

    @Test
    void shouldReturnQaOperationsSummaryWithFilters() throws Exception {
        given(qaOperationsService.summaryLogs(any(), eq(authenticatedAdmin())))
                .willReturn(new QaOperationsSummaryResponse(42L, 30L, 5L, 4L, 3L));

        mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs/summary")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.success").value(30))
                .andExpect(jsonPath("$.data.failed").value(5))
                .andExpect(jsonPath("$.data.lowConfidence").value(4))
                .andExpect(jsonPath("$.data.needReview").value(3));

        then(qaOperationsService).should().summaryLogs(any(), eq(authenticatedAdmin()));
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
    void shouldExportLogsAsCsvWithBomAndAttachment() throws Exception {
        given(qaOperationsService.exportFlatRows(any(), eq(authenticatedAdmin())))
                .willReturn(List.of(buildExportRow()));

        var result = mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs/export.csv")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "global"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // 期望 UTF-8 BOM 起头，让 Excel 识别中文
        assertThat(body[0] & 0xFF).isEqualTo(0xEF);
        assertThat(body[1] & 0xFF).isEqualTo(0xBB);
        assertThat(body[2] & 0xFF).isEqualTo(0xBF);
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8).substring(1);
        assertThat(text).contains("日志ID");
        assertThat(text).contains("操作系统2026春");
        assertThat(text).contains("global");
    }

    @Test
    void shouldExportLogsAsXlsxWithAttachmentHeaders() throws Exception {
        given(qaOperationsService.exportFlatRows(any(), eq(authenticatedAdmin())))
                .willReturn(List.of(buildExportRow()));

        var result = mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs/export.xlsx")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "global"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // xlsx 是 zip 包，魔数 50 4B（PK）
        assertThat(body[0] & 0xFF).isEqualTo(0x50);
        assertThat(body[1] & 0xFF).isEqualTo(0x4B);
    }

    @Test
    void shouldReturnJsonErrorWhenXlsxGenerationFailsBeforeDownloadHeadersAreCommitted() throws Exception {
        QaOperationLogXlsxExporter failingExporter = Mockito.mock(QaOperationLogXlsxExporter.class);
        given(qaOperationsService.exportFlatRows(any(), eq(authenticatedAdmin())))
                .willReturn(List.of(buildExportRow()));
        willThrow(new NoClassDefFoundError("com/alibaba/excel/EasyExcel"))
                .given(failingExporter).write(any(), any());
        MockMvc failingMvc = MockMvcBuilders.standaloneSetup(new QaOperationsController(
                        qaOperationsService,
                        sourceReviewsService,
                        failingExporter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(newValidator())
                .build();

        failingMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs/export.xlsx")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.code").value(5000));
    }

    @Test
    void shouldRejectInvalidOperationFilter() throws Exception {
        mockMvc.perform(get(ApiPaths.QA_OPERATIONS + "/logs")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedAdmin())
                        .param("mode", "auto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    private QaOperationLogExportRow buildExportRow() {
        QaOperationLogExportRow row = new QaOperationLogExportRow();
        row.setRetrievalLogId(42L);
        row.setCourseName("操作系统2026春");
        row.setKnowledgeBaseName("操作系统教材主知识库");
        row.setUserDisplay("周子涵");
        row.setQueryMode("global");
        row.setQueryStrategy("cli / 已降级");
        row.setTaskStatus("success");
        row.setRoutingConfidenceBand("high_confidence");
        row.setRoutingReviewPriority("normal");
        row.setDurationMs(196_000L);
        row.setSourceCount(23L);
        row.setHelpfulCount(2L);
        row.setUnhelpfulCount(0L);
        row.setNeedsImprovementCount(0L);
        row.setSourceIssueCount(0L);
        row.setCreatedAt("2026-05-29 09:42:00");
        return row;
    }

    private LocalValidatorFactoryBean newValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
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
