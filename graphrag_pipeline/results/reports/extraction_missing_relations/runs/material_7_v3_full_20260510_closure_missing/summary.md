# Gold 侧缺失关系诊断

- `run_id`：material_7_v3_full_20260510_closure_missing
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_structured_closure`
- `audit_path`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/data/eval/material_7_audit_extraction_set.json`
- `candidate_count`：7

## 跨候选汇总

- 所有候选 × gold 关系总数：567

| 类别 | count | 占比 |
|---|---:|---:|
| hit | 103 | 0.1817 |
| direction_reversed | 1 | 0.0018 |
| wrong_type | 21 | 0.0370 |
| both_endpoints_present_but_not_connected | 22 | 0.0388 |
| source_endpoint_missing | 99 | 0.1746 |
| target_endpoint_missing | 106 | 0.1869 |
| both_endpoints_missing | 215 | 0.3792 |

### 按关系类型（跨候选汇总）

| relation_type | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| appears_in | 42 | 0 | 0 | 0 | 0 | 5 | 20 | 17 | 0.0000 |
| applied_in | 21 | 0 | 0 | 1 | 0 | 6 | 0 | 14 | 0.0000 |
| belongs_to | 21 | 0 | 0 | 10 | 0 | 9 | 2 | 0 | 0.0000 |
| contains | 210 | 71 | 0 | 0 | 9 | 65 | 22 | 43 | 0.3381 |
| defined_by | 42 | 0 | 0 | 0 | 0 | 0 | 27 | 15 | 0.0000 |
| depends_on | 84 | 23 | 0 | 1 | 11 | 10 | 6 | 33 | 0.2738 |
| evaluated_by | 77 | 0 | 0 | 0 | 0 | 0 | 24 | 53 | 0.0000 |
| implemented_by | 21 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0000 |
| prerequisite_of | 14 | 0 | 0 | 8 | 0 | 0 | 0 | 6 | 0.0000 |
| related_to | 35 | 9 | 1 | 1 | 2 | 4 | 5 | 13 | 0.2571 |

## 各候选详情

| candidate | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| auto_tuned | 81 | 14 | 0 | 5 | 3 | 13 | 15 | 31 | 0.1728 |
| default | 81 | 16 | 1 | 3 | 4 | 13 | 13 | 31 | 0.1975 |
| schema_aware | 81 | 13 | 0 | 2 | 4 | 15 | 13 | 34 | 0.1605 |
| schema_aware_directional | 81 | 15 | 0 | 2 | 3 | 15 | 15 | 31 | 0.1852 |
| schema_fewshot | 81 | 13 | 0 | 4 | 3 | 12 | 18 | 31 | 0.1605 |
| schema_fewshot_distilled | 81 | 15 | 0 | 3 | 2 | 15 | 18 | 28 | 0.1852 |
| schema_fewshot_distilled_v2 | 81 | 17 | 0 | 2 | 3 | 16 | 14 | 29 | 0.2099 |

## 典型 miss 样本（每候选最多 20 条）

### auto_tuned

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | source_endpoint_missing | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | both_endpoints_missing | 处理机调度算法 | 第三章习题 |

### default

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |

### schema_aware

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |

### schema_aware_directional

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |

### schema_fewshot

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |

### schema_fewshot_distilled

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0049-bd80db3cdf | m49-r01 | contains | target_endpoint_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | wrong_type | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | both_endpoints_missing | 处理机调度算法 | 第三章习题 |

### schema_fewshot_distilled_v2

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r01 | contains | both_endpoints_missing | 第十一章 多媒体操作系统 | 11.1 多媒体系统简介 |
| pts-0054-eaa3d01814 | m54-r02 | contains | source_endpoint_missing | 11.1 多媒体系统简介 | 媒体 |
| pts-0054-eaa3d01814 | m54-r09 | depends_on | source_endpoint_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | source_endpoint_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r01 | contains | both_endpoints_missing | 第七章 文件管理 | 7.1 文件和文件系统 |
| pts-0075-016a0e9b7c | m75-r02 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 数据项 |
| pts-0075-016a0e9b7c | m75-r03 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 记录 |
| pts-0075-016a0e9b7c | m75-r04 | contains | source_endpoint_missing | 7.1 文件和文件系统 | 文件 |
| pts-0049-bd80db3cdf | m49-r01 | contains | both_endpoints_missing | 第四章 存储器管理 | 4.5 分页存储管理方式 |
| pts-0049-bd80db3cdf | m49-r02 | contains | both_endpoints_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | both_endpoints_missing | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | both_endpoints_missing | 处理机调度算法 | 第三章习题 |

