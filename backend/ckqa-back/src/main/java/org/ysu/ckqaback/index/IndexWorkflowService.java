package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.IndexRunResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagIndexOrchestrator;
import org.ysu.ckqaback.integration.graphrag.IndexRunMetadata;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    public IndexWorkflowService(
            IndexRunsService indexRunsService,
            KnowledgeBasesService knowledgeBasesService,
            GraphRagIndexOrchestrator graphRagIndexOrchestrator,
            CkqaIntegrationProperties properties
    ) {
        this(
                indexRunsService,
                knowledgeBasesService,
                graphRagIndexOrchestrator,
                new ObjectMapper(),
                Duration.ofSeconds(properties.getTimeout().getIndexStaleSeconds())
        );
    }

    IndexWorkflowService(
            IndexRunsService indexRunsService,
            KnowledgeBasesService knowledgeBasesService,
            GraphRagIndexOrchestrator graphRagIndexOrchestrator,
            ObjectMapper objectMapper,
            Duration staleThreshold
    ) {
        this.indexRunsService = indexRunsService;
        this.knowledgeBasesService = knowledgeBasesService;
        this.graphRagIndexOrchestrator = graphRagIndexOrchestrator;
        this.objectMapper = objectMapper;
        this.staleThreshold = staleThreshold;
    }

    public IndexRunResponse createIndexRun(Long knowledgeBaseId) throws IOException, InterruptedException {
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
}
