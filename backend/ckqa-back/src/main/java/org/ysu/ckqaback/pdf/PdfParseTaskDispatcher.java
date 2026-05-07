package org.ysu.ckqaback.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * 后台执行 pdf_ingest 解析命令，避免 HTTP 请求线程等待 MinerU 全流程。
 */
@Slf4j
@Service
public class PdfParseTaskDispatcher {

    private final CourseMaterialsService courseMaterialsService;
    private final PdfIngestOrchestrator pdfIngestOrchestrator;
    private final Executor executor;

    public PdfParseTaskDispatcher(
            CourseMaterialsService courseMaterialsService,
            PdfIngestOrchestrator pdfIngestOrchestrator,
            @Qualifier("pdfParseExecutor") Executor executor
    ) {
        this.courseMaterialsService = courseMaterialsService;
        this.pdfIngestOrchestrator = pdfIngestOrchestrator;
        this.executor = executor;
    }

    public void dispatch(CourseMaterials material) {
        executor.execute(() -> runParse(material));
    }

    private void runParse(CourseMaterials material) {
        Long materialId = material.getId();
        try {
            ProcessExecutionResult result = pdfIngestOrchestrator.parse(material);
            if (result.isTimedOut() || result.getExitCode() != 0) {
                courseMaterialsService.markParseFailedIfStillProcessing(
                        materialId,
                        result.isTimedOut() ? "解析命令执行超时" : result.getStderr()
                );
            }
        } catch (IOException ex) {
            courseMaterialsService.markParseFailedIfStillProcessing(materialId, ex.getMessage());
            log.warn("PDF 解析命令启动失败, materialId={}", materialId, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            courseMaterialsService.markParseFailedIfStillProcessing(materialId, "解析任务被中断");
            log.warn("PDF 解析后台任务被中断, materialId={}", materialId, ex);
        } catch (RuntimeException ex) {
            courseMaterialsService.markParseFailedIfStillProcessing(materialId, ex.getMessage());
            log.warn("PDF 解析后台任务异常, materialId={}", materialId, ex);
        }
    }
}
