<script setup lang="ts">
/**
 * 404。
 *
 * 不做花哨的插画,就是一个地图针偏离轨道的图标 + 一句话 + 一条回去的路。
 * 移动端用户撞到 404 的路由很少,这里的目标是"别让人卡住"。
 */
import { useRouter } from 'vue-router'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <EmptyState
      icon="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
      title="这条路没通"
      description="你要找的页面不存在,或者已经被移走了"
    >
      <template #action>
        <button class="btn btn--primary" type="button" @click="router.replace({ name: 'timeline' })">
          回到时间线
        </button>
      </template>
    </EmptyState>
  </div>
</template>

<style scoped>
.not-found {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: var(--gutter);
  background: var(--bg-grouped);
}
</style>
