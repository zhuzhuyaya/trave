<script setup lang="ts">
/**
 * 头像。
 *
 * 没有头像图时不用默认的灰色剪影,而是按昵称算一个稳定的渐变色 + 首字 ——
 * 列表里几个纯色渐变圆比一排一样的灰色剪影好看得多,
 * 而且同一个人每次渲染颜色都一样,能被认出来。
 */
import { computed } from 'vue'
import { gradientFor, initialOf } from '@/utils/misc'

const props = withDefaults(
  defineProps<{
    name?: string | null
    src?: string | null
    /** 尺寸(px) */
    size?: number
  }>(),
  { name: '', src: null, size: 40 },
)

const fallbackStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: gradientFor(props.name || 'traveler'),
  fontSize: `${Math.round(props.size * 0.42)}px`,
}))

const imgStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))
</script>

<template>
  <img v-if="src" class="avatar avatar--img" :src="src" :alt="name ?? ''" :style="imgStyle" />
  <div v-else class="avatar" :style="fallbackStyle" :aria-label="name ?? ''">
    {{ initialOf(name) }}
  </div>
</template>

<style scoped>
.avatar {
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
  overflow: hidden;
  user-select: none;
  /* 头像上的文字不需要被选中,但也不要太小看不清 */
  line-height: 1;
}

.avatar--img {
  object-fit: cover;
  display: block;
}
</style>
