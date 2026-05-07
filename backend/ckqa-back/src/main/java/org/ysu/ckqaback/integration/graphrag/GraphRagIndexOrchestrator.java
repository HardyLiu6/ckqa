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
                        .indexRunId(run.getId())
                        .build()
        );
    }

    public ProcessExecutionResult runIndex(IndexRuns run, Path workspaceRoot) throws IOException, InterruptedException {
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
        return processRunner.run(
                command,
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(
                        "GRAPHRAG_INPUT_DIR", indexRoot.resolve("input").toString(),
                        "GRAPHRAG_OUTPUT_DIR", indexRoot.resolve("output").toString(),
                        "GRAPHRAG_STORAGE_DIR", indexRoot.resolve("output").toString(),
                        "GRAPHRAG_REPORTING_DIR", indexRoot.resolve("reports").toString(),
                        "GRAPHRAG_CACHE_DIR", indexRoot.resolve("cache").toString()
                ),
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
}
