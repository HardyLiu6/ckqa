import MarkdownIt from 'markdown-it'
import createDOMPurify from 'dompurify'

const SOURCE_MARKER_PATTERN = /\[来源\s+([0-9\s、,，]+)\]/g

const SANITIZE_CONFIG = {
  ALLOWED_TAGS: [
    'h1',
    'h2',
    'h3',
    'h4',
    'p',
    'strong',
    'em',
    'ul',
    'ol',
    'li',
    'code',
    'pre',
    'blockquote',
    'a',
    'br',
    'span',
  ],
  ALLOWED_ATTR: ['href', 'target', 'rel', 'class', 'data-source-refs'],
  ALLOW_DATA_ATTR: true,
}

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
})

const defaultLinkOpen = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const hrefIndex = token.attrIndex('href')
  if (hrefIndex >= 0) {
    const href = token.attrs[hrefIndex][1] || ''
    if (!/^https?:\/\//i.test(href) && !href.startsWith('/')) {
      token.attrs.splice(hrefIndex, 1)
    }
  }
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen ? defaultLinkOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
}

let browserPurifier = null

export function renderQaMarkdown(content, options = {}) {
  const rawHtml = markdown.render(stripDangerousMarkdownInput(String(content ?? '')))
  const htmlWithSources = decorateSourceMarkers(rawHtml)
  return sanitizeQaHtml(htmlWithSources, options)
}

export function stripDangerousMarkdownInput(markdownText) {
  return String(markdownText ?? '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<[^>]+\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)[^>]*>/gi, '')
    .replace(/\[([^\]]+)]\(\s*javascript:[^)]+\)/gi, '$1')
    .replace(/javascript:/gi, '')
}

export function decorateSourceMarkers(html) {
  return String(html ?? '').replace(SOURCE_MARKER_PATTERN, (match, rawRefs) => {
    const refs = String(rawRefs)
      .split(/[、,，\s]+/)
      .map((ref) => ref.trim())
      .filter(Boolean)
    if (refs.length === 0 || refs.some((ref) => !/^\d+$/.test(ref))) {
      return match
    }
    const label = refs.join('、')
    return `<span class="qa-source-marker" data-source-refs="${refs.join(',')}">来源 ${label}</span>`
  })
}

export function sanitizeQaHtml(html, options = {}) {
  const purifier = options.purifier || getBrowserPurifier()
  if (purifier?.sanitize) {
    return purifier.sanitize(String(html ?? ''), SANITIZE_CONFIG)
  }
  return fallbackSanitize(String(html ?? ''))
}

function getBrowserPurifier() {
  if (typeof window === 'undefined' || !window.document) {
    return null
  }
  if (!browserPurifier) {
    browserPurifier = createDOMPurify(window)
  }
  return browserPurifier
}

function fallbackSanitize(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}
