package org.ysu.ckqaback.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.qa.QaOperationsService;
import org.ysu.ckqaback.qa.dto.QaOperationLogDetailResponse;
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;
import org.ysu.ckqaback.qa.dto.QaOperationLogResponse;
import org.ysu.ckqaback.qa.dto.QaOperationsQueryRequest;
import org.ysu.ckqaback.qa.dto.QaOperationsSummaryResponse;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.dto.UpsertQaSourceReviewRequest;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理端问答运维接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_OPERATIONS)
public class QaOperationsController {

    private final QaOperationsService qaOperationsService;
    private final QaSourceReviewsService sourceReviewsService;

    @GetMapping("/logs")
    public ApiResponse<ApiPageData<QaOperationLogResponse>> listLogs(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.pageLogs(request, currentUser(servletRequest)));
    }

    /**
     * 全库聚合统计：按当前筛选条件返回总数、成功、失败/失效、低置信、待复核数。
     * 让前端运维概览卡片不必基于当前页做误导性统计。
     */
    @GetMapping("/logs/summary")
    public ApiResponse<QaOperationsSummaryResponse> summaryLogs(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.summaryLogs(request, currentUser(servletRequest)));
    }

    @GetMapping("/logs/export")
    public ApiResponse<List<QaOperationLogDetailResponse>> exportLogs(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.exportLogs(request, currentUser(servletRequest)));
    }

    /**
     * 扁平 Excel 导出。
     *
     * <p>使用 EasyExcel 流式写入，便于大批量样本导出不触发 OOM。
     * 完整快照仍走 /logs/export 的 JSON 端点。
     */
    @GetMapping("/logs/export.xlsx")
    public void exportLogsAsExcel(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) throws IOException {
        List<QaOperationLogExportRow> rows = qaOperationsService.exportFlatRows(request, currentUser(servletRequest));
        writeDownloadHeaders(response, "qa-operation-samples", "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (OutputStream out = response.getOutputStream()) {
            EasyExcel.write(out, QaOperationLogExportRow.class)
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet("问答运维样本")
                    .doWrite(rows);
        }
    }

    /**
     * 扁平 CSV 导出。
     *
     * <p>纯 UTF-8 + BOM，Excel 与 LibreOffice 直接打开均可正确显示中文。
     * 字段顺序与 Excel 端一致；助手回复等长文本未在导出范围内。
     */
    @GetMapping("/logs/export.csv")
    public void exportLogsAsCsv(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) throws IOException {
        List<QaOperationLogExportRow> rows = qaOperationsService.exportFlatRows(request, currentUser(servletRequest));
        writeDownloadHeaders(response, "qa-operation-samples", "csv",
                "text/csv; charset=UTF-8");
        try (OutputStream out = response.getOutputStream();
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // UTF-8 BOM 让 Excel 默认按 UTF-8 识别中文
            writer.write('\uFEFF');
            String[] headers = {
                    "日志ID", "课程", "知识库", "学生", "模式", "查询策略",
                    "任务状态", "路由置信度", "复核优先级",
                    "耗时(ms)", "来源数",
                    "有用反馈", "无用反馈", "待改进反馈", "来源问题反馈",
                    "创建时间"
            };
            writer.write(String.join(",", headers));
            writer.write('\n');
            for (QaOperationLogExportRow row : rows) {
                writer.write(String.join(",",
                        csv(row.getRetrievalLogId()),
                        csv(row.getCourseName()),
                        csv(row.getKnowledgeBaseName()),
                        csv(row.getUserDisplay()),
                        csv(row.getQueryMode()),
                        csv(row.getQueryStrategy()),
                        csv(row.getTaskStatus()),
                        csv(row.getRoutingConfidenceBand()),
                        csv(row.getRoutingReviewPriority()),
                        csv(row.getDurationMs()),
                        csv(row.getSourceCount()),
                        csv(row.getHelpfulCount()),
                        csv(row.getUnhelpfulCount()),
                        csv(row.getNeedsImprovementCount()),
                        csv(row.getSourceIssueCount()),
                        csv(row.getCreatedAt())
                ));
                writer.write('\n');
            }
            writer.flush();
        }
    }

    @GetMapping("/logs/{retrievalLogId}")
    public ApiResponse<QaOperationLogDetailResponse> getLogDetail(
            @PathVariable @Positive(message = "retrievalLogId必须大于0") Long retrievalLogId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.getLogDetail(retrievalLogId, currentUser(servletRequest)));
    }

    @PutMapping("/source-reviews/{retrievalHitId}")
    public ApiResponse<QaSourceReviewResponse> upsertSourceReview(
            @PathVariable @Positive(message = "retrievalHitId必须大于0") Long retrievalHitId,
            @Valid @RequestBody UpsertQaSourceReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(sourceReviewsService.upsertReview(retrievalHitId, request, currentUser(servletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        return AuthContext.fromRequestOrCurrentJwt(servletRequest);
    }

    /**
     * 通用下载响应头：按 Content-Disposition: attachment 让浏览器触发下载，
     * 文件名含日期前缀，按 RFC 5987 编码兼容含中文/空格场景。
     */
    private void writeDownloadHeaders(HttpServletResponse response, String baseName,
                                      String extension, String contentType) {
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fileName = baseName + "-" + date + "." + extension;
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
    }

    /**
     * 极简 CSV 转义：包含逗号/双引号/换行的字段用双引号包裹，内部双引号 `""` 转义。
     */
    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        boolean needQuote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (needQuote) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
