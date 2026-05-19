package org.ysu.ckqaback.qa.summary;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QaSummaryClientTest {

    @Test
    void shouldCallOneApiChatCompletionWithDeepSeekFlashAndParseContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://one-api.local/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(containsString("\"model\":\"deepseek-v4-flash\"")))
                .andExpect(content().string(containsString("\"stream\":false")))
                .andExpect(content().string(containsString("\"thinking\":{\"type\":\"disabled\"}")))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "本会话已讨论死锁定义、必要条件和资源分配图。"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        QaSummaryClient client = new QaSummaryClient(
                builder,
                "http://one-api.local/v1",
                "test-key",
                "deepseek-v4-flash",
                800,
                Duration.ofSeconds(5),
                true
        );

        QaSummaryResult result = client.summarize("旧摘要：无", "学生：什么是死锁？\n助手：死锁是...");

        assertThat(result.success()).isTrue();
        assertThat(result.summaryText()).isEqualTo("本会话已讨论死锁定义、必要条件和资源分配图。");
        assertThat(result.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void shouldFailWhenModelReturnsEmptyContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://one-api.local/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "   "
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        QaSummaryClient client = new QaSummaryClient(
                builder,
                "http://one-api.local/v1",
                "test-key",
                "deepseek-v4-flash",
                800,
                Duration.ofSeconds(5),
                true
        );

        QaSummaryResult result = client.summarize("", "学生：什么是死锁？");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("空摘要");
        server.verify();
    }
}
