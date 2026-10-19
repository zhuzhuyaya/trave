<script setup lang="ts">
/**
 * 圆角分组列表里的一行。
 *
 * 三个细节让它像原生:
 *   1. 同一组里只有第一行有上圆角、最后一行有下圆角,中间是直角
 *   2. 分隔线不是贯穿整行,而是从左边 16px 处开始(缩进对齐文字)
 *   3. 可点击的行右侧有一个灰色 chevron
 */
withDefaults(
  defineProps<{
    /** 主标题 */
    title?: string
    /** 副标题(次要文字色) */
    subtitle?: string
    /** 右侧的值(如"3 篇""2024 年") */
    value?: string
    /** 是否可点击 —— 决定要不要画 chevron 和按压反馈 */
    clickable?: boolean
    /** 是否显示 chevron(即使不可点击) */
    chevron?: boolean
    /** 是否是分组第一行 / 最后一行,决定圆角 */
    first?: boolean
    last?: boolean
    /** 危险操作(删除),文字用红色 */
    danger?: boolean
  }>(),
  {
    title: '',
    subtitle: '',
    value: '',
    clickable: false,
    chevron: false,
    first: false,
    last: false,
    danger: false,
  },
)

const emit = defineEmits<{ click: [] }>()

function onClick(): void {
  emit('click')
}
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="list-row"
    :class="{
      'list-row--clickable': clickable,
      'list-row--first': first,
      'list-row--last': last,
      'list-row--danger': danger,
    }"
    :type="clickable ? 'button' : undefined"
    @click="onClick"
  >
    <span v-if="$slots.leading" class="list-row__leading">
      <slot name="leading" />
    </span>

    <span class="list-row__content">
      <span class="list-row__title">{{ title }}<slot name="title" /></span>
      <span v-if="subtitle" class="list-row__subtitle">{{ subtitle }}</span>
    </span>

    <span v-if="value" class="list-row__value">{{ value }}</span>
    <span v-if="$slots.trailing" class="list-row__trailing">
      <slot name="trailing" />
    </span>

    <svg
      v-if="chevron || clickable"
      class="list-row__chevron"
      width="7"
      height="12"
      viewBox="0 0 7 12"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M1 1l5 5-5 5"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  </component>
</template>

<style scoped>
.list-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 44px;
  padding: var(--space-2) var(--space-4);
  background: var(--bg-grouped-cell);
  text-align: left;
  font-size: var(--text-body);
  color: var(--label);
}

/* 圆角只加在分组的两头 */
.list-row--first {
  border-top-left-radius: var(--radius-cell);
  border-top-right-radius: var(--radius-cell);
}

.list-row--last {
  border-bottom-left-radius: var(--radius-cell);
  border-bottom-right-radius: var(--radius-cell);
}

/* 分隔线:从左边 16px 起,用 1px 再压扁到 0.5px */
.list-row:not(.list-row--last)::after {
  content: '';
  position: absolute;
  left: var(--space-4);
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--separator);
  transform: scaleY(0.5);
  transform-origin: bottom;
  pointer-events: none;
}

/*
  可点的行在鼠标悬停时给底色 —— 桌面端列表里"这行能点"全靠这个提示,
  只有 :active 的话鼠标划过是完全没有反馈的。
  用 :where() 把 :active 一起带上,免得鼠标按下和悬停两个状态打架。
*/
.list-row--clickable {
  transition: background var(--dur-fast) var(--ease-standard);
}

.list-row--clickable:hover,
.list-row--clickable:active {
  background: var(--bg-hover);
}

/* 悬停时右侧的 chevron 也往目标方向挪一点 */
.list-row__chevron {
  transition: transform var(--dur-fast) var(--ease-ios);
}

.list-row--clickable:hover .list-row__chevron {
  transform: translateX(2px);
}

.list-row--danger .list-row__title {
  color: var(--ios-red);
}

.list-row__leading {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--ios-blue);
}

.list-row__content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.list-row__title {
  font-size: var(--text-body);
  line-height: var(--lh-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-row__subtitle {
  font-size: var(--text-footnote);
  line-height: var(--lh-footnote);
  color: var(--label-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-row__value {
  font-size: var(--text-body);
  color: var(--label-2);
  flex-shrink: 0;
}

.list-row__trailing {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.list-row__chevron {
  color: var(--label-3);
  flex-shrink: 0;
}
</style>
