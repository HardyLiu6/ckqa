# Gold 侧缺失关系诊断

- `run_id`：material_7_v3_full_20260510_closure_v2_seed_meta_sim_missing
- `source_eval_dir`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/results/extraction_eval/runs/material_7_v3_full_20260510_closure_v2_seed_meta_sim`
- `audit_path`：`/home/sunlight/Projects/ckqa/graphrag_pipeline/data/eval/material_7_audit_extraction_set.json`
- `candidate_count`：7

## 跨候选汇总

- 所有候选 × gold 关系总数：567

| 类别 | count | 占比 |
|---|---:|---:|
| hit | 177 | 0.3122 |
| direction_reversed | 1 | 0.0018 |
| wrong_type | 43 | 0.0758 |
| both_endpoints_present_but_not_connected | 101 | 0.1781 |
| source_endpoint_missing | 100 | 0.1764 |
| target_endpoint_missing | 45 | 0.0794 |
| both_endpoints_missing | 100 | 0.1764 |

### 按关系类型（跨候选汇总）

| relation_type | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| appears_in | 42 | 0 | 0 | 0 | 20 | 13 | 0 | 9 | 0.0000 |
| applied_in | 21 | 0 | 0 | 1 | 0 | 20 | 0 | 0 | 0.0000 |
| belongs_to | 21 | 0 | 0 | 12 | 0 | 9 | 0 | 0 | 0.0000 |
| contains | 210 | 145 | 0 | 0 | 37 | 21 | 7 | 0 | 0.6905 |
| defined_by | 42 | 0 | 0 | 0 | 0 | 0 | 27 | 15 | 0.0000 |
| depends_on | 84 | 23 | 0 | 1 | 11 | 10 | 6 | 33 | 0.2738 |
| evaluated_by | 77 | 0 | 0 | 20 | 31 | 23 | 0 | 3 | 0.0000 |
| implemented_by | 21 | 0 | 0 | 0 | 0 | 0 | 0 | 21 | 0.0000 |
| prerequisite_of | 14 | 0 | 0 | 8 | 0 | 0 | 0 | 6 | 0.0000 |
| related_to | 35 | 9 | 1 | 1 | 2 | 4 | 5 | 13 | 0.2571 |

## 各候选详情

| candidate | total | hit | dir_rev | wrong_type | both_present_no_edge | src_miss | tgt_miss | both_miss | hit_rate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| auto_tuned | 81 | 26 | 0 | 9 | 15 | 13 | 5 | 13 | 0.3210 |
| default | 81 | 26 | 1 | 6 | 14 | 15 | 6 | 13 | 0.3210 |
| schema_aware | 81 | 23 | 0 | 4 | 13 | 14 | 7 | 20 | 0.2840 |
| schema_aware_directional | 81 | 25 | 0 | 5 | 15 | 15 | 6 | 15 | 0.3086 |
| schema_fewshot | 81 | 25 | 0 | 8 | 15 | 12 | 8 | 13 | 0.3086 |
| schema_fewshot_distilled | 81 | 25 | 0 | 6 | 14 | 15 | 8 | 13 | 0.3086 |
| schema_fewshot_distilled_v2 | 81 | 27 | 0 | 5 | 15 | 16 | 5 | 13 | 0.3333 |

## 典型 miss 样本（每候选最多 20 条）

### auto_tuned

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | source_endpoint_missing | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_present_but_not_connected | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | wrong_type | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r05 | defined_by | target_endpoint_missing | 进程 | 进程定义 |
| pts-0066-4bacda7c7a | m66-r07 | applied_in | source_endpoint_missing | 进程同步机制 | 进程 |

### default

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r01 | contains | both_endpoints_present_but_not_connected | 第二章 进程的描述与控制 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |

### schema_aware

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r01 | contains | both_endpoints_present_but_not_connected | 第二章 进程的描述与控制 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |

### schema_aware_directional

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r01 | contains | both_endpoints_present_but_not_connected | 第二章 进程的描述与控制 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |

### schema_fewshot

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0075-016a0e9b7c | m75-r08 | contains | both_endpoints_present_but_not_connected | 文件 | 记录 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r05 | defined_by | target_endpoint_missing | 进程 | 进程定义 |

### schema_fewshot_distilled

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | both_endpoints_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | both_endpoints_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | target_endpoint_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | target_endpoint_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | wrong_type | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r01 | contains | both_endpoints_present_but_not_connected | 第二章 进程的描述与控制 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r05 | defined_by | target_endpoint_missing | 进程 | 进程定义 |

### schema_fewshot_distilled_v2

| sample_id | relation_id | relation_type | category | source_name | target_name |
|---|---|---|---|---|---|
| pts-0054-eaa3d01814 | m54-r09 | depends_on | source_endpoint_missing | 多媒体技术 | 音像技术 |
| pts-0054-eaa3d01814 | m54-r10 | depends_on | source_endpoint_missing | 多媒体技术 | 计算机技术 |
| pts-0054-eaa3d01814 | m54-r11 | depends_on | both_endpoints_missing | 多媒体技术 | 通信网络技术 |
| pts-0049-bd80db3cdf | m49-r02 | contains | target_endpoint_missing | 4.5 分页存储管理方式 | 分页存储管理 |
| pts-0049-bd80db3cdf | m49-r03 | contains | source_endpoint_missing | 分页存储管理 | 页面 |
| pts-0049-bd80db3cdf | m49-r04 | contains | source_endpoint_missing | 分页存储管理 | 物理块 |
| pts-0049-bd80db3cdf | m49-r05 | contains | source_endpoint_missing | 分页存储管理 | 页表 |
| pts-0049-bd80db3cdf | m49-r06 | defined_by | both_endpoints_missing | 页号P | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r07 | defined_by | both_endpoints_missing | 位移量W | 页号和页内地址计算公式 |
| pts-0049-bd80db3cdf | m49-r08 | depends_on | both_endpoints_missing | 页面大小 | 内存利用率 |
| pts-0049-bd80db3cdf | m49-r09 | depends_on | both_endpoints_present_but_not_connected | 地址映射 | 页表 |
| pts-0046-a99abcf7ae | m46-r01 | evaluated_by | wrong_type | 第三章 处理机调度与死锁 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r02 | evaluated_by | source_endpoint_missing | 处理机调度算法 | 第三章习题 |
| pts-0046-a99abcf7ae | m46-r03 | related_to | both_endpoints_missing | 高级调度 | 低级调度 |
| pts-0046-a99abcf7ae | m46-r04 | prerequisite_of | both_endpoints_missing | 高级调度 | 中级调度 |
| pts-0046-a99abcf7ae | m46-r05 | appears_in | both_endpoints_present_but_not_connected | 高响应比优先调度算法 | 第三章习题 |
| pts-0066-4bacda7c7a | m66-r01 | contains | both_endpoints_present_but_not_connected | 第二章 进程的描述与控制 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r03 | belongs_to | wrong_type | 进程 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r04 | appears_in | source_endpoint_missing | 进程控制块 | 2.2.1 进程的定义和特征 |
| pts-0066-4bacda7c7a | m66-r05 | defined_by | target_endpoint_missing | 进程 | 进程定义 |

