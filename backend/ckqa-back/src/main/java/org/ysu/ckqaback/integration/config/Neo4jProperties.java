package org.ysu.ckqaback.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Neo4j 连接与查询限流配置。
 * <p>
 * 学生端知识图谱接口（GraphController）只读访问 GraphRAG 灌库后的 Neo4j 数据，
 * 该配置主要用于隔离 Neo4j 连接信息与默认查询上限，不与 graphrag-pipeline 共享路径。
 * </p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.neo4j")
public class Neo4jProperties {

    /**
     * Neo4j Bolt URI，例如 bolt://localhost:17687 / neo4j://...。
     */
    private String uri;

    /**
     * 数据库用户名。
     */
    private String username;

    /**
     * 数据库密码。
     */
    private String password;

    /**
     * 目标数据库名（Community Edition 固定为 neo4j）。
     */
    private String database = "neo4j";

    /**
     * 是否启用 Neo4j 客户端。生产 / 联调期建议为 true；本地纯前端调试可关掉避免启动报错。
     */
    private boolean enabled = true;

    /**
     * 健康检查 / 单条查询的最大耗时（毫秒）。
     */
    private long readTimeoutMillis = 5000L;

    /**
     * QA 语义主题弱绑定的外层等待预算（毫秒）。
     * <p>
     * 该查询只用于运维诊断增强，默认给到 3 秒以适配本地 Neo4j 冷启动和较慢索引；
     * 实际执行仍不会超过 {@link #readTimeoutMillis}。
     * </p>
     */
    private long topicBindingTimeoutMillis = 3000L;

    /**
     * 顶层社区返回上限（overview.topN 默认）。
     */
    private int defaultOverviewTopN = 20;

    /**
     * overview 接口节点总数硬上限。
     */
    private int overviewNodeLimit = 100;

    /**
     * overview 接口关系总数硬上限。
     */
    private int overviewEdgeLimit = 240;

    /**
     * 邻域接口默认返回邻居数。
     */
    private int defaultNeighborhoodLimit = 100;

    /**
     * 邻域接口节点数硬上限。
     */
    private int neighborhoodNodeLimit = 200;

    /**
     * 长字段（description / summary 等）在列表 / 子图响应中的截断长度。
     */
    private int truncateLength = 200;
}
