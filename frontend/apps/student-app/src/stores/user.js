import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', () => {
  const user = ref({
    id: null,
    name: '',
    email: '',
    role: 'guest'
  })

  const isLoggedIn = computed(() => user.value.id !== null)

  function setUser(userData) {
    user.value = { ...user.value, ...userData }
  }

  function logout() {
    user.value = {
      id: null,
      name: '',
      email: '',
      role: 'guest'
    }
  }

  return { user, isLoggedIn, setUser, logout }
})
