import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'

import './styles/base.css'
import './styles/transitions.css'
import './styles/markdown.css'

const app = createApp(App)
const pinia = createPinia()

// 顺序有讲究:pinia 必须先装,后面才能在组件之外拿到 store
app.use(pinia)
app.use(router)

/**
 * 401 的接线。
 *
 * http.ts 是最底层模块,让它反向 import router / store 会形成循环依赖,
 * 模块初始化顺序会变得不可预测。所以由这里显式把回调注册进去。
 *
 * 注意 store 要显式传入 pinia 实例 —— 这段代码跑在组件之外,
 * useAuthStore() 无法从组件上下文里自动找到 pinia。
 */
const auth = useAuthStore(pinia)
const ui = useUiStore(pinia)

setUnauthorizedHandler(() => {
  auth.clear()
  const current = router.currentRoute.value
  if (current.name === 'login') {
    // 已经在登录页了,不用再跳,否则会把自己的 redirect 参数覆盖掉
    return
  }
  ui.push('登录状态已过期,请重新登录', 'info')
  void router.push({ name: 'login', query: { redirect: current.fullPath } })
})

app.mount('#app')
