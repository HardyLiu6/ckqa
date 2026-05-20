# CKQA 问答上下文、会话管理与混合问答遗留问题清单

日期：2026-05-18

状态：P2 落实后，P3 已按实施方向拆分；智能推荐路由闭环已迁入 main，学生端 Redis 服务端读缓存已接入，Local history 与跨对话学习记忆 Beta 已在主分支接入

适用分支 / worktree：当前以主分支 `main` 为准；历史实现来自 `.claude/worktrees/student-qa-integration`

## 1. 当前完成状态

当前主分支已经完成问答上下文、会话恢复、来源展示和混合问答正式链路的核心闭环：

1. 学生端真实问答走 Java `/api/v1/qa-sessions` 异步链路，不直连 Python。
2. formal session 创建时固化 `indexRunId`，后续消息使用 session 级索引版本。
3. 已实现会话恢复、URL `sessionId` 续聊、历史消息恢复。
4. 已实现 recent 上下文、滚动摘要、LLM 追问改写和双输入协议字段。
5. Python `/v1/query-tasks` 已接收 `retrievalQuery + generationContext`，当前实际检索仍使用短 `retrievalQuery`。
6. assistant 消息已支持 Markdown 渲染和来源卡片。
7. GraphRAG `[Data: Sources (...)]` 已解析为学生可读的 `[来源 N]`，并写入 `qa_retrieval_hits`。
8. `hybrid_v0` 已作为手动 Beta 模式接入正式 Java QA 链路。
9. 学生创建 formal session 时已校验课程权限、知识库归属和 active index。
10. 学生端已补 mocked Playwright E2E，覆盖 hybrid 两轮追问、Markdown、来源卡片、403 文案和 assistant 延迟装配兜底。
11. Python query task 已增加 file-backed snapshot store，服务重启后可回读终态 task 诊断快照。
12. 摘要链路已补调用审计字段，并新增低成本离线摘要质量评估 fixture。
13. 历史回答 `[Data: Sources (...)]` 清理已正式化为 dry-run 优先的维护脚本。
14. 会话已支持 owner 范围内改名、归档和恢复；archived session 只读。
15. 智能推荐路由已具备后端 `/api/v1/qa-routing/recommend`、离线路由评测集、阈值回归测试和真实登录态 smoke 矩阵。
16. 操作系统 QA 评估题集已补齐 text unit 级专家审定标注，100 条样本均带有可在当前 build-run `text_units.parquet` 命中的 `gold_text_unit_ids`。
17. 学生端服务端 Redis 读缓存已接入 Java `/api/v1`：课程列表、课程知识库列表、智能推荐结果、Hybrid warmup/readiness 可按权限隔离和 TTL 缓存；Redis 异常时 fail-open 回源。
18. Python `/v1/query-tasks` 已支持 `queryEngineStrategy=local_history`，在 `mode=local` 时可调用 GraphRAG LocalSearch conversation history；异常时自动回退 CLI local，并记录 fallback 诊断。
19. Local history 默认参数已按当前决策固定为 `32000 / 6x6 / 3 turns`，首版只服务 `local`，不新增 QA mode。
20. 跨对话学习记忆已作为系统级 Beta 能力接入：按 `userId + courseId + knowledgeBaseId + indexRunId` 隔离，默认关闭，学生可显式开启、关闭和清理；学习记忆只帮助追问理解，不作为课程事实来源。

最近一次 live smoke 结果：

1. 学生 `student.zhouzh` 在操作系统课程 `crs-20260506-r4slkr`、知识库 `5` 上创建 session `21`。
2. `hybrid_v0` 第一问“什么是死锁？”：task `24` success，来源 `10` 条，正文不含 `[Data:]`。
3. `hybrid_v0` 第二问“它和资源分配图有什么关系？”：task `25` success，`contextStrategy=recent`，rewrite 生效，来源 `7` 条，正文不含 `[Data:]`。
4. 无权限学生 `student.zhaoyn` 创建同课程会话返回 `403`，message 为 `无课程访问权限`。

最近一次智能推荐路由 smoke 结果：

1. 主分支 Java 后端临时运行于 `18081`，GraphRAG Python API 临时运行于 `18112`。
2. 学生 `student.zhouzh` 真实登录成功，课程 `crs-20260506-r4slkr`、知识库 `5` 的 hybrid warmup 返回 `ready=True`、`cached=True`。
3. 路由矩阵全部命中预期：
   - 定义题：`basic`
   - 材料定位题：`local`
   - 综述题：`global`
   - 迁移/扩展题：`drift`
   - Beta 开启且要求证据融合的问题：`hybrid_v0`
   - Beta 关闭时 hybrid 候选回退：`local`
   - 上下文追问关系题：`drift`
4. 本次 smoke 默认没有提交 `/qa-sessions/{id}/messages`，因此没有发起新的 hybrid 生成，也没有新增模型外发课程片段。

本轮相关提交：

1. `bfe7910 db: allow hybrid qa query mode`
2. `8bd8c4b backend: harden formal qa hybrid access`
3. `d81cda6 graphrag: run hybrid tasks on build-run data`
4. `cab6443 student-app: expose hybrid qa beta mode`
5. `3c3e9e9 feat: add student redis read cache`

## 2. 已关闭的 P0 问题

### P0-1：`hybrid_v0` 未接入正式 QA 链路

状态：已关闭。

当前结果：

1. Java `CreateQaMessageRequest.mode` 已允许 `hybrid_v0`。
2. Java task 仍统一写入 `qa_retrieval_logs`，成功后写 assistant message 和 `qa_retrieval_hits`。
3. Python `/v1/query-tasks` 对 `hybrid_v0` 不再走 GraphRAG CLI mode，而是调用 in-process Hybrid v0 orchestrator。
4. Python hybrid task 已使用 Java 传入的 build-run `dataDirUri`，不再读取共享 `graphrag_pipeline/output`。

### P0-2：学生端会话创建权限边界不足

状态：已关闭。

当前结果：

1. 学生端 create session 必须有登录态。
2. body 中 `userId` 只作为兼容字段，若存在必须等于 JWT user id。
3. formal session 只能绑定当前用户可读课程。
4. `knowledgeBaseId` 必须属于 `courseId`。
5. 知识库必须有 active index，创建时固化到 session。
6. send message 时继续校验 session owner 和课程可读。

## 3. 已关闭的 P1 问题

以下问题已在 P1 工作中关闭，保留在本文中作为闭环记录。

### P1-1：`hybrid_v0` 仍是手动 Beta，尚未纳入智能路由

状态：已关闭。

当前结果：

1. 学生端已展示“混合检索 Beta”。
2. “智能推荐”默认仍不自动选择 `hybrid_v0`，保持低风险路径。
3. 用户显式开启 Beta 开关后，前端规则才允许将综合解释、比较关联、证据要求高、追问且已有上下文的问题路由到 `hybrid_v0`。
4. 后端仍只信任合法 mode、权限和知识库索引校验，不把前端路由作为安全来源。

保留限制：

1. Beta 开关仍不默认开启。
2. 真正的在线 LLM 路由不在当前阶段实现。

### P1-2：hybrid 首轮冷启动和模型依赖较重

状态：已关闭。

当前结果：

1. Python 已新增 `/v1/hybrid-v0/warmup` 与 `/v1/hybrid-v0/readiness`。
2. warmup 按 build-run `dataDirUri` 初始化 text_units、BM25、citation lookup 和 orchestrator cache。
3. Java 已增加安全转发入口 `POST /api/v1/qa-sessions/hybrid-warmup`，先校验课程和知识库权限。
4. 学生端在选择 hybrid 或开启 Beta 智能推荐时触发 warmup，并展示准备状态。

保留限制：

1. warmup 不调用 One API/DeepSeek。
2. 模型本地路径和生产部署策略仍需在发布环境单独固化。

### P1-3：task success 与 assistant message 装配存在短暂竞态

状态：已关闭。

当前结果：

1. 后端 task worker 已调整完成顺序：先追加 assistant message、写入 sources、确认可装配响应，再标记 success。
2. sources 写入失败仍不阻断主回答成功，只记录 warning。
3. 前端已增加兜底：task success 但 assistant 为空时短延迟重查 task/messages。

保留限制：

1. 若后续引入分布式 worker，还需要重新验证最终一致性边界。

### P1-4：`generationContext` 已传输但尚未真正参与 Python 生成

状态：已关闭。

当前结果：

1. Java 已生成 `retrievalQuery + generationContext`。
2. Python task snapshot 会保留 `generationContext`。
3. GraphRAG CLI 和 BM25 检索仍只使用短 `retrievalQuery`，避免污染召回。
4. hybrid synthesis prompt 已低成本接入截断后的 `generationContext`，不新增额外 LLM 调用。

保留限制：

1. basic/local/global/drift 仍不把 generationContext 拼进 GraphRAG CLI。
2. 真正 GraphRAG query engine conversation history 仍留作 P3 方向。

### P1-5：来源质量还有提升空间

状态：已关闭。

当前结果：

1. 来源卡片已经可恢复。
2. `[Data: Sources (...)]` 已被清理为 `[来源 N]`。
3. hybrid sources 已能落到 `qa_retrieval_hits`。
4. Python sources 已标准化 `sourceType`，Java/前端会展示来源类型标签。
5. 正文 `[来源 N]` 与来源卡片 rank 保持一致。
6. 缺页码、缺章节时使用稳定 fallback 文案。

保留限制：

1. 来源命中质量评估仍可后续增强，例如 citation recall、source precision、人工审核标签。

## 4. P2 遗留问题（已关闭）

P2 表示：不影响当前正式链路的基础可用，但会影响产品完整度、运维能力或长期演进。

### P2-1：还缺少真实浏览器 E2E 覆盖

状态：已关闭。

当前结果：

1. `frontend/apps/student-app` 已增加 Playwright 配置和 `pnpm test:e2e`。
2. 默认 E2E 使用 mocked Java `/api/v1`，不会触发真实模型费用或外发课程片段。
3. 覆盖登录态注入、进入 `/qa/ask?sessionId=...`、选择 `hybrid_v0`、两轮追问、Markdown、来源卡片和 403 权限文案。
4. 覆盖 task success 但 assistant 延迟装配时的前端重查兜底。

后续增强：

1. live E2E 保持显式 opt-in，只在真实 smoke 或发布前手动开启。
2. 后续可以补视觉截图断言，但不是当前 P2 阻塞项。

### P2-2：Python task 状态仍是进程内存

状态：已关闭。

当前结果：

1. Java 侧有 `qa_retrieval_logs` 持久化。
2. Python `QueryTaskManager` 已支持 file-backed snapshot store。
3. 默认目录为 `GRAPHRAG_ROOT/runtime/query-tasks`，也可用 `GRAPHRAG_QUERY_TASK_STORE_DIR` 覆盖。
4. task create/running/success/failed/stale 都会写入 JSON snapshot。
5. 服务重启后可按 `python_task_id` 回读终态结果、sources、error、retrievalQuery、generationContext 等诊断字段。
6. Java/MySQL 仍是业务事实源，Python 持久化只承担诊断和结果回读。

后续增强：

1. 如果后续要支持分布式 Python worker，再评估 DB/队列型 task store。
2. Python task snapshot 仍不使用 Redis；Redis 当前只作为 Java 学生端读缓存和后续生产增强候选。

### P2-3：摘要质量和摘要漂移需要评估

状态：已关闭。

当前结果：

1. Phase 2 已有滚动摘要表和摘要触发逻辑。
2. 摘要失败不阻断主问答。
3. `qa_session_summaries` 已补最小审计字段：model、duration、input chars、output chars。
4. 已新增 20 轮会话 fixture 和离线评估脚本，默认不调用 LLM。
5. 评估覆盖主题保留、关键结论、未解决问题和疑似新增事实。

后续增强：

1. 管理端可增加摘要审计入口。
2. One API 评估继续保持显式 opt-in，避免默认测试产生模型费用。

### P2-4：历史回答仍可能存在未清理的旧格式

状态：已关闭。

当前结果：

1. 新答案已经走解析层。
2. 已新增 `backend/ckqa-back/scripts/clean_qa_source_markers.py` 维护脚本。
3. 脚本默认 dry-run，支持 `--execute`、`--session-id`、`--since`、`--limit`。
4. 扫描并清理 assistant messages，以及存在时的 `qa_retrieval_logs.assistant_message_text`。
5. execute 模式只更新命中的旧文本，不修改 sources/hits 结构，并支持 JSON/CSV 报告。

后续增强：

1. 真实库写回前仍先执行 dry-run 并核对报告。
2. 执行后再次 dry-run 应返回 0 条待修复记录。

### P2-5：会话生命周期能力还不完整

状态：已关闭。

当前结果：

1. 已支持会话恢复和历史消息读取。
2. 已新增 `PATCH /api/v1/qa-sessions/{id}`，仅 owner 可修改 `title` 和 `status`。
3. `status` 支持 `active` / `archived`。
4. archived session 允许读取历史和 sources，禁止继续发送消息。
5. 恢复 active 时重新校验课程权限。
6. 学生端历史会话列表已提供改名、归档、查看已归档和恢复入口。

后续增强：

1. `/fork` 仍作为后续设计项，需明确是否复制摘要、消息、来源和上下文快照。
2. active index 变化继续使用“继续旧索引 / 新建新索引会话”路径。

### P2-6：学生端读路径缺少服务端缓存

状态：已关闭。

当前结果：

1. 已接入后端 Redis 服务端缓存，浏览器和 student-app 不直连 Redis。
2. 已缓存低风险学生端读模型：
   - `GET /courses`
   - `GET /courses/{courseId}/knowledge-bases`
   - `/api/v1/qa-routing/recommend`
   - Hybrid warmup/readiness
3. Redis key 已按当前登录主体、课程、知识库、active index、Beta 开关、问题 hash 和上下文状态隔离，避免跨用户或跨索引版本串用。
4. Redis 读写异常全部 fail-open，缓存不可用时回源 MySQL / GraphRAG Python，不阻断学生问答。
5. 课程、知识库、active index 变更后会粗粒度清理相关学生端缓存。
6. `/api/v1/system/health` 已增加 `redis` 健康项，缓存配置已写入 `backend/ckqa-back/.env.example`、`application.properties` 和 infra/backend 文档。

保留限制：

1. 不缓存 QA messages、task detail 和最终 assistant answer，避免 pending/success 竞态和旧答案展示。
2. 当前失效策略偏保守，课程或知识库变更会清理较大范围 key；短 TTL 用于抵消粗粒度失效成本。
3. Redis health 失败会在健康项中体现，但业务接口仍按 fail-open 策略继续工作。

## 5. P3 后续增强拆分

P3 表示：方向正确，但不是当前上线质量的直接阻塞项。

P3 不建议一次性实施。当前剩余增强跨越“质量反馈、检索优化、生产保护、长期记忆”四类问题，风险边界、验证方式、数据库影响和模型成本都不同。后续建议采用一个 P3 总纲，加多个可独立验收的实施计划。

### P3-A：QA 反馈闭环与管理端运维面板

目标：先让系统能持续观察“哪里答得好、哪里答得差、为什么差”，为 hybrid_v1、智能路由和长期记忆提供真实依据。

建议范围：

1. 学生端增加轻量反馈：有用、无用、来源不相关、答案过长、希望举例。
2. 后端记录反馈与 `session/message/retrieval_log/source` 的关联。
3. 管理端增加 QA 运维面板：
   - task 状态；
   - query mode；
   - rewrite method / model / confidence；
   - `retrievalQuery`；
   - `generationContext`；
   - sources；
   - 耗时；
   - 模型调用摘要；
   - 学生反馈。
4. 增加来源质量标注入口，用于人工判断 citation recall、source precision 和来源排序问题。

成本策略：

1. 不新增在线 LLM 调用。
2. 默认只做记录、筛选、展示和导出。
3. 优先复用已有 `qa_messages`、`qa_retrieval_logs`、`qa_retrieval_hits`，如需新表只新增反馈和标注相关最小 schema。

验收重点：

1. 每条 assistant 回答都能追溯到 task、rewrite、sources 和学生反馈。
2. 管理端能筛出“答案无用”“来源不相关”“hybrid_v0 高耗时”等问题样本。
3. 导出的样本能直接用于后续 hybrid_v1 / 路由评估。

### P3-B：hybrid_v1 检索质量与延迟优化

目标：在现有 `hybrid_v0` 可用闭环和智能推荐路由闭环基础上，提升来源排序、融合解释、路由稳定性和响应稳定性。

当前可用性判断：

1. **已达到 Beta 可用**：当前智能推荐可以作为“受控 Beta”入口使用，适合在学生显式开启 Beta 或教师/管理员试用场景中放量。
2. **不建议默认全量开启 hybrid 自动路由**：评测集目前只有 30 条，live smoke 覆盖的是代表性路径，不足以证明所有课程、所有题型、所有索引版本都稳定。
3. **当前策略偏保守是合理的**：`basic/local/global/drift` 仍是默认主路径，`hybrid_v0` 只有 Beta 开启且出现证据融合/比较关联信号时才推荐，能控制成本和失败面。
4. **主要风险不在路由能否工作，而在边界样本不足**：目前还缺真实学生提问、教师审核样本和错路由样本积累，后续需要用反馈闭环持续扩充 holdout 集。

建议范围：

1. 改进 rerank 和融合策略，让正文早期引用与高质量证据排序一致。
2. 建立来源质量评估：citation recall、source precision、rank consistency、人工审核标签。
3. 优化 hybrid 延迟：
   - warmup 缓存复用；
   - evidence 截断；
   - synthesis prompt 压缩；
   - 可选关闭高成本分支。
4. 为 hybrid_v1 增加离线评估集和对比报告，不直接替换默认路由。

短期可明显提升的点：

1. **继续补负样本和边界样本**：当前已从外部习题集扩到 100 条带专家审定 text unit 标注的 QA 评估题和 152 条路由用例，下一步重点补“解释概念但要求举例”“综述但指定教材段落”“比较但不要求来源”“引用来源但只是简单定义”。这些样本最容易暴露 `local/drift/hybrid` 的误判。
2. **已完成：记录 smoke 报告到版本化目录**：`run_qa_routing_smoke_matrix.py` 默认写入 `docs/reports/qa-routing-smoke/<yyyyMMdd-HHmmss>-<label>/`，并支持 `--compare-to` 生成阈值调整比较。
3. **已完成：增加置信度区间告警**：`0.50-0.65` 标为 `low_confidence`，前端提示可手动切换，管理端可按低置信度和复核优先级筛选。
4. **已完成：补前端智能推荐真实联调用例**：学生端 Playwright mock E2E 覆盖 Beta 开启后以后端推荐为准，而不是只走本地规则预览。
5. **已完成：把 warmup readiness 纳入 UI 决策**：如果后端推荐 `hybrid_v0` 但 warmup 未 ready，Smart 路径自动降级到 fallback，并提示“准备中/建议降级”。

中期改进点：

1. **基于反馈数据做混淆矩阵**：把学生反馈、教师审核和运维标注关联到 `recommendedMode`、`actualMode`、`confidence`、`reasons`，形成每周路由质量报告。
2. **引入轻量语义路由但不新增在线 LLM**：优先评估本地 embedding/KNN 或 RedisVL 风格 reference phrases，而不是直接让 LLM 每次判路由。
3. **按课程类型配置路由权重**：教材型课程、习题型课程、实验型课程的最佳模式可能不同，后续可以给课程或知识库增加可选 routing profile。
4. **区分“推荐模式”和“执行模式”审计**：记录智能推荐结果、用户手动切换结果、最终执行结果，用于判断推荐是否被用户频繁纠正。

成本策略：

1. 优先用离线评估和已有 smoke 数据，不为每次路由新增 LLM 调用。
2. 默认仍保持 `basic` 优先，`hybrid_v1` 先作为 Beta 或实验模式。
3. 只有当离线指标和 live smoke 都稳定后，再考虑扩大智能推荐命中范围。

验收重点：

1. hybrid_v1 相比 hybrid_v0 在来源排序和答案可解释性上有可复现提升。
2. 延迟和失败率没有明显恶化。
3. 能清楚说明哪些题型适合 hybrid，哪些题型仍应走 basic/local/global/drift。
4. 智能推荐在 holdout 集上持续达到 `acceptableModes` 全命中，exact accuracy 不低于当前 `0.90` 门槛。
5. Beta 开关关闭时，`hybrid_v0` 不会成为自动推荐结果。

### P3-C：生产保护、限流与任务通知

目标：提升正式运行时的稳定性，防止模型调用、hybrid 生成和异步任务被突发请求拖垮。

当前 Redis 相关基线：

1. 学生端服务端读缓存已经接入 Redis，覆盖课程列表、课程知识库列表、智能推荐结果和 Hybrid warmup/readiness。
2. 当前 Redis 仍是性能增强层，不是事实源；MySQL、GraphRAG Python 和 QA 日志仍是正式状态来源。
3. 缓存已具备用户/课程/知识库/index 维度隔离和 fail-open 行为，但还没有承担限流、任务通知或分布式协调职责。

建议范围：

1. 增加用户级、课程级、知识库级并发限制。
2. 增加 mode 级别限流，尤其是 `global/drift/hybrid_v0` 这类高耗时模式。
3. 增加任务排队和拒绝策略：
   - 排队中；
   - 稍后重试；
   - 已达到课程并发上限；
   - 模型服务暂不可用。
4. 在现有 Redis 基础上扩展：
   - 限流计数；
   - 短期 task 状态缓存；
   - 通知/SSE 辅助状态；
   - 分布式 worker 协调。
5. 保持 MySQL 作为正式业务事实源，Redis 只作为生产增强项。
6. 增加缓存治理和观测：
   - cache hit/miss 指标；
   - Redis 错误率；
   - key 数量和内存占用；
   - 热点 key；
   - 缓存失效扫描耗时；
   - 按接口区分的缓存收益。

成本策略：

1. 限流第一版可以先做 Java 内存级配置开关；多实例部署前再把计数迁到 Redis。
2. Redis 已经引入，但后续只承接短期状态、限流和通知，不存正式问答答案。
3. 不在这一阶段改变 QA 主协议。
4. 缓存观测先用日志和简单 health 指标，后续再接 Prometheus / Grafana。

验收重点：

1. 高并发下不会无限派发 hybrid/model 请求。
2. 用户能看到明确的排队、限流或降级提示。
3. 单实例和未来多实例方案边界清楚。
4. Redis 缓存命中率、错误率和内存占用可被观测；Redis 异常时接口仍能回源。
5. Redis 中不出现 assistant answer、完整 prompt、用户明文问题等不适合长期缓存的敏感内容。

新增遗留问题：**Redis 缓存治理与隐私边界**

优先级：P3-C 子项。

说明：当前 key 中问题内容只进入 hash，不写入明文；value 主要是课程读模型、推荐结果和 warmup 状态。后续如果扩展 Redis 用途，必须继续保持“不缓存最终答案、不缓存完整 prompt、不把 Redis 当事实源”的边界。

建议下一步：

1. 为 `StudentRedisCacheService` 增加轻量指标埋点：hit、miss、load、write、error、evict。
2. 增加运维诊断接口或管理端面板，展示学生端缓存开关、TTL、Redis 健康和最近错误摘要。
3. 为 SCAN 失效增加批量大小配置和超时保护，避免大库下失效操作拖慢写请求。
4. 增加缓存 value 尺寸上限，防止异常响应被写入 Redis。

### P3-D：GraphRAG conversation history 与长期学习记忆 Beta

目标：探索真正的多轮 GraphRAG query engine 能力和跨 session 学习记忆，但必须先控制隐私、课程和用户隔离风险。

当前状态：P3-D 已从“只做旁路 PoC”推进到“Local 模式 Beta 接入”。长期学习记忆不是新 QA mode，而是用户显式开启的系统级能力；首版只在 `local` 或 `smart -> local` 场景下可能启用。

#### P3-D1：GraphRAG query engine conversation history PoC

状态：已完成 Local History Beta 接入，后续仅保留针对性优化与扩样本评估；学生端正式 QA 仍通过 Java 编排，不直连 Python。

范围：

1. 当前 GraphRAG `3.0.9` 本地包确认存在 `LocalSearch.search(query, conversation_history=...)`。
2. Python 已新增默认禁用的实验端点：
   - `GET /v1/experiments/query-engine-history/readiness`
   - `POST /v1/experiments/query-engine-history/query`
3. Python `/v1/query-tasks` 已新增可选 `queryEngineStrategy=cli|local_history` 与 `conversationHistory`，`mode` 仍为 `local`，不新增 `long_memory` 或 `history` mode。
4. `local_history` 复用 build-run `dataDirUri` resolver，拒绝绝对路径和路径穿越；artifact、GraphRAG import、readiness 或执行异常时自动 fallback 到 CLI local。
5. 当前默认参数为 `max_context_tokens=32000`、`top_k_entities=6`、`top_k_relationships=6`、`max_turns=3`。
6. `basic/global/drift/hybrid_v0` 首版不走 GraphRAG conversation history。
7. 已新增 dry-run 优先的评估脚本 `graphrag_pipeline/scripts/run_graphrag_history_poc_eval.py`，默认输出到 `docs/reports/graphrag-history-poc/`。
8. PoC 实施计划已固化到 `docs/superpowers/plans/2026-05-19-graphrag-query-engine-history-poc.md`；Beta 接入计划见 `docs/superpowers/plans/2026-05-20-qa-local-history-long-memory.md`。

验收重点：

1. 证明 GraphRAG history 能带来质量收益，而不是只增加复杂度。
2. 不破坏当前 Java 正式 QA 链路。
3. 明确是否值得扩大 query engine 封装的使用范围。
4. Local history fallback 不影响 task 创建和学生问答可用性。

当前结论边界：

1. 已完成技术入口和 dry-run 报告链路。
2. 已在操作系统 build-run `user_12/kb_5/build_20/index/output` 上完成 5 条授权 live PoC：
   - 报告目录：`docs/reports/graphrag-history-poc/20260519-210729-os-live-local-history/`
   - 5 条样本均返回 `success`，`historyApplied=true`。
   - 单条延迟约 25s 到 94s，明显高于当前正式链路的理想交互目标。
   - GraphRAG query engine 原始回答仍包含 `[Data: ...]` 内部编号，需要继续经过 CKQA 的来源解析/清洗层后才能面向学生。
   - 查询过程中多次触发 GraphRAG 内部 `Reached token limit - reverting to previous context state` 警告，说明默认 LocalSearch history 参数还需要进一步收敛上下文预算。
3. D1.2 已完成小步调参与对比验证：
   - LocalSearch history PoC 新增 `max_context_tokens`、`top_k_entities`、`top_k_relationships` 参数，当前 live 配置为 `5000 / 6 / 6`。
   - PoC 已复用 CKQA `[Data:]` 清理与来源解析层；live 报告中 raw answer 仍可保留内部编号用于诊断，展示 answer 已清理为 `[来源 N]` 或 `[已参考课程知识库]`。
   - 新增同批追问对比报告，比较 LocalSearch history 与正式 CKQA rewrite/hybrid baseline 的质量信号、来源覆盖和延迟。
   - tuned LocalSearch history live 报告：`docs/reports/graphrag-history-poc/20260519-213001-os-live-local-history-d1-2/`，5 条样本均 success，延迟约 26s 到 62s；`sourceRecallAt3` 只有 `Q2001=0.3333`，其余为 `0.0`。
   - LocalSearch history vs CKQA hybrid baseline live 报告：`docs/reports/graphrag-history-poc/20260519-213520-os-live-local-vs-hybrid-d1-2/`，3 条样本两路均 success；LocalSearch 延迟约 29s 到 43s，hybrid 约 38s 到 72s；hybrid 来源数更多，但 text unit recall@3 暂未明显优于 LocalSearch。
   - D1.2 报告已补充 `tokenLimitWarningCount`，用于直接观察 `Reached token limit - reverting to previous context state` 是否仍出现。
   - 2026-05-20 本地 context probe 报告：`docs/reports/graphrag-history-poc/20260520-105801-context-probe-param-matrix/`。该 probe 不生成答案、不调用 query embedding/One API，只用本地 parquet 和人工 `gold_entities` 锚定上下文，因此只能用于低风险筛参数，不能代表最终答案质量。
   - context probe 结果：`5000/6x6/5 turns`、`12000/4x4/3 turns`、`24000/4x4/3 turns` 在 3 条样本上均未触发 token rollback；其中 `24000/4x4/3 turns` 才把 community report 纳入上下文，平均上下文约 `10373` 字符级 token。
   - 2026-05-20 已按授权完成 `24000/4x4/3 turns` 的 3 条真实生成型 live smoke，报告目录：`docs/reports/graphrag-history-poc/20260520-113722-os-live-local-history-24k-4x4-turn3/`。
   - live smoke 结果：3 条样本均 success，`tokenLimitWarningCount=0`，清洗后的 answer 均不含 `[Data:]`；延迟约 `43.8s` 到 `66.5s`；来源数分别为 `5/2/6`；`sourceRecallAt3` 分别为 `0.3333/0.0/0.6667`。
   - 与早前 `5000/6x6/5 turns` 小样本相比，`24000/4x4/3 turns` 明显消除了 token rollback warning，并提升了部分样本的来源覆盖，但前两条延迟更高，仍不适合作为默认正式链路参数直接放量。
   - 2026-05-20 追加完成 `32000/6x6/3 turns` 的 3 条真实生成型 live smoke，报告目录：`docs/reports/graphrag-history-poc/20260520-120752-os-live-local-history-32k-6x6-turn3/`。
   - `32000/6x6/3 turns` 结果：3 条样本均 success，`tokenLimitWarningCount=0`，清洗后的 answer 均不含 `[Data:]`；延迟约 `51.8s` 到 `68.4s`；来源数分别为 `1/13/9`；`sourceRecallAt3` 分别为 `0.0/0.0/0.3333`。
   - 与 `24000/4x4/3 turns` 对比，`32000/6x6/3 turns` 虽然继续消除了 token rollback，但平均延迟更高、平均来源数更多、平均 `sourceRecallAt3` 更低，出现“证据变多但更分散”的信号。当前更推荐保留 `24000/4x4/3 turns` 作为 LocalSearch history 后续对比基线，而不是继续盲目增大 context 和 topK。
   - 2026-05-20 进一步完成单变量调参 `32000/4x4/3 turns` 的 3 条真实生成型 live smoke，报告目录：`docs/reports/graphrag-history-poc/20260520-122254-os-live-local-history-32k-4x4-turn3/`。
   - `32000/4x4/3 turns` 结果：3 条样本均 success，`tokenLimitWarningCount=0`，清洗后的 answer 均不含 `[Data:]`；延迟约 `25.7s` 到 `33.9s`；来源数分别为 `6/10/7`；`sourceRecallAt3` 分别为 `0.0/0.0/0.3333`。
   - 单变量对比显示：`32000/4x4/3 turns` 比 `24000/4x4/3 turns` 更快且来源数更多，但 `sourceRecallAt3` 更低。这说明仅提高 `max_context_tokens` 不必然导致延迟上升，但也不必然提升来源命中；当前更像是“可读证据变多，但 gold text unit 覆盖变差”。在没有更大样本前，不建议只凭本次延迟优势把 `32000/4x4/3` 作为默认参数。
   - 2026-05-20 再次重跑 `24000/4x4/3 turns`，报告目录：`docs/reports/graphrag-history-poc/20260520-122725-os-live-local-history-24k-4x4-turn3-rerun/`。
   - 重跑结果：3 条样本均 success，`tokenLimitWarningCount=0`，清洗后的 answer 均不含 `[Data:]`；延迟约 `29.0s` 到 `38.3s`，平均 `33.8s`，明显低于同参数上一轮 `58.6s`，说明网络/模型服务状态对延迟影响很大。
   - 重跑的 `sourceRecallAt3` 三条均为 `0.0`。因此目前不能用 3 条样本的 recall 波动直接断言 `24000` 或 `32000` 哪个更优；更稳妥的结论是：两组参数均解决了 token rollback，但来源覆盖需要扩大到同一批 10-20 条追问样本再比较。
   - 2026-05-20 再次重跑 `32000/6x6/3 turns`，报告目录：`docs/reports/graphrag-history-poc/20260520-123936-os-live-local-history-32k-6x6-turn3-rerun/`。
   - `32000/6x6/3 turns` 重跑结果：3 条样本均 success，`tokenLimitWarningCount=0`，清洗后的 answer 均不含 `[Data:]`；延迟约 `28.6s` 到 `40.9s`，平均 `33.8s`，相比同参数上一轮 `62.4s` 明显降低；来源数分别为 `9/4/9`；`sourceRecallAt3` 分别为 `0.3333/0.0/0.3333`，平均 `0.2222`。
   - 这次进一步证明：延迟受运行时网络/模型服务状态影响很大，不能用单轮 3 条样本判断参数快慢；`32000/6x6/3` 在重跑后来源覆盖优于 `32000/4x4/3` 和 `24000/4x4/3` 重跑，但仍需要更大样本确认是否真的优于 `24000/4x4/3` 初次运行结果。
4. 当前接入策略：
   - `local_history` 是 Local 模式可选增强，不替代全部 QA 链路。
   - Java 仍负责权限、会话、上下文预算、长期记忆开关和审计字段。
   - Python 负责 LocalSearch history 调用与 fallback 诊断。
   - 学生端只看到“本次是否使用学习记忆/Local history”的安全元数据，不暴露长期记忆正文。
5. 仍需继续验证：
   - 扩大到 20-30 条人工审定追问样本，比较 answer must-hit、negative-hit、source recall、延迟和失败率。
   - 进一步调 `text_unit_prop`、`community_prop`、`top_k_mapped_entities`、`top_k_relationships`、`conversation_history_max_turns`。
   - 只有当 LocalSearch history 在来源覆盖或追问理解上稳定优于 CLI local / hybrid baseline，才考虑扩大到更多课程或更多模式。

#### P3-D2：跨 session 长期学习记忆 Beta

状态：已完成 Beta 闭环：偏好、自动生成、动态注入、读取、删除和真实 smoke。默认关闭，用户显式开启后才参与 `local` 模式上下文。

已完成范围：

1. 新增 `qa_memory_preferences`，记录学生对 `courseId + knowledgeBaseId + indexRunId` 的跨对话学习记忆开关。
2. 新增 `qa_learning_memories`，记录学习关注点、未解决问题和解释偏好；不把 assistant 回答当作课程事实。
3. 新增 `QaMemoryContextService`：
   - 只读取同一 `userId + courseId + knowledgeBaseId + indexRunId` 范围内的记忆；
   - 最多选取 3 条长期记忆；
   - 长期记忆总字符不超过 1000；
   - 与最近 3 轮短期会话合并后裁剪到 3000 字；
   - 通过 `QaMemoryInjectionRouter` 按问题类型动态筛选长期记忆，降低跨 session 关注点串台风险。
4. `CreateQaMessageRequest.memoryPolicy` 支持 `default|off|auto`，默认读取用户偏好。
5. `QaWorkflowService.sendMessage()` 在 `mode=local` 时可把短期历史和长期学习记忆转为 Python `conversationHistory`。
6. task 响应和管理端运维详情展示安全元数据：
   - `memoryApplied`
   - `memoryStrategy`
   - `memoryScope`
   - `memorySourceCount`
   - `memorySizeEstimate`
   - `queryEngineStrategy`
   - `historyFallbackReason`
7. 学生端问答页新增“跨对话学习记忆”开关和清理入口；非 Local 模式不把学习记忆展示为新模式。
8. 新增规则式 `QaLearningMemoryCaptureService`，在 assistant 成功写入后旁路沉淀长期学习记忆，不调用 LLM，不阻塞 QA task success。
9. 自动生成记忆类型限定为：
   - `learning_topic`：学生正在关注的课程概念；
   - `explanation_preference`：步骤化、举例、通俗解释等偏好；
   - `unresolved_focus`：概念区别、关系、对比等持续关注点。
10. 记忆写入具备幂等治理：同一 scope 下相同 `memory_type + normalized memory_text` 刷新原记录，每个 scope 最多保留 20 条 active 记忆，超出后软删除最旧记录。
11. 学生端在回答成功后刷新一次学习记忆列表，让学生能看到系统自动沉淀的新记忆。
12. 长期记忆注入已加防串台规则：
   - 指代追问优先使用当前 session 最近主题，长期记忆只注入解释偏好；
   - 独立定义题默认不注入历史主题；
   - 明确继续学习/复习时才注入长期关注点和未解决焦点；
   - 当前主题与长期 `learning_topic` 明显不一致时过滤该记忆。

验收重点：

1. 不发生跨用户、跨课程、跨知识库污染。
2. 记忆可解释、可删除、可关闭。
3. 默认不把长期记忆混入当前稳定问答链路。
4. 长期记忆只帮助理解追问、学习关注点和解释偏好，事实来源仍以课程知识库为准。

保留限制与后续完成标记：

1. 当前学习记忆只用低成本规则生成和规则式动态注入，embedding 相似度筛选、质量评估、LLM 抽取、语义压缩、冲突合并留作后续优化。
2. 管理端默认不展示学生私密记忆正文，只展示策略和计数；教师可见性需要单独做隐私产品决策。
3. 首版只接 `local` 的 GraphRAG conversation history；Global/DRIFT history 继续留作实验方向。
4. Redis 可作为偏好/记忆读缓存候选，但 MySQL 仍是事实源。
5. 后续如要扩大 Beta，优先补长期记忆质量评估、学生可编辑/合并记忆、教师端隐私审计、跨课程长期记忆策略。

## 6. 推荐下一步顺序

建议按下面顺序推进 P3：

1. **P3-A：QA 反馈闭环与管理端运维面板**：先补观测和反馈数据，让后续优化有依据。
2. **P3-B：hybrid_v1 检索质量与延迟优化**：基于反馈和来源质量样本优化 hybrid，而不是凭感觉调参。
3. **P3-C：生产保护、限流与任务通知**：在扩大使用面前补稳定性护栏。
4. **P3-D1：Local history Beta 扩样本评估**：当前已接入 Local Beta，下一步扩大追问样本验证是否值得继续放量。
5. **P3-D2：长期学习记忆治理**：Beta 闭环已完成，后续重点是记忆质量评估、LLM 抽取、隐私审计、教师可见性边界和缓存优化。

如果只选择一个最先做，推荐从 **P3-A** 开始。它不需要新增在线 LLM 成本，却能为 hybrid_v1、智能推荐和长期记忆提供后续评估依据。
