package org.ysu.ckqaback.qa.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QaQuestionRewriteClientTest {

    @Test
    void shouldSendNonStreamingDeepseekRewriteRequestAndParseStrictJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:3000/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("\"model\":\"deepseek-v4-flash\"")))
                .andExpect(content().string(containsString("\"stream\":false")))
                .andExpect(content().string(containsString("\"response_format\":{\"type\":\"json_object\"}")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"standaloneQuery\\":\\"死锁和资源分配图有什么关系？\\",\\"confidence\\":0.93,\\"reason\\":\\"消解它\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        QaQuestionRewriteClient client = new QaQuestionRewriteClient(
                builder,
                new ObjectMapper(),
                "http://127.0.0.1:3000/v1",
                "test-key",
                "deepseek-v4-flash",
                800,
                true
        );
        QaContextAssembly context = new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2");

        QaLlmQuestionRewriteResult result = client.rewrite("它和资源分配图有什么关系？", context);

        assertThat(result.success()).isTrue();
        assertThat(result.standaloneQueryText()).isEqualTo("死锁和资源分配图有什么关系？");
        assertThat(result.confidence()).isEqualTo(0.93D);
        assertThat(result.model()).isEqualTo("deepseek-v4-flash");
        server.verify();
    }

    @Test
    void shouldReturnFailureWhenModelContentIsNotJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:3000/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "死锁和资源分配图有什么关系？"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        QaQuestionRewriteClient client = new QaQuestionRewriteClient(
                builder,
                new ObjectMapper(),
                "http://127.0.0.1:3000/v1",
                "",
                "deepseek-v4-flash",
                800,
                true
        );

        QaLlmQuestionRewriteResult result = client.rewrite(
                "它和资源分配图有什么关系？",
                new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2")
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("非严格 JSON");
        server.verify();
    }
}
