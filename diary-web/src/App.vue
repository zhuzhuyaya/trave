<script setup lang="ts">
/**
 * 应用外壳。
 *
 * 负责三件事:
 *   1. 启动时核对登录态并预取行程列表(两处都要等网络,并行发出)
 *   2. 显示顶部导航栏(登录/注册页除外 —— 那两页是全屏的,不该有导航)
 *   3. 挂载全局 Toast
 *
 * 页面滚动交给浏览器默认的窗口滚动,不再让每个页面自己在内部滚 ——
 * 这样浏览器查找、滚动位置恢复、吸顶定位才都按网页的规则工作。
 */
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import TopNav from '@/components/layout/TopNav.vue'
import ToastHost from '@/components/ui/ToastHost.vue'
import { useAuthStore } from '@/stores/auth'
import { useTripStore } from '@/stores/trips'

const route = useRoute()
const auth = useAuthStore()
const trips = useTripStore()

/**
 * 登录/注册页不显示导航栏:这两页是独立的入口,顶上挂一条导航
 * 反而会暗示"已经进来了"。其余页面(含 404)一律显示,
 * 这样用户在任何地方迷路都能直接点回时间线。
 */
const showTopNav = computed(() => route.name !== 'login' && route.name !== 'register')

onMounted(() => {
  // 两件事互不依赖,并行发出而不是串行等待
  auth.refresh()
  if (auth.isLoggedIn) {
    trips.ensureLoaded()
  }
})
</script>

<template>
  <div class="app">
    <TopNav v-if="showTopNav" />

    <main class="app__main">
      <router-view v-slot="{ Component, route: current }">
        <!-- 页面之间用一个克制的淡入上浮。原来那套"右滑入"是手机的层级语言,
             宽屏上整页横向平移会显得很晃。 -->
        <transition name="page" mode="out-in">
          <component :is="Component" :key="current.path" />
        </transition>
      </router-view>
    </main>

    <ToastHost />
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/*
  flex:1 + min-height:0 让内容区撑满导航栏以下的全部高度,
  这样地图页、空状态页这些"要占满剩余空间"的页面才有高度可拿。
*/
.app__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
