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

const filtered = computed(() => filterGroups(props.groups, query.value))
const flat = computed(() => flattenForKeyboard(filtered.value))

function handleKeydown(event) {
  if (isCommandShortcut(event) && !shouldIgnoreShortcut(event.target)) {
    event.preventDefault()
    open.value = true
    nextTick(() => inputRef.value?.focus())
    return
  }
  if (event.key === 'Escape' && open.value) {
    open.value = false
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
  open.value = false
  query.value = ''
}

watch(query, () => { activeIndex.value = 0 })

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <div v-if="open" class="ck-cmdpalette" role="dialog" aria-modal="true">
    <div class="ck-cmdpalette-backdrop" @click="open = false" />
    <div class="ck-cmdpalette-frame">
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
</template>

<style scoped lang="scss">
.ck-cmdpalette {
  position: fixed; inset: 0; z-index: 60;
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 12vh;
}
.ck-cmdpalette-backdrop {
  position: absolute; inset: 0;
  background: rgb(28 26 23 / 30%);
}
.ck-cmdpalette-frame {
  position: relative;
  width: min(640px, 92vw);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-xl);
  box-shadow: var(--ckqa-shadow-lg);
  overflow: hidden;
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
