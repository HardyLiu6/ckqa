package org.ysu.ckqaback.integration.graphrag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.time.Duration;
import java.util.List;

/**
 * GraphRAG Python 内部课程路由客户端。
 */
@Service
public class GraphRagCourseRoutingClient {

    private final RestClient restClient;

    @Autowired
    public GraphRagCourseRoutingClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(Duration.ofSeconds(10).toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(Duration.ofSeconds(
                Math.max(30L, properties.getTimeout().getQuerySeconds())
        ).toMillis()));
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
    }

    GraphRagCourseRoutingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public GraphRagCourseRoutingProfileUpsertResponse upsertProfiles(
            List<GraphRagCourseRoutingProfileUpsertRequest.Item> profiles
    ) {
        return restClient.post()
                .uri("/v1/internal/course-routing/profiles/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GraphRagCourseRoutingProfileUpsertRequest(profiles))
                .retrieve()
                .body(GraphRagCourseRoutingProfileUpsertResponse.class);
    }

    public GraphRagCourseRoutingRecommendResponse recommend(GraphRagCourseRoutingRecommendRequest request) {
        return restClient.post()
                .uri("/v1/internal/course-routing/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GraphRagCourseRoutingRecommendResponse.class);
    }

    public GraphRagCourseProfileHintsResponse extractProfileHints(GraphRagCourseProfileHintsRequest request) {
        return restClient.post()
                .uri("/v1/internal/course-routing/profile-hints")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GraphRagCourseProfileHintsResponse.class);
    }
}
