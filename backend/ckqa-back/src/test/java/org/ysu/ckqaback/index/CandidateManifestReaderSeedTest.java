package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateManifestReaderSeedTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SeedInfoStore seedInfoStore = new SeedInfoStore(objectMapper);
    private final CandidateManifestReader reader =
            new CandidateManifestReader(new CandidateMetadataLookup(), objectMapper, seedInfoStore);

    @Test
    void injectsSeedFromSeedInfoFileWhenPresent(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {"candidates":[{"candidate_name":"default","schema_used":false}]}
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");
        Files.writeString(candidatesDir.resolve("seed-info.json"), """
                {"seed":"graphrag_tuned","autoTunedPromptDir":"/cache/run_3","buildRunId":18}
                """);

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void seedIsNullWhenSeedInfoFileMissing(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {"candidates":[{"candidate_name":"default","schema_used":false}]}
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSeed()).isNull();
    }
}
