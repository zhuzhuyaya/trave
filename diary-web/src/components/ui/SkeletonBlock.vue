<script setup lang="ts">
/**
 * 骨架屏。
 *
 * 比转圈的加载指示器好,因为它同时告诉用户"等一下这里会出现什么形状的内容",
 * 内容到位时版式不会跳。
 *
 * 呼吸动画用 opacity 而不是常见的从左到右的"扫光":
 * 扫光在深色模式下会显出一道很扎眼的亮条,iOS 自己也用渐变呼吸。
 */
withDefaults(
  defineProps<{
    /** 高度 */
    height?: string
    width?: string
    /** 圆角 */
    radius?: string
    /** 是否显示为圆形(头像占位) */
    circle?: boolean
  }>(),
  {
    height: '16px',
    width: '100%',
    radius: '6px',
    circle: false,
  },
)
</script>

<template>
  <div
    class="skeleton"
    :style="{
      height,
      width,
      borderRadius: circle ? '50%' : radius,
    }"
    aria-hidden="true"
  />
</template>

<style scoped>
.skeleton {
  background: var(--bg-fill);
  animation: skeleton-pulse 1.4s var(--ease-standard) infinite;
}

@keyframes skeleton-pulse {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

/* 开了"减弱动态效果"就不再呼吸,静态灰块足够传达"正在加载" */
@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
  }
}
</style>
