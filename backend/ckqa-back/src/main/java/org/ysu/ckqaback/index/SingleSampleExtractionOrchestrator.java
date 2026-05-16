package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单样本 GraphRAG 抽取脚本包装。
 * <p>
 * 用于 02 步 AI 预填功能：把一条 audit 样本喂给 {@code run_native_extraction.py --limit 1}，
 * 得到候选实体和候选关系。和 {@link AuditPipelineOrchestrator} 同模式：
 * 同步串行调用 Python 子进程，调用方需保证不在 {@code @Transactional} 中。
 * </p>
 * <p>
 * 输出路径约定：{@code <GRAPHRAG_ROOT>/results/extraction_eval/<runId>/<candidate-name>.json}。
 * 通过 {@code --run-id} 参数实现确定性路径，不解析 stdout，避免脚本日志格式变更带来脆弱性。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SingleSampleExtractionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SingleSampleExtractionOrchestrator.class);

    /**
     * 单样本抽取的最长执行时长。一次 LLM 调用通常 10-30 秒，预留 2 分钟兜底网络抖动。
     */
    private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 输出文件 candidate 名（与 --candidate-name 参数一致），固定为 ai_suggestion。
     */
    private static final String CANDIDATE_NAME = "ai_suggestion";

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对单条样本执行 GraphRAG 抽取，返回候选实体/关系。
     *
     * @param sampleJson         一条样本对象（含 source_sample_id / text / heading_path / page_*）
     * @param entityTypes        逗号分隔的实体类型列表，如 "Course,Concept,Term,..."
     * @param promptFile         GraphRAG 抽取 prompt 文件绝对路径（通常 prompts/extract_graph.txt）
     * @param workspaceDir       临时工作目录，用于存放输入/日志
     * @param runId              自定义 run id，决定输出文件路径
     *                           ({@code <GRAPHRAG_ROOT>/results/extraction_eval/<runId>/ai_suggestion.json})
     */
    public ExtractionResult runSingleExtract(
            Map<String, Object> sampleJson,
            String entityTypes,
            Path promptFile,
            Path workspaceDir,
            String runId
    ) throws IOException, InterruptedException {
        Files.createDirectories(workspaceDir);

        // 1. 构造临时 audit_samples.json（包一层 audit_samples 数组）
        Path samplesFile = workspaceDir.resolve("single_sample.json");
        Map<String, Object> samplesPayload = new LinkedHashMap<>();
        samplesPayload.put("audit_samples", List.of(sampleJson));
        Files.writeString(
                samplesFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(samplesPayload),
                StandardCharsets.UTF_8
        );

        // 2. 构造命令行
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("scripts/extraction_eval/run_native_extraction.py");
        argv.add("--samples-file");
        argv.add(samplesFile.toAbsolutePath().toString());
        argv.add("--prompt");
        argv.add(promptFile.toAbsolutePath().toString());
        argv.add("--candidate-name");
        argv.add(CANDIDATE_NAME);
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--entity-types");
        argv.add(entityTypes);
        argv.add("--limit");
        argv.add("1");
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("ai_suggestion.log");
        ProcessContext context = ProcessContext.builder()
                .operation("ai-suggestion-extract")
                .logFile(logFile)
                .build();

        log.info("运行 AI 单样本抽取: samplesFile={}, prompt={}, runId={}", samplesFile, promptFile, runId);

        ProcessExecutionResult result = processRunner.run(
                argv,
                scriptRoot,
                Map.of(),
                EXTRACT_TIMEOUT,
                context
        );

        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 候选生成超时（>" + EXTRACT_TIMEOUT.toSeconds() + "s）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选生成失败: " + firstSummary(result.getStderr(), "未知错误")
            );
        }

        // 3. 读输出文件——固定路径 <GRAPHRAG_ROOT>/results/extraction_eval/<runId>/ai_suggestion.json
        Path outputFile = scriptRoot
                .resolve("results")
                .resolve("extraction_eval")
                .resolve(runId)
                .resolve(CANDIDATE_NAME + ".json");
        if (!Files.exists(outputFile)) {
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选输出文件不存在：" + outputFile
            );
        }

        return parseExtractionOutput(outputFile);
    }

    private ExtractionResult parseExtractionOutput(Path outputFile) throws IOException {
        String json = Files.readString(outputFile, StandardCharsets.UTF_8);
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
        // run_native_extraction 输出结构：
        // { "samples": [ { "id": "...", "extraction": { "entities": [...], "relations": [...] } } ] }
        Object samplesNode = root.get("samples");
        if (!(samplesNode instanceof List<?> samplesList) || samplesList.isEmpty()) {
            return ExtractionResult.builder()
                    .entities(List.of())
                    .relations(List.of())
                    .build();
        }
        Object first = samplesList.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return ExtractionResult.builder().entities(List.of()).relations(List.of()).build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> firstSample = (Map<String, Object>) firstMap;
        Object extraction = firstSample.get("extraction");
        if (!(extraction instanceof Map<?, ?> extractionRaw)) {
            return ExtractionResult.builder().entities(List.of()).relations(List.of()).build();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> extractionMap = (Map<String, Object>) extractionRaw;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>)
                extractionMap.getOrDefault("entities", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>)
                extractionMap.getOrDefault("relations", List.of());

        return ExtractionResult.builder()
                .entities(entities)
                .relations(relations)
                .build();
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
     * 单样本抽取结果。
     */
    @Getter
    @Builder
    public static class ExtractionResult {
        private final List<Map<String, Object>> entities;
        private final List<Map<String, Object>> relations;
    }
}
