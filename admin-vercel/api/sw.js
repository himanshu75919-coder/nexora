// NEXORA service worker: shell cache + offline last-known-good UX
const SHELL = ["nexora-shell-v1"];
self.addEventListener("install", e => {
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(["/app"]).catch(() => {})));
  self.skipWaiting();
});
self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));
self.addEventListener("fetch", e => {
  const u = new URL(e.request.url);
  if (e.request.method !== "GET") return;
  // API: network-first (fresh config wins; 304/localStorage handled in page)
  if (u.pathname.startsWith("/api/")) {
    e.respondWith(fetch(e.request).catch(() => caches.match(e.request)));
    return;
  }
  // app shell: cache-first then network
  if (u.pathname === "/app" || u.pathname === "/" || u.pathname === "/manifest.webmanifest") {
    e.respondWith(caches.match(u.pathname).then(hit => hit || fetch(e.request).then(r => {
      const cp = r.clone(); caches.open(SHELL).then(c => c.put(u.pathname, cp)); return r;
    }).catch(() => caches.match("/app"))));
  }
});
