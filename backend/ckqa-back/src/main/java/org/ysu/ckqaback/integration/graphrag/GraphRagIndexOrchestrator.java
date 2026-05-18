package org.ysu.ckqaback.integration.graphrag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraphRAG 索引相关外部命令编排器。
 */
@Service
@RequiredArgsConstructor
public class GraphRagIndexOrchestrator {

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    public ProcessExecutionResult fetchInput(IndexRuns run, KnowledgeBases knowledgeBase)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveGraphRagPython());
        command.addAll(List.of(
                "utils/fetch_from_minio.py",
                knowledgeBase.getCourseId(),
                "--clean"
        ));
        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getFetchSeconds()),
                ProcessContext.builder()
                        .operation("fetch-input")
                        .indexRunId(run.getId())
                        .build()
        );
    }

    public ProcessExecutionResult runIndex(IndexRuns run) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveGraphRagPython());
        command.addAll(List.of(
                "-m",
                "graphrag",
                "index",
                "--root",
                "."
        ));
        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getIndexSeconds()),
                ProcessContext.builder()
                        .operation("index")
                        .indexRunId(run.getId())
                        .build()
        );
    }

    public ProcessExecutionResult fetchMaterialInput(
            IndexRuns run,
            KnowledgeBases knowledgeBase,
            Long materialId,
            Path graphInputDir,
            String jsonFile,
            String outputFile
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveGraphRagPython());
        command.addAll(List.of(
                "utils/fetch_from_minio.py",
                knowledgeBase.getCourseId(),
                "--material-id",
                String.valueOf(materialId),
                "--json-file",
                jsonFile,
                "--input-dir",
                graphInputDir.toString(),
                "--output-file",
                outputFile
        ));
        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getFetchSeconds()),
                ProcessContext.builder()
                        .operation("fetch-material-input")
                        // run 在 prompt-tune 链路下可能为空（不属于具体 IndexRuns）；
                        // ProcessContext.indexRunId 只是审计字段，允许 null。
                        .indexRunId(run == null ? null : run.getId())
                        .build()
        );
    }

    public ProcessExecutionResult runIndex(IndexRuns run, Path workspaceRoot) throws IOException, InterruptedException {
        return runIndex(run, workspaceRoot, null);
    }

    /**
     * 在 build run 工作区内执行 {@code graphrag index}。
     *
     * @param entityExtractionPromptFile 实体抽取提示词文件绝对路径；为空时使用 graphrag {@code .env}
     *                                   中默认配置（向后兼容）
     */
    public ProcessExecutionResult runIndex(
            IndexRuns run,
            Path workspaceRoot,
            Path entityExtractionPromptFile
    ) throws IOException, InterruptedException {
        Path indexRoot = workspaceRoot.resolve("index");
        Path logsDir = indexRoot.resolve("logs");
        java.nio.file.Files.createDirectories(logsDir);
        List<String> command = new ArrayList<>(resolveGraphRagPython());
        command.addAll(List.of(
                "-m",
                "graphrag",
                "index",
                "--root",
                "."
        ));

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("GRAPHRAG_INPUT_DIR", indexRoot.resolve("input").toString());
        environment.put("GRAPHRAG_OUTPUT_DIR", indexRoot.resolve("output").toString());
        environment.put("GRAPHRAG_STORAGE_DIR", indexRoot.resolve("output").toString());
        environment.put("GRAPHRAG_REPORTING_DIR", indexRoot.resolve("reports").toString());
        environment.put("GRAPHRAG_CACHE_DIR", indexRoot.resolve("cache").toString());
        if (entityExtractionPromptFile != null) {
            // settings.yaml 中 extract_graph.prompt 解析的是相对 GRAPHRAG_ROOT 的路径，
            // 这里直接把工作区下的绝对路径塞进去，CLI 会按绝对路径打开文件。
            environment.put(
                    "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE",
                    entityExtractionPromptFile.toAbsolutePath().toString()
            );
        }

        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                environment,
                Duration.ofSeconds(properties.getTimeout().getIndexSeconds()),
                ProcessContext.builder()
                        .operation("index")
                        .indexRunId(run.getId())
                        .logFile(logsDir.resolve("process.log"))
                        .build()
        );
    }

    private List<String> resolveGraphRagPython() {
        return PythonCommandResolver.resolve(properties.getGraphrag().getPython(), "graphrag-oneapi");
    }

    /**
     * 在 build run 索引成功后调用 {@code utils/index_summary.py}，
     * 读取 parquet 行数与 stats.json 耗时分布，输出单行 JSON。
     *
     * <p>失败不抛错——摘要只是补充展示信息，不能拖累索引主流程。</p>
     *
     * @param outputDir 索引输出目录（{@code <workspace>/index/output}）
     * @return 子进程执行结果；调用方拿 {@link ProcessExecutionResult#getStdout()} 解析 JSON
     */
    public ProcessExecutionResult summarizeIndex(Path outputDir) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveGraphRagPython());
        command.addAll(List.of(
                "utils/index_summary.py",
                "--output-dir",
                outputDir.toAbsolutePath().toString()
        ));
        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(),
                Duration.ofSeconds(60),
                ProcessContext.builder()
                        .operation("index-summary")
                        .build()
        );
    }
}
