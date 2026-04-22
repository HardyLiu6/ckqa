package org.ysu.ckqaback.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SystemHealthServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnUnreachableItemWhenGraphRagApiThrowsException() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        given(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).willReturn(1);

        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPdfIngest().setRoot("/tmp/pdf_ingest");
        properties.getGraphrag().setRoot("/tmp/graphrag");
        properties.getGraphrag().setApiBaseUrl("http://127.0.0.1:8012");

        RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_DEEP_STUBS);
        given(builder.build().get().uri("http://127.0.0.1:8012/health").retrieve().toBodilessEntity())
                .willThrow(new RuntimeException("connect refused"));

        SystemHealthService service = new SystemHealthService(jdbcTemplate, properties, builder);
        SystemHealthResponse response = service.check();

        assertThat(response.getItems()).anySatisfy(item -> {
            assertThat(item.getName()).isEqualTo("graphrag-api");
            assertThat(item.isReachable()).isFalse();
        });
    }

    @Test
    void shouldReportGraphRagOutputNotReadyWhenArtifactsMissing() throws IOException {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        given(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).willReturn(1);

        Path pdfRoot = Files.createDirectories(tempDir.resolve("pdf_ingest"));
        Path graphragRoot = Files.createDirectories(tempDir.resolve("graphrag"));

        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPdfIngest().setRoot(pdfRoot.toString());
        properties.getGraphrag().setRoot(graphragRoot.toString());
        properties.getGraphrag().setApiBaseUrl("http://127.0.0.1:8012");

        RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_DEEP_STUBS);
        given(builder.build().get().uri("http://127.0.0.1:8012/health").retrieve().toBodilessEntity())
                .willReturn(null);
        given(builder.build().get().uri("http://127.0.0.1:8012/v1/models").retrieve().toEntity(String.class))
                .willReturn(null);

        SystemHealthService service = new SystemHealthService(jdbcTemplate, properties, builder);
        SystemHealthResponse response = service.check();

        assertThat(response.getItems()).anySatisfy(item -> {
            assertThat(item.getName()).isEqualTo("graphrag-output");
            assertThat(item.isReachable()).isTrue();
            assertThat(item.isReady()).isFalse();
        });
    }
}
