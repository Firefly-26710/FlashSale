import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { API_BASE_URL } from './api/config'
import axios from 'axios'
import { createPinia } from "pinia";


// 配置axios默认URL
axios.defaults.baseURL = API_BASE_URL

const app = createApp(App)
const pinia = createPinia();


// 注册所有Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.use(router)
app.use(pinia);
app.mount('#app')