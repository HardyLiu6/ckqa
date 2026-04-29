package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedGraphRagApiServerTest {

    @TempDir
    Path graphRagRoot;

    @Test
    void shouldBuildCondaCommandAndRuntimeEnvironmentFromManagedApiConfig() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphRagRoot.toString());
        properties.getGraphrag().setApiBaseUrl("http://127.0.0.1:18012");
        properties.getGraphrag().getManagedApi().setEnabled(true);
        properties.getGraphrag().getManagedApi().setPort(18012);

        ManagedGraphRagApiServer server = new ManagedGraphRagApiServer(properties, RestClient.builder());

        ManagedGraphRagApiServer.StartPlan plan = server.buildStartPlan();

        assertThat(plan.workDir()).isEqualTo(graphRagRoot);
        assertThat(plan.command()).containsExactly(
                "conda",
                "run",
                "-n",
                "graphrag-oneapi",
                "python",
                "utils/main.py"
        );
        assertThat(plan.environment())
                .containsEntry("GRAPHRAG_API_HOST", "127.0.0.1")
                .containsEntry("GRAPHRAG_API_PORT", "18012")
                .containsEntry("GRAPHRAG_OUTPUT_DIR", graphRagRoot.resolve("output").toString())
                .containsEntry("GRAPHRAG_STORAGE_DIR", graphRagRoot.resolve("output").toString())
                .containsEntry("GRAPHRAG_LANCEDB_URI", graphRagRoot.resolve("output/lancedb").toString());
    }

    @Test
    void shouldPreferConfiguredPythonOverCondaCommand() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphRagRoot.toString());
        properties.getGraphrag().setPython("/opt/conda/envs/graphrag-oneapi/bin/python");
        properties.getGraphrag().getManagedApi().setEnabled(true);

        ManagedGraphRagApiServer server = new ManagedGraphRagApiServer(properties, RestClient.builder());

        ManagedGraphRagApiServer.StartPlan plan = server.buildStartPlan();

        assertThat(plan.command()).containsExactly(
                "/opt/conda/envs/graphrag-oneapi/bin/python",
                "utils/main.py"
        );
    }
}
