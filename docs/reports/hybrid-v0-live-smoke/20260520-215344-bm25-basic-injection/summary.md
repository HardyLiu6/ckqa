# Hybrid v0 Live Smoke - BM25 Basic Injection

- dataDirUri: user_12/kb_5/build_20/index/output
- expectation: v6 + one-shot basic injection + disable synthesis + no local fallback

## Q1. 什么是死锁？

- elapsedSeconds: 121.669
- answerLength: 1186
- sourceCount: 8
- synthesisAttempted: False
- usedLocalFallback: False
- localFallbackEnabled: False
- guardrailStatus: pass
- fallbackReasons: []
- errors: []

Answer preview:

## 死锁的定义与成因 死锁是操作系统中一组进程陷入无限期等待的僵持状态。具体而言，如果一组进程中的每一个进程都在等待仅由该组进程中的其它进程才能引发的事件，那么这组进程就处于死锁状态 [Data: Sources (141)]。这些进程既无法继续运行，也无法释放其占有的资源，导致没有任何进程能够被唤醒。 死锁的产生源于多个进程对资源的激烈争夺。这种争夺既可能发生在竞争不可抢占性资源（如文件、打印机等可重用资源）时，也可能发生在争夺可消耗资源（如报文）时 [Data: Sources (137)]。例如，当两个进程各自占用了一个文件，又同时去请求对方已占用的文件时，双方都会因资源被占用而阻塞，并无限期地等待对方释放，从而形成死锁。 ## 产生死锁的四个必要条件 进程死锁的发生并非偶然，必须同时具备以下四个必要条件，缺一不可 [Data: Sources (141)]： 1.  **互斥条件**：进程对所分配到的资源进行排它性使用

## Q2. 死锁和资源分配图有什么关系？

- elapsedSeconds: 13.975
- answerLength: 0
- sourceCount: 8
- synthesisAttempted: False
- usedLocalFallback: False
- localFallbackEnabled: False
- guardrailStatus: fail
- fallbackReasons: ['basic_empty_answer', 'basic_error', 'basic_guardrail_fail']
- errors: ['graphrag basic embedding failed: Range of input length should be [1, 8192]']

Answer preview:


