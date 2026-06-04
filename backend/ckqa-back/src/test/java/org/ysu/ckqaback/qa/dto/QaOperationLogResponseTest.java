package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;

import static org.assertj.core.api.Assertions.assertThat;

class QaOperationLogResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeResolvedTopicForSmokeDiagnosticsWithoutRawContext() throws Exception {
        QaOperationLogRow row = new QaOperationLogRow();
        row.setResolvedTopic("死锁");
        row.setTopicSource("history");

        QaOperationLogResponse response = QaOperationLogResponse.fromRow(row);

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("resolvedTopic").asText()).isEqualTo("死锁");
        assertThat(root.get("topicSource").asText()).isEqualTo("history");
        assertThat(json).doesNotContain("contextSnapshotText", "conversationHistory", "memoryText");
    }
}
