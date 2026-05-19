# BM25 Bakeoff Failure Cases

- best config: `jieba_course_terms` k1=`1.2` b=`0.9`
- failures without gold in top10: `2`

## Q002 (factual_lookup)

- question: 教材列出的操作系统主要目标有哪些？
- gold_refs: `6ce5eb6c812b`
- top_refs: `f7b796b75760,be601c0e13c5,ff60ea8a386b,51981e6562e0,1c106850e081,a9528e6c3626,7b14faf7462f,d7d811b1507a,e5379f40b8b0,0b847347fbd6`
- top_previews: document_type: textbook. chapter: 第一章 操作系统引论. section: 习题. heading_level: 2. ... || document_type: textbook. chapter: 第十二章 保护和安全. section: 习题. heading_level: 2. ... || document_type: textbook. chapter: 第二章 进程的描述与控制. section: 习题. heading_level: 2... || document_type: textbook. chapter: 第九章 操作系统接口. section: 习题. heading_level: 2. ... || document_type: textbook. chapter: 第十一章 多媒体操作系统. section: 习题. heading_level: 2... || document_type: textbook. chapter: 第十章 多处理机操作系统. section: 习题. heading_level: 2... || document_type: textbook. chapter: 第八章 磁盘存储器的管理. section: 习题. heading_level: 2... || document_type: textbook. chapter: 第一章 操作系统引论. section: 1.5 OS结构设计. subsection... || document_type: textbook. chapter: 第九章 操作系统接口. section: 9.2 Shell命令语言. subsect... || document_type: textbook. chapter: 第六章 输入输出系统. section: 习题. heading_level: 2. ...

## Q027 (global_overview)

- question: 处理机管理这条主线如何从进程扩展到线程和调度？
- gold_refs: `014964257784,88e3f6ddcfdc,6119e805a0a2,23baa0a3cf45`
- top_refs: `ef931e4dc568,c4b3aaad6a90,b109a29417ed,23140ef67ae0,c9ded14a3f74,8a7a4a8408b0,c65cd347ad4d,4cd16e844a7f,a6d8a728ef73,0b847347fbd6`
- top_previews: document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. subse... || document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. subse... || document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. subse... || document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. subse... || document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. headi... || document_type: textbook. chapter: 第六章 输入输出系统. section: 6.8 磁盘存储器的性能和调度. subse... || document_type: textbook. chapter: 第十一章 多媒体操作系统. section: 11.6 多媒体存储器的分配方法. su... || document_type: textbook. chapter: 第十一章 多媒体操作系统. section: 11.7 高速缓存与磁盘调度. subs... || document_type: textbook. chapter: 第十一章 多媒体操作系统. section: 11.7 高速缓存与磁盘调度. subs... || document_type: textbook. chapter: 第六章 输入输出系统. section: 习题. heading_level: 2. ...
