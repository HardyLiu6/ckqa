# 学生端知识图谱 MVP（G6）实施计划

> 起草日期：2026-05-19
> 工作分支：`feat/2026-05-19-student-knowledge-graph`
> 工作目录：`.worktrees/student-knowledge-graph/`
> 关联文档：
> - `docs/2026-05-19-student-knowledge-graph-page-plan.md`（设计与契约规划）
> - `docs/student-backend-graphrag-api-contract.md`（既有契约边界）
> - `graphrag_pipeline/utils/neo4jTest.py`（Neo4j schema 来源）

本计划是落地"学生端知识图谱"最小闭环的执行手册。设计与边界已在规划文档定稿，本文件只负责把设计切成可独立 review 的小步任务并给出验收口径。

## 0. MVP 范围

### 必须交付

1. Java 后端三个只读图谱接口：`overview` / `entity-neighborhood` / `entity-detail`
2. `system/health` 增加 `neo4j` 检查项
3. 学生端 `KnowledgeGraph.vue` 用 G6 + 真实接口跑通：
   - 进入页面 → 拉知识库摘要 → 选取 `activeIndexRunId != null` 的知识库
   - 渲染顶层社区 + Top-N 实体
   - 点选实体 → 右侧详情面板
   - 双击实体 → 邻域扩展
   - 点「以这个概念去问答」→ 跳 `/qa/ask?topic=节点名`
4. Neo4j 不可用时显式降级，不卡死画布

### 暂不做（留给后续 PR）

- 社区钻取的多层级展开（MVP 仅 `level=0`）
- `KnowledgeSearch.vue` 接入真实接口
- `KnowledgeDetail.vue` 替换 `coming-soon`
- `/knowledge/path` 学习路径
- chunk 原文 drawer
- "加入我的学习路径"持久化
- Playwright 故障注入 e2e
- 后端 Redis 缓存（先直查 Neo4j，下个 PR 再加）

## 1. 数据契约（与规划文档对齐）

### 1.1 后端新增接口

| 用途 | 方法 / 路径 | 关键参数 | 默认 / 上限 |
| --- | --- | --- | --- |
| 图谱总览 | `GET /api/v1/knowledge-bases/{id}/graph/overview` | `level`、`topN` | `level=0`、`topN=20`、`limit=100` 硬上限 |
| 实体邻域 | `GET /api/v1/knowledge-bases/{id}/graph/entities/{entityId}/neighborhood` | `depth`、`limit` | `depth=1`（MVP 不支持 2）、`limit=100` 硬上限 200 |
| 实体详情 | `GET /api/v1/knowledge-bases/{id}/graph/entities/{entityId}` | — | — |

响应统一信封 `code / message / data / timestamp`，时间字段 `Asia/Shanghai` 无偏移 `LocalDateTime`。

### 1.2 响应字段（草案）

`overview`：

```json
{
  "knowledgeBaseId": 3,
  "indexRunId": 18,
  "level": 0,
  "communities": [
    {
      "id": "c-0-7",
      "communityId": 7,
      "title": "进程与线程",
      "rank": 0.92,
      "summary": "（截断到 200 字符）",
      "topEntities": [
        { "id": "e-...", "name": "进程", "type": "Concept", "degree": 18 }
      ]
    }
  ],
  "nodes": [
    { "id": "e-...", "name": "进程", "type": "Concept", "communityId": 7, "degree": 18 }
  ],
  "edges": [
    { "id": "r-...", "source": "e-...", "target": "e-...", "weight": 0.7, "description": "（截断）" }
  ],
  "limits": { "nodeCount": 100, "edgeCount": 240 }
}
```

`entity-neighborhood`：

```json
{
  "centerId": "e-...",
  "nodes": [
    { "id": "e-...", "name": "进程控制块", "type": "Concept", "degree": 9 }
  ],
  "edges": [
    { "id": "r-...", "source": "e-...", "target": "e-...", "weight": 0.81, "description": "（截断）" }
  ],
  "limits": { "nodeCount": 30, "edgeCount": 60 }
}
```

`entity-detail`：

```json
{
  "id": "e-...",
  "name": "进程",
  "type": "Concept",
  "description": "（全文）",
  "humanReadableId": 42,
  "communityPath": [
    { "level": 0, "communityId": 7, "title": "进程与线程" }
  ],
  "chunkCount": 12
}
```

### 1.3 错误码

- `4097` 知识库当前没有可用索引（`activeIndexRunId == null` 时直接抛）
- `4050` 知识图谱节点不存在（实体 ID 找不到）
- `4051` 节点超过限流上限（MVP 阶段不抛，仅占位常量，留给二期）

## 2. 风险预控

### 2.1 G6 供应链

2025-11 antv 系列 npm 包出现过 Shai-Hulud worm 投毒事件，落地步骤：

1. 安装时**锁定具体小版本**（不用 `^`），并在 PR 描述贴 `pnpm why @antv/g6`。
2. 选事件后已发布的修复版本；安装前查 npm release 页确认无 IOC。
3. PR 中跑 `pnpm audit --prod` 贴日志。
4. 检查 `node_modules/@antv/*/package.json` 的 `postinstall` / `preinstall` 字段。

### 2.2 Neo4j 凭据

- 仅放在 `backend/ckqa-back/application.yml` / `.env`，不出现在任何 `/api/v1/*` 响应里。
- PR review 前对响应 JSON 做 `grep -i "neo4j\|bolt\|password"` 一遍。

### 2.3 大图打爆浏览器

- 后端 `overview` / `neighborhood` 用 Cypher `LIMIT` 截断，不在内存里截。
- 前端默认 `limit=100`，最大 200，与后端硬上限一致。

### 2.4 激活索引缺失

进入图谱页第一件事是拉知识库摘要：

- `activeIndexRunId == null` → 渲染 `RouteState`-like 提示，不发图谱请求。
- `system/health` 的 `neo4j=false` → 同样降级。

## 3. 任务拆分

按"一次提交一个可独立 review 的小步"控制粒度，提交标题前缀沿用仓库现有风格。

### T1. Java 后端：图谱只读接口骨架

提交：`feat(graph-api): student knowledge graph read-only endpoints`

- T1.1 在 `backend/ckqa-back` 新增 `GraphController`，路径前缀 `/api/v1/knowledge-bases/{id}/graph`。先用 mock JSON 跑通路由 + 响应信封 + 鉴权拦截。
- T1.2 新增 `Neo4jClient` / `GraphService`：
  - 从 `application.yml` 读 `spring.neo4j.uri / username / password`；
  - 加连接池与超时；
  - 启动时只做 `verifyConnectivity`，**不**做任何写操作。
- T1.3 实现 `getOverview(kbId, level=0, topN=20)`：
  - `MATCH (c:__Community__ {level: $level}) RETURN ... ORDER BY c.rank DESC LIMIT $topN`
  - 每社区 `MATCH (c)<-[:IN_COMMUNITY]-(e:__Entity__) RETURN ... ORDER BY size((e)-[:RELATED]-()) DESC LIMIT 5`
  - 关系：`MATCH (a:__Entity__)-[r:RELATED]->(b:__Entity__) WHERE a.id IN $ids AND b.id IN $ids LIMIT 240`
  - 长字段截断到 200 字符。
- T1.4 实现 `getEntityNeighborhood(kbId, entityId, depth=1, limit=100)`：
  - `MATCH (e:__Entity__ {id: $id})-[r:RELATED]-(n:__Entity__) RETURN ... ORDER BY r.weight DESC LIMIT $limit`
  - MVP 阶段 `depth` 仅接受 `1`，传其他值返回 `4000`。
- T1.5 实现 `getEntityDetail(kbId, entityId)`：
  - 实体全字段；
  - 沿 `IN_COMMUNITY` 上溯不同 `level` 拼 `communityPath`；
  - `MATCH (:__Chunk__)-[:HAS_ENTITY]->(e) RETURN count(*)` 得 `chunkCount`；
  - **不**返回 chunk 文本，下个 PR 再加。
- T1.6 在统一异常处理里加 `4050` 与 `4051`（`4051` 仅占位常量）。
- T1.7 单测：覆盖 `overview` 正常路径、`entity-detail` 找不到（`4050`）、知识库无激活索引（`4097`）。Testcontainers Neo4j 如果当前 CI 不支持，用 mock service 单测先行。
- T1.8 PR 描述贴最小 `curl` 自测脚本（用本地 `os` 知识库）。

### T2. Java 后端：health 增补 neo4j

提交：`feat(system-health): include neo4j reachability`

- T2.1 在 `system/health` items 列表增加 `name=neo4j`，状态来自 T1.2 的 `verifyConnectivity`，超时 1s。
- T2.2 `system/readiness` 不动。
- T2.3 同 PR 修 `docs/student-backend-graphrag-api-contract.md` 的"健康检查"小节，追加 `neo4j` 一项。
- T2.4 单测覆盖 `neo4j` 可达 / 不可达两种情况。

### T3. 学生端：依赖与封装

提交：`feat(student-graph-skeleton): add g6 deps and graph store/api/canvas`

- T3.1 在 `frontend/apps/student-app` 安装 G6：
  ```bash
  cd frontend/apps/student-app
  pnpm add @antv/g6@<事件后修复版本，落地时再确定>
  pnpm audit --prod
  pnpm build
  ```
  把 audit 与 build 输出贴 PR。
- T3.2 新增 `src/api/graph.js`：
  - `getGraphOverview(kbId, params)`
  - `getEntityNeighborhood(kbId, entityId, params)`
  - `getEntityDetail(kbId, entityId)`
  - 全部走 `src/axios/index.js`，自动挂 `Authorization` / `X-CKQA-User-Code`。
- T3.3 新增 `src/stores/graph.js`（Pinia）：
  - state：`activeKbId`、`nodes`、`edges`、`selectedNodeId`、`loading`、`error`
  - actions：`loadOverview`、`expandNeighborhood`、`selectNode`、`clear`
  - MVP 不做 history stack（留二期）。
- T3.4 新增 `src/components/knowledge/GraphCanvas.vue`：
  - props：`nodes`、`edges`、`selectedId`
  - emits：`select`、`expand`
  - 内部 `onMounted` 创建 G6 实例（`force` 布局、`drag-canvas` / `zoom-canvas` / `click-select`）；`onBeforeUnmount` 调 `graph.destroy()`；`watch(props.data)` 通过 `graph.changeData` 同步。
  - **不**对外暴露 G6 实例。
- T3.5 新增 `src/components/knowledge/EntityDetailPanel.vue`：
  - props：`entity`（`name / type / description / communityPath / chunkCount`）
  - emits：`ask-question`、`expand-neighborhood`

### T4. 学生端：页面接通

提交：`feat(student-graph-page): wire knowledge graph view to live api`

- T4.1 改造 `src/views/knowledge/KnowledgeGraph.vue`：
  - 进入时调用 `getCourseKnowledgeBases(courseId)`（如 store 没有，复用 `src/api/` 已有方法或新增），挑首个 `activeIndexRunId != null` 的 KB；
  - 没有可用 KB → 渲染降级提示（沿用 `RouteState` 风格或加 `routeState='no-active-index'`）；
  - 有可用 KB → 调 `getGraphOverview` 渲染 `GraphCanvas`；
  - 选中实体 → 调 `getEntityDetail` 填充 `EntityDetailPanel`；
  - 「以这个概念去问答」→ `router.push({ path: '/qa/ask', query: { topic: entity.name } })`，与现有 `KnowledgeGraph.vue#goQA` 思路一致。
- T4.2 双击实体 → 调 `getEntityNeighborhood`，结果合入 store 后由 `GraphCanvas` 自动重渲染。
- T4.3 错误处理：接口失败 ElMessage + 局部错误占位，不让画布转圈；`system/health` 的 `neo4j=false` 时直接降级。
- T4.4 现有 `mock/knowledge.json` 保留，但不再被 `KnowledgeGraph.vue` 引用。

### T5. 联调 + 验收

提交：`chore(graph-mvp-verify): mvp walk-through and docs refresh`

- T5.1 端到端走查清单（PR 中逐项打勾）：
  - [ ] DevTools 中所有图谱请求前缀为 `/api/v1/knowledge-bases/{id}/graph/...`
  - [ ] 无任何 `bolt://` / `:7474` / `:8012` 直连
  - [ ] 单页一次性节点 ≤ 200
  - [ ] 选实体后右侧详情字段齐全
  - [ ] 「去问答」跳转到 `/qa/ask?topic=...` 并能正确触发问答流程
  - [ ] 关闭 Neo4j 容器后刷新页面，画布显式降级，不卡死
  - [ ] `pnpm build` 通过
  - [ ] `./mvnw -pl backend/ckqa-back test` 关键单测通过
  - [ ] `pnpm audit --prod` 无高危项
- T5.2 在 `docs/2026-05-19-student-knowledge-graph-page-plan.md` 末尾追加一行 `MVP 落地状态：✅ <commit hash>`。
- T5.3 PR 描述贴：
  - 三个接口的 `curl` 输出
  - 关键页面截图（顶层社区、邻域展开、降级状态）
  - `pnpm audit` 结果
  - Neo4j down 时的截图

## 4. 时序依赖

```
T1 ──┐
     ├─► T4 ─► T5
T2 ──┤
T3 ──┘
```

- T1、T2、T3 之间无强依赖，可并行；
- T4 需要 T1 和 T3 都就绪；
- T2 不阻塞 T4，但 T5 的"Neo4j down 降级"验收要求 T2 已完成。

单人推进建议顺序：T1 → T3 → T2 → T4 → T5。

## 5. 提交与 PR 策略

- 每个任务 T1~T5 各一个独立 commit，必要时再细拆。
- 仅 stage 与本计划相关的文件；worktree 中已存在的与本任务无关的 prompt-builder phase 文件移动**不**纳入本 PR。
- 推送（首次）：
  ```bash
  git push -u origin feat/2026-05-19-student-knowledge-graph
  ```
- PR：用 `gh pr create` 创建到 `main`，标题不超过 70 字符，例如：
  ```
  feat(student-graph): MVP knowledge graph page powered by G6
  ```
- 不直接合并 `main`，等 review 通过后再合。

## 6. 关键代码骨架（仅供 review 参照）

### 6.1 G6 画布生命周期

```js
// onMounted
graph = new G6.Graph({
  container: containerRef.value,
  layout: { type: 'force', preventOverlap: true, linkDistance: 90 },
  modes: { default: ['drag-canvas', 'zoom-canvas', 'click-select'] },
  defaultNode: { size: 26, style: { lineWidth: 2 } },
  defaultEdge: { style: { stroke: '#94a3b8', endArrow: true } },
})
graph.data(props.data)
graph.render()
graph.on('node:click', evt => emit('select', evt.item.getModel().id))
graph.on('node:dblclick', evt => emit('expand', evt.item.getModel().id))

// onBeforeUnmount
graph?.destroy()

// watch(() => props.nodes, () => graph.changeData({ nodes, edges }))
```

### 6.2 Cypher 集中

后端把 Cypher 写在 `GraphCypherStatements` 常量类里集中管理，便于审计与单测；不在 Service 里散落字符串拼接。

### 6.3 Pinia store 形态

```js
// stores/graph.js
export const useGraphStore = defineStore('graph', () => {
  const activeKbId = ref(null)
  const nodes = ref([])
  const edges = ref([])
  const selectedNodeId = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function loadOverview(kbId) { /* ... */ }
  async function expandNeighborhood(entityId) { /* ... */ }
  function selectNode(id) { selectedNodeId.value = id }
  function clear() { /* ... */ }

  return { activeKbId, nodes, edges, selectedNodeId, loading, error,
           loadOverview, expandNeighborhood, selectNode, clear }
})
```

## 7. 完工定义（DoD）

- T1~T5 全部完成，各自单测 / 自测通过；
- PR 在 `feat/2026-05-19-student-knowledge-graph` 分支上；
- `docs/2026-05-19-student-knowledge-graph-page-plan.md` 与本实施计划文档随 PR 一并提交，并标注 MVP 落地状态；
- §3 T5.1 走查清单全部打勾；
- `pnpm audit --prod` 无高危项；如有，PR 描述说明并由负责人复核临时豁免。

## 8. 不在本 MVP 范围

- 学生端登录 / JWT 改造（沿用现有 `auth/student/*`）
- 浏览器直传 PDF
- WebSocket / SSE 流式图谱更新
- `full` 查询模式相关图谱视图
- 教师端图谱编辑能力（admin-app 范畴）
- 社区多层级钻取
- chunk 原文 drawer
