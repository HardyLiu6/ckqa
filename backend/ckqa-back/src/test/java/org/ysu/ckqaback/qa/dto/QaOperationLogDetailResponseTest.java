package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaOperationLogDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeTopicEntityBindingDiagnosticsWithoutUnsafeFields() throws Exception {
        QaOperationLogRow row = new QaOperationLogRow();
        row.setTopicEntityBindingApplied(true);
        row.setTopicEntityBindingStatus("success");
        row.setTopicEntityBindingStrategy("active_neo4j_topic_match");
        row.setTopicEntityCandidateCount(1);
        row.setTopicEntityTopScore(1.0D);
        row.setTopicEntitySelectedId("entity-deadlock");
        row.setTopicEntitySelectedName("死锁");
        row.setTopicEntitySelectedType("concept");
        row.setTopicEntityCandidatesJson("""
                [{"id":"entity-deadlock","name":"死锁","type":"concept","humanReadableId":"E-42","score":1.0,"matchReason":"exact_name","source":"active_neo4j"}]
                """);
        row.setTopicEntityFallbackReason(null);
        row.setTopicEntityLookupDurationMs(12L);

        QaOperationLogDetailResponse response = QaOperationLogDetailResponse.of(row, List.of(), List.of());

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("topicEntityBindingApplied").asBoolean()).isTrue();
        assertThat(root.get("topicEntityBindingStatus").asText()).isEqualTo("success");
        assertThat(root.get("topicEntityBindingStrategy").asText()).isEqualTo("active_neo4j_topic_match");
        assertThat(root.get("topicEntityCandidateCount").asInt()).isEqualTo(1);
        assertThat(root.get("topicEntitySelectedId").asText()).isEqualTo("entity-deadlock");
        assertThat(root.get("topicEntitySelectedName").asText()).isEqualTo("死锁");
        assertThat(root.get("topicEntitySelectedType").asText()).isEqualTo("concept");
        assertThat(root.get("topicEntityCandidatesJson").asText()).contains("\"id\":\"entity-deadlock\"");
        assertThat(root.get("topicEntityCandidatesJson").asText()).contains("\"source\":\"active_neo4j\"");
        assertThat(json).doesNotContain("description", "snippet", "memoryText", "full_content");
    }

    @Test
    void shouldSerializeMemoryGovernanceDiagnosticsWithoutRawMemoryHistory() throws Exception {
        QaOperationLogRow row = new QaOperationLogRow();
        row.setMemoryApplied(true);
        row.setMemoryStrategy("local_history_preference_only");
        row.setMemorySourceCount(3);
        row.setMemorySizeChars(128);
        row.setMemoryGovernanceVersion("memory-governance-v1");
        row.setMemoryLongTermCount(1);
        row.setMemoryRecentHistoryCount(2);
        row.setMemoryInjectionReason("preference_enabled:auto");
        row.setMemorySourcesJson("""
                [{"memoryId":101,"memoryType":"explanation_preference","sourceSessionId":5,"sourceMessageId":88,"includeReason":"preference_enabled:auto","textHash":"abc12345","textChars":14}]
                """);

        QaOperationLogDetailResponse response = QaOperationLogDetailResponse.of(row, List.of(), List.of());

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("memoryLongTermCount").asInt()).isEqualTo(1);
        assertThat(root.get("memoryRecentHistoryCount").asInt()).isEqualTo(2);
        assertThat(root.get("memoryGovernanceVersion").asText()).isEqualTo("memory-governance-v1");
        assertThat(root.get("memorySourcesJson").asText()).contains("\"textHash\":\"abc12345\"");
        assertThat(json).doesNotContain("memoryHistoryJson", "conversationHistory", "memoryText", "学生偏好");
    }
}
