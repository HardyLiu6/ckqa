package org.ysu.ckqaback.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseCoverObjectStorage;
import org.ysu.ckqaback.course.StoredCourseCoverObject;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.ParseResults;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.locks.DatabaseNamedLockService;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.pdf.dto.ParseResultContent;
import org.ysu.ckqaback.pdf.dto.ParseResultResponse;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.pdf.dto.PdfOperationResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

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
    private final CourseCoverObjectStorage objectStorage;
    private final PdfParseTaskDispatcher parseTaskDispatcher;
    private final CourseAccessService courseAccessService;

    public PdfFileResponse getPdfFile(Long id) {
        return PdfFileResponse.fromEntity(courseMaterialsService.getRequiredById(id));
    }

    public List<ParseResultResponse> listParseResults(Long pdfFileId) {
        return parseResultsService.listByPdfFileId(pdfFileId).stream()
                .map(ParseResultResponse::fromEntity)
                .toList();
    }

    public ParseResultContent loadParseResultContent(Long pdfFileId, Long resultId) {
        ParseResults parseResult = parseResultsService.getById(resultId);
        if (parseResult == null || !Objects.equals(pdfFileId, parseResult.getCourseMaterialId())) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND, "解析产物不存在");
        }
        try {
            StoredCourseCoverObject object = objectStorage.get(
                    parseResult.getMinioBucket(),
                    parseResult.getMinioObjectKey()
            );
            String contentType = object.contentType() == null || object.contentType().isBlank()
                    || "application/octet-stream".equalsIgnoreCase(object.contentType())
                    ? ParseResultResponse.inferContentType(parseResult.getFileName())
                    : object.contentType();
            return ParseResultContent.builder()
                    .fileName(parseResult.getFileName())
                    .contentType(contentType)
                    .fileSize(object.size())
                    .bytes(object.bytes())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND, "解析产物不存在");
        }
    }

    public PdfOperationResponse startParse(Long pdfFileId) {
        CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);
        courseAccessService.assertCourseWritable(material.getCourseId());
        if (!courseMaterialsService.claimParseStart(pdfFileId)) {
            throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT);
        }

        try {
            parseTaskDispatcher.dispatch(material);
        } catch (RejectedExecutionException ex) {
            courseMaterialsService.markParseFailedIfStillProcessing(pdfFileId, "解析任务提交失败: " + ex.getMessage());
            throw new BusinessException(
                    ApiResultCode.PDF_PARSE_EXECUTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF解析执行失败"
            );
        }

        return PdfOperationResponse.success(
                material.getId(),
                material.getCourseId(),
                material.getDisplayName(),
                "processing",
                "解析任务已提交"
        );
    }

    public PdfOperationResponse exportGraphRag(Long pdfFileId, ExportGraphRagRequest request)
            throws IOException, InterruptedException {
        CourseMaterials material = courseMaterialsService.getRequiredById(pdfFileId);
        courseAccessService.assertCourseWritable(material.getCourseId());
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

            ProcessExecutionResult result = pdfIngestOrchestrator.exportGraphRag(material, request);
            if (result.isTimedOut() || result.getExitCode() != 0) {
                throw new BusinessException(
                        ApiResultCode.PDF_PARSE_EXECUTION_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "GraphRAG导出失败"
                );
            }

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
