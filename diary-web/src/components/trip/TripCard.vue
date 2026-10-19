<script setup lang="ts">
/**
 * 行程卡片。
 *
 * 相册式的封面 + 标题 + 一行"3 篇 · 2024.03.12 - 03.16"。
 * 左滑可见"编辑/归档"(暂以长按菜单代替),这里只做展示与进入。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { TripVO } from '@/api/types'
import { gradientFor } from '@/utils/misc'
import { formatRange, daysBetween } from '@/utils/time'

const props = defineProps<{ trip: TripVO }>()

const router = useRouter()

const coverStyle = computed(() => ({ background: gradientFor(`trip-${props.trip.id}`) }))

const rangeText = computed(() => formatRange(props.trip.startDate, props.trip.endDate))

const days = computed(() => daysBetween(props.trip.startDate, props.trip.endDate))

function open(): void {
  void router.push({ name: 'trip-detail', params: { id: props.trip.id } })
}
</script>

<template>
  <article class="trip-card" @click="open">
    <div class="trip-card__cover-wrap">
      <img
        v-if="trip.coverUrl"
        class="trip-card__cover"
        :src="trip.coverUrl"
        :alt="trip.title"
        loading="lazy"
      />
      <div v-else class="trip-card__cover" :style="coverStyle" />

      <!--
        角标排在封面左上角一行:私密行程给用户一个明确的提示。
        原来是"私密在左、已归档在右"贴两个角,但右上角要留给
        列表页悬停浮出的删除按钮,两个角标就都收到左边来。
      -->
      <div class="trip-card__badges">
        <span v-if="trip.visibility === 'PRIVATE'" class="trip-card__badge" title="仅自己可见">
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="4.5" y="10" width="15" height="10.5" rx="2.5" stroke="currentColor" stroke-width="2" />
            <path d="M8 10V7.5a4 4 0 1 1 8 0V10" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </span>

        <span v-if="trip.archived" class="trip-card__badge trip-card__badge--muted">已归档</span>
      </div>
    </div>

    <div class="trip-card__body">
      <h3 class="trip-card__title ellipsis">{{ trip.title }}</h3>

      <p v-if="trip.description" class="trip-card__desc ellipsis">{{ trip.description }}</p>

      <div class="trip-card__meta">
        <span class="trip-card__stat">
          <strong>{{ trip.entryCount }}</strong> 篇
        </span>
        <span v-if="rangeText" class="trip-card__dot">·</span>
        <span v-if="rangeText" class="trip-card__range">{{ rangeText }}</span>
        <span v-if="days > 1" class="trip-card__dot">·</span>
        <span v-if="days > 1">{{ days }} 天</span>
      </div>

      <p v-if="trip.locationSummary" class="trip-card__places ellipsis">{{ trip.locationSummary }}</p>
    </div>
  </article>
</template>

<style scoped>
.trip-card {
  /* 间距由列表的 grid gap 提供,卡片自己不带外边距 ——
     否则网格的间距会变成"gap + margin",列与列之间看起来比行之间宽。 */
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-ios),
    box-shadow var(--dur-fast) var(--ease-ios);
}

/* 鼠标悬停时轻微上浮 + 阴影加深,是"这张卡可以点进去"的桌面端提示 */
.trip-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-float);
}

.trip-card__cover-wrap {
  position: relative;
  /* 3:2 略宽于常规相册比例,横构图的风光照更常出现在这里 */
  aspect-ratio: 3 / 2;
  overflow: hidden;
}

.trip-card__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.trip-card__badges {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.trip-card__badge {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 3px 7px;
  border-radius: var(--radius-full);
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  font-size: var(--text-caption2);
  line-height: 1.3;
}

.trip-card__badge--muted {
  background: rgba(255, 255, 255, 0.22);
}

/* 简介长短不一,让正文块撑满剩余高度,一格里的卡片才不会一个高一个矮 */
.trip-card__body {
  flex: 1;
  padding: var(--space-3) var(--space-4) var(--space-4);
}

.trip-card__title {
  font-size: var(--text-title3);
  line-height: var(--lh-title3);
  font-weight: 600;
  letter-spacing: -0.3px;
}

.trip-card__desc {
  margin-top: 2px;
  font-size: var(--text-footnote);
  line-height: var(--lh-footnote);
  color: var(--label-2);
}

.trip-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-size: var(--text-footnote);
  line-height: var(--lh-footnote);
  color: var(--label-2);
}

.trip-card__stat strong {
  font-family: var(--font-num);
  font-weight: 600;
  color: var(--label);
}

.trip-card__dot {
  color: var(--label-3);
}

.trip-card__range {
  font-family: var(--font-num);
  letter-spacing: -0.2px;
}

.trip-card__places {
  margin-top: var(--space-1);
  font-size: var(--text-footnote);
  line-height: var(--lh-footnote);
  color: var(--ios-blue);
}
</style>
