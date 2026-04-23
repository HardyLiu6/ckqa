package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.pdf.PdfWorkflowService;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.pdf.dto.PdfOperationResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PdfFilesControllerWebMvcTest {

    private PdfWorkflowService pdfWorkflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pdfWorkflowService = Mockito.mock(PdfWorkflowService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PdfFilesController(pdfWorkflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnPdfDetail() throws Exception {
        PdfFileResponse response = PdfFileResponse.of(7L, "os", "book.pdf", "done", null, null, null);
        given(pdfWorkflowService.getPdfFile(7L)).willReturn(response);

        mockMvc.perform(get(ApiPaths.PDF_FILES + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.parseStatus").value("done"));
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
}
