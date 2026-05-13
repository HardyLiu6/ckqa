import { createApp } from 'vue'
import './styles/index.scss'
// 函数式 API 组件（ElMessageBox / ElMessage / ElNotification 等）的样式
// 不会被 unplugin-vue-components 的 ElementPlusResolver 自动注入，
// 必须手动导入，否则弹窗会缺失 overlay / 居中定位等关键样式。
import 'element-plus/es/components/overlay/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
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
