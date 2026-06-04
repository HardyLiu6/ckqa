package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.qa.context.BudgetSizeEstimate;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSizeEstimateResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepLegacyCharsJsonCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(ContextSizeEstimateResponse.of(128));
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("chars").asInt()).isEqualTo(128);
        assertThat(root.has("tokens")).isTrue();
        assertThat(root.get("tokens").isNull()).isTrue();
        assertThat(root.get("tokenizer").isNull()).isTrue();
        assertThat(root.get("fallbackReason").isNull()).isTrue();
    }

    @Test
    void shouldSerializeTokenEstimateFields() throws Exception {
        ContextSizeEstimateResponse response = ContextSizeEstimateResponse.of(
                new BudgetSizeEstimate(42, 9, "jtokkit:o200k_base", null)
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("chars").asInt()).isEqualTo(42);
        assertThat(root.get("tokens").asInt()).isEqualTo(9);
        assertThat(root.get("tokenizer").asText()).isEqualTo("jtokkit:o200k_base");
        assertThat(root.get("fallbackReason").isNull()).isTrue();
    }
}
