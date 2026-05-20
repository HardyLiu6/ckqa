package org.ysu.ckqaback.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.graph.dto.GraphCommunityOverview;
import org.ysu.ckqaback.graph.dto.GraphCommunityRef;
import org.ysu.ckqaback.graph.dto.GraphEdgeResponse;
import org.ysu.ckqaback.graph.dto.GraphEntityDetailResponse;
import org.ysu.ckqaback.graph.dto.GraphLimitInfo;
import org.ysu.ckqaback.graph.dto.GraphNeighborhoodResponse;
import org.ysu.ckqaback.graph.dto.GraphNodeResponse;
import org.ysu.ckqaback.graph.dto.GraphOverviewResponse;
import org.ysu.ckqaback.integration.config.Neo4jProperties;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生端知识图谱只读查询服务。
 * <p>
 * 仅做读取：所有 Cypher 集中在 {@link GraphCypher}，参数化注入，禁止字符串拼接用户输入。
 * 长字段（description / summary）按 {@link Neo4jProperties#getTruncateLength()} 截断。
 * </p>
 * <p>
 * MVP 阶段假设「同一个 GraphRAG 灌库后的 Neo4j 实例」即为当前激活索引数据。
 * 以后需要按 {@code knowledgeBaseId} 隔离时（例如按 build run 拆数据库 / 标签），
 * 在这里加一层过滤即可，不影响接口形态。
 * </p>
 */
@Slf4j
@Service
public class GraphService {

    private final Driver driver; // 可能为 null（未启用 / 未启动连接成功）
    private final Neo4jProperties properties;
    private final KnowledgeBasesService knowledgeBasesService;

    @Autowired
    public GraphService(@Nullable Driver driver,
                        Neo4jProperties properties,
                        KnowledgeBasesService knowledgeBasesService) {
        this.driver = driver;
        this.properties = properties;
        this.knowledgeBasesService = knowledgeBasesService;
    }

    public GraphOverviewResponse getOverview(Long knowledgeBaseId, Integer level, Integer topN) {
        KnowledgeBases knowledgeBase = requireActiveKnowledgeBase(knowledgeBaseId);
        ensureDriverReady();

        int safeLevel = level == null || level < 0 ? 0 : level;
        int safeTopN = clamp(topN == null ? properties.getDefaultOverviewTopN() : topN, 1, 50);
        int perCommunity = 5;
        int nodeLimit = properties.getOverviewNodeLimit();
        int edgeLimit = properties.getOverviewEdgeLimit();

        try (Session session = openSession()) {
            List<Record> communityRecords = session.run(
                    GraphCypher.OVERVIEW_COMMUNITIES,
                    Values.parameters("level", safeLevel, "topN", safeTopN)
            ).list();

            if (communityRecords.isEmpty()) {
                return new GraphOverviewResponse(
                        knowledgeBaseId,
                        knowledgeBase.getActiveIndexRunId(),
                        safeLevel,
                        List.of(),
                        List.of(),
                        List.of(),
                        new GraphLimitInfo(0, 0, nodeLimit, edgeLimit)
                );
            }

            List<Long> communityIds = communityRecords.stream()
                    .map(r -> r.get("communityId").asLong())
                    .toList();

            List<Record> entityRecords = session.run(
                    GraphCypher.OVERVIEW_TOP_ENTITIES,
                    Values.parameters("communityIds", communityIds, "perCommunity", perCommunity)
            ).list();

            Map<Long, List<GraphNodeResponse>> entitiesByCommunity = new LinkedHashMap<>();
            Map<String, GraphNodeResponse> uniqueNodes = new LinkedHashMap<>();
            for (Record record : entityRecords) {
                if (uniqueNodes.size() >= nodeLimit) {
                    break;
                }
                Long communityId = record.get("communityId").asLong();
                String entityId = record.get("entityId").asString("");
                if (!StringUtils.hasText(entityId)) {
                    continue;
                }
                GraphNodeResponse node = GraphNodeResponse.of(
                        entityId,
                        record.get("name").asString(""),
                        record.get("type").asString(""),
                        communityId,
                        safeIntFromValue(record.get("degree"))
                );
                uniqueNodes.putIfAbsent(entityId, node);
                entitiesByCommunity
                        .computeIfAbsent(communityId, k -> new ArrayList<>())
                        .add(node);
            }

            List<GraphEdgeResponse> edges = List.of();
            if (!uniqueNodes.isEmpty()) {
                List<String> entityIds = new ArrayList<>(uniqueNodes.keySet());
                List<Record> edgeRecords = session.run(
                        GraphCypher.OVERVIEW_EDGES,
                        Values.parameters("entityIds", entityIds, "edgeLimit", edgeLimit)
                ).list();
                edges = new ArrayList<>(edgeRecords.size());
                for (Record record : edgeRecords) {
                    edges.add(GraphEdgeResponse.of(
                            record.get("edgeId").asString(""),
                            record.get("sourceId").asString(""),
                            record.get("targetId").asString(""),
                            record.get("weight").asDouble(0.0),
                            truncate(record.get("description").asString(""))
                    ));
                }
            }

            List<GraphCommunityOverview> communities = new ArrayList<>(communityRecords.size());
            for (Record record : communityRecords) {
                Long communityId = record.get("communityId").asLong();
                communities.add(new GraphCommunityOverview(
                        communityId,
                        record.get("title").asString(""),
                        record.get("rank").asDouble(0.0),
                        truncate(record.get("summary").asString("")),
                        entitiesByCommunity.getOrDefault(communityId, List.of())
                ));
            }

            return new GraphOverviewResponse(
                    knowledgeBaseId,
                    knowledgeBase.getActiveIndexRunId(),
                    safeLevel,
                    communities,
                    new ArrayList<>(uniqueNodes.values()),
                    edges,
                    new GraphLimitInfo(uniqueNodes.size(), edges.size(), nodeLimit, edgeLimit)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("查询知识图谱总览失败 knowledgeBaseId={} reason={}", knowledgeBaseId, ex.getMessage());
            throw new BusinessException(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public GraphNeighborhoodResponse getEntityNeighborhood(Long knowledgeBaseId,
                                                           String entityId,
                                                           Integer depth,
                                                           Integer limit) {
        if (!StringUtils.hasText(entityId)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "entityId 不能为空");
        }
        int safeDepth = depth == null ? 1 : depth;
        if (safeDepth != 1) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                    "MVP 阶段邻域查询仅支持 depth=1");
        }
        requireActiveKnowledgeBase(knowledgeBaseId);
        ensureDriverReady();

        int safeLimit = clamp(limit == null ? properties.getDefaultNeighborhoodLimit() : limit,
                1, properties.getNeighborhoodNodeLimit());

        try (Session session = openSession()) {
            List<Record> records = session.run(
                    GraphCypher.ENTITY_NEIGHBORHOOD,
                    Values.parameters("entityId", entityId, "limit", safeLimit)
            ).list();

            if (records.isEmpty()) {
                throw new BusinessException(ApiResultCode.GRAPH_ENTITY_NOT_FOUND, HttpStatus.NOT_FOUND);
            }

            Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
            List<GraphEdgeResponse> edges = new ArrayList<>();
            Set<String> edgeIds = new HashSet<>();

            Record first = records.get(0);
            String centerId = first.get("centerId").asString("");
            int centerDegree = safeIntFromValue(first.get("centerDegree"));
            nodes.put(centerId, GraphNodeResponse.of(
                    centerId,
                    first.get("centerName").asString(""),
                    first.get("centerType").asString(""),
                    null,
                    centerDegree
            ));

            for (Record record : records) {
                Value neighborValue = record.get("neighborId");
                if (!neighborValue.isNull()) {
                    String neighborId = neighborValue.asString();
                    nodes.computeIfAbsent(neighborId, id -> GraphNodeResponse.of(
                            id,
                            record.get("neighborName").asString(""),
                            record.get("neighborType").asString(""),
                            null,
                            safeIntFromValue(record.get("neighborDegree"))
                    ));
                }
                Value edgeValue = record.get("edgeId");
                if (!edgeValue.isNull()) {
                    String edgeId = edgeValue.asString();
                    if (edgeIds.add(edgeId)) {
                        edges.add(GraphEdgeResponse.of(
                                edgeId,
                                record.get("sourceId").asString(""),
                                record.get("targetId").asString(""),
                                record.get("weight").asDouble(0.0),
                                truncate(record.get("edgeDescription").asString(""))
                        ));
                    }
                }
            }

            return new GraphNeighborhoodResponse(
                    centerId,
                    new ArrayList<>(nodes.values()),
                    edges,
                    new GraphLimitInfo(nodes.size(), edges.size(),
                            properties.getNeighborhoodNodeLimit(), properties.getNeighborhoodNodeLimit() * 2)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("查询实体邻域失败 entityId={} reason={}", entityId, ex.getMessage());
            throw new BusinessException(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public GraphEntityDetailResponse getEntityDetail(Long knowledgeBaseId, String entityId) {
        if (!StringUtils.hasText(entityId)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "entityId 不能为空");
        }
        requireActiveKnowledgeBase(knowledgeBaseId);
        ensureDriverReady();

        try (Session session = openSession()) {
            List<Record> records = session.run(
                    GraphCypher.ENTITY_DETAIL,
                    Values.parameters("entityId", entityId)
            ).list();
            if (records.isEmpty()) {
                throw new BusinessException(ApiResultCode.GRAPH_ENTITY_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            Record record = records.get(0);

            List<GraphCommunityRef> communityPath = parseCommunityPath(record.get("communityPath"));
            communityPath.sort(Comparator.comparingInt(GraphCommunityRef::getLevel));

            Long humanReadableId = record.get("humanReadableId").isNull()
                    ? null
                    : record.get("humanReadableId").asLong();

            return new GraphEntityDetailResponse(
                    record.get("entityId").asString(""),
                    record.get("name").asString(""),
                    record.get("type").asString(""),
                    record.get("description").asString(""),
                    humanReadableId,
                    communityPath,
                    record.get("chunkCount").asLong(0L)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("查询实体详情失败 entityId={} reason={}", entityId, ex.getMessage());
            throw new BusinessException(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 仅供 SystemHealthService 调用：检查 Neo4j 连通性并返回简要描述。
     */
    public Neo4jHealth pingForHealth() {
        if (!properties.isEnabled() || driver == null) {
            return new Neo4jHealth(false, "neo4j client disabled");
        }
        try (Session session = openSession()) {
            Record record = session.run(GraphCypher.PING).single();
            int ok = record.get("ok").asInt(0);
            return new Neo4jHealth(ok == 1, ok == 1 ? "RETURN 1 ok" : "ping failed");
        } catch (Exception ex) {
            return new Neo4jHealth(false, ex.getMessage());
        }
    }

    public record Neo4jHealth(boolean reachable, String message) {
    }

    private KnowledgeBases requireActiveKnowledgeBase(Long knowledgeBaseId) {
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(knowledgeBaseId);
        if (knowledgeBase.getActiveIndexRunId() == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
        }
        return knowledgeBase;
    }

    private void ensureDriverReady() {
        if (driver == null || !properties.isEnabled()) {
            throw new BusinessException(ApiResultCode.GRAPH_BACKEND_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Session openSession() {
        SessionConfig config = SessionConfig.builder()
                .withDatabase(properties.getDatabase())
                .build();
        return driver.session(config);
    }

    private List<GraphCommunityRef> parseCommunityPath(Value value) {
        List<GraphCommunityRef> result = new ArrayList<>();
        if (value == null || value.isNull()) {
            return result;
        }
        for (Value item : value.values()) {
            if (item == null || item.isNull()) {
                continue;
            }
            Value levelValue = item.get("level");
            Value communityIdValue = item.get("communityId");
            Value titleValue = item.get("title");
            if (levelValue == null || levelValue.isNull()
                    || communityIdValue == null || communityIdValue.isNull()) {
                continue;
            }
            result.add(new GraphCommunityRef(
                    levelValue.asInt(0),
                    communityIdValue.asLong(),
                    titleValue == null || titleValue.isNull() ? "" : titleValue.asString("")
            ));
        }
        return result;
    }

    private String truncate(String raw) {
        if (raw == null) {
            return "";
        }
        int max = Math.max(20, properties.getTruncateLength());
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, max) + "…";
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int safeIntFromValue(Value value) {
        if (value == null || value.isNull()) {
            return 0;
        }
        try {
            return value.asInt();
        } catch (Exception ex) {
            return 0;
        }
    }
}
