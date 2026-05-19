# Gold Section Audit

- purpose: audit whether raw gold refs are too narrow for section-level questions.
- note: this report does not modify `qa_test_set.jsonl`.

| question_id | category | audit_label | raw_hit | expanded_hit | parent_hit | sibling_hit | child_hit |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| Q001 | factual_lookup | ok | True | True | False | False | True |
| Q002 | factual_lookup | retrieval_miss | False | False | False | False | False |
| Q003 | factual_lookup | ok | True | True | False | True | False |
| Q004 | factual_lookup | ok | True | True | False | True | False |
| Q005 | factual_lookup | ok | True | True | False | True | False |
| Q006 | factual_lookup | ok | True | True | False | True | False |
| Q007 | factual_lookup | ok | True | True | False | False | True |
| Q008 | factual_lookup | ok | True | True | False | False | False |
| Q009 | relation_reasoning | ok | True | True | False | False | True |
| Q010 | relation_reasoning | ok | True | True | True | False | False |
| Q011 | relation_reasoning | ok | True | True | True | False | True |
| Q012 | relation_reasoning | ok | True | True | False | False | True |
| Q013 | relation_reasoning | ok | True | True | False | False | True |
| Q014 | relation_reasoning | ok | True | True | False | True | False |
| Q015 | relation_reasoning | ok | True | True | False | True | False |
| Q016 | relation_reasoning | ok | True | True | False | False | True |
| Q017 | chapter_summary | ok | True | True | True | False | True |
| Q018 | chapter_summary | ok | True | True | True | True | False |
| Q019 | chapter_summary | ok | True | True | True | False | True |
| Q020 | chapter_summary | gold_maybe_too_narrow | False | True | True | True | False |
| Q021 | chapter_summary | ok | True | True | True | False | False |
| Q022 | chapter_summary | ok | True | True | False | True | True |
| Q023 | chapter_summary | ok | True | True | True | False | False |
| Q024 | chapter_summary | ok | True | True | False | False | True |
| Q025 | global_overview | ok | True | True | False | False | False |
| Q026 | global_overview | ok | True | True | False | True | True |
| Q027 | global_overview | ok | True | True | False | False | True |
| Q028 | global_overview | ok | True | True | True | False | True |
| Q029 | global_overview | ok | True | True | False | False | False |
| Q030 | global_overview | ok | True | True | False | False | True |
| Q031 | global_overview | question_too_broad | False | False | False | False | False |
| Q032 | global_overview | ok | True | True | False | True | True |

## Details

### Q001 (factual_lookup)

- question: 教材第一章如何定义操作系统，它在计算机硬件之上处于什么位置？
- audit_label: `ok`
- raw_gold_refs: `81d99ad61e36`
- heading_path: `第一章 操作系统引论`
- parent_refs: ``
- same_section_sibling_refs: ``
- child_refs: `f55c02f07061,6ce5eb6c812b,a237fc2edcfb,769fc023aace,80a635231043,0b727efb10f2,f7324b146aa2,acf71dc19377,e7b61df827f9,baee31c12a77,8b322941f06a,7eca1f05aeb7,8b2772e305c6,22fb5c469642,093253f12d29,d1de500cf18d,43580e117b70,8327254fcda2,34ca836229df,e31711587ab3,6fce15825141,3768a1d36c92,9213459b0aa8,014964257784,be67038427c3,09957405da60,616795a1c017,c2d570d976d8,fc7b77262a61,5064b027cd17,3716cc52020c,ecbfb1d5d35c,cb39172ad98e,a9eb61a4db8d,366a61932f17,4ca5d175a742,d7d811b1507a,10d15b75ea9d,a3342f3ef7c1,600c0103e521,f7b796b75760`
- top10_refs: `6ce5eb6c812b,a237fc2edcfb,81d99ad61e36,769fc023aace,e7b61df827f9,baee31c12a77,96b69eabc764,383ac29db710,a3342f3ef7c1,d7d811b1507a`

### Q002 (factual_lookup)

- question: 教材列出的操作系统主要目标有哪些？
- audit_label: `retrieval_miss`
- raw_gold_refs: `6ce5eb6c812b`
- heading_path: `第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.1 操作系统的目标`
- parent_refs: `f55c02f07061,81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: ``
- top10_refs: `d7d811b1507a,19a6cb0bc22c,cea83c4f184f,fac33c613da5,8947f69faaed,80a635231043,e5379f40b8b0,880da5723fbf,e2a495c80be2,a237fc2edcfb`

### Q003 (factual_lookup)

- question: 分时系统最核心的服务目标是什么？
- audit_label: `ok`
- raw_gold_refs: `8b322941f06a`
- heading_path: `第一章 操作系统引论 > 1.2 操作系统的发展过程 > 1.2.4 分时系统(Time Sharing System)`
- parent_refs: `0b727efb10f2,81d99ad61e36`
- same_section_sibling_refs: `7eca1f05aeb7`
- child_refs: ``
- top10_refs: `7eca1f05aeb7,66666285733e,8b322941f06a,880da5723fbf,ae144456d81a,8b2772e305c6,d7d811b1507a,115c5a0d33c4,c28e1414f143,04f546377b87`

### Q004 (factual_lookup)

- question: 进程管理中的 PCB 主要用于保存什么信息？
- audit_label: `ok`
- raw_gold_refs: `88e3f6ddcfdc,d5a1a7999273`
- heading_path: `第二章 进程的描述与控制 > 2.2 进程的描述 > 2.2.4 进程管理中的数据结构 || 第二章 进程的描述与控制 > 2.2 进程的描述 > 2.2.4 进程管理中的数据结构`
- parent_refs: `6c5c54439cd0,fa9277086ac8`
- same_section_sibling_refs: `69d73be86d1e,a527d7bbf85c`
- child_refs: ``
- top10_refs: `69d73be86d1e,88e3f6ddcfdc,fe3e4fc89c4f,d5a1a7999273,158cebb2fc56,9f9133cf38af,b08f2d8a4d0f,0b146d2e8345,76f121972f21,93bf6a4839c1`

### Q005 (factual_lookup)

- question: 信号量机制中，互斥信号量的典型初值为什么通常设为 1？
- audit_label: `ok`
- raw_gold_refs: `6c0fe52fc6d8`
- heading_path: `第二章 进程的描述与控制 > 2.4 进程同步 > 2.4.3 信号量机制`
- parent_refs: `fdd5deaf51f6,fa9277086ac8`
- same_section_sibling_refs: `f23cc0f41de4,b751bb087d00`
- child_refs: ``
- top10_refs: `f23cc0f41de4,fde5f27b43ea,6c0fe52fc6d8,b751bb087d00,9315e07d3644,39ac7386457e,f52c3e0c2223,9f9133cf38af,f80ae69072b0,49269108f9bc`

### Q006 (factual_lookup)

- question: 银行家算法用于解决死锁处理中的哪一类问题？
- audit_label: `ok`
- raw_gold_refs: `c0f3c202a762`
- heading_path: `第三章 处理机调度与死锁 > 3.7 避免死锁 > 3.7.2 利用银行家算法避免死锁`
- parent_refs: `839602cebe13,2b7b30555d10`
- same_section_sibling_refs: `a33a51c56b4a,3c84cabb2ac6,85256adf9b3b,2f36a9448f37,a5466c94bc55`
- child_refs: ``
- top10_refs: `c0f3c202a762,a5466c94bc55,3c84cabb2ac6,85256adf9b3b,a33a51c56b4a,2f36a9448f37,2926bcf6cf67,4a2ee9f71e49,0571e0dd9e04,19a6cb0bc22c`

### Q007 (factual_lookup)

- question: 请求分页系统相对基本分页增加了哪两个核心功能？
- audit_label: `ok`
- raw_gold_refs: `e698d871994b`
- heading_path: `第五章 虚拟存储器 > 5.2 请求分页存储管理方式`
- parent_refs: `a2b896fc708e`
- same_section_sibling_refs: ``
- child_refs: `d4d11199bc79,44dc09628be0,f76665b9dd95,4484b5876a58,d60cf0a48b38,fe03ddbc53d3`
- top10_refs: `e698d871994b,d4d11199bc79,6d3c41766085,17a9641bfe6c,d8440049cb5f,f76665b9dd95,fea9d22bfccd,a7352905e495,49ee217ae3aa,44dc09628be0`

### Q008 (factual_lookup)

- question: 设备管理的主要任务之一是什么？
- audit_label: `ok`
- raw_gold_refs: `09957405da60`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.3 设备管理功能`
- parent_refs: `9213459b0aa8,81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: ``
- top10_refs: `09957405da60,cdfd392e0705,044c071690a4,d5f0b3788377,6cf0e2a88f13,6453da8d633c,da8a93db1b05,c176c1c66f17,a237fc2edcfb,c1e7946b1376`

### Q009 (relation_reasoning)

- question: 进程和线程在资源拥有与调度单位上的关键差异是什么？
- audit_label: `ok`
- raw_gold_refs: `6119e805a0a2,fe3e4fc89c4f`
- heading_path: `第二章 进程的描述与控制 > 2.7 线程(Threads)的基本概念 || 第二章 进程的描述与控制 > 2.7 线程(Threads)的基本概念 > 2.7.1 线程的引入`
- parent_refs: `fa9277086ac8`
- same_section_sibling_refs: ``
- child_refs: `e04aa616effa,f290d67dc19a,d332e031d720,3cab01f43566`
- top10_refs: `e04aa616effa,fe3e4fc89c4f,014964257784,d332e031d720,e2e8799f676c,ddd624b40085,3c908cc2c513,0bd26f213ef3,766f4fbd7628,6119e805a0a2`

### Q010 (relation_reasoning)

- question: 并发和共享这两个操作系统基本特性之间是什么关系？
- audit_label: `ok`
- raw_gold_refs: `8327254fcda2,34ca836229df`
- heading_path: `第一章 操作系统引论 > 1.3 操作系统的基本特性 > 1.3.1 并发(Concurrence) || 第一章 操作系统引论 > 1.3 操作系统的基本特性 > 1.3.2 共享(Sharing)`
- parent_refs: `43580e117b70,81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: ``
- top10_refs: `8327254fcda2,34ca836229df,1aae8ef34909,cea83c4f184f,96b69eabc764,177a9f830d40,fe3e4fc89c4f,43580e117b70,a6d8a728ef73,27fe8c74d081`

### Q011 (relation_reasoning)

- question: 预防死锁和避免死锁在处理思路上有什么不同？
- audit_label: `ok`
- raw_gold_refs: `d5fb4ca2b663,c0f3c202a762`
- heading_path: `第三章 处理机调度与死锁 > 3.6 预防死锁 || 第三章 处理机调度与死锁 > 3.7 避免死锁 > 3.7.2 利用银行家算法避免死锁`
- parent_refs: `2b7b30555d10,839602cebe13`
- same_section_sibling_refs: `a33a51c56b4a,3c84cabb2ac6,85256adf9b3b,2f36a9448f37,a5466c94bc55`
- child_refs: `ae55a9738406,ee9f2f3f2064,08741f4bb7b2`
- top10_refs: `839602cebe13,27a11a04433a,26dbff780c69,045b43597822,d5fb4ca2b663,08741f4bb7b2,ae55a9738406,c0f3c202a762,fb1441c0819e,ee9f2f3f2064`

### Q012 (relation_reasoning)

- question: 基本分页和请求分页在虚拟存储器支持上的差异是什么？
- audit_label: `ok`
- raw_gold_refs: `17a9641bfe6c,e698d871994b`
- heading_path: `第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.3 访问内存的有效时间 || 第五章 虚拟存储器 > 5.2 请求分页存储管理方式`
- parent_refs: `49ee217ae3aa,085c2bd3447b,a2b896fc708e`
- same_section_sibling_refs: ``
- child_refs: `d4d11199bc79,44dc09628be0,f76665b9dd95,4484b5876a58,d60cf0a48b38,fe03ddbc53d3`
- top10_refs: `e698d871994b,d8440049cb5f,d4d11199bc79,a6f8f2655d64,6d3c41766085,a7352905e495,44dc09628be0,f76665b9dd95,9815684f7670,d463e1eb2c77`

### Q013 (relation_reasoning)

- question: 设备管理中的缓冲管理与磁盘调度分别解决什么层面的问题？
- audit_label: `ok`
- raw_gold_refs: `09957405da60,c9ded14a3f74,c4b3aaad6a90`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.3 设备管理功能 || 第六章 输入输出系统 > 6.8 磁盘存储器的性能和调度 || 第六章 输入输出系统 > 6.8 磁盘存储器的性能和调度 > 6.8.2 早期的磁盘调度算法`
- parent_refs: `9213459b0aa8,81d99ad61e36,310c7bf83bb5`
- same_section_sibling_refs: ``
- child_refs: `1514616f6b4b,23140ef67ae0,ef931e4dc568,b109a29417ed,8a7a4a8408b0`
- top10_refs: `09957405da60,cdfd392e0705,c4b3aaad6a90,da8a93db1b05,ef931e4dc568,b109a29417ed,4cd16e844a7f,c9ded14a3f74,8a7a4a8408b0,c1e7946b1376`

### Q014 (relation_reasoning)

- question: 单道批处理系统和多道批处理系统在资源利用上的主要区别是什么？
- audit_label: `ok`
- raw_gold_refs: `acf71dc19377,e7b61df827f9`
- heading_path: `第一章 操作系统引论 > 1.2 操作系统的发展过程 > 1.2.2 单道批处理系统 || 第一章 操作系统引论 > 1.2 操作系统的发展过程 > 1.2.3 多道批处理系统(Multiprogrammed Batch Processing System)`
- parent_refs: `0b727efb10f2,81d99ad61e36`
- same_section_sibling_refs: `baee31c12a77`
- child_refs: ``
- top10_refs: `acf71dc19377,e7b61df827f9,cda28aa1fac4,ae144456d81a,8b322941f06a,880da5723fbf,baee31c12a77,23baa0a3cf45,48a63b31e3e5,80a635231043`

### Q015 (relation_reasoning)

- question: 传统操作系统结构与微内核 OS 结构的设计取舍有什么不同？
- audit_label: `ok`
- raw_gold_refs: `ecbfb1d5d35c,d7d811b1507a`
- heading_path: `第一章 操作系统引论 > 1.5 OS结构设计 > 1.5.1 传统操作系统结构 || 第一章 操作系统引论 > 1.5 OS结构设计 > 1.5.4 微内核OS结构`
- parent_refs: `3716cc52020c,81d99ad61e36`
- same_section_sibling_refs: `cb39172ad98e,10d15b75ea9d,a3342f3ef7c1,600c0103e521`
- child_refs: ``
- top10_refs: `d7d811b1507a,ecbfb1d5d35c,600c0103e521,597abdfc5230,10d15b75ea9d,a3342f3ef7c1,cb39172ad98e,2ee170e99c13,6ce5eb6c812b,2f301abe0ab1`

### Q016 (relation_reasoning)

- question: 用户接口和程序接口分别面向谁，二者如何共同构成操作系统对外服务？
- audit_label: `ok`
- raw_gold_refs: `c2d570d976d8,a9cdc5e0345c,74a5d66045ce`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.5 操作系统与用户之间的接口 || 第九章 操作系统接口 > 9.1 用户接口 || 第九章 操作系统接口`
- parent_refs: `9213459b0aa8,81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: `1f3e886f1076,939b158250d1,9c15925ba333,b2c3f64aa7ca,e29644cdcdd6,22077b014d5e,8947f69faaed,f3f5ad971e4e,f653e48f93b9,e5379f40b8b0,477a91b4f131,747c2bf68314,7b3f13a8cc29,2147a91a37a8,d02810b74ede,d790f3f353e9,5945b1b63fc1,05700a729eb0,5afbed58f67f,2eba1e180adb,162968032e11,40a5fc62ed89,ad386509c319,c919e0c958ea,4080802d3065,7dbad7f44bed,6ce444e74ff4,49269108f9bc,e43e08b8dc8a,cd842640ffa4,7a69d2fe64dc,948cb9a68a03,856b8685b318,2012756fac98,95549d40a73f,51981e6562e0`
- top10_refs: `c2d570d976d8,74a5d66045ce,939b158250d1,366a61932f17,a9cdc5e0345c,2eba1e180adb,1f3e886f1076,95549d40a73f,c35d5e401730,9c15925ba333`

### Q017 (chapter_summary)

- question: 请概括第一章 1.4「操作系统的主要功能」的学习重点。
- audit_label: `ok`
- raw_gold_refs: `9213459b0aa8,014964257784,09957405da60,616795a1c017,c2d570d976d8`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.1 处理机管理功能 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.3 设备管理功能 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.4 文件管理功能 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.5 操作系统与用户之间的接口`
- parent_refs: `81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: `be67038427c3,fc7b77262a61,5064b027cd17`
- top10_refs: `9213459b0aa8,5064b027cd17,09957405da60,616795a1c017,c2d570d976d8,014964257784,be67038427c3,fc7b77262a61,81d99ad61e36,d244f9016ac8`

### Q018 (chapter_summary)

- question: 请总结第二章 2.4「进程同步」围绕哪些核心问题展开。
- audit_label: `ok`
- raw_gold_refs: `3768a1d36c92,6c0fe52fc6d8`
- heading_path: `第一章 操作系统引论 > 1.3 操作系统的基本特性 > 1.3.4 异步(Asynchronism) || 第二章 进程的描述与控制 > 2.4 进程同步 > 2.4.3 信号量机制`
- parent_refs: `43580e117b70,81d99ad61e36,fdd5deaf51f6,fa9277086ac8`
- same_section_sibling_refs: `f23cc0f41de4,b751bb087d00`
- child_refs: ``
- top10_refs: `74bf53426d6c,012adacab0f2,6c0fe52fc6d8,fde5f27b43ea,f23cc0f41de4,fdd5deaf51f6,e74272900408,1aae8ef34909,3124ecd12b98,c35d5e401730`

### Q019 (chapter_summary)

- question: 请概括第三章「处理机调度与死锁」的主线。
- audit_label: `ok`
- raw_gold_refs: `2b7b30555d10,23baa0a3cf45,27a11a04433a,d5fb4ca2b663,c0f3c202a762`
- heading_path: `第三章 处理机调度与死锁 || 第三章 处理机调度与死锁 > 3.1 处理机调度的层次和调度算法的目标 || 第三章 处理机调度与死锁 > 3.5 死锁概述 > 3.5.3 死锁的定义、必要条件和处理方法 || 第三章 处理机调度与死锁 > 3.6 预防死锁 || 第三章 处理机调度与死锁 > 3.7 避免死锁 > 3.7.2 利用银行家算法避免死锁`
- parent_refs: `26dbff780c69,839602cebe13`
- same_section_sibling_refs: `a33a51c56b4a,3c84cabb2ac6,85256adf9b3b,2f36a9448f37,a5466c94bc55`
- child_refs: `ae144456d81a,880da5723fbf,66666285733e,45646212d185,cda28aa1fac4,04f546377b87,e9fe5c6e1b7a,ca310ae37fef,5bef81b4470c,93bf6a4839c1,c28e1414f143,960820795398,4693176e7167,ec52a9ace9b6,71e01f09dd12,34a83931daef,6ccad2457be3,8d7458ee71be,ece16b0b51ed,91f667359c4c,109d49f5c8d2,1c910e312496,f826ab0df460,af10f44ea5d1,848739d9382c,26dbff780c69,4a2ee9f71e49,daa5b3e1e0e6,f6adc3650161,c4e088baaaf3,733f7b7a8815,ae55a9738406,ee9f2f3f2064,08741f4bb7b2,839602cebe13,fb1441c0819e,58366c6553c1,a33a51c56b4a,3c84cabb2ac6,85256adf9b3b,2f36a9448f37,a5466c94bc55,7dbec3305350,0571e0dd9e04,2926bcf6cf67,484256ed3872,d8e7c00fd547,c7f725cc2c61`
- top10_refs: `ec52a9ace9b6,d5fb4ca2b663,7dbec3305350,e9fe5c6e1b7a,839602cebe13,ae144456d81a,27a11a04433a,880da5723fbf,23baa0a3cf45,5bef81b4470c`

### Q020 (chapter_summary)

- question: 请总结第四章「存储器管理」中从连续分配到分页管理的知识脉络。
- audit_label: `gold_maybe_too_narrow`
- raw_gold_refs: `1fede2ba5057,cc4ed7d3e49d,9815684f7670,17a9641bfe6c`
- heading_path: `第四章 存储器管理 > 4.1 存储器的层次结构 > 4.1.1 多层结构的存储器系统 || 第四章 存储器管理 > 4.3 连续分配存储管理方式 > 4.3.5 基于索引搜索的动态分区分配算法 || 第四章 存储器管理 > 4.4 对换(Swapping) > 4.4.1 多道程序环境下的对换技术 || 第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.3 访问内存的有效时间`
- parent_refs: `914ba3ba7a51,085c2bd3447b,7859eb4ce0fe,115c5a0d33c4,49ee217ae3aa`
- same_section_sibling_refs: `8a51750704ad,76f4b69196fa`
- child_refs: ``
- top10_refs: `7859eb4ce0fe,73c28dce4d85,bc73b415943d,1985e26f9dc6,688348367b03,2412e8d31913,49ee217ae3aa,9d58e24a1026,76f4b69196fa,d463e1eb2c77`

### Q021 (chapter_summary)

- question: 请概括第五章「虚拟存储器」的核心内容。
- audit_label: `ok`
- raw_gold_refs: `d8440049cb5f,e698d871994b,039ce7bf8cc6`
- heading_path: `第五章 虚拟存储器 > 5.1 虚拟存储器概述 > 5.1.3 虚拟存储器的实现方法 || 第五章 虚拟存储器 > 5.2 请求分页存储管理方式 || 第五章 虚拟存储器 > 5.1 虚拟存储器概述 > 5.1.3 虚拟存储器的实现方法`
- parent_refs: `e0e32cd74351,a2b896fc708e`
- same_section_sibling_refs: ``
- child_refs: `d4d11199bc79,44dc09628be0,f76665b9dd95,4484b5876a58,d60cf0a48b38,fe03ddbc53d3`
- top10_refs: `e698d871994b,079991b6aa91,039ce7bf8cc6,a6f8f2655d64,554afa0f800c,a2b896fc708e,d8440049cb5f,e0e32cd74351,a69ecafce350,19ce758b2756`

### Q022 (chapter_summary)

- question: 请总结第六章输入输出系统中用户层 I/O 与磁盘调度部分的重点。
- audit_label: `ok`
- raw_gold_refs: `5a620bb11ded,c9ded14a3f74,c4b3aaad6a90`
- heading_path: `第六章 输入输出系统 > 6.6 用户层的I/O软件 > 6.6.2 假脱机(Spooling)系统 || 第六章 输入输出系统 > 6.8 磁盘存储器的性能和调度 || 第六章 输入输出系统 > 6.8 磁盘存储器的性能和调度 > 6.8.2 早期的磁盘调度算法`
- parent_refs: `17108047a338,310c7bf83bb5`
- same_section_sibling_refs: `c1e7946b1376,605baeef7d0b`
- child_refs: `1514616f6b4b,23140ef67ae0,ef931e4dc568,b109a29417ed,8a7a4a8408b0`
- top10_refs: `c1e7946b1376,605baeef7d0b,cdfd392e0705,4bfd066f8538,c4b3aaad6a90,ef931e4dc568,c176c1c66f17,044c071690a4,d5f0b3788377,23140ef67ae0`

### Q023 (chapter_summary)

- question: 请概括第七章文件管理中「文件目录」部分的学习重点。
- audit_label: `ok`
- raw_gold_refs: `0e7191c372f1,e796c9dfd296`
- heading_path: `第七章 文件管理 > 7.3 文件目录 > 7.3.1 文件控制块和索引结点 || 第七章 文件管理 > 7.3 文件目录 > 7.3.1 文件控制块和索引结点`
- parent_refs: `a42a86a1e63f,cd31d95183e6`
- same_section_sibling_refs: ``
- child_refs: ``
- top10_refs: `0e7191c372f1,e796c9dfd296,92258a2e0cd3,0d5563a5770d,29558a596b42,13552f644730,5496618d5e00,a458269196d0,a42a86a1e63f,3bb97404393c`

### Q024 (chapter_summary)

- question: 请概括第九章「操作系统接口」的主要内容。
- audit_label: `ok`
- raw_gold_refs: `74a5d66045ce,a9cdc5e0345c,c2d570d976d8`
- heading_path: `第九章 操作系统接口 || 第九章 操作系统接口 > 9.1 用户接口 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.5 操作系统与用户之间的接口`
- parent_refs: `9213459b0aa8,81d99ad61e36`
- same_section_sibling_refs: ``
- child_refs: `1f3e886f1076,939b158250d1,9c15925ba333,b2c3f64aa7ca,e29644cdcdd6,22077b014d5e,8947f69faaed,f3f5ad971e4e,f653e48f93b9,e5379f40b8b0,477a91b4f131,747c2bf68314,7b3f13a8cc29,2147a91a37a8,d02810b74ede,d790f3f353e9,5945b1b63fc1,05700a729eb0,5afbed58f67f,2eba1e180adb,162968032e11,40a5fc62ed89,ad386509c319,c919e0c958ea,4080802d3065,7dbad7f44bed,6ce444e74ff4,49269108f9bc,e43e08b8dc8a,cd842640ffa4,7a69d2fe64dc,948cb9a68a03,856b8685b318,2012756fac98,95549d40a73f,51981e6562e0`
- top10_refs: `74a5d66045ce,9c15925ba333,a9cdc5e0345c,22077b014d5e,e5379f40b8b0,2147a91a37a8,2eba1e180adb,4080802d3065,e43e08b8dc8a,477a91b4f131`

### Q025 (global_overview)

- question: 从全书结构看，这门操作系统课程的整体学习路径是什么？
- audit_label: `ok`
- raw_gold_refs: `d244f9016ac8`
- heading_path: `前言`
- parent_refs: ``
- same_section_sibling_refs: ``
- child_refs: ``
- top10_refs: `3bb97404393c,cdfd392e0705,d244f9016ac8,600c0103e521,e6f73efeafdf,50d8e06da91b,7e9f3875f6bf,d7d811b1507a,044c071690a4,25a0917c5a0c`

### Q026 (global_overview)

- question: 操作系统为什么既可以被看作资源管理者，也可以被看作用户与硬件之间的抽象层？
- audit_label: `ok`
- raw_gold_refs: `81d99ad61e36,a237fc2edcfb,9213459b0aa8`
- heading_path: `第一章 操作系统引论 || 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.2 操作系统的作用 || 第一章 操作系统引论 > 1.4 操作系统的主要功能`
- parent_refs: `f55c02f07061`
- same_section_sibling_refs: `769fc023aace`
- child_refs: `f55c02f07061,6ce5eb6c812b,769fc023aace,80a635231043,0b727efb10f2,f7324b146aa2,acf71dc19377,e7b61df827f9,baee31c12a77,8b322941f06a,7eca1f05aeb7,8b2772e305c6,22fb5c469642,093253f12d29,d1de500cf18d,43580e117b70,8327254fcda2,34ca836229df,e31711587ab3,6fce15825141,3768a1d36c92,014964257784,be67038427c3,09957405da60,616795a1c017,c2d570d976d8,fc7b77262a61,5064b027cd17,3716cc52020c,ecbfb1d5d35c,cb39172ad98e,a9eb61a4db8d,366a61932f17,4ca5d175a742,d7d811b1507a,10d15b75ea9d,a3342f3ef7c1,600c0103e521,f7b796b75760`
- top10_refs: `a237fc2edcfb,34ca836229df,c35d5e401730,f6844e0abbab,fc7b77262a61,044c071690a4,96b69eabc764,840ace0d43ed,769fc023aace,e94e650defcb`

### Q027 (global_overview)

- question: 处理机管理这条主线如何从进程扩展到线程和调度？
- audit_label: `ok`
- raw_gold_refs: `014964257784,88e3f6ddcfdc,6119e805a0a2,23baa0a3cf45`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.1 处理机管理功能 || 第二章 进程的描述与控制 > 2.2 进程的描述 > 2.2.4 进程管理中的数据结构 || 第二章 进程的描述与控制 > 2.7 线程(Threads)的基本概念 || 第三章 处理机调度与死锁 > 3.1 处理机调度的层次和调度算法的目标`
- parent_refs: `9213459b0aa8,81d99ad61e36,6c5c54439cd0,fa9277086ac8,2b7b30555d10`
- same_section_sibling_refs: `69d73be86d1e,d5a1a7999273,a527d7bbf85c`
- child_refs: `fe3e4fc89c4f,e04aa616effa,f290d67dc19a,d332e031d720,3cab01f43566,ae144456d81a,880da5723fbf,66666285733e`
- top10_refs: `014964257784,ddd624b40085,e2e8799f676c,ae144456d81a,25000c4030b3,0bd26f213ef3,ec52a9ace9b6,0e90e53751a6,fe3e4fc89c4f,766f4fbd7628`

### Q028 (global_overview)

- question: 存储管理为什么会从连续分配发展到分页和虚拟存储器？
- audit_label: `ok`
- raw_gold_refs: `cc4ed7d3e49d,17a9641bfe6c,d8440049cb5f,e698d871994b`
- heading_path: `第四章 存储器管理 > 4.3 连续分配存储管理方式 > 4.3.5 基于索引搜索的动态分区分配算法 || 第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.3 访问内存的有效时间 || 第五章 虚拟存储器 > 5.1 虚拟存储器概述 > 5.1.3 虚拟存储器的实现方法 || 第五章 虚拟存储器 > 5.2 请求分页存储管理方式`
- parent_refs: `7859eb4ce0fe,085c2bd3447b,49ee217ae3aa,e0e32cd74351,a2b896fc708e`
- same_section_sibling_refs: `8a51750704ad,76f4b69196fa,039ce7bf8cc6`
- child_refs: `d4d11199bc79,44dc09628be0,f76665b9dd95,4484b5876a58,d60cf0a48b38,fe03ddbc53d3`
- top10_refs: `d8440049cb5f,1985e26f9dc6,e698d871994b,49ee217ae3aa,7859eb4ce0fe,bc73b415943d,d4d11199bc79,079991b6aa91,57ff2fa14b24,d60cf0a48b38`

### Q029 (global_overview)

- question: I/O 管理、磁盘调度和文件系统在课程中如何衔接？
- audit_label: `ok`
- raw_gold_refs: `09957405da60,c9ded14a3f74,0e7191c372f1,e796c9dfd296`
- heading_path: `第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.3 设备管理功能 || 第六章 输入输出系统 > 6.8 磁盘存储器的性能和调度 || 第七章 文件管理 > 7.3 文件目录 > 7.3.1 文件控制块和索引结点 || 第七章 文件管理 > 7.3 文件目录 > 7.3.1 文件控制块和索引结点`
- parent_refs: `9213459b0aa8,81d99ad61e36,310c7bf83bb5,a42a86a1e63f,cd31d95183e6`
- same_section_sibling_refs: ``
- child_refs: `1514616f6b4b,23140ef67ae0,ef931e4dc568,c4b3aaad6a90,b109a29417ed,8a7a4a8408b0`
- top10_refs: `e796c9dfd296,0e7191c372f1,29558a596b42,92258a2e0cd3,0d5563a5770d,15d8e8ce23a7,5496618d5e00,a458269196d0,13552f644730,09957405da60`

### Q030 (global_overview)

- question: 操作系统的发展过程如何体现硬件能力和应用需求的共同推动？
- audit_label: `ok`
- raw_gold_refs: `0b727efb10f2,8b322941f06a,093253f12d29,80a635231043`
- heading_path: `第一章 操作系统引论 > 1.2 操作系统的发展过程 || 第一章 操作系统引论 > 1.2 操作系统的发展过程 > 1.2.4 分时系统(Time Sharing System) || 第一章 操作系统引论 > 1.2 操作系统的发展过程 > 1.2.6 微机操作系统的发展 || 第一章 操作系统引论 > 1.1 操作系统的目标和作用 > 1.1.3 推动操作系统发展的主要动力`
- parent_refs: `81d99ad61e36,f55c02f07061`
- same_section_sibling_refs: `7eca1f05aeb7,d1de500cf18d`
- child_refs: `f7324b146aa2,acf71dc19377,e7b61df827f9,baee31c12a77,7eca1f05aeb7,8b2772e305c6,22fb5c469642,d1de500cf18d`
- top10_refs: `80a635231043,8b322941f06a,0b727efb10f2,6ce5eb6c812b,43580e117b70,c28e1414f143,093253f12d29,e7b61df827f9,8b2772e305c6,ae144456d81a`

### Q031 (global_overview)

- question: 并发控制、同步机制和死锁处理共同服务于什么课程目标？
- audit_label: `question_too_broad`
- raw_gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663`
- heading_path: `第一章 操作系统引论 > 1.3 操作系统的基本特性 > 1.3.1 并发(Concurrence) || 第二章 进程的描述与控制 > 2.4 进程同步 > 2.4.3 信号量机制 || 第三章 处理机调度与死锁 > 3.5 死锁概述 > 3.5.3 死锁的定义、必要条件和处理方法 || 第三章 处理机调度与死锁 > 3.6 预防死锁`
- parent_refs: `43580e117b70,81d99ad61e36,fdd5deaf51f6,fa9277086ac8,26dbff780c69,2b7b30555d10`
- same_section_sibling_refs: `f23cc0f41de4,b751bb087d00`
- child_refs: `ae55a9738406,ee9f2f3f2064,08741f4bb7b2`
- top10_refs: `79ed196bc327,c35d5e401730,e8e5e8c14f8a,014964257784,012adacab0f2,34ca836229df,177a9f830d40,045b43597822,880da5723fbf,76f121972f21`

### Q032 (global_overview)

- question: 操作系统接口与微内核结构如何体现把系统能力模块化并对外提供服务的思想？
- audit_label: `ok`
- raw_gold_refs: `74a5d66045ce,c2d570d976d8,d7d811b1507a`
- heading_path: `第九章 操作系统接口 || 第一章 操作系统引论 > 1.4 操作系统的主要功能 > 1.4.5 操作系统与用户之间的接口 || 第一章 操作系统引论 > 1.5 OS结构设计 > 1.5.4 微内核OS结构`
- parent_refs: `9213459b0aa8,81d99ad61e36,3716cc52020c`
- same_section_sibling_refs: `10d15b75ea9d,a3342f3ef7c1,600c0103e521`
- child_refs: `a9cdc5e0345c,1f3e886f1076,939b158250d1,9c15925ba333,b2c3f64aa7ca,e29644cdcdd6,22077b014d5e,8947f69faaed,f3f5ad971e4e,f653e48f93b9,e5379f40b8b0,477a91b4f131,747c2bf68314,7b3f13a8cc29,2147a91a37a8,d02810b74ede,d790f3f353e9,5945b1b63fc1,05700a729eb0,5afbed58f67f,2eba1e180adb,162968032e11,40a5fc62ed89,ad386509c319,c919e0c958ea,4080802d3065,7dbad7f44bed,6ce444e74ff4,49269108f9bc,e43e08b8dc8a,cd842640ffa4,7a69d2fe64dc,948cb9a68a03,856b8685b318,2012756fac98,95549d40a73f,51981e6562e0`
- top10_refs: `a3342f3ef7c1,600c0103e521,2f301abe0ab1,d7d811b1507a,10d15b75ea9d,2ee170e99c13,6ce5eb6c812b,ecbfb1d5d35c,74a5d66045ce,9c15925ba333`
