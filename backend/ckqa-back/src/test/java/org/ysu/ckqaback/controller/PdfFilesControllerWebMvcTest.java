package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.ParseResults;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.pdf.ParseStreamTokenResponse;
import org.ysu.ckqaback.pdf.PdfParseEventStreamService;
import org.ysu.ckqaback.pdf.PdfParseStreamTokenService;
import org.ysu.ckqaback.pdf.PdfWorkflowService;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.pdf.dto.ParseResultContent;
import org.ysu.ckqaback.pdf.dto.ParseResultResponse;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.pdf.dto.PdfOperationResponse;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PdfFilesControllerWebMvcTest {

    private PdfWorkflowService pdfWorkflowService;
    private PdfParseStreamTokenService parseStreamTokenService;
    private PdfParseEventStreamService parseEventStreamService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pdfWorkflowService = Mockito.mock(PdfWorkflowService.class);
        parseStreamTokenService = Mockito.mock(PdfParseStreamTokenService.class);
        parseEventStreamService = Mockito.mock(PdfParseEventStreamService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PdfFilesController(
                        pdfWorkflowService,
                        parseStreamTokenService,
                        parseEventStreamService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnPdfDetail() throws Exception {
        PdfFileResponse response = PdfFileResponse.of(
                7L,
                7L,
                17L,
                "os",
                "book.pdf",
                "processing",
                LocalDateTime.parse("2026-05-07T10:00:00"),
                null,
                null,
                "mineru-batch-7"
        );
        given(pdfWorkflowService.getPdfFile(7L)).willReturn(response);

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.materialId").value(7))
                .andExpect(jsonPath("$.data.materialObjectId").value(17))
                .andExpect(jsonPath("$.data.fileName").value("book.pdf"))
                .andExpect(jsonPath("$.data.parseStatus").value("processing"))
                .andExpect(jsonPath("$.data.parseStage").value("mineru_processing"))
                .andExpect(jsonPath("$.data.parseProgress.stage").value("mineru_processing"))
                .andExpect(jsonPath("$.data.parseProgress.percent").value(35))
                .andExpect(jsonPath("$.data.parseProgress.estimated").value(true));
    }

    @Test
    void shouldReturnRealMineruPageProgressWhenStored() throws Exception {
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setMaterialObjectId(17L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");
        material.setParseStatus("processing");
        material.setMineruBatchId("mineru-batch-7");
        material.setParseStartedAt(LocalDateTime.parse("2026-05-07T10:00:00"));
        material.setParseProgressExtractedPages(3);
        material.setParseProgressTotalPages(5);
        material.setParseProgressPercent(60);
        material.setParseProgressStartedAt(LocalDateTime.parse("2026-05-07T10:20:00"));
        material.setParseProgressUpdatedAt(LocalDateTime.parse("2026-05-07T10:21:00"));
        given(pdfWorkflowService.getPdfFile(7L)).willReturn(PdfFileResponse.fromEntity(material));

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStage").value("mineru_page_extracting"))
                .andExpect(jsonPath("$.data.parseProgressPercent").value(60))
                .andExpect(jsonPath("$.data.parseProgress.stage").value("mineru_page_extracting"))
                .andExpect(jsonPath("$.data.parseProgress.percent").value(60))
                .andExpect(jsonPath("$.data.parseProgress.estimated").value(false))
                .andExpect(jsonPath("$.data.parseProgress.extractedPages").value(3))
                .andExpect(jsonPath("$.data.parseProgress.totalPages").value(5))
                .andExpect(jsonPath("$.data.parseProgress.startedAt").value("2026-05-07T10:20:00"))
                .andExpect(jsonPath("$.data.parseProgress.updatedAt").value("2026-05-07T10:21:00"));
    }

    @Test
    void shouldReturnParseResultsWithPreviewAndDownloadUrls() throws Exception {
        ParseResults result = parseResult(31L, 7L, "content_list.json");
        given(pdfWorkflowService.listParseResults(7L)).willReturn(List.of(ParseResultResponse.fromEntity(result)));

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(31))
                .andExpect(jsonPath("$.data[0].previewUrl").value("/api/v1/pdf-files/7/results/31/preview"))
                .andExpect(jsonPath("$.data[0].downloadUrl").value("/api/v1/pdf-files/7/results/31/download"))
                .andExpect(jsonPath("$.data[0].previewable").value(true));
    }

    @Test
    void shouldStreamParseResultPreviewAndDownload() throws Exception {
        given(pdfWorkflowService.loadParseResultContent(7L, 31L)).willReturn(ParseResultContent.builder()
                .fileName("content_list.json")
                .contentType("application/json")
                .fileSize(15L)
                .bytes("{\"ok\":true}".getBytes())
                .build());

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7/results/31/preview"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "inline; filename=\"content_list.json\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/json"));

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7/results/31/download"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"content_list.json\""));
    }

    @Test
    void shouldTriggerParse() throws Exception {
        PdfOperationResponse response = PdfOperationResponse.success(7L, "os", "book.pdf", "processing", "解析任务已启动");
        given(pdfWorkflowService.startParse(7L)).willReturn(response);

        mockMvc.perform(post(ApiPaths.PDF_FILES + "/7/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("解析任务已启动"));
    }

    @Test
    void shouldIssueParseEventStreamToken() throws Exception {
        given(parseStreamTokenService.issue(7L)).willReturn(new ParseStreamTokenResponse(
                "stream-token",
                Instant.parse("2026-05-07T08:05:00Z")
        ));

        mockMvc.perform(post(ApiPaths.PDF_FILES + "/7/parse-events/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("stream-token"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-05-07T08:05:00Z"));
    }

    @Test
    void shouldOpenParseEventStream() throws Exception {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        given(parseEventStreamService.openStream(7L, "stream-token")).willReturn(emitter);

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7/parse-events")
                        .param("token", "stream-token"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void shouldTriggerExport() throws Exception {
        PdfOperationResponse response = PdfOperationResponse.success(9L, "os", "slides.pdf", "done", "GraphRAG导出完成");
        given(pdfWorkflowService.exportGraphRag(org.mockito.ArgumentMatchers.eq(9L), any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "section",
                                  "withPageDocs": true,
                                  "force": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("GraphRAG导出完成"));
    }

    @Test
    void shouldDefaultModeToSectionWhenModeMissing() throws Exception {
        PdfOperationResponse response = PdfOperationResponse.success(9L, "os", "slides.pdf", "done", "已存在完整导出结果");
        given(pdfWorkflowService.exportGraphRag(org.mockito.ArgumentMatchers.eq(9L), any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "withPageDocs": false,
                                  "force": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("已存在完整导出结果"));

        then(pdfWorkflowService).should().exportGraphRag(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.argThat(req -> "section".equals(req.getMode())));
    }

    @Test
    void shouldRejectNullModeWhenExportRequestContainsNullMode() throws Exception {
        mockMvc.perform(post(ApiPaths.PDF_FILES + "/9/export-graphrag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": null,
                                  "withPageDocs": false,
                                  "force": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private ParseResults parseResult(Long id, Long courseMaterialId, String fileName) {
        ParseResults result = new ParseResults();
        result.setId(id);
        result.setCourseMaterialId(courseMaterialId);
        result.setCourseId("os");
        result.setResultType("mineru");
        result.setFileName(fileName);
        result.setMinioBucket("course-artifacts");
        result.setMinioObjectKey("parse-results/os/7/" + fileName);
        result.setFileSize(128L);
        result.setCreatedAt(LocalDateTime.parse("2026-05-07T10:15:00"));
        return result;
    }
}
