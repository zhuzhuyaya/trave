<script setup lang="ts">
/**
 * 日记卡片。
 *
 * 时间线里的一条。
 *
 * 版式:左侧日期竖排(年/月/日三段) + 右侧内容。
 * 日期放左边而不是标题下面,是因为时间线的核心信息是"什么时候",
 * 而且竖排的日期形成了整齐的左对齐列,扫一眼就能定位到某个月。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { EntrySummaryVO } from '@/api/types'
import { gradientFor } from '@/utils/misc'

const props = defineProps<{ entry: EntrySummaryVO; score?: number | null }>()

const router = useRouter()

/** 语义搜索结果才有:cosine 0~1 转成给人看的百分比 */
const scorePercent = computed(() =>
  props.score == null ? null : Math.round(props.score * 100),
)

const dateParts = computed(() => {
  const [year, month, day] = props.entry.entryDate.split('-')
  return { year, month: String(Number(month)), day: String(Number(day)) }
})

const placeholderStyle = computed(() => ({
  background: gradientFor(props.entry.id),
}))

function open(): void {
  void router.push({ name: 'entry-detail', params: { id: props.entry.id } })
}
</script>

<template>
  <article class="card" @click="open">
    <!-- 左:竖排日期 -->
    <div class="card__date">
      <span class="card__day">{{ dateParts.day }}</span>
      <span class="card__month">{{ dateParts.month }} 月</span>
      <span class="card__year">{{ dateParts.year }}</span>
    </div>

    <div class="card__main">
      <!--
        封面。没有图时用按 id 生成的渐变块撑住版式 ——
        列表高度参差不齐会比"有一些色块"更难读。
      -->
      <div class="card__cover-wrap">
        <img
          v-if="entry.coverUrl"
          class="card__cover"
          :src="entry.coverUrl"
          :alt="entry.title"
          loading="lazy"
        />
        <div v-else class="card__cover card__cover--placeholder" :style="placeholderStyle" />

        <span v-if="entry.photoCount > 1" class="card__count">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="3" y="6" width="18" height="14" rx="2" stroke="currentColor" stroke-width="2" />
            <path d="M8 3h11a2 2 0 0 1 2 2v11" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
          {{ entry.photoCount }}
        </span>
      </div>

      <div class="card__body">
        <h3 class="card__title ellipsis-2">{{ entry.title }}</h3>

        <p v-if="entry.excerpt" class="card__excerpt ellipsis-2">{{ entry.excerpt }}</p>

        <div class="card__meta">
          <span v-if="scorePercent !== null" class="card__meta-item card__meta-item--score">
            相关度 {{ scorePercent }}%
          </span>

          <span v-if="entry.locationName" class="card__meta-item">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 21s-6.5-5.5-6.5-10.5a6.5 6.5 0 1 1 13 0C18.5 15.5 12 21 12 21Z"
                stroke="currentColor"
                stroke-width="2"
              />
              <circle cx="12" cy="10.5" r="2" stroke="currentColor" stroke-width="2" />
            </svg>
            {{ entry.locationName }}
          </span>

          <span v-if="entry.weather" class="card__meta-item">{{ entry.weather }}</span>

          <span v-if="entry.mood" class="card__meta-item card__meta-item--mood">{{ entry.mood }}</span>
        </div>

        <div v-if="entry.tags.length" class="card__tags">
          <span v-for="tag in entry.tags.slice(0, 3)" :key="tag" class="card__tag">#{{ tag }}</span>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  position: relative;
  display: flex;
  gap: var(--space-4);
  padding: var(--space-4) var(--gutter);
  background: var(--bg-elevated);
  /* 卡片之间用发丝线分隔而不是拉开间距:
     时间线是连续的流水账,分开成一块块反而割裂了时间感 */
  border-bottom: 0.5px solid var(--separator);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-standard);
}

.card:hover {
  background: var(--bg-hover);
}

/*
  悬停时左侧浮出一条蓝色强调条。
  桌面端光标不会告诉用户"这一整行是一个链接",这条竖线补上这个信息,
  而且它落在行的左边缘,不会推动任何内容。
*/
.card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--ios-blue);
  transform: scaleY(0);
  transform-origin: center;
  transition: transform var(--dur-fast) var(--ease-ios);
}

.card:hover::before {
  transform: scaleY(1);
}

/* ---- 左侧日期列 ---- */
.card__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 56px;
  flex-shrink: 0;
  padding-top: 2px;
}

.card__day {
  font-family: var(--font-num);
  font-size: 26px;
  line-height: 1.1;
  font-weight: 600;
  letter-spacing: -0.5px;
}

.card__month,
.card__year {
  font-size: var(--text-caption1);
  line-height: 16px;
  color: var(--label-2);
}

/* ---- 右侧主体 ---- */
.card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: var(--space-4);
}

.card__cover-wrap {
  position: relative;
  width: 92px;
  height: 92px;
  flex-shrink: 0;
  border-radius: var(--radius-cell);
  overflow: hidden;
}

.card__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card__cover--placeholder {
  /* 空封面也要有内容感,不能是一块灰 */
  opacity: 0.9;
}

.card__count {
  position: absolute;
  right: 4px;
  bottom: 4px;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 5px;
  border-radius: var(--radius-full);
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 10px;
  font-family: var(--font-num);
  line-height: 1;
}

.card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.card__title {
  font-size: var(--text-headline);
  line-height: 1.35;
  font-weight: 600;
  letter-spacing: -0.2px;
}

.card__excerpt {
  font-size: var(--text-subhead);
  line-height: 1.5;
  color: var(--label-2);
}

.card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: 2px;
  font-size: var(--text-caption1);
  line-height: var(--lh-caption1);
  color: var(--label-3);
}

.card__meta-item {
  display: flex;
  align-items: center;
  gap: 2px;
}

.card__meta-item--mood {
  color: var(--ios-orange);
  font-weight: 500;
}

.card__meta-item--score {
  color: var(--ios-blue);
  font-weight: 500;
}

.card__tags {
  display: flex;
  gap: var(--space-2);
  margin-top: 2px;
}

.card__tag {
  font-size: var(--text-caption1);
  line-height: var(--lh-caption1);
  color: var(--ios-blue);
}
</style>
