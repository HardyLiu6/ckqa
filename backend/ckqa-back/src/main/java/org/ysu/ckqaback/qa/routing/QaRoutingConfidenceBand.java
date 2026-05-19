package org.ysu.ckqaback.qa.routing;

/**
 * 智能路由置信度分档。
 */
public final class QaRoutingConfidenceBand {

    public static final String HIGH = "high_confidence";
    public static final String MEDIUM = "medium_confidence";
    public static final String LOW = "low_confidence";
    public static final String UNCERTAIN = "uncertain";

    private QaRoutingConfidenceBand() {
    }

    public static String from(Double confidence) {
        double value = confidence == null ? 0D : confidence;
        if (value >= 0.80D) {
            return HIGH;
        }
        if (value >= 0.65D) {
            return MEDIUM;
        }
        if (value >= 0.50D) {
            return LOW;
        }
        return UNCERTAIN;
    }

    public static boolean needsManualSwitch(String band) {
        return LOW.equals(band) || UNCERTAIN.equals(band);
    }

    public static String reviewPriority(String band) {
        return needsManualSwitch(band) ? "low_confidence" : "normal";
    }
}
