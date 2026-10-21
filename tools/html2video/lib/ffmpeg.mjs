/**
 * ffmpeg / ffprobe wrappers: encoding, concatenation, and verification.
 */
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { dirname } from 'node:path'

function run(bin, args, { onStdout } = {}) {
  return new Promise((resolve, reject) => {
    const proc = spawn(bin, args, {
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    })
    let stdout = ''
    let stderr = ''
    proc.stdout.on('data', (d) => {
      stdout += d
      onStdout?.(d)
    })
    proc.stderr.on('data', (d) => {
      stderr += d
    })
    proc.on('error', (err) => {
      if (err.code === 'ENOENT') {
        reject(
          new Error(
            `${bin} not found on PATH. Install it (macOS: brew install ffmpeg, ` +
              `Windows: winget install Gyan.FFmpeg, Debian: apt-get install ffmpeg).`,
          ),
        )
        return
      }
      reject(err)
    })
    proc.on('close', (code) => {
      if (code === 0) resolve({ stdout, stderr })
      else
        reject(
          new Error(
            `${bin} exited with code ${code}\n` +
              `args: ${bin} ${args.join(' ')}\n` +
              `stderr:\n${stderr.slice(-4000)}`,
          ),
        )
    })
  })
}

export const ffmpeg = (args, opts) => run('ffmpeg', args, opts)
export const ffprobe = (args, opts) => run('ffprobe', args, opts)

const ENCODE_ARGS = [
  '-c:v',
  'libx264',
  '-pix_fmt',
  'yuv420p',
  '-preset',
  'medium',
  '-crf',
  '20',
  '-movflags',
  '+faststart',
]

/**
 * Mode A: WebM (from Playwright's recordVideo) -> MP4.
 *
 * `-ss` before `-i` drops the dead lead-in at the input stage. The seek is
 * backed off by 120ms so recorder start jitter cannot clip the first real
 * animation frame: extra still frames at the head are invisible, a missing
 * opening beat is obvious.
 */
export async function encodeWebm({
  webmPath,
  outPath,
  leadInMs,
  durationS,
  fps,
  padTail = false,
}) {
  await mkdir(dirname(outPath), { recursive: true })
  const seekSec = Math.max(0, (leadInMs - 120) / 1000)
  const args = ['-y']
  if (seekSec > 0) args.push('-ss', seekSec.toFixed(3))
  args.push('-i', webmPath)
  if (padTail) {
    args.push('-vf', `tpad=stop_mode=clone:stop_duration=${durationS.toFixed(3)}`)
  }
  args.push('-t', durationS.toFixed(3), '-r', String(fps), ...ENCODE_ARGS, outPath)
  await ffmpeg(args)
  return { seekSec }
}

/** Mode B: PNG sequence -> MP4. `-framerate` must precede `-i`. */
export async function encodeFrames({
  framesDir,
  outPath,
  fps,
  scaleTo = null,
  pattern = 'f%05d.png',
}) {
  await mkdir(dirname(outPath), { recursive: true })
  const args = ['-y', '-framerate', String(fps), '-i', `${framesDir}/${pattern}`]
  if (scaleTo) {
    args.push('-vf', `scale=${scaleTo.width}:${scaleTo.height}:flags=lanczos`)
  }
  args.push(...ENCODE_ARGS, outPath)
  await ffmpeg(args)
}

/**
 * Concatenate segments.
 *
 * `reencode: false` uses the concat demuxer with stream copy — correct when
 * every segment came from the same encoder with matching timebase. Mixed
 * rendering engines need `reencode: true`: the demuxer accumulates bad PTS
 * and an 8s output silently becomes 35s, and no amount of `-vsync cfr` fixes
 * it because the broken PTS are already in the stream.
 */
export async function concatSegments(segments, outPath, { reencode = false, fps = 30 } = {}) {
  await mkdir(dirname(outPath), { recursive: true })
  if (!reencode) {
    const listPath = `${outPath}.concat.txt`
    const list = segments.map((s) => `file '${s.replace(/'/g, "'\\''")}'`).join('\n')
    await (await import('node:fs/promises')).writeFile(listPath, list, 'utf8')
    try {
      await ffmpeg(['-y', '-f', 'concat', '-safe', '0', '-i', listPath, '-c', 'copy', outPath])
    } finally {
      await (await import('node:fs/promises')).rm(listPath, { force: true })
    }
    return
  }
  const args = ['-y']
  for (const s of segments) args.push('-i', s)
  const chain = segments
    .map((_, i) => `[${i}:v]`)
    .join('')
  args.push(
    '-filter_complex',
    `${chain}concat=n=${segments.length}:v=1:a=0[v]`,
    '-map',
    '[v]',
    '-c:v',
    'libx264',
    '-pix_fmt',
    'yuv420p',
    '-r',
    String(fps),
    '-movflags',
    '+faststart',
    outPath,
  )
  await ffmpeg(args)
}

/** Average luma per frame — a cheap "is anything actually rendering?" probe. */
export async function frameLumaSeries(videoPath) {
  // metadata=print writes to the log at INFO level, which -v error would
  // swallow; file=- forces it to stdout instead.
  const { stdout } = await ffmpeg([
    '-v',
    'error',
    '-i',
    videoPath,
    '-vf',
    'signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-',
    '-f',
    'null',
    '-',
  ])
  const out = []
  for (const m of stdout.matchAll(/YAVG=([0-9.]+)/g)) out.push(Number(m[1]))
  return out
}

/** Extract a single frame at `atSec` to `outPath`. */
export async function extractFrame(videoPath, atSec, outPath) {
  await mkdir(dirname(outPath), { recursive: true })
  await ffmpeg([
    '-v',
    'error',
    '-ss',
    String(atSec),
    '-i',
    videoPath,
    '-frames:v',
    '1',
    '-y',
    outPath,
  ])
}

/**
 * Per-frame chroma of a small corner region.
 *
 * Used to find the release marker in the recording. The marker is detected by
 * *deviation from the baseline* rather than by an absolute colour, so a page
 * whose corner is already saturated cannot produce a false positive.
 *
 * @param {string} videoPath
 * @param {number} size edge length of the square region, in pixels
 * @returns {Promise<Array<{t:number,y:number,u:number,v:number}>>}
 */
export async function cornerChromaSeries(videoPath, size = 24) {
  // metadata=print without a key emits every frame metadata key, which gets
  // Y/U/V in a single pass. (The '|'-separated key list form is not
  // supported by every ffmpeg build.)
  const { stdout } = await ffmpeg([
    '-v',
    'error',
    '-i',
    videoPath,
    '-vf',
    `crop=${size}:${size}:0:0,signalstats,metadata=print:file=-`,
    '-fps_mode',
    'passthrough',
    '-f',
    'null',
    '-',
  ])

  const out = []
  let t = 0
  let y = null
  let u = null
  let v = null
  const flush = () => {
    if (y === null || u === null || v === null) return
    out.push({ t, y, u, v })
    y = u = v = null
  }
  for (const line of stdout.split(/\r?\n/)) {
    const pt = line.match(/pts_time:([0-9.]+)/)
    if (pt) {
      t = parseFloat(pt[1])
      continue
    }
    let m
    if ((m = line.match(/YAVG=([0-9.]+)/))) {
      y = Number(m[1])
      continue
    }
    if ((m = line.match(/UAVG=([0-9.]+)/))) {
      u = Number(m[1])
      continue
    }
    if ((m = line.match(/VAVG=([0-9.]+)/))) {
      v = Number(m[1])
      flush()
    }
  }
  flush()
  return out
}

/**
 * Verify the output. "The file was written" is not success — wrong duration,
 * FOUT, and a dead lead-in all produce a perfectly playable MP4.
 */
export async function verify({
  videoPath,
  expectedDurationS = null,
  frameDir = null,
}) {
  const report = { ok: true, warnings: [], checks: {} }

  const { stdout: durOut } = await ffprobe([
    '-v',
    'error',
    '-show_entries',
    'format=duration',
    '-of',
    'csv=p=0',
    videoPath,
  ])
  const actual = parseFloat(durOut.trim())
  report.checks.durationS = actual
  if (!Number.isFinite(actual) || actual <= 0) {
    report.ok = false
    report.warnings.push(`unreadable duration: ${JSON.stringify(durOut.trim())}`)
  } else if (expectedDurationS !== null) {
    const drift = Math.abs(actual - expectedDurationS)
    report.checks.durationDriftS = drift
    if (drift > 0.05) {
      report.ok = false
      report.warnings.push(
        `duration ${actual.toFixed(3)}s differs from expected ${expectedDurationS.toFixed(3)}s ` +
          `by ${(drift * 1000).toFixed(0)}ms (>50ms)`,
      )
    }
  }

  // Full decode: a broken concat throws here.
  try {
    await ffmpeg(['-v', 'error', '-i', videoPath, '-f', 'null', '-'])
    report.checks.decode = 'ok'
  } catch (err) {
    report.ok = false
    report.checks.decode = 'failed'
    report.warnings.push(`decode failed: ${err.message.split('\n')[0]}`)
  }

  // Motion / blank check via per-frame luma.
  try {
    const luma = await frameLumaSeries(videoPath)
    report.checks.frames = luma.length
    if (luma.length === 0) {
      report.ok = false
      report.warnings.push('no frames decoded')
    } else {
      const min = Math.min(...luma)
      const max = Math.max(...luma)
      const spread = max - min
      report.checks.lumaMin = min
      report.checks.lumaMax = max
      report.checks.lumaSpread = spread
      if (spread < 0.01) {
        report.ok = false
        report.warnings.push(
          `every frame has identical luma (${min.toFixed(1)}) — the video is a still image`,
        )
      }
      if (max < 4 || min > 251) {
        report.ok = false
        report.warnings.push(
          `frames are blank (luma range ${min.toFixed(1)}..${max.toFixed(1)})`,
        )
      }
    }
  } catch (err) {
    report.warnings.push(`luma probe failed: ${err.message.split('\n')[0]}`)
  }

  // Sample frames for eyeballing (font correctness at the head).
  if (frameDir) {
    await mkdir(frameDir, { recursive: true })
    const samples = [0.05, Math.min(0.5, actual / 2), Math.max(0, actual - 0.2)]
    report.checks.frames_sampled = []
    for (const at of samples) {
      const p = `${frameDir}/t${at.toFixed(2).replace('.', '_')}.png`
      try {
        await extractFrame(videoPath, at, p)
        report.checks.frames_sampled.push({ at, path: p })
      } catch (err) {
        report.warnings.push(`frame sample at ${at}s failed`)
      }
    }
  }

  return report
}
