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
- dense rerank policy: `factual_relation`
- dense rerank candidate pool: `20`
- dense rerank model: `/home/sunlight/.cache/huggingface/hub/models--BAAI--bge-m3/snapshots/5617a9f61b028005a4858fdac845db406aefb181`
- dense rerank device: `cuda`
- noise audit findings: `7`
- noise filter: `enabled`
- active noise blacklist refs: `7`
- text units used by retrieval: `526`
- query decomposition policy: `none`

## best config

- config: `jieba_course_terms_thuocl_multi_rrf_filtered_dense_rerank_top20`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.6245`
- expanded_recall_at_3: `0.2864`
- rr: `0.7144`
- expanded_rr: `0.8698`
- ndcg_at_5: `0.6357`
- expanded_ndcg_at_5: `0.6206`

## baseline config

- config: `jieba_baseline`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.3771`
- expanded_recall_at_3: `0.1799`
- rr: `0.4934`
- expanded_rr: `0.643`
- ndcg_at_5: `0.3967`
- expanded_ndcg_at_5: `0.4536`

## 结论

- recall_at_3 delta vs baseline: `+0.2474`
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。

## overall scores

| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 | expanded r@3 | expanded rr |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| jieba_course_terms_thuocl_multi_rrf_filtered_dense_rerank_top20 | 1.50 | 0.75 | 0.3292 | 0.6245 | 0.6891 | 0.7474 | 0.7144 | 0.6357 | 0.2864 | 0.8698 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.75 | 0.2927 | 0.5620 | 0.6630 | 0.7474 | 0.6832 | 0.5987 | 0.2668 | 0.8505 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.75 | 0.3083 | 0.5568 | 0.6630 | 0.7474 | 0.6795 | 0.6050 | 0.2589 | 0.8474 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.5568 | 0.6318 | 0.7474 | 0.6464 | 0.5693 | 0.2589 | 0.8161 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2849 | 0.5542 | 0.6760 | 0.7630 | 0.6696 | 0.5967 | 0.2689 | 0.8148 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2380 | 0.5438 | 0.6422 | 0.7708 | 0.6394 | 0.5585 | 0.2653 | 0.7842 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.5412 | 0.6057 | 0.7083 | 0.6383 | 0.5425 | 0.2381 | 0.8307 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.55 | 0.2458 | 0.5359 | 0.6432 | 0.7302 | 0.6565 | 0.5615 | 0.2587 | 0.8516 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.90 | 0.3083 | 0.5255 | 0.6865 | 0.7161 | 0.6759 | 0.6128 | 0.2355 | 0.8174 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.90 | 0.3083 | 0.5255 | 0.6474 | 0.7474 | 0.6737 | 0.5938 | 0.2381 | 0.8146 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2927 | 0.5229 | 0.6682 | 0.7630 | 0.6800 | 0.5930 | 0.2571 | 0.8289 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.75 | 0.2771 | 0.5214 | 0.6745 | 0.7380 | 0.6722 | 0.5907 | 0.2509 | 0.8531 |
| jieba_course_terms_filtered | 1.20 | 0.90 | 0.2771 | 0.5203 | 0.6510 | 0.7302 | 0.7087 | 0.5883 | 0.2302 | 0.8380 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.90 | 0.2667 | 0.5151 | 0.6083 | 0.7708 | 0.6909 | 0.5558 | 0.2283 | 0.8068 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.90 | 0.2927 | 0.5151 | 0.6318 | 0.7068 | 0.6903 | 0.5789 | 0.2300 | 0.8344 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.75 | 0.2823 | 0.5151 | 0.6005 | 0.7370 | 0.6730 | 0.5506 | 0.2300 | 0.8084 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.90 | 0.2823 | 0.5151 | 0.5953 | 0.7474 | 0.6715 | 0.5533 | 0.2339 | 0.8097 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.90 | 0.2823 | 0.5151 | 0.5927 | 0.6953 | 0.6625 | 0.5449 | 0.2417 | 0.8109 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.75 | 0.2771 | 0.5151 | 0.6849 | 0.7536 | 0.6581 | 0.5896 | 0.2444 | 0.8271 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.90 | 0.3240 | 0.5099 | 0.6865 | 0.7302 | 0.6934 | 0.6183 | 0.2355 | 0.8339 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2693 | 0.5099 | 0.5693 | 0.7604 | 0.6808 | 0.5411 | 0.2275 | 0.8218 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.55 | 0.2615 | 0.5073 | 0.5979 | 0.7396 | 0.6673 | 0.5446 | 0.2261 | 0.8436 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.75 | 0.2615 | 0.5073 | 0.6318 | 0.7161 | 0.6644 | 0.5653 | 0.2224 | 0.8446 |
| jieba_course_terms_filtered | 1.50 | 0.90 | 0.2771 | 0.5047 | 0.6510 | 0.7224 | 0.7128 | 0.5939 | 0.2393 | 0.8422 |
| jieba_course_terms_filtered | 0.90 | 0.90 | 0.2771 | 0.5047 | 0.6042 | 0.7302 | 0.7006 | 0.5658 | 0.2249 | 0.8076 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.75 | 0.2536 | 0.5047 | 0.6240 | 0.7422 | 0.6628 | 0.5479 | 0.2400 | 0.8049 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.90 | 0.2562 | 0.5047 | 0.6266 | 0.7188 | 0.6594 | 0.5537 | 0.2478 | 0.8073 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2719 | 0.5047 | 0.6370 | 0.7318 | 0.6482 | 0.5663 | 0.2418 | 0.8295 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2849 | 0.5021 | 0.6318 | 0.7474 | 0.6661 | 0.5671 | 0.2311 | 0.8109 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2354 | 0.5021 | 0.6656 | 0.7708 | 0.6383 | 0.5628 | 0.2507 | 0.8185 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2380 | 0.5021 | 0.6448 | 0.7708 | 0.6370 | 0.5484 | 0.2275 | 0.7964 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.90 | 0.2927 | 0.4995 | 0.6318 | 0.7068 | 0.6954 | 0.5852 | 0.2287 | 0.8359 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.90 | 0.2849 | 0.4995 | 0.6432 | 0.7458 | 0.6831 | 0.5799 | 0.2249 | 0.8109 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.90 | 0.2849 | 0.4995 | 0.6823 | 0.7380 | 0.6826 | 0.5987 | 0.2293 | 0.8344 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.90 | 0.2927 | 0.4995 | 0.5823 | 0.7161 | 0.6797 | 0.5536 | 0.2248 | 0.8050 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.90 | 0.2849 | 0.4995 | 0.6823 | 0.7302 | 0.6795 | 0.5942 | 0.2249 | 0.8172 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.75 | 0.2615 | 0.4995 | 0.6318 | 0.7224 | 0.6785 | 0.5703 | 0.2316 | 0.8474 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.75 | 0.2458 | 0.4995 | 0.6474 | 0.7536 | 0.6378 | 0.5592 | 0.2170 | 0.8073 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.4995 | 0.6604 | 0.7083 | 0.6365 | 0.5621 | 0.2249 | 0.8146 |
| jieba_course_terms_filtered | 1.50 | 0.55 | 0.2771 | 0.4969 | 0.5938 | 0.7146 | 0.6972 | 0.5495 | 0.2244 | 0.8438 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.75 | 0.2615 | 0.4969 | 0.5823 | 0.7161 | 0.6568 | 0.5391 | 0.2142 | 0.8083 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.75 | 0.2667 | 0.4943 | 0.6005 | 0.7526 | 0.6881 | 0.5475 | 0.2254 | 0.8027 |
| jieba_course_terms | 1.20 | 0.90 | 0.2432 | 0.4943 | 0.5885 | 0.7224 | 0.6464 | 0.5295 | 0.2137 | 0.7446 |
| jieba_course_terms | 0.90 | 0.90 | 0.2432 | 0.4943 | 0.5885 | 0.7224 | 0.6399 | 0.5268 | 0.1916 | 0.7294 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.55 | 0.2458 | 0.4943 | 0.6510 | 0.7302 | 0.6354 | 0.5524 | 0.2481 | 0.8083 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2432 | 0.4917 | 0.6474 | 0.7500 | 0.6500 | 0.5538 | 0.2408 | 0.8135 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.2458 | 0.4917 | 0.6135 | 0.7708 | 0.6495 | 0.5383 | 0.2510 | 0.8125 |
| jieba_course_terms | 1.50 | 0.90 | 0.2432 | 0.4891 | 0.5885 | 0.7224 | 0.6534 | 0.5347 | 0.2127 | 0.7440 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4891 | 0.6031 | 0.7318 | 0.6352 | 0.5387 | 0.2209 | 0.8146 |
| jieba_course_terms_filtered | 1.20 | 0.55 | 0.2771 | 0.4865 | 0.6328 | 0.7146 | 0.6958 | 0.5633 | 0.2140 | 0.8266 |
| jieba_course_terms | 1.20 | 0.55 | 0.2406 | 0.4865 | 0.5417 | 0.7146 | 0.6314 | 0.4950 | 0.1903 | 0.7378 |
| jieba_course_terms_filtered | 1.50 | 0.75 | 0.2771 | 0.4849 | 0.6589 | 0.7380 | 0.7118 | 0.5828 | 0.2218 | 0.8552 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.75 | 0.2901 | 0.4839 | 0.6083 | 0.7370 | 0.6840 | 0.5585 | 0.2105 | 0.8092 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.90 | 0.2823 | 0.4839 | 0.6083 | 0.7370 | 0.6701 | 0.5585 | 0.2183 | 0.8102 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.55 | 0.2589 | 0.4839 | 0.6057 | 0.7292 | 0.6690 | 0.5445 | 0.2222 | 0.8068 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2536 | 0.4839 | 0.5693 | 0.7760 | 0.6648 | 0.5250 | 0.2194 | 0.8212 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.75 | 0.2797 | 0.4839 | 0.6188 | 0.6953 | 0.6578 | 0.5510 | 0.2105 | 0.8068 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2667 | 0.4839 | 0.6578 | 0.7708 | 0.6521 | 0.5704 | 0.2354 | 0.8333 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.3005 | 0.4812 | 0.6422 | 0.7630 | 0.6924 | 0.5799 | 0.2233 | 0.8242 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2797 | 0.4812 | 0.6526 | 0.7318 | 0.6549 | 0.5708 | 0.2131 | 0.8195 |
| jieba_course_terms | 1.50 | 0.75 | 0.2432 | 0.4786 | 0.5625 | 0.7146 | 0.6430 | 0.5097 | 0.2020 | 0.7596 |
| jieba_course_terms_thuocl | 1.20 | 0.90 | 0.2589 | 0.4786 | 0.5927 | 0.6990 | 0.6295 | 0.5328 | 0.2100 | 0.7411 |
| jieba_course_terms_filtered | 0.90 | 0.75 | 0.2771 | 0.4760 | 0.5979 | 0.7380 | 0.6838 | 0.5474 | 0.2096 | 0.8111 |
| jieba_course_terms | 0.90 | 0.75 | 0.2432 | 0.4760 | 0.5615 | 0.6833 | 0.6227 | 0.5026 | 0.1914 | 0.7307 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.55 | 0.2745 | 0.4734 | 0.5953 | 0.7266 | 0.6928 | 0.5422 | 0.2176 | 0.8173 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.90 | 0.2667 | 0.4734 | 0.6526 | 0.7604 | 0.6871 | 0.5728 | 0.2166 | 0.8070 |
| jieba_course_terms_thuocl | 1.50 | 0.90 | 0.2589 | 0.4734 | 0.5927 | 0.6677 | 0.6331 | 0.5382 | 0.2089 | 0.7380 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2589 | 0.4708 | 0.5693 | 0.7370 | 0.6581 | 0.5307 | 0.2170 | 0.8173 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.55 | 0.2615 | 0.4708 | 0.5641 | 0.7083 | 0.6506 | 0.5249 | 0.2105 | 0.8076 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2484 | 0.4708 | 0.5745 | 0.7266 | 0.6357 | 0.5205 | 0.2131 | 0.8111 |
| jieba_course_terms_thuocl | 0.90 | 0.75 | 0.2276 | 0.4708 | 0.5615 | 0.6849 | 0.5980 | 0.5000 | 0.1905 | 0.7292 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.55 | 0.2615 | 0.4656 | 0.6214 | 0.7083 | 0.6602 | 0.5522 | 0.2131 | 0.8222 |
| jieba_course_terms | 1.50 | 0.55 | 0.2328 | 0.4656 | 0.5833 | 0.7068 | 0.6159 | 0.5071 | 0.2046 | 0.7271 |
| jieba_course_terms_filtered | 1.20 | 0.75 | 0.2771 | 0.4630 | 0.6745 | 0.7380 | 0.6927 | 0.5816 | 0.2192 | 0.8511 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.75 | 0.2667 | 0.4630 | 0.5875 | 0.7526 | 0.6825 | 0.5372 | 0.2166 | 0.8181 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.55 | 0.2458 | 0.4630 | 0.6188 | 0.7536 | 0.6355 | 0.5411 | 0.2413 | 0.8135 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.55 | 0.2484 | 0.4630 | 0.5849 | 0.6797 | 0.6354 | 0.5225 | 0.2048 | 0.7941 |
| jieba_course_terms_thuocl | 0.90 | 0.90 | 0.2589 | 0.4630 | 0.5823 | 0.6615 | 0.6173 | 0.5237 | 0.1879 | 0.7234 |
| jieba_course_terms_thuocl | 1.20 | 0.75 | 0.2276 | 0.4630 | 0.5927 | 0.6849 | 0.6037 | 0.5183 | 0.1957 | 0.7385 |
| jieba_course_terms_filtered | 0.90 | 0.55 | 0.2771 | 0.4604 | 0.5849 | 0.7302 | 0.6803 | 0.5359 | 0.2085 | 0.8109 |
| jieba_course_terms_thuocl | 1.50 | 0.55 | 0.2354 | 0.4604 | 0.5875 | 0.6693 | 0.6143 | 0.5121 | 0.2037 | 0.7536 |
| jieba_course_terms | 0.90 | 0.55 | 0.2302 | 0.4604 | 0.5094 | 0.7068 | 0.6049 | 0.4694 | 0.1848 | 0.7170 |
| jieba_course_terms_thuocl | 0.90 | 0.55 | 0.2250 | 0.4552 | 0.5536 | 0.6693 | 0.5892 | 0.4910 | 0.1840 | 0.7260 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2432 | 0.4526 | 0.5927 | 0.7917 | 0.6473 | 0.5304 | 0.2116 | 0.8225 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2432 | 0.4526 | 0.5901 | 0.7318 | 0.6368 | 0.5200 | 0.2077 | 0.8138 |
| jieba_course_terms | 1.20 | 0.75 | 0.2432 | 0.4526 | 0.5729 | 0.7146 | 0.6305 | 0.5100 | 0.1953 | 0.7439 |
| jieba_course_terms_thuocl | 1.20 | 0.55 | 0.2354 | 0.4500 | 0.5719 | 0.6771 | 0.6123 | 0.5064 | 0.1894 | 0.7484 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.55 | 0.2641 | 0.4422 | 0.6057 | 0.7161 | 0.6706 | 0.5402 | 0.2059 | 0.8008 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.55 | 0.2484 | 0.4422 | 0.6057 | 0.7188 | 0.6479 | 0.5368 | 0.2076 | 0.8071 |
| jieba_course_terms_thuocl | 1.50 | 0.75 | 0.2276 | 0.4422 | 0.6240 | 0.6536 | 0.6068 | 0.5312 | 0.1985 | 0.7510 |
| jieba_thuocl_it_filtered | 0.90 | 0.75 | 0.1755 | 0.4396 | 0.5354 | 0.6667 | 0.5344 | 0.4516 | 0.1850 | 0.7104 |
| jieba_thuocl_it_filtered | 1.20 | 0.55 | 0.1755 | 0.4344 | 0.5250 | 0.6823 | 0.5240 | 0.4378 | 0.1840 | 0.7297 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.55 | 0.2536 | 0.4318 | 0.5745 | 0.7031 | 0.6402 | 0.5126 | 0.2059 | 0.7866 |
| jieba_thuocl_it_filtered | 0.90 | 0.90 | 0.2484 | 0.4318 | 0.5563 | 0.6510 | 0.5880 | 0.5001 | 0.1915 | 0.7130 |
| jieba_thuocl_it_filtered | 1.20 | 0.90 | 0.2510 | 0.4266 | 0.5458 | 0.6979 | 0.5990 | 0.4951 | 0.2072 | 0.7568 |
| jieba_thuocl_it_filtered | 1.20 | 0.75 | 0.1964 | 0.4240 | 0.5458 | 0.6667 | 0.5715 | 0.4722 | 0.1955 | 0.7510 |
| jieba_thuocl_it_filtered | 1.50 | 0.90 | 0.2510 | 0.4214 | 0.5380 | 0.6615 | 0.5935 | 0.4925 | 0.2061 | 0.7589 |
| jieba_thuocl_it_filtered | 1.50 | 0.55 | 0.1859 | 0.4135 | 0.5198 | 0.6432 | 0.5325 | 0.4393 | 0.1983 | 0.7422 |
| jieba_thuocl_it_filtered | 1.50 | 0.75 | 0.2198 | 0.4109 | 0.5458 | 0.6745 | 0.5779 | 0.4787 | 0.1957 | 0.7542 |
| jieba_thuocl_it_filtered | 0.90 | 0.55 | 0.1755 | 0.3927 | 0.5354 | 0.6406 | 0.5095 | 0.4383 | 0.1772 | 0.6948 |
| jieba_baseline | 0.90 | 0.75 | 0.1339 | 0.3901 | 0.4391 | 0.6354 | 0.4660 | 0.3701 | 0.1731 | 0.6203 |
| jieba_baseline | 0.90 | 0.90 | 0.1755 | 0.3875 | 0.4599 | 0.6339 | 0.5187 | 0.4109 | 0.1705 | 0.6382 |
| jieba_baseline | 1.20 | 0.55 | 0.1443 | 0.3849 | 0.4234 | 0.6250 | 0.4759 | 0.3642 | 0.1721 | 0.6358 |
| jieba_baseline | 1.50 | 0.90 | 0.1781 | 0.3771 | 0.4896 | 0.6339 | 0.5194 | 0.4280 | 0.1877 | 0.6625 |
| jieba_baseline | 1.50 | 0.75 | 0.1677 | 0.3771 | 0.4599 | 0.6339 | 0.4934 | 0.3967 | 0.1799 | 0.6430 |
| jieba_baseline | 0.90 | 0.55 | 0.1339 | 0.3745 | 0.4391 | 0.6250 | 0.4533 | 0.3635 | 0.1692 | 0.6269 |
| jieba_baseline | 1.20 | 0.90 | 0.1781 | 0.3719 | 0.4599 | 0.6339 | 0.5189 | 0.4104 | 0.1861 | 0.6613 |
| jieba_baseline | 1.20 | 0.75 | 0.1443 | 0.3667 | 0.4599 | 0.6276 | 0.4854 | 0.3900 | 0.1692 | 0.6399 |
| jieba_baseline | 1.50 | 0.55 | 0.1443 | 0.3641 | 0.4339 | 0.6328 | 0.4794 | 0.3687 | 0.1825 | 0.6460 |

## dense rerank diagnostics

| config | attempted | helped | hurt | mean delta r@3 |
| --- | ---: | ---: | ---: | ---: |
| jieba_course_terms_thuocl_multi_rrf_filtered_dense_rerank_top20 | 16 | 3 | 0 | 0.1562 |

## 实验结论与下一步建议

- 主指标 best vs baseline recall_at_3 delta 为 `+0.2474`。
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。
- 本报告只评估离线 evidence retrieval；不能直接证明最终问答质量。
- sidecar 噪声过滤已启用，未修改 GraphRAG 原始 output 产物。
- dense rerank 为诊断性 gated 策略；若 hurt_count 不为 0，不建议默认进入真实问答链路。
