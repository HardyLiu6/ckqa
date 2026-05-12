# 课程问答 baseline 评测假设清单

> 本文件在评测运行前撰写并提交。运行后，所有假设在 `manual_review.md` 的「假设验证」段中按 [验证 / 部分验证 / 不支持] 三档给出结论，并粘贴具体数据依据。修改假设需重新跑评测。

## 理论依据

- GraphRAG（Edge et al., 2024）的 local-global 二分理论：local 侧重实体邻域，global 侧重社区总结，drift 在二者之间漂移，basic 退化到纯向量检索加完整 chunk。
- 我们用四类问题对应不同语义粒度：factual_lookup 偏 local 基础事实，chapter_summary 偏 mixed，global_overview 偏 community report。

## 假设

- H1：factual_lookup 类问题，local 与 basic 模式在 `entity_hit_rate` 与 `retrieval_precision` 上优于 global / drift。理由：global 的社区摘要会稀释具体术语，basic 直接召回原始 chunk，对术语命中更直接。
- H2：global_overview 类问题，global 模式在 `semantic_correctness` 上优于其他三种模式，但 `faithfulness` 可能下降。理由：community report 已经做过 LLM 汇总，叙述贴合「整体方法论」类问题，但摘要本身脱离原文 chunk，回答的字面依据更难追踪。
- H3：chapter_summary 类问题，local 与 drift 在 `semantic_correctness` 上优于 basic。理由：basic 只能返回单个 chunk，难覆盖整章；local/drift 通过实体/关系把章节内多个 chunk 拼起来。
- H4：relation_reasoning 类问题，drift 模式 `semantic_correctness` 排名第一。理由：drift 设计上更适合跨社区或跨子图的关系探测。
- H5：整体来看，规则层 `entity_hit_rate` 与裁判层 `semantic_correctness` 在 factual_lookup 类上正相关高于 global_overview 类。理由：global 类问题的同义改写比例更高，字面命中会失真。

## 失效情形清单（任意一条命中，假设全部需要重审）

- 测试集少于 30 条，或任一类目少于 6 条。
- `gold_text_unit_ids` 非空率 < 80%。
- 裁判 LLM `fallback_sentinel`（-1）出现率 > 15%。
