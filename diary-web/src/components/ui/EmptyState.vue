<script setup lang="ts">
/**
 * 空状态。
 *
 * iOS 的空态不是一句"暂无数据":它有一个图标、一句标题、一句解释,
 * 以及——最关键——一个"下一步做什么"的按钮。
 * 只显示"暂无数据"等于把用户丢在原地。
 */
withDefaults(
  defineProps<{
    /** 图标:内联 SVG 的 path 字符串(24x24 viewBox) */
    icon?: string
    title?: string
    description?: string
  }>(),
  {
    icon: 'M4 5.5h16v13H4zM4 10h16M9 14h6',
    title: '这里还是空的',
    description: '',
  },
)
</script>

<template>
  <div class="empty">
    <div class="empty__icon">
      <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          :d="icon"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>

    <h3 class="empty__title">{{ title }}</h3>
    <p v-if="description" class="empty__desc">{{ description }}</p>

    <div v-if="$slots.action" class="empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-8) var(--space-6);
}

.empty__icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-fill);
  color: var(--label-2);
  margin-bottom: var(--space-4);
}

.empty__title {
  font-size: var(--text-headline);
  line-height: var(--lh-headline);
  font-weight: 600;
  margin-bottom: var(--space-1);
}

.empty__desc {
  font-size: var(--text-subhead);
  line-height: var(--lh-subhead);
  color: var(--label-2);
  max-width: 260px;
}

.empty__action {
  margin-top: var(--space-5);
}
</style>
