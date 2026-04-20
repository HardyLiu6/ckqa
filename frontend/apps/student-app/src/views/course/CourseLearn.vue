<template>
  <div class="course-learn-page">
    <!-- 顶部导航 -->
    <div class="learn-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon>
            <ArrowLeft />
          </el-icon>
          返回课程
        </el-button>
        <el-divider direction="vertical" />
        <span class="course-name">{{ course.title }}</span>
      </div>
      <div class="header-right">
        <div class="progress-indicator">
          <el-icon>
            <Trophy />
          </el-icon>
          <span>学习进度：{{ learningProgress }}%</span>
          <el-progress :percentage="learningProgress" :stroke-width="6" :show-text="false" color="#667eea" />
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="learn-container">
      <!-- 左侧：视频播放区 -->
      <div class="video-section">
        <div class="video-player">
          <div class="player-placeholder">
            <div class="placeholder-content">
              <el-icon class="play-icon">
                <VideoPlay />
              </el-icon>
              <p>{{ currentLesson?.title || '请选择课时' }}</p>
            </div>
          </div>

          <div class="player-controls">
            <div class="control-left">
              <el-button text circle><el-icon>
                  <VideoPlay />
                </el-icon></el-button>
              <span class="time-display">00:00 / {{ formatTime(currentLesson?.duration * 60 || 0) }}</span>
            </div>
            <div class="control-progress">
              <el-slider v-model="playProgress" :show-tooltip="false" />
            </div>
            <div class="control-right">
              <el-button text circle><el-icon>
                  <Setting />
                </el-icon></el-button>
              <el-button text circle><el-icon>
                  <FullScreen />
                </el-icon></el-button>
            </div>
          </div>
        </div>

        <div class="lesson-info-bar">
          <div class="lesson-title">
            <h3>{{ currentLesson?.title }}</h3>
            <el-tag size="small">{{ currentLesson?.type === 'video' ? '视频' : '文档' }}</el-tag>
          </div>
          <div class="lesson-actions">
            <el-button text @click="showNoteDialog = true">
              <el-icon>
                <EditPen />
              </el-icon>
              记笔记
            </el-button>
            <el-button text @click="markComplete">
              <el-icon>
                <CircleCheck />
              </el-icon>
              标记完成
            </el-button>
          </div>
        </div>

        <!-- Tab区域 -->
        <div class="content-tabs-wrapper">
          <el-tabs v-model="activeTab">
            <!-- AI问答 -->
            <el-tab-pane name="qa">
              <template #label>
                <span class="tab-label"><el-icon>
                    <ChatLineRound />
                  </el-icon>AI智能问答</span>
              </template>

              <div class="qa-container">
                <div class="chat-messages" ref="chatContainer">
                  <!-- 欢迎消息 -->
                  <div class="welcome-message" v-if="chatMessages.length === 0">
                    <div class="welcome-icon">
                      <el-icon>
                        <Cpu />
                      </el-icon>
                    </div>
                    <h4>AI学习助手</h4>
                    <p>您好！我是您的AI学习助手，可以帮您解答关于本课程的任何问题。</p>
                    <div class="quick-questions">
                      <span>快速提问：</span>
                      <el-button v-for="q in quickQuestions" :key="q" size="small" round @click="askQuestion(q)">
                        {{ q }}
                      </el-button>
                    </div>
                  </div>

                  <!-- 消息列表 -->
                  <div v-for="(msg, index) in chatMessages" :key="index" class="message-item" :class="msg.role">
                    <div class="message-avatar">
                      <el-avatar v-if="msg.role === 'user'" :size="36">
                        <el-icon>
                          <User />
                        </el-icon>
                      </el-avatar>
                      <div v-else class="ai-avatar">
                        <el-icon>
                          <Cpu />
                        </el-icon>
                      </div>
                    </div>
                    <div class="message-content">
                      <div class="message-bubble">{{ msg.content }}</div>
                      <span class="message-time">{{ msg.time }}</span>
                    </div>
                  </div>

                  <!-- AI思考中 -->
                  <div v-if="isAIThinking" class="message-item assistant">
                    <div class="message-avatar">
                      <div class="ai-avatar thinking"><el-icon>
                          <Cpu />
                        </el-icon></div>
                    </div>
                    <div class="message-content">
                      <div class="message-bubble thinking">
                        <span class="dot"></span>
                        <span class="dot"></span>
                        <span class="dot"></span>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="chat-input">
                  <el-input v-model="questionInput" type="textarea" :rows="2" placeholder="输入您的问题，AI助手将为您解答..."
                    resize="none" @keyup.enter.ctrl="sendQuestion" />
                  <el-button type="primary" :disabled="!questionInput.trim() || isAIThinking" @click="sendQuestion">
                    <el-icon>
                      <Promotion />
                    </el-icon>
                    发送
                  </el-button>
                </div>
              </div>
            </el-tab-pane>

            <!-- 课程讨论 -->
            <el-tab-pane name="discuss">
              <template #label>
                <span class="tab-label"><el-icon>
                    <Comment />
                  </el-icon>课程讨论</span>
              </template>

              <div class="discuss-container">
                <div class="discuss-list">
                  <div v-for="q in discussions" :key="q.id" class="discuss-item">
                    <div class="discuss-header">
                      <el-avatar :src="q.avatar" :size="32" />
                      <span class="user-name">{{ q.name }}</span>
                      <span class="discuss-time">{{ q.time }}</span>
                      <el-tag v-if="q.isResolved" type="success" size="small">已解决</el-tag>
                    </div>
                    <div class="discuss-content">{{ q.content }}</div>
                    <div v-if="q.reply" class="discuss-reply">
                      <el-icon>
                        <ChatDotRound />
                      </el-icon>
                      <span>{{ q.reply }}</span>
                    </div>
                  </div>
                </div>

                <div class="discuss-input">
                  <el-input v-model="discussInput" type="textarea" :rows="2" placeholder="分享您的问题或心得..." resize="none" />
                  <el-button type="primary" @click="submitDiscuss">发布</el-button>
                </div>
              </div>
            </el-tab-pane>

            <!-- 我的笔记 -->
            <el-tab-pane name="notes">
              <template #label>
                <span class="tab-label"><el-icon>
                    <Notebook />
                  </el-icon>我的笔记</span>
              </template>

              <div class="notes-container">
                <div class="notes-list">
                  <div v-for="note in notes" :key="note.id" class="note-item">
                    <div class="note-time">
                      <el-icon>
                        <VideoPause />
                      </el-icon>
                      {{ formatTime(note.timestamp) }}
                    </div>
                    <div class="note-content">{{ note.content }}</div>
                    <div class="note-meta">{{ note.date }}</div>
                  </div>
                  <el-empty v-if="notes.length === 0" description="暂无笔记" />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <!-- 右侧：课程目录 -->
      <div class="sidebar-section">
        <div class="sidebar-header">
          <h4>课程目录</h4>
          <span class="progress-text">{{ completedCount }}/{{ totalLessons }}课时</span>
        </div>

        <div class="chapters-wrapper">
          <el-collapse v-model="expandedChapters">
            <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="chapter.id">
              <template #title>
                <div class="chapter-title-bar">
                  <span class="chapter-order">第{{ chapter.order }}章</span>
                  <span class="chapter-name">{{ chapter.title }}</span>
                </div>
              </template>

              <div class="sidebar-lessons">
                <div v-for="lesson in chapter.lessons" :key="lesson.id" class="sidebar-lesson-item"
                  :class="{ active: currentLesson?.id === lesson.id, completed: lesson.isCompleted }"
                  @click="selectLesson(lesson)">
                  <div class="lesson-status-icon">
                    <el-icon v-if="lesson.isCompleted" class="completed">
                      <CircleCheckFilled />
                    </el-icon>
                    <el-icon v-else-if="currentLesson?.id === lesson.id" class="playing">
                      <VideoPlay />
                    </el-icon>
                    <span v-else class="lesson-number">{{ lesson.order }}</span>
                  </div>
                  <div class="lesson-text">
                    <span class="lesson-name">{{ lesson.title }}</span>
                    <span class="lesson-duration">{{ lesson.duration }}分钟</span>
                  </div>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </div>

    <!-- 笔记弹窗 -->
    <el-dialog v-model="showNoteDialog" title="添加笔记" width="500px">
      <div class="note-dialog-content">
        <div class="note-timestamp">
          <el-icon>
            <Clock />
          </el-icon>
          <span>当前时间点：{{ formatTime(currentTimestamp) }}</span>
        </div>
        <el-input v-model="noteContent" type="textarea" :rows="4" placeholder="记录您的学习心得..." />
      </div>
      <template #footer>
        <el-button @click="showNoteDialog = false">取消</el-button>
        <el-button type="primary" @click="saveNote">保存笔记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Trophy, VideoPlay, Setting, FullScreen, EditPen, CircleCheck,
  ChatLineRound, Cpu, User, Promotion, Comment, ChatDotRound, Notebook,
  VideoPause, Clock, CircleCheckFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// ========== 模拟课程数据 ==========
const course = ref({
  id: 1,
  title: '深度学习入门：从原理到实践'
})

const chapters = ref([
  {
    id: 1, order: 1, title: '深度学习概述',
    lessons: [
      { id: 1, order: 1, title: '什么是深度学习', type: 'video', duration: 15, isCompleted: true },
      { id: 2, order: 2, title: '深度学习的发展历程', type: 'video', duration: 20, isCompleted: true },
      { id: 3, order: 3, title: '深度学习的应用场景', type: 'video', duration: 18, isCompleted: false },
      { id: 4, order: 4, title: '章节测验', type: 'quiz', duration: 10, isCompleted: false }
    ]
  },
  {
    id: 2, order: 2, title: '神经网络基础',
    lessons: [
      { id: 5, order: 1, title: '感知机模型', type: 'video', duration: 25, isCompleted: false },
      { id: 6, order: 2, title: '多层感知机', type: 'video', duration: 30, isCompleted: false },
      { id: 7, order: 3, title: '激活函数详解', type: 'video', duration: 22, isCompleted: false },
      { id: 8, order: 4, title: '实战：手写数字识别', type: 'video', duration: 35, isCompleted: false }
    ]
  },
  {
    id: 3, order: 3, title: '反向传播算法',
    lessons: [
      { id: 9, order: 1, title: '梯度下降法', type: 'video', duration: 28, isCompleted: false },
      { id: 10, order: 2, title: '反向传播原理', type: 'video', duration: 35, isCompleted: false },
      { id: 11, order: 3, title: '优化器详解', type: 'video', duration: 30, isCompleted: false }
    ]
  }
])

// ========== 模拟讨论数据 ==========
const discussions = ref([
  {
    id: 1,
    name: '学员小明',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user1',
    content: '请问深度学习和机器学习的主要区别是什么？',
    time: '2小时前',
    isResolved: true,
    reply: '深度学习是机器学习的一个子集，主要区别在于深度学习使用多层神经网络自动学习特征，而传统机器学习需要人工设计特征。'
  },
  {
    id: 2,
    name: '学员小红',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user2',
    content: '老师讲的很清楚，受益匪浅！',
    time: '5小时前',
    isResolved: false
  }
])

// ========== 模拟笔记数据 ==========
const notes = ref([
  { id: 1, timestamp: 120, content: '深度学习三要素：数据、算法、算力', date: '2024-03-15 14:35' },
  { id: 2, timestamp: 450, content: '感知机是最简单的神经网络模型', date: '2024-03-15 15:20' }
])

// ========== AI问答模拟回复 ==========
const aiAnswers = {
  '这节课的重点是什么？': '这节课的重点包括：\n1. 深度学习的基本概念和定义\n2. 深度学习与传统机器学习的区别\n3. 深度学习三要素：数据、算法和算力\n4. 神经网络的基本结构和工作原理\n\n建议重点理解神经网络如何通过多层结构逐层提取特征。',
  '请解释一下核心概念': '深度学习的核心概念包括：\n\n1. **神经网络**：由大量人工神经元组成的网络，模拟人脑信息处理方式\n2. **层次结构**：输入层→隐藏层→输出层的多层架构\n3. **激活函数**：为网络引入非线性，常见的有ReLU、Sigmoid等\n4. **反向传播**：通过计算梯度来更新网络参数的核心算法\n\n这些概念构成了深度学习的基础框架。',
  '有什么实践建议？': '学习建议：\n\n1. **编程基础**：先掌握Python和NumPy库\n2. **循序渐进**：从简单的全连接网络开始\n3. **动手实践**：用MNIST数据集做手写数字识别\n4. **理解数学**：理解反向传播的数学原理\n5. **框架学习**：熟悉PyTorch或TensorFlow\n6. **项目驱动**：通过实际项目巩固知识\n\n记住：实践是最好的学习方式！',
  default: '这是一个很好的问题！\n\n根据课程内容，让我来为您详细解答：\n\n首先，我们需要理解相关的基础概念，然后结合实际应用场景来分析。深度学习作为人工智能的重要分支，其核心在于通过多层神经网络自动学习数据的特征表示。\n\n如果您有更具体的问题，欢迎继续提问，我会为您详细解答。'
}

// ========== 状态 ==========
const currentLesson = ref(chapters.value[0].lessons[0])
const expandedChapters = ref([1, 2, 3])
const activeTab = ref('qa')
const playProgress = ref(0)

// AI问答
const chatMessages = ref([])
const questionInput = ref('')
const isAIThinking = ref(false)
const chatContainer = ref(null)
const quickQuestions = ['这节课的重点是什么？', '请解释一下核心概念', '有什么实践建议？']

// 讨论
const discussInput = ref('')

// 笔记
const showNoteDialog = ref(false)
const noteContent = ref('')
const currentTimestamp = ref(0)

// ========== 计算属性 ==========
const totalLessons = computed(() => {
  return chapters.value.reduce((sum, ch) => sum + ch.lessons.length, 0)
})

const completedCount = computed(() => {
  return chapters.value.reduce((sum, ch) => {
    return sum + ch.lessons.filter(l => l.isCompleted).length
  }, 0)
})

const learningProgress = computed(() => {
  if (totalLessons.value === 0) return 0
  return Math.round((completedCount.value / totalLessons.value) * 100)
})

// ========== 方法 ==========
const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const goBack = () => {
  router.push(`/course/detail/${route.params.id}`)
}

const selectLesson = (lesson) => {
  currentLesson.value = lesson
  playProgress.value = 0
}

const markComplete = () => {
  if (currentLesson.value) {
    currentLesson.value.isCompleted = true
    ElMessage.success('已标记为完成')
  }
}

// AI问答
const sendQuestion = async () => {
  const question = questionInput.value.trim()
  if (!question) return

  chatMessages.value.push({
    role: 'user',
    content: question,
    time: new Date().toLocaleTimeString()
  })
  questionInput.value = ''

  await nextTick()
  scrollToBottom()

  isAIThinking.value = true

  // 模拟AI响应延迟
  setTimeout(() => {
    const answer = aiAnswers[question] || aiAnswers.default
    chatMessages.value.push({
      role: 'assistant',
      content: answer,
      time: new Date().toLocaleTimeString()
    })
    isAIThinking.value = false
    nextTick(() => scrollToBottom())
  }, 1500)
}

const askQuestion = (question) => {
  questionInput.value = question
  sendQuestion()
}

const scrollToBottom = () => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 讨论
const submitDiscuss = () => {
  if (!discussInput.value.trim()) return
  discussions.value.unshift({
    id: Date.now(),
    name: '我',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=me',
    content: discussInput.value,
    time: '刚刚',
    isResolved: false
  })
  discussInput.value = ''
  ElMessage.success('发布成功')
}

// 笔记
const saveNote = () => {
  if (!noteContent.value.trim()) return
  notes.value.unshift({
    id: Date.now(),
    timestamp: currentTimestamp.value,
    content: noteContent.value,
    date: new Date().toLocaleString()
  })
  noteContent.value = ''
  showNoteDialog.value = false
  ElMessage.success('笔记保存成功')
}
</script>

<style lang="scss" scoped>
.course-learn-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #fff;
}

// 顶部导航
.learn-header {
  height: 60px;
  background: #16162a;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .el-button {
      color: rgba(255, 255, 255, 0.8);
    }

    .el-divider {
      border-color: rgba(255, 255, 255, 0.2);
    }

    .course-name {
      font-size: 15px;
      color: rgba(255, 255, 255, 0.9);
    }
  }

  .header-right .progress-indicator {
    display: flex;
    align-items: center;
    gap: 12px;

    .el-icon {
      color: #f7ba2a;
    }

    span {
      font-size: 13px;
      color: rgba(255, 255, 255, 0.8);
    }

    :deep(.el-progress) {
      width: 120px;

      .el-progress-bar__outer {
        background: rgba(255, 255, 255, 0.1);
      }
    }
  }
}

// 主容器
.learn-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

// 视频区域
.video-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.video-player {
  position: relative;
  background: #000;
  aspect-ratio: 16 / 9;
  max-height: 55vh;

  .player-placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #1a1a2e 0%, #2d2d44 100%);

    .placeholder-content {
      text-align: center;

      .play-icon {
        font-size: 80px;
        color: rgba(255, 255, 255, 0.3);
        margin-bottom: 16px;
      }

      p {
        font-size: 16px;
        color: rgba(255, 255, 255, 0.6);
        margin: 0;
      }
    }
  }

  .player-controls {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 50px;
    background: linear-gradient(transparent, rgba(0, 0, 0, 0.8));
    display: flex;
    align-items: center;
    padding: 0 16px;
    gap: 16px;

    .control-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .time-display {
        font-size: 13px;
        color: rgba(255, 255, 255, 0.8);
      }
    }

    .control-progress {
      flex: 1;

      :deep(.el-slider__runway) {
        background: rgba(255, 255, 255, 0.2);
      }

      :deep(.el-slider__bar) {
        background: #667eea;
      }

      :deep(.el-slider__button) {
        width: 14px;
        height: 14px;
        border-color: #667eea;
      }
    }

    .control-right {
      display: flex;
      gap: 8px;
    }

    .el-button {
      color: rgba(255, 255, 255, 0.8);
    }
  }
}

.lesson-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #16162a;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .lesson-title {
    display: flex;
    align-items: center;
    gap: 12px;

    h3 {
      font-size: 16px;
      font-weight: 500;
      margin: 0;
    }
  }

  .lesson-actions {
    display: flex;
    gap: 8px;

    .el-button {
      color: rgba(255, 255, 255, 0.7);
    }
  }
}

// Tab区域
.content-tabs-wrapper {
  flex: 1;
  overflow: hidden;
  background: #1e1e32;

  :deep(.el-tabs) {
    height: 100%;
    display: flex;
    flex-direction: column;

    .el-tabs__header {
      margin: 0;
      background: #16162a;
      padding: 0 24px;

      .el-tabs__nav-wrap::after {
        display: none;
      }

      .el-tabs__item {
        color: rgba(255, 255, 255, 0.6);
        height: 50px;
        line-height: 50px;

        &.is-active {
          color: #667eea;
        }
      }

      .el-tabs__active-bar {
        background: #667eea;
      }
    }

    .el-tabs__content {
      flex: 1;
      overflow: hidden;

      .el-tab-pane {
        height: 100%;
      }
    }
  }

  .tab-label {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

// AI问答区域
.qa-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;

  .welcome-message {
    text-align: center;
    padding: 40px 20px;

    .welcome-icon {
      width: 80px;
      height: 80px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 20px;

      .el-icon {
        font-size: 40px;
        color: #fff;
      }
    }

    h4 {
      font-size: 20px;
      font-weight: 600;
      margin: 0 0 12px;
    }

    p {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.6);
      margin: 0 0 24px;
    }

    .quick-questions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 8px;

      span {
        font-size: 13px;
        color: rgba(255, 255, 255, 0.5);
      }

      .el-button {
        background: rgba(102, 126, 234, 0.2);
        border: 1px solid rgba(102, 126, 234, 0.4);
        color: #667eea;

        &:hover {
          background: rgba(102, 126, 234, 0.3);
        }
      }
    }
  }
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;
    }

    .message-bubble {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: #fff;
    }
  }

  &.assistant .message-bubble {
    background: rgba(255, 255, 255, 0.1);
    color: rgba(255, 255, 255, 0.9);
  }

  .message-avatar {
    flex-shrink: 0;

    .ai-avatar {
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;

      .el-icon {
        font-size: 20px;
        color: #fff;
      }

      &.thinking {
        animation: pulse 1.5s infinite;
      }
    }
  }

  .message-content {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-width: 70%;

    .message-bubble {
      padding: 14px 18px;
      border-radius: 16px;
      font-size: 14px;
      line-height: 1.7;
      white-space: pre-wrap;

      &.thinking {
        display: flex;
        gap: 4px;
        padding: 16px 20px;

        .dot {
          width: 8px;
          height: 8px;
          background: rgba(255, 255, 255, 0.6);
          border-radius: 50%;
          animation: bounce 1.4s infinite ease-in-out;

          &:nth-child(1) {
            animation-delay: -0.32s;
          }

          &:nth-child(2) {
            animation-delay: -0.16s;
          }
        }
      }
    }

    .message-time {
      font-size: 11px;
      color: rgba(255, 255, 255, 0.4);
    }
  }
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.6;
  }
}

@keyframes bounce {

  0%,
  80%,
  100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}

.chat-input {
  padding: 16px 24px;
  background: #16162a;
  display: flex;
  gap: 12px;

  :deep(.el-textarea__inner) {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    color: #fff;
    resize: none;

    &::placeholder {
      color: rgba(255, 255, 255, 0.4);
    }

    &:focus {
      border-color: #667eea;
    }
  }

  .el-button {
    align-self: flex-end;
  }
}

// 讨论区域
.discuss-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.discuss-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.discuss-item {
  padding: 18px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  margin-bottom: 16px;

  .discuss-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;

    .user-name {
      font-size: 14px;
      font-weight: 500;
    }

    .discuss-time {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.4);
      margin-left: auto;
    }
  }

  .discuss-content {
    font-size: 14px;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.85);
  }

  .discuss-reply {
    margin-top: 14px;
    padding: 14px;
    background: rgba(102, 126, 234, 0.1);
    border-radius: 10px;
    display: flex;
    gap: 10px;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.8);

    .el-icon {
      color: #667eea;
      flex-shrink: 0;
      margin-top: 3px;
    }
  }
}

.discuss-input {
  padding: 16px 24px;
  background: #16162a;
  display: flex;
  gap: 12px;

  :deep(.el-textarea__inner) {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    color: #fff;

    &::placeholder {
      color: rgba(255, 255, 255, 0.4);
    }
  }

  .el-button {
    align-self: flex-end;
  }
}

// 笔记区域
.notes-container {
  height: 100%;
  overflow-y: auto;
  padding: 20px 24px;
}

.note-item {
  padding: 18px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  margin-bottom: 14px;
  border-left: 4px solid #667eea;

  .note-time {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #667eea;
    margin-bottom: 10px;
  }

  .note-content {
    font-size: 14px;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.85);
  }

  .note-meta {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.4);
    margin-top: 10px;
  }
}

// 右侧边栏
.sidebar-section {
  width: 320px;
  background: #16162a;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;

  h4 {
    font-size: 16px;
    font-weight: 600;
    margin: 0;
  }

  .progress-text {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.6);
  }
}

.chapters-wrapper {
  flex: 1;
  overflow-y: auto;

  :deep(.el-collapse) {
    border: none;

    .el-collapse-item__header {
      background: transparent;
      border: none;
      color: rgba(255, 255, 255, 0.9);
      padding: 16px 20px;
      height: auto;

      &:hover {
        background: rgba(255, 255, 255, 0.05);
      }
    }

    .el-collapse-item__wrap {
      background: transparent;
      border: none;
    }

    .el-collapse-item__content {
      padding: 0;
    }

    .el-collapse-item__arrow {
      color: rgba(255, 255, 255, 0.5);
    }
  }
}

.chapter-title-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .chapter-order {
    font-size: 12px;
    color: #667eea;
  }

  .chapter-name {
    font-size: 14px;
    font-weight: 500;
  }
}

.sidebar-lessons {
  padding: 0 12px 12px;
}

.sidebar-lesson-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.05);
  }

  &.active {
    background: rgba(102, 126, 234, 0.2);

    .lesson-name {
      color: #667eea;
    }
  }

  &.completed .lesson-name {
    color: rgba(255, 255, 255, 0.5);
  }

  .lesson-status-icon {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .lesson-number {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.4);
    }

    .completed {
      color: #67c23a;
      font-size: 18px;
    }

    .playing {
      color: #667eea;
      font-size: 18px;
    }
  }

  .lesson-text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;

    .lesson-name {
      font-size: 13px;
      color: rgba(255, 255, 255, 0.85);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .lesson-duration {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.4);
    }
  }
}

// 笔记弹窗
.note-dialog-content {
  .note-timestamp {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 8px;
    margin-bottom: 16px;
    font-size: 14px;
    color: #606266;

    .el-icon {
      color: #667eea;
    }
  }
}

// 响应式
@media (max-width: 1024px) {
  .sidebar-section {
    display: none;
  }
}
</style>