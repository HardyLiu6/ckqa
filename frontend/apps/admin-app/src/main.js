import { createApp } from 'vue'
import './styles/index.scss'
import App from './App.vue'
import router from './router/index.js'
import { getAdminPinia } from './stores/pinia.js'
import { useThemeStore } from './stores/theme.js'
import { useScopeStore } from './stores/scope.js'

const app = createApp(App)
const pinia = getAdminPinia()

app.use(pinia)
const themeStore = useThemeStore(pinia)
themeStore.init()
const scopeStore = useScopeStore(pinia)
scopeStore.load()
app.use(router)
app.mount('#app')
