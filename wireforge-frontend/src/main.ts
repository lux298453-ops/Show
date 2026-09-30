import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// Balsamiq 官方开源手写字体（OFL 1.1），线稿文字与 Balsamiq Wireframes 同款
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import '@fontsource/balsamiq-sans/400.css'
import '@fontsource/balsamiq-sans/700.css'
import App from './App.vue'
import router from './router'
import './styles/main.scss'
import { initTheme } from './utils/theme'
import { initLanguage } from './utils/i18n'

initTheme()
initLanguage()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')