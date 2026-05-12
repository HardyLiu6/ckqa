# baseline-diagnostic-lgb-20260512-2120 scoring

## 规则层 - 模式总均值

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8125 | 1.0000 | 1.0000 | 0.2500 | 0.0000 | 3.2045 | 1106.5000 | 36.4711 | 0.0000 |
| graphrag-global-search:latest | 0.9375 | 1.0000 | 1.0000 | 0.5000 | 0.0000 | 1.8765 | 2148.2500 | 120.7850 | 0.0000 |
| graphrag-basic-search:latest | 0.9375 | 1.0000 | 1.0000 | 0.5000 | 0.0000 | 4.5532 | 980.7500 | 31.7949 | 0.0000 |

## 规则层 - 按题型 x 模式

### factual_lookup

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8125 | 1.0000 | 1.0000 | 0.2500 | 0.0000 | 3.2045 | 1106.5000 | 36.4711 | 0.0000 |
| graphrag-global-search:latest | 0.9375 | 1.0000 | 1.0000 | 0.5000 | 0.0000 | 1.8765 | 2148.2500 | 120.7850 | 0.0000 |
| graphrag-basic-search:latest | 0.9375 | 1.0000 | 1.0000 | 0.5000 | 0.0000 | 4.5532 | 980.7500 | 31.7949 | 0.0000 |


_未发现 `judge_scoring.json`，本次报告只包含规则层评分。_
