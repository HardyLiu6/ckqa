// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/audit-samples.js
//
// 5 条 audit 样本：高优 2 + 中优 2 + 低优 1，覆盖典型场景。

export const MOCK_AUDIT_SAMPLES = [
  {
    id: 'audit-0001',
    sourceSampleId: 'sample-os-2-1',
    text: '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。进程具有动态性、并发性、独立性、异步性和结构性五大基本特征。',
    headingPath: ['第二章 进程管理', '2.1 进程的定义'],
    pageStart: 34,
    pageEnd: 34,
    auditPriority: 'high',
    auditReason: '高价值定义/公式样本，覆盖 Concept + FormulaOrDefinition + Chapter 三种实体类型',
    hitSignals: ['definition_signal', 'formula_signal'],
    guessedSampleType: 'definition_or_formula',
    status: 'in_progress',
    goldEntities: [
      { id: 'e1', name: '进程',           type: 'Concept',             description: '课程概念，第 2.1 节核心定义对象', source: 'reused' },
      { id: 'e2', name: '第二章 进程管理', type: 'Chapter',             description: '', source: 'reused' },
      { id: 'e3', name: '进程定义',       type: 'FormulaOrDefinition', description: '', source: 'manual' },
    ],
    aiSuggestedEntities: [
      { id: 'ai1', name: '系统',     type: 'Concept', description: '由 AI 从"系统进行资源分配..."识别', confidence: 0.72 },
      { id: 'ai2', name: '动态性',   type: 'Term',    description: '由 AI 从"动态性、并发性..."识别',     confidence: 0.58 },
    ],
    goldRelations: [
      { id: 'r1', sourceEntityId: 'e1', targetEntityId: 'e3', type: 'defined_by', evidence: '文本给出了进程的正式定义', source: 'manual' },
    ],
    aiSuggestedRelations: [
      { id: 'ar1', sourceEntityId: 'e2', targetEntityId: 'e1', type: 'contains', evidence: 'Chapter→Concept 唯一合法关系', source: 'ai_schema_inferred' },
    ],
    reusedFrom: { buildRunId: 'br-os-2026-04-12', buildRunName: '操作系统 · 上学期构建', reusedAt: '2026-04-12T14:23:54Z' },
  },
  {
    id: 'audit-0002',
    sourceSampleId: 'sample-os-3-2',
    text: '调度算法主要包括先来先服务（FCFS）、短作业优先（SJF）、时间片轮转（RR）和多级反馈队列。其中 FCFS 实现简单但不利于短作业。',
    headingPath: ['第三章 处理机调度', '3.2 调度算法'],
    pageStart: 56, pageEnd: 57,
    auditPriority: 'high',
    auditReason: '覆盖 AlgorithmOrMethod + Term 多类型',
    hitSignals: ['method_signal'],
    guessedSampleType: 'algorithm_or_method',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0003',
    sourceSampleId: 'sample-os-lab-1',
    text: '实验目的：通过实现时间片轮转调度算法，理解多任务调度的基本机制。',
    headingPath: ['实验一', '进程调度'],
    pageStart: 102, pageEnd: 105,
    auditPriority: 'medium',
    auditReason: '实验类型样本',
    hitSignals: ['experiment_signal', 'method_signal'],
    guessedSampleType: 'experiment_instruction',
    status: 'done',
    goldEntities: [
      { id: 'e1', name: '实验一 进程调度',     type: 'Experiment',        source: 'manual' },
      { id: 'e2', name: '时间片轮转调度算法', type: 'AlgorithmOrMethod', source: 'manual' },
    ],
    goldRelations: [
      { id: 'r1', sourceEntityId: 'e2', targetEntityId: 'e1', type: 'applied_in', evidence: '实验要求实现该算法', source: 'manual' },
    ],
    aiSuggestedEntities: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0004',
    sourceSampleId: 'sample-os-4-1',
    text: '内存管理的目标是为多个进程提供独立、安全、高效的虚拟地址空间。常见的内存分配策略有连续分配和分页分配。',
    headingPath: ['第四章 内存管理', '4.1 内存管理基础'],
    pageStart: 78, pageEnd: 79,
    auditPriority: 'medium',
    auditReason: '章节概念讲解类型',
    hitSignals: ['method_signal'],
    guessedSampleType: 'chapter_concept_explanation',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0005',
    sourceSampleId: 'sample-os-hw-5',
    text: '习题 5：使用信号量实现生产者-消费者问题，并分析死锁产生的条件。',
    headingPath: ['习题', '第五章 习题 5'],
    pageStart: 130, pageEnd: 130,
    auditPriority: 'low',
    auditReason: '作业类型样本',
    hitSignals: ['assignment_signal'],
    guessedSampleType: 'assignment_requirement',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
]

export const MOCK_TASK_SUMMARY = {
  samplesBuilt: { count: 80, types: 5, durationSec: 12 },
  auditSampled: { high: 2, medium: 2, low: 1, total: 5, durationSec: 3 },
}
