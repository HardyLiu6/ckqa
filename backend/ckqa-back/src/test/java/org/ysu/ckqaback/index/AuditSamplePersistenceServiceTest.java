package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditSamplePersistenceServiceTest {

    private PromptTuneAuditSamplesService samplesStore;
    private ObjectMapper objectMapper;
    private AuditSamplePersistenceService service;

    @BeforeEach
    void setUp() {
        samplesStore = mock(PromptTuneAuditSamplesService.class);
        objectMapper = new ObjectMapper();
        service = new AuditSamplePersistenceService(samplesStore, objectMapper);
    }

    // ─── mergeReusedAnnotations 测试 ───────────────────────────────────────────

    @Test
    void mergeReusedAnnotationsCopiesGoldFieldsWhenStableKeyHits() {
        // 准备：当前样本有 goldStableKey，历史库中有已完成的匹配记录
        PromptTuneAuditSamples current = new PromptTuneAuditSamples();
        current.setGoldStableKey("stable-abc");
        current.setBuildRunId(200L);

        PromptTuneAuditSamples historical = new PromptTuneAuditSamples();
        historical.setGoldStableKey("stable-abc");
        historical.setBuildRunId(100L);
        historical.setGoldEntities("[{\"type\":\"Person\"}]");
        historical.setGoldRelations("[{\"rel\":\"knows\"}]");
        historical.setAnnotationNotes("历史标注备注");
        historical.setReviewerDecision("completed");
        historical.setReviewerConfidence(new java.math.BigDecimal("0.95"));
        historical.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));

        when(samplesStore.findCompletedByStableKeys(eq(1L), anyList()))
                .thenReturn(List.of(historical));

        List<PromptTuneAuditSamples> samples = new ArrayList<>(List.of(current));

        // 执行
        service.mergeReusedAnnotations(1L, 200L, samples);

        // 验证：gold 字段被复制
        assertThat(current.getGoldEntities()).isEqualTo("[{\"type\":\"Person\"}]");
        assertThat(current.getGoldRelations()).isEqualTo("[{\"rel\":\"knows\"}]");
        assertThat(current.getAnnotationNotes()).isEqualTo("历史标注备注");
        assertThat(current.getReviewerDecision()).isEqualTo("completed");
        assertThat(current.getReviewerConfidence()).isEqualByComparingTo("0.95");
        assertThat(current.getReusedFromBuildRunId()).isEqualTo(100L);
    }

    @Test
    void mergeReusedAnnotationsIgnoresHistoryFromSameBuildRun() {
        // 准备：历史记录的 buildRunId 与当前相同，应被排除
        PromptTuneAuditSamples current = new PromptTuneAuditSamples();
        current.setGoldStableKey("stable-xyz");
        current.setBuildRunId(200L);

        PromptTuneAuditSamples sameRunHistory = new PromptTuneAuditSamples();
        sameRunHistory.setGoldStableKey("stable-xyz");
        sameRunHistory.setBuildRunId(200L); // 同一 buildRunId
        sameRunHistory.setGoldEntities("[{\"type\":\"Org\"}]");
        sameRunHistory.setReviewerDecision("completed");
        sameRunHistory.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));

        when(samplesStore.findCompletedByStableKeys(eq(1L), anyList()))
                .thenReturn(List.of(sameRunHistory));

        List<PromptTuneAuditSamples> samples = new ArrayList<>(List.of(current));

        // 执行
        service.mergeReusedAnnotations(1L, 200L, samples);

        // 验证：不应复制任何字段
        assertThat(current.getGoldEntities()).isNull();
        assertThat(current.getReusedFromBuildRunId()).isNull();
    }

    @Test
    void mergeReusedAnnotationsLeavesNewSampleWhenNoHistoryHit() {
        // 准备：历史库中没有匹配的 stableKey
        PromptTuneAuditSamples current = new PromptTuneAuditSamples();
        current.setGoldStableKey("stable-no-match");
        current.setBuildRunId(200L);

        when(samplesStore.findCompletedByStableKeys(eq(1L), anyList()))
                .thenReturn(List.of()); // 无匹配

        List<PromptTuneAuditSamples> samples = new ArrayList<>(List.of(current));

        // 执行
        service.mergeReusedAnnotations(1L, 200L, samples);

        // 验证：样本保持原样
        assertThat(current.getGoldEntities()).isNull();
        assertThat(current.getGoldRelations()).isNull();
        assertThat(current.getReusedFromBuildRunId()).isNull();
    }

    @Test
    void mergeReusedAnnotationsSkipsSamplesWithoutStableKey() {
        // 准备：样本没有 goldStableKey
        PromptTuneAuditSamples noKey = new PromptTuneAuditSamples();
        noKey.setBuildRunId(200L);
        noKey.setGoldStableKey(null);

        PromptTuneAuditSamples blankKey = new PromptTuneAuditSamples();
        blankKey.setBuildRunId(200L);
        blankKey.setGoldStableKey("   ");

        List<PromptTuneAuditSamples> samples = new ArrayList<>(List.of(noKey, blankKey));

        // 执行
        service.mergeReusedAnnotations(1L, 200L, samples);

        // 验证：不应调用 findCompletedByStableKeys（因为没有有效 stableKey）
        verify(samplesStore, never()).findCompletedByStableKeys(anyLong(), anyList());
        assertThat(noKey.getReusedFromBuildRunId()).isNull();
        assertThat(blankKey.getReusedFromBuildRunId()).isNull();
    }

    // ─── replaceForBuildRun 测试 ──────────────────────────────────────────────

    @Test
    void replaceForBuildRunParsesAuditJsonRemovesOldRowsAndSavesNewRows() throws Exception {
        // 准备：创建临时 JSON 文件
        String json = """
                {
                  "audit_samples": [
                    {
                      "source_sample_id": "s1",
                      "text": "测试文本",
                      "heading_path": ["第一章", "第一节"],
                      "page_start": 1,
                      "page_end": 3,
                      "document_type": "textbook",
                      "audit_priority": "high",
                      "audit_reason": "关键段落",
                      "hit_signals": [{"signal": "keyword_match"}],
                      "gold_entities": [],
                      "gold_relations": [],
                      "annotation_notes": "",
                      "reviewer_decision": "",
                      "reviewer_confidence": null,
                      "gold_stable_key": "key-001"
                    }
                  ]
                }
                """;
        Path tempFile = Files.createTempFile("audit-set-", ".json");
        Files.writeString(tempFile, json);

        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setId(50L);
        buildRun.setKnowledgeBaseId(10L);

        when(samplesStore.remove(any())).thenReturn(true);
        when(samplesStore.saveBatch(anyList())).thenReturn(true);

        List<PromptTuneAuditSamples> expectedResult = List.of(new PromptTuneAuditSamples());
        when(samplesStore.listByBuildRunId(50L)).thenReturn(expectedResult);
        when(samplesStore.findCompletedByStableKeys(eq(10L), anyList())).thenReturn(List.of());

        // 执行
        List<PromptTuneAuditSamples> result = service.replaceForBuildRun(buildRun, tempFile);

        // 验证：删除旧数据
        verify(samplesStore).remove(any());
        // 验证：保存新数据
        verify(samplesStore).saveBatch(argThat(collection -> {
            if (collection.size() != 1) return false;
            PromptTuneAuditSamples saved = collection.iterator().next();
            return "s1".equals(saved.getSourceSampleId())
                    && "测试文本".equals(saved.getText())
                    && "第一章 > 第一节".equals(saved.getHeadingPath())
                    && saved.getPageStart() == 1
                    && saved.getPageEnd() == 3
                    && "textbook".equals(saved.getDocumentType())
                    && "high".equals(saved.getAuditPriority())
                    && "关键段落".equals(saved.getAuditReason())
                    && saved.getHitSignals() != null
                    && "[]".equals(saved.getGoldEntities())
                    && "[]".equals(saved.getGoldRelations())
                    && "pending".equals(saved.getReviewerDecision())
                    && "key-001".equals(saved.getGoldStableKey())
                    && saved.getBuildRunId() == 50L
                    && saved.getKnowledgeBaseId() == 10L;
        }));
        // 验证：返回最终查询结果
        verify(samplesStore).listByBuildRunId(50L);
        assertThat(result).isSameAs(expectedResult);

        // 清理
        Files.deleteIfExists(tempFile);
    }
}
