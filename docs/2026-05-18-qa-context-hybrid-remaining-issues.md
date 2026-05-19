# CKQA 问答上下文、会话管理与混合问答遗留问题清单

日期：2026-05-18

状态：P2 落实后，P3 已按实施方向拆分；智能推荐路由闭环已迁入 main 并完成低成本 live smoke

适用分支 / worktree：当前以主分支 `main` 为准；历史实现来自 `.claude/worktrees/student-qa-integration`

## 1. 当前完成状态

当前隔离分支已经完成问答上下文、会话恢复、来源展示和混合问答正式链路的核心闭环：

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

## 4. P2 遗留问题

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
2. 当前不引入 Redis。

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

建议范围：

1. 增加用户级、课程级、知识库级并发限制。
2. 增加 mode 级别限流，尤其是 `global/drift/hybrid_v0` 这类高耗时模式。
3. 增加任务排队和拒绝策略：
   - 排队中；
   - 稍后重试；
   - 已达到课程并发上限；
   - 模型服务暂不可用。
4. 评估 Redis 用于：
   - 限流计数；
   - 短期 task 状态缓存；
   - 通知/SSE 辅助状态；
   - 分布式 worker 协调。
5. 保持 MySQL 作为正式业务事实源，Redis 只作为生产增强项。

成本策略：

1. 第一版可以先做 Java 内存级限流和配置开关。
2. Redis 只在确实需要跨实例部署、任务通知或更精确限流时引入。
3. 不在这一阶段改变 QA 主协议。

验收重点：

1. 高并发下不会无限派发 hybrid/model 请求。
2. 用户能看到明确的排队、限流或降级提示。
3. 单实例和未来多实例方案边界清楚。

### P3-D：GraphRAG conversation history 与长期学习记忆 PoC

目标：探索真正的多轮 GraphRAG query engine 能力和跨 session 学习记忆，但必须先控制隐私、课程和用户隔离风险。

建议先拆成两个子计划，不要合并实施。

#### P3-D1：GraphRAG query engine conversation history PoC

范围：

1. 调研当前 GraphRAG 版本 query engine 是否能稳定接入 conversation history。
2. 在 Python 内部新增实验端点或实验 mode，不替换正式 `/v1/query-tasks` 默认路径。
3. 对比 CLI 单字符串查询、Java `retrievalQuery + generationContext`、GraphRAG history 三种方式。
4. 用固定课程样本测试追问、主题切换和来源稳定性。

验收重点：

1. 证明 GraphRAG history 能带来质量收益，而不是只增加复杂度。
2. 不破坏当前 Java 正式 QA 链路。
3. 明确是否值得从 CLI 封装迁移到 query engine 封装。

#### P3-D2：跨 session 长期学习记忆设计

范围：

1. 定义记忆边界：
   - 用户级；
   - 课程级；
   - 知识库级；
   - 是否跨索引版本；
   - 是否允许教师/管理员查看。
2. 定义隐私和权限规则：
   - 学生个人问题是否可复用；
   - 跨课程是否隔离；
   - 用户删除/归档后如何处理记忆。
3. 先做只读候选记忆，不自动注入生成。
4. 通过管理端和学生端显式提示“本次回答是否使用了历史学习记忆”。

验收重点：

1. 不发生跨用户、跨课程、跨知识库污染。
2. 记忆可解释、可删除、可关闭。
3. 默认不把长期记忆混入当前稳定问答链路。

## 6. 推荐下一步顺序

建议按下面顺序推进 P3：

1. **P3-A：QA 反馈闭环与管理端运维面板**：先补观测和反馈数据，让后续优化有依据。
2. **P3-B：hybrid_v1 检索质量与延迟优化**：基于反馈和来源质量样本优化 hybrid，而不是凭感觉调参。
3. **P3-C：生产保护、限流与任务通知**：在扩大使用面前补稳定性护栏。
4. **P3-D1：GraphRAG query engine conversation history PoC**：先小范围验证技术收益，不直接替换正式链路。
5. **P3-D2：跨 session 长期学习记忆设计与 PoC**：最后处理长期记忆，因为它的隐私、权限和产品语义风险最高。

如果只选择一个最先做，推荐从 **P3-A** 开始。它不需要新增在线 LLM 成本，却能为 hybrid_v1、智能推荐和长期记忆提供后续评估依据。
