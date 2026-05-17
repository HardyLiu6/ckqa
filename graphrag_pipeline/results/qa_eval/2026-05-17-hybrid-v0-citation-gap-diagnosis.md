# Hybrid v0 citation gap 离线诊断

## 目标

排查 `hybrid-v0-v6-oneshot-rest4-20260517` 中 `Q009/Q017/Q021` 为什么 `fused evidence` 已有 gold 命中，但最终答案的 `citation_*` 指标没有同步提升。本诊断只读取已有 run、test set 与 GraphRAG parquet，不调用 API/LLM。

## 数据来源

- run: `graphrag_pipeline/results/qa_eval/runs/hybrid-v0-v6-oneshot-rest4-20260517`
- scoring: `algorithmic_scoring.csv`
- raw: `raw/Q009.json`, `raw/Q017.json`, `raw/Q021.json`
- test set: `graphrag_pipeline/data/eval/qa_test_set.jsonl`
- index output: `graphrag_pipeline/output/auto_tuned_crs-20260506-r4slkr_material7_20260513_001047`

## 总览

| question | category | selected evidence | answer citation | 主要原因 |
| --- | --- | ---: | ---: | --- |
| Q009 | relation_reasoning | `selected_r@3=0.5000` | `citation_r@3=0.0000` | 答案用了 `Sources(hex)`，scorer 将 hex 内数字片段误解析为 human id，导致映射到无关 text units |
| Q017 | chapter_summary | `selected_r@3=0.6000` | `citation_r@3=0.0000` | 答案写成 `Hybrid(24/25/...)`，这些是页码/序号，不是 text unit ref，无法映射 |
| Q021 | chapter_summary | `selected_r@3=0.3333` | `citation_r@3=0.0000` | 答案引用顺序优先章节标题/概述，gold refs 出现在第 4 位以后，top3 口径未命中 |

## Q009: 抽取器误解析 `Sources(hex)`

问题：`进程和线程在资源拥有与调度单位上的关键差异是什么？`

gold refs:

- `6119e805a0a2`
- `fe3e4fc89c4f`

fused refs top8:

```text
e04aa616effa, fe3e4fc89c4f, 15d8e8ce23a7, 6ce5eb6c812b,
a237fc2edcfb, abd74cc16dd1, e3c8ca971b47, 4873dc4116ae
```

答案中可见 citation 示例：

```text
[Data: Sources (d332e031d720)]
[Data: Sources (fe3e4fc89c4f, d332e031d720)]
[Data: Sources (e04aa616effa)]
```

离线复算显示：

- 现有 scorer 抽取结果：`15d8e8ce23a7, 6ce5eb6c812b, ...`
- 若把 `Sources(...)` 内 12 位 hex 直接当 text unit ref：`d332e031d720, fe3e4fc89c4f, e04aa616effa, ...`
- 现有 scorer: `citation_r@3=0.0000`
- direct hex 口径: `citation_r@3=0.5000`
- fused evidence 口径: `selected_r@3=0.5000`

根因：`extract_data_citations_from_answer()` 目前对 `Sources(...)` 只提取数字序列。遇到 `d332e031d720` 这种 hex ref 时，会拆出 `332/031/720` 等数字片段，再按 GraphRAG human id 映射，反而映射到无关 text units。

结论：Q009 主要是评测抽取口径问题，不是答案完全没引用到证据。应修正 citation extractor：`Sources/Entities/Relationships/Reports` 中如果 token 是 8 位以上 alnum/hex ref，应优先按 direct text unit prefix 解析；只有纯数字 token 才走 human id lookup。

## Q017: 答案引用不是 text unit ref

问题：`请概括第一章 1.4「操作系统的主要功能」的学习重点。`

gold refs:

- `9213459b0aa8`
- `014964257784`
- `09957405da60`
- `616795a1c017`
- `c2d570d976d8`

fused refs top8:

```text
9213459b0aa8, 616795a1c017, 014964257784, f55c02f07061,
be67038427c3, 5064b027cd17, 769fc023aace, 09957405da60
```

答案中可见 citation 示例：

```text
[Data: Hybrid(24)]
[Data: Hybrid(25)]
[Data: Hybrid(26)]
[Data: Hybrid(27)]
[Data: Hybrid(28)]
[Data: Hybrid(30)]
[Data: Hybrid(31)]
```

离线复算显示：

- 现有 scorer 抽取结果：空
- direct token 口径：空
- fused evidence 口径: `selected_r@3=0.6000`, `selected_rr=1.0000`

根因：答案使用的 `Hybrid(24/25/...)` 不是 text unit ref，而更像证据文本里的页码或序号。因为这些 token 太短，且没有 sidecar 映射，scorer 无法也不应该把它们当 text unit 命中。

结论：Q017 不是 scorer 漏算，而是 one-shot prompt 的引用合同不够稳。模型看到了 evidence 里的章节/页码元数据后，选择了短数字作为 `Hybrid` 引用。若保持“不强制 Text Unit 文案”，仍需要在 prompt 中明确：`Hybrid(...)` 只能使用 evidence label 中的 12 位 ref，不能使用页码、章节号或列表序号。

## Q021: 引用顺序与 gold 粒度错位

问题：`请概括第五章「虚拟存储器」的核心内容。`

gold refs:

- `d8440049cb5f`
- `e698d871994b`
- `039ce7bf8cc6`

fused refs top8:

```text
a2b896fc708e, e0e32cd74351, 039ce7bf8cc6, 19ce758b2756,
079991b6aa91, d8440049cb5f, 57ff2fa14b24, 1a66dd3c30d1
```

答案中可见 citation 示例：

```text
[Data: Sources (202, 203)]
[Data: Sources (203)]
[Data: Sources (205)]
[Data: Sources (206, 208)]
[Data: Sources (206, 229)]
[Data: Hybrid(a2b896fc708e, e0e32cd74351, 19ce758b2756, 039ce7bf8cc6, 079991b6aa91, +more)]
```

离线复算显示：

- 现有 scorer: `citation_r@3=0.0000`, `citation_r@5=0.3333`, `citation_rr=0.2500`
- direct Hybrid/Sources hex 口径: `citation_r@3=0.0000`, `citation_r@5=0.3333`
- fused evidence 口径: `selected_r@3=0.3333`

根因：fused top3 里第 3 个就是 gold `039ce7bf8cc6`，但最终答案引用顺序把第五章标题、5.1 概述和 5.1.2 定义特征排在前面，gold ref 到第 4 位才出现。该题是 chapter summary，gold 只标了部分关键小节，答案从人类视角覆盖“第五章整体”时引用章节标题/概述并不离谱，但与 raw gold top3 指标不完全一致。

结论：Q021 主要是引用排序和 gold 粒度问题。应在章节总结题中优先把“与 gold/关键小节更接近的 subsection refs”排到 `Hybrid(...)` 前 3；同时保留 chapter heading 作为背景 evidence，但不要让它压过具体小节。

## 根因归类

1. **评测抽取 bug / 口径缺口**：Q009。
   `Sources(hex)` 被当作 `Sources(number)`，导致错误映射。

2. **prompt 引用合同不稳**：Q017。
   模型输出 `Hybrid(24)` 这种不可追踪引用。

3. **evidence/citation 排序问题**：Q021。
   可用证据存在，但 top3 citation 排序没有优先 gold-like subsection。

## 建议的低成本修复顺序

1. 修正 `citation_extractor` 的 direct-ref 规则：支持 `Sources/Entities/Relationships/Reports` 内 8 位以上 alnum token 作为 direct text unit prefix；纯数字才走 `DataCitationLookup`。这是纯离线修复，能立刻修正 Q009 这类误判。

2. 修改 one-shot prompt 的引用约束，但不强制使用 `Text Units` 文案：允许 `[Data: Hybrid(ref...)]`，同时明确 `ref` 必须来自 evidence label 的 12 位 ID，不能使用页码、章节号、列表编号或 `+more`。

3. 对 chapter/global summary 的 evidence pack 做显示层排序：章节标题/section heading 可以保留在 context 前面帮助理解，但 `Hybrid(...)` 可引用列表应优先提供具体 subsection refs，避免 Q021 这类 gold-like 证据落到 top3 之外。

4. 在再跑真实 smoke 前，先用现有 raw 重新跑 scorer 验证 Q009 是否恢复；Q017/Q021 需要 prompt/排序改动后才值得花 API 成本。
