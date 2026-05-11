# 归档：手工 JSON 抽取器相关代码

## 归档时间

2026-05-11

## 归档原因

桥接验证（`experiments/native_extraction_bridge/`）证明：

1. 手工 JSON 抽取器的容错解析会**掩盖 prompt 的真实格式缺陷**（引号包裹、括号失衡），导致实验结论无法安全迁移到生产 `graphrag index` 管线。
2. 切换到 GraphRAG 原生 `GraphExtractor`（tuple 格式 + gleaning）后，配合 `schema_fewshot_distilled_v2_strict_tuple` 的硬格式约束，audit 指标全面超越手工抽取器。
3. 原生抽取器与生产管线完全一致，消除了"实验好看/生产翻车"的迁移风险。

因此手工 JSON 抽取器的代码不再用于新实验，归档保留以便历史追溯。

## 归档内容

| 文件 | 原位置 | 用途 |
|---|---|---|
| `run_candidate_extraction.py` | `scripts/extraction_eval/` | 手工 JSON 抽取器主入口 |
| `extraction_parser.py` | `scripts/extraction_eval/` | JSON 容错解析器 |
| `output_guard.py` | `scripts/extraction_eval/` | JSON 输出守卫（识别非 JSON 输出） |
| `prompt_renderer.py` | `scripts/extraction_eval/` | 手工抽取器的 prompt 渲染（构造 system/user 消息） |
| `llm_client.py` | `scripts/extraction_eval/` | 手工抽取器的 OpenAI 兼容 LLM 客户端 |
| `diagnose_step8.py` | `scripts/extraction_eval/` | 旧版诊断脚本（已被 `diagnose_gold_missing_relations.py` 替代） |

根目录兼容入口（`scripts/*.py`）中对应的 shim 文件也一并归档到 `scripts/_archive_manual_json_extractor/`。

## 替代方案

- 抽取：`scripts/extraction_eval/run_native_extraction.py`（原生 GraphExtractor 适配）
- 诊断：`scripts/extraction_eval/diagnose_gold_missing_relations.py`
- 后处理：`scripts/extraction_eval/relationship_postprocessor.py`

## 如果需要恢复

把归档文件移回原位置即可。注意 `__init__.py` 里可能需要恢复 import（当前 `__init__.py` 不做显式 import，所以不受影响）。
