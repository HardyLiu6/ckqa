import test from 'node:test'
import assert from 'node:assert/strict'

import { routes, whiteList } from '../src/router/routes.js'

const routeMap = new Map(routes.map((route) => [route.name, route]))

test('新增视觉壳路由已从 coming-soon 清单移除', () => {
  // 这些原本是 coming-soon，本次改成真实视觉壳
  const nowImplementedNames = [
    'KnowledgeGraph',
    'KnowledgeSearch',
    'UserProfile',
    'UserSettings',
    'UserNotification',
    'UserFavorite',
  ]
  for (const name of nowImplementedNames) {
    const route = routeMap.get(name)
    assert.ok(route, `路由 ${name} 不存在`)
    assert.notEqual(route.meta.routeState, 'coming-soon', `${name} 不应再是 coming-soon`)
    assert.equal(route.meta.layout, 'module', `${name} 应该使用 module layout`)
  }
})

test('剩余 coming-soon 路由仍显式标记', () => {
  const comingSoonNames = [
    'QADetail',
    'KnowledgeDetail',
    'CommunityDiscuss', 'CommunityPost', 'CommunityCreate', 'CommunityRank',
    'WrongAnalysis', 'LearningReport', 'SmartRecommend',
  ]
  for (const routeName of comingSoonNames) {
    const route = routeMap.get(routeName)
    assert.ok(route, `${routeName} 路由不存在`)
    assert.equal(route.meta.routeState, 'coming-soon', `${routeName} 未标 coming-soon`)
  }
})

test('认证路由已经开放并保持免登录访问', () => {
  for (const routeName of ['Login', 'Register', 'ForgotPassword']) {
    const route = routeMap.get(routeName)
    assert.ok(route, `${routeName} 路由不存在`)
    assert.equal(route.meta.routeState, undefined, `${routeName} 不应再是 coming-soon`)
    assert.equal(route.meta.noAuth, true, `${routeName} 应允许未登录访问`)
    assert.equal(route.meta.layout, 'landing', `${routeName} 应使用 landing layout`)
  }
})

test('关键主路由都有 layout meta', () => {
  const cases = [
    ['Intro', 'landing'],
    ['Home', 'product'],
    ['QAAsk', 'module'],
    ['CourseList', 'module'],
    ['CourseLearn', 'product'], // 深色例外
    ['KnowledgeGraph', 'module'],
    ['UserProfile', 'module'],
  ]
  for (const [name, expected] of cases) {
    const route = routeMap.get(name)
    assert.ok(route, `${name} 不存在`)
    assert.equal(route.meta.layout, expected, `${name} layout 不等于 ${expected}`)
  }
})

test('whiteList 覆盖未登录可访问的基础路径', () => {
  assert.ok(whiteList.includes('/'))
  assert.ok(whiteList.includes('/login'))
  assert.ok(whiteList.includes('/404'))
})
