# 四模式显著性对比

## 小样本警告

- WARNING: category=chapter_summary, mode=graphrag-basic-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=chapter_summary, mode=graphrag-global-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=chapter_summary, mode=graphrag-local-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=factual_lookup, mode=graphrag-basic-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=factual_lookup, mode=graphrag-global-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=factual_lookup, mode=graphrag-local-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=global_overview, mode=graphrag-basic-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=global_overview, mode=graphrag-global-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=global_overview, mode=graphrag-local-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=relation_reasoning, mode=graphrag-basic-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=relation_reasoning, mode=graphrag-global-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。
- WARNING: category=relation_reasoning, mode=graphrag-local-search:latest 只有 8 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。

> 当前实现使用普通百分位 bootstrap。若后续要比较很小的 route margin，需增加 BCa bootstrap 或扩大测试集后再下结论。

## effective_score_experimental

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.6073 | 0.6630 | 0.7195 |
| chapter_summary | graphrag-global-search:latest | 0.4899 | 0.5314 | 0.5790 |
| chapter_summary | graphrag-local-search:latest | 0.5170 | 0.5739 | 0.6265 |
| factual_lookup | graphrag-basic-search:latest | 0.8082 | 0.8526 | 0.8920 |
| factual_lookup | graphrag-global-search:latest | 0.4773 | 0.5248 | 0.5705 |
| factual_lookup | graphrag-local-search:latest | 0.5824 | 0.6490 | 0.7272 |
| global_overview | graphrag-basic-search:latest | 0.5965 | 0.6462 | 0.7057 |
| global_overview | graphrag-global-search:latest | 0.1301 | 0.2959 | 0.4594 |
| global_overview | graphrag-local-search:latest | 0.5530 | 0.5967 | 0.6407 |
| relation_reasoning | graphrag-basic-search:latest | 0.7959 | 0.8382 | 0.8796 |
| relation_reasoning | graphrag-global-search:latest | 0.3969 | 0.4836 | 0.5368 |
| relation_reasoning | graphrag-local-search:latest | 0.5996 | 0.6698 | 0.7497 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.2911 | 0.2278 | 0.3583 | 1.0000 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.1276 | 0.0894 | 0.1674 | 1.0000 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.1634 | -0.2229 | -0.1081 | 0.0000 |

## semantic_coverage_f1

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.9546 | 0.9790 | 1.0000 |
| chapter_summary | graphrag-global-search:latest | 0.9011 | 0.9433 | 0.9821 |
| chapter_summary | graphrag-local-search:latest | 0.8755 | 0.9437 | 0.9917 |
| factual_lookup | graphrag-basic-search:latest | 0.9750 | 0.9917 | 1.0000 |
| factual_lookup | graphrag-global-search:latest | 0.9519 | 0.9808 | 1.0000 |
| factual_lookup | graphrag-local-search:latest | 1.0000 | 1.0000 | 1.0000 |
| global_overview | graphrag-basic-search:latest | 0.9712 | 0.9904 | 1.0000 |
| global_overview | graphrag-global-search:latest | 0.2500 | 0.5933 | 0.8654 |
| global_overview | graphrag-local-search:latest | 1.0000 | 1.0000 | 1.0000 |
| relation_reasoning | graphrag-basic-search:latest | 1.0000 | 1.0000 | 1.0000 |
| relation_reasoning | graphrag-global-search:latest | 0.8439 | 0.9387 | 1.0000 |
| relation_reasoning | graphrag-local-search:latest | 0.9712 | 0.9904 | 1.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.1262 | 0.0362 | 0.2385 | 1.0000 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.0068 | -0.0102 | 0.0293 | 0.7385 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.1195 | -0.2327 | -0.0249 | 0.0005 |

## citation_recall_at_3

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.0833 | 0.2458 | 0.4208 |
| chapter_summary | graphrag-global-search:latest | 0.0000 | 0.0875 | 0.2125 |
| chapter_summary | graphrag-local-search:latest | 0.0000 | 0.0667 | 0.1500 |
| factual_lookup | graphrag-basic-search:latest | 1.0000 | 1.0000 | 1.0000 |
| factual_lookup | graphrag-global-search:latest | 0.0000 | 0.1250 | 0.3750 |
| factual_lookup | graphrag-local-search:latest | 0.0000 | 0.2500 | 0.6250 |
| global_overview | graphrag-basic-search:latest | 0.0625 | 0.1979 | 0.3750 |
| global_overview | graphrag-global-search:latest | 0.0000 | 0.0000 | 0.0000 |
| global_overview | graphrag-local-search:latest | 0.0312 | 0.1042 | 0.1979 |
| relation_reasoning | graphrag-basic-search:latest | 0.5416 | 0.7292 | 0.9375 |
| relation_reasoning | graphrag-global-search:latest | 0.0000 | 0.0000 | 0.0000 |
| relation_reasoning | graphrag-local-search:latest | 0.1667 | 0.3750 | 0.6042 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.4901 | 0.3442 | 0.6349 | 1.0000 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.3443 | 0.1932 | 0.4907 | 1.0000 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.1458 | -0.2828 | -0.0130 | 0.0150 |

## citation_rr

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.2083 | 0.4896 | 0.7812 |
| chapter_summary | graphrag-global-search:latest | 0.0626 | 0.1759 | 0.3287 |
| chapter_summary | graphrag-local-search:latest | 0.0764 | 0.2639 | 0.5105 |
| factual_lookup | graphrag-basic-search:latest | 0.6875 | 0.8750 | 1.0000 |
| factual_lookup | graphrag-global-search:latest | 0.0428 | 0.0973 | 0.1820 |
| factual_lookup | graphrag-local-search:latest | 0.1216 | 0.2110 | 0.3129 |
| global_overview | graphrag-basic-search:latest | 0.2263 | 0.4868 | 0.7917 |
| global_overview | graphrag-global-search:latest | 0.0067 | 0.0391 | 0.0864 |
| global_overview | graphrag-local-search:latest | 0.0806 | 0.2744 | 0.5104 |
| relation_reasoning | graphrag-basic-search:latest | 0.8125 | 0.9375 | 1.0000 |
| relation_reasoning | graphrag-global-search:latest | 0.0178 | 0.0316 | 0.0456 |
| relation_reasoning | graphrag-local-search:latest | 0.2396 | 0.5104 | 0.7812 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.6112 | 0.4633 | 0.7508 | 1.0000 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.3823 | 0.2479 | 0.5136 | 1.0000 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.2289 | -0.3607 | -0.1028 | 0.0000 |

## citation_ndcg_at_5

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.1220 | 0.3258 | 0.5320 |
| chapter_summary | graphrag-global-search:latest | 0.0000 | 0.0751 | 0.1770 |
| chapter_summary | graphrag-local-search:latest | 0.0424 | 0.1709 | 0.3398 |
| factual_lookup | graphrag-basic-search:latest | 0.8006 | 0.9155 | 1.0000 |
| factual_lookup | graphrag-global-search:latest | 0.0000 | 0.0625 | 0.1875 |
| factual_lookup | graphrag-local-search:latest | 0.0627 | 0.2524 | 0.4263 |
| global_overview | graphrag-basic-search:latest | 0.1173 | 0.2800 | 0.4601 |
| global_overview | graphrag-global-search:latest | 0.0000 | 0.0189 | 0.0566 |
| global_overview | graphrag-local-search:latest | 0.0370 | 0.1355 | 0.2340 |
| relation_reasoning | graphrag-basic-search:latest | 0.5769 | 0.7503 | 0.9136 |
| relation_reasoning | graphrag-global-search:latest | 0.0000 | 0.0000 | 0.0000 |
| relation_reasoning | graphrag-local-search:latest | 0.2170 | 0.4529 | 0.6828 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.5288 | 0.3891 | 0.6595 | 1.0000 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.3150 | 0.2005 | 0.4292 | 1.0000 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.2138 | -0.3276 | -0.0999 | 0.0000 |

## rouge_lsum

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.0477 | 0.0579 | 0.0677 |
| chapter_summary | graphrag-global-search:latest | 0.0554 | 0.0599 | 0.0648 |
| chapter_summary | graphrag-local-search:latest | 0.0433 | 0.0522 | 0.0620 |
| factual_lookup | graphrag-basic-search:latest | 0.0575 | 0.0783 | 0.1005 |
| factual_lookup | graphrag-global-search:latest | 0.0531 | 0.0637 | 0.0730 |
| factual_lookup | graphrag-local-search:latest | 0.0739 | 0.0923 | 0.1116 |
| global_overview | graphrag-basic-search:latest | 0.0509 | 0.0578 | 0.0635 |
| global_overview | graphrag-global-search:latest | 0.0169 | 0.0372 | 0.0563 |
| global_overview | graphrag-local-search:latest | 0.0508 | 0.0544 | 0.0599 |
| relation_reasoning | graphrag-basic-search:latest | 0.0611 | 0.0766 | 0.0959 |
| relation_reasoning | graphrag-global-search:latest | 0.0414 | 0.0601 | 0.0726 |
| relation_reasoning | graphrag-local-search:latest | 0.0600 | 0.0728 | 0.0943 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.0124 | 0.0036 | 0.0223 | 0.9980 |
| graphrag-basic-search:latest | graphrag-local-search:latest | -0.0003 | -0.0087 | 0.0085 | 0.4790 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.0127 | -0.0259 | -0.0012 | 0.0125 |

## keyword_recall

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| chapter_summary | graphrag-basic-search:latest | 0.7812 | 0.8333 | 0.8958 |
| chapter_summary | graphrag-global-search:latest | 0.7500 | 0.8333 | 0.9062 |
| chapter_summary | graphrag-local-search:latest | 0.7604 | 0.8229 | 0.8958 |
| factual_lookup | graphrag-basic-search:latest | 0.9236 | 0.9549 | 0.9896 |
| factual_lookup | graphrag-global-search:latest | 0.8263 | 0.8715 | 0.9132 |
| factual_lookup | graphrag-local-search:latest | 0.7014 | 0.8264 | 0.9375 |
| global_overview | graphrag-basic-search:latest | 0.7917 | 0.8333 | 0.8750 |
| global_overview | graphrag-global-search:latest | 0.2188 | 0.5208 | 0.7604 |
| global_overview | graphrag-local-search:latest | 0.7083 | 0.8021 | 0.8854 |
| relation_reasoning | graphrag-basic-search:latest | 0.7708 | 0.8854 | 0.9792 |
| relation_reasoning | graphrag-global-search:latest | 0.5000 | 0.7188 | 0.8545 |
| relation_reasoning | graphrag-local-search:latest | 0.7292 | 0.8125 | 0.8958 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
| graphrag-basic-search:latest | graphrag-global-search:latest | 0.1406 | 0.0443 | 0.2552 | 0.9980 |
| graphrag-basic-search:latest | graphrag-local-search:latest | 0.0608 | 0.0139 | 0.1076 | 0.9950 |
| graphrag-global-search:latest | graphrag-local-search:latest | -0.0799 | -0.1944 | 0.0182 | 0.0635 |
