package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BuildRunWorkspaceServiceTest {

    @Test
    void createLayoutIncludesPromptCandidatesDir(@TempDir Path tmp) throws Exception {
        BuildRunWorkspaceService service = new BuildRunWorkspaceService(tmp.toString());
        String workspaceUri = service.workspaceUri(0L, 5L, 18L);
        service.createLayout(workspaceUri);

        Path workspace = service.resolve(workspaceUri);
        assertThat(Files.isDirectory(workspace.resolve("prompt").resolve("candidates"))).isTrue();
        // 现有目录也保留
        assertThat(Files.isDirectory(workspace.resolve("prompt"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve("index/output"))).isTrue();
    }
}
