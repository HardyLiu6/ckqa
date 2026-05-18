import { defineConfig, devices } from '@playwright/test'

const localNoProxy = appendNoProxy(process.env.NO_PROXY)
process.env.NO_PROXY = localNoProxy
process.env.no_proxy = appendNoProxy(process.env.no_proxy)

export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  outputDir: './test-results',
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:15175',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'pnpm dev --host 127.0.0.1 --port 15175',
    url: 'http://127.0.0.1:15175',
    reuseExistingServer: false,
    timeout: 120000,
  },
})

function appendNoProxy(value = '') {
  const entries = new Set(
    value
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean),
  )
  for (const entry of ['127.0.0.1', 'localhost', '::1']) {
    entries.add(entry)
  }
  return [...entries].join(',')
}
