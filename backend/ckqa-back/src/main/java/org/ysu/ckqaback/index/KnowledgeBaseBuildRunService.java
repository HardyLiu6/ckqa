package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunCreateRequest;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.BuildRunGcRequest;
import org.ysu.ckqaback.index.dto.BuildRunGcResponse;
import org.ysu.ckqaback.index.dto.BuildRunGraphInputRequest;
import org.ysu.ckqaback.index.dto.BuildRunMaterialSelectionRequest;
import org.ysu.ckqaback.index.dto.BuildRunParseCheckRequest;
import org.ysu.ckqaback.index.dto.BuildRunPromptConfirmationRequest;
import org.ysu.ckqaback.index.dto.BuildRunQaSmokeRequest;
import org.ysu.ckqaback.index.dto.BuildRunSummaryResponse;
import org.ysu.ckqaback.index.dto.BuildRunUpdateRequest;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识库构建流水线控制面服务。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseBuildRunService {

    private static final DateTimeFormatter BUILD_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final BuildRunWorkspaceService workspaceService;
    private final CkqaIntegrationProperties properties;
    private final QaWorkflowService qaWorkflowService;
    private final IndexRunsService indexRunsService;
    private final CourseMaterialsService courseMaterialsService;
    private final ParseResultsService parseResultsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public BuildRunDetailResponse createBuildRun(Long knowledgeBaseId, BuildRunCreateRequest request) {
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(knowledgeBaseId);
        if (!properties.getGraphrag().isConcurrentBuildsEnabled()
                && buildRunsStore.findActivePendingOrRunning(knowledgeBaseId).isPresent()) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_BUILD_RUN_ALREADY_RUNNING, HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBaseBuildRuns buildRun = new KnowledgeBaseBuildRuns();
        buildRun.setKnowledgeBaseId(knowledgeBaseId);
        buildRun.setCourseId(knowledgeBase.getCourseId());
        buildRun.setRequestedByUserId(request == null ? null : request.getRequestedByUserId());
        buildRun.setBuildVersion(buildVersion(knowledgeBaseId, now));
        buildRun.setStatus("pending");
        buildRun.setCurrentStage("material_selection");
        buildRun.setQaStatus("skipped");
        buildRun.setActivationPolicy(Boolean.FALSE.equals(request == null ? null : request.getActivateOnSuccess())
                ? "manual"
                : "index_success");
        buildRun.setSelectedMaterialIds(toJson(request == null ? List.of() : nullToEmpty(request.getMaterialIds())));
        buildRun.setBuildMetadata(stageMetadata("material_selection", Map.of(
                "jsonFile", defaultText(request == null ? null : request.getJsonFile(), "section_docs.json"),
                "promptStrategy", defaultText(request == null ? null : request.getPromptStrategy(), "active")
        )));
        buildRun.setCreatedAt(now);
        buildRun.setUpdatedAt(now);
        buildRunsStore.save(buildRun);

        String workspaceUri = workspaceService.workspaceUri(buildRun.getRequestedByUserId(), knowledgeBaseId, buildRun.getId());
        buildRun.setWorkspaceUri(workspaceUri);
        try {
            workspaceService.createLayout(workspaceUri);
            writeSelectedMaterials(workspaceUri, buildRun.getSelectedMaterialIds());
        } catch (IOException exception) {
            deleteWorkspaceQuietly(workspaceUri);
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "构建工作区创建失败");
        }
        buildRunsStore.updateById(buildRun);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(buildRun.getId()));
    }

    @Transactional(readOnly = true)
    public ApiPageData<BuildRunSummaryResponse> listBuildRuns(Long knowledgeBaseId, String status, Long page, Long size) {
        knowledgeBasesService.getRequiredById(knowledgeBaseId);
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        List<BuildRunSummaryResponse> allItems = buildRunsStore.listByKnowledgeBaseId(knowledgeBaseId).stream()
                .filter(run -> !StringUtils.hasText(status) || status.equals(run.getStatus()))
                .map(BuildRunSummaryResponse::fromEntity)
                .toList();
        long total = allItems.size();
        long fromIndex = Math.min((current - 1) * pageSize, total);
        long toIndex = Math.min(fromIndex + pageSize, total);
        List<BuildRunSummaryResponse> pageItems = allItems.subList((int) fromIndex, (int) toIndex);
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / pageSize);
        return new ApiPageData<>(pageItems, current, pageSize, total, pages);
    }

    @Transactional
    public BuildRunDetailResponse getBuildRun(Long id) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        syncQaSmokeTerminalIfAvailable(buildRun);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse updateBuildRun(Long id, BuildRunUpdateRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        if (request.getStatus() != null) {
            buildRun.setStatus(request.getStatus());
        }
        if (StringUtils.hasText(request.getCurrentStage())) {
            buildRun.setCurrentStage(request.getCurrentStage().trim());
        }
        if (request.getBuildMetadata() != null) {
            validateJson(request.getBuildMetadata());
            buildRun.setBuildMetadata(request.getBuildMetadata());
        }
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse archiveBuildRun(Long id, boolean deleteWorkspace) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        if ("running".equals(buildRun.getStatus())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "运行中的构建流水线不能归档");
        }
        if (deleteWorkspace && buildRun.getActiveIndexRunId() != null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "激活索引构建工作区不能删除");
        }

        boolean workspaceDeleted = deleteWorkspace && deleteWorkspace(buildRun.getWorkspaceUri());
        buildRun.setStatus("archived");
        buildRun.setBuildMetadata(stageMetadata("archived", Map.of(
                "deleteWorkspace", deleteWorkspace,
                "workspaceDeleted", workspaceDeleted
        )));
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunGcResponse gcBuildRuns(Long knowledgeBaseId, BuildRunGcRequest request) {
        knowledgeBasesService.getRequiredById(knowledgeBaseId);
        boolean dryRun = request == null || request.getDryRun() == null || request.getDryRun();
        List<KnowledgeBaseBuildRuns> candidates = gcCandidates(buildRunsStore.listByKnowledgeBaseId(knowledgeBaseId));
        int workspaceCount;
        if (!dryRun) {
            LocalDateTime now = LocalDateTime.now();
            for (KnowledgeBaseBuildRuns candidate : candidates) {
                candidate.setStatus("archived");
                candidate.setUpdatedAt(now);
                buildRunsStore.updateById(candidate);
            }
            // Task 2 只处理控制面归档，物理 workspace 删除留给后续产物生命周期补偿逻辑。
            workspaceCount = 0;
        } else {
            workspaceCount = (int) candidates.stream().filter(run -> StringUtils.hasText(run.getWorkspaceUri())).count();
        }
        return BuildRunGcResponse.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .deletedBuildRunCount(candidates.size())
                .deletedWorkspaceCount(workspaceCount)
                .dryRun(dryRun)
                .build();
    }

    @Transactional
    public BuildRunDetailResponse createCompatibilityBuildRun(Long knowledgeBaseId) {
        BuildRunCreateRequest request = new BuildRunCreateRequest();
        request.setActivateOnSuccess(true);
        request.setJsonFile("section_docs.json");
        request.setPromptStrategy("active");
        return createBuildRun(knowledgeBaseId, request);
    }

    @Transactional(readOnly = true)
    public boolean isLatestBuildRun(Long buildRunId) {
        KnowledgeBaseBuildRuns current = buildRunsStore.getRequiredById(buildRunId);
        return buildRunsStore.listByKnowledgeBaseId(current.getKnowledgeBaseId()).stream()
                .max(Comparator
                        .comparing(KnowledgeBaseBuildRuns::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(KnowledgeBaseBuildRuns::getId, Comparator.nullsFirst(Long::compareTo)))
                .map(latest -> latest.getId().equals(buildRunId))
                .orElse(false);
    }

    @Transactional
    public BuildRunDetailResponse markIndexSuccessDone(Long buildRunId, String qaStatus) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        buildRun.setStatus("success");
        buildRun.setCurrentStage("done");
        buildRun.setQaStatus(normalizeQaStatus(qaStatus));
        buildRun.setBuildMetadata(stageMetadata("done", Map.of("indexStatus", "success")));
        buildRun.setFinishedAt(LocalDateTime.now());
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(buildRunId));
    }

    @Transactional
    public BuildRunDetailResponse updateMaterialSelection(Long id, BuildRunMaterialSelectionRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        List<Long> materialIds = request == null ? List.of() : nullToEmpty(request.getMaterialIds());
        assertMaterialIdsBelongToBuildRun(buildRun, materialIds);
        buildRun.setSelectedMaterialIds(toJson(materialIds));
        buildRun.setStatus("pending");
        updateStage(buildRun, "material_selection", stageMetadata("material_selection", Map.of("updated", true)));
        try {
            writeSelectedMaterials(buildRun.getWorkspaceUri(), buildRun.getSelectedMaterialIds());
        } catch (IOException exception) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "资料选择快照写入失败");
        }
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse checkParse(Long id, BuildRunParseCheckRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        updateStage(buildRun, "parse", stageMetadata("parse", Map.of(
                "parseMissing", request != null && Boolean.TRUE.equals(request.getParseMissing())
        )));
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse syncGraphInput(Long id, BuildRunGraphInputRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        String jsonFile = request == null || request.getJsonFile() == null ? "section_docs.json" : request.getJsonFile();
        boolean exportMissing = request == null || !Boolean.FALSE.equals(request.getExportMissing());
        if (!exportMissing) {
            assertGraphInputComplete(buildRun);
        } else {
            assertMaterialIdsBelongToBuildRun(buildRun, selectedMaterialIds(buildRun));
        }
        updateStage(buildRun, "graph_input_export", stageMetadata("graph_input_export", Map.of(
                "jsonFile", jsonFile,
                "exportMissing", exportMissing
        )));
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse confirmPrompt(Long id, BuildRunPromptConfirmationRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
        String strategy = normalizeStrategy(request == null ? null : request.getPromptStrategy());

        if (confirmed && "custom_pipeline".equals(strategy)) {
            assertCustomDraftExists(buildRun);
        }

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("promptConfirmed", confirmed);
        extras.put("promptStrategy", strategy);

        String metadata = mergeStageMetadata(buildRun, "prompt", extras,
                List.of("customPromptDraft"));
        updateStage(buildRun, "prompt", metadata);
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    @Transactional
    public BuildRunDetailResponse runQaSmoke(Long id, BuildRunQaSmokeRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
        Long smokeIndexRunId = resolveSmokeIndexRunId(buildRun);
        if (smokeIndexRunId == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
        }

        String question = defaultText(request == null ? null : request.getQuestion(), "请用一句话说明当前知识库的主要内容");
        String mode = defaultText(request == null ? null : request.getMode(), "basic");
        writeQaSmokeRequest(buildRun, question, mode, smokeIndexRunId);

        buildRun.setCurrentStage("qa_smoke");
        buildRun.setQaStatus("running");
        buildRun.setBuildMetadata(stageMetadata("qa_smoke", Map.of(
                "question", question,
                "mode", mode,
                "activeIndexRunId", smokeIndexRunId
        )));
        if (buildRun.getStartedAt() == null) {
            buildRun.setStartedAt(LocalDateTime.now());
        }
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);

        QaSessionResponse session = qaWorkflowService.createSession(createSmokeSessionRequest(buildRun));
        QaTaskSubmissionResponse submission = qaWorkflowService.sendMessage(session.getId(), new CreateQaMessageRequest(mode, question), smokeIndexRunId);
        buildRun.setBuildMetadata(stageMetadata("qa_smoke", Map.of(
                "question", question,
                "mode", mode,
                "sessionId", session.getId(),
                "taskId", submission.getTaskId(),
                "activeIndexRunId", smokeIndexRunId
        )));
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);
        syncQaSmokeTerminalIfAvailable(buildRun, session.getId(), submission.getTaskId());
        return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
    }

    private void assertCustomDraftExists(KnowledgeBaseBuildRuns buildRun) {
        String content = readDraftExtractGraphContent(buildRun.getBuildMetadata());
        if (content == null || content.strip().isEmpty()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "请先完成手动调优提示词构建");
        }
    }

    private String readDraftExtractGraphContent(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode node = root
                    .path("customPromptDraft")
                    .path("prompts")
                    .path("extract_graph")
                    .path("content");
            return node.isTextual() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void updateStage(KnowledgeBaseBuildRuns buildRun, String stage, String metadata) {
        buildRun.setStatus("running");
        buildRun.setCurrentStage(stage);
        buildRun.setBuildMetadata(metadata);
        if (buildRun.getStartedAt() == null) {
            buildRun.setStartedAt(LocalDateTime.now());
        }
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsStore.updateById(buildRun);
    }

    private void assertGraphInputComplete(KnowledgeBaseBuildRuns buildRun) {
        List<Long> materialIds = selectedMaterialIds(buildRun);
        if (materialIds.isEmpty()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "请先选择课程资料");
        }
        assertMaterialIdsBelongToBuildRun(buildRun, materialIds);
        boolean hasMissing = materialIds.stream()
                .anyMatch(materialId -> !parseResultsService.hasCompleteGraphRagExport(materialId, "section", true));
        if (hasMissing) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "图谱输入产物缺失，请先生成缺失图谱输入");
        }
    }

    private void assertMaterialIdsBelongToBuildRun(KnowledgeBaseBuildRuns buildRun, List<Long> materialIds) {
        for (Long materialId : materialIds) {
            if (materialId == null) {
                continue;
            }
            CourseMaterials material = courseMaterialsService.getRequiredById(materialId);
            if (!Objects.equals(buildRun.getCourseId(), material.getCourseId())) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "资料不属于当前知识库课程");
            }
        }
    }

    private List<Long> selectedMaterialIds(KnowledgeBaseBuildRuns buildRun) {
        if (buildRun == null || !StringUtils.hasText(buildRun.getSelectedMaterialIds())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(buildRun.getSelectedMaterialIds(), new TypeReference<List<Object>>() {})
                    .stream()
                    .map(this::toLongOrNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception exception) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "资料选择快照格式非法");
        }
    }

    private Long toLongOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<KnowledgeBaseBuildRuns> gcCandidates(List<KnowledgeBaseBuildRuns> buildRuns) {
        int keepSuccess = Math.max(properties.getGraphrag().getRetention().getKeepSuccessBuildRuns(), 0);
        int keepFailed = Math.max(properties.getGraphrag().getRetention().getKeepFailedBuildRuns(), 0);
        return buildRuns.stream()
                .filter(run -> "success".equals(run.getStatus()) || "failed".equals(run.getStatus()))
                .collect(java.util.stream.Collectors.groupingBy(KnowledgeBaseBuildRuns::getStatus))
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    int keep = "success".equals(entry.getKey()) ? keepSuccess : keepFailed;
                    return entry.getValue().stream()
                            .sorted(Comparator
                                    .comparing(KnowledgeBaseBuildRuns::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparing(KnowledgeBaseBuildRuns::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                            .skip(keep);
                })
                .toList();
    }

    private boolean deleteWorkspace(String workspaceUri) {
        if (!StringUtils.hasText(workspaceUri)) {
            return false;
        }
        try {
            Path root = workspaceService.resolve(workspaceUri);
            if (!Files.exists(root)) {
                return false;
            }
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
            return true;
        } catch (Exception exception) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "构建工作区清理失败");
        }
    }

    private void deleteWorkspaceQuietly(String workspaceUri) {
        try {
            deleteWorkspace(workspaceUri);
        } catch (RuntimeException ignored) {
            // 保留原始失败原因，残留空目录可由后续 GC 处理。
        }
    }

    private void writeSelectedMaterials(String workspaceUri, String selectedMaterialIds) throws IOException {
        Path selectionFile = workspaceService.resolve(workspaceUri).resolve("selection/selected_materials.json");
        Files.createDirectories(selectionFile.getParent());
        Files.writeString(selectionFile, selectedMaterialIds == null ? "[]" : selectedMaterialIds);
    }

    private void writeQaSmokeRequest(KnowledgeBaseBuildRuns buildRun, String question, String mode, Long indexRunId) {
        try {
            Path requestFile = workspaceService.resolve(buildRun.getWorkspaceUri()).resolve("qa-smoke/request.json");
            Files.createDirectories(requestFile.getParent());
            Files.writeString(requestFile, toJson(Map.of(
                    "buildRunId", buildRun.getId(),
                    "knowledgeBaseId", buildRun.getKnowledgeBaseId(),
                    "activeIndexRunId", indexRunId,
                    "question", question,
                    "mode", mode
            )));
        } catch (IOException exception) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "QA冒烟请求写入失败");
        }
    }

    private CreateQaSessionRequest createSmokeSessionRequest(KnowledgeBaseBuildRuns buildRun) {
        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(buildRun.getRequestedByUserId() == null ? 1L : buildRun.getRequestedByUserId());
        request.setCourseId(buildRun.getCourseId());
        request.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        request.setSessionType("smoke");
        request.setTitle("知识库构建冒烟验证");
        return request;
    }

    private Long resolveSmokeIndexRunId(KnowledgeBaseBuildRuns buildRun) {
        if (buildRun.getActiveIndexRunId() != null) {
            return buildRun.getActiveIndexRunId();
        }
        return indexRunsService.listByKnowledgeBaseId(buildRun.getKnowledgeBaseId()).stream()
                .filter(run -> buildRun.getId().equals(run.getBuildRunId()))
                .filter(run -> "success".equals(run.getStatus()))
                .max(Comparator
                        .comparing(this::indexRunSortTime, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(IndexRuns::getId, Comparator.nullsFirst(Long::compareTo)))
                .map(IndexRuns::getId)
                .orElse(null);
    }

    private LocalDateTime indexRunSortTime(IndexRuns run) {
        if (run.getFinishedAt() != null) {
            return run.getFinishedAt();
        }
        if (run.getStartedAt() != null) {
            return run.getStartedAt();
        }
        return run.getCreatedAt();
    }

    private void syncQaSmokeTerminalIfAvailable(KnowledgeBaseBuildRuns buildRun) {
        if (!"qa_smoke".equals(buildRun.getCurrentStage()) || !"running".equals(buildRun.getQaStatus())) {
            return;
        }
        try {
            var metadata = objectMapper.readTree(buildRun.getBuildMetadata());
            Long sessionId = metadata.hasNonNull("sessionId") ? metadata.get("sessionId").asLong() : null;
            Long taskId = metadata.hasNonNull("taskId") ? metadata.get("taskId").asLong() : null;
            syncQaSmokeTerminalIfAvailable(buildRun, sessionId, taskId);
        } catch (Exception ignored) {
            // 元数据不完整时保持 running，由人工或后续重试处理。
        }
    }

    private void syncQaSmokeTerminalIfAvailable(KnowledgeBaseBuildRuns buildRun, Long sessionId, Long taskId) {
        if (sessionId == null || taskId == null) {
            return;
        }
        QaTaskDetailResponse detail = qaWorkflowService.getTaskDetail(sessionId, taskId);
        if (detail == null || !isQaTerminal(detail.getTaskStatus())) {
            return;
        }
        boolean success = "success".equals(detail.getTaskStatus());
        writeQaSmokeResponse(buildRun, detail);
        buildRun.setStatus("success");
        buildRun.setCurrentStage("done");
        buildRun.setQaStatus(success ? "success" : "failed");
        buildRun.setFinishedAt(LocalDateTime.now());
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRun.setBuildMetadata(stageMetadata("done", qaSmokeTerminalMetadata(detail, success)));
        buildRunsStore.updateById(buildRun);
    }

    private boolean isQaTerminal(String taskStatus) {
        return "success".equals(taskStatus) || "failed".equals(taskStatus) || "stale".equals(taskStatus);
    }

    private Map<String, Object> qaSmokeTerminalMetadata(QaTaskDetailResponse detail, boolean success) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("qaStatus", success ? "success" : "failed");
        metadata.put("taskId", detail.getTaskId());
        metadata.put("taskStatus", detail.getTaskStatus());
        metadata.put("progressStage", detail.getProgressStage());
        metadata.put("errorMessage", detail.getErrorMessage());
        return metadata;
    }

    private void writeQaSmokeResponse(KnowledgeBaseBuildRuns buildRun, QaTaskDetailResponse detail) {
        try {
            Path responseFile = workspaceService.resolve(buildRun.getWorkspaceUri()).resolve("qa-smoke/response.json");
            Files.createDirectories(responseFile.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("buildRunId", buildRun.getId());
            payload.put("taskId", detail.getTaskId());
            payload.put("taskStatus", detail.getTaskStatus());
            payload.put("progressStage", detail.getProgressStage());
            payload.put("errorMessage", detail.getErrorMessage());
            payload.put("assistantMessage", assistantMessagePayload(detail.getAssistantMessage()));
            payload.put("latestLogs", detail.getLatestLogs());
            Files.writeString(responseFile, toJson(payload));
        } catch (IOException exception) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "QA冒烟响应写入失败");
        }
    }

    private Map<String, Object> assistantMessagePayload(QaMessageResponse message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.getId());
        payload.put("role", message.getRole());
        payload.put("content", message.getContent());
        return payload;
    }

    /**
     * 合并阶段元数据，保留跨阶段持久键。
     * extras 在 persist 之后 put，因此 extras 中的同名键会覆盖 persist 的旧值。
     */
    String mergeStageMetadata(KnowledgeBaseBuildRuns buildRun,
                              String stage,
                              Map<String, ?> extras,
                              List<String> persistKeys) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("stage", stage);

        if (StringUtils.hasText(buildRun.getBuildMetadata())) {
            try {
                com.fasterxml.jackson.databind.JsonNode existing = objectMapper.readTree(buildRun.getBuildMetadata());
                for (String key : persistKeys) {
                    if (existing.has(key) && !existing.get(key).isNull()) {
                        merged.put(key, objectMapper.treeToValue(existing.get(key), Object.class));
                    }
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // 旧 metadata 无效 JSON 时，按空对象处理；不影响新阶段写入
            }
        }
        if (extras != null) {
            merged.putAll(extras);
        }
        return toJson(merged);
    }

    private String stageMetadata(String stage, Map<String, ?> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", stage);
        metadata.putAll(extra);
        return toJson(metadata);
    }

    private String buildVersion(Long knowledgeBaseId, LocalDateTime now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        return "kb" + knowledgeBaseId + "-" + BUILD_VERSION_FORMATTER.format(now) + "-" + random;
    }

    private static final java.util.Set<String> SUPPORTED_PROMPT_STRATEGIES =
            java.util.Set.of("default", "graphrag_tuned", "custom_pipeline");

    /**
     * 归一化提示词策略值。
     * null/空白 → "default"；旧值 "active"（不区分大小写）→ "default"；
     * 已知策略原样返回；未知策略抛出 BusinessException。
     */
    String normalizeStrategy(String raw) {
        if (raw == null) {
            return "default";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "default";
        }
        // legacy: "active" 旧值在仓库语义上等价于"使用 .env 当前激活的提示词"，
        // 既可能是系统默认也可能是自动调优。新设计下统一归一化为 default，
        // 因为 default 行为最稳健，不依赖 active_prompt.json 是否存在。
        if ("active".equalsIgnoreCase(trimmed)) {
            return "default";
        }
        if (SUPPORTED_PROMPT_STRATEGIES.contains(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                "未知的提示词策略: " + raw);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String normalizeQaStatus(String qaStatus) {
        if (!StringUtils.hasText(qaStatus)) {
            return "skipped";
        }
        String normalized = qaStatus.trim();
        if (List.of("pending", "running", "success", "failed", "skipped").contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "qaStatus取值不合法");
    }

    private List<Long> nullToEmpty(List<Long> values) {
        return values == null ? List.of() : values;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "构建元数据序列化失败");
        }
    }

    private void validateJson(String rawJson) {
        try {
            objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "buildMetadata必须是合法JSON");
        }
    }
}
