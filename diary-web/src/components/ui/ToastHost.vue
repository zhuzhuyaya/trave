<script setup lang="ts">
/**
 * Toast 容器。
 *
 * 从顶部滑入的横幅,停在右上角。
 * 位置定在导航栏下方而不是屏幕正中上方:桌面端的内容栏是居中的,
 * 顶部居中的横幅会正好盖住标题。
 */
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

/** 每种类型配一个图标与颜色,让用户不用读完文字就知道是好消息还是坏消息 */
const ICONS: Record<string, string> = {
  success: 'M4 12.5l5 5L20 6.5',
  error: 'M12 7v6.5M12 17.2v.01M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z',
  info: 'M12 11v6M12 7.2v.01M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z',
}
</script>

<template>
  <Teleport to="body">
    <div class="toast-host" aria-live="polite">
      <transition-group name="toast">
        <div
          v-for="toast in ui.toasts"
          :key="toast.id"
          class="toast"
          :class="`toast--${toast.kind}`"
          role="status"
          @click="ui.dismiss(toast.id)"
        >
          <svg class="toast__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              :d="ICONS[toast.kind]"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span class="toast__text">{{ toast.text }}</span>
        </div>
      </transition-group>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-host {
  position: fixed;
  /* 停在顶部导航栏下方,右上角 —— 桌面端提示的常规位置,
     也不会压住居中的内容栏 */
  top: calc(var(--topnav-h) + var(--space-4));
  right: var(--space-5);
  z-index: 200;
  width: min(360px, calc(100vw - var(--space-8)));
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-card);
  background: var(--material-thick);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  box-shadow: var(--shadow-float);
  border: 0.5px solid var(--separator);
  cursor: pointer;
}

.toast__icon {
  flex-shrink: 0;
}

.toast--success .toast__icon {
  color: var(--ios-green);
}

.toast--error .toast__icon {
  color: var(--ios-red);
}

.toast--info .toast__icon {
  color: var(--ios-blue);
}

.toast__text {
  font-size: var(--text-subhead);
  line-height: var(--lh-subhead);
  color: var(--label);
}

.toast-enter-active {
  transition: opacity var(--dur) var(--ease-ios), transform var(--dur) var(--ease-ios);
}

.toast-leave-active {
  transition: opacity var(--dur-fast) var(--ease-standard), transform var(--dur-fast) var(--ease-standard);
  position: absolute;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-16px) scale(0.96);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}
</style>
