# 小规模 Audit 标注统计摘要

- 生成时间：`2026-04-15T19:16:20+08:00`
- 来源文件：[audit_extraction_set.json](/home/sunlight/Projects/ckqa/graphrag_pipeline/data/eval/audit_extraction_set.json)
- 关联抽样报告：[audit_sampling_report.json](/home/sunlight/Projects/ckqa/graphrag_pipeline/results/reports/audit_sampling_report.json)

## 总览

- audit 样本数：`20`
- 已完成人工标注：`20/20`
- 审核结论：`accepted=20`
- 置信度分布：`high=11`，`medium=9`
- 实体总数：`184`
- 关系总数：`168`
- 平均每条样本实体数：`9.2`
- 平均每条样本关系数：`8.4`
- 平均文本长度：`1155.45`
- 平均页跨度：`1.95`

## 覆盖情况

- 章节覆盖：`13` 个章节/前言单元
- 来源文件：`1` 个
- 文档类型：`textbook=20`
- audit 优先级：`high=15`，`medium=3`，`low=2`
- 样本类型：
  - `definition_or_formula=9`
  - `assignment_requirement=6`
  - `algorithm_or_method=4`
  - `chapter_concept_explanation=1`
- 标题层级：
  - `heading_level=3`：`10`
  - `heading_level=2`：`8`
  - `heading_level=1`：`2`
- 含 `FormulaOrDefinition` 的样本：`6`
- 含 `Assignment` 的样本：`6`

## 标注分布

实体类型分布：

- `Concept=95`
- `AlgorithmOrMethod=32`
- `Section=23`
- `Chapter=19`
- `FormulaOrDefinition=7`
- `Assignment=6`
- `ToolOrPlatform=2`

未使用的实体类型：

- `Course`
- `KnowledgePoint`
- `Term`
- `Experiment`

关系类型分布：

- `contains=55`
- `related_to=42`
- `evaluated_by=37`
- `depends_on=14`
- `implemented_by=13`
- `defined_by=7`

未使用的关系类型：

- `belongs_to`
- `prerequisite_of`
- `applied_in`
- `appears_in`

## 二审后结论

- 所有样本都已从 `accepted_with_notes` 收紧为 `accepted`。
- 当前仍保留 `9` 条 `medium` 置信度样本，但它们主要是：
  - 章末综合 `习题`，题面覆盖多个子主题
  - `前言` 这类混有出版信息的边界样本
  - 存在局部 OCR 可疑项的样本
- 这些 `medium` 样本并不是未完成标注，而是保留了更谨慎的审稿置信度。

## 需要重点关注的 9 条 Medium 样本

- `audit-ext-0004-6f6c12c93a`
  - `第三章 处理机调度与死锁 > 习题`
  - 章级综合作业样本，gold 使用代表性考点锚点而非题面穷举。
- `audit-ext-0009-54f6ca566e`
  - `第八章 磁盘存储器的管理 > 习题`
  - 题组跨文件组织、容错与恢复等主题，保留稳定审计锚点。
- `audit-ext-0010-9b897c11e7`
  - `第十一章 多媒体操作系统 > 11.2 多媒体文件中的各种媒体 > 11.2.2 图像`
  - 存在单处 OCR 可疑格式名 `GIP`，已继续排除在 gold 外。
- `audit-ext-0012-f1f5dcc216`
  - `第六章 输入输出系统 > 习题`
  - I/O 综合题组，gold 聚焦设备控制、DMA、SPOOLing、缓冲与磁盘调度。
- `audit-ext-0014-39d0ed7b26`
  - `第十章 多处理机操作系统`
  - 属于章节引言样本，标注聚焦“多处理器系统 + 并行处理 + 性能目标”。
- `audit-ext-0016-0123e08d34`
  - `第二章 进程的描述与控制 > 习题`
  - 进程/线程/内核综合题组，gold 采用代表性概念锚点。
- `audit-ext-0017-f3cae72cda`
  - `第九章 操作系统接口 > 习题`
  - 命令接口与系统调用并存，但结构边界清晰。
- `audit-ext-0019-279ca38655`
  - `前言`
  - 仅保留课程内容范围与章节主题，不纳入版次、作者和致谢信息。
- `audit-ext-0020-8d820aa8f0`
  - `第十一章 多媒体操作系统 > 习题`
  - 多媒体章综合题组跨度大，保留调度、服务器与点播存储等高价值考点。

## 高密度样本

- `audit-ext-0020-8d820aa8f0`
  - `entity=10`，`relation=11`
- `audit-ext-0013-ab6ee0d771`
  - `entity=13`，`relation=10`
- `audit-ext-0018-d04b63e777`
  - `entity=11`，`relation=10`
- `audit-ext-0005-1ad586fd09`
  - `entity=11`，`relation=10`
- `audit-ext-0007-98389d2968`
  - `entity=10`，`relation=10`

## 结论

- 这份 audit 校准集已经达到“小规模、人工完成、可复用”的目标。
- 它当前更适合承担：
  - 自动评测规则校准
  - Prompt 分数接近时的人工作为裁决锚点
  - 论文或实验报告里的小规模人工可信度支撑
- 当前最大限制不在标注本身，而在语料来源仍然单一；如果后续加入 `slides`、`lab`、`syllabus`，这份统计摘要会更具代表性。
