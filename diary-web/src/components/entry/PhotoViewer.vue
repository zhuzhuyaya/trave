<script setup lang="ts">
/**
 * 全屏图片查看器。
 *
 * 看图的顺手感是四件事:
 *   1. 黑底全屏,图片等比居中(铺满窗口,不留窗口滚动条)
 *   2. 左右切换,带回弹(桌面端用 ← → 键,滚动容器负责吸附)
 *   3. Esc 关闭
 *   4. 向下拖拽关闭(拖到一半松手会弹回去)
 *
 * 这里用 CSS scroll-snap 实现横向切换 —— 不写一行手势代码就拿到了
 * 原生滚动惯性、回弹与边缘阻尼,比手写 touch 事件稳得多。
 * "下拉关闭"和键盘翻页才需要自己处理。
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    urls: string[]
    /** 打开时定位到第几张 */
    index?: number
  }>(),
  { index: 0 },
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const scroller = ref<HTMLElement | null>(null)
const current = ref(props.index)

/** 下拉关闭的位移与状态 */
const dragY = ref(0)
const dragging = ref(false)
let startY = 0

const counterText = computed(() =>
  props.urls.length > 1 ? `${current.value + 1} / ${props.urls.length}` : '',
)

/**
 * 打开时锁住背景滚动。
 *
 * 桌面端窗口滚动条是常驻的,查看器铺满整个窗口时右边如果还杵着一根
 * 滚动条,一眼就能看出这层是"盖上去的"而不是真的全屏。
 * 补 padding 的理由和 Sheet 一样:滚动条一藏,页面会整体右移十几像素。
 */
let lockedPadding = ''

function lockScroll(): void {
  const gap = window.innerWidth - document.documentElement.clientWidth
  lockedPadding = document.body.style.paddingRight
  document.body.style.overflow = 'hidden'
  if (gap > 0) {
    document.body.style.paddingRight = `${gap}px`
  }
}

function unlockScroll(): void {
  document.body.style.overflow = ''
  document.body.style.paddingRight = lockedPadding
}

/** 打开时把横滚容器滚到目标图。必须在 DOM 渲染后做,否则容器尺寸还是 0 */
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      unlockScroll()
      document.removeEventListener('keydown', onKeydown)
      return
    }
    current.value = props.index
    lockScroll()
    // 键盘是桌面端翻图最顺手的方式,打开期间才需要监听
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    const el = scroller.value
    if (el) {
      el.scrollLeft = current.value * el.clientWidth
    }
  },
)

// 带着查看器切走路由(比如按 Esc 之前点了页头链接)时不会走上面的 watch,
// 不在这里兜底的话滚动锁和 keydown 会一直留在 document 上。
onUnmounted(() => {
  unlockScroll()
  document.removeEventListener('keydown', onKeydown)
})

function close(): void {
  emit('update:modelValue', false)
}

/** 滚动结束后反推当前是第几张,更新计数器 */
function onScroll(): void {
  const el = scroller.value
  if (!el || el.clientWidth === 0) return
  current.value = Math.round(el.scrollLeft / el.clientWidth)
}

/**
 * 上一张 / 下一张。
 *
 * 直接改横滚容器的 scrollLeft,而不是自己维护一个索引再渲染 ——
 * 翻页的吸附、惯性仍然交给 scroll-snap,键盘和鼠标滚轮走的是同一套逻辑。
 */
function go(delta: number): void {
  const el = scroller.value
  if (!el) return
  const last = props.urls.length - 1
  const next = Math.min(Math.max(current.value + delta, 0), last)
  if (next === current.value) return
  el.scrollTo({ left: next * el.clientWidth, behavior: 'smooth' })
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  } else if (event.key === 'ArrowLeft') {
    go(-1)
  } else if (event.key === 'ArrowRight') {
    go(1)
  }
}

function onDragStart(event: PointerEvent): void {
  // 只在纵向意图明显时才接管 —— 否则会把横向翻页的手势抢走
  startY = event.clientY
  dragging.value = true
}

function onDragMove(event: PointerEvent): void {
  if (!dragging.value) return
  const delta = event.clientY - startY
  // 只允许向下拖;向上拖给一点阻尼,让它"拖不动但也不死"
  dragY.value = delta > 0 ? delta : delta * 0.2
  if (Math.abs(delta) > 10) {
    event.preventDefault()
  }
}

function onDragEnd(): void {
  if (!dragging.value) return
  dragging.value = false
  if (dragY.value > 100) {
    close()
  }
  dragY.value = 0
  startY = 0
}
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div
        v-if="modelValue"
        class="viewer"
        :style="{
          transform: dragY ? `translateY(${dragY}px)` : undefined,
          transition: dragging ? 'none' : undefined,
          opacity: dragY ? String(Math.max(0.3, 1 - dragY / 500)) : undefined,
        }"
        @pointerdown="onDragStart"
        @pointermove="onDragMove"
        @pointerup="onDragEnd"
        @pointercancel="onDragEnd"
      >
        <!-- 顶栏:关闭按钮 + 计数 -->
        <header class="viewer__bar">
          <button class="viewer__close" type="button" aria-label="关闭" @click="close">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
            </svg>
          </button>
          <span class="viewer__counter">{{ counterText }}</span>
          <span class="viewer__spacer" />
        </header>

        <!-- 横向滚动容器:scroll-snap 提供原生翻页手感 -->
        <div ref="scroller" class="viewer__scroller" @scroll.passive="onScroll">
          <div v-for="(url, i) in urls" :key="i" class="viewer__slide">
            <img class="viewer__img" :src="url" alt="" :draggable="false" />
          </div>
        </div>

        <p class="viewer__hint">← → 切换 · Esc 关闭</p>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.viewer {
  position: fixed;
  inset: 0;
  z-index: 150;
  background: #000;
  display: flex;
  flex-direction: column;
  touch-action: pan-x pan-y;
  will-change: transform;
}

.viewer__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-2);
  color: #fff;
  z-index: 1;
}

.viewer__close,
.viewer__spacer {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.viewer__close {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.viewer__close:active {
  transform: scale(0.92);
}

.viewer__counter {
  font-family: var(--font-num);
  font-size: var(--text-footnote);
  opacity: 0.85;
}

.viewer__scroller {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow-x: auto;
  overflow-y: hidden;
  /* 横向翻页的核心三行:吸附 + 惯性 + 隐藏滚动条 */
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.viewer__scroller::-webkit-scrollbar {
  height: 0;
}

.viewer__slide {
  flex: 0 0 100%;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  scroll-snap-align: center;
  scroll-snap-stop: always;
}

.viewer__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  /* 避免长按图片出现系统菜单 */
  -webkit-touch-callout: none;
}

.viewer__hint {
  text-align: center;
  padding-bottom: var(--space-5);
  font-size: var(--text-caption1);
  color: rgba(255, 255, 255, 0.4);
  user-select: none;
}
</style>
