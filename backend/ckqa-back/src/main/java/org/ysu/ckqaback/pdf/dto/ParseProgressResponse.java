package org.ysu.ckqaback.pdf.dto;

import lombok.Getter;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.CourseMaterials;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 解析进度响应体。
 */
@Getter
public class ParseProgressResponse {

    private final String stage;
    private final String stageLabel;
    private final Integer percent;
    private final boolean estimated;
    private final String detail;
    private final Integer extractedPages;
    private final Integer totalPages;
    private final LocalDateTime startedAt;
    private final LocalDateTime updatedAt;

    private ParseProgressResponse(
            String stage,
            String stageLabel,
            Integer percent,
            boolean estimated,
            String detail,
            Integer extractedPages,
            Integer totalPages,
            LocalDateTime startedAt,
            LocalDateTime updatedAt
    ) {
        this.stage = stage;
        this.stageLabel = stageLabel;
        this.percent = percent;
        this.estimated = estimated;
        this.detail = detail;
        this.extractedPages = extractedPages;
        this.totalPages = totalPages;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
    }

    public static ParseProgressResponse fromMaterial(CourseMaterials material) {
        String status = material == null ? "pending" : normalizeStatus(material.getParseStatus());
        ParseProgressResponse realProgress = fromStoredMineruProgress(status, material);
        if (realProgress != null) {
            return realProgress;
        }
        return switch (status) {
            case "done" -> new ParseProgressResponse(
                    "parse_completed",
                    "解析完成",
                    100,
                    false,
                    "MinerU 解析已完成，解析产物可查看。",
                    null,
                    null,
                    null,
                    null
            );
            case "failed" -> new ParseProgressResponse(
                    "parse_failed",
                    "解析失败",
                    null,
                    false,
                    firstText(material == null ? null : material.getParseErrorMsg(), "解析失败，请查看错误信息后重新触发。"),
                    null,
                    null,
                    null,
                    null
            );
            case "processing" -> {
                boolean hasBatch = StringUtils.hasText(material == null ? null : material.getMineruBatchId());
                yield hasBatch
                        ? new ParseProgressResponse(
                        "mineru_processing",
                        "MinerU 解析中",
                        35,
                        true,
                        "解析任务已提交到 MinerU，后端正在等待产物回写。",
                        null,
                        null,
                        null,
                        null
                )
                        : new ParseProgressResponse(
                        "task_submitted",
                        "任务已提交",
                        15,
                        true,
                        "后端已接收解析请求，正在确认 MinerU 任务状态。",
                        null,
                        null,
                        null,
                        null
                );
            }
            default -> new ParseProgressResponse(
                    "waiting",
                    "等待解析",
                    0,
                    false,
                    "资料已上传，尚未触发解析。",
                    null,
                    null,
                    null,
                    null
            );
        };
    }

    private static ParseProgressResponse fromStoredMineruProgress(String status, CourseMaterials material) {
        if (material == null || !"processing".equals(status)) {
            return null;
        }

        Integer extractedPages = material.getParseProgressExtractedPages();
        Integer totalPages = material.getParseProgressTotalPages();
        Integer percent = firstProgressPercent(material.getParseProgressPercent(), extractedPages, totalPages);
        if (percent == null) {
            return null;
        }

        return new ParseProgressResponse(
                "mineru_page_extracting",
                "MinerU 页级解析中",
                percent,
                false,
                buildPageProgressDetail(extractedPages, totalPages),
                extractedPages,
                totalPages,
                material.getParseProgressStartedAt(),
                material.getParseProgressUpdatedAt()
        );
    }

    private static Integer firstProgressPercent(Integer storedPercent, Integer extractedPages, Integer totalPages) {
        if (storedPercent != null) {
            return clampPercent(storedPercent);
        }
        if (extractedPages == null || totalPages == null || totalPages <= 0) {
            return null;
        }
        return clampPercent(Math.round((extractedPages * 100.0f) / totalPages));
    }

    private static String buildPageProgressDetail(Integer extractedPages, Integer totalPages) {
        if (extractedPages != null && totalPages != null && totalPages > 0) {
            return "MinerU 已解析 " + extractedPages + "/" + totalPages + " 页。";
        }
        return "MinerU 已返回页级解析进度。";
    }

    private static Integer clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if ("success".equals(normalized) || "complete".equals(normalized) || "completed".equals(normalized)) {
            return "done";
        }
        if ("running".equals(normalized)) {
            return "processing";
        }
        if ("error".equals(normalized)) {
            return "failed";
        }
        return StringUtils.hasText(normalized) ? normalized : "pending";
    }

    private static String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
