# html2video

把一个带动画的 HTML 页面渲染成 MP4。无头 Chromium 负责出画，ffmpeg 负责编码。

工具本身与仓库里的业务代码无关，可以单独拷走用。

```
tools/html2video/
  html2video.mjs        CLI 入口
  lib/
    browsers-path.mjs   浏览器缓存适配(见下)
    browser.mjs         冻结动画、字体等待、时长探测、逐帧定位
    prepare.mjs         多场景内联(file:// 下 fetch 被禁)
    ffmpeg.mjs          编码、拼接、校验
  scripts/
    concat.mjs          拼接多个片段
    probe-record.mjs    排查"录制为空"用的小工具
  sample/
    demo.html           三场景多组合示例(外链字体 + CSS 关键帧 + JS 时间轴)
    single.html         单文件纯 CSS 动画示例
```

## 快速开始

```bash
cd tools/html2video
npm install

# 渲染示例
node html2video.mjs sample/demo.html --out out/demo.mp4

# 确定性帧步进(帧率精确、不受机器负载影响)
node html2video.mjs sample/demo.html --out out/demo.mp4 --mode b
```

## 命令行

```
node html2video.mjs <input.html | http(s)://url> --out out.mp4 [options]
```

| 选项 | 说明 |
| --- | --- |
| `-o, --out <file>` | 输出 MP4 路径(必填) |
| `--width` / `--height` | 视口尺寸,默认 1920×1080 |
| `--fps <n>` | 输出帧率,默认 30 |
| `--duration <s\|auto>` | 输出时长秒数,或 `auto`(默认) |
| `--mode <a\|b>` | `a` 实时录制,`b` 帧步进(默认 `a`) |
| `--font-timeout <ms>` | 字体等待硬上限,默认 8000 |
| `--lead-in <ms>` | 手动指定裁掉的死前导,覆盖自动探测 |
| `--keep-webm <file>` | 保留原始录制(WebM)以便排查 |
| `--chromium <path>` | 指定 Chromium 可执行文件 |
| `--frames-dir <dir>` | 帧步进模式的 PNG 输出目录 |
| `--keep-frames` | 编码后不删 PNG 帧 |
| `--no-verify` | 跳过编码后校验 |
| `--verbose` | 打印更多细节 |

拼接:

```bash
node scripts/concat.mjs out.mp4 seg1.mp4 seg2.mp4            # 同编码器,流拷贝
node scripts/concat.mjs out.mp4 seg1.mp4 seg2.mp4 --reencode # 混合来源,重新编码
```

## 管线

```
launch → 冻结在第 0 帧 → 让录制起点对齐动画起点 → 录制 → 裁剪并编码 → 校验
```

不变式:**动画的任何一个像素都不能在录制就绪之前渲染出来,并且录制不能在所有影响布局的资源加载完成之前开始。**

### 1. 冻结动画必须在页面解析之前

纯 CSS `@keyframes` 从元素被样式化的那一刻就开始跑,没有 JS 触发点可以拦截。
如果先 `goto()` 再等字体,CSS 时间轴早就跑完了 —— 录下来的是字体中途换脸、
开场几拍丢失。

`addInitScript` 注入的冻结样式会在文档自己的脚本之前、CSS 生效之前执行:

```js
*, *::before, *::after { animation-play-state: paused !important; }
```

### 2. 用 `domcontentloaded` 加载,不要用 `load`

`load` 会等所有外部资源,包括没有 CORS 头的跨域视频。Chromium 会重试约 4 秒,
这 4 秒就以一个冻结的首屏被烤进录像里。

### 3. 字体要按顺序等

`document.fonts.ready` 单独用**不够**。在 `domcontentloaded` 下,
Google Fonts 的 `<link>` 通常还没返回,它的 `@font-face` 规则根本不在
`document.fonts` 里 —— `fonts.ready` 看到空集合,瞬间 resolve,于是字体在
镜头前换脸。

正确顺序:等 `<link rel=stylesheet>` load → 对每个 face 调 `face.load()` →
`fonts.ready` → 两个 rAF。

### 4. 用标记块确定录制起点

**墙钟时间对不上。** 录屏是从 Chromium 吐出第一帧开始的,可能比建页晚好几秒。
在本仓库实测:墙钟说动画在 +7237ms,而录像里实际在 +3957ms —— 差 3.3 秒,
照墙钟裁会直接砍掉动画前半段。

所以录制前在左上角闪一个 24×24 的品红色块,持续 250ms,然后在**释放动画之前**
把它拿掉。事后在录像里找这个块,就能算出墙钟到录像时间的精确偏移。
因为块在释放前就没了,成片里不会出现它。

标记找不到时(模板盖住了角落、顶层有 dialog),退回到"最长持续运动段"启发式。

### 5. 时长探测要跳过无限循环

取"最长的非无限 CSS 动画"和"最长的有限 JS 时间轴"两者的最大值。
**不要**用 `gsap.globalTimeline.totalDuration()` —— 一个 `repeat: -1` 的
补间会让它变成约 1e10 秒。遍历子节点并跳过 `repeat() === -1` 的那些。

CSS 侧要跳过 `animation-iteration-count: infinite` 的条目。

### 6. 编码

```
ffmpeg -y -ss <leadIn-120ms> -i raw.webm -t <duration> -r <fps> \
  -c:v libx264 -pix_fmt yuv420p -preset medium -crf 20 -movflags +faststart out.mp4
```

`-ss` 放在 `-i` 前面,直接从输入端丢掉死前导。回退 120ms:多带几帧静止画面
看不出来,少一帧开场节拍一眼就穿帮。

### 7. 校验

"文件写出来了"不等于成功 —— 时长不对、字体换脸、死前录,产出的都是**能正常
播放**的 MP4。每次都跑:

- `ffprobe` 读实际时长,与期望相差不超过 50ms
- `ffmpeg -f null -` 完整解码一遍
- 逐帧 luma 序列:全片 luma 恒定说明是静止画面,不是动画
- 抽 0.05s / 中段 / 末尾三帧,人工看字体对不对

## 两种模式

| | A:实时录制 | B:帧步进 |
| --- | --- | --- |
| 流畅度 | 可变约 25fps,负载高时掉帧 | 精确 fps,每帧都完整渲染 |
| 时长精度 | `-ss` 裁 + `-t`,±1 帧 | 构造上精确(`帧数 / fps`) |
| 耗时 | 等于片长 | 约每秒抓 2–10 帧,长片更慢 |
| `<video>` / 定时器 / rAF 物理 | 可以 | **不行**,需要虚拟时钟 |
| 需要裁前导 | 需要 | 不需要 |
| 受机器负载影响 | 会,卡顿会烤进文件 | 不会 |

模式 B 要求页面的运动是**可定位**的:CSS `@keyframes`(用 `document.getAnimations()`
加 `currentTime`)、WAAPI、GSAP(`gsap.globalTimeline.time(t)`)都行。
`<video>` 元素、`setTimeout` 编排的场景、rAF 驱动的物理都不行。

时间只能**单调向前**推进。`suppressEvents` 要传 `false`,否则 DOM 会被
`onStart`/`onComplete` 回调改乱。

## 多场景模板

有些模板把场景拆成子文件,用占位符在运行时拉进来:

```html
<div data-composition-src="scenes/intro.html"></div>
```

Chromium **禁止 `file://` 下的 `fetch()`**,所以无头渲染时这些场景会是空的。
工具在 Node 侧把子文件全部内联进入口 HTML,再写一份 `.tmp.html` 加载。

子文件里的 `<script>` 会被重新执行一次 —— 克隆出来的 `<script>` 节点不会自己跑。
注意 `<script>` 写在 `<template>` 旁边(而不是里面)时,重新执行前必须先把它
从游离的 holder 里取出来,否则它会被留在原地永不执行。

## 浏览器缓存适配

Playwright 锁死精确的浏览器构建号,缓存里没有就硬失败。而机器上缓存的常常是
另一个构建(能正常用 CDP 驱动,只是目录名对不上)。

`lib/browsers-path.mjs` 会在 `tools/html2video/.browsers/` 里把"需要的构建号"
链接到缓存里实际存在的构建,并把 `PLAYWRIGHT_BROWSERS_PATH` 指过去。
不改全局缓存,也不用重新下载 Chromium。

**注意:Playwright 自带的 ffmpeg 必须版本严格匹配。** 录制用的 ffmpeg 是
Playwright 私有构建,不是系统 ffmpeg;老版本会拒绝新版本的参数
(`Error splitting the argument list: Option not found`),表现为**录像文件
根本不生成**。缺版本时补一下:

```bash
npx playwright install ffmpeg
```

## 排查

**成片开头是空的 / 时长不对** —— 用 `--keep-webm out/raw.webm` 留下原始录制,
再抽帧看动画到底从哪一帧开始:

```bash
ffmpeg -ss 4.2 -i out/raw.webm -frames:v 1 -y f.png
```

**录像文件是空的** —— 大概率是 Playwright 的 ffmpeg 版本不匹配(见上)。
`node scripts/probe-record.mjs` 可以单独验证录制链路通不通。

**字体在片头闪一下才变** —— 字体等待没生效。确认页面用的是 `<link>` 外链字体,
而不是 JS 动态插入的 `@font-face`。

**动画从中途开始 / 结尾被砍** —— 看日志里 `lead-in` 那行的取值和它是
`(marker)` 还是 `(motion fallback)`。`motion fallback` 说明标记没被找到,
对齐精度会差一些,可以用 `--lead-in <ms>` 手动指定。
