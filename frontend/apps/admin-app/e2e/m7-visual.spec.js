import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

// M7 · 任务 7.2：暗色视觉快照基准建立。
//
// 验收目标（design.md §10.3 / DoD-9 / CC-4 / NFR-3）：
// - 覆盖 `UserListPage / RoleListPage / PermissionListPage / HealthPage /
//   KbValidationPage` 五个页面；
// - 每页分别在 `data-theme='light'` 与 `'dark'` 两套 token 下截取整页基准图，
//   共 5 × 2 = 10 张快照；
// - 后续回归允许不超过 0.3% 像素差（`maxDiffPixelRatio: 0.003`）；
// - 首次运行需用 `pnpm test:e2e -- e2e/m7-visual.spec.js --update-snapshots`
//   建立基线并 commit；第二次运行起必须全绿。
//
// 稳定性约束：
// - 所有 API 走 `loginAsAdmin` + `page.route` mock，避免真实后端的时间戳/序号
//   与默认列表随机性污染截图；
// - 每个 mock handler 只返回 1 行稳定数据；
// - 主题通过 `page.addInitScript` 预写 `localStorage.ckqa-admin-theme`，确保
//   theme store 首帧即读到目标 mode，不依赖运行期切换；
// - `emulateMedia({ colorScheme })` 让 `mode: 'dark'` 在 auto 兼容兜底也成立；
// - Playwright 截屏时通过 `animations: 'disabled'` 冻结 CSS transition / 动画。

const VIEWPORT = { width: 1440, height: 900 }
const THEMES = ['light', 'dark']
const SCREENSHOT_OPTIONS = {
  fullPage: true,
  maxDiffPixelRatio: 0.003,
  animations: 'disabled',
}

// ---------------------------------------------------------------------------
// API mock handlers —— 每个 handler 只返回 1 行，保持视觉基线完全确定。
// ---------------------------------------------------------------------------

const USERS_HANDLER = () => ({
  data: {
    items: [
      {
        id: 1,
        code: 'ADM2026001',
        userCode: 'ADM2026001',
        username: 'admin.visual',
        displayName: '视觉快照管理员',
        status: 'active',
        roles: [{ code: 'admin', name: '平台管理员' }],
        lastLoginAt: '2026-05-10 10:00',
      },
    ],
    current: 1,
    page: 1,
    size: 20,
    total: 1,
    pages: 1,
  },
})

const ROLES_HANDLER = () => ({
  data: {
    items: [
      {
        id: 1,
        code: 'admin',
        name: '平台管理员',
        status: 'active',
        permissions: [],
        updatedAt: '2026-05-10 10:00',
      },
    ],
    current: 1,
    page: 1,
    size: 20,
    total: 1,
    pages: 1,
  },
})

const PERMISSIONS_HANDLER = () => ({
  data: {
    items: [
      {
        id: 1,
        code: 'user:read',
        name: '查看用户',
        resource: 'user',
        action: 'read',
        status: 'active',
      },
    ],
    current: 1,
    page: 1,
    size: 20,
    total: 1,
    pages: 1,
  },
})

// `GET /system/health`：healthy + 所有 service reachable/ready 为 true，
// 且不带 message；确保 diagnostics 日志区为空、避免噪声字符影响基线。
const HEALTH_HANDLER = () => ({
  data: {
    status: 'healthy',
    checkedAt: '2026-05-10 14:20',
    services: {
      javaApi: { reachable: true, ready: true, message: '' },
      mysql: { reachable: true, ready: true, message: '' },
      pdfIngest: { reachable: true, ready: true, message: '' },
      graphRagApi: { reachable: true, ready: true, message: '' },
    },
  },
})

// `GET /knowledge-bases`：返回 1 个 KB，但不主动选择也不发起验证 —— 页面停留在
// `runState = 'idle'`（右侧是 `CkEmptyState`），左侧表单为默认值。这样视觉快照
// 不会被动画 / 进度条干扰。
const KB_LIST_HANDLER = () => ({
  data: {
    items: [
      {
        id: 7,
        name: 'OS 知识库',
        courseId: 'crs-os',
        status: 'active',
        latestBuildRunId: 123,
        activeBuildRunId: 123,
        buildRunId: 123,
        activeIndexRunId: 42,
      },
    ],
    current: 1,
    page: 1,
    size: 20,
    total: 1,
    pages: 1,
  },
})

// ---------------------------------------------------------------------------
// 主题注入 helper —— 必须在第一次 `page.goto` 之前调用，使 theme store 首帧即
// 命中目标 mode；配合 `emulateMedia` 保证 `auto` 兜底也走暗色。
// ---------------------------------------------------------------------------

async function applyTheme(page, theme) {
  await page.addInitScript((mode) => {
    try {
      window.localStorage.setItem(
        'ckqa-admin-theme',
        JSON.stringify({ mode, accent: 'rust' }),
      )
    } catch (_err) {
      // 非浏览器或 storage 不可用时静默跳过；theme store 会退回到 light 默认。
    }
  }, theme)
  await page.emulateMedia({ colorScheme: theme })
}

// ---------------------------------------------------------------------------
// 页面清单 —— 每个 spec 定义 name / url / 渲染完成的锚点 / mock handlers。
// ---------------------------------------------------------------------------

const PAGE_SPECS = [
  {
    name: 'user-list',
    url: '/app/users',
    anchor: '[data-testid="user-table"]',
    mocks: () => ({ 'GET /users': USERS_HANDLER }),
  },
  {
    name: 'role-list',
    url: '/app/roles',
    anchor: '[data-testid="role-table"]',
    mocks: () => ({ 'GET /roles': ROLES_HANDLER }),
  },
  {
    name: 'permission-list',
    url: '/app/permissions',
    anchor: '[data-testid="permission-table"]',
    mocks: () => ({ 'GET /permissions': PERMISSIONS_HANDLER }),
  },
  {
    name: 'health-page',
    url: '/app/health',
    anchor: '[data-testid="health-overall-pill"]',
    mocks: () => ({ 'GET /system/health': HEALTH_HANDLER }),
  },
  {
    name: 'kb-validation',
    url: '/app/qa-smoke',
    // 页面 idle 态下右侧渲染 `CkEmptyState`（带 `data-testid="kb-validation-empty"`）；
    // 顶层 `data-testid="kb-validation-page"` 也会存在，这里选 empty 确保 KB 列表
    // 已经拉完（下拉已 populate），避免"下拉占位"导致的高度跳动。
    anchor: '[data-testid="kb-validation-empty"]',
    mocks: () => ({ 'GET /knowledge-bases': KB_LIST_HANDLER }),
  },
]

// ---------------------------------------------------------------------------
// 测试矩阵：5 页 × 2 主题 = 10 张快照。
// ---------------------------------------------------------------------------

test.describe('M7 · 视觉快照', () => {
  for (const spec of PAGE_SPECS) {
    for (const theme of THEMES) {
      test(`${spec.name} · ${theme}`, async ({ page }) => {
        await applyTheme(page, theme)
        await loginAsAdmin(page, { mocks: spec.mocks(), viewport: VIEWPORT })

        await page.goto(spec.url)
        await expect(page.locator(spec.anchor).first()).toBeVisible()

        // 让 Element Plus 的过渡 / CkSkeleton 的淡入 / 字体再流式布局一次收敛。
        // Playwright 的 `animations: 'disabled'` 只能冻结 CSS 动画帧，对
        // IntersectionObserver / ResizeObserver 驱动的 JS 动画无能为力，这里加
        // 一个较短的稳定睡眠做兜底。
        await page.waitForTimeout(200)

        await expect(page).toHaveScreenshot(
          `${spec.name}-${theme}.png`,
          SCREENSHOT_OPTIONS,
        )
      })
    }
  }
})
