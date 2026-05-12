# QA Baseline 耗时分解 - baseline-b-global-tuned-20260512-2205

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-global-search:latest | 1 | 0 | 1 | 1 | 180.1164 | 0.0000 | 180.1164 | 180.1164 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q001 | factual_lookup | graphrag-global-search:latest | 180.1164 | False | GraphRagApiError |
