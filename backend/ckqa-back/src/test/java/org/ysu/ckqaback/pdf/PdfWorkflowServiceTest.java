package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.CourseCoverObjectStorage;
import org.ysu.ckqaback.course.StoredCourseCoverObject;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.ParseResults;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PdfWorkflowServiceTest {

    @Test
    void shouldRejectParseWhenClaimParseStartFails() {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
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

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
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

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
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

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));

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

    @Test
    void shouldReturnExistingGraphRagExportWhenCompleteAndForceFalse() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = new CourseMaterials();
        material.setId(9L);
        material.setCourseId("os");
        material.setDisplayName("slides.pdf");
        material.setParseStatus("done");

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);
        given(parseResultsService.hasCompleteGraphRagExport(9L, "section", true)).willReturn(true);

        ExportGraphRagRequest request = new ExportGraphRagRequest();
        request.setMode("section");
        request.setWithPageDocs(true);
        request.setForce(false);

        var response = workflowService.exportGraphRag(9L, request);

        assertThat(response.getMessage()).isEqualTo("已存在完整导出结果");
        then(orchestrator).should(never()).exportGraphRag(material, request);
        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldRunExportWhenForceTrueEvenIfCompleteExportExists() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = new CourseMaterials();
        material.setId(9L);
        material.setCourseId("os");
        material.setDisplayName("slides.pdf");
        material.setParseStatus("done");

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);

        ExportGraphRagRequest request = new ExportGraphRagRequest();
        request.setMode("section");
        request.setWithPageDocs(true);
        request.setForce(true);
        given(orchestrator.exportGraphRag(material, request)).willReturn(successResult());

        var response = workflowService.exportGraphRag(9L, request);

        assertThat(response.getMessage()).isEqualTo("GraphRAG导出完成");
        then(parseResultsService).should(never()).hasCompleteGraphRagExport(9L, "section", true);
        then(orchestrator).should().exportGraphRag(material, request);
        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldRejectExportWhenProcessReturnsNonZeroAndReleaseLock() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = doneMaterial();
        ExportGraphRagRequest request = exportRequest(true);

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);
        given(orchestrator.exportGraphRag(material, request)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(1)
                .stderr("export failed")
                .timedOut(false)
                .build());

        assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GraphRAG导出失败")
                .extracting("code")
                .isEqualTo(ApiResultCode.PDF_PARSE_EXECUTION_FAILED.getCode());

        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldRejectExportWhenProcessTimesOutAndReleaseLock() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = doneMaterial();
        ExportGraphRagRequest request = exportRequest(true);

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);
        given(orchestrator.exportGraphRag(material, request)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(0)
                .stderr("")
                .timedOut(true)
                .build());

        assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GraphRAG导出失败");

        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldReleaseExportLockWhenExportCommandThrowsIOException() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = doneMaterial();
        ExportGraphRagRequest request = exportRequest(true);

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);
        willThrow(new IOException("spawn failed")).given(orchestrator).exportGraphRag(material, request);

        assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spawn failed");

        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldReleaseExportLockWhenExportCommandThrowsInterruptedException() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, mock(CourseCoverObjectStorage.class));
        CourseMaterials material = doneMaterial();
        ExportGraphRagRequest request = exportRequest(true);

        given(courseMaterialsService.getRequiredById(9L)).willReturn(material);
        given(databaseNamedLockService.acquire("pdf-export:9", 1)).willReturn(true);
        willThrow(new InterruptedException("interrupted")).given(orchestrator).exportGraphRag(material, request);

        assertThatThrownBy(() -> workflowService.exportGraphRag(9L, request))
                .isInstanceOf(InterruptedException.class)
                .hasMessageContaining("interrupted");

        then(databaseNamedLockService).should().release("pdf-export:9");
    }

    @Test
    void shouldLoadParseResultContentFromObjectStorage() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        DatabaseNamedLockService databaseNamedLockService = mock(DatabaseNamedLockService.class);
        CourseCoverObjectStorage objectStorage = mock(CourseCoverObjectStorage.class);

        PdfWorkflowService workflowService = new PdfWorkflowService(courseMaterialsService, parseResultsService, orchestrator, databaseNamedLockService, objectStorage);
        ParseResults parseResult = new ParseResults();
        parseResult.setId(31L);
        parseResult.setCourseMaterialId(9L);
        parseResult.setFileName("content_list.json");
        parseResult.setMinioBucket("course-artifacts");
        parseResult.setMinioObjectKey("parse-results/os/9/content_list.json");

        given(parseResultsService.getById(31L)).willReturn(parseResult);
        given(objectStorage.get("course-artifacts", "parse-results/os/9/content_list.json"))
                .willReturn(new StoredCourseCoverObject("[]".getBytes(), "application/octet-stream", 2L));

        var content = workflowService.loadParseResultContent(9L, 31L);

        assertThat(content.getFileName()).isEqualTo("content_list.json");
        assertThat(content.getContentType()).isEqualTo("application/json");
        assertThat(content.getFileSize()).isEqualTo(2L);
        assertThat(content.getBytes()).containsExactly('[', ']');
    }

    private static CourseMaterials doneMaterial() {
        CourseMaterials material = new CourseMaterials();
        material.setId(9L);
        material.setCourseId("os");
        material.setDisplayName("slides.pdf");
        material.setParseStatus("done");
        return material;
    }

    private static ExportGraphRagRequest exportRequest(boolean force) {
        ExportGraphRagRequest request = new ExportGraphRagRequest();
        request.setMode("section");
        request.setWithPageDocs(true);
        request.setForce(force);
        return request;
    }

    private static ProcessExecutionResult successResult() {
        return ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(0)
                .stderr("")
                .timedOut(false)
                .build();
    }
}
