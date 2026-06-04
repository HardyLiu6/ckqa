package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
                          "prompt": "请概括这套图谱的主题",
                          "retrievalQuery": "请概括这套图谱的主题"
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
                          "retrievalQuery": "问题",
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
    void shouldCreateQueryTaskWithDualInputContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(not(containsString("topicEntity"))))
                .andExpect(content().string(not(containsString("semanticState"))))
                .andExpect(content().string(not(containsString("entityCandidates"))))
                .andExpect(content().json("""
                        {
                          "mode": "basic",
                          "prompt": "死锁和资源分配图有什么关系？",
                          "retrievalQuery": "死锁和资源分配图有什么关系？",
                          "generationContext": "最近对话：什么是死锁？",
                          "indexRunId": 18,
                          "dataDirUri": "user_2/kb_5/build_27/index/output"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260517_000001_001",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-05-17T20:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask(
                "basic",
                "死锁和资源分配图有什么关系？",
                18L,
                "user_2/kb_5/build_27/index/output",
                "最近对话：什么是死锁？"
        );

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260517_000001_001");
        server.verify();
    }

    @Test
    void shouldCreateQueryTaskWithLocalHistoryStrategyAndConversationHistory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(not(containsString("contextToken"))))
                .andExpect(content().string(not(containsString("memoryToken"))))
                .andExpect(content().string(not(containsString("tokenizer"))))
                .andExpect(content().string(not(containsString("budget"))))
                .andExpect(content().json("""
                        {
                          "mode": "local",
                          "prompt": "时间片轮转为什么会影响响应时间？",
                          "retrievalQuery": "时间片轮转为什么会影响响应时间？",
                          "generationContext": "最近对话：调度算法",
                          "queryEngineStrategy": "local_history",
                          "conversationHistory": [
                            {
                              "role": "user",
                              "content": "什么是时间片轮转？"
                            },
                            {
                              "role": "assistant",
                              "content": "学习记忆：偏好用步骤化解释调度算法。"
                            }
                          ],
                          "indexRunId": 18,
                          "dataDirUri": "user_2/kb_5/build_27/index/output"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260520_000001_001",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-05-20T20:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask(
                "local",
                "时间片轮转为什么会影响响应时间？",
                18L,
                "user_2/kb_5/build_27/index/output",
                "最近对话：调度算法",
                "local_history",
                List.of(
                        new GraphRagConversationMessage("user", "什么是时间片轮转？"),
                        new GraphRagConversationMessage("assistant", "学习记忆：偏好用步骤化解释调度算法。")
                )
        );

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260520_000001_001");
        server.verify();
    }

    @Test
    void shouldCreateQueryTaskWithNativeStreamFlag() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "mode": "hybrid_v0",
                          "prompt": "什么是死锁？",
                          "retrievalQuery": "什么是死锁？",
                          "streamResponse": true,
                          "streamSource": "native_graphrag",
                          "indexRunId": 18,
                          "dataDirUri": "user_2/kb_5/build_27/index/output"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_20260520_000001_002",
                          "taskStatus": "pending",
                          "progressStage": "queued",
                          "createdAt": "2026-05-20T20:20:34"
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagTaskCreateResult result = client.createTask(
                "hybrid_v0",
                "什么是死锁？",
                18L,
                "user_2/kb_5/build_27/index/output",
                null,
                null,
                null,
                true
        );

        assertThat(result.pythonTaskId()).isEqualTo("qt_20260520_000001_002");
        server.verify();
    }

    @Test
    void shouldParsePythonTaskEventStream() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks/qt_1/events?afterEventSeq=7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        event: ack
                        data: {"pythonTaskId":"qt_1"}

                        id: 8
                        event: progress
                        data: {"type":"context_selected","mode":"basic","summary":"已选取 1 个课程片段作为回答依据。","metrics":{"textUnitCount":1},"evidence":[{"kind":"text_unit","title":"操作系统教材"}],"eventSeq":8}

                        id: 9
                        event: delta
                        data: {"text":"死锁","eventSeq":9}

                        id: 10
                        event: done
                        data: {"taskStatus":"success","eventSeq":10}

                        """, MediaType.TEXT_EVENT_STREAM));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        List<GraphRagTaskEvent> events = new ArrayList<>();
        client.streamTaskEvents("qt_1", 7L, events::add);

        assertThat(events).extracting(GraphRagTaskEvent::eventName)
                .containsExactly("ack", "progress", "delta", "done");
        assertThat(events.get(1).data().get("summary").asText()).contains("课程片段");
        assertThat(events.get(1).eventSeq()).isEqualTo(8L);
        assertThat(events.get(2).data().get("text").asText()).isEqualTo("死锁");
        assertThat(events.get(2).eventSeq()).isEqualTo(9L);
        assertThat(events.get(3).eventSeq()).isEqualTo(10L);
        server.verify();
    }

    @Test
    void shouldMapStructuredProgressEventsFromSnapshot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/query-tasks/qt_progress"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "pythonTaskId": "qt_progress",
                          "taskStatus": "running",
                          "progressStage": "streaming",
                          "processAlive": true,
                          "createdAt": "2026-05-27T15:20:00",
                          "latestLogs": ["已选取 1 个课程片段作为回答依据。"],
                          "progressEvents": [
                            {
                              "type": "context_selected",
                              "mode": "basic",
                              "summary": "已选取 1 个课程片段作为回答依据。",
                              "metrics": {"textUnitCount": 1},
                              "evidence": [{"kind": "text_unit", "title": "操作系统教材"}],
                              "eventSeq": 8
                            }
                          ],
                          "streamEventSeq": 8
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        Optional<GraphRagTaskSnapshot> snapshot = client.getTask("qt_progress");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().progressEvents()).hasSize(1);
        assertThat(snapshot.get().progressEvents().get(0).summary()).contains("课程片段");
        assertThat(snapshot.get().progressEvents().get(0).metrics().get("textUnitCount")).isEqualTo(1);
        assertThat(snapshot.get().progressEvents().get(0).evidence().get(0).get("title")).isEqualTo("操作系统教材");
        assertThat(snapshot.get().progressEvents().get(0).eventSeq()).isEqualTo(8L);
        server.verify();
    }

    @Test
    void shouldWarmupHybridV0WithBackendOnlyDataDirUri() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/hybrid-v0/warmup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"dataDirUri\":\"user_2/kb_5/build_27/index/output\"")))
                .andExpect(content().string(not(containsString("/home/"))))
                .andRespond(withSuccess("""
                        {
                          "ready": true,
                          "status": "ready",
                          "dataDirUri": "user_2/kb_5/build_27/index/output",
                          "cached": true,
                          "textUnitsReady": true,
                          "missing": []
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagHybridReadinessResult result = client.warmupHybridV0("user_2/kb_5/build_27/index/output");

        assertThat(result.ready()).isTrue();
        assertThat(result.status()).isEqualTo("ready");
        assertThat(result.textUnitsReady()).isTrue();
        server.verify();
    }

    @Test
    void shouldFetchHybridV0ReadinessWithEncodedDataDirUri() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/hybrid-v0/readiness?dataDirUri=user_2/kb_5/build_27/index/output"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "ready": false,
                          "status": "not_ready",
                          "dataDirUri": "user_2/kb_5/build_27/index/output",
                          "cached": false,
                          "textUnitsReady": false,
                          "missing": ["text_units.parquet"]
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagTaskClient client = new GraphRagTaskClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(5));
        GraphRagHybridReadinessResult result = client.getHybridV0Readiness("user_2/kb_5/build_27/index/output");

        assertThat(result.ready()).isFalse();
        assertThat(result.missing()).containsExactly("text_units.parquet");
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
                          "sources": [
                            {
                              "rank": 1,
                              "kind": "bm25",
                              "source_type": "bm25",
                              "ref": "156",
                              "chunk_id": "chunk-1",
                              "document_key": "doc-1",
                              "source_file": "操作系统教材",
                              "heading_path": "第3章/死锁",
                              "page_start": 123,
                              "page_end": 124,
                              "snippet": "死锁相关片段"
                            }
                          ],
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
            assertThat(value.sources()).hasSize(1);
            assertThat(value.sources().get(0).sourceType()).isEqualTo("bm25");
            assertThat(value.sources().get(0).sourceFile()).isEqualTo("操作系统教材");
            assertThat(value.returnCode()).isEqualTo(0);
            assertThat(value.lastHeartbeatAt()).isEqualTo(LocalDateTime.of(2026, 4, 22, 20, 20, 36));
            assertThat(value.isTerminal()).isTrue();
        });
        server.verify();
    }
}
