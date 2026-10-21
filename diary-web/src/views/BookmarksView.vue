<script setup lang="ts">
/**
 * 收藏页。
 *
 * 后端在 /entries/bookmarks 上同时给出分页卡片与可筛标签,
 * 所以这里不需要分 second request:标签 chips 直接用响应里的 tags 字段。
 *
 * 筛选条件存 URL query(?tag=xx),与时间线同一套约定 ——
 * 返回时状态还在,分享出去的链接也带着条件。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import EntryCard from '@/components/entry/EntryCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import { entryApi } from '@/api/entry'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { BookmarkPageVO } from '@/api/types'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const result = ref<BookmarkPageVO | null>(null)
const loading = ref(true)

/** 防串台:响应回来时条件已经换了,就丢掉这份结果 */
let generation = 0

const tag = computed(() => (typeof route.query.tag === 'string' ? route.query.tag : ''))

async function load(): Promise<void> {
  const token = ++generation
  loading.value = true
  try {
    const page = await entryApi.bookmarks(tag.value || undefined)
    if (token !== generation) return
    result.value = page
    loading.value = false
  } catch (error) {
    if (token !== generation) return
    ui.error(toMessage(error))
    result.value = null
    loading.value = false
  }
}

function pick(name: string): void {
  // 再点一次当前标签 = 取消筛选;与时间线 chips 的交互一致
  void router.push({ query: tag.value === name ? {} : { tag: name } })
}

function clear(): void {
  void router.push({ query: {} })
}

onMounted(load)

watch(
  () => route.query.tag,
  () => {
    void load()
    window.scrollTo({ top: 0 })
  },
)
</script>

<template>
  <div class="page">
    <NavBar title="收藏" back>
      <template #subtitle>
        <p class="page__summary">
          <template v-if="loading">正在读取…</template>
          <template v-else-if="!result || result.total === 0">
            {{ result?.filtered ? '这个标签下没有收藏' : '还没有收藏' }}
          </template>
          <template v-else>收藏了 {{ result.total }} 篇</template>
        </p>
      </template>
    </NavBar>

    <div class="page__scroll">
      <!-- 标签筛选:数据直接来自列表响应,选中静态即高亮 -->
      <div v-if="result && result.tags.length" class="filter">
        <button
          class="filter__chip"
          :class="{ 'filter__chip--active': !tag }"
          type="button"
          @click="clear"
        >
          全部
        </button>
        <button
          v-for="item in result.tags"
          :key="item.name"
          class="filter__chip"
          :class="{ 'filter__chip--active': tag === item.name }"
          type="button"
          @click="pick(item.name)"
        >
          #{{ item.name }}
          <span class="filter__count">{{ item.count }}</span>
        </button>
      </div>

      <!-- 骨架屏:与卡片同形,到位时版式不跳 -->
      <template v-if="loading">
        <div v-for="n in 3" :key="n" class="skeleton-card">
          <SkeletonBlock width="40px" height="34px" radius="8px" />
          <SkeletonBlock width="92px" height="92px" radius="10px" />
          <div class="skeleton-card__text">
            <SkeletonBlock width="70%" height="15px" />
            <SkeletonBlock width="90%" height="13px" />
            <SkeletonBlock width="40%" height="11px" />
          </div>
        </div>
      </template>

      <template v-else-if="result && result.list.length">
        <EntryCard v-for="entry in result.list" :key="entry.id" :entry="entry" />
      </template>

      <EmptyState
        v-else
        icon="M6 3h12v18l-6-4.5L6 21V3Z"
        :title="
          result?.filtered
            ? '这个标签下没有收藏'
            : '书签架还是空的'
        "
        :description="
          result?.filtered
            ? '换个标签看看,或清除筛选回到全部'
            : '读到想过阵子再翻的日记,点标题旁的书签把它收进来'
        "
      >
        <template #action>
          <button
            v-if="result?.filtered"
            class="btn btn--secondary"
            type="button"
            @click="clear"
          >
            清除筛选
          </button>
          <router-link v-else class="btn btn--primary" :to="{ name: 'timeline' }">
            去翻时间线
          </router-link>
        </template>
      </EmptyState>
    </div>
  </div>
</template>

<style scoped>
/* 收藏列表与时间线同一宽度,卡片也完全同款 —— 差异只在筛选条 */
.page {
  --page-max: var(--list-max);
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
}

.page__summary {
  margin-top: 2px;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/* ---- 标签筛选(样式对齐时间线年份 chips,多计数徽标) ---- */
.filter {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-2) var(--gutter) var(--space-3);
  flex-wrap: wrap;
}

.filter__chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  height: 34px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-full);
  background: var(--bg-fill);
  color: var(--label-2);
  font-size: var(--text-subhead);
  transition: background var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard);
}

.filter__chip:hover {
  background: var(--bg-hover);
  color: var(--label);
}

.filter__chip--active {
  background: var(--ios-blue);
  color: #fff;
  font-weight: 600;
}

.filter__chip--active:hover {
  background: var(--ios-blue);
  color: #fff;
}

.filter__count {
  font-family: var(--font-num);
  font-size: var(--text-footnote);
  opacity: 0.72;
}
</style>
