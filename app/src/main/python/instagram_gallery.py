import json
import os
import re
import requests
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


def _username(url):
    match = re.fullmatch(
        r"https?://(?:www\.)?instagram\.com/([^/?#]+)/?(?:[?#].*)?",
        (url or "").strip(),
        flags=re.IGNORECASE,
    )
    if not match:
        raise ValueError("Instagram 프로필 링크를 입력해 주세요.")
    username = match.group(1)
    if username.lower() in _RESERVED:
        raise ValueError("게시물 링크가 아니라 프로필 링크를 입력해 주세요.")
    return username


def _normalize_url(url, kind):
    url = (url or "").strip()
    if kind == "story":
        try:
            username = _username(url)
            return f"https://www.instagram.com/stories/{username}/"
        except ValueError:
            pass
    return url


def _files(root):
    items = []
    if not os.path.isdir(root):
        return items
    for base, _, names in os.walk(root):
        for name in names:
            items.append(os.path.join(base, name))
    return sorted(items)


def _settings(cookie_header="", max_posts=0):
    settings = [
        (("cache",), "file", ":memory:"),
        (("extractor", "instagram"), "videos", True),
        (("extractor", "instagram"), "previews", False),
        (("extractor", "instagram"), "sleep-request", "0.2-0.6"),
        (("extractor", "instagram"), "sleep-429", 0),
        (("extractor", "instagram"), "retries", 0),
        (("extractor", "instagram"), "timeout", 8.0),
        (("extractor", "instagram"), "user-cache", "memory"),
        (("downloader",), "retries", 1),
        (("downloader",), "timeout", 15.0),
    ]
    if int(max_posts) > 0:
        settings.append((("extractor", "instagram"), "max-posts", int(max_posts)))
    cookie_map = _cookies(cookie_header)
    if cookie_map:
        settings.append((("extractor", "instagram"), "cookies", cookie_map))
    return settings


def _collect(url, cookie_header="", max_posts=0):
    with config.apply(_settings(cookie_header, max_posts)):
        task = job.DataJob(url, file=None, resolve=False)
        task.run()
    if task.exception:
        raise task.exception
    return task.data


def _directory(data):
    for msg in data:
        if isinstance(msg, (list, tuple)) and len(msg) == 2 and isinstance(msg[1], dict):
            return msg[1]
    return {}


def _urls(data):
    result = []
    for msg in data:
        if not isinstance(msg, (list, tuple)) or len(msg) < 3:
            continue
        url = msg[1]
        meta = msg[2]
        if isinstance(url, str) and isinstance(meta, dict):
            result.append((url, meta))
    return result


def _first_url(data):
    items = _urls(data)
    return items[0] if items else (None, {})


def _profile_base(username):
    return f"https://www.instagram.com/{username}"


_PROFILE_HEADERS = {
    "X-IG-App-ID": "936619743392459",
    "X-ASBD-ID": "198387",
    "X-Requested-With": "XMLHttpRequest",
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 14; Mobile) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/140.0.0.0 Mobile Safari/537.36"
    ),
}


def _direct_profile(username, cookie_header=""):
    session = requests.Session()
    session.headers.update(_PROFILE_HEADERS)
    session.headers["Referer"] = f"https://www.instagram.com/{username}/"

    for name, value in _cookies(cookie_header).items():
        session.cookies.set(name, value, domain=".instagram.com")

    endpoints = (
        "https://www.instagram.com/api/v1/users/web_profile_info/",
        "https://i.instagram.com/api/v1/users/web_profile_info/",
    )

    errors = []
    for endpoint in endpoints:
        try:
            response = session.get(
                endpoint,
                params={"username": username},
                timeout=(4, 8),
                allow_redirects=False,
            )
            if response.status_code == 200:
                payload = response.json()
                user = (payload.get("data") or {}).get("user")
                if user:
                    return user
            errors.append(f"{response.status_code}:{endpoint}")
        except Exception as exc:
            errors.append(f"{type(exc).__name__}:{exc}")

    raise RuntimeError("profile_api_failed " + " | ".join(errors))


def _profile_from_feed(username, cookie_header=""):
    session = requests.Session()
    session.headers.update(_PROFILE_HEADERS)
    session.headers["Referer"] = f"https://www.instagram.com/{username}/"

    for name, value in _cookies(cookie_header).items():
        session.cookies.set(name, value, domain=".instagram.com")

    url = f"https://www.instagram.com/api/v1/feed/user/{username}/username/"
    response = session.get(
        url,
        params={"count": 1},
        timeout=(4, 8),
        allow_redirects=False,
    )
    if response.status_code != 200:
        raise RuntimeError(f"feed_profile_http_{response.status_code}")

    content_type = response.headers.get("content-type", "")
    if "json" not in content_type.lower():
        raise RuntimeError("feed_profile_non_json")

    payload = response.json()
    user = payload.get("user")
    if isinstance(user, dict) and user:
        return user

    items = payload.get("items") or []
    if items:
        owner = items[0].get("user") or {}
        if isinstance(owner, dict) and owner:
            return owner

    raise RuntimeError("feed_profile_missing_user")


def _best_image(item):
    candidates = ((item.get("image_versions2") or {}).get("candidates") or [])
    if candidates:
        best = max(
            candidates,
            key=lambda x: int(x.get("width") or 0) * int(x.get("height") or 0),
        )
        return best.get("url") or ""
    return item.get("thumbnail_url") or ""


def _direct_feed_cards(username, cookie_header=""):
    session = requests.Session()
    session.headers.update(_PROFILE_HEADERS)
    session.headers["Referer"] = f"https://www.instagram.com/{username}/"

    for name, value in _cookies(cookie_header).items():
        session.cookies.set(name, value, domain=".instagram.com")

    endpoint = f"https://www.instagram.com/api/v1/feed/user/{username}/username/"
    cards = []
    max_id = None
    seen_cursors = set()

    while True:
        params = {"count": 33}
        if max_id:
            params["max_id"] = max_id

        response = session.get(
            endpoint,
            params=params,
            timeout=(4, 10),
            allow_redirects=False,
        )

        if response.status_code != 200:
            raise RuntimeError(f"feed_http_{response.status_code}")

        content_type = response.headers.get("content-type", "")
        if "json" not in content_type.lower():
            raise RuntimeError("feed_non_json")

        payload = response.json()
        items = payload.get("items") or []

        for item in items:
            code = str(item.get("code") or "")
            if not code:
                continue

            product_type = str(item.get("product_type") or "").lower()
            kind = "reel" if product_type in {"clips", "reel"} else "post"
            carousel = item.get("carousel_media") or []
            count = len(carousel) if carousel else 1

            first_media = carousel[0] if carousel else item
            preview = _best_image(first_media) or _best_image(item)

            is_video = bool(item.get("video_versions"))
            if carousel and not is_video:
                is_video = any(bool(child.get("video_versions")) for child in carousel)

            caption = item.get("caption")
            if isinstance(caption, dict):
                title = str(caption.get("text") or "")
            else:
                title = ""

            target = (
                f"https://www.instagram.com/reel/{code}/"
                if kind == "reel"
                else f"https://www.instagram.com/p/{code}/"
            )

            cards.append({
                "id": str(item.get("pk") or item.get("id") or code),
                "kind": kind,
                "title": (title or code)[:140],
                "target_url": target,
                "preview_url": preview,
                "is_video": is_video,
                "date": str(item.get("taken_at") or ""),
                "count": count,
            })

        if not payload.get("more_available"):
            break

        next_max_id = payload.get("next_max_id")
        if not next_max_id or next_max_id in seen_cursors:
            break

        seen_cursors.add(next_max_id)
        max_id = next_max_id

    return cards



def _media_card(url, meta, fallback_kind):
    media_id = str(meta.get("media_id") or meta.get("post_id") or meta.get("post_shortcode") or "")
    post_url = meta.get("post_url") or url
    kind = meta.get("type") or fallback_kind
    preview = meta.get("display_url") or meta.get("thumbnail_url") or url
    is_video = bool(meta.get("video_url")) or str(url).startswith("ytdl:")
    title = (
        meta.get("highlight_title")
        or meta.get("description")
        or meta.get("post_shortcode")
        or media_id
        or kind
    )
    return {
        "id": media_id or post_url,
        "kind": kind,
        "title": str(title)[:140],
        "target_url": post_url,
        "preview_url": preview,
        "is_video": is_video,
        "date": str(meta.get("date") or meta.get("post_date") or ""),
        "num": int(meta.get("num") or 1),
    }


def _group_cards(data, fallback_kind):
    groups = {}
    for url, meta in _urls(data):
        card = _media_card(url, meta, fallback_kind)
        key = (
            meta.get("post_id")
            or meta.get("post_shortcode")
            or meta.get("highlight_title")
            or card["target_url"]
        )
        key = str(key)
        current = groups.get(key)
        if current is None:
            card["count"] = 1
            groups[key] = card
        else:
            current["count"] += 1
            if not current.get("preview_url") and card.get("preview_url"):
                current["preview_url"] = card["preview_url"]
    return list(groups.values())


def profile_catalog(url, cookie_header="", max_posts=0, sections="all"):
    username = _username(url)
    base = _profile_base(username)
    requested = {
        "info", "avatar", "stories", "highlights", "posts", "reels"
    } if sections == "all" else {
        part.strip() for part in sections.split(",") if part.strip()
    }

    result = {
        "ok": True,
        "username": username,
        "profile": {},
        "avatar": None,
        "stories": [],
        "highlights": [],
        "posts": [],
        "reels": [],
        "errors": {},
    }

    def run_section(name, target, mapper):
        if name not in requested:
            return
        try:
            data = _collect(target, cookie_header, max_posts)
            mapper(data)
        except Exception as exc:
            result["errors"][name] = f"{type(exc).__name__}: {exc}"

    if "info" in requested:
        direct_exc = None
        feed_exc = None
        try:
            result["profile"] = _direct_profile(username, cookie_header)
        except Exception as exc:
            direct_exc = exc

        if not result["profile"]:
            try:
                result["profile"] = _profile_from_feed(username, cookie_header)
            except Exception as exc:
                feed_exc = exc

        if not result["profile"]:
            try:
                data = _collect(base + "/info/", cookie_header, max_posts)
                result["profile"] = _directory(data)
            except Exception as fallback_exc:
                result["errors"]["info"] = (
                    f"direct={type(direct_exc).__name__ if direct_exc else 'none'}: {direct_exc}; "
                    f"feed={type(feed_exc).__name__ if feed_exc else 'none'}: {feed_exc}; "
                    f"gallery={type(fallback_exc).__name__}: {fallback_exc}"
                )

    if "avatar" in requested and result["profile"]:
        profile = result["profile"]
        avatar_url = profile.get("profile_pic_url_hd") or profile.get("profile_pic_url")
        if not avatar_url:
            info = profile.get("hd_profile_pic_url_info") or {}
            avatar_url = info.get("url") if isinstance(info, dict) else None
        if not avatar_url:
            versions = profile.get("hd_profile_pic_versions") or []
            if versions and isinstance(versions[-1], dict):
                avatar_url = versions[-1].get("url")
        if avatar_url:
            result["avatar"] = {
                "id": "avatar:" + username,
                "kind": "avatar",
                "title": "프로필 사진",
                "target_url": avatar_url,
                "preview_url": avatar_url,
                "is_video": False,
                "date": "",
                "count": 1,
            }

    def map_stories(data):
        cards = []
        seen = set()
        for media_url, meta in _urls(data):
            card = _media_card(media_url, meta, "story")
            media_id = str(meta.get("media_id") or meta.get("post_id") or card["id"])
            if media_id in seen:
                continue
            seen.add(media_id)
            card["target_url"] = f"https://www.instagram.com/stories/{username}/{media_id}/"
            card["count"] = 1
            cards.append(card)
        result["stories"] = cards

    run_section(
        "stories",
        f"https://www.instagram.com/stories/{username}/",
        map_stories,
    )

    run_section(
        "highlights",
        base + "/highlights/",
        lambda data: result.update(highlights=_group_cards(data, "highlight")),
    )
    if "posts" in requested or "reels" in requested:
        feed_cards = None
        try:
            feed_cards = _direct_feed_cards(username, cookie_header)
        except Exception as feed_exc:
            result["errors"]["feed"] = f"{type(feed_exc).__name__}: {feed_exc}"

        if feed_cards is not None:
            if "posts" in requested:
                result["posts"] = [card for card in feed_cards if card["kind"] == "post"]
            if "reels" in requested:
                result["reels"] = [card for card in feed_cards if card["kind"] == "reel"]
        else:
            run_section(
                "posts",
                base + "/posts/",
                lambda data: result.update(posts=_group_cards(data, "post")),
            )
            run_section(
                "reels",
                base + "/reels/",
                lambda data: result.update(reels=_group_cards(data, "reel")),
            )

    if not result["profile"] and result["errors"].get("info"):
        result["ok"] = False

    return json.dumps(result, ensure_ascii=False, default=str)


def download(url, output_dir, cookie_header="", kind="post"):
    os.makedirs(output_dir, exist_ok=True)
    target = _normalize_url(url, kind)
    before = set(_files(output_dir))

    include_map = {
        "story": "stories",
        "highlight": "highlights",
        "post": "posts",
        "reel": "reels",
    }

    settings = [
        ((), "base-directory", output_dir),
        ((), "directory", ()),
        ((), "filename", "{date:%Y%m%d_%H%M%S}_{media_id}_{num}.{extension}"),
        (("cache",), "file", ":memory:"),
        (("extractor", "instagram"), "include", include_map.get(kind, "posts")),
        (("extractor", "instagram"), "videos", True),
        (("extractor", "instagram"), "previews", False),
        (("extractor", "instagram"), "sleep-request", "0.5-1.5"),
        (("extractor", "instagram"), "user-cache", "memory"),
    ]
    cookie_map = _cookies(cookie_header)
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
