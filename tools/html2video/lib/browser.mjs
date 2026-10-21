/**
 * Browser-side helpers: launch config, the animation freeze, the font-wait
 * routine, and the duration probe.
 */

/**
 * Resolve an explicitly requested Chromium executable.
 *
 * Returns undefined when nothing was requested, which lets Playwright use
 * its own registry path (already handled by the browsers-path shim).
 *
 * @param {string|undefined} explicit path from --chromium
 */
export function resolveChromiumExecutable(explicit) {
  const candidate = explicit || process.env.H2V_CHROMIUM
  if (!candidate) return undefined
  if (!existsSync(candidate)) {
    throw new Error(`Chromium executable not found: ${candidate}`)
  }
  return candidate
}

/**
 * Release marker.
 *
 * The recording starts whenever the screencast produces its first frame,
 * which can be seconds after the page is created. Wall-clock timing from
 * `newPage()` therefore cannot locate the animation's start in the video.
 *
 * A tiny high-chroma patch in the corner is shown for a few frames at a
 * known wall-clock time, then removed before the animation starts. Finding
 * it in the recording establishes the wall-clock -> video-time offset
 * exactly, and because the patch is gone before the release it never
 * appears in the output.
 */
export const MARKER_ON_SCRIPT = () => {
  let m = document.getElementById('__h2v_marker')
  if (!m) {
    m = document.createElement('div')
    m.id = '__h2v_marker'
    m.setAttribute('aria-hidden', 'true')
    document.body.appendChild(m)
  }
  m.style.cssText =
    'position:fixed;left:0;top:0;width:24px;height:24px;z-index:2147483647;' +
    'pointer-events:none;background:rgb(255,0,255);'
}

export const MARKER_OFF_SCRIPT = () => {
  document.getElementById('__h2v_marker')?.remove()
}

/** Freeze every CSS animation at frame 0, before the document's own scripts run. */
export const FREEZE_SCRIPT = () => {
  const style = document.createElement('style')
  style.id = '__h2v_freeze'
  style.textContent =
    '*, *::before, *::after { animation-play-state: paused !important;' +
    ' -webkit-animation-play-state: paused !important; }'
  const attach = () =>
    (document.head || document.documentElement).appendChild(style)
  if (document.head || document.documentElement) attach()
  else
    document.addEventListener('DOMContentLoaded', attach, { once: true })
  window.__h2vUnfreeze = () => document.getElementById('__h2v_freeze')?.remove()
  // Claim the playback slot so a template's own auto-play fallback (e.g. the
  // multi-composition player's 250ms timer) does not start the animation
  // before the font wait finishes. The renderer starts playback explicitly.
  // A page opened in a normal browser has no freeze script, so the fallback
  // still runs there.
  window.__played = true
}

export const UNFREEZE_SCRIPT = () => {
  window.__h2vUnfreeze?.()
  window.__played = true
  if (typeof window.__playAll === 'function') window.__playAll()
}

/**
 * Font wait. `document.fonts.ready` alone resolves instantly under
 * `domcontentloaded` because the stylesheet that declares the @font-face
 * rules has not returned yet — the face set is empty, so there is nothing to
 * wait for and the swap happens on camera.
 *
 * Ordering is load-bearing:
 *   1. every <link rel=stylesheet> load/error  -> @font-face rules registered
 *   2. face.load() per face                    -> forces the actual download
 *   3. fonts.ready                             -> real face set settled
 *   4. two rAFs                                -> layout settles on real metrics
 */
export const FONT_WAIT_SCRIPT = (hardCapMs) => {
  const doc = document
  const fonts = doc.fonts
  if (!fonts || typeof fonts.ready?.then !== 'function') return

  return new Promise((resolve) => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      // One more frame so the relayout on the real face is painted.
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    }
    // Hard cap: a blocked CDN must never stall the render.
    const cap = setTimeout(finish, hardCapMs)

    const links = Array.from(doc.querySelectorAll('link[rel="stylesheet"]'))
    const linkDone = links.map((link) => {
      // An already-loaded sheet exposes cssRules without throwing.
      try {
        if (link.sheet && link.sheet.cssRules) return Promise.resolve()
      } catch {
        /* not ready yet — fall through to event wait */
      }
      return new Promise((r) => {
        const done = () => r()
        link.addEventListener('load', done, { once: true })
        link.addEventListener('error', done, { once: true })
        // Per-link safety so one wedged link can't hold the batch.
        setTimeout(done, 6000)
      })
    })

    Promise.all(linkDone)
      .then(() => {
        // font-display: swap defers the fetch until something paints with the
        // face. Off-screen text may never have triggered it.
        const loads = []
        fonts.forEach((face) => {
          try {
            loads.push(face.load().catch(() => undefined))
          } catch {
            /* some faces reject load() pre-paint — ignore */
          }
        })
        return Promise.all(loads)
      })
      .then(() => fonts.ready)
      .then(() => {
        clearTimeout(cap)
        finish()
      })
      .catch(() => {
        clearTimeout(cap)
        finish()
      })
  })
}

/**
 * Probe how long the animation actually needs.
 *
 * Takes the max of the longest non-infinite CSS animation and the longest
 * finite JS timeline. Infinite animations are skipped on both sides: one
 * `repeat: -1` tween makes a naive probe read ~1e10 seconds.
 */
export const DURATION_PROBE_SCRIPT = () => {
  let maxMs = 0

  // ---- CSS @keyframes ----
  Array.from(document.querySelectorAll('*')).forEach((el) => {
    const s = getComputedStyle(el)
    const durs = (s.animationDuration || '').split(',')
    const dels = (s.animationDelay || '').split(',')
    const iters = (s.animationIterationCount || '').split(',')
    durs.forEach((d, i) => {
      if ((iters[i] || '').trim() === 'infinite') return
      const total =
        ((parseFloat(d) || 0) + (parseFloat(dels[i] || '0') || 0)) * 1000
      if (total > maxMs) maxMs = total
    })
  })

  // ---- JS timelines ----
  // Never use globalTimeline.totalDuration(): a single repeat:-1 tween makes
  // it ~1e10s. Walk the children and take the longest finite tween.
  const g = window.gsap
  let jsMs = 0
  const children = g?.globalTimeline?.getChildren?.(true, true, true) ?? []
  for (const c of children) {
    const repeat =
      typeof c.repeat === 'function' ? c.repeat() : (c.vars?.repeat ?? 0)
    if (repeat === -1) continue
    const td = typeof c.totalDuration === 'function' ? c.totalDuration() : 0
    if (Number.isFinite(td)) jsMs = Math.max(jsMs, td * 1000)
  }

  // A timeline registered by a multi-composition player.
  const tls = window.__timelines || {}
  for (const k of Object.keys(tls)) {
    const tl = tls[k]
    if (!tl) continue
    const repeat = typeof tl.repeat === 'function' ? tl.repeat() : 0
    if (repeat === -1) continue
    const td = typeof tl.duration === 'function' ? tl.duration() : 0
    if (Number.isFinite(td)) jsMs = Math.max(jsMs, td * 1000)
  }

  return Math.max(maxMs, jsMs)
}

/** Pin every seekable timeline at 0 for deterministic frame stepping. */
export const PIN_AT_ZERO_SCRIPT = () => {
  document.getAnimations().forEach((a) => {
    a.pause()
    a.currentTime = 0
  })
  window.gsap?.globalTimeline?.pause?.().time(0, true)
  const tls = window.__timelines || {}
  for (const k of Object.keys(tls)) {
    const tl = tls[k]
    if (tl && typeof tl.pause === 'function') tl.pause(0)
  }
}

/** Seek every seekable timeline to `tMs` and wait for the paint. */
export const SEEK_SCRIPT = (tMs) => {
  document.getAnimations().forEach((a) => {
    a.currentTime = tMs
  })
  // suppressEvents=false: we only ever step forward, so DOM-mutating
  // onStart/onComplete callbacks fire exactly as they would in playback.
  window.gsap?.globalTimeline?.time?.(tMs / 1000, false)
  const tls = window.__timelines || {}
  for (const k of Object.keys(tls)) {
    const tl = tls[k]
    if (tl && typeof tl.time === 'function') tl.time(tMs / 1000, false)
  }
  // Let the compositor flush the new state before the screenshot is taken.
  return new Promise((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
  )
}

/** Count of seekable animations, used as a sanity check for the probe. */
export const ANIMATION_COUNT_SCRIPT = () => ({
  css: document.getAnimations().length,
  gsap: window.gsap?.globalTimeline?.getChildren?.(true, true, true)?.length ?? 0,
  timelines: Object.keys(window.__timelines || {}).length,
})
