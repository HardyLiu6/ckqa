/**
 * 提示词构建器 5 步向导的步骤模型与导航辅助。
 *
 * 用途：
 * - 维护向导步骤白名单与展示元数据（key / label / detail）
 * - 提供从 vue-router query 解析当前步骤、以及顺序前进 / 回退的纯函数
 * - 暴露 Phase 1a 阶段的步骤解锁规则（仅依赖 seed 选择）
 *
 * 调用方：PromptBuilderPage.vue（Phase 1a Task 8 接入）按 query 驱动当前步骤展示。
 * Phase 2-7 真实接入 build run 数据后会扩展解锁与导航逻辑。
 */

export const BUILDER_STEP_KEYS = ['seed', 'prepare', 'candidates', 'scoring', 'save']

export const BUILDER_STEPS = [
  { key: 'seed',       label: '选模板',       detail: '从模板或现有版本起步' },
  { key: 'prepare',    label: '构建准备材料', detail: '生成样本与校准集' },
  { key: 'candidates', label: '生成候选',     detail: '生成多版候选提示词' },
  { key: 'scoring',    label: '抽取评分',     detail: '在校准集上对候选打分' },
  { key: 'save',       label: '预览保存',     detail: '确认后入库' },
]

function firstQueryValue(value) {
  if (Array.isArray(value)) return value[0] ?? ''
  return value ?? ''
}

/**
 * 从 vue-router query 推导当前激活的步骤 key。
 *
 * 行为：
 * - 仅接受 BUILDER_STEP_KEYS 白名单内的值
 * - 当 query.step 是数组时（重复 query 参数）取首项
 * - 缺省、空串、非法值统一回落到首步 'seed'
 *
 * @param {Record<string, string | string[]>} [query] vue-router 的 route.query 对象
 * @returns {string} 命中白名单的步骤 key，未命中时返回 'seed'
 */
export function resolveActiveStepKey(query = {}) {
  const candidate = String(firstQueryValue(query.step) ?? '').trim()
  if (BUILDER_STEP_KEYS.includes(candidate)) return candidate
  return 'seed'
}

/**
 * 按 BUILDER_STEP_KEYS 顺序推进到下一步。
 *
 * 端点行为：当前步骤已是末步、或 currentKey 不在白名单内，返回 null。
 *
 * @param {string} currentKey 当前步骤 key
 * @returns {string | null} 下一步 key；末步或非法输入时为 null
 */
export function resolveNextStepKey(currentKey) {
  const idx = BUILDER_STEP_KEYS.indexOf(currentKey)
  if (idx < 0 || idx >= BUILDER_STEP_KEYS.length - 1) return null
  return BUILDER_STEP_KEYS[idx + 1]
}

/**
 * 按 BUILDER_STEP_KEYS 顺序回退到上一步。
 *
 * 端点行为：当前步骤已是首步、或 currentKey 不在白名单内，返回 null。
 *
 * @param {string} currentKey 当前步骤 key
 * @returns {string | null} 上一步 key；首步或非法输入时为 null
 */
export function resolvePrevStepKey(currentKey) {
  const idx = BUILDER_STEP_KEYS.indexOf(currentKey)
  if (idx <= 0) return null
  return BUILDER_STEP_KEYS[idx - 1]
}

/**
 * 判断指定步骤在当前向导状态下是否解锁。
 *
 * Phase 1a 解锁规则：
 * - seed 永远解锁
 * - 其余步骤当 seed 已选（system_default 或 graphrag_tuned）时解锁
 * - history_draft 在 Phase 1a/1e 才会接入，本期视为未选
 *
 * Phase 2-7 真实接入后会改成读取 build run 数据来决定解锁。
 *
 * @param {string} stepKey 待判定的步骤 key
 * @param {{ seed?: string }} [state] 向导当前状态，至少包含 seed 选择
 * @returns {boolean} 该步骤是否可进入
 */
export function isStepUnlocked(stepKey, state = {}) {
  if (stepKey === 'seed') return true
  return state?.seed === 'system_default' || state?.seed === 'graphrag_tuned'
}
