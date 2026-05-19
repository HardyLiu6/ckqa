# GraphRAG Global Search G3 组合优化实验计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重建索引的前提下，围绕 `G3 concurrent_requests=30` 做组合实验，验证能否把 global search 的压力题从 240 秒 timeout 拉回可用窗口。

**Architecture:** 复用 `.claude/worktrees/student-qa-integration` 隔离 worktree、canonical index 和现有 `run_global_cli_tuning.py`。先增强 runner 支持自定义候选组与 GraphRAG CLI runtime flags，再用压力题集小规模筛选，最后只对幸存候选跑完整 B 规模。

**Tech Stack:** Python 3.11, Microsoft GraphRAG 3.0.9 CLI, `pytest`, `latency_reporter`, `baseline_scorer`, One API `http://127.0.0.1:3301/v1`。

---

## 背景结论

上一轮 `rerun2` 的有效结论：

| 组别 | 核心配置 | 结果 |
| --- | --- | --- |
| `G0` | `concurrent_requests=20`, `max_context_tokens=24000`, `map/reduce=250/600` | 12/14 成功，但 `Q025/Q027` 超时 |
| `G1` | `max_context_tokens=18000` | 前两题超时，淘汰 |
| `G2` | `max_context_tokens=30000` | 3 题 2 超时，淘汰 |
| `G3` | `concurrent_requests=30` | 前 7 题成功，`success_avg_s=129.5591`，但 `Q016/Q017` 超时 |
| `G5` | `map/reduce=200/500` | 8 题 2 超时，淘汰 |

下一轮不再单独扫 `max_context_tokens`；主线改为：

1. 保留 `concurrent_requests=30`。
2. 叠加输出长度压缩。
3. 测试 GraphRAG CLI 的 `--dynamic-community-selection` 与 `--community-level`。
4. 优先用压力题集验证，不先跑完整 16 题。

本地 GraphRAG 3.0.9 CLI 已确认支持：

```bash
graphrag query --root . --method global \
  --community-level 2 \
  --dynamic-community-selection \
  "问题"
```

---

## 样本设计

### Stage A: 压力题集

只跑上一轮暴露问题的 6 题：

| 题号 | 原因 |
| --- | --- |
| `Q016` | `G3/G5` 均在此题附近出现 timeout |
| `Q017` | `G3` 第二个 timeout |
| `Q025` | `G0` 全局概览 timeout |
| `Q027` | `G0` 全局概览 timeout |
| `Q029` | `G0` 因止损未执行，属于全局概览压力题 |
| `Q032` | `G0` 因止损未执行，属于全局概览压力题 |

Stage A 通过标准：

1. `success_rate >= 5/6`
2. `timeout_like_count <= 1`
3. `answer_chars_mean >= 700`
4. `citation_format_present >= 0.8`
5. 不出现固定 no-data fallback：`I am sorry but I am unable to answer this question given the provided data.`

### Stage B: 完整 B 规模

只有 Stage A 通过的组进入完整 16 题：

```text
Q001 Q004 Q007 Q008
Q009 Q012 Q015 Q016
Q017 Q019 Q022 Q024
Q025 Q027 Q029 Q032
```

Stage B 通过标准沿用原实验：

1. `success_rate = 16/16`
2. 相比 `G0` 或 `G3` 参考组：
   - `avg_elapsed_s` 下降 `>= 8%`，或
   - `p95_elapsed_s` 下降 `>= 10%`
3. `answer_chars_mean` 不比参考组下降超过 `25%`
4. `chapter_summary` 与 `global_overview` 不出现明显空泛回答

---

## 候选组

| 组别 | `concurrent_requests` | `max_context_tokens` | `data_max_tokens` | `map_max_length` | `reduce_max_length` | CLI runtime flags | 目的 |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| `C0` | 30 | 24000 | 3000 | 250 | 600 | 无 | `G3` 压力题复验参考 |
| `C1` | 30 | 24000 | 3000 | 200 | 500 | 无 | 组合 `G3 + G5`，验证并发加输出压缩 |
| `C2` | 30 | 24000 | 3000 | 180 | 450 | 无 | 更激进压缩输出，观察质量损失 |
| `C3` | 30 | 24000 | 3000 | 250 | 600 | `--dynamic-community-selection --community-level 2` | 验证动态社区选择是否减少无关 reports |
| `C4` | 30 | 24000 | 3000 | 200 | 500 | `--dynamic-community-selection --community-level 2` | 最有希望组合：并发 + 输出压缩 + 动态社区选择 |

暂不把 `community-level=1` 放入第一轮，避免一次变量过多；若 `C3/C4` 有收益，再追加 `C5 community-level=1`。

---

## 文件结构

**Modify:** `graphrag_pipeline/scripts/qa_eval/run_global_cli_tuning.py`

- 支持从 JSON 读取候选组配置。
- 支持每组附加 GraphRAG CLI runtime flags。
- 把 no-data fallback 标为错误，避免再次把无效 75 字符答案计入成功。

**Modify:** `graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py`

- 覆盖自定义候选组配置。
- 覆盖 CLI flags 注入。
- 覆盖 no-data fallback 错误化。

**Create:** `graphrag_pipeline/results/qa_eval/global_search_g3_combo_groups.json`

- 存放 `C0-C4` 候选组配置。

**Update:** `graphrag_pipeline/results/qa_eval/2026-05-14-global-search-tuning-experiment.md`

- 写入 Stage A / Stage B 结果与结论。

---

## Task 1: 扩展 CLI Runner 支持候选组 JSON

**Files:**

- Modify: `graphrag_pipeline/scripts/qa_eval/run_global_cli_tuning.py`
- Modify: `graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py`

- [ ] **Step 1: 写失败测试，验证可加载自定义候选组**

在 `test_qa_eval_global_cli_tuning.py` 增加：

```python
def test_runner_loads_custom_group_config(tmp_path):
    from graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning import load_group_config

    config_path = tmp_path / "groups.json"
    config_path.write_text(
        json.dumps(
            {
                "C4": {
                    "concurrent_requests": 30,
                    "max_context_tokens": 24000,
                    "data_max_tokens": 3000,
                    "map_max_length": 200,
                    "reduce_max_length": 500,
                    "extra_query_args": [
                        "--dynamic-community-selection",
                        "--community-level",
                        "2",
                    ],
                }
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    groups = load_group_config(config_path)

    assert groups["C4"].global_search_config.concurrent_requests == 30
    assert groups["C4"].global_search_config.map_max_length == 200
    assert groups["C4"].extra_query_args == [
        "--dynamic-community-selection",
        "--community-level",
        "2",
    ]
```

- [ ] **Step 2: 运行测试确认失败**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_loads_custom_group_config
```

预期：`ImportError` 或 `AttributeError`，因为 `load_group_config` 尚未实现。

- [ ] **Step 3: 实现最小数据结构**

在 `run_global_cli_tuning.py` 增加：

```python
@dataclass(frozen=True, slots=True)
class CandidateGroup:
    global_search_config: GlobalSearchConfig
    extra_query_args: list[str]


def load_group_config(path: Path) -> dict[str, CandidateGroup]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    groups: dict[str, CandidateGroup] = {}
    for name, raw in payload.items():
        groups[name.upper()] = CandidateGroup(
            global_search_config=GlobalSearchConfig(
                concurrent_requests=int(raw["concurrent_requests"]),
                max_context_tokens=int(raw["max_context_tokens"]),
                data_max_tokens=int(raw["data_max_tokens"]),
                map_max_length=int(raw["map_max_length"]),
                reduce_max_length=int(raw["reduce_max_length"]),
            ),
            extra_query_args=[str(value) for value in raw.get("extra_query_args", [])],
        )
    return groups
```

- [ ] **Step 4: 运行测试确认通过**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_loads_custom_group_config
```

预期：`1 passed`。

---

## Task 2: 支持把 runtime flags 注入 GraphRAG CLI

**Files:**

- Modify: `graphrag_pipeline/scripts/qa_eval/run_global_cli_tuning.py`
- Modify: `graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py`

- [ ] **Step 1: 写失败测试**

在现有 fake subprocess 测试里新增断言：

```python
from graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning import CandidateGroup


def test_runner_passes_extra_query_args_to_graphrag_cli(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_global_cli_tuning

    captured_commands: list[list[str]] = []

    def _fake_run(command, **kwargs):
        captured_commands.append(command)
        return subprocess.CompletedProcess(
            command,
            0,
            stdout="SUCCESS: Global Search Response:\n有效答案 [Data: Reports (1)]\n",
            stderr="",
        )

    monkeypatch.setattr(run_global_cli_tuning.subprocess, "run", _fake_run)
    graphrag_root = tmp_path / "graphrag_pipeline"
    graphrag_root.mkdir()
    settings_path = graphrag_root / "settings.yaml"
    env_file = graphrag_root / ".env"
    test_set = tmp_path / "qa_test_set.jsonl"
    index_output_dir = tmp_path / "output" / "index"
    _write_settings(settings_path)
    env_file.write_text(
        "GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1\n"
        "GRAPHRAG_CHAT_API_KEY=test-key\n",
        encoding="utf-8",
    )
    _write_test_set(test_set, count=1)
    _write_core_index_output(index_output_dir)
    monkeypatch.setattr(
        run_global_cli_tuning,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )

    runner = GlobalCliTuningRunner(
        graphrag_root=graphrag_root,
        test_set_path=test_set,
        index_output_dir=index_output_dir,
        output_root=tmp_path / "runs",
        settings_path=settings_path,
        env_file=env_file,
        groups=["C4"],
        question_ids=["Q001"],
        run_id_prefix="test-extra-args",
        python_executable="/fake/python",
        custom_group_presets={
            "C4": CandidateGroup(
                global_search_config=GlobalSearchConfig(30, 24000, 3000, 200, 500),
                extra_query_args=[
                    "--dynamic-community-selection",
                    "--community-level",
                    "2",
                ],
            )
        },
    )
    runner.run()

    assert "--dynamic-community-selection" in captured_commands[0]
    assert "--community-level" in captured_commands[0]
    assert "2" in captured_commands[0]
```

- [ ] **Step 2: 运行测试确认失败**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_passes_extra_query_args_to_graphrag_cli
```

预期：断言失败，因为当前 command 未包含 runtime flags。

- [ ] **Step 3: 修改 command 构造**

把 `_run_item()` 的 command 改为：

```python
command = [
    self.python_executable,
    "-m",
    "graphrag",
    "query",
    "--root",
    ".",
    "--method",
    "global",
    *extra_query_args,
    item.question,
]
```

同时把 `extra_query_args` 从 `_run_group()` 传入 `_run_item()`。

- [ ] **Step 4: 运行测试确认通过**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_passes_extra_query_args_to_graphrag_cli
```

预期：`1 passed`。

---

## Task 3: 把 no-data fallback 计为错误

**Files:**

- Modify: `graphrag_pipeline/scripts/qa_eval/run_global_cli_tuning.py`
- Modify: `graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py`

- [ ] **Step 1: 写失败测试**

```python
def test_runner_records_no_data_fallback_as_error(tmp_path, monkeypatch):
    from graphrag_pipeline.scripts.qa_eval import run_global_cli_tuning

    def _fake_run(command, **kwargs):
        return subprocess.CompletedProcess(
            command,
            0,
            stdout="I am sorry but I am unable to answer this question given the provided data.\n",
            stderr="",
        )

    monkeypatch.setattr(run_global_cli_tuning.subprocess, "run", _fake_run)
    graphrag_root = tmp_path / "graphrag_pipeline"
    graphrag_root.mkdir()
    settings_path = graphrag_root / "settings.yaml"
    env_file = graphrag_root / ".env"
    test_set = tmp_path / "qa_test_set.jsonl"
    index_output_dir = tmp_path / "output" / "index"
    output_root = tmp_path / "runs"
    _write_settings(settings_path)
    env_file.write_text(
        "GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1\n"
        "GRAPHRAG_CHAT_API_KEY=test-key\n",
        encoding="utf-8",
    )
    _write_test_set(test_set, count=1)
    _write_core_index_output(index_output_dir)
    monkeypatch.setattr(
        run_global_cli_tuning,
        "validate_jsonl",
        lambda path: ValidationReport(ok=True, total=1),
    )

    runner = GlobalCliTuningRunner(
        graphrag_root=graphrag_root,
        test_set_path=test_set,
        index_output_dir=index_output_dir,
        output_root=output_root,
        settings_path=settings_path,
        env_file=env_file,
        groups=["G0"],
        question_ids=["Q001"],
        run_id_prefix="test-no-data",
        python_executable="/fake/python",
    )
    run_dir = runner.run()[0]

    raw = json.loads((run_dir / "raw" / "Q001.json").read_text(encoding="utf-8"))
    payload = raw["modes"]["graphrag-global-search:latest"]
    assert payload["error_type"] == "NoDataFallback"
```

- [ ] **Step 2: 运行测试确认失败**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_records_no_data_fallback_as_error
```

预期：当前会被计为成功，测试失败。

- [ ] **Step 3: 实现 fallback 检测**

在 `run_global_cli_tuning.py` 增加：

```python
NO_DATA_FALLBACKS = (
    "I am sorry but I am unable to answer this question given the provided data.",
)


def is_no_data_fallback(answer: str) -> bool:
    normalized = " ".join(answer.strip().split())
    return any(normalized == fallback for fallback in NO_DATA_FALLBACKS)
```

在 returncode 为 0 的分支中：

```python
answer = extract_cli_answer(stdout)
if is_no_data_fallback(answer):
    mode_payload = {
        "error": answer,
        "error_type": "NoDataFallback",
        "elapsed_seconds": elapsed_seconds,
        "raw": {
            "command": _safe_command(command),
            "returncode": completed.returncode,
            "stdout_tail": _tail_text(stdout),
            "stderr_tail": _tail_text(stderr),
        },
    }
else:
    mode_payload = {
        "answer": answer,
        "total_tokens": None,
        "elapsed_seconds": elapsed_seconds,
        "raw": {
            "command": _safe_command(command),
            "returncode": completed.returncode,
            "stdout_tail": _tail_text(stdout),
            "stderr_tail": _tail_text(stderr),
        },
    }
```

- [ ] **Step 4: 运行测试确认通过**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py::test_runner_records_no_data_fallback_as_error
```

预期：`1 passed`。

---

## Task 4: 写候选组 JSON

**Files:**

- Create: `graphrag_pipeline/results/qa_eval/global_search_g3_combo_groups.json`

- [ ] **Step 1: 创建配置文件**

写入：

```json
{
  "C0": {
    "concurrent_requests": 30,
    "max_context_tokens": 24000,
    "data_max_tokens": 3000,
    "map_max_length": 250,
    "reduce_max_length": 600,
    "extra_query_args": []
  },
  "C1": {
    "concurrent_requests": 30,
    "max_context_tokens": 24000,
    "data_max_tokens": 3000,
    "map_max_length": 200,
    "reduce_max_length": 500,
    "extra_query_args": []
  },
  "C2": {
    "concurrent_requests": 30,
    "max_context_tokens": 24000,
    "data_max_tokens": 3000,
    "map_max_length": 180,
    "reduce_max_length": 450,
    "extra_query_args": []
  },
  "C3": {
    "concurrent_requests": 30,
    "max_context_tokens": 24000,
    "data_max_tokens": 3000,
    "map_max_length": 250,
    "reduce_max_length": 600,
    "extra_query_args": ["--dynamic-community-selection", "--community-level", "2"]
  },
  "C4": {
    "concurrent_requests": 30,
    "max_context_tokens": 24000,
    "data_max_tokens": 3000,
    "map_max_length": 200,
    "reduce_max_length": 500,
    "extra_query_args": ["--dynamic-community-selection", "--community-level", "2"]
  }
}
```

- [ ] **Step 2: 校验 JSON**

```bash
python -m json.tool graphrag_pipeline/results/qa_eval/global_search_g3_combo_groups.json >/tmp/global_search_g3_combo_groups.pretty.json
```

预期：命令 exit `0`。

---

## Task 5: Stage A 压力题实验

**Files:**

- Output: `graphrag_pipeline/results/qa_eval/runs/global-tuning-g3-combo-a-*`

- [ ] **Step 1: 运行压力题集**

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning \
  --graphrag-root graphrag_pipeline \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
  --index-output-dir graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --group-config-file graphrag_pipeline/results/qa_eval/global_search_g3_combo_groups.json \
  --groups C0 C1 C2 C3 C4 \
  --question-ids Q016 Q017 Q025 Q027 Q029 Q032 \
  --run-id-prefix global-tuning-g3-combo-a \
  --python-executable /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python \
  --request-timeout-seconds 240 \
  --stop-after-timeout-count 2
```

预期：

- 每组自动生成 `latency_breakdown.*`
- 每组自动生成 `rule_scoring.*`
- 任何组出现 2 个 timeout 即止损

- [ ] **Step 2: 汇总 Stage A**

```bash
for d in graphrag_pipeline/results/qa_eval/runs/global-tuning-g3-combo-a-*; do
  echo "$d"
  jq '.per_mode["graphrag-global-search:latest"]' "$d/latency_breakdown.json"
  jq '.per_mode["graphrag-global-search:latest"]' "$d/rule_scoring.json"
done
```

预期：

- 至少输出 `success_count/error_count/timeout_like_error_count`
- 至少输出 `answer_chars/entity_hit_rate/citation_format_present`

- [ ] **Step 3: 决定 Stage B 候选**

进入 Stage B 的组必须满足：

```text
success_count >= 5
timeout_like_error_count <= 1
answer_chars >= 700
citation_format_present >= 0.8
```

若没有任何组达标，停止实验并直接写结论：当前 global 在 240 秒预算下需要改 retrieval/runtime 策略，而不是继续微调这几个表层参数。

---

## Task 6: Stage B 完整 B 规模

**Files:**

- Output: `graphrag_pipeline/results/qa_eval/runs/global-tuning-g3-combo-b-*`

- [ ] **Step 1: 运行幸存组**

下面命令以 `C4` 为示例；执行前先把 Stage A 达标组替换到 `--groups` 后面。若 Stage A 达标组是 `C1 C4`，则使用 `--groups C1 C4`。

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m graphrag_pipeline.scripts.qa_eval.run_global_cli_tuning \
  --graphrag-root graphrag_pipeline \
  --test-set graphrag_pipeline/data/eval/qa_test_set.jsonl \
  --index-output-dir graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047 \
  --group-config-file graphrag_pipeline/results/qa_eval/global_search_g3_combo_groups.json \
  --groups C4 \
  --question-ids Q001 Q004 Q007 Q008 Q009 Q012 Q015 Q016 Q017 Q019 Q022 Q024 Q025 Q027 Q029 Q032 \
  --run-id-prefix global-tuning-g3-combo-b \
  --python-executable /home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python \
  --request-timeout-seconds 240 \
  --stop-after-timeout-count 2
```

若幸存组不是 `C4`，只替换 `--groups` 参数。

- [ ] **Step 2: 汇总 Stage B**

```bash
for d in graphrag_pipeline/results/qa_eval/runs/global-tuning-g3-combo-b-*; do
  echo "$d"
  jq '.per_mode["graphrag-global-search:latest"]' "$d/latency_breakdown.json"
  jq '.per_mode["graphrag-global-search:latest"]' "$d/rule_scoring.json"
done
```

预期：

- 如果 `success_count=16` 且 `timeout_like_error_count=0`，可进入人工质量观察。
- 如果仍触发 2 个 timeout，停止，不再扩大实验。

---

## Task 7: 回写实验记录

**Files:**

- Modify: `graphrag_pipeline/results/qa_eval/2026-05-14-global-search-tuning-experiment.md`

- [ ] **Step 1: 增加 `G3 组合优化` 小节**

写入内容：

```markdown
### 2026-05-15 G3 组合优化实验

| stage | group | run_id | executed | success | timeout_like | avg_s | p95_s | answer_chars | conclusion |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
```

- [ ] **Step 2: 写入结论**

结论格式：

```markdown
#### G3 组合优化结论

1. `C?` 是否通过 Stage A。
2. 若进入 Stage B，是否达到 `16/16`。
3. 是否推荐替换当前默认配置。
4. 若全部失败，下一步是否转向 dynamic community selection 更深参数、提高 timeout，或拆分 global_overview 专用策略。
```

- [ ] **Step 3: 校验文档中没有把 no-data fallback 当成功**

```bash
rg -n "unable to answer|no-data|75.0|fallback" \
  graphrag_pipeline/results/qa_eval/2026-05-14-global-search-tuning-experiment.md
```

预期：任何 fallback 都明确标为无效，不出现在候选收益结论里。

---

## Verification

实现 runner 改动后必须运行：

```bash
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  graphrag_pipeline/tests/test_qa_eval_global_cli_tuning.py \
  graphrag_pipeline/tests/test_qa_eval_latency_reporter.py
```

预期：全部通过。

每轮实验后必须运行：

```bash
git diff --exit-code -- graphrag_pipeline/settings.yaml
pgrep -af '[r]un_global_cli_tuning|[g]raphrag query --root . --method global' || true
git status --short --branch
```

预期：

- `settings.yaml` 无 diff
- 无残留调参进程
- 改动只出现在 `.claude/worktrees/student-qa-integration`

---

## Stop Conditions

立即停止实验并写入记录：

1. 任意组出现 2 个 timeout。
2. 任意组出现 `Arrearage / Access denied / overdue-payment`。
3. 任意组出现 no-data fallback。
4. Stage A 无候选达到 `success_count >= 5/6`。
5. `settings.yaml` 未恢复到运行前状态。

---

## Self-Review

- 覆盖上一轮结论：已覆盖 `G3`、输出压缩、dynamic community selection、压力题集。
- 避免重跑无效旧组：已把 Stage A 限定为压力题集，完整 B 规模只给幸存组。
- 避免污染：已要求 no-data fallback 计错，且保留 stop conditions。
- 本计划只作用于 `.claude/worktrees/student-qa-integration`，不触碰根工作区。
