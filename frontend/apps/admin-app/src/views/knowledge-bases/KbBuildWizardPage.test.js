import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function readSource(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

test('KbBuildWizardPage.vue 左栏直接接入 BuildWizardForm', () => {
  const source = readSource('./KbBuildWizardPage.vue')

  assert.match(source, /import\s+BuildWizardForm\s+from\s+'\.\/components\/BuildWizardForm\.vue'/)
  assert.doesNotMatch(source, /import\s+ModulePage\s+from\s+'\.\.\/pages\/ModulePage\.vue'/)
  assert.match(
    source,
    /<BuildWizardForm[\s\S]*:build-run-id="buildRunId"[\s\S]*:kb="knowledgeBase"[\s\S]*:readonly="readonly"[\s\S]*\/>/,
  )
  assert.doesNotMatch(source, /<ModulePage\s*\/>/)
})

test('KbBuildWizardPage.vue 会在 buildRunId 切流时同步刷新 runCache', () => {
  const source = readSource('./KbBuildWizardPage.vue')

  assert.match(source, /let\s+runCacheRefreshSeq\s*=\s*0/)
  assert.match(source, /const\s+\{\s*state:\s*streamState,\s*start,\s*reset,\s*refresh\s*\}\s*=\s*useBuildRunStream\(/)
  assert.match(
    source,
    /async function syncRunCache\(\)\s*\{[\s\S]*const requestSeq = \+\+runCacheRefreshSeq[\s\S]*const snapshot = await refresh\(\)[\s\S]*if \(requestSeq !== runCacheRefreshSeq\) return null[\s\S]*runCache\.value = snapshot \?\? null[\s\S]*return snapshot[\s\S]*\}/,
  )
  assert.match(
    source,
    /watch\(buildRunId,\s*\(next\) => \{[\s\S]*if \(next\) \{[\s\S]*start\(\{ buildRunId: next \}\)[\s\S]*void syncRunCache\(\)[\s\S]*\} else \{[\s\S]*reset\(\)[\s\S]*runCacheRefreshSeq \+= 1[\s\S]*runCache\.value = null[\s\S]*\}/,
  )
  assert.match(
    source,
    /onMounted\(\(\) => \{[\s\S]*if \(buildRunId\.value\) \{[\s\S]*start\(\{ buildRunId: buildRunId\.value \}\)[\s\S]*void syncRunCache\(\)[\s\S]*\}/,
  )
})
