package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.pdf.PdfWorkflowService;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;
import org.ysu.ckqaback.pdf.dto.ParseResultResponse;
import org.ysu.ckqaback.pdf.dto.PdfFileResponse;
import org.ysu.ckqaback.pdf.dto.PdfOperationResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 * PDF文件表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.PDF_FILES)
public class PdfFilesController {

    private final PdfWorkflowService pdfWorkflowService;

    @GetMapping("/{id}")
    public ApiResponse<PdfFileResponse> getPdfFile(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(pdfWorkflowService.getPdfFile(id));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<List<ParseResultResponse>> listParseResults(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(pdfWorkflowService.listParseResults(id));
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<PdfOperationResponse> parse(@PathVariable @Positive(message = "id必须大于0") Long id)
            throws IOException, InterruptedException {
        return ApiResponseUtils.success(pdfWorkflowService.startParse(id));
    }

    @PostMapping("/{id}/export-graphrag")
    public ApiResponse<PdfOperationResponse> exportGraphRag(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody ExportGraphRagRequest request
    ) throws IOException, InterruptedException {
        return ApiResponseUtils.success(pdfWorkflowService.exportGraphRag(id, request));
    }
}
