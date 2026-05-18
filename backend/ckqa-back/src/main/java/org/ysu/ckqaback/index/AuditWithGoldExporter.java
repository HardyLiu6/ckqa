package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 build run 的 audit 样本（含 DB 中的 gold_entities / gold_relations）
 * 序列化成 {@code generate_candidate_prompts.py} 期望的 JSON 形态。
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li><b>合并</b>：02 步落盘的 audit JSON 中 gold 字段全空，本类把 DB 中的真实 gold
 *       注入回 JSON 让脚本能用</li>
 *   <li><b>字段归一化</b>：DB 用 camelCase（id/sourceEntityId/evidence），脚本用
 *       snake_case（entity_id/source_entity_id/evidence_text），必须做命名转换。
 *       转换规则见 {@link #normalizeEntity} 和 {@link #normalizeRelation}</li>
 *   <li><b>id 生成</b>：DB entity 缺失 id 时（早期数据），按 sample id + 索引生成稳定 id
 *       {@code auto_e_<sampleId>_<idx>}，避免脚本 entity_by_id map miss</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class AuditWithGoldExporter {

    private static final Logger log = LoggerFactory.getLogger(AuditWithGoldExporter.class);

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public void export(List<PromptTuneAuditSamples> samples, Path outputFile) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> auditSamples = new ArrayList<>();
        for (PromptTuneAuditSamples s : samples) {
            auditSamples.add(toJsonForm(s));
        }
        root.put("audit_samples", auditSamples);

        Path parent = outputFile.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(
                outputFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8
        );
    }

    private Map<String, Object> toJsonForm(PromptTuneAuditSamples s) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("source_sample_id", s.getSourceSampleId());
        json.put("text", s.getText());
        json.put("heading_path", parseHeadingPath(s.getHeadingPath()));
        json.put("page_start", s.getPageStart() != null ? s.getPageStart() : 0);
        json.put("page_end", s.getPageEnd() != null ? s.getPageEnd() : 0);
        json.put("document_type", s.getDocumentType());
        json.put("audit_priority", s.getAuditPriority());
        json.put("audit_reason", s.getAuditReason());

        // 关键：归一化字段名
        List<Map<String, Object>> rawEntities = parseJsonArrayOrEmpty(s.getGoldEntities(), s.getId(), "gold_entities");
        List<Map<String, Object>> rawRelations = parseJsonArrayOrEmpty(s.getGoldRelations(), s.getId(), "gold_relations");
        List<Map<String, Object>> normalizedEntities = new ArrayList<>(rawEntities.size());
        for (int i = 0; i < rawEntities.size(); i++) {
            normalizedEntities.add(normalizeEntity(rawEntities.get(i), s.getId(), i));
        }
        List<Map<String, Object>> normalizedRelations = new ArrayList<>(rawRelations.size());
        for (Map<String, Object> r : rawRelations) {
            normalizedRelations.add(normalizeRelation(r));
        }
        json.put("gold_entities", normalizedEntities);
        json.put("gold_relations", normalizedRelations);

        json.put("annotation_notes", s.getAnnotationNotes() != null ? s.getAnnotationNotes() : "");
        json.put("reviewer_decision", s.getReviewerDecision() != null ? s.getReviewerDecision() : "");
        return json;
    }

    /**
     * Entity 字段归一化：
     * <ul>
     *   <li>{@code id} → {@code entity_id}（缺失时生成 {@code auto_e_<sampleId>_<idx>}）</li>
     *   <li>其它字段（name/type/description/source/spanStart/spanEnd）原样保留</li>
     * </ul>
     */
    private Map<String, Object> normalizeEntity(Map<String, Object> raw, Long sampleId, int index) {
        Map<String, Object> copy = new LinkedHashMap<>(raw);
        Object dbId = copy.remove("id");
        String entityId = dbId instanceof String s && !s.isBlank()
                ? s
                : "auto_e_" + sampleId + "_" + index;
        copy.put("entity_id", entityId);
        return copy;
    }

    /**
     * Relation 字段归一化：
     * <ul>
     *   <li>{@code sourceEntityId} → {@code source_entity_id}</li>
     *   <li>{@code targetEntityId} → {@code target_entity_id}</li>
     *   <li>{@code evidence} → {@code evidence_text}（脚本通过此字段渲染 fewshot）</li>
     *   <li>{@code id} 字段去掉（脚本不需要 relation id）</li>
     *   <li>其它字段（type/description/source）原样保留</li>
     * </ul>
     */
    private Map<String, Object> normalizeRelation(Map<String, Object> raw) {
        Map<String, Object> copy = new LinkedHashMap<>(raw);
        copy.remove("id");
        Object src = copy.remove("sourceEntityId");
        Object tgt = copy.remove("targetEntityId");
        Object evidence = copy.remove("evidence");
        if (src != null) copy.put("source_entity_id", src);
        if (tgt != null) copy.put("target_entity_id", tgt);
        if (evidence != null) copy.put("evidence_text", evidence);
        return copy;
    }

    private static List<String> parseHeadingPath(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(" > "));
    }

    private List<Map<String, Object>> parseJsonArrayOrEmpty(String raw, Long sampleId, String fieldName) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<Map<String, Object>> result = objectMapper.readValue(raw, LIST_MAP_TYPE);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("解析 sample {} {} JSON 失败，降级为空列表：{}", sampleId, fieldName, e.getMessage());
            return List.of();
        }
    }
}
