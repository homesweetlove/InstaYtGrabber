import json
import re
from urllib.parse import urlparse

import instaloader


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
        key, value = part.split("=", 1)
        if key and value:
            result[key.strip()] = value.strip()
    return result


def _username(value):
    raw = (value or "").strip()
    if raw.startswith("@"):
        name = raw[1:]
    elif "://" not in raw:
        name = raw.strip("/")
    else:
        parsed = urlparse(raw)
        host = (parsed.hostname or "").lower()
        if host not in {"instagram.com", "www.instagram.com"}:
            raise ValueError("Instagram 프로필 링크를 입력해 주세요.")
        name = parsed.path.strip("/").split("/")[0]

    if not re.fullmatch(r"[A-Za-z0-9._]{1,30}", name or ""):
        raise ValueError("Instagram 프로필 링크를 입력해 주세요.")
    if name.lower() in _RESERVED:
        raise ValueError("프로필 링크를 입력해 주세요.")
    return name


def _loader(cookie_header=""):
    cookies = _cookies(cookie_header)
    loader = instaloader.Instaloader(
        sleep=False,
        quiet=True,
        download_pictures=False,
        download_videos=False,
        download_video_thumbnails=False,
        download_geotags=False,
        download_comments=False,
        save_metadata=False,
        compress_json=False,
        max_connection_attempts=1,
        request_timeout=12.0,
        iphone_support=bool(cookies),
        resume_prefix=None,
        check_resume_bbd=False,
    )
    if cookies:
        # Instaloader only needs a truthy username to switch to its authenticated
        # query path. The actual identity is carried by the Instagram cookies.
        loader.load_session("webview-session", cookies)
    return loader


def _post_card(post, kind):
    caption = (post.caption or "").replace("\n", " ").strip()
    title = caption[:140] if caption else ("릴스" if kind == "reel" else "게시글")
    return {
        "id": str(post.shortcode),
        "kind": kind,
        "title": title,
        "target_url": f"instaloader://{kind}/{post.shortcode}",
        "preview_url": str(post.url or ""),
        "is_video": bool(post.is_video),
        "date": post.date_utc.isoformat() if post.date_utc else "",
        "count": int(post.mediacount or 1),
    }


def _story_card(item):
    media_url = item.video_url if item.is_video else item.url
    return {
        "id": str(item.mediaid),
        "kind": "story",
        "title": "스토리",
        "target_url": str(media_url or ""),
        "preview_url": str(item.url or media_url or ""),
        "is_video": bool(item.is_video),
        "date": item.date_utc.isoformat() if item.date_utc else "",
        "count": 1,
    }


def _highlight_card(profile, highlight):
    return {
        "id": str(highlight.unique_id),
        "kind": "highlight",
        "title": str(highlight.title or "하이라이트"),
        "target_url": (
            "instaloader://highlight/"
            + profile.username
            + "/"
            + str(highlight.unique_id)
        ),
        "preview_url": str(highlight.cover_cropped_url or highlight.cover_url or ""),
        "is_video": False,
        "date": "",
        "count": 0,
    }


def base(profile_url, cookie_header=""):
    try:
        username = _username(profile_url)
        loader = _loader(cookie_header)
        profile = instaloader.Profile.from_username(loader.context, username)
        avatar = str(profile.profile_pic_url or "")
        return json.dumps({
            "ok": True,
            "username": profile.username,
            "full_name": profile.full_name or "",
            "biography": profile.biography or "",
            "is_private": bool(profile.is_private),
            "avatar": {
                "id": "avatar:" + profile.username,
                "kind": "avatar",
                "title": "프로필 사진",
                "target_url": avatar,
                "preview_url": avatar,
                "is_video": False,
                "date": "",
                "count": 1,
            } if avatar else None,
        }, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
        }, ensure_ascii=False)


def section(profile_url, section_name, cookie_header=""):
    try:
        username = _username(profile_url)
        loader = _loader(cookie_header)
        profile = instaloader.Profile.from_username(loader.context, username)
        section_name = (section_name or "").lower()
        items = []

        if section_name == "posts":
            for post in profile.get_posts():
                items.append(_post_card(post, "post"))

        elif section_name == "reels":
            for post in profile.get_reels():
                items.append(_post_card(post, "reel"))

        elif section_name == "stories":
            if not cookie_header:
                raise RuntimeError("스토리는 Instagram 로그인 세션이 필요합니다.")
            for story in loader.get_stories(userids=[profile.userid]):
                if int(story.owner_id) != int(profile.userid):
                    continue
                for item in story.get_items():
                    items.append(_story_card(item))

        elif section_name == "highlights":
            if not cookie_header:
                raise RuntimeError("하이라이트는 Instagram 로그인 세션이 필요합니다.")
            for highlight in loader.get_highlights(profile):
                card = _highlight_card(profile, highlight)
                try:
                    card["count"] = sum(1 for _ in highlight.get_items())
                except Exception:
                    card["count"] = 0
                items.append(card)

        else:
            raise ValueError("지원하지 않는 Instagram 섹션입니다.")

        return json.dumps({
            "ok": True,
            "items": items,
        }, ensure_ascii=False)

    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
            "items": [],
        }, ensure_ascii=False)


def resolve(target, cookie_header=""):
    try:
        raw = (target or "").strip()
        if raw.startswith("https://"):
            return json.dumps({
                "ok": True,
                "items": [{"url": raw, "video": False}],
            }, ensure_ascii=False)

        parsed = urlparse(raw)
        if parsed.scheme != "instaloader":
            raise ValueError("지원하지 않는 Instagram 선택 항목입니다.")

        kind = (parsed.netloc or "").lower()
        parts = [part for part in parsed.path.split("/") if part]
        loader = _loader(cookie_header)
        result = []

        if kind in {"post", "reel"}:
            if len(parts) != 1:
                raise ValueError("잘못된 Instagram 게시물 식별자입니다.")
            post = instaloader.Post.from_shortcode(loader.context, parts[0])

            if post.typename == "GraphSidecar":
                for node in post.get_sidecar_nodes():
                    url = node.video_url if node.is_video else node.display_url
                    if url:
                        result.append({"url": str(url), "video": bool(node.is_video)})
            elif post.is_video:
                if post.video_url:
                    result.append({"url": str(post.video_url), "video": True})
            else:
                if post.url:
                    result.append({"url": str(post.url), "video": False})

        elif kind == "highlight":
            if len(parts) != 2:
                raise ValueError("잘못된 하이라이트 식별자입니다.")
            username, highlight_id = parts
            if not cookie_header:
                raise RuntimeError("하이라이트 다운로드에는 Instagram 로그인 세션이 필요합니다.")

            profile = instaloader.Profile.from_username(loader.context, username)
            match = None
            for highlight in loader.get_highlights(profile):
                if str(highlight.unique_id) == str(highlight_id):
                    match = highlight
                    break
            if match is None:
                raise RuntimeError("하이라이트를 찾지 못했습니다.")

            for item in match.get_items():
                url = item.video_url if item.is_video else item.url
                if url:
                    result.append({"url": str(url), "video": bool(item.is_video)})

        else:
            raise ValueError("지원하지 않는 Instagram 선택 항목입니다.")

        if not result:
            raise RuntimeError("저장할 원본 미디어 URL을 찾지 못했습니다.")

        return json.dumps({
            "ok": True,
            "items": result,
        }, ensure_ascii=False)

    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
            "items": [],
        }, ensure_ascii=False)
