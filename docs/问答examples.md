# 问答路由五种检索模式与示例（操作系统课程）

> 来源：`backend/ckqa-back/src/main/java/org/ysu/ckqaback/qa/routing/QaModeRoutingService.java`
> 策略版本：`rule_semantic_v1`
> 适用：学生端 QA 智能推荐与管理端路由复核

## 一、模式速览

| 模式 | 中文名 | 适用问题特征 | 主要触发信号（关键词/正则） | Beta 依赖 | 兜底回退 |
| --- | --- | --- | --- | --- | --- |
| `basic` | 快速问答 | 定义、概念、单点事实，问题较短 | 什么是 / 是什么 / 定义 / 概念 / 含义 / 简述 / 解释一下 / 介绍一下 | 否 | 默认模式 |
| `local` | 精确定位 | 指向具体材料、章节、算法步骤、计算题 | 第 N 章节讲页 / 教材 / 课件 / 原文 / 公式 / 例题 / 算法 / 步骤 / 机制 / 条件 / 平均等待 / 响应比 / 页号 / 偏移 / 缺页 / 磁道 / 逻辑/物理地址 / 访问序列 / 属于哪类 / 破坏了哪个 | 否 | 自身 |
| `global` | 全局综述 | 课程整体脉络、章节主题概览 | 综述 / 概括 / 总结 / 整体 / 全局 / 主题 / 脉络 / 知识体系 / 框架 / overview | 否 | 自身 |
| `drift` | 探索扩展 | 关联、对比、迁移、原因、应用 | 关联 / 联系 / 扩展 / 延伸 / 迁移 / 类似 / 对比 / 比较 / 区别 / 异同 / 优缺点 / 特点 / 作用 / 场景 / 影响 / 应用 / 探索 / 为什么 / 为何 / 原因 / 适合 / 瓶颈 / 局限 / 开销 / 设计 | 否 | 自身 |
| `hybrid_v0` | 证据融合（Beta） | 同时要求"对比/关系"与"证据/出处" | 证据 / 依据 / 来源 / 引用 / 出处 / 佐证 / 交叉验证 / 可靠 ＋ 关系/比较/对比 类词 | **是**：`betaHybridEnabled=true`，否则回退 | `local` |

## 二、操作系统课程问题示例

### `basic` — 快速问答

| 示例问题 | 命中信号 |
| --- | --- |
| 什么是信号量？ | `definition_intent`，短问题加权 |
| 进程的定义是什么？ | `definition_intent` |
| 简述 PCB 的含义。 | `definition_intent` |
| 解释一下临界区。 | `definition_intent` |
| 介绍一下虚拟内存的概念。 | `definition_intent` |
| 什么是系统调用？ | `definition_intent` |
| 什么是中断？ | `definition_intent`，短问题加权 |
| 线程是什么？ | `definition_intent`，短问题加权 |
| 简述文件控制块 FCB 的作用。 | `definition_intent` |
| 什么是页面置换？ | `definition_intent` |
| 解释一下缓冲区的含义。 | `definition_intent` |
| 什么是设备驱动程序？ | `definition_intent` |
| 简述分时系统的概念。 | `definition_intent` |

### `local` — 精确定位

| 示例问题 | 命中信号 |
| --- | --- |
| 请根据第 3 章解释银行家算法的安全性检查过程 | `material_locator`（第 N 章 + 算法 + 过程） |
| 教材里 SCAN 磁盘调度算法的步骤是什么？ | `material_locator`（教材 + 算法 + 步骤 + 磁道隐含） |
| 课件中 FCFS 进程调度在给定到达时间下的平均等待时间怎么算？ | `material_locator`（课件 + 平均等待） |
| 给定页面访问序列 7,0,1,2,0,3,0,4,2,3 和 3 个物理块，LRU 的缺页次数是多少？ | `material_locator`（访问序列 + 缺页） |
| 死锁产生的四个必要条件分别是什么，破坏了哪一个就能预防？ | `material_locator`（条件 + 破坏了哪个） |
| 逻辑地址 0x1A3F 在二级页表下的页号和偏移是多少？ | `material_locator`（逻辑地址 + 页号 + 偏移） |
| 请根据第 2 章说明信号量 P/V 操作如何实现进程互斥。 | `material_locator`（第 N 章 + 机制） |
| 课件里生产者-消费者问题的同步关系和互斥关系分别是什么？ | `material_locator`（课件 + 关系 + 机制） |
| 给定磁道访问序列 98,183,37,122,14,124,65,67，SSTF 的移动磁道数怎么算？ | `material_locator`（访问序列 + 磁道 + 算法） |
| 教材中 FIFO 页面置换算法的例题是怎样计算缺页率的？ | `material_locator`（教材 + 例题 + 缺页率） |
| 第 4 章里页表、快表和地址转换过程的步骤是什么？ | `material_locator`（第 N 章 + 步骤 + 地址转换） |
| 课件中目录项和 FCB 的区别在哪一页讲到？ | `material_locator`（课件 + 页号 + 区别） |
| 请按教材原文解释索引文件的查找过程。 | `material_locator`（教材 + 原文 + 过程） |
| I/O 中断处理流程在课件中的步骤是什么？ | `material_locator`（I/O + 课件 + 步骤） |

### `global` — 全局综述

| 示例问题 | 命中信号 |
| --- | --- |
| 请综述操作系统进程管理这一章的知识体系和主题脉络 | `summary_intent`（综述 + 知识体系 + 脉络） |
| 总结一下操作系统这门课的整体框架 | `summary_intent`（总结 + 整体 + 框架） |
| 概括存储管理章节涉及的核心主题 | `summary_intent`（概括 + 主题） |
| 操作系统课程的知识体系是怎样组织的，给我一个 overview | `summary_intent`（知识体系 + overview） |
| 整体上文件系统这一部分包含哪些主题？ | `summary_intent`（整体 + 主题） |
| 请总结第一章操作系统引论从目标、作用到发展历程的整体脉络。 | `summary_intent`（总结 + 整体 + 脉络） |
| 概括进程管理章节从进程概念到调度与同步的知识框架。 | `summary_intent`（概括 + 知识框架） |
| 请综述存储管理中连续分配、分页、分段和虚拟存储之间的主题关系。 | `summary_intent`（综述 + 主题 + 关系） |
| 按章节梳理进程管理、存储管理、文件系统和设备管理之间的主线。 | `summary_intent`（梳理 + 主线） |
| 请从管理对象、核心机制、典型算法三个角度总结 I/O 设备管理章节。 | `summary_intent`（总结 + 章节） |
| 文件系统章节从文件组织到磁盘空间管理，整体知识结构是什么？ | `summary_intent`（整体 + 知识结构） |
| 请概括操作系统课程中“资源管理”这一主线贯穿了哪些内容。 | `summary_intent`（概括 + 主线） |
| 给我一个操作系统期末复习的全局知识地图。 | `summary_intent`（全局 + 知识地图） |

### `drift` — 探索扩展

| 示例问题 | 命中信号 |
| --- | --- |
| 进程和线程有什么区别和联系？ | `exploration_intent`（区别 + 联系） |
| 为什么分页比分段更适合现代操作系统？ | `exploration_intent`（为什么 + 适合） |
| 抢占式调度和非抢占式调度各有什么优缺点？ | `exploration_intent`（优缺点） |
| 自旋锁和互斥量的区别在哪些应用场景下值得权衡？ | `exploration_intent`（区别 + 场景 + 应用） |
| 多级反馈队列调度是怎么从短作业优先和时间片轮转扩展演化出来的？ | `exploration_intent`（扩展 + 迁移） |
| LRU 和 Clock 算法相比有什么瓶颈和局限？ | `exploration_intent`（对比 + 瓶颈 + 局限） |
| 为什么死锁避免通常比死锁预防更灵活，但运行时开销也更高？ | `exploration_intent`（为什么 + 灵活 + 开销） |
| 内存局部性原理和页面置换算法之间有什么联系？ | `exploration_intent`（联系） |
| 文件系统目录结构和数据库索引在设计目标上有什么异同？ | `exploration_intent`（异同 + 设计） |
| 磁盘调度算法为什么需要在吞吐量和公平性之间权衡？ | `exploration_intent`（为什么 + 权衡） |
| 分页、分段和段页式管理分别适合解决什么设计问题？ | `exploration_intent`（适合 + 设计） |
| 用户态和内核态的切换对系统调用性能有什么影响？ | `exploration_intent`（影响） |
| 读写锁和互斥锁相比，在什么场景下更合适？ | `exploration_intent`（相比 + 场景 + 合适） |
| 虚拟内存为什么能让程序看起来拥有比物理内存更大的地址空间？ | `exploration_intent`（为什么 + 原因） |

### `hybrid_v0` — 证据融合（Beta）

> 仅当请求带 `betaHybridEnabled=true` 才会真正下发，否则推荐结果会被改写成 `local`，并附 `hybrid_beta_disabled` reason。

| 示例问题 | 命中信号 |
| --- | --- |
| 请结合教材原文给出依据，比较 SJF 与 SRTN 调度的差异和适用场景 | `evidence_relation_intent` + `evidence_material_fusion`（教材 + 依据 + 比较 + 差异） |
| 死锁预防、避免、检测三种策略的区别是什么？请给出课程证据并交叉验证 | `evidence_relation_intent`（区别 + 证据 + 交叉验证） |
| 分页与分段的优缺点对比，请引用课件出处并交叉验证 | `evidence_relation_intent` + `evidence_material_fusion`（优缺点 + 对比 + 引用 + 出处） |
| 请综合比较自旋锁和互斥量在多核系统中的影响，给出材料依据 | `evidence_relation_intent`（比较 + 影响 + 材料依据） |
| 工作集模型和缺页率两种页面置换控制策略的关系，请给出来源引用作为佐证 | `evidence_relation_intent`（关系 + 来源 + 引用 + 佐证） |
| 请引用教材和课件依据，比较 FIFO、LRU、Clock 三种页面置换算法的优缺点。 | `evidence_relation_intent` + `evidence_material_fusion`（引用 + 依据 + 比较 + 优缺点） |
| 请用课程材料佐证分页和分段在地址转换方式上的差异。 | `evidence_relation_intent`（材料佐证 + 差异） |
| 请结合两处材料验证银行家算法为什么可以避免死锁，并说明它和死锁预防的区别。 | `evidence_relation_intent`（验证 + 区别 + 材料） |
| 请引用课件出处，对比连续分配、链接分配和索引分配三种文件存储方式。 | `evidence_relation_intent` + `evidence_material_fusion`（引用 + 出处 + 对比） |
| 请给出课程来源，说明进程调度和内存调度在“资源分配”上的共同点与差异。 | `evidence_relation_intent`（来源 + 共同点 + 差异） |
| 请交叉验证教材中关于中断、陷入和系统调用关系的表述。 | `evidence_relation_intent`（交叉验证 + 关系 + 教材） |
| 请结合材料依据说明 SCAN 和 C-SCAN 磁盘调度算法为何会影响公平性。 | `evidence_relation_intent`（材料依据 + 影响） |
| 请引用原文比较顺序文件、索引文件和索引顺序文件的适用场景。 | `evidence_relation_intent` + `evidence_material_fusion`（引用原文 + 比较 + 场景） |

## 三、其他打分细则

| 细则 | 触发条件 | 影响 |
| --- | --- | --- |
| 短定义加权 | `length<=24` 且只命中 `definition`，未命中 material/summary/exploration | `basic +0.12` |
| 长证据关系加权 | `length>=42` 且同时命中 `relation` 与 `evidence` | `hybrid_v0 +0.18` |
| 上下文跟进 | `hasConversationContext=true` 且命中代词（它/这个/上述/前者/刚才/继续） | `local +0.16`，`hybrid_v0 +0.20`；纯关系跟进则改加权 `drift +0.36` |
| 证据 + 跟进 | 同时命中 `evidence` 与 `follow_up` | `hybrid_v0 +0.24` |
| 探索 + 关系 + 材料 + 无证据 | 同时命中 exploration / relation / material 且无 evidence | `drift +0.24` |
| Hybrid Beta 关闭 | 命中 `hybrid_v0` 但 `betaHybridEnabled=false` | 推荐改写成 `bestNonHybridMode`，reason 追加 `hybrid_beta_disabled` |
| 兜底 fallback | 命中 `evidence_seeking` 或 `evidence_relation_intent` | `fallbackMode=local`，否则取非 hybrid 的次优模式 |

## 四、置信度区间

| 区间 | 范围 | 含义 |
| --- | --- | --- |
| `high_confidence` | `>= 0.65` | 直接采纳推荐结果 |
| `low_confidence` | `0.50 <= confidence < 0.65` | 标记 `manualSwitchSuggested=true`，并按 `reviewPriority` 进入管理端复核列表 |
| `insufficient` | `< 0.50` | 走 fallback，建议人工复核 |

> 置信度计算：`0.55 + min(0.39, max(0, top - second) * 0.35)`，与最高分和次高分的差距相关。

## 五、参考

- 全量回归集 152 条：`graphrag_pipeline/data/eval/qa_routing_evaluation_set.jsonl`（边界/负样本另有 80 条专项集）
- 路由 smoke 矩阵脚本：`backend/ckqa-back/scripts/run_qa_routing_smoke_matrix.py`
- 设计文档：`docs/superpowers/plans/2026-05-19-qa-smart-routing-confidence-smoke-frontend.md`
- 相关后端测试：`QaModeRoutingServiceTest`、`QaModeRoutingEvaluationTest`、`QaRoutingControllerWebMvcTest`
