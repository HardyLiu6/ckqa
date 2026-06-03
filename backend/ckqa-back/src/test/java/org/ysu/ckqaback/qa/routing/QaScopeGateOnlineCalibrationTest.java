package org.ysu.ckqaback.qa.routing;

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
import org.ysu.ckqaback.course.routing.CourseRoutingService;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.Label;
import org.ysu.ckqaback.qa.routing.QaScopeGateCalibrationReport.Sample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实线上「课程问答语义闸门」阈值校准测试。
 * <p>
 * 默认不运行；需要显式传入 {@code -Dckqa.qaScopeGate.onlineCalibration=true}，并具备
 * GraphRAG 服务 + embedding key + 课程画像 + MySQL（与课程路由校准同一套基础设施）。
 * </p>
 * <p>
 * 它对验证集逐条调用 {@link CourseRoutingService#evaluateScopeRelevance(String, String)} 取真实余弦相似度，
 * 喂给纯逻辑 {@link QaScopeGateCalibrationReport} 打印 in_scope / out_of_scope 分布与推荐阈值。
 * 推荐阈值回填到 {@code ckqa.qa-domain-guard.out-of-scope-threshold}。
 * </p>
 * <p>
 * 验证集 {@code qa-scope/qa-scope-validation-v1.jsonl} 中 {@code courseId="*"} 的无关问题为共享池，
 * 会对数据集里出现的每一门 in_scope 课程各评测一遍；接入新课程画像时，只需为该课追加若干 in_scope 行
 * （{@code courseId} 写新课 id），共享无关池自动套用——无需改动本测试。
 * </p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "ckqa.qaScopeGate.onlineCalibration", matches = "true")
class QaScopeGateOnlineCalibrationTest {

    private static final int GRAPHRAG_API_PORT = 18013;
    private static final double SWEEP_LOW = 0.15D;
    private static final double SWEEP_HIGH = 0.45D;
    private static final double SWEEP_STEP = 0.01D;
    private static final String SHARED_OFF_TOPIC_COURSE = "*";
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
    void shouldCalibrateQaScopeGateThresholdAgainstValidationSet() throws Exception {
        waitForCourseRoutingReadiness();
        List<ValidationSample> validationSamples = loadSamples();
        Set<String> inScopeCourseIds = collectInScopeCourseIds(validationSamples);

        List<Sample> scored = new ArrayList<>();
        for (ValidationSample sample : validationSamples) {
            if (SHARED_OFF_TOPIC_COURSE.equals(sample.courseId())) {
                for (String courseId : inScopeCourseIds) {
                    scored.add(score(courseId, sample));
                }
            } else {
                scored.add(score(sample.courseId(), sample));
            }
        }

        QaScopeGateCalibrationReport report = QaScopeGateCalibrationReport.from(scored, SWEEP_LOW, SWEEP_HIGH, SWEEP_STEP);
        System.out.print(report.render());
        printPerSample(scored);

        assertThat(inScopeCourseIds)
                .as("验证集必须至少包含一门 in_scope 课程")
                .isNotEmpty();
        assertThat(report.recommendation().inScopeEvaluated())
                .as("没有任何 in_scope 样本被算出相似度，基础设施可能未就绪")
                .isPositive();
        assertThat(report.recommendation().outOfScopeEvaluated())
                .as("没有任何 out_of_scope 样本被算出相似度，基础设施可能未就绪")
                .isPositive();
    }

    private Sample score(String courseId, ValidationSample sample) {
        ScopeRelevance relevance = courseRoutingService.evaluateScopeRelevance(courseId, sample.question());
        return new Sample(courseId, sample.question(), sample.label(), relevance.evaluated(), relevance.confidence());
    }

    private Set<String> collectInScopeCourseIds(List<ValidationSample> samples) {
        Set<String> courseIds = new LinkedHashSet<>();
        for (ValidationSample sample : samples) {
            if (sample.label() == Label.IN_SCOPE && !SHARED_OFF_TOPIC_COURSE.equals(sample.courseId())) {
                courseIds.add(sample.courseId());
            }
        }
        return courseIds;
    }

    private void printPerSample(List<Sample> scored) {
        for (Sample sample : scored) {
            System.out.printf(
                    "qa-scope-sample courseId=%s label=%s evaluated=%s confidence=%.6f question=%s%n",
                    sample.courseId(),
                    sample.label(),
                    sample.evaluated(),
                    sample.confidence(),
                    sample.question()
            );
        }
    }

    private List<ValidationSample> loadSamples() throws IOException {
        ClassPathResource resource = new ClassPathResource("qa-scope/qa-scope-validation-v1.jsonl");
        List<ValidationSample> samples = new ArrayList<>();
        for (String line : resource.getContentAsString(StandardCharsets.UTF_8).split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            JsonNode node = OBJECT_MAPPER.readTree(line);
            String id = node.get("id").asText();
            String courseId = node.get("courseId").asText();
            String label = node.get("label").asText();
            assertThat(courseId).as("样本缺少 courseId: %s", id).isNotBlank();
            samples.add(new ValidationSample(id, courseId, node.get("question").asText(), toLabel(label, id)));
        }
        return samples;
    }

    private Label toLabel(String label, String id) {
        return switch (label) {
            case "in_scope" -> Label.IN_SCOPE;
            case "out_of_scope" -> Label.OUT_OF_SCOPE;
            default -> throw new IllegalArgumentException("未知 label=" + label + " (样本 " + id + ")");
        };
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

    private record ValidationSample(String id, String courseId, String question, Label label) {
    }
}
