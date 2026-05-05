package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphRagTaskClientTest {

    @Test
    void shouldCreateQueryTaskAndMapCreateResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "mode": "global",
                          "prompt": "请概括这套图谱的主题"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260422_000001_001",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-04-22T20:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask("global", "请概括这套图谱的主题");

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260422_000001_001");
        assertThat(result.taskStatus()).isEqualTo("pending");
        assertThat(result.progressStage()).isEqualTo("queued");
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 4, 22, 20, 20, 34));
        server.verify();
    }

    @Test
    void shouldCreateQueryTaskWithBackendOnlyIndexContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"indexRunId\":18")))
                .andExpect(content().string(containsString("\"dataDirUri\":\"user_2/kb_5/build_27/index/output\"")))
                .andExpect(content().string(not(containsString("/home/"))))
                .andExpect(content().json("""
                        {
                          "mode": "basic",
                          "prompt": "问题",
                          "indexRunId": 18,
                          "dataDirUri": "user_2/kb_5/build_27/index/output"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260505_000001_001",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-05-05T20:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask(
                "basic",
                "问题",
                18L,
                "user_2/kb_5/build_27/index/output"
        );

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260505_000001_001");
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenPythonTaskSnapshotIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks/qt_missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        Optional<GraphRagTaskSnapshot> snapshot = client.getTask("qt_missing");

        assertThat(snapshot).isEmpty();
        server.verify();
    }

    @Test
    void shouldMapPythonTaskSnapshotResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks/qt_20260422_000001_001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260422_000001_001",
                          "mode": "global",
                          "prompt": "请概括这套图谱的主题",
                          "taskStatus": "success",
                          "progressStage": "done",
                          "processAlive": false,
                          "createdAt": "2026-04-22T20:20:34",
                          "startedAt": "2026-04-22T20:20:35",
                          "lastHeartbeatAt": "2026-04-22T20:20:36",
                          "finishedAt": "2026-04-22T20:20:37",
                          "latestLogs": ["started", "done"],
                          "resultText": "图谱主题是操作系统",
                          "errorMessage": null,
                          "returnCode": 0
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        Optional<GraphRagTaskSnapshot> snapshot = client.getTask("qt_20260422_000001_001");

        assertThat(snapshot).isPresent();
        assertThat(snapshot).get().satisfies(value -> {
            assertThat(value.pythonTaskId()).isEqualTo("qt_20260422_000001_001");
            assertThat(value.taskStatus()).isEqualTo("success");
            assertThat(value.progressStage()).isEqualTo("done");
            assertThat(value.processAlive()).isFalse();
            assertThat(value.latestLogs()).containsExactly("started", "done");
            assertThat(value.resultText()).isEqualTo("图谱主题是操作系统");
            assertThat(value.returnCode()).isEqualTo(0);
            assertThat(value.lastHeartbeatAt()).isEqualTo(LocalDateTime.of(2026, 4, 22, 20, 20, 36));
            assertThat(value.isTerminal()).isTrue();
        });
        server.verify();
    }
}
