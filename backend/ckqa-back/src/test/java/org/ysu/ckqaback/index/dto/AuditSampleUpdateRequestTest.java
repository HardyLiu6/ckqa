package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSampleUpdateRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jacksonDistinguishesMissingFieldFromExplicitNull() throws Exception {
        AuditSampleUpdateRequest missing = mapper.readValue("{}", AuditSampleUpdateRequest.class);
        assertThat(missing.hasField("skipReason")).isFalse();
        assertThat(missing.hasField("reviewerDecision")).isFalse();

        AuditSampleUpdateRequest explicitNull =
                mapper.readValue("{\"skipReason\":null}", AuditSampleUpdateRequest.class);
        assertThat(explicitNull.hasField("skipReason")).isTrue();
        assertThat(explicitNull.getSkipReason()).isNull();

        AuditSampleUpdateRequest value =
                mapper.readValue("{\"skipReason\":\"no_relevant\"}", AuditSampleUpdateRequest.class);
        assertThat(value.hasField("skipReason")).isTrue();
        assertThat(value.getSkipReason()).isEqualTo("no_relevant");
    }

    @Test
    void multipleFieldsMixedPresence() throws Exception {
        String json = "{\"reviewerDecision\":\"completed\",\"reviewerConfidence\":0.9}";
        AuditSampleUpdateRequest req = mapper.readValue(json, AuditSampleUpdateRequest.class);

        assertThat(req.hasField("reviewerDecision")).isTrue();
        assertThat(req.getReviewerDecision()).isEqualTo("completed");
        assertThat(req.hasField("reviewerConfidence")).isTrue();
        assertThat(req.hasField("goldEntities")).isFalse();
        assertThat(req.hasField("skipReason")).isFalse();
    }

    @Test
    void unknownFieldsAreRecordedButIgnored() throws Exception {
        String json = "{\"unknownField\":123,\"reviewerDecision\":\"pending\"}";
        AuditSampleUpdateRequest req = mapper.readValue(json, AuditSampleUpdateRequest.class);

        assertThat(req.hasField("reviewerDecision")).isTrue();
        // unknownField is recorded in presentFields but hasField only recognizes KNOWN_FIELDS
        assertThat(req.hasField("unknownField")).isFalse();
    }
}
