package org.ysu.ckqaback.integration.graphrag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.io.IOException;
import java.nio.file.Path;
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
        return processRunner.run(
                List.of(
                        properties.getGraphrag().getPython(),
                        "utils/fetch_from_minio.py",
                        knowledgeBase.getCourseId(),
                        "--clean"
                ),
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
        return processRunner.run(
                List.of(
                        properties.getGraphrag().getPython(),
                        "-m",
                        "graphrag",
                        "index",
                        "--root",
                        "."
                ),
                Path.of(properties.getGraphrag().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getIndexSeconds()),
                ProcessContext.builder()
                        .operation("index")
                        .indexRunId(run.getId())
                        .build()
        );
    }
}
