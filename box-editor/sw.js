/* CS2-Box box editor service worker — offline support for the static deploy
 * (GitHub Pages). Cache name carries the build version (__VER__ is rewritten
 * by build.mjs), so a new deploy starts a fresh cache and the activate handler
 * drops the stale ones.
 *
 * Strategies:
 *   - navigations (HTML): network-first, cached shell as offline fallback
 *   - static assets:      cache-first, populate at runtime (HTML references
 *                         assets with ?v=<version>, so a new build misses and
 *                         refreshes naturally)
 */
const VER = '__VER__';
const CACHE = 'cs2box-editor-' + VER;

const PRECACHE = [
  './',
  'index.html',
  'prices.html',
  'css/style.css',
  'js/i18n.js',
  'js/data.js',
  'js/model.js',
  'js/validator.js',
  'js/app.js',
  'img/bg.jpg',
  'img/icon.svg',
  'manifest.webmanifest',
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await Promise.all(PRECACHE.map(async (path) => {
      try {
        await cache.add(new Request(path, { cache: 'reload' }));
      } catch (err) { // optional asset missing — skip, don't fail the install
      }
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  const accept = req.headers.get('accept') || '';
  if (req.mode === 'navigate' || accept.includes('text/html')) {
    event.respondWith((async () => {
      try {
        const fresh = await fetch(req);
        const cache = await caches.open(CACHE);
        cache.put(req, fresh.clone());
        return fresh;
      } catch (err) {
        const cached = await caches.match(req, { ignoreSearch: true });
        return cached || caches.match('./index.html');
      }
    })());
    return;
  }

  event.respondWith((async () => {
    const cached = await caches.match(req);
    if (cached) return cached;
    try {
      const fresh = await fetch(req);
      if (fresh && fresh.ok) {
        const cache = await caches.open(CACHE);
        cache.put(req, fresh.clone());
      }
      return fresh;
    } catch (err) {
      return Response.error();
    }
  })());
});
