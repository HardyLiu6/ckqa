package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesAllFields() throws Exception {
        CandidateResponse response = CandidateResponse.builder()
                .candidateId("default")
                .displayNameZh("默认基线")
                .category("baseline")
                .description("基线 · 课程域微调")
                .isRecommended(false)
                .traits(List.of(
                        CandidateResponse.TraitInfo.builder().key("baseline").label("课程基线").build(),
                        CandidateResponse.TraitInfo.builder().key("no_schema").label("无 schema 注入").build()
                ))
                .estimatedTokenPerCall(3000)
                .promptSizeBytes(2300)
                .schemaUsed(false)
                .fewshotExampleCount(0)
                .fewshotStrategy(null)
                .basePromptSource("default_adapted")
                .build();

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"candidateId\":\"default\"");
        assertThat(json).contains("\"displayNameZh\":\"默认基线\"");
        assertThat(json).contains("\"description\":\"基线 · 课程域微调\"");
        assertThat(json).contains("\"key\":\"baseline\"");
        assertThat(json).contains("\"label\":\"课程基线\"");
    }
}
