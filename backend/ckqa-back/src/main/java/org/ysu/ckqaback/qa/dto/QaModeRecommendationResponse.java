package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 智能问答模式推荐响应。
 */
@Getter
public class QaModeRecommendationResponse {

    private final String recommendedMode;
    private final String fallbackMode;
    private final Double confidence;
    private final List<String> reasons;
    private final String reasonText;
    private final String confidenceBand;
    private final Boolean manualSwitchSuggested;
    private final String reviewPriority;
    private final Boolean betaHybridEnabled;
    private final Boolean contextDetected;
    private final String strategy;
    private final Map<String, Double> routeScores;

    private QaModeRecommendationResponse(
            String recommendedMode,
            String fallbackMode,
            Double confidence,
            List<String> reasons,
            String reasonText,
            String confidenceBand,
            Boolean manualSwitchSuggested,
            String reviewPriority,
            Boolean betaHybridEnabled,
            Boolean contextDetected,
            String strategy,
            Map<String, Double> routeScores
    ) {
        this.recommendedMode = recommendedMode;
        this.fallbackMode = fallbackMode;
        this.confidence = confidence;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
        this.reasonText = reasonText;
        this.confidenceBand = confidenceBand;
        this.manualSwitchSuggested = manualSwitchSuggested;
        this.reviewPriority = reviewPriority;
        this.betaHybridEnabled = betaHybridEnabled;
        this.contextDetected = contextDetected;
        this.strategy = strategy;
        this.routeScores = routeScores == null ? Map.of() : Map.copyOf(routeScores);
    }

    public static QaModeRecommendationResponse of(
            String recommendedMode,
            String fallbackMode,
            Double confidence,
            List<String> reasons,
            String reasonText,
            String confidenceBand,
            Boolean manualSwitchSuggested,
            String reviewPriority,
            Boolean betaHybridEnabled,
            Boolean contextDetected,
            String strategy,
            Map<String, Double> routeScores
    ) {
        return new QaModeRecommendationResponse(
                recommendedMode,
                fallbackMode,
                confidence,
                reasons,
                reasonText,
                confidenceBand,
                manualSwitchSuggested,
                reviewPriority,
                betaHybridEnabled,
                contextDetected,
                strategy,
                routeScores
        );
    }

    public static QaModeRecommendationResponse of(
            String recommendedMode,
            String fallbackMode,
            Double confidence,
            List<String> reasons,
            String reasonText,
            Boolean betaHybridEnabled,
            Boolean contextDetected,
            String strategy,
            Map<String, Double> routeScores
    ) {
        return of(
                recommendedMode,
                fallbackMode,
                confidence,
                reasons,
                reasonText,
                null,
                null,
                null,
                betaHybridEnabled,
                contextDetected,
                strategy,
                routeScores
        );
    }
}
