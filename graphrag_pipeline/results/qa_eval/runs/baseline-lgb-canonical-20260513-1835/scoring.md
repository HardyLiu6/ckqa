# baseline-lgb-canonical-20260513-1835 scoring

## 规则层 - 模式总均值

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8481 | 0.9688 | 1.0000 | 0.0312 | 0.0000 | 3.1442 | 1475.2188 | 74.3391 | 0.0000 |
| graphrag-global-search:latest | 0.8575 | 0.8966 | 1.0000 | 0.1379 | 0.0172 | 2.9888 | 1448.2069 | 247.5643 | 3.0000 |
| graphrag-basic-search:latest | 0.8895 | 1.0000 | 1.0000 | 0.0625 | 0.0312 | 3.1841 | 1436.6562 | 49.8482 | 0.0000 |

## 规则层 - 按题型 x 模式

### factual_lookup

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.9062 | 0.8750 | 1.0000 | 0.0000 | 0.0000 | 5.2021 | 816.7500 | 52.9047 | 0.0000 |
| graphrag-global-search:latest | 0.9688 | 0.8750 | 1.0000 | 0.3750 | 0.0000 | 3.5960 | 1120.6250 | 151.5748 | 0.0000 |
| graphrag-basic-search:latest | 0.9688 | 1.0000 | 1.0000 | 0.2500 | 0.0000 | 3.6530 | 1165.1250 | 40.1508 | 0.0000 |

### relation_reasoning

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8750 | 1.0000 | 1.0000 | 0.1250 | 0.0000 | 2.4889 | 1558.0000 | 80.3818 | 0.0000 |
| graphrag-global-search:latest | 0.8187 | 0.8750 | 1.0000 | 0.0000 | 0.0000 | 2.5250 | 1700.8750 | 208.3977 | 0.0000 |
| graphrag-basic-search:latest | 1.0000 | 1.0000 | 1.0000 | 0.0000 | 0.1250 | 3.9202 | 1321.8750 | 50.5433 | 0.0000 |

### chapter_summary

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8375 | 1.0000 | 1.0000 | 0.0000 | 0.0000 | 2.4216 | 1773.8750 | 82.3630 | 0.0000 |
| graphrag-global-search:latest | 0.8542 | 1.0000 | 1.0000 | 0.0000 | 0.0625 | 3.1927 | 1414.2500 | 213.7783 | 0.0000 |
| graphrag-basic-search:latest | 0.8375 | 1.0000 | 1.0000 | 0.0000 | 0.0000 | 2.6844 | 1604.3750 | 51.6659 | 0.0000 |

### global_overview

| mode | entity_hit_rate | must_cite_hit | citation_format_present | negative_hit | length_score | info_density | answer_chars | elapsed_seconds | error_count |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| graphrag-local-search:latest | 0.7738 | 1.0000 | 1.0000 | 0.0000 | 0.0000 | 2.4642 | 1752.2500 | 81.7068 | 0.0000 |
| graphrag-global-search:latest | 0.7467 | 0.8000 | 1.0000 | 0.2000 | 0.0000 | 2.4330 | 1622.4000 | 416.5063 | 3.0000 |
| graphrag-basic-search:latest | 0.7518 | 1.0000 | 1.0000 | 0.0000 | 0.0000 | 2.4789 | 1655.2500 | 57.0329 | 0.0000 |

## 裁判层 - 模式总均值

| mode | semantic_correctness | faithfulness | retrieval_precision |
| --- | --- | --- | --- |
| graphrag-local-search:latest | 0.9531 | 0.3594 | 0.6219 |
| graphrag-global-search:latest | 0.9655 | 0.2759 | 0.7781 |
| graphrag-basic-search:latest | 0.9688 | 0.3281 | 0.6484 |

裁判模型：`deepseek-v4-flash`

## 裁判层 - 按题型 x 模式

### factual_lookup

| mode | semantic_correctness | faithfulness | retrieval_precision |
| --- | --- | --- | --- |
| graphrag-local-search:latest | 1.0000 | 0.4375 | 1.0000 |
| graphrag-global-search:latest | 0.9375 | 0.5000 | 1.0000 |
| graphrag-basic-search:latest | 1.0000 | 0.5000 | 1.0000 |

### relation_reasoning

| mode | semantic_correctness | faithfulness | retrieval_precision |
| --- | --- | --- | --- |
| graphrag-local-search:latest | 1.0000 | 0.4375 | 0.5417 |
| graphrag-global-search:latest | 1.0000 | 0.3750 | 0.7292 |
| graphrag-basic-search:latest | 1.0000 | 0.3750 | 0.7708 |

### chapter_summary

| mode | semantic_correctness | faithfulness | retrieval_precision |
| --- | --- | --- | --- |
| graphrag-local-search:latest | 0.8125 | 0.1875 | 0.4667 |
| graphrag-global-search:latest | 0.9375 | 0.0000 | 0.8625 |
| graphrag-basic-search:latest | 0.8750 | 0.1250 | 0.3542 |

### global_overview

| mode | semantic_correctness | faithfulness | retrieval_precision |
| --- | --- | --- | --- |
| graphrag-local-search:latest | 1.0000 | 0.3750 | 0.4792 |
| graphrag-global-search:latest | 1.0000 | 0.2000 | 0.5208 |
| graphrag-basic-search:latest | 1.0000 | 0.3125 | 0.4688 |

