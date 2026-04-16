# GraphRAG 3.0.9 验证清单

## 1. 依赖与环境

- [ ] 在 `graphrag_pipeline/` 执行 `pip install -e ".[all]"` 成功
- [ ] `python -c "import graphrag; print(graphrag.__version__)"` 输出 `3.0.9`
- [ ] `python --version` 在项目支持范围内（建议 3.11）

## 2. 配置有效性

- [ ] `settings.yaml` 中使用 `chunking`，不再使用 `chunks`
- [ ] `settings.yaml` 中模型鉴权键为 `auth_method`
- [ ] `settings.yaml` 中 workflow 键已迁移为 `completion_model_id` / `embedding_model_id`
- [ ] `settings.yaml` 中 `vector_store` 为根对象，不再使用 `default_vector_store` / `vector_store_id`
- [ ] 如果需要最新模板，执行 `graphrag init --root . --force` 后，手工对齐自定义 prompt 与模型配置

## 3. 索引与查询

- [ ] `graphrag index --root .` 可成功完成
- [ ] `graphrag query --root . --method local --query "测试问题"` 返回有效回答
- [ ] `graphrag query --root . --method global --query "测试问题"` 返回有效回答
- [ ] `output/` 中存在 Parquet 文件且 `output/lancedb/` 存在

## 4. API 服务

- [ ] 启动 `python utils/main.py` 成功
- [ ] `GET /health` 返回 `status=healthy`
- [ ] `GET /health` 中 `compat_mode=cli_query`
- [ ] `GET /health` 中 `graphrag_version_target` 与 `pyproject.toml` 一致
- [ ] `POST /v1/chat/completions` 在 `graphrag-local-search:latest` 下返回结果
- [ ] `POST /v1/chat/completions` 在 `graphrag-global-search:latest` 下返回结果
- [ ] `POST /v1/chat/completions` 在 `full-model:latest` 下返回结果

## 5. 下游工具

- [ ] `python utils/neo4jTest.py --folder output` 在 v3 索引数据下可执行
- [ ] 若旧索引仍保留，`neo4jTest.py` 对 `document_ids` 也兼容
- [ ] `python utils/graphrag3dknowledge.py --directory output --port 8080` 可读取关系数据

## 6. 仓库入口与活跃文档

- [ ] `python scripts/audit_repo_drift.py --strict` 通过
- [ ] `README.md`、`AGENTS.md`、`.codex`、各模块 `CLAUDE.md` 与当前代码行为一致

## 7. 数据契约

- [ ] `pdf_ingest` 导出的 `section_docs.json` 字段仍满足 `settings.yaml` 的 `input` 映射
- [ ] `fetch_from_minio.py` 拉取后 JSON 数组可被索引，不需要改动 `pdf_ingest` 导出逻辑
