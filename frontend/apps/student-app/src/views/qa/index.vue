<template>
  <div class="qa-container">
    <!-- 顶部导航栏 -->
    <header class="qa-header">
      <div class="header-content">
        <div class="header-left">
          <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
          <div class="header-title">
            <h1>智能问答</h1>
            <el-tag v-if="currentSession" type="info" effect="plain" size="small">
              {{ currentSession.title || '新对话' }}
            </el-tag>
          </div>
        </div>

        <div class="header-actions">
          <el-button type="primary" :icon="Plus" @click="createNewSession">新对话</el-button>

          <el-select v-model="selectedCourseId" placeholder="选择课程" class="course-selector" filterable clearable
            @change="handleCourseChange">
            <el-option v-for="course in courseList" :key="course.id" :label="course.name" :value="course.id">
              <div class="course-option">
                <el-icon>
                  <Reading />
                </el-icon>
                <span>{{ course.name }}</span>
              </div>
            </el-option>
          </el-select>

          <el-dropdown trigger="click">
            <el-button :icon="More" circle class="more-btn" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="saveSession" :disabled="!hasMessages">
                  <el-icon>
                    <FolderAdd />
                  </el-icon>保存对话
                </el-dropdown-item>
                <el-dropdown-item @click="clearCurrentChat" :disabled="!hasMessages">
                  <el-icon>
                    <Delete />
                  </el-icon>清空当前对话
                </el-dropdown-item>
                <el-dropdown-item @click="viewHistory" divided>
                  <el-icon>
                    <Clock />
                  </el-icon>历史记录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="qa-body">
      <!-- 左侧会话列表 -->
      <aside class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <h3>对话列表</h3>
          <el-button :icon="sidebarCollapsed ? Expand : Fold" text @click="sidebarCollapsed = !sidebarCollapsed" />
        </div>

        <div class="session-list">
          <div v-for="session in recentSessions" :key="session.id" class="session-item"
            :class="{ active: currentSession?.id === session.id }" @click="loadSession(session)">
            <div class="session-icon"><el-icon>
                <ChatDotRound />
              </el-icon></div>
            <div class="session-info">
              <span class="session-title">{{ session.title }}</span>
              <span class="session-meta">{{ session.messageCount }}条对话 · {{ session.lastTime }}</span>
            </div>
            <el-dropdown trigger="click" @click.stop>
              <el-button :icon="More" text size="small" class="session-more" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="viewSessionDetail(session)"><el-icon>
                      <View />
                    </el-icon>查看详情</el-dropdown-item>
                  <el-dropdown-item @click="deleteSession(session)"><el-icon>
                      <Delete />
                    </el-icon>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div class="sidebar-footer">
          <el-button text @click="viewHistory"><el-icon>
              <Clock />
            </el-icon>查看全部历史</el-button>
        </div>
      </aside>

      <!-- 主对话区域 -->
      <main class="qa-main">
        <div class="messages-container" ref="messagesContainer">
          <!-- 欢迎消息 -->
          <div v-if="messages.length === 0" class="welcome-section">
            <div class="welcome-icon"><el-icon :size="64">
                <ChatDotRound />
              </el-icon></div>
            <h2 class="welcome-title">开始新的对话</h2>
            <p class="welcome-desc">我是智课问答助手，融合知识图谱与大语言模型。<br />选择一门课程开始针对性问答，或直接提问进行通用对话。</p>
            <div class="quick-questions">
              <h3 class="quick-title">试试这些问题：</h3>
              <div class="question-cards">
                <div v-for="(q, i) in suggestedQuestions" :key="i" class="question-card" @click="askQuestion(q.text)">
                  <div class="card-icon" :class="q.theme"><el-icon>
                      <component :is="q.icon" />
                    </el-icon></div>
                  <div class="card-content">
                    <span class="card-category">{{ q.category }}</span>
                    <p class="card-text">{{ q.text }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div v-else class="messages-list">
            <TransitionGroup name="message">
              <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.role">
                <template v-if="msg.role === 'user'">
                  <div class="message-content user-message">
                    <div class="message-text">{{ msg.content }}</div>
                    <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
                  </div>
                  <el-avatar :size="40" class="message-avatar">U</el-avatar>
                </template>
                <template v-else>
                  <div class="ai-avatar"><el-icon :size="24">
                      <ChatDotRound />
                    </el-icon></div>
                  <div class="message-content ai-message">
                    <div v-if="msg.sources?.length" class="knowledge-sources">
                      <span class="source-label">知识来源：</span>
                      <el-tag v-for="s in msg.sources" :key="s.id" size="small" effect="plain" class="source-tag">{{
                        s.name }}</el-tag>
                    </div>
                    <div class="message-text" v-html="renderMarkdown(msg.content)"></div>
                    <div v-if="msg.relatedKnowledge?.length" class="related-knowledge">
                      <span class="related-label">相关知识点：</span>
                      <div class="related-tags">
                        <el-tag v-for="k in msg.relatedKnowledge" :key="k.id" :type="k.type || 'info'" size="small"
                          class="related-tag">{{ k.name }}</el-tag>
                      </div>
                    </div>
                    <div class="message-actions">
                      <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                      <div class="action-buttons">
                        <el-button :icon="CopyDocument" text size="small" @click="copyMessage(msg)" />
                        <el-button :icon="msg.liked ? StarFilled : Star" text size="small" :class="{ liked: msg.liked }"
                          @click="toggleLike(msg)" />
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </TransitionGroup>
            <div v-if="isTyping" class="message-item assistant typing">
              <div class="ai-avatar"><el-icon :size="24">
                  <ChatDotRound />
                </el-icon></div>
              <div class="message-content ai-message">
                <div class="typing-indicator"><span></span><span></span><span></span></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="input-wrapper">
            <div v-if="contextHint" class="context-hint">
              <el-icon>
                <InfoFilled />
              </el-icon>
              <span>{{ contextHint }}</span>
              <el-button text size="small" @click="contextHint = ''"><el-icon>
                  <Close />
                </el-icon></el-button>
            </div>
            <div class="input-container">
              <div class="input-row">
                <div class="input-tools">
                  <el-button :icon="Picture" circle class="tool-btn" @click="$refs.imageInput.click()" />
                  <el-button :icon="FolderAdd" circle class="tool-btn" @click="$refs.fileInput.click()" />
                </div>
                <el-input v-model="inputText" type="textarea" :autosize="{ minRows: 1, maxRows: 6 }"
                  placeholder="输入问题，支持连续追问... (Ctrl+Enter发送)" class="message-input" @keydown="handleKeydown" />
                <el-button type="primary" class="send-btn" :disabled="!canSend" :loading="isSending"
                  @click="sendMessage">
                  <el-icon>
                    <Promotion />
                  </el-icon>
                </el-button>
              </div>
              <div class="input-footer">
                <span class="input-tip"><el-icon>
                    <InfoFilled />
                  </el-icon>支持连续对话，AI会记住上下文</span>
                <span class="char-count">{{ inputText.length }} / 2000</span>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <input ref="imageInput" type="file" accept="image/*" hidden @change="handleFileChange" />
    <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.txt" hidden @change="handleFileChange" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Reading, More, FolderAdd, Delete, Clock, ChatDotRound, CopyDocument, Star, StarFilled, InfoFilled, Close, Picture, Promotion, DataLine, Connection, Cpu, Collection, Expand, Fold, View } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const messagesContainer = ref(null)

// 模拟数据
const courseList = ref([
  { id: 1, name: '数据结构与算法' },
  { id: 2, name: '计算机网络' },
  { id: 3, name: '操作系统原理' },
  { id: 4, name: '数据库系统概论' },
  { id: 5, name: '软件工程' }
])

const recentSessions = ref([
  { id: 'session-1', title: '二叉树遍历算法讨论', courseId: 1, messageCount: 6, lastTime: '10分钟前' },
  { id: 'session-2', title: 'TCP/IP协议详解', courseId: 2, messageCount: 4, lastTime: '1小时前' },
  { id: 'session-3', title: '进程调度算法比较', courseId: 3, messageCount: 8, lastTime: '昨天' }
])

const suggestedQuestions = ref([
  { icon: DataLine, theme: 'theme-blue', category: '数据结构', text: '什么是二叉树？它有哪些常见的遍历方式？' },
  { icon: Connection, theme: 'theme-green', category: '计算机网络', text: 'TCP三次握手的过程是怎样的？' },
  { icon: Cpu, theme: 'theme-orange', category: '操作系统', text: '进程和线程有什么区别？' },
  { icon: Collection, theme: 'theme-purple', category: '数据库', text: '什么是数据库索引？如何优化SQL查询？' }
])

// 状态
const sidebarCollapsed = ref(false)
const selectedCourseId = ref('')
const inputText = ref('')
const isTyping = ref(false)
const isSending = ref(false)
const contextHint = ref('')
const messages = ref([])
const currentSession = ref(null)

const hasMessages = computed(() => messages.value.length > 0)
const canSend = computed(() => inputText.value.trim().length > 0)

// AI回复库
const aiResponses = {
  '二叉树': { content: `**二叉树**是一种重要的数据结构，每个节点最多有两个子节点。\n\n### 常见遍历方式\n1. **前序遍历**：根→左→右\n2. **中序遍历**：左→根→右\n3. **后序遍历**：左→右→根\n4. **层序遍历**：按层访问\n\n\`\`\`python\ndef inorder(root):\n    if root:\n        inorder(root.left)\n        print(root.val)\n        inorder(root.right)\n\`\`\`\n\n还想了解**二叉搜索树**或**平衡树**吗？`, sources: [{ id: 1, name: '数据结构-第5章' }], relatedKnowledge: [{ id: 1, name: '二叉树' }, { id: 2, name: '递归', type: 'success' }] },
  'TCP': { content: `**TCP三次握手**确保双方建立可靠连接。\n\n### 过程\n1. **SYN**：客户端发送请求\n2. **SYN+ACK**：服务器确认回复\n3. **ACK**：客户端确认，连接建立\n\n### 为什么三次？\n两次无法确认客户端接收能力，四次冗余。\n\n想继续了解**四次挥手**吗？`, sources: [{ id: 2, name: '计算机网络-传输层' }], relatedKnowledge: [{ id: 3, name: 'TCP协议' }, { id: 4, name: '三次握手', type: 'success' }] },
  '进程': { content: `**进程**和**线程**是操作系统核心概念。\n\n### 主要区别\n| 维度 | 进程 | 线程 |\n|------|------|------|\n| 资源 | 独立空间 | 共享资源 |\n| 开销 | 大 | 小 |\n| 通信 | IPC | 共享内存 |\n\n想深入了解**进程调度**还是**线程同步**？`, sources: [{ id: 3, name: '操作系统-第3章' }], relatedKnowledge: [{ id: 5, name: '进程' }, { id: 6, name: '线程', type: 'success' }] },
  '索引': { content: `**数据库索引**是加速查询的关键。\n\n### 类型\n1. **B+树索引**：最常用\n2. **哈希索引**：等值查询O(1)\n3. **全文索引**：文本搜索\n\n### 优化建议\n- WHERE字段建索引\n- 避免索引列使用函数\n- 使用EXPLAIN分析\n\n要了解**索引失效场景**吗？`, sources: [{ id: 4, name: '数据库-索引' }], relatedKnowledge: [{ id: 7, name: 'B+树' }, { id: 8, name: 'SQL优化', type: 'success' }] },
  'default': { content: `好的，让我来回答这个问题。\n\n根据课程知识库，这是一个很好的问题。主要包括：\n\n1. **基本概念**：核心定义\n2. **关键特性**：重要特点\n3. **应用场景**：实际用途\n\n可以继续追问，我会基于上下文给出更详细的解答。`, sources: [{ id: 0, name: '知识图谱' }], relatedKnowledge: [] }
}

const followUpPrefixes = ['基于我们的讨论，', '继续上面的话题，', '好问题！结合之前的内容，', '这与前面讨论的相关，']

// 方法
const goBack = () => router.back()
const viewHistory = () => router.push('/qa/history')
const viewSessionDetail = (session) => router.push(`/qa/detail/${session.id}`)

const handleCourseChange = (id) => {
  if (id) {
    const course = courseList.value.find(c => c.id === id)
    contextHint.value = `已切换到「${course?.name}」，回答将基于该课程知识`
  } else contextHint.value = ''
}

const createNewSession = () => {
  currentSession.value = { id: `session-${Date.now()}`, title: '新对话', courseId: selectedCourseId.value, messageCount: 0, lastTime: '刚刚', createdAt: new Date() }
  messages.value = []
  ElMessage.success('已创建新对话')
}

const loadSession = (session) => {
  currentSession.value = session
  messages.value = session.id === 'session-1' ? [
    { id: 1, role: 'user', content: '什么是二叉树？', timestamp: new Date(Date.now() - 600000) },
    { id: 2, role: 'assistant', content: '**二叉树**是一种树形数据结构，每个节点最多有两个子节点...', timestamp: new Date(Date.now() - 590000), sources: [{ id: 1, name: '数据结构讲义' }], relatedKnowledge: [{ id: 1, name: '二叉树' }] },
    { id: 3, role: 'user', content: '那二叉搜索树呢？', timestamp: new Date(Date.now() - 500000) },
    { id: 4, role: 'assistant', content: '**二叉搜索树(BST)**满足：左子树 < 根 < 右子树，查找效率O(log n)...', timestamp: new Date(Date.now() - 490000), sources: [{ id: 1, name: '数据结构讲义' }], relatedKnowledge: [{ id: 2, name: 'BST', type: 'success' }] }
  ] : []
  nextTick(scrollToBottom)
}

const saveSession = () => {
  if (!hasMessages.value) return
  const firstMsg = messages.value.find(m => m.role === 'user')
  if (currentSession.value) {
    currentSession.value.title = firstMsg?.content.substring(0, 20) + '...' || '新对话'
    currentSession.value.messageCount = messages.value.length
  }
  ElMessage.success('对话已保存')
}

const clearCurrentChat = async () => {
  try {
    await ElMessageBox.confirm('确定清空当前对话？', '提示', { type: 'warning' })
    messages.value = []
    currentSession.value = null
    ElMessage.success('已清空')
  } catch { }
}

const deleteSession = async (session) => {
  try {
    await ElMessageBox.confirm('确定删除这个对话？', '确认', { type: 'warning' })
    const idx = recentSessions.value.findIndex(s => s.id === session.id)
    if (idx > -1) recentSessions.value.splice(idx, 1)
    if (currentSession.value?.id === session.id) { currentSession.value = null; messages.value = [] }
    ElMessage.success('已删除')
  } catch { }
}

const askQuestion = (text) => { inputText.value = text; sendMessage() }

const sendMessage = async () => {
  if (!canSend.value || isSending.value) return
  const content = inputText.value.trim()
  const isFollowUp = messages.value.length > 0

  messages.value.push({ id: Date.now(), role: 'user', content, timestamp: new Date() })
  inputText.value = ''
  await nextTick(); scrollToBottom()

  if (!currentSession.value) {
    currentSession.value = { id: `session-${Date.now()}`, title: content.substring(0, 20) + (content.length > 20 ? '...' : ''), courseId: selectedCourseId.value, messageCount: 1, lastTime: '刚刚' }
    recentSessions.value.unshift(currentSession.value)
  }

  isSending.value = true; isTyping.value = true
  await new Promise(r => setTimeout(r, 1200 + Math.random() * 800))

  let resp = aiResponses.default
  for (const key of Object.keys(aiResponses)) { if (content.includes(key)) { resp = aiResponses[key]; break } }

  let respContent = resp.content
  if (isFollowUp) respContent = followUpPrefixes[Math.floor(Math.random() * followUpPrefixes.length)] + '\n\n' + respContent

  messages.value.push({ id: Date.now() + 1, role: 'assistant', content: respContent, sources: resp.sources, relatedKnowledge: resp.relatedKnowledge, timestamp: new Date(), liked: false })

  if (currentSession.value) { currentSession.value.messageCount = messages.value.length; currentSession.value.lastTime = '刚刚' }

  isSending.value = false; isTyping.value = false
  await nextTick(); scrollToBottom()
}

const handleKeydown = (e) => { if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); sendMessage() } }
const copyMessage = async (msg) => { try { await navigator.clipboard.writeText(msg.content); ElMessage.success('已复制') } catch { ElMessage.error('复制失败') } }
const toggleLike = (msg) => { msg.liked = !msg.liked; ElMessage.success(msg.liked ? '感谢反馈！' : '已取消') }
const handleFileChange = (e) => { if (e.target.files?.[0]) ElMessage.success('文件已添加'); e.target.value = '' }
const formatTime = (d) => d ? new Date(d).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
const renderMarkdown = (c) => c?.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/`{3}(\w+)?\n([\s\S]*?)`{3}/g, '<pre><code>$2</code></pre>').replace(/`([^`]+)`/g, '<code>$1</code>').replace(/### (.*)/g, '<h4>$1</h4>').replace(/\n/g, '<br>') || ''
const scrollToBottom = () => { if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight }

onMounted(() => {
  const { sessionId, q, courseId } = route.query
  if (courseId) selectedCourseId.value = Number(courseId)
  if (sessionId) { const s = recentSessions.value.find(x => x.id === sessionId); if (s) loadSession(s) }
  if (q) { inputText.value = q; nextTick(sendMessage) }
})

watch(messages, () => nextTick(scrollToBottom), { deep: true })
</script>

<style lang="scss" scoped>
$primary: #4f46e5;
$primary-light: #818cf8;
$primary-dark: #3730a3;
$success: #10b981;
$warning: #f59e0b;
$danger: #ef4444;
$bg: #f8fafc;
$bg-card: #fff;
$bg-dark: #0f172a;
$text: #1e293b;
$text-secondary: #64748b;
$text-muted: #94a3b8;
$border: #e2e8f0;
$radius: 12px;

.qa-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $bg;
}

.qa-header {
  background: $bg-card;
  border-bottom: 1px solid $border;
  padding: 0 24px;
  flex-shrink: 0;
  z-index: 100;

  .header-content {
    max-width: 1400px;
    margin: 0 auto;
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .back-btn {
      border: none;
      background: $bg;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }

    .header-title {
      display: flex;
      align-items: center;
      gap: 12px;

      h1 {
        font-size: 18px;
        font-weight: 600;
        margin: 0;
      }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;

    .course-selector {
      width: 180px;
    }

    .more-btn {
      border: none;
      background: $bg;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }
  }
}

.qa-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.session-sidebar {
  width: 280px;
  background: $bg-card;
  border-right: 1px solid $border;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;

  &.collapsed {
    width: 0;
    overflow: hidden;
  }

  .sidebar-header {
    padding: 16px 20px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid $border;

    h3 {
      font-size: 14px;
      font-weight: 600;
      margin: 0;
      color: $text-secondary;
    }
  }

  .session-list {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
  }

  .session-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: $radius;
    cursor: pointer;
    transition: all 0.2s;
    margin-bottom: 8px;

    &:hover {
      background: $bg;
    }

    &.active {
      background: rgba($primary, 0.08);

      .session-icon {
        background: $primary;
        color: white;
      }
    }

    .session-icon {
      width: 36px;
      height: 36px;
      background: $bg;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: $text-secondary;
      flex-shrink: 0;
    }

    .session-info {
      flex: 1;
      min-width: 0;

      .session-title {
        display: block;
        font-size: 14px;
        font-weight: 500;
        color: $text;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .session-meta {
        font-size: 12px;
        color: $text-muted;
      }
    }

    .session-more {
      opacity: 0;
    }

    &:hover .session-more {
      opacity: 1;
    }
  }

  .sidebar-footer {
    padding: 16px;
    border-top: 1px solid $border;
    text-align: center;
  }
}

.qa-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 3px;
  }
}

.welcome-section {
  max-width: 800px;
  margin: 40px auto;
  text-align: center;

  .welcome-icon {
    width: 100px;
    height: 100px;
    background: linear-gradient(135deg, $primary, $primary-light);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 24px;
    color: white;
    animation: pulse 2s infinite;
  }

  .welcome-title {
    font-size: 28px;
    font-weight: 700;
    color: $text;
    margin-bottom: 12px;
  }

  .welcome-desc {
    font-size: 16px;
    color: $text-secondary;
    line-height: 1.6;
    margin-bottom: 40px;
  }
}

.quick-questions {
  .quick-title {
    font-size: 14px;
    color: $text-muted;
    margin-bottom: 16px;
  }

  .question-cards {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;

    @media (max-width: 640px) {
      grid-template-columns: 1fr;
    }
  }

  .question-card {
    display: flex;
    gap: 12px;
    padding: 16px;
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius;
    cursor: pointer;
    text-align: left;
    transition: all 0.2s;

    &:hover {
      border-color: $primary;
      box-shadow: 0 4px 12px rgba($primary, 0.15);
      transform: translateY(-2px);
    }

    .card-icon {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      &.theme-blue {
        background: rgba($primary, 0.1);
        color: $primary;
      }

      &.theme-green {
        background: rgba($success, 0.1);
        color: $success;
      }

      &.theme-orange {
        background: rgba($warning, 0.1);
        color: $warning;
      }

      &.theme-purple {
        background: rgba(#8b5cf6, 0.1);
        color: #8b5cf6;
      }
    }

    .card-content {
      .card-category {
        font-size: 12px;
        color: $text-muted;
      }

      .card-text {
        font-size: 14px;
        color: $text;
        margin-top: 4px;
        line-height: 1.4;
      }
    }
  }
}

.messages-list {
  max-width: 900px;
  margin: 0 auto;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;

  &.user {
    justify-content: flex-end;
  }

  .message-avatar {
    background: linear-gradient(135deg, $primary, $primary-dark);
    color: white;
    font-weight: 600;
    flex-shrink: 0;
  }

  .ai-avatar {
    width: 40px;
    height: 40px;
    background: linear-gradient(135deg, $primary, $primary-light);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    flex-shrink: 0;
  }

  .message-content {
    max-width: 70%;
    padding: 14px 18px;
    border-radius: 16px;
  }

  .user-message {
    background: linear-gradient(135deg, $primary, $primary-dark);
    color: white;
    border-bottom-right-radius: 4px;

    .message-text {
      line-height: 1.6;
    }

    .message-time {
      font-size: 11px;
      opacity: 0.7;
      margin-top: 8px;
      text-align: right;
    }
  }

  .ai-message {
    background: $bg-card;
    border: 1px solid $border;
    border-bottom-left-radius: 4px;

    .knowledge-sources {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
      padding-bottom: 12px;
      border-bottom: 1px solid $border;

      .source-label {
        font-size: 12px;
        color: $text-muted;
      }

      .source-tag {
        cursor: pointer;

        &:hover {
          color: $primary;
          border-color: $primary;
        }
      }
    }

    .message-text {
      line-height: 1.8;
      font-size: 15px;
      color: $text;

      :deep(strong) {
        color: $text;
        font-weight: 600;
      }

      :deep(h4) {
        margin: 16px 0 8px;
        font-weight: 600;
      }

      :deep(code) {
        background: $bg;
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 13px;
        color: $danger;
      }

      :deep(pre) {
        background: $bg-dark;
        padding: 16px;
        border-radius: 8px;
        margin: 12px 0;
        overflow-x: auto;

        code {
          background: none;
          color: #e2e8f0;
        }
      }
    }

    .related-knowledge {
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid $border;

      .related-label {
        font-size: 12px;
        color: $text-muted;
        display: block;
        margin-bottom: 8px;
      }

      .related-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }

      .related-tag {
        cursor: pointer;

        &:hover {
          transform: scale(1.05);
        }
      }
    }

    .message-actions {
      display: flex;
      justify-content: space-between;
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid $border;

      .message-time {
        font-size: 12px;
        color: $text-muted;
      }

      .action-buttons {
        display: flex;
        gap: 4px;

        .el-button {
          color: $text-muted;

          &:hover {
            color: $primary;
          }

          &.liked {
            color: $warning;
          }
        }
      }
    }
  }

  &.typing .ai-message {
    padding: 18px 24px;
  }
}

.typing-indicator {
  display: flex;
  gap: 6px;

  span {
    width: 8px;
    height: 8px;
    background: $text-muted;
    border-radius: 50%;
    animation: bounce 1.4s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

.input-area {
  background: $bg-card;
  border-top: 1px solid $border;
  padding: 16px 24px 24px;

  .input-wrapper {
    max-width: 900px;
    margin: 0 auto;
  }

  .context-hint {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    background: rgba($primary, 0.08);
    border-radius: $radius;
    margin-bottom: 12px;
    font-size: 13px;
    color: $primary;

    .el-button {
      margin-left: auto;
    }
  }

  .input-container {
    background: $bg;
    border: 1px solid $border;
    border-radius: 24px;
    padding: 12px 16px;
    transition: all 0.2s;

    &:focus-within {
      border-color: $primary;
      box-shadow: 0 0 0 3px rgba($primary, 0.1);
    }
  }

  .input-row {
    display: flex;
    align-items: flex-end;
    gap: 12px;

    .input-tools {
      display: flex;
      gap: 4px;

      .tool-btn {
        border: none;
        background: transparent;
        color: $text-muted;

        &:hover {
          background: rgba($primary, 0.1);
          color: $primary;
        }
      }
    }

    .message-input {
      flex: 1;

      :deep(.el-textarea__inner) {
        border: none;
        background: transparent;
        box-shadow: none;
        padding: 8px 0;
        font-size: 15px;
        resize: none;

        &::placeholder {
          color: $text-muted;
        }
      }
    }

    .send-btn {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      padding: 0;
      background: linear-gradient(135deg, $primary, $primary-dark);
      border: none;

      &:hover:not(:disabled) {
        background: linear-gradient(135deg, $primary-light, $primary);
      }

      &:disabled {
        background: $border;
        opacity: 0.6;
      }
    }
  }

  .input-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;
    padding-top: 8px;
    font-size: 12px;
    color: $text-muted;

    .input-tip {
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }
}

@keyframes pulse {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.02);
  }
}

@keyframes bounce {

  0%,
  60%,
  100% {
    transform: translateY(0);
  }

  30% {
    transform: translateY(-6px);
  }
}

.message-enter-active,
.message-leave-active {
  transition: all 0.3s;
}

.message-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.message-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .session-sidebar {
    position: absolute;
    z-index: 50;
    height: calc(100vh - 64px);

    &.collapsed {
      width: 0;
    }
  }

  .message-content {
    max-width: 85% !important;
  }
}
</style>