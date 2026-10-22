// Bundled extension for the in-app GeckoView browser; no network telemetry,
// no cookie reads, and no messages to external servers.
(() => {
  const API = "instaytgrabber";
  let previousKey = "";
  let previousAuthCategory = "";

  /**
   * Classify only visible high-level page state. Do not read passwords,
   * usernames, form contents, cookies or raw server responses.
   */
  function reportAuthState() {
    const path = window.location.pathname;
    const onLogin = path.startsWith("/accounts/login") ||
      path.startsWith("/challenge") || path.startsWith("/accounts/onetap");
    if (!onLogin) {
      // Do not claim success merely because navigation changed; the page may
      // redirect to an error/challenge rather than an authenticated account.
      previousAuthCategory = "";
      return;
    }
    let category = "login_form";
    const visible = (document.body?.innerText || "").slice(0, 9000).toLowerCase();
    if (/unsupported browser|browser isn't supported|브라우저.*지원하지/.test(visible)) {
      category = "unsupported_browser";
    } else if (/too many requests|try again later|wait a few minutes|잠시 후.*다시|429/.test(visible)) {
      category = "rate_limited";
    } else if (/security code|confirm it's you|enter the code|인증 코드|본인 확인|보안 코드/.test(visible) ||
               path.startsWith("/challenge")) {
      category = "challenge";
    } else if (/sorry, there was a problem|incorrect password|couldn't log you in|잘못된 비밀번호|로그인할 수 없/.test(visible)) {
      category = "login_error";
    }
    if (category !== previousAuthCategory) {
      previousAuthCategory = category;
      browser.runtime.sendNativeMessage(API, {
        type: "status",
        category
      }).catch(() => {});
    }
  }

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
  setInterval(() => {
    reportAuthState();
    collect();
  }, 3000);
  reportAuthState();
  collect();
})();