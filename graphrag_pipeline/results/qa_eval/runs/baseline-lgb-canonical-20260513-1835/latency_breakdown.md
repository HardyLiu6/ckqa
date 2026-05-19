# QA Baseline 耗时分解 - baseline-lgb-canonical-20260513-1835

## 按模式汇总

| mode | total | success | error | timeout_like | mean_s | success_mean_s | p95_s | max_s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| graphrag-local-search:latest | 32 | 32 | 0 | 0 | 74.3391 | 74.3391 | 124.6637 | 141.1964 |
| graphrag-global-search:latest | 32 | 29 | 3 | 3 | 247.5643 | 198.0049 | 726.5020 | 726.7218 |
| graphrag-basic-search:latest | 32 | 32 | 0 | 0 | 49.8482 | 49.8482 | 65.6641 | 76.9355 |

## 最慢请求 Top 20

| question_id | category | mode | elapsed_s | success | error_type |
| --- | --- | --- | ---: | --- | --- |
| Q026 | global_overview | graphrag-global-search:latest | 726.7218 | False | GraphRagApiError |
| Q029 | global_overview | graphrag-global-search:latest | 726.6905 | False | GraphRagApiError |
| Q025 | global_overview | graphrag-global-search:latest | 726.5020 | False | GraphRagApiError |
| Q030 | global_overview | graphrag-global-search:latest | 463.0325 | True |  |
| Q017 | chapter_summary | graphrag-global-search:latest | 449.5224 | True |  |
| Q013 | relation_reasoning | graphrag-global-search:latest | 433.1611 | True |  |
| Q027 | global_overview | graphrag-global-search:latest | 236.2601 | True |  |
| Q022 | chapter_summary | graphrag-global-search:latest | 220.1068 | True |  |
| Q010 | relation_reasoning | graphrag-global-search:latest | 217.2671 | True |  |
| Q028 | global_overview | graphrag-global-search:latest | 208.2706 | True |  |
| Q016 | relation_reasoning | graphrag-global-search:latest | 207.3249 | True |  |
| Q019 | chapter_summary | graphrag-global-search:latest | 200.5411 | True |  |
| Q023 | chapter_summary | graphrag-global-search:latest | 198.5569 | True |  |
| Q008 | factual_lookup | graphrag-global-search:latest | 178.8666 | True |  |
| Q018 | chapter_summary | graphrag-global-search:latest | 176.7730 | True |  |
| Q005 | factual_lookup | graphrag-global-search:latest | 173.1991 | True |  |
| Q020 | chapter_summary | graphrag-global-search:latest | 172.6028 | True |  |
| Q012 | relation_reasoning | graphrag-global-search:latest | 167.9432 | True |  |
| Q015 | relation_reasoning | graphrag-global-search:latest | 166.3909 | True |  |
| Q014 | relation_reasoning | graphrag-global-search:latest | 165.6937 | True |  |
