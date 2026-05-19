# BM25 Retrieval Bakeoff

## 数据来源

- test set: `graphrag_pipeline/data/eval/qa_test_set.jsonl`
- text units: `graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047/text_units.parquet`
- questions: `32`
- THUOCL source: `https://github.com/thunlp/THUOCL`
- THUOCL cache: `graphrag_pipeline/.cache/thuocl/THUOCL_IT.txt`
- course terms: `300`
- filtered THUOCL IT terms: `1497`
- gold context expansion: `enabled`
- section-aware heading weight: `4`
- dense rerank: `enabled`
- dense rerank candidate pool: `20`
- dense rerank model: `/home/sunlight/.cache/huggingface/hub/models--BAAI--bge-m3/snapshots/5617a9f61b028005a4858fdac845db406aefb181`
- dense rerank device: `cuda`
- noise audit findings: `7`

## best config

- config: `jieba_course_terms_thuocl_multi_rrf_filtered_section_aware_dense_rerank_top20`
- k1/b: `1.5` / `0.9`
- recall_at_3: `0.5964`
- expanded_recall_at_3: `0.2809`
- rr: `0.7158`
- expanded_rr: `0.9219`
- ndcg_at_5: `0.6087`
- expanded_ndcg_at_5: `0.6209`

## baseline config

- config: `jieba_baseline`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.3833`
- expanded_recall_at_3: `0.1825`
- rr: `0.4982`
- expanded_rr: `0.643`
- ndcg_at_5: `0.402`
- expanded_ndcg_at_5: `0.4589`

## 结论

- recall_at_3 delta vs baseline: `+0.2131`
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。

## overall scores

| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 | expanded r@3 | expanded rr |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware_dense_rerank_top20 | 1.50 | 0.90 | 0.3302 | 0.5964 | 0.6406 | 0.7484 | 0.7158 | 0.6087 | 0.2809 | 0.9219 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2849 | 0.5646 | 0.6760 | 0.7708 | 0.6689 | 0.5977 | 0.2689 | 0.8141 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.75 | 0.3083 | 0.5568 | 0.6318 | 0.7536 | 0.6829 | 0.5929 | 0.2589 | 0.8474 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.5568 | 0.6318 | 0.7536 | 0.6498 | 0.5693 | 0.2589 | 0.8161 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2380 | 0.5411 | 0.6484 | 0.7849 | 0.6524 | 0.5645 | 0.2650 | 0.7920 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.75 | 0.2927 | 0.5307 | 0.6630 | 0.7536 | 0.6842 | 0.5932 | 0.2589 | 0.8505 |
| jieba_course_terms_filtered | 1.20 | 0.90 | 0.2771 | 0.5266 | 0.6260 | 0.7365 | 0.7102 | 0.5811 | 0.2328 | 0.8380 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.90 | 0.3240 | 0.5255 | 0.6552 | 0.7224 | 0.6941 | 0.6064 | 0.2355 | 0.8315 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.90 | 0.3083 | 0.5255 | 0.6161 | 0.7536 | 0.6758 | 0.5817 | 0.2381 | 0.8135 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.90 | 0.2823 | 0.5255 | 0.5953 | 0.7474 | 0.6719 | 0.5544 | 0.2339 | 0.8097 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2927 | 0.5229 | 0.6682 | 0.7630 | 0.6803 | 0.5930 | 0.2571 | 0.8293 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.75 | 0.2771 | 0.5214 | 0.6911 | 0.7755 | 0.6738 | 0.5996 | 0.2509 | 0.8531 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.90 | 0.2667 | 0.5151 | 0.6146 | 0.7771 | 0.6971 | 0.5597 | 0.2322 | 0.8068 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.90 | 0.2927 | 0.5151 | 0.6005 | 0.7130 | 0.6910 | 0.5675 | 0.2300 | 0.8344 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.75 | 0.2823 | 0.5151 | 0.6005 | 0.7370 | 0.6730 | 0.5506 | 0.2300 | 0.8084 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.90 | 0.2823 | 0.5151 | 0.6031 | 0.6953 | 0.6625 | 0.5506 | 0.2417 | 0.8109 |
| jieba_course_terms_filtered | 1.50 | 0.90 | 0.2771 | 0.5109 | 0.6573 | 0.7286 | 0.7139 | 0.5974 | 0.2419 | 0.8422 |
| jieba_course_terms_filtered | 0.90 | 0.90 | 0.2771 | 0.5109 | 0.6104 | 0.7365 | 0.7017 | 0.5707 | 0.2275 | 0.8068 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.5109 | 0.6495 | 0.7365 | 0.6565 | 0.5642 | 0.2509 | 0.8516 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.90 | 0.3240 | 0.5099 | 0.6865 | 0.7365 | 0.6925 | 0.6169 | 0.2418 | 0.8354 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2693 | 0.5099 | 0.5693 | 0.7604 | 0.6808 | 0.5411 | 0.2275 | 0.8218 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.75 | 0.2615 | 0.5073 | 0.6005 | 0.7224 | 0.6678 | 0.5533 | 0.2224 | 0.8451 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.90 | 0.2849 | 0.5057 | 0.6495 | 0.7521 | 0.6849 | 0.5847 | 0.2275 | 0.8109 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.90 | 0.2849 | 0.5057 | 0.6964 | 0.7443 | 0.6847 | 0.6069 | 0.2319 | 0.8354 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.90 | 0.2849 | 0.5057 | 0.6651 | 0.7521 | 0.6811 | 0.5917 | 0.2275 | 0.8172 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.5057 | 0.6536 | 0.7599 | 0.6417 | 0.5645 | 0.2196 | 0.8073 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.75 | 0.2536 | 0.5047 | 0.6240 | 0.7484 | 0.6680 | 0.5479 | 0.2400 | 0.8049 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.90 | 0.2562 | 0.5047 | 0.6016 | 0.7250 | 0.6661 | 0.5462 | 0.2478 | 0.8073 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2719 | 0.5047 | 0.6370 | 0.7318 | 0.6482 | 0.5663 | 0.2418 | 0.8295 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2849 | 0.5021 | 0.6318 | 0.7474 | 0.6661 | 0.5671 | 0.2311 | 0.8109 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2380 | 0.5021 | 0.6510 | 0.7771 | 0.6448 | 0.5550 | 0.2275 | 0.7979 |
| jieba_course_terms | 1.20 | 0.90 | 0.2432 | 0.5005 | 0.5948 | 0.7286 | 0.6500 | 0.5348 | 0.2163 | 0.7446 |
| jieba_course_terms | 0.90 | 0.90 | 0.2432 | 0.5005 | 0.5885 | 0.6974 | 0.6405 | 0.5280 | 0.1942 | 0.7263 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.5005 | 0.6354 | 0.7365 | 0.6385 | 0.5489 | 0.2481 | 0.8083 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.90 | 0.2927 | 0.4995 | 0.6318 | 0.7130 | 0.6946 | 0.5838 | 0.2391 | 0.8385 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.90 | 0.2927 | 0.4995 | 0.5823 | 0.7224 | 0.6829 | 0.5536 | 0.2248 | 0.8050 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.4995 | 0.6057 | 0.7146 | 0.6396 | 0.5393 | 0.2277 | 0.8307 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.4995 | 0.6292 | 0.7083 | 0.6354 | 0.5500 | 0.2249 | 0.8146 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.75 | 0.2615 | 0.4969 | 0.5823 | 0.7224 | 0.6608 | 0.5391 | 0.2142 | 0.8083 |
| jieba_course_terms | 1.50 | 0.90 | 0.2432 | 0.4953 | 0.5948 | 0.7286 | 0.6552 | 0.5396 | 0.2153 | 0.7440 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.75 | 0.2667 | 0.4943 | 0.6146 | 0.7589 | 0.6954 | 0.5563 | 0.2254 | 0.8037 |
| jieba_course_terms_filtered | 1.20 | 0.55 | 0.2771 | 0.4927 | 0.6078 | 0.7365 | 0.6974 | 0.5560 | 0.2140 | 0.8266 |
| jieba_course_terms | 1.20 | 0.55 | 0.2406 | 0.4927 | 0.5417 | 0.7208 | 0.6350 | 0.4962 | 0.1929 | 0.7378 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2432 | 0.4917 | 0.6474 | 0.7875 | 0.6576 | 0.5538 | 0.2408 | 0.8167 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.2458 | 0.4917 | 0.6198 | 0.7771 | 0.6565 | 0.5424 | 0.2510 | 0.8133 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2354 | 0.4917 | 0.6719 | 0.7771 | 0.6477 | 0.5668 | 0.2507 | 0.8044 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.75 | 0.2771 | 0.4901 | 0.6599 | 0.7599 | 0.6570 | 0.5802 | 0.2157 | 0.8089 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4891 | 0.6031 | 0.7318 | 0.6346 | 0.5387 | 0.2209 | 0.8146 |
| jieba_course_terms_filtered | 1.50 | 0.75 | 0.2771 | 0.4849 | 0.6651 | 0.7443 | 0.7102 | 0.5866 | 0.2244 | 0.8552 |
| jieba_course_terms | 1.50 | 0.75 | 0.2432 | 0.4849 | 0.5792 | 0.7208 | 0.6448 | 0.5206 | 0.2046 | 0.7596 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.75 | 0.2901 | 0.4839 | 0.6083 | 0.7370 | 0.6840 | 0.5585 | 0.2105 | 0.8092 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.90 | 0.2823 | 0.4839 | 0.6083 | 0.7370 | 0.6701 | 0.5585 | 0.2183 | 0.8102 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.55 | 0.2589 | 0.4839 | 0.6057 | 0.7292 | 0.6690 | 0.5445 | 0.2222 | 0.8068 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2536 | 0.4839 | 0.5693 | 0.7760 | 0.6655 | 0.5250 | 0.2194 | 0.8219 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2667 | 0.4839 | 0.6250 | 0.7771 | 0.6604 | 0.5587 | 0.2354 | 0.8349 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.75 | 0.2797 | 0.4839 | 0.6188 | 0.6953 | 0.6578 | 0.5510 | 0.2105 | 0.8068 |
| jieba_course_terms_filtered | 0.90 | 0.75 | 0.2771 | 0.4823 | 0.6042 | 0.7443 | 0.6877 | 0.5527 | 0.2122 | 0.8111 |
| jieba_course_terms | 0.90 | 0.75 | 0.2432 | 0.4823 | 0.5677 | 0.6896 | 0.6279 | 0.5079 | 0.1940 | 0.7307 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.3005 | 0.4812 | 0.6474 | 0.7630 | 0.6924 | 0.5817 | 0.2233 | 0.8242 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2797 | 0.4812 | 0.6526 | 0.7318 | 0.6549 | 0.5708 | 0.2131 | 0.8195 |
| jieba_course_terms_thuocl | 1.20 | 0.90 | 0.2589 | 0.4786 | 0.5927 | 0.6990 | 0.6310 | 0.5335 | 0.2100 | 0.7411 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.55 | 0.2615 | 0.4760 | 0.5979 | 0.7458 | 0.6692 | 0.5424 | 0.2183 | 0.8436 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.55 | 0.2745 | 0.4734 | 0.5953 | 0.7328 | 0.6967 | 0.5422 | 0.2176 | 0.8181 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.90 | 0.2667 | 0.4734 | 0.6589 | 0.7667 | 0.6949 | 0.5773 | 0.2166 | 0.8070 |
| jieba_course_terms_thuocl | 1.50 | 0.90 | 0.2589 | 0.4734 | 0.5927 | 0.6740 | 0.6336 | 0.5382 | 0.2089 | 0.7380 |
| jieba_course_terms_filtered | 1.50 | 0.55 | 0.2771 | 0.4719 | 0.6000 | 0.7208 | 0.6972 | 0.5522 | 0.2192 | 0.8438 |
| jieba_course_terms | 1.50 | 0.55 | 0.2328 | 0.4719 | 0.5521 | 0.6818 | 0.6159 | 0.4962 | 0.2072 | 0.7240 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2589 | 0.4708 | 0.5693 | 0.7370 | 0.6566 | 0.5294 | 0.2170 | 0.8173 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.55 | 0.2615 | 0.4708 | 0.5641 | 0.7146 | 0.6532 | 0.5249 | 0.2105 | 0.8076 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2484 | 0.4708 | 0.5745 | 0.7370 | 0.6357 | 0.5205 | 0.2131 | 0.8111 |
| jieba_course_terms_thuocl | 0.90 | 0.75 | 0.2276 | 0.4708 | 0.5615 | 0.6911 | 0.6016 | 0.5000 | 0.1905 | 0.7292 |
| jieba_course_terms_filtered | 1.20 | 0.75 | 0.2771 | 0.4693 | 0.6026 | 0.7443 | 0.6933 | 0.5549 | 0.2218 | 0.8345 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4693 | 0.6250 | 0.7599 | 0.6414 | 0.5464 | 0.2413 | 0.8135 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.75 | 0.2615 | 0.4682 | 0.6318 | 0.7224 | 0.6764 | 0.5668 | 0.2237 | 0.8474 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2432 | 0.4682 | 0.5901 | 0.7422 | 0.6368 | 0.5213 | 0.2122 | 0.8138 |
| jieba_course_terms_filtered | 0.90 | 0.55 | 0.2771 | 0.4667 | 0.5911 | 0.7365 | 0.6857 | 0.5412 | 0.2085 | 0.8109 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.55 | 0.2615 | 0.4656 | 0.5901 | 0.7146 | 0.6627 | 0.5401 | 0.2157 | 0.8222 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.75 | 0.2667 | 0.4630 | 0.5937 | 0.7589 | 0.6891 | 0.5413 | 0.2166 | 0.8185 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.55 | 0.2484 | 0.4630 | 0.5849 | 0.6797 | 0.6354 | 0.5225 | 0.2048 | 0.7941 |
| jieba_course_terms_thuocl | 0.90 | 0.90 | 0.2589 | 0.4630 | 0.5823 | 0.6677 | 0.6204 | 0.5237 | 0.1879 | 0.7234 |
| jieba_course_terms_thuocl | 1.20 | 0.75 | 0.2276 | 0.4630 | 0.5927 | 0.6911 | 0.6070 | 0.5183 | 0.1957 | 0.7385 |
| jieba_course_terms | 0.90 | 0.55 | 0.2302 | 0.4604 | 0.5156 | 0.7130 | 0.6085 | 0.4740 | 0.1848 | 0.7170 |
| jieba_course_terms_thuocl | 1.50 | 0.55 | 0.2276 | 0.4604 | 0.5563 | 0.6755 | 0.6016 | 0.4955 | 0.2037 | 0.7380 |
| jieba_course_terms | 1.20 | 0.75 | 0.2432 | 0.4589 | 0.5729 | 0.7208 | 0.6338 | 0.5112 | 0.1901 | 0.7410 |
| jieba_course_terms_thuocl | 0.90 | 0.55 | 0.2250 | 0.4552 | 0.5536 | 0.6693 | 0.5888 | 0.4910 | 0.1840 | 0.7260 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2432 | 0.4526 | 0.5927 | 0.7917 | 0.6466 | 0.5296 | 0.2116 | 0.8218 |
| jieba_course_terms_thuocl | 1.20 | 0.55 | 0.2354 | 0.4500 | 0.5719 | 0.6833 | 0.6148 | 0.5064 | 0.1868 | 0.7484 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.55 | 0.2641 | 0.4422 | 0.6057 | 0.7161 | 0.6706 | 0.5402 | 0.2059 | 0.8008 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.55 | 0.2484 | 0.4422 | 0.6057 | 0.7188 | 0.6464 | 0.5354 | 0.2076 | 0.8071 |
| jieba_course_terms_thuocl | 1.50 | 0.75 | 0.2276 | 0.4422 | 0.5927 | 0.6599 | 0.6102 | 0.5191 | 0.1985 | 0.7510 |
| jieba_thuocl_it_filtered | 0.90 | 0.90 | 0.2484 | 0.4422 | 0.5563 | 0.6729 | 0.6020 | 0.5030 | 0.1915 | 0.7130 |
| jieba_thuocl_it_filtered | 0.90 | 0.75 | 0.1755 | 0.4396 | 0.5354 | 0.6667 | 0.5352 | 0.4523 | 0.1850 | 0.7120 |
| jieba_thuocl_it_filtered | 1.20 | 0.55 | 0.1755 | 0.4344 | 0.5250 | 0.6510 | 0.5203 | 0.4378 | 0.1840 | 0.7266 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.55 | 0.2536 | 0.4318 | 0.5745 | 0.7031 | 0.6397 | 0.5126 | 0.2059 | 0.7860 |
| jieba_thuocl_it_filtered | 1.20 | 0.90 | 0.2510 | 0.4266 | 0.5458 | 0.7042 | 0.6019 | 0.4951 | 0.2072 | 0.7568 |
| jieba_thuocl_it_filtered | 1.20 | 0.75 | 0.1885 | 0.4240 | 0.5458 | 0.6729 | 0.5588 | 0.4677 | 0.1955 | 0.7354 |
| jieba_thuocl_it_filtered | 1.50 | 0.90 | 0.2510 | 0.4214 | 0.5380 | 0.6755 | 0.5967 | 0.4925 | 0.2061 | 0.7589 |
| jieba_thuocl_it_filtered | 1.50 | 0.55 | 0.1859 | 0.4135 | 0.5198 | 0.6510 | 0.5310 | 0.4387 | 0.1983 | 0.7422 |
| jieba_thuocl_it_filtered | 1.50 | 0.75 | 0.2198 | 0.4109 | 0.5458 | 0.6807 | 0.5807 | 0.4778 | 0.1957 | 0.7542 |
| jieba_baseline | 0.90 | 0.90 | 0.1755 | 0.3937 | 0.4661 | 0.6479 | 0.5279 | 0.4162 | 0.1731 | 0.6382 |
| jieba_thuocl_it_filtered | 0.90 | 0.55 | 0.1755 | 0.3927 | 0.5354 | 0.6406 | 0.5091 | 0.4383 | 0.1772 | 0.6948 |
| jieba_baseline | 0.90 | 0.75 | 0.1339 | 0.3901 | 0.4453 | 0.6417 | 0.4730 | 0.3746 | 0.1731 | 0.6203 |
| jieba_baseline | 1.20 | 0.55 | 0.1443 | 0.3849 | 0.4297 | 0.6313 | 0.4832 | 0.3688 | 0.1721 | 0.6358 |
| jieba_baseline | 1.50 | 0.90 | 0.1781 | 0.3833 | 0.4896 | 0.6323 | 0.5194 | 0.4292 | 0.1903 | 0.6594 |
| jieba_baseline | 1.50 | 0.75 | 0.1677 | 0.3833 | 0.4661 | 0.6401 | 0.4982 | 0.4020 | 0.1825 | 0.6430 |
| jieba_baseline | 1.20 | 0.90 | 0.1781 | 0.3781 | 0.4661 | 0.6479 | 0.5268 | 0.4157 | 0.1888 | 0.6613 |
| jieba_baseline | 0.90 | 0.55 | 0.1339 | 0.3745 | 0.4453 | 0.6313 | 0.4588 | 0.3676 | 0.1692 | 0.6269 |
| jieba_baseline | 1.20 | 0.75 | 0.1443 | 0.3729 | 0.4661 | 0.6339 | 0.4953 | 0.3953 | 0.1718 | 0.6399 |
| jieba_baseline | 1.50 | 0.55 | 0.1599 | 0.3641 | 0.4401 | 0.6391 | 0.5025 | 0.3804 | 0.1825 | 0.6613 |

## Dense Rerank 复盘摘要（2026-05-16）

- 本轮新增策略：先用 v3 的预选最优配置 `jieba_course_terms_thuocl_multi_rrf_filtered_section_aware` 取 top20，再用本地 BGE-M3 dense embedding 对候选重排到 top10。没有调用 GraphRAG API 或 LLM。
- 运行环境观察：默认沙箱内 `torch.cuda.is_available()` 为 False，CPU 路径 10 分钟未完成；沙箱外使用本地 BGE-M3 snapshot + `cuda/fp16` 约 2 分钟完成。因此该策略若要作为常规离线工具，需要明确本地模型路径和 GPU 可用性，不能默认依赖 CPU。
- 相比 v3 best，dense rerank 的 overall `recall_at_3` 从 `0.5646` 升到 `0.5964`，`rr` 从 `0.6689` 升到 `0.7158`，`ndcg_at_5` 从 `0.5977` 升到 `0.6087`。
- 代价是 top5/top10 覆盖下降：`recall_at_5` 从 `0.6760` 降到 `0.6406`，`recall_at_10` 从 `0.7708` 降到 `0.7484`。这说明 dense rerank 更擅长把语义相近证据前推，但会把一部分长尾 gold evidence 挤出前 5/10。
- 分题型看，factual_lookup 提升最明显：`recall_at_3` 从 `0.7500` 到 `1.0000`；relation_reasoning 从 `0.7083` 到 `0.7917`。chapter_summary 明显变差：`0.4771` 到 `0.2917`；global_overview 小幅下降：`0.3229` 到 `0.3021`。
- 重点失败题：Q019 在 dense rerank 后从 raw top10 miss 变为 top3 命中，但命中的 `2b7b30555d10` 是噪声审计中标记过的 repeated placeholder text，因此这类改善不能直接视为真实内容改善。Q031 仍然 top10 miss，说明问题语义和 gold 证据之间不是简单 rerank 能解决的差距，更像 gold 标注/章节映射/查询改写问题。Q029 被 dense rerank 伤害，`recall_at_3` 从 `0.5` 降到 `0.25`，不适合作为 global overview 默认重排策略。
- 结论：BGE dense rerank 可作为“factual/relation 或失败题诊断”的离线工具，但不建议直接进入 BM25 默认链路；更不建议在当前阶段把它放进学生交互路径。下一步更稳的是：先清理噪声 text units，再把 dense rerank 限定到 factual/relation 或 raw top10 miss 的诊断场景，同时针对 global/chapter 继续做 query decomposition / heading-aware gold 审计。
