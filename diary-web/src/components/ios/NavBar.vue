<script setup lang="ts">
/**
 * 页面标题区(原来的 iOS 导航栏)。
 *
 * 移动版里它是一条吸顶导航栏,标题会随滚动缩小、小标题在栏内淡入 ——
 * 那套是为了让"当前在哪一页"在窄屏上一直可见。
 * 桌面版不同:全站导航已经在顶部的 TopNav 里常驻了,
 * 这里只需要老老实实做一个页面标题块 ——
 * 一行「← 返回」、一个大标题、一个副标题,右侧放本页的操作。
 *
 * 所以滚动那套逻辑(progress / 大标题缩放 / setScroll)整体删掉了。
 */
import { useRouter } from 'vue-router'

withDefaults(
  defineProps<{
    /** 页面标题 */
    title?: string
    /** 是否显示返回链接(二级页面用) */
    back?: boolean
  }>(),
  {
    title: '',
    back: false,
  },
)

const router = useRouter()

/**
 * 返回链接的点击。
 *
 * 默认行为是后退一页;页面监听 back 事件可以打断它 ——
 * 编辑器要靠这个在离开前问一句"还没保存,确定要走吗"。
 * 监听方调用 preventDefault() 即接管返回逻辑。
 */
const emit = defineEmits<{ back: [event: { preventDefault: () => void }] }>()

function goBack(): void {
  let prevented = false
  emit('back', {
    preventDefault: () => {
      prevented = true
    },
  })
  if (prevented) return

  // 有历史就后退(保留来源页的滚动位置),否则回首页
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/timeline')
  }
}
</script>

<template>
  <header class="pagehead">
    <div class="pagehead__inner navbar__inner">
      <button v-if="back" class="pagehead__back" type="button" @click="goBack">
        <svg width="7" height="12" viewBox="0 0 7 12" fill="none" aria-hidden="true">
          <path
            d="M5.5 1 1 6l4.5 5"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        返回
      </button>

      <div class="pagehead__row">
        <div class="pagehead__text">
          <h1 v-if="title" class="pagehead__title">{{ title }}</h1>
          <!-- 副标题:统计摘要一类的一行说明,跟着标题走 -->
          <slot name="subtitle" />
        </div>

        <div class="pagehead__actions">
          <slot name="right" />
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.pagehead {
  flex-shrink: 0;
}

.pagehead__inner {
  padding: var(--space-6) var(--gutter) var(--space-2);
}

.pagehead__back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  height: 28px;
  margin-bottom: var(--space-2);
  padding: 0 var(--space-2) 0 0;
  font-size: var(--text-subhead);
  color: var(--ios-blue);
  transition: opacity var(--dur-fast) var(--ease-standard);
}

.pagehead__back:hover {
  opacity: 0.65;
}

/* 标题与右侧操作同一行,操作靠右对齐到标题的水平位置 */
.pagehead__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.pagehead__text {
  min-width: 0;
}

.pagehead__title {
  font-size: var(--text-large-title);
  line-height: var(--lh-large-title);
  font-weight: 700;
  letter-spacing: -0.7px;
}

.pagehead__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
</style>
