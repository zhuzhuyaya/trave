<script setup lang="ts">
/**
 * 搜索页。
 *
 * 两条搜索路径:
 *   - 关键词:SQL LIKE 字面匹配,适合记得原话/地名;
 *   - 语义:把查询嵌入成向量再算余弦,适合只记得"那件事大概是什么感觉"。
 *
 * 输入防抖 320ms;每次请求带序号,过期响应直接丢弃 ——
 * 语义搜索要等 Ollama,慢请求比关键词更容易出现"后发先至"。
 */
import { computed, onMounted, ref, watch } from 'vue'
import NavBar from '@/components/ios/NavBar.vue'
import EntryCard from '@/components/entry/EntryCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import { entryApi } from '@/api/entry'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { EntrySummaryVO } from '@/api/types'
import { debounce } from '@/utils/misc'

type SearchMode = 'keyword' | 'semantic'

const ui = useUiStore()

const inputRef = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const mode = ref<SearchMode>('keyword')
const results = ref<EntrySummaryVO[]>([])
/** entryId → 余弦相似度;只在意语义模式下有值 */
const scores = ref<Record<number, number>>({})
/** 后端 AI 是否可用;enabled=false 时提示去配置,而不是说"没搜到" */
const semanticEnabled = ref(true)
const loading = ref(false)
const searched = ref(false)

/** 关键词最短 2 个字:一个字的搜索会命中几乎所有记录,没有意义 */
const MIN_LENGTH = 2

const placeholder = computed(() =>
  mode.value === 'semantic'
    ? '描述你记得的内容,比如「秋天在山里看枫叶」'
    : '搜索标题、正文或地点',
)

/** 请求序号:防抖之后仍可能有两个请求在途,只认最后一次的结果 */
let requestSeq = 0

async function run(): Promise<void> {
  const query = keyword.value.trim()
  if (query.length < MIN_LENGTH) {
    requestSeq++ // 让在途请求的结果作废
    loading.value = false
    results.value = []
    scores.value = {}
    searched.value = false
    return
  }

  const seq = ++requestSeq
  loading.value = true
  try {
    if (mode.value === 'semantic') {
      const result = await entryApi.semanticSearch(query)
      if (seq !== requestSeq) return
      semanticEnabled.value = result.enabled
      results.value = result.hits.map((hit) => hit.entry)
      scores.value = Object.fromEntries(result.hits.map((hit) => [hit.entry.id, hit.score]))
    } else {
      const result = await entryApi.list({ keyword: query, page: 1, size: 30 })
      if (seq !== requestSeq) return
      results.value = result.list
      scores.value = {}
    }
    searched.value = true
  } catch (error) {
    if (seq !== requestSeq) return
    ui.error(toMessage(error))
    results.value = []
    scores.value = {}
    searched.value = false
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

const debouncedRun = debounce(() => void run(), 320)

watch([keyword, mode], () => {
  requestSeq++ // 输入或模式一变,在途请求立刻作废
  debouncedRun()
})

function clear(): void {
  requestSeq++ // 清空时在途请求也应作废,否则慢响应会把结果又填回来
  keyword.value = ''
  results.value = []
  scores.value = {}
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
            :placeholder="placeholder"
            @keyup.enter="run"
          />
          <button v-if="keyword" class="search__clear" type="button" aria-label="清空" @click="clear">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="12" r="9" fill="currentColor" />
              <path d="M9 9l6 6M15 9l-6 6" stroke="var(--bg-elevated)" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>

        <!-- 关键词是"记得原话",语义是"记得意思"——两条路各有用处 -->
        <div class="search__modes" role="tablist" aria-label="搜索方式">
          <button
            type="button"
            role="tab"
            class="search__mode"
            :class="{ 'search__mode--active': mode === 'keyword' }"
            :aria-selected="mode === 'keyword'"
            @click="mode = 'keyword'"
          >
            关键词
          </button>
          <button
            type="button"
            role="tab"
            class="search__mode"
            :class="{ 'search__mode--active': mode === 'semantic' }"
            :aria-selected="mode === 'semantic'"
            @click="mode = 'semantic'"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 3l1.9 5.4L19.5 10l-5.6 1.6L12 17l-1.9-5.4L4.5 10l5.6-1.6z"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linejoin="round"
              />
            </svg>
            语义
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
        <p class="search__count">
          <template v-if="mode === 'semantic'">
            找到 {{ results.length }} 篇语义相近的日记 · 按相关度排序
          </template>
          <template v-else>找到 {{ results.length }} 篇</template>
        </p>
        <EntryCard
          v-for="entry in results"
          :key="entry.id"
          :entry="entry"
          :score="mode === 'semantic' ? scores[entry.id] : undefined"
        />
      </template>

      <EmptyState
        v-else-if="searched && mode === 'semantic' && !semanticEnabled"
        icon="M12 3l1.9 5.4L19.5 10l-5.6 1.6L12 17l-1.9-5.4L4.5 10l5.6-1.6z"
        title="语义搜索未启用"
        description="后端 AI 未开启。启用 AI_ENABLED 并启动 Ollama 后,就能按意思搜索。"
      />

      <EmptyState
        v-else-if="searched && mode === 'semantic'"
        icon="M12 3l1.9 5.4L19.5 10l-5.6 1.6L12 17l-1.9-5.4L4.5 10l5.6-1.6z"
        title="没有语义相近的日记"
        description="换个说法试试:描述当时在做什么、看到了什么,比只写名词更容易命中"
      />

      <EmptyState
        v-else-if="searched"
        icon="M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14ZM16 16l4.5 4.5"
        title="没有找到相关日记"
        description="试试地点名或行程名,比如「大理」「京都」"
      />

      <EmptyState
        v-else
        icon="M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14ZM16 16l4.5 4.5"
        :title="mode === 'semantic' ? '按意思搜索日记' : '搜索你的日记'"
        :description="
          mode === 'semantic'
            ? `输入至少 ${MIN_LENGTH} 个字,描述你想找的那段经历`
            : `输入至少 ${MIN_LENGTH} 个字开始搜索`
        "
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

/* iOS 分段控件:关键词 / 语义 */
.search__modes {
  display: flex;
  gap: 2px;
  margin-top: var(--space-2);
  padding: 2px;
  border-radius: 9px;
  background: var(--bg-fill);
}

.search__mode {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: var(--text-footnote);
  color: var(--label-2);
  cursor: pointer;
  transition:
    background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.search__mode--active {
  background: var(--bg-elevated);
  color: var(--label);
  font-weight: 600;
  box-shadow: 0 1px 4px rgb(0 0 0 / 12%);
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
