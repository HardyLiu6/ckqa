/**
 * 提示词自动调优进度展示用的纯函数辅助。
 *
 * 提取出来便于在 node:test 下单测，UI 组件直接复用同一份逻辑。
 */

const STAGE_LABELS = {
  idle: '尚未开始',
  queued: '已入队，等待执行',
  fetch_input: '正在拉取课程资料',
  prompt_tune: '正在调用 GraphRAG 官方调优',
  done: '已完成',
  // GraphRAG 官方调优内部细分阶段；与后端 PromptTunePhase 枚举一一对应
  prompt_tune_chunking: '拆分文档',
  prompt_tune_domain: '识别课程领域',
  prompt_tune_language: '识别语言',
  prompt_tune_persona: '生成专家角色画像',
  prompt_tune_community_ranking: '生成社区报告排序描述',
  prompt_tune_entity_types: '识别实体类型',
  prompt_tune_examples: '生成实体关系示例',
  prompt_tune_extract_prompt: '撰写实体抽取提示词',
  prompt_tune_summary_prompt: '撰写实体摘要提示词',
  prompt_tune_community_role: '生成社区报告角色',
  prompt_tune_community_summary: '撰写社区摘要提示词',
  prompt_tune_writing: '保存调优产物',
}

/**
 * 后端 PromptTunePhase 枚举的进度百分比；与 PromptTunePhase.java 中保持一致。
 * 用于阶段维度的进度条体感档位。
 */
const PROMPT_TUNE_PHASE_PERCENTAGE = {
  prompt_tune_chunking: 5,
  prompt_tune_domain: 10,
  prompt_tune_language: 15,
  prompt_tune_persona: 20,
  prompt_tune_community_ranking: 30,
  prompt_tune_entity_types: 40,
  prompt_tune_examples: 60,
  prompt_tune_extract_prompt: 75,
  prompt_tune_summary_prompt: 80,
  prompt_tune_community_role: 85,
  prompt_tune_community_summary: 95,
  prompt_tune_writing: 100,
}

/**
 * 根据 status / stage 计算 el-progress 的百分比。
 *
 * 调优是不可精确预测耗时的任务，这里只用粗粒度档位让用户感知有进展。
 * 当 stage 命中后端 PromptTunePhase 细分阶段时，使用其精确百分比；否则按
 * 粗粒度规则给一个体感档位。
 */
export function resolveProgressPercentage(status, stage) {
  if (status === 'success') return 100
  if (status === 'failed' || status === 'cancelled') return 0
  if (status === 'pending') return 5
  if (status === 'running') {
    if (PROMPT_TUNE_PHASE_PERCENTAGE[stage] != null) {
      return PROMPT_TUNE_PHASE_PERCENTAGE[stage]
    }
    if (stage === 'fetch_input') return 25
    if (stage === 'prompt_tune') return 65
    if (stage === 'done') return 95
    return 30
  }
  return 0
}

export function resolveStageLabel(stage) {
  return STAGE_LABELS[stage] ?? stage ?? ''
}

/**
 * 末尾日志最多保留 6 行，便于在收起的小面板里一目了然。
 */
export function selectLatestLogTail(latestLogs, max = 6) {
  if (typeof latestLogs !== 'string' || latestLogs.length === 0) {
    return []
  }
  return latestLogs.split('\n').slice(-max).filter((line) => line.length > 0)
}

/**
 * 根据 status 派生主按钮的文案、动作语义。
 *
 * 返回 null 表示当前状态不需要主按钮。
 */
export function resolvePrimaryAction(status) {
  if (status === 'not_started') {
    return { label: '开始调优', kind: 'trigger' }
  }
  if (status === 'failed') {
    return { label: '重试', kind: 'retry' }
  }
  if (status === 'success') {
    return { label: '重新生成', kind: 'regenerate' }
  }
  return null
}
