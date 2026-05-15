# 手动调优提示词向导 · Phase 1e PromptDisplay 与完整保存

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 1 收官：把 03 步抽屉、05 步保存的 prompt 文本展示统一升级为 `<PromptDisplay>` 三视图组件（rich / split / raw 切换），并把 05 步从 1a 简版升级为完整版（左 prompt 预览 + 右草稿名表单 + 保存范围 radio + 历史草稿入库 mock）。同时修复 Phase 1a 列出的 1-4 项已知局限，并为第 5 项解锁规则细化记录 Phase 2 hook。

**Architecture:** `PromptDisplay.vue` 作为通用组件接受 `text` prop，内部维护 `mode = 'rich' | 'split' | 'raw'`。rich 模式用 `prompt-display-parser.js`（已在 spec 范围内，本期实现）按 `-Section Name-` 切章节，再用 `markdown-it` 渲染段落正文（兼容 list / inline code）；raw 模式用手写轻量高亮（section 行染绿、placeholder 染橙、行号），加暗色 IDE 风；split 模式左 rich 右 raw，用 `ref` + 手动 scroll 事件做同步滚动。05 步保存组件重写为左右两栏布局，`saveMode` 选项 + 历史草稿 mock 入库。

**Tech Stack:** Phase 1a 已装的 `markdown-it`；本期首次引用。不引入 `prismjs`（raw 模式用手写轻量高亮足够）。不引入 `@vueuse/core`（同步滚动用原生 scroll 事件 + ref 实现）。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 提示词文本显示组件（M 组合方案） / § 05 预览保存

**前置：** Phase 1a 完成；Phase 1c 完成（03 抽屉简版需要换成 PromptDisplay）。1d 不是硬前置，但建议有（05 步要能从 04 选定候选过来）。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-parser.js` | 新建 | 章节解析 + 段落元信息映射 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js` | 大改 | 扩展 buildSaveDraftPayload 支持完整字段（selectedCandidate / saveMode） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/prompt-texts.js` | 新建 | 4 个候选的 mock prompt 文本 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js` | 修改 | 重导出 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplay.vue` | 新建 | 三视图主壳 + 模式切换 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRich.vue` | 新建 | rich 子模式：章节卡片渲染 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue` | 新建 | raw 子模式：暗色 IDE 风 + 手写轻量高亮 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue` | 大改 | 替换为完整版（左预览右表单） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue` | 修改 | 抽屉简版 pre 替换为 PromptDisplay |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue` | 删除 | 1a 已标记 deprecated，本期删 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue` | 删除 | 同上 |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 修改 | 修复 1a 5 项已知局限 |
| `frontend/apps/admin-app/src/styles/components.scss` | 末尾追加 | PromptDisplay 三视图 + 05 完整版样式（约 480 行） |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prompt-display-parser.test.js` | 新建 | Task 1 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js` | 修改 | Task 6 扩展 |

---

## Task 1：prompt 文本解析器 + 测试

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prompt-display-parser.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-parser.js`

- [ ] **Step 1: 写失败测试**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prompt-display-parser.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  parsePromptSections,
  resolveSectionMeta,
} from '../../views/pages/prompt-builder/prompt-display-parser.js'

describe('parsePromptSections', () => {
  it('splits text by -SectionName- markers', () => {
    const text = `-Goal-
extract entities

-Schema Constraints-
- Course
- Chapter

-Real Data-
text: {input_text}
output:`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 3)
    assert.equal(sections[0].title, 'Goal')
    assert.equal(sections[1].title, 'Schema Constraints')
    assert.equal(sections[2].title, 'Real Data')
    assert.match(sections[0].body, /extract entities/)
    assert.match(sections[1].body, /Course/)
  })

  it('returns single fallback section when no markers found', () => {
    const text = 'just plain text without markers'
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 1)
    assert.equal(sections[0].title, '原文')
    assert.equal(sections[0].body, text)
    assert.equal(sections[0].fallback, true)
  })

  it('returns empty array when input is empty/whitespace', () => {
    assert.deepEqual(parsePromptSections(''), [])
    assert.deepEqual(parsePromptSections('   \n\n   '), [])
  })

  it('preserves leading content before first marker as 前言 section', () => {
    const text = `intro line\n\n-Goal-\nbody`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 2)
    assert.equal(sections[0].title, '前言')
    assert.match(sections[0].body, /intro line/)
    assert.equal(sections[1].title, 'Goal')
  })

  it('handles 中文 section markers', () => {
    const text = `-关系方向卡片-\n规则一\n\n-Real Data-\noutput:`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 2)
    assert.equal(sections[0].title, '关系方向卡片')
    assert.equal(sections[1].title, 'Real Data')
  })
})

describe('resolveSectionMeta', () => {
  it('maps known section names to icon + 中文别名', () => {
    assert.deepEqual(resolveSectionMeta('Goal'),               { icon: '🎯', alias: '任务目标' })
    assert.deepEqual(resolveSectionMeta('Schema Constraints'), { icon: '📐', alias: '实体类型约束' })
    assert.deepEqual(resolveSectionMeta('Real Data'),          { icon: '📊', alias: '输入输出格式' })
    assert.deepEqual(resolveSectionMeta('关系方向卡片'),       { icon: '↔️', alias: '关系方向规则' })
  })

  it('returns generic meta for unknown name', () => {
    assert.deepEqual(resolveSectionMeta('Unknown Section'), { icon: '§', alias: 'Unknown Section' })
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-prompt-display-parser.test.js`

Expected: 7 个 it FAIL（模块不存在）

- [ ] **Step 3: 实现 parser**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-parser.js
//
// Phase 1e 解析器：把 prompt.txt 按 -SectionName- 行切章节。
// 每章返回 { title, body, fallback? }。

const SECTION_META = {
  'Goal':                          { icon: '🎯', alias: '任务目标' },
  'Task Context':                  { icon: '📖', alias: '任务上下文' },
  'Schema Constraints':            { icon: '📐', alias: '实体类型约束' },
  'Quality Constraints':           { icon: '✅', alias: '质量约束' },
  '关系方向卡片':                  { icon: '↔️', alias: '关系方向规则' },
  'Micro-examples':                { icon: '✨', alias: '微样例' },
  'Real Data':                     { icon: '📊', alias: '输入输出格式' },
  'Course Baseline Constraints':   { icon: '🎓', alias: '课程基线约束' },
  'Strict JSON Output Guard':      { icon: '🛡️', alias: '严格 JSON 输出' },
  'Base Prompt Note':              { icon: '📝', alias: '基底说明' },
}

const MARKER_RE = /^-([^-\n][^\n]*?)-\s*$/

export function parsePromptSections(text) {
  if (!text || !text.trim()) return []
  const lines = text.split(/\r?\n/)
  const sections = []
  let current = null
  let leadingBuffer = []

  for (const line of lines) {
    const m = line.match(MARKER_RE)
    if (m) {
      if (current) {
        sections.push(current)
      } else if (leadingBuffer.length && leadingBuffer.some((l) => l.trim())) {
        sections.push({ title: '前言', body: leadingBuffer.join('\n').trim() })
      }
      current = { title: m[1].trim(), body: '' }
      leadingBuffer = []
    } else if (current) {
      current.body += (current.body ? '\n' : '') + line
    } else {
      leadingBuffer.push(line)
    }
  }
  if (current) sections.push(current)

  if (sections.length === 0) {
    return [{ title: '原文', body: text, fallback: true }]
  }
  for (const s of sections) s.body = s.body.trim()
  return sections
}

export function resolveSectionMeta(title) {
  return SECTION_META[title] ?? { icon: '§', alias: title }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 7 个 it PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/prompt-display-parser.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-prompt-display-parser.test.js
git commit -m "feat(prompt-builder): 新增 prompt 文本解析器 (Phase 1e)"
```

---

## Task 2：mock 数据 — prompt-texts

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/prompt-texts.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js`

- [ ] **Step 1: 创建 mocks/prompt-texts.js**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/prompt-texts.js
//
// 4 个候选的 mock extract_graph.txt 文本。
// 注意：内容是真实候选的精简版本，保留 -Section- 标记便于 PromptDisplay 解析。

export const MOCK_PROMPT_TEXTS = {
  default: `-Goal-
从课程相关文本中按课程 schema 抽取稳定的实体与关系。

-Task Context-
当前任务是"课程知识图谱抽取"，目标是从教材、课件、课程大纲、实验指导中抽取稳定、可复用的课程领域实体与关系。

-Course Baseline Constraints-
当前任务聚焦"课程知识图谱抽取"，不是通用信息抽取。
请优先抽取课程结构、概念/术语、方法/算法、实验、作业等稳定对象。

-Real Data-
entity_types: [Course, Chapter, Section, KnowledgePoint, Concept, Term, FormulaOrDefinition, AlgorithmOrMethod, Experiment, Assignment, ToolOrPlatform]
text: {input_text}
output:
`,
  auto_tuned: `-Goal-
Given a course text document and the course schema, extract entities and relationships.

-Task Context-
This task is course knowledge graph extraction targeting textbooks, slides, syllabi, lab guides, notes, assignments and quizzes.

-Schema Constraints-
- Course: 课程顶层对象
- Chapter: 课程结构章节
- Section: 课程结构单元
- KnowledgePoint: 课程知识点

-Real Data-
entity_types: [Course, Chapter, Section, KnowledgePoint, Concept, Term, FormulaOrDefinition, AlgorithmOrMethod, Experiment, Assignment, ToolOrPlatform]
text: {input_text}
output:
`,
  schema_aware_directional_v2: `-Goal-
从课程相关文本中按课程 schema 抽取稳定的实体与关系。

-Schema Constraints-
实体类型必须来自以下课程 Schema：
- Course（课程顶层对象）
- Chapter（课程结构章节）
- Section（课程结构单元）
- KnowledgePoint（课程知识点）
- Concept（课程概念）
- Term（课程术语）
- FormulaOrDefinition（课程中的定义或公式）
- AlgorithmOrMethod（课程中的方法或算法）
- Experiment（课程实验任务）
- Assignment（课程作业或题组）
- ToolOrPlatform（课程使用的工具或平台）

-关系方向卡片-
- applied_in：source 是被应用的知识/方法/公式，target 是知识主题、实验、作业或平台操作场景
- defined_by：source 是被定义对象，target 是定义、公式、判定条件或符号
- evaluated_by：source 是被考核或评估的知识/概念/术语/方法，target 是 Assignment/Experiment 等考核载体
- appears_in：source 是出现的实体，target 是 Course/Chapter/Section/Experiment/Assignment/ToolOrPlatform 上下文
- related_to：保底关系；不能承接 missing 端点

-Quality Constraints-
- 实体类型必须来自课程 Schema，不要自造类型
- 关系说明必须以 [type=<relation_type>] 开头
- 优先保留课程结构、概念/术语、方法/算法、实验、作业相关对象

-Real Data-
entity_types: [Course, Chapter, Section, KnowledgePoint, Concept, Term, FormulaOrDefinition, AlgorithmOrMethod, Experiment, Assignment, ToolOrPlatform]
text: {input_text}
output:
`,
  schema_fewshot_distilled_v2_strict_tuple: `-Goal-
从课程相关文本中按课程 schema 抽取稳定的实体与关系。

-Schema Constraints-
实体类型必须来自以下课程 Schema：
- Course、Chapter、Section、KnowledgePoint、Concept、Term
- FormulaOrDefinition、AlgorithmOrMethod、Experiment、Assignment、ToolOrPlatform

-关系方向卡片-
- applied_in：source 是被应用的知识/方法/公式，target 是知识主题、实验、作业或平台操作场景
- defined_by：source 是被定义对象，target 是定义/公式/判定条件
- appears_in：source 是出现的实体，target 是上下文容器

-Micro-examples-
只蒸馏端点和类型，不嵌完整 audit text。
- audit-0001: 进程 -> 进程定义: [type=defined_by]
- audit-0007: 时间片轮转算法 -> 实验一: [type=applied_in]
- audit-0012: 第二章 -> 进程: [type=contains]

-Strict JSON Output Guard-
- 最终只返回一个 JSON 对象，根对象只包含 entities 与 relationships 两个数组
- 不输出额外说明、指标、指令、Markdown code fence 或前后缀文本
- 每条 relationship 的 source 和 target 必须逐字匹配 entities[].title

-Real Data-
entity_types: [Course, Chapter, Section, KnowledgePoint, Concept, Term, FormulaOrDefinition, AlgorithmOrMethod, Experiment, Assignment, ToolOrPlatform]
text: {input_text}
output:
`,
}

export function resolveCandidatePromptText(candidateId) {
  return MOCK_PROMPT_TEXTS[candidateId] ?? ''
}
```

- [ ] **Step 2: 修改 mocks/index.js 末尾追加重导出**

⚠️ 这里是 **追加** 而非替换。Phase 1a-1d 已有内容必须保留。

操作步骤：

1. 先读取文件确认尾部内容：

```bash
cat frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
cat -A frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js | tail -3
```

预期：文件以 Phase 1d 追加的 `export * from './scoring-report.js'` 这一行结束。

2. 用 str_replace 在 Phase 1d 追加的那一行后再追加新的重导出：

- oldStr：
  ```javascript
  // Phase 1d：评分报告 mock
  export * from './scoring-report.js'
  ```
- newStr：
  ```javascript
  // Phase 1d：评分报告 mock
  export * from './scoring-report.js'

  // Phase 1e：候选 prompt 文本 mock
  export * from './prompt-texts.js'
  ```

3. 再次 `cat` 确认所有已有导出仍在，且新增了 `export * from './prompt-texts.js'`。

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/prompt-texts.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
git commit -m "feat(prompt-builder): mock 4 个候选 prompt 文本 (Phase 1e)"
```

---

## Task 3：PromptDisplayRich 子组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRich.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRich.vue -->
<script setup>
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  parsePromptSections,
  resolveSectionMeta,
} from './prompt-display-parser.js'

const props = defineProps({
  text: { type: String, required: true },
  /** 折叠状态控制：默认全部展开 */
  collapsedSections: { type: Object, default: () => ({}) },
})

const sections = computed(() => parsePromptSections(props.text))

const md = new MarkdownIt({ html: false, breaks: false, linkify: false })

function renderBody(body) {
  // 把 {input_text} 这种占位符高亮，再交给 markdown-it 渲染。
  // 渲染前先把占位符替换为 Unicode 私有区 token（不会触发 Markdown 语法），
  // 渲染后再替换回 HTML span。
  // 注意：不能用 __xxx__ 作为 token，markdown-it 会把双下划线解析为 <strong>。
  const tokenMap = new Map()
  let counter = 0
  const placeholderPattern = /\{[a-zA-Z_][a-zA-Z0-9_]*\}/g
  const escaped = body.replace(placeholderPattern, (match) => {
    const token = `\uE000PH${counter++}\uE001`
    tokenMap.set(token, match)
    return token
  })

  let html = md.render(escaped)
  for (const [token, original] of tokenMap) {
    html = html.replaceAll(token, `<mark class="prompt-display-placeholder">${original}</mark>`)
  }
  return html
}

const localCollapsed = ref({ ...props.collapsedSections })

function toggleSection(idx) {
  localCollapsed.value = {
    ...localCollapsed.value,
    [idx]: !localCollapsed.value[idx],
  }
}
</script>

<template>
  <article class="prompt-display-rich">
    <section
      v-for="(section, idx) in sections"
      :key="idx"
      class="prompt-display-rich__section"
      :class="{ 'is-fallback': section.fallback }"
    >
      <header
        class="prompt-display-rich__head"
        @click="toggleSection(idx)"
      >
        <div class="prompt-display-rich__title">
          <span class="prompt-display-rich__icon">{{ resolveSectionMeta(section.title).icon }}</span>
          <div>
            <strong>{{ resolveSectionMeta(section.title).alias }}</strong>
            <small v-if="!section.fallback">原文标题 {{ section.title }}</small>
          </div>
        </div>
        <span class="prompt-display-rich__toggle">{{ localCollapsed[idx] ? '展开 ▾' : '收起 ▴' }}</span>
      </header>
      <div
        v-if="!localCollapsed[idx]"
        class="prompt-display-rich__body"
        v-html="renderBody(section.body)"
      />
    </section>
  </article>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRich.vue
git commit -m "feat(prompt-builder): 新增 PromptDisplayRich 章节卡片视图 (Phase 1e)"
```

---

## Task 4：PromptDisplayRaw 子组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue -->
<script setup>
import { computed } from 'vue'
// 不使用 Prism：prompt 文本是自定义 -Section- 格式，不是真正的 Markdown，
// Prism markdown 语法高亮反而会误染。手写轻量高亮（section 行 + placeholder）足够。

const props = defineProps({
  text: { type: String, required: true },
})

// 行内高亮：把 -SectionName- 染绿、{placeholder} 染橙、其余按 markdown 高亮
function highlightLine(line) {
  if (/^-([^-\n][^\n]*?)-\s*$/.test(line)) {
    return `<span class="raw-section">${escapeHtml(line)}</span>`
  }
  // 占位符：{xxx}
  let escaped = escapeHtml(line)
  escaped = escaped.replace(
    /\{[a-zA-Z_][a-zA-Z0-9_]*\}/g,
    (m) => `<span class="raw-placeholder">${m}</span>`
  )
  return escaped
}

function escapeHtml(s) {
  return s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

const lines = computed(() =>
  props.text.split(/\r?\n/).map((line, i) => ({
    no: i + 1,
    html: highlightLine(line) || '&nbsp;',
  }))
)
</script>

<template>
  <pre class="prompt-display-raw">
<span
  v-for="line in lines"
  :key="line.no"
  class="prompt-display-raw__line"
><span class="prompt-display-raw__lineno">{{ line.no }}</span><span class="prompt-display-raw__text" v-html="line.html"></span></span>
  </pre>
</template>
```

注意：上述模板里 `<pre>` 内部的换行处理依赖 Vue 编译保留空白。如果首行/末行多了空行，编辑时手动调整。

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplayRaw.vue
git commit -m "feat(prompt-builder): 新增 PromptDisplayRaw 暗色 IDE 视图 (Phase 1e)"
```

---

## Task 5：PromptDisplay 主壳（三视图切换）

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplay.vue`

- [ ] **Step 1: 创建主壳**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplay.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PromptDisplayRich from './PromptDisplayRich.vue'
import PromptDisplayRaw from './PromptDisplayRaw.vue'
import { parsePromptSections } from './prompt-display-parser.js'

const props = defineProps({
  text: { type: String, required: true },
  defaultMode: { type: String, default: 'rich' }, // 'rich' | 'split' | 'raw'
})

const mode = ref(props.defaultMode)

// 容错：如果 parser 解析后只有 1 个 section 且是 fallback，自动切到 raw
const fallbackToRaw = computed(() => {
  const sections = parsePromptSections(props.text)
  return sections.length === 1 && sections[0].fallback
})

watch(fallbackToRaw, (val) => {
  if (val && mode.value !== 'raw') {
    mode.value = 'raw'
    ElMessage.warning('该提示词无法解析为文档视图，已切换到原文视图')
  }
}, { immediate: true })

// split 模式同步滚动（原生 scroll 事件 + ref，不依赖 @vueuse/core）
const leftPaneRef = ref(null)
const rightPaneRef = ref(null)
let syncing = false

function onLeftScroll() {
  if (mode.value !== 'split' || syncing) return
  const left = leftPaneRef.value
  const right = rightPaneRef.value
  if (!left || !right) return
  const leftMax = left.scrollHeight - left.clientHeight
  const rightMax = right.scrollHeight - right.clientHeight
  if (leftMax <= 0 || rightMax <= 0) return
  syncing = true
  right.scrollTop = (left.scrollTop / leftMax) * rightMax
  requestAnimationFrame(() => { syncing = false })
}

function onRightScroll() {
  if (mode.value !== 'split' || syncing) return
  const left = leftPaneRef.value
  const right = rightPaneRef.value
  if (!left || !right) return
  const leftMax = left.scrollHeight - left.clientHeight
  const rightMax = right.scrollHeight - right.clientHeight
  if (leftMax <= 0 || rightMax <= 0) return
  syncing = true
  left.scrollTop = (right.scrollTop / rightMax) * leftMax
  requestAnimationFrame(() => { syncing = false })
}

async function copyText() {
  try {
    await navigator.clipboard.writeText(props.text)
    ElMessage.success('已复制完整提示词')
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
</script>

<template>
  <article class="prompt-display">
    <header class="prompt-display__head">
      <div class="prompt-display__view-switch">
        <button :class="{ active: mode === 'rich' }"  :disabled="fallbackToRaw" @click="mode = 'rich'">仅文档</button>
        <button :class="{ active: mode === 'split' }" :disabled="fallbackToRaw" @click="mode = 'split'">分屏</button>
        <button :class="{ active: mode === 'raw' }"   @click="mode = 'raw'">仅原文</button>
      </div>
      <button class="prompt-display__copy" @click="copyText">📋 复制</button>
    </header>

    <div v-if="mode === 'rich'" class="prompt-display__body">
      <PromptDisplayRich :text="text" />
    </div>

    <div v-else-if="mode === 'split'" class="prompt-display__split">
      <div ref="leftPaneRef" class="prompt-display__pane prompt-display__pane--left" @scroll="onLeftScroll">
        <PromptDisplayRich :text="text" />
      </div>
      <div ref="rightPaneRef" class="prompt-display__pane prompt-display__pane--right" @scroll="onRightScroll">
        <PromptDisplayRaw :text="text" />
      </div>
    </div>

    <div v-else-if="mode === 'raw'" class="prompt-display__body">
      <PromptDisplayRaw :text="text" />
    </div>
  </article>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptDisplay.vue
git commit -m "feat(prompt-builder): 新增 PromptDisplay 三视图主壳 (Phase 1e)"
```

---

## Task 6：扩展 save-step-model（Phase 1e 完整字段）

更新 `buildSaveDraftPayload` 接受候选 + 综合分 + saveMode；测试同步扩展。

**Files:**
- Modify: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js`

- [ ] **Step 1: 在测试文件末尾追加完整版测试**

```javascript
describe('buildSaveDraftPayload (Phase 1e 扩展)', () => {
  it('builds payload with selectedCandidate and saveMode', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '操作系统 · 图谱感知 + 蒸馏样例 · 2026-05-14',
      description: '初版草稿',
      selectedCandidate: 'schema_fewshot_distilled_v2_strict_tuple',
      candidateDisplayName: '图谱感知 + 蒸馏样例',
      compositeScore: 0.71,
      saveMode: 'build_run_with_history',
    })
    assert.equal(payload.seed, 'system_default')
    assert.equal(payload.selectedCandidate, 'schema_fewshot_distilled_v2_strict_tuple')
    assert.equal(payload.saveMode, 'build_run_with_history')
    assert.equal(payload.metadata.candidateDisplayName, '图谱感知 + 蒸馏样例')
    assert.equal(payload.metadata.compositeScore, 0.71)
  })

  it('omits candidate fields when selectedCandidate is empty (向后兼容 1a 简版)', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '草稿',
      description: '',
    })
    assert.equal(payload.seed, 'system_default')
    assert.equal(payload.selectedCandidate, undefined)
    assert.equal(payload.metadata.candidateDisplayName, undefined)
    assert.equal(payload.metadata.compositeScore, undefined)
    // saveMode 默认 build_run_only
    assert.equal(payload.saveMode, 'build_run_only')
  })

  it('saveMode "build_run_with_history" requires selectedCandidate', () => {
    assert.throws(
      () => buildSaveDraftPayload({
        seed: 'system_default',
        name: '草稿',
        saveMode: 'build_run_with_history',
        // 缺 selectedCandidate
      }),
      /selectedCandidate/
    )
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-save-step.test.js`

Expected: 3 个新 it FAIL（其余仍 PASS）

- [ ] **Step 3: 改写 buildSaveDraftPayload 支持完整字段**

替换 `save-step-model.js` 中 `buildSaveDraftPayload`：

```javascript
/**
 * 构造保存草稿的 payload。
 *
 * Phase 1a：支持简版字段（seed / name / description）；saveMode 缺省为 build_run_only。
 * Phase 1e：新增 selectedCandidate / candidateDisplayName / compositeScore / saveMode。
 *
 * 当 saveMode === 'build_run_with_history' 时必须传 selectedCandidate（入库需要候选标识）。
 */
export function buildSaveDraftPayload({
  seed,
  name,
  description,
  selectedCandidate,
  candidateDisplayName,
  compositeScore,
  saveMode = 'build_run_only',
}) {
  if (!seed) throw new Error('seed is required')
  const trimmedName = String(name ?? '').trim()
  if (!trimmedName) throw new Error('name is required')
  if (saveMode === 'build_run_with_history' && !selectedCandidate) {
    throw new Error('selectedCandidate is required when saveMode is build_run_with_history')
  }

  const metadata = { draftName: trimmedName }
  const trimmedDesc = String(description ?? '').trim()
  if (trimmedDesc) metadata.draftDescription = trimmedDesc
  if (candidateDisplayName) {
    metadata.candidateDisplayName = String(candidateDisplayName).trim()
  }
  if (typeof compositeScore === 'number') {
    metadata.compositeScore = compositeScore
  }

  const payload = { seed, saveMode, metadata }
  if (selectedCandidate) payload.selectedCandidate = selectedCandidate
  return payload
}
```

- [ ] **Step 4: 跑测试，确认全部通过（含 1a 已有测试）**

Expected: 11 + 3 = 14 个 it 全 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js
git commit -m "feat(prompt-builder): 扩展保存模型支持候选+saveMode (Phase 1e)"
```

---

## Task 7：重写 PromptBuilderSaveStep 为完整版

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`

- [ ] **Step 1: 完整替换组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue -->
<script setup>
import { computed, watch } from 'vue'
import PromptDisplay from './PromptDisplay.vue'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from './save-step-model.js'
import {
  resolveCandidatePromptText,
  MOCK_CANDIDATES,
  MOCK_SCORING_REPORT,
} from './mocks/index.js'

const props = defineProps({
  buildRunId: { type: [String, Number], default: '' },
  courseName: { type: String, default: '' },
  seed: { type: String, default: null },
  /** 04 步选定的候选 ID */
  selectedCandidateId: { type: String, default: '' },
  saving: { type: Boolean, default: false },
  saveError: { type: String, default: '' },
  /** Page 层持有的草稿名（v-if 卸载后不丢失） */
  draftName: { type: String, default: '' },
  /** Page 层持有的草稿说明 */
  draftDescription: { type: String, default: '' },
  /** 用户是否手动改过草稿名 */
  draftNameTouched: { type: Boolean, default: false },
  /** 用户是否手动改过说明 */
  draftDescriptionTouched: { type: Boolean, default: false },
  /** Page 层持有的保存范围（v-if 卸载后不丢失） */
  saveMode: { type: String, default: 'build_run_with_history' },
})

const emit = defineEmits(['save', 'back', 'mark-dirty', 'update:draftName', 'update:draftDescription', 'update:draftNameTouched', 'update:draftDescriptionTouched', 'update:saveMode'])

// 候选元信息
const candidateInfo = computed(() =>
  MOCK_CANDIDATES.find((c) => c.candidateId === props.selectedCandidateId) ?? null
)

const candidateScore = computed(() =>
  MOCK_SCORING_REPORT.candidates.find((c) => c.candidateId === props.selectedCandidateId) ?? null
)

const promptText = computed(() => resolveCandidatePromptText(props.selectedCandidateId))

// 默认草稿名以课程名 + 种子为准；候选信息进入来源记录和 metadata
const defaultName = computed(() =>
  buildDefaultDraftName({
    courseName: props.courseName,
    seed: props.seed,
  })
)

// 草稿名状态由 Page 层持有（v-if 卸载后不丢失）。
// 如果用户没手动改过，跟随 defaultName 自动更新。
watch(defaultName, (val) => {
  if (!props.draftNameTouched) emit('update:draftName', val)
}, { immediate: true })

function onNameInput(val) {
  emit('update:draftName', val)
  emit('update:draftNameTouched', true)
  emit('mark-dirty')
}

// 默认说明跟随候选变化（用户没手动改过时）
const defaultDescription = computed(() =>
  candidateScore.value
    ? `经过 ${MOCK_SCORING_REPORT.totalSamples} 条校准集评估，召回率 ${Math.round(candidateScore.value.recall * 100)}%、准确率 ${Math.round(candidateScore.value.precision * 100)}%，综合分 ${candidateScore.value.compositeScore.toFixed(2)}。`
    : ''
)

watch(defaultDescription, (val) => {
  if (!props.draftDescriptionTouched) emit('update:draftDescription', val)
}, { immediate: true })

function onDescriptionInput(val) {
  emit('update:draftDescription', val)
  emit('update:draftDescriptionTouched', true)
  emit('mark-dirty')
}

function onSaveModeChange(val) {
  emit('update:saveMode', val)
  emit('mark-dirty')
}

const validation = computed(() =>
  validateSaveForm({ name: props.draftName, seed: props.seed })
)

// 保存按钮 disabled 条件：表单校验不过 / 正在保存 / 选了"入库历史草稿"但没有候选
const hasCandidate = computed(() => !!props.selectedCandidateId && !!promptText.value)
const canSave = computed(() =>
  validation.value.valid &&
  !props.saving &&
  (props.saveMode !== 'build_run_with_history' || hasCandidate.value)
)

const seedLabel = computed(() => {
  if (props.seed === 'system_default') return '系统默认'
  if (props.seed === 'graphrag_tuned') return 'GraphRAG 自动调优'
  return '—'
})

function handleSubmit() {
  if (!canSave.value) return
  const payload = buildSaveDraftPayload({
    seed: props.seed,
    name: props.draftName,
    description: props.draftDescription,
    selectedCandidate: props.selectedCandidateId || undefined,
    candidateDisplayName: candidateInfo.value?.displayNameZh,
    compositeScore: candidateScore.value?.compositeScore,
    saveMode: props.saveMode,
  })
  emit('save', payload)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-save-step--full">
    <header class="prompt-builder-step__header">
      <h3>预览保存</h3>
      <p>复核选定候选的提示词内容，命名后保存到本次构建。可选同步入库为历史草稿。</p>
    </header>

    <div class="save-step-layout">

      <!-- 左：prompt 预览 -->
      <article class="save-step-preview">
        <header class="save-step-preview__head">
          <div>
            <div class="save-step-preview__pills">
              <span v-if="candidateScore?.rank === 1" class="ann-pill ann-pill--gold">★ 第 1 名</span>
              <span v-if="candidateScore" class="ann-pill ann-pill--success">综合分 {{ candidateScore.compositeScore.toFixed(2) }}</span>
            </div>
            <h2 class="save-step-preview__title">
              {{ candidateInfo?.displayNameZh ?? '（未选定候选）' }}
            </h2>
            <div class="save-step-preview__sub">
              <span>来源候选</span>
              <code>{{ candidateInfo?.candidateId ?? '—' }}</code>
              <template v-if="candidateInfo">
                <span>·</span>
                <span>{{ (candidateInfo.promptSizeBytes / 1024).toFixed(1) }} KB</span>
                <span>·</span>
                <span>~{{ (candidateInfo.estimatedTokenPerCall / 1000).toFixed(1) }}k token/调用</span>
              </template>
            </div>
          </div>
        </header>

        <div v-if="!promptText" class="save-step-preview__empty">
          请先在 04 步选定一个候选
        </div>
        <PromptDisplay v-else :text="promptText" />
      </article>

      <!-- 右：表单 -->
      <aside class="save-step-form">
        <header class="save-step-form__head">
          <div class="save-step-form__icon">📦</div>
          <div>
            <h2>保存草稿</h2>
            <small>为本次构建确认提示词</small>
          </div>
        </header>

        <div class="form-row">
          <label class="form-row__label">
            草稿名
            <span class="form-row__optional">将出现在历史草稿列表</span>
          </label>
          <el-input :model-value="draftName" placeholder="如：操作系统 · 图谱感知 · 2026-05-14" @input="onNameInput" />
          <p v-if="validation.errors.name" class="form-row__error">{{ validation.errors.name }}</p>
        </div>

        <div class="form-row">
          <label class="form-row__label">
            说明 <span class="form-row__optional">（选填）</span>
          </label>
          <el-input
            :model-value="draftDescription"
            type="textarea"
            :rows="3"
            placeholder="例如：经过 20 条校准集评估，准确率明显高于基线..."
            @input="onDescriptionInput"
          />
        </div>

        <div class="form-row">
          <label class="form-row__label">
            来源记录 <span class="form-row__optional">自动生成</span>
          </label>
          <div class="save-step-form__source">
            <div><span>课程</span><strong>{{ courseName || '—' }}</strong></div>
            <div><span>构建运行</span><strong>{{ buildRunId || '—' }}</strong></div>
            <div><span>选定种子</span><strong>{{ seedLabel }}</strong></div>
            <div><span>选定候选</span><strong>{{ candidateInfo?.candidateId ?? '—' }}</strong></div>
            <div v-if="candidateScore"><span>评分 run</span><strong>{{ MOCK_SCORING_REPORT.evalRunId }}</strong></div>
          </div>
        </div>

        <div class="form-row">
          <label class="form-row__label">保存范围</label>
          <el-radio-group :model-value="saveMode" @change="onSaveModeChange">
            <el-radio value="build_run_with_history" border>
              <strong>仅本次构建 + 入库历史草稿</strong>
              <small>下次类似课程可在 01 步复用</small>
            </el-radio>
            <el-radio value="build_run_only" border>
              <strong>仅本次构建</strong>
              <small>调试性试验，不入库历史草稿</small>
            </el-radio>
          </el-radio-group>
        </div>

        <p v-if="saveError" class="form-row__error">{{ saveError }}</p>

        <footer class="save-step-form__actions">
          <el-button @click="$emit('back')" :disabled="saving">← 返回 04</el-button>
          <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSubmit">
            ✓ 保存并返回构建向导
          </el-button>
        </footer>
      </aside>

    </div>
  </section>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue
git commit -m "feat(prompt-builder): 重写 05 步为完整版（左预览+右表单+saveMode）(Phase 1e)"
```

---

## Task 8：03 抽屉换为 PromptDisplay

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`

- [ ] **Step 1: 加 import**

```javascript
import PromptDisplay from './PromptDisplay.vue'
import { resolveCandidatePromptText } from './mocks/index.js'
```

- [ ] **Step 2: 加 computed**

```javascript
const drawerPromptText = computed(() =>
  drawerCandidate.value ? resolveCandidatePromptText(drawerCandidate.value.candidateId) : ''
)
```

- [ ] **Step 3: 替换抽屉内容（drawerOpen 抽屉的 inner）**

把原来的：

```vue
<div class="candidate-prompt-drawer">
  <div class="ann-text-muted" style="margin-bottom:8px">
    Phase 1e 会换成富文本三视图。当前展示为暗色 IDE 简版。
  </div>
  <pre class="candidate-prompt-drawer__pre">...</pre>
</div>
```

替换为：

```vue
<div class="candidate-prompt-drawer">
  <PromptDisplay v-if="drawerPromptText" :text="drawerPromptText" default-mode="rich" />
  <div v-else class="ann-text-muted">未找到该候选的提示词文本</div>
</div>
```

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue
git commit -m "feat(prompt-builder): 03 抽屉换为 PromptDisplay (Phase 1e)"
```

---

## Task 9：修复 Phase 1a 已知局限 1-4，并记录局限 5 的 Phase 2 hook

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1: 在 PromptBuilderPage 声明草稿名/说明状态（解决 v-if 卸载丢失问题）**

```javascript
// 草稿名/说明状态上提到 Page 层，避免 v-if 切换步骤时 SaveStep 卸载导致用户输入丢失
const saveDraftName = ref('')
const saveDraftNameTouched = ref(false)
const saveDraftDescription = ref('')
const saveDraftDescriptionTouched = ref(false)
const saveMode = ref('build_run_with_history')
```

模板里 `<PromptBuilderSaveStep>` 的 props 加：

```vue
<PromptBuilderSaveStep
  v-else-if="activeStepKey === 'save'"
  :build-run-id="buildRunId"
  :course-name="MOCK_COURSE_NAME"
  :seed="seed"
  :selected-candidate-id="selectedCandidateId"
  :saving="saving"
  :save-error="saveError"
  :draft-name="saveDraftName"
  :draft-description="saveDraftDescription"
  :draft-name-touched="saveDraftNameTouched"
  :draft-description-touched="saveDraftDescriptionTouched"
  :save-mode="saveMode"
  @update:draft-name="saveDraftName = $event"
  @update:draft-description="saveDraftDescription = $event"
  @update:draft-name-touched="saveDraftNameTouched = $event"
  @update:draft-description-touched="saveDraftDescriptionTouched = $event"
  @update:save-mode="saveMode = $event"
  @mark-dirty="markDirty"
  @save="handleSave"
  @back="gotoPrev"
/>
```

- [ ] **Step 2: 修复局限 2 — dirty 标志覆盖**

`PromptBuilderSaveStep` 现已 emit `mark-dirty` 事件，主壳要监听并设 `dirty=true`。

在 PromptBuilderPage `<script setup>` 中：

```javascript
function markDirty() { dirty.value = true }
```

模板里 `<PromptBuilderSaveStep>` 的 `@mark-dirty="markDirty"` 已在 Step 1 加好。

- [ ] **Step 3: 修复局限 3 — history_draft 接入 mock**

把 1a 的 `if (seedKey === 'history_draft')` 分支改为支持 mock：

```javascript
import { MOCK_HISTORY_DRAFTS } from './prompt-builder/mocks/index.js'

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    if (MOCK_HISTORY_DRAFTS.length === 0) {
      ElMessage.info('暂无历史草稿可复用')
      return
    }
    // Phase 1e mock：随便选第一条草稿，把它的种子标识带回来
    const draft = MOCK_HISTORY_DRAFTS[0]
    seed.value = draft.sourceCandidateId === 'default' ? 'system_default' : 'graphrag_tuned'
    dirty.value = true
    ElMessage.success(`已使用历史草稿 "${draft.name}"`)
    return
  }
  if (seed.value && seed.value !== seedKey) {
    ElMessageBox.confirm('切换种子会重置后续步骤已有的进度，确定吗？', '切换种子', { type: 'warning' })
      .then(() => { seed.value = seedKey; dirty.value = true })
      .catch(() => {})
    return
  }
  seed.value = seedKey
  dirty.value = true
}
```

- [ ] **Step 4: 修复局限 4 — handleSave mock 加入 saveMode 分支提示**

```javascript
async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    await new Promise((resolve) => setTimeout(resolve, 500))
    // eslint-disable-next-line no-console
    console.log('[Phase 1e mock] save payload', payload)
    dirty.value = false
    if (payload.saveMode === 'build_run_with_history') {
      ElMessage.success('已保存到本次构建并入库为历史草稿（mock）')
    } else {
      ElMessage.success('已保存到本次构建（mock）')
    }
    await router.push({
      name: 'knowledge-base-build',
      params: { kbId: kbId.value },
      query: {
        buildRunId: buildRunId.value,
        step: 'prompt',
        promptStrategy: 'custom_pipeline',
      },
    })
  } catch (e) {
    saveError.value = e?.message ?? '保存失败，请重试'
    ElMessage.error(saveError.value)
  } finally {
    saving.value = false
  }
}
```

- [ ] **Step 5: 修复局限 1（draftName 重建）— 已在 Step 1 由 Page 层状态上提解决**

草稿名/说明状态在 Page 层持有（`saveDraftName` / `saveDraftDescription`），v-if 切换步骤时不丢失。SaveStep 通过 props 接收、emit 更新。无需额外改动。

- [ ] **Step 6: 局限 5（解锁规则只看 seed）— 无需 Phase 1e 改，记录 Phase 2 hook**

`isStepUnlocked` 在 1a 写得很简单。本期 mock 状态下，扩展为：candidates / scoring / save 仍然按 seed 解锁；prepare 也按 seed 解锁。**实际上 1a 已经是这样**。本局限主要是给 Phase 2-7 留 hook，无需 Phase 1e 改。文档里注明即可。

- [ ] **Step 7: 跑 build + test**

Run: `cd frontend/apps/admin-app && pnpm build && pnpm test`

Expected: 全部 PASS

- [ ] **Step 8: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "fix(prompt-builder): 修复 Phase 1a 已知局限 (Phase 1e)"
```

---

## Task 10：删除 deprecated 旧组件

**Files:**
- Delete: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue`
- Delete: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue`

- [ ] **Step 1: 验证已无引用**

```bash
grep -rn "PromptBuilderEditStep\|PromptBuilderPreviewStep" frontend/apps/admin-app/src
```

Expected: 无任何引用（除组件文件自身）。如果有引用，先在引用处替换为新组件再做下一步。

- [ ] **Step 2: 删除文件**

```bash
rm frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue
rm frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue
```

- [ ] **Step 3: 跑 build 确认无引用残留**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功

- [ ] **Step 4: 提交**

```bash
git add -A frontend/apps/admin-app/src/views/pages/prompt-builder/
git commit -m "chore(prompt-builder): 删除 deprecated 的旧 EditStep/PreviewStep (Phase 1e)"
```

---

## Task 11：PromptDisplay + 05 完整版样式

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（末尾追加，约 480 行）

- [ ] **Step 1: 在 components.scss 末尾追加 PromptDisplay 样式**

```scss
// 手动调优向导 · PromptDisplay 三视图 (Phase 1e)
.prompt-display {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  overflow: hidden;
  display: flex; flex-direction: column;
}
.prompt-display__head {
  padding: 12px 18px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--ckqa-bg) 70%, var(--ckqa-surface)), var(--ckqa-bg));
  border-bottom: 1px solid var(--ckqa-border);
  display: flex; justify-content: space-between; align-items: center;
}
.prompt-display__view-switch {
  display: inline-flex;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 8px;
  padding: 2px;
}
.prompt-display__view-switch button {
  border: 0; background: transparent; cursor: pointer;
  padding: 6px 12px; font-size: 12px;
  color: var(--ckqa-text-muted);
  border-radius: 6px;
}
.prompt-display__view-switch button.active {
  background: var(--ckqa-accent);
  color: white;
}
.prompt-display__view-switch button:disabled {
  opacity: 0.5; cursor: not-allowed;
}
.prompt-display__copy {
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  padding: 6px 12px; border-radius: 6px;
  font-size: 12px; cursor: pointer;
  color: var(--ckqa-text-muted);
}
.prompt-display__copy:hover {
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
}
.prompt-display__body {
  max-height: 580px; overflow-y: auto;
}
.prompt-display__split {
  display: grid; grid-template-columns: 1fr 1fr;
  height: 580px;
}
.prompt-display__pane {
  overflow: auto;
  height: 100%;
}
.prompt-display__pane--left { border-right: 1px solid var(--ckqa-border); background: var(--ckqa-surface); }
.prompt-display__pane--right { background: #1e1e1e; }

// rich 子模式
.prompt-display-rich {
  padding: 24px 28px;
  display: flex; flex-direction: column;
}
.prompt-display-rich__section {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--ckqa-border);
}
.prompt-display-rich__section:last-child {
  border-bottom: 0; margin-bottom: 0;
}
.prompt-display-rich__head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; cursor: pointer;
}
.prompt-display-rich__title {
  display: flex; align-items: center; gap: 10px;
}
.prompt-display-rich__icon {
  width: 28px; height: 28px;
  border-radius: 8px;
  display: grid; place-items: center;
  background: color-mix(in srgb, var(--ckqa-accent) 12%, transparent);
  color: var(--ckqa-accent);
  font-size: 14px;
  flex-shrink: 0;
}
.prompt-display-rich__title strong {
  display: block; font-size: 14px; font-weight: 600;
}
.prompt-display-rich__title small {
  display: block; color: var(--ckqa-text-muted); font-size: 11px; margin-top: 2px;
}
.prompt-display-rich__toggle {
  color: var(--ckqa-text-soft, #a8a29e);
  font-size: 12px;
}
.prompt-display-rich__body {
  font-size: 13.5px; line-height: 1.85;
  color: var(--ckqa-text);
}
.prompt-display-rich__body p { margin: 0 0 10px; }
.prompt-display-rich__body ul {
  display: flex; flex-direction: column; gap: 6px;
  margin: 8px 0; padding: 0; list-style: none;
}
.prompt-display-rich__body li {
  padding: 6px 12px;
  background: var(--ckqa-bg);
  border-radius: 6px;
  font-size: 13px;
}
.prompt-display-rich__body code {
  background: var(--ckqa-border);
  padding: 1px 6px; border-radius: 4px;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12px;
  color: var(--ckqa-text);
}
.prompt-display-placeholder {
  background: #fef3c7;
  color: #b45309;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12px;
}

// raw 子模式
.prompt-display-raw {
  background: #1e1e1e; color: #d4d4d4;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12.5px; line-height: 1.7;
  padding: 18px 0;
  margin: 0;
  white-space: pre;
  overflow: auto;
}
.prompt-display-raw__line {
  display: flex; padding: 0 16px;
}
.prompt-display-raw__line:hover {
  background: rgba(255, 255, 255, 0.03);
}
.prompt-display-raw__lineno {
  color: #858585; min-width: 32px; text-align: right;
  margin-right: 16px; user-select: none;
  flex-shrink: 0;
}
.prompt-display-raw__text {
  white-space: pre; flex: 1;
}
.raw-section { color: #4ec9b0; font-weight: 600; }
.raw-placeholder { color: #ce9178; }

// —— 05 完整版（左预览 + 右表单） ——
.prompt-builder-save-step--full {
  display: flex; flex-direction: column;
  gap: var(--ckqa-space-3);
}
.save-step-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 16px;
  align-items: start;
}

.save-step-preview {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  overflow: hidden;
  display: flex; flex-direction: column;
}
.save-step-preview__head {
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--ckqa-border);
}
.save-step-preview__pills {
  display: flex; gap: 6px; align-items: center; margin-bottom: 8px;
}
.ann-pill--gold {
  background: color-mix(in srgb, #f59e0b 18%, transparent);
  color: #b45309;
}
.save-step-preview__title {
  margin: 4px 0; font-size: 18px; font-weight: 600;
}
.save-step-preview__sub {
  display: flex; gap: 6px; align-items: center; flex-wrap: wrap;
  font-size: 12px; color: var(--ckqa-text-muted);
}
.save-step-preview__sub code {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11px;
  background: var(--ckqa-border);
  padding: 1px 6px; border-radius: 4px;
}
.save-step-preview__empty {
  padding: 60px 24px; text-align: center;
  color: var(--ckqa-text-muted);
}

.save-step-form {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  padding: 24px;
  display: flex; flex-direction: column;
  gap: 18px;
  position: sticky; top: 20px;
}
.save-step-form__head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ckqa-border);
  display: flex; align-items: center; gap: 10px;
}
.save-step-form__icon {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--ckqa-accent), #a855f7);
  color: white;
  display: grid; place-items: center; font-size: 16px;
  flex-shrink: 0;
}
.save-step-form__head h2 { margin: 0; font-size: 14px; font-weight: 600; }
.save-step-form__head small { color: var(--ckqa-text-muted); font-size: 12px; }

.save-step-form__source {
  display: flex; flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--ckqa-bg);
  border-radius: 8px;
  font-size: 12px;
}
.save-step-form__source > div { display: flex; justify-content: space-between; }
.save-step-form__source span { color: var(--ckqa-text-muted); }
.save-step-form__source strong {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  color: var(--ckqa-text);
  font-weight: 500;
}

.save-step-form__actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--ckqa-border);
}

// el-radio-group 自定义：支持 strong + small 同时显示
// 注意：这是全局 SCSS，不能用 :deep()（那是 Vue SFC scoped 样式的编译时指令）。
.save-step-form .el-radio.is-bordered {
  width: 100%;
  margin: 0 0 8px;
  padding: 12px 16px;
  height: auto;
  align-items: flex-start;
  white-space: normal;
}
.save-step-form .el-radio.is-bordered .el-radio__label {
  display: flex; flex-direction: column; gap: 2px;
}
.save-step-form .el-radio.is-bordered small {
  color: var(--ckqa-text-muted); font-size: 11px; font-weight: 400;
}
```

- [ ] **Step 2: 跑 build 验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): PromptDisplay 三视图 + 05 完整版样式 (Phase 1e)"
```

---

## Task 12：手动验证

- [ ] **Step 1: 启动 dev**

Run: `cd frontend/apps/admin-app && pnpm dev`

- [ ] **Step 2: 验证 03 抽屉的 PromptDisplay**

- 进 candidates 步骤，点任一候选"查看完整提示词 →"
- 抽屉滑出，默认 rich 模式：章节卡片渲染（Goal / Schema Constraints / Real Data ...）
- 占位符 `{input_text}` 黄色高亮
- 点视图切换"分屏"→ 左右两栏，左富文本右暗色 IDE
- 滚动左侧，右侧同步滚动；反之亦然
- 切到"仅原文"→ 全暗色 IDE 风，行号 + section 染绿
- 点"📋 复制"→ toast 已复制

- [ ] **Step 3: 验证 05 完整版**

- 流程：seed 选 system_default → 跳过 prepare → candidates 全选 → 进入 scoring → 等评分跑完 → 选 rank 1（图谱感知 + 蒸馏样例）→ 进入 05
- 左侧 prompt 预览：金牌 + 综合分 chip + 中文译名 + 候选 ID 副标题 + 大小 / token；下方 PromptDisplay 默认 rich 模式
- 右侧表单：草稿名默认"操作系统 · 系统默认 · 2026-05-14"（注意：候选译名不进默认草稿名，因为种子是 system_default）
- 说明默认填入"经过 20 条校准集评估，召回率 74%、准确率 68%，综合分 0.71。"
- 来源记录：课程 / 构建运行 / 选定种子 / 选定候选 / 评分 run 5 行
- 保存范围 radio：默认"仅本次构建 + 入库历史草稿"
- 切换为"仅本次构建" → 触发 mark-dirty
- 改草稿名 → 触发 mark-dirty
- 浏览器后退 → 弹"未保存修改"确认弹窗

- [ ] **Step 4: 验证保存**

- 点"保存并返回构建向导" → toast"已保存到本次构建并入库为历史草稿（mock）"
- 控制台打印 `[Phase 1e mock] save payload {seed, name, description, selectedCandidate, candidateDisplayName, compositeScore, saveMode, metadata}`
- 跳回构建向导

- [ ] **Step 5: 验证局限修复**

- 1a 局限 1：seed → save 改名 → 回 seed 切 graphrag_tuned → 回 save → 草稿名应保留用户改过的内容（不被默认值覆盖）
- 1a 局限 2：seed → save 改 description → 浏览器后退 → 弹"未保存修改"
- 1a 局限 3：01 步选 history_draft → 不再 toast"开放"，而是 mock 接受并把对应种子标识带回，toast"已使用历史草稿..."
- 1a 局限 4：handleSave 根据 saveMode 显示不同 toast 文案

---

## 自检

### Spec 覆盖
- [x] § 提示词文本显示组件 M 组合方案 → Task 1-5（rich / split / raw 三视图 + 容错降级）
- [x] § rich 模式章节解析 + 占位符高亮 → Task 1 + Task 3
- [x] § raw 模式暗色 IDE + 语法高亮 → Task 4
- [x] § split 模式同步滚动 → Task 5（原生 scroll 事件 + ref）
- [x] § 05 左 prompt 预览 + 右草稿名表单 → Task 7
- [x] § 05 来源记录（5 行：课程 / 构建运行 / 种子 / 候选 / 评分 run）→ Task 7
- [x] § 05 保存范围 radio（build_run_with_history / build_run_only）→ Task 7
- [x] § Phase 1a 局限 1-4 修复 → Task 7 + Task 9（局限 1 由 Page 层状态上提解决）
- [ ] 局限 5 解锁规则细化 → Phase 2 hook

未覆盖：
- 真实 prompt-drafts 入库（属于 Phase 6 真实 API 接入）
- prompt 内联编辑（spec 不要求；用户在标注 IDE 编辑了实体/关系，prompt 是只读展示）

### 占位扫描
- 无 TBD / TODO

### 类型一致性
- `parsePromptSections` / `resolveSectionMeta` 在 Task 1 定义，Task 3-5 使用，签名一致。
- `buildSaveDraftPayload` 扩展字段（selectedCandidate / candidateDisplayName / compositeScore / saveMode）在 Task 6 定义，Task 7 使用。
- `MOCK_PROMPT_TEXTS` / `resolveCandidatePromptText` 在 Task 2 定义，Task 7 + Task 8 使用。
- `mark-dirty` 事件在 Task 7 emit，Task 9 在主壳监听并 set dirty=true。

### Phase 1 全部完成清单
- Phase 1a 骨架 ✓
- Phase 1b 02 标注 IDE ✓
- Phase 1c 03 候选勾选 ✓
- Phase 1d 04 评分 ✓
- Phase 1e PromptDisplay + 05 完整版 ✓

完成本计划后整个 Phase 1 收官，前端可独立运行的 5 步全 mock 版本就绪。下一步是 Phase 2（接真实 API），每个 phase 单独写计划。
