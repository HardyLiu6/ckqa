package org.ysu.ckqaback.course.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendRequest;
import org.ysu.ckqaback.service.CourseRouteProfilesService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实线上 embedding 课程路由 smoke。
 * <p>
 * 默认不运行；需要显式传入 {@code -Dckqa.courseRouting.onlineSmoke=true}。
 * 该测试会读取本机 env、启动托管 GraphRAG API，并写入真实 MySQL/LanceDB。
 * </p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "ckqa.courseRouting.onlineSmoke", matches = "true")
class CourseRoutingOnlineSmokeTest {

    private static final String OS_COURSE_ID = "crs-20260506-r4slkr";
    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final int GRAPHRAG_API_PORT = 18012;
    private static final Path WORKTREE_ROOT = Path.of("").toAbsolutePath().normalize().getParent().getParent();
    private static final Path ENV_ROOT = Path.of(System.getProperty("ckqa.smoke.envRoot", WORKTREE_ROOT.toString()));
    private static final Map<String, String> LOCAL_ENV = loadLocalEnv();

    @Autowired
    private CourseRoutingService courseRoutingService;

    @Autowired
    private CourseRouteProfilesService profilesService;

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
        registry.add("GRAPHRAG_EMBEDDING_MODEL", () -> EMBEDDING_MODEL);
        registry.add("GRAPHRAG_EMBEDDING_DIMENSIONS", () -> String.valueOf(EMBEDDING_DIMENSIONS));
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
    void shouldBuildOnlineEmbeddingProfileForCurrentOperatingSystemCourse() throws InterruptedException {
        waitForCourseRoutingReadiness();

        CourseRoutingRecommendRequest request = new CourseRoutingRecommendRequest();
        request.setQuestion("什么是进程");
        request.setLimit(3);

        var response = courseRoutingService.recommend(
                request,
                new AuthenticatedUser(2L, "STU2026001", "student.zhouzh", "周子涵", List.of("student"), List.of())
        );

        assertThat(response.getCandidates())
                .extracting("courseId")
                .contains(OS_COURSE_ID);

        var profile = profilesService.findActiveByCourseAndModel(
                OS_COURSE_ID,
                EMBEDDING_MODEL,
                EMBEDDING_DIMENSIONS
        );
        assertThat(profile).isPresent();
        assertThat(profile.get().getProfileHash()).hasSize(64);
        assertThat(profile.get().getVectorId()).contains(OS_COURSE_ID).contains("text_embedding_v4");
        assertThat(profile.get().getLancedbTable()).isEqualTo("course_profiles_text_embedding_v4");
        assertThat(profile.get().getProfileText()).contains("操作系统2026春", "进程", "知识库");
        assertThat(profile.get().getProfileText()).contains("TLB", "快表", "I/O", "设备驱动程序", "中断", "轮询");
        System.out.printf(
                "course-routing-online-smoke courseId=%s status=%s selected=%s confidence=%.6f profileHash=%s vectorId=%s table=%s candidates=%s%n",
                OS_COURSE_ID,
                response.getStatus(),
                response.getSelectedCourseId(),
                response.getConfidence(),
                profile.get().getProfileHash(),
                profile.get().getVectorId(),
                profile.get().getLancedbTable(),
                response.getCandidates().stream()
                        .map(candidate -> candidate.getCourseId() + ":" + String.format("%.6f", candidate.getConfidence()))
                        .collect(Collectors.joining(","))
        );
        assertThat(response.getStatus()).isEqualTo("matched");
        assertThat(response.getSelectedCourseId()).isEqualTo(OS_COURSE_ID);
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
            throw new IllegalStateException("读取本地 smoke env 失败: " + path, exception);
        }
    }

    private static String required(String key) {
        String value = LOCAL_ENV.get(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少本地 smoke 配置: " + key);
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
}
