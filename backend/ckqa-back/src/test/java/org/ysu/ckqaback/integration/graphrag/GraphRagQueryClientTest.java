package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphRagQueryClientTest {

    @Test
    void shouldMapLocalModeToLocalModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": { "role": "assistant", "content": "答案" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagQueryClient client = new GraphRagQueryClient(builder, "http://127.0.0.1:8012", Duration.ofSeconds(120));
        GraphRagChatResult result = client.query("local", "什么是进程");

        assertThat(result.content()).isEqualTo("答案");
    }
}
