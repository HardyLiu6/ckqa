// 课程 schema 实体/关系类型（与 graphrag_pipeline/config/schema/*.json 同步）。
// Phase 1b 用前端 hardcode；Phase 2+ 改为 GET /relation-schemas 拉取。

export const ENTITY_TYPES = [
  { name: 'Course',              label_zh: '课程' },
  { name: 'Chapter',             label_zh: '章节' },
  { name: 'Section',             label_zh: '小节' },
  { name: 'KnowledgePoint',      label_zh: '知识点' },
  { name: 'Concept',             label_zh: '概念' },
  { name: 'Term',                label_zh: '术语' },
  { name: 'FormulaOrDefinition', label_zh: '定义/公式' },
  { name: 'AlgorithmOrMethod',   label_zh: '算法/方法' },
  { name: 'Experiment',          label_zh: '实验' },
  { name: 'Assignment',          label_zh: '作业' },
  { name: 'ToolOrPlatform',      label_zh: '工具/平台' },
]

const ALL_ENTITY_NAMES = ENTITY_TYPES.map((e) => e.name)

export const RELATION_TYPES = [
  { name: 'contains', label_zh: '包含',
    source_types: ['Course', 'Chapter', 'Section'],
    target_types: ['Chapter', 'Section', 'KnowledgePoint', 'Concept', 'Term'],
    extraction_hint: '结构化容器与下属内容的隶属关系' },
  { name: 'belongs_to', label_zh: '属于',
    source_types: ['KnowledgePoint', 'Concept', 'Term'],
    target_types: ['Course', 'Chapter', 'Section'],
    extraction_hint: '知识对象归属到课程结构（contains 的反向）' },
  { name: 'defined_by', label_zh: '由...定义',
    source_types: ['Concept', 'Term', 'KnowledgePoint'],
    target_types: ['FormulaOrDefinition'],
    extraction_hint: '被定义对象 → 定义/公式' },
  { name: 'applied_in', label_zh: '应用于',
    source_types: ['KnowledgePoint', 'AlgorithmOrMethod', 'FormulaOrDefinition'],
    target_types: ['Experiment', 'Assignment', 'ToolOrPlatform'],
    extraction_hint: '知识/方法 → 应用场景' },
  { name: 'evaluated_by', label_zh: '由...考核',
    source_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    target_types: ['Assignment', 'Experiment'],
    extraction_hint: '考核对象 → 作业/实验载体' },
  { name: 'depends_on', label_zh: '依赖于',
    source_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    target_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    extraction_hint: '同层知识对象之间的依赖' },
  { name: 'prerequisite_of', label_zh: '是...的先修',
    source_types: ['KnowledgePoint', 'Chapter', 'Section'],
    target_types: ['KnowledgePoint', 'Chapter', 'Section'],
    extraction_hint: '前置 → 后续' },
  { name: 'appears_in', label_zh: '出现于',
    source_types: ['Concept', 'Term', 'KnowledgePoint', 'AlgorithmOrMethod', 'FormulaOrDefinition'],
    target_types: ['Course', 'Chapter', 'Section', 'Experiment', 'Assignment', 'ToolOrPlatform'],
    extraction_hint: '内容实体出现在哪个上下文容器' },
  { name: 'related_to', label_zh: '相关',
    source_types: ALL_ENTITY_NAMES,
    target_types: ALL_ENTITY_NAMES,
    extraction_hint: '保底关系；只有无更具体关系时使用' },
]

export function filterRelationTypesByEndpoints({ sourceType, targetType }) {
  if (!sourceType || !targetType) return []
  return RELATION_TYPES.filter((r) =>
    r.source_types.includes(sourceType) && r.target_types.includes(targetType)
  )
}

export function describeRelationType(name) {
  return RELATION_TYPES.find((r) => r.name === name) ?? null
}
