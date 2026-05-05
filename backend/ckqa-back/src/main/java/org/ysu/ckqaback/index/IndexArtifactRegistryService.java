package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.service.IndexArtifactsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描并登记单次索引运行的本地产物。
 */
@Service
@RequiredArgsConstructor
public class IndexArtifactRegistryService {

    private final IndexArtifactsService artifactsService;

    public List<IndexArtifacts> scanAndRegister(IndexRuns run, Path workspaceRoot, String workspaceUri) throws IOException {
        artifactsService.removeByIndexRunId(run.getId());
        List<IndexArtifacts> artifacts = new ArrayList<>();

        registerGlob(artifacts, run, workspaceRoot, workspaceUri, "input_json", "index/input", "*.json");
        registerPath(artifacts, run, workspaceRoot, workspaceUri, "output_dir", "index/output");
        registerPath(artifacts, run, workspaceRoot, workspaceUri, "lancedb", "index/output/lancedb");
        registerGlob(artifacts, run, workspaceRoot, workspaceUri, "parquet", "index/output", "*.parquet");
        registerGlob(artifacts, run, workspaceRoot, workspaceUri, "report", "index/reports", "*");
        registerPath(artifacts, run, workspaceRoot, workspaceUri, "log", "index/logs/process.log");
        registerPath(artifacts, run, workspaceRoot, workspaceUri, "manifest", "manifest.json");

        artifactsService.saveBatch(artifacts);
        return artifacts;
    }

    private void registerGlob(
            List<IndexArtifacts> artifacts,
            IndexRuns run,
            Path workspaceRoot,
            String workspaceUri,
            String artifactType,
            String directory,
            String pattern
    ) throws IOException {
        Path dir = workspaceRoot.resolve(directory);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(dir, pattern)) {
            for (Path path : stream) {
                registerPath(artifacts, run, workspaceRoot, workspaceUri, artifactType, workspaceRoot.relativize(path).toString());
            }
        }
    }

    private void registerPath(
            List<IndexArtifacts> artifacts,
            IndexRuns run,
            Path workspaceRoot,
            String workspaceUri,
            String artifactType,
            String relativePath
    ) throws IOException {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        boolean exists = Files.exists(path);

        IndexArtifacts artifact = new IndexArtifacts();
        artifact.setIndexRunId(run.getId());
        artifact.setArtifactType(artifactType);
        artifact.setDisplayName(path.getFileName() == null ? artifactType : path.getFileName().toString());
        artifact.setStorageUri(toStorageUri(workspaceUri, relativePath));
        artifact.setStorageScope("local");
        artifact.setArtifactStatus(exists ? "ready" : "missing");
        artifact.setFileSize(exists && Files.isRegularFile(path) ? Files.size(path) : 0L);
        artifacts.add(artifact);
    }

    private String toStorageUri(String workspaceUri, String relativePath) {
        String normalizedRelative = relativePath.replace('\\', '/');
        return workspaceUri + "/" + normalizedRelative;
    }
}
