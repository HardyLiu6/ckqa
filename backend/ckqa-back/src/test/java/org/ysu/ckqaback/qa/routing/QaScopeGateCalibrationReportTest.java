package org.ysu.ckqaback.qa.routing;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.CourseDistribution;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.Label;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.Recommendation;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.Sample;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class QaScopeGateCalibrationReportTest {

    @Test
    void shouldComputePerCourseDistributionAndCleanThreshold() {
        List<Sample> samples = List.of(
                new Sample("os", "什么是进程", Label.IN_SCOPE, true, 0.55D),
                new Sample("os", "页面置换算法", Label.IN_SCOPE, true, 0.48D),
                new Sample("os", "银行家算法安全性检查", Label.IN_SCOPE, true, 0.40D),
                new Sample("os", "进程与线程区别", Label.IN_SCOPE, true, 0.36D),
                new Sample("os", "今天晚上吃什么", Label.OUT_OF_SCOPE, true, 0.30D),
                new Sample("os", "宿舍报修电话", Label.OUT_OF_SCOPE, true, 0.22D),
                new Sample("os", "帮我写一首诗", Label.OUT_OF_SCOPE, true, 0.10D)
        );

        QaScopeGateCalibrationReport report = QaScopeGateCalibrationReport.from(samples, 0.15D, 0.45D, 0.01D);

        assertThat(report.notEvaluatedCount()).isZero();
        List<CourseDistribution> distributions = report.courseDistributions();
        assertThat(distributions).hasSize(1);
        CourseDistribution os = distributions.getFirst();
        assertThat(os.courseId()).isEqualTo("os");
        assertThat(os.inScope().count()).isEqualTo(4);
        assertThat(os.inScope().min()).isCloseTo(0.36D, within(1e-9));
        assertThat(os.inScope().median()).isCloseTo(0.44D, within(1e-9));
        assertThat(os.inScope().max()).isCloseTo(0.55D, within(1e-9));
        assertThat(os.outOfScope().count()).isEqualTo(3);
        assertThat(os.outOfScope().min()).isCloseTo(0.10D, within(1e-9));
        assertThat(os.outOfScope().median()).isCloseTo(0.22D, within(1e-9));
        assertThat(os.outOfScope().max()).isCloseTo(0.30D, within(1e-9));

        Recommendation recommendation = report.recommendation();
        assertThat(recommendation.threshold()).isCloseTo(0.36D, within(1e-9));
        assertThat(recommendation.inScopeFalseBlocks()).isZero();
        assertThat(recommendation.outOfScopeCaught()).isEqualTo(3);
        assertThat(recommendation.inScopeEvaluated()).isEqualTo(4);
        assertThat(recommendation.outOfScopeEvaluated()).isEqualTo(3);
        assertThat(recommendation.cleanSeparation()).isTrue();
    }

    @Test
    void shouldExcludeNotEvaluatedAndExposeOverlapWhenInScopeOutlierIsLow() {
        List<Sample> samples = List.of(
                new Sample("os", "什么是进程", Label.IN_SCOPE, true, 0.50D),
                new Sample("os", "页面置换算法", Label.IN_SCOPE, true, 0.45D),
                new Sample("os", "冷门措辞的真问题", Label.IN_SCOPE, true, 0.18D),
                new Sample("os", "今天晚上吃什么", Label.OUT_OF_SCOPE, true, 0.30D),
                new Sample("os", "宿舍报修", Label.OUT_OF_SCOPE, true, 0.20D),
                new Sample("os", "服务故障的问题", Label.IN_SCOPE, false, 0.0D)
        );

        QaScopeGateCalibrationReport report = QaScopeGateCalibrationReport.from(samples, 0.15D, 0.45D, 0.01D);

        assertThat(report.notEvaluatedCount()).isEqualTo(1);
        Recommendation recommendation = report.recommendation();
        assertThat(recommendation.inScopeEvaluated()).isEqualTo(3);
        assertThat(recommendation.outOfScopeEvaluated()).isEqualTo(2);
        // in_scope 离群值 0.18 低于所有无关问题，零误拦下无法拦住任何无关问题
        assertThat(recommendation.threshold()).isCloseTo(0.18D, within(1e-9));
        assertThat(recommendation.inScopeFalseBlocks()).isZero();
        assertThat(recommendation.outOfScopeCaught()).isZero();
        assertThat(recommendation.cleanSeparation()).isTrue();
    }

    @Test
    void shouldRenderHumanReadableSummary() {
        List<Sample> samples = List.of(
                new Sample("os", "什么是进程", Label.IN_SCOPE, true, 0.50D),
                new Sample("os", "今天晚上吃什么", Label.OUT_OF_SCOPE, true, 0.20D)
        );

        String rendered = QaScopeGateCalibrationReport.from(samples, 0.15D, 0.45D, 0.01D).render();

        assertThat(rendered).contains("os");
        assertThat(rendered).contains("threshold");
    }
}
