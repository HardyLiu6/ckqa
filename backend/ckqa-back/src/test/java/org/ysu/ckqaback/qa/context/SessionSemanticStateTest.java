package org.ysu.ckqaback.qa.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSemanticStateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeStableSemanticStateWithComparisonRoles() throws Exception {
        QaTopicStack topicStack = QaTopicStack.of(
                "饥饿",
                "3-4",
                "comparison_pronoun",
                0.86D,
                List.of("死锁", "饥饿"),
                List.of("死锁", "饥饿")
        );
        QaContextSummary summary = new QaContextSummary(
                "本会话已比较死锁与饥饿。",
                4,
                "饥饿",
                "3-4",
                "[{\"topic\":\"死锁\",\"role\":\"former\"},{\"topic\":\"饥饿\",\"role\":\"latter\"}]"
        );

        SessionSemanticState state = SessionSemanticState.from(topicStack, summary);

        assertThat(state.version()).isEqualTo(SessionSemanticState.VERSION);
        JsonNode json = objectMapper.readTree(state.json());
        assertThat(json.get("version").asText()).isEqualTo(SessionSemanticState.VERSION);
        assertThat(json.get("latestTopic").asText()).isEqualTo("饥饿");
        assertThat(json.get("latestTopicMessageRange").asText()).isEqualTo("3-4");
        assertThat(json.get("topicSource").asText()).isEqualTo("comparison_pronoun");
        assertThat(json.get("topicConfidence").asDouble()).isEqualTo(0.86D);
        assertThat(json.get("restoredFromSummary").asBoolean()).isTrue();
        assertThat(json.get("summaryUntilSequenceNo").asInt()).isEqualTo(4);
        assertThat(json.get("activeTopics")).hasSize(2);
        assertThat(json.get("comparisonTopics").get(0).get("topic").asText()).isEqualTo("死锁");
        assertThat(json.get("comparisonTopics").get(0).get("role").asText()).isEqualTo("former");
        assertThat(json.get("comparisonTopics").get(1).get("topic").asText()).isEqualTo("饥饿");
        assertThat(json.get("comparisonTopics").get(1).get("role").asText()).isEqualTo("latter");
    }

    @Test
    void shouldFallbackToTopicFieldsWhenTopicStackJsonIsInvalid() throws Exception {
        SessionSemanticState state = SessionSemanticState.fromTopicFields(
                "死锁",
                "1-2",
                "history",
                0.75D,
                "{broken"
        );

        JsonNode json = objectMapper.readTree(state.json());
        assertThat(json.get("latestTopic").asText()).isEqualTo("死锁");
        assertThat(json.get("activeTopics").get(0).get("topic").asText()).isEqualTo("死锁");
        assertThat(json.get("comparisonTopics")).isEmpty();
    }

    @Test
    void legacyContextAssemblyConstructorShouldDeriveSemanticStateFromTopicStackJson() throws Exception {
        QaContextAssembly assembly = new QaContextAssembly(
                "recent",
                "学生：死锁和饥饿有什么区别？",
                "1-2",
                14,
                "饥饿",
                "1-2",
                "history",
                0.86D,
                "[{\"topic\":\"死锁\",\"role\":\"former\"},{\"topic\":\"饥饿\",\"role\":\"latter\"}]"
        );

        JsonNode json = objectMapper.readTree(assembly.semanticStateJson());
        assertThat(assembly.semanticStateVersion()).isEqualTo(SessionSemanticState.VERSION);
        assertThat(json.get("latestTopic").asText()).isEqualTo("饥饿");
        assertThat(json.get("comparisonTopics").get(0).get("role").asText()).isEqualTo("former");
        assertThat(json.get("comparisonTopics").get(1).get("role").asText()).isEqualTo("latter");
    }
}
