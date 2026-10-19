<script setup lang="ts">
/**
 * 分段控件(iOS Segmented Control)。
 *
 * 用于在同一页切换数据视图,比如时间线的「全部 / 按行程」、
 * 统计页的「年 / 月」。
 *
 * 选中背景块会滑动到目标位置 —— 这个位移的连续性是它像原生的关键,
 * 直接切换两个 background 会显得很生硬。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    options: { value: string; label: string }[]
  }>(),
  {},
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const activeIndex = computed(() => {
  const index = props.options.findIndex((option) => option.value === props.modelValue)
  return index < 0 ? 0 : index
})

/** 滑块的位置与宽度:用百分比算,不依赖元素实测尺寸,布局变化时自动跟随 */
const indicatorStyle = computed(() => {
  const count = props.options.length || 1
  const width = 100 / count
  return {
    width: `calc(${width}% - 2px)`,
    transform: `translateX(calc(${activeIndex.value * 100}% + ${activeIndex.value * 2}px))`,
  }
})

function select(value: string): void {
  if (value !== props.modelValue) {
    emit('update:modelValue', value)
  }
}
</script>

<template>
  <div class="segmented" role="tablist">
    <div class="segmented__indicator" :style="indicatorStyle" />
    <button
      v-for="option in options"
      :key="option.value"
      class="segmented__option"
      type="button"
      role="tab"
      :aria-selected="option.value === modelValue"
      :class="{ 'segmented__option--active': option.value === modelValue }"
      @click="select(option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented {
  position: relative;
  display: flex;
  padding: 2px;
  border-radius: 9px;
  background: var(--bg-fill);
}

.segmented__indicator {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 2px;
  border-radius: 7px;
  background: var(--bg-elevated);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 0 1px rgba(0, 0, 0, 0.06);
  transition: transform var(--dur) var(--ease-ios);
  will-change: transform;
}

.segmented__option {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 30px;
  font-size: var(--text-subhead);
  font-weight: 500;
  color: var(--label-2);
  transition: color var(--dur-fast) var(--ease-standard);
}

.segmented__option:hover:not(.segmented__option--active) {
  color: var(--label);
}

.segmented__option--active {
  color: var(--label);
  font-weight: 600;
}
</style>
