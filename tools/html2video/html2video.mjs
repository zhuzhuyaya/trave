#!/usr/bin/env node
/**
 * html2video — render an animated HTML page to MP4.
 *
 * The pipeline, in order:
 *   launch -> hold @ frame 0 -> align capture start with animation start
 *          -> record -> trim & encode -> verify
 *
 * The invariant: no pixel of the animation renders before capture is ready,
 * and capture never starts before every layout-affecting asset has loaded.
 *
 * Modes:
 *   a  real-time recordVideo  — wall clock = clip length, ~25fps variable.
 *   b  deterministic stepping  — exact fps, every frame rendered, no trim.
 */

// MUST be the first import: playwright-core computes its browser registry
// directory at module-evaluation time, so the shim has to set
// PLAYWRIGHT_BROWSERS_PATH before `playwright` is evaluated.
import './lib/browsers-path.mjs'

import { chromium } from 'playwright'
import { mkdir, readdir, rm, stat, copyFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, resolve, dirname, extname } from 'node:path'
import { tmpdir } from 'node:os'
import { pathToFileURL } from 'node:url'

import { prepareSourceHtml } from './lib/prepare.mjs'
import {
  FREEZE_SCRIPT,
  UNFREEZE_SCRIPT,
  FONT_WAIT_SCRIPT,
  DURATION_PROBE_SCRIPT,
  PIN_AT_ZERO_SCRIPT,
  SEEK_SCRIPT,
  ANIMATION_COUNT_SCRIPT,
  MARKER_ON_SCRIPT,
  MARKER_OFF_SCRIPT,
  resolveChromiumExecutable,
} from './lib/browser.mjs'
import {
  encodeWebm,
  encodeFrames,
  verify,
  ffmpeg,
  ffprobe,
  cornerChromaSeries,
} from './lib/ffmpeg.mjs'

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

const USAGE = `
html2video — render an animated HTML page to MP4

Usage:
  node html2video.mjs <input.html | http(s)://url> --out out.mp4 [options]

Options:
  -o, --out <file>          Output MP4 path (required)
      --width <px>          Viewport width  (default 1920)
      --height <px>         Viewport height (default 1080)
      --fps <n>             Output frame rate (default 30)
      --duration <s|auto>   Output length in seconds, or 'auto' (default auto)
      --mode <a|b>          a = real-time record, b = frame stepping (default a)
      --font-timeout <ms>   Hard cap for the font wait (default 8000)
      --lead-in <ms>        Override the measured lead-in trim
      --keep-webm <file>    Save the raw recording (mode a) for inspection
      --chromium <path>     Chromium executable (default: auto-detect)
      --supersample         Mode b only: capture at 2x and downscale
      --frames-dir <dir>    Where mode b writes PNG frames
      --keep-frames         Do not delete the PNG frames after encoding
      --no-verify           Skip the post-encode verification pass
      --verbose             Print timing detail
  -h, --help                Show this help
`

function parseArgs(argv) {
  const opts = {
    input: null,
    out: null,
    width: 1920,
    height: 1080,
    fps: 30,
    duration: 'auto',
    mode: 'a',
    fontTimeout: 8000,
    leadIn: null,
    keepWebm: null,
    chromium: null,
    supersample: false,
    framesDir: null,
    keepFrames: false,
    verify: true,
    verbose: false,
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const need = () => {
      const v = argv[++i]
      if (v === undefined) throw new Error(`missing value for ${a}`)
      return v
    }
    switch (a) {
      case '-o':
      case '--out':
        opts.out = need()
        break
      case '--width':
        opts.width = parseInt(need(), 10)
        break
      case '--height':
        opts.height = parseInt(need(), 10)
        break
      case '--fps':
        opts.fps = parseInt(need(), 10)
        break
      case '--duration':
        opts.duration = need()
        break
      case '--mode':
        opts.mode = need().toLowerCase()
        break
      case '--font-timeout':
        opts.fontTimeout = parseInt(need(), 10)
        break
      case '--lead-in':
        opts.leadIn = parseInt(need(), 10)
        break
      case '--keep-webm':
        opts.keepWebm = need()
        break
      case '--chromium':
        opts.chromium = need()
        break
      case '--supersample':
        opts.supersample = true
        break
      case '--frames-dir':
        opts.framesDir = need()
        break
      case '--keep-frames':
        opts.keepFrames = true
        break
      case '--no-verify':
        opts.verify = false
        break
      case '--verbose':
        opts.verbose = true
        break
      case '-h':
      case '--help':
        process.stdout.write(USAGE)
        process.exit(0)
        break
      default:
        if (a.startsWith('-')) throw new Error(`unknown option: ${a}`)
        if (opts.input === null) opts.input = a
        else throw new Error(`unexpected argument: ${a}`)
    }
  }
  if (!opts.input) throw new Error('missing <input.html | url>')
  if (!opts.out) throw new Error('missing --out <file.mp4>')
  if (opts.mode !== 'a' && opts.mode !== 'b')
    throw new Error(`--mode must be 'a' or 'b' (got ${opts.mode})`)
  if (!Number.isFinite(opts.fps) || opts.fps <= 0)
    throw new Error('--fps must be a positive integer')
  return opts
}

const log = (...a) => console.log('[html2video]', ...a)

// ---------------------------------------------------------------------------
// Lead-in measurement
// ---------------------------------------------------------------------------

/**
 * Locate the animation's start in the recording.
 *
 * The screencast begins whenever Chromium produces its first frame, which can
 * be seconds after the page was created — so wall-clock timing from
 * `newPage()` cannot locate the animation in the video. Instead we flash a
 * known marker at a known wall-clock time and find it in the recording,
 * which yields the wall-clock -> video-time offset directly.
 *
 * @param {string} webmPath
 * @param {number} markerOnAt epoch ms when the marker was shown
 * @param {number} releaseAt epoch ms when the animation was released
 * @returns {Promise<{leadInS: number|null, method: string}>}
 */
async function locateRelease(webmPath, markerOnAt, releaseAt) {
  const series = await cornerChromaSeries(webmPath, 24)
  if (series.length < 4) return { leadInS: null, method: 'no-frames' }

  // Baseline = the corner's chroma before the marker appeared.
  const base = series.slice(0, 3)
  const u0 = base.reduce((a, p) => a + p.u, 0) / base.length
  const v0 = base.reduce((a, p) => a + p.v, 0) / base.length

  let markerIdx = -1
  for (let i = 3; i < series.length; i++) {
    const deviation = Math.abs(series[i].u - u0) + Math.abs(series[i].v - v0)
    if (deviation > 60) {
      markerIdx = i
      break
    }
  }
  if (markerIdx === -1) return { leadInS: null, method: 'marker-not-found' }

  const markerVideoT = series[markerIdx].t
  // The marker was shown at markerOnAt and the animation released at
  // releaseAt; the offset between the two is a plain wall-clock delta.
  const leadInS = markerVideoT + (releaseAt - markerOnAt) / 1000
  return { leadInS, method: 'marker' }
}

/**
 * Fallback release detection for when the marker cannot be seen (a template
 * that covers the corner, a modal dialog, a canvas at the top layer).
 *
 * Finds the start of the longest sustained motion stretch, which is the
 * animation; isolated bursts before it are page-load and font reflow.
 */
async function locateReleaseByMotion(webmPath) {
  // tblend=difference gives the per-frame difference against the previous
  // frame; signalstats reduces it to one luma number per frame.
  // -fps_mode passthrough keeps the source's variable frame timing so the
  // printed pts_time values are the real capture timestamps.
  const { stdout } = await ffmpeg([
    '-v',
    'error',
    '-i',
    webmPath,
    '-vf',
    'tblend=all_mode=difference,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-',
    '-fps_mode',
    'passthrough',
    '-f',
    'null',
    '-',
  ])

  const curve = []
  let t = 0
  for (const line of stdout.split(/\r?\n/)) {
    const pt = line.match(/pts_time:([0-9.]+)/)
    if (pt) t = parseFloat(pt[1])
    const y = line.match(/YAVG=([0-9.]+)/)
    if (y) curve.push({ t, d: parseFloat(y[1]) })
  }
  if (curve.length < 4) return { leadInS: null, curve }

  const diffs = curve.map((p) => p.d).sort((a, b) => a - b)
  // 10th percentile approximates the static region's noise floor.
  const noiseFloor = diffs[Math.floor(diffs.length * 0.1)]
  const motionThreshold = Math.max(0.08, noiseFloor * 3)

  // Find the longest stretch of motion. Page-load reflows are isolated
  // bursts; the animation runs continuously.
  let bestStart = 0
  let bestLen = 0
  let runStart = -1
  for (let i = 0; i <= curve.length; i++) {
    const moving = i < curve.length && curve[i].d > motionThreshold
    if (moving) {
      if (runStart === -1) runStart = i
    } else if (runStart !== -1) {
      if (i - runStart > bestLen) {
        bestLen = i - runStart
        bestStart = runStart
      }
      runStart = -1
    }
  }
  if (bestLen === 0) return { leadInS: null, curve }

  // Walk back to the first frame of the change, not the first frame that
  // crossed the threshold — a slow fade crosses it a few frames late.
  const backThreshold = Math.max(0.02, noiseFloor * 1.5)
  let idx = bestStart
  while (idx > 0 && curve[idx - 1].d > backThreshold) idx--

  return { leadInS: curve[idx].t, curve }
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  const opts = parseArgs(process.argv.slice(2))
  const outPath = resolve(opts.out)
  const isUrl = /^https?:\/\//i.test(opts.input)

  // ---- Phase 0: prepare the source -------------------------------------
  let loadPath = opts.input
  let cleanup = async () => {}
  if (!isUrl) {
    const abs = resolve(opts.input)
    if (!existsSync(abs)) throw new Error(`input not found: ${abs}`)
    if (extname(abs).toLowerCase() !== '.html')
      throw new Error(`input must be an .html file or an http(s) URL: ${abs}`)
    const prepared = await prepareSourceHtml(abs)
    loadPath = prepared.loadPath
    cleanup = prepared.cleanup
  }
  const targetUrl = isUrl ? opts.input : pathToFileURL(loadPath).href

  const workDir = await mkdtempish()
  const videoDir = join(workDir, 'video')
  const framesDir = opts.framesDir
    ? resolve(opts.framesDir)
    : join(workDir, 'frames')
  await mkdir(videoDir, { recursive: true })
  await mkdir(framesDir, { recursive: true })

  log(`target   ${targetUrl}`)
  log(`output   ${outPath}`)
  log(
    `mode     ${opts.mode === 'a' ? 'A (real-time record)' : 'B (frame stepping)'} ` +
      `· ${opts.width}x${opts.height} @ ${opts.fps}fps`,
  )

  let browser = null
  let result
  try {
    const executablePath = resolveChromiumExecutable(opts.chromium)
    if (executablePath) log(`browser  ${executablePath}`)

    browser = await chromium.launch({
      headless: true,
      ...(executablePath ? { executablePath } : {}),
      args: [
        '--force-color-profile=srgb',
        '--font-render-hinting=none',
        '--disable-lcd-text',
        '--hide-scrollbars',
        '--mute-audio',
      ],
    })

    const context = await browser.newContext({
      viewport: { width: opts.width, height: opts.height },
      deviceScaleFactor:
        opts.mode === 'b' && opts.supersample ? 2 : 1,
      // viewport MUST equal recordVideo.size or the screencast is rescaled
      // and the whole output goes soft.
      ...(opts.mode === 'a'
        ? {
            recordVideo: {
              dir: videoDir,
              size: { width: opts.width, height: opts.height },
            },
          }
        : {}),
    })

    const page = await context.newPage()
    // Only used for logging: the screencast does NOT start here, so this is
    // not the WebM's t=0. The release marker establishes the real offset.
    const tWebmStart = Date.now()

    // ---- Phase 2: freeze BEFORE the document parses ---------------------
    await page.addInitScript(FREEZE_SCRIPT)

    // ---- Phase 3: load --------------------------------------------------
    // 'load' blocks on every external asset, including cross-origin videos
    // with no CORS headers; Chromium retries those for ~4s and the frozen
    // first scene is burned into the recording.
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded' })

    // ---- Phase 4: fonts, then release ------------------------------------
    await page.evaluate(FONT_WAIT_SCRIPT, opts.fontTimeout)
    log('fonts    ready')

    const animMs = await page.evaluate(DURATION_PROBE_SCRIPT)
    const counts = await page.evaluate(ANIMATION_COUNT_SCRIPT)
    const probedS = animMs / 1000
    log(
      `probe    ${probedS.toFixed(2)}s ` +
        `(css animations: ${counts.css}, gsap tweens: ${counts.gsap}, ` +
        `timelines: ${counts.timelines})`,
    )

    const explicit = opts.duration !== 'auto'
    let durationS
    if (explicit) {
      durationS = parseFloat(opts.duration)
      if (!Number.isFinite(durationS) || durationS <= 0)
        throw new Error(`--duration must be a positive number of seconds`)
      if (probedS > durationS + 0.05) {
        log(
          `WARN     animation needs ${probedS.toFixed(2)}s but --duration is ` +
            `${durationS.toFixed(2)}s — the tail will be cut`,
        )
      }
    } else {
      // +400ms settle so the last animation frame is captured; cap at 30s so
      // a stray huge value cannot run away.
      durationS = probedS > 0 ? Math.min(30, probedS + 0.4) : 5
    }

    if (opts.mode === 'a') {
      result = await runModeA({
        page,
        context,
        opts,
        outPath,
        videoDir,
        durationS,
        tWebmStart,
        padTail: explicit && probedS < durationS,
      })
    } else {
      result = await runModeB({
        page,
        context,
        opts,
        outPath,
        framesDir,
        durationS,
      })
    }
  } finally {
    await browser?.close().catch(() => {})
    await cleanup().catch(() => {})
  }

  // ---- Verify ----------------------------------------------------------
  let report = null
  if (opts.verify) {
    log('verify   probing duration, decoding, checking motion…')
    report = await verify({
      videoPath: outPath,
      expectedDurationS: result.durationS,
      frameDir: join(workDir, 'check'),
    })
    for (const w of report.warnings) log(`WARN     ${w}`)
    if (!report.ok) {
      log('FAIL     verification failed')
      process.exitCode = 1
    } else {
      log(
        `OK       ${report.checks.durationS.toFixed(2)}s ` +
          `(${report.checks.frames} frames, luma spread ` +
          `${report.checks.lumaSpread?.toFixed(1)})`,
      )
    }
    if (!opts.keepFrames) {
      await rm(join(workDir, 'check'), { recursive: true, force: true }).catch(
        () => {},
      )
    }
  }

  if (opts.mode === 'b' && !opts.keepFrames && !opts.framesDir) {
    await rm(framesDir, { recursive: true, force: true }).catch(() => {})
  }
  await rm(workDir, { recursive: true, force: true }).catch(() => {})

  log(`done     ${outPath}`)
  return report
}

// ---------------------------------------------------------------------------
// Mode A — real-time recording
// ---------------------------------------------------------------------------

async function runModeA({
  page,
  context,
  opts,
  outPath,
  videoDir,
  durationS,
  tWebmStart,
  padTail,
}) {
  // ---- Release marker ----------------------------------------------------
  // Flash a known patch at a known wall-clock time so the recording can be
  // aligned to the animation later. It is removed before the release, so it
  // never reaches the output.
  const markerOnAt = Date.now()
  await page.evaluate(MARKER_ON_SCRIPT)
  await page.waitForTimeout(250)
  await page.evaluate(MARKER_OFF_SCRIPT)
  await page.waitForTimeout(200)

  // ---- Phase 4b: release the freeze. This is the true t=0 of the animation.
  const releaseAt = Date.now()
  await page.evaluate(UNFREEZE_SCRIPT)
  log(`release  t=0 of animation at +${releaseAt - tWebmStart}ms (wall clock)`)

  // ---- Phase 5: record --------------------------------------------------
  await page.waitForTimeout(durationS * 1000)

  const video = page.video()
  await context.close()

  // The recording can land a moment after close() returns, so fall back to
  // scanning the record directory.
  let webmPath = null
  try {
    webmPath = await video.path()
  } catch {
    /* fall through to the directory scan */
  }
  if (!webmPath || !existsSync(webmPath)) {
    const deadline = Date.now() + 15000
    while (Date.now() < deadline) {
      const files = existsSync(videoDir)
        ? (await readdir(videoDir)).filter((f) => f.endsWith('.webm'))
        : []
      if (files.length > 0) {
        webmPath = join(videoDir, files[0])
        break
      }
      await new Promise((r) => setTimeout(r, 250))
    }
  }
  if (!webmPath || !existsSync(webmPath)) {
    const listing = existsSync(videoDir) ? await readdir(videoDir) : []
    throw new Error(
      `recorded video not found in ${videoDir} ` +
        `(contains: ${listing.join(', ') || 'nothing'})`,
    )
  }
  const size = (await stat(webmPath)).size
  log(`recorded ${webmPath} (${(size / 1024 / 1024).toFixed(2)} MB)`)

  if (opts.keepWebm) {
    const dest = resolve(opts.keepWebm)
    await mkdir(dirname(dest), { recursive: true })
    await copyFile(webmPath, dest)
    log(`webm     saved to ${dest}`)
  }

  // ---- Locate the animation's start in the recording --------------------
  let leadInS = null
  let method = 'none'
  if (opts.leadIn !== null) {
    leadInS = opts.leadIn / 1000
    method = 'override'
    log(`lead-in  overridden to ${opts.leadIn}ms`)
  } else {
    const r = await locateRelease(webmPath, markerOnAt, releaseAt)
    method = r.method
    if (r.leadInS !== null) {
      leadInS = r.leadInS
      log(`lead-in  ${(leadInS * 1000).toFixed(0)}ms (${r.method})`)
    } else {
      const m = await locateReleaseByMotion(webmPath)
      if (m.leadInS !== null) {
        leadInS = m.leadInS
        method = 'motion'
        log(`lead-in  ${(leadInS * 1000).toFixed(0)}ms (${method} fallback)`)
      } else {
        log(`lead-in  WARN could not locate the animation start — using 0ms`)
      }
    }
  }
  if (leadInS === null) leadInS = 0

  const { seekSec } = await encodeWebm({
    webmPath,
    outPath,
    leadInMs: leadInS * 1000,
    durationS,
    fps: opts.fps,
    padTail,
  })
  log(`encoded  -ss ${seekSec.toFixed(3)} -t ${durationS.toFixed(3)}`)

  return { durationS, leadInS, method }
}

// ---------------------------------------------------------------------------
// Mode B — deterministic frame stepping
// ---------------------------------------------------------------------------

async function runModeB({
  page,
  context,
  opts,
  outPath,
  framesDir,
  durationS,
}) {
  // The freeze style stays on for the whole capture: a paused animation still
  // renders when its currentTime is set.
  await page.evaluate(PIN_AT_ZERO_SCRIPT)

  const fps = opts.fps
  const totalFrames = Math.ceil(durationS * fps)
  log(`capture  ${totalFrames} frames @ ${fps}fps`)

  for (let i = 0; i < totalFrames; i++) {
    const tMs = (i / fps) * 1000
    await page.evaluate(SEEK_SCRIPT, tMs)
    const name = `f${String(i).padStart(5, '0')}.png`
    await page.screenshot({ path: join(framesDir, name) })
    if (opts.verbose && i % 30 === 0) {
      process.stdout.write(`\r         frame ${i}/${totalFrames}`)
    }
  }
  if (opts.verbose) process.stdout.write('\r         done        \n')

  await context.close()

  await encodeFrames({
    framesDir,
    outPath,
    fps,
    scaleTo:
      opts.supersample
        ? { width: opts.width, height: opts.height }
        : null,
  })
  log('encoded  PNG sequence -> MP4')

  // The encoded length is frames/fps by construction.
  return { durationS: totalFrames / fps, leadInS: 0 }
}

// ---------------------------------------------------------------------------
// utils
// ---------------------------------------------------------------------------

async function mkdtempish() {
  const base = join(tmpdir(), `h2v-${process.pid}-${Date.now().toString(36)}`)
  await mkdir(base, { recursive: true })
  return base
}

main().catch((err) => {
  console.error('[html2video] ERROR', err.message)
  if (process.env.H2V_DEBUG) console.error(err.stack)
  process.exit(1)
})
