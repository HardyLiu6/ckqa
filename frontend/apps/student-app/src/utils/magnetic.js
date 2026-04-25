/**
 * 计算磁吸按钮的位移向量
 * @param {object} opts
 * @param {{x: number, y: number}} opts.cursor 鼠标当前坐标
 * @param {{x: number, y: number}} opts.center 按钮中心坐标
 * @param {number} opts.radius 吸附半径（超过此距离不吸附）
 * @param {number} opts.maxShift 最大位移
 * @returns {{x: number, y: number}}
 */
export function computeMagneticOffset({ cursor, center, radius, maxShift }) {
  const dx = cursor.x - center.x
  const dy = cursor.y - center.y
  const distance = Math.sqrt(dx * dx + dy * dy)

  if (distance >= radius || distance === 0) {
    return { x: 0, y: 0 }
  }

  // 比例：越靠近中心吸附越强
  const strength = 1 - distance / radius
  // 单位向量 * 最大位移 * 强度
  const unitX = dx / distance
  const unitY = dy / distance

  return {
    x: unitX * maxShift * strength,
    y: unitY * maxShift * strength,
  }
}
