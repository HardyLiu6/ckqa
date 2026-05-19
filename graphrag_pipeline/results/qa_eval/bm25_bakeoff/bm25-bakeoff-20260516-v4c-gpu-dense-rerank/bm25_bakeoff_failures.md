# BM25 Bakeoff Failure Cases

- best config: `jieba_course_terms_thuocl_multi_rrf_filtered_section_aware_dense_rerank_top20` k1=`1.5` b=`0.9`
- failures without gold in top10: `1`
- failures without expanded gold in top10: `0`

## Q031 (global_overview)

- question: 并发控制、同步机制和死锁处理共同服务于什么课程目标？
- raw_gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663`
- expanded_gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663,43580e117b70,81d99ad61e36,f23cc0f41de4,b751bb087d00,fdd5deaf51f6,fa9277086ac8,26dbff780c69,2b7b30555d10`
- expanded_recall_at_10: `0.1667`
- top_refs: `79ed196bc327,66666285733e,c28e1414f143,23baa0a3cf45,880da5723fbf,b751bb087d00,fdd5deaf51f6,e8e5e8c14f8a,c5936c24cf3d,1aae8ef34909`
- top_previews: document_type: textbook. chapter: 第八章 磁盘存储器的管理. section: 8.5 数据一致性控制. subsect... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.3 进程调度. subsection... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. heading_le... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.4 进程同步. subsectio... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.4 进程同步. subsectio... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection...
