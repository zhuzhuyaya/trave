import '../lib/browsers-path.mjs'
import { chromium } from 'playwright'
import { readdirSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const dir = join(tmpdir(), `h2v-probe-${Date.now()}`)

const browser = await chromium.launch({ headless: true, channel: 'chromium' })
const context = await browser.newContext({
  viewport: { width: 640, height: 360 },
  recordVideo: { dir, size: { width: 640, height: 360 } },
})
const page = await context.newPage()
await page.goto('data:text/html,<body style="background:red"><h1>hi</h1></body>')
await page.waitForTimeout(1500)
const video = page.video()
console.log('video object:', video ? 'yes' : 'null')
await context.close()
try {
  const p = await video.path()
  console.log('video.path() ->', p, 'exists:', existsSync(p))
} catch (e) {
  console.log('video.path() THREW:', e.message.split('\n').slice(0, 6).join(' | '))
}
console.log('dir contents:', existsSync(dir) ? readdirSync(dir) : 'NO DIR', dir)
await browser.close()
