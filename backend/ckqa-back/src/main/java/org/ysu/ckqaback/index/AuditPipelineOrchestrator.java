package org.ysu.ckqaback.index;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步包装 02.1（build_prompt_tuning_samples）和 02.2（build_audit_extraction_set）两个 Python 脚本，
 * 用于生成标注样本和审计抽取集。
 */
@Service
@RequiredArgsConstructor
public class AuditPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AuditPipelineOrchestrator.class);

    private static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(5);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;
    private final PromptTuneOrchestrator promptTuneOrchestrator;

    /**
     * 执行完整标注流水线：拉取资料 → 生成样本 → 生成审计抽取集。
     *
     * @param knowledgeBase 知识库
     * @param materialIds   选中的资料 ID 列表
     * @param workspaceDir  工作区目录（用于存放中间产物和日志）
     * @return 流水线执行结果
     */
    public AuditPipelineResult runFullPipeline(
            KnowledgeBases knowledgeBase,
            List<Long> materialIds,
            Path workspaceDir
    ) {
        Path inputDir = workspaceDir.resolve("input");
        Path samplesFile = workspaceDir.resolve("prompt_tuning_samples.json");
        Path auditSetFile = workspaceDir.resolve("audit_extraction_set.json");

        // 1. 从 MinIO 拉取资料到输入目录
        try {
            List<ProcessExecutionResult> fetchResults = promptTuneOrchestrator.fetchInputs(
                    null, knowledgeBase, materialIds, inputDir
            );
            for (ProcessExecutionResult result : fetchResults) {
                if (result.getExitCode() != 0 || result.isTimedOut()) {
                    throw new BusinessException(
                            ApiResultCode.AUDIT_PIPELINE_FAILED,
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "拉取资料失败: " + result.getStderr()
                    );
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "拉取资料异常: " + e.getMessage()
            );
        }

        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> pythonPrefix = PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        );

        // 2. 执行 build_prompt_tuning_samples.py
        long samplesElapsedSeconds;
        try {
            List<String> samplesCommand = new ArrayList<>(pythonPrefix);
            samplesCommand.add("scripts/build_prompt_tuning_samples.py");
            samplesCommand.add("--input_dir");
            samplesCommand.add(inputDir.toAbsolutePath().toString());
            samplesCommand.add("--output_file");
            samplesCommand.add(samplesFile.toAbsolutePath().toString());

            Path samplesLogFile = workspaceDir.resolve("build_prompt_tuning_samples.py.log");
            ProcessContext samplesContext = ProcessContext.builder()
                    .operation("build_prompt_tuning_samples")
                    .logFile(samplesLogFile)
                    .build();

            log.info("执行 build_prompt_tuning_samples.py, inputDir={}, outputFile={}",
                    inputDir, samplesFile);

            ProcessExecutionResult samplesResult = processRunner.run(
                    samplesCommand, scriptRoot, Map.of(), SCRIPT_TIMEOUT, samplesContext
            );
            samplesElapsedSeconds = samplesResult.getElapsedSeconds();

            if (samplesResult.isTimedOut()) {
                throw new BusinessException(
                        ApiResultCode.AUDIT_PIPELINE_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "build_prompt_tuning_samples.py 执行超时"
                );
            }
            if (samplesResult.getExitCode() != 0) {
                throw new BusinessException(
                        ApiResultCode.AUDIT_PIPELINE_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "build_prompt_tuning_samples.py 执行失败 (exit=" + samplesResult.getExitCode() + "): "
                                + samplesResult.getStderr()
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "build_prompt_tuning_samples.py 执行异常: " + e.getMessage()
            );
        }

        // 3. 执行 build_audit_extraction_set.py
        long auditSetElapsedSeconds;
        try {
            List<String> auditCommand = new ArrayList<>(pythonPrefix);
            auditCommand.add("scripts/build_audit_extraction_set.py");
            auditCommand.add("--input_file");
            auditCommand.add(samplesFile.toAbsolutePath().toString());
            auditCommand.add("--output_file");
            auditCommand.add(auditSetFile.toAbsolutePath().toString());
            auditCommand.add("--preserve_existing_gold");

            Path auditLogFile = workspaceDir.resolve("build_audit_extraction_set.py.log");
            ProcessContext auditContext = ProcessContext.builder()
                    .operation("build_audit_extraction_set")
                    .logFile(auditLogFile)
                    .build();

            log.info("执行 build_audit_extraction_set.py, inputFile={}, outputFile={}",
                    samplesFile, auditSetFile);

            ProcessExecutionResult auditResult = processRunner.run(
                    auditCommand, scriptRoot, Map.of(), SCRIPT_TIMEOUT, auditContext
            );
            auditSetElapsedSeconds = auditResult.getElapsedSeconds();

            if (auditResult.isTimedOut()) {
                throw new BusinessException(
                        ApiResultCode.AUDIT_PIPELINE_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "build_audit_extraction_set.py 执行超时"
                );
            }
            if (auditResult.getExitCode() != 0) {
                throw new BusinessException(
                        ApiResultCode.AUDIT_PIPELINE_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "build_audit_extraction_set.py 执行失败 (exit=" + auditResult.getExitCode() + "): "
                                + auditResult.getStderr()
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "build_audit_extraction_set.py 执行异常: " + e.getMessage()
            );
        }

        log.info("标注流水线完成, samplesElapsed={}s, auditSetElapsed={}s",
                samplesElapsedSeconds, auditSetElapsedSeconds);

        return AuditPipelineResult.builder()
                .samplesFile(samplesFile)
                .auditSetFile(auditSetFile)
                .samplesElapsedSeconds(samplesElapsedSeconds)
                .auditSetElapsedSeconds(auditSetElapsedSeconds)
                .build();
    }

    /**
     * 标注流水线执行结果。
     */
    @Getter
    @Builder
    public static class AuditPipelineResult {
        private final Path samplesFile;
        private final Path auditSetFile;
        private final long samplesElapsedSeconds;
        private final long auditSetElapsedSeconds;
    }
}
