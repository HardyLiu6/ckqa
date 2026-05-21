package org.ysu.ckqaback.course.routing;

import org.ysu.ckqaback.course.dto.CourseRoutingCandidateResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 课程路由阈值与分差判定。
 */
public class CourseRoutingDecisionPolicy {

    private final double scoreThreshold;
    private final double marginThreshold;

    public CourseRoutingDecisionPolicy(double scoreThreshold, double marginThreshold) {
        this.scoreThreshold = scoreThreshold;
        this.marginThreshold = marginThreshold;
    }

    public CourseRoutingDecision decide(List<CourseRoutingCandidateResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new CourseRoutingDecision("no_match", null, 0D, 0D);
        }
        CourseRoutingCandidateResponse top = candidates.getFirst();
        double confidence = round(top.getConfidence());
        double second = candidates.size() > 1 ? candidates.get(1).getConfidence() : 0D;
        double margin = round(confidence - second);
        if (confidence < scoreThreshold) {
            return new CourseRoutingDecision("no_match", null, confidence, margin);
        }
        if (confidence >= scoreThreshold && margin >= marginThreshold) {
            return new CourseRoutingDecision("matched", top.getCourseId(), confidence, margin);
        }
        return new CourseRoutingDecision("needs_confirmation", null, confidence, margin);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
