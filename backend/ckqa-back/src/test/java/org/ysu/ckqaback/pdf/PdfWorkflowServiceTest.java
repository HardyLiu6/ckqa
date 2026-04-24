package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.locks.DatabaseNamedLockService;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class PdfWorkflowServiceTest {

    @Test
    void shouldRejectParseWhenClaimParseStartFails() {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService);
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");
        material.setParseStatus("processing");

        given(courseMaterialsService.getRequiredById(7L)).willReturn(material);
        given(courseMaterialsService.claimParseStart(7L)).willReturn(false);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PDF当前状态不允许再次触发解析");
    }

    @Test
    void shouldFallbackToFailedWhenParseProcessReturnsError() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService);
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");
        material.setParseStatus("pending");

        given(courseMaterialsService.getRequiredById(7L)).willReturn(material, material);
        given(courseMaterialsService.claimParseStart(7L)).willReturn(true);
        given(orchestrator.parse(material)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(1)
                .stdout("")
                .stderr("spawn failed")
                .elapsedSeconds(1L)
                .timedOut(false)
                .terminatedByShutdown(false)
                .build());

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF解析执行失败");

        then(courseMaterialsService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    @Test
    void shouldFallbackToFailedWhenParseCommandThrowsException() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService);
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");

        given(courseMaterialsService.getRequiredById(7L)).willReturn(material);
        given(courseMaterialsService.claimParseStart(7L)).willReturn(true);
        willThrow(new IOException("spawn failed")).given(orchestrator).parse(material);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spawn failed");

        then(courseMaterialsService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    @Test
    void shouldRejectExportWhenNamedLockNotAcquired() {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService);

        CourseMaterials material = new CourseMaterials();
        material.setId(9L);
        material.setCourseId("os");
        material.setDisplayName("slides.pdf");
        material.setParseStatus("done");

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(false);

        ExportGraphRagRequest request = new ExportGraphRagRequest();
        request.setMode("section");
        request.setWithPageDocs(false);
        request.setForce(false);

        assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前已有导出任务在执行");
    }
}
