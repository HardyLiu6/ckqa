# 课程路由小型验证集 v1 来源

本验证集用于校准 CKQA 当前本地课程画像路由。题目为中文原创改写，不复制来源原文；来源只用于确定课程主题覆盖面。

## 外部数据源

- `ostep`: Operating Systems: Three Easy Pieces 官方页面，覆盖进程、调度、虚拟内存、线程、同步、I/O、文件系统等操作系统核心主题。https://pages.cs.wisc.edu/~remzi/OSTEP/
- `mit_6s081`: MIT 6.S081 Operating System Engineering 课程资料，覆盖系统调用、虚拟内存、文件系统、线程、锁、进程间通信等主题。https://pdos.csail.mit.edu/6.S081/
- `stanford_cs140`: Stanford CS140 Operating Systems 课程页面，覆盖进程/线程、调度、死锁、内存管理、文件系统等主题。https://web.stanford.edu/~ouster/cgi-bin/cs140-spring19/index.php
- `nyu_os_syllabus`: NYU Introduction to Operating Systems syllabus，补充死锁、虚拟内存、I/O 与文件系统主题。https://engineering.nyu.edu/sites/default/files/2021-06/introduction_to_operating_systems%20%28updated%29.pdf

## 本地数据源

- `local-db`: 当前本地 MySQL 中 active 课程的课程名、简介、知识库名与资料名，用于覆盖非操作系统的本地演示课程。其中内部 smoke 课程只用于确认“不会自动路由到内部课程”，不作为学生正式课程正样本。
- `synthetic-negative`: 人工构造的非课程知识问题，用于约束阈值不要把生活、创作、个人资料或教务时间类问题误判为课程。
