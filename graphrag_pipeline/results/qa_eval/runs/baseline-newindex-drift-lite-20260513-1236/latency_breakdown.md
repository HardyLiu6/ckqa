# QA Baseline 耗时分解 - baseline-newindex-drift-lite-20260513-1236

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-drift-search:latest | 1 | 0 | 1 | 1 | 360.0834 | 0.0000 | 360.0834 | 360.0834 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q001 | factual_lookup | graphrag-drift-search:latest | 360.0834 | False | GraphRagApiError |
