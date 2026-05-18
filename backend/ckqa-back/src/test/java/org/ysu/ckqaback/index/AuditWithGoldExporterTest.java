package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditWithGoldExporterTest {

    private final AuditWithGoldExporter exporter = new AuditWithGoldExporter(new ObjectMapper());

    @Test
    void normalizesEntityFieldsForScriptConsumption(@TempDir Path tmp) throws Exception {
        // DB 真实形态：camelCase + entity 用 id 而非 entity_id
        // 脚本期望：snake_case + entity 用 entity_id
        // exporter 必须做字段归一化
        String dbGoldEntitiesJson = """
                [
                  {"id": "e1", "name": "进程", "type": "Concept", "description": "执行单位", "spanStart": 0, "spanEnd": 2, "source": "manual"},
                  {"id": "e2", "name": "线程", "type": "Concept", "description": "轻量进程", "source": "accepted"}
                ]
                """;
        String dbGoldRelationsJson = """
                [
                  {"id": "r1", "sourceEntityId": "e1", "targetEntityId": "e2", "type": "contains", "evidence": "进程包含线程", "description": "进程是容器", "source": "accepted"}
                ]
                """;
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", dbGoldEntitiesJson, dbGoldRelationsJson);

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldEntities = (List<Map<String, Object>>) samples.get(0).get("gold_entities");
        assertThat(goldEntities).hasSize(2);
        // entity_id 必须存在（脚本通过它建 entity_by_id map）
        assertThat(goldEntities.get(0)).containsEntry("entity_id", "e1");
        assertThat(goldEntities.get(0)).containsEntry("name", "进程");
        assertThat(goldEntities.get(0)).containsEntry("type", "Concept");
        assertThat(goldEntities.get(1)).containsEntry("entity_id", "e2");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldRelations = (List<Map<String, Object>>) samples.get(0).get("gold_relations");
        assertThat(goldRelations).hasSize(1);
        // source_entity_id / target_entity_id / evidence_text 必须存在
        assertThat(goldRelations.get(0)).containsEntry("source_entity_id", "e1");
        assertThat(goldRelations.get(0)).containsEntry("target_entity_id", "e2");
        assertThat(goldRelations.get(0)).containsEntry("type", "contains");
        assertThat(goldRelations.get(0)).containsEntry("evidence_text", "进程包含线程");
    }

    @Test
    void generatesEntityIdWhenDbIdMissing(@TempDir Path tmp) throws Exception {
        // 边界：entity 在 DB 中无 id（早期数据 / 数据损坏）
        // exporter 必须用稳定方式生成 entity_id（基于 sample id + 索引），保证 fewshot 选择一致性
        String dbGoldJson = """
                [
                  {"name": "X", "type": "Concept"},
                  {"name": "Y", "type": "Concept"}
                ]
                """;
        PromptTuneAuditSamples sample = newSample(7L, "sample-007", "text", dbGoldJson, "[]");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldEntities = (List<Map<String, Object>>) samples.get(0).get("gold_entities");

        // 期望基于 sample.id + 索引生成稳定 id
        assertThat(goldEntities.get(0)).containsEntry("entity_id", "auto_e_7_0");
        assertThat(goldEntities.get(1)).containsEntry("entity_id", "auto_e_7_1");
    }

    @Test
    void preservesHeadingPathAsArray(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", "[]", "[]");
        sample.setHeadingPath("第二章 进程的描述与控制 > 2.1 进程的概念");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        assertThat(samples.get(0).get("heading_path")).isEqualTo(
                List.of("第二章 进程的描述与控制", "2.1 进程的概念")
        );
    }

    @Test
    void writesEmptyAuditSamplesArrayWhenInputEmpty(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        assertThat(root.get("audit_samples")).isEqualTo(List.of());
    }

    @Test
    void preservesPageRangeAndPriority(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", "[]", "[]");
        sample.setPageStart(34);
        sample.setPageEnd(35);
        sample.setAuditPriority("high");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        assertThat(samples.get(0).get("page_start")).isEqualTo(34);
        assertThat(samples.get(0).get("page_end")).isEqualTo(35);
        assertThat(samples.get(0).get("audit_priority")).isEqualTo("high");
    }

    @Test
    void recoversFromMalformedGoldJsonAsEmpty(@TempDir Path tmp) throws Exception {
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", "{ malformed", "[]");

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        assertThat(samples.get(0).get("gold_entities")).isEqualTo(List.of());
    }

    @Test
    void skipsRelationsReferencingMissingEntityIds(@TempDir Path tmp) throws Exception {
        // 边界：relation 引用了不存在的 entity（数据不一致），仍写出 relation 让脚本自己跳过
        // 不要在 Java 层做 cascade 过滤，避免引入隐藏行为；脚本的 _select_fewshot_gold_items
        // 会在 entity_by_id map miss 时跳过这条 relation
        String entitiesJson = "[{\"id\":\"e1\",\"name\":\"进程\",\"type\":\"Concept\"}]";
        String relationsJson = """
                [
                  {"id": "r1", "sourceEntityId": "e1", "targetEntityId": "e_missing", "type": "contains"}
                ]
                """;
        PromptTuneAuditSamples sample = newSample(1L, "sample-001", "text", entitiesJson, relationsJson);

        Path output = tmp.resolve("audit_with_gold.json");
        exporter.export(List.of(sample), output);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> root = mapper.readValue(Files.readString(output), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("audit_samples");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goldRelations = (List<Map<String, Object>>) samples.get(0).get("gold_relations");
        assertThat(goldRelations).hasSize(1);
        assertThat(goldRelations.get(0)).containsEntry("target_entity_id", "e_missing");
    }

    private static PromptTuneAuditSamples newSample(
            Long id, String sourceSampleId, String text, String goldEntitiesJson, String goldRelationsJson
    ) {
        PromptTuneAuditSamples e = new PromptTuneAuditSamples();
        e.setId(id);
        e.setSourceSampleId(sourceSampleId);
        e.setText(text);
        e.setHeadingPath("第一章 引论");
        e.setPageStart(1);
        e.setPageEnd(1);
        e.setAuditPriority("medium");
        e.setAuditReason("test reason");
        e.setGoldEntities(goldEntitiesJson);
        e.setGoldRelations(goldRelationsJson);
        e.setReviewerDecision("completed");
        return e;
    }
}
