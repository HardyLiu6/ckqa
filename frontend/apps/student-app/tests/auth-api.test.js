// 验证认证 API 模块和 user store 在源码层面提供了登录注册需要的全部端点与配置
// stores/user.js 用 @/ 别名间接引用 axios，node --test 没有 alias 解析能力
// 因此这里通过读取源码字符串做轻量契约校验，确保关键端点不会被意外删除

import test from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import fs from 'node:fs'

const ROOT = path.resolve(import.meta.dirname, '..')
const SRC = path.join(ROOT, 'src')

test('认证 API 模块声明了所有学生端必需的端点', () => {
  const source = fs.readFileSync(path.join(SRC, 'api/auth.js'), 'utf8')
  // 登录 / 注册原有契约保持不变
  assert.match(source, /post\('\/auth\/student\/login'/)
  assert.match(source, /post\('\/auth\/student\/register'/)
  // 学生端邮箱验证码登录
  assert.match(source, /post\('\/auth\/email\/student\/login'/)
  // 邮箱验证码下发与忘记密码端点
  assert.match(source, /post\('\/auth\/email\/send-code'/)
  assert.match(source, /post\('\/auth\/password\/reset-by-email'/)
  // 注册时的账号占用检查
  assert.match(source, /get\('\/auth\/account\/availability'/)
  // 邮箱验证码场景参数会被传给后端
  assert.match(source, /scene:\s*payload\.scene/)
})

test('user store 暴露 7 天记住我配置和邮箱重置接口', () => {
  const source = fs.readFileSync(path.join(SRC, 'stores/user.js'), 'utf8')
  assert.match(source, /export const REMEMBER_ME_DAYS = 7/)
  // 登录、邮箱验证码登录、注册、重置密码都应被暴露
  assert.match(source, /loginByEmailCode/)
  assert.match(source, /resetPassword/)
  // 记住我标识被持久化到 localStorage
  assert.match(source, /persistRememberedProfile/)
  // 会话过期校验函数存在
  assert.match(source, /isSessionAlive/)
})
