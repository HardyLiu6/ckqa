# QA Baseline 耗时分解 - baseline-b2-global-tuned-20260512-2217

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-global-search:latest | 4 | 4 | 0 | 0 | 106.3997 | 106.3997 | 113.0781 | 113.0781 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q003 | factual_lookup | graphrag-global-search:latest | 113.0781 | True |  |
| Q002 | factual_lookup | graphrag-global-search:latest | 108.1324 | True |  |
| Q004 | factual_lookup | graphrag-global-search:latest | 105.8162 | True |  |
| Q001 | factual_lookup | graphrag-global-search:latest | 98.5720 | True |  |
