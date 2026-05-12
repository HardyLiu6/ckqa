# QA Baseline 耗时分解 - baseline-b3-drift-tuned-20260512-2233

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-drift-search:latest | 1 | 0 | 1 | 1 | 90.0906 | 0.0000 | 90.0906 | 90.0906 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q001 | factual_lookup | graphrag-drift-search:latest | 90.0906 | False | GraphRagApiError |
