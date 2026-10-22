// Bundled extension for the in-app GeckoView browser; no network telemetry,
// no cookie reads, and no messages to external servers.
(() => {
  const API = "instaytgrabber";
  let previousKey = "";

  function collect() {
    const href = window.location.href;
    const route = new URL(href).pathname;
    if (!/^\/(p|reel|reels|tv|stories)\//.test(route)) {
      report(href, []);
      return;
    }
    const urls = new Map();
    function add(url, kind) {
      if (!url || typeof url !== "string" || !/^https:\/\//i.test(url)) return;
      if (!/(\.cdninstagram\.com|\.fbcdn\.net|\.instagram\.com)(?:\/|:|$)/i.test(url)) return;
      if (!urls.has(url)) urls.set(url, {url, kind});
    }
    document.querySelectorAll("video").forEach(v => {
      add(v.currentSrc || v.src || "", "video");
      v.querySelectorAll("source[src]").forEach(s => add(s.src, "video"));
    });
    add(document.querySelector('meta[property="og:video"]')?.content, "video");
    add(document.querySelector('meta[property="og:video:secure_url"]')?.content, "video");
    document.querySelectorAll("main img, article img").forEach(img => {
      if (Math.max(img.naturalWidth, img.width) < 350) return;
      add(img.currentSrc || img.src, "image");
    });
    if (urls.size === 0) {
      add(document.querySelector('meta[property="og:image"]')?.content, "image");
    }
    const videos = [...urls.values()].filter(v => v.kind === "video");
    const images = [...urls.values()].filter(v => v.kind === "image");
    // Choose actual video first for reels, rather than silently downloading
    // a cover image as if it were a video.
    const items = /^\/(reel|reels|stories)\//.test(route)
      ? videos.slice(0, 8) : [...videos, ...images].slice(0, 12);
    report(href, items);
  }

  function report(href, items) {
    const key = href + "|" + items.map(item => item.url).join("|");
    if (key === previousKey) return;
    previousKey = key;
    browser.runtime.sendNativeMessage(API, {
      type: "media",
      page: href,
      items
    }).catch(() => {});
  }

  // Instagram is a client-side SPA, so the document may persist while routes
  // change. Checking the DOM doesn't send Instagram additional HTTP requests.
  setInterval(collect, 3000);
  collect();
})();