# 课程领域实体与关系抽取规则

本文档用于约束后续 GraphRAG prompt 生成、自动抽取和评测脚本的边界。目标不是构建通用知识图谱，而是构建**面向课程问答**的课程领域图谱。

配套配置：

- `entity_types.json`
- `relation_types.json`

## 1. 适用范围

当前 schema 面向以下课程材料：

1. 教材
2. 课件 / slides
3. 课程大纲
4. 实验指导
5. 课堂笔记
6. 作业 / 习题 / 测验 / 考试资料

当前 schema 的抽取目标是支撑：

1. 课程结构问答
2. 概念解释与术语问答
3. 方法 / 算法 / 公式问答
4. 实验与作业关联问答
5. 课程内知识依赖与先修关系分析

## 2. 应该抽取什么

优先抽取以下四类对象：

### 2.1 教学结构对象

包括：

1. `Course`
2. `Chapter`
3. `Section`

抽取标准：

1. 这些对象必须是稳定的课程结构边界。
2. 标题应优先来自标准化后的 `chapter`、`section`、`subsection`、`heading_path`、`heading_level`。
3. 目录页中的伪标题、版权页栏目、装饰性标题不抽取。

### 2.2 知识内容对象

包括：

1. `KnowledgePoint`
2. `Concept`
3. `Term`
4. `FormulaOrDefinition`
5. `AlgorithmOrMethod`

抽取标准：

1. 必须能服务于课程问答或课程考核。
2. 必须是稳定可复用的知识对象，而不是一次性描述。
3. 必须能在课程内被解释、比较、应用、考核或引用。

### 2.3 学习活动对象

包括：

1. `Experiment`
2. `Assignment`

抽取标准：

1. 必须是明确的实验、作业、报告、习题、题组、课程项目或测验对象。
2. 必须具备稳定标题或任务标识。
3. 仅有“请按时提交”“本周完成作业”等通知性语言时，不抽取。

### 2.4 工具与平台对象

包括：

1. `ToolOrPlatform`

抽取标准：

1. 必须是课程中实际用于实现、实验、演示、部署或分析的工具、平台、软件或环境。
2. 仅当它对课程内容、实验流程或实现方案有实质作用时才抽取。

## 3. 不应该抽取什么

以下内容默认不抽取为实体：

### 3.1 课程行政与通知信息

包括但不限于：

1. 上课时间调整
2. 截止时间提醒
3. QQ 群、微信群、雨课堂通知
4. 教室、地点、签到要求
5. 任课教师联系方式

例外：

1. 如果通知正文中包含稳定的 `Assignment` 或 `Experiment` 标题，可以抽取该任务实体。
2. 但通知本身不作为实体。

### 3.2 无意义短语与话语组织用语

包括但不限于：

1. `如下图所示`
2. `由此可见`
3. `本章内容`
4. `本节开始`
5. `有关内容见上文`
6. `重点掌握`
7. `请同学们思考`

处理原则：

1. 这些短语不能单独作为 `KnowledgePoint`、`Concept`、`Term` 或其它知识实体。
2. 如果它们所在的标题本身是正式结构标题，可保留为 `Section`，但不要把标题里的空泛词语再拆成额外实体。

### 3.3 图表残片与排版碎片

包括但不限于：

1. 单独出现的图号、表号，如 `图 3-1`、`表 2-3`
2. 孤立表格单元格值
3. 轴标签、页眉页脚、页码
4. OCR 造成的公式残片、乱码、控制字符
5. 没有语义名字的碎片化 LaTeX 符号

处理原则：

1. 图表残片默认不抽取。
2. 若图题、表题、图注、表注与正文共同表达了稳定知识对象，可以抽取该对象，但实体名应使用知识对象本身，而不是图号表号。

### 3.4 课程外低价值信息

包括但不限于：

1. 出版社、ISBN、印次、版权说明
2. 参考文献编号
3. 下载链接、文件路径、普通文件名
4. 与课程知识目标无关的人名、机构名、地名

## 4. 实体类型边界

### 4.1 KnowledgePoint 与 Concept 的区别

1. `KnowledgePoint` 更偏教学目标、考点和课程掌握单元。
2. `Concept` 更偏理论概念、机制和对象本身。

建议：

1. 如果一个对象主要用于表达“本课程要掌握什么”，优先归为 `KnowledgePoint`。
2. 如果一个对象主要用于表达“它是什么、有什么性质”，优先归为 `Concept`。

示例：

1. `进程调度` 更像 `KnowledgePoint`
2. `进程`、`线程` 更像 `Concept`

### 4.2 Concept 与 Term 的区别

1. `Concept` 是可被独立解释的语义对象。
2. `Term` 是术语、缩写、符号或命名单位。

建议：

1. 如果一个对象既可视为概念又可视为术语，优先保留为 `Concept`。
2. `Term` 主要用于缩写、英文名、符号名或专有短语的归一化。

示例：

1. `TLB` 更适合 `Term`
2. `虚拟内存` 更适合 `Concept`

### 4.3 FormulaOrDefinition 的边界

应抽取：

1. 有稳定名字的定义
2. 有稳定名字的公式
3. 有稳定名字的定律、判定条件

不应抽取：

1. 没有标题、没有上下文语义的孤立公式碎片
2. 纯变量列表
3. 图中的残缺数学符号

### 4.4 AlgorithmOrMethod 的边界

应抽取：

1. 算法
2. 策略
3. 机制
4. 协议
5. 实验方法

不应抽取：

1. 一般性动作动词
2. 一次性的课堂说明步骤
3. 没有固定名称的临时操作描述

## 5. 别名、缩写、重复实体处理

### 5.1 别名与缩写

规则：

1. 优先保留课程内最稳定的正式名称作为 canonical name。
2. 缩写、英文名、全称与简称默认视为同一实体的别名，不拆成多个实体。
3. 若课程文本同时出现全称和缩写，后续脚本应将缩写保留在 alias 列表中。

示例：

1. `Translation Lookaside Buffer` 与 `TLB` 合并
2. `Process Control Block` 与 `PCB` 合并

### 5.2 重复实体

规则：

1. 同一课程内，相同类型、相同 canonical name、语义一致的对象应合并。
2. 结构实体合并时必须结合 `course_id` 和层级上下文，不能只看名字。
3. 当前 schema 默认**不跨课程合并**同名实体。

### 5.3 一词多义

规则：

1. 只有在课程上下文能明确区分语义时，才允许拆成多个实体。
2. 若无法可靠区分，优先保守合并，并在描述中保留上下文说明。

## 6. 章节层级处理规则

### 6.1 结构字段优先级

后续脚本在构造结构实体时，应优先使用现有标准字段：

1. `course_id`
2. `chapter`
3. `section`
4. `subsection`
5. `heading_level`
6. `heading_path`
7. `heading_path_text`

### 6.2 Chapter 与 Section 的层级映射

规则：

1. `Chapter` 对应顶层结构单元，通常来自章级标题。
2. `Section` 统一覆盖节、小节、专题、实验步骤等所有非章级结构。
3. 当前 schema 不单独设置 `Subsection` 类型。
4. 如果存在节中套小节，应通过 `Section -> Section` 的 `contains` / `belongs_to` 表达层级。

### 6.3 编号保留

规则：

1. 章节和节标题中的有效编号必须保留。
2. 目录页页码、点线、装饰符号必须去掉。

正确示例：

1. `第二章 进程的描述与控制`
2. `2.1 前趋图和程序执行`

错误示例：

1. `第二章 进程的描述与控制 ..... 32`
2. `2.1 前趋图和程序执行 15`

## 7. 关系抽取规则

### 7.1 只抽稳定关系

关系必须满足以下条件之一：

1. 文本显式陈述
2. 同一结构单元内可稳定推断
3. 对课程问答或评测有直接价值

仅仅因为同段共现，不足以建立关系。

### 7.2 优先选择最具体关系

当一个实体对可能对应多个关系时，优先级如下：

1. `contains` / `belongs_to`
2. `defined_by`
3. `prerequisite_of`
4. `depends_on`
5. `implemented_by`
6. `applied_in`
7. `evaluated_by`
8. `related_to`
9. `appears_in`

说明：

1. `appears_in` 只在缺乏更强语义时使用。
2. `related_to` 是保底关系，不应成为默认关系，也不能用 `related_to` 代替缺失端点。

### 7.2.1 关系端点完整性

规则：

1. 所有关系的 `source` 和 `target` 必须能在 `entities` 中找到。
2. source/target 必须都在 `entities` 中，缺 target 时补实体或跳过。
3. 如果关系端点在原文中证据充足但尚未输出实体，应先补齐该实体，再输出关系。
4. 如果无法补齐端点实体，应跳过该关系。
5. 不能输出 `<missing>`、`unknown`、`N/A`、空字符串或临时占位关系。
6. 关系端点应使用实体标题，不要用章节字段、页码、行号或说明性短语临时占位。

错误示例：

1. `进程 related_to <missing>`
2. `<missing> defined_by 响应比公式`
3. `银行家算法 appears_in unknown`

### 7.2.2 related_to 的端点完整性

规则：

1. `related_to` 只能连接两个已经抽取出来的实体。
2. 不能用 `related_to` 代替缺失端点；如果 target 证据充足，应先补齐 target 实体。
3. 如果无法补齐端点实体，应跳过该关系。
4. source/target 必须都在 `entities` 中，缺 target 时补实体或跳过。

错误示例：

1. `进程 related_to <missing>`
2. `磁盘高速缓存 related_to 文件访问速度`，但 `文件访问速度` 没有输出实体。
3. `RAID related_to 系统容错`，但 `系统容错` 没有输出实体。

### 7.3 contains 与 belongs_to

规则：

1. 如果文本表达为“X 包含 Y”“本章介绍 Y”，优先抽 `contains`。
2. 如果文本表达为“Y 属于 X”“Y 位于第 3 章”，可抽 `belongs_to`。
3. 后续脚本可以根据需要自动补齐反向边，但原始抽取不要求强制双向输出。
4. `belongs_to` 的目标只能是 `Course`、`Chapter`、`Section` 这类结构容器。
5. 只有当 `contains.source_type` 是 `Course`、`Chapter`、`Section` 时，才允许自动派生反向 `belongs_to`。
6. 知识对象之间的 `contains` 只在原文明示分类、组成或步骤分解时使用，不派生反向 `belongs_to`。
7. 共现、同段出现、主题相关不构成 `contains`。
8. 知识对象之间不要用 `belongs_to` 表达上下位、组成或相关；应按证据改用 `contains`、`depends_on`、`applied_in`、`related_to`，证据不足则跳过。

正确示例：

1. `媒体 contains 感觉媒体`，因为原文明确列出“媒体可分为以下六类”。
2. `分页存储管理 contains 页面`，因为原文在方法内明确分解页面和物理块。

错误示例：

1. `关键字 contains 记录`，仅因“关键字标识记录”同句出现，不是组成关系。
2. `概念A belongs_to 概念B`，`belongs_to` 不能指向知识对象。
3. `死锁 belongs_to 资源分配图`，这是 `Concept->Concept` 的知识对象关系，不能用 `belongs_to`。

### 7.3.1 defined_by 与 alias 的边界

规则：

1. `defined_by` 指向 `FormulaOrDefinition` 时，用于正式定义、公式、定律或判定条件。
2. `defined_by` 指向 `Term` 时，`Term` 必须承担符号、变量、参数或公式记号角色。
3. 英文全称、简称、缩写、别名解释不使用 `defined_by`，应进入实体 alias 或归一化字段。
4. 禁止 `Concept->Concept` 和 `Term->Concept` 使用 `defined_by`。
5. 存在标志、背景解释、普通说明、命名别名和简称展开不能用 `defined_by`。
6. 别名、简称、缩写、编号、存在标志不建立 `defined_by`，进入 alias / 归一化字段，或直接跳过。

正确示例：

1. `工作集 defined_by Δ`
2. `响应比 defined_by HRN`

错误示例：

1. `PCB defined_by Process Control Block`
2. `TLB defined_by Translation Lookaside Buffer`
3. `进程 defined_by process`
4. `进程 defined_by 线程`，这是 `Concept->Concept`。
5. `PCB defined_by 进程控制块`，这是 `Term->Concept` 或别名展开。
6. `存在标志 defined_by present`，存在标志不是正式定义、公式、符号、变量或参数。

### 7.4 depends_on 与 prerequisite_of

规则：

1. `depends_on` 用于理解、实现、推导和求解上的依赖。
2. `prerequisite_of` 用于教学先修、学习顺序或实验前置要求。
3. 若两者都成立，优先看文本是否强调“先学/先做”。

### 7.5 applied_in 的方向边界

规则：

1. `applied_in` 表示算法、方法、公式或知识对象被应用到某知识主题、实验、作业、章节讲解或平台操作场景。
2. 当文本表达“X 以 Y 为例/使用 Y”时，通常应输出 `Y applied_in X` 或 `X depends_on Y`。
3. 如果 Y 是算法、方法、公式或知识对象，并且语义是 Y 用来解释或处理 X，输出 `Y applied_in X`。
4. 如果语义是 X 的理解、实现或求解依赖 Y，输出 `X depends_on Y`。
5. 不能反向输出 `X applied_in Y`，也不要为了修评分把 schema 放宽为 `Concept->AlgorithmOrMethod`。

正确示例：

1. `银行家算法 applied_in 死锁`
2. `地址映射 applied_in 分页存储管理`

错误示例：

1. `死锁 applied_in 银行家算法`，这是反向的 `Concept->AlgorithmOrMethod`。
2. `本节 applied_in 银行家算法`，结构标题不能反向应用到算法。

### 7.6 evaluated_by 的端点边界

规则：

1. `evaluated_by` 表示课程、章节、节、知识点、概念、术语、方法或实验被作业、测验、题组或实验任务评估。
2. source 应为 `Course`、`Chapter`、`Section`、`KnowledgePoint`、`Concept`、`Term`、`AlgorithmOrMethod` 或 `Experiment`。
3. target 应为 `Assignment` 或 `Experiment`。
4. 如果术语本身就是题目考核对象，允许 `Term->Assignment`，例如习题直接询问 `PCB` 的作用和组织方式。
5. 但别名展开、英文全称解释、普通出现位置不能误判为 `evaluated_by`。

正确示例：

1. `死锁处理 evaluated_by 作业 3`
2. `文件系统概念 evaluated_by 文件系统设计实验`
3. `PCB evaluated_by 第二章习题`

错误示例：

1. `习题 evaluated_by 进程`，这是反向 `Assignment->Concept`。
2. `习题 evaluated_by PCB`，这是反向 `Assignment->Term`。
3. `TLB evaluated_by 习题 3`，如果只是普通出现而不是题目考核对象，应使用 `appears_in` 或跳过。

### 7.7 appears_in 的边界

规则：

1. 当实体只是出现在某章、某节、某实验、某题组或平台上下文中，但没有更强关系时，用 `appears_in`。
2. 如果能判断“知识点用于实验”，应使用 `applied_in` 而不是 `appears_in`。
3. 如果能判断“知识点被作业考核”，应使用 `evaluated_by` 而不是 `appears_in`。
4. `appears_in` 的目标只能是 `Course`、`Chapter`、`Section`、`Experiment`、`Assignment` 或 `ToolOrPlatform`，不能指向另一个知识对象。
5. source 必须是出现的知识实体，target 必须是 `Course`、`Chapter`、`Section`、`Experiment`、`Assignment` 或 `ToolOrPlatform`。
6. 禁止反向 `Section appears_in Concept`，也禁止 `Section/Assignment appears_in 知识对象`。
7. 如果结构单元讲授或包含知识对象，优先使用 `contains`。
8. 平台/工具上下文中允许 `Concept/Term/AlgorithmOrMethod -> ToolOrPlatform`，例如 `系统调用 appears_in Linux`。

错误示例：

1. `第三章 存储器管理 appears_in TLB`，这是反向 `Section appears_in Concept`。
2. `实验一 appears_in 银行家算法`，这是反向或端点类型错误。
3. `虚拟内存 appears_in 页面置换算法`，目标不是课程结构或学习活动容器。
4. `习题 1 appears_in SPOOLing 系统`，这是反向 `Assignment appears_in ToolOrPlatform`。
5. `Linux appears_in 系统调用`，这是反向 `ToolOrPlatform->Concept`。

### 7.8 related_to 与 implemented_by 的端点边界

规则：

1. `related_to` 只能连接两个已经抽取出来的实体。
2. 不能用 `related_to` 代替缺失端点；如果 target 证据充足，应先补齐 target 实体。
3. 如果无法补齐 source 或 target，跳过该关系。
4. `implemented_by` 的目标必须是可执行方法或工具平台，即 `AlgorithmOrMethod` 或 `ToolOrPlatform`。
5. 如果目标只是概念、属性、原则或效果，不要用 `implemented_by`，应改用 `depends_on`、`applied_in`、`related_to` 或跳过。

错误示例：

1. `磁盘高速缓存 related_to 文件访问速度`，但 `文件访问速度` 没有输出实体。
2. `RAID related_to 系统容错`，但 `系统容错` 没有输出实体。
3. `高响应比优先调度算法 implemented_by 动态优先级`，`动态优先级` 是概念，不是实现方法或工具平台。

## 8. 对课程通知、无意义短语、图表残片的处理

### 8.1 课程通知

默认不抽取：

1. 停课通知
2. 时间调整
3. 提交提醒
4. 分组通知
5. 课堂事务说明

可保留的例外：

1. 通知中出现了稳定任务标题，如 `作业 2：进程同步分析`
2. 通知中出现了稳定实验标题，如 `实验三 文件系统实现`

### 8.2 无意义短语

默认不抽取：

1. `如下图所示`
2. `由此得到`
3. `本章小结`
4. `思考题`
5. `习题`

补充说明：

1. `本章小结`、`思考题`、`习题` 若是正式结构标题，可保留为 `Section`。
2. 但不要把这些词本身抽成 `KnowledgePoint` 或 `Concept`。

### 8.3 图表残片

默认不抽取：

1. 单个单元格
2. 单个坐标轴标签
3. 单个变量名
4. 没有上下文的图号表号

可抽取的例外：

1. 图题或表题明确给出了稳定概念、方法、实验对象名称。
2. 正文与图注共同说明了一个课程领域对象。

此时应抽取课程对象本身，不应抽取图号或表号。

## 9. 对后续 prompt 生成脚本的约束

后续脚本在生成抽取 prompt 时，建议遵守：

1. 先读取 `entity_types.json` 中的 `entity_type_order`
2. 再按顺序拼接每个实体类型的 `description`、`canonical_name_rule`、`positive_signals`、`negative_signals`
3. 关系 prompt 同理读取 `relation_types.json`
4. 将当前文档的 `course_id`、`document_type`、`chapter`、`section`、`subsection`、`heading_path_text`、`page_start`、`page_end` 一并注入上下文
5. 默认以“课程内去重、课程内建图”为边界，不做跨课程自动对齐
