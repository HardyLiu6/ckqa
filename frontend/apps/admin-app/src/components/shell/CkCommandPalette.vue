<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  filterGroups,
  flattenForKeyboard,
  isCommandShortcut,
  shouldIgnoreShortcut,
} from './command-palette-model.js'

const props = defineProps({
  groups: { type: Array, default: () => [] },
})

const router = useRouter()

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref(null)
// 打开前的焦点元素，关闭时归还焦点，配合 role="dialog" 的 a11y 语义
let lastFocusedElement = null

const filtered = computed(() => filterGroups(props.groups, query.value))
const flat = computed(() => flattenForKeyboard(filtered.value))

function openPalette() {
  if (open.value) return
  lastFocusedElement = typeof document !== 'undefined' ? document.activeElement : null
  open.value = true
  nextTick(() => inputRef.value?.focus())
}

function closePalette() {
  if (!open.value) return
  open.value = false
  query.value = ''
  activeIndex.value = 0
  if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
    lastFocusedElement.focus()
  }
  lastFocusedElement = null
}

function handleKeydown(event) {
  if (isCommandShortcut(event) && !shouldIgnoreShortcut(event.target)) {
    event.preventDefault()
    openPalette()
    return
  }
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    closePalette()
    return
  }
  if (!open.value) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = Math.min(flat.value.length - 1, activeIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = Math.max(0, activeIndex.value - 1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    activate(flat.value[activeIndex.value])
  }
}

function activate(item) {
  if (!item) return
  if (item.path) {
    router.push(item.path)
  } else if (typeof item.onActivate === 'function') {
    item.onActivate()
  }
  closePalette()
}

watch(query, () => { activeIndex.value = 0 })

// 打开时锁定背景滚动，关闭时恢复，避免 backdrop 后页面仍可滚动
watch(open, (next) => {
  if (typeof document === 'undefined') return
  document.body.style.overflow = next ? 'hidden' : ''
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (typeof document !== 'undefined') document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="ck-cmdpalette-fade">
      <div v-if="open" class="ck-cmdpalette" role="dialog" aria-modal="true" aria-label="命令面板">
        <div class="ck-cmdpalette-backdrop" @click="closePalette" />
        <div class="ck-cmdpalette-frame" role="document">
          <input
            ref="inputRef"
            v-model="query"
            class="ck-cmdpalette-input"
            type="search"
            placeholder="搜索课程 / 资料 / 知识库 / 操作"
            aria-label="命令面板搜索"
          >
          <div class="ck-cmdpalette-results">
            <div v-if="!filtered.length" class="ck-cmdpalette-empty">暂无匹配</div>
            <section
              v-for="group in filtered"
              :key="group.key"
              class="ck-cmdpalette-group"
            >
              <header>{{ group.label }}</header>
              <ul>
                <li
                  v-for="item in group.items"
                  :key="item.id"
                  :class="{ 'is-active': item === flat[activeIndex] }"
                  @click="activate(item)"
                >
                  <span>{{ item.label }}</span>
                  <span v-if="item.hint" class="ck-cmdpalette-hint">{{ item.hint }}</span>
                </li>
              </ul>
            </section>
          </div>
          <footer class="ck-cmdpalette-footer">
            <span>↑↓ 选择</span><span>Enter 确认</span><span>Esc 关闭</span>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.ck-cmdpalette {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 12vh;
}
.ck-cmdpalette-backdrop {
  position: absolute; inset: 0;
  background: var(--ckqa-overlay-backdrop);
  backdrop-filter: blur(6px) saturate(120%);
  -webkit-backdrop-filter: blur(6px) saturate(120%);
}
[data-theme='dark'] .ck-cmdpalette-backdrop {
  background: var(--ckqa-overlay-backdrop);
}
.ck-cmdpalette-frame {
  position: relative;
  width: min(640px, 92vw);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-xl);
  box-shadow: var(--ckqa-shadow-modal);
  overflow: hidden;
}

/* 进出场：backdrop 仅淡入，frame 同时微缩放上滑。
 * 使用 ::v-deep 避免 scoped 选择器穿透不到 .ck-cmdpalette-backdrop / frame */
.ck-cmdpalette-fade-enter-active,
.ck-cmdpalette-fade-leave-active {
  transition: opacity var(--ckqa-duration-base, 0.18s) var(--ckqa-ease-glass, ease-out);
}
.ck-cmdpalette-fade-enter-from,
.ck-cmdpalette-fade-leave-to { opacity: 0; }

.ck-cmdpalette-fade-enter-active .ck-cmdpalette-frame,
.ck-cmdpalette-fade-leave-active .ck-cmdpalette-frame {
  transition:
    transform var(--ckqa-duration-base, 0.18s) var(--ckqa-ease-spring, cubic-bezier(0.34, 1.3, 0.64, 1)),
    opacity var(--ckqa-duration-base, 0.18s) var(--ckqa-ease-glass, ease-out);
}
.ck-cmdpalette-fade-enter-from .ck-cmdpalette-frame,
.ck-cmdpalette-fade-leave-to .ck-cmdpalette-frame {
  transform: translateY(-8px) scale(0.97);
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .ck-cmdpalette-fade-enter-active,
  .ck-cmdpalette-fade-leave-active,
  .ck-cmdpalette-fade-enter-active .ck-cmdpalette-frame,
  .ck-cmdpalette-fade-leave-active .ck-cmdpalette-frame {
    transition: none;
  }
}

.ck-cmdpalette-input {
  width: 100%; padding: 14px 16px;
  border: none; border-bottom: 1px solid var(--ckqa-border-soft);
  background: transparent;
  font-size: var(--ckqa-text-md-size);
  color: var(--ckqa-text);
  outline: none;
}
.ck-cmdpalette-results { max-height: 60vh; overflow-y: auto; padding: 6px; }
.ck-cmdpalette-empty {
  padding: 18px; text-align: center;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.ck-cmdpalette-group header {
  padding: 8px 10px 4px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
}
.ck-cmdpalette-group ul { list-style: none; margin: 0; padding: 0; }
.ck-cmdpalette-group li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 10px;
  border-radius: var(--ckqa-radius-sm);
  cursor: pointer;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
}
.ck-cmdpalette-group li.is-active,
.ck-cmdpalette-group li:hover {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
}
.ck-cmdpalette-hint { color: var(--ckqa-text-weak); font-size: var(--ckqa-text-xs-size); }
.ck-cmdpalette-footer {
  display: flex; gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--ckqa-border-soft);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-xs-size);
}
</style>
