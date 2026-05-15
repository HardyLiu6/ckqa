# GraphRAG Global Search 调参实验记录

> **状态：2026-05-14 结果已作废；2026-05-15 重跑入口已校准，但正式实验因上游模型账号 `Arrearage` 中止。**
>
> 复盘原因：
>
> 1. 文档中把 `http://127.0.0.1:8012` 写成 Python GraphRAG 服务入口，但 2026-05-15 复核时宿主机 `8012` 未监听；用户也指出该地址不是当前 Python 服务端。
> 2. 后续“直跑 CLI”是在沙箱网络环境中执行的；同一 `127.0.0.1` 下的 One API 网关 `3301` 在沙箱内不可达，而在宿主网络下可返回 One API `401 Unauthorized`，说明此前超时很可能测到的是网络/入口链路问题，不是 `global_search` 参数本身。
> 3. 下方 `global-tuning-g*` run 目录只保留为错误实验的审计痕迹，不应作为参数调优依据。
> 4. 2026-05-15 宿主网络 CLI smoke 已能返回真实答案，但正式 B 组执行到 `G0` 后段时 One API 上游 `openai/deepseek-v4-pro` 返回 `Arrearage / overdue-payment`，后续 `G1/G2` 退化为固定 no-data fallback，因此本轮仍不能得出参数优劣结论。

## 目标

在不重建索引的前提下，仅通过调整 `settings.yaml` 的 `global_search` 与 `concurrent_requests`，验证下列问题：

1. `global_search.max_context_tokens` 在当前课程图谱上是否存在更优窗口。
2. `concurrent_requests` 从 20 提升到 30 是否能稳定降低 `global` 的 p95。
3. 进一步压缩 `map_max_length` / `reduce_max_length` 是否能在可接受的质量损失内继续提速。

## 固定前提

- worktree：`.claude/worktrees/student-qa-integration`
- 索引来源：`graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047`
- 仅执行 `global` 模式
- API Base URL：原计划写作 `http://127.0.0.1:8012`，但该前提已被 2026-05-15 复核否定
- 不重建索引，不改 prompt，不改模型
- `data_max_tokens` 固定为 `3000`

## 2026-05-15 重跑配置

本轮重跑不再依赖 `http://127.0.0.1:8012`。实验入口改为：

- 执行方式：宿主网络权限下直跑 GraphRAG CLI
- 命令入口：`python -m graphrag query --root . --method global "<question>"`
- 模型网关：读取 `.env` 中的 `GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1`
- 环境：`graphrag-oneapi`
- 索引目录：
  - `GRAPHRAG_OUTPUT_DIR=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047`
  - `GRAPHRAG_STORAGE_DIR=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047`
  - `GRAPHRAG_LANCEDB_URI=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/lancedb`
- 先决 smoke：`Q001` 必须在该入口下成功返回，才允许重跑 `G0/G1/G2/G3/G5`
- 执行审批：宿主网络 CLI 会通过本机 One API 调用模型，可能发送问题和检索到的课程片段；需用户显式批准后再执行

## 本次实际执行方式（已判定无效）

- 沙箱内无法稳定绑定本地监听端口，`utils/main.py` 在 `8012` 和 `18012` 上都返回 `could not bind on any address`
- 为避免把结论污染到 HTTP 包装层，本次实际执行改为 **直接调用 `python -m graphrag query --method global`**
- 运行环境仍然显式指向同一份 canonical index：
  - `GRAPHRAG_OUTPUT_DIR`
  - `GRAPHRAG_STORAGE_DIR`
  - `GRAPHRAG_LANCEDB_URI`
- 复盘后确认：由于直跑 CLI 仍需访问 `.env` 中的 `GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1`，而该地址在沙箱网络内不可达，本轮记录不能代表 `global` 真实执行结果

## 基线来源

- 现有基线文档：`graphrag_pipeline/results/qa_eval/settings_ab_latency_findings.md`
- 当前已验证较稳参数：

```yaml
concurrent_requests: 20

global_search:
  max_context_tokens: 24000
  data_max_tokens: 3000
  map_max_length: 250
  reduce_max_length: 600
```

## 样本规模

本轮采用 B 规模，共 16 题，四类题型均衡，每类 4 题。

| 类别 | 题号 |
| --- | --- |
| `factual_lookup` | `Q001` `Q004` `Q007` `Q008` |
| `relation_reasoning` | `Q009` `Q012` `Q015` `Q016` |
| `chapter_summary` | `Q017` `Q019` `Q022` `Q024` |
| `global_overview` | `Q025` `Q027` `Q029` `Q032` |

## 实验组

按 `G0 -> G1 -> G2 -> G3 -> G5` 顺序执行。

| 组别 | `concurrent_requests` | `max_context_tokens` | `data_max_tokens` | `map_max_length` | `reduce_max_length` | 目的 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `G0` | 20 | 24000 | 3000 | 250 | 600 | 基线重放 |
| `G1` | 20 | 18000 | 3000 | 250 | 600 | 验证适度下压 `max_context_tokens` |
| `G2` | 20 | 30000 | 3000 | 250 | 600 | 验证继续增大 `max_context_tokens` |
| `G3` | 30 | 24000 | 3000 | 250 | 600 | 验证中幅提高并发 |
| `G5` | 20 | 24000 | 3000 | 200 | 500 | 验证更激进压输出长度 |

## 记录字段

每组至少记录：

- `run_id`
- `success_rate`
- `avg_elapsed_s`
- `p95_elapsed_s`
- `max_elapsed_s`
- `timeout_count`
- `answer_chars_mean`
- `Q017 / Q022` 人工观察
- `Q025 / Q027 / Q029 / Q032` 人工观察

## 通过标准

一组参数要进入下一轮候选，至少满足：

1. `success_rate = 16/16`
2. 相比 `G0`：
   - `avg_elapsed_s` 下降 `>= 8%`，或
   - `p95_elapsed_s` 下降 `>= 10%`
3. `answer_chars_mean` 不比 `G0` 下降超过 `25%`
4. `chapter_summary` 与 `global_overview` 不出现明显空泛回答

## 止损规则

- 任一组出现 `2` 题及以上超时：停止该组
- 出现 `429`、连接错误或空回答：标记该组不稳定
- `global_overview` 连续 2 题明显空泛：该组不进入下一轮

## 执行命令模板（旧版，保留作废审计）

> 下面命令是原计划模板，当前不应直接复用。下一轮实验必须先确认真实 Python GraphRAG 服务端地址，或在宿主网络权限下执行 CLI / runner。

先运行 baseline runner：

```bash
conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.run_baseline_eval \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
  --run-id <run_id> \
  --index-run-label auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --index-output-dir graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --base-url http://127.0.0.1:8012 \
  --modes global \
  --request-timeout-seconds 240 \
  --question-ids Q001 Q004 Q007 Q008 Q009 Q012 Q015 Q016 Q017 Q019 Q022 Q024 Q025 Q027 Q029 Q032
```

再生成耗时分解：

```bash
conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.latency_reporter \
  --run-dir graphrag_pipeline/results/qa_eval/runs/<run_id>
```

可选补充规则评分：

```bash
conda run -n graphrag-oneapi python -m graphrag_pipeline.scripts.qa_eval.baseline_scorer \
  --run-dir graphrag_pipeline/results/qa_eval/runs/<run_id>
```

## 2026-05-15 smoke 命令模板

```bash
cd /home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline
set -a
source .env
export GRAPHRAG_OUTPUT_DIR=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047
export GRAPHRAG_STORAGE_DIR=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047
export GRAPHRAG_LANCEDB_URI=/home/sunlight/Projects/ckqa/.claude/worktrees/student-qa-integration/graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/lancedb
export NO_PROXY=127.0.0.1,localhost,::1
export no_proxy=127.0.0.1,localhost,::1
set +a
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m graphrag query \
  --root . \
  --method global \
  "教材第一章如何定义操作系统，它在计算机硬件之上处于什么位置？"
```

## 2026-05-15 smoke 结果

| 项目 | 结果 |
| --- | --- |
| `Q001` smoke | 成功，`124.64s` |
| 入口 | 宿主网络权限下 GraphRAG CLI |
| 是否允许重跑参数组 | 是，继续执行 `G0/G1/G2/G3/G5` |

## 2026-05-15 重跑结果

> 本轮重跑已证明入口问题被修正：`global` 可以在宿主网络 CLI 下访问 canonical index 并生成带引用的真实答案。但正式参数实验在外部模型账号不可用后中止，不能把 `G1/G2` 的快速返回视为调参收益。

| 组别 | run_id | 执行题数 | success_rate | avg_elapsed_s | p95_elapsed_s | max_elapsed_s | timeout_like_count | answer_chars_mean | 结论 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `G0` | `global-tuning-20260515-g0-20260515-121804` | `9/16` | `7/9` | `97.8280` | `122.9334` | `122.9334` | `0` | `1228.5714` | 前 7 题为真实回答；`Q016/Q017` 出现上游 `Arrearage / overdue-payment`，提前停止 |
| `G1` | `global-tuning-20260515-g1-20260515-123305` | `16/16` | `16/16` | `8.7849` | `9.1378` | `9.4795` | `0` | `75.0` | 全部为 `I am sorry but I am unable to answer...` no-data fallback，质量失效 |
| `G2` | `global-tuning-20260515-g2-20260515-123529` | `8/16` | `8/8` | `9.2604` | `9.7815` | `9.7815` | `0` | `75.0` | 同样为 no-data fallback；确认上游异常后手动停止 |
| `G3` | 未执行 | `0/16` | - | - | - | - | - | - | 因上游模型账号不可用停止，不继续污染实验 |
| `G5` | 未执行 | `0/16` | - | - | - | - | - | - | 因上游模型账号不可用停止，不继续污染实验 |

### 2026-05-15 观察

1. `G0/Q001` 等前序问题能返回真实、带 `[Data: Reports (...)]` 引用的答案，说明正确入口下 global 并非必然超时。
2. `Q016/Q017` 的错误根因是 One API 上游返回 `Arrearage / overdue-payment`，不是 `global_search` 参数或索引路径问题。
3. `G1/G2` 的低延迟来自固定 no-data fallback，`entity_hit_rate=0`、`must_cite_hit=0`、`citation_format_present=0`，不满足质量门槛。
4. 本轮修正了 `timeout_like` 分类：traceback 中的 `timeout=None` 不再被误判为超时；`Arrearage` 归类为上游模型错误。

### 2026-05-15 配置修复后复测

用户修复配置后，先重新执行 `Q001` smoke：

| 项目 | 结果 |
| --- | --- |
| `Q001` smoke | 成功，`106.57s` |
| 回答形态 | 正常课程答案，包含 `[Data: Reports (...)]` 引用 |
| 结论 | CLI、索引路径、One API 入口仍可通 |

随后用新前缀 `global-tuning-20260515-rerun` 重新启动完整 `G0/G1/G2/G3/G5`。`G0/Q001` 第一题即失败：

| 组别 | run_id | 执行题数 | success_rate | avg_elapsed_s | timeout_like_count | 结论 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `G0` | `global-tuning-20260515-rerun-g0-20260515-125050` | `1/16` | `0/1` | `72.1536` | `0` | 仍返回上游 `Arrearage / overdue-payment`，手动停止 |

复测时 `.env` 非密配置仍为：

```env
GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1
GRAPHRAG_QUERY_MODEL=deepseek-v4-pro
GRAPHRAG_SUMMARY_MODEL=deepseek-v4-pro
GRAPHRAG_EXTRACTION_MODEL=deepseek-v4-flash
```

runner traceback 中实际请求模型显示为 `openai/deepseek-v4-pro`，One API 返回：

- `Access denied, please make sure your account is in good standing`
- `type/code: Arrearage`
- `overdue-payment`

因此，当前阻塞仍是 One API 上游账号或 `deepseek-v4-pro` 通道状态，不是 GraphRAG 参数或实验 runner。

### 2026-05-15 禁用欠费渠道后增量实验

用户禁用欠费渠道后，重新执行 `Q001` smoke：

| 项目 | 结果 |
| --- | --- |
| `Q001` smoke | 成功，`160.17s` |
| 回答形态 | 正常课程答案，包含 `[Data: Reports (...)]` 引用 |
| 结论 | 欠费通道已不再污染该 smoke；可以继续参数实验 |

本轮不重复跑已经有效的结果：

- 复用 `global-tuning-20260515-g0-20260515-121804` 中 `G0` 已成功的 7 题：`Q001/Q004/Q007/Q008/Q009/Q012/Q015`
- 补跑 `G0` 缺口：`Q016/Q017/Q019/Q022/Q024/Q025/Q027/Q029/Q032`
- 合并 run：`global-tuning-20260515-rerun2-g0-combined-20260515-131031`
- `G1/G2` 旧结果是 no-data fallback，不能复用；`G3/G5` 旧轮未执行，均重新跑

| 组别 | run_id | 执行题数 | success_rate | avg_elapsed_s | success_avg_s | p95_elapsed_s | max_elapsed_s | timeout_like_count | answer_chars_mean | 结论 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `G0` | `global-tuning-20260515-rerun2-g0-combined-20260515-131031` | `14/16` | `12/14` | `152.7602` | `138.1936` | `240.1539` | `240.1658` | `2` | `1423.0` | 真实答案质量尚可，但 `Q025/Q027` 两个全局概览题超时，`Q029/Q032` 因止损未执行 |
| `G1` | `global-tuning-20260515-rerun2-g1-20260515-133508` | `2/16` | `0/2` | `240.1695` | `0.0` | `240.1708` | `240.1708` | `2` | `0.0` | `max_context_tokens=18000` 前两题即超时，淘汰 |
| `G2` | `global-tuning-20260515-rerun2-g2-20260515-134321` | `3/16` | `1/3` | `220.1382` | `180.0933` | `240.1693` | `240.1693` | `2` | `1232.0` | `max_context_tokens=30000` 未改善，`Q001/Q007` 超时，淘汰 |
| `G3` | `global-tuning-20260515-rerun2-g3-20260515-135440` | `9/16` | `7/9` | `154.1409` | `129.5591` | `240.1863` | `240.1863` | `2` | `1660.1429` | `concurrent_requests=30` 前 7 题稳定成功，但 `Q016/Q017` 连续超时，不能直接晋级 |
| `G5` | `global-tuning-20260515-rerun2-g5-20260515-141827` | `8/16` | `6/8` | `179.5965` | `159.4064` | `240.1683` | `240.1683` | `2` | `1243.6667` | 压缩 map/reduce 输出长度未稳定提速，`Q007/Q016` 超时，淘汰 |

#### rerun2 结论

1. 本轮没有任何组满足通过标准：所有组都未达到 `success_rate = 16/16`，且均触发 2 次 timeout 止损。
2. `G1` 下压 `max_context_tokens` 到 `18000` 明显不可取，前两题直接超时。
3. `G2` 增大 `max_context_tokens` 到 `30000` 也不可取，成功率低且耗时更重。
4. `G5` 压缩 `map_max_length/reduce_max_length` 对稳定性帮助有限，不能解决 timeout。
5. `G3` 是相对最有价值的方向：`concurrent_requests=30` 让前 7 题成功且 `success_avg_s=129.5591`，但仍在 `Q016/Q017` 连续超时；它可以作为下一轮组合实验的基础，而不是当前默认值。
6. `G0` 基线质量最好、成功题最多，但全局概览题 `Q025/Q027` 在 240 秒预算内不可用。

### 2026-05-15 G0 + dynamic community selection 小实验

用户追问后，先做一个最小实验，不展开完整组合矩阵：只验证 `G0` 原配置叠加 GraphRAG CLI runtime flags 是否能缩短上一轮 `G0` 超时题。

运行配置：

- `concurrent_requests=20`
- `global_search.max_context_tokens=24000`
- `global_search.data_max_tokens=3000`
- `global_search.map_max_length=250`
- `global_search.reduce_max_length=600`
- CLI flags：`--dynamic-community-selection --community-level 2`
- 时间墙：`timeout 240s`
- run 目录：`results/qa_eval/runs/global-tuning-20260515-g0-dynamic-smoke-q025-q027`

| question_id | 问题类型 | old `G0` | `G0 + dynamic` | exit_code | stdout_chars | citation_count | 结论 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| `Q025` | `global_overview` 全书学习路径 | `240.1658s timeout` | `249.20s timeout` | `124` | `0` | `0` | 未改善 |
| `Q027` | `global_overview` 处理机管理主线 | `240.1539s timeout` | `223.77s success` | `0` | `1898` | `13` | 拉回 240 秒预算内 |

结论：

1. `--dynamic-community-selection --community-level 2` 对 `G0` 有局部价值：`Q027` 从 timeout 变为成功，并保留 `[Data: Reports (...)]` 引用。
2. 它不是稳定解法：`Q025` 仍在同一时间墙下超时。
3. 下一轮计划不应单线围绕 `G3`；更稳妥的是保留 `G0` 为稳定基线，单独比较 `G0 + dynamic`、`G0 + 输出压缩`，再把 `G3` 仅作为并发探索变量。
4. `Q025` 这类“全书结构路径”问题仍需要额外策略，例如更强的社区筛选、降低 map/reduce 输出长度，或把 `global_overview` 拆为专用检索策略。

下一轮建议不再单独扫 `max_context_tokens`，而是做 `G0` 稳定基线与 `G3` 并发探索的双轨收敛：

- `concurrent_requests=30`
- 适度降低输出长度，例如 `map_max_length=200`、`reduce_max_length=500`
- 或尝试 GraphRAG CLI 的 dynamic community selection / community 层级相关参数
- 同时将全局概览题单独列为压力样本，优先看 `Q025/Q027/Q029/Q032`

### 2026-05-15 后续命令

若需要再次完整复验本轮结论，可复用当前 CLI runner，并换一个新的 `run-id-prefix`：

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning \
  --graphrag-root graphrag_pipeline \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
  --index-output-dir graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --groups G0 G1 G2 G3 G5 \
  --question-ids Q001 Q004 Q007 Q008 Q009 Q012 Q015 Q016 Q017 Q019 Q022 Q024 Q025 Q027 Q029 Q032 \
  --run-id-prefix global-tuning-20260515-rerun3 \
  --python-executable /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python \
  --request-timeout-seconds 240 \
  --stop-after-timeout-count 2
```

## 2026-05-14 作废执行结果记录

| 组别 | run_id | success_rate | avg_elapsed_s | p95_elapsed_s | max_elapsed_s | timeout_count | answer_chars_mean | 结论 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `G0` | `global-tuning-g0-20260514-2254` | `0/2` | `240.1610` | `240.1732` | `240.1732` | `2` | `0.0` | 前两题均超时，按止损规则中断 |
| `G1` | `global-tuning-g1-20260514-2303` | `0/2` | `240.1406` | `240.1411` | `240.1411` | `2` | `0.0` | 下压 `max_context_tokens` 无改善，前两题均超时 |
| `G2` | `global-tuning-g2-20260514-2313` | `0/2` | `240.1416` | `240.1417` | `240.1417` | `2` | `0.0` | 增大 `max_context_tokens` 仍无改善，前两题均超时 |
| `G3` | `global-tuning-g3-20260514-2323` | `0/2` | `240.1360` | `240.1365` | `240.1365` | `2` | `0.0` | 提高 `concurrent_requests` 无改善，前两题均超时 |
| `G5` | `global-tuning-g5-20260514-2333` | `0/2` | `240.1471` | `240.1472` | `240.1472` | `2` | `0.0` | 压缩 `map/reduce length` 无改善，前两题均超时 |

## 本轮结论

1. 原始结论“这五组表层参数无法把 global 拉回可用窗口”不成立，不能继续引用。
2. 2026-05-15 已校准真实执行入口：宿主网络权限下的 GraphRAG CLI + worktree canonical index + One API `3301/v1`。
3. 禁用欠费渠道后，实验不再被 `Arrearage` 污染；本轮超时主要是 global 真实耗时问题。
4. `G0/G1/G2/G3/G5` 均未满足 `success_rate = 16/16`，不能把任何一组提升为当前稳定默认值。
5. 当前最值得继续探索的是 `G3` 方向，即提高 `concurrent_requests`，但需要再叠加缩短输出或 dynamic community selection 来处理 `Q016/Q017` 与全局概览题的超时。

## 备注

- 本文档既是执行前实验设计，也是执行后的结果汇总入口。
- 若执行中发现 `G3` 已出现上游排队或错误，不再追加更高并发组。
- 若 `G1` / `G2` 都没有明显收益，下一步优先考虑 CLI 直跑 `dynamic community selection`。
