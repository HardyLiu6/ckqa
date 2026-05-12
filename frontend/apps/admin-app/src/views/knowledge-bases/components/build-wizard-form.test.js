import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function readSource(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

test('BuildWizardForm.vue 通过真实 step composable 和组件壳组合构建向导', () => {
  const source = readSource('./BuildWizardForm.vue')

  assert.match(source, /import\s+\{\s*computed,\s*onBeforeUnmount\s*\}\s+from\s+'vue'/)
  assert.match(source, /import\s+\{\s*useRoute,\s*useRouter\s*\}\s+from\s+'vue-router'/)
  assert.match(source, /import\s+BuildStepMaterial\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepMaterial\.vue'/)
  assert.match(source, /import\s+BuildStepParse\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepParse\.vue'/)
  assert.match(source, /import\s+BuildStepExport\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepExport\.vue'/)
  assert.match(source, /import\s+BuildStepPrompt\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepPrompt\.vue'/)
  assert.match(source, /import\s+BuildStepIndex\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepIndex\.vue'/)
  assert.match(source, /import\s+BuildStepQaCheck\s+from\s+'\.\.\/\.\.\/\.\.\/components\/build-wizard\/BuildStepQaCheck\.vue'/)
  assert.match(source, /import\s+DataSourceChip\s+from\s+'\.\.\/\.\.\/\.\.\/components\/common\/DataSourceChip\.vue'/)
  assert.match(source, /import\s+StatusBadge\s+from\s+'\.\.\/\.\.\/\.\.\/components\/common\/StatusBadge\.vue'/)
  assert.match(source, /import\s+WorkflowStepper\s+from\s+'\.\.\/\.\.\/\.\.\/components\/common\/WorkflowStepper\.vue'/)
  assert.match(source, /import\s+RouteState\s+from\s+'\.\.\/\.\.\/status\/RouteState\.vue'/)
  assert.match(source, /import\s+\{\s*useBuildOperations\s*\}\s+from\s+'\.\.\/\.\.\/\.\.\/composables\/useBuildOperations\.js'/)
  assert.match(source, /import\s+\{\s*useBuildWizardForm\s*\}\s+from\s+'\.\.\/\.\.\/\.\.\/composables\/useBuildWizardForm\.js'/)
  assert.match(source, /import\s+\{\s*ChevronLeft,\s*DatabaseZap\s*\}\s+from\s+'lucide-vue-next'/)

  assert.match(source, /const operations = useBuildOperations\(\{\s*readonly:\s*\(\)\s*=>\s*props\.readonly,[\s\S]*route,[\s\S]*router,[\s\S]*\}\)/)
  assert.match(source, /const state = useBuildWizardForm\(\{\s*buildRunId:\s*\(\)\s*=>\s*props\.buildRunId,[\s\S]*kb:\s*\(\)\s*=>\s*props\.kb,[\s\S]*readonly:\s*\(\)\s*=>\s*props\.readonly,[\s\S]*operations,[\s\S]*route,[\s\S]*router,[\s\S]*stepComponents:[\s\S]*material:\s*BuildStepMaterial,[\s\S]*qa_check:\s*BuildStepQaCheck,[\s\S]*\}\)/)
  assert.match(source, /onBeforeUnmount\(\(\)\s*=>\s*\{\s*operations\.cancelLongTask\(\)\s*\}\)/)

  assert.match(source, /<article class="build-wizard-form" data-testid="build-wizard-form">/)
  assert.match(source, /<section class="module-hero">[\s\S]*<p v-if="heroEyebrow" class="eyebrow">\{\{ heroEyebrow \}\}<\/p>[\s\S]*<h2>\{\{ heroTitle \}\}<\/h2>[\s\S]*<DataSourceChip :source="heroSource" :refreshed-at="state\.config\.value\.refreshedAt" \/>[\s\S]*<DatabaseZap class="button-icon"/)
  assert.match(source, /<WorkflowStepper[\s\S]*:active-key="state\.activeStepKey\.value"[\s\S]*:steps="state\.config\.value\.workflowSteps \?\? \[\]"/)
  assert.match(source, /@update:active-key="state\.updateActiveStep"/)
  assert.match(source, /<section class="build-step-stage" :data-step="state\.activeStep\.value\?\.key">/)
  assert.doesNotMatch(source, /<section v-else class="build-step-stage">/)
  assert.match(source, /<header class="build-step-stage__header">/)
  assert.match(source, /class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"/)
  assert.match(source, /<ChevronLeft class="button-icon" :size="18" aria-hidden="true" \/>/)
  assert.match(source, /<p class="eyebrow">STEP \{\{ state\.stepIndexLabel\.value \}\}<\/p>/)
  assert.match(source, /<h2>\{\{ state\.activeStep\.value\?\.label \}\}<\/h2>/)
  assert.match(source, /<p>\{\{ state\.activeStep\.value\?\.detail \}\}<\/p>/)
  assert.match(source, /<StatusBadge[\s\S]*v-if="state\.activeStep\.value\?\.status"[\s\S]*:status="state\.activeStep\.value\.status"[\s\S]*:label="state\.activeStep\.value\.displayStatus \|\| state\.activeStep\.value\.status"/)
  assert.match(source, /<div class="build-summary-strip">[\s\S]*<span[\s\S]*class="build-summary-chip"[\s\S]*:data-tone="chip\.tone"/)
  assert.match(source, /<component[\s\S]*:is="state\.activeStepComponent\.value"[\s\S]*:blocks="state\.config\.value\.blocks"[\s\S]*:step="state\.activeStep\.value"[\s\S]*:action-running="operations\.actionRunning\.value"[\s\S]*:operation-feedback="state\.activeOperationFeedback\.value"[\s\S]*:smoke-question="operations\.smokeQuestion\.value"[\s\S]*:smoke-result="operations\.smokeResult\.value"[\s\S]*@select-materials="state\.updateMaterialSelection"[\s\S]*@update-smoke-question="operations\.updateSmokeQuestion"/)
  assert.match(source, /<footer class="build-step-stage__actions">[\s\S]*:disabled="state\.primaryAction\.value\.disabled \|\| operations\.actionRunning\.value"[\s\S]*@click="state\.handlePrimaryAction"[\s\S]*<component[\s\S]*:is="state\.primaryActionIcon\.value"[\s\S]*{{ state\.primaryAction\.value\.label }}/)

  const workflowStepperIndex = source.indexOf('<WorkflowStepper')
  const stageIndex = source.indexOf('<section class="build-step-stage" :data-step="state.activeStep.value?.key">')
  const routeStateIndex = source.indexOf('<RouteState')

  assert.ok(workflowStepperIndex !== -1, '应存在 WorkflowStepper')
  assert.ok(stageIndex !== -1, '应存在 build-step-stage section')
  assert.ok(routeStateIndex !== -1, '应存在 RouteState 错误态')
  assert.ok(workflowStepperIndex < stageIndex, 'WorkflowStepper 应位于 stage 之前')
  assert.ok(stageIndex < routeStateIndex, 'stage 应位于 RouteState 之前')
  assert.match(source, /<RouteState[\s\S]*v-if="state\.loadError\.value"[\s\S]*variant="error"[\s\S]*:title="state\.loadError\.value\.message"/)

  assert.match(source, /\.build-step-stage\s*\{/)
  assert.match(source, /\.build-step-stage__header\s*\{/)
  assert.match(source, /\.build-step-stage__header h2,\s*\.build-step-stage__header p\s*\{/)
  assert.match(source, /\.build-step-stage__header p:not\(\.eyebrow\)\s*\{/)
  assert.match(source, /\.build-step-stage__back\.el-button\s*\{/)
  assert.match(source, /\.build-summary-strip\s*\{/)
  assert.match(source, /\.build-summary-chip\s*\{/)
  assert.match(source, /\.build-summary-chip\[data-tone='ok'\]\s*\{/)
  assert.match(source, /\.build-summary-chip\[data-tone='warn'\]\s*\{/)
  assert.match(source, /\.build-summary-chip\[data-tone='info'\]\s*\{/)
  assert.match(source, /\.build-step-stage__body,\s*\.build-step-panel\s*\{/)
  assert.match(source, /\.build-step-stage__actions\s*\{/)
  assert.match(source, /\.build-step-stage__actions \.el-button\s*\{/)
  assert.match(source, /\.inline-error\s*\{/)
})

test('RouteState.vue 新增 variant/title/message 兼容层且 error 映射到 server-error', () => {
  const source = readSource('../../status/RouteState.vue')

  assert.match(source, /const props = defineProps\(\{[\s\S]*variant:[\s\S]*type:\s*String[\s\S]*default:\s*''[\s\S]*title:[\s\S]*type:\s*String[\s\S]*default:\s*''[\s\S]*message:[\s\S]*type:\s*String[\s\S]*default:\s*''[\s\S]*\}\)/)
  assert.match(source, /function resolveRouteStateVariant\(variant\) \{[\s\S]*if \(normalized === 'error'\) return 'server-error'[\s\S]*return normalized[\s\S]*\}/)
  assert.match(source, /const currentState = computed\(\(\) => resolveRouteStateVariant\(props\.variant\) \|\| props\.state \|\| route\.meta\.routeState \|\| 'coming-soon'\)/)
  assert.match(source, /const copy = computed\(\(\) => \{[\s\S]*title:\s*props\.title \|\| routeTitle\.value,[\s\S]*message:\s*props\.message \|\| '该入口已保留在导航和权限结构中，当前阶段暂不开放业务页面。'/)
  assert.match(source, /if \(currentState\.value === 'server-error'\) \{[\s\S]*eyebrow:\s*'500 \/ Server Error'/)
})
