<script setup lang="ts">
/**
 * 时间线 —— 默认首页。
 *
 * 结构:页面标题 + (搜索 + 年份筛选)+ 按年分段的日记列表 + 分页。
 *
 * 两个关键实现:
 *
 * 1. 无尽翻页改用窗口滚动 + 哨兵元素的距离判断。
 *    原来挂在内容容器上的 @scroll / @scrollend 在改成窗口滚动后
 *    再也不会触发 —— 滚动的不再是那个元素了。
 *
 * 2. 筛选条件全部存在 URL query 里。这样返回上一页时筛选状态还在,
 *    分享链接也带着条件 —— 如果存组件 ref,一刷新就全丢了。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import EntryCard from '@/components/entry/EntryCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import { entryApi } from '@/api/entry'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import { useTripStore } from '@/stores/trips'
import type { EntrySummaryVO } from '@/api/types'
import { groupByYear } from '@/utils/group'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const trips = useTripStore()

const PAGE_SIZE = 10

/** 列表末尾的哨兵:它离视口底部的距离决定要不要再取一页 */
const sentinel = ref<HTMLElement | null>(null)

const entries = ref<EntrySummaryVO[]>([])
const total = ref(0)
const pages = ref(1)
const loading = ref(true)
/** 首屏之后的翻页:与首屏加载分开,才能只让"加载更多"那行转圈 */
const loadingMore = ref(false)

/**
 * 已经装到第几页。只活在组件里,不进 URL。
 *
 * 无尽列表和"页码"本来就是两个对不上的东西:URL 写着 page=2 时单独打开它,
 * 只能拿到第 2 页那几条,前面几页凭空消失;而每追加一页就 replace 一次路由,
 * 还会和"URL 变了要不要重新加载"互相打架 —— 列表会缩水成最后一页。
 * 所以 URL 只留真正的筛选条件:年份 / 行程 / 关键词。
 */
const loadedPage = ref(1)

/**
 * 每次重新加载都自增。翻页请求回来时对不上号,就说明筛选条件已经换过,
 * 这份结果是旧条件的,直接丢掉 —— 否则会给"2024"的列表拼上"2026"的条目。
 */
let generation = 0

/* ---------------------------------------------------------------------------
 *  URL 即状态
 * ------------------------------------------------------------------------- */

const year = computed(() => {
  const raw = Number(route.query.year)
  return Number.isFinite(raw) && raw > 0 ? raw : undefined
})
const tripId = computed(() => {
  const raw = Number(route.query.trip)
  return Number.isFinite(raw) && raw > 0 ? raw : undefined
})
const keyword = computed(() => (typeof route.query.q === 'string' ? route.query.q : ''))

/** 改动筛选条件时一律回到第 1 页 —— 否则会停在一个不存在的页码上 */
function updateQuery(patch: Record<string, string | number | undefined>): void {
  const next = { ...route.query, ...patch }
  if (!('page' in patch)) {
    next.page = undefined
  }
  // 清掉空值,避免 URL 里出现 ?year=undefined&page=
  for (const key of Object.keys(next)) {
    if (next[key] === undefined || next[key] === '' || next[key] === null) {
      delete next[key]
    }
  }
  void router.push({ query: next })
}

const grouped = computed(() => groupByYear(entries.value))

const hasFilter = computed(() => Boolean(year.value || tripId.value || keyword.value))

/* ---------------------------------------------------------------------------
 *  数据
 * ------------------------------------------------------------------------- */

/** 重新读列表。永远从第 1 页开始,后面几页由无尽翻页续上。 */
async function load(): Promise<void> {
  const token = ++generation
  loading.value = true
  // 换筛选条件等于把列表推倒重来,还在飞的翻页请求就此作废
  loadingMore.value = false
  try {
    const result = await entryApi.list({
      page: 1,
      size: PAGE_SIZE,
      year: year.value,
      tripId: tripId.value,
      keyword: keyword.value || undefined,
    })
    // 等结果的这段时间里筛选条件又变了,这份已经过期,连 loading 都别去动
    if (token !== generation) return
    entries.value = result.list
    loadedPage.value = 1
    total.value = result.total
    pages.value = result.pages
    loading.value = false
  } catch (error) {
    if (token !== generation) return
    ui.error(toMessage(error))
    entries.value = []
    loadedPage.value = 1
    total.value = 0
    pages.value = 1
    loading.value = false
  }

  // 首屏只有一页而页面还没铺满时,应该接着往下填 ——
  // 否则用户会以为"就这些了",其实下面还有。
  // 走 checkMore 而不是直接 requestMore:先量一下哨兵离视口有多远,
  // 列表本来就长(第一页能撑满一屏)时不要白取一页。
  await nextTick()
  checkMore()
}

/** 追加下一页。分页用"加载更多":下一页总在上一页的视觉延长线上,不用跳转 */
async function loadMore(): Promise<void> {
  if (loadingMore.value || loadedPage.value >= pages.value) return
  const token = generation
  const next = loadedPage.value + 1
  loadingMore.value = true
  try {
    const result = await entryApi.list({
      page: next,
      size: PAGE_SIZE,
      year: year.value,
      tripId: tripId.value,
      keyword: keyword.value || undefined,
    })
    // 翻页途中换了筛选条件,这一页是旧条件的结果,拼上去就是串行
    if (token !== generation) return
    entries.value = [...entries.value, ...result.list]
    loadedPage.value = next
  } catch (error) {
    if (token !== generation) return
    ui.error(toMessage(error))
  } finally {
    // 作废的那次不能清掉新一代的标记
    if (token === generation) loadingMore.value = false
  }
}

/**
 * 取下一页(如果还有)。
 *
 * 三个守卫缺一不可:首屏加载中、已经在翻页、已经没有下一页时都不能再发请求 ——
 * 滚动事件一帧可能触发多次,没有守卫会瞬间打出好几个重复请求。
 */
function requestMore(): void {
  if (loading.value || loadingMore.value) return
  if (loadedPage.value >= pages.value) return
  const before = loadedPage.value
  void loadMore().then(() => {
    // 只有真的多装了一页才继续往下填。请求失败时页码没动,
    // 再调 checkMore 就会一直"取一页→失败→再取"地空转。
    if (loadedPage.value > before) void nextTick().then(checkMore)
  })
}

/** 哨兵进入"视口下方 400px 以内"就提前取下一页,滚到底时内容是齐的 */
function checkMore(): void {
  const el = sentinel.value
  if (!el) return
  if (el.getBoundingClientRect().top - window.innerHeight < 400) {
    requestMore()
  }
}

/**
 * 窗口滚动时判断是否该翻页。
 *
 * 用 requestAnimationFrame 合并同一帧内的多次触发:滚动事件一帧可能来好几条,
 * 每条都读一次 getBoundingClientRect 会强制同步布局,白掉帧。
 */
let frame = 0

function onScroll(): void {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    checkMore()
  })
}

function scrollToTop(): void {
  window.scrollTo({ top: 0 })
}

onMounted(() => {
  trips.ensureLoaded()
  window.addEventListener('scroll', onScroll, { passive: true })
  void load()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (frame) cancelAnimationFrame(frame)
})

watch(
  () => [route.query.year, route.query.trip, route.query.q],
  () => {
    void load()
    scrollToTop()
  },
)

/* ---------------------------------------------------------------------------
 *  年份选项:从已加载的数据里推不出来(只加载了第一页),
 *  所以直接用当前年份往前推 6 年,足够覆盖个人日记的使用年限。
 * ------------------------------------------------------------------------- */
const yearOptions = computed(() => {
  const current = new Date().getFullYear()
  return Array.from({ length: 6 }, (_, i) => current - i)
})
</script>

<template>
  <div class="page">
    <NavBar title="旅行日记">
      <!-- 副标题行:统计摘要 -->
      <template #subtitle>
        <p class="page__summary">
          <template v-if="loading">正在读取…</template>
          <template v-else-if="total === 0">还没有记录</template>
          <template v-else>共 {{ total }} 篇日记</template>
        </p>
      </template>
    </NavBar>

    <div class="page__scroll">
      <!-- 年份筛选。跟着内容一起滚:桌面端一屏能看到整年,不需要它常驻 -->
      <div class="filter">
        <button
          class="filter__chip"
          :class="{ 'filter__chip--active': !year }"
          type="button"
          @click="updateQuery({ year: undefined })"
        >
          全部
        </button>
        <button
          v-for="option in yearOptions"
          :key="option"
          class="filter__chip"
          :class="{ 'filter__chip--active': year === option }"
          type="button"
          @click="updateQuery({ year: option })"
        >
          {{ option }}
        </button>
      </div>

      <!-- 当前筛选条件的提示条 -->
      <div v-if="hasFilter" class="active-filter">
        <span>
          已筛选
          <strong v-if="year">{{ year }} 年</strong>
          <strong v-if="tripId">{{
            trips.byId(tripId)?.title ?? '某个行程'
          }}</strong>
          <strong v-if="keyword">“{{ keyword }}”</strong>
        </span>
        <button class="active-filter__clear" type="button" @click="router.push({ query: {} })">
          清除
        </button>
      </div>

      <!-- 骨架屏:形状与真实卡片一致,内容到位时版式不跳 -->
      <template v-if="loading">
        <div v-for="n in 4" :key="n" class="skeleton-card">
          <SkeletonBlock width="40px" height="34px" radius="8px" />
          <SkeletonBlock width="92px" height="92px" radius="10px" />
          <div class="skeleton-card__text">
            <SkeletonBlock width="70%" height="15px" />
            <SkeletonBlock width="90%" height="13px" />
            <SkeletonBlock width="40%" height="11px" />
          </div>
        </div>
      </template>

      <template v-else-if="entries.length">
        <section v-for="group in grouped" :key="group.year" class="year">
          <h2 class="year__label">
            <span class="year__num">{{ group.year }}</span>
            <span class="year__count">{{ group.items.length }} 篇</span>
          </h2>

          <EntryCard v-for="entry in group.items" :key="entry.id" :entry="entry" />
        </section>

        <div class="page__more">
          <span v-if="loadingMore">正在加载…</span>
          <span v-else-if="loadedPage < pages">
            <button class="btn btn--secondary" type="button" @click="requestMore">加载更多</button>
          </span>
          <span v-else class="text-tertiary">— 到底了 —</span>
        </div>
      </template>

      <EmptyState
        v-else
        :icon="hasFilter ? 'M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14ZM16 16l4.5 4.5' : 'M12 6v12M6 12h12'"
        :title="hasFilter ? '没有匹配的日记' : '开始记录第一段旅程'"
        :description="
          hasFilter
            ? '换个年份或关键词试试,也可能这段行程还没写日记'
            : '写下地点、日期与几张照片,之后就能在地图上看见自己的足迹'
        "
      >
        <template #action>
          <button
            v-if="hasFilter"
            class="btn btn--secondary"
            type="button"
            @click="router.push({ query: {} })"
          >
            清除筛选
          </button>
          <router-link v-else class="btn btn--primary" :to="{ name: 'entry-new' }">
            写第一篇
          </router-link>
        </template>
      </EmptyState>

      <!-- 翻页哨兵:必须始终渲染,否则首屏为空时观察不到它 -->
      <div ref="sentinel" class="page__sentinel" />
    </div>
  </div>
</template>

<style scoped>
/*
  时间线是卡片列表,不是正文:用 --list-max 而不是 content-max ——
  一行文字超过约 45 个中文字,眼睛就找不到下一行的开头了。
*/
.page {
  --page-max: var(--list-max);
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
}

/* 页面标题区与列表同宽,所以这里的 padding 由 NavBar 自己给 */
.page__summary {
  margin-top: 2px;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/* ---- 年份筛选 ---- */
.filter {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-2) var(--gutter) var(--space-3);
  flex-wrap: wrap;
}

.filter__chip {
  height: 34px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-full);
  background: var(--bg-fill);
  color: var(--label-2);
  font-size: var(--text-subhead);
  font-family: var(--font-num);
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

.active-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin: 0 var(--gutter) var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-cell);
  background: var(--bg-fill);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.active-filter strong {
  color: var(--label);
  font-weight: 600;
}

.active-filter__clear {
  color: var(--ios-blue);
  font-size: var(--text-footnote);
  flex-shrink: 0;
}

.active-filter__clear:hover {
  opacity: 0.6;
}

.year {
  padding-top: var(--space-2);
}

.year__label {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  /* 吸顶年份:滚到年中时还能知道现在看的是哪一年。
     top 要让开顶部导航栏的高度,否则会被盖住。 */
  position: sticky;
  top: var(--sticky-offset);
  z-index: 5;
  padding: var(--space-2) var(--gutter);
  background: var(--material);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
}

.year__num {
  font-family: var(--font-num);
  font-size: var(--text-title3);
  line-height: var(--lh-title3);
  font-weight: 700;
  letter-spacing: -0.4px;
}

.year__count {
  font-size: var(--text-caption1);
  color: var(--label-2);
}

.page__more {
  display: flex;
  justify-content: center;
  padding: var(--space-6) var(--gutter) var(--space-4);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

/* 哨兵本身不可见,只用来测距 */
.page__sentinel {
  height: 1px;
}

/* ---- 骨架卡片 ---- */
.skeleton-card {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--gutter);
  border-bottom: 0.5px solid var(--separator);
}

.skeleton-card__text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-top: 4px;
}
</style>
