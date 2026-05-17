package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
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
import java.util.List;
import java.util.Map;

/**
 * 04 步评分脚本同步包装：
 * <ul>
 *   <li>{@link #runSingleCandidateExtract} 调 {@code run_native_extraction.py}（单候选模式）</li>
 *   <li>{@link #runScoring} 调 {@code score_extraction_results.py}</li>
 * </ul>
 *
 * <p>本类<strong>不</strong>关心 worker 串行调度 / DB 写入，纯粹是 Python 子进程包装器。
 * 一次抽取的最长耗时按 60 分钟兜底（实测 5-15 分钟）；scoring 30 分钟兜底（实测 < 30 秒）。</p>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalOrchestrator.class);

    /** 单候选 20 样本抽取兜底超时；正常 5-15 分钟，60 分钟基本不会触发。 */
    static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(60);

    /** 评分脚本兜底超时；纯 CPU 计算 + IO，正常 < 30 秒，30 分钟兜底。 */
    static final Duration SCORE_TIMEOUT = Duration.ofMinutes(30);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    /**
     * 单候选抽取。
     *
     * @param candidateId       候选 id（同时作为 --candidate-name 和输出 JSON 文件名）
     * @param samplesFile       audit_with_gold.json 路径（绝对路径）
     * @param promptFile        候选 prompt.txt 路径（绝对路径）
     * @param entityTypes       逗号分隔的实体类型列表
     * @param runId             共享的 runId，eval_<buildRunId>_<evalRunId>
     * @param workspaceDir      worker 自己的临时工作区，用来落 ProcessContext.logFile
     */
    public void runSingleCandidateExtract(
            String candidateId,
            Path samplesFile,
            Path promptFile,
            String entityTypes,
            String runId,
            Path workspaceDir
    ) throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.extraction_eval.run_native_extraction");
        argv.add("--samples-file");
        argv.add(samplesFile.toAbsolutePath().toString());
        argv.add("--prompt");
        argv.add(promptFile.toAbsolutePath().toString());
        argv.add("--candidate-name");
        argv.add(candidateId);
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--entity-types");
        argv.add(entityTypes);
        argv.add("--max-gleanings");
        argv.add("1");
        argv.add("--concurrency");
        argv.add("1");
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("extract_" + candidateId + ".log");
        ProcessContext context = ProcessContext.builder()
                .operation("extraction-eval-extract")
                .logFile(logFile)
                .build();

        log.info("评分抽取开始: candidateId={}, runId={}", candidateId, runId);
        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), EXTRACT_TIMEOUT, context
        );
        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "评分抽取超时（candidate=" + candidateId + ", >" + EXTRACT_TIMEOUT.toMinutes() + "min）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分抽取失败 (candidate=" + candidateId + "): " + firstSummary(result.getStderr(), "未知错误")
            );
        }
    }

    /**
     * 全候选抽取完成后跑一次评分。
     *
     * @param runId         与抽取共享的 runId（用于 eval-dir 推断）
     * @param auditFile     audit_with_gold.json 路径
     * @param workspaceDir  log 文件输出目录
     */
    public void runScoring(
            String runId,
            Path auditFile,
            Path workspaceDir
    ) throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.extraction_eval.score_extraction_results");
        argv.add("--audit");
        argv.add(auditFile.toAbsolutePath().toString());
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("score.log");
        ProcessContext context = ProcessContext.builder()
                .operation("extraction-eval-score")
                .logFile(logFile)
                .build();

        log.info("评分汇总开始: runId={}", runId);
        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), SCORE_TIMEOUT, context
        );
        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "评分汇总超时"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分汇总失败: " + firstSummary(result.getStderr(), "未知错误")
            );
        }
    }

    private static String firstSummary(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return fallback;
    }
}
