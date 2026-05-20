import { onBeforeUnmount, ref } from 'vue'

// 通用倒计时 composable
// 主要用于"获取邮箱验证码"按钮，避免学生反复点按导致接口被风控
export function useCountdown(initialDuration = 60) {
  const remaining = ref(0)
  let timer = null

  function clear() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function start(duration = initialDuration) {
    clear()
    remaining.value = duration
    timer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value <= 0) {
        clear()
        remaining.value = 0
      }
    }, 1000)
  }

  function stop() {
    clear()
    remaining.value = 0
  }

  onBeforeUnmount(clear)

  return {
    remaining,
    start,
    stop,
    isActive: remaining,
  }
}
