package org.ysu.ckqaback.qa.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 问答运维样本扁平导出行：CSV / Excel 共用，用于人工查看与统计。
 *
 * <p>只保留单条日志的关键字段（不含 sources / feedback / reviews 子表）；
 * 完整快照仍走 JSON 导出。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(20)
public class QaOperationLogExportRow {

    private static final DateTimeFormatter SHANGHAI_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExcelProperty("日志ID")
    @ColumnWidth(10)
    private Long retrievalLogId;

    @ExcelProperty("课程")
    @ColumnWidth(24)
    private String courseName;

    @ExcelProperty("知识库")
    @ColumnWidth(24)
    private String knowledgeBaseName;

    @ExcelProperty("学生")
    @ColumnWidth(16)
    private String userDisplay;

    @ExcelProperty("模式")
    @ColumnWidth(12)
    private String queryMode;

    @ExcelProperty("查询策略")
    @ColumnWidth(20)
    private String queryStrategy;

    @ExcelProperty("任务状态")
    @ColumnWidth(12)
    private String taskStatus;

    @ExcelProperty("路由置信度")
    @ColumnWidth(14)
    private String routingConfidenceBand;

    @ExcelProperty("复核优先级")
    @ColumnWidth(14)
    private String routingReviewPriority;

    @ExcelProperty("耗时(ms)")
    @ColumnWidth(12)
    private Long durationMs;

    @ExcelProperty("来源数")
    @ColumnWidth(10)
    private Long sourceCount;

    @ExcelProperty("有用反馈")
    @ColumnWidth(10)
    private Long helpfulCount;

    @ExcelProperty("无用反馈")
    @ColumnWidth(10)
    private Long unhelpfulCount;

    @ExcelProperty("待改进反馈")
    @ColumnWidth(12)
    private Long needsImprovementCount;

    @ExcelProperty("来源问题反馈")
    @ColumnWidth(14)
    private Long sourceIssueCount;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createdAt;

    /**
     * 由列表查询行投影出扁平导出行（基础字段）。
     */
    public static QaOperationLogExportRow fromLogResponse(QaOperationLogResponse row) {
        QaOperationLogExportRow export = new QaOperationLogExportRow();
        export.retrievalLogId = row.getRetrievalLogId();
        export.courseName = Optional.ofNullable(row.getCourseName()).orElse(row.getCourseId());
        export.knowledgeBaseName = Optional.ofNullable(row.getKnowledgeBaseName())
                .orElseGet(() -> row.getKnowledgeBaseId() == null ? "" : ("KB " + row.getKnowledgeBaseId()));
        export.userDisplay = firstNonEmpty(row.getDisplayName(), row.getUsername(),
                row.getUserId() == null ? null : row.getUserId().toString());
        export.queryMode = row.getQueryMode();
        export.queryStrategy = renderStrategy(row);
        export.taskStatus = row.getTaskStatus();
        export.routingConfidenceBand = row.getRoutingConfidenceBand();
        export.routingReviewPriority = row.getRoutingReviewPriority();
        export.durationMs = row.getDurationMs();
        export.sourceCount = (long) row.getSourceCount();
        if (row.getFeedbackSummary() != null) {
            export.helpfulCount = (long) row.getFeedbackSummary().getHelpful();
            export.unhelpfulCount = (long) row.getFeedbackSummary().getUnhelpful();
            export.needsImprovementCount = (long) row.getFeedbackSummary().getNeedsImprovement();
            export.sourceIssueCount = (long) row.getFeedbackSummary().getSourceIssue();
        }
        export.createdAt = formatDateTime(row.getCreatedAt());
        return export;
    }

    private static String renderStrategy(QaOperationLogResponse row) {
        String strategy = row.getQueryEngineStrategy() == null ? "cli" : row.getQueryEngineStrategy();
        if (row.getHistoryFallbackReason() != null && !row.getHistoryFallbackReason().isBlank()) {
            return strategy + " / 已降级";
        }
        if (Boolean.TRUE.equals(row.getMemoryApplied())) {
            long count = row.getMemorySourceCount() == null ? 0L : row.getMemorySourceCount();
            return strategy + " / 记忆 " + count;
        }
        return strategy;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(SHANGHAI_FORMAT);
    }
}
