import { reactive } from 'vue'

export const THEME_MODES = ['light', 'dark', 'auto']

export const THEME_ACCENTS = [
  { key: 'indigo', label: 'Indigo', color: '#6366f1', strong: '#4f46e5', contrast: '#ffffff' },
  { key: 'blue', label: 'Blue', color: '#2563eb', strong: '#1d4ed8', contrast: '#ffffff' },
  { key: 'teal', label: 'Teal', color: '#0d9488', strong: '#0f766e', contrast: '#ffffff' },
  { key: 'purple', label: 'Purple', color: '#9333ea', strong: '#7e22ce', contrast: '#ffffff' },
  { key: 'amber', label: 'Amber', color: '#d97706', strong: '#b45309', contrast: '#ffffff' },
]

const STORAGE_KEY = 'ckqa-theme'
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function isValidMode(mode) {
  return THEME_MODES.includes(mode)
}

export function isValidAccent(accent) {
  return THEME_ACCENTS.some((item) => item.key === accent)
}

export function resolveTheme(mode, prefersDark) {
  if (mode === 'dark') return 'dark'
  if (mode === 'light') return 'light'
  return prefersDark ? 'dark' : 'light'
}

const state = reactive({
  mode: 'auto',
  accent: 'indigo',
  resolvedTheme: 'light',
})

let mediaQuery = null

function getMediaQuery() {
  if (!isBrowser || !window.matchMedia) return null
  return window.matchMedia('(prefers-color-scheme: dark)')
}

function save() {
  if (!isBrowser) return
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode: state.mode, accent: state.accent }))
}

function load() {
  if (!isBrowser) return
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    if (isValidMode(saved.mode)) state.mode = saved.mode
    if (isValidAccent(saved.accent)) state.accent = saved.accent
  } catch {
    state.mode = 'auto'
    state.accent = 'indigo'
  }
}

function syncDocumentTheme() {
  mediaQuery = mediaQuery ?? getMediaQuery()
  state.resolvedTheme = resolveTheme(state.mode, Boolean(mediaQuery?.matches))
  if (!isBrowser) return
  document.documentElement.setAttribute('data-theme', state.resolvedTheme)
  document.documentElement.setAttribute('data-accent', state.accent)
}

function setMode(mode) {
  if (!isValidMode(mode)) return
  state.mode = mode
  save()
  syncDocumentTheme()
}

function setAccent(accent) {
  if (!isValidAccent(accent)) return
  state.accent = accent
  save()
  syncDocumentTheme()
}

function initTheme() {
  load()
  syncDocumentTheme()
  mediaQuery = mediaQuery ?? getMediaQuery()
  mediaQuery?.addEventListener?.('change', syncDocumentTheme)
}

export const themeStore = {
  state,
  initTheme,
  setMode,
  setAccent,
  syncDocumentTheme,
}

