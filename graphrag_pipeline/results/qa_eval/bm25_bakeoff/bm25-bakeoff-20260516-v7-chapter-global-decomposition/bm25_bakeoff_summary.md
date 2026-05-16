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
- dense rerank: `disabled`
- dense rerank policy: `none`
- dense rerank candidate pool: `20`
- dense rerank model: `BAAI/bge-m3`
- dense rerank device: `auto`
- noise audit findings: `7`
- noise filter: `enabled`
- active noise blacklist refs: `7`
- text units used by retrieval: `526`
- query decomposition policy: `chapter_global`

## best config

- config: `jieba_course_terms_multi_rrf_filtered`
- k1/b: `1.5` / `0.55`
- recall_at_3: `0.5813`
- expanded_recall_at_3: `0.2556`
- rr: `0.6818`
- expanded_rr: `0.8568`
- ndcg_at_5: `0.5797`
- expanded_ndcg_at_5: `0.5707`

## baseline config

- config: `jieba_baseline`
- k1/b: `1.5` / `0.75`
- recall_at_3: `0.4354`
- expanded_recall_at_3: `0.1877`
- rr: `0.5373`
- expanded_rr: `0.654`
- ndcg_at_5: `0.4442`
- expanded_ndcg_at_5: `0.4739`

## 结论

- recall_at_3 delta vs baseline: `+0.1459`
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。

## overall scores

| config | k1 | b | r@1 | r@3 | r@5 | r@10 | rr | ndcg@5 | expanded r@3 | expanded rr |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.55 | 0.2615 | 0.5813 | 0.6562 | 0.7531 | 0.6818 | 0.5797 | 0.2556 | 0.8568 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.75 | 0.2964 | 0.5786 | 0.6667 | 0.7521 | 0.7094 | 0.6076 | 0.2639 | 0.8490 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.55 | 0.2599 | 0.5656 | 0.6250 | 0.7312 | 0.6758 | 0.5667 | 0.2350 | 0.8404 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.75 | 0.2599 | 0.5604 | 0.6146 | 0.7521 | 0.6930 | 0.5766 | 0.2548 | 0.8396 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.90 | 0.2807 | 0.5604 | 0.6276 | 0.7510 | 0.6807 | 0.5736 | 0.2522 | 0.8047 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.75 | 0.3120 | 0.5578 | 0.6562 | 0.7521 | 0.7115 | 0.6104 | 0.2522 | 0.8552 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.75 | 0.2495 | 0.5578 | 0.6667 | 0.7208 | 0.6687 | 0.5846 | 0.2654 | 0.8130 |
| jieba_course_terms_filtered | 1.50 | 0.55 | 0.2771 | 0.5552 | 0.6562 | 0.7375 | 0.6990 | 0.5851 | 0.2554 | 0.8255 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.90 | 0.2573 | 0.5526 | 0.6562 | 0.8057 | 0.6896 | 0.5807 | 0.2348 | 0.8305 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.75 | 0.2547 | 0.5500 | 0.6745 | 0.7911 | 0.6911 | 0.5910 | 0.2533 | 0.8063 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2859 | 0.5448 | 0.6484 | 0.7667 | 0.6844 | 0.5876 | 0.2520 | 0.8281 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.90 | 0.2807 | 0.5448 | 0.6198 | 0.7719 | 0.6824 | 0.5724 | 0.2352 | 0.8177 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.75 | 0.2599 | 0.5448 | 0.6042 | 0.7208 | 0.6800 | 0.5630 | 0.2469 | 0.8161 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.75 | 0.2807 | 0.5448 | 0.6172 | 0.7677 | 0.6797 | 0.5662 | 0.2381 | 0.8133 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.75 | 0.2651 | 0.5422 | 0.6250 | 0.7911 | 0.6980 | 0.5702 | 0.2361 | 0.8271 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.75 | 0.2807 | 0.5422 | 0.6562 | 0.7208 | 0.6844 | 0.5918 | 0.2498 | 0.8352 |
| jieba_course_terms_filtered | 0.90 | 0.55 | 0.2771 | 0.5396 | 0.5885 | 0.7547 | 0.6977 | 0.5524 | 0.2424 | 0.8125 |
| jieba_course_terms_filtered | 1.50 | 0.90 | 0.2729 | 0.5380 | 0.6469 | 0.7349 | 0.7078 | 0.5918 | 0.2393 | 0.8344 |
| jieba_course_terms_filtered | 1.50 | 0.75 | 0.2729 | 0.5370 | 0.6625 | 0.7427 | 0.7078 | 0.5910 | 0.2530 | 0.8385 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2495 | 0.5370 | 0.6406 | 0.7911 | 0.6722 | 0.5652 | 0.2353 | 0.8279 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2589 | 0.5370 | 0.6094 | 0.7781 | 0.6644 | 0.5640 | 0.2301 | 0.8378 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2417 | 0.5370 | 0.6406 | 0.8057 | 0.6626 | 0.5645 | 0.2351 | 0.8297 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2495 | 0.5344 | 0.6849 | 0.7599 | 0.6786 | 0.5888 | 0.2512 | 0.8187 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.55 | 0.2599 | 0.5344 | 0.5677 | 0.7547 | 0.6772 | 0.5405 | 0.2207 | 0.8284 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.55 | 0.2599 | 0.5344 | 0.6250 | 0.7391 | 0.6740 | 0.5626 | 0.2244 | 0.8240 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.55 | 0.2599 | 0.5344 | 0.6354 | 0.7625 | 0.6727 | 0.5686 | 0.2259 | 0.8201 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.75 | 0.2885 | 0.5318 | 0.6781 | 0.7427 | 0.7036 | 0.6048 | 0.2455 | 0.8688 |
| jieba_course_terms_filtered | 1.20 | 0.90 | 0.2729 | 0.5318 | 0.6469 | 0.7349 | 0.7010 | 0.5828 | 0.2262 | 0.8146 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.90 | 0.2964 | 0.5292 | 0.6510 | 0.7875 | 0.7043 | 0.5959 | 0.2335 | 0.8424 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.50 | 0.75 | 0.2964 | 0.5292 | 0.6172 | 0.7677 | 0.7006 | 0.5768 | 0.2363 | 0.8383 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.90 | 0.2859 | 0.5292 | 0.6615 | 0.7745 | 0.6974 | 0.5942 | 0.2554 | 0.8219 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.90 | 0.2573 | 0.5292 | 0.6693 | 0.7953 | 0.6791 | 0.5843 | 0.2554 | 0.8313 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.90 | 0.2729 | 0.5292 | 0.6328 | 0.7719 | 0.6664 | 0.5751 | 0.2287 | 0.8186 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.50 | 0.90 | 0.3276 | 0.5266 | 0.6406 | 0.7271 | 0.7086 | 0.6013 | 0.2327 | 0.8451 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 1.20 | 0.90 | 0.3120 | 0.5266 | 0.6406 | 0.7312 | 0.6922 | 0.5946 | 0.2288 | 0.8138 |
| jieba_course_terms_thuocl_multi_rrf_filtered | 0.90 | 0.90 | 0.3120 | 0.5266 | 0.5990 | 0.7625 | 0.6865 | 0.5768 | 0.2288 | 0.7907 |
| jieba_course_terms_thuocl_filtered | 1.50 | 0.90 | 0.2807 | 0.5266 | 0.6250 | 0.7115 | 0.6789 | 0.5761 | 0.2235 | 0.8076 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.90 | 0.2807 | 0.5266 | 0.6250 | 0.7156 | 0.6729 | 0.5707 | 0.2235 | 0.7927 |
| jieba_course_terms_filtered | 1.20 | 0.55 | 0.2771 | 0.5240 | 0.6458 | 0.7391 | 0.6953 | 0.5753 | 0.2163 | 0.8047 |
| jieba_course_terms_multi_rrf_filtered | 1.50 | 0.90 | 0.2885 | 0.5224 | 0.6469 | 0.7349 | 0.6932 | 0.5875 | 0.2291 | 0.8474 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.75 | 0.2703 | 0.5214 | 0.6302 | 0.7469 | 0.6638 | 0.5690 | 0.2211 | 0.7961 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.50 | 0.55 | 0.2432 | 0.5214 | 0.6406 | 0.7781 | 0.6435 | 0.5646 | 0.2303 | 0.8128 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.55 | 0.2599 | 0.5188 | 0.5781 | 0.7391 | 0.6693 | 0.5430 | 0.2157 | 0.8050 |
| jieba_course_terms_filtered | 0.90 | 0.90 | 0.2729 | 0.5161 | 0.5948 | 0.7271 | 0.6930 | 0.5605 | 0.2236 | 0.7865 |
| jieba_course_terms | 1.20 | 0.55 | 0.2510 | 0.5161 | 0.5729 | 0.7234 | 0.6506 | 0.5249 | 0.1953 | 0.7185 |
| jieba_course_terms | 1.50 | 0.90 | 0.2391 | 0.5146 | 0.5818 | 0.7141 | 0.6432 | 0.5345 | 0.2205 | 0.7161 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.90 | 0.2807 | 0.5135 | 0.6615 | 0.8057 | 0.6927 | 0.5876 | 0.2455 | 0.8375 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2885 | 0.5135 | 0.6484 | 0.7875 | 0.6842 | 0.5891 | 0.2309 | 0.8417 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.75 | 0.2807 | 0.5135 | 0.6328 | 0.7781 | 0.6802 | 0.5748 | 0.2185 | 0.8156 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.90 | 0.2417 | 0.5135 | 0.6693 | 0.7953 | 0.6530 | 0.5694 | 0.2557 | 0.8313 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2380 | 0.5135 | 0.5990 | 0.7365 | 0.6292 | 0.5443 | 0.2251 | 0.8026 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.75 | 0.2651 | 0.5109 | 0.6432 | 0.7911 | 0.6955 | 0.5720 | 0.2272 | 0.8267 |
| jieba_course_terms_thuocl_filtered | 0.90 | 0.90 | 0.2807 | 0.5109 | 0.5729 | 0.7312 | 0.6631 | 0.5458 | 0.2209 | 0.7620 |
| jieba_course_terms_thuocl_filtered_section_aware | 0.90 | 0.55 | 0.2224 | 0.5109 | 0.6120 | 0.7391 | 0.6068 | 0.5354 | 0.2157 | 0.7792 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.55 | 0.2615 | 0.5083 | 0.6042 | 0.7703 | 0.6743 | 0.5481 | 0.2385 | 0.8281 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.55 | 0.2615 | 0.5083 | 0.6458 | 0.7547 | 0.6703 | 0.5642 | 0.2137 | 0.8203 |
| jieba_course_terms | 1.50 | 0.75 | 0.2573 | 0.5083 | 0.5573 | 0.6880 | 0.6677 | 0.5225 | 0.2098 | 0.7490 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.75 | 0.2859 | 0.5057 | 0.6406 | 0.7625 | 0.6831 | 0.5821 | 0.2210 | 0.8196 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2380 | 0.5057 | 0.5964 | 0.7391 | 0.6250 | 0.5363 | 0.2183 | 0.8026 |
| jieba_course_terms_thuocl_filtered | 1.20 | 0.55 | 0.2599 | 0.5031 | 0.6354 | 0.7391 | 0.6677 | 0.5651 | 0.2181 | 0.8005 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.90 | 0.2885 | 0.5005 | 0.5948 | 0.7427 | 0.6878 | 0.5595 | 0.2181 | 0.7917 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.90 | 0.2885 | 0.5005 | 0.6469 | 0.7349 | 0.6865 | 0.5790 | 0.2181 | 0.8135 |
| jieba_course_terms_multi_rrf_filtered | 1.20 | 0.75 | 0.2885 | 0.5005 | 0.6677 | 0.7583 | 0.6847 | 0.5876 | 0.2103 | 0.8375 |
| jieba_course_terms_filtered_section_aware | 1.50 | 0.55 | 0.2589 | 0.5005 | 0.6406 | 0.7651 | 0.6642 | 0.5675 | 0.2231 | 0.8019 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.50 | 0.55 | 0.2432 | 0.5005 | 0.6094 | 0.7755 | 0.6452 | 0.5475 | 0.2194 | 0.8176 |
| jieba_course_terms | 0.90 | 0.55 | 0.2510 | 0.5005 | 0.5234 | 0.7234 | 0.6436 | 0.4981 | 0.1901 | 0.7154 |
| jieba_course_terms | 1.20 | 0.90 | 0.2391 | 0.5005 | 0.5833 | 0.7036 | 0.6357 | 0.5266 | 0.2153 | 0.7146 |
| jieba_course_terms | 0.90 | 0.90 | 0.2391 | 0.5005 | 0.5651 | 0.6958 | 0.6266 | 0.5175 | 0.1931 | 0.7031 |
| jieba_course_terms_thuocl_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.2964 | 0.4979 | 0.6224 | 0.7937 | 0.6979 | 0.5776 | 0.2285 | 0.8391 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.75 | 0.2495 | 0.4953 | 0.6380 | 0.7911 | 0.6675 | 0.5596 | 0.2275 | 0.8263 |
| jieba_course_terms_thuocl | 0.90 | 0.55 | 0.2339 | 0.4953 | 0.5703 | 0.7000 | 0.6220 | 0.5147 | 0.1866 | 0.7130 |
| jieba_course_terms_multi_rrf_filtered | 0.90 | 0.75 | 0.2573 | 0.4927 | 0.5990 | 0.7583 | 0.6722 | 0.5508 | 0.2103 | 0.8253 |
| jieba_course_terms | 0.90 | 0.75 | 0.2573 | 0.4927 | 0.5651 | 0.6880 | 0.6612 | 0.5244 | 0.1929 | 0.7505 |
| jieba_course_terms | 1.20 | 0.75 | 0.2573 | 0.4927 | 0.5573 | 0.7193 | 0.6607 | 0.5187 | 0.2059 | 0.7522 |
| jieba_course_terms_thuocl | 1.50 | 0.90 | 0.2469 | 0.4927 | 0.5964 | 0.6844 | 0.6125 | 0.5345 | 0.2116 | 0.6766 |
| jieba_course_terms_filtered | 1.20 | 0.75 | 0.2729 | 0.4901 | 0.6573 | 0.7427 | 0.6862 | 0.5749 | 0.2218 | 0.8263 |
| jieba_course_terms_thuocl | 1.20 | 0.75 | 0.2339 | 0.4875 | 0.5833 | 0.7052 | 0.6314 | 0.5252 | 0.2024 | 0.7347 |
| jieba_course_terms_thuocl | 0.90 | 0.75 | 0.2339 | 0.4875 | 0.5755 | 0.7052 | 0.6303 | 0.5211 | 0.1868 | 0.7326 |
| jieba_course_terms_thuocl_filtered_section_aware | 1.20 | 0.55 | 0.2224 | 0.4875 | 0.6302 | 0.7677 | 0.6130 | 0.5467 | 0.2185 | 0.7823 |
| jieba_course_terms | 1.50 | 0.55 | 0.2573 | 0.4849 | 0.6146 | 0.7297 | 0.6674 | 0.5450 | 0.2070 | 0.7513 |
| jieba_course_terms_thuocl | 1.20 | 0.90 | 0.2469 | 0.4849 | 0.5964 | 0.6740 | 0.6039 | 0.5280 | 0.2116 | 0.6766 |
| jieba_course_terms_filtered | 0.90 | 0.75 | 0.2729 | 0.4823 | 0.5885 | 0.7427 | 0.6878 | 0.5496 | 0.2085 | 0.8097 |
| jieba_course_terms_thuocl | 1.50 | 0.55 | 0.2339 | 0.4797 | 0.6250 | 0.7000 | 0.6260 | 0.5384 | 0.2035 | 0.7292 |
| jieba_course_terms_thuocl | 1.20 | 0.55 | 0.2339 | 0.4797 | 0.6042 | 0.7000 | 0.6243 | 0.5320 | 0.1918 | 0.7109 |
| jieba_course_terms_filtered_section_aware | 1.20 | 0.55 | 0.2380 | 0.4771 | 0.6302 | 0.7651 | 0.6313 | 0.5491 | 0.2142 | 0.7693 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 1.20 | 0.55 | 0.2328 | 0.4771 | 0.6146 | 0.7911 | 0.6283 | 0.5428 | 0.2144 | 0.8009 |
| jieba_thuocl_it_filtered | 0.90 | 0.75 | 0.1948 | 0.4745 | 0.5469 | 0.6922 | 0.5797 | 0.4814 | 0.1892 | 0.7286 |
| jieba_thuocl_it_filtered | 1.20 | 0.75 | 0.1870 | 0.4745 | 0.5469 | 0.6922 | 0.5647 | 0.4771 | 0.2035 | 0.7260 |
| jieba_course_terms_thuocl | 1.50 | 0.75 | 0.2339 | 0.4719 | 0.6250 | 0.6740 | 0.6307 | 0.5422 | 0.2063 | 0.7271 |
| jieba_course_terms_filtered_section_aware | 0.90 | 0.55 | 0.2380 | 0.4693 | 0.6094 | 0.7599 | 0.6274 | 0.5352 | 0.2142 | 0.7529 |
| jieba_course_terms_multi_rrf_filtered_section_aware | 0.90 | 0.55 | 0.2328 | 0.4693 | 0.5885 | 0.7599 | 0.6237 | 0.5266 | 0.2077 | 0.7842 |
| jieba_course_terms_thuocl | 0.90 | 0.90 | 0.2469 | 0.4693 | 0.5651 | 0.6661 | 0.5964 | 0.5121 | 0.1868 | 0.6807 |
| jieba_thuocl_it_filtered | 1.20 | 0.55 | 0.1948 | 0.4641 | 0.5391 | 0.7052 | 0.5707 | 0.4649 | 0.1866 | 0.7163 |
| jieba_thuocl_it_filtered | 0.90 | 0.90 | 0.2469 | 0.4589 | 0.5469 | 0.6740 | 0.5940 | 0.5035 | 0.1905 | 0.7198 |
| jieba_thuocl_it_filtered | 1.50 | 0.75 | 0.2260 | 0.4589 | 0.5573 | 0.6740 | 0.5925 | 0.4966 | 0.2035 | 0.7375 |
| jieba_thuocl_it_filtered | 1.50 | 0.90 | 0.2469 | 0.4536 | 0.5339 | 0.7000 | 0.5997 | 0.4940 | 0.2061 | 0.7417 |
| jieba_thuocl_it_filtered | 1.20 | 0.90 | 0.2469 | 0.4536 | 0.5495 | 0.6818 | 0.5983 | 0.5017 | 0.2061 | 0.7417 |
| jieba_baseline | 0.90 | 0.75 | 0.1531 | 0.4510 | 0.4792 | 0.6193 | 0.5170 | 0.4207 | 0.1851 | 0.6549 |
| jieba_baseline | 1.20 | 0.75 | 0.1531 | 0.4432 | 0.4792 | 0.6505 | 0.5236 | 0.4240 | 0.1825 | 0.6548 |
| jieba_baseline | 0.90 | 0.55 | 0.1531 | 0.4432 | 0.4792 | 0.6688 | 0.5176 | 0.4146 | 0.1744 | 0.6281 |
| jieba_baseline | 1.50 | 0.75 | 0.1844 | 0.4354 | 0.4974 | 0.6193 | 0.5373 | 0.4442 | 0.1877 | 0.6540 |
| jieba_baseline | 1.20 | 0.55 | 0.1531 | 0.4354 | 0.4792 | 0.6688 | 0.5157 | 0.4133 | 0.1718 | 0.6237 |
| jieba_thuocl_it_filtered | 0.90 | 0.55 | 0.1948 | 0.4328 | 0.5365 | 0.6844 | 0.5687 | 0.4640 | 0.1827 | 0.6964 |
| jieba_thuocl_it_filtered | 1.50 | 0.55 | 0.1948 | 0.4328 | 0.5495 | 0.6740 | 0.5648 | 0.4706 | 0.1983 | 0.7216 |
| jieba_baseline | 0.90 | 0.90 | 0.1844 | 0.4302 | 0.4870 | 0.6193 | 0.5492 | 0.4450 | 0.1773 | 0.6714 |
| jieba_baseline | 1.50 | 0.55 | 0.1531 | 0.4120 | 0.4974 | 0.6766 | 0.5140 | 0.4223 | 0.1877 | 0.6554 |
| jieba_baseline | 1.50 | 0.90 | 0.1844 | 0.4068 | 0.5130 | 0.6271 | 0.5474 | 0.4534 | 0.1903 | 0.6740 |
| jieba_baseline | 1.20 | 0.90 | 0.1844 | 0.4068 | 0.4974 | 0.6271 | 0.5436 | 0.4449 | 0.1903 | 0.6687 |

## 实验结论与下一步建议

- 主指标 best vs baseline recall_at_3 delta 为 `+0.1459`。
- best config 相比 baseline 的 recall_at_3 有提升，可进入小规模 one-shot smoke。
- 本报告只评估离线 evidence retrieval；不能直接证明最终问答质量。
- sidecar 噪声过滤已启用，未修改 GraphRAG 原始 output 产物。
- chapter/global query decomposition 仅用于离线召回验证；真实 prompt 注入仍需单独 smoke 批准。
