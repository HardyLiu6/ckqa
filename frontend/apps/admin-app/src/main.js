import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router/index.js'
import { themeStore } from './stores/theme.js'

themeStore.initTheme()
createApp(App).use(router).mount('#app')
