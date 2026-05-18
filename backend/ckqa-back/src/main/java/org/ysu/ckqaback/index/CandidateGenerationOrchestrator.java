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
 * 同步包装 {@code generate_candidate_prompts.py}，为某个 build run 生成 4 个候选 prompt
 * 到其 workspace 下的 {@code prompt/candidates/} 目录。
 *
 * <p>实测脚本耗时 ~66 ms，无 LLM 调用，纯字符串拼接 + schema 模板。
 * 用 {@link Duration} 60 秒兜底网络/文件 IO 抖动；正常路径几乎瞬完。</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CandidateGenerationOrchestrator.class);

    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(60);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    /**
     * 同步执行候选生成。
     *
     * @param auditWithGoldFile 含 DB gold 的 audit JSON 文件，作为 --audit_file 传入脚本
     * @param outputDir         build run workspace 下的 prompt/candidates 目录（manifest 等输出位置）
     * @param baseOverride      可选的底板路径覆盖；null 时走 Phase 4 默认行为，不附加 --auto_tuned_prompt_dir
     */
    public void run(Path auditWithGoldFile, Path outputDir, BaseOverride baseOverride)
            throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());

        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.prompt_tuning.generate_candidate_prompts");
        argv.add("--audit_file");
        argv.add(auditWithGoldFile.toAbsolutePath().toString());
        argv.add("--output_dir");
        argv.add(outputDir.toAbsolutePath().toString());
        // 把脚本默认会写到仓库根级 results/reports/prompt_generation_report.json 的报告
        // 强制重定向到 build_run workspace 内，实现 build_run 级别隔离。
        Path generationReport = outputDir.resolve("prompt_generation_report.json");
        argv.add("--report_file");
        argv.add(generationReport.toAbsolutePath().toString());
        argv.add("--overwrite");
        if (baseOverride != null) {
            argv.add("--auto_tuned_prompt_dir");
            argv.add(baseOverride.autoTunedPromptDir().toAbsolutePath().toString());
        }

        Path logFile = outputDir.resolve("generate_candidate_prompts.log");
        ProcessContext context = ProcessContext.builder()
                .operation("candidate-generation")
                .logFile(logFile)
                .build();

        log.info("生成候选 Prompt: auditFile={}, outputDir={}", auditWithGoldFile, outputDir);

        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), GENERATE_TIMEOUT, context
        );

        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "候选 Prompt 生成超时（>" + GENERATE_TIMEOUT.toSeconds() + "s）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 Prompt 生成失败: " + firstSummary(result.getStderr(), "未知错误")
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

    /**
     * 候选生成时的底板覆盖。仅影响脚本中以 auto_tuned 优先、default 兜底的候选
     * （schema_aware_directional_v2 / schema_fewshot_distilled_v2_strict_tuple）。
     */
    public record BaseOverride(Path autoTunedPromptDir) {

        /**
         * seed=system_default：把 auto_tuned 目录指向一个一定不存在的子目录，
         * 强制脚本走 default 分支。
         *
         * @param outputDir build run workspace 下的 prompt/candidates 目录
         *                  （借用此目录拼一个绝对不会存在的子路径）
         */
        public static BaseOverride systemDefault(Path outputDir) {
            return new BaseOverride(outputDir.resolve("_disabled_auto_tuned"));
        }

        /**
         * seed=graphrag_tuned：指向 prompt-tune cache 命中目录，目录下含 extract_graph.txt。
         */
        public static BaseOverride graphragTuned(Path autoTunedPromptDir) {
            return new BaseOverride(autoTunedPromptDir);
        }
    }
}
