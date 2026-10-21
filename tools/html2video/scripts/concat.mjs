/**
 * Concatenate rendered segments into one MP4.
 *
 *   node scripts/concat.mjs out.mp4 seg1.mp4 seg2.mp4 [--reencode] [--fps 30]
 *
 * Default (stream copy) is correct when every segment came from the same
 * encoder with a matching timebase. Pass --reencode when the segments came
 * from different rendering engines: the concat demuxer accumulates bad PTS
 * and an 8s output can silently become 35s, and no amount of -vsync cfr
 * fixes it because the broken PTS are already in the stream.
 */
import { resolve } from 'node:path'
import { existsSync } from 'node:fs'
import { concatSegments, verify } from '../lib/ffmpeg.mjs'

const args = process.argv.slice(2)
let reencode = false
let fps = 30
const positional = []
for (let i = 0; i < args.length; i++) {
  const a = args[i]
  if (a === '--reencode') {
    reencode = true
  } else if (a === '--fps') {
    fps = parseInt(args[++i], 10)
  } else if (a === '-h' || a === '--help') {
    console.log(
      'Usage: node scripts/concat.mjs <out.mp4> <seg1.mp4> [seg2.mp4 ...] ' +
        '[--reencode] [--fps 30]',
    )
    process.exit(0)
  } else {
    positional.push(a)
  }
}

if (positional.length < 2) {
  console.error('need an output path and at least one segment')
  process.exit(1)
}

const outPath = resolve(positional[0])
const segments = positional.slice(1).map((p) => {
  const abs = resolve(p)
  if (!existsSync(abs)) throw new Error(`segment not found: ${abs}`)
  return abs
})

await concatSegments(segments, outPath, { reencode, fps })
console.log(`[concat] wrote ${outPath} (${segments.length} segments, reencode=${reencode})`)

const report = await verify({ videoPath: outPath })
for (const w of report.warnings) console.log(`[concat] WARN ${w}`)
console.log(
  `[concat] duration ${report.checks.durationS?.toFixed(3)}s, ` +
    `${report.checks.frames} frames`,
)
if (!report.ok) process.exit(1)
