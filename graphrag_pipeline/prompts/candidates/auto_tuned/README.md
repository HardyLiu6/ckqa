# auto_tuned

## 来源

本目录来源于 GraphRAG prompt-tune 自动生成结果，并已整理为当前项目候选 Prompt 目录结构。

## 本次执行

- 调用时间：`2026-05-08T18:54:04+08:00`
- 命令入口：`python -m graphrag prompt-tune`
- GraphRAG root：`/home/sunlight/Projects/ckqa/graphrag_pipeline`
- config 路径：`/home/sunlight/Projects/ckqa/graphrag_pipeline/settings.yaml`
- 输出目录：`/home/sunlight/Projects/ckqa/graphrag_pipeline/prompts/candidates/auto_tuned`
- domain：`未显式传入`
- language：`未显式传入`
- chunk_size：`未显式传入`

## 与其他候选 Prompt 的区别

- `default`：接近当前项目默认抽取 Prompt 的基线版本。
- `schema_aware`：在基线 Prompt 上显式注入课程 Schema 约束。
- `schema_fewshot`：在 schema_aware 基础上加入轻量 few-shot 示例。
- `auto_tuned`：由 GraphRAG 官方 prompt-tune 根据当前输入数据自动生成，强调对语料自适应，而不是人工注入课程 Schema。

## 归档文件

- `community_report_graph.txt`
- `extract_graph.txt`
- `prompt.txt`
- `summarize_descriptions.txt`

## 备注

- 无
