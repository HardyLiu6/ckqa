import { createApp } from 'vue'
import './styles/index.scss'
import App from './App.vue'
import router from './router/index.js'
import { getAdminPinia } from './stores/pinia.js'
import { useThemeStore } from './stores/theme.js'

const app = createApp(App)
const pinia = getAdminPinia()

app.use(pinia)
const themeStore = useThemeStore(pinia)
themeStore.init()
app.use(router)
app.mount('#app')
