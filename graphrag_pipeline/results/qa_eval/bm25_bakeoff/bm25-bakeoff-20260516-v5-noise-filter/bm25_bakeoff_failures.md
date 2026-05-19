# BM25 Bakeoff Failure Cases

- best config: `jieba_course_terms_thuocl_multi_rrf_filtered` k1=`1.5` b=`0.75`
- failures without gold in top10: `2`
- failures without expanded gold in top10: `0`

## Q019 (chapter_summary)

- question: 请概括第三章「处理机调度与死锁」的主线。
- raw_gold_refs: `2b7b30555d10,23baa0a3cf45,27a11a04433a,d5fb4ca2b663,c0f3c202a762`
- expanded_gold_refs: `2b7b30555d10,23baa0a3cf45,27a11a04433a,d5fb4ca2b663,c0f3c202a762,ae144456d81a,880da5723fbf,66666285733e,45646212d185,cda28aa1fac4,04f546377b87,e9fe5c6e1b7a`
- expanded_recall_at_10: `0.4167`
- top_refs: `ae144456d81a,ec52a9ace9b6,e9fe5c6e1b7a,45646212d185,5bef81b4470c,04f546377b87,880da5723fbf,ca310ae37fef,c28e1414f143,34a83931daef`
- top_previews: document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.3 进程调度. subsection... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.2 作业与作业调度. subsect... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.2 作业与作业调度. heading... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.3 进程调度. heading_le... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.2 作业与作业调度. subsect... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.2 作业与作业调度. subsect... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.3 进程调度. subsection... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.3 进程调度. subsection...
- subqueries: `[]`
- subquery_top_refs: `{}`
- rrf_sources: `{}`

## Q031 (global_overview)

- question: 并发控制、同步机制和死锁处理共同服务于什么课程目标？
- raw_gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663`
- expanded_gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663,43580e117b70,81d99ad61e36,f23cc0f41de4,b751bb087d00,fdd5deaf51f6,fa9277086ac8,26dbff780c69,2b7b30555d10`
- expanded_recall_at_10: `0.1667`
- top_refs: `79ed196bc327,014964257784,012adacab0f2,fdd5deaf51f6,e8e5e8c14f8a,c35d5e401730,e74272900408,880da5723fbf,74bf53426d6c,b751bb087d00`
- top_previews: document_type: textbook. chapter: 第八章 磁盘存储器的管理. section: 8.5 数据一致性控制. subsect... || document_type: textbook. chapter: 第一章 操作系统引论. section: 1.4 操作系统的主要功能. subsect... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. heading_le... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.4 进程同步. subsectio... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection...
- subqueries: `[]`
- subquery_top_refs: `{}`
- rrf_sources: `{}`
