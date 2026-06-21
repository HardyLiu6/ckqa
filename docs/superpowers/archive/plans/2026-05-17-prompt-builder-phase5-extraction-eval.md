# Prompt Builder Phase 5：04 步候选抽取评分 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 04 步从纯 mock 切到真实 API：后端引入 `prompt_tune_extraction_eval_runs` 状态表 + 异步 worker 串行调度 `run_native_extraction.py`（每候选 1 次）和 `score_extraction_results.py`（汇总 1 次），实现 `POST /extraction-eval` / `GET /extraction-eval/status` / `GET /extraction-eval/report` 三个端点；前端把 `MOCK_SCORING_REPORT` 替换成真实接口数据，候选矩阵实时进度（候选粒度）+ 排行榜（金银铜牌）+ 详情抽屉（指标 + 质量门控）+ "选定候选"动作健全。

**Architecture:**
- **异步执行（仿 PromptTuneWorker 模式）**：04 步每候选 20 次大模型调用、4 候选 80 次，总耗时 5-30 分钟。同步阻塞 controller 线程会撑满 Tomcat 池，必须放线程池。复用现有 `PromptTuneAsyncConfig` 的 executor pattern，独立 `extractionEvalExecutor`。
- **状态机表 `prompt_tune_extraction_eval_runs`**：`pending → running → success / failed / cancelling → cancelled`；增加 `progress_stage`（queued/extracting/scoring/done）、`extracting_candidate_id`、`finished_candidates`（JSON 数组）等字段，让 `GET /status` 可纯 DB 查询。
- **评分粒度 = 候选粒度**：`run_native_extraction.py` 是单候选 CLI，每候选跑完才退出；本期不解析子进程 stdout 做样本级进度，用候选粒度（"已完成 2/4 候选"）。Phase 8 异步化时若需要样本级精度，再仿 `PromptTuneLogTailer` 加 stdout 流式监听。
- **磁盘布局（关键决策）**：评分产物按 build run 隔离到 `<workspace>/eval/<runId>/`，**不**污染 `GRAPHRAG_ROOT/results/extraction_eval`。具体做法：脚本 `--root` 仍是 GRAPHRAG_ROOT（脚本要读 schema/prompts），但 `--run-id` 用 `eval_<buildRunId>_<evalRunId>` 保证唯一；worker 跑完把 `<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/` 和 `<GRAPHRAG_ROOT>/results/reports/extraction_scoring/runs/<runId>/` 复制到 build run workspace，然后清理共享路径。这样原型仍能跑 CLI，正式 build run 互不干扰。
- **门控（双层）**：前端门控 + 后端门控同时存在。后端 `ExtractionEvalService.trigger` 检查 02 步 ≥1 条 completed（4104）+ 03 步候选已生成（4105）+ 用户选的 candidateId 必须 ⊆ 已生成候选（4108）；前端 `PromptBuilderScoringStep.onMounted` 同样检查这三件事，给出友好引导。
- **复用 Phase 4 工件**：评分用的 audit 集是 `<workspace>/prompt/candidates/audit_with_gold.json`（Phase 4 已落地）；用户选的候选 prompt 文件是 `<workspace>/prompt/candidates/<candidateId>/prompt.txt`（Phase 4 已落地）。**Phase 5 不重新导出 audit**，直接读 Phase 4 产物。
- **Phase 4.5 衔接：seed 透传**：表 schema 加 `seed` 列（VARCHAR(32)）；`ExtractionEvalService.createPending` 启动评分时从 build run metadata.customPromptDraft.seed 读快照写入；`ExtractionEvalReportResponse` 顶层透传 seed，便于审计"本次评分基于哪个种子的候选"。本期对 04 步只做"读 seed 写到 eval run + report 响应展示"，**不做候选与 seed 的强一致校验**（用户切了 seed 但 04 仍跑旧候选时仍允许）。
- **前端轮询频率**：1500 ms（与 PromptTune 默认对齐）；候选数 ≤ 4 时单次响应 < 5 KB，DB 查询单行 < 1 ms，无需 SSE。

**Tech Stack:** Spring Boot 4.0.5 + Java 21 + MyBatis-Plus + 独立 ThreadPoolExecutor（后端）；Vue 3 + Element Plus + Vite（前端）；Python `run_native_extraction.py`（每候选）+ `score_extraction_results.py`（汇总），均由 graphrag_pipeline 提供，不修改。

---

## 范围与非范围

### 包含

1. 评分触发 + 进度查询 + 报告获取三个端点的真实落地。
2. 评分产物按 build run 隔离（workspace 下 `eval/<runId>/`）。
3. 门控：02 步 ≥1 completed + 03 步候选已生成 + 选的候选 ⊆ 已生成候选。
4. 异步 worker：dispatch + 心跳 + 失败诊断 + 进程超时兜底。
5. 启动恢复：服务重启时按状态分流恢复 active 任务（pending → 重新 dispatch；running → failed；cancelling → cancelled）。
6. 前端候选矩阵（候选粒度进度）+ 排行榜表格 + 详情抽屉。
7. "选定候选"路由 query 持久化（`selectedCandidates` 参数，复数，与后端 request body 一致）。
8. 中止评分（cancel）：当评分跑了一半用户想放弃时，提供取消入口；worker 检测 cancelling 状态后**在候选边界**终止剩余候选（不强杀当前候选子进程）。
9. **Phase 4.5 衔接：seed 透传**：表加 `seed` 列；启动评分时把 build run metadata 中的 seed 写快照；`ExtractionEvalReportResponse` 顶层透传 seed。

### 不包含

- 样本级抽取进度（候选内 20 条样本逐条更新）。本期候选粒度够用；Phase 8 再做。
- 多候选并行抽取（避免冲击 LLM 服务）。串行跑足够，也方便错误隔离。
- 评分历史 / 跨 build run 候选对比。spec 已写"v2 需求"，本期不做。
- "重新评分"按钮（用户重新选候选必须先回 03 步覆盖式重生成；单纯换 selectedCandidates 列表的"重跑评分"留到 Phase 8）。
- 详情抽屉里的"查看 20 条样本抽取详情 →"二级抽屉。spec 提到但本期 YAGNI，等用户反馈再做。
- 自定义 audit 子集 / 自定义 weights。脚本支持 `--weights`，但本期固定 DEFAULT_WEIGHTS。

---

## 关键架构决策

### 决策 1：执行模式 — 异步任务

**理由：** 04 步实测耗时 5-30 分钟（4 候选 × 20 样本 × 含 1 轮 gleaning 的 90-150 秒/样本，理论值 1-2 小时；deepseek-v4-flash 实际 ~5 分钟以内）。同步阻塞 controller 线程会让其他 API 卡顿，且用户关闭浏览器后 HTTP 连接断开会被 Tomcat 误判为客户端取消。

**实现：** 仿 `PromptTuneWorker` 模式：
- 新表 `prompt_tune_extraction_eval_runs` 持久化任务状态。
- `extractionEvalExecutor`（独立 ThreadPoolExecutor，`corePoolSize=1`，`queueCapacity=10`，串行评分避免冲击 LLM 服务）。
- POST `/extraction-eval` 立即返回 202 + `evalRunId` + `pending`，前端轮询 GET `/extraction-eval/status`。
- Worker 跑完 / 失败 / 取消后写终态，前端 stop 轮询。

### 决策 2：评分产物按 build run 隔离

**理由：** spec § "知识库构建 build run 隔离"原则要求所有 build run 工件互不污染。`run_native_extraction.py` 默认输出 `<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/` 是仓库共享路径，多 build run 同时跑会出现 runId 冲撞、报告覆盖。

**实现：**
- 脚本 `--run-id` 取 `eval_<buildRunId>_<evalRunId>`（保证唯一，含 buildRunId 便于排障）。
- 脚本 `--root` 仍是 GRAPHRAG_ROOT（脚本依赖 `prompts/` `config/schema/` 这些 root-relative 资源）。
- Worker 跑完后把以下两个目录的内容**复制**到 build run workspace：
  - `<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/` → `<workspace>/eval/<evalRunId>/extraction_eval/`
  - `<GRAPHRAG_ROOT>/results/reports/extraction_scoring/runs/<runId>/` → `<workspace>/eval/<evalRunId>/scoring_report/`
- 复制成功后同时清理 `<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/` 与 `<GRAPHRAG_ROOT>/results/reports/extraction_scoring/runs/<runId>/` 共享目录，避免遗留旧报告污染下一次运行。
- 失败时也保留 `<workspace>/eval/<evalRunId>/logs/` 用于排障，并把 stderr 摘要写到 `error_message`。

### 决策 3：进度粒度 — 候选级

**理由：** `run_native_extraction.py` 单次执行覆盖一个候选的全部 20 条样本，进程结束才退出。要做样本级进度需要 stdout 流式监听 + 解析 INFO 日志中"sample N/20"的字符串，强耦合脚本日志格式，脆弱。

**实现：**
- worker 串行跑每个 selectedCandidateId 的 `run_native_extraction.py`，每个候选完成时把 `extracting_candidate_id` 推进到下一个 + `finished_candidates` 数组追加。
- 抽取阶段全部完成后切到 scoring，跑一次 `score_extraction_results.py`（< 30 秒）。
- 前端候选矩阵展示：当前正在跑的候选 status=`extracting`，已完成的 status=`done`，未轮到的 status=`queued`。每个候选的 `extract.finished` = 0 或 20（无中间值）；`scoring` 阶段所有完成的候选 status=`scoring`，全跑完切 `done`。

**前端体感：** 矩阵每个候选条目跳跃式推进（queued → extracting → done），用户能清楚看到"现在在跑哪个 / 已经完成几个"。本期对每个候选只输出两态进度：未完成 → `extract.finished=0`，已完成 → `extract.finished=20`，**不**引入基于 elapsed 时间的"看似精确实则猜测"中间值。当前正在跑的候选若想给用户额外反馈，仅靠顶部 `overall.elapsedSeconds` 与 `estimatedRemainingSeconds`（基于"未完成候选数 × 单候选平均耗时常数"的粗略估算）即可。

### 决策 4：评分门控 — 三重

**理由：** 评分启动失败的早期诊断比晚期复杂错误诊断便宜得多。三类常见错误：02 步没完成 / 03 步未生成候选 / 用户选了不存在的候选 ID（手工调 API 或前端 query 参数被篡改）。

**实现：**
- **后端门控**（`ExtractionEvalService.trigger`，按顺序）：
  1. `samplesStore.listByBuildRunId(...)` 过滤 `reviewerDecision=completed`，0 条 → `4104 CANDIDATE_REQUIRES_AUDIT_COMPLETED`。
  2. `manifestReader.read(<workspace>/prompt/candidates)` 候选 0 条 → `4105 CANDIDATES_NOT_GENERATED`。
  3. `request.selectedCandidates` 中任一 ID 不在 manifest 中 → `4108 INVALID_EVAL_CANDIDATE_SELECTION`，message 含未识别 ID 列表。
  4. 若已有 active eval run（status ∈ pending / running / cancelling），返回该任务 id + 200 OK（**不抛错**），让前端复用进度而不是双开任务（仿 PromptTuneService）。
- **前端门控**（`PromptBuilderScoringStep.onMounted`）：先 GET `/extraction-eval/status` 看是否有 active 任务；若有 → 直接展示进度；若无 → 检查 `route.query.selectedCandidates`，缺失或与候选不匹配 → 提示用户回 03 步重选；齐备 → POST 触发评分。

### 决策 5：质量门控阈值 — spec 阈值，后端计算

**理由：** spec § 04 详情抽屉规定 4 条 gate 文案与阈值（解析成功率 ≥ 80% / 召回率 ≥ 50% / 准确率 ≥ 50% / 关系类型方向正确）。但 Python `score_extraction_results.py` 的 `GATE_THRESHOLD = 0.95` 严苛得多（每个硬指标都要 95%+）。这两套阈值不是冲突而是侧重不同：脚本的 gate_passed 是"上线生产推荐"信号，spec 的 gate 是"基础可用性"门槛。

**实现：**
- 后端 `ExtractionEvalReportAssembler` 在拼 `ExtractionEvalReportResponse.gates` 时按 spec 阈值重新算 passed：
  - `parse_success`: threshold=0.8, value=metrics.parse_success_rate, passed=value≥0.8
  - `audit_recall`: threshold=0.5, value=metrics.audit_entity_recall, passed=value≥0.5（**注意**：audit 集为空时 value=null → passed=null，前端按"未评估"显示灰色）
  - `audit_precision`: threshold=0.5, value=metrics.audit_entity_precision, passed=value≥0.5
  - `relation_direction`: threshold=null, value=metrics.endpoint_valid_rate, passed=value≥0.95（这条是脚本独有的方向性指标，沿用 95% 严格阈值）
- 前端**不**重算，直接渲染后端 gate 数组。这样后续阈值微调只改后端常量。

### 决策 6：cancel 语义 — 软取消（候选边界检查，不动子进程）

**理由：** 强杀 Python 子进程会留下半成品文件污染 build run workspace；硬取消还要处理"刚启动子进程但还没拿到 PID"的边界，本期不值得引入进程句柄注册 + 线程安全清理的复杂度。本期 worker 是候选粒度串行执行，单候选耗时通常 1-5 分钟，候选边界检查的最大延迟可接受。

**实现：**
- POST `/extraction-eval/cancel`（额外端点，spec 没显式提及但中止按钮需要）：把 status 改为 `cancelling`。
- Worker **不**主动中断当前正在跑的 `run_native_extraction.py` 子进程；当前候选会跑到结束（或自然超时落 failed）。
- 当前候选结束、worker 进入下一候选循环前 `select` 一次 status，若为 `cancelling` → 把后续未跑候选标记 skipped、写 `progressStage=done`、`status=cancelled`，整体退出。
- `runScoring` 阶段同样在调脚本前检查一次，若已 `cancelling` 则不跑 scoring，整体落 cancelled。
- 前端中止按钮调用 cancel API 后**立即**把状态卡片切到「正在中止当前候选，将在本候选结束后停止」文案，**继续**按原节奏轮询；当后端把 status 写成 cancelled 终态时前端再停止轮询并切到 cancelled phase（由用户主动点"返回 03 步"按钮，而非自动跳转）。
- 不实现 `Process.destroyForcibly`，也不在本期暴露子进程句柄；如未来需要"立即中断"，再单独走 Phase 8 改进。


### 决策 7：启动恢复 — 三态分流恢复

**理由：** 服务重启 / OOM / kill -9 时所有 active 任务都已与原进程脱钩。仅扫 `running` 不足以覆盖所有边界：`pending` 任务可能因 after-commit dispatch 在 worker 真正接手前进程崩溃而永远停留；`cancelling` 任务的原 worker 已不存在，下次 trigger 还会被 findActiveByBuildRunId 视为 active 而误复用。

**实现：** 新建 `ExtractionEvalStartupRecovery`（`@Component` + `ApplicationRunner`），启动时按状态分三类处理：

| 状态 | 处理 |
|---|---|
| `pending` | 重新调用 `worker.dispatch(id)` 派发；保持 status=pending（worker 进入 runInternal 后会切 running） |
| `running` | 切 status=`failed`，error_message="服务重启时被中断（last heartbeat: ...）"，写 finishedAt |
| `cancelling` | 切 status=`cancelled`，error_message="服务重启时被中断"，写 finishedAt（用户已请求取消，等价于已经收尾） |

扫描使用 `evalRunsService.listAllActive()`（不限心跳）。失联日志统一记 warn。

### 决策 8：报告快照 — DB 列 + 磁盘双写

**理由：** spec § "评分完成后获取排行榜"是只读高频接口，每次轮询都解析 1 MB 的 top_candidates.json 没意义；但磁盘文件作为审计材料保留也有价值。

**实现：**
- Worker 跑完 score 脚本后读 `<workspace>/eval/<evalRunId>/scoring_report/top_candidates.json`，把内容序列化后写入 DB `report_json`（mediumtext 列，足以容纳 ~1 MB JSON）。
- GET `/extraction-eval/report` 直接读 DB，不读磁盘。
- 失败时 report_json 为空，前端按 status=failed 展示错误页。

---

## 已识别风险

1. **大模型 API 限流 / 网络抖动**：实测单候选 20 样本 × 90-150 秒/样本，可能 30-50 分钟。GraphRAG 的 `GraphExtractor` 默认带重试，但 `run_native_extraction.py --concurrency 1` 串行执行时单点故障会卡死整个候选。**缓解**：worker 配置 `EXTRACT_TIMEOUT = Duration.ofMinutes(60)`（兜底，正常 5-15 分钟），超时后单候选标记 failed，其余候选继续跑。**整体终态语义**：评分链路完整结束（抽取 + scoring 都完成）则整体 status=`success`，即便其中部分候选 failed；这些失败候选**不进入排行榜**，而是写入新增的 `candidate_failures` JSON 列，由 `ExtractionEvalReportResponse.failedCandidates` 单独透传给前端，由前端在排行榜下方"未进入排名"区域展示候选名 + stage + reason。**仅当所有候选都 failed 或 scoring 自身失败**时整体落 `failed`。这条与"单候选失败不阻断整体"的初衷自洽。
2. **score 脚本对 audit 文件格式敏感**：脚本 `load_audit_index` 读 `audit_samples` 数组的 `source_sample_id` / `gold_entities` / `gold_relations`。Phase 4 `AuditWithGoldExporter` 已经对齐这个 schema（关键字段 `entity_id` / `source_entity_id` / `target_entity_id` / `evidence_text`）。**风险**：score 脚本未来若改字段名，Phase 5 不会立刻发现。**缓解**：Task 0 自检显式 grep `score_extraction_results.py` 的 audit 字段引用，与 `AuditWithGoldExporter` 对照一遍。
3. **Phase 4 audit_with_gold.json 只含 completed 样本**：score 脚本期望 audit 集是"评分参照集"，应该和 03 步生成 distilled fewshot 时用的 audit 一致。Phase 5 沿用 Phase 4 的 audit_with_gold.json 是符合"完成样本即可信标注"原则的，但需要在文档明确：评分时用的就是 03 步候选生成时用的 audit 集，二者必须同步（即 02 步后续新增 completed 样本必须先回 03 步重新生成候选才能进 04 步）。
4. **磁盘清理半成品**：worker 跑到一半被 kill / OOM，`<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/` 会留下半成品 JSON。下一次同 runId 又会冲撞。**缓解**：worker 启动时先 `rm -rf` 该 runId 目录（且因为 runId 含 evalRunId 自增主键，重启后重试也是新 runId，不会真冲撞）。
5. **score 脚本 audit 集为空时仍能跑**：`_audit_gold_available` 全 false → 软指标全 None，但 hard 指标仍能算。前端展示时要能识别 null（"未评估"灰色），不要按 0 渲染（误导用户以为指标"完全失败"）。
6. **关系方向指标在 spec 中是 "X/Y" 字符串（如 "5/5"）**：mock 里 `value: '5/5'`，但脚本里 `endpoint_valid_rate` 是 0-1 浮点数（如 0.95）。**实现**：`Gate` DTO 保留 `value: BigDecimal`（用于 passed 计算）；同时补两个结构化字段 `endpointTotalCount: Integer` / `endpointInvalidCount: Integer`，专供前端在 `name == "relation_direction"` 时按 `(total - invalid) / total` 渲染分子分母（其他 gate 这两个字段返回 null）。这样后端结构化、前端纯展示，不在后端拼字符串。
7. **report.json 1 MB 写 DB**：MySQL `mediumtext` 上限 16 MB，足够；但 mybatis-plus 默认会查全字段，列表页若误带上 `report_json` 会拖慢。**缓解**：mapper 提供 `excludeReportJson` 重载，只在 GET `/extraction-eval/report` 时 select 该列。
8. **multi build run 同时点评分**：本期 worker 池 corePoolSize=1 + queueCapacity=10，评分串行跑避免 LLM API 限流；前端在轮询返回 `pending` 时简单展示「队列中，等待开始」即可，**不**承诺精确的"队列中第 N 位"（Spring `ThreadPoolTaskExecutor` 的 BlockingQueue 没有稳定可暴露的位置序号，伪精确反而误导用户）。
9. **前端轮询不停**：用户离开 04 步页面但不关 tab 时（如切到其他系统页面再回来），轮询可能持续触发。**缓解**：`onUnmounted` 必须 `clearInterval`；route 切到非 prompt-builder 时也 clear。复用 PromptTuneService 用过的 `setInterval` 模式。
10. **`PipelineStepResponse` 不够用**：现 DTO 只含 step + status，无 evalRunId。Phase 5 必须新增 `ExtractionEvalRunStartedResponse`（含 evalRunId / status / reusedActiveRun / startedAt / recommendedPollingIntervalMillis）。

---

## 文件结构（新增 / 修改）

### 后端（Java）

- 新增 `sql/migrations/20260517_prompt_tune_extraction_eval_runs.sql`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneExtractionEvalRunsMapper.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneExtractionEvalRunsService.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneExtractionEvalRunsServiceImpl.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRunStartedResponse.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalOrchestrator.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalAsyncConfig.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalWorker.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java`
- 新增 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecovery.java`
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java`（增加 evalRunId / status / errorMessage / startedAt 等持久化字段）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java`（增加 evalRunId / generatedAt / candidate.displayNameZh）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`（加 4106 / 4108 / 5008）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`（替换 3 个 501 占位 + 新增 cancel 端点）
- 修改 `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`（layout 加 `eval/`）
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalOrchestratorTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalReportAssemblerTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalWorkerTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalServiceTest.java`
- 测试 `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecoveryTest.java`

### 前端

- 修改 `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`（加 `cancelExtractionEval`、调整 startExtractionEval payload）
- 修改 `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（透传 `selectedCandidates` query）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue`（去 mock + 真实轮询 + 五态）
- 修改 `frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-progress-model.js`（保留客户端 helper，增加"从 server 状态映射成 progress 数组"工具函数）
- 测试 `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-extraction-eval.test.js`
- 测试 `frontend/apps/admin-app/src/views/pages/prompt-builder/__tests__/scoring-progress-model.spec.js`（如未存在）
- E2E 新增 `frontend/apps/admin-app/e2e/helpers/prompt-builder-scoring.js`
- E2E 新增 `frontend/apps/admin-app/e2e/prompt-builder-scoring.spec.js`

---

## Task 0：契约自检

> 在写代码前确认所有外部依赖契约稳定，避免后续 Task 因假设错误返工。

**Files:**
- Read: `graphrag_pipeline/scripts/extraction_eval/run_native_extraction.py`
- Read: `graphrag_pipeline/scripts/extraction_eval/score_extraction_results.py`
- Read: `graphrag_pipeline/scripts/extraction_eval/scoring_audit.py`
- Read: `graphrag_pipeline/scripts/extraction_eval/scoring_metrics.py`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneWorker.java`
- Read: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneAsyncConfig.java`
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue`
- Read: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/scoring-report.js`

- [ ] **Step 1：确认 `run_native_extraction.py` 单候选模式参数**

Run:
```bash
cd graphrag_pipeline && python -m scripts.extraction_eval.run_native_extraction --help 2>&1 | tail -50
```

Expected：参数清单含 `--root / --samples-file / --prompt / --candidate-name / --max-gleanings / --limit / --run-id / --overwrite / --strict / --concurrency / --entity-types`。

确认 4 个关键事实：
1. **模块路径**：`scripts.extraction_eval.run_native_extraction`（与 Phase 4 候选生成的 `scripts.prompt_tuning.generate_candidate_prompts` 路径不同）。
2. **必填参数**：`--entity-types` 必须显式传（逗号分隔字符串），否则 argparse 会立刻报错。
3. **输出位置**：`<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/<candidateName>.json`，由 `result_writer.py` 写。
4. **单候选模式启动条件**：传 `--prompt` + `--candidate-name`（不传 `--manifest`）即走单候选模式。

**注意**：本期 Phase 5 后端**只用单候选模式**调脚本，每个候选一次进程调用，不用 manifest 模式。这样：
- 进程出错时只有一个候选 fail，可以隔离；
- 候选粒度的进度推进更直观；
- worker 中 `runSingleCandidateExtract(candidateId)` 方法独立可测。

- [ ] **Step 2：确认 `score_extraction_results.py` 输入与输出**

Run:
```bash
cd graphrag_pipeline && python -m scripts.extraction_eval.score_extraction_results --help 2>&1 | tail -30
```

Expected：参数清单含 `--eval-dir / --entity-schema / --relation-schema / --audit / --weights / --top-k / --run-id / --include-fallback-auto-tuned / --relation-validation-mode / --fewshot-source-sample-ids / --overwrite`。

**确认 5 个事实**：
1. **`--eval-dir` 接候选 JSON 目录**：`<GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/`，每个候选一个 `<candidateName>.json`。
2. **`--audit` 接 audit 文件**：可以是 Phase 4 产出的 `<workspace>/prompt/candidates/audit_with_gold.json`（含 `audit_samples` 数组 + 每条样本的 `gold_entities` / `gold_relations`），脚本通过 `load_audit_index` 读 `source_sample_id` 索引。
3. **`--run-id`**：复用与 `run_native_extraction.py` 相同的 runId，输出落到 `<GRAPHRAG_ROOT>/results/reports/extraction_scoring/runs/<runId>/`。
4. **输出文件**：`extraction_compare.csv` / `extraction_compare.md` / `top_candidates.json` / `run_meta.json`。Phase 5 主要消费 `top_candidates.json`（含 ranked 数组 + composite_score / 各项指标）。
5. **gate / threshold**：脚本里 GATE_THRESHOLD=0.95，但 spec 用 0.8/0.5/0.5/0.95 四档。**Phase 5 后端 ExtractionEvalReportAssembler 按 spec 阈值计算 gate.passed**，不直接使用脚本 `gate_passed` 字段。

- [ ] **Step 3：确认 audit JSON 格式与 Phase 4 产物一致**

Run:
```bash
ls graphrag_pipeline/runtime/kb-build-runs/user_*/kb_*/build_*/prompt/candidates/audit_with_gold.json 2>/dev/null | head -1
```

如果有产物：
```bash
python -c "import json,sys; p=open('<上面找到的路径>'); d=json.load(p); s=d['audit_samples'][0]; print('keys:', list(s.keys())); print('first entity keys:', list(s.get('gold_entities',[{}])[0].keys()))"
```

Expected：`source_sample_id / text / gold_entities / gold_relations / heading_path / page_start / page_end / audit_priority / reviewer_decision`，gold_entities 元素含 `entity_id / name / type` snake_case 字段。

如果环境里没有 build run 跑过 03，先跑一遍 Phase 4 产生该文件再来。

- [ ] **Step 4：确认错误码命名空间空闲**

Run:
```bash
grep -E "4106|4108|5008" backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java
```

Expected 输出：无（这 3 个码未占用）。

- [ ] **Step 5：确认 `PromptTuneAsyncConfig` 的 executor 模板**

Run:
```bash
grep -nE "ThreadPoolTaskExecutor|@Bean|setCorePoolSize|setQueueCapacity|setThreadNamePrefix" backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/PromptTuneAsyncConfig.java
```

Expected：含 `corePoolSize=1`、`queueCapacity=8`、`threadNamePrefix="prompt-tune-"` 等典型 ThreadPoolTaskExecutor 配置。Phase 5 仿这个 ThreadPoolTaskExecutor 模板新建 `extractionEvalExecutor`，使用 `corePoolSize=1`（决策 1，串行评分）和 `queueCapacity=10`（比 PromptTune 略宽，因评分链路单次更耗时）。

- [ ] **Step 6：自检通过承诺**

如果 Steps 1-5 全部 PASS，本计划架构假设成立，进入 Task 1。

如果 Step 1 / 2 / 4 / 5 任一项与计划假设不符，**停止执行**，把不符细节贴出来重新调整计划。

**Step 7（手工）：无 commit**

Task 0 不产生代码改动。

---

## Task 1：DB 迁移 + Entity / Mapper / Service 骨架

新建 `prompt_tune_extraction_eval_runs` 表 + 配套 MyBatis-Plus 三件套。

**Files:**
- Create: `sql/migrations/20260517_prompt_tune_extraction_eval_runs.sql`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneExtractionEvalRunsMapper.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneExtractionEvalRunsService.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneExtractionEvalRunsServiceImpl.java`

- [ ] **Step 1：新建 SQL 迁移**

Create `sql/migrations/20260517_prompt_tune_extraction_eval_runs.sql`：

```sql
-- CKQA 手动调优 04 步评分运行表
-- Date: 2026-05-17
-- 异步任务持久化：每次 04 步触发评分时插一条 pending，worker 跑完写终态。

CREATE TABLE IF NOT EXISTS `prompt_tune_extraction_eval_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `build_run_id` bigint NOT NULL COMMENT '所属构建流水线ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `selected_candidate_ids` json NOT NULL COMMENT '用户在 03 步勾选的候选 ID 列表（["default","auto_tuned",...]）',
  `seed` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null），由 Phase 4.5 引入',
  `status` enum('pending','running','success','failed','cancelling','cancelled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '任务状态',
  `progress_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'queued' COMMENT '当前阶段：queued/extracting/scoring/done',
  `extracting_candidate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '当前正在抽取的候选ID',
  `finished_candidates` json NULL DEFAULT NULL COMMENT '已完成候选ID数组（按完成顺序）',
  `candidate_failures` json NULL DEFAULT NULL COMMENT '失败候选结构化清单：[{"candidateId":"default","stage":"extract","reason":"..."}]',
  `eval_dir` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '相对 GRAPHRAG_BUILD_RUNS_ROOT 的评分产物目录路径',
  `report_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'top_candidates.json 内容快照',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '失败原因摘要',
  `latest_logs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '末尾若干行 stdout/stderr 拼接',
  `triggered_by_user_id` bigint NULL DEFAULT NULL COMMENT '触发用户ID',
  `started_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `last_heartbeat_at` timestamp NULL DEFAULT NULL COMMENT '最近一次心跳时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_eval_runs_build_run_status` (`build_run_id`, `status`) USING BTREE,
  KEY `idx_eval_runs_kb_status` (`knowledge_base_id`, `status`) USING BTREE,
  KEY `idx_eval_runs_status_heartbeat` (`status`, `last_heartbeat_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手动调优 04 步评分运行表';

-- 外键：build_run 删除时评分记录保留为孤儿（RESTRICT，不级联），便于审计
SET @has_fk_eval_runs_build_run := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_eval_runs_build_run'
);
SET @sql := IF(@has_fk_eval_runs_build_run = 0,
  'ALTER TABLE `prompt_tune_extraction_eval_runs` ADD CONSTRAINT `fk_eval_runs_build_run` FOREIGN KEY (`build_run_id`) REFERENCES `knowledge_base_build_runs` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_eval_runs_kb := (
  SELECT COUNT(1) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_eval_runs_kb'
);
SET @sql := IF(@has_fk_eval_runs_kb = 0,
  'ALTER TABLE `prompt_tune_extraction_eval_runs` ADD CONSTRAINT `fk_eval_runs_kb` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2：执行迁移**

Run:
```bash
cd infra && ENV_FILE=.env mysql -h"$(grep MYSQL_HOST .env | cut -d= -f2)" -u"$(grep MYSQL_USER .env | cut -d= -f2)" -p"$(grep MYSQL_PASSWORD .env | cut -d= -f2)" "$(grep MYSQL_DATABASE .env | cut -d= -f2)" < ../sql/migrations/20260517_prompt_tune_extraction_eval_runs.sql
```

或在 IDE 数据库工具直接跑 SQL。

Expected：表创建成功，无 SQL 错误。

- [ ] **Step 3：写 Entity**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java`：

```java
package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手动调优 04 步评分运行表。
 * <p>
 * 持久化每次评分任务的状态：pending → running → success/failed/cancelling/cancelled。
 * Worker 跑完后把 {@code top_candidates.json} 内容写入 {@link #reportJson}，
 * 列表查询时排除该列以避免拉满 ~1MB 文本。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_tune_extraction_eval_runs")
public class PromptTuneExtractionEvalRuns implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("build_run_id")
    private Long buildRunId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /** 用户在 03 步勾选的候选 ID 列表，JSON 字符串形态。 */
    @TableField("selected_candidate_ids")
    private String selectedCandidateIds;

    /**
     * 本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null）。
     * <p>由 Phase 4.5 引入，用于审计"本次评分基于哪个种子的候选 prompt"。
     * 若启动评分时 build run metadata 中没有 customPromptDraft.seed 字段，写入 null。</p>
     */
    @TableField("seed")
    private String seed;

    /** pending / running / success / failed / cancelling / cancelled。 */
    @TableField("status")
    private String status;

    /** queued / extracting / scoring / done。 */
    @TableField("progress_stage")
    private String progressStage;

    /** 当前正在抽取的候选 ID（为空表示无进行中候选）。 */
    @TableField("extracting_candidate_id")
    private String extractingCandidateId;

    /** 已完成候选 ID 数组（JSON 字符串）。 */
    @TableField("finished_candidates")
    private String finishedCandidates;

    /**
     * 失败候选结构化清单（JSON 字符串）。
     * <p>schema：{@code [{"candidateId":"...","stage":"extract","reason":"..."}]}。
     * worker 在 single-candidate 抽取异常时追加，scoring 阶段再汇总；
     * report 投影时映射为 {@code ExtractionEvalReportResponse.failedCandidates}，
     * 让排行榜外可单独展示"未进入排名"区域。</p>
     */
    @TableField("candidate_failures")
    private String candidateFailures;

    /** 相对 {@code GRAPHRAG_BUILD_RUNS_ROOT} 的评分产物目录路径，形如 {@code user_X/kb_Y/build_Z/eval/<evalRunId>}。 */
    @TableField("eval_dir")
    private String evalDir;

    /** {@code top_candidates.json} 序列化内容。 */
    @TableField("report_json")
    private String reportJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("latest_logs")
    private String latestLogs;

    @TableField("triggered_by_user_id")
    private Long triggeredByUserId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4：写 Mapper**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneExtractionEvalRunsMapper.java`：

```java
package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;

@Mapper
public interface PromptTuneExtractionEvalRunsMapper extends BaseMapper<PromptTuneExtractionEvalRuns> {
}
```

- [ ] **Step 5：写 Service 接口 + 实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneExtractionEvalRunsService.java`：

```java
package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 04 步评分运行存取服务。
 */
public interface PromptTuneExtractionEvalRunsService extends IService<PromptTuneExtractionEvalRuns> {

    /**
     * 取指定 buildRun 下最近一条评分记录（无论状态），按 id 倒序。
     * <p>用于 GET /extraction-eval/status；前端只看 build run 维度的最新一次。</p>
     */
    Optional<PromptTuneExtractionEvalRuns> findLatestByBuildRunId(Long buildRunId);

    /**
     * 取指定 buildRun 下处于 active（pending/running/cancelling）的评分记录。
     * <p>用于 trigger 时复用已运行任务（决策 4：返回该任务 id 而不是抛错）。</p>
     */
    Optional<PromptTuneExtractionEvalRuns> findActiveByBuildRunId(Long buildRunId);

    /**
     * 列出心跳过期的 running 任务（用于启动恢复时把卡死的运行任务标记 failed）。
     */
    List<PromptTuneExtractionEvalRuns> listStaleRunning(LocalDateTime heartbeatBefore);

    /**
     * 列出所有当前处于 active 状态（pending / running / cancelling）的任务，
     * 不限心跳——专供 {@code ExtractionEvalStartupRecovery} 在服务启动时使用。
     * <p>启动场景下任何 active 任务都已与正在运行的进程脱钩：</p>
     * <ul>
     *   <li>pending：worker 派发前进程崩溃 → 永远不会被消费，必须显式恢复。</li>
     *   <li>running：心跳是否过期都不重要——服务都重启了，不可能还在跑。</li>
     *   <li>cancelling：用户已请求取消，原 worker 已不复存在，需要直接落 cancelled 终态。</li>
     * </ul>
     */
    List<PromptTuneExtractionEvalRuns> listAllActive();

    /**
     * 列表查询时不带 report_json，避免拉满大字段。
     */
    Optional<PromptTuneExtractionEvalRuns> findByIdWithoutReport(Long id);

    /**
     * 必查；为空时抛 NotFound。
     */
    PromptTuneExtractionEvalRuns getRequiredById(Long id);
}
```

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneExtractionEvalRunsServiceImpl.java`：

```java
package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.PromptTuneExtractionEvalRunsMapper;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PromptTuneExtractionEvalRunsServiceImpl
        extends ServiceImpl<PromptTuneExtractionEvalRunsMapper, PromptTuneExtractionEvalRuns>
        implements PromptTuneExtractionEvalRunsService {

    private static final List<String> ACTIVE_STATUSES = List.of("pending", "running", "cancelling");

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findLatestByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findActiveByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .in(PromptTuneExtractionEvalRuns::getStatus, ACTIVE_STATUSES)
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public List<PromptTuneExtractionEvalRuns> listStaleRunning(LocalDateTime heartbeatBefore) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getStatus, "running")
                .lt(PromptTuneExtractionEvalRuns::getLastHeartbeatAt, heartbeatBefore)
                .list();
    }

    @Override
    public List<PromptTuneExtractionEvalRuns> listAllActive() {
        return this.lambdaQuery()
                .in(PromptTuneExtractionEvalRuns::getStatus, ACTIVE_STATUSES)
                .orderByAsc(PromptTuneExtractionEvalRuns::getId)
                .list();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findByIdWithoutReport(Long id) {
        // ServiceImpl.lambdaQuery 默认 select(*)；这里显式列字段排除 report_json。
        LambdaQueryWrapper<PromptTuneExtractionEvalRuns> q = new LambdaQueryWrapper<>();
        q.select(
                PromptTuneExtractionEvalRuns::getId,
                PromptTuneExtractionEvalRuns::getBuildRunId,
                PromptTuneExtractionEvalRuns::getKnowledgeBaseId,
                PromptTuneExtractionEvalRuns::getSelectedCandidateIds,
                PromptTuneExtractionEvalRuns::getSeed,
                PromptTuneExtractionEvalRuns::getStatus,
                PromptTuneExtractionEvalRuns::getProgressStage,
                PromptTuneExtractionEvalRuns::getExtractingCandidateId,
                PromptTuneExtractionEvalRuns::getFinishedCandidates,
                PromptTuneExtractionEvalRuns::getCandidateFailures,
                PromptTuneExtractionEvalRuns::getEvalDir,
                PromptTuneExtractionEvalRuns::getErrorMessage,
                PromptTuneExtractionEvalRuns::getTriggeredByUserId,
                PromptTuneExtractionEvalRuns::getStartedAt,
                PromptTuneExtractionEvalRuns::getFinishedAt,
                PromptTuneExtractionEvalRuns::getLastHeartbeatAt,
                PromptTuneExtractionEvalRuns::getCreatedAt,
                PromptTuneExtractionEvalRuns::getUpdatedAt
        );
        q.eq(PromptTuneExtractionEvalRuns::getId, id);
        return Optional.ofNullable(this.getOne(q));
    }

    @Override
    public PromptTuneExtractionEvalRuns getRequiredById(Long id) {
        PromptTuneExtractionEvalRuns entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(
                    ApiResultCode.NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "评分任务不存在: " + id
            );
        }
        return entity;
    }
}
```

- [ ] **Step 6：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 7：commit**

```bash
git add sql/migrations/20260517_prompt_tune_extraction_eval_runs.sql backend/ckqa-back/src/main/java/org/ysu/ckqaback/entity/PromptTuneExtractionEvalRuns.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/mapper/PromptTuneExtractionEvalRunsMapper.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/PromptTuneExtractionEvalRunsService.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/service/impl/PromptTuneExtractionEvalRunsServiceImpl.java
git commit -m "feat(prompt-builder): 新建 prompt_tune_extraction_eval_runs 表 + Entity/Mapper/Service 骨架 (Phase 5)"
```

---

## Task 2：扩展错误码 + workspace layout

加 4106 / 4108 / 5008 错误码 + `BuildRunWorkspaceService.createLayout` 增加 `eval/` 子目录。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/BuildRunWorkspaceServiceTest.java`（如已存在则补一个 case）

- [ ] **Step 1：加错误码**

定位 `CANDIDATES_NOT_GENERATED(4105, ...)` 和 `PIPELINE_NOT_IMPLEMENTED(5099, ...)` 之间，整段保持原样并在中间插入 3 个新常量。

**oldStr** 段（实际段内容以仓库为准，本计划为示意）：

```java
    CANDIDATES_NOT_GENERATED(4105, "本次构建尚未生成候选 Prompt，请先调用生成接口"),

    /**
     * 候选 Prompt 生成失败（脚本超时或异常）。
     */
    CANDIDATE_GENERATION_FAILED(5007, "候选 Prompt 生成失败"),
```

**newStr** 段（在 4105 与 5007 之间插入 3 个新常量）：

```java
    CANDIDATES_NOT_GENERATED(4105, "本次构建尚未生成候选 Prompt，请先调用生成接口"),

    /**
     * 04 步评分尚未触发或已结束，前端依赖 status 接口判断。
     */
    EXTRACTION_EVAL_NOT_STARTED(4106, "本次构建尚未启动评分任务"),

    /**
     * 用户传入的 selectedCandidates 含未生成候选 ID（绕过前端门控直接调 API）。
     */
    INVALID_EVAL_CANDIDATE_SELECTION(4108, "选定候选 ID 不在当前构建的候选清单中"),

    /**
     * 候选 Prompt 生成失败（脚本超时或异常）。
     */
    CANDIDATE_GENERATION_FAILED(5007, "候选 Prompt 生成失败"),

    /**
     * 04 步评分执行失败（脚本超时、异常退出或产物缺失）。
     */
    EXTRACTION_EVAL_FAILED(5008, "评分任务执行失败"),
```

实际改动用 `str_replace` 工具，oldStr 含完整 4105 注释 + 5007 注释段。

- [ ] **Step 2：加 workspace layout**

修改 `BuildRunWorkspaceService.createLayout`，把 `"eval"` 加到目录数组末尾：

**oldStr**：
```java
        for (String directory : new String[]{
                "selection",
                "parse",
                "graph-input",
                "prompt",
                "prompt/candidates",
                "index/input",
                "index/output",
                "index/cache",
                "index/reports",
                "index/logs",
                "qa-smoke"
        }) {
```

**newStr**：
```java
        for (String directory : new String[]{
                "selection",
                "parse",
                "graph-input",
                "prompt",
                "prompt/candidates",
                "eval",
                "index/input",
                "index/output",
                "index/cache",
                "index/reports",
                "index/logs",
                "qa-smoke"
        }) {
```

- [ ] **Step 3：跑现有测试确认不破坏**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=BuildRunWorkspaceServiceTest test 2>&1 | tail -5
```

Expected：既有测试 PASS。如果 test 中显式断言 layout 包含特定目录，更新为含 `eval`。

- [ ] **Step 4：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/api/ApiResultCode.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/BuildRunWorkspaceService.java
git commit -m "feat(prompt-builder): 加 4106/4108/5008 错误码 + workspace layout 加 eval/ (Phase 5)"
```

---

## Task 3：扩展 DTO

补 `ExtractionEvalRunStartedResponse`（POST 响应），扩 `ExtractionEvalStatusResponse`（含 evalRunId / status / errorMessage / heartbeat），扩 `ExtractionEvalReportResponse`（含 evalRunId / generatedAt / candidate.displayNameZh）。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRunStartedResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java`
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java`

- [ ] **Step 1：创建 ExtractionEvalRunStartedResponse**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRunStartedResponse.java`：

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /extraction-eval 响应：评分任务已被排队/运行。
 *
 * <p>同步返回 evalRunId 让前端立即开始轮询；reusedActiveRun=true 时表示
 * 复用已存在的同 buildRun 活动任务（决策 4），前端无需启动新任务。</p>
 */
@Getter
@Builder
public class ExtractionEvalRunStartedResponse {

    private final Long evalRunId;
    private final Long buildRunId;
    private final List<String> selectedCandidateIds;

    /** pending / running / cancelling。 */
    private final String status;

    /** 是否复用已存在的活动评分任务。 */
    private final Boolean reusedActiveRun;

    private final LocalDateTime startedAt;

    /** 推荐的轮询间隔（毫秒），前端按这个周期调用 status 接口。 */
    private final Integer recommendedPollingIntervalMillis;
}
```

- [ ] **Step 2：扩展 ExtractionEvalStatusResponse**

Replace 整个 `ExtractionEvalStatusResponse.java` 为：

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /extraction-eval/status 评分进度响应。
 *
 * <p>从 {@code prompt_tune_extraction_eval_runs} 实时投影：</p>
 * <ul>
 *   <li>status / progressStage / errorMessage 直接来自 DB</li>
 *   <li>candidates[] 按 selectedCandidateIds 顺序，结合 finishedCandidates / extractingCandidateId 拼装每条状态</li>
 *   <li>overall.elapsedSeconds = now - startedAt（毫秒级）；estimatedRemainingSeconds 用候选数和已用时近似</li>
 * </ul>
 */
@Getter
@Builder
public class ExtractionEvalStatusResponse {

    /** 评分任务在数据库的主键 ID。 */
    private final Long evalRunId;

    /** pending / running / success / failed / cancelling / cancelled。 */
    private final String status;

    /** queued / extracting / scoring / done。 */
    private final String progressStage;

    /** 失败时的错误摘要。 */
    private final String errorMessage;

    /** 推荐前端轮询间隔（毫秒），任务终态时返回 null。 */
    private final Integer recommendedPollingIntervalMillis;

    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime lastHeartbeatAt;

    private final Overall overall;
    private final List<CandidateProgress> candidates;

    @Getter
    @Builder
    public static class Overall {
        /** 已完成的大模型抽取调用次数（已完成候选数 × 20）。 */
        private final Integer finishedCalls;
        /** 总调用次数（候选数 × 20）。 */
        private final Integer totalCalls;
        /** 起始到现在已用秒数。 */
        private final Integer elapsedSeconds;
        /** 估算剩余秒数（基于已完成候选 + 单候选平均耗时）。 */
        private final Integer estimatedRemainingSeconds;
        /** 已消耗 token 估算值（候选数 × 估算每候选 token），可空。 */
        private final Integer tokensUsed;
        private final Integer estimatedTotalTokens;
    }

    @Getter
    @Builder
    public static class CandidateProgress {
        private final String candidateId;
        private final String displayNameZh;
        /**
         * queued / extracting / scoring / done。
         * <p>本期不在进度 DTO 暴露单候选 failed 状态：单候选失败时 worker 仅追加
         * 结构化 candidate_failures 列与 latestLogs 行（参见 ExtractionEvalWorker.recordFailure），continue 到下一候选；
         * 失败信息最终在排行榜阶段通过 report 的 failedCandidates 数组呈现。</p>
         */
        private final String status;
        private final Stage extract;
        private final Stage score;
    }

    @Getter
    @Builder
    public static class Stage {
        private final Integer finished;
        private final Integer total;
        /** 当前阶段处理的样本 id（保留字段，本期候选粒度时返回 null）。 */
        private final String currentSampleId;
    }
}
```

- [ ] **Step 3：扩展 ExtractionEvalReportResponse**

Replace 整个 `ExtractionEvalReportResponse.java` 为：

```java
package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /extraction-eval/report 评分排行榜响应。
 *
 * <p>评分完成后从 DB report_json 列读出。candidates 按 composite_score 倒序，
 * 含 spec § 04 详情抽屉所需的指标 + 质量门控 + 失败样本。</p>
 */
@Getter
@Builder
public class ExtractionEvalReportResponse {

    private final Long evalRunId;
    private final LocalDateTime generatedAt;
    /**
     * 本次评分启动时 build run 的 seed 快照（system_default / graphrag_tuned / null）。
     * <p>由 Phase 4.5 引入；前端用于展示"本次评分基于哪个种子的候选"。</p>
     */
    private final String seed;
    /** 进入排行榜的候选（成功跑完抽取 + scoring）。 */
    private final List<CandidateReport> candidates;
    /**
     * 未进入排行榜的失败候选（结构化）。
     * <p>来源：worker 把 entity 的 {@code candidate_failures} JSON 列反序列化得到。
     * 排行榜区域不展示这些候选，由前端在排行榜下方"未进入排名"区域单独渲染。
     * 整体 status=success 时也可能存在；只有 finished 全空时整体才 status=failed。</p>
     */
    private final List<CandidateFailure> failedCandidates;

    @Getter
    @Builder
    public static class CandidateReport {
        private final String candidateId;
        private final String displayNameZh;
        private final Integer rank;

        /** composite_score = 0.6 × hard + 0.4 × soft（脚本默认权重）。 */
        private final BigDecimal compositeScore;
        private final BigDecimal parseSuccessRate;
        /** spec 称为"召回率"，对应脚本 audit_entity_recall（可空：audit gold 缺失时）。 */
        private final BigDecimal recall;
        /** spec 称为"准确率"，对应脚本 audit_entity_precision（可空）。 */
        private final BigDecimal precision;
        /** F1 = 2RP/(R+P)，可空（recall/precision 任一为空时）。 */
        private final BigDecimal f1;
        private final BigDecimal entityCountAvg;
        private final BigDecimal relationCountAvg;
        private final Integer tokensUsed;
        private final Integer elapsedSeconds;
        private final List<Gate> gates;
        private final List<FailedSample> failedSamples;
    }

    @Getter
    @Builder
    public static class Gate {
        /** parse_success / audit_recall / audit_precision / relation_direction。 */
        private final String name;
        /** 阈值，relation_direction 时为空（前端按 X/Y 渲染）。 */
        private final BigDecimal threshold;
        /** 实测值，audit 系列指标 audit gold 缺失时为空。 */
        private final BigDecimal value;
        /** value 缺失或 audit 集为空时为 null（前端按"未评估"灰色）。 */
        private final Boolean passed;
        /**
         * 仅 relation_direction 用：关系端点总数（候选输出关系数 × 2）。
         * 其他 gate 返回 null。前端按 (endpointTotalCount - endpointInvalidCount) / endpointTotalCount 渲染分子分母 "X / Y"。
         */
        private final Integer endpointTotalCount;
        /**
         * 仅 relation_direction 用：方向无效的端点数（同上）；其他 gate 返回 null。
         */
        private final Integer endpointInvalidCount;
    }

    @Getter
    @Builder
    public static class FailedSample {
        private final String sampleId;
        private final String reason;
    }

    /**
     * 失败候选审计条目，对应 entity {@code candidate_failures} JSON 列中的一项。
     */
    @Getter
    @Builder
    public static class CandidateFailure {
        private final String candidateId;
        /** 中文展示名（CandidateMetadataLookup 注入），未知 candidate 返回 candidateId 原样。 */
        private final String displayNameZh;
        /** "extract" / "scoring"。 */
        private final String stage;
        private final String reason;
    }
}
```

- [ ] **Step 4：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalRunStartedResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalStatusResponse.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/dto/ExtractionEvalReportResponse.java
git commit -m "feat(prompt-builder): 扩展评分相关 DTO（RunStarted / Status / Report） (Phase 5)"
```

---
## Task 4：ExtractionEvalAsyncConfig + ExtractionEvalOrchestrator

新增独立 worker 线程池配置，新增评分脚本调用包装类（仿 `CandidateGenerationOrchestrator` + `PromptTuneOrchestrator`）。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalAsyncConfig.java`
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalOrchestrator.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalOrchestratorTest.java`

- [ ] **Step 1：写 AsyncConfig**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalAsyncConfig.java`：

```java
package org.ysu.ckqaback.index;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 04 步评分任务专用线程池。
 *
 * <p>串行跑（corePoolSize=1）：避免多 build run 同时打 LLM 服务导致限流。
 * queueCapacity=10 允许排队，超过则拒绝（前端会拿到 5xx）。</p>
 */
@Configuration
public class ExtractionEvalAsyncConfig {

    @Bean(name = "extractionEvalExecutor")
    public Executor extractionEvalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("extraction-eval-");
        executor.setKeepAliveSeconds(0);
        executor.setAllowCoreThreadTimeOut(false);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalOrchestratorTest.java`：

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractionEvalOrchestratorTest {

    private CkqaIntegrationProperties properties;
    private ProcessRunner processRunner;
    private ExtractionEvalOrchestrator orchestrator;

    private void initWithRoot(Path graphragRoot) {
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphragRoot.toString());
        properties.getGraphrag().setPython("/usr/bin/python");
        // managedApi.condaEnv 默认值或显式赋空字符串均可
        processRunner = mock(ProcessRunner.class);
        orchestrator = new ExtractionEvalOrchestrator(properties, processRunner);
    }

    @Test
    void runSingleCandidateExtractInvokesScriptWithExpectedArgs(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path samplesFile = graphragRoot.resolve("audit_with_gold.json");
        Files.writeString(samplesFile, "{}");
        Path promptFile = graphragRoot.resolve("prompt.txt");
        Files.writeString(promptFile, "prompt body");
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);

        ProcessExecutionResult ok = new ProcessExecutionResult(0, "stdout", "", false);
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(ok);

        orchestrator.runSingleCandidateExtract(
                "default",
                samplesFile,
                promptFile,
                "Concept,Term",
                "eval_18_3",
                workspace
        );

        ArgumentCaptor<List<String>> argv = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(argv.capture(), eq(graphragRoot), any(), any(), any());

        // argv 含 -m 模块路径 + 关键参数
        List<String> args = argv.getValue();
        assertThat(args).contains("-m", "scripts.extraction_eval.run_native_extraction");
        assertThat(args).contains("--samples-file", samplesFile.toAbsolutePath().toString());
        assertThat(args).contains("--prompt", promptFile.toAbsolutePath().toString());
        assertThat(args).contains("--candidate-name", "default");
        assertThat(args).contains("--run-id", "eval_18_3");
        assertThat(args).contains("--entity-types", "Concept,Term");
        assertThat(args).contains("--max-gleanings", "1");
        assertThat(args).contains("--concurrency", "1");
        assertThat(args).contains("--overwrite");
    }

    @Test
    void runSingleCandidateExtractRaises5008OnNonZeroExit(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path samplesFile = graphragRoot.resolve("audit.json");
        Files.writeString(samplesFile, "{}");
        Path promptFile = graphragRoot.resolve("p.txt");
        Files.writeString(promptFile, "p");
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);
        when(processRunner.run(any(), any(), any(), any(), any())).thenReturn(
                new ProcessExecutionResult(1, "", "boom", false)
        );

        assertThatThrownBy(() -> orchestrator.runSingleCandidateExtract(
                "default", samplesFile, promptFile, "Concept", "rid", workspace
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("评分抽取失败")
                .hasMessageContaining("boom");
    }

    @Test
    void runScoringInvokesScriptWithExpectedArgs(@TempDir Path graphragRoot) throws Exception {
        initWithRoot(graphragRoot);
        Path workspace = graphragRoot.resolve("ws");
        Files.createDirectories(workspace);
        Path auditFile = graphragRoot.resolve("audit.json");
        Files.writeString(auditFile, "{}");
        when(processRunner.run(any(), any(), any(), any(), any()))
                .thenReturn(new ProcessExecutionResult(0, "summary", "", false));

        orchestrator.runScoring(
                "eval_18_3",
                auditFile,
                workspace
        );

        ArgumentCaptor<List<String>> argv = ArgumentCaptor.forClass(List.class);
        verify(processRunner).run(argv.capture(), eq(graphragRoot), any(), any(), any());

        List<String> args = argv.getValue();
        assertThat(args).contains("-m", "scripts.extraction_eval.score_extraction_results");
        assertThat(args).contains("--audit", auditFile.toAbsolutePath().toString());
        assertThat(args).contains("--run-id", "eval_18_3");
        assertThat(args).contains("--overwrite");
    }
}
```

- [ ] **Step 3：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalOrchestratorTest test 2>&1 | tail -10
```

Expected：编译失败，`ExtractionEvalOrchestrator` 类不存在。

- [ ] **Step 4：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalOrchestrator.java`：

```java
package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.integration.process.PythonCommandResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 04 步评分脚本同步包装：
 * <ul>
 *   <li>{@link #runSingleCandidateExtract} 调 {@code run_native_extraction.py}（单候选模式）</li>
 *   <li>{@link #runScoring} 调 {@code score_extraction_results.py}</li>
 * </ul>
 *
 * <p>本类**不**关心 worker 串行调度 / DB 写入，纯粹是 Python 子进程包装器。
 * 一次抽取的最长耗时按 60 分钟兜底（实测 5-15 分钟）；scoring 30 分钟兜底（实测 < 30 秒）。</p>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalOrchestrator.class);

    /** 单候选 20 样本抽取兜底超时；正常 5-15 分钟，60 分钟基本不会触发。 */
    static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(60);

    /** 评分脚本兜底超时；纯 CPU 计算 + IO，正常 < 30 秒，30 分钟兜底。 */
    static final Duration SCORE_TIMEOUT = Duration.ofMinutes(30);

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    /**
     * 单候选抽取。
     *
     * @param candidateId       候选 id（同时作为 --candidate-name 和输出 JSON 文件名）
     * @param samplesFile       audit_with_gold.json 路径（绝对路径）
     * @param promptFile        候选 prompt.txt 路径（绝对路径）
     * @param entityTypes       逗号分隔的实体类型列表
     * @param runId             共享的 runId，eval_<buildRunId>_<evalRunId>
     * @param workspaceDir      worker 自己的临时工作区，用来落 ProcessContext.logFile
     */
    public void runSingleCandidateExtract(
            String candidateId,
            Path samplesFile,
            Path promptFile,
            String entityTypes,
            String runId,
            Path workspaceDir
    ) throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.extraction_eval.run_native_extraction");
        argv.add("--samples-file");
        argv.add(samplesFile.toAbsolutePath().toString());
        argv.add("--prompt");
        argv.add(promptFile.toAbsolutePath().toString());
        argv.add("--candidate-name");
        argv.add(candidateId);
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--entity-types");
        argv.add(entityTypes);
        argv.add("--max-gleanings");
        argv.add("1");
        argv.add("--concurrency");
        argv.add("1");
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("extract_" + candidateId + ".log");
        ProcessContext context = ProcessContext.builder()
                .operation("extraction-eval-extract")
                .logFile(logFile)
                .build();

        log.info("评分抽取开始: candidateId={}, runId={}", candidateId, runId);
        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), EXTRACT_TIMEOUT, context
        );
        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "评分抽取超时（candidate=" + candidateId + ", >" + EXTRACT_TIMEOUT.toMinutes() + "min）"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分抽取失败 (candidate=" + candidateId + "): " + firstSummary(result.getStderr(), "未知错误")
            );
        }
    }

    /**
     * 全候选抽取完成后跑一次评分。
     *
     * @param runId         与抽取共享的 runId（用于 eval-dir 推断）
     * @param auditFile     audit_with_gold.json 路径
     * @param workspaceDir  log 文件输出目录
     */
    public void runScoring(
            String runId,
            Path auditFile,
            Path workspaceDir
    ) throws IOException, InterruptedException {
        Path scriptRoot = Path.of(properties.getGraphrag().getRoot());
        List<String> argv = new ArrayList<>(PythonCommandResolver.resolve(
                properties.getGraphrag().getPython(),
                properties.getGraphrag().getManagedApi().getCondaEnv()
        ));
        argv.add("-m");
        argv.add("scripts.extraction_eval.score_extraction_results");
        argv.add("--audit");
        argv.add(auditFile.toAbsolutePath().toString());
        argv.add("--run-id");
        argv.add(runId);
        argv.add("--overwrite");

        Path logFile = workspaceDir.resolve("score.log");
        ProcessContext context = ProcessContext.builder()
                .operation("extraction-eval-score")
                .logFile(logFile)
                .build();

        log.info("评分汇总开始: runId={}", runId);
        ProcessExecutionResult result = processRunner.run(
                argv, scriptRoot, Map.of(), SCORE_TIMEOUT, context
        );
        if (result.isTimedOut()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "评分汇总超时"
            );
        }
        if (result.getExitCode() != 0) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分汇总失败: " + firstSummary(result.getStderr(), "未知错误")
            );
        }
    }

    private static String firstSummary(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return fallback;
    }
}
```

- [ ] **Step 5：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalOrchestratorTest test 2>&1 | tail -10
```

Expected：3 个测试 PASS。

- [ ] **Step 6：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalAsyncConfig.java backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalOrchestrator.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalOrchestratorTest.java
git commit -m "feat(prompt-builder): 新建 ExtractionEvalOrchestrator + 独立线程池配置 (Phase 5)"
```

---

## Task 5：ExtractionEvalReportAssembler（DB report_json → ExtractionEvalReportResponse）

把 `top_candidates.json` 的 `top_candidates` / `all_candidates_ranked` 投影到 `ExtractionEvalReportResponse`。负责：注入 displayNameZh、按 spec 阈值重算 gates、生成 BigDecimal、过滤 fallback 候选。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalReportAssemblerTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalReportAssemblerTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionEvalReportAssemblerTest {

    private ExtractionEvalReportAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ExtractionEvalReportAssembler(
                new CandidateMetadataLookup(),
                new ObjectMapper()
        );
    }

    @Test
    void assemblesRankedCandidatesWithDisplayName() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "schema_fewshot_distilled_v2_strict_tuple",
                      "rank": 1,
                      "composite_score": 0.71,
                      "parse_success_rate": 0.95,
                      "audit_entity_recall": 0.74,
                      "audit_entity_precision": 0.68,
                      "endpoint_valid_rate": 0.96,
                      "sample_count": 20,
                      "success_count": 19
                    },
                    {
                      "candidate": "default",
                      "rank": 2,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.80,
                      "audit_entity_recall": 0.45,
                      "audit_entity_precision": 0.42,
                      "endpoint_valid_rate": 0.90,
                      "sample_count": 20,
                      "success_count": 16
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);

        assertThat(response.getEvalRunId()).isEqualTo(run.getId());
        assertThat(response.getCandidates()).hasSize(2);

        ExtractionEvalReportResponse.CandidateReport top = response.getCandidates().get(0);
        assertThat(top.getCandidateId()).isEqualTo("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(top.getDisplayNameZh()).isEqualTo("图谱感知 + 蒸馏样例");
        assertThat(top.getRank()).isEqualTo(1);
        assertThat(top.getCompositeScore()).isEqualByComparingTo(new BigDecimal("0.71"));
    }

    @Test
    void computesGatesAccordingToSpecThresholds() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.80,
                      "audit_entity_recall": 0.49,
                      "audit_entity_precision": 0.50,
                      "endpoint_valid_rate": 0.96,
                      "endpoint_total_count": 50,
                      "endpoint_invalid_count": 2,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        List<ExtractionEvalReportResponse.Gate> gates = response.getCandidates().get(0).getGates();

        // spec 阈值：parse>=0.8 / recall>=0.5 / precision>=0.5 / endpoint>=0.95
        assertThat(gates).hasSize(4);
        ExtractionEvalReportResponse.Gate parse = findGate(gates, "parse_success");
        assertThat(parse.getThreshold()).isEqualByComparingTo(new BigDecimal("0.80"));
        assertThat(parse.getValue()).isEqualByComparingTo(new BigDecimal("0.80"));
        assertThat(parse.getPassed()).isTrue();
        // 非 relation_direction 时分子分母字段必须为 null
        assertThat(parse.getEndpointTotalCount()).isNull();
        assertThat(parse.getEndpointInvalidCount()).isNull();

        ExtractionEvalReportResponse.Gate recall = findGate(gates, "audit_recall");
        // value=0.49 < 0.50 → 不通过
        assertThat(recall.getPassed()).isFalse();

        ExtractionEvalReportResponse.Gate precision = findGate(gates, "audit_precision");
        // value=0.50 >= 0.50 → 通过
        assertThat(precision.getPassed()).isTrue();

        ExtractionEvalReportResponse.Gate direction = findGate(gates, "relation_direction");
        // value=0.96 >= 0.95 → 通过；threshold 为 null（前端按 X/Y 文案展示）
        assertThat(direction.getPassed()).isTrue();
        assertThat(direction.getThreshold()).isNull();
        // relation_direction 透传 endpoint 分子分母，前端用此渲染 "48 / 50"
        assertThat(direction.getEndpointTotalCount()).isEqualTo(50);
        assertThat(direction.getEndpointInvalidCount()).isEqualTo(2);
    }

    @Test
    void marksAuditGatesAsNullWhenAuditValuesMissing() {
        // 边界：audit 集为空时脚本返回 null
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.85,
                      "audit_entity_recall": null,
                      "audit_entity_precision": null,
                      "endpoint_valid_rate": 0.92,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        List<ExtractionEvalReportResponse.Gate> gates = response.getCandidates().get(0).getGates();
        ExtractionEvalReportResponse.Gate recall = findGate(gates, "audit_recall");
        assertThat(recall.getValue()).isNull();
        assertThat(recall.getPassed()).isNull();  // 未评估
    }

    @Test
    void computesF1FromRecallAndPrecision() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {
                      "candidate": "default",
                      "rank": 1,
                      "composite_score": 0.42,
                      "parse_success_rate": 0.85,
                      "audit_entity_recall": 0.6,
                      "audit_entity_precision": 0.4,
                      "endpoint_valid_rate": 0.92,
                      "sample_count": 20
                    }
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        // F1 = 2*0.6*0.4 / (0.6+0.4) = 0.48
        BigDecimal f1 = response.getCandidates().get(0).getF1();
        assertThat(f1).isEqualByComparingTo(new BigDecimal("0.48"));
    }

    @Test
    void skipsUnknownCandidatesWithWarning() {
        // 边界：脚本输出含未在 CandidateMetadataLookup 中的 candidate（不应该发生但要鲁棒）
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                {
                  "all_candidates_ranked": [
                    {"candidate": "default", "rank": 1, "composite_score": 0.4, "parse_success_rate": 0.8, "endpoint_valid_rate": 0.9, "sample_count": 20},
                    {"candidate": "mystery_candidate", "rank": 2, "composite_score": 0.3, "parse_success_rate": 0.7, "endpoint_valid_rate": 0.8, "sample_count": 20}
                  ]
                }
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);
        // 未知 candidate 跳过，剩下 1 条
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo("default");
    }

    @Test
    void returnsEmptyListWhenReportJsonEmpty() {
        PromptTuneExtractionEvalRuns run = newRunWithReport(null);
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getCandidates()).isEmpty();
    }

    @Test
    void exposesSeedFromRunEntity() {
        // Phase 4.5 引入：Report 顶层透传 seed 快照
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        run.setSeed("graphrag_tuned");
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void seedIsNullWhenRunHasNoSeed() {
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        // run.seed 留 null
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getSeed()).isNull();
    }

    @Test
    void exposesFailedCandidatesFromRunEntity() {
        // 风险 1：单候选失败 → entity.candidate_failures 由 worker 写入；report 投影时透传到 failedCandidates 数组
        PromptTuneExtractionEvalRuns run = newRunWithReport("""
                { "all_candidates_ranked": [] }
                """);
        run.setCandidateFailures("""
                [
                  {"candidateId": "default", "stage": "extract", "reason": "timeout"},
                  {"candidateId": "auto_tuned", "stage": "extract", "reason": "prompt 文件不存在"}
                ]
                """);

        ExtractionEvalReportResponse response = assembler.assemble(run);

        assertThat(response.getFailedCandidates()).hasSize(2);
        ExtractionEvalReportResponse.CandidateFailure first = response.getFailedCandidates().get(0);
        assertThat(first.getCandidateId()).isEqualTo("default");
        // 已知 candidate 注入中文展示名
        assertThat(first.getDisplayNameZh()).isEqualTo("默认基线");
        assertThat(first.getStage()).isEqualTo("extract");
        assertThat(first.getReason()).isEqualTo("timeout");
    }

    @Test
    void emptyFailedCandidatesWhenColumnIsNullOrInvalid() {
        // 边界：未发生失败 / 列为空 / JSON 解析失败 → 空 List，永不抛错
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        // 不 set candidateFailures
        assertThat(assembler.assemble(run).getFailedCandidates()).isEmpty();

        run.setCandidateFailures("not-a-json");
        assertThat(assembler.assemble(run).getFailedCandidates()).isEmpty();
    }

    @Test
    void failedCandidatesPreserveUnknownCandidateIdAsDisplayName() {
        // 不在 metadata lookup 白名单中的 candidate，不能丢失，displayNameZh 退化为原 ID
        PromptTuneExtractionEvalRuns run = newRunWithReport("{\"all_candidates_ranked\":[]}");
        run.setCandidateFailures("""
                [{"candidateId": "phantom_x", "stage": "extract", "reason": "unknown"}]
                """);
        ExtractionEvalReportResponse response = assembler.assemble(run);
        assertThat(response.getFailedCandidates()).hasSize(1);
        assertThat(response.getFailedCandidates().get(0).getDisplayNameZh()).isEqualTo("phantom_x");
    }

    private PromptTuneExtractionEvalRuns newRunWithReport(String reportJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(99L);
        r.setReportJson(reportJson);
        r.setFinishedAt(LocalDateTime.of(2026, 5, 17, 12, 0));
        return r;
    }

    private ExtractionEvalReportResponse.Gate findGate(
            List<ExtractionEvalReportResponse.Gate> gates, String name
    ) {
        return gates.stream().filter(g -> name.equals(g.getName())).findFirst().orElseThrow();
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalReportAssemblerTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse.CandidateReport;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse.Gate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 DB report_json（{@code top_candidates.json} 序列化）投影成 {@link ExtractionEvalReportResponse}。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>注入 displayNameZh（来自 {@link CandidateMetadataLookup}）</li>
 *   <li>按 spec § 04 详情抽屉阈值（0.8 / 0.5 / 0.5 / 0.95）重新计算 gates，不直接用脚本 gate_passed</li>
 *   <li>F1 由 recall + precision 重算</li>
 *   <li>过滤未在 CandidateMetadataLookup 白名单中的候选</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalReportAssembler {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalReportAssembler.class);

    /** 与 spec § 04 详情抽屉阈值对齐。 */
    private static final BigDecimal PARSE_SUCCESS_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal RECALL_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal PRECISION_THRESHOLD = new BigDecimal("0.50");
    /** relation_direction 用脚本严格阈值。 */
    private static final BigDecimal RELATION_DIRECTION_THRESHOLD = new BigDecimal("0.95");

    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    public ExtractionEvalReportResponse assemble(PromptTuneExtractionEvalRuns run) {
        return ExtractionEvalReportResponse.builder()
                .evalRunId(run.getId())
                .generatedAt(run.getFinishedAt())
                .seed(run.getSeed())
                .candidates(parseCandidates(run.getReportJson()))
                .failedCandidates(parseFailedCandidates(run.getCandidateFailures()))
                .build();
    }

    /**
     * 把 entity {@code candidate_failures} 列（worker 写入的 JSON 数组）解析成 DTO。
     * <p>解析失败 / 列为空 时返回空 List，永不抛错——失败信息缺失不应让 report 接口失败。</p>
     */
    private List<ExtractionEvalReportResponse.CandidateFailure> parseFailedCandidates(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<ExtractionEvalReportResponse.CandidateFailure> result = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                String candidateId = stringField(entry, "candidateId");
                if (candidateId == null) continue;
                result.add(ExtractionEvalReportResponse.CandidateFailure.builder()
                        .candidateId(candidateId)
                        .displayNameZh(metadataLookup.isKnown(candidateId)
                                ? metadataLookup.displayNameZh(candidateId)
                                : candidateId)
                        .stage(stringField(entry, "stage"))
                        .reason(stringField(entry, "reason"))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 candidate_failures 失败，按空列表返回: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CandidateReport> parseCandidates(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) return List.of();
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(reportJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 report_json 失败：{}", e.getMessage());
            return List.of();
        }
        Object node = root.get("all_candidates_ranked");
        if (!(node instanceof List<?> rawList)) return List.of();

        List<CandidateReport> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) rawMap;
            String candidateId = stringField(entry, "candidate");
            if (candidateId == null || !metadataLookup.isKnown(candidateId)) {
                if (candidateId != null) {
                    log.warn("评分报告含未知候选 {}，已跳过", candidateId);
                }
                continue;
            }
            result.add(buildCandidateReport(candidateId, entry));
        }
        return result;
    }

    private CandidateReport buildCandidateReport(String candidateId, Map<String, Object> entry) {
        BigDecimal recall = bigDecimalField(entry, "audit_entity_recall");
        BigDecimal precision = bigDecimalField(entry, "audit_entity_precision");
        BigDecimal parseSuccess = bigDecimalField(entry, "parse_success_rate");
        BigDecimal endpointValidRate = bigDecimalField(entry, "endpoint_valid_rate");
        Integer endpointTotalCount = integerField(entry, "endpoint_total_count");
        Integer endpointInvalidCount = integerField(entry, "endpoint_invalid_count");

        return CandidateReport.builder()
                .candidateId(candidateId)
                .displayNameZh(metadataLookup.displayNameZh(candidateId))
                .rank(integerField(entry, "rank"))
                .compositeScore(bigDecimalField(entry, "composite_score"))
                .parseSuccessRate(parseSuccess)
                .recall(recall)
                .precision(precision)
                .f1(computeF1(recall, precision))
                .entityCountAvg(bigDecimalField(entry, "entity_count_avg"))
                .relationCountAvg(bigDecimalField(entry, "relation_count_avg"))
                .tokensUsed(integerField(entry, "tokens_used"))
                .elapsedSeconds(integerField(entry, "elapsed_seconds"))
                .gates(computeGates(parseSuccess, recall, precision, endpointValidRate, endpointTotalCount, endpointInvalidCount))
                .failedSamples(List.of())  // 本期不返回成功候选内的"个别失败样本"明细（≠失败候选），留 Phase 9
                .build();
    }

    private List<Gate> computeGates(
            BigDecimal parseSuccess,
            BigDecimal recall,
            BigDecimal precision,
            BigDecimal endpointValidRate,
            Integer endpointTotalCount,
            Integer endpointInvalidCount
    ) {
        return List.of(
                Gate.builder()
                        .name("parse_success")
                        .threshold(PARSE_SUCCESS_THRESHOLD)
                        .value(parseSuccess)
                        .passed(passed(parseSuccess, PARSE_SUCCESS_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("audit_recall")
                        .threshold(RECALL_THRESHOLD)
                        .value(recall)
                        .passed(passed(recall, RECALL_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("audit_precision")
                        .threshold(PRECISION_THRESHOLD)
                        .value(precision)
                        .passed(passed(precision, PRECISION_THRESHOLD))
                        .build(),
                Gate.builder()
                        .name("relation_direction")
                        .threshold(null)  // 前端按 X/Y 文案展示，不显示阈值数值
                        .value(endpointValidRate)
                        .passed(passed(endpointValidRate, RELATION_DIRECTION_THRESHOLD))
                        .endpointTotalCount(endpointTotalCount)
                        .endpointInvalidCount(endpointInvalidCount)
                        .build()
        );
    }

    /** value 为空时返回 null（前端按"未评估"渲染）；否则与阈值比较。 */
    private static Boolean passed(BigDecimal value, BigDecimal threshold) {
        if (value == null) return null;
        return value.compareTo(threshold) >= 0;
    }

    private static BigDecimal computeF1(BigDecimal recall, BigDecimal precision) {
        if (recall == null || precision == null) return null;
        BigDecimal sum = recall.add(precision);
        if (sum.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return recall.multiply(precision)
                .multiply(new BigDecimal("2"))
                .divide(sum, 4, RoundingMode.HALF_UP);
    }

    private static String stringField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof String s ? s : null;
    }

    private static Integer integerField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value instanceof Number n) return n.intValue();
        return null;
    }

    private static BigDecimal bigDecimalField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s && !s.isBlank()) {
            try { return new BigDecimal(s); } catch (NumberFormatException ignore) { }
        }
        return null;
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalReportAssemblerTest test 2>&1 | tail -10
```

Expected：8 个测试 PASS（含 Phase 4.5 引入的 seed 透传两条）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalReportAssembler.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalReportAssemblerTest.java
git commit -m "feat(prompt-builder): 新建 ExtractionEvalReportAssembler 把 report_json 投影成 spec 阈值的报告响应 (Phase 5)"
```

---
## Task 6：ExtractionEvalWorker（异步任务执行器）

仿 `PromptTuneWorker`：dispatch + 串行候选抽取 + scoring + 心跳更新 + cancel 检测 + 终态写入 + 产物复制到 build run workspace。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalWorker.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalWorkerTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalWorkerTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Worker 同步路径单测；不验证 Spring 异步派发，直接调 runInternal。
 *
 * 关键测试点：
 * 1. 候选按顺序串行调用 runSingleCandidateExtract
 * 2. 全部完成后调 runScoring
 * 3. cancelling 状态时立即终止（候选边界软取消）
 * 4. 单个候选失败时仅追加 latestLogs 并继续跑剩余候选；finished 全空才整体 failed
 * 5. 成功时把 top_candidates.json 内容写入 reportJson 列
 */
class ExtractionEvalWorkerTest {

    private PromptTuneExtractionEvalRunsService evalRunsService;
    private KnowledgeBaseBuildRunsService buildRunsService;
    private BuildRunWorkspaceService workspaceService;
    private ExtractionEvalOrchestrator orchestrator;
    private CkqaIntegrationProperties properties;
    private CandidateMetadataLookup metadataLookup;
    private Executor inlineExecutor;
    private ExtractionEvalWorker worker;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        orchestrator = mock(ExtractionEvalOrchestrator.class);
        metadataLookup = new CandidateMetadataLookup();
        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(tmp.resolve("graphrag").toString());
        Files.createDirectories(tmp.resolve("graphrag/results/extraction_eval/runs/eval_18_3"));
        Files.createDirectories(tmp.resolve("graphrag/results/reports/extraction_scoring/runs/eval_18_3"));
        Files.writeString(
                tmp.resolve("graphrag/results/reports/extraction_scoring/runs/eval_18_3/top_candidates.json"),
                "{\"all_candidates_ranked\": []}"
        );

        Path workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/default"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/default/prompt.txt"), "default prompt");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates/auto_tuned"));
        Files.writeString(workspaceDir.resolve("prompt/candidates/auto_tuned/prompt.txt"), "auto prompt");
        Files.writeString(workspaceDir.resolve("prompt/candidates/audit_with_gold.json"), "{\"audit_samples\":[]}");
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);

        inlineExecutor = Runnable::run;  // 同步执行

        worker = new ExtractionEvalWorker(
                evalRunsService,
                buildRunsService,
                workspaceService,
                orchestrator,
                properties,
                metadataLookup,
                new ObjectMapper(),
                inlineExecutor
        );
    }

    @Test
    void runsSelectedCandidatesInOrderThenScores() throws Exception {
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        InOrder inOrder = inOrder(orchestrator);
        inOrder.verify(orchestrator).runSingleCandidateExtract(
                eq("default"), any(), any(), anyString(), eq("eval_18_3"), any()
        );
        inOrder.verify(orchestrator).runSingleCandidateExtract(
                eq("auto_tuned"), any(), any(), anyString(), eq("eval_18_3"), any()
        );
        inOrder.verify(orchestrator).runScoring(eq("eval_18_3"), any(), any());
    }

    @Test
    void marksSuccessAndPersistsReportJson() throws Exception {
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        // 抓 final updateById 的 entity
        AtomicReference<PromptTuneExtractionEvalRuns> finalState = new AtomicReference<>();
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r -> {
            if ("success".equals(r.getStatus())) {
                finalState.set(r);
                return true;
            }
            return false;
        }));
        assertThat(finalState.get()).isNotNull();
        assertThat(finalState.get().getProgressStage()).isEqualTo("done");
        assertThat(finalState.get().getReportJson()).contains("all_candidates_ranked");
        assertThat(finalState.get().getFinishedAt()).isNotNull();
    }

    @Test
    void marksFailedOnlyWhenAllCandidatesFail() throws Exception {
        // 仅一个候选且抛错 → finished 为空 → 整体 failed（保留原"全失败"语义）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
        doThrow(new RuntimeException("extract boom"))
                .when(orchestrator).runSingleCandidateExtract(
                        eq("default"), any(), any(), anyString(), anyString(), any()
                );

        worker.runInternal(3L);

        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "failed".equals(r.getStatus()) && r.getErrorMessage() != null && r.getErrorMessage().contains("全部候选抽取失败")
        ));
        // 不应该尝试跑 scoring
        verify(orchestrator, never()).runScoring(anyString(), any(), any());
    }

    @Test
    void singleCandidateFailureKeepsOverallSuccess() throws Exception {
        // 第一个候选抛错，第二个候选成功 → 整体 success；scoring 仍然跑（决策与风险 1）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
        doThrow(new RuntimeException("default boom"))
                .when(orchestrator).runSingleCandidateExtract(
                        eq("default"), any(), any(), anyString(), anyString(), any()
                );
        // auto_tuned 默认 mock 不抛错，记为成功

        worker.runInternal(3L);

        // scoring 依然被调
        verify(orchestrator, times(1)).runScoring(anyString(), any(), any());
        // 整体最终状态 success
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r -> "success".equals(r.getStatus())));
        // 失败原因被结构化写入 candidate_failures（含 candidateId / stage / reason）
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                r.getCandidateFailures() != null
                        && r.getCandidateFailures().contains("\"candidateId\":\"default\"")
                        && r.getCandidateFailures().contains("\"stage\":\"extract\"")
                        && r.getCandidateFailures().contains("default boom")
        ));
    }

    @Test
    void respectsCancellationBeforeNextCandidate() throws Exception {
        PromptTuneExtractionEvalRuns first = newRun(3L, 18L, "running", "[\"default\",\"auto_tuned\"]");
        // 第二次 query（在第二个候选前 reload）返回 cancelling
        PromptTuneExtractionEvalRuns cancelling = newRun(3L, 18L, "cancelling", "[\"default\",\"auto_tuned\"]");
        when(evalRunsService.getRequiredById(3L))
                .thenReturn(first)        // initial
                .thenReturn(first)        // 第一个候选前 query
                .thenReturn(cancelling)   // 第二个候选前 query → 取消
                .thenReturn(cancelling);  // markCancelled 内部 reload
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        // 只跑了第一个候选
        verify(orchestrator, times(1)).runSingleCandidateExtract(
                eq("default"), any(), any(), anyString(), anyString(), any()
        );
        verify(orchestrator, never()).runSingleCandidateExtract(
                eq("auto_tuned"), any(), any(), anyString(), anyString(), any()
        );
        // 不跑 scoring
        verify(orchestrator, never()).runScoring(anyString(), any(), any());
        // 状态写为 cancelled
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "cancelled".equals(r.getStatus())
        ));
    }

    @Test
    void rejectsUnknownCandidateId() throws Exception {
        // selectedCandidateIds 含不在 manifest 白名单的 ID（绕过 Service 门控）
        PromptTuneExtractionEvalRuns run = newRun(3L, 18L, "running", "[\"unknown_x\"]");
        when(evalRunsService.getRequiredById(3L)).thenReturn(run);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        worker.runInternal(3L);

        verify(orchestrator, never()).runSingleCandidateExtract(any(), any(), any(), anyString(), anyString(), any());
        verify(evalRunsService, atLeastOnce()).updateById(argThat(r ->
                "failed".equals(r.getStatus()) && r.getErrorMessage() != null && r.getErrorMessage().contains("unknown")
        ));
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, Long buildRunId, String status, String selectedJson) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setId(id);
        run.setBuildRunId(buildRunId);
        run.setKnowledgeBaseId(5L);
        run.setSelectedCandidateIds(selectedJson);
        run.setStatus(status);
        run.setProgressStage("queued");
        return run;
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(5L);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalWorkerTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalWorker.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 04 步评分异步 worker。
 *
 * <p>状态机：</p>
 * <pre>
 *   pending → running ─ for each candidate ─→ runSingleCandidateExtract → 心跳推进
 *           ↓ all done             ↓ error                ↓ cancelling 检测
 *           runScoring             markFailed             markCancelled
 *           ↓                      ↓                      ↓
 *           markSuccess + 写 reportJson
 * </pre>
 *
 * <p>磁盘约定：脚本输出仍落 {@code <GRAPHRAG_ROOT>/results/extraction_eval/runs/<runId>/} 共享路径，
 * worker 跑完后把产物复制到 build run workspace 的 {@code eval/<evalRunId>/} 下，并清掉共享路径。</p>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalWorker {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalWorker.class);

    /** 课程域抽取实体类型，与 Phase 3 SingleSampleExtractionOrchestrator 保持一致。
     *  TODO Phase 7+：从 schema 配置文件动态读取，避免与 graphrag_pipeline/config/schema/entity_types.json 漂移。 */
    private static final String DEFAULT_ENTITY_TYPES =
            "Course,Concept,Term,KnowledgePoint,FormulaOrDefinition,AlgorithmOrMethod,Theorem,Property,Example,Topic";

    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final BuildRunWorkspaceService workspaceService;
    private final ExtractionEvalOrchestrator orchestrator;
    private final CkqaIntegrationProperties properties;
    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    @Qualifier("extractionEvalExecutor")
    private final Executor extractionEvalExecutor;

    /**
     * 异步派发：把任务塞进线程池。
     */
    public void dispatch(Long evalRunId) {
        extractionEvalExecutor.execute(() -> {
            try {
                runInternal(evalRunId);
            } catch (RuntimeException exception) {
                log.error("评分任务异常 evalRunId={}", evalRunId, exception);
                markFailed(evalRunId, exception.getMessage());
            }
        });
    }

    void runInternal(Long evalRunId) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(evalRunId);
        markRunning(run.getId());

        KnowledgeBaseBuildRuns buildRun;
        try {
            buildRun = buildRunsService.getRequiredById(run.getBuildRunId());
        } catch (RuntimeException exception) {
            markFailed(run.getId(), "构建运行不存在: " + exception.getMessage());
            return;
        }

        Path workspace = workspaceService.resolve(buildRun.getWorkspaceUri());
        Path candidatesDir = workspace.resolve("prompt/candidates");
        Path auditFile = candidatesDir.resolve("audit_with_gold.json");
        Path evalDir = workspace.resolve("eval").resolve(String.valueOf(run.getId()));
        Path workerLogsDir = evalDir.resolve("logs");
        try {
            Files.createDirectories(workerLogsDir);
        } catch (IOException e) {
            markFailed(run.getId(), "创建评分目录失败: " + e.getMessage());
            return;
        }

        List<String> selected = parseSelectedIds(run.getSelectedCandidateIds());
        // 校验候选 ID（worker 层兜底；正常 Service 已经过滤）
        for (String candidateId : selected) {
            if (!metadataLookup.isKnown(candidateId)) {
                markFailed(run.getId(), "selectedCandidateIds 含 unknown candidate: " + candidateId);
                return;
            }
        }
        if (selected.isEmpty()) {
            markFailed(run.getId(), "selectedCandidateIds 为空");
            return;
        }

        String runId = "eval_" + buildRun.getId() + "_" + run.getId();

        // 共享磁盘路径，worker 跑完会复制到 evalDir 后清理
        Path sharedExtractDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/extraction_eval/runs").resolve(runId);
        Path sharedReportDir = Path.of(properties.getGraphrag().getRoot())
                .resolve("results/reports/extraction_scoring/runs").resolve(runId);

        // 启动前清理可能存在的半成品
        try {
            deleteIfExists(sharedExtractDir);
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            markFailed(run.getId(), "清理共享磁盘路径失败: " + e.getMessage());
            return;
        }

        // ---- 抽取阶段：串行跑每个候选；单候选失败不阻断剩余候选 ----
        updateStage(run.getId(), "extracting", null, List.of());
        List<String> finished = new ArrayList<>();
        // 结构化失败清单（持久化到 candidate_failures 列）：
        // [{"candidateId":"default","stage":"extract","reason":"timeout"}, ...]
        List<Map<String, String>> failures = new ArrayList<>();
        for (String candidateId : selected) {
            // 每候选前检查 cancelling（候选边界软取消，决策 6）
            PromptTuneExtractionEvalRuns reloaded = evalRunsService.getRequiredById(run.getId());
            if ("cancelling".equals(reloaded.getStatus())) {
                markCancelled(run.getId(), finished);
                return;
            }
            updateStage(run.getId(), "extracting", candidateId, finished);
            Path promptFile = candidatesDir.resolve(candidateId).resolve("prompt.txt");
            if (!Files.exists(promptFile)) {
                recordFailure(run.getId(), failures, candidateId, "extract", "候选 prompt 文件不存在");
                continue;
            }
            try {
                orchestrator.runSingleCandidateExtract(
                        candidateId,
                        auditFile,
                        promptFile,
                        DEFAULT_ENTITY_TYPES,
                        runId,
                        workerLogsDir
                );
                finished.add(candidateId);
            } catch (Exception e) {
                // 单候选失败：记录但不抛；剩余候选继续跑
                Throwable rootCause = e;
                while (rootCause.getCause() != null) rootCause = rootCause.getCause();
                String reason = e.getMessage() != null ? e.getMessage() : rootCause.getClass().getSimpleName();
                recordFailure(run.getId(), failures, candidateId, "extract", reason);
            }
        }

        // 整体终态判定（风险 1）：finished 全空才整体 failed
        if (finished.isEmpty()) {
            String failedIds = failures.stream().map(f -> f.get("candidateId")).collect(java.util.stream.Collectors.joining(", "));
            markFailed(run.getId(), "全部候选抽取失败：" + failedIds);
            return;
        }

        // ---- scoring 阶段；scoring 之前再检查一次 cancelling ----
        PromptTuneExtractionEvalRuns reloaded = evalRunsService.getRequiredById(run.getId());
        if ("cancelling".equals(reloaded.getStatus())) {
            markCancelled(run.getId(), finished);
            return;
        }
        updateStage(run.getId(), "scoring", null, finished);
        try {
            orchestrator.runScoring(runId, auditFile, workerLogsDir);
        } catch (Exception e) {
            markFailed(run.getId(), "评分汇总失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }

        // ---- 复制产物到 evalDir + 清理共享磁盘路径 ----
        try {
            copyDirectory(sharedExtractDir, evalDir.resolve("extraction_eval"));
            copyDirectory(sharedReportDir, evalDir.resolve("scoring_report"));
            deleteIfExists(sharedExtractDir);
            deleteIfExists(sharedReportDir);
        } catch (IOException e) {
            log.warn("复制评分产物到 build run workspace 失败 evalRunId={}: {}", run.getId(), e.getMessage());
            // 不强制失败：磁盘清理失败不影响任务结果，下次启动 sweep 时会再尝试
        }

        // ---- 读 top_candidates.json 写 reportJson ----
        Path reportFile = evalDir.resolve("scoring_report/top_candidates.json");
        String reportJson = "";
        try {
            if (Files.exists(reportFile)) {
                reportJson = Files.readString(reportFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("读取 top_candidates.json 失败 evalRunId={}: {}", run.getId(), e.getMessage());
        }
        markSuccess(run.getId(), evalDir, reportJson, finished);
    }

    // -----------------------------------------------------------------
    // 状态机操作
    // -----------------------------------------------------------------

    @Transactional
    protected void markRunning(Long id) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getRequiredById(id);
        if ("running".equals(run.getStatus())) return;
        run.setStatus("running");
        run.setStartedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
    }

    @Transactional
    protected void updateStage(Long id, String stage, String currentCandidateId, List<String> finished) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setProgressStage(stage);
        run.setExtractingCandidateId(currentCandidateId);
        run.setFinishedCandidates(serializeIds(finished));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
    }

    @Transactional
    protected void markSuccess(Long id, Path evalDir, String reportJson, List<String> finished) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("success");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setFinishedCandidates(serializeIds(finished));
        run.setEvalDir(toRelativeWorkspaceUri(evalDir));
        run.setReportJson(reportJson);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务成功 evalRunId={}, finishedCandidates={}", id, finished);
    }

    @Transactional
    protected void markFailed(Long id, String error) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("failed");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setErrorMessage(truncate(error, 1000));
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.warn("评分任务失败 evalRunId={}, error={}", id, error);
    }

    @Transactional
    protected void markCancelled(Long id, List<String> finishedSoFar) {
        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        run.setStatus("cancelled");
        run.setProgressStage("done");
        run.setExtractingCandidateId(null);
        run.setFinishedCandidates(serializeIds(finishedSoFar));
        run.setFinishedAt(LocalDateTime.now());
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务已取消 evalRunId={}", id);
    }

    /**
     * 记录单候选失败：往结构化 candidate_failures 列追加 JSON 项 + 同时写一条 latestLogs 行
     * （前者给前端 report.failedCandidates 用，后者给运维快速排障用）。
     *
     * <p>用于"单候选 failed 但整体仍可能 success"场景的审计闭环。
     * stage 取 "extract" / "scoring"；reason 简短，长字符串会被截断到 500 字节。</p>
     */
    @Transactional
    protected void recordFailure(Long id,
                                 List<Map<String, String>> inMemoryFailures,
                                 String candidateId,
                                 String stage,
                                 String reason) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("candidateId", candidateId);
        entry.put("stage", stage);
        entry.put("reason", truncate(reason, 500));
        inMemoryFailures.add(entry);

        PromptTuneExtractionEvalRuns run = evalRunsService.getById(id);
        if (run == null) return;
        try {
            run.setCandidateFailures(objectMapper.writeValueAsString(inMemoryFailures));
        } catch (JsonProcessingException e) {
            // 序列化失败时仅记日志，不让审计写失败阻断主流程
            log.warn("写入 candidate_failures 失败 evalRunId={}: {}", id, e.getMessage());
        }
        String prevLogs = run.getLatestLogs() == null ? "" : run.getLatestLogs();
        String nextLogs = (prevLogs + "\n[" + LocalDateTime.now() + "] [" + stage + "] " + candidateId + ": " + reason).strip();
        run.setLatestLogs(truncate(nextLogs, 4000));
        run.setLastHeartbeatAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.warn("候选异常 evalRunId={} candidate={} stage={}: {}", id, candidateId, stage, reason);
    }

    // -----------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------

    private List<String> parseSelectedIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String serializeIds(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 把 worker 中的绝对 Path 转成相对 GRAPHRAG_BUILD_RUNS_ROOT 的工作区 URI（与 evalDir 字段注释一致）。
     * <p>形式：{@code user_X/kb_Y/build_Z/eval/<evalRunId>}。这样 DB 不灌机器绝对路径，
     * 真实部署可在 {@code GRAPHRAG_BUILD_RUNS_ROOT} 切换路径时无需迁移数据。</p>
     */
    private String toRelativeWorkspaceUri(Path absolute) {
        Path runsRoot = Path.of(properties.getGraphrag().getBuildRunsRoot()).toAbsolutePath().normalize();
        Path normalized = absolute.toAbsolutePath().normalize();
        if (!normalized.startsWith(runsRoot)) {
            // 不在 build runs root 下：兜底退化为绝对路径（极少见，只在测试或异常部署时发生）
            log.warn("evalDir 不在 GRAPHRAG_BUILD_RUNS_ROOT 下，退化为绝对路径 abs={}, root={}",
                    normalized, runsRoot);
            return normalized.toString();
        }
        return runsRoot.relativize(normalized).toString().replace('\\', '/');
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static void deleteIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignore) { }
                    });
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path rel = source.relativize(p);
                    Path dst = target.resolve(rel.toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalWorkerTest test 2>&1 | tail -10
```

Expected：5 个测试 PASS。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalWorker.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalWorkerTest.java
git commit -m "feat(prompt-builder): 新建 ExtractionEvalWorker 串行候选抽取 + scoring + 取消 + 产物复制 (Phase 5)"
```

---

## Task 7：ExtractionEvalService（业务编排：trigger / status / report / cancel）

整合：门控 → 写 pending → after-commit dispatch；status 投影；report 投影；cancel 状态切换。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalServiceTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalServiceTest.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExtractionEvalServiceTest {

    private KnowledgeBaseBuildRunsService buildRunsService;
    private PromptTuneAuditSamplesService samplesService;
    private PromptTuneExtractionEvalRunsService evalRunsService;
    private CandidateManifestReader manifestReader;
    private BuildRunWorkspaceService workspaceService;
    private ExtractionEvalReportAssembler reportAssembler;
    private ExtractionEvalWorker worker;
    private ObjectMapper objectMapper;
    private ExtractionEvalService service;

    private Path workspaceDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        buildRunsService = mock(KnowledgeBaseBuildRunsService.class);
        samplesService = mock(PromptTuneAuditSamplesService.class);
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        manifestReader = mock(CandidateManifestReader.class);
        workspaceService = mock(BuildRunWorkspaceService.class);
        reportAssembler = mock(ExtractionEvalReportAssembler.class);
        worker = mock(ExtractionEvalWorker.class);
        objectMapper = new ObjectMapper();

        service = new ExtractionEvalService(
                buildRunsService,
                samplesService,
                evalRunsService,
                manifestReader,
                workspaceService,
                reportAssembler,
                worker,
                objectMapper
        );

        workspaceDir = tmp.resolve("kb-build-runs/user_0/kb_5/build_18");
        Files.createDirectories(workspaceDir.resolve("prompt/candidates"));
        when(workspaceService.resolve(any())).thenReturn(workspaceDir);
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));
    }

    // ----- trigger -----

    @Test
    void triggerThrows4104WhenNoCompletedSamples() {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(
                newSample("pending"), newSample("in_progress")
        ));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED);
                });
    }

    @Test
    void triggerThrows4105WhenCandidatesNotGenerated() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of());

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.CANDIDATES_NOT_GENERATED);
                });
    }

    @Test
    void triggerThrows4108WhenSelectedCandidateIdNotInManifest() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(
                stubCandidateResponse("default")
        ));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default", "phantom_candidate"));

        assertThatThrownBy(() -> service.trigger(18L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiResultCode.INVALID_EVAL_CANDIDATE_SELECTION);
                    assertThat(e.getMessage()).contains("phantom_candidate");
                });
    }

    @Test
    void triggerReturnsExistingActiveRunInsteadOfCreatingNew() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        PromptTuneExtractionEvalRuns active = newRun(7L, "running", "[\"default\"]");
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.of(active));

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        ExtractionEvalRunStartedResponse response = service.trigger(18L, req);

        assertThat(response.getEvalRunId()).isEqualTo(7L);
        assertThat(response.getReusedActiveRun()).isTrue();
        verify(worker, never()).dispatch(any());
    }

    @Test
    void triggerCreatesPendingRunAndDispatchesAfterCommit() throws Exception {
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(123L);
            return true;
        });

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));

        ExtractionEvalRunStartedResponse response = service.trigger(18L, req);

        assertThat(response.getEvalRunId()).isEqualTo(123L);
        assertThat(response.getReusedActiveRun()).isFalse();
        // 测试环境无事务上下文 → 直接 dispatch
        verify(worker).dispatch(123L);

        // pending 行字段
        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
        assertThat(captor.getValue().getProgressStage()).isEqualTo("queued");
        assertThat(captor.getValue().getSelectedCandidateIds()).contains("default");
    }

    @Test
    void triggerWritesSeedSnapshotFromBuildRunMetadata() {
        // Phase 4.5 引入：启动评分时把 build run metadata.customPromptDraft.seed 写入 eval run
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(124L);
            return true;
        });
        // 覆盖默认 newBuildRun(18L)，注入含 seed 的 metadata
        KnowledgeBaseBuildRuns withSeed = newBuildRun(18L);
        withSeed.setBuildMetadata("{\"customPromptDraft\":{\"seed\":\"graphrag_tuned\"}}");
        when(buildRunsService.getRequiredById(18L)).thenReturn(withSeed);

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));
        service.trigger(18L, req);

        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getSeed()).isEqualTo("graphrag_tuned");
    }

    @Test
    void triggerWritesNullSeedWhenMetadataMissing() {
        // 兼容路径：build run 没有 customPromptDraft.seed 字段时 seed 写 null
        when(samplesService.listByBuildRunId(18L)).thenReturn(List.of(newSample("completed")));
        when(manifestReader.read(any())).thenReturn(List.of(stubCandidateResponse("default")));
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        when(evalRunsService.save(any())).thenAnswer(inv -> {
            PromptTuneExtractionEvalRuns r = inv.getArgument(0);
            r.setId(125L);
            return true;
        });

        ExtractionEvalRequest req = new ExtractionEvalRequest();
        req.setSelectedCandidates(List.of("default"));
        service.trigger(18L, req);

        ArgumentCaptor<PromptTuneExtractionEvalRuns> captor = ArgumentCaptor.forClass(PromptTuneExtractionEvalRuns.class);
        verify(evalRunsService).save(captor.capture());
        assertThat(captor.getValue().getSeed()).isNull();
    }

    // ----- status -----

    @Test
    void getStatusThrows4106WhenNoRunExists() {
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus(18L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ApiResultCode.EXTRACTION_EVAL_NOT_STARTED));
    }

    @Test
    void getStatusReturnsCandidateProgressMatchingFinished() {
        PromptTuneExtractionEvalRuns run = newRun(3L, "running", "[\"default\",\"auto_tuned\"]");
        run.setProgressStage("extracting");
        run.setExtractingCandidateId("auto_tuned");
        run.setFinishedCandidates("[\"default\"]");
        when(evalRunsService.findLatestByBuildRunId(18L)).thenReturn(Optional.of(run));
        when(buildRunsService.getRequiredById(18L)).thenReturn(newBuildRun(18L));

        ExtractionEvalStatusResponse response = service.getStatus(18L);

        assertThat(response.getEvalRunId()).isEqualTo(3L);
        assertThat(response.getStatus()).isEqualTo("running");
        assertThat(response.getCandidates()).hasSize(2);
        assertThat(response.getCandidates().get(0).getCandidateId()).isEqualTo("default");
        assertThat(response.getCandidates().get(0).getStatus()).isEqualTo("done");
        assertThat(response.getCandidates().get(1).getCandidateId()).isEqualTo("auto_tuned");
        assertThat(response.getCandidates().get(1).getStatus()).isEqualTo("extracting");
    }

    // ----- cancel -----

    @Test
    void cancelTransitionsRunningToCancelling() {
        PromptTuneExtractionEvalRuns run = newRun(3L, "running", "[\"default\"]");
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.of(run));

        service.cancel(18L);

        verify(evalRunsService).updateById(argThat(r -> "cancelling".equals(r.getStatus())));
    }

    @Test
    void cancelIsNoOpWhenNoActiveRun() {
        when(evalRunsService.findActiveByBuildRunId(18L)).thenReturn(Optional.empty());
        // 不抛错（幂等）
        service.cancel(18L);
        verify(evalRunsService, never()).updateById(any());
    }

    // ----- helpers -----

    private static PromptTuneAuditSamples newSample(String decision) {
        PromptTuneAuditSamples s = new PromptTuneAuditSamples();
        s.setReviewerDecision(decision);
        return s;
    }

    private static KnowledgeBaseBuildRuns newBuildRun(Long id) {
        KnowledgeBaseBuildRuns r = new KnowledgeBaseBuildRuns();
        r.setId(id);
        r.setKnowledgeBaseId(5L);
        r.setRequestedByUserId(0L);
        r.setWorkspaceUri("user_0/kb_5/build_18");
        return r;
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, String status, String selectedJson) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setBuildRunId(18L);
        r.setKnowledgeBaseId(5L);
        r.setSelectedCandidateIds(selectedJson);
        r.setStatus(status);
        r.setProgressStage("queued");
        return r;
    }

    private static org.ysu.ckqaback.index.dto.CandidateResponse stubCandidateResponse(String id) {
        return org.ysu.ckqaback.index.dto.CandidateResponse.builder().candidateId(id).build();
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalServiceTest test 2>&1 | tail -10
```

Expected：编译失败。

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java`：

```java
package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 04 步评分业务编排：
 * <ul>
 *   <li>{@link #trigger}：门控（02 完成 / 候选已生成 / 选定候选合法）→ 复用 active / 创建 pending → after-commit dispatch</li>
 *   <li>{@link #getStatus}：从 DB 投影最新 run 的进度</li>
 *   <li>{@link #getReport}：从 DB report_json 投影报告</li>
 *   <li>{@link #cancel}：把 active run 切换到 cancelling，worker 自感知后落终态</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExtractionEvalService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalService.class);

    private static final int POLLING_INTERVAL_MS = 1500;

    /** 单候选预估 token（与 candidates 列表中估算保持口径一致），用于 overall.estimatedTotalTokens。 */
    private static final int ESTIMATED_TOKEN_PER_CANDIDATE = 5000 * 20;

    /** 单候选 20 样本预估总耗时秒数（5-15 分钟，取中位 8 分钟）。 */
    private static final int ESTIMATED_SECONDS_PER_CANDIDATE = 8 * 60;

    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final PromptTuneAuditSamplesService samplesService;
    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final CandidateManifestReader manifestReader;
    private final BuildRunWorkspaceService workspaceService;
    private final ExtractionEvalReportAssembler reportAssembler;
    private final ExtractionEvalWorker worker;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExtractionEvalRunStartedResponse trigger(Long buildRunId, ExtractionEvalRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsService.getRequiredById(buildRunId);

        // 门控 1：02 步至少 1 条 completed
        List<PromptTuneAuditSamples> samples = samplesService.listByBuildRunId(buildRunId);
        boolean hasCompleted = samples.stream()
                .anyMatch(s -> "completed".equals(s.getReviewerDecision()));
        if (!hasCompleted) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED,
                    HttpStatus.BAD_REQUEST
            );
        }

        // 门控 2：03 步候选已生成
        Path candidatesDir = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("candidates");
        List<CandidateResponse> candidates;
        try {
            candidates = manifestReader.read(candidatesDir);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATES_NOT_GENERATED,
                    HttpStatus.BAD_REQUEST
            );
        }

        // 门控 3：selectedCandidates ⊆ 已生成候选
        Set<String> generatedIds = new LinkedHashSet<>();
        for (CandidateResponse c : candidates) generatedIds.add(c.getCandidateId());
        List<String> selected = request.getSelectedCandidates();
        List<String> unknown = new ArrayList<>();
        for (String id : selected) {
            if (!generatedIds.contains(id)) unknown.add(id);
        }
        if (!unknown.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.INVALID_EVAL_CANDIDATE_SELECTION,
                    HttpStatus.BAD_REQUEST,
                    "未识别的候选 ID：" + String.join(", ", unknown)
            );
        }

        // 复用 active：避免双开任务
        Optional<PromptTuneExtractionEvalRuns> active = evalRunsService.findActiveByBuildRunId(buildRunId);
        if (active.isPresent()) {
            return toStartedResponse(active.get(), true);
        }

        // 创建 pending + after-commit dispatch
        PromptTuneExtractionEvalRuns run = createPending(buildRun, selected);
        dispatchAfterCommit(run.getId());
        return toStartedResponse(run, false);
    }

    public ExtractionEvalStatusResponse getStatus(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> latest =
                evalRunsService.findLatestByBuildRunId(buildRunId);
        if (latest.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND
            );
        }
        return projectStatus(latest.get());
    }

    public ExtractionEvalReportResponse getReport(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> latest =
                evalRunsService.findLatestByBuildRunId(buildRunId);
        if (latest.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND
            );
        }
        PromptTuneExtractionEvalRuns run = latest.get();
        if (!"success".equals(run.getStatus())) {
            // 评分未成功 → 让前端继续轮询 status；不返回半成品报告
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_STARTED,
                    HttpStatus.NOT_FOUND,
                    "评分未完成，当前状态：" + run.getStatus()
            );
        }
        return reportAssembler.assemble(run);
    }

    @Transactional
    public void cancel(Long buildRunId) {
        Optional<PromptTuneExtractionEvalRuns> active =
                evalRunsService.findActiveByBuildRunId(buildRunId);
        if (active.isEmpty()) return;  // 幂等
        PromptTuneExtractionEvalRuns run = active.get();
        if ("cancelling".equals(run.getStatus())) return;
        run.setStatus("cancelling");
        run.setUpdatedAt(LocalDateTime.now());
        evalRunsService.updateById(run);
        log.info("评分任务请求取消 evalRunId={}", run.getId());
    }

    // -----------------------------------------------------------------
    // 辅助
    // -----------------------------------------------------------------

    private PromptTuneExtractionEvalRuns createPending(KnowledgeBaseBuildRuns buildRun, List<String> selected) {
        PromptTuneExtractionEvalRuns run = new PromptTuneExtractionEvalRuns();
        run.setBuildRunId(buildRun.getId());
        run.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        run.setSelectedCandidateIds(serialize(selected));
        run.setSeed(resolveSeedFromMetadata(buildRun));  // Phase 4.5 引入：seed 快照
        run.setStatus("pending");
        run.setProgressStage("queued");
        run.setTriggeredByUserId(buildRun.getRequestedByUserId());
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        evalRunsService.save(run);
        return run;
    }

    private void dispatchAfterCommit(Long evalRunId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            worker.dispatch(evalRunId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.dispatch(evalRunId);
            }
        });
    }

    private ExtractionEvalRunStartedResponse toStartedResponse(PromptTuneExtractionEvalRuns run, boolean reused) {
        return ExtractionEvalRunStartedResponse.builder()
                .evalRunId(run.getId())
                .buildRunId(run.getBuildRunId())
                .selectedCandidateIds(parseSelectedIds(run.getSelectedCandidateIds()))
                .status(run.getStatus())
                .reusedActiveRun(reused)
                .startedAt(run.getStartedAt())
                .recommendedPollingIntervalMillis(POLLING_INTERVAL_MS)
                .build();
    }

    private ExtractionEvalStatusResponse projectStatus(PromptTuneExtractionEvalRuns run) {
        List<String> selected = parseSelectedIds(run.getSelectedCandidateIds());
        List<String> finished = parseSelectedIds(run.getFinishedCandidates());

        List<ExtractionEvalStatusResponse.CandidateProgress> progresses = new ArrayList<>();
        CandidateMetadataLookup metadataLookup = new CandidateMetadataLookup();
        for (String id : selected) {
            String candidateStatus;
            if (finished.contains(id)) {
                candidateStatus = "done";
            } else if (id.equals(run.getExtractingCandidateId())) {
                candidateStatus = "scoring".equals(run.getProgressStage()) ? "scoring" : "extracting";
            } else if ("failed".equals(run.getStatus())) {
                candidateStatus = "failed";
            } else {
                candidateStatus = "queued";
            }
            int extractFinished = finished.contains(id) ? 20 : 0;
            progresses.add(ExtractionEvalStatusResponse.CandidateProgress.builder()
                    .candidateId(id)
                    .displayNameZh(metadataLookup.displayNameZh(id))
                    .status(candidateStatus)
                    .extract(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(extractFinished)
                            .total(20)
                            .currentSampleId(null)
                            .build())
                    .score(ExtractionEvalStatusResponse.Stage.builder()
                            .finished(finished.contains(id) ? 1 : 0)
                            .total(1)
                            .currentSampleId(null)
                            .build())
                    .build());
        }

        int totalCandidates = selected.size();
        int finishedCalls = finished.size() * 20;
        int totalCalls = totalCandidates * 20;

        Integer elapsedSeconds = null;
        if (run.getStartedAt() != null) {
            LocalDateTime end = run.getFinishedAt() != null ? run.getFinishedAt() : LocalDateTime.now();
            elapsedSeconds = (int) Duration.between(run.getStartedAt(), end).toSeconds();
        }
        Integer estimatedRemainingSeconds = null;
        if (elapsedSeconds != null && finished.size() < totalCandidates) {
            int remaining = (totalCandidates - finished.size()) * ESTIMATED_SECONDS_PER_CANDIDATE;
            estimatedRemainingSeconds = remaining;
        } else if (finished.size() == totalCandidates) {
            estimatedRemainingSeconds = 0;
        }

        ExtractionEvalStatusResponse.Overall overall = ExtractionEvalStatusResponse.Overall.builder()
                .finishedCalls(finishedCalls)
                .totalCalls(totalCalls)
                .elapsedSeconds(elapsedSeconds)
                .estimatedRemainingSeconds(estimatedRemainingSeconds)
                .tokensUsed(finished.size() * ESTIMATED_TOKEN_PER_CANDIDATE)
                .estimatedTotalTokens(totalCandidates * ESTIMATED_TOKEN_PER_CANDIDATE)
                .build();

        boolean terminal = "success".equals(run.getStatus())
                || "failed".equals(run.getStatus())
                || "cancelled".equals(run.getStatus());

        return ExtractionEvalStatusResponse.builder()
                .evalRunId(run.getId())
                .status(run.getStatus())
                .progressStage(run.getProgressStage())
                .errorMessage(run.getErrorMessage())
                .recommendedPollingIntervalMillis(terminal ? null : POLLING_INTERVAL_MS)
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .lastHeartbeatAt(run.getLastHeartbeatAt())
                .overall(overall)
                .candidates(progresses)
                .build();
    }

    private List<String> parseSelectedIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String serialize(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化候选 ID 失败"
            );
        }
    }

    /**
     * Phase 4.5 引入：从 build run metadata 取 seed 写入 eval run 快照。
     * 缺失时返回 null（按"未指定种子"对待，与 Phase 4 兼容）。
     */
    private String resolveSeedFromMetadata(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(metadata);
            com.fasterxml.jackson.databind.JsonNode seed = root.path("customPromptDraft").path("seed");
            return seed.isTextual() ? seed.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalServiceTest test 2>&1 | tail -10
```

Expected：11 个测试 PASS（含 Phase 4.5 引入的 seed 写入两条）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalService.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalServiceTest.java
git commit -m "feat(prompt-builder): 新建 ExtractionEvalService 三重门控 + after-commit dispatch + 状态投影 (Phase 5)"
```

---

## Task 8：ExtractionEvalStartupRecovery（启动恢复 — 三态分流）

仿 `PromptTuneStartupRecovery` 但更完整：服务重启时把所有 active 任务（pending / running / cancelling）按状态分流恢复，避免 trigger 复用逻辑被僵尸任务阻塞（决策 7）。

**Files:**
- Create: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecovery.java`
- Test: `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecoveryTest.java`

- [ ] **Step 1：写失败的测试**

Create `backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecoveryTest.java`：

```java
package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExtractionEvalStartupRecoveryTest {

    private PromptTuneExtractionEvalRunsService evalRunsService;
    private ExtractionEvalWorker worker;
    private ExtractionEvalStartupRecovery recovery;

    @BeforeEach
    void setUp() {
        evalRunsService = mock(PromptTuneExtractionEvalRunsService.class);
        worker = mock(ExtractionEvalWorker.class);
        recovery = new ExtractionEvalStartupRecovery(evalRunsService, worker);
    }

    @Test
    void redispatchesPendingTasks() {
        PromptTuneExtractionEvalRuns pending = newRun(5L, "pending");
        when(evalRunsService.listAllActive()).thenReturn(List.of(pending));

        recovery.run(new DefaultApplicationArguments());

        // pending 不动 DB，由 worker.dispatch 重新派发
        verify(worker).dispatch(eq(5L));
        verify(evalRunsService, never()).updateById(any());
    }

    @Test
    void marksRunningTasksAsFailed() {
        PromptTuneExtractionEvalRuns running = newRun(7L, "running");
        running.setLastHeartbeatAt(LocalDateTime.now().minusMinutes(10));
        when(evalRunsService.listAllActive()).thenReturn(List.of(running));

        recovery.run(new DefaultApplicationArguments());

        verify(worker, never()).dispatch(any());
        verify(evalRunsService).updateById(argThat(r ->
                "failed".equals(r.getStatus())
                        && r.getErrorMessage() != null
                        && r.getErrorMessage().contains("服务重启")
                        && r.getFinishedAt() != null
        ));
    }

    @Test
    void marksCancellingTasksAsCancelled() {
        // 用户已请求取消但 worker 没来得及收尾就被服务重启 → 直接落 cancelled
        PromptTuneExtractionEvalRuns cancelling = newRun(9L, "cancelling");
        when(evalRunsService.listAllActive()).thenReturn(List.of(cancelling));

        recovery.run(new DefaultApplicationArguments());

        verify(worker, never()).dispatch(any());
        verify(evalRunsService).updateById(argThat(r ->
                "cancelled".equals(r.getStatus())
                        && r.getFinishedAt() != null
        ));
    }

    @Test
    void doesNothingWhenNoActiveTasks() {
        when(evalRunsService.listAllActive()).thenReturn(List.of());
        recovery.run(new DefaultApplicationArguments());
        verify(worker, never()).dispatch(any());
        verify(evalRunsService, never()).updateById(any());
    }

    private static PromptTuneExtractionEvalRuns newRun(Long id, String status) {
        PromptTuneExtractionEvalRuns r = new PromptTuneExtractionEvalRuns();
        r.setId(id);
        r.setStatus(status);
        return r;
    }
}
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalStartupRecoveryTest test 2>&1 | tail -10
```

- [ ] **Step 3：写实现**

Create `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecovery.java`：

```java
package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务启动时的评分任务恢复扫描。
 *
 * <p>决策 7：所有 active 任务在服务重启后必然与原进程脱钩，按状态分三类恢复：</p>
 * <ul>
 *   <li>{@code pending} → 重新 dispatch（worker 接管后会自然切 running）</li>
 *   <li>{@code running} → 标 {@code failed}，error_message 注明心跳时间</li>
 *   <li>{@code cancelling} → 标 {@code cancelled}，等价于"用户的取消请求被服务终结时收尾"</li>
 * </ul>
 *
 * <p>不再像 Phase 4 PromptTuneStartupRecovery 那样仅扫 stale running，
 * 因为 after-commit dispatch 留下的 pending 与用户请求取消后崩溃留下的 cancelling
 * 都会卡住后续 trigger 复用逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class ExtractionEvalStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalStartupRecovery.class);

    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final ExtractionEvalWorker worker;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<PromptTuneExtractionEvalRuns> active = evalRunsService.listAllActive();
            if (active.isEmpty()) return;
            log.warn("启动时发现 {} 个失联评分任务，按状态分类恢复", active.size());
            for (PromptTuneExtractionEvalRuns run : active) {
                String status = run.getStatus();
                if ("pending".equalsIgnoreCase(status)) {
                    log.info("评分任务 {} 处于 pending，重新派发", run.getId());
                    worker.dispatch(run.getId());
                } else if ("running".equalsIgnoreCase(status)) {
                    run.setStatus("failed");
                    run.setProgressStage("done");
                    run.setErrorMessage("服务重启时被中断（last heartbeat: " + run.getLastHeartbeatAt() + "）");
                    run.setFinishedAt(LocalDateTime.now());
                    run.setUpdatedAt(LocalDateTime.now());
                    evalRunsService.updateById(run);
                    log.warn("评分任务 {} 处于 running，标记为 failed", run.getId());
                } else if ("cancelling".equalsIgnoreCase(status)) {
                    run.setStatus("cancelled");
                    run.setProgressStage("done");
                    run.setErrorMessage("服务重启时被中断（用户已请求取消）");
                    run.setFinishedAt(LocalDateTime.now());
                    run.setUpdatedAt(LocalDateTime.now());
                    evalRunsService.updateById(run);
                    log.warn("评分任务 {} 处于 cancelling，标记为 cancelled", run.getId());
                }
            }
        } catch (RuntimeException exception) {
            log.error("评分任务启动恢复失败，跳过", exception);
        }
    }
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -Dtest=ExtractionEvalStartupRecoveryTest test 2>&1 | tail -10
```

Expected：4 个测试 PASS（pending 重派发 / running → failed / cancelling → cancelled / 空列表 noop）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecovery.java backend/ckqa-back/src/test/java/org/ysu/ckqaback/index/ExtractionEvalStartupRecoveryTest.java
git commit -m "feat(prompt-builder): 新建 ExtractionEvalStartupRecovery 三态分流恢复（pending 重派发 / running → failed / cancelling → cancelled）(Phase 5)"
```

---
## Task 9：替换 Controller 占位 + 新增 cancel 端点

把 `PromptTunePipelineController` 中 3 个 501 占位换成真实实现，再加一个 `POST /extraction-eval/cancel`。

**Files:**
- Modify: `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`

- [ ] **Step 1：注入 ExtractionEvalService**

定位类字段段：

**oldStr**：
```java
    private final AuditSampleService auditSampleService;
    private final AiSuggestionService aiSuggestionService;
    private final CandidateService candidateService;
```

**newStr**：
```java
    private final AuditSampleService auditSampleService;
    private final AiSuggestionService aiSuggestionService;
    private final CandidateService candidateService;
    private final ExtractionEvalService extractionEvalService;
```

补 import：

**oldStr**：
```java
import org.ysu.ckqaback.index.AiSuggestionService;
import org.ysu.ckqaback.index.AuditSampleService;
import org.ysu.ckqaback.index.CandidateService;
```

**newStr**：
```java
import org.ysu.ckqaback.index.AiSuggestionService;
import org.ysu.ckqaback.index.AuditSampleService;
import org.ysu.ckqaback.index.CandidateService;
import org.ysu.ckqaback.index.ExtractionEvalService;
```

补 ExtractionEvalRunStartedResponse import：

**oldStr**：
```java
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
```

**newStr**：
```java
import org.ysu.ckqaback.index.dto.ExtractionEvalRequest;
import org.ysu.ckqaback.index.dto.ExtractionEvalReportResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalRunStartedResponse;
import org.ysu.ckqaback.index.dto.ExtractionEvalStatusResponse;
```

- [ ] **Step 2：替换 3 个 501 占位 + 新增 cancel 端点**

定位 04 步整段。

**oldStr**：
```java
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval")
    public ApiResponse<PipelineStepResponse> startExtractionEval(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody ExtractionEvalRequest request
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/status")
    public ApiResponse<ExtractionEvalStatusResponse> getExtractionEvalStatus(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }

    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/report")
    public ApiResponse<ExtractionEvalReportResponse> getExtractionEvalReport(
            @PathVariable @Positive(message = "id必须大于0") Long id
    ) {
        throw notImplemented();
    }
```

**newStr**：
```java
    /**
     * 04 步：触发候选 prompt 评分。
     *
     * <p>异步：立即返回 evalRunId + status=pending（或复用已有 active 任务），前端轮询
     * status 接口跟踪进度。门控：02 ≥1 completed + 03 候选已生成 + selectedCandidates ⊆ 已生成。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval")
    public ApiResponse<ExtractionEvalRunStartedResponse> startExtractionEval(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId,
            @Valid @RequestBody ExtractionEvalRequest request
    ) {
        return ApiResponseUtils.success(extractionEvalService.trigger(buildRunId, request));
    }

    /**
     * 04 步：查询评分进度。前端按 recommendedPollingIntervalMillis 轮询；
     * 任务终态时该字段为 null，前端停止轮询。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/status")
    public ApiResponse<ExtractionEvalStatusResponse> getExtractionEvalStatus(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(extractionEvalService.getStatus(buildRunId));
    }

    /**
     * 04 步：评分完成后获取排行榜与候选指标详情。
     */
    @GetMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/report")
    public ApiResponse<ExtractionEvalReportResponse> getExtractionEvalReport(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        return ApiResponseUtils.success(extractionEvalService.getReport(buildRunId));
    }

    /**
     * 04 步：中止评分。
     *
     * <p>软取消：把 active 任务状态切到 cancelling，worker 在下一个候选切换前自感知后落
     * 终态 cancelled。无 active 任务时幂等返回 200。</p>
     */
    @PostMapping(ApiPaths.KNOWLEDGE_BASE_BUILD_RUNS + "/{id}/extraction-eval/cancel")
    public ApiResponse<Void> cancelExtractionEval(
            @PathVariable("id") @Positive(message = "id必须大于0") Long buildRunId
    ) {
        extractionEvalService.cancel(buildRunId);
        return ApiResponseUtils.success(null);
    }
```

- [ ] **Step 3：构建确认编译通过**

Run:
```bash
cd backend/ckqa-back && ./mvnw -q -DskipTests compile 2>&1 | tail -10
```

Expected：BUILD SUCCESS。

- [ ] **Step 4：跑全部后端测试**

Run:
```bash
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run|FAIL|ERROR" | tail -8
```

Expected：所有 PASS（含 Phase 5 新增的 ExtractionEvalOrchestratorTest / ExtractionEvalReportAssemblerTest / ExtractionEvalWorkerTest / ExtractionEvalServiceTest / ExtractionEvalStartupRecoveryTest）。

- [ ] **Step 5：commit**

```bash
git add backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java
git commit -m "feat(prompt-builder): 04 步 controller 真实接入 + 新增 cancel 端点 (Phase 5)"
```

---

## Task 10：前端 API 客户端补 cancel + 调整 startExtractionEval 返回类型契约

`prompt-tune-pipeline.js` 已有 `startExtractionEval / getExtractionEvalStatus / getExtractionEvalReport`，本期：
- 新增 `cancelExtractionEval`
- 写测试覆盖 4 个端点的请求方法 / URL / 业务码处理

**Files:**
- Modify: `frontend/apps/admin-app/src/api/prompt-tune-pipeline.js`
- Test: `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-extraction-eval.test.js`

- [ ] **Step 1：写失败的测试**

Create `frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-extraction-eval.test.js`：

```javascript
import { describe, it, expect, vi } from 'vitest'
import {
  startExtractionEval,
  getExtractionEvalStatus,
  getExtractionEvalReport,
  cancelExtractionEval,
} from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  const calls = []
  const dispatcher = (method) => async (url, payload, config) => {
    calls.push({ method, url, payload, config })
    const next = responses.shift()
    if (!next) throw new Error('no response queued')
    if (next.code !== 200) {
      const err = new Error(next.message ?? 'business error')
      err.code = next.code
      err.response = { data: { code: next.code, message: next.message, data: null } }
      throw err
    }
    return { data: { code: 200, message: 'ok', data: next.data, timestamp: '2026-05-17T10:00:00' } }
  }
  return {
    get: dispatcher('GET'),
    post: dispatcher('POST'),
    put: dispatcher('PUT'),
    requests: calls,
  }
}

describe('extraction eval API client', () => {
  it('startExtractionEval POST /extraction-eval', async () => {
    const client = makeMockClient([
      { code: 200, data: { evalRunId: 7, buildRunId: 18, status: 'pending', reusedActiveRun: false, recommendedPollingIntervalMillis: 1500 } },
    ])
    const result = await startExtractionEval(18, { selectedCandidates: ['default', 'auto_tuned'] }, client)
    expect(client.requests[0].method).toBe('POST')
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/18/extraction-eval')
    expect(client.requests[0].payload).toEqual({ selectedCandidates: ['default', 'auto_tuned'] })
    expect(result.evalRunId).toBe(7)
  })

  it('startExtractionEval 4108 时抛出业务码', async () => {
    const client = makeMockClient([
      { code: 4108, message: '选定候选 ID 不在当前构建的候选清单中' },
    ])
    await expect(() => startExtractionEval(18, { selectedCandidates: ['phantom'] }, client))
      .rejects.toMatchObject({ code: 4108 })
  })

  it('getExtractionEvalStatus GET /extraction-eval/status', async () => {
    const client = makeMockClient([
      { code: 200, data: { evalRunId: 7, status: 'running', candidates: [] } },
    ])
    const result = await getExtractionEvalStatus(18, client)
    expect(client.requests[0].method).toBe('GET')
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/18/extraction-eval/status')
    expect(result.status).toBe('running')
  })

  it('getExtractionEvalStatus 4106 时抛出（前端按"未触发"渲染）', async () => {
    const client = makeMockClient([{ code: 4106, message: '本次构建尚未启动评分任务' }])
    await expect(() => getExtractionEvalStatus(18, client))
      .rejects.toMatchObject({ code: 4106 })
  })

  it('getExtractionEvalReport GET /extraction-eval/report', async () => {
    const client = makeMockClient([
      { code: 200, data: { evalRunId: 7, candidates: [{ candidateId: 'default' }] } },
    ])
    const result = await getExtractionEvalReport(18, client)
    expect(client.requests[0].method).toBe('GET')
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/18/extraction-eval/report')
    expect(result.candidates).toHaveLength(1)
  })

  it('cancelExtractionEval POST /extraction-eval/cancel', async () => {
    const client = makeMockClient([{ code: 200, data: null }])
    await cancelExtractionEval(18, client)
    expect(client.requests[0].method).toBe('POST')
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/18/extraction-eval/cancel')
  })

  it('对 buildRunId 做 URL encoding', async () => {
    const client = makeMockClient([{ code: 200, data: null }])
    await cancelExtractionEval('a/b 18', client)
    expect(client.requests[0].url).toBe('/knowledge-base-build-runs/a%2Fb%2018/extraction-eval/cancel')
  })
})
```

- [ ] **Step 2：跑测试确认失败**

Run:
```bash
cd frontend/apps/admin-app && pnpm test prompt-tune-pipeline-extraction-eval 2>&1 | tail -10
```

Expected：`cancelExtractionEval` 测试 FAIL（函数未导出）。

- [ ] **Step 3：在 prompt-tune-pipeline.js 加 cancelExtractionEval**

定位 `getExtractionEvalReport` 之后，在 `// ----- 05 步` 注释之前插入：

```javascript
export async function cancelExtractionEval(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/cancel`,
  ))
}
```

- [ ] **Step 4：跑测试确认通过**

Run:
```bash
cd frontend/apps/admin-app && pnpm test prompt-tune-pipeline-extraction-eval 2>&1 | tail -10
```

Expected：7 个测试全 PASS。

- [ ] **Step 5：commit**

```bash
git add frontend/apps/admin-app/src/api/prompt-tune-pipeline.js frontend/apps/admin-app/src/api/__tests__/prompt-tune-pipeline-extraction-eval.test.js
git commit -m "feat(prompt-builder): API 加 cancelExtractionEval 端点 + 04 步 4 个端点单测 (Phase 5)"
```

---

## Task 11：PromptBuilderPage 透传 selectedCandidates 进 04 步路由 query

03 步进入 04 步时把 `selectedCandidates` 数组写到 URL（spec § 路由设计）。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`（确认 emit 携带 candidate ids）

- [ ] **Step 1：定位现有 emit 行为**

Run:
```bash
grep -nE "start-scoring|emit\(" frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue
```

确认 03 步 emit `start-scoring` 时是否带候选 ID 数组。Phase 4 计划里 emit 是 `emit('start-scoring', selectedIds.value)`。

- [ ] **Step 2：在 PromptBuilderPage 接收并写入 query**

定位 `<PromptBuilderCandidatesStep` 段：

**oldStr**：
```html
        <PromptBuilderCandidatesStep
          v-else-if="activeStepKey === 'candidates'"
          :dirty="dirty"
          @start-scoring="gotoStep('scoring')"
          @back="gotoPrev"
        />
```

**newStr**：
```html
        <PromptBuilderCandidatesStep
          v-else-if="activeStepKey === 'candidates'"
          :dirty="dirty"
          @start-scoring="handleEnterScoring"
          @back="gotoPrev"
        />
```

在 `<script setup>` 内补一个 handler（紧接现有 `function gotoStep(...)`）：

**oldStr**：
```javascript
async function gotoStep(stepKey) {
```

**newStr**：
```javascript
async function handleEnterScoring(selectedCandidateIds) {
  const ids = Array.isArray(selectedCandidateIds) ? selectedCandidateIds : []
  // 写到 URL，让 04 步能从 query 读取；逗号分隔便于人读
  await router.replace({
    query: {
      ...route.query,
      step: 'scoring',
      selectedCandidates: ids.join(','),
    },
  })
}

async function gotoStep(stepKey) {
```

- [ ] **Step 3：跑前端构建**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：构建成功。

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 03→04 路由切换写入 selectedCandidates query 参数 (Phase 5)"
```

---

## Task 12：PromptBuilderScoringStep 真实化（去 mock + 五态 + 轮询）

把 `PromptBuilderScoringStep.vue` 替换成真实 API 驱动的实现：
- `mounted`：检查 query 参数 → 复用 active / 触发评分 / 引导回 03。
- 轮询 status 直到终态。
- 排行榜从 GET /report 加载（status=success 后切换）。
- 中止按钮调 cancel。
- 5 个核心阶段 UI：loading / blocked / running（候选矩阵）/ done（排行榜）/ failed/cancelled。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue`

- [ ] **Step 1：完整替换 PromptBuilderScoringStep.vue**

Replace 整个文件为：

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CandidateMatrixRow from './CandidateMatrixRow.vue'
import ScoringRankingTable from './ScoringRankingTable.vue'
import ScoringDetailDrawer from './ScoringDetailDrawer.vue'
import { formatTokens, formatDuration } from './scoring-format-model.js'
import {
  startExtractionEval,
  getExtractionEvalStatus,
  getExtractionEvalReport,
  cancelExtractionEval,
} from '../../../api/prompt-tune-pipeline.js'

const props = defineProps({
  dirty: { type: Boolean, default: false },
})

const emit = defineEmits(['enter-save', 'back', 'select-candidate'])

const route = useRoute()

// 业务码常量
const CODE_AUDIT_NOT_COMPLETED = 4104
const CODE_CANDIDATES_NOT_GENERATED = 4105
const CODE_EVAL_NOT_STARTED = 4106
const CODE_INVALID_CANDIDATE_SELECTION = 4108

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

const selectedCandidatesFromQuery = computed(() => {
  const raw = route.query.selectedCandidates
  if (!raw) return []
  return String(raw).split(',').map((s) => s.trim()).filter(Boolean)
})

// 五态：loading / blocked / running / done / failed
const phase = ref('loading')
const errorMessage = ref('')
const blockedReason = ref('')

// running 态数据
const evalRunId = ref(null)
const status = ref(null)  // 整个 ExtractionEvalStatusResponse
let pollTimer = null

// done 态数据
const report = ref(null)  // ExtractionEvalReportResponse
const detailOpen = ref(false)
const detailCandidate = ref(null)
const highlightedId = ref('')
const selectedId = ref('')

const overall = computed(() => status.value?.overall ?? null)
const matrixCandidates = computed(() => status.value?.candidates ?? [])
const reportCandidates = computed(() => report.value?.candidates ?? [])
/** 风险 1：未进入排行榜的失败候选清单，由后端 report.failedCandidates 透传。 */
const failedCandidates = computed(() => report.value?.failedCandidates ?? [])

onMounted(initialize)

onBeforeUnmount(() => {
  stopPolling()
})

async function initialize() {
  if (!buildRunId.value) {
    phase.value = 'failed'
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }

  phase.value = 'loading'
  // 1. 先看是否已有 active / 历史评分
  try {
    const s = await getExtractionEvalStatus(buildRunId.value)
    handleStatusUpdate(s)
    return  // 有现成任务（pending/running/success/failed），不再触发新任务
  } catch (err) {
    if (err?.code !== CODE_EVAL_NOT_STARTED) {
      phase.value = 'failed'
      errorMessage.value = err?.message ?? '加载评分进度失败'
      return
    }
    // 4106 → 没有任务，下一步检查 selectedCandidates 看是否要立即触发
  }

  // 2. 没有现成任务：必须有 selectedCandidates 才触发，否则提示用户回 03 步
  if (selectedCandidatesFromQuery.value.length === 0) {
    phase.value = 'blocked'
    blockedReason.value = '请先在 03 步勾选要评分的候选 Prompt'
    return
  }

  await triggerNewEval(selectedCandidatesFromQuery.value)
}

async function triggerNewEval(selectedIds) {
  phase.value = 'loading'
  try {
    const started = await startExtractionEval(buildRunId.value, {
      selectedCandidates: selectedIds,
    })
    evalRunId.value = started.evalRunId
    // 立即拉一次 status 然后开始轮询
    const s = await getExtractionEvalStatus(buildRunId.value)
    handleStatusUpdate(s)
  } catch (err) {
    if (err?.code === CODE_AUDIT_NOT_COMPLETED) {
      phase.value = 'blocked'
      blockedReason.value = '请先完成 02 步至少 1 条样本审阅'
    } else if (err?.code === CODE_CANDIDATES_NOT_GENERATED) {
      phase.value = 'blocked'
      blockedReason.value = '请先在 03 步生成候选 Prompt'
    } else if (err?.code === CODE_INVALID_CANDIDATE_SELECTION) {
      phase.value = 'blocked'
      blockedReason.value = '所选候选 ID 与当前构建不匹配，请回 03 步重选'
    } else {
      phase.value = 'failed'
      errorMessage.value = err?.message ?? '触发评分任务失败'
    }
  }
}

function handleStatusUpdate(s) {
  status.value = s
  evalRunId.value = s.evalRunId

  switch (s.status) {
    case 'success':
      phase.value = 'done'
      stopPolling()
      loadReport()
      break
    case 'failed':
      phase.value = 'failed'
      errorMessage.value = s.errorMessage ?? '评分任务执行失败'
      stopPolling()
      break
    case 'cancelled':
      phase.value = 'failed'
      errorMessage.value = '评分任务已取消'
      stopPolling()
      break
    case 'pending':
    case 'running':
    case 'cancelling':
      phase.value = 'running'
      ensurePolling(s.recommendedPollingIntervalMillis ?? 1500)
      break
    default:
      phase.value = 'running'
      ensurePolling(1500)
  }
}

function ensurePolling(intervalMs) {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    try {
      const s = await getExtractionEvalStatus(buildRunId.value)
      handleStatusUpdate(s)
    } catch (err) {
      // 轮询失败时降级：不弹 toast 避免刷屏，下一次自动重试
      // eslint-disable-next-line no-console
      console.warn('[scoring] 轮询失败，将在下一周期重试', err)
    }
  }, intervalMs)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadReport() {
  try {
    report.value = await getExtractionEvalReport(buildRunId.value)
    // 默认选中排名第一
    const top = reportCandidates.value.find((c) => c.rank === 1)
    if (top && !selectedId.value) selectedId.value = top.candidateId
  } catch (err) {
    phase.value = 'failed'
    errorMessage.value = err?.message ?? '加载评分报告失败'
  }
}

async function handleAbort() {
  try {
    await ElMessageBox.confirm('中止评分会丢失当前进度，确定吗？', '中止评分', { type: 'warning' })
  } catch {
    return  // 用户取消
  }
  try {
    await cancelExtractionEval(buildRunId.value)
    ElMessage.info('已请求中止，评分将在当前候选完成后停止')
    // 不立即停止轮询，让 worker 自感知后写 cancelled 终态
  } catch (err) {
    ElMessage.error(err?.message ?? '中止失败')
  }
}

function handleViewDetail(candidateId) {
  highlightedId.value = candidateId
  detailCandidate.value = reportCandidates.value.find((c) => c.candidateId === candidateId) ?? null
  detailOpen.value = true
}

function handleSelectCandidate(candidateId) {
  selectedId.value = candidateId
  emit('select-candidate', candidateId)
  const candidate = reportCandidates.value.find((c) => c.candidateId === candidateId)
  ElMessage.success(`已选定：${candidate?.displayNameZh ?? candidateId}`)
}

function handleEnterSave() {
  if (!selectedId.value) {
    ElMessage.warning('请先在排行榜操作列点击"选定"')
    return
  }
  emit('enter-save', selectedId.value)
}

async function handleRetry() {
  // 失败态点重试：检查 selectedCandidates 重新触发
  if (selectedCandidatesFromQuery.value.length === 0) {
    emit('back')
    return
  }
  await triggerNewEval(selectedCandidatesFromQuery.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-scoring">
    <header class="prompt-builder-step__header">
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>抽取评分</h3>
        <p>在校准集上跑候选提示词，按综合分排序选出最佳候选。</p>
      </div>
      <div class="prompt-builder-step__header-right">
        <el-tag v-if="dirty" type="warning" size="small" effect="light">已修改未保存</el-tag>
        <el-tag v-else type="success" size="small" effect="light">已是最新</el-tag>
      </div>
    </header>

    <!-- loading -->
    <div v-if="phase === 'loading'" class="scoring-state-card">
      <span>正在加载评分任务...</span>
    </div>

    <!-- blocked（02/03 门控失败 / 缺少 selectedCandidates） -->
    <div v-else-if="phase === 'blocked'" class="scoring-state-card scoring-state-card--blocked">
      <p>{{ blockedReason }}</p>
      <el-button @click="$emit('back')">返回 03 步</el-button>
    </div>

    <!-- failed（含 cancelled） -->
    <div v-else-if="phase === 'failed'" class="scoring-state-card scoring-state-card--error">
      <p>{{ errorMessage }}</p>
      <div class="scoring-state-card__actions">
        <el-button @click="handleRetry">重试</el-button>
        <el-button @click="$emit('back')">返回 03 步</el-button>
      </div>
    </div>

    <!-- running -->
    <template v-else-if="phase === 'running'">
      <div class="scoring-progress-summary">
        <div>
          <div class="scoring-progress-summary__metric">
            <strong>{{ overall?.finishedCalls ?? 0 }}</strong> / {{ overall?.totalCalls ?? 0 }}
          </div>
          <div class="ann-text-tiny">
            大模型调用 · 已用时 {{ formatDuration(overall?.elapsedSeconds ?? 0) }}
            <template v-if="overall?.estimatedRemainingSeconds != null">
              · 预估剩余 {{ Math.ceil((overall.estimatedRemainingSeconds ?? 0) / 60) }} min
            </template>
          </div>
        </div>
        <div class="scoring-progress-summary__divider"></div>
        <div>
          <div class="scoring-progress-summary__metric">
            ~ <strong>{{ formatTokens(overall?.tokensUsed ?? 0) }}</strong>
          </div>
          <div class="ann-text-tiny">已消耗 token · 预估总量 {{ formatTokens(overall?.estimatedTotalTokens ?? 0) }}</div>
        </div>
        <div class="scoring-progress-summary__abort">
          <el-button @click="handleAbort">中止评分</el-button>
        </div>
      </div>

      <div class="scoring-matrix">
        <CandidateMatrixRow
          v-for="(c, i) in matrixCandidates"
          :key="c.candidateId"
          :candidate="{ candidateId: c.candidateId, displayNameZh: c.displayNameZh }"
          :progress="{
            candidateId: c.candidateId,
            status: c.status,
            extractDone: c.extract?.finished ?? 0,
            scoringStartedAtMs: null,
          }"
          :index="i"
        />
      </div>
    </template>

    <!-- done -->
    <template v-else-if="phase === 'done'">
      <ScoringRankingTable
        :candidates="reportCandidates"
        :selected-candidate-id="selectedId"
        :highlighted-candidate-id="highlightedId"
        @select-candidate="handleSelectCandidate"
        @view-detail="handleViewDetail"
      />

      <!-- 风险 1：失败候选不进入排行榜，单独成块；report.failedCandidates 非空时才渲染 -->
      <section v-if="failedCandidates.length > 0" class="scoring-failed-candidates">
        <header class="scoring-failed-candidates__header">未进入排名（{{ failedCandidates.length }}）</header>
        <ul class="scoring-failed-candidates__list">
          <li v-for="f in failedCandidates" :key="f.candidateId" class="scoring-failed-candidates__item">
            <strong>{{ f.displayNameZh || f.candidateId }}</strong>
            <span class="ann-text-tiny">{{ f.stage === 'extract' ? '抽取阶段' : '评分阶段' }}</span>
            <span class="ann-text-tiny ann-text-tiny--danger">{{ f.reason }}</span>
          </li>
        </ul>
      </section>

      <ScoringDetailDrawer
        v-model="detailOpen"
        :candidate="detailCandidate"
        :is-selected="detailCandidate?.candidateId === selectedId"
      />

      <footer class="scoring-bottom-bar">
        <div class="scoring-bottom-bar__info">
          <template v-if="selectedId">
            已选定：<strong>{{ reportCandidates.find((c) => c.candidateId === selectedId)?.displayNameZh }}</strong>
          </template>
          <template v-else>尚未选定候选</template>
        </div>
        <div class="scoring-bottom-bar__actions">
          <el-button type="primary" :disabled="!selectedId" @click="handleEnterSave">进入预览 →</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.scoring-state-card {
  padding: 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  color: var(--ckqa-text-muted, #78716c);
}
.scoring-state-card--blocked { color: var(--ckqa-warning, #d97706); }
.scoring-state-card--error { color: var(--ckqa-danger, #dc2626); }
.scoring-state-card__actions { display: flex; gap: 8px; }
</style>
```

- [ ] **Step 2：构建确认无语法错**

Run:
```bash
cd frontend/apps/admin-app && pnpm build 2>&1 | tail -5
```

Expected：构建成功。

- [ ] **Step 3：跑前端单测确认未破坏**

Run:
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```

Expected：所有单测 PASS。

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue
git commit -m "feat(prompt-builder): 04 步替换 mock 接入真实评分 API + 五态 + 轮询 + 中止按钮 (Phase 5)"
```

---

## Task 13：真后端集成冒烟（手工 + 5 步精简）

> 本步专做"Mock e2e 做不到的"集成验证：DB 任务持久化 / Python 脚本真跑 / 候选切换串行 / 产物复制 / 取消语义。

**前置：** infra docker compose 已启动；后端已重启加载 Phase 5 代码；某 build run 已完成 02 步 ≥1 完成 + 03 步候选生成。

- [ ] **Step 1：触发评分（POST + 异步）**

在 admin-app 进入某个已完成 02 步 + 03 步的 build run，进入 04 步页面。Phase 5 前端会自动 POST `/extraction-eval`。

DevTools Network 面板：
- 请求体：`{"selectedCandidates": ["default", "auto_tuned", ...]}`
- 响应体：`{ "code": 200, "data": { "evalRunId": N, "status": "pending", "reusedActiveRun": false } }`

DB 检查：
```bash
mysql -h ... -e "SELECT id, status, progress_stage, extracting_candidate_id, finished_candidates FROM prompt_tune_extraction_eval_runs WHERE build_run_id = <BID> ORDER BY id DESC LIMIT 1"
```
Expected：status=`pending` 或已切到 `running`。

- [ ] **Step 2：观察候选串行切换**

页面候选矩阵随 1.5s 轮询刷新；DB 同步：
```bash
watch -n 2 "mysql -h ... -e 'SELECT status, progress_stage, extracting_candidate_id, finished_candidates FROM prompt_tune_extraction_eval_runs ORDER BY id DESC LIMIT 1'"
```

Expected：
- 第一阶段：status=running, progress_stage=extracting, extracting_candidate_id 依次切换 default → auto_tuned → ...
- 第二阶段：progress_stage=scoring, finished_candidates 含全部候选
- 终态：status=success, progress_stage=done

- [ ] **Step 3：评分产物落到 build run workspace**

Run:
```bash
WS=/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/user_0/kb_<KB>/build_<RID>
ls $WS/eval/<EVALID>/
```
Expected：`extraction_eval/` 和 `scoring_report/` 两个子目录。
```bash
ls $WS/eval/<EVALID>/scoring_report/top_candidates.json $WS/eval/<EVALID>/extraction_eval/
```
Expected：`top_candidates.json` 存在；`extraction_eval/` 含每候选一个 JSON。

共享路径已被清理：
```bash
ls /home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/eval_<RID>_<EVALID> 2>&1 | head -3
ls /home/sunlight/Projects/ckqa/graphrag_pipeline/results/reports/extraction_scoring/runs/eval_<RID>_<EVALID> 2>&1 | head -3
```
Expected：两个路径都不存在（已清理）。

DB 列 `eval_dir` 验证为相对路径形式（决策 7 / 自检 A12）：
```bash
mysql -h ... -e "SELECT eval_dir FROM prompt_tune_extraction_eval_runs ORDER BY id DESC LIMIT 1"
```
Expected：值形如 `user_0/kb_<KB>/build_<RID>/eval/<EVALID>`，**不含**机器绝对路径前缀（不应出现 `/home/...`）。

- [ ] **Step 4：报告 API 返回正确指标**

DevTools 直接发请求：
```javascript
fetch('/api/v1/knowledge-base-build-runs/<BID>/extraction-eval/report', {
  headers: { Authorization: 'Bearer ' + localStorage.token }
}).then(r => r.json()).then(console.log)
```

Expected：响应含 `evalRunId / generatedAt / candidates[] / failedCandidates[]`，每个候选含 `compositeScore / parseSuccessRate / recall / precision / f1 / gates[]` 字段；gate.passed 按 spec 阈值（0.8 / 0.5 / 0.5 / 0.95）正确计算。**Phase 4.5 衔接**：响应顶层 `seed` 字段与触发评分时 build run metadata 的 seed 一致。**风险 1 衔接**：若本次评分有候选抽取失败，`failedCandidates[]` 应含对应条目（candidateId / displayNameZh / stage="extract" / reason）；正常路径下应是空数组。

- [ ] **Step 5：cancel 软取消验证（候选边界）**

启一个新评分（≥3 候选），等 1 个候选已经在跑时点"中止评分"按钮：

DevTools Network：
- POST `/extraction-eval/cancel` → 200 OK

立即查 DB：
```bash
mysql -h ... -e "SELECT status FROM prompt_tune_extraction_eval_runs ORDER BY id DESC LIMIT 1"
```
Expected：状态先变 `cancelling`；**当前候选**会跑完（不强杀子进程）；当前候选结束后 worker 进入下一候选循环时检测到 `cancelling`，跳过剩余候选，最终落 `cancelled`。前端轮询应该在 `cancelling` 阶段维持"正在中止当前候选"提示，最终切到 cancelled phase。
冒烟时若 cancel 后等了 5-10 分钟仍未变 cancelled，多半是当前候选没跑完，属于预期行为而非 bug。

- [ ] **Step 6：不需要 commit**

集成冒烟无代码改动。

---

## Task 14：Playwright e2e 覆盖前端五态 + 轮询 + 中止 + 排行榜交互

仿 Phase 4 的 `prompt-builder-candidates.spec.js`，新建 helper 模拟评分轮询。

**Files:**
- Create: `frontend/apps/admin-app/e2e/helpers/prompt-builder-scoring.js`
- Create: `frontend/apps/admin-app/e2e/prompt-builder-scoring.spec.js`

- [ ] **Step 1：创建 helper**

Create `frontend/apps/admin-app/e2e/helpers/prompt-builder-scoring.js`：

```javascript
/**
 * E2E prompt-builder Phase 5 helpers。
 *
 * 模拟评分任务的轮询：通过 statusSequence 数组定义每次 GET /status 返回什么，
 * 让 spec 能控制"先 running 5 次，再 success 1 次"这种序列。
 */

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1,
  userCode: 'ADM2026001',
  username: 'admin.heqh',
  displayName: '平台管理员',
  roleCode: 'admin',
}

function defaultStatusRunning() {
  return {
    evalRunId: 7,
    status: 'running',
    progressStage: 'extracting',
    recommendedPollingIntervalMillis: 200,  // e2e 加速
    startedAt: '2026-05-17T10:00:00',
    overall: {
      finishedCalls: 20,
      totalCalls: 80,
      elapsedSeconds: 60,
      estimatedRemainingSeconds: 180,
      tokensUsed: 100000,
      estimatedTotalTokens: 400000,
    },
    candidates: [
      { candidateId: 'default',                       displayNameZh: '默认基线',          status: 'done',       extract: { finished: 20, total: 20 }, score: { finished: 1, total: 1 } },
      { candidateId: 'auto_tuned',                    displayNameZh: 'GraphRAG 自动调优', status: 'extracting', extract: { finished: 0,  total: 20 }, score: { finished: 0, total: 1 } },
      { candidateId: 'schema_aware_directional_v2',   displayNameZh: '图谱感知',          status: 'queued',     extract: { finished: 0,  total: 20 }, score: { finished: 0, total: 1 } },
    ],
  }
}

function defaultStatusSuccess() {
  return { ...defaultStatusRunning(), status: 'success', progressStage: 'done', recommendedPollingIntervalMillis: null }
}

function defaultReport() {
  return {
    evalRunId: 7,
    generatedAt: '2026-05-17T10:30:00',
    candidates: [
      {
        candidateId: 'schema_aware_directional_v2',
        displayNameZh: '图谱感知',
        rank: 1,
        compositeScore: 0.71,
        parseSuccessRate: 0.95,
        recall: 0.74,
        precision: 0.68,
        f1: 0.71,
        entityCountAvg: 18.3,
        relationCountAvg: 12.1,
        tokensUsed: 168000,
        elapsedSeconds: 312,
        gates: [
          { name: 'parse_success', threshold: 0.8, value: 0.95, passed: true },
          { name: 'audit_recall', threshold: 0.5, value: 0.74, passed: true },
          { name: 'audit_precision', threshold: 0.5, value: 0.68, passed: true },
          { name: 'relation_direction', threshold: null, value: 0.96, passed: true, endpointTotalCount: 50, endpointInvalidCount: 2 },
        ],
        failedSamples: [],
      },
      {
        candidateId: 'default',
        displayNameZh: '默认基线',
        rank: 2,
        compositeScore: 0.42,
        parseSuccessRate: 0.80,
        recall: 0.45,
        precision: 0.42,
        f1: 0.43,
        entityCountAvg: 11.8,
        relationCountAvg: 5.5,
        tokensUsed: 60000,
        elapsedSeconds: 175,
        gates: [
          { name: 'parse_success', threshold: 0.8, value: 0.80, passed: true },
          { name: 'audit_recall', threshold: 0.5, value: 0.45, passed: false },
          { name: 'audit_precision', threshold: 0.5, value: 0.42, passed: false },
          { name: 'relation_direction', threshold: null, value: 0.90, passed: false, endpointTotalCount: 40, endpointInvalidCount: 4 },
        ],
        failedSamples: [],
      },
    ],
    // 风险 1：未进入排行榜的失败候选清单（结构化）。e2e 默认无失败候选，spec 可覆盖此字段。
    failedCandidates: [],
  }
}

export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * 安装 04 步 mock。
 *
 * @param {object} options
 * @param {number} options.buildRunId
 * @param {string} options.initialPhase  'no-task' | 'running' | 'done' | 'failed'
 * @param {Array<object>} options.statusSequence  自定义 status 响应序列（覆盖 initialPhase）
 * @param {object} options.report  自定义 report 响应
 */
export async function installScoringMocks(page, {
  buildRunId = 18,
  initialPhase = 'running',
  statusSequence,
  report = defaultReport(),
} = {}) {
  // 计算 status 序列
  let sequence
  if (statusSequence) {
    sequence = [...statusSequence]
  } else if (initialPhase === 'no-task') {
    sequence = [{ httpStatus: 404, code: 4106, message: '本次构建尚未启动评分任务' }]
  } else if (initialPhase === 'running') {
    sequence = [
      defaultStatusRunning(),
      defaultStatusRunning(),
      defaultStatusSuccess(),
    ]
  } else if (initialPhase === 'done') {
    sequence = [defaultStatusSuccess()]
  } else if (initialPhase === 'failed') {
    sequence = [{
      ...defaultStatusRunning(),
      status: 'failed',
      progressStage: 'done',
      errorMessage: '评分抽取失败：模型 API 调用超时',
      recommendedPollingIntervalMillis: null,
    }]
  } else {
    sequence = [defaultStatusRunning()]
  }

  let cancelled = false
  let cancelledSeenOnce = false

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    const path = url.pathname.replace(API_PREFIX, '')
    const method = request.method()

    if (path === '/auth/me' && method === 'GET') {
      return reply(route, 200, { code: 200, data: ADMIN_USER })
    }
    if (path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval` && method === 'POST') {
      const body = JSON.parse(request.postData() ?? '{}')
      return reply(route, 200, {
        code: 200,
        data: {
          evalRunId: 7,
          buildRunId,
          selectedCandidateIds: body.selectedCandidates ?? [],
          status: 'pending',
          reusedActiveRun: false,
          recommendedPollingIntervalMillis: 200,
        },
      })
    }
    if (path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/status` && method === 'GET') {
      const next = sequence.length > 1 ? sequence.shift() : sequence[0]
      if (cancelled) {
        // 先返 cancelling 一次（模拟"当前候选未结束"），后续返 cancelled
        if (!cancelledSeenOnce) {
          cancelledSeenOnce = true
          return reply(route, 200, {
            code: 200,
            data: { ...defaultStatusRunning(), status: 'cancelling' },
          })
        }
        return reply(route, 200, {
          code: 200,
          data: { ...defaultStatusSuccess(), status: 'cancelled', errorMessage: '评分任务已取消' },
        })
      }
      if (next.httpStatus && next.httpStatus !== 200) {
        return reply(route, next.httpStatus, { code: next.code, message: next.message, data: null })
      }
      return reply(route, 200, { code: 200, data: next })
    }
    if (path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/report` && method === 'GET') {
      return reply(route, 200, { code: 200, data: report })
    }
    if (path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/cancel` && method === 'POST') {
      cancelled = true
      return reply(route, 200, { code: 200, data: null })
    }

    return reply(route, 500, { code: 5000, message: `未配置 mock: ${method} ${path}`, data: null })
  })
}

export async function gotoScoringStep(page, { buildRunId = 18, selectedCandidates = ['default', 'auto_tuned', 'schema_aware_directional_v2'] } = {}) {
  const sc = selectedCandidates.join(',')
  await page.goto(`/prompt-builder?buildRunId=${buildRunId}&step=scoring&selectedCandidates=${encodeURIComponent(sc)}`)
}

function reply(route, httpStatus, body) {
  return route.fulfill({
    status: httpStatus,
    headers: jsonHeaders(),
    body: JSON.stringify({ ...body, message: body.message ?? 'ok', timestamp: new Date().toISOString() }),
  })
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'access-control-allow-headers': '*',
  }
}

function jsonHeaders() {
  return { ...corsHeaders(), 'content-type': 'application/json' }
}
```

- [ ] **Step 2：创建 spec**

Create `frontend/apps/admin-app/e2e/prompt-builder-scoring.spec.js`：

```javascript
import { test, expect } from '@playwright/test'
import {
  loginAsAdmin,
  installScoringMocks,
  gotoScoringStep,
} from './helpers/prompt-builder-scoring.js'

test.describe('04 步候选评分', () => {

  test('blocked：缺少 selectedCandidates 时引导回 03 步', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'no-task' })
    // 注意：URL 不带 selectedCandidates
    await page.goto('/prompt-builder?buildRunId=18&step=scoring')

    await expect(page.getByText(/请先在 03 步勾选要评分的候选/)).toBeVisible()
    await expect(page.getByRole('button', { name: '返回 03 步' })).toBeVisible()
  })

  test('running → done：候选矩阵刷新后切到排行榜', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'running' })
    await gotoScoringStep(page)

    // 先看到候选矩阵
    await expect(page.getByText('候选基线').or(page.getByText('GraphRAG 自动调优'))).toBeVisible()
    // 等到 success 切排行榜（轮询 200ms × 3 = ~600ms）
    await expect(page.getByText(/候选排行榜/)).toBeVisible({ timeout: 5000 })
    // rank 1 候选默认选中
    await expect(page.getByText('图谱感知', { exact: true })).toBeVisible()
  })

  test('done：rank 1 候选默认选中且进入预览按钮可用', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    await expect(page.getByText(/候选排行榜/)).toBeVisible()
    const enterBtn = page.getByRole('button', { name: /进入预览/ })
    await expect(enterBtn).toBeEnabled()
  })

  test('done：点查看详情打开抽屉显示门控', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    // 点击 rank 1 行（不是操作列）
    await page.locator('.scoring-ranking-table tbody tr').first().click()
    // 抽屉显示综合分 + 4 条门控
    await expect(page.getByText(/综合分/)).toBeVisible()
    await expect(page.getByText(/解析成功率/)).toBeVisible()
    await expect(page.getByText(/召回率/)).toBeVisible()
  })

  test('done：选定不同候选切换 selectedCandidates', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    // 默认选中 rank 1（图谱感知）；点 rank 2 操作列"选定"
    const row2 = page.locator('.scoring-ranking-table tbody tr').nth(1)
    await row2.getByRole('button', { name: '选定' }).click()
    await expect(page.getByText(/已选定：默认基线/)).toBeVisible()
  })

  test('failed：评分失败时显示错误 + 重试 + 返回按钮', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'failed' })
    await gotoScoringStep(page)

    await expect(page.getByText(/模型 API 调用超时/)).toBeVisible()
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
    await expect(page.getByRole('button', { name: '返回 03 步' })).toBeVisible()
  })

  test('cancel：点击中止后切到 cancelled', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'running' })
    await gotoScoringStep(page)

    // 中止按钮
    page.on('dialog', (dialog) => dialog.accept())  // ElMessageBox 走 dialog 风格
    const abortBtn = page.getByRole('button', { name: '中止评分' })
    await expect(abortBtn).toBeVisible()
    await abortBtn.click()

    // 等到状态切到 cancelled（mock 的 cancelled flag 触发）
    await expect(page.getByText(/评分任务已取消/)).toBeVisible({ timeout: 5000 })
  })
})
```

- [ ] **Step 3：跑 e2e**

Run:
```bash
cd frontend/apps/admin-app && pnpm test:e2e prompt-builder-scoring 2>&1 | tail -20
```

Expected：7 个 e2e 测试全 PASS。

> **注意**：cancel 测试用 `ElMessageBox.confirm` 走的是 element-plus 弹窗（不是 native dialog），可能需要把 `page.on('dialog', ...)` 替换为定位 `.el-message-box .el-button--primary` 直接点击。如 cancel 测试失败先去 element-plus 弹窗里点确认按钮：

```javascript
// 替代 page.on('dialog', ...) 那行：
await abortBtn.click()
await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
```

- [ ] **Step 4：commit**

```bash
git add frontend/apps/admin-app/e2e/helpers/prompt-builder-scoring.js frontend/apps/admin-app/e2e/prompt-builder-scoring.spec.js
git commit -m "test(prompt-builder): 04 步 Playwright e2e 覆盖五态 + 轮询 + 中止 + 排行榜交互 (Phase 5)"
```

---

## Task 15：联合验证 + spec 标记完成

> 收尾：聚合 Task 13 真后端冒烟 + Task 14 mock e2e 的产出，确认 Phase 5 真正完结。

- [ ] **Step 1：跑全部测试套件**

后端：
```bash
cd backend/ckqa-back && ./mvnw test 2>&1 | grep -E "Tests run|FAIL|ERROR" | tail -5
```
Expected：所有 PASS。Phase 5 新增的 5 个测试类（ExtractionEvalOrchestratorTest / ExtractionEvalReportAssemblerTest / ExtractionEvalWorkerTest / ExtractionEvalServiceTest / ExtractionEvalStartupRecoveryTest）全部 PASS。

前端单测：
```bash
cd frontend/apps/admin-app && pnpm test 2>&1 | tail -5
```
Expected：所有 PASS。Phase 5 新增的 7 个 API 单测全部 PASS。

前端 e2e：
```bash
cd frontend/apps/admin-app && pnpm test:e2e 2>&1 | tail -8
```
Expected：原有 e2e 仍 PASS + Phase 5 新增 7 个 PASS。

- [ ] **Step 2：自检清单走一遍**

打开 plan 末尾的"自检清单"，逐项打勾。任一项未达成停止合并。

- [ ] **Step 3：spec 标记 ✅**

Modify `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md`：

定位 `**Phase 5（⏸）**` 段，把 ⏸ 改成 ✅。补一段"Phase 5 已落地"小结：

```markdown
- **Phase 5（✅）：04 步候选矩阵 + 排行榜 + 详情抽屉**
  - 实现 `POST /extraction-eval` / `/status` / `/report` / `/cancel` 四个端点（异步任务化，仿 PromptTune 模式）。
  - 新增 `prompt_tune_extraction_eval_runs` 状态表 + `ExtractionEvalAsyncConfig`（独立线程池 corePoolSize=1，串行评分避免限流）。
  - 评分产物按 build run 隔离到 `<workspace>/eval/<evalRunId>/`，worker 跑完复制 + 清理共享磁盘路径，多 build run 互不污染。
  - 三重门控：02 ≥1 completed（4104）+ 03 候选已生成（4105）+ selectedCandidates ⊆ 已生成（4108）；前端在 onMounted 检查 selectedCandidates query 缺失 → 引导回 03 步。
  - 报告 gate.passed 在后端按 spec 阈值（0.8 / 0.5 / 0.5 / 0.95）重新计算，与脚本严格 GATE_THRESHOLD=0.95 解耦；F1 由 recall + precision 重算。
  - 服务启动恢复：`ExtractionEvalStartupRecovery` 把心跳 > 2 分钟的 running 任务标记 failed。
  - 前端 5 态（loading / blocked / running / done / failed），轮询 1500ms，终态停止；中止按钮调 cancel 端点（软取消），worker 在下一个候选切换时落 cancelled 终态。
  - **承接 Phase 4.5 的 seed 透传**：表 schema 直接含 `seed` 列；`ExtractionEvalService` 启动评分时把 build run metadata.customPromptDraft.seed 写入快照；`ExtractionEvalReportResponse` 顶层透传 seed，前端可展示"本次评分基于哪个种子的候选"。
  - 测试：后端 N 测试 PASS，前端单测 7 个新增 API PASS，Playwright e2e 7/7 PASS（覆盖五态 + 排行榜交互 + 中止）。
```

同时把"已识别风险" §4（评分时长不可预测）补上"Phase 5 已落地缓解（异步任务 + DB 持久化进度 + 启动恢复）"备注。

- [ ] **Step 4：commit**

```bash
git add docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md
git commit -m "docs(spec): Phase 5 标记完成 + 风险 #4 缓解备注 (Phase 5)"
```

- [ ] **Step 5：push**

```bash
git push origin feature/prompt-confirmation-step
```

---

## 自检清单

> 实施前主会话过一遍这个清单，对应不上的项停止执行重新调整计划。

### 一致性自检

- [ ] **A1**：所有 Task 中提到的状态枚举（`pending` / `running` / `cancelling` / `cancelled` / `success` / `failed`）拼写一致，无遗漏字符。
- [ ] **A2**：progressStage 枚举（`queued` / `extracting` / `scoring` / `done`）拼写一致。
- [ ] **A3**：错误码 `EXTRACTION_EVAL_NOT_STARTED(4106)` / `INVALID_EVAL_CANDIDATE_SELECTION(4108)` / `EXTRACTION_EVAL_FAILED(5008)` 在所有 Task 中数字一致，未与现有码冲突（4104/4105/5006/5007 已占用；4107 已删除，重复触发由 service 直接返回 200 + 当前任务承载）。
- [ ] **A4**：build run workspace 下评分目录路径在所有 Task 中一致：`<workspace>/eval/<evalRunId>/extraction_eval/` 和 `<workspace>/eval/<evalRunId>/scoring_report/`。
- [ ] **A5**：脚本 `--run-id` 取值在 Task 4 / 6 / 13 中一致：`eval_<buildRunId>_<evalRunId>`。
- [ ] **A6**：脚本调用方式（`-m scripts.extraction_eval.run_native_extraction` / `-m scripts.extraction_eval.score_extraction_results`）在 Task 0 / 4 一致。
- [ ] **A7**：cancel 语义统一为"候选边界软取消"：决策 6 / Task 6 worker 实现 / Task 12 前端中止按钮文案 / Task 13 真后端冒烟说明 都不出现 `Process.destroyForcibly` 或"立即中断当前候选子进程"等强杀承诺；前端在 cancel 后**继续轮询**直到 cancelled 终态（不引入"2 秒后跳回"的硬编码 setTimeout）。
- [ ] **A8**：整体终态规则统一：finished 全空 → 整体 failed；finished 非空且 scoring 完成 → 整体 success（含部分候选 failed 的情况），决策 1 / 风险 1 / Task 6 worker 三处描述一致；失败候选通过 `candidate_failures` JSON 列与 `ExtractionEvalReportResponse.failedCandidates` 结构化呈现，**不**仅靠 latestLogs。
- [ ] **A9**：路由 query / API 请求体 / 后端 DTO 三处统一为复数 `selectedCandidates`，无遗留单数 `selectedCandidate` 表述。
- [ ] **A10**：corePoolSize 唯一值为 1（决策 1 / 风险 8 / Task 4 AsyncConfig 三处一致），不再出现 corePoolSize=2 的 Phase 5 自身配置。
- [ ] **A11**：启动恢复覆盖三态（pending / running / cancelling），决策 7 / Task 1 service `listAllActive` / Task 8 Recovery 实现 / Recovery 测试 四处一致。
- [ ] **A12**：`eval_dir` 列只存相对 `GRAPHRAG_BUILD_RUNS_ROOT` 的 URI（形如 `user_X/kb_Y/build_Z/eval/<evalRunId>`），SQL 列注释 / Entity Javadoc / worker `toRelativeWorkspaceUri` 三处一致，不灌机器绝对路径。

### 上下文自检

- [ ] **B1**：`PromptTuneAsyncConfig` 已存在，本计划的 `ExtractionEvalAsyncConfig` 仿其结构（ThreadPoolTaskExecutor + corePoolSize / queueCapacity / threadNamePrefix）。
- [ ] **B2**：`PromptTuneAuditSamplesService.listByBuildRunId(...)` 方法已存在（Phase 2b 落地），返回 `List<PromptTuneAuditSamples>`。
- [ ] **B3**：`KnowledgeBaseBuildRunsService.getRequiredById(...)` 已存在，返回 `KnowledgeBaseBuildRuns` 或抛 NotFound。
- [ ] **B4**：`BuildRunWorkspaceService.resolve(workspaceUri)` 已存在（Phase 4 已扩 layout，加 `prompt/candidates`），Task 2 再加 `eval/`。
- [ ] **B5**：`CandidateManifestReader.read(...)` 已存在（Phase 4 落地），返回 `List<CandidateResponse>`。
- [ ] **B6**：`CandidateMetadataLookup.isKnown(...)` / `displayNameZh(...)` 已存在（Phase 4 落地）。
- [ ] **B7**：前端 `prompt-tune-pipeline.js` 已含 `startExtractionEval / getExtractionEvalStatus / getExtractionEvalReport`（Phase 2a 桩落地，Phase 5 直接用），Task 10 仅补 `cancelExtractionEval`。
- [ ] **B8**：前端 `CandidateMatrixRow.vue / ScoringRankingTable.vue / ScoringDetailDrawer.vue / scoring-format-model.js` 已存在（Phase 1d UI 落地），Phase 5 直接复用，不修改组件 API。

### Spec 覆盖自检

- [ ] **C1**：spec § 04 时态 ① "评分进行中"覆盖：候选矩阵 + 顶部进度摘要 + 中止按钮（Task 12）。
- [ ] **C2**：spec § 04 时态 ② "评分完成"覆盖：排行榜（金银铜牌 ScoringRankingTable 已落地）+ 详情抽屉（ScoringDetailDrawer 已落地）+ 操作列"选定"按钮。
- [ ] **C3**：spec § 04 详情抽屉 6 块指标 + 4 条质量门控覆盖：后端 ExtractionEvalReportAssembler 按 spec 阈值算 gate.passed（Task 5）。
- [ ] **C4**：spec § 04 底部固定操作条覆盖：进入预览按钮在未选定候选时禁用（Task 12 模板）。
- [ ] **C5**：spec § 路由设计 `selectedCandidates` query 持久化覆盖：03→04 router.replace 写入（Task 11）。
- [ ] **C6**：spec § 错误处理 "04 评分中途失败 → 候选矩阵中失败行变红，弹窗"覆盖：phase=failed 错误页 + 重试按钮（Task 12）。
- [ ] **C7**：spec § 已识别风险 #4 "04 评分时长不可预测"覆盖：异步任务 + DB 持久化进度 + 启动恢复（Task 1/6/8）。

### 依赖自检

- [ ] **D1**：Phase 5 不依赖 Phase 6 工件（05 步保存 / 历史草稿）。
- [ ] **D2**：Phase 5 复用 Phase 4 的 audit_with_gold.json / 候选 prompt.txt，不重新导出 audit。
- [ ] **D3**：Phase 5 不修改 Phase 4 已落地的 CandidateService / CandidateGenerationOrchestrator / AuditWithGoldExporter。
- [ ] **D4**：Phase 5 不依赖 Phase 7 IndexedDB 持久化，前端无标注表单需要离线缓存。
- [ ] **D5**：评分线程池 `extractionEvalExecutor` 和 `promptTuneExecutor` 互不阻塞（独立 bean）。
- [ ] **D6**：Phase 5 表 schema 直接含 `seed` 列（承接 Phase 4.5），不需要单独 ALTER TABLE 迁移；Entity / Service / Report 同步包含 seed 字段。

### 测试覆盖自检

- [ ] **E1**：每个新增类（ExtractionEvalOrchestrator / ExtractionEvalReportAssembler / ExtractionEvalWorker / ExtractionEvalService / ExtractionEvalStartupRecovery）至少 2 个单测；ExtractionEvalStartupRecoveryTest 必须覆盖 pending / running / cancelling 三态分流 + 空列表 noop。
- [ ] **E2**：异常路径测试：脚本超时、子进程非零退出、4104/4105/4108 三类门控、cancel 软取消、unknown candidate id 兜底、`candidate_failures` 列为空 / 非合法 JSON 时 report 接口降级为空数组。
- [ ] **E3**：worker 单测覆盖"按顺序串行抽取"、"全部完成跑 scoring"、"中途 cancelling 候选边界终止"、"单候选 failed 不阻断剩余 + 整体仍 success（且写入结构化 candidate_failures）"、"全候选 failed 才整体 failed"五条主路径。
- [ ] **E4**：前端 7 个 API 单测：startExtractionEval / 4108 业务码 / getExtractionEvalStatus / 4106 业务码 / getExtractionEvalReport / cancelExtractionEval / URL encoding。
- [ ] **E5**：Playwright e2e 7 个测试：blocked / running→done / done 默认选中 / 详情抽屉 / 选定切换 / failed / cancel。
- [ ] **E6**：真后端集成冒烟 5 步：触发 + 候选串行 + 产物隔离 + 报告（**含 seed 透传断言**）+ 取消。
- [ ] **E7**：ExtractionEvalServiceTest 含 seed 写入两条路径（含 seed / 不含 seed → null）；ExtractionEvalReportAssemblerTest 含 seed 透传两条路径。

---

## Phase 5 完成判定

- 后端：现有所有测试 PASS + Phase 5 新增测试全部 PASS（不写死数字，避免别人新增测试后失效）
- 前端单测：现有所有测试 PASS + 7 个新 API 测试 PASS
- 前端 e2e：现有套件 PASS + Phase 5 新加 7 个 PASS
- 真后端集成冒烟 5 步全过（触发 + 串行调度 + 产物按 build run 隔离 + 报告 / 取消）
- 一致性 / 上下文 / spec 覆盖 / 依赖 / 测试 5 类自检全过
- 所有 commit message 含 `(Phase 5)` 标记
- spec § Phase 5 段标记为 ✅ 已完成
