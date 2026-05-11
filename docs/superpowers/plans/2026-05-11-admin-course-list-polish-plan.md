# Admin 课程列表页打磨 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the courses list page redundancy (面包屑 + eyebrow + 标题三重重复)，移除默认封面的 CKQA 英文字样，并让共享 `CkResourceCard` 在课程语境下提供二行标题 / 浮动状态 / 强调数字 / hover 提升的可选变体。

**Architecture:** Pure-JS model 文件承担调色板与首字符派生（`node --test` 单测），Vue 单文件组件只渲染 SVG 与卡片。`CkResourceCard` 通过 3 个**默认值与现状一致**的 prop + 一个 `#cover` 默认槽扩展，避免现有调用方（资料、知识库、QA 列表）回归。课程列表页通过这些 prop 与槽接入新封面组件。后端静态 SVG 文本同步清洗以兜底任何漏迁路径。

**Tech Stack:** Vue 3 `<script setup>`、Vite、Vue Router、SCSS、`node --test` (Node 内建测试运行器)、Playwright 1.59。

**Spec:** `docs/superpowers/specs/2026-05-11-admin-course-list-polish-design.md`

**Worktree:** `.worktrees/admin-redesign-m1-m2`（分支 `feature/admin-app-redesign-m1-m2`）。所有路径都相对该 worktree 根。

---

## File Structure

| 路径 | 动作 | 职责 |
|------|------|------|
| `frontend/apps/admin-app/src/views/courses/course-page-copy.js` | Modify | 删除 `list.eyebrow` |
| `frontend/apps/admin-app/src/views/courses/CourseListPage.vue` | Modify | 拆 eyebrow 绑定、接入新封面组件与卡片 prop |
| `frontend/apps/admin-app/src/components/common/course-cover-art-model.js` | Create | 纯函数：`pickPalette(seed)` / `pickGlyph(label)` / `PALETTES` 常量 |
| `frontend/apps/admin-app/src/components/common/course-cover-art-model.test.js` | Create | `node --test` 单测 |
| `frontend/apps/admin-app/src/components/common/CkCourseCoverArt.vue` | Create | 调用 model 渲染 inline SVG |
| `frontend/apps/admin-app/src/components/common/CkResourceCard.vue` | Modify | 新增 3 个 prop + `#cover` 槽 + hover 增强 |
| `frontend/apps/admin-app/src/components/common/resource-card-model.js` | Modify (微) | 暴露常量 `META_VARIANTS` 给卡片与单测共享 |
| `frontend/apps/admin-app/src/components/common/resource-card-model.test.js` | Modify | 追加 `META_VARIANTS` 用例 |
| `backend/ckqa-back/src/main/resources/static/assets/course-covers/default-course-cover.svg` | Modify | 删除 `CKQA Course` 与英文副标题 `<text>` |
| `frontend/apps/admin-app/e2e/course-flow.spec.js` | Modify | 追加面包屑 / hero / 封面文本断言 |

---

## Task 1: 移除课程列表 hero 的 eyebrow（最小可独立提交）

**Files:**
- Modify: `frontend/apps/admin-app/src/views/courses/course-page-copy.js`
- Modify: `frontend/apps/admin-app/src/views/courses/CourseListPage.vue:100-114`
- Modify: `frontend/apps/admin-app/e2e/course-flow.spec.js`

- [ ] **Step 1: 在 e2e 里写一条 failing 断言：课程列表 hero 不再出现 `生产 · 课程`**

打开 `frontend/apps/admin-app/e2e/course-flow.spec.js`，找到既有的「课程列表 → 课程详情 → 切换到资料 tab」用例，在 `await page.goto('/app/courses')` 之后插入：

```javascript
  // 课程 hero 不再有 eyebrow（避免与顶部面包屑「生产 / 课程列表」重复）
  await expect(page.locator('.ck-page-hero-eyebrow')).toHaveCount(0)
  // 顶部面包屑仍存在
  await expect(page.getByTestId('breadcrumbs')).toContainText('生产')
  await expect(page.getByTestId('breadcrumbs')).toContainText('课程列表')
```

如 `getByTestId('breadcrumbs')` 在当前 layout 中不存在（先 grep 一下 `data-testid="breadcrumbs"`），用 `page.locator('nav.ck-breadcrumbs')` 替代——以实际 layout 中存在的选择器为准；这一步只追加新断言，不删除原有逻辑。

- [ ] **Step 2: 跑测试，确认它失败**

```bash
cd frontend/apps/admin-app
pnpm exec playwright test e2e/course-flow.spec.js --reporter=line
```

Expected：当前 hero 仍渲染 `生产 · 课程`，`toHaveCount(0)` 断言失败。

- [ ] **Step 3: 删除 `COURSE_PAGE_COPY.list.eyebrow`**

在 `frontend/apps/admin-app/src/views/courses/course-page-copy.js` 里把 `list` 对象改成：

```javascript
  list: {
    title: '课程',
    subtitle: '管理已有课程，进入详情后可以维护成员、资料和知识库。',
    emptyTitle: '还没有课程',
    emptyDescription: '点击右上角"新建课程"开始第一门课的搭建。',
    createCta: '新建课程',
    loadError: '课程列表加载失败，请稍后重试。',
  },
```

（删掉 `eyebrow: '生产 · 课程'` 这一行，其他字段保持。）

- [ ] **Step 4: 移除 `CourseListPage.vue` 的 eyebrow 绑定**

把 `<CkPageHero>` 开标签改成：

```vue
    <CkPageHero
      :title="COURSE_PAGE_COPY.list.title"
      :subtitle="COURSE_PAGE_COPY.list.subtitle"
    >
```

（删掉 `:eyebrow="COURSE_PAGE_COPY.list.eyebrow"` 这一行。）

- [ ] **Step 5: 跑测试，确认通过**

```bash
cd frontend/apps/admin-app
pnpm exec playwright test e2e/course-flow.spec.js --reporter=line
```

Expected：原用例 + 新断言均 PASS。

- [ ] **Step 6: 提交**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add frontend/apps/admin-app/src/views/courses/course-page-copy.js \
        frontend/apps/admin-app/src/views/courses/CourseListPage.vue \
        frontend/apps/admin-app/e2e/course-flow.spec.js
git commit -m "fix(admin/course-list): remove redundant hero eyebrow"
```

---

## Task 2: 新增 `course-cover-art-model.js` 纯函数 + 单测

**Files:**
- Create: `frontend/apps/admin-app/src/components/common/course-cover-art-model.js`
- Create: `frontend/apps/admin-app/src/components/common/course-cover-art-model.test.js`

- [ ] **Step 1: 写 failing 单测**

新建 `frontend/apps/admin-app/src/components/common/course-cover-art-model.test.js`：

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PALETTES,
  pickPalette,
  pickGlyph,
  hashString,
} from './course-cover-art-model.js'

test('PALETTES 提供 6 套调色板，每套都有 bgFrom/bgTo/plateRing/accent', () => {
  assert.equal(PALETTES.length, 6)
  for (const palette of PALETTES) {
    assert.ok(palette.bgFrom?.startsWith('#'))
    assert.ok(palette.bgTo?.startsWith('#'))
    assert.ok(palette.plateRing?.startsWith('#'))
    assert.ok(palette.accent?.startsWith('#'))
  }
})

test('hashString 对相同输入产出相同 32-bit 整数', () => {
  assert.equal(hashString('crs-123'), hashString('crs-123'))
  assert.notEqual(hashString('crs-123'), hashString('crs-124'))
})

test('hashString 空 / null 输入返回 0', () => {
  assert.equal(hashString(''), 0)
  assert.equal(hashString(null), 0)
  assert.equal(hashString(undefined), 0)
})

test('pickPalette 对同 seed 稳定，分布在 PALETTES 范围内', () => {
  const first = pickPalette('crs-20260101-120000')
  const second = pickPalette('crs-20260101-120000')
  assert.equal(first, second)
  assert.ok(PALETTES.includes(first))
})

test('pickPalette seed 缺省回退到默认调色板，不抛错', () => {
  assert.equal(pickPalette(null), PALETTES[0])
  assert.equal(pickPalette(''), PALETTES[0])
})

test('pickGlyph 中文取首字', () => {
  assert.equal(pickGlyph('操作系统2026春'), '操')
  assert.equal(pickGlyph('  公开访问演示课  '), '公')
})

test('pickGlyph ASCII 取前两字符并大写', () => {
  assert.equal(pickGlyph('Smoke GraphRAG Isolation 20260101'), 'SM')
  assert.equal(pickGlyph('osCourse'), 'OS')
  assert.equal(pickGlyph('a'), 'A')
})

test('pickGlyph 混合：首字符是中文时只取 1 字', () => {
  assert.equal(pickGlyph('课 Course'), '课')
})

test('pickGlyph 空 / null 回退到「课」', () => {
  assert.equal(pickGlyph(''), '课')
  assert.equal(pickGlyph(null), '课')
  assert.equal(pickGlyph(undefined), '课')
  assert.equal(pickGlyph('   '), '课')
})
```

- [ ] **Step 2: 跑测试，确认失败**

```bash
cd frontend/apps/admin-app
node --test src/components/common/course-cover-art-model.test.js
```

Expected：FAIL（模块尚未实现）。

- [ ] **Step 3: 写实现**

新建 `frontend/apps/admin-app/src/components/common/course-cover-art-model.js`：

```javascript
// 课程默认封面的调色板与首字符派生
// 设计 spec: docs/superpowers/specs/2026-05-11-admin-course-list-polish-design.md

export const PALETTES = Object.freeze([
  // 蓝
  { bgFrom: '#eef4ff', bgTo: '#dbe7ff', plateRing: '#bfd3ff', accent: '#2563eb' },
  // 绿
  { bgFrom: '#ecfdf5', bgTo: '#d1fae5', plateRing: '#a7f3d0', accent: '#059669' },
  // 紫
  { bgFrom: '#f5f3ff', bgTo: '#e9d5ff', plateRing: '#d8b4fe', accent: '#7c3aed' },
  // 琥珀
  { bgFrom: '#fff7ed', bgTo: '#fed7aa', plateRing: '#fdba74', accent: '#d97706' },
  // 玫红
  { bgFrom: '#fdf2f8', bgTo: '#fbcfe8', plateRing: '#f9a8d4', accent: '#db2777' },
  // 青
  { bgFrom: '#ecfeff', bgTo: '#cffafe', plateRing: '#a5f3fc', accent: '#0891b2' },
])

// djb2 风格的 32-bit 字符串哈希。同字符串 → 同结果。
export function hashString(input) {
  if (!input) return 0
  const text = String(input)
  let hash = 5381
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0
  }
  return hash
}

export function pickPalette(seed) {
  if (!seed) return PALETTES[0]
  const hash = Math.abs(hashString(seed))
  return PALETTES[hash % PALETTES.length]
}

// 判定一个 code point 是否属于"取 1 字即可"的非 ASCII 范畴
// （CJK 中日韩、假名、谚文、emoji 等）
function isWideGlyph(ch) {
  if (!ch) return false
  const code = ch.codePointAt(0)
  return code > 0x024F
}

export function pickGlyph(label) {
  if (label == null) return '课'
  const trimmed = String(label).trim()
  if (!trimmed) return '课'
  const first = trimmed[0]
  if (isWideGlyph(first)) return first
  // ASCII / 拉丁：取前两个非空字符（若只剩一个就给一个）
  const ascii = trimmed.replace(/\s+/g, '')
  if (!ascii) return '课'
  return ascii.slice(0, 2).toUpperCase()
}
```

- [ ] **Step 4: 跑测试，确认通过**

```bash
cd frontend/apps/admin-app
node --test src/components/common/course-cover-art-model.test.js
```

Expected：所有用例 PASS。

- [ ] **Step 5: 提交**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add frontend/apps/admin-app/src/components/common/course-cover-art-model.js \
        frontend/apps/admin-app/src/components/common/course-cover-art-model.test.js
git commit -m "feat(admin/course-cover): add palette + glyph derivation model"
```

---

## Task 3: 新增 `CkCourseCoverArt.vue` 内联 SVG 组件

**Files:**
- Create: `frontend/apps/admin-app/src/components/common/CkCourseCoverArt.vue`

- [ ] **Step 1: 写组件**

新建 `frontend/apps/admin-app/src/components/common/CkCourseCoverArt.vue`：

```vue
<script setup>
import { computed } from 'vue'

import { pickPalette, pickGlyph } from './course-cover-art-model.js'

const props = defineProps({
  seed: { type: String, default: '' },
  label: { type: String, default: '' },
  ariaLabel: { type: String, default: '课程封面' },
})

const palette = computed(() => pickPalette(props.seed || props.label))
const glyph = computed(() => pickGlyph(props.label))
// 单字符（中文/CJK）字号更大，双字符稍小以保持留白
const glyphFontSize = computed(() => (glyph.value.length === 1 ? 220 : 180))
// 为同一封面生成可复用的 gradient id（避免多卡片冲突）
const gradientId = computed(() => `ck-cover-grad-${Math.abs(props.seed?.length ?? 0)}-${glyph.value.charCodeAt(0)}`)
</script>

<template>
  <svg
    class="ck-course-cover-art"
    viewBox="0 0 960 540"
    role="img"
    :aria-label="ariaLabel"
    preserveAspectRatio="xMidYMid slice"
  >
    <title>{{ ariaLabel }}</title>
    <defs>
      <linearGradient :id="gradientId" x1="0" x2="1" y1="0" y2="1">
        <stop offset="0" :stop-color="palette.bgFrom" />
        <stop offset="1" :stop-color="palette.bgTo" />
      </linearGradient>
    </defs>
    <rect width="960" height="540" :fill="`url(#${gradientId})`" />
    <!-- 装饰底坡 -->
    <path
      d="M0 410 C170 350 285 470 455 400 C625 330 740 365 960 292 L960 540 L0 540 Z"
      :fill="palette.plateRing"
      opacity="0.32"
    />
    <path
      d="M0 450 C180 400 330 470 500 425 C680 378 760 425 960 365 L960 540 L0 540 Z"
      :fill="palette.accent"
      opacity="0.16"
    />
    <!-- 中央面板 -->
    <g transform="translate(300 120)">
      <rect x="0" y="0" width="360" height="300" rx="28" fill="#ffffff" :stroke="palette.plateRing" stroke-width="4" />
      <text
        x="180"
        y="180"
        text-anchor="middle"
        dominant-baseline="central"
        font-family="var(--ckqa-font-display, 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif)"
        :font-size="glyphFontSize"
        font-weight="700"
        :fill="palette.accent"
      >{{ glyph }}</text>
    </g>
    <!-- 装饰节点：保留旧设计的图谱意象，但缩小并淡化 -->
    <g :stroke="palette.accent" stroke-width="3" stroke-linecap="round" fill="#ffffff" opacity="0.65">
      <line x1="760" y1="120" x2="820" y2="80" />
      <line x1="820" y1="80" x2="880" y2="130" />
      <line x1="760" y1="120" x2="880" y2="130" />
      <circle cx="760" cy="120" r="9" />
      <circle cx="820" cy="80" r="9" />
      <circle cx="880" cy="130" r="9" />
    </g>
  </svg>
</template>

<style scoped lang="scss">
.ck-course-cover-art {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
```

- [ ] **Step 2: 提交（组件本身将在 Task 5 通过 e2e 间接验证；这里先入库）**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add frontend/apps/admin-app/src/components/common/CkCourseCoverArt.vue
git commit -m "feat(admin/course-cover): add CkCourseCoverArt inline SVG"
```

---

## Task 4: 扩展 `CkResourceCard` — 三个 prop + `#cover` 槽 + hover 增强

**Files:**
- Modify: `frontend/apps/admin-app/src/components/common/CkResourceCard.vue`
- Modify: `frontend/apps/admin-app/src/components/common/resource-card-model.js`
- Modify: `frontend/apps/admin-app/src/components/common/resource-card-model.test.js`

- [ ] **Step 1: 在 model 里加 META_VARIANTS 常量 + failing 测试**

在 `frontend/apps/admin-app/src/components/common/resource-card-model.test.js` 末尾追加：

```javascript
import { META_VARIANTS, normalizeMetaVariant } from './resource-card-model.js'

test('META_VARIANTS 暴露 inline / emphasis 两种取值', () => {
  assert.deepEqual([...META_VARIANTS].sort(), ['emphasis', 'inline'])
})

test('normalizeMetaVariant 非法值回退到 inline', () => {
  assert.equal(normalizeMetaVariant('inline'), 'inline')
  assert.equal(normalizeMetaVariant('emphasis'), 'emphasis')
  assert.equal(normalizeMetaVariant('weird'), 'inline')
  assert.equal(normalizeMetaVariant(null), 'inline')
  assert.equal(normalizeMetaVariant(undefined), 'inline')
})
```

跑：

```bash
cd frontend/apps/admin-app
node --test src/components/common/resource-card-model.test.js
```

Expected：FAIL — `META_VARIANTS is not exported`。

- [ ] **Step 2: 在 model 里加导出**

在 `frontend/apps/admin-app/src/components/common/resource-card-model.js` 末尾追加：

```javascript
// 元信息视觉变体：inline = 一行 label + value；emphasis = 数字大号 + label 小字
export const META_VARIANTS = Object.freeze(new Set(['inline', 'emphasis']))

export function normalizeMetaVariant(variant) {
  return META_VARIANTS.has(variant) ? variant : 'inline'
}
```

跑测试确认通过：

```bash
cd frontend/apps/admin-app
node --test src/components/common/resource-card-model.test.js
```

Expected：所有用例 PASS。

- [ ] **Step 3: 扩展 `CkResourceCard.vue`**

将 `frontend/apps/admin-app/src/components/common/CkResourceCard.vue` 全文替换为：

```vue
<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'

import {
  resolveCardStatus,
  formatMetaEntries,
  normalizeMetaVariant,
} from './resource-card-model.js'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  status: { type: String, default: '' },
  statusLabel: { type: String, default: '' },
  meta: { type: Array, default: () => [] },
  to: { type: [String, Object], default: null },
  cover: { type: String, default: '' },
  titleClamp: { type: Number, default: 1 },
  statusFloating: { type: Boolean, default: false },
  metaVariant: { type: String, default: 'inline' },
})

const resolvedStatus = computed(() => resolveCardStatus(props.status, props.statusLabel))
const metaEntries = computed(() => formatMetaEntries(props.meta))
const resolvedMetaVariant = computed(() => normalizeMetaVariant(props.metaVariant))
const titleClampValue = computed(() => Math.max(1, Number(props.titleClamp) || 1))
const titleMultiline = computed(() => titleClampValue.value > 1)
</script>

<template>
  <article class="ck-resource-card ck-glass-card" data-testid="resource-card">
    <RouterLink v-if="to" :to="to" class="ck-resource-card-link">
      <figure v-if="cover || $slots.cover" class="ck-resource-card-cover">
        <slot name="cover">
          <img v-if="cover" :src="cover" :alt="title" loading="lazy" />
        </slot>
        <CkStatusPill
          v-if="statusFloating && resolvedStatus.label"
          class="ck-resource-card-status-floating"
          data-testid="resource-card-status-floating"
          :tone="resolvedStatus.tone"
          :label="resolvedStatus.label"
          size="sm"
        />
      </figure>
      <div class="ck-resource-card-body">
        <header class="ck-resource-card-header">
          <h3
            class="ck-resource-card-title"
            :class="{ 'ck-resource-card-title-multiline': titleMultiline }"
            :style="titleMultiline ? { '--ck-card-title-clamp': titleClampValue } : null"
            :data-clamp="titleClampValue"
          >{{ title }}</h3>
          <CkStatusPill
            v-if="!statusFloating && resolvedStatus.label"
            :tone="resolvedStatus.tone"
            :label="resolvedStatus.label"
            size="sm"
          />
        </header>
        <p v-if="description" class="ck-resource-card-description">{{ description }}</p>
        <ul
          v-if="metaEntries.length"
          class="ck-resource-card-meta"
          :class="`ck-resource-card-meta-${resolvedMetaVariant}`"
          :data-meta-variant="resolvedMetaVariant"
        >
          <li v-for="entry in metaEntries" :key="entry.label">
            <template v-if="resolvedMetaVariant === 'emphasis'">
              <strong>{{ entry.value }}</strong>
              <span>{{ entry.label }}</span>
            </template>
            <template v-else>
              <span>{{ entry.label }}</span>
              <strong>{{ entry.value }}</strong>
            </template>
          </li>
        </ul>
      </div>
    </RouterLink>
    <div v-if="$slots.actions" class="ck-resource-card-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<style scoped lang="scss">
.ck-resource-card {
  position: relative;
  overflow: hidden;
  transition:
    box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    transform var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-resource-card:hover {
  box-shadow: var(--ckqa-shadow-card-hover);
  transform: translateY(-2px);
  border-color: var(--ckqa-border-strong);
}
.ck-resource-card-link {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  color: inherit;
  text-decoration: none;
}
.ck-resource-card-cover {
  position: relative;
  margin: 0;
  aspect-ratio: 16 / 9;
  background: var(--ckqa-surface-muted);
  overflow: hidden;
}
.ck-resource-card-cover :slotted(img),
.ck-resource-card-cover :slotted(svg),
.ck-resource-card-cover img,
.ck-resource-card-cover svg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.ck-resource-card-status-floating {
  position: absolute;
  top: var(--ckqa-space-2);
  right: var(--ckqa-space-2);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(6px);
  border-radius: var(--ckqa-radius-pill, 999px);
  padding: 2px 8px;
}
.ck-resource-card-body {
  padding: var(--ckqa-space-3) var(--ckqa-space-4) var(--ckqa-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.ck-resource-card-header {
  display: flex;
  justify-content: space-between;
  gap: var(--ckqa-space-2);
  align-items: center;
}
.ck-resource-card-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-resource-card-title-multiline {
  white-space: normal;
  text-overflow: clip;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: var(--ck-card-title-clamp, 2);
  overflow: hidden;
}
.ck-resource-card-description {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ck-resource-card-meta {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ckqa-space-2);
  font-size: var(--ckqa-text-xs-size);
}
.ck-resource-card-meta-inline li {
  display: flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
}
.ck-resource-card-meta-inline span {
  color: var(--ckqa-text-weak);
}
.ck-resource-card-meta-inline strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-resource-card-meta-emphasis li {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}
.ck-resource-card-meta-emphasis strong {
  font-size: var(--ckqa-text-xl-size);
  line-height: var(--ckqa-text-xl-line);
  font-weight: 700;
  color: var(--ckqa-text);
}
.ck-resource-card-meta-emphasis span {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
}
.ck-resource-card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}
</style>
```

- [ ] **Step 4: 跑既有单测，保证 `formatMetaEntries` 等 model 用例无回归**

```bash
cd frontend/apps/admin-app
node --test src/components/common/resource-card-model.test.js
```

Expected：所有 PASS。

- [ ] **Step 5: 跑现有 e2e 中所有用了 CkResourceCard 的页面，确认默认行为没回归**

```bash
cd frontend/apps/admin-app
pnpm exec playwright test \
  e2e/course-flow.spec.js \
  e2e/kb-detail.spec.js \
  e2e/material-detail.spec.js \
  --reporter=line
```

Expected：全部 PASS。如有失败，先看是否因 `CkResourceCard` 默认行为意外变化导致；按需修复后再继续。

- [ ] **Step 6: 提交**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add frontend/apps/admin-app/src/components/common/CkResourceCard.vue \
        frontend/apps/admin-app/src/components/common/resource-card-model.js \
        frontend/apps/admin-app/src/components/common/resource-card-model.test.js
git commit -m "feat(admin/resource-card): add titleClamp + statusFloating + metaVariant + cover slot"
```

---

## Task 5: `CourseListPage` 接入新组件与新 prop

**Files:**
- Modify: `frontend/apps/admin-app/src/views/courses/CourseListPage.vue`

- [ ] **Step 1: 改 import 与 `cards` 计算属性**

在 `frontend/apps/admin-app/src/views/courses/CourseListPage.vue` 顶部 import 区追加：

```javascript
import CkCourseCoverArt from '../../components/common/CkCourseCoverArt.vue'
```

把 `cards` 计算属性改为：

```javascript
const cards = computed(() =>
  state.value.rows.map((row) => {
    const course = row.raw ?? {}
    const status = String(course.status ?? '').toLowerCase()
    const thumbnail = row.thumbnailUrl || course.coverUrl || ''
    return {
      id: row.id,
      to: row.to,
      title: course.courseName || course.courseId || '未命名课程',
      description: course.description || course.courseDesc || '',
      status: status === 'archived' ? 'archived' : 'active',
      cover: thumbnail,
      useDefaultArt: !thumbnail,
      seed: row.id || course.courseId || course.courseName || '',
      meta: [
        { label: '资料', value: course.materialCount },
        { label: '知识库', value: course.knowledgeBaseCount },
      ],
    }
  }),
)
```

（移除对 `DEFAULT_COURSE_COVER_URL` 的引用；`import { loadModulePage }` 那一行同步去掉该 named import。）

调整 import 行为：

```javascript
import { loadModulePage } from '../pages/module-loaders.js'
```

- [ ] **Step 2: 改模板，把卡片 cover 改用 `#cover` 槽**

把 `<ul class="course-list-page-grid">` 的内层 `<CkResourceCard>` 块替换为：

```vue
      <li v-for="card in cards" :key="card.id">
        <CkResourceCard
          :title="card.title"
          :description="card.description"
          :status="card.status"
          :meta="card.meta"
          :to="card.to"
          :title-clamp="2"
          status-floating
          meta-variant="emphasis"
        >
          <template #cover>
            <CkCourseCoverArt
              v-if="card.useDefaultArt"
              :seed="card.seed"
              :label="card.title"
            />
            <img v-else :src="card.cover" :alt="card.title" loading="lazy" />
          </template>
        </CkResourceCard>
      </li>
```

- [ ] **Step 3: e2e 补一条断言：默认封面 SVG 不再包含 `CKQA`**

在 `frontend/apps/admin-app/e2e/course-flow.spec.js` 的「课程列表 → 课程详情」用例里、`expect(page.getByTestId('resource-card').first()).toContainText('操作系统')` 之后追加：

```javascript
  // 默认课程封面（无 thumbnailUrl）不再渲染 CKQA 英文品牌字
  const coverSvg = page.getByTestId('resource-card').first().locator('.ck-resource-card-cover svg.ck-course-cover-art')
  await expect(coverSvg).toHaveCount(1)
  await expect(coverSvg).not.toContainText('CKQA')
  // 状态徽标浮在封面上
  await expect(page.getByTestId('resource-card').first().getByTestId('resource-card-status-floating')).toBeVisible()
  // 资料/知识库 meta 走 emphasis（数字字号变大）
  await expect(page.getByTestId('resource-card').first().locator('ul[data-meta-variant="emphasis"]')).toHaveCount(1)
```

- [ ] **Step 4: 跑 e2e 确认通过**

```bash
cd frontend/apps/admin-app
pnpm exec playwright test e2e/course-flow.spec.js --reporter=line
```

Expected：所有用例 PASS。

- [ ] **Step 5: 浏览器手测（如本地有 dev 服务器）**

```bash
cd frontend/apps/admin-app
pnpm dev
```

浏览器打开 `/app/courses`，目视确认：
- 顶部面包屑只有一行 `生产 / 课程列表`；hero 直接显示「课程」+ 副标题，没有第二行 eyebrow。
- 4 张卡的封面颜色不同，各显示对应课程的首字符（例如「Smoke GraphRAG…」显示 `SM`、「操作系统2026春」显示 `操`）。
- 状态徽标浮在封面右上角；课程名超长时换行不再被裁掉。
- 鼠标悬停时卡片轻微上抬、边框颜色变深。

如无 dev 环境，跳过此步。

- [ ] **Step 6: 提交**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add frontend/apps/admin-app/src/views/courses/CourseListPage.vue \
        frontend/apps/admin-app/e2e/course-flow.spec.js
git commit -m "feat(admin/course-list): adopt cover-art component + emphasized card variant"
```

---

## Task 6: 清理后端默认封面 SVG 文本

**Files:**
- Modify: `backend/ckqa-back/src/main/resources/static/assets/course-covers/default-course-cover.svg`

- [ ] **Step 1: 改 SVG**

把 `backend/ckqa-back/src/main/resources/static/assets/course-covers/default-course-cover.svg` 全文替换为：

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540" role="img" aria-labelledby="title desc">
  <title id="title">默认课程封面</title>
  <desc id="desc">带有资料卡片与图谱节点的默认封面</desc>
  <defs>
    <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
      <stop offset="0" stop-color="#f8fafc"/>
      <stop offset="0.52" stop-color="#eef7f0"/>
      <stop offset="1" stop-color="#e8f0ff"/>
    </linearGradient>
    <linearGradient id="accent" x1="0" x2="1" y1="0" y2="0">
      <stop offset="0" stop-color="#2563eb"/>
      <stop offset="1" stop-color="#059669"/>
    </linearGradient>
  </defs>
  <rect width="960" height="540" fill="url(#bg)"/>
  <path d="M0 410 C170 350 285 470 455 400 C625 330 740 365 960 292 L960 540 L0 540 Z" fill="#dbeafe" opacity="0.76"/>
  <path d="M0 450 C180 400 330 470 500 425 C680 378 760 425 960 365 L960 540 L0 540 Z" fill="#bbf7d0" opacity="0.62"/>
  <g transform="translate(188 120)">
    <rect x="0" y="0" width="292" height="260" rx="22" fill="#ffffff" stroke="#cbd5e1" stroke-width="3"/>
    <rect x="34" y="42" width="224" height="22" rx="11" fill="#2563eb"/>
    <rect x="34" y="88" width="164" height="16" rx="8" fill="#94a3b8"/>
    <rect x="34" y="122" width="198" height="16" rx="8" fill="#cbd5e1"/>
    <rect x="34" y="156" width="142" height="16" rx="8" fill="#cbd5e1"/>
    <rect x="34" y="200" width="98" height="32" rx="16" fill="#dcfce7"/>
  </g>
  <g fill="none" stroke="#334155" stroke-width="4" stroke-linecap="round">
    <path d="M548 204 L636 156 L724 206"/>
    <path d="M636 156 L640 290"/>
    <path d="M548 204 L640 290 L724 206"/>
  </g>
  <g>
    <circle cx="548" cy="204" r="34" fill="#ffffff" stroke="#2563eb" stroke-width="5"/>
    <circle cx="636" cy="156" r="34" fill="#ffffff" stroke="#059669" stroke-width="5"/>
    <circle cx="724" cy="206" r="34" fill="#ffffff" stroke="#7c3aed" stroke-width="5"/>
    <circle cx="640" cy="290" r="40" fill="#ffffff" stroke="url(#accent)" stroke-width="6"/>
  </g>
</svg>
```

（与原文件比较：仅删除第 37、38 两个 `<text>` 节点；标题/描述改为不含 CKQA 的中文。）

- [ ] **Step 2: 确认资源仍能被加载（如本地后端在跑）**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/course-covers/default-course-cover.svg
```

Expected：`200`（如后端未跑则跳过此步）。

- [ ] **Step 3: grep 仓库确认无任何文件再出现裸字符串 `CKQA Course`**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git grep -n "CKQA Course" -- ':!docs' ':!**/CHANGELOG*' ':!**/node_modules/**' || echo "clean"
```

Expected：输出 `clean`（仓库名 `ckqa.code-workspace` 等不算 UI 文案，且筛掉了 docs 历史）。

- [ ] **Step 4: 提交**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git add backend/ckqa-back/src/main/resources/static/assets/course-covers/default-course-cover.svg
git commit -m "fix(course-cover): drop CKQA english brand text from default svg"
```

---

## Task 7: 全套测试 + 收尾

**Files:** N/A

- [ ] **Step 1: 跑前端所有 node 单测**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected：全部 PASS（包含 Task 2 与 Task 4 新增用例）。

- [ ] **Step 2: 跑全部 admin-app e2e**

```bash
cd frontend/apps/admin-app
pnpm exec playwright test --reporter=line
```

Expected：全部 PASS。若 `m8-copy-audit.spec.js` 或 `m8-visual-core.spec.js` 因为 hero 视觉变化失败，更新对应快照后重跑（命令 `pnpm exec playwright test --update-snapshots`，仅在视觉差异已肉眼确认正确时使用，并将更新的快照随本任务一起提交）。

- [ ] **Step 3: 看一眼 git log 与 diff，确认 6 个提交清晰可读**

```bash
cd /home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2
git log --oneline origin/feature/admin-app-redesign-m1-m2..HEAD
```

Expected：6 条新提交，标题如下顺序：
1. `fix(admin/course-list): remove redundant hero eyebrow`
2. `feat(admin/course-cover): add palette + glyph derivation model`
3. `feat(admin/course-cover): add CkCourseCoverArt inline SVG`
4. `feat(admin/resource-card): add titleClamp + statusFloating + metaVariant + cover slot`
5. `feat(admin/course-list): adopt cover-art component + emphasized card variant`
6. `fix(course-cover): drop CKQA english brand text from default svg`

如有视觉快照更新提交，可作为额外一条 `chore(e2e): refresh course list visual snapshots`。

---

## Self-Review

**Spec coverage:**

- 4.1 面包屑/标题去重 → Task 1 ✓
- 4.2 新组件 `CkCourseCoverArt` → Task 2（model）+ Task 3（组件） ✓
- 4.3 `CkResourceCard` 三个 prop + `#cover` 槽 + hover → Task 4 ✓
- 4.4 `CourseListPage` 接入 → Task 5 ✓
- 4.5 后端默认 SVG 清洗 → Task 6 ✓
- 测试计划（model 单测 / e2e 断言）→ Task 2 / Task 4 / Task 5 / Task 7 ✓

**Type consistency:** 

- `pickPalette` / `pickGlyph` / `hashString` / `PALETTES` 在 model 与 SFC 中署名一致。
- `META_VARIANTS` / `normalizeMetaVariant` 与 `CkResourceCard` 模板中 `'ck-resource-card-meta-${resolvedMetaVariant}'` 字符串前缀一致。
- `data-testid="resource-card-status-floating"` 在 Task 4 定义、Task 5 e2e 引用，一致。
- `ck-course-cover-art` 类名在 Task 3 定义、Task 5 e2e 选择器引用，一致。

**Placeholder scan:** 全部步骤含具体代码 / 命令 / 预期输出，无 TODO/TBD。Task 5 Step 5 的本地手测在无 dev 环境时明确允许跳过。
