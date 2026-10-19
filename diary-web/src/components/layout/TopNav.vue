<script setup lang="ts">
/**
 * 顶部导航栏(PC 版的主导航)。
 *
 * 取代了原来的底部 TabBar。桌面端的导航常驻在页面顶部:
 * 五个目的地随时可点、随时可看出"我在哪",这是网页最不需要重新学习的形态。
 *
 * 三个刻意的选择:
 *
 * 1. 用 <router-link> 而不是 button + router.push。
 *    TabBar 用 button 是对的(标签栏的每一项不是一个地址),
 *    但网页顶部导航的每一项都应该是真正的链接 ——
 *    中键新标签页打开、复制链接、右键菜单,这些都只有 <a> 才有。
 *
 * 2. 激活态用「浅色药丸 + 系统蓝」而不是下划线。
 *    整套界面是 iOS 观感,分组控件(见 SegmentedControl)用的就是这个高亮方式,
 *    在顶部再来一条下划线会多出第四种"选中"的视觉语言。
 *
 * 3. 用户区做成点开的下拉菜单而不是把"退出"直接铺在导航栏上。
 *    常驻的退出按钮既占地方又容易被误点。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Avatar from '@/components/ui/Avatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useTripStore } from '@/stores/trips'
import { useUiStore } from '@/stores/ui'

interface NavItem {
  /** 与路由 name 一致,用来判断激活态 */
  name: string
  label: string
  path: string
}

const NAV: NavItem[] = [
  { name: 'timeline', label: '时间线', path: '/timeline' },
  { name: 'trips', label: '行程', path: '/trips' },
  { name: 'map', label: '足迹', path: '/map' },
  { name: 'memory', label: '回忆', path: '/memory' },
  { name: 'profile', label: '我的', path: '/profile' },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const trips = useTripStore()
const ui = useUiStore()

const activeName = computed(() => String(route.name ?? ''))

function isActive(item: NavItem): boolean {
  return activeName.value === item.name
}

/* ---- 用户菜单 ---- */

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

/** 点菜单外面就收起 —— 下拉菜单不这么做会一直挂着,挡住下面的内容 */
function onDocumentPointerDown(event: PointerEvent): void {
  if (!menuOpen.value) return
  if (menuRoot.value && !menuRoot.value.contains(event.target as Node)) {
    menuOpen.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    menuOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})

async function logout(): Promise<void> {
  menuOpen.value = false
  await auth.logout()
  // 行程列表是跨页缓存的,换账号必须让它失效,否则会看到上一个人的行程
  trips.invalidate()
  ui.success('已退出登录')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="topnav">
    <div class="topnav__inner navbar__inner">
      <router-link class="topnav__brand" :to="{ name: 'timeline' }">
        <span class="topnav__logo" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="10.5" r="2.2" stroke="currentColor" stroke-width="1.9" />
          </svg>
        </span>
        <span class="topnav__name">旅行日记</span>
      </router-link>

      <nav class="topnav__links" aria-label="主导航">
        <router-link
          v-for="item in NAV"
          :key="item.name"
          class="topnav__link"
          :class="{ 'topnav__link--active': isActive(item) }"
          :to="item.path"
          :aria-current="isActive(item) ? 'page' : undefined"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="topnav__actions">
        <router-link class="topnav__icon-btn" :to="{ name: 'search' }" aria-label="搜索" title="搜索">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.9" />
            <path d="M16 16l4 4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
          </svg>
        </router-link>

        <router-link class="btn btn--primary topnav__write" :to="{ name: 'entry-new' }">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
          </svg>
          写日记
        </router-link>

        <div ref="menuRoot" class="topnav__user">
          <button
            class="topnav__user-btn"
            type="button"
            :aria-expanded="menuOpen"
            aria-haspopup="menu"
            @click="toggleMenu"
          >
            <Avatar :name="auth.displayName" :src="auth.user?.avatar" :size="28" />
            <span class="topnav__user-name ellipsis">{{ auth.displayName || '旅行者' }}</span>
            <svg
              class="topnav__chevron"
              :class="{ 'topnav__chevron--open': menuOpen }"
              width="10"
              height="10"
              viewBox="0 0 12 12"
              fill="none"
              aria-hidden="true"
            >
              <path d="M2.5 4.5 6 8l3.5-3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>

          <transition name="menu">
            <div v-if="menuOpen" class="menu" role="menu">
              <div class="menu__head">
                <strong class="menu__name ellipsis">{{ auth.displayName || '旅行者' }}</strong>
                <span class="menu__id ellipsis">@{{ auth.username }}</span>
              </div>
              <router-link class="menu__item" role="menuitem" :to="{ name: 'profile' }" @click="menuOpen = false">
                个人资料
              </router-link>
              <button class="menu__item menu__item--danger" type="button" role="menuitem" @click="logout">
                退出登录
              </button>
            </div>
          </transition>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topnav {
  position: sticky;
  top: 0;
  z-index: 50;
  flex-shrink: 0;
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  border-bottom: 0.5px solid var(--separator);
}

.topnav__inner {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  height: var(--topnav-h);
  padding: 0 var(--gutter);
}

/* ---- 品牌 ---- */
.topnav__brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
  color: var(--label);
}

.topnav__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  color: #fff;
  background: linear-gradient(150deg, var(--ios-teal), var(--ios-blue) 60%, var(--ios-indigo));
}

.topnav__name {
  font-size: var(--text-headline);
  font-weight: 600;
  letter-spacing: -0.3px;
}

/* ---- 主导航 ---- */
.topnav__links {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.topnav__link {
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-full);
  font-size: var(--text-subhead);
  font-weight: 500;
  color: var(--label-2);
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.topnav__link:hover {
  background: var(--bg-fill);
  color: var(--label);
}

.topnav__link--active {
  background: color-mix(in srgb, var(--ios-blue) 12%, transparent);
  color: var(--ios-blue);
  font-weight: 600;
}

/* ---- 右侧 ---- */
.topnav__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
  flex-shrink: 0;
}

.topnav__icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-full);
  color: var(--label-2);
  transition: background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.topnav__icon-btn:hover {
  background: var(--bg-fill);
  color: var(--ios-blue);
}

.topnav__write {
  height: 34px;
  padding: 0 var(--space-4);
  font-size: var(--text-subhead);
}

/* ---- 用户 ---- */
.topnav__user {
  position: relative;
}

.topnav__user-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 180px;
  height: 40px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-full);
  transition: background var(--dur-fast) var(--ease-standard);
}

.topnav__user-btn:hover {
  background: var(--bg-fill);
}

.topnav__user-name {
  font-size: var(--text-subhead);
  font-weight: 500;
  min-width: 0;
}

.topnav__chevron {
  flex-shrink: 0;
  color: var(--label-3);
  transition: transform var(--dur-fast) var(--ease-standard);
}

.topnav__chevron--open {
  transform: rotate(180deg);
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  z-index: 60;
  width: 220px;
  padding: var(--space-1);
  border-radius: var(--radius-card);
  background: var(--material-thick);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  box-shadow: var(--shadow-float);
  border: 0.5px solid var(--separator);
  overflow: hidden;
}

.menu__head {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: var(--space-2) var(--space-3) var(--space-3);
  border-bottom: 0.5px solid var(--separator);
}

.menu__name {
  font-size: var(--text-subhead);
  font-weight: 600;
}

.menu__id {
  font-size: var(--text-caption1);
  color: var(--label-2);
}

.menu__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 38px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-cell);
  font-size: var(--text-subhead);
  color: var(--label);
  text-align: left;
  transition: background var(--dur-fast) var(--ease-standard);
}

.menu__item:hover {
  background: var(--bg-fill);
}

.menu__item--danger {
  color: var(--ios-red);
}

.menu-enter-active,
.menu-leave-active {
  transition: opacity var(--dur-fast) var(--ease-standard),
    transform var(--dur-fast) var(--ease-standard);
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}
</style>
