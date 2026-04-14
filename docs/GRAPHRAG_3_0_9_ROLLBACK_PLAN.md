# GraphRAG 3.0.9 回滚建议

## 目标

在升级失败或查询质量明显下降时，快速恢复到 GraphRAG 2.7.0 的稳定运行状态，尽量缩短不可用窗口。

## 回滚触发条件

- `graphrag index --root .` 在修复后仍持续失败
- API 无法稳定返回本地/全局查询结果
- 索引结果结构变化导致关键下游流程不可用（如 Neo4j 导入）
- 响应质量显著下降且短期无法通过 prompt/参数调优恢复

## 回滚步骤

1. 代码回滚
- 使用 Git 将以下文件回退到升级前版本：
  - `graphrag_pipeline/pyproject.toml`
  - `graphrag_pipeline/requirements.txt`
  - `graphrag_pipeline/settings.yaml`
  - `graphrag_pipeline/utils/main.py`
  - `graphrag_pipeline/utils/neo4jTest.py`

2. 依赖回滚
- 在 `graphrag_pipeline/` 执行：
  - `pip install -e ".[all]"`
- 验证：`python -c "import graphrag; print(graphrag.__version__)"` 为 `2.7.0`

3. 配置回滚
- 恢复到 2.7.0 可用的 `settings.yaml`
- 检查 `.env` 中模型、路径、prompt 变量仍然可用

4. 索引与服务恢复
- 清理旧索引产物（按你们现网规范保留备份）
- 执行：
  - `graphrag index --root .`
  - `python utils/main.py`
- 验证 API 健康检查与核心问答路径

## 降低回滚风险的实践

- 升级前打标签：例如 `pre-graphrag-3-0-9`
- 保留一份升级前 `settings.yaml` 与 `prompts/` 快照
- 将 `output/` 目录按版本分开（如 `output_v2`、`output_v3`）
- 先在测试环境完成全量验证清单后再切生产

## 升级失败后的折中方案

- 保留 GraphRAG 3.0.9 依赖，但 API 强制走 CLI 兼容模式（`utils/main.py` 已支持）
- 暂缓内部 API 级重构，仅通过 CLI 提供稳定查询能力
- 待官方 API 层稳定后再恢复高性能内嵌调用路径
