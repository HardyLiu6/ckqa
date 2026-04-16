# auto_tuned

## 作用

- 候选名称：`auto_tuned`
- 生成时间：`2026-04-16T16:44:16+08:00`
- 来源类型：`fallback_default_copy`
- 基础 Prompt 来源：`未使用外部文件，脚本内生成`
- 是否注入 schema：`no`
- 是否包含 few-shot：`no`
- few-shot 策略：`none`

## 用途

- 供后续候选 Prompt 抽取执行、规则化自动评测、top-k Prompt 筛选和问答级验证使用。

## 备注

- GraphRAG 官方 auto-tuned 输出不存在，将保留占位目录并回退到 default 候选 Prompt。
- 由于未发现实际 auto-tuned Prompt，当前候选内容回退为 default 候选 Prompt，以保证目录结构和后续切换流程可运行。
