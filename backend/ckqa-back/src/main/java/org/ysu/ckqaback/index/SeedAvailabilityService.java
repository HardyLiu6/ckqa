package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse;
import org.ysu.ckqaback.index.dto.SeedAvailabilityResponse.SeedOption;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算 01 步种子选项的可用状态。
 *
 * <ul>
 *   <li>{@code system_default}：始终可用</li>
 *   <li>{@code graphrag_tuned}：当且仅当当前 build run 选材的 prompt-tune 缓存为 success 时可用</li>
 *   <li>{@code history_draft}：本期始终不可用（Phase 6 落地）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SeedAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(SeedAvailabilityService.class);

    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final PromptTuneService promptTuneService;
    private final ObjectMapper objectMapper;

    public SeedAvailabilityResponse evaluate(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        String currentSeed = readCurrentSeed(buildRun);

        List<SeedOption> options = new ArrayList<>();
        options.add(buildSystemDefault());
        options.add(buildGraphragTuned(buildRun));
        options.add(buildHistoryDraft());

        return SeedAvailabilityResponse.builder()
                .currentSeed(currentSeed)
                .options(options)
                .build();
    }

    private SeedOption buildSystemDefault() {
        return SeedOption.builder()
                .key("system_default")
                .available(true)
                .reason(null)
                .summary("使用 GraphRAG 内置默认提示词作为起点")
                .build();
    }

    private SeedOption buildGraphragTuned(KnowledgeBaseBuildRuns buildRun) {
        try {
            List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
            PromptTuneRunResponse probe = promptTuneService.probeBySelection(
                    buildRun.getKnowledgeBaseId(), buildRun.getCourseId(), materialIds
            );
            String status = probe.getStatus();
            if ("success".equals(status)) {
                return SeedOption.builder()
                        .key("graphrag_tuned")
                        .available(true)
                        .reason(null)
                        .summary("当前选材的自动调优结果可用")
                        .build();
            }
            String reason = switch (status == null ? "not_started" : status) {
                case "running" -> "auto_tuned_running";
                case "pending" -> "auto_tuned_pending";
                case "failed" -> "auto_tuned_failed";
                default -> "auto_tuned_not_started";
            };
            return SeedOption.builder()
                    .key("graphrag_tuned")
                    .available(false)
                    .reason(reason)
                    .summary("当前选材尚未生成可用的自动调优产物")
                    .build();
        } catch (RuntimeException e) {
            log.warn("评估 graphrag_tuned 可用性失败 buildRunId={}", buildRun.getId(), e);
            return SeedOption.builder()
                    .key("graphrag_tuned")
                    .available(false)
                    .reason("evaluation_failed")
                    .summary("无法评估自动调优产物状态")
                    .build();
        }
    }

    private SeedOption buildHistoryDraft() {
        return SeedOption.builder()
                .key("history_draft")
                .available(false)
                .reason("phase_6_not_implemented")
                .summary("历史草稿入口将在 Phase 6 开放")
                .build();
    }

    private String readCurrentSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode seed = root.path("customPromptDraft").path("seed");
            return seed.isTextual() ? seed.asText() : null;
        } catch (Exception e) {
            log.warn("解析 build run metadata 失败 buildRunId={}", buildRun.getId(), e);
            return null;
        }
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
