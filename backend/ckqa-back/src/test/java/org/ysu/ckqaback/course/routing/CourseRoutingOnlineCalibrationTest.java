package org.ysu.ckqaback.course.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.dto.CourseRoutingCandidateResponse;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实线上课程路由校准测试。
 * <p>
 * 默认不运行；需要显式传入 {@code -Dckqa.courseRouting.onlineCalibration=true}。
 * </p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "ckqa.courseRouting.onlineCalibration", matches = "true")
class CourseRoutingOnlineCalibrationTest {

    private static final int GRAPHRAG_API_PORT = 18013;
    private static final double DEFAULT_SCORE_THRESHOLD = 0.30D;
    private static final double DEFAULT_MARGIN_THRESHOLD = 0.06D;
    private static final Path WORKTREE_ROOT = Path.of("").toAbsolutePath().normalize().getParent().getParent();
    private static final Path ENV_ROOT = Path.of(System.getProperty("ckqa.smoke.envRoot", WORKTREE_ROOT.toString()));
    private static final Map<String, String> LOCAL_ENV = loadLocalEnv();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private CourseRoutingService courseRoutingService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String mysqlHost = required("MYSQL_HOST");
        String mysqlPort = required("MYSQL_PORT");
        String mysqlDatabase = required("MYSQL_DATABASE");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
                + "&allowPublicKeyRetrieval=true&tinyInt1isBit=true");
        registry.add("spring.datasource.username", () -> required("MYSQL_USER"));
        registry.add("spring.datasource.password", () -> LOCAL_ENV.getOrDefault("MYSQL_PASSWORD", ""));

        registry.add("ckqa.integration.graphrag.root", () -> WORKTREE_ROOT.resolve("graphrag_pipeline").toString());
        registry.add("ckqa.integration.graphrag.build-runs-root", () -> ENV_ROOT
                .resolve("graphrag_pipeline/runtime/kb-build-runs")
                .toString());
        registry.add("ckqa.integration.graphrag.api-base-url", () -> "http://127.0.0.1:" + GRAPHRAG_API_PORT);
        registry.add("ckqa.integration.graphrag.managed-api.enabled", () -> "true");
        registry.add("ckqa.integration.graphrag.managed-api.skip-if-reachable", () -> "false");
        registry.add("ckqa.integration.graphrag.managed-api.host", () -> "127.0.0.1");
        registry.add("ckqa.integration.graphrag.managed-api.port", () -> String.valueOf(GRAPHRAG_API_PORT));
        registry.add("ckqa.integration.graphrag.managed-api.conda-env", () -> "graphrag-oneapi");
        registry.add("GRAPHRAG_API_BASE", () -> required("GRAPHRAG_API_BASE"));
        registry.add("GRAPHRAG_EMBEDDING_API_KEY", () -> required("GRAPHRAG_EMBEDDING_API_KEY"));
        registry.add("GRAPHRAG_EMBEDDING_MODEL", () -> "text-embedding-v4");
        registry.add("GRAPHRAG_EMBEDDING_DIMENSIONS", () -> "1024");
        registry.add("GRAPHRAG_EMBEDDING_OUTPUT_TYPE", () -> "dense");
        registry.add("GRAPHRAG_BUILD_RUNS_ROOT", () -> ENV_ROOT
                .resolve("graphrag_pipeline/runtime/kb-build-runs")
                .toString());
        registry.add("GRAPHRAG_COURSE_ROUTER_TABLE", () -> "course_profiles_text_embedding_v4");
        registry.add("GRAPHRAG_COURSE_ROUTER_LANCEDB_URI", () -> WORKTREE_ROOT
                .resolve("graphrag_pipeline/runtime/course-router/lancedb")
                .toString());
    }

    @Test
    void shouldCalibrateCourseRoutingThresholdsAgainstSmallValidationSet() throws Exception {
        waitForCourseRoutingReadiness();
        List<ValidationSample> samples = loadSamples();
        List<SampleResult> results = new ArrayList<>();
        for (ValidationSample sample : samples) {
            CourseRoutingRecommendRequest request = new CourseRoutingRecommendRequest();
            request.setQuestion(sample.question());
            request.setLimit(3);
            var response = courseRoutingService.recommend(request, adminUser());
            results.add(new SampleResult(sample, response.getCandidates()));
        }

        CalibrationResult defaultResult = evaluate(results, DEFAULT_SCORE_THRESHOLD, DEFAULT_MARGIN_THRESHOLD);
        CalibrationResult bestResult = findBestResult(results);
        printSummary(results, defaultResult, bestResult);

        assertThat(samples).hasSizeGreaterThanOrEqualTo(12);
        assertThat(defaultResult.falseAutoMatches()).isZero();
        assertThat(defaultResult.exactMatches()).isGreaterThanOrEqualTo(10);
    }

    private CalibrationResult findBestResult(List<SampleResult> results) {
        CalibrationResult best = null;
        for (double score = 0.25D; score <= 0.50D + 0.0001D; score += 0.025D) {
            for (double margin = 0.04D; margin <= 0.12D + 0.0001D; margin += 0.02D) {
                CalibrationResult current = evaluate(results, round(score), round(margin));
                if (best == null || current.compareTo(best) > 0) {
                    best = current;
                }
            }
        }
        return best;
    }

    private CalibrationResult evaluate(List<SampleResult> results, double scoreThreshold, double marginThreshold) {
        CourseRoutingDecisionPolicy policy = new CourseRoutingDecisionPolicy(scoreThreshold, marginThreshold);
        int exactMatches = 0;
        int falseAutoMatches = 0;
        int positiveTotal = 0;
        for (SampleResult result : results) {
            CourseRoutingDecision decision = policy.decide(result.candidates());
            String expectedCourseId = result.sample().expectedCourseId();
            if ("matched".equals(result.sample().expectedStatus())) {
                positiveTotal++;
                if ("matched".equals(decision.status()) && expectedCourseId.equals(decision.selectedCourseId())) {
                    exactMatches++;
                }
            } else if ("matched".equals(decision.status())) {
                falseAutoMatches++;
            }
        }
        return new CalibrationResult(scoreThreshold, marginThreshold, exactMatches, falseAutoMatches, positiveTotal);
    }

    private void printSummary(
            List<SampleResult> results,
            CalibrationResult defaultResult,
            CalibrationResult bestResult
    ) {
        System.out.printf(
                "course-routing-calibration default score=%.3f margin=%.3f exact=%d/%d falseAuto=%d%n",
                defaultResult.scoreThreshold(),
                defaultResult.marginThreshold(),
                defaultResult.exactMatches(),
                defaultResult.positiveTotal(),
                defaultResult.falseAutoMatches()
        );
        System.out.printf(
                "course-routing-calibration best score=%.3f margin=%.3f exact=%d/%d falseAuto=%d%n",
                bestResult.scoreThreshold(),
                bestResult.marginThreshold(),
                bestResult.exactMatches(),
                bestResult.positiveTotal(),
                bestResult.falseAutoMatches()
        );
        for (SampleResult result : results) {
            CourseRoutingCandidateResponse top = result.candidates().isEmpty() ? null : result.candidates().getFirst();
            CourseRoutingDecision decision = new CourseRoutingDecisionPolicy(
                    defaultResult.scoreThreshold(),
                    defaultResult.marginThreshold()
            ).decide(result.candidates());
            double margin = result.candidates().size() < 2
                    ? (top == null ? 0D : top.getConfidence())
                    : top.getConfidence() - result.candidates().get(1).getConfidence();
            System.out.printf(
                    "course-routing-sample id=%s expected=%s status=%s top=%s score=%.6f margin=%.6f%n",
                    result.sample().id(),
                    result.sample().expectedCourseId(),
                    decision.status(),
                    top == null ? null : top.getCourseId(),
                    top == null ? 0D : top.getConfidence(),
                    margin
            );
        }
    }

    private List<ValidationSample> loadSamples() throws IOException {
        ClassPathResource resource = new ClassPathResource("course-routing/course-routing-validation-v1.jsonl");
        List<ValidationSample> samples = new ArrayList<>();
        for (String line : resource.getContentAsString(StandardCharsets.UTF_8).split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            JsonNode node = OBJECT_MAPPER.readTree(line);
            String expectedStatus = node.get("expectedStatus").asText();
            String expectedCourseId = node.get("expectedCourseId").isNull() ? null : node.get("expectedCourseId").asText();
            if ("matched".equals(expectedStatus)) {
                assertThat(expectedCourseId)
                        .as("matched 样本必须指定 expectedCourseId: %s", node.get("id").asText())
                        .isNotBlank();
            }
            samples.add(new ValidationSample(
                    node.get("id").asText(),
                    node.get("question").asText(),
                    expectedStatus,
                    expectedCourseId
            ));
        }
        return samples;
    }

    private void waitForCourseRoutingReadiness() throws InterruptedException {
        RestClient client = RestClient.create("http://127.0.0.1:" + GRAPHRAG_API_PORT);
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        RuntimeException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = client.get()
                        .uri("/v1/internal/course-routing/readiness")
                        .retrieve()
                        .body(Map.class);
                if (body != null && Boolean.TRUE.equals(body.get("ready"))) {
                    return;
                }
            } catch (RuntimeException exception) {
                lastError = exception;
            }
            Thread.sleep(1000L);
        }
        throw new AssertionError("GraphRAG course-routing readiness 未在 90 秒内就绪", lastError);
    }

    private AuthenticatedUser adminUser() {
        return new AuthenticatedUser(1L, "ADM2026001", "admin.heqh", "何启航", List.of("admin"), List.of());
    }

    private static Map<String, String> loadLocalEnv() {
        Map<String, String> values = new LinkedHashMap<>();
        readEnvFile(ENV_ROOT.resolve("infra/.env"), values);
        readEnvFile(ENV_ROOT.resolve("backend/ckqa-back/.env"), values);
        readEnvFile(ENV_ROOT.resolve("graphrag_pipeline/.env"), values);
        return values;
    }

    private static void readEnvFile(Path path, Map<String, String> values) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                values.put(trimmed.substring(0, separator).trim(), unquote(trimmed.substring(separator + 1).trim()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地 calibration env 失败: " + path, exception);
        }
    }

    private static String required(String key) {
        String value = LOCAL_ENV.get(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少本地 calibration 配置: " + key);
        }
        return value.trim();
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private record ValidationSample(String id, String question, String expectedStatus, String expectedCourseId) {
    }

    private record SampleResult(ValidationSample sample, List<CourseRoutingCandidateResponse> candidates) {
    }

    private record CalibrationResult(
            double scoreThreshold,
            double marginThreshold,
            int exactMatches,
            int falseAutoMatches,
            int positiveTotal
    ) implements Comparable<CalibrationResult> {

        @Override
        public int compareTo(CalibrationResult other) {
            int byFalseAuto = Integer.compare(other.falseAutoMatches, falseAutoMatches);
            if (byFalseAuto != 0) {
                return byFalseAuto;
            }
            int byExact = Integer.compare(exactMatches, other.exactMatches);
            if (byExact != 0) {
                return byExact;
            }
            return Double.compare(scoreThreshold, other.scoreThreshold);
        }
    }
}
