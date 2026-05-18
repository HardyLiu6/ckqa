# GraphRAG Auto-Tuned Prompt 固化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `auto_tuned` 候选 Prompt 固化为当前 GraphRAG 索引链路的活动 Prompt，并完成一次首版索引构建。

**Architecture:** 通过一个最小 CLI 脚本把候选 Prompt 复制到 `prompts/final/<candidate>/`，再用 `.env` 驱动 `settings.yaml` 的活动 Prompt 路径。这样候选归档、活动配置与索引执行三者分离，但链路仍保持简单。

**Tech Stack:** Python 3.10+、`pathlib`、`json`、`argparse`、`unittest`、GraphRAG CLI。

---

### Task 1: 为候选 Prompt 固化脚本建立测试

**Files:**
- Create: `graphrag_pipeline/tests/test_finalize_candidate_prompt.py`
- Modify: `graphrag_pipeline/settings.yaml`

- [ ] 写失败测试，覆盖：成功激活、缺文件回退、候选不存在
- [ ] 运行单测确认失败

### Task 2: 实现候选 Prompt 固化脚本

**Files:**
- Create: `graphrag_pipeline/scripts/prompt_tuning/finalize_candidate_prompt.py`
- Create: `graphrag_pipeline/scripts/finalize_candidate_prompt.py`

- [ ] 按测试实现最小 CLI
- [ ] 复制候选文件到 `prompts/final/<candidate>/`
- [ ] 更新 `.env` 活动 Prompt 变量
- [ ] 写出 `prompts/final/active_prompt.json`
- [ ] 运行单测确认通过

### Task 3: 接通索引配置与文档

**Files:**
- Modify: `graphrag_pipeline/.env`
- Modify: `graphrag_pipeline/settings.yaml`
- Modify: `graphrag_pipeline/README.md`
- Modify: `graphrag_pipeline/CLAUDE.md`
- Modify: `graphrag_pipeline/PROMPT_TUNING_PIPELINE.md`

- [ ] 把索引阶段 Prompt 路径改为环境变量驱动
- [ ] 补充“先激活候选，再建索引”的文档说明

### Task 4: 激活 auto_tuned 并执行验证

**Files:**
- Modify: `graphrag_pipeline/.env`
- Create/Modify: `graphrag_pipeline/prompts/final/auto_tuned/*`
- Create/Modify: `graphrag_pipeline/prompts/final/active_prompt.json`

- [ ] 运行 `python scripts/finalize_candidate_prompt.py --candidate auto_tuned`
- [ ] 运行相关测试和仓库漂移审计
- [ ] 运行 `python -m graphrag index --root .`
- [ ] 记录成功产物或环境阻塞项
