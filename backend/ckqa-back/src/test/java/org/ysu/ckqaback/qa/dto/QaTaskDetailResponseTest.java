package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QaTaskDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNotSerializeInternalMemoryOrSemanticStateFieldsToStudentTaskDetail() throws Exception {
        QaTaskDetailResponse response = QaTaskDetailResponse.of(
                9001L,
                101L,
                null,
                "running",
                "streaming",
                "running",
                "local",
                "local",
                "local",
                "什么是死锁？",
                List.of("已选取课程片段"),
                List.of(QaProgressEventResponse.of(
                        "context_selected",
                        "local",
                        "已选取课程片段。",
                        Map.of(
                                "textUnitCount", 2,
                                "memoryHistoryJson", "[{\"content\":\"长期记忆原文\"}]",
                                "conversationHistory", List.of(Map.of("content", "会话历史原文"))
                        ),
                        List.of(Map.of(
                                "kind", "text_unit",
                                "title", "操作系统教材",
                                "memoryText", "学习记忆正文",
                                "nested", Map.of("contextSnapshotText", "上下文快照原文")
                        )),
                        12L
                )),
                null,
                null,
                null,
                null,
                null,
                10L,
                300L,
                "任务心跳超时",
                true,
                "recent",
                ContextSizeEstimateResponse.of(32),
                true,
                "local_history_preference_only",
                "userId=7;courseId=os;knowledgeBaseId=3;indexRunId=17",
                3,
                128,
                null,
                12L
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("queryText")).isFalse();
        assertThat(root.get("progressEvents").get(0).get("metrics").get("textUnitCount").asInt()).isEqualTo(2);
        assertThat(root.get("progressEvents").get(0).get("evidence").get(0).get("title").asText()).isEqualTo("操作系统教材");
        assertThat(json).doesNotContain(
                "memoryHistoryJson",
                "memory_history_json",
                "conversationHistory",
                "conversation_history",
                "memoryText",
                "memory_text",
                "contextSnapshotText",
                "context_snapshot_text",
                "semanticStateJson",
                "semantic_state_json",
                "长期记忆原文",
                "会话历史原文",
                "学习记忆正文",
                "上下文快照原文"
        );
    }
}
