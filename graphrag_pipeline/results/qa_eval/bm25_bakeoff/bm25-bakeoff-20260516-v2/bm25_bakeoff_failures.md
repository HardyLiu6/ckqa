# BM25 Bakeoff Failure Cases

- best config: `jieba_course_terms_thuocl_multi_rrf_filtered` k1=`1.2` b=`0.75`
- failures without gold in top10: `2`

## Q002 (factual_lookup)

- question: 教材列出的操作系统主要目标有哪些？
- gold_refs: `6ce5eb6c812b`
- top_refs: `d7d811b1507a,19a6cb0bc22c,e5379f40b8b0,cea83c4f184f,e2a495c80be2,8947f69faaed,7694bb9c99ef,880da5723fbf,80a635231043,04f546377b87`
- top_previews: document_type: textbook. chapter: 第一章 操作系统引论. section: 1.5 OS结构设计. subsection... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.3 多处理机操作系统的特征与分类.... || document_type: textbook. chapter: 第九章 操作系统接口. section: 9.2 Shell命令语言. subsect... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.3 多处理机操作系统的特征与分类.... || document_type: textbook. chapter: 第十二章 保护和安全. section: 12.5 来自系统外部的攻击. subsec... || document_type: textbook. chapter: 第九章 操作系统接口. section: 9.2 Shell命令语言. subsect... || document_type: textbook. chapter: 第十二章 保护和安全. section: 12.4 来自系统内部的攻击. subsec... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第一章 操作系统引论. section: 1.1 操作系统的目标和作用. subsec... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.2 作业与作业调度. subsect...

## Q031 (global_overview)

- question: 并发控制、同步机制和死锁处理共同服务于什么课程目标？
- gold_refs: `8327254fcda2,6c0fe52fc6d8,27a11a04433a,d5fb4ca2b663`
- top_refs: `79ed196bc327,014964257784,012adacab0f2,fdd5deaf51f6,e8e5e8c14f8a,c35d5e401730,e74272900408,74bf53426d6c,880da5723fbf,b751bb087d00`
- top_previews: document_type: textbook. chapter: 第八章 磁盘存储器的管理. section: 8.5 数据一致性控制. subsect... || document_type: textbook. chapter: 第一章 操作系统引论. section: 1.4 操作系统的主要功能. subsect... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. heading_le... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 10.4 进程同步. subsectio... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection... || document_type: textbook. chapter: 第三章 处理机调度与死锁. section: 3.1 处理机调度的层次和调度算法的目标... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 2.4 进程同步. subsection...
