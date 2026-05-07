package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.io.IOException;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class PdfParseTaskDispatcherTest {

    @Test
    void shouldMarkFailedWhenBackgroundParseReturnsNonZero() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        PdfParseTaskDispatcher dispatcher = new PdfParseTaskDispatcher(
                courseMaterialsService,
                orchestrator,
                Runnable::run
        );
        CourseMaterials material = material();

        given(orchestrator.parse(material)).willReturn(ProcessExecutionResult.builder()
                .command(List.of("python", "mineru_parser.py"))
                .exitCode(1)
                .stderr("MinerU failed")
                .timedOut(false)
                .build());

        dispatcher.dispatch(material);

        then(courseMaterialsService).should().markParseFailedIfStillProcessing(7L, "MinerU failed");
    }

    @Test
    void shouldMarkFailedWhenBackgroundParseThrowsIOException() throws Exception {
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        PdfIngestOrchestrator orchestrator = mock(PdfIngestOrchestrator.class);
        PdfParseTaskDispatcher dispatcher = new PdfParseTaskDispatcher(
                courseMaterialsService,
                orchestrator,
                Runnable::run
        );
        CourseMaterials material = material();

        willThrow(new IOException("spawn failed")).given(orchestrator).parse(material);

        dispatcher.dispatch(material);

        then(courseMaterialsService).should().markParseFailedIfStillProcessing(7L, "spawn failed");
    }

    private static CourseMaterials material() {
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setDisplayName("book.pdf");
        return material;
    }
}
