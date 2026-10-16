<script setup lang="ts">
/**
 * 相册式图片网格。
 *
 * 列数跟着可用宽度自动排(auto-fill + minmax),
 * 最后一个是虚线框的加号 —— 它在编辑器里是"添加照片",在只读场景里不显示。
 *
 * 选文件这件事由网格自己做:加号格是个 `<label>`,里面藏着原生 file input,
 * 点它由浏览器去激活绑定的控件,**不走 JS 触发**。对外只抛 File[],
 * 上传是调用方的事 —— 网格不知道图片要传去哪,也不该知道。
 */
withDefaults(
  defineProps<{
    /** 每张图的 url。空串表示占位 */
    urls: string[]
    /** 是否显示末尾的"添加"格子 */
    editable?: boolean
    /** 允许的最大图片数,达到上限后隐藏添加格 */
    max?: number
  }>(),
  { editable: false, max: 18 },
)

const emit = defineEmits<{
  /** 用户选好了一批文件,交给调用方去上传 */
  files: [files: File[]]
  remove: [index: number]
  preview: [index: number]
}>()

function onPicked(event: Event): void {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  // 立刻清空,否则同一张图连着选两次不会触发 change
  input.value = ''
  if (picked.length) {
    emit('files', picked)
  }
}
</script>

<template>
  <div class="grid">
    <div v-for="(url, index) in urls" :key="`${url}-${index}`" class="grid__cell">
      <img class="grid__img" :src="url" alt="" loading="lazy" @click="emit('preview', index)" />

      <!-- 删除按钮靠右上角浮出 -->
      <button
        v-if="editable"
        class="grid__remove"
        type="button"
        aria-label="移除这张图片"
        @click.stop="emit('remove', index)"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M7 7l10 10M17 7L7 17" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
        </svg>
      </button>
    </div>

    <!--
      加号格用 <label> 而不是 <button>。
      file input 就藏在里面,点 label 由浏览器自己去激活它绑定的控件 ——
      浏览器原生行为,不经过 JS,也就没有"程序化 click 该不该被信任"这回事。
      之前是 button @click="input.click()",在某些浏览器上会被拦掉,
      表现就是点了毫无反应、连选择图片的窗口都不弹。
    -->
    <label v-if="editable && urls.length < max" class="grid__cell grid__add">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      </svg>
      <span class="grid__add-text">{{ urls.length }}/{{ max }}</span>
      <input
        class="grid__add-input"
        type="file"
        accept="image/*"
        multiple
        aria-label="添加照片"
        @change="onPicked"
      />
    </label>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  /*
    自适应列数。原来固定 3 列是照 iPhone 的相册排的;
    宽屏上列宽跟着内容栏走,auto-fill 才不会出现"最后一格空半块"。
  */
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--space-2);
}

.grid__cell {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--bg-fill);
  border-radius: var(--radius-cell);
}

.grid__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: zoom-in;
  transition: transform var(--dur) var(--ease-ios);
}

.grid__cell:hover .grid__img {
  transform: scale(1.05);
}

/*
  删除按钮:悬停到这张图上才浮出来。
  原来是"常驻在左上角"—— 手机上没有 hover,只能让它一直在。
  桌面端常驻一排小叉子会把照片本身遮得很难看。
*/
.grid__remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 深色半透明底:在任何图片上都看得清 */
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: #fff;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.grid__cell:hover .grid__remove,
.grid__remove:focus-visible {
  opacity: 1;
}

.grid__remove:hover {
  background: var(--ios-red);
}

/* 添加格:虚线框 + 加号。虚线而不是实线,明确表达"这里可以放东西" */
.grid__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  border: 1.5px dashed var(--label-4);
  color: var(--label-2);
  background: transparent;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-standard),
    border-color var(--dur-fast) var(--ease-standard);
}

.grid__add:hover {
  background: var(--bg-hover);
  border-color: var(--ios-blue);
  color: var(--ios-blue);
}

.grid__add-text {
  font-size: var(--text-caption2);
  font-family: var(--font-num);
}

/*
  藏在加号格里的 file input:缩到 1px 且透明,不参与布局也不接点击。
  点击由外面那层 <label> 负责激活它 —— 所以这里不需要铺满、也不需要 z-index,
  和相册里其他格子之间不会有任何"谁盖住谁"的问题。
*/
.grid__add-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
</style>
