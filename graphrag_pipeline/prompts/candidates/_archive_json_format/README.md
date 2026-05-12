# 归档：JSON 输出格式候选 Prompt

## 归档时间

2026-05-11

## 归档原因

这些候选要求模型输出 JSON 格式（`{"entities": [...], "relationships": [...]}`），与 GraphRAG 原生 `GraphExtractor` 的 tuple 格式不兼容。切换到原生管线后，这些候选无法直接使用。

## 归档内容

- `default_guarded/`：在 default 基础上追加 JSON 根对象守卫
- `schema_fewshot_distilled_v3/`：低成本失败族守卫 + JSON 输出

## 如果需要恢复

移回 `prompts/candidates/` 即可。但注意它们只能配合手工 JSON 抽取器（`_archive_manual_json_extractor/run_candidate_extraction.py`）使用。
