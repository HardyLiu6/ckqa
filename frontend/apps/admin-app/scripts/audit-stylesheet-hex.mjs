#!/usr/bin/env node
// 文件说明：
//   admin-app M7 样式巡检脚本。静态扫描 src/**/*.{vue,scss,css}，
//   检测是否出现裸 hex（#RRGGBB 等）或裸 rgb()/rgba() 写法。
//   例外路径来自 M7 requirements.md NFR-4 与 tasks.md 7.4：
//     - src/styles/element-plus.scss
//     - src/styles/tokens/**
//     - node_modules/**、dist/**（天然不在扫描范围，这里再防御性跳过）
//   同时保留 M1~M2 历史遗留文件的"legacy allow-list"，原因与后续清理计划
//   在白名单处逐条记录；本脚本仅负责把 M7 阶段 3~6 的新/改文件锁死为 Token。
//
// 使用方式：
//   pnpm lint:style                # 正常巡检
//   pnpm lint:style --debug        # 额外打印已跳过的文件列表
//
// 退出码：
//   0 通过、1 命中违规或执行出错。

import { readdir, readFile, stat } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { resolve, relative, join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const APP_ROOT = resolve(dirname(__filename), '..')
const SRC_DIR = resolve(APP_ROOT, 'src')

const DEBUG = process.argv.includes('--debug')

// 完全跳过的单文件（相对 APP_ROOT 的 POSIX 风格路径）
const EXCLUDED_FILES = new Set([
  'src/styles/element-plus.scss',
])

// 完全跳过的目录前缀（相对 APP_ROOT 的 POSIX 风格路径，带末尾 /）
const EXCLUDED_DIR_PREFIXES = [
  'src/styles/tokens/',
  'node_modules/',
  'dist/',
]

// M1~M2 已存在的历史文件，M7 不负责清理。后续拆独立的
// "admin-app-style-legacy-cleanup" spec 时，删除对应条目并把裸色
// 替换成 Token 引用。
//
// 每一条请附一个简短原因说明，便于 PR 审阅者追溯。
const LEGACY_ALLOWLIST = new Map([
  [
    'src/styles/components.scss',
    'M1~M2 全局组件样式集含大量历史裸值（Playwright/Element Plus 兼容写法），独立清理 PR 处理',
  ],
])

// 命中规则（只扫 .vue 的 <style> 块与 .scss/.css 整文件）
const HEX_RE = /#[0-9a-fA-F]{3,8}\b/g
const RGB_RE = /\brgba?\(/g

const SCSS_INTERPOLATION_RE = /#\{[^}]*\}/g
const VUE_STYLE_BLOCK_RE = /<style\b[^>]*>([\s\S]*?)<\/style>/g

/**
 * 将相对路径统一成 POSIX 风格，方便跨平台比对。
 */
function toPosix(relPath) {
  return relPath.split(/[\\/]+/).join('/')
}

function isExcluded(posixRel) {
  if (EXCLUDED_FILES.has(posixRel)) return { hit: true, reason: 'excluded-file' }
  for (const prefix of EXCLUDED_DIR_PREFIXES) {
    if (posixRel.startsWith(prefix)) return { hit: true, reason: `excluded-dir:${prefix}` }
  }
  if (LEGACY_ALLOWLIST.has(posixRel)) {
    return { hit: true, reason: `legacy-allowlist:${LEGACY_ALLOWLIST.get(posixRel)}` }
  }
  return { hit: false }
}

async function* walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  for (const entry of entries) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      yield* walk(full)
    } else if (entry.isFile()) {
      yield full
    }
  }
}

/**
 * 从 .vue 文件内容中提取所有 <style> 块，并附带"style 块起始在原文件中的行号"
 * 以便命中时还原为源文件行号。
 */
function extractVueStyleBlocks(source) {
  const blocks = []
  let match
  VUE_STYLE_BLOCK_RE.lastIndex = 0
  while ((match = VUE_STYLE_BLOCK_RE.exec(source)) !== null) {
    const blockContent = match[1]
    const offset = match.index + match[0].indexOf('>') + 1
    const preceding = source.slice(0, offset)
    const startLine = preceding.split('\n').length
    blocks.push({ content: blockContent, startLine })
  }
  return blocks
}

/**
 * 逐行扫一段样式文本，抽出命中行。行号以 baseLine 为起点（baseLine 指向 content 的第 1 行在源文件中的行号）。
 */
function scanStyleChunk(content, baseLine, posixRel, violations) {
  const lines = content.split('\n')
  for (let i = 0; i < lines.length; i += 1) {
    const rawLine = lines[i]
    // 先抹掉 SCSS 插值 `#{ ... }`，避免把插值误判为 hex。
    const stripped = rawLine.replace(SCSS_INTERPOLATION_RE, '')

    HEX_RE.lastIndex = 0
    const hexMatch = HEX_RE.exec(stripped)

    RGB_RE.lastIndex = 0
    const rgbMatch = RGB_RE.exec(stripped)

    if (hexMatch) {
      violations.push({
        file: posixRel,
        line: baseLine + i,
        kind: 'hex',
        snippet: rawLine.trim(),
        token: hexMatch[0],
      })
    }
    if (rgbMatch) {
      violations.push({
        file: posixRel,
        line: baseLine + i,
        kind: 'rgb',
        snippet: rawLine.trim(),
        token: rgbMatch[0] + ')',
      })
    }
  }
}

async function scanFile(absPath, posixRel, violations) {
  const source = await readFile(absPath, 'utf8')

  if (absPath.endsWith('.vue')) {
    const blocks = extractVueStyleBlocks(source)
    for (const block of blocks) {
      scanStyleChunk(block.content, block.startLine, posixRel, violations)
    }
  } else {
    // .scss / .css 整文件
    scanStyleChunk(source, 1, posixRel, violations)
  }
}

async function main() {
  if (!existsSync(SRC_DIR)) {
    console.error(`[audit-stylesheet-hex] 找不到 src 目录：${SRC_DIR}`)
    process.exit(1)
  }

  const violations = []
  const skipped = []
  let scannedCount = 0

  for await (const absPath of walk(SRC_DIR)) {
    if (!/\.(vue|scss|css)$/.test(absPath)) continue
    const posixRel = toPosix(relative(APP_ROOT, absPath))
    const skip = isExcluded(posixRel)
    if (skip.hit) {
      skipped.push({ file: posixRel, reason: skip.reason })
      continue
    }
    await scanFile(absPath, posixRel, violations)
    scannedCount += 1
  }

  if (DEBUG) {
    console.log(`[audit-stylesheet-hex] 扫描文件数：${scannedCount}`)
    if (skipped.length > 0) {
      console.log(`[audit-stylesheet-hex] 跳过的文件（${skipped.length}）：`)
      for (const item of skipped) {
        console.log(`  - ${item.file}  (${item.reason})`)
      }
    }
  }

  if (violations.length === 0) {
    console.log(`[audit-stylesheet-hex] ✅ 通过，扫描 ${scannedCount} 个样式文件，零裸 hex/rgb 写法`)
    process.exit(0)
  }

  console.error(`[audit-stylesheet-hex] ❌ 命中 ${violations.length} 处裸色值写法：\n`)
  for (const v of violations) {
    console.error(`  ${v.file}:${v.line}  [${v.kind}] ${v.token}`)
    console.error(`      ${v.snippet}`)
  }
  console.error('\n修复建议：把对应颜色替换为 src/styles/tokens/** 暴露的 CSS 变量。')
  console.error('若某文件属于历史遗留范围需临时放行，请在 scripts/audit-stylesheet-hex.mjs 的 LEGACY_ALLOWLIST 中登记并注明清理计划。')
  process.exit(1)
}

main().catch((error) => {
  console.error('[audit-stylesheet-hex] 扫描过程抛错：')
  console.error(error)
  process.exit(1)
})
