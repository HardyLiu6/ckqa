# CKQA 智能推荐路由闭环说明

日期：2026-05-18

状态：P3-B1 evaluation matrix added

## 参考方案

本轮检索后，采用“条件路由 + 路由参考语义面”的低成本方案作为第一版智能推荐闭环。

参考成熟方案：

1. LlamaIndex `RouterQueryEngine`：把候选 query engines 包装为 tools，selector 根据 query 和 tool metadata 选择一路执行。
   - https://docs.llamaindex.ai/en/stable/api_reference/query_engine/router/
2. Haystack `ConditionalRouter`：通过条件表达式把 pipeline 输入路由到不同输出路径。
   - https://docs.haystack.deepset.ai/docs/conditionalrouter
3. RedisVL `SemanticRouter`：为每个 route 配置 reference phrases 和 distance threshold，使用 KNN 风格匹配选路。
   - https://redis.io/docs/latest/develop/ai/redisvl/0.15.0/user_guide/semantic_router/

## CKQA 当前选型

P3-B0 不新增在线 LLM 路由请求，也不引入 Redis/向量库。正式链路新增 Java `/api/v1/qa-routing/recommend`，由后端做可观测模式推荐，前端只保留本地规则作为预览和兜底。

当前策略：

1. 条件路由：识别定义、资料定位、综述、扩展关联、证据要求、追问上下文。
2. 路由参考语义面：每个模式配置少量参考短语，使用轻量 token overlap 提供额外分数。
3. 成本门控：`hybrid_v0` 只有在 Beta 开关开启时才会成为推荐模式；关闭时回退到 `local` 或其它低成本模式。
4. 可观测输出：返回 `recommendedMode`、`fallbackMode`、`confidence`、`reasons`、`reasonText`、`routeScores` 和 `strategy`。

## P3-B1 路由评测与阈值调优

本轮补齐了离线路由评测集和真实 smoke 矩阵入口，目标是让“智能推荐”先具备可回归、可解释、低成本的闭环，而不是直接升级成新的在线 LLM 路由器。

新增评测集：

1. `backend/ckqa-back/src/test/resources/qa-routing-eval-set.jsonl`
   - 覆盖 `basic/local/global/drift/hybrid_v0`、Beta 关闭回退、上下文追问。
   - 每条样本包含 `expectedMode` 和 `acceptableModes`，允许少量业务上合理的等价路由。
2. `QaModeRoutingEvaluationTest`
   - 要求 `acceptableModes` 全部命中。
   - 要求 exact accuracy 不低于 `0.90`。

本轮阈值调优原则：

1. 证据 + 对比/关系 + 课程材料信号，在 Beta 开启时更倾向 `hybrid_v0`。
2. 指代型关系追问，例如“它和前面那个概念有什么区别”，不把“概念”误判为普通定义题。
3. `hybrid_v0` 仍受 Beta 开关控制；关闭时回退到 `local`。

新增 smoke 矩阵脚本：

```bash
cd backend/ckqa-back
python scripts/run_qa_routing_smoke_matrix.py \
  --base-url http://127.0.0.1:18081/api/v1 \
  --student-username "$CKQA_STUDENT_USERNAME" \
  --student-password "$CKQA_STUDENT_PASSWORD" \
  --course-id "$CKQA_SMOKE_COURSE_ID" \
  --knowledge-base-id "$CKQA_SMOKE_KNOWLEDGE_BASE_ID"
```

默认行为只做：

1. 真实学生登录。
2. 真实 `/api/v1/qa-routing/recommend` 路由矩阵。
3. hybrid warmup/readiness。

默认不会调用 `/qa-sessions/{id}/messages`，因此不会新增 hybrid 生成，也不会因为 smoke 矩阵额外外发课程片段。若确实要做生成型 smoke，必须显式加：

```bash
--execute-qa --i-understand-external-model-calls
```

## 后续 P3-B2

等 P3-B1 真实使用后，再基于运维样本和学生反馈继续扩充 holdout 集。若需要进一步提高语义识别效果，可把当前 `route reference` 升级为本地 embedding/KNN 或 RedisVL 风格语义路由，但仍应保持 Beta、成本和权限门控。
