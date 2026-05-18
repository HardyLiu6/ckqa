package org.ysu.ckqaback.qa.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QaModeRoutingEvaluationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldPassOfflineRoutingEvaluationSet() throws Exception {
        QaModeRoutingService service = new QaModeRoutingService(
                mock(QaSessionsService.class),
                mock(KnowledgeBasesService.class)
        );
        List<RoutingCase> cases = loadCases();
        List<String> exactMisses = new ArrayList<>();
        List<String> acceptableMisses = new ArrayList<>();

        for (RoutingCase item : cases) {
            QaModeRecommendationRequest request = new QaModeRecommendationRequest();
            request.setQuestion(item.question());
            request.setBetaHybridEnabled(item.betaHybridEnabled());
            request.setHasConversationContext(item.hasConversationContext());
            String actual = service.recommend(request, student()).getRecommendedMode();
            if (!item.expectedMode().equals(actual)) {
                exactMisses.add(item.id() + ": expected=" + item.expectedMode() + ", actual=" + actual);
            }
            if (!item.acceptableModes().contains(actual)) {
                acceptableMisses.add(item.id() + ": acceptable=" + item.acceptableModes() + ", actual=" + actual);
            }
        }

        double exactRate = (cases.size() - exactMisses.size()) / (double) cases.size();
        double acceptableRate = (cases.size() - acceptableMisses.size()) / (double) cases.size();
        assertThat(acceptableMisses)
                .as("acceptable route misses: exactRate=" + exactRate + ", acceptableRate=" + acceptableRate)
                .isEmpty();
        assertThat(exactRate)
                .as("exact route misses: " + exactMisses)
                .isGreaterThanOrEqualTo(0.90D);
    }

    private List<RoutingCase> loadCases() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream("qa-routing-eval-set.jsonl");
        assertThat(resource).isNotNull();
        List<RoutingCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> raw = OBJECT_MAPPER.readValue(line, new TypeReference<>() {});
                cases.add(new RoutingCase(
                        (String) raw.get("id"),
                        (String) raw.get("question"),
                        (String) raw.get("expectedMode"),
                        castStringList(raw.get("acceptableModes")),
                        Boolean.TRUE.equals(raw.get("betaHybridEnabled")),
                        Boolean.TRUE.equals(raw.get("hasConversationContext"))
                ));
            }
        }
        return cases;
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return value instanceof List<?> ? (List<String>) value : List.of();
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }

    private record RoutingCase(
            String id,
            String question,
            String expectedMode,
            List<String> acceptableModes,
            boolean betaHybridEnabled,
            boolean hasConversationContext
    ) {
    }
}
