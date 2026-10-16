<script setup lang="ts">
/**
 * 确认对话框。
 *
 * 用于删除这类不可逆操作。样式对齐 iOS 的 Alert:
 * 居中、圆角、按钮横向平分、危险操作用红色。
 */
withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    message?: string
    confirmText?: string
    cancelText?: string
    /** 确认按钮是否用红色(删除类操作) */
    danger?: boolean
  }>(),
  {
    title: '确认操作',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    danger: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

function dismiss(): void {
  emit('update:modelValue', false)
  emit('cancel')
}

function confirm(): void {
  emit('update:modelValue', false)
  emit('confirm')
}
</script>

<template>
  <Teleport to="body">
    <transition name="fade">
      <div v-if="modelValue" class="confirm__mask" @click="dismiss" />
    </transition>

    <transition name="confirm">
      <div v-if="modelValue" class="confirm" role="alertdialog" aria-modal="true">
        <div class="confirm__body">
          <h2 class="confirm__title">{{ title }}</h2>
          <p v-if="message" class="confirm__message">{{ message }}</p>
        </div>

        <div class="confirm__actions">
          <button class="confirm__btn" type="button" @click="dismiss">
            {{ cancelText }}
          </button>
          <span class="confirm__divider" />
          <button
            class="confirm__btn confirm__btn--confirm"
            :class="{ 'confirm__btn--danger': danger }"
            type="button"
            @click="confirm"
          >
            {{ confirmText }}
          </button>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.confirm__mask {
  position: fixed;
  inset: 0;
  z-index: 110;
  background: rgba(0, 0, 0, 0.4);
}

.confirm {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 111;
  width: 270px;
  border-radius: var(--radius-sheet);
  overflow: hidden;
  background: var(--material-thick);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
}

.confirm__body {
  padding: var(--space-5) var(--space-4) var(--space-4);
  text-align: center;
}

.confirm__title {
  font-size: var(--text-headline);
  line-height: var(--lh-headline);
  font-weight: 600;
}

.confirm__message {
  margin-top: var(--space-1);
  font-size: var(--text-footnote);
  line-height: var(--lh-footnote);
  color: var(--label-2);
}

.confirm__actions {
  position: relative;
  display: flex;
  border-top: 1px solid var(--separator);
}

.confirm__actions::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--separator);
  transform: scaleX(0.5);
}

.confirm__btn {
  flex: 1;
  height: 44px;
  font-size: var(--text-body);
  color: var(--ios-blue);
}

.confirm__btn:active {
  background: var(--bg-hover);
}

.confirm__btn--confirm {
  font-weight: 600;
}

.confirm__btn--danger {
  color: var(--ios-red);
}

.confirm-enter-active,
.confirm-leave-active {
  transition: opacity var(--dur-fast) var(--ease-standard), transform var(--dur) var(--ease-ios);
}

.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.94);
}

.confirm-leave-active {
  transition: opacity 0.15s var(--ease-standard);
}
</style>
