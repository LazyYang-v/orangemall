import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useUserStore } from './stores/user'
import { useCartStore } from './stores/cart'
import { toast } from './utils/toast'
import './styles/base.css'

const app = createApp(App)
app.use(createPinia())

const user = useUserStore()
setUnauthorizedHandler(() => {
  if (!user.isLogin) return
  user.clear()
  toast.warning('登录已过期，请重新登录')
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
})

user.fetchProfile().finally(() => {
  useCartStore().load()
  app.use(router)
  app.mount('#app')
})
