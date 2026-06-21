# CKQA P3-D1 GraphRAG Query Engine Conversation History PoC 实施计划

## Summary

在 `graphrag_pipeline` 内实现一个旁路 PoC，用来验证 Microsoft GraphRAG `LocalSearch.search(query, conversation_history=...)` 是否比当前 CKQA 的 `retrievalQuery + generationContext` CLI 封装更适合多轮追问。

本计划只新增实验端点和离线评估脚本，不替换正式 Java QA 链路，不修改 student-app，不新增数据库 schema。默认不发起真实模型调用，只有显式开启环境变量后才运行 live PoC。

## Current Facts

1. 项目固定 `graphrag==3.0.9`。
2. 正式 `/v1/query-tasks` 仍走 GraphRAG CLI。
3. `generationContext` 对 `basic/local/global/drift` 只做诊断保存，不参与 CLI 查询。
4. 本地包存在 `LocalSearch.search(query, conversation_history=...)`。
5. GraphRAG query engine 内部 API 仍属于 PoC 依赖，不应直接替换正式链路。

## Public Interfaces

新增 Python 实验开关：

```text
CKQA_GRAPHRAG_HISTORY_POC_ENABLED=false
CKQA_GRAPHRAG_HISTORY_POC_MAX_TURNS=5
CKQA_GRAPHRAG_HISTORY_POC_MAX_HISTORY_CHARS=2500
CKQA_GRAPHRAG_HISTORY_POC_MAX_CONTEXT_TOKENS=5000
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_ENTITIES=6
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_RELATIONSHIPS=6
CKQA_GRAPHRAG_HISTORY_POC_RETURN_CONTEXT=false
```

新增实验 API，默认禁用：

```text
GET /v1/experiments/query-engine-history/readiness?dataDirUri=...
POST /v1/experiments/query-engine-history/query
```

`POST` 请求体：

```json
{
  "dataDirUri": "index/output",
  "query": "它和资源分配图有什么关系？",
  "conversationHistory": [
    { "role": "user", "content": "什么是死锁？" },
    { "role": "assistant", "content": "死锁是多个进程相互等待资源而无法继续推进的状态。" }
  ],
  "maxTurns": 5,
  "userTurnsOnly": true,
  "returnCandidateContext": false
}
```

响应体：

```json
{
  "enabled": true,
  "supported": true,
  "method": "local_history_poc",
  "answer": "...",
  "historyApplied": true,
  "historyTurnsUsed": 2,
  "elapsedMs": 1234,
  "diagnostics": {
    "graphragVersion": "3.0.9",
    "adapter": "LocalSearch",
    "dataDirUri": "index/output"
  },
  "candidateContextSummary": null,
  "errorMessage": null
}
```

## Key Changes

1. 新增 `graphrag_pipeline/utils/query_engine_history_poc.py`
   - 只支持 `local` query engine。
   - 使用 build-run `dataDirUri` resolver，拒绝绝对路径和路径穿越。
   - 读取 build-run output parquet 与 `lancedb`，构建 `LocalSearch`。
   - 通过 `ConversationHistory.from_list()` 构造最近 N 条历史。
   - 调用 `LocalSearch.search(query, conversation_history=history)`。
   - import、artifact、LanceDB 或模型配置不可用时返回明确诊断，不影响正式服务。

2. 更新 `graphrag_pipeline/utils/main.py`
   - 挂载 `/v1/experiments/query-engine-history/readiness`。
   - 挂载 `/v1/experiments/query-engine-history/query`。
   - 默认 disabled；query 在未启用时返回 403。
   - 不接入 `/v1/query-tasks`，不改变 Java `GraphRagTaskClient`。

3. 新增 `graphrag_pipeline/scripts/run_graphrag_history_poc_eval.py`
   - 默认 dry-run，只生成报告，不调用真实 query engine。
   - 必须设置 `CKQA_HISTORY_POC_LIVE=1` 才执行 live 查询。
   - 输出 `docs/reports/graphrag-history-poc/<yyyyMMdd-HHmmss>-<label>/report.json` 和 `summary.md`。
   - 报告比较三路：当前 CLI local、CKQA 双输入诊断、实验 LocalSearch history。
   - D1.2 增加 `--run-hybrid-baseline`，在显式授权后同批执行 CKQA `hybrid_v0` baseline，用于比较质量信号、来源覆盖和延迟。
   - D1.2 报告会输出 LocalSearch history 与 CKQA hybrid 的 status、elapsedMs、sources 数、`sourceRecallAt3` 和 `tokenLimitWarningCount`。

4. D1.2 调参与清洗
   - LocalSearch history 使用受控 context 参数，避免默认 query engine 一次性吞入过多上下文。
   - 实验回答复用 CKQA 的 `[Data:]` 清洗和来源解析层，确保 PoC 结果能与正式学生端展示口径对齐。
   - raw answer 仍保留在 JSON 报告中，便于诊断 GraphRAG 原始引用行为。
   - 当前不把该能力接入 Java 正式 QA 链路。

5. 更新遗留问题清单
   - 将 P3-D1 标记为旁路 PoC 已落地。
   - 后续是否接入正式链路取决于 live PoC 报告中的质量收益、延迟和失败率。

## Test Plan

Python 单元测试：

1. readiness disabled。
2. artifact 缺失返回 not ready。
3. 路径穿越被拒绝。
4. conversation history role 校验和长度裁剪。
5. adapter mock 确认调用 `LocalSearch.search(query, conversation_history=...)`，不把 history 拼进 query 字符串。
6. endpoint mock 确保实验端点 JSON 稳定，不影响 `/v1/query-tasks`。
7. eval script dry-run 生成版本化报告。

验证命令：

```bash
cd graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python scripts/run_graphrag_history_poc_eval.py --dry-run
cd ..
git diff --check -- graphrag_pipeline docs
```

可选 live PoC：

```bash
cd graphrag_pipeline
CKQA_GRAPHRAG_HISTORY_POC_ENABLED=true \
CKQA_GRAPHRAG_HISTORY_POC_MAX_CONTEXT_TOKENS=5000 \
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_ENTITIES=6 \
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_RELATIONSHIPS=6 \
CKQA_HISTORY_POC_LIVE=1 \
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python scripts/run_graphrag_history_poc_eval.py \
  --data-dir-uri user_12/kb_5/build_20/index/output \
  --run-label os-live-local-history
```

可选 live 对比 CKQA hybrid baseline：

```bash
cd graphrag_pipeline
CKQA_GRAPHRAG_HISTORY_POC_ENABLED=true \
CKQA_GRAPHRAG_HISTORY_POC_MAX_CONTEXT_TOKENS=5000 \
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_ENTITIES=6 \
CKQA_GRAPHRAG_HISTORY_POC_TOP_K_RELATIONSHIPS=6 \
CKQA_HISTORY_POC_LIVE=1 \
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python scripts/run_graphrag_history_poc_eval.py \
  --data-dir-uri user_12/kb_5/build_20/index/output \
  --run-label os-live-local-vs-hybrid-d1-2 \
  --limit 3 \
  --run-hybrid-baseline
```

## Assumptions

1. P3-D1 只验证 GraphRAG query engine conversation history 的技术收益。
2. 只做 `local` history PoC。
3. 不改 Java `/api/v1/qa-sessions`、不改 DB、不改 student-app。
4. 如果 GraphRAG 3.0.9 内部 API 在真实 build-run 上不稳定，本阶段可以得出“暂不迁移”的结论。
5. D1.2 只做调参、清洗和对比报告；只有当报告显示多轮追问质量有稳定收益且延迟可接受，后续才制定正式链路接入计划。
