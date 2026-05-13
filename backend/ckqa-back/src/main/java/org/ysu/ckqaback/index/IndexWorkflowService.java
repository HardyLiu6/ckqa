package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.BuildRunIndexRequest;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagIndexOrchestrator;
import org.ysu.ckqaback.integration.graphrag.IndexRunMetadata;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 索引业务工作流服务。
 */
@Service
public class IndexWorkflowService {

    private static final String ENGINE = "graphrag";
    private static final String FETCH_COMMAND = "python utils/fetch_from_minio.py";
    private static final String INDEX_COMMAND = "python -m graphrag index --root .";
    private static final DateTimeFormatter INDEX_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final IndexRunsService indexRunsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final GraphRagIndexOrchestrator graphRagIndexOrchestrator;
    private final ObjectMapper objectMapper;
    private final Duration staleThreshold;
    private final KnowledgeBaseBuildRunService buildRunService;
    private final BuildRunWorkspaceService workspaceService;
    private final IndexArtifactRegistryService artifactRegistryService;
    private final ActiveIndexRunService activeIndexRunService;

    @Autowired
    public IndexWorkflowService(
            IndexRunsService indexRunsService,
            KnowledgeBasesService knowledgeBasesService,
            GraphRagIndexOrchestrator graphRagIndexOrchestrator,
            KnowledgeBaseBuildRunService buildRunService,
            BuildRunWorkspaceService workspaceService,
            IndexArtifactRegistryService artifactRegistryService,
            ActiveIndexRunService activeIndexRunService,
            CkqaIntegrationProperties properties
    ) {
        this(
                indexRunsService,
                knowledgeBasesService,
                graphRagIndexOrchestrator,
                new ObjectMapper(),
                Duration.ofSeconds(properties.getTimeout().getIndexStaleSeconds()),
                buildRunService,
                workspaceService,
                artifactRegistryService,
                activeIndexRunService
        );
    }

    IndexWorkflowService(
            IndexRunsService indexRunsService,
            KnowledgeBasesService knowledgeBasesService,
            GraphRagIndexOrchestrator graphRagIndexOrchestrator,
            ObjectMapper objectMapper,
            Duration staleThreshold
    ) {
        this(indexRunsService, knowledgeBasesService, graphRagIndexOrchestrator, objectMapper, staleThreshold, null, null, null, null);
    }

    IndexWorkflowService(
            IndexRunsService indexRunsService,
            KnowledgeBasesService knowledgeBasesService,
            GraphRagIndexOrchestrator graphRagIndexOrchestrator,
            ObjectMapper objectMapper,
            Duration staleThreshold,
            KnowledgeBaseBuildRunService buildRunService,
            BuildRunWorkspaceService workspaceService,
            IndexArtifactRegistryService artifactRegistryService,
            ActiveIndexRunService activeIndexRunService
    ) {
        this.indexRunsService = indexRunsService;
        this.knowledgeBasesService = knowledgeBasesService;
        this.graphRagIndexOrchestrator = graphRagIndexOrchestrator;
        this.objectMapper = objectMapper;
        this.staleThreshold = staleThreshold;
        this.buildRunService = buildRunService;
        this.workspaceService = workspaceService;
        this.artifactRegistryService = artifactRegistryService;
        this.activeIndexRunService = activeIndexRunService;
    }

    public IndexRunResponse createIndexRun(Long knowledgeBaseId) throws IOException, InterruptedException {
        if (buildRunService != null) {
            BuildRunDetailResponse buildRun = buildRunService.createCompatibilityBuildRun(knowledgeBaseId);
            BuildRunIndexRequest request = new BuildRunIndexRequest();
            request.setActivateOnSuccess(true);
            request.setForceRebuild(false);
            return createBuildRunIndexRun(buildRun.getId(), request);
        }

        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(knowledgeBaseId);
        indexRunsService.recoverStaleRunningRuns(knowledgeBaseId, staleThreshold);

        if (indexRunsService.findActiveRunningByKnowledgeBaseId(knowledgeBaseId).isPresent()) {
            throw new BusinessException(ApiResultCode.INDEX_RUN_ALREADY_RUNNING, HttpStatus.CONFLICT);
        }

        String indexVersion = ENGINE + "-" + INDEX_VERSION_FORMATTER.format(LocalDateTime.now());
        IndexRuns run = indexRunsService.createPendingRun(knowledgeBaseId, indexVersion);
        indexRunsService.markRunning(run.getId());

        try {
            ProcessExecutionResult fetchResult = graphRagIndexOrchestrator.fetchInput(run, knowledgeBase);
            if (fetchResult.isTimedOut() || fetchResult.getExitCode() != 0) {
                indexRunsService.markFailed(
                        run.getId(),
                        toMetadataJson(
                                FETCH_COMMAND,
                                fetchResult.getElapsedSeconds(),
                                fetchResult.getExitCode(),
                                fetchResult.isTimedOut() ? "索引输入拉取超时" : defaultErrorSummary(fetchResult.getStderr(), "索引输入拉取失败"),
                                false
                        )
                );
                throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引输入拉取失败");
            }

            ProcessExecutionResult indexResult = graphRagIndexOrchestrator.runIndex(run);
            if (indexResult.isTimedOut() || indexResult.getExitCode() != 0) {
                indexRunsService.markFailed(
                        run.getId(),
                        toMetadataJson(
                                INDEX_COMMAND,
                                indexResult.getElapsedSeconds(),
                                indexResult.getExitCode(),
                                indexResult.isTimedOut() ? "索引命令执行超时" : defaultErrorSummary(indexResult.getStderr(), "索引构建失败"),
                                false
                        )
                );
                throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引构建失败");
            }

            indexRunsService.markSuccess(
                    run.getId(),
                    toMetadataJson(INDEX_COMMAND, indexResult.getElapsedSeconds(), indexResult.getExitCode(), null, false)
            );
            knowledgeBasesService.updateActiveIndexRunId(knowledgeBaseId, run.getId());
            return IndexRunResponse.fromEntity(indexRunsService.getRequiredById(run.getId()));
        } catch (IOException exception) {
            indexRunsService.markFailed(
                    run.getId(),
                    toMetadataJson(INDEX_COMMAND, null, null, defaultErrorSummary(exception.getMessage(), "索引任务执行异常"), false)
            );
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            indexRunsService.markFailed(
                    run.getId(),
                    toMetadataJson(INDEX_COMMAND, null, null, "索引任务被中断", false)
            );
            throw exception;
        }
    }

    public IndexRunResponse createBuildRunIndexRun(Long buildRunId, BuildRunIndexRequest request) throws IOException, InterruptedException {
        if (buildRunService == null || workspaceService == null || artifactRegistryService == null || activeIndexRunService == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "构建流水线索引服务未配置");
        }

        BuildRunDetailResponse buildRun = buildRunService.getBuildRun(buildRunId);
        assertPromptConfirmed(buildRun);
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(buildRun.getKnowledgeBaseId());
        indexRunsService.recoverStaleRunningRuns(knowledgeBase.getId(), staleThreshold);
        if (indexRunsService.findActiveRunningByKnowledgeBaseId(knowledgeBase.getId()).isPresent()) {
            throw new BusinessException(ApiResultCode.INDEX_RUN_ALREADY_RUNNING, HttpStatus.CONFLICT);
        }

        String indexVersion = ENGINE + "-" + INDEX_VERSION_FORMATTER.format(LocalDateTime.now());
        IndexRuns run = indexRunsService.createPendingRun(knowledgeBase.getId(), buildRunId, indexVersion);
        indexRunsService.markRunning(run.getId());
        Path workspaceRoot = workspaceService.resolve(buildRun.getWorkspaceUri());

        try {
            prepareIndexInput(run, knowledgeBase, buildRun, workspaceRoot);
            ProcessExecutionResult indexResult = graphRagIndexOrchestrator.runIndex(run, workspaceRoot);
            if (indexResult.isTimedOut() || indexResult.getExitCode() != 0) {
                indexRunsService.markFailed(
                        run.getId(),
                        toMetadataJson(
                                INDEX_COMMAND,
                                indexResult.getElapsedSeconds(),
                                indexResult.getExitCode(),
                                indexResult.isTimedOut() ? "索引命令执行超时" : defaultErrorSummary(indexResult.getStderr(), "索引构建失败"),
                                false
                        )
                );
                throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引构建失败");
            }

            String metadata = toMetadataJson(INDEX_COMMAND, indexResult.getElapsedSeconds(), indexResult.getExitCode(), null, false);
            indexRunsService.markSuccess(run.getId(), metadata);
            IndexRuns refreshedRun = indexRunsService.getRequiredById(run.getId());
            artifactRegistryService.scanAndRegister(refreshedRun, workspaceRoot, buildRun.getWorkspaceUri());

            boolean activateOnSuccess = request == null || !Boolean.FALSE.equals(request.getActivateOnSuccess());
            if (activateOnSuccess && buildRunService.isLatestBuildRun(buildRunId)) {
                activeIndexRunService.activate(knowledgeBase.getId(), run.getId(), false);
            } else if (activateOnSuccess) {
                indexRunsService.markSuccess(
                        run.getId(),
                        toMetadataJson(INDEX_COMMAND, indexResult.getElapsedSeconds(), indexResult.getExitCode(), "skipped_newer_build_exists", false)
                );
            }
            buildRunService.markIndexSuccessDone(buildRunId, "skipped");
            return IndexRunResponse.fromEntity(indexRunsService.getRequiredById(run.getId()));
        } catch (IOException exception) {
            indexRunsService.markFailed(
                    run.getId(),
                    toMetadataJson(INDEX_COMMAND, null, null, defaultErrorSummary(exception.getMessage(), "索引任务执行异常"), false)
            );
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            indexRunsService.markFailed(
                    run.getId(),
                    toMetadataJson(INDEX_COMMAND, null, null, "索引任务被中断", false)
            );
            throw exception;
        }
    }

    public IndexRunResponse getIndexRun(Long id) {
        return IndexRunResponse.fromEntity(indexRunsService.getRequiredById(id));
    }

    public List<IndexRunResponse> listIndexRuns(Long knowledgeBaseId) {
        knowledgeBasesService.getRequiredById(knowledgeBaseId);
        return indexRunsService.listByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(IndexRunResponse::fromEntity)
                .toList();
    }

    private String toMetadataJson(
            String command,
            Long elapsedSeconds,
            Integer exitCode,
            String errorSummary,
            boolean staleTimeoutRecovered
    ) {
        try {
            return objectMapper.writeValueAsString(IndexRunMetadata.builder()
                    .command(command)
                    .elapsedSeconds(elapsedSeconds)
                    .exitCode(exitCode)
                    .errorSummary(errorSummary)
                    .staleTimeoutRecovered(staleTimeoutRecovered)
                    .build());
        } catch (Exception exception) {
            return "{\"command\":\"metadata-serialization-failed\",\"errorSummary\":\"" + escapeJson(errorSummary) + "\"}";
        }
    }

    private void prepareIndexInput(
            IndexRuns run,
            KnowledgeBases knowledgeBase,
            BuildRunDetailResponse buildRun,
            Path workspaceRoot
    ) throws IOException, InterruptedException {
        Path graphInputDir = workspaceRoot.resolve("graph-input");
        Path indexInputDir = workspaceRoot.resolve("index/input");
        Files.createDirectories(graphInputDir);
        Files.createDirectories(indexInputDir);
        cleanDirectory(indexInputDir);

        List<Path> graphInputFiles = listJsonFiles(graphInputDir);
        if (graphInputFiles.isEmpty()) {
            List<Long> materialIds = selectedMaterialIds(buildRun.getSelectedMaterialIds());
            String jsonFile = "section_docs.json";
            for (Long materialId : materialIds) {
                String outputFile = "material_" + materialId + "." + jsonFile;
                ProcessExecutionResult fetchResult = graphRagIndexOrchestrator.fetchMaterialInput(
                        run,
                        knowledgeBase,
                        materialId,
                        graphInputDir,
                        jsonFile,
                        outputFile
                );
                if (fetchResult.isTimedOut() || fetchResult.getExitCode() != 0) {
                    indexRunsService.markFailed(
                            run.getId(),
                            toMetadataJson(
                                    FETCH_COMMAND,
                                    fetchResult.getElapsedSeconds(),
                                    fetchResult.getExitCode(),
                                    fetchResult.isTimedOut() ? "索引输入拉取超时" : defaultErrorSummary(fetchResult.getStderr(), "索引输入拉取失败"),
                                    false
                            )
                    );
                    throw new BusinessException(ApiResultCode.INDEX_RUN_EXECUTION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "索引输入拉取失败");
                }
            }
            graphInputFiles = listJsonFiles(graphInputDir);
        }

        for (Path source : graphInputFiles) {
            Files.copy(source, indexInputDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanDirectory(Path directory) throws IOException {
        try (var stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    cleanDirectory(path);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private List<Path> listJsonFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        return files;
    }

    private List<Long> selectedMaterialIds(String selectedMaterialIds) {
        if (selectedMaterialIds == null || selectedMaterialIds.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(selectedMaterialIds, new TypeReference<List<Long>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String defaultErrorSummary(String rawMessage, String fallback) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return fallback;
        }
        String compact = rawMessage.trim();
        return compact.length() > 500 ? compact.substring(0, 500) : compact;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 兜底校验：确保构建流水线已确认提示词策略，防止通过 URL 伪造跳过确认步骤。
     */
    private void assertPromptConfirmed(BuildRunDetailResponse buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (!org.springframework.util.StringUtils.hasText(metadata)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "请先确认提示词策略");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadata);
            if (!node.path("promptConfirmed").asBoolean(false)) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                        "请先确认提示词策略");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "构建元数据格式无效");
        }
    }
}
