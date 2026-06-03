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
}
