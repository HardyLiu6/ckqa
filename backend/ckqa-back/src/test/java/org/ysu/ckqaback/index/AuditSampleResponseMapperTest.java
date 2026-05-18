package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSampleResponseMapperTest {

    private AuditSampleResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AuditSampleResponseMapper(new ObjectMapper());
    }

    @Test
    void parsesJsonArrayFieldsIntoStructuredList() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities("[{\"name\":\"进程\",\"type\":\"Concept\"},{\"name\":\"线程\",\"type\":\"Concept\"}]");
        entity.setGoldRelations("[{\"source\":\"进程\",\"target\":\"线程\",\"type\":\"contains\"}]");
        entity.setHitSignals("[\"definition_signal\",\"formula_signal\"]");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).hasSize(2);
        assertThat(response.getGoldEntities().get(0)).containsEntry("name", "进程");
        assertThat(response.getGoldEntities().get(1)).containsEntry("type", "Concept");
        assertThat(response.getGoldRelations()).hasSize(1);
        assertThat(response.getGoldRelations().get(0)).containsEntry("source", "进程");
        assertThat(response.getHitSignals()).containsExactly("definition_signal", "formula_signal");
    }

    @Test
    void emptyJsonFieldsBecomeEmptyLists() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities(null);
        entity.setGoldRelations("");
        entity.setHitSignals("[]");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).isEmpty();
        assertThat(response.getGoldRelations()).isEmpty();
        assertThat(response.getHitSignals()).isEmpty();
    }

    @Test
    void malformedJsonFieldsFallbackToEmptyList() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setGoldEntities("not a json");
        entity.setGoldRelations("{broken");
        entity.setHitSignals("definitely not json [");

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getGoldEntities()).isEmpty();
        assertThat(response.getGoldRelations()).isEmpty();
        assertThat(response.getHitSignals()).isEmpty();
    }

    @Test
    void reusedFromIsNullWhenReusedFromBuildRunIdAbsent() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReusedFromBuildRunId(null);
        entity.setHitSignals(null);
        entity.setGoldEntities(null);
        entity.setGoldRelations(null);

        AuditSampleResponse response = mapper.toResponse(entity, "some-build-name");

        assertThat(response.getReusedFrom()).isNull();
    }

    @Test
    void reusedFromIsPopulatedWhenReusedFromBuildRunIdPresent() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReusedFromBuildRunId(99L);
        entity.setHitSignals(null);
        entity.setGoldEntities(null);
        entity.setGoldRelations(null);
        LocalDateTime createdAt = LocalDateTime.of(2025, 6, 1, 10, 30, 0);
        entity.setCreatedAt(createdAt);

        AuditSampleResponse response = mapper.toResponse(entity, "第3次构建");

        assertThat(response.getReusedFrom()).isNotNull();
        assertThat(response.getReusedFrom().getBuildRunId()).isEqualTo(99L);
        assertThat(response.getReusedFrom().getBuildRunName()).isEqualTo("第3次构建");
        assertThat(response.getReusedFrom().getReusedAt()).isEqualTo(createdAt);
    }

    @Test
    void reviewerConfidenceAndDecisionPassThrough() {
        PromptTuneAuditSamples entity = newSampleEntity();
        entity.setReviewerDecision("completed");
        entity.setReviewerConfidence(new BigDecimal("0.95"));
        entity.setHitSignals(null);
        entity.setGoldEntities(null);
        entity.setGoldRelations(null);

        AuditSampleResponse response = mapper.toResponse(entity, null);

        assertThat(response.getReviewerDecision()).isEqualTo("completed");
        assertThat(response.getReviewerConfidence()).isEqualByComparingTo(new BigDecimal("0.95"));
    }

    private PromptTuneAuditSamples newSampleEntity() {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(1L);
        e.setBuildRunId(10L);
        e.setKnowledgeBaseId(100L);
        e.setSourceSampleId("sample-os-2-1");
        e.setText("进程是程序的一次执行过程。");
        e.setHeadingPath("第二章 进程管理 > 2.1 进程的定义");
        e.setPageStart(34);
        e.setPageEnd(34);
        e.setDocumentType("textbook");
        e.setAuditPriority("high");
        e.setAuditReason("覆盖 Concept + FormulaOrDefinition");
        e.setReviewerDecision("pending");
        e.setGoldStableKey("doc1|34|34|abc123");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
