/**
 * Multi-composition preparation.
 *
 * Templates that split scenes into sub-files via `data-composition-src` load
 * them with client-side `fetch()`. Chromium blocks `fetch()` on `file://`, so
 * a headless render of the raw entry file produces an empty shell. We inline
 * every sub-scene on the Node side and inject a player that mounts them.
 */
import { readFile, writeFile, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'

const PLAYER = `<script>
(function () {
  function reexec(scripts) {
    scripts.forEach(function (old) {
      if (old.src) return;
      const s = document.createElement('script');
      // Wrapped in a block so top-level \`const tl = …\` locals do not collide
      // across scenes; window.__timelines assignments still escape the block.
      s.textContent = '{\\n' + old.textContent + '\\n}';
      document.body.appendChild(s);
    });
  }
  function mountOne(host) {
    const src = host.getAttribute('data-composition-src');
    const text = (window.__COMPOSITIONS__ || {})[src];
    if (!text) return;
    const holder = document.createElement('div');
    holder.innerHTML = text;
    const tpl = holder.querySelector('template');
    const frag = tpl ? tpl.content.cloneNode(true) : holder;

    // A composition's scripts may sit inside the <template> or beside it.
    // Both must be collected and re-executed: a cloned <script> never runs
    // on its own, and a script that is a sibling of the <template> would
    // otherwise be left behind in the detached holder.
    const scripts = Array.from(frag.querySelectorAll('script'));
    if (tpl) scripts.push.apply(scripts, Array.from(holder.querySelectorAll('script')));
    scripts.forEach(function (s) { s.remove(); });

    host.appendChild(frag);
    reexec(scripts);
  }
  window.__playAll = function () {
    window.__played = true;
    const tls = window.__timelines || {};
    Object.keys(tls).forEach((k) => {
      const tl = tls[k];
      if (tl && typeof tl.play === 'function') tl.play(0);
    });
  };
  function boot() {
    document.querySelectorAll('[data-composition-src]').forEach(mountOne);
    // Composition scripts register their (paused) timelines synchronously as
    // they are injected. Leave them paused — the renderer probes duration
    // first, then calls __playAll() exactly when capture starts. Fall back to
    // auto-play if no driver calls it (standalone browser view).
    setTimeout(function () { if (!window.__played) window.__playAll(); }, 250);
  }
  document.readyState === 'loading'
    ? document.addEventListener('DOMContentLoaded', boot)
    : boot();
})();
</script>`

/**
 * @param {string} sourcePath absolute path to the entry HTML
 * @returns {Promise<{ loadPath: string, cleanup: () => Promise<void> }>}
 */
export async function prepareSourceHtml(sourcePath) {
  const abs = resolve(sourcePath)
  const raw = await readFile(abs, 'utf8')
  const srcMatches = Array.from(raw.matchAll(/data-composition-src=["']([^"']+)["']/g))
  if (srcMatches.length === 0) {
    return { loadPath: abs, cleanup: async () => {} }
  }

  const srcDir = dirname(abs)
  const compMap = {}
  const missing = []
  for (const m of srcMatches) {
    const rel = m[1]
    if (compMap[rel] !== undefined) continue
    const p = join(srcDir, rel)
    if (existsSync(p)) compMap[rel] = await readFile(p, 'utf8')
    else missing.push(rel)
  }
  if (missing.length > 0) {
    throw new Error(
      `data-composition-src references not found on disk: ${missing.join(', ')}`,
    )
  }
  if (Object.keys(compMap).length === 0) {
    return { loadPath: abs, cleanup: async () => {} }
  }

  // Escape `</` and comment openers so the JSON survives inside a <script>.
  // Composition files contain their own </script> tags.
  const safeJson = JSON.stringify(compMap)
    .replace(/<\//g, '<\\/')
    .replace(/<!--/g, '<\\!--')

  const head =
    `<script>window.__timelines=window.__timelines||{};` +
    `window.__COMPOSITIONS__=${safeJson};</script>`

  let out = /<head[^>]*>/i.test(raw)
    ? raw.replace(/<head[^>]*>/i, (m) => `${m}\n${head}`)
    : `${head}\n${raw}`

  out = out.includes('</body>')
    ? out.replace('</body>', `${PLAYER}\n</body>`)
    : `${out}\n${PLAYER}`

  // Sibling .tmp file so relative asset paths still resolve.
  const loadPath = join(srcDir, `.tmp-h2v-${process.pid}.html`)
  await writeFile(loadPath, out, 'utf8')
  return {
    loadPath,
    cleanup: async () => {
      await rm(loadPath, { force: true })
    },
  }
}
