// 品牌常量：UI 层统一显示「智课问答」；CKQA 仅保留在工程上下文（README / package.json / API 路径）。
// 视觉打磨迭代（2026-05-09）新增。

const DEFAULT_VERSION = '0.7.0'

function readAppVersion() {
  if (typeof __APP_VERSION__ !== 'undefined' && __APP_VERSION__) {
    return __APP_VERSION__
  }
  return DEFAULT_VERSION
}

export const BRAND = Object.freeze({
  name: '智课问答',
  tagline: '教学知识平台',
  version: readAppVersion(),
})
