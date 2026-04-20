import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useQAStore = defineStore('qa', () => {
  const questions = ref([
    {
      id: 1,
      title: '如何安装 Vue 3？',
      content: 'Vue 3 的安装步骤是什么？',
      author: 'User1',
      answers: 2,
    },
    {
      id: 2,
      title: 'Pinia 如何使用？',
      content: 'Pinia 的基本使用方法',
      author: 'User2',
      answers: 1,
    },
  ])

  const totalQuestions = computed(() => questions.value.length)

  function addQuestion(question) {
    questions.value.push({ id: Date.now(), ...question, answers: 0 })
  }

  function removeQuestion(questionId) {
    questions.value = questions.value.filter((q) => q.id !== questionId)
  }

  function getQuestionById(questionId) {
    return questions.value.find((q) => q.id === questionId)
  }

  function addAnswer(questionId) {
    const question = questions.value.find((q) => q.id === questionId)
    if (question) {
      question.answers++
    }
  }

  return { questions, totalQuestions, addQuestion, removeQuestion, getQuestionById, addAnswer }
})
