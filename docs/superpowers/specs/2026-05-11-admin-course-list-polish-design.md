# Admin 课程列表页打磨设计稿

- **日期**: 2026-05-11
- **作者**: 何启航 (与 Claude 协作)
- **分支**: `feature/admin-app-redesign-m1-m2`
- **范围**: `frontend/apps/admin-app` 课程列表页（`/app/courses`），共享 `CkResourceCard` 组件，后端默认封面 SVG 资源。

## 1. 背景与问题

管理员控制台 `/app/courses` 列表页存在三个肉眼可见的瑕疵：

1. **面包屑 / 标题信息重复**。顶部 console 面包屑渲染 `生产 / 课程列表`，紧跟着 `CkPageHero` 的 eyebrow 又写了 `生产 · 课程`、标题再写一遍 `课程`，三层信息密度堆在 80px 高度内，"生产"出现两次，"课程"出现两次。
2. **默认课程封面带 CKQA 英文品牌字**。`default-course-cover.svg` 内嵌 `CKQA Course` 与 `资料解析 · 知识图谱 · 智能问答`。平台对外品牌是「智课问答」，CKQA 是仓库内部代号，UI 不应露出；同时 4 张课程卡全部走同一张 fallback，整列视觉极其重复。
3. **共享卡片在课程语境下的细节不到位**。`CkResourceCard` 默认 `nowrap` 截断长课程名（截图中 `Smoke GraphRAG Isolation 2026…` 已被截掉），状态徽标挤在标题右侧抢空间，资料/知识库 meta 用普通粗细的文字呈现，缺乏 hover 反馈。

## 2. 目标与非目标

### 目标

- 让课程列表页的视觉层级清晰：一行面包屑 + 一组 hero + 一面卡片网格，三者各司其职。
- 默认封面在不同课程间产生视觉差异，且不再出现任何英文品牌字符。
- 共享卡片在不破坏既有调用方的前提下，提供"课程列表"所需的展示变体。

### 非目标

- 不修改 `CourseDetailPage` 与 `ModulePage` 的封面行为（用户没指出问题，归属下一轮）。
- 不新增设计 token，全部复用 `--ckqa-*` 现有变量。
- 不调整路由 `meta.title`、`section`、面包屑组件本身的逻辑。
- 不引入新的依赖。

## 3. 设计概览

```
┌─────────────────────────────────────────────────────┐
│ 顶部面包屑（layout 已有）：生产 / 课程列表           │
├─────────────────────────────────────────────────────┤
│ CkPageHero                                          │
│   ─ 课程                                             │
│   ─ 管理已有课程，进入详情后可以维护成员、资料和知识库 │
│   ─ [新建课程] CTA                                   │
├─────────────────────────────────────────────────────┤
│ 卡片网格（auto-fill minmax(280px,1fr))               │
│  ┌──────────────┐  ┌──────────────┐                 │
│  │ [封面 16:9]   │  │ [封面 16:9]   │  ……           │
│  │   ┌──────┐   │  │   ┌──────┐   │                │
│  │   │  操  │   │  │   │  公  │   │                │
│  │   └──────┘   │  │   └──────┘   │                │
│  │      [● 已激活│  │      [● 已激活│                │
│  ├──────────────┤  ├──────────────┤                 │
│  │ 课程名（最多 │  │ 课程名（最多 │                 │
│  │ 两行换行）   │  │ 两行换行）   │                 │
│  │ 描述…        │  │ 描述…        │                 │
│  │  0  | 1      │  │  4  | 1      │                 │
│  │ 资料 知识库  │  │ 资料 知识库  │                 │
│  └──────────────┘  └──────────────┘                 │
└─────────────────────────────────────────────────────┘
```

## 4. 详细设计

### 4.1 面包屑 / 标题去重

**改动文件**：`frontend/apps/admin-app/src/views/courses/course-page-copy.js`

- 删除 `COURSE_PAGE_COPY.list.eyebrow`。
- `CourseListPage.vue` 模板里 `<CkPageHero>` 移除 `:eyebrow="..."` 绑定（`CkPageHero` 在 prop 缺省时已经不渲染 eyebrow 节点，无需改组件）。
- 顶部 `CkBreadcrumbs` 继续使用 `console-breadcrumb-model` 的输出 `生产 / 课程列表`，无改动。

不动 `eyebrowFormat` 给 detail 页用的副本，避免连带影响。

### 4.2 新组件 `CkCourseCoverArt.vue`

**新增文件**：`frontend/apps/admin-app/src/components/common/CkCourseCoverArt.vue`

#### Props

| 名称 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `seed` | `string` | 必填 | 用于挑调色板的稳定输入（课程 id；缺失时回退到课程名）。 |
| `label` | `string` | 必填 | 课程名，用于推导封面字形。 |
| `ariaLabel` | `string` | `'课程封面'` | 给 SVG `<title>` 用。 |

#### 行为

1. **首字符提取**：
   - 取 `label` 去除空白后的第 1 个字符；若该字符是 ASCII 字母 / 数字，且第 2 个字符也是 ASCII，再取一个，合成 2 字符并 `toUpperCase()`。
   - 中日韩字符只取 1 个。
   - 如果 `label` 为空，回退为 `课`。
2. **调色板挑选**：
   - 定义 6 套调色板（每套 4 个值：`bgFrom`、`bgTo`、`plateRing`、`accent`），写在组件内的常量里。
   - 哈希函数：djb2 风格的 32-bit 串行哈希（`hash = ((hash << 5) - hash) + ch | 0`），输入用 `seed || label`。
   - `palette = PALETTES[Math.abs(hash) % PALETTES.length]`。
3. **SVG 结构**（`viewBox="0 0 960 540"`，与原 SVG 一致，便于卡片 `object-fit: cover`）：
   - `<linearGradient>` 背景：`bgFrom → bgTo`，对角方向。
   - 一层 `<path>` 模拟柔和坡线（重用原 SVG 的两条 wave 路径，但颜色用 palette 的 wave token，opacity 0.55 / 0.4）。
   - 中央一个 360x300 圆角面板（`rx=28`），白底加 `plateRing` 描边。
   - 面板里居中绘制首字符 `<text>`：`font-family: var(--ckqa-font-display, "Inter, ...")`，字号 180，`font-weight: 700`，色 `accent`。
   - 面板右上角点缀 3 个小圆点连线（半径 7，描边 3），与原图谱意象呼应；坐标固定，颜色 `accent` 淡化版（`opacity: 0.45`）。
   - **不写任何文本品牌字**。
4. **可访问性**：SVG 带 `role="img"`、`aria-label`、内部 `<title>{{ ariaLabel }}</title>`。

#### 决定性

同样的 `seed` 必定产出同一套调色板与同一个字符——做 snapshot 测试时不引入随机性。

### 4.3 `CkResourceCard` 扩展

**改动文件**：`frontend/apps/admin-app/src/components/common/CkResourceCard.vue`

新增 3 个**默认值与旧行为一致**的 prop：

| 名称 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `titleClamp` | `Number` | `1` | `1` 时保留现有 `nowrap + ellipsis`；`>=2` 时切换为 `-webkit-line-clamp: N` 多行省略。 |
| `statusFloating` | `Boolean` | `false` | `true` 时 `CkStatusPill` 改为绝对定位在封面右上角，并加毛玻璃底色（`background: rgba(255,255,255,0.78)` + `backdrop-filter: blur(6px)`）；内部 header 的 pill 实例隐藏。 |
| `metaVariant` | `'inline' \| 'emphasis'` | `'inline'` | `'emphasis'` 时单元从一行改为 stacked：上面是数字（`font-size: var(--ckqa-text-xl-size)`，`font-weight: 700`），下面是 label（`var(--ckqa-text-xs-size)`，`color: var(--ckqa-text-weak)`）。 |

并新增一个具名插槽：

- `#cover`：当父组件传入插槽内容时，封面 `<figure>` 内部渲染插槽；否则维持当前的 `<img :src="cover">` 行为。这样课程列表可以把 `<CkCourseCoverArt>` 塞进去而不破坏既有图片入口（如真实课程缩略图、KB 列表等其他用方）。

Hover 增强（默认即生效，但只是 token 内变化，视觉柔和，不会破坏既有页面）：

- 现有 `.ck-resource-card:hover { box-shadow: var(--ckqa-shadow-card-hover); }` 之外，追加 `transform: translateY(-2px)` 与 `border-color: var(--ckqa-border-strong)`（已确认该 token 同时在亮/暗主题里定义于 `styles/tokens/_colors.scss`）。
- 为使位移不抖，给 `.ck-resource-card` 加 `transition: box-shadow ..., transform var(--ckqa-duration-fast) var(--ckqa-ease-standard), border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard)`。

### 4.4 `CourseListPage` 接入

**改动文件**：`frontend/apps/admin-app/src/views/courses/CourseListPage.vue`

- `import CkCourseCoverArt from '../../components/common/CkCourseCoverArt.vue'`
- `cards.value` 里去掉 `cover` 字段的硬绑定，新增 `useDefaultArt: !row.thumbnailUrl` 与 `seed: row.id`。
- 模板：

```vue
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
```

`card.cover` 仍然保留 `row.thumbnailUrl`（真实封面 URL）用于上面这条 `<img>`；不再走 `DEFAULT_COURSE_COVER_URL` 兜底。

### 4.5 后端默认封面 SVG 文本清理

**改动文件**：`backend/ckqa-back/src/main/resources/static/assets/course-covers/default-course-cover.svg`

- 删除两条 `<text>` 节点（`CKQA Course` 与下方副标题）。
- `<title>` 改为 `默认课程封面`，`<desc>` 改为 `带有资料卡片与节点的默认封面`。
- 其他几何元素不动，保留装饰感，留作其他列表（如管理员"全部课程"老调用方）的兜底。

此举确保即便有未迁移的地方仍命中 `DEFAULT_COURSE_COVER_URL`，也不会再出现 CKQA 字样。

## 5. 数据流 / 接口影响

- 前端无新 API 调用，所有改动停留在视图层。
- 后端只动静态资源，不影响 `course-covers` HTTP 接口形态（缓存 header 已由 Spring 处理；建议在 commit 信息中提示运维侧 CDN 缓存可能需要 purge）。

## 6. 测试计划

| 类型 | 文件 | 用例 |
|------|------|------|
| 单元 | `frontend/apps/admin-app/src/components/common/__tests__/CkCourseCoverArt.spec.js` (新建) | 1) 相同 `seed` 必产生同一字符 & palette；2) 中文 / 英文 / 空 label 三种入参产出预期首字符；3) 输出 `<svg>` 不包含字符串 `CKQA`。 |
| 单元 | `frontend/apps/admin-app/src/components/common/__tests__/CkResourceCard.spec.js` (新建或扩展) | 1) 默认 prop 时 DOM 与之前一致（snapshot）；2) `statusFloating` 时 pill 节点位于 `figure` 而非 `header`；3) `titleClamp=2` 时标题节点带 `data-clamp="2"` 之类的可断言钩子。 |
| 视图 | `frontend/apps/admin-app/src/views/courses/__tests__/CourseListPage.spec.js` (若不存在则新建) | 1) 渲染包含 `CkCourseCoverArt` 当 thumbnailUrl 缺省；2) hero 不再渲染 eyebrow（断言 `.ck-page-hero-eyebrow` 不存在）。 |
| e2e | `frontend/apps/admin-app/e2e/courses.list.spec.js` (扩充已有 spec) | Playwright 打开课程列表，断言：a) 顶栏存在 `生产 / 课程列表` 面包屑；b) hero 只有一个 `课程` 文本节点；c) 至少一张卡片 SVG 内不含 `CKQA`。 |

CI 中按 `pnpm -C frontend/apps/admin-app test` 与 `pnpm -C frontend/apps/admin-app exec playwright test e2e/courses.list.spec.js` 跑。

## 7. 回滚

- 单 PR；如出问题 `git revert` 即可。`default-course-cover.svg` 在历史里仍可恢复。
- 由于新增的 prop 与插槽默认行为与旧版一致，对其他页面（资料、知识库、QA 会话等）的 `CkResourceCard` 调用无破坏。

## 8. 风险与缓解

| 风险 | 缓解 |
|------|------|
| `CkResourceCard` 其他用方意外受 hover 位移影响 | hover 位移幅度限 -2px，且加 transition；评审时跑 `frontend/apps/admin-app/e2e` 全套，重点看资料、知识库、QA 会话三处列表。 |
| 中文首字符出现非常用字（如生僻字） | SVG `<text>` 用回退字体栈 `var(--ckqa-font-display), "PingFang SC", "Microsoft YaHei", sans-serif`，与全站 `body` 同源。 |
| 哈希分布不均，导致同一调色板扎堆 | 列表常驻数量 ≤ 一屏；djb2 + 6 个 bucket 在样本上已足够分散，必要时未来再扩到 8 个调色板。 |

## 9. 待办拆解（写入实现计划时再展开）

1. 改 `course-page-copy.js` + `CourseListPage.vue` 去 eyebrow。
2. 新增 `CkCourseCoverArt.vue` + 单测。
3. 扩展 `CkResourceCard.vue` 三个 prop / `#cover` 插槽 + 单测。
4. `CourseListPage.vue` 接入新组件与新 prop。
5. 清理 `default-course-cover.svg` 文本。
6. 补 e2e 用例。
7. `pnpm -C frontend/apps/admin-app lint && test && playwright test` 全绿后提交。
