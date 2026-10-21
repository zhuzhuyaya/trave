/**
 * Browser-cache shim. MUST be imported before `playwright`.
 *
 * Playwright pins an exact browser build (see playwright-core/browsers.json)
 * and hard-fails when that build is not in the cache. A machine's cache often
 * holds a *different* build, which drives fine over CDP — but the pinned name
 * is what the registry looks for, so `chromium-1223` is invisible to
 * Playwright 1.64, which wants `chromium-1248`.
 *
 * Rather than demanding a ~150MB download, this module builds a local
 * browsers directory where each *required* revision is a link to the newest
 * build actually present, and points PLAYWRIGHT_BROWSERS_PATH at it.
 * Everything else in the cache is copied over by name.
 *
 * This covers the ffmpeg build too, which Playwright needs for recordVideo's
 * screencast muxing and which is not the system ffmpeg.
 */
import {
  existsSync,
  readdirSync,
  symlinkSync,
  mkdirSync,
  copyFileSync,
  readFileSync,
} from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))

/** The default Playwright browser cache for this platform. */
function defaultBrowsersPath() {
  if (process.env.PLAYWRIGHT_BROWSERS_PATH) return process.env.PLAYWRIGHT_BROWSERS_PATH
  if (process.platform === 'win32') {
    return join(process.env.LOCALAPPDATA || join(homedir(), 'AppData', 'Local'), 'ms-playwright')
  }
  if (process.platform === 'darwin') {
    return join(homedir(), 'Library', 'Caches', 'ms-playwright')
  }
  return join(homedir(), '.cache', 'ms-playwright')
}

/** Required browser revisions for the installed playwright-core. */
function requiredRevisions() {
  const manifest = join(HERE, '..', 'node_modules', 'playwright-core', 'browsers.json')
  if (!existsSync(manifest)) return null
  try {
    const data = JSON.parse(readFileSync(manifest, 'utf8'))
    const out = new Map()
    for (const b of data.browsers ?? []) {
      if (b.installByDefault) out.set(b.name, b.revision)
    }
    return out
  } catch {
    return null
  }
}

/** Directory names in the cache that look like `<name>-<revision>`. */
function cacheEntries(root) {
  const out = []
  if (!existsSync(root)) return out
  for (const name of readdirSync(root)) {
    const m = name.match(/^(.*)-(\d+)$/)
    if (!m) continue
    if (!existsSync(join(root, name))) continue
    out.push({
      name,
      // The cache uses underscores ("chromium_headless_shell-1223") while
      // browsers.json uses dashes ("chromium-headless-shell"). Normalize so
      // the two vocabularies meet.
      family: m[1].replace(/_/g, '-'),
      revision: parseInt(m[2], 10),
    })
  }
  return out
}

/**
 * The directory name Playwright uses in the browser cache.
 * "chromium-headless-shell" -> "chromium_headless_shell-1248"
 */
function dirNameFor(family, revision) {
  return `${family.replace(/-/g, '_')}-${revision}`
}

/** Link (or copy) `target` at `linkPath`. */
function linkOrCopy(target, linkPath) {
  mkdirSync(dirname(linkPath), { recursive: true })
  try {
    // 'junction' needs no elevation on Windows and preserves absolute paths.
    symlinkSync(target, linkPath, process.platform === 'win32' ? 'junction' : 'dir')
    return true
  } catch {
    /* fall through to copy */
  }
  try {
    // Directories that are too large to copy are skipped; only small helper
    // trees (ffmpeg) realistically reach this path.
    const src = readdirSync(target)
    mkdirSync(linkPath, { recursive: true })
    for (const f of src) {
      const s = join(target, f)
      const d = join(linkPath, f)
      if (existsSync(s) && !existsSync(d)) copyFileSync(s, d)
    }
    return true
  } catch {
    return false
  }
}

function setup() {
  const required = requiredRevisions()
  if (!required) return

  const cacheRoot = defaultBrowsersPath()
  const entries = cacheEntries(cacheRoot)
  if (entries.length === 0) return

  // Is everything already in place? Then there is nothing to shim.
  const missing = []
  for (const [family, revision] of required) {
    const dir = join(cacheRoot, dirNameFor(family, revision))
    if (!existsSync(dir)) missing.push({ family, revision })
  }
  if (missing.length === 0) return

  const localRoot = resolve(HERE, '..', '.browsers')
  mkdirSync(localRoot, { recursive: true })

  // Every family gets an entry named for the *required* revision, pointing at
  // the newest build present in the cache for that family.
  const families = new Set([
    ...required.keys(),
    ...entries.map((e) => e.family),
  ])

  for (const family of families) {
    const wantRev = required.get(family)
    const candidates = entries
      .filter((e) => e.family === family)
      .sort((a, b) => b.revision - a.revision)
    if (candidates.length === 0) continue

    const linkName = wantRev ? dirNameFor(family, wantRev) : candidates[0].name
    const linkPath = join(localRoot, linkName)
    if (existsSync(linkPath)) continue

    // Prefer a build that is exactly the required revision.
    const exact = candidates.find((c) => c.revision === wantRev)
    const source = exact ?? candidates[0]
    if (linkOrCopy(join(cacheRoot, source.name), linkPath)) {
      process.env.H2V_SHIM_NOTE = `linked ${linkName} -> ${source.name}`
    }
  }

  process.env.PLAYWRIGHT_BROWSERS_PATH = localRoot
}

setup()
