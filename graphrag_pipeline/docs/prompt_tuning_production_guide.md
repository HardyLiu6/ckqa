# 提示词调优生产环境使用指南

## 一、服务接口

提示词调优服务集成在 GraphRAG Python API 中，提供以下 REST 接口：

### 1.1 获取调优状态

```
GET /v1/prompt-tuning/status
```

返回当前生产 prompt 的冻结状态、支持的实体/关系类型。

**响应示例**：
```json
{
  "production_prompt": "prompts/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt.txt",
  "production_prompt_md5": "14ab82b37aa379f6026287a491888a44",
  "production_status": "frozen_v1",
  "frozen_at": "2026-05-12",
  "entity_types": ["Course", "Chapter", "Section", ...],
  "relation_types": ["contains", "belongs_to", "defined_by", ...]
}
```

### 1.2 执行抽取

```
POST /v1/prompt-tuning/extract
```

使用生产 prompt 对样本执行知识图谱抽取。

**请求体**：
```json
{
  "samples": [
    {
      "sample_id": "sample-001",
      "text": "2.2.1 进程的定义和特征\n\n在多道程序环境下..."
    }
  ],
  "concurrency": 20,
  "max_gleanings": 1
}
```

**响应**：
```json
{
  "status": "success",
  "sample_count": 1,
  "success_count": 1,
  "results": [
    {
      "sample_id": "sample-001",
      "candidate": "production_v1",
      "status": "success",
      "entities": [...],
      "relationships": [...]
    }
  ]
}
```

### 1.3 评估抽取质量

```
POST /v1/prompt-tuning/evaluate
```

对抽取结果进行质量评估（需要提供带 gold 标注的评测样本）。

**请求体**：
```json
{
  "extraction_results": [...],
  "audit_samples": [
    {
      "source_sample_id": "sample-001",
      "text": "...",
      "gold_entities": [...],
      "gold_relations": [...]
    }
  ]
}
```

**响应**：
```json
{
  "status": "success",
  "metrics": {
    "audit_entity_recall": 0.6245,
    "audit_entity_precision": 0.2930,
    "audit_relation_recall": 0.3683,
    "faithfulness_error_rate": 0.0237,
    "gate_passed": true
  },
  "gate_passed": true
}
```

### 1.4 获取生产 prompt

```
GET /v1/prompt-tuning/prompt
```

返回当前生产 prompt 的完整文本内容。

### 1.5 获取 Schema

```
GET /v1/prompt-tuning/schema
```

返回完整的实体类型和关系类型 schema 定义。

---

## 二、生产环境提示词调优流程

### 2.1 新课程上线流程

当有新课程材料需要建立知识图谱时：

```
1. 课程材料 PDF 解析（pdf_ingest）
       ↓
2. 调用 POST /v1/prompt-tuning/extract
   - 输入：课程文本片段（建议 10-20 条代表性样本）
   - 输出：抽取结果
       ↓
3. 人工抽检抽取质量
   - 检查实体类型是否正确
   - 检查关系方向是否合理
   - 检查是否有明显遗漏
       ↓
4. 如果质量达标 → 直接使用生产 prompt 建索引
   如果质量不达标 → 进入调优流程（见 2.2）
```

### 2.2 调优流程（仅在质量不达标时）

```
1. 准备评测集
   - 从新课程中选 20 条代表性样本
   - 对其中 10-15 条做人工 gold 标注
       ↓
2. 调用 POST /v1/prompt-tuning/evaluate
   - 输入：抽取结果 + gold 标注
   - 输出：recall / precision / faithfulness 指标
       ↓
3. 分析指标
   - faithfulness_error_rate > 15% → prompt 格式问题，需要修复
   - entity_recall < 40% → 可能需要调整 schema 或 few-shot 示例
   - 正常波动范围：entity_recall 40-65%，faithfulness < 5%
       ↓
4. 如需调整 prompt（罕见情况）
   - 运行 scripts/prompt_tuning/generate_candidate_prompts.py
   - 跑 A/B 测试对比新旧 prompt
   - 确认新 prompt 优于旧 prompt 后更新冻结版本
```

### 2.3 日常监控

```
定期（每周/每月）：
1. 随机抽取 5 个已建索引的课程样本
2. 调用 extract + evaluate 检查指标
3. 如果 faithfulness_error_rate 突然升高 → 可能是模型版本变更
4. 如果 recall 持续下降 → 可能需要更新 few-shot 示例
```

---

## 三、关键配置

### 3.1 环境变量

```bash
# API 配置（.env 文件）
GRAPHRAG_API_BASE=http://127.0.0.1:3301/v1
GRAPHRAG_CHAT_API_KEY=your-api-key
GRAPHRAG_EXTRACTION_MODEL=deepseek-v4-flash
```

### 3.2 性能参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| concurrency | 20 | 并发抽取数，20 并发约 3-4 分钟完成 20 样本 |
| max_gleanings | 1 | Gleaning 轮数，1 轮即可 |
| temperature | 模型默认 | 评测时建议设 0 以保证可复现 |

### 3.3 质量门禁

| 指标 | 阈值 | 说明 |
|------|------|------|
| faithfulness_error_rate | ≤ 0.15 | 幻觉率不超过 15% |
| parse_success_rate | ≥ 0.95 | 解析成功率 |
| endpoint_valid_rate | ≥ 0.95 | 关系端点合规率 |

---

## 四、注意事项

1. **不要手动修改生产 prompt 文件**——它已冻结，任何修改需要走完整的 A/B 测试流程。
2. **LLM 输出有随机性**——同一 prompt 跑两次结果可能有 ±10-15% 波动，这是正常的。
3. **metadata-closure 是独立的后处理**——它在 `graphrag index` 阶段自动执行，不需要在 prompt 层面处理。
4. **跨课程泛化**——当前 prompt 在操作系统教材上验证，其他课程首次使用时建议做一次抽检。
5. **API 速率限制**——deepseek-v4-flash 限制 15,000 RPM，20 并发远低于限制。
