package org.ysu.ckqaback.qa.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程问答语义闸门阈值校准报告（纯逻辑，无外部依赖，便于单测）。
 * <p>
 * 输入每条样本的 {@code (courseId, label, evaluated, confidence)}，输出：
 * <ul>
 *   <li>按课程的 in_scope / out_of_scope 余弦相似度分布；</li>
 *   <li>在「in_scope 零误拦」为硬约束下，扫描出能拦下最多无关问题的单一全局阈值。</li>
 * </ul>
 * 仅统计 {@code evaluated=true} 的样本；{@code evaluated=false}（fail-open）单独计数。
 * </p>
 */
public final class QaScopeGateCalibrationReport {

    public enum Label {
        IN_SCOPE,
        OUT_OF_SCOPE
    }

    public record Sample(String courseId, String question, Label label, boolean evaluated, double confidence) {
    }

    public record Stats(int count, double min, double median, double max) {

        static Stats of(List<Double> values) {
            if (values == null || values.isEmpty()) {
                return new Stats(0, Double.NaN, Double.NaN, Double.NaN);
            }
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Comparator.naturalOrder());
            int size = sorted.size();
            double median = size % 2 == 1
                    ? sorted.get(size / 2)
                    : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2D;
            return new Stats(size, sorted.getFirst(), median, sorted.getLast());
        }
    }

    public record CourseDistribution(String courseId, Stats inScope, Stats outOfScope) {
    }

    public record Recommendation(
            double threshold,
            int inScopeFalseBlocks,
            int outOfScopeCaught,
            int inScopeEvaluated,
            int outOfScopeEvaluated,
            boolean cleanSeparation
    ) {
    }

    private final List<CourseDistribution> courseDistributions;
    private final Recommendation recommendation;
    private final int notEvaluatedCount;

    private QaScopeGateCalibrationReport(
            List<CourseDistribution> courseDistributions,
            Recommendation recommendation,
            int notEvaluatedCount
    ) {
        this.courseDistributions = courseDistributions;
        this.recommendation = recommendation;
        this.notEvaluatedCount = notEvaluatedCount;
    }

    public static QaScopeGateCalibrationReport from(List<Sample> samples, double sweepLow, double sweepHigh, double step) {
        List<Sample> safeSamples = samples == null ? List.of() : samples;
        int notEvaluated = (int) safeSamples.stream().filter(sample -> !sample.evaluated()).count();

        Map<String, List<Double>> inScopeByCourse = new LinkedHashMap<>();
        Map<String, List<Double>> outOfScopeByCourse = new LinkedHashMap<>();
        List<Double> allInScope = new ArrayList<>();
        List<Double> allOutOfScope = new ArrayList<>();
        for (Sample sample : safeSamples) {
            if (!sample.evaluated()) {
                continue;
            }
            inScopeByCourse.computeIfAbsent(sample.courseId(), key -> new ArrayList<>());
            outOfScopeByCourse.computeIfAbsent(sample.courseId(), key -> new ArrayList<>());
            if (sample.label() == Label.IN_SCOPE) {
                inScopeByCourse.get(sample.courseId()).add(sample.confidence());
                allInScope.add(sample.confidence());
            } else {
                outOfScopeByCourse.get(sample.courseId()).add(sample.confidence());
                allOutOfScope.add(sample.confidence());
            }
        }

        List<CourseDistribution> distributions = new ArrayList<>();
        for (String courseId : inScopeByCourse.keySet()) {
            distributions.add(new CourseDistribution(
                    courseId,
                    Stats.of(inScopeByCourse.get(courseId)),
                    Stats.of(outOfScopeByCourse.get(courseId))
            ));
        }

        Recommendation recommendation = recommend(allInScope, allOutOfScope, sweepLow, sweepHigh, step);
        return new QaScopeGateCalibrationReport(distributions, recommendation, notEvaluated);
    }

    private static Recommendation recommend(
            List<Double> inScope,
            List<Double> outOfScope,
            double sweepLow,
            double sweepHigh,
            double step
    ) {
        double bestThreshold = round(sweepLow);
        int bestFalseBlocks = Integer.MAX_VALUE;
        int bestCaught = -1;
        int steps = (int) Math.floor((sweepHigh - sweepLow) / step + 1e-9);
        for (int i = 0; i <= steps; i++) {
            double threshold = round(sweepLow + i * step);
            int falseBlocks = (int) inScope.stream().filter(confidence -> confidence < threshold).count();
            int caught = (int) outOfScope.stream().filter(confidence -> confidence < threshold).count();
            if (isBetter(falseBlocks, caught, threshold, bestFalseBlocks, bestCaught, bestThreshold)) {
                bestThreshold = threshold;
                bestFalseBlocks = falseBlocks;
                bestCaught = caught;
            }
        }
        if (bestCaught < 0) {
            bestFalseBlocks = 0;
            bestCaught = 0;
        }
        return new Recommendation(
                bestThreshold,
                bestFalseBlocks,
                bestCaught,
                inScope.size(),
                outOfScope.size(),
                bestFalseBlocks == 0
        );
    }

    /** 优先级：误拦更少 &gt; 拦下更多 &gt; 阈值更高（留更大余量）。 */
    private static boolean isBetter(
            int falseBlocks,
            int caught,
            double threshold,
            int bestFalseBlocks,
            int bestCaught,
            double bestThreshold
    ) {
        if (falseBlocks != bestFalseBlocks) {
            return falseBlocks < bestFalseBlocks;
        }
        if (caught != bestCaught) {
            return caught > bestCaught;
        }
        return threshold > bestThreshold;
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000D) / 1_000_000D;
    }

    public List<CourseDistribution> courseDistributions() {
        return courseDistributions;
    }

    public Recommendation recommendation() {
        return recommendation;
    }

    public int notEvaluatedCount() {
        return notEvaluatedCount;
    }

    public String render() {
        StringBuilder builder = new StringBuilder();
        builder.append("=== qa-scope-gate calibration ===\n");
        builder.append(String.format(
                "recommended threshold=%.3f cleanSeparation=%s inScopeFalseBlocks=%d outOfScopeCaught=%d/%d inScopeEvaluated=%d notEvaluated=%d%n",
                recommendation.threshold(),
                recommendation.cleanSeparation(),
                recommendation.inScopeFalseBlocks(),
                recommendation.outOfScopeCaught(),
                recommendation.outOfScopeEvaluated(),
                recommendation.inScopeEvaluated(),
                notEvaluatedCount
        ));
        for (CourseDistribution distribution : courseDistributions) {
            builder.append(String.format(
                    "course=%s in_scope[n=%d min=%.3f med=%.3f max=%.3f] out_of_scope[n=%d min=%.3f med=%.3f max=%.3f]%n",
                    distribution.courseId(),
                    distribution.inScope().count(),
                    distribution.inScope().min(),
                    distribution.inScope().median(),
                    distribution.inScope().max(),
                    distribution.outOfScope().count(),
                    distribution.outOfScope().min(),
                    distribution.outOfScope().median(),
                    distribution.outOfScope().max()
            ));
        }
        return builder.toString();
    }
}
