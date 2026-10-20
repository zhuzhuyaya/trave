<script setup lang="ts">
/**
 * 回忆。
 *
 * 三段内容:
 *   1. 顶部「历史上的今天」—— 每年今天你在哪,铺成一格一格的回忆卡
 *   2. 概览数字 —— 四张小卡
 *   3. 年度足迹 —— 每年一行条形,或最近 12 个月的格子
 *
 * 图表全是 CSS 画的(CSS 变量控宽/控色),没有引图表库:
 * 这里只有条形和格子两种形状,引一个上百 KB 的库来画它们是杀鸡用牛刀,
 * 而且图表库的默认配色与这套 iOS 观感根本不搭。
 *
 * 数字全部来自 /stats 的实时聚合 —— 个人日记的量级下,一条 SQL 算完
 * 比维护冗余计数表简单得多,也不会有一致性问题。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/ios/NavBar.vue'
import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import SegmentedControl from '@/components/ios/SegmentedControl.vue'
import { insightApi } from '@/api/insight'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'
import type { MemoryVO, StatsVO } from '@/api/types'
import { formatDateCN, formatDateISO, formatMonthDay, weekdayCN } from '@/utils/time'
import { gradientFor, numberToCN } from '@/utils/misc'

const router = useRouter()
const ui = useUiStore()

const stats = ref<StatsVO | null>(null)
const memories = ref<MemoryVO[]>([])
const loading = ref(true)

/** 年度足迹用条形还是格子看 —— 两种视角各有所长 */
const view = ref<'year' | 'month'>('year')

/* ---------------------------------------------------------------------------
 *  数据
 * ------------------------------------------------------------------------- */

async function load(): Promise<void> {
  loading.value = true
  try {
    const [statsResult, memoryResult] = await Promise.all([
      insightApi.stats(),
      insightApi.memories(),
    ])
    stats.value = statsResult
    memories.value = memoryResult
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ---------------------------------------------------------------------------
 *  概览
 * ------------------------------------------------------------------------- */

const overview = computed(() => {
  const s = stats.value
  if (!s) return []
  return [
    { label: '篇日记', value: s.entryCount },
    { label: '座城市', value: s.placeCount },
    { label: '天在路上', value: s.dayCount },
    { label: '张照片', value: s.photoCount },
  ]
})

const sinceText = computed(() => {
  const first = stats.value?.firstDate
  if (!first) return ''
  return `从 ${formatDateCN(first)} 写下第一篇`
})

const hasData = computed(() => (stats.value?.entryCount ?? 0) > 0)

/**
 * 今天的日期串。
 *
 * 用 formatDateISO 而不是 `new Date().toISOString().slice(0, 10)`:
 * 后者取的是 UTC 日期,东八区凌晨 0-8 点会显示成昨天。
 */
const todayISO = formatDateISO(new Date())

/** 今天是什么日子 —— 回忆页的副标题 */
const todayText = computed(() => `${formatMonthDay(todayISO)} ${weekdayCN(todayISO)}`)

/* ---------------------------------------------------------------------------
 *  年度条形
 * ------------------------------------------------------------------------- */

const yearly = computed(() => stats.value?.yearly ?? [])

/** 条形长度的分母。用最大值而不是总和 —— 看的是"哪年最多",不是占比 */
const yearlyMax = computed(() => Math.max(1, ...yearly.value.map((item) => item.entryCount)))

/* ---------------------------------------------------------------------------
 *  月度格子
 * ------------------------------------------------------------------------- */

const monthly = computed(() => stats.value?.monthly ?? [])
const monthlyMax = computed(() => Math.max(1, ...monthly.value.map((item) => item.entryCount)))

/** 12 级色阶里取第几级。最少的也有色,不然分不清"0 篇"和"没数据" */
function level(count: number): number {
  if (count <= 0) return 0
  return Math.max(1, Math.ceil((count / monthlyMax.value) * 4))
}

/** 每格的颜色。用 iOS 蓝做四级透明度梯度,深色模式下同样成立 */
function cellStyle(count: number): Record<string, string> {
  const step = level(count)
  if (step === 0) return { background: 'var(--bg-fill)' }
  const alpha = [0, 0.25, 0.45, 0.7, 1][step]
  return { background: `color-mix(in srgb, var(--ios-blue) ${alpha * 100}%, transparent)` }
}

function cellTitle(item: { year: number; month: number; entryCount: number }): string {
  return `${item.year} 年 ${item.month} 月:${item.entryCount} 篇`
}

/* ---------------------------------------------------------------------------
 *  交互
 * ------------------------------------------------------------------------- */

function openEntry(id: number): void {
  void router.push({ name: 'entry-detail', params: { id } })
}
</script>

<template>
  <div class="page">
    <NavBar title="回忆">
      <template #subtitle>
        <p class="page__summary">{{ todayText }}</p>
      </template>
    </NavBar>

    <div class="page__scroll">
      <template v-if="loading">
        <div class="section">
          <SkeletonBlock width="40%" height="15px" />
          <SkeletonBlock height="180px" radius="14px" />
        </div>
        <div class="section">
          <SkeletonBlock height="120px" radius="12px" />
        </div>
      </template>

      <template v-else-if="!hasData">
        <EmptyState
          icon="M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17ZM12 7v5l3.5 2"
          title="还没有可以回忆的东西"
          description="写满几篇日记,这一页就会开始长出统计和历史"
        >
          <template #action>
            <router-link class="btn btn--primary" :to="{ name: 'entry-new' }">写第一篇</router-link>
          </template>
        </EmptyState>
      </template>

      <template v-else>
        <!-- ---- 历史上的今天 ---- -->
        <section class="section">
          <h2 class="section__title">
            <span>历史上的今天</span>
            <span class="section__sub">{{ formatMonthDay(todayISO) }}</span>
          </h2>

          <div v-if="memories.length" class="memories">
            <button
              v-for="memory in memories"
              :key="memory.entryId"
              class="memory"
              type="button"
              @click="openEntry(memory.entryId)"
            >
              <div
                class="memory__cover"
                :style="memory.coverUrl ? undefined : { background: gradientFor(memory.entryId) }"
              >
                <img v-if="memory.coverUrl" :src="memory.coverUrl" alt="" loading="lazy" />
                <span class="memory__years">{{ numberToCN(memory.yearsAgo) }}年前</span>
              </div>

              <div class="memory__body">
                <p class="memory__caption">{{ memory.caption }}</p>
                <h3 class="memory__title ellipsis-2">{{ memory.title }}</h3>
                <p v-if="memory.excerpt" class="memory__excerpt ellipsis-2">{{ memory.excerpt }}</p>
                <p class="memory__meta">
                  {{ formatDateCN(memory.entryDate) }}
                  <template v-if="memory.locationName"> · {{ memory.locationName }}</template>
                </p>
              </div>
            </button>
          </div>

          <p v-else class="section__empty">
            今天还没有对得上的旧日子。等到明年的这一天,
            今天写的日记就会变成一张回忆卡。
          </p>
        </section>

        <!-- ---- 概览数字 ---- -->
        <section class="section">
          <div class="overview">
            <div v-for="item in overview" :key="item.label" class="overview__item">
              <strong class="overview__value">{{ item.value }}</strong>
              <span class="overview__label">{{ item.label }}</span>
            </div>
          </div>
          <p v-if="sinceText" class="section__foot">{{ sinceText }}</p>
        </section>

        <!-- ---- 年度 / 月度 ---- -->
        <section class="section">
          <div class="section__head">
            <h2 class="section__title">走过的路</h2>
            <div class="section__switch">
              <SegmentedControl
                v-model="view"
                :options="[
                  { value: 'year', label: '按年' },
                  { value: 'month', label: '按月' },
                ]"
              />
            </div>
          </div>

          <!-- 按年:每年一行条形 -->
          <div v-if="view === 'year'" class="bars">
            <div v-for="item in yearly" :key="item.year" class="bar">
              <span class="bar__label">{{ item.year }}</span>
              <div class="bar__track">
                <div
                  class="bar__fill"
                  :style="{ width: `${Math.max(4, (item.entryCount / yearlyMax) * 100)}%` }"
                />
              </div>
              <span class="bar__value">
                {{ item.entryCount }}
                <span class="bar__unit">篇</span>
              </span>
            </div>

            <div class="yearly-detail">
              <div v-for="item in yearly" :key="item.year" class="yearly-detail__item">
                <span class="yearly-detail__year">{{ item.year }}</span>
                <span class="yearly-detail__text">
                  {{ item.placeCount }} 个地方 · {{ item.dayCount }} 天
                </span>
              </div>
            </div>
          </div>

          <!-- 按月:最近 12 个月的格子,宽屏下一行排满 -->
          <div v-else class="months">
            <div
              v-for="item in monthly"
              :key="`${item.year}-${item.month}`"
              class="month-cell"
              :title="cellTitle(item)"
              :style="cellStyle(item.entryCount)"
            >
              <span class="month-cell__label">{{ item.month }}月</span>
            </div>

            <p class="months__legend">
              最近 12 个月,颜色越深写得越多(最多 {{ monthlyMax }} 篇 / 月)。
            </p>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-grouped);
}

.page__summary {
  margin-top: 2px;
  font-size: var(--text-subhead);
  color: var(--label-2);
}

/* ---- 区块 ---- */
.section {
  padding: var(--space-4) var(--gutter) 0;
}

.section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.section__title {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--text-title3);
  line-height: var(--lh-title3);
  font-weight: 700;
  letter-spacing: -0.3px;
  margin-bottom: var(--space-3);
}

.section__head .section__title {
  margin-bottom: 0;
}

.section__sub {
  font-size: var(--text-footnote);
  font-weight: 400;
  color: var(--label-2);
}

.section__switch {
  width: 180px;
}

.section__empty,
.section__foot {
  font-size: var(--text-footnote);
  line-height: 1.7;
  color: var(--label-2);
}

.section__foot {
  margin-top: var(--space-3);
  text-align: center;
  color: var(--label-3);
}

/*
  回忆卡:自适应网格。
  原来是横向 scroll-snap 的一条滑轨 —— 窄屏上"露出半张卡"是唯一的
  "还有更多"的提示。宽屏不需要这套:直接铺成网格,有几张一眼看完。
*/
.memories {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
}

.memory {
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  overflow: hidden;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
  text-align: left;
  transition: transform var(--dur-fast) var(--ease-ios),
    box-shadow var(--dur-fast) var(--ease-ios);
}

.memory:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-float);
}

.memory__cover {
  position: relative;
  height: 160px;
  background: var(--bg-fill);
}

.memory__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 「三年前」角标:回忆卡的核心信息就是这个时间跨度 */
.memory__years {
  position: absolute;
  left: var(--space-3);
  top: var(--space-3);
  padding: 3px var(--space-3);
  border-radius: var(--radius-full);
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  font-size: var(--text-caption1);
  font-weight: 600;
}

.memory__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.memory__caption {
  font-size: var(--text-caption1);
  color: var(--ios-orange);
  font-weight: 600;
}

.memory__title {
  font-size: var(--text-headline);
  line-height: 1.35;
  font-weight: 600;
}

.memory__excerpt {
  font-size: var(--text-subhead);
  line-height: 1.5;
  color: var(--label-2);
}

.memory__meta {
  margin-top: auto;
  padding-top: var(--space-2);
  font-size: var(--text-caption1);
  color: var(--label-3);
}

/* ---- 概览数字:四张分开的小卡,不再挤在同一块白底里 ---- */
.overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.overview__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-5) var(--space-3);
  border-radius: var(--radius-card);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
}

.overview__value {
  font-family: var(--font-num);
  font-size: var(--text-title1);
  line-height: 1.1;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.overview__label {
  font-size: var(--text-footnote);
  color: var(--label-2);
  text-align: center;
}

/* ---- 条形 ---- */
.bars {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
  border-radius: var(--radius-card);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
}

.bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.bar__label {
  flex-shrink: 0;
  width: 52px;
  font-family: var(--font-num);
  font-size: var(--text-subhead);
  font-weight: 600;
  color: var(--label-2);
}

.bar__track {
  flex: 1;
  min-width: 0;
  height: 12px;
  border-radius: var(--radius-full);
  background: var(--bg-fill);
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: var(--radius-full);
  background: linear-gradient(90deg, var(--ios-teal), var(--ios-blue));
  transition: width var(--dur-slow) var(--ease-ios);
}

.bar__value {
  flex-shrink: 0;
  min-width: 48px;
  text-align: right;
  font-family: var(--font-num);
  font-size: var(--text-subhead);
  font-weight: 600;
}

.bar__unit {
  font-family: var(--font);
  font-size: var(--text-caption2);
  font-weight: 400;
  color: var(--label-2);
}

.yearly-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 0.5px solid var(--separator);
}

.yearly-detail__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.yearly-detail__year {
  font-family: var(--font-num);
  font-size: var(--text-footnote);
  color: var(--label-2);
}

.yearly-detail__text {
  font-size: var(--text-footnote);
  color: var(--label-2);
}

/* ---- 月度格子:宽屏下一行排满 12 个月 ---- */
.months {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--space-2);
  padding: var(--space-5);
  border-radius: var(--radius-card);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
}

.month-cell {
  aspect-ratio: 1;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-fill);
  transition: background var(--dur-fast) var(--ease-standard);
}

.month-cell__label {
  font-family: var(--font-num);
  font-size: var(--text-caption1);
  color: var(--label-2);
}

.months__legend {
  grid-column: 1 / -1;
  margin-top: var(--space-2);
  font-size: var(--text-caption1);
  line-height: 1.6;
  color: var(--label-3);
}
</style>
