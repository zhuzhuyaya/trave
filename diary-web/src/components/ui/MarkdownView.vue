<script setup lang="ts">
/**
 * Markdown 渲染。
 *
 * 两条链:marked 把 Markdown 转 HTML,DOMPurify 洗掉所有危险内容。
 * 少了第二步,一篇写着 <img onerror=...> 的日记就能在读者浏览器里执行脚本 ——
 * 日记内容是可以从别处粘贴来的,必须当作不可信输入。
 *
 * 洗的时候保留 img 的 src:日记里的图片本来就是渲染的重点。
 */
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

marked.setOptions({
  // 代码块不自动高亮:引一个高亮库会让首屏包体翻倍,
  // 而日记里的代码块本来就少,等宽字体足够读
  gfm: true,
  breaks: true, // 单个换行也当换行 —— 写日记的人不会记得敲两个空格
})

const html = computed(() => {
  const raw = marked.parse(props.content ?? '', { async: false })
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ['target', 'rel', 'loading'],
    // 允许 img,但洗掉一切脚本、iframe、表单
    FORBID_TAGS: ['script', 'style', 'iframe', 'form', 'input', 'object', 'embed'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover'],
  })
})
</script>

<template>
  <div class="md user-selectable" v-html="html" />
</template>

<style scoped>
/* 正文区保留文字选中能力 —— 日记是要能被复制分享的 */
.md {
  word-wrap: break-word;
}
</style>
