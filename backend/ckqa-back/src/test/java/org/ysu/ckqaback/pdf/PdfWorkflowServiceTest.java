package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PdfFiles;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.locks.DatabaseNamedLockService;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.service.ParseResultsService;
import org.ysu.ckqaback.service.PdfFilesService;

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
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator, databaseNamedLockService);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");
        pdfFile.setParseStatus("processing");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(false);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PDF当前状态不允许再次触发解析");
    }

    @Test
    void shouldFallbackToFailedWhenParseProcessReturnsError() throws Exception {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator, databaseNamedLockService);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");
        pdfFile.setParseStatus("pending");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile, pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(true);
        given(orchestrator.parse(pdfFile)).willReturn(ProcessExecutionResult.builder()
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

        then(pdfFilesService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    @Test
    void shouldFallbackToFailedWhenParseCommandThrowsException() throws Exception {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator, databaseNamedLockService);
        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(7L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("book.pdf");

        given(pdfFilesService.getRequiredById(7L)).willReturn(pdfFile);
        given(pdfFilesService.claimParseStart(7L)).willReturn(true);
        willThrow(new IOException("spawn failed")).given(orchestrator).parse(pdfFile);

        assertThatThrownBy(() -> workflowService.startParse(7L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spawn failed");

        then(pdfFilesService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    @Test
    void shouldRejectExportWhenNamedLockNotAcquired() {
        PdfFilesService pdfFilesService = mock(PdfFilesService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(pdfFilesService, parseResultsService, orchestrator, databaseNamedLockService);

        PdfFiles pdfFile = new PdfFiles();
        pdfFile.setId(9L);
        pdfFile.setCourseId("os");
        pdfFile.setFileName("slides.pdf");
        pdfFile.setParseStatus("done");

        given(pdfFilesService.getRequiredById(9L)).willReturn(pdfFile);
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
