# 学生端知识图谱页面规划

> 起草日期：2026-05-19
> 适用模块：`frontend/apps/student-app/`、`backend/ckqa-back/`、`graphrag_pipeline/`、`infra/`（Neo4j）
> 关联文档：
> - `docs/student-backend-graphrag-api-contract.md`
> - `graphrag_pipeline/utils/neo4jTest.py`（GraphRAG → Neo4j 灌库脚本）
> - `frontend/apps/student-app/src/views/knowledge/`（现有视觉稿）

## 0. 目的与边界

本规划面向学生端 `student-app` 的「知识图谱」一级菜单，目标是把 GraphRAG 已经产出的实体、关系、社区、社区报告等结构以**面向学习者**的方式呈现，并与现有问答 / 课程 / 学习路径页打通。

边界说明（必须遵守，否则违反仓库已确立的契约）：

- 学生端浏览器**只**调用 Java `backend/ckqa-back` 的 `/api/v1`，不得直接连接 `graphrag_pipeline` 的 Python `/v1`，更不得直接连接 Neo4j Bolt 端口或暴露任何 Neo4j 凭据。
- Neo4j 数据来源由 `graphrag_pipeline/utils/neo4jTest.py` 灌入，节点 / 关系 schema 与 GraphRAG `parquet` 输出严格对齐。
- 学生端只消费**已激活** build run（即 `knowledge-bases` 摘要里 `activeIndexRunId != null`）对应的图，不展示尚未发布的实验图。

## 1. 数据基础

### 1.1 Neo4j 节点 / 关系 schema

由 `graphrag_pipeline/utils/neo4jTest.py` 直接决定，**禁止**学生端规划里引入这套 schema 之外的结构：

| 节点 | 关键字段 | 来源 parquet |
| --- | --- | --- |
| `__Document__` | `id`、`title`、`text` | `documents.parquet` |
| `__Chunk__` | `id`、`text`、`n_tokens` | `text_units.parquet` |
| `__Entity__` | `id`、`title`(=`name`)、`type`、`description`、`human_readable_id`、动态 CamelCase 标签 | `entities.parquet` |
| `__Community__` | `community`、`level`、`title`、`rank`、`rating_explanation`、`full_content`、`summary` | `communities.parquet` + `community_reports.parquet` |
| `Finding` | 来自 `community_reports.findings` 的有序条目 | `community_reports.parquet` |
| `__Covariate__` | 由 `covariates.parquet` 提供（声明 / 事件等） | `covariates.parquet` |

| 关系 | 含义 |
| --- | --- |
| `(:__Chunk__)-[:PART_OF]->(:__Document__)` | 文本块属于文档 |
| `(:__Chunk__)-[:HAS_ENTITY]->(:__Entity__)` | 文本块包含实体 |
| `(:__Entity__)-[:RELATED]->(:__Entity__)` | 实体间关系（带 `weight`、`description`） |
| `(:__Entity__)-[:IN_COMMUNITY]->(:__Community__)` | 实体属于社区 |
| `(:__Community__)-[:HAS_CHUNK]->(:__Chunk__)` | 社区关联文本块 |
| `(:__Community__)-[:HAS_FINDING]->(:Finding)` | 社区报告的关键发现 |
| `(:__Chunk__)-[:HAS_COVARIATE]->(:__Covariate__)` | 文本块关联协变量 |

### 1.2 GraphRAG 语义

GraphRAG 的社区是**严格层级**的；`level=0` 是顶层社区，往下逐层细分。这是相对于普通知识图谱的核心增益，必须在前端体现出来（社区钻取、按 `rank` 排序的学习重点等），不要把它退化成"普通的实体-关系网"。

## 2. 后端契约设计（Java `/api/v1` 新增草案）

> 本节给出**接口草案**，供 Java 侧落地参考，最终路径以 Java 实现为准。响应统一使用现有 `code / message / data / timestamp` 信封，时间字段保持 `Asia/Shanghai` 无偏移 `LocalDateTime`。

### 2.1 通用约束

- 入参均要求 `knowledgeBaseId`，并校验该知识库 `activeIndexRunId != null`，否则返回 `4097`（知识库当前没有可用索引）。
- 所有节点 / 关系返回字段都必须经过**截断处理**：`description / summary / full_content / findings` 等长字段在列表 / 子图接口里只回截断版（≤ 200 字符），全文仅在详情接口返回。
- 节点数硬上限 `limit ≤ 200`，默认 `100`；前端不得绕过该上限。
- 后端在 Java 层做短 TTL Redis 缓存（建议 30~120 秒），失败回源 Cypher。
- Neo4j 凭据保留在 Java 服务侧，不出现在任何前端可达响应里。

### 2.2 接口草案

| 用途 | 方法 / 路径 | 关键参数 | 返回要点 |
| --- | --- | --- | --- |
| 图谱总览 | `GET /api/v1/knowledge-bases/{id}/graph/overview` | `level`（默认 `0`）、`topN`（默认 `20`） | 顶层社区列表 + 每社区 Top-N 实体的轻量节点；含全图节点 / 关系 / 社区计数 |
| 社区详情 | `GET /api/v1/knowledge-bases/{id}/graph/communities/{communityId}` | — | `title / level / rank / summary / findings[] / 关键实体 / 关联 chunk 数 / 子社区列表` |
| 实体邻域子图 | `GET /api/v1/knowledge-bases/{id}/graph/entities/{entityId}/neighborhood` | `depth`（1 或 2，默认 `1`）、`limit`（默认 `100`） | 中心实体 + 邻居实体节点 + `RELATED` 关系，按 `weight desc` 截断 |
| 实体详情 | `GET /api/v1/knowledge-bases/{id}/graph/entities/{entityId}` | — | 实体基本字段 + 所属社区路径（`level=0 → 1 → ...`）+ 关联 chunk 摘要列表 |
| 知识检索 | `GET /api/v1/knowledge-bases/{id}/graph/search` | `q`、`types`（`Entity,Community,Chunk` 多选）、`page`、`size` | 三类命中结果分页列表 |
| 子图导出（二期） | `GET /api/v1/knowledge-bases/{id}/graph/subgraph` | `seedEntityIds[]`、`depth` | 用于"我的学习路径"等定制视图 |

### 2.3 错误码扩展（建议）

沿用现有命名，**新增**以下业务码（待 Java 侧确认编号）：

- `4050` 知识图谱节点不存在（实体 / 社区 ID 找不到）
- `4051` 知识图谱节点超过限流上限
- `4052` 知识图谱检索关键字过短

## 3. 学生端信息架构

`student-app` 现有路由已预留 `/knowledge`、`/knowledge/graph`、`/knowledge/search`、`/knowledge/detail/:id`，骨架够用，**不再新增一级菜单**。本规划在此基础上明确各页职责。

### 3.1 主页：`/knowledge/graph` —— 课程图谱浏览器

**布局**：左 240 / 中自适应 / 右 360。

- **左侧筛选区**
  - 课程 → 知识库（默认选取 `activeIndexRunId != null` 的第一项）
  - 社区层级（`level=0/1/2…` 单选）
  - 实体类型（多选 chip，对应 `__Entity__` 上的 CamelCase 标签）
  - 关系强度阈值（`RELATED.weight` 滑杆）
  - 节点数上限（默认 100，最大 200，与后端 `limit` 对齐）
- **中央画布**
  - 默认渲染 `overview` 接口返回的"顶层社区 + 该社区 Top-N 实体"，社区用图形库的 Combo / Compound 节点能力包裹
  - 双击社区 → 钻入下一层级（请求该社区的 `communities/{id}` + 子社区 overview）
  - 双击实体 → 替换中心节点 + 调用 `neighborhood` 扩展邻域
  - 单击节点 → 高亮 1 跳邻居，半透明非邻居
  - 顶栏布局切换：`force` / `radial` / `dagre`
  - 右下角 mini-map + 缩放控制
- **右侧详情抽屉**
  - 选中实体：`title / type / description / 所属社区路径 / 关联 chunk 数`
  - 选中社区：`title / rank / summary / findings`（折叠列表）
  - 底部 CTA：
    1. 「查看相关原文 chunk」→ 弹出 chunk 列表 drawer
    2. 「以这个概念去问答」→ `router.push('/qa/ask', { query: { topic: 节点名 } })`，复用现有 `KnowledgeGraph.vue#goQA` 的思路
    3. 「加入我的学习路径」→ 写入 `useGraphStore` 的本地集合，二期再持久化

### 3.2 检索页：`/knowledge/search` —— 知识点检索 + 微图

- 顶部输入框（关键字 / 概念）
- 命中结果三类 tab：实体 / 社区 / 文档原文（`__Chunk__`）
- 每条结果右侧给「微图」按钮，点开右侧抽屉展示该实体的 1 跳邻域（仍走 `neighborhood` 接口）
- 不再做"独立大图"，避免和 3.1 重复

### 3.3 详情页：`/knowledge/detail/:id` —— 概念详情

- 顶部：实体名 / 类型 / 所属社区面包屑（社区按层级 `0 → 1 → ...` 逐级跳转）
- 主体三栏：
  - 概念定义（实体 `description` 全文）
  - 邻域小图（节点 ≤ 30 的固定子图，便于截图分享）
  - 来源文本（关联 `__Chunk__` 列表，文本片段 + 所属 `__Document__.title`）
- 底部 CTA：「就这个概念发起问答」「查看课程章节」

### 3.4（二期）学习路径：`/knowledge/path`

- 利用 GraphRAG 严格层级社区做"先掌握 `level=0` 高 `rank` 社区，再按层级推进"的学习路径
- 视觉首选 sunburst / icicle（理解层级结构时的可读性优于 treemap）
- Java 接口未就绪前继续走现有 `routeState: 'coming-soon'`，避免空白页

## 4. 前端可视化技术选型

### 4.1 推荐顺位

1. **首选：AntV G6 v5**
   - Vue 3 集成简单，中文文档完善，内置 force / dagre / radial / combo 多种布局，社区钻取与邻居高亮是它的强项。
   - **前置条件**：必须锁定可信版本，并在 CI 加 SCA 扫描（详见第 6 节安全提醒）。
2. **稳健备选：Cytoscape.js**
   - 老牌稳定，扩展（dagre、cose-bilkent、cola）齐全。
   - 没有官方 Vue wrapper，但通过 `ref` + 生命周期手动挂载即可。
3. **大图兜底：Sigma.js v3 + graphology**
   - 单课程图谱节点超过 5000 时再考虑切到此组合。
4. **快速原型：vis-network**
   - 仅用于 demo，不作为正式技术栈。
5. **不采用：Neovis.js**
   - 它需要浏览器持有 Neo4j 凭据，违反学生端 `/api/v1` 边界。
6. **不采用：现有 `graphrag_pipeline/utils/graphrag3dknowledge.py`**
   - 适合作为教师端 / 调试工具的离线 HTML，**不**搬进学生端主流量页面。

### 4.2 落地阶段建议

- **阶段 1（MVP）**：在审计无问题的前提下选用 G6；如团队对 antv 供应链事件仍有顾虑，则先用 Cytoscape.js，二期再视情况切换。
- 切换库的代价由 `GraphCanvas.vue` 单文件吸收（详见第 5 节），上层不感知。

## 5. 学生端工程结构（建议增量）

> 本节是建议的目录与文件草案，**不在调研阶段创建任何文件**。

```
frontend/apps/student-app/src/
├── api/
│   └── graph.js                    # 新增：图谱接口封装，复用 src/axios/index.js
├── components/
│   └── knowledge/
│       ├── GraphCanvas.vue         # 新增：图可视化封装（G6 / Cytoscape 实现细节藏在内部）
│       ├── EntityDetailDrawer.vue  # 新增：右侧实体详情抽屉
│       ├── CommunityDetailDrawer.vue # 新增：右侧社区详情抽屉
│       └── KnowledgeFilterPanel.vue  # 新增：左侧筛选区
├── composables/
│   └── useGraphInteractions.js     # 新增：高亮 / 钻取 / 邻域展开等纯交互逻辑
├── stores/
│   └── graph.js                    # 新增：当前知识库 / 选中节点 / 邻域历史栈
└── views/
    └── knowledge/
        ├── KnowledgeGraph.vue      # 重构现有视觉稿，挂上真实接口
        ├── KnowledgeSearch.vue     # 重构现有视觉稿，挂上检索接口
        ├── KnowledgeDetail.vue     # 新增：替换 RouteState 占位
        └── KnowledgePath.vue       # 二期：学习路径
```

封装要点：

- `GraphCanvas.vue` 必须把图引擎实例的生命周期（`onMounted` 创建 / `onBeforeUnmount` `destroy()` / `watch(props.data) → graph.changeData()`）封死，**外部不得直接拿到 G6 / Cytoscape 实例**，便于将来切库。
- 状态：用 Pinia `useGraphStore` 缓存当前知识库 ID、选中节点、邻域展开历史栈（用于"返回上一跳"）。
- 错误兜底：图谱接口失败时显式回退到现有 `RouteState`，不让画布一直转圈。
- 测试：参考 admin-app 的 Playwright 故障注入做法，给 `/api/v1/.../graph/*` 的失败用例补 e2e。

## 6. 安全与运维

- **AntV 供应链事件（2025-11）**：`@antv/*` 系列 npm 包出现过 Shai-Hulud worm 投毒。如果选 G6：
  1. 在 `pnpm-lock.yaml` 锁定到事件前已知干净的版本；
  2. 在 CI 加 `pnpm audit` / SCA 扫描；
  3. 审查 `postinstall` 脚本；
  4. 团队对此风险敏感时，第一阶段先走 Cytoscape.js。
- **Neo4j 凭据**：保留在 Java 服务侧，前端任何渠道都不得获取。
- **大图保护**：邻域 / 检索接口必须强制 `limit`，前端再做分批扩展；GraphRAG 全量实体可能十万级，一次拉完会卡死浏览器。
- **3D 可视化定位**：`graphrag3dknowledge.py` 仅作教师 / 调试工具，正式学生端不引入。
- **建议补 system/health 项**：在 Java 健康检查里增加 `neo4j` 项（与现有 `graphrag-api / graphrag-ready` 同档），让学生端能在画布前提前感知 Neo4j 不可用。

## 7. 落地路线图

### 阶段 1 · 最小闭环（建议先做）

1. **Java 后端**：实现 `overview / neighborhood / entity-detail` 三个端点；扩展 `system/health` 增加 `neo4j` 检查项；选定缓存策略。
2. **学生端**：
   - 新增 `src/api/graph.js`、`src/stores/graph.js`、`src/components/knowledge/GraphCanvas.vue`；
   - 用真实接口替换 `KnowledgeGraph.vue` 的 mock 数据；
   - 实现节点选中 → 详情抽屉 → 「以这个概念去问答」闭环。
3. **联调验证**：用现有 `os` 知识库走通"主页 → 选实体 → 详情 → 跳问答"。

### 阶段 2 · 体验升级

- 社区钻取（combo 折叠展开 + `level` 切换）
- 邻居高亮、回退栈、布局切换
- `KnowledgeSearch.vue` 接入 `graph/search`
- `KnowledgeDetail.vue` 上线，替换 `coming-soon`

### 阶段 3 · 差异化

- `/knowledge/path` 学习路径页（sunburst / icicle）
- 错题 / 收藏与图谱节点的双向跳转
- 教师批注覆盖层（与 admin-app 联动，二期再启动）

### 阶段 4 · 可选

- 把 `graphrag3dknowledge.py` 的 3D 视图作为"展示模式"嵌入课程介绍页（仅 iframe / 静态 HTML），与学生主流程解耦。

## 8. 验收口径

- 学生端任意图谱页加载时调用的接口前缀均为 `/api/v1/knowledge-bases/{id}/graph/...`，浏览器 DevTools 不出现任何指向 Python `/v1`、`bolt://`、`http://*:7474` 的请求。
- `system/health` 的 `neo4j` 项失败时，知识图谱页显式降级到错误状态卡，不再尝试渲染画布。
- 单页一次性加载节点数 ≤ 200；超过阈值时由后端返回 `4051`，前端展示「结果过多，请收紧筛选条件」提示。
- 任何长字段（`description / summary / findings / full_content`）在列表 / 子图接口中只回截断版；全文只在详情接口返回。

## 9. 暂不在本次范围

- 学生端登录 / JWT 改造（沿用现有 `auth/student/*`）
- 浏览器直传 PDF
- WebSocket / SSE 流式图谱更新
- `full` 查询模式相关图谱视图
- 教师端图谱编辑能力（admin-app 范畴）

## 10. 参考来源

- `graphrag_pipeline/utils/neo4jTest.py`（schema 来源，仓库内）
- `docs/student-backend-graphrag-api-contract.md`（契约边界，仓库内）
- Microsoft GraphRAG 官方文档：`https://microsoft.github.io/graphrag/`（dataflow、outputs）
- AntV G6：`https://g6.antv.antgroup.com/en/manual/introduction`
- Cytoscape.js：`https://github.com/cytoscape/cytoscape.js/`
- Sigma.js v3：`https://www.sigmajs.org/`
- Neovis.js：`https://github.com/neo4j-contrib/neovis.js/`（仅作排除项参考）
- AntV 供应链事件：
  - `https://www.aikido.dev/blog/mini-shai-hulud-antv-npm-supply-chain-attack`
  - `https://www.stepsecurity.io/blog/shai-hulud-here-we-go-again-mass-npm-supply-chain-attack-hits-the-antv-ecosystem`
- 层级可视化研究（sunburst / icicle 优先于 treemap）：`https://ar5iv.labs.arxiv.org/html/1908.01277`

> 内容已根据来源改写以满足合规要求。

---

## MVP 落地状态

- 落地分支：`feat/2026-05-19-student-knowledge-graph`
- 完成提交：`feat(graph-api)`、`feat(system-health)`、`feat(student-graph-skeleton)`、`feat(student-graph-page)`
- 已完成项：
  - Java 后端 `GET /api/v1/knowledge-bases/{id}/graph/{overview,entities/{id}/neighborhood,entities/{id}}` 三个只读接口
  - `system/health` 增加 `neo4j` 检查项（参见 [docs/student-backend-graphrag-api-contract.md](./student-backend-graphrag-api-contract.md)）
  - 学生端引入 G6 5.0.50（事件前发布版本），`KnowledgeGraph.vue` 接通真实接口、节点选中、邻域扩展、跳转 `/qa/ask`
  - 业务码 `4053 GRAPH_ENTITY_NOT_FOUND`、`5010 GRAPH_BACKEND_UNAVAILABLE`
  - `pom.xml` 加 `org.neo4j.driver:neo4j-java-driver`（版本由 spring-boot-dependencies 管理）
- 暂未做（留给后续 PR）：社区多层级钻取、知识检索接口接通、`/knowledge/detail/:id` 替换 `coming-soon`、`/knowledge/path` 学习路径、Playwright 故障注入 e2e、Redis 缓存。
- 已知基线问题：`IndexProgressParserTest` 在 `origin/main` 上即失败，本 PR 未引入回归（验证见 `./mvnw '-Dtest=!IndexProgressParserTest' test`，461 通过 / 0 失败 / 1 跳过）。
