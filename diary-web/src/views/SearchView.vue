<script setup lang="ts">
/**
 * 搜索页。
 *
 * 结果按时间倒序排成一条列表 —— 搜索是一次"找到就走"的动作,
 * 不需要再花哨的版式。
 *
 * 输入防抖 300ms:每敲一个字打一次接口,在网络慢的时候请求会互相覆盖,
 * 结果顺序都可能乱掉。
 */
import { onMounted, ref, watch } from 'vue'
import NavBar from '@/components/ios/NavBar.vue'
import EntryCard from '@/components/entry/EntryCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import { entryApi } from '@/api/entry'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { EntrySummaryVO } from '@/api/types'
import { debounce } from '@/utils/misc'

const ui = useUiStore()

const inputRef = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const results = ref<EntrySummaryVO[]>([])
const loading = ref(false)
const searched = ref(false)

/** 关键词最短 2 个字:一个字的搜索会命中几乎所有记录,没有意义 */
const MIN_LENGTH = 2

async function run(): Promise<void> {
  const query = keyword.value.trim()
  if (query.length < MIN_LENGTH) {
    results.value = []
    searched.value = false
    return
  }

  loading.value = true
  try {
    const result = await entryApi.list({ keyword: query, page: 1, size: 30 })
    results.value = result.list
    searched.value = true
  } catch (error) {
    ui.error(toMessage(error))
    results.value = []
  } finally {
    loading.value = false
  }
}

const debouncedRun = debounce(() => void run(), 320)

watch(keyword, () => debouncedRun())

function clear(): void {
  keyword.value = ''
  results.value = []
  searched.value = false
  inputRef.value?.focus()
}

// 结果条目用 EntryCard 渲染,点击进详情的跳转由卡片自己处理
onMounted(() => {
  // 进搜索页就是为了打字,直接聚焦
  inputRef.value?.focus()
})
</script>

<template>
  <div class="search">
    <NavBar title="搜索" back />

    <!--
      搜索框吸在标题区下面:结果往下翻时还能直接改关键词。
      毛玻璃背景要铺满整宽,所以「吸顶 + 背景」放在外层,
      里层才是限宽的内容栏 —— 直接在内容栏上做吸顶的话,
      毛玻璃只会是一块 860px 宽的灰方块,两侧留出明显的接缝。
    -->
    <div class="search__sticky">
      <div class="search__bar navbar__inner">
        <div class="search__field">
          <svg class="search__icon" width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="2" />
            <path d="M16 16l4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          <input
            ref="inputRef"
            v-model="keyword"
            class="search__input"
            type="search"
            autocomplete="off"
            placeholder="搜索标题、正文或地点"
            @keyup.enter="run"
          />
          <button v-if="keyword" class="search__clear" type="button" aria-label="清空" @click="clear">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="12" r="9" fill="currentColor" />
              <path d="M9 9l6 6M15 9l-6 6" stroke="var(--bg-elevated)" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div class="page__scroll">
      <template v-if="loading">
        <div v-for="n in 3" :key="n" class="search__skeleton">
          <SkeletonBlock width="92px" height="92px" radius="10px" />
          <div class="search__skeleton-text">
            <SkeletonBlock width="60%" height="15px" />
            <SkeletonBlock width="85%" height="13px" />
          </div>
        </div>
      </template>

      <template v-else-if="results.length">
        <p class="search__count">找到 {{ results.length }} 篇</p>
        <EntryCard v-for="entry in results" :key="entry.id" :entry="entry" />
      </template>

      <EmptyState
        v-else-if="searched"
        icon="M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14ZM16 16l4.5 4.5"
        title="没有找到相关日记"
        description="试试地点名或行程名,比如「大理」「京都」"
      />

      <EmptyState
        v-else
        icon="M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14ZM16 16l4.5 4.5"
        title="搜索你的日记"
        :description="`输入至少 ${MIN_LENGTH} 个字开始搜索`"
      />
    </div>
  </div>
</template>

<style scoped>
/* 搜索结果是一条卡片列表,和阅读页同一个宽度 */
.search {
  --page-max: var(--list-max);
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
}

/* 吸顶条:背景铺满整宽,内层再限宽居中 */
.search__sticky {
  position: sticky;
  top: var(--sticky-offset);
  z-index: 10;
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  border-bottom: 0.5px solid var(--separator);
}

.search__bar {
  padding: var(--space-3) var(--gutter);
}

.search__field {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 42px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-field);
  background: var(--bg-fill);
  transition: box-shadow var(--dur-fast) var(--ease-standard);
}

/* 聚焦时给一圈蓝边:桌面端键盘操作多,焦点必须看得见 */
.search__field:focus-within {
  box-shadow: 0 0 0 2px var(--ios-blue);
}

.search__icon {
  color: var(--label-3);
  flex-shrink: 0;
}

.search__input {
  flex: 1;
  min-width: 0;
  height: 42px;
  border: none;
  background: transparent;
  font-size: var(--text-body);
  outline: none;
}

.search__input::placeholder {
  color: var(--label-3);
}

/* 浏览器自带的搜索框清除按钮长得各不相同,统一用自己画的 */
.search__input::-webkit-search-cancel-button {
  display: none;
}

.search__clear {
  color: var(--label-3);
  flex-shrink: 0;
  display: flex;
  transition: color var(--dur-fast) var(--ease-standard);
}

.search__clear:hover {
  color: var(--label);
}

.search__count {
  padding: var(--space-4) var(--gutter) var(--space-2);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.search__skeleton {
  display: flex;
  gap: var(--space-4);
  padding: var(--space-3) var(--gutter);
}

.search__skeleton-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-top: 4px;
}
</style>
