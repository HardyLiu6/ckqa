# QA Baseline 耗时分解 - baseline-diagnostic-lgb-20260512-2120

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-local-search:latest | 4 | 4 | 0 | 0 | 36.4711 | 36.4711 | 44.8259 | 44.8259 |
| graphrag-global-search:latest | 4 | 4 | 0 | 0 | 120.7850 | 120.7850 | 138.5347 | 138.5347 |
| graphrag-basic-search:latest | 4 | 4 | 0 | 0 | 31.7949 | 31.7949 | 56.4833 | 56.4833 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q004 | factual_lookup | graphrag-global-search:latest | 138.5347 | True |  |
| Q002 | factual_lookup | graphrag-global-search:latest | 134.1938 | True |  |
| Q001 | factual_lookup | graphrag-global-search:latest | 113.3942 | True |  |
| Q003 | factual_lookup | graphrag-global-search:latest | 97.0173 | True |  |
| Q004 | factual_lookup | graphrag-basic-search:latest | 56.4833 | True |  |
| Q004 | factual_lookup | graphrag-local-search:latest | 44.8259 | True |  |
| Q001 | factual_lookup | graphrag-local-search:latest | 42.0500 | True |  |
| Q002 | factual_lookup | graphrag-local-search:latest | 32.6146 | True |  |
| Q003 | factual_lookup | graphrag-basic-search:latest | 32.4275 | True |  |
| Q003 | factual_lookup | graphrag-local-search:latest | 26.3939 | True |  |
| Q002 | factual_lookup | graphrag-basic-search:latest | 19.4757 | True |  |
| Q001 | factual_lookup | graphrag-basic-search:latest | 18.7931 | True |  |
