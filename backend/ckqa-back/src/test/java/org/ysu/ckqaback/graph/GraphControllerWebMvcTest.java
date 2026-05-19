package org.ysu.ckqaback.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.graph.dto.GraphCommunityOverview;
import org.ysu.ckqaback.graph.dto.GraphCommunityRef;
import org.ysu.ckqaback.graph.dto.GraphEdgeResponse;
import org.ysu.ckqaback.graph.dto.GraphEntityDetailResponse;
import org.ysu.ckqaback.graph.dto.GraphLimitInfo;
import org.ysu.ckqaback.graph.dto.GraphNeighborhoodResponse;
import org.ysu.ckqaback.graph.dto.GraphNodeResponse;
import org.ysu.ckqaback.graph.dto.GraphOverviewResponse;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GraphController 路由层薄壳测试。
 * <p>
 * 业务逻辑（活动索引校验、Neo4j 查询、长字段截断）走 {@link GraphServiceTest}，
 * 此处仅校验路由 / 参数绑定 / 错误码透传。
 * </p>
 */
class GraphControllerWebMvcTest {

    private GraphService graphService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        graphService = Mockito.mock(GraphService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GraphController(graphService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnOverview() throws Exception {
        GraphNodeResponse node = GraphNodeResponse.of("e-1", "进程", "Concept", 7L, 12);
        GraphEdgeResponse edge = GraphEdgeResponse.of("r-1", "e-1", "e-2", 0.7, "describes");
        GraphCommunityOverview community = new GraphCommunityOverview(7L, "进程与线程", 0.92,
                "顶层社区摘要", List.of(node));
        GraphOverviewResponse response = new GraphOverviewResponse(
                3L, 18L, 0,
                List.of(community),
                List.of(node),
                List.of(edge),
                new GraphLimitInfo(1, 1, 100, 240)
        );
        given(graphService.getOverview(eq(3L), eq(0), eq(20))).willReturn(response);

        mockMvc.perform(get("/api/v1/knowledge-bases/3/graph/overview")
                        .param("level", "0")
                        .param("topN", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(3))
                .andExpect(jsonPath("$.data.indexRunId").value(18))
                .andExpect(jsonPath("$.data.communities[0].title").value("进程与线程"))
                .andExpect(jsonPath("$.data.nodes[0].id").value("e-1"))
                .andExpect(jsonPath("$.data.edges[0].weight").value(0.7))
                .andExpect(jsonPath("$.data.limits.nodeLimit").value(100));
    }

    @Test
    void shouldReturnConflictWhenKnowledgeBaseNotReady() throws Exception {
        willThrow(new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT))
                .given(graphService).getOverview(eq(99L), eq(null), eq(null));

        mockMvc.perform(get("/api/v1/knowledge-bases/99/graph/overview"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4097));
    }

    @Test
    void shouldReturnNeighborhood() throws Exception {
        GraphNeighborhoodResponse response = new GraphNeighborhoodResponse(
                "e-1",
                List.of(
                        GraphNodeResponse.of("e-1", "进程", "Concept", null, 12),
                        GraphNodeResponse.of("e-2", "线程", "Concept", null, 8)
                ),
                List.of(GraphEdgeResponse.of("r-1", "e-1", "e-2", 0.81, "shares")),
                new GraphLimitInfo(2, 1, 200, 400)
        );
        given(graphService.getEntityNeighborhood(eq(3L), eq("e-1"), eq(1), eq(50)))
                .willReturn(response);

        mockMvc.perform(get("/api/v1/knowledge-bases/3/graph/entities/e-1/neighborhood")
                        .param("depth", "1")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.centerId").value("e-1"))
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.edges[0].weight").value(0.81));
    }

    @Test
    void shouldReturnEntityDetail() throws Exception {
        GraphEntityDetailResponse response = new GraphEntityDetailResponse(
                "e-1", "进程", "Concept", "完整描述",
                42L,
                List.of(new GraphCommunityRef(0, 7L, "进程与线程")),
                12L
        );
        given(graphService.getEntityDetail(eq(3L), eq("e-1"))).willReturn(response);

        mockMvc.perform(get("/api/v1/knowledge-bases/3/graph/entities/e-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("e-1"))
                .andExpect(jsonPath("$.data.description").value("完整描述"))
                .andExpect(jsonPath("$.data.communityPath[0].communityId").value(7))
                .andExpect(jsonPath("$.data.chunkCount").value(12));
    }

    @Test
    void shouldReturnNotFoundWhenEntityMissing() throws Exception {
        willThrow(new BusinessException(ApiResultCode.GRAPH_ENTITY_NOT_FOUND, HttpStatus.NOT_FOUND))
                .given(graphService).getEntityDetail(eq(3L), eq("missing"));

        mockMvc.perform(get("/api/v1/knowledge-bases/3/graph/entities/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4053));
    }

    @Test
    void shouldReturnServiceUnavailableWhenGraphBackendDown() throws Exception {
        willThrow(new BusinessException(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE))
                .given(graphService).getOverview(eq(3L), eq(null), eq(null));

        mockMvc.perform(get("/api/v1/knowledge-bases/3/graph/overview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(5010));
    }
}
