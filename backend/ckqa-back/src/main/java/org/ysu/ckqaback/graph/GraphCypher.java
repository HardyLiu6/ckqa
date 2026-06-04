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
            WITH communityId, e, COUNT { (e)-[:RELATED]-() } AS degree
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
                   COUNT { (e)-[:RELATED]-() }              AS centerDegree,
                   r.id                                    AS edgeId,
                   coalesce(startNode(r).id, '')            AS sourceId,
                   coalesce(endNode(r).id, '')              AS targetId,
                   coalesce(r.weight, 0.0)                  AS weight,
                   coalesce(r.description, '')              AS edgeDescription,
                   n.id                                    AS neighborId,
                   coalesce(n.name, n.title, '')            AS neighborName,
                   coalesce(n.type, '')                     AS neighborType,
                   CASE WHEN n IS NULL THEN 0 ELSE COUNT { (n)-[:RELATED]-() } END AS neighborDegree
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
     * QA 语义主题弱绑定（exact 阶段）：
     * 当前只在 active_neo4j 这份已激活图上做诊断性候选查询，
     * knowledgeBaseId / indexRunId 仅供上层日志记录与后续 scope 扩展使用；
     * 现有 Neo4j schema 暂无 per-index / per-knowledge-base 过滤字段，不能假装已经做到精确 scope 绑定。
     * 入参：topic（string）、limit（int，调用方默认 5）
     * 出参：entityId, name, type, humanReadableId, score, matchReason, source
     */
    public static final String TOPIC_ENTITY_EXACT_CANDIDATES = """
            MATCH (e:`__Entity__`)
            WITH e,
                 toLower(trim($topic)) AS topic,
                 toLower(toString(coalesce(e.id, ''))) AS entityIdValue,
                 toLower(toString(coalesce(e.name, ''))) AS nameValue,
                 toLower(toString(coalesce(e.title, ''))) AS titleValue,
                 toLower(toString(coalesce(e.type, ''))) AS typeValue,
                 toLower(toString(coalesce(e.human_readable_id, ''))) AS humanReadableIdValue
            WITH e,
                 CASE
                   WHEN entityIdValue = topic THEN 1.0
                   WHEN nameValue = topic THEN 1.0
                   WHEN titleValue = topic THEN 0.9800
                   WHEN humanReadableIdValue = topic THEN 0.9600
                   WHEN typeValue = topic THEN 0.6200
                   ELSE 0.0
                 END AS score,
                 CASE
                   WHEN entityIdValue = topic THEN 'exact_id'
                   WHEN nameValue = topic THEN 'exact_name'
                   WHEN titleValue = topic THEN 'exact_title'
                   WHEN humanReadableIdValue = topic THEN 'exact_human_readable_id'
                   WHEN typeValue = topic THEN 'exact_type'
                   ELSE 'none'
                 END AS matchReason
            WHERE score > 0
            RETURN coalesce(e.id, '') AS entityId,
                   coalesce(e.name, e.title, '') AS name,
                   coalesce(e.type, '') AS type,
                   coalesce(e.human_readable_id, '') AS humanReadableId,
                   score AS score,
                   matchReason AS matchReason,
                   'active_neo4j' AS source
            ORDER BY score DESC, name ASC, entityId ASC
            LIMIT $limit
            """;

    /**
     * QA 语义主题弱绑定（contains fallback 阶段）：
     * 仅当 exact 阶段没有任何候选时才执行，范围仍是当前 active_neo4j 图，不带 per-index 过滤。
     */
    public static final String TOPIC_ENTITY_CONTAINS_CANDIDATES = """
            MATCH (e:`__Entity__`)
            WITH e,
                 toLower(trim($topic)) AS topic,
                 toLower(toString(coalesce(e.id, ''))) AS entityIdValue,
                 toLower(toString(coalesce(e.name, ''))) AS nameValue,
                 toLower(toString(coalesce(e.title, ''))) AS titleValue,
                 toLower(toString(coalesce(e.type, ''))) AS typeValue,
                 toLower(toString(coalesce(e.human_readable_id, ''))) AS humanReadableIdValue
            WITH e,
                 CASE
                   WHEN nameValue CONTAINS topic THEN 0.8800
                   WHEN titleValue CONTAINS topic THEN 0.8600
                   WHEN entityIdValue CONTAINS topic THEN 0.7800
                   WHEN humanReadableIdValue CONTAINS topic THEN 0.7600
                   WHEN typeValue CONTAINS topic THEN 0.5000
                   ELSE 0.0
                 END AS score,
                 CASE
                   WHEN nameValue CONTAINS topic THEN 'contains_name'
                   WHEN titleValue CONTAINS topic THEN 'contains_title'
                   WHEN entityIdValue CONTAINS topic THEN 'contains_id'
                   WHEN humanReadableIdValue CONTAINS topic THEN 'contains_human_readable_id'
                   WHEN typeValue CONTAINS topic THEN 'contains_type'
                   ELSE 'none'
                 END AS matchReason
            WHERE score > 0
            RETURN coalesce(e.id, '') AS entityId,
                   coalesce(e.name, e.title, '') AS name,
                   coalesce(e.type, '') AS type,
                   coalesce(e.human_readable_id, '') AS humanReadableId,
                   score AS score,
                   matchReason AS matchReason,
                   'active_neo4j' AS source
            ORDER BY score DESC, name ASC, entityId ASC
            LIMIT $limit
            """;

    /**
     * 健康检查 / 自测用的最小 Cypher。
     */
    public static final String PING = "RETURN 1 AS ok";

    private GraphCypher() {
    }
}
