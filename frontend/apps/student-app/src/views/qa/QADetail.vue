<template>
  <div class="qa-detail-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
          <div class="header-title">
            <h1>{{ session.title }}</h1>
            <div class="header-meta">
              <el-tag v-if="session.courseName" size="small" type="primary" effect="plain">{{ session.courseName
                }}</el-tag>
              <span class="meta-text"><el-icon>
                  <Clock />
                </el-icon>{{ session.createdAt }}</span>
              <span class="meta-text"><el-icon>
                  <Comment />
                </el-icon>{{ messages.length }} 条消息</span>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <el-button :icon="session.isFavorite ? StarFilled : Star" :type="session.isFavorite ? 'warning' : 'default'"
            @click="toggleFavorite">
            {{ session.isFavorite ? '已收藏' : '收藏' }}
          </el-button>
          <el-dropdown trigger="click">
            <el-button :icon="More" circle class="more-btn" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="exportSession"><el-icon>
                    <Download />
                  </el-icon>导出对话</el-dropdown-item>
                <el-dropdown-item @click="copyAll"><el-icon>
                    <CopyDocument />
                  </el-icon>复制全部</el-dropdown-item>
                <el-dropdown-item @click="deleteSession" divided><el-icon>
                    <Delete />
                  </el-icon>删除对话</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 对话内容 -->
    <main class="detail-main">
      <div class="messages-wrapper" ref="messagesWrapper">
        <div class="messages-container">
          <!-- 对话开始提示 -->
          <div class="session-start">
            <div class="start-icon"><el-icon>
                <ChatDotRound />
              </el-icon></div>
            <span>对话开始于 {{ session.createdAt }}</span>
          </div>

          <!-- 消息列表 -->
          <div v-for="(msg, index) in messages" :key="msg.id" class="message-item" :class="msg.role">
            <!-- 时间分割线 -->
            <div v-if="showTimeDivider(index)" class="time-divider">
              <span>{{ formatDate(msg.timestamp) }}</span>
            </div>

            <!-- 用户消息 -->
            <template v-if="msg.role === 'user'">
              <div class="message-content user-message">
                <div class="message-text">{{ msg.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                </div>
              </div>
              <el-avatar :size="40" class="message-avatar">U</el-avatar>
            </template>

            <!-- AI消息 -->
            <template v-else>
              <div class="ai-avatar"><el-icon :size="24">
                  <ChatDotRound />
                </el-icon></div>
              <div class="message-content ai-message">
                <!-- 知识来源 -->
                <div v-if="msg.sources?.length" class="knowledge-sources">
                  <span class="source-label">知识来源：</span>
                  <el-tag v-for="s in msg.sources" :key="s.id" size="small" effect="plain" class="source-tag">{{ s.name
                    }}</el-tag>
                </div>

                <div class="message-text" v-html="renderMarkdown(msg.content)"></div>

                <!-- 相关知识点 -->
                <div v-if="msg.relatedKnowledge?.length" class="related-knowledge">
                  <span class="related-label">相关知识点：</span>
                  <div class="related-tags">
                    <el-tag v-for="k in msg.relatedKnowledge" :key="k.id" :type="k.type || 'info'" size="small"
                      class="related-tag">{{ k.name }}</el-tag>
                  </div>
                </div>

                <div class="message-footer">
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                  <div class="message-actions">
                    <el-button :icon="CopyDocument" text size="small" @click="copyMessage(msg)">复制</el-button>
                    <el-button :icon="msg.liked ? StarFilled : Star" text size="small" :class="{ liked: msg.liked }"
                      @click="toggleLike(msg)">
                      {{ msg.liked ? '已赞' : '有帮助' }}
                    </el-button>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- AI正在输入 -->
          <div v-if="isTyping" class="message-item assistant">
            <div class="ai-avatar"><el-icon :size="24">
                <ChatDotRound />
              </el-icon></div>
            <div class="message-content ai-message">
              <div class="typing-indicator"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部输入区 -->
    <footer class="detail-footer">
      <div class="footer-content">
        <div class="input-container">
          <el-input v-model="inputText" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="继续追问... (Ctrl+Enter 发送)" class="message-input" @keydown="handleKeydown" />
          <el-button type="primary" class="send-btn" :disabled="!inputText.trim()" :loading="isSending"
            @click="sendMessage">
            <el-icon>
              <Promotion />
            </el-icon>
            发送
          </el-button>
        </div>
        <div class="input-hint">
          <el-icon>
            <InfoFilled />
          </el-icon>
          <span>AI会基于上方对话上下文进行回答</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Clock, Comment, Star, StarFilled, More, Download, CopyDocument, Delete, ChatDotRound, Promotion, InfoFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const messagesWrapper = ref(null)

// 模拟会话数据
const sessionId = computed(() => route.params.id)

const session = ref({
  id: 'session-1',
  title: '二叉树遍历算法讨论',
  courseId: 1,
  courseName: '数据结构与算法',
  createdAt: '2024-03-15 09:30',
  isFavorite: true
})

// 模拟消息数据
const messages = ref([
  {
    id: 1,
    role: 'user',
    content: '什么是二叉树？它有哪些常见的遍历方式？',
    timestamp: new Date('2024-03-15 09:30:00')
  },
  {
    id: 2,
    role: 'assistant',
    content: `**二叉树**是一种重要的数据结构，它的特点是每个节点最多有两个子节点，分别称为左子节点和右子节点。

### 二叉树的遍历方式

二叉树有四种主要的遍历方式：

1. **前序遍历（Pre-order）**
   - 顺序：根节点 → 左子树 → 右子树
   - 应用：复制树结构、表达式的前缀表示

2. **中序遍历（In-order）**
   - 顺序：左子树 → 根节点 → 右子树
   - 应用：二叉搜索树的有序输出

3. **后序遍历（Post-order）**
   - 顺序：左子树 → 右子树 → 根节点
   - 应用：删除树、表达式的后缀表示

4. **层序遍历（Level-order）**
   - 按层从上到下、从左到右访问
   - 应用：寻找最短路径、按层打印

### 代码示例

\`\`\`python
def inorder_traversal(root):
    if root:
        inorder_traversal(root.left)
        print(root.val)
        inorder_traversal(root.right)
\`\`\`

如果你想深入了解某种遍历方式的实现细节，可以继续追问！`,
    timestamp: new Date('2024-03-15 09:30:30'),
    sources: [{ id: 1, name: '数据结构讲义-第5章' }],
    relatedKnowledge: [
      { id: 1, name: '二叉树', type: '' },
      { id: 2, name: '树的遍历', type: 'success' },
      { id: 3, name: '递归', type: 'info' }
    ],
    liked: true
  },
  {
    id: 3,
    role: 'user',
    content: '那二叉搜索树和普通二叉树有什么区别呢？',
    timestamp: new Date('2024-03-15 09:35:00')
  },
  {
    id: 4,
    role: 'assistant',
    content: `好问题！**二叉搜索树（BST, Binary Search Tree）**是一种特殊的二叉树，它满足以下性质：

### 核心特性

- **左子树**所有节点的值 < **根节点**的值
- **右子树**所有节点的值 > **根节点**的值
- 左右子树也分别是二叉搜索树

### 与普通二叉树的区别

| 特性 | 普通二叉树 | 二叉搜索树 |
|------|-----------|-----------|
| 节点排列 | 无特殊要求 | 有序排列 |
| 查找效率 | O(n) | O(log n) 平均 |
| 中序遍历 | 无特殊结果 | 有序序列 |

### 优势

1. **高效查找**：平均时间复杂度 O(log n)
2. **有序输出**：中序遍历直接得到有序序列
3. **范围查询**：支持快速范围查找

### 注意事项

当BST退化成链表时（如插入有序数据），时间复杂度会退化到O(n)。这就是为什么需要**平衡二叉树**（如AVL树、红黑树）的原因。

想了解如何在BST中插入、删除节点吗？`,
    timestamp: new Date('2024-03-15 09:35:45'),
    sources: [{ id: 1, name: '数据结构讲义-第5章' }],
    relatedKnowledge: [
      { id: 4, name: '二叉搜索树', type: '' },
      { id: 5, name: 'AVL树', type: 'warning' },
      { id: 6, name: '红黑树', type: 'danger' }
    ],
    liked: false
  },
  {
    id: 5,
    role: 'user',
    content: '红黑树是什么？为什么很多地方都用它？',
    timestamp: new Date('2024-03-15 09:40:00')
  },
  {
    id: 6,
    role: 'assistant',
    content: `基于我们前面对BST的讨论，来介绍一下**红黑树**：

### 什么是红黑树

红黑树是一种**自平衡二叉搜索树**，通过对节点着色和旋转操作来保持平衡。

### 五大性质

1. 每个节点是**红色**或**黑色**
2. 根节点是**黑色**
3. 叶子节点（NIL）是**黑色**
4. 红色节点的子节点必须是**黑色**（不能有连续红节点）
5. 从任一节点到其叶子的所有路径包含**相同数量**的黑色节点

### 为什么广泛使用

| 场景 | 应用 |
|------|------|
| Java | TreeMap, TreeSet |
| C++ STL | map, set |
| Linux | 进程调度CFS |
| 数据库 | 索引结构 |

### 优势

- **平衡保证**：最长路径不超过最短路径的2倍
- **高效操作**：插入、删除、查找都是O(log n)
- **旋转次数少**：比AVL树的旋转次数更少

### 与AVL树对比

- AVL树更严格平衡，查找更快
- 红黑树插入删除更快（旋转少）
- 工程实践中红黑树更常用

还有什么想了解的吗？`,
    timestamp: new Date('2024-03-15 09:41:00'),
    sources: [{ id: 1, name: '数据结构讲义-高级树结构' }],
    relatedKnowledge: [
      { id: 6, name: '红黑树', type: 'danger' },
      { id: 5, name: 'AVL树', type: 'warning' },
      { id: 7, name: '树的旋转', type: 'info' }
    ],
    liked: false
  }
])

// 状态
const inputText = ref('')
const isTyping = ref(false)
const isSending = ref(false)

// AI回复
const followUpResponses = [
  '继续我们关于树结构的讨论，',
  '基于上面的内容，',
  '好问题！结合之前的知识，',
  '这与我们讨论的内容密切相关，'
]

// 方法
const goBack = () => router.back()

const toggleFavorite = () => {
  session.value.isFavorite = !session.value.isFavorite
  ElMessage.success(session.value.isFavorite ? '已收藏' : '已取消收藏')
}

const showTimeDivider = (index) => {
  if (index === 0) return true
  const current = messages.value[index]
  const prev = messages.value[index - 1]
  const diff = new Date(current.timestamp) - new Date(prev.timestamp)
  return diff > 30 * 60 * 1000 // 30分钟
}

const formatDate = (timestamp) => {
  const date = new Date(timestamp)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) return '今天'
  return date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
}

const formatTime = (timestamp) => new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const renderMarkdown = (content) => {
  if (!content) return ''
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`{3}(\w+)?\n([\s\S]*?)`{3}/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/### (.*)/g, '<h4>$1</h4>')
    .replace(/## (.*)/g, '<h3>$1</h3>')
    .replace(/\|(.*)\|/g, (match) => `<div class="table-row">${match}</div>`)
    .replace(/\n/g, '<br>')
}

const copyMessage = async (msg) => {
  try {
    await navigator.clipboard.writeText(msg.content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

const copyAll = async () => {
  const content = messages.value.map(m => `【${m.role === 'user' ? '我' : 'AI'}】${formatTime(m.timestamp)}\n${m.content}`).join('\n\n---\n\n')
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制全部对话')
  } catch {
    ElMessage.error('复制失败')
  }
}

const toggleLike = (msg) => {
  msg.liked = !msg.liked
  ElMessage.success(msg.liked ? '感谢反馈！' : '已取消')
}

const exportSession = () => {
  const content = `【${session.value.title}】\n课程：${session.value.courseName}\n时间：${session.value.createdAt}\n\n` +
    messages.value.map(m => `【${m.role === 'user' ? '我' : 'AI'}】${formatTime(m.timestamp)}\n${m.content}`).join('\n\n---\n\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `${session.value.title}.txt`; a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

const deleteSession = async () => {
  try {
    await ElMessageBox.confirm('确定删除此对话？删除后无法恢复。', '删除确认', { type: 'warning' })
    ElMessage.success('已删除')
    router.push('/qa/history')
  } catch { }
}

const handleKeydown = (e) => {
  if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); sendMessage() }
}

const sendMessage = async () => {
  if (!inputText.value.trim() || isSending.value) return

  const content = inputText.value.trim()
  messages.value.push({ id: Date.now(), role: 'user', content, timestamp: new Date() })
  inputText.value = ''

  await nextTick()
  scrollToBottom()

  isSending.value = true
  isTyping.value = true

  await new Promise(r => setTimeout(r, 1500))

  const prefix = followUpResponses[Math.floor(Math.random() * followUpResponses.length)]
  messages.value.push({
    id: Date.now() + 1,
    role: 'assistant',
    content: `${prefix}让我来回答这个问题。\n\n这是一个很好的延续性问题。根据我们之前讨论的内容，主要有以下几点：\n\n1. **概念理解**：首先要明确基本定义\n2. **核心特性**：把握关键属性\n3. **实际应用**：了解使用场景\n\n如果你还有其他问题，可以继续追问！`,
    timestamp: new Date(),
    sources: [{ id: 1, name: '知识图谱' }],
    relatedKnowledge: [],
    liked: false
  })

  isSending.value = false
  isTyping.value = false
  await nextTick()
  scrollToBottom()
}

const scrollToBottom = () => {
  if (messagesWrapper.value) {
    messagesWrapper.value.scrollTop = messagesWrapper.value.scrollHeight
  }
}

onMounted(() => {
  // 根据sessionId加载对应数据
  nextTick(scrollToBottom)
})
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

.qa-detail-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $bg;
}

.page-header {
  background: $bg-card;
  border-bottom: 1px solid $border;
  padding: 0 24px;
  flex-shrink: 0;
  z-index: 100;

  .header-content {
    max-width: 1000px;
    margin: 0 auto;
    min-height: 72px;
    padding: 16px 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;

    .back-btn {
      border: none;
      background: $bg;
      flex-shrink: 0;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }

    .header-title {
      min-width: 0;

      h1 {
        font-size: 18px;
        font-weight: 600;
        margin: 0 0 8px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .header-meta {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;

        .meta-text {
          display: flex;
          align-items: center;
          gap: 4px;
          font-size: 13px;
          color: $text-muted;
        }
      }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;

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

.detail-main {
  flex: 1;
  overflow: hidden;
}

.messages-wrapper {
  height: 100%;
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

.messages-container {
  max-width: 900px;
  margin: 0 auto;
}

.session-start {
  text-align: center;
  padding: 24px;
  margin-bottom: 24px;

  .start-icon {
    width: 60px;
    height: 60px;
    background: linear-gradient(135deg, $primary, $primary-light);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 12px;
    color: white;
    font-size: 28px;
  }

  span {
    font-size: 13px;
    color: $text-muted;
  }
}

.time-divider {
  text-align: center;
  margin: 32px 0;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    border-top: 1px solid $border;
  }

  span {
    position: relative;
    background: $bg;
    padding: 0 16px;
    font-size: 12px;
    color: $text-muted;
  }
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
    max-width: 75%;
    padding: 16px 20px;
    border-radius: 16px;
  }

  .user-message {
    background: linear-gradient(135deg, $primary, $primary-dark);
    color: white;
    border-bottom-right-radius: 4px;

    .message-text {
      line-height: 1.7;
      font-size: 15px;
    }

    .message-footer {
      margin-top: 10px;
      text-align: right;

      .message-time {
        font-size: 11px;
        opacity: 0.7;
      }
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
      margin-bottom: 14px;
      padding-bottom: 14px;
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

      :deep(h3),
      :deep(h4) {
        margin: 18px 0 10px;
        font-weight: 600;
        color: $text;
      }

      :deep(code) {
        background: rgba($primary, 0.08);
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 13px;
        color: $danger;
        font-family: 'Fira Code', monospace;
      }

      :deep(pre) {
        background: $bg-dark;
        padding: 16px;
        border-radius: 8px;
        margin: 14px 0;
        overflow-x: auto;

        code {
          background: none;
          color: #e2e8f0;
        }
      }
    }

    .related-knowledge {
      margin-top: 16px;
      padding-top: 14px;
      border-top: 1px solid $border;

      .related-label {
        font-size: 12px;
        color: $text-muted;
        display: block;
        margin-bottom: 10px;
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

    .message-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid $border;

      .message-time {
        font-size: 12px;
        color: $text-muted;
      }

      .message-actions {
        display: flex;
        gap: 8px;

        .el-button {
          color: $text-muted;
          font-size: 12px;

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

.detail-footer {
  background: $bg-card;
  border-top: 1px solid $border;
  padding: 20px 24px;
  flex-shrink: 0;

  .footer-content {
    max-width: 900px;
    margin: 0 auto;
  }

  .input-container {
    display: flex;
    gap: 12px;
    align-items: flex-end;

    .message-input {
      flex: 1;

      :deep(.el-textarea__inner) {
        border-radius: $radius;
        padding: 14px 16px;
        font-size: 15px;
        resize: none;

        &:focus {
          border-color: $primary;
          box-shadow: 0 0 0 3px rgba($primary, 0.1);
        }
      }
    }

    .send-btn {
      height: 48px;
      padding: 0 24px;
      border-radius: $radius;
    }
  }

  .input-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
    font-size: 12px;
    color: $text-muted;
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

@media (max-width: 768px) {
  .page-header .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-header .header-actions {
    align-self: flex-end;
  }

  .message-content {
    max-width: 85% !important;
  }
}
</style>