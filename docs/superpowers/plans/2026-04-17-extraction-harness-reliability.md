# Extraction Harness 稳定性治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏步骤 8 评测输入契约的前提下，让 `graphrag_pipeline` 的候选 Prompt 抽取链路具备可观测性、流式能力和自动化重试/降载策略，避免用户反复手调 timeout。

**Architecture:** 保留现有 `scripts/run_candidate_extraction.py -> llm_client.py -> extraction_parser.py -> result_writer.py` 主链路，不重写调用栈。先把 LLM 调用结果从“只有 content 字符串”升级为“带 finish_reason / usage / request_mode / raw diagnostics 的结构化结果”，再在执行器层增加流式、空闲超时、长度截断自动重试和输出规模限制。步骤 8 继续读取现有统一结果 JSON，新增字段只作为可选调试信息。

**Tech Stack:** Python 3.10+, requests, Pydantic, pytest, one-api OpenAI 兼容接口, 阿里百炼 SSE 流式

---

## 文件结构与职责

- 修改: `graphrag_pipeline/scripts/llm_client.py`
  负责统一的 OpenAI 兼容调用；新增流式 SSE 读取、finish_reason/usage 收集、空闲超时、回环地址禁用代理。
- 修改: `graphrag_pipeline/scripts/run_candidate_extraction.py`
  负责 CLI、自动执行策略、失败重试、参数透传；新增 `--stream-mode`、`--idle-timeout`、自动重试和输出预算控制。
- 修改: `graphrag_pipeline/scripts/extraction_parser.py`
  负责 JSON 解析和容错；新增“长度截断/疑似未完成 JSON”识别，向上游返回更细的失败原因。
- 修改: `graphrag_pipeline/scripts/result_writer.py`
  负责结构化结果、raw、error 日志落盘；增加可选调试字段写入，不破坏现有结果格式。
- 可能修改: `graphrag_pipeline/scripts/extraction_schema.py`
  如果需要用 Pydantic 固化调试元信息，就在此新增可选模型字段。
- 修改: `graphrag_pipeline/tests/test_llm_client.py`
  新增同步/流式/usage/finish_reason/代理禁用/空闲超时相关测试。
- 修改: `graphrag_pipeline/tests/test_run_candidate_extraction.py`
  新增自动重试、长度截断回退、输出预算提示透传的端到端测试。
- 新增: `graphrag_pipeline/tests/test_extraction_parser.py`
  把“半截 JSON”“code fence JSON”“tuple 兜底”“长度截断标记”拆成独立测试，减少端到端测试复杂度。
- 修改: `graphrag_pipeline/README.md`
  补充步骤 7 的流式/自动重试参数说明与 one-api + 百炼使用建议。

---

### Task 1: 先补齐可观测性，不改默认行为

**Files:**
- Modify: `graphrag_pipeline/scripts/llm_client.py`
- Modify: `graphrag_pipeline/scripts/run_candidate_extraction.py`
- Modify: `graphrag_pipeline/tests/test_llm_client.py`
- Modify: `graphrag_pipeline/tests/test_run_candidate_extraction.py`

- [ ] **Step 1: 先写失败测试，固定新返回结构**

```python
def test_create_chat_completion_returns_finish_reason_and_usage():
    payload = {
        "choices": [
            {
                "message": {"content": "{\"entities\": [], \"relationships\": []}"},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 12, "completion_tokens": 34, "total_tokens": 46},
    }
    result = _parse_non_stream_response(payload, request_mode="sync")
    assert result.content == "{\"entities\": [], \"relationships\": []}"
    assert result.finish_reason == "stop"
    assert result.usage["total_tokens"] == 46
    assert result.request_mode == "sync"
```

- [ ] **Step 2: 跑测试确认当前代码不满足**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/test_llm_client.py -q
```

Expected: 至少有一条关于 `finish_reason` / `usage` / 返回对象结构的失败。

- [ ] **Step 3: 在 `llm_client.py` 引入结构化结果对象**

```python
@dataclass(frozen=True)
class LlmCompletionResult:
    content: str
    finish_reason: str | None
    usage: dict[str, Any] | None
    request_mode: str
    reasoning_seen: bool = False
    raw_chunks: int = 0
```

```python
def create_chat_completion(...) -> LlmCompletionResult:
    ...
```

- [ ] **Step 4: 在 `run_candidate_extraction.py` 消费新结果对象，但对步骤 8 结果文件仍只保留原有主字段**

```python
llm_result = client.create_chat_completion(...)
raw_output = llm_result.content
llm_debug = {
    "finish_reason": llm_result.finish_reason,
    "usage": llm_result.usage,
    "request_mode": llm_result.request_mode,
}
```

- [ ] **Step 5: 跑测试确认兼容旧路径**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  tests/test_llm_client.py \
  tests/test_run_candidate_extraction.py -q
```

Expected: 现有测试仍通过，新测试转绿。

---

### Task 2: 增加流式模式，但先作为可选能力

**Files:**
- Modify: `graphrag_pipeline/scripts/llm_client.py`
- Modify: `graphrag_pipeline/scripts/run_candidate_extraction.py`
- Modify: `graphrag_pipeline/tests/test_llm_client.py`

- [ ] **Step 1: 先写流式 SSE 单元测试**

```python
def test_streaming_chunks_only_join_delta_content():
    chunks = [
        'data: {"choices":[{"delta":{"reasoning_content":"思考"},"finish_reason":null}]}',
        'data: {"choices":[{"delta":{"content":"{"},"finish_reason":null}]}',
        'data: {"choices":[{"delta":{"content":"\\"entities\\": []"},"finish_reason":null}]}',
        'data: {"choices":[{"delta":{"content":"}"},"finish_reason":"stop"}]}',
        'data: {"choices":[],"usage":{"total_tokens":99}}',
        "data: [DONE]",
    ]
    result = _parse_stream_events(chunks)
    assert result.content == '{"entities": []}'
    assert result.finish_reason == "stop"
    assert result.reasoning_seen is True
```

- [ ] **Step 2: 在配置里增加流式开关与空闲超时**

```python
@dataclass(frozen=True)
class OpenAICompatibleLlmConfig:
    ...
    stream_mode: str = "off"
    idle_timeout_seconds: int = 30
```

- [ ] **Step 3: 在 `create_chat_completion` 中根据 `stream_mode` 走同步或 SSE**

```python
if self._config.stream_mode == "on":
    payload["stream"] = True
    payload["stream_options"] = {"include_usage": True}
    return self._post_streaming(...)
return self._post_sync(...)
```

- [ ] **Step 4: 流式读取时只拼接 `delta.content`，不把 `reasoning_content` 混进 JSON**

```python
for line in response.iter_lines(decode_unicode=True):
    if not line or not line.startswith("data: "):
        continue
    if line == "data: [DONE]":
        break
    event = json.loads(line[6:])
    delta = event["choices"][0].get("delta", {})
    if isinstance(delta.get("content"), str):
        parts.append(delta["content"])
```

- [ ] **Step 5: CLI 增加参数，但默认先保持 `off`**

```python
parser.add_argument(
    "--stream-mode",
    choices=["off", "on"],
    default="off",
    help="是否使用 SSE 流式调用 LLM；默认关闭，验证稳定后再考虑改默认值",
)
parser.add_argument("--idle-timeout", type=int, default=30, help="流式模式下的空闲超时（秒）")
```

- [ ] **Step 6: 跑单测确认同步路径未回归**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest tests/test_llm_client.py -q
```

Expected: 同步与流式两条路径都通过。

---

### Task 3: 识别“输出被截断”，不要再把它和普通 parse_error 混为一谈

**Files:**
- Modify: `graphrag_pipeline/scripts/extraction_parser.py`
- Modify: `graphrag_pipeline/scripts/result_writer.py`
- New: `graphrag_pipeline/tests/test_extraction_parser.py`

- [ ] **Step 1: 先写 parser 级失败测试**

```python
def test_parse_incomplete_json_reports_truncation():
    raw = '{"entities":[{"id":"e1","title":"A"}],"relationships":[{"source":"e1"'
    parsed = parse_extraction_output(raw)
    assert parsed.status == "parse_error"
    assert parsed.error_code == "truncated_json"
```

- [ ] **Step 2: 在 parser 里增加明确的错误分类**

```python
@dataclass(frozen=True)
class ParsedExtraction:
    entities: list[dict[str, Any]]
    relationships: list[dict[str, Any]]
    error_code: str | None = None
    error_detail: str | None = None
```

候选错误码最少包括：

```python
TRUNCATED_JSON = "truncated_json"
INVALID_JSON = "invalid_json"
EMPTY_OUTPUT = "empty_output"
```

- [ ] **Step 3: 用轻量规则识别明显截断**

```python
def _looks_truncated_json(text: str) -> bool:
    return text.count("{") > text.count("}") or text.rstrip().endswith((':', ',', '"'))
```

- [ ] **Step 4: 在 raw/error 日志中补充调试字段**

```python
record["llm_debug"] = {
    "finish_reason": llm_result.finish_reason,
    "usage": llm_result.usage,
    "request_mode": llm_result.request_mode,
    "parser_error_code": parsed.error_code,
}
```

- [ ] **Step 5: 跑解析与端到端测试**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  tests/test_extraction_parser.py \
  tests/test_run_candidate_extraction.py -q
```

Expected: `truncated_json` 可以稳定识别，旧结果结构不被破坏。

---

### Task 4: 把“调 timeout”变成系统自动策略

**Files:**
- Modify: `graphrag_pipeline/scripts/run_candidate_extraction.py`
- Modify: `graphrag_pipeline/scripts/prompt_renderer.py`
- Modify: `graphrag_pipeline/tests/test_run_candidate_extraction.py`

- [ ] **Step 1: 先写执行器策略测试**

```python
def test_retry_with_larger_budget_when_finish_reason_is_length():
    runner = ExtractionRunner(
        max_tokens=2000,
        retry_on_truncation=True,
        retry_max_tokens=4000,
    )
    first = LlmCompletionResult(content='{"entities":[', finish_reason="length", usage=None, request_mode="sync")
    second = LlmCompletionResult(content='{"entities":[],"relationships":[]}', finish_reason="stop", usage=None, request_mode="sync")
    ...
    assert result.status == "success"
    assert fake_client.calls == [2000, 4000]
```

- [ ] **Step 2: 在 CLI 中加入自动策略参数**

```python
parser.add_argument("--retry-on-truncation", action="store_true", help="遇到 length/truncated_json 时自动重试一次")
parser.add_argument("--retry-max-tokens", type=int, default=4000, help="自动重试时使用的 max_tokens")
parser.add_argument("--high-risk-timeout", type=int, default=240, help="高风险样本自动重试时使用的超时")
parser.add_argument("--max-entities", type=int, default=12, help="限制输出实体数量")
parser.add_argument("--max-relationships", type=int, default=12, help="限制输出关系数量")
```

- [ ] **Step 3: 给 prompt 渲染层加“输出预算提示”**

```python
budget_hint = (
    f"请只保留最核心的实体与关系，最多输出 {max_entities} 个实体、"
    f"{max_relationships} 条关系。优先稳定的课程主题、章节、知识点，忽略穷举型条目。"
)
```

- [ ] **Step 4: 自动重试顺序固定为一次，不做指数级复杂化**

```python
first_attempt = {"max_tokens": args.max_tokens, "timeout": args.timeout}
retry_attempt = {"max_tokens": args.retry_max_tokens, "timeout": args.high_risk_timeout}
```

触发条件只保留这两类：

```python
should_retry = llm_result.finish_reason == "length" or parsed.error_code == "truncated_json"
```

- [ ] **Step 5: 跑问题样本回归，确认“少调参、多自动”**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python scripts/run_candidate_extraction.py \
  --limit 5 \
  --overwrite \
  --stream-mode on \
  --retry-on-truncation \
  --retry-max-tokens 4000 \
  --high-risk-timeout 240 \
  --max-entities 12 \
  --max-relationships 12
```

Expected: 原本的 `llm_error`/`parse_error` 应明显下降，至少不再需要人工多次改 timeout 重跑。

---

### Task 5: 决定默认值与交付文档

**Files:**
- Modify: `graphrag_pipeline/README.md`
- Modify: `graphrag_pipeline/scripts/run_candidate_extraction.py`

- [ ] **Step 1: 先用真实链路做小样本 A/B**

对比两组：

```bash
# A 组：旧模式
python scripts/run_candidate_extraction.py --limit 5 --overwrite

# B 组：新模式
python scripts/run_candidate_extraction.py \
  --limit 5 \
  --overwrite \
  --stream-mode on \
  --retry-on-truncation \
  --retry-max-tokens 4000 \
  --high-risk-timeout 240 \
  --max-entities 12 \
  --max-relationships 12
```

- [ ] **Step 2: 只有在 B 组明显更稳时，才考虑把默认值从 `stream_mode=off` 调成 `on`**

```python
default_stream_mode = "off"  # 第一轮实现保持保守
```

- [ ] **Step 3: README 补充最小使用说明**

```markdown
### 步骤 7 稳定运行建议

- one-api + 百炼推荐开启 `--stream-mode on`
- 如需减少长样本截断，建议同时开启 `--retry-on-truncation`
- 若候选 Prompt 输出过多，可通过 `--max-entities` / `--max-relationships` 限制规模
```

- [ ] **Step 4: 跑最终回归并记录结果**

Run:

```bash
cd /home/sunlight/Projects/ckqa/graphrag_pipeline
/home/sunlight/miniconda3/envs/graphrag-oneapi/bin/python -m pytest \
  tests/test_llm_client.py \
  tests/test_extraction_parser.py \
  tests/test_run_candidate_extraction.py -q
```

Expected: 新增测试全部通过；README 参数说明与代码一致。

---

## 建议采用顺序

1. 先做 **Task 1 + Task 3**
   先解决“看不清问题”的状态，把 `finish_reason`、`usage`、`truncated_json` 分开。
2. 再做 **Task 2**
   把 one-api + 百炼流式能力接进 harness，但先通过开关启用，不一次性切默认。
3. 最后做 **Task 4 + Task 5**
   用自动重试和输出预算替代人工调 timeout，并在 A/B 后决定默认值。

## 非目标

- 不重写整套步骤 7 架构
- 不接入异步任务队列
- 不改步骤 8 的输入契约
- 不引入新的外部依赖或复杂状态机

## 采纳标准

- 用户不需要反复手调 `--timeout`
- 能区分 `timeout`、`length`、`truncated_json`、普通 `invalid_json`
- 问题样本 `pts-0004-ac3447c62d` / `pts-0005-a0753fd9ff` 的成功率或至少可诊断性显著提高
- `results/extraction_eval/*.json` 仍可直接给步骤 8 读取
