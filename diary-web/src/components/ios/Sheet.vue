<script setup lang="ts">
/**
 * 居中弹窗。
 *
 * 移动版是 iOS 的 Sheet:从底部升起、只有上方两个圆角、可以用手指下拉关闭。
 * 桌面版改成居中卡片 —— 屏幕下方的弹层是"拇指够得着"这个身体条件的产物,
 * 在键鼠上没有意义,反而会离视线中心很远。
 * 内容居中 + Esc 关闭 + 右上角关闭按钮,才是桌面弹窗的通行做法。
 *
 * 顺带说明:mask 与弹窗是元素而不是 <dialog>,
 * 所以 Esc 要自己监听(见下面的 keydown)。
 */
import { nextTick, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    /** 是否允许点遮罩关闭 */
    dismissible?: boolean
  }>(),
  { title: '', dismissible: true },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const panelRef = ref<HTMLElement | null>(null)

function close(): void {
  emit('update:modelValue', false)
}

function onMaskClick(): void {
  if (props.dismissible) close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    close()
  }
}

/**
 * 打开时锁住背景滚动,并给 body 补上滚动条让出的那点宽度。
 *
 * 不锁的话,滚轮在弹窗上滚动会把后面的页面一起滚走。
 * 补宽度是必须的:窗口滚动条被隐藏后页面会整体右移约 12px,
 * 关掉弹窗再移回来 —— 弹一次跳两下,很廉价。
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

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      lockScroll()
      document.addEventListener('keydown', onKeydown)
      await nextTick()
      // 让键盘用户一进来就落在弹窗里,Tab 不会先跑到背后的页面上
      panelRef.value?.focus()
    } else {
      unlockScroll()
      document.removeEventListener('keydown', onKeydown)
    }
  },
)
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div v-if="modelValue" class="mask" @click="onMaskClick" />
    </transition>

    <transition name="modal">
      <div
        v-if="modelValue"
        ref="panelRef"
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="title || undefined"
        tabindex="-1"
      >
        <header class="modal__header">
          <slot name="header">
            <h2 class="modal__title">{{ title }}</h2>
          </slot>

          <button class="modal__close" type="button" aria-label="关闭" @click="close">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
            </svg>
          </button>
        </header>

        <div class="modal__body scroll-area">
          <slot />
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.4);
}

.modal {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 101;
  width: min(520px, calc(100vw - 48px));
  max-height: min(80vh, 720px);
  display: flex;
  flex-direction: column;
  outline: none;

  background: var(--bg-elevated);
  border-radius: var(--radius-sheet);
  box-shadow: var(--shadow-float);
  overflow: hidden;
}

.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-shrink: 0;
  padding: var(--space-4) var(--space-4) var(--space-3) var(--space-5);
  border-bottom: 0.5px solid var(--separator);
}

.modal__title {
  font-size: var(--text-headline);
  line-height: var(--lh-headline);
  font-weight: 600;
}

.modal__close {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  color: var(--label-2);
  transition: background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.modal__close:hover {
  background: var(--bg-fill);
  color: var(--label);
}

.modal__body {
  /* min-height:0 是必需的:否则 flex 子项的最小尺寸会撑破 max-height,
     长列表(比如地点选择)会把弹窗顶到屏幕外面去 */
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-4) var(--space-5) var(--space-5);
}
</style>
