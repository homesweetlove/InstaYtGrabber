import json
import os
import re
from gallery_dl import config, job

_RESERVED = {
    "accounts", "about", "developer", "directory", "explore", "p", "reel",
    "reels", "stories", "tv"
}


def _cookies(header):
    result = {}
    for part in (header or "").split(";"):
        part = part.strip()
        if not part or "=" not in part:
            continue
        name, value = part.split("=", 1)
        result[name.strip()] = value.strip()
    return result


def _normalize_url(url, kind):
    url = (url or "").strip()
    if kind == "story":
        match = re.fullmatch(
            r"https?://(?:www\.)?instagram\.com/([^/?#]+)/?",
            url,
            flags=re.IGNORECASE,
        )
        if match:
            username = match.group(1)
            if username.lower() not in _RESERVED:
                return f"https://www.instagram.com/stories/{username}/"
    return url


def _files(root):
    items = []
    if not os.path.isdir(root):
        return items
    for base, _, names in os.walk(root):
        for name in names:
            items.append(os.path.join(base, name))
    return sorted(items)


def download(url, output_dir, cookie_header="", kind="post"):
    os.makedirs(output_dir, exist_ok=True)
    target = _normalize_url(url, kind)
    before = set(_files(output_dir))
    cookie_map = _cookies(cookie_header)

    include_map = {
        "story": "stories",
        "highlight": "highlights",
        "post": "posts,reels",
    }

    settings = [
        ((), "base-directory", output_dir),
        ((), "directory", ()),
        ((), "filename", "{date:%Y%m%d_%H%M%S}_{media_id}_{num}.{extension}"),
        (("cache",), "file", ":memory:"),
        (("extractor", "instagram"), "include", include_map.get(kind, "posts")),
        (("extractor", "instagram"), "videos", True),
        (("extractor", "instagram"), "previews", False),
        (("extractor", "instagram"), "sleep-request", "6.0-12.0"),
    ]
    if cookie_map:
        settings.append((("extractor", "instagram"), "cookies", cookie_map))

    try:
        with config.apply(settings):
            code = job.DownloadJob(target).run()
        after = set(_files(output_dir))
        new_files = sorted(after - before)
        return json.dumps(
            {
                "ok": code == 0 and bool(new_files),
                "code": int(code),
                "files": new_files,
                "target": target,
            },
            ensure_ascii=False,
        )
    except Exception as exc:
        return json.dumps(
            {
                "ok": False,
                "code": -1,
                "files": [],
                "target": target,
                "error": f"{type(exc).__name__}: {exc}",
            },
            ensure_ascii=False,
        )
