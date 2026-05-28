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

### `local` — 精确定位

| 示例问题 | 命中信号 |
| --- | --- |
| 请根据第 3 章解释银行家算法的安全性检查过程 | `material_locator`（第 N 章 + 算法 + 过程） |
| 教材里 SCAN 磁盘调度算法的步骤是什么？ | `material_locator`（教材 + 算法 + 步骤 + 磁道隐含） |
| 课件中 FCFS 进程调度在给定到达时间下的平均等待时间怎么算？ | `material_locator`（课件 + 平均等待） |
| 给定页面访问序列 7,0,1,2,0,3,0,4,2,3 和 3 个物理块，LRU 的缺页次数是多少？ | `material_locator`（访问序列 + 缺页） |
| 死锁产生的四个必要条件分别是什么，破坏了哪一个就能预防？ | `material_locator`（条件 + 破坏了哪个） |
| 逻辑地址 0x1A3F 在二级页表下的页号和偏移是多少？ | `material_locator`（逻辑地址 + 页号 + 偏移） |

### `global` — 全局综述

| 示例问题 | 命中信号 |
| --- | --- |
| 请综述操作系统进程管理这一章的知识体系和主题脉络 | `summary_intent`（综述 + 知识体系 + 脉络） |
| 总结一下操作系统这门课的整体框架 | `summary_intent`（总结 + 整体 + 框架） |
| 概括存储管理章节涉及的核心主题 | `summary_intent`（概括 + 主题） |
| 操作系统课程的知识体系是怎样组织的，给我一个 overview | `summary_intent`（知识体系 + overview） |
| 整体上文件系统这一部分包含哪些主题？ | `summary_intent`（整体 + 主题） |

### `drift` — 探索扩展

| 示例问题 | 命中信号 |
| --- | --- |
| 进程和线程有什么区别和联系？ | `exploration_intent`（区别 + 联系） |
| 为什么分页比分段更适合现代操作系统？ | `exploration_intent`（为什么 + 适合） |
| 抢占式调度和非抢占式调度各有什么优缺点？ | `exploration_intent`（优缺点） |
| 自旋锁和互斥量的区别在哪些应用场景下值得权衡？ | `exploration_intent`（区别 + 场景 + 应用） |
| 多级反馈队列调度是怎么从短作业优先和时间片轮转扩展演化出来的？ | `exploration_intent`（扩展 + 迁移） |
| LRU 和 Clock 算法相比有什么瓶颈和局限？ | `exploration_intent`（对比 + 瓶颈 + 局限） |

### `hybrid_v0` — 证据融合（Beta）

> 仅当请求带 `betaHybridEnabled=true` 才会真正下发，否则推荐结果会被改写成 `local`，并附 `hybrid_beta_disabled` reason。

| 示例问题 | 命中信号 |
| --- | --- |
| 请结合教材原文给出依据，比较 SJF 与 SRTN 调度的差异和适用场景 | `evidence_relation_intent` + `evidence_material_fusion`（教材 + 依据 + 比较 + 差异） |
| 死锁预防、避免、检测三种策略的区别是什么？请给出课程证据并交叉验证 | `evidence_relation_intent`（区别 + 证据 + 交叉验证） |
| 分页与分段的优缺点对比，请引用课件出处并交叉验证 | `evidence_relation_intent` + `evidence_material_fusion`（优缺点 + 对比 + 引用 + 出处） |
| 请综合比较自旋锁和互斥量在多核系统中的影响，给出材料依据 | `evidence_relation_intent`（比较 + 影响 + 材料依据） |
| 工作集模型和缺页率两种页面置换控制策略的关系，请给出来源引用作为佐证 | `evidence_relation_intent`（关系 + 来源 + 引用 + 佐证） |

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
