// 侧栏折叠模型 —— 视觉打磨迭代（2026-05-09）。
// 折叠态不进 Pinia；用 localStorage + 局部 ref + 快捷键。

export const STORAGE_KEY = 'ckqa.sidebar.collapsed'
export const EXPANDED_SIDEBAR_WIDTH = 240
export const COLLAPSED_SIDEBAR_WIDTH = 64

const isBrowser = () => typeof globalThis !== 'undefined' && typeof globalThis.window !== 'undefined'

function resolveStorage(storage) {
  if (storage) return storage
  if (isBrowser() && globalThis.window.localStorage) return globalThis.window.localStorage
  return null
}

/**
 * 从存储中读取折叠状态。
 * @param {Storage} [storage] 可选注入，测试用
 * @returns {boolean}
 */
export function readCollapsed(storage) {
  const target = resolveStorage(storage)
  if (!target) return false
  try {
    return target.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * 写入折叠状态。
 * @param {boolean} collapsed
 * @param {Storage} [storage]
 */
export function writeCollapsed(collapsed, storage) {
  const target = resolveStorage(storage)
  if (!target) return
  try {
    if (collapsed) {
      target.setItem(STORAGE_KEY, '1')
    } else {
      target.removeItem(STORAGE_KEY)
    }
  } catch {
    /* 写入失败（quota / 禁用）时静默，避免阻断 UI */
  }
}

/**
 * 判断 keyboard event 是否触发折叠/展开。
 * 要求：Ctrl+\ 或 Meta+\，且不处于 IME 合成态。
 * @param {KeyboardEvent|{key:string,ctrlKey:boolean,metaKey:boolean,isComposing?:boolean,keyCode?:number}} event
 */
export function isToggleKey(event) {
  if (!event) return false
  // IME 守卫：拼音 / 日文输入候选词时按键不触发
  if (event.isComposing) return false
  if (event.keyCode === 229) return false
  if (event.key !== '\\') return false
  return Boolean(event.ctrlKey || event.metaKey)
}
