<script setup>
// 第三方登录占位（圆形图标按钮，无文字标签）
// 当前学生端原型还未对接微信 / QQ / GitHub OAuth，先以视觉占位呈现
// 后续接入 Java `/api/v1/auth/oauth2/{provider}/authorize` 时，把 toast 替换为实际跳转即可
//
// 图标 path 数据来自 Bootstrap Icons (MIT 许可)
// https://icons.getbootstrap.com/icons/wechat / tencent-qq / github
import { ElMessage } from 'element-plus'

const providers = [
  {
    id: 'wechat',
    label: '微信',
    tone: 'wechat',
    viewBox: '0 0 16 16',
    paths: [
      'M11.176 14.429c-2.665 0-4.826-1.8-4.826-4.018 0-2.22 2.159-4.02 4.824-4.02S16 8.191 16 10.411c0 1.21-.65 2.301-1.666 3.036a.32.32 0 0 0-.12.366l.218.81a.6.6 0 0 1 .029.117.166.166 0 0 1-.162.162.2.2 0 0 1-.092-.03l-1.057-.61a.5.5 0 0 0-.256-.074.5.5 0 0 0-.142.021 5.7 5.7 0 0 1-1.576.22M9.064 9.542a.647.647 0 1 0 .557-1 .645.645 0 0 0-.646.647.6.6 0 0 0 .09.353Zm3.232.001a.646.646 0 1 0 .546-1 .645.645 0 0 0-.644.644.63.63 0 0 0 .098.356',
      'M0 6.826c0 1.455.781 2.765 2.001 3.656a.385.385 0 0 1 .143.439l-.161.6-.1.373a.5.5 0 0 0-.032.14.19.19 0 0 0 .193.193q.06 0 .111-.029l1.268-.733a.6.6 0 0 1 .308-.088q.088 0 .171.025a6.8 6.8 0 0 0 1.625.26 4.5 4.5 0 0 1-.177-1.251c0-2.936 2.785-5.02 5.824-5.02l.15.002C10.587 3.429 8.392 2 5.796 2 2.596 2 0 4.16 0 6.826m4.632-1.555a.77.77 0 1 1-1.54 0 .77.77 0 0 1 1.54 0m3.875 0a.77.77 0 1 1-1.54 0 .77.77 0 0 1 1.54 0',
    ],
  },
  {
    id: 'qq',
    label: 'QQ',
    tone: 'qq',
    viewBox: '0 0 16 16',
    paths: [
      'M6.048 3.323c.022.277-.13.523-.338.55-.21.026-.397-.176-.419-.453s.13-.523.338-.55c.21-.026.397.176.42.453Zm2.265-.24c-.603-.146-.894.256-.936.333-.027.048-.008.117.037.15.045.035.092.025.119-.003.361-.39.751-.172.829-.129l.011.007c.053.024.147.028.193-.098.023-.063.017-.11-.006-.142-.016-.023-.089-.08-.247-.118',
      'M11.727 6.719c0-.022.01-.375.01-.557 0-3.07-1.45-6.156-5.015-6.156S1.708 3.092 1.708 6.162c0 .182.01.535.01.557l-.72 1.795a26 26 0 0 0-.534 1.508c-.68 2.187-.46 3.093-.292 3.113.36.044 1.401-1.647 1.401-1.647 0 .979.504 2.256 1.594 3.179-.408.126-.907.319-1.228.556-.29.213-.253.43-.201.518.228.386 3.92.246 4.985.126 1.065.12 4.756.26 4.984-.126.052-.088.088-.305-.2-.518-.322-.237-.822-.43-1.23-.557 1.09-.922 1.594-2.2 1.594-3.178 0 0 1.041 1.69 1.401 1.647.168-.02.388-.926-.292-3.113a26 26 0 0 0-.534-1.508l-.72-1.795ZM9.773 5.53a.1.1 0 0 1-.009.096c-.109.159-1.554.943-3.033.943h-.017c-1.48 0-2.925-.784-3.034-.943a.1.1 0 0 1-.018-.055q0-.022.01-.04c.13-.287 1.43-.606 3.042-.606h.017c1.611 0 2.912.319 3.042.605m-4.32-.989c-.483.022-.896-.529-.922-1.229s.344-1.286.828-1.308c.483-.022.896.529.922 1.23.027.7-.344 1.286-.827 1.307Zm2.538 0c-.484-.022-.854-.607-.828-1.308.027-.7.44-1.25.923-1.23.483.023.853.608.827 1.309-.026.7-.439 1.251-.922 1.23ZM2.928 8.99q.32.063.639.117v2.336s1.104.222 2.21.068V9.363q.49.027.937.023h.017c1.117.013 2.474-.136 3.786-.396.097.622.151 1.386.097 2.284-.146 2.45-1.6 3.99-3.846 4.012h-.091c-2.245-.023-3.7-1.562-3.846-4.011-.054-.9 0-1.663.097-2.285',
    ],
  },
  {
    id: 'github',
    label: 'GitHub',
    tone: 'github',
    viewBox: '0 0 16 16',
    paths: [
      'M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8',
    ],
  },
]

function handleClick(provider) {
  ElMessage({
    type: 'info',
    message: `${provider.label} 登录正在对接中，敬请期待`,
    grouping: true,
  })
}
</script>

<template>
  <div class="third-party">
    <div class="third-party__divider" role="presentation">
      <span>或使用第三方账号继续</span>
    </div>
    <div class="third-party__list" role="group" aria-label="第三方登录占位">
      <button
        v-for="provider in providers"
        :key="provider.id"
        type="button"
        class="third-party__btn"
        :class="`third-party__btn--${provider.tone}`"
        :title="`使用 ${provider.label} 登录（占位，暂未开放）`"
        :aria-label="`使用 ${provider.label} 登录（占位，暂未开放）`"
        @click="handleClick(provider)"
      >
        <svg
          class="third-party__icon"
          :viewBox="provider.viewBox"
          fill="currentColor"
          aria-hidden="true"
        >
          <path v-for="(d, idx) in provider.paths" :key="idx" :d="d" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;

.third-party {
  display: grid;
  gap: 14px;
}

.third-party__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.third-party__divider::before,
.third-party__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.18), transparent);
}

.third-party__list {
  display: flex;
  justify-content: center;
  gap: 18px;
}

.third-party__btn {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #f8fafc;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, background $duration-fast $ease-out,
    border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out, color $duration-fast $ease-out;
}

.third-party__btn:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.32);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.36);
}

.third-party__btn:focus-visible {
  outline: 2px solid #5eead4;
  outline-offset: 2px;
}

.third-party__icon {
  width: 22px;
  height: 22px;
}

.third-party__btn--wechat:hover {
  color: #07c160;
  border-color: rgba(7, 193, 96, 0.55);
}

.third-party__btn--qq:hover {
  color: #1989fa;
  border-color: rgba(25, 137, 250, 0.55);
}

.third-party__btn--github:hover {
  color: #f1f5f9;
  background: rgba(36, 41, 47, 0.92);
  border-color: rgba(255, 255, 255, 0.32);
}
</style>
