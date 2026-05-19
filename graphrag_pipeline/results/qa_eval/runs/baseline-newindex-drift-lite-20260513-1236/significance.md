# 四模式显著性对比

## 小样本警告

- WARNING: category=factual_lookup, mode=graphrag-drift-search:latest 只有 1 题，低于 15；该分层 CI 只能作探索性参考，不能作为上线判据。

> 当前实现使用普通百分位 bootstrap。若后续要比较很小的 route margin，需增加 BCa bootstrap 或扩大测试集后再下结论。

## effective_score_experimental

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## semantic_coverage_f1

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## citation_recall_at_3

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## citation_rr

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## citation_ndcg_at_5

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## rouge_lsum

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |

## keyword_recall

### 按题型 x 模式

| category | mode | ci_low | mean | ci_high |
| --- | --- | --- | --- | --- |
| factual_lookup | graphrag-drift-search:latest | 0.0000 | 0.0000 | 0.0000 |

### Pairwise

| mode_a | mode_b | mean_diff | ci_low | ci_high | win_rate |
| --- | --- | --- | --- | --- | --- |
