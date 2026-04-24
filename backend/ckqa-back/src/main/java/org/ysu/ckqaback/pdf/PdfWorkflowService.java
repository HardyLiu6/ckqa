package org.ysu.ckqaback.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.locks.DatabaseNamedLockService;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.pdf.dto.ParseResultResponse;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.pdf.dto.PdfOperationResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.io.IOException;
import java.util.List;

/**
 * PDF 查询与解析工作流服务。
 */
@Service
@RequiredArgsConstructor
public class PdfWorkflowService {

    private final CourseMaterialsService courseMaterialsService;
    private final ParseResultsService parseResultsService;
    private final PdfIngestOrchestrator pdfIngestOrchestrator;
    private final DatabaseNamedLockService databaseNamedLockService;

    public PdfFileResponse getPdfFile(Long id) {
        return PdfFileResponse.fromEntity(courseMaterialsService.getRequiredById(id));
    }

    public List<ParseResultResponse> listParseResults(Long pdfFileId) {
        return parseResultsService.listByPdfFileId(pdfFileId).stream()
                .map(ParseResultResponse::fromEntity)
                .toList();
    }

    public PdfOperationResponse startParse(Long pdfFileId) throws IOException, InterruptedException {
        CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);
        if (!courseMaterialsService.claimParseStart(pdfFileId)) {
            throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT);
        }

        ProcessExecutionResult result;
        try {
            result = pdfIngestOrchestrator.parse(material);
        } catch (IOException ex) {
            courseMaterialsService.markParseFailedIfStillProcessing(pdfFileId, ex.getMessage());
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            courseMaterialsService.markParseFailedIfStillProcessing(pdfFileId, "解析任务被中断");
            throw ex;
        }

        if (result.isTimedOut() || result.getExitCode() != 0) {
            courseMaterialsService.markParseFailedIfStillProcessing(
                    pdfFileId,
                    result.isTimedOut() ? "解析命令执行超时" : result.getStderr()
            );
            throw new BusinessException(
                    ApiResultCode.PDF_PARSE_EXECUTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF解析执行失败"
            );
        }

        CourseMaterials refreshed = courseMaterialsService.getRequiredById(pdfFileId);
        return PdfOperationResponse.success(
                refreshed.getId(),
                refreshed.getCourseId(),
                refreshed.getDisplayName(),
                refreshed.getParseStatus(),
                "解析任务已启动"
        );
    }

    public PdfOperationResponse exportGraphRag(Long pdfFileId, ExportGraphRagRequest request)
            throws IOException, InterruptedException {
        CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);
        if (!"done".equals(material.getParseStatus())) {
            throw new BusinessException(
                    ApiResultCode.PDF_PARSE_STATE_CONFLICT,
                    HttpStatus.CONFLICT,
                    "PDF解析完成后才能导出GraphRAG输入"
            );
        }

        String lockName = "pdf-export:" + pdfFileId;
        if (!databaseNamedLockService.acquire(lockName, 1)) {
            throw new BusinessException(ApiResultCode.PDF_EXPORT_LOCKED, HttpStatus.CONFLICT, "当前已有导出任务在执行");
        }

        try {
            if (!request.isForce() && parseResultsService.hasCompleteGraphRagExport(pdfFileId, request.getMode(), request.isWithPageDocs())) {
                return PdfOperationResponse.success(
                        pdfFileId,
                        material.getCourseId(),
                        material.getDisplayName(),
                        material.getParseStatus(),
                        "已存在完整导出结果"
                );
            }

            pdfIngestOrchestrator.exportGraphRag(material, request);
            return PdfOperationResponse.success(
                    pdfFileId,
                    material.getCourseId(),
                    material.getDisplayName(),
                    material.getParseStatus(),
                    "GraphRAG导出完成"
            );
        } finally {
            databaseNamedLockService.release(lockName);
        }
    }
}
