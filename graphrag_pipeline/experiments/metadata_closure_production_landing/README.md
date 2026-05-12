# metadata-closure 的生产落地调研

本文档整理 2026-05-11 的调研结果，回答一个关键问题：**我们实验环境里效果显著的 metadata-closure 后处理（实体注入 + 层级 contains 派生），如何落到生产 `graphrag index` 管线里？**

## 1. 背景

### 1.1 当前实验环境

- 手工 JSON 抽取器或原生 `GraphExtractor` → eval JSON
- `scripts/postprocess_extraction_results.py --mode strict-metadata-closure`
  在 eval JSON 上做后处理，从 audit/sample metadata 读 `course_id / chapter / section / heading_path`，注入 Course/Chapter/Section seed 实体 + 派生 contains 边
- 效果：`audit_relation_recall` 从 0.18–0.21（裸抽取）推到 0.29–0.35（+closure）

### 1.2 生产环境

- `graphrag index` 基于 parquet workflow 管线：
  `load_input_documents → create_base_text_units → create_final_documents → extract_graph → finalize_graph → extract_covariates → create_communities → create_final_text_units → create_community_reports → generate_text_embeddings`
- 抽取阶段使用 `graphrag.index.operations.extract_graph.graph_extractor.GraphExtractor`（tuple 格式 + gleaning）
- 结果是 `entities.parquet / relationships.parquet / text_units.parquet / documents.parquet`，不会经过我们的 `postprocess_extraction_results.py`

因此：**实验环境的 metadata-closure 在生产管线里默认不会生效**，必须显式落地。

## 2. 调研发现

### 2.1 GraphRAG 3.0.9 官方原生**没有**等价功能

检索 [microsoft.github.io/graphrag](https://microsoft.github.io/graphrag/) 和 GitHub 源码，没有任何一个 workflow 会基于文档 metadata 自动生成容器实体和层级 contains 关系。

官方最接近的机制只有两个：

| 机制 | 能做什么 | 不能做什么 |
|---|---|---|
| [`chunking.prepend_metadata`](https://microsoft.github.io/graphrag/index/inputs/#chunking-and-metadata)（list[str]） | 把指定 metadata 字段以 `key: value` 形式拼到每个 chunk 正文前面，LLM 能"看见" | 不主动生成实体，不建立边；依赖 LLM 自己识别并抽出来，稳定性未知 |
| [`workflows: [...]`](https://microsoft.github.io/graphrag/config/yaml/#workflows)（list[str]） | 允许在 `settings.yaml` 里重排 workflow 顺序，也能插入自定义 workflow | 自定义 workflow 需自己写 |

### 2.2 业界有类似实践，方向被主流认可

#### 直接对标：AWS Bedrock Knowledge Bases GraphRAG (Neptune Analytics)

[官方文档](https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base-build-graphs.html) 明确说明该能力会自动识别文档内的**结构元素（如 section titles）**并作为图的组成部分，无需用户配置。

Content was rephrased for compliance with licensing restrictions.

> **这说明"用文档结构 metadata 派生容器实体+层级关系"不是实验性技巧，而是被商业化 GraphRAG 产品吸收的主流做法。**

#### schema 内建已预期：`appears_in.derivation_source: "metadata_first"`

仓库自己的 `config/schema/relation_types.json` 里 `appears_in` 早已带有 derivation_source 提示，明确要求该关系优先由标准化文档元数据派生，LLM 只在必要时参与。换句话说，schema 设计者 **早已预期** 这部分关系应由 metadata 生成，只是 GraphRAG 3.0.9 暂未提供默认 workflow。

#### 学术方向

- [*Efficient Knowledge Graph Construction and Hybrid Retrieval at Scale*（arxiv 2507.03226）](https://arxiv.org/abs/2507.03226)：专门讨论如何减少对 LLM-based 抽取的依赖。
- [*How to Mitigate Information Loss in Knowledge Graphs for GraphRAG*（arxiv 2501.15378）](https://arxiv.org/html/2501.15378v1)：分析 graphrag 信息损失来源，包括结构化容器丢失一类。

这些工作共同支撑同一个观察：结构化容器实体由 metadata 派生优于完全依赖 LLM 抽取。

Content was rephrased for compliance with licensing restrictions.

### 2.3 GraphRAG 的自定义 workflow 是一等公民

[BYOG 文档](https://microsoft.github.io/graphrag/index/byog/) + `PipelineFactory` 源码表明：

- `PipelineFactory.register(name, workflow)` 是公开 API
- `WorkflowFunction` 协议：`async def run_workflow(config, context) -> WorkflowFunctionOutput`
- 通过 `context.output_table_provider.read_dataframe / write_dataframe` 读写中间 parquet 表
- 在 `settings.yaml` 里用 `workflows: [...]` 覆盖默认顺序即可把自定义 workflow 插进来
- 官方支持：不需要 fork graphrag，不需要改源码

## 3. 三种落地方案

### 方案 A：仅用 `prepend_metadata`（零代码）

在 `settings.yaml` 追加：

```yaml
input:
  type: json
  metadata: [chapter, section, heading_path_text, course_id]

chunks:
  size: 1200
  overlap: 100
  prepend_metadata: [chapter, section, heading_path_text]
```

**预期效果**：每个 chunk 在被送给 LLM 前，正文前面会多出 3 行 metadata。

**优点**：
- 零代码改动
- 原生 graphrag 完全支持

**缺点**：
- 只是让 LLM"看到"metadata，抽不抽出来、方向对不对完全靠模型
- 我们的 pre-native 实验（手工抽取器）已经显示，LLM 对结构化容器实体识别率 ~50%，且经常方向反（`Concept contains Section` 这种）

**复杂度**：1（只改配置）

### 方案 B：自定义 `inject_metadata_graph` workflow（推荐）

实现步骤：

1. 把 `scripts/extraction_eval/relationship_postprocessor.py::_apply_metadata_container_injection` 的逻辑包装成 graphrag workflow 接口：

   ```python
   # graphrag_pipeline/custom_workflows/inject_metadata_graph.py
   async def run_workflow(config, context):
       documents = await context.output_table_provider.read_dataframe("documents")
       text_units = await context.output_table_provider.read_dataframe("text_units")
       entities = await context.output_table_provider.read_dataframe("entities")
       relationships = await context.output_table_provider.read_dataframe("relationships")

       # 从 documents 的 metadata 列读 chapter/section/heading_path
       # 复用 _apply_metadata_container_injection 的核心逻辑
       new_entities, new_relationships = inject_metadata_containers(
           documents, entities, relationships,
       )

       await context.output_table_provider.write_dataframe(
           "entities", pd.concat([entities, new_entities])
       )
       await context.output_table_provider.write_dataframe(
           "relationships", pd.concat([relationships, new_relationships])
       )
       return WorkflowFunctionOutput(
           result={"new_entity_count": len(new_entities),
                   "new_relationship_count": len(new_relationships)}
       )
   ```

2. 写启动 wrapper（因为 CLI `graphrag index` 启动前需要完成 register）：

   ```python
   # scripts/run_graphrag_index_with_metadata_closure.py
   import asyncio
   from pathlib import Path
   from graphrag.index.workflows.factory import PipelineFactory
   from graphrag.config.load_config import load_config
   import graphrag.api as api
   from custom_workflows.inject_metadata_graph import run_workflow as inject_metadata_graph

   PipelineFactory.register("inject_metadata_graph", inject_metadata_graph)

   async def main():
       config = load_config(Path("."))
       return await api.build_index(config=config)

   asyncio.run(main())
   ```

3. 在 `settings.yaml` 覆盖 workflow 顺序，在 `extract_graph` 之后 `finalize_graph` 之前插入新步骤：

   ```yaml
   workflows:
     - load_input_documents
     - create_base_text_units
     - create_final_documents
     - extract_graph
     - inject_metadata_graph   # ← 新增
     - finalize_graph
     - extract_covariates
     - create_communities
     - create_final_text_units
     - create_community_reports
     - generate_text_embeddings
   ```

**优点**：
- 和实验环境 metadata-closure 行为完全对齐
- 不依赖 LLM，派生结果确定性
- 配合原生 graphrag 运行，不污染主流程

**缺点**：
- 需要一个 Python wrapper 启动（不能裸用 `graphrag index` CLI）
- 约 100 行 workflow 代码 + 10 行 wrapper

**复杂度**：3（写代码 + 测试）

### 方案 C：A + B 组合（最稳）

`prepend_metadata` 提高 LLM 自己识别容器的概率（做什么是什么），`inject_metadata_graph` 兜底确保结构边一定齐全。适合上生产。

**复杂度**：3–4

## 4. 建议路线

1. **Phase 1（本周）**：先只改 `settings.yaml` 启用 `prepend_metadata`，跑一次真实 `graphrag index`，看 LLM 自己抽出的容器实体覆盖率能到多少。
2. **Phase 2（下周）**：如果 Phase 1 覆盖率 < 80%，就上方案 B 自定义 workflow。代码已经在 `relationship_postprocessor.py::_apply_metadata_container_injection`，只需要包装成 workflow 接口。
3. **Phase 3（长期）**：若方案 B 稳定，可给 `microsoft/graphrag` 提 PR 或 issue 讨论，把这类 metadata-driven 结构实体注入做成官方可选 workflow。

## 5. 参考资料

- [GraphRAG – Inputs and chunking](https://microsoft.github.io/graphrag/index/inputs/) — `prepend_metadata` 官方说明
- [GraphRAG – Bring Your Own Graph (BYOG)](https://microsoft.github.io/graphrag/index/byog/) — 自定义 workflow 接入点
- [GraphRAG – Detailed Configuration (workflows key)](https://microsoft.github.io/graphrag/config/yaml/#workflows) — workflow 顺序覆盖
- [AWS Bedrock Knowledge Bases with Amazon Neptune GraphRAG](https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base-build-graphs.html) — 商业化产品已内置同类能力
- [Efficient Knowledge Graph Construction and Hybrid Retrieval at Scale (arxiv 2507.03226)](https://arxiv.org/abs/2507.03226)
- [How to Mitigate Information Loss in Knowledge Graphs for GraphRAG (arxiv 2501.15378)](https://arxiv.org/html/2501.15378v1)
- 本仓库 `config/schema/relation_types.json` 中 `appears_in.derivation_source: "metadata_first"`

上述第三方资料的内容均为转述摘录，单一来源连续原文不超过 30 词，已做改写以符合许可合规要求。

Content was rephrased for compliance with licensing restrictions.
