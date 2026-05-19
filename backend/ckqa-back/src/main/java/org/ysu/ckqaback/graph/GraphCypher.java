package org.ysu.ckqaback.graph;

/**
 * 学生端知识图谱接口使用的 Cypher 查询集中常量。
 * <p>
 * 节点 / 关系 schema 与 {@code graphrag_pipeline/utils/neo4jTest.py} 保持一致：
 * </p>
 * <ul>
 *   <li>节点：{@code __Document__ / __Chunk__ / __Entity__ / __Community__ / Finding / __Covariate__}</li>
 *   <li>关系：{@code PART_OF / HAS_ENTITY / RELATED / IN_COMMUNITY / HAS_CHUNK / HAS_FINDING / HAS_COVARIATE}</li>
 * </ul>
 * <p>
 * 出于审计与单测可读性考虑，所有 Cypher 写在这里集中管理；不要在 Service 里用字符串拼接。
 * 业务参数走 Cypher 形参（{@code $name}），不允许用字符串拼接拼入用户输入。
 * </p>
 */
public final class GraphCypher {

    /**
     * 总览：按 level 取顶层社区，按 rank 倒序截断。
     * 出参：communityId(long), title, rank(double), summary, level
     */
    public static final String OVERVIEW_COMMUNITIES = """
            MATCH (c:`__Community__`)
            WHERE c.level = $level
            RETURN c.community AS communityId,
                   coalesce(c.title, '')           AS title,
                   coalesce(c.rank, 0.0)            AS rank,
                   coalesce(c.summary, '')          AS summary,
                   c.level                          AS level
            ORDER BY rank DESC
            LIMIT $topN
            """;

    /**
     * 总览：按社区列表抽取每社区 Top-N 实体（按度数倒序）。
     * 入参：communityIds（list<long>）、perCommunity（int）
     * 出参：communityId, entityId, name, type, degree
     */
    public static final String OVERVIEW_TOP_ENTITIES = """
            UNWIND $communityIds AS communityId
            MATCH (c:`__Community__` { community: communityId })
            MATCH (c)<-[:IN_COMMUNITY]-(e:`__Entity__`)
            WITH communityId, e, size((e)-[:RELATED]-()) AS degree
            ORDER BY communityId, degree DESC
            WITH communityId, collect({entity: e, degree: degree})[..$perCommunity] AS topItems
            UNWIND topItems AS item
            RETURN communityId,
                   item.entity.id   AS entityId,
                   coalesce(item.entity.name, item.entity.title, '') AS name,
                   coalesce(item.entity.type, '')                    AS type,
                   item.degree                                       AS degree
            """;

    /**
     * 总览：根据上面拿到的实体 id 集合，取实体之间的 RELATED 关系。
     * 入参：entityIds（list<string>）、edgeLimit（int）
     */
    public static final String OVERVIEW_EDGES = """
            MATCH (a:`__Entity__`)-[r:RELATED]->(b:`__Entity__`)
            WHERE a.id IN $entityIds AND b.id IN $entityIds
            RETURN r.id                                    AS edgeId,
                   a.id                                    AS sourceId,
                   b.id                                    AS targetId,
                   coalesce(r.weight, 0.0)                  AS weight,
                   coalesce(r.description, '')              AS description
            ORDER BY weight DESC
            LIMIT $edgeLimit
            """;

    /**
     * 邻域：以中心实体为锚，1 跳邻居 + 关系。MVP 阶段只支持 depth=1。
     * 入参：entityId（string）、limit（int）
     */
    public static final String ENTITY_NEIGHBORHOOD = """
            MATCH (e:`__Entity__` { id: $entityId })
            OPTIONAL MATCH (e)-[r:RELATED]-(n:`__Entity__`)
            WITH e, r, n
            ORDER BY coalesce(r.weight, 0.0) DESC
            LIMIT $limit
            RETURN e.id                                    AS centerId,
                   coalesce(e.name, e.title, '')            AS centerName,
                   coalesce(e.type, '')                     AS centerType,
                   size((e)-[:RELATED]-())                  AS centerDegree,
                   r.id                                    AS edgeId,
                   coalesce(startNode(r).id, '')            AS sourceId,
                   coalesce(endNode(r).id, '')              AS targetId,
                   coalesce(r.weight, 0.0)                  AS weight,
                   coalesce(r.description, '')              AS edgeDescription,
                   n.id                                    AS neighborId,
                   coalesce(n.name, n.title, '')            AS neighborName,
                   coalesce(n.type, '')                     AS neighborType,
                   CASE WHEN n IS NULL THEN 0 ELSE size((n)-[:RELATED]-()) END AS neighborDegree
            """;

    /**
     * 实体详情：实体本体 + 所属社区路径 + 关联 chunk 数。
     * 入参：entityId（string）
     */
    public static final String ENTITY_DETAIL = """
            MATCH (e:`__Entity__` { id: $entityId })
            OPTIONAL MATCH (e)-[:IN_COMMUNITY]->(c:`__Community__`)
            WITH e, collect(DISTINCT { level: c.level, communityId: c.community, title: coalesce(c.title, '') }) AS communityPath
            OPTIONAL MATCH (:`__Chunk__`)-[:HAS_ENTITY]->(e)
            WITH e, communityPath, count(*) AS chunkCount
            RETURN e.id                                    AS entityId,
                   coalesce(e.name, e.title, '')            AS name,
                   coalesce(e.type, '')                     AS type,
                   coalesce(e.description, '')              AS description,
                   e.human_readable_id                      AS humanReadableId,
                   communityPath                            AS communityPath,
                   chunkCount                               AS chunkCount
            """;

    /**
     * 健康检查 / 自测用的最小 Cypher。
     */
    public static final String PING = "RETURN 1 AS ok";

    private GraphCypher() {
    }
}
