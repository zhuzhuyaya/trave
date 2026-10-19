<script setup lang="ts">
/**
 * 行程封面选择器 —— 一块 3:2 的方框,点它或往里拖一张图就换封面。
 *
 * 新建行程和编辑行程都要用,所以抽成组件:上传、预览、移除这三条逻辑
 * 抄两遍必然会长歪,而且比例、拖拽高亮这些细节也没法保证一致。
 *
 * 对外传的是**图片 id** 而不是 url —— 库里 trips.cover_photo_id 存的就是 id。
 * 传 url 的话后端还得反查一次,而且图片被删之后无从判断该不该退回兜底。
 *
 * 预览地址也是父组件持有的(props.previewUrl)。上传接口返回的 url 父组件拿不到,
 * 所以这里在 emit id 的同时把 url 一并 emit 上去,**组件自己不存任何状态**。
 * 自己存的话,面板被取消关闭时那份预览就留在组件里了 —— 下次打开
 * 会显示一张早就不要的图,而且父组件的重置管不到它。
 */
import { computed, ref } from 'vue'
import { photoApi } from '@/api/photo'
import { toMessage } from '@/api/http'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  /** 选中/已上传的图片 id;null 表示没有自定义封面 */
  modelValue: number | null
  /** 当前封面地址。新建态一直是 null,编辑态由父组件填 */
  previewUrl: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  'update:previewUrl': [value: string | null]
}>()

const ui = useUiStore()

const uploading = ref(false)
const dragging = ref(false)

const shownUrl = computed(() => props.previewUrl)

async function onPicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 立刻清空,否则同一张图连着选两次不会触发 change
  input.value = ''
  if (file) {
    await upload(file)
  }
}

async function upload(file: File): Promise<void> {
  uploading.value = true
  try {
    const photo = await photoApi.upload(file)
    emit('update:modelValue', photo.id)
    emit('update:previewUrl', photo.url)
  } catch (error) {
    ui.error(toMessage(error))
  } finally {
    uploading.value = false
  }
}

/**
 * 移除只改内存状态,不调 photoApi.remove 删服务器上的文件。
 *
 * 与 EntryEditorView.removePhoto 同一个取舍:上传接口本来就是"先落盘、
 * 保存时才挂到实体上",传了不用的图成为孤儿文件是可接受的代价
 * (见 EntryPhoto 的类注释)。真去删反而有风险 —— 万一那张图还挂在别处。
 */
function remove(): void {
  emit('update:modelValue', null)
  emit('update:previewUrl', null)
}

/* ---------------------------------------------------------------------------
 *  从文件管理器拖进来
 *
 *  和编辑器里那套是同一份逻辑。只在真的拖着文件时才高亮 ——
 *  拖着一段文字或一个链接路过时不该有反应。
 * ------------------------------------------------------------------------- */

function onDragOver(event: DragEvent): void {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragging.value = true
}

/** 指针移到子元素上也会触发 dragleave,用 relatedTarget 判断是不是还在框内,否则高亮会一直闪 */
function onDragLeave(event: DragEvent): void {
  const next = event.relatedTarget as Node | null
  if (next && (event.currentTarget as Node).contains(next)) return
  dragging.value = false
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) {
    void upload(file)
  }
}
</script>

<template>
  <div class="cover">
    <label
      class="cover__box"
      :class="{
        'cover__box--empty': !shownUrl,
        'cover__box--dragging': dragging,
        'cover__box--uploading': uploading,
      }"
      @dragover.prevent="onDragOver"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <img v-if="shownUrl" class="cover__img" :src="shownUrl" alt="行程封面" />

      <span v-else class="cover__hint">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        选择一张封面图
      </span>

      <span v-if="uploading" class="cover__uploading">正在上传...</span>

      <!--
        file input 藏在 <label> 里,缩到 1px 且透明。
        点方框(方框就是那个 label)由浏览器自己去激活这个控件 ——
        浏览器原生行为,不经 JS,也就没有"程序化 click 该不该被信任"这回事。

        两版之前是"点 button → fileInput.click()",在某些浏览器上会被拦掉,
        表现就是点了毫无反应;上一版改成"input 铺满整块方框",虽然能点,
        但它压在图片和提示文案之上,删除按钮还得专门抬 z-index 才点得到。
        藏起来交给 label 激活,这两个问题一起没了。
      -->
      <input
        class="cover__input"
        type="file"
        accept="image/*"
        :aria-label="shownUrl ? '更换封面图' : '选择一张封面图'"
        :disabled="uploading"
        @change="onPicked"
      />
    </label>

    <!-- 悬停才浮出,和相册网格里的删除按钮一个做法:常驻会一直压在画面上 -->
    <button
      v-if="shownUrl && !uploading"
      class="cover__remove"
      type="button"
      aria-label="移除封面图"
      title="移除封面图"
      @click="remove"
    >
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M7 7l10 10M17 7L7 17" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
      </svg>
    </button>

    <p class="cover__tip">
      不设封面时,自动用这段行程里最新一篇日记的首图;都没有才退回渐变底色。
    </p>
  </div>
</template>

<style scoped>
.cover {
  position: relative;
}

.cover__box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  /* 和 TripCard 的封面同比例 —— 面板里看到的裁切结果就是列表里看到的 */
  aspect-ratio: 3 / 2;
  overflow: hidden;
  border-radius: var(--radius-card);
  background: var(--bg-fill);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

/* 空态:虚线框 + 加号,和相册网格末尾那个"添加"格是同一种表达 */
.cover__box--empty {
  border: 1.5px dashed var(--label-4);
  color: var(--label-2);
}

.cover__box--empty:hover {
  background: var(--bg-hover);
  border-color: var(--ios-blue);
  color: var(--ios-blue);
}

.cover__box--dragging {
  border-style: solid;
  border-color: var(--ios-blue);
  background: color-mix(in srgb, var(--ios-blue) 10%, transparent);
  color: var(--ios-blue);
}

/*
  上传中:label 本身不能 disabled,里面的 input 是 disabled 的,
  点下去不会有任何反应 —— 光标得跟着说实话,否则用户会以为点漏了。
*/
.cover__box--uploading {
  cursor: default;
}

.cover__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover__hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-subhead);
}

/* 上传中的遮罩要盖住下面的图片和文案 */
.cover__uploading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: var(--text-subhead);
}

/* 删除按钮是 label 的兄弟节点,不在 label 层里 —— 点它不会连带触发选图 */
.cover__remove {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  /* 深色半透明底:不管底下是亮是暗都看得清 */
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: #fff;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-standard),
    background var(--dur-fast) var(--ease-standard);
}

.cover:hover .cover__remove,
.cover__remove:focus-visible {
  opacity: 1;
}

.cover__remove:hover {
  background: var(--ios-red);
}

/* 兜底规则得说出来 —— 否则用户下次打开发现行程自己长了张封面会莫名其妙 */
.cover__tip {
  margin-top: var(--space-2);
  font-size: var(--text-caption2);
  line-height: 1.5;
  color: var(--label-3);
}

/*
  藏在方框里的 file input。

  缩到 1px 且透明:不占位置、不参与命中测试,也不需要 z-index ——
  点击由外面那个 <label class="cover__box"> 负责,浏览器会自己去激活它绑定的控件。
  用户看到的是"点这张图就能换一张",但拿点击的一直是浏览器,不是 JS。
*/
.cover__input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.cover__input:disabled {
  cursor: default;
}
</style>
