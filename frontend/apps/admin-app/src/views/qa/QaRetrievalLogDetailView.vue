<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getQaOperationLog, upsertQaSourceReview } from '../../api/qa-operations.js'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const detail = ref(null)
const reviewDrafts = reactive({})

const sources = computed(() => detail.value?.sources ?? [])
const feedback = computed(() => detail.value?.feedback ?? [])

onMounted(loadDetail)

async function loadDetail() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await getQaOperationLog(route.params.logId)
    detail.value = payload
    for (const source of payload.sources ?? []) {
      const ownReview = source.reviews?.[0] ?? {}
      reviewDrafts[source.id] = {
        relevance: ownReview.relevance || 'unknown',
        citationQuality: ownReview.citationQuality || 'unknown',
        note: ownReview.note || '',
      }
    }
  } catch (error) {
    errorMessage.value = error?.message || '检索日志详情加载失败'
  } finally {
    loading.value = false
  }
}

async function saveReview(source) {
  const draft = reviewDrafts[source.id]
  if (!source?.id || !draft) {
    return
  }
  try {
    const saved = await upsertQaSourceReview(source.id, draft)
    source.reviews = [saved]
    Object.assign(reviewDrafts[source.id], {
      relevance: saved.relevance || draft.relevance,
      citationQuality: saved.citationQuality || draft.citationQuality,
      note: saved.note || draft.note,
    })
    ElMessage.success('来源标注已保存')
  } catch (error) {
    ElMessage.error(error?.message || '来源标注保存失败')
  }
}

function formatTags(tags = []) {
  return Array.isArray(tags) && tags.length ? tags.join('、') : '无标签'
}

function routingSnapshotText(snapshot) {
  if (!snapshot) {
    return '-'
  }
  if (typeof snapshot === 'string') {
    try {
      return JSON.stringify(JSON.parse(snapshot), null, 2)
    } catch {
      return snapshot
    }
  }
  return JSON.stringify(snapshot, null, 2)
}
</script>

<template>
  <section class="qa-log-detail">
    <header class="detail-header">
      <div>
        <p class="eyebrow">Retrieval Log</p>
        <h1>检索日志 #{{ route.params.logId }}</h1>
        <p>内部诊断字段仅在管理端可见，用于排查 rewrite、上下文、来源与学生反馈。</p>
      </div>
      <el-button :loading="loading" @click="loadDetail">刷新</el-button>
    </header>

    <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon />

    <main v-if="detail" v-loading="loading" class="detail-grid">
      <section class="panel overview-panel">
        <h2>任务概览</h2>
        <dl>
          <div><dt>状态</dt><dd>{{ detail.taskStatus }}</dd></div>
          <div><dt>模式</dt><dd>{{ detail.queryMode }}</dd></div>
          <div><dt>路由置信度</dt><dd>{{ detail.routingConfidenceBand || '-' }}</dd></div>
          <div><dt>复核优先级</dt><dd>{{ detail.routingReviewPriority || 'normal' }}</dd></div>
          <div><dt>课程</dt><dd>{{ detail.courseName || detail.courseId }}</dd></div>
          <div><dt>知识库</dt><dd>{{ detail.knowledgeBaseName || detail.knowledgeBaseId }}</dd></div>
          <div><dt>学生</dt><dd>{{ detail.displayName || detail.username || detail.userId }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ detail.createdAt }}</dd></div>
        </dl>
      </section>

      <section class="panel">
        <h2>Rewrite 与上下文</h2>
        <div class="diagnostic-block">
          <span>原始问题</span>
          <pre>{{ detail.originalQueryText || detail.queryText || '-' }}</pre>
        </div>
        <div class="diagnostic-block">
          <span>检索问题</span>
          <pre>{{ detail.retrievalQueryText || '-' }}</pre>
        </div>
        <div class="diagnostic-block">
          <span>独立问题</span>
          <pre>{{ detail.standaloneQueryText || '-' }}</pre>
        </div>
        <div class="diagnostic-block">
          <span>生成上下文</span>
          <pre>{{ detail.generationContext || detail.contextSnapshotText || '-' }}</pre>
        </div>
        <div class="diagnostic-block">
          <span>智能推荐快照</span>
          <pre>{{ routingSnapshotText(detail.routingSnapshotJson) }}</pre>
        </div>
      </section>

      <section class="panel full-span">
        <h2>回答正文</h2>
        <p class="answer-text">{{ detail.assistantContent || detail.assistantMessageText || '暂无回答正文' }}</p>
      </section>

      <section class="panel full-span">
        <h2>学生反馈</h2>
        <div v-if="!feedback.length" class="empty-line">暂无反馈</div>
        <ul v-else class="feedback-list">
          <li v-for="item in feedback" :key="item.id">
            <strong>{{ item.rating }}</strong>
            <span>{{ formatTags(item.tags) }}</span>
            <p v-if="item.comment">{{ item.comment }}</p>
          </li>
        </ul>
      </section>

      <section class="panel full-span">
        <h2>参考来源与人工标注</h2>
        <div v-if="!sources.length" class="empty-line">暂无来源</div>
        <article v-for="source in sources" v-else :key="source.id" class="source-review-card">
          <header>
            <strong>来源 {{ source.rankPosition }}</strong>
            <span>{{ source.sourceType || 'unknown' }}</span>
            <span>{{ source.sourceFile || source.documentKey || '-' }}</span>
          </header>
          <p v-if="source.headingPath" class="muted">{{ source.headingPath }}</p>
          <p v-if="source.snippet" class="snippet">{{ source.snippet }}</p>
          <div class="review-controls">
            <el-select v-model="reviewDrafts[source.id].relevance" placeholder="相关性">
              <el-option label="相关" value="relevant" />
              <el-option label="部分相关" value="partially_relevant" />
              <el-option label="不相关" value="irrelevant" />
              <el-option label="未知" value="unknown" />
            </el-select>
            <el-select v-model="reviewDrafts[source.id].citationQuality" placeholder="引用质量">
              <el-option label="支持结论" value="supports_claim" />
              <el-option label="弱支持" value="weak_support" />
              <el-option label="错误来源" value="wrong_source" />
              <el-option label="重复" value="duplicate" />
              <el-option label="未知" value="unknown" />
            </el-select>
            <el-input v-model="reviewDrafts[source.id].note" maxlength="500" placeholder="备注" />
            <el-button type="primary" @click="saveReview(source)">保存标注</el-button>
          </div>
        </article>
      </section>
    </main>
  </section>
</template>

<style scoped lang="scss">
.qa-log-detail {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.detail-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  h1 {
    margin: 4px 0;
    color: #0f172a;
    font-size: 26px;
  }

  p {
    margin: 0;
    color: #64748b;
  }
}

.eyebrow {
  color: #2563eb !important;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(360px, 1.2fr);
  gap: 14px;
}

.panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  padding: 16px;

  h2 {
    margin: 0 0 12px;
    color: #0f172a;
    font-size: 16px;
  }
}

.full-span {
  grid-column: 1 / -1;
}

dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

dl div {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 10px;
}

dt {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

dd {
  margin: 0;
  color: #0f172a;
}

.diagnostic-block {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;

  span {
    color: #475569;
    font-size: 12px;
    font-weight: 800;
  }

  pre {
    max-height: 180px;
    overflow: auto;
    margin: 0;
    border-radius: 6px;
    background: #f8fafc;
    color: #1e293b;
    font-size: 12px;
    line-height: 1.6;
    padding: 10px;
    white-space: pre-wrap;
  }
}

.answer-text,
.snippet {
  margin: 0;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
}

.empty-line,
.muted {
  color: #64748b;
}

.feedback-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.source-review-card {
  display: grid;
  gap: 10px;
  border-top: 1px solid #e2e8f0;
  padding: 14px 0;

  &:first-of-type {
    border-top: 0;
    padding-top: 0;
  }

  header {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.review-controls {
  display: grid;
  grid-template-columns: 160px 160px minmax(180px, 1fr) max-content;
  gap: 8px;
}

@media (max-width: 960px) {
  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-grid,
  .review-controls {
    grid-template-columns: 1fr;
  }
}
</style>
