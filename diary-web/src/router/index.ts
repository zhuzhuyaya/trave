import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录;未登录会被引导到登录页并记住来路 */
    requiresAuth?: boolean
  }
}

/**
 * 路由表。前五条是顶部导航栏的五个目的地,顺序与 TopNav 从左到右一致 ——
 * 写在同一个文件里,路由表与导航栏的高度一致不需要靠人工比对。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/timeline',
  },
  {
    path: '/timeline',
    name: 'timeline',
    component: () => import('@/views/TimelineView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/trips',
    name: 'trips',
    component: () => import('@/views/TripListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/map',
    name: 'map',
    component: () => import('@/views/MapView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/memory',
    name: 'memory',
    component: () => import('@/views/MemoryView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },

  /* ---- 二级页面:由页面标题区的「← 返回」回到上一级 ---- */
  {
    path: '/entries/new',
    name: 'entry-new',
    component: () => import('@/views/EntryEditorView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/entries/:id(\\d+)',
    name: 'entry-detail',
    component: () => import('@/views/EntryDetailView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/entries/:id(\\d+)/edit',
    name: 'entry-edit',
    component: () => import('@/views/EntryEditorView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/trips/:id(\\d+)',
    name: 'trip-detail',
    component: () => import('@/views/TripDetailView.vue'),
    props: true,
    meta: { requiresAuth: true },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/bookmarks',
    name: 'bookmarks',
    component: () => import('@/views/BookmarksView.vue'),
    meta: { requiresAuth: true },
  },

  /* ---- 认证 ---- */
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
  },

  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  /**
   * 滚动行为。
   *
   * 返回上一页时恢复原来位置(这是能不能顺畅往回翻的关键),
   * 其余情况回到顶部。页面滚动交给窗口之后,这段才真正生效 ——
   * 之前滚动发生在每个页面自己的容器里,这里返回的位置没人用。
   */
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) {
    return true
  }
  const auth = useAuthStore()
  if (auth.isLoggedIn) {
    return true
  }
  // 记住来路:登录成功后跳回用户原本想去的地方
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
