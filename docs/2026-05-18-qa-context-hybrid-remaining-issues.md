# CKQA 问答上下文、会话管理与混合问答遗留问题清单

日期：2026-05-18

状态：P0 闭环后的遗留问题整理

适用分支 / worktree：`.claude/worktrees/student-qa-integration`

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

最近一次 live smoke 结果：

1. 学生 `student.zhouzh` 在操作系统课程 `crs-20260506-r4slkr`、知识库 `5` 上创建 session `21`。
2. `hybrid_v0` 第一问“什么是死锁？”：task `24` success，来源 `10` 条，正文不含 `[Data:]`。
3. `hybrid_v0` 第二问“它和资源分配图有什么关系？”：task `25` success，`contextStrategy=recent`，rewrite 生效，来源 `7` 条，正文不含 `[Data:]`。
4. 无权限学生 `student.zhaoyn` 创建同课程会话返回 `403`，message 为 `无课程访问权限`。

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

## 3. P1 遗留问题

P1 表示：不阻塞当前 Beta 可用性，但会直接影响正式上线质量、稳定性或用户体验。

### P1-1：`hybrid_v0` 仍是手动 Beta，尚未纳入智能路由

当前状态：

1. 学生端已展示“混合检索 Beta”。
2. “智能推荐”仍只会路由到 `basic/local/global/drift`，不会自动选择 `hybrid_v0`。
3. 这是当前的保守上线决策，不是功能缺失导致的临时绕过。

影响：

1. 用户需要主动理解并选择 hybrid。
2. 混合问答的收益无法在普通用户路径中自动释放。
3. 后续若直接把 hybrid 加进智能推荐，可能引入成本、延迟和稳定性波动。

建议下一步：

1. 基于现有 qa_eval / holdout 问题集补一个路由评估报告。
2. 明确哪些题型适合 hybrid，例如“定位 + 综合解释 + 证据要求高”的问题。
3. 先做灰度策略：仅对高置信题型推荐 hybrid，低置信仍落 basic/local。
4. 前端文案继续保留 Beta 标识，直到延迟和质量指标稳定。

### P1-2：hybrid 首轮冷启动和模型依赖较重

当前状态：

1. live smoke 中 GraphRAG API 重启后，hybrid 首轮会加载 BGE-M3，并出现 Hugging Face 访问与权重加载日志。
2. 首轮耗时明显高于后续轮次。
3. 当前仍可成功完成，但生产体验不可直接接受这种冷启动路径。

影响：

1. 首个学生请求可能等待过久。
2. 无外网或模型缓存缺失时，hybrid 可能失败。
3. 服务重启后延迟抖动较大。

建议下一步：

1. 固定本地 BGE-M3 模型路径，避免运行时访问 Hugging Face。
2. GraphRAG API 启动后增加 hybrid warmup，可选预热 BM25、dense rerank 和 synthesis client。
3. 增加 health/readiness 中的 hybrid readiness 状态。
4. 区分 cold start latency 和 steady-state latency，避免平均值掩盖首轮问题。

### P1-3：task success 与 assistant message 装配存在短暂竞态

当前状态：

1. live smoke 中曾出现 task 已 success，但第一次 task detail 响应里的 `assistantMessage` 为空。
2. 稳定复查后 assistant message 和 sources 均已落库并可恢复。
3. 说明问题发生在异步完成边界，而不是最终持久化失败。

影响：

1. 前端如果在 success 瞬间停止轮询，可能短暂显示空回答。
2. 这个问题会降低用户对异步 QA 的信任。

建议下一步：

1. 后端 task 状态改为在 assistant message 与 sources 完成装配后再标记 success。
2. 或者 task detail 在 success 但 assistant 为空时返回 `progressStage=finalizing`，前端继续轮询。
3. 前端增加兜底：success 但无 assistant 内容时延迟重查一次 messages。

### P1-4：`generationContext` 已传输但尚未真正参与 Python 生成

当前状态：

1. Java 已生成 `retrievalQuery + generationContext`。
2. Python task snapshot 会保留 `generationContext`。
3. GraphRAG CLI 和当前 hybrid runner 实际仍主要使用短 `retrievalQuery`。
4. recent / summary 当前主要服务于 rewrite、诊断和上下文快照，而不是完整“带历史生成”。

影响：

1. 指代追问的检索输入已经改善。
2. 但答案生成本身还不是真正的多轮 chat memory。
3. 对“继续上面的回答展开”“按刚才那个例子再讲一遍”这类生成依赖更强的问题，效果仍有限。

建议下一步：

1. 保持检索输入短查询不变。
2. 在 hybrid synthesis 阶段引入 `generationContext`，只影响生成，不污染检索。
3. 对 basic/local/global/drift 继续评估是否升级 Python 协议，而不是把上下文拼进 CLI prompt。
4. 增加测试：同一 retrievalQuery 下，有无 generationContext 的回答差异。

### P1-5：来源质量还有提升空间

当前状态：

1. 来源卡片已经可恢复。
2. `[Data: Sources (...)]` 已被清理为 `[来源 N]`。
3. hybrid sources 已能落到 `qa_retrieval_hits`。

影响：

1. 学生不再看到神秘编号。
2. 但来源排序、片段可读性、章节路径完整性仍会影响信任感。
3. hybrid 的 fused evidence、BM25 evidence、basic citation evidence 还需要更清楚地区分。

建议下一步：

1. 来源卡片展示来源类型：GraphRAG citation、BM25、融合证据等。
2. 统一 rank 规则，避免正文 `[来源 N]` 与卡片排序不一致。
3. 增加来源命中质量评估：citation recall、source precision、人工审核标签。
4. 对缺页码或缺章节的来源补齐 fallback 展示规则。

## 4. P2 遗留问题

P2 表示：不影响当前正式链路的基础可用，但会影响产品完整度、运维能力或长期演进。

### P2-1：还缺少真实浏览器 E2E 覆盖

当前状态：

1. 已有后端、Python、前端 Node 测试。
2. 已做真实登录态 API smoke。
3. 但学生端真实浏览器路径尚未完整自动化覆盖。

建议下一步：

1. 增加 Playwright 或等价 E2E：登录、进入问答页、选择 hybrid、发送两问、看到 Markdown 和来源卡片。
2. 覆盖 403 权限错误文案。
3. 覆盖 success 但 assistant 延迟装配的前端兜底。

### P2-2：Python task 状态仍是进程内存

当前状态：

1. Java 侧有 `qa_retrieval_logs` 持久化。
2. Python `QueryTaskManager` 的 snapshot 仍在进程内存。
3. Python 重启后，Java 只能把未完成任务判为 stale 或失败。

建议下一步：

1. 短期继续以 Java 为事实源。
2. 中期为 Python task 增加轻量持久化，至少保存 task id、status、result、sources、error。
3. 或将 Python task 改为无状态同步 worker，由 Java 完整管理异步状态。

### P2-3：摘要质量和摘要漂移需要评估

当前状态：

1. Phase 2 已有滚动摘要表和摘要触发逻辑。
2. 摘要失败不阻断主问答。
3. 但摘要是否稳定保留关键学习上下文，还没有专门评估。

建议下一步：

1. 建立 20 轮会话测试集。
2. 检查摘要是否保留主题、结论、未解决问题。
3. 增加摘要审计字段或管理端查看入口。
4. 对摘要调用 One API 的失败率、耗时和成本做统计。

### P2-4：历史回答仍可能存在未清理的旧格式

当前状态：

1. 新答案已经走解析层。
2. 之前做过 backfill 修复旧数据。
3. 但本次 hybrid 修复前生成的少量测试回答可能仍含旧格式，需要按需复跑 backfill。

建议下一步：

1. 执行一次 dry-run backfill，筛选仍含 `[Data: Sources` 的 assistant messages。
2. 确认目标范围后再执行写回。
3. backfill 后抽样检查来源卡片是否仍能正确恢复。

### P2-5：会话生命周期能力还不完整

当前状态：

1. 已支持会话恢复和历史消息读取。
2. `/fork` 暂不实现。
3. 会话改名、归档、恢复、按索引版本分支继续等能力仍不完整。

建议下一步：

1. 先补归档和改标题。
2. 再设计 `/fork`，明确是否复制摘要、消息、来源和上下文快照。
3. 前端在 active index 变化时提供“继续旧索引 / 新建新索引会话”的稳定交互。

## 5. P3 后续增强

P3 表示：方向正确，但不是当前上线质量的直接阻塞项。

1. 引入真正的 GraphRAG query engine conversation history，而不是 CLI 单字符串查询。
2. 做跨 session 长期学习记忆，但必须先定义课程、用户和隐私隔离边界。
3. 把 hybrid_v0 演进为 hybrid_v1：更稳定的 rerank、更可解释的融合、更低延迟。
4. 增加学生反馈闭环：有用 / 无用、来源不相关、答案过长、希望举例。
5. 增加管理端 QA 运维面板：task 状态、rewrite、retrievalQuery、generationContext、sources、耗时、模型调用。
6. 增加限流与队列保护。Redis 仍不是当前必须项，但可作为生产限流、任务通知和短期状态缓存增强。

## 6. 推荐下一步顺序

建议按下面顺序推进：

1. 修复 task success 与 assistant message 装配竞态。
2. 固化 BGE-M3 本地模型路径并增加 hybrid warmup/readiness。
3. 增加学生端真实浏览器 E2E，覆盖 hybrid 两轮追问和来源卡片。
4. 做 hybrid 智能路由评估，但暂不默认开启。
5. 在 hybrid synthesis 阶段消费 `generationContext`，实现不污染检索的多轮生成。
6. 做来源质量评估与来源卡片信息增强。

