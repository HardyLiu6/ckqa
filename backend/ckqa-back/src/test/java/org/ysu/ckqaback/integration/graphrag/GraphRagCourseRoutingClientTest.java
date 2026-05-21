package org.ysu.ckqaback.integration.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphRagCourseRoutingClientTest {

    @Test
    void shouldRequestCourseProfileHintsFromInternalEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8012");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:8012/v1/internal/course-routing/profile-hints"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "courseId": "course-os",
                          "dataDirUris": ["user_0/kb_5/build_19/index/output"],
                          "maxHints": 3,
                          "seedKeywords": ["操作系统"]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "heading": "第六章 输入输出系统 > 6.3 中断机构和中断处理程序",
                              "keywords": ["I/O", "设备驱动程序", "中断", "轮询"],
                              "sourceType": "text_units",
                              "sourceRef": "249",
                              "score": 4.0
                            }
                          ],
                          "sourceCounts": {"text_units": 1}
                        }
                        """, MediaType.APPLICATION_JSON));

        GraphRagCourseRoutingClient client = new GraphRagCourseRoutingClient(builder.build());

        GraphRagCourseProfileHintsResponse response = client.extractProfileHints(new GraphRagCourseProfileHintsRequest(
                "course-os",
                List.of("user_0/kb_5/build_19/index/output"),
                3,
                List.of("操作系统")
        ));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().keywords()).contains("设备驱动程序", "轮询");
        assertThat(response.sourceCounts()).containsEntry("text_units", 1);
        server.verify();
    }
}
