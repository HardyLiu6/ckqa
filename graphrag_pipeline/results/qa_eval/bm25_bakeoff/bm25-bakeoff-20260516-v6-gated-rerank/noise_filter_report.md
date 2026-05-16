# Text Unit Noise Filter Report

- scope: sidecar only; GraphRAG parquet / LanceDB outputs are not modified.
- original text units: `533`
- active blacklist refs: `7`
- search text units after filter: `526`

| ref | issue | preview | active |
| --- | --- | --- | --- |
| 22fb5c469642 | replacement_character | document_type: textbook. chapter: 第一章 操作系统引论. section: 1.2 操作系统的发展过程. subsection: 1.2.5 实时系统(Real Time System). heading_level: 3. heading_path_text: 第一章 操作系统... | yes |
| fa9277086ac8 | repeated_placeholder_text | document_type: textbook. chapter: 第二章 进程的描述与控制. heading_level: 1. heading_path_text: 第二章 进程的描述与控制. page_start: 40. page_end: 40. section_level: 1. source_fil... | yes |
| 2b7b30555d10 | repeated_placeholder_text | document_type: textbook. chapter: 第三章 处理机调度与死锁. heading_level: 1. heading_path_text: 第三章 处理机调度与死锁. page_start: 93. page_end: 93. section_level: 1. source_fil... | yes |
| 085c2bd3447b | repeated_placeholder_text | document_type: textbook. chapter: 第四章 存储器管理. heading_level: 1. heading_path_text: 第四章 存储器管理. page_start: 128. page_end: 128. section_level: 1. source_file: 计... | yes |
| 4a50381c2dfe | replacement_character | document_type: textbook. chapter: 第六章 输入输出系统. section: 6.7 缓冲区管理. subsection: 6.7.4 缓冲池(Buffer Pool). heading_level: 3. heading_path_text: 第六章 输入输出系统 > 6.7 缓... | yes |
| 84d64bd5b229 | repeated_placeholder_text | document_type: textbook. chapter: 第八章 磁盘存储器的管理. heading_level: 1. heading_path_text: 第八章 磁盘存储器的管理. page_start: 258. page_end: 258. section_level: 1. source_f... | yes |
| caf27acac921 | replacement_character | document_type: textbook. chapter: 第十一章 多媒体操作系统. section: 11.6 多媒体存储器的分配方法. subsection: 11.6.2 帧索引存放方式. heading_level: 3. heading_path_text: 第十一章 多媒体操作系统 > 11... | yes |
