import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useCourseStore = defineStore('course', () => {
  // ========== 全部课程数据 ==========
  const allCourses = ref([
    {
      id: 1,
      title: '深度学习入门：从原理到实践',
      cover: 'https://picsum.photos/seed/dl1/400/225',
      description:
        '本课程从零开始讲解深度学习的核心概念，包括神经网络基础、反向传播算法、常见网络架构等。',
      teacher: {
        id: 1,
        name: '张教授',
        avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1',
        title: '人工智能专家',
      },
      category: '人工智能',
      tags: ['深度学习', '神经网络', 'Python', 'TensorFlow'],
      lessonCount: 48,
      studentCount: 12580,
      rating: 4.9,
      price: 0,
    },
    {
      id: 2,
      title: 'Vue3.0 + Pinia 企业级实战开发',
      cover: 'https://picsum.photos/seed/vue3/400/225',
      description: '从Vue3新特性到Pinia状态管理，手把手带你构建企业级中后台管理系统。',
      teacher: {
        id: 2,
        name: '李老师',
        avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher2',
        title: '前端架构师',
      },
      category: '前端开发',
      tags: ['Vue3', 'Pinia', 'Vite', 'Element Plus'],
      lessonCount: 36,
      studentCount: 8920,
      rating: 4.8,
      price: 199,
    },
    {
      id: 3,
      title: '自然语言处理(NLP)完全指南',
      cover: 'https://picsum.photos/seed/nlp1/400/225',
      description: '系统学习NLP核心技术，从词向量、序列模型到Transformer和大语言模型。',
      teacher: {
        id: 3,
        name: '王博士',
        avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3',
        title: '机器学习专家',
      },
      category: '人工智能',
      tags: ['NLP', 'Transformer', 'BERT', 'GPT'],
      lessonCount: 56,
      studentCount: 6750,
      rating: 4.9,
      price: 299,
    },
  ])

  // ========== 我的课程数据 ==========
  const myCourses = ref([
    { id: 1, progress: 75, lastLearnAt: '今天 14:30' },
    { id: 2, progress: 100, lastLearnAt: '昨天' },
    { id: 3, progress: 30, lastLearnAt: '3天前' },
  ])

  // ========== 计算属性 ==========
  const enrolledCourseIds = computed(() => myCourses.value.map((c) => c.id))

  const myCoursesWithDetail = computed(() => {
    return myCourses.value.map((mc) => {
      const course = allCourses.value.find((c) => c.id === mc.id)
      return { ...course, ...mc }
    })
  })

  // ========== 方法 ==========
  const getCourseById = (id) => {
    return allCourses.value.find((c) => c.id === Number(id))
  }

  const isEnrolled = (courseId) => {
    return enrolledCourseIds.value.includes(Number(courseId))
  }

  const enrollCourse = (courseId) => {
    if (!isEnrolled(courseId)) {
      myCourses.value.push({
        id: Number(courseId),
        progress: 0,
        lastLearnAt: '刚刚',
      })
    }
  }

  const updateProgress = (courseId, progress) => {
    const course = myCourses.value.find((c) => c.id === Number(courseId))
    if (course) {
      course.progress = progress
      course.lastLearnAt = '刚刚'
    }
  }

  return {
    allCourses,
    myCourses,
    enrolledCourseIds,
    myCoursesWithDetail,
    getCourseById,
    isEnrolled,
    enrollCourse,
    updateProgress,
  }
})
