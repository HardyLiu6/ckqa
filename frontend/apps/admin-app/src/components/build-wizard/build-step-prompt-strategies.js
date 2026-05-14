/**
 * 构建向导第 4 步「提示词确认」的三种策略元数据。
 *
 * 提取出来便于单测固定文案、避免 BuildStepPrompt.vue 内联导致语义难以测试。
 * 文案敲定见 docs/superpowers/specs/2026-05-14-prompt-step-redesign-design.md。
 */

export const STRATEGIES = [
  {
    key: 'default',
    title: '默认提示词',
    icon: '⚙',
    tagline: '开箱即用，零等待。',
    pros: [
      '立即可用，无需调优',
      '与官方语义保持一致',
    ],
    cons: [
      '通用模板，未针对本课程语料优化',
      '抽取的实体可能更倾向通用领域而非课程概念',
    ],
    bestFor: '快速验证流程 / 跨课程通用知识库',
  },
  {
    key: 'graphrag_tuned',
    title: '自动调优提示词',
    icon: '✨',
    tagline: '基于本课程样本由 GraphRAG 自动调优。',
    pros: [
      '自动生成专家角色画像、领域识别、实体类型',
      '同一组资料命中缓存可秒级复用',
    ],
    cons: [
      '首次调优需要 10–20 分钟（受 LLM 速率限制）',
      '资料重新解析后会自动重跑',
    ],
    bestFor: '单门课程长期沉淀 / 注重抽取质量',
  },
  {
    key: 'custom_pipeline',
    title: '手动调优提示词',
    icon: '🛠',
    tagline: '进入独立工作台，3 步流程亲手调试。',
    pros: [
      '完全控制实体抽取规则',
      '可基于「系统默认」或「自动调优」为种子继续打磨',
    ],
    cons: [
      '需要熟悉 GraphRAG prompt 模板结构',
      '需要 30 分钟以上人工编辑',
    ],
    bestFor: '领域专家精细化迭代 / 已知抽取偏差需修正',
  },
]
