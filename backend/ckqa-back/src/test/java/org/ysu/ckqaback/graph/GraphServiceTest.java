package org.ysu.ckqaback.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.Neo4jProperties;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GraphService 业务校验侧单测：
 * <ul>
 *   <li>知识库无激活索引 → 抛 4097</li>
 *   <li>Neo4j Driver 缺席 → 抛 5010</li>
 *   <li>邻域 depth!=1 → 抛 4000</li>
 * </ul>
 * <p>真正的 Cypher 走查依赖运行的 Neo4j（在 PR 阶段以手工 curl 自测覆盖）。</p>
 */
class GraphServiceTest {

    private KnowledgeBasesService knowledgeBasesService;
    private Neo4jProperties properties;

    @BeforeEach
    void setUp() {
        knowledgeBasesService = Mockito.mock(KnowledgeBasesService.class);
        properties = new Neo4jProperties();
    }

    @Test
    void shouldRejectKnowledgeBaseWithoutActiveIndex() {
        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(3L);
        kb.setActiveIndexRunId(null);
        Mockito.when(knowledgeBasesService.getRequiredById(3L)).thenReturn(kb);

        GraphService service = new GraphService(null, properties, knowledgeBasesService);

        assertThatThrownBy(() -> service.getOverview(3L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.KNOWLEDGE_BASE_NOT_READY.getCode());
    }

    @Test
    void shouldRejectWhenDriverNull() {
        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(3L);
        kb.setActiveIndexRunId(18L);
        Mockito.when(knowledgeBasesService.getRequiredById(3L)).thenReturn(kb);

        GraphService service = new GraphService(null, properties, knowledgeBasesService);

        assertThatThrownBy(() -> service.getOverview(3L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE.getCode());
    }

    @Test
    void shouldRejectNeighborhoodDepthOtherThanOne() {
        KnowledgeBases kb = new KnowledgeBases();
        kb.setId(3L);
        kb.setActiveIndexRunId(18L);
        Mockito.when(knowledgeBasesService.getRequiredById(3L)).thenReturn(kb);

        GraphService service = new GraphService(null, properties, knowledgeBasesService);

        assertThatThrownBy(() -> service.getEntityNeighborhood(3L, "e-1", 2, 50))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ApiResultCode.BAD_REQUEST.getCode());
    }

    @Test
    void healthShouldReportDisabledWhenDriverNull() {
        GraphService service = new GraphService(null, properties, knowledgeBasesService);
        GraphService.Neo4jHealth health = service.pingForHealth();
        assertThat(health.reachable()).isFalse();
        assertThat(health.message()).contains("disabled");
    }
}
