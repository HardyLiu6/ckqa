import config from './playwright.config.js'

if (process.env.CKQA_STUDENT_E2E_LIVE !== '1') {
  throw new Error('live E2E 需要显式设置 CKQA_STUDENT_E2E_LIVE=1')
}

export default {
  ...config,
  testDir: './e2e-live',
  webServer: undefined,
}
