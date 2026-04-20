import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

// 导出所有store
export { useUserStore } from './user'
export { useCourseStore } from './course'
export { useQAStore } from './qa'

export const userLoadingStore = defineStore('loading', () => {
  const loading = ref(false)
  function setLoading(val) {
    loading.value = val
  }
  return { loading, setLoading }
})
