import test from 'node:test'
import assert from 'node:assert/strict'

import { routes, whiteList } from '../src/router/routes.js'

const routeMap = new Map(routes.map((route) => [route.name, route]))

test('占位功能路由会显式标记为未开放', () => {
  const comingSoonNames = [
    'KnowledgeGraph',
    'KnowledgeSearch',
    'KnowledgeDetail',
    'CommunityDiscuss',
    'CommunityPost',
    'CommunityCreate',
    'CommunityRank',
    'WrongAnalysis',
    'LearningReport',
    'SmartRecommend',
    'UserProfile',
    'UserSettings',
    'UserNotification',
    'UserFavorite',
    'Login',
    'Register',
    'ForgotPassword',
  ]

  for (const routeName of comingSoonNames) {
    const route = routeMap.get(routeName)
    assert.ok(route, `${routeName} 路由不存在`)
    assert.equal(route.meta.routeState, 'coming-soon')
    assert.equal(typeof route.component, 'function')
  }
})

test('系统状态路由保留明确状态，未实现页面不会伪装成正常页面', () => {
  assert.equal(routeMap.get('Home').meta.routeState, undefined)
  assert.equal(routeMap.get('Forbidden').meta.routeState, '403')
  assert.equal(routeMap.get('NotFound').meta.routeState, '404')
  assert.equal(routeMap.get('ServerError').meta.routeState, '500')
  assert.ok(whiteList.includes('/404'))
  assert.equal(routes.at(-1).redirect, '/404')
})
