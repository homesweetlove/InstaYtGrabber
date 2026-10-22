import json
import os
import platform

import yt_dlp


def version():
    return json.dumps({
        "ok": True,
        "yt_dlp": getattr(yt_dlp.version, "__version__", "unknown"),
        "python": platform.python_version(),
    })


def _common(qjs_path="", ffmpeg_path="", cookie_header=""):
    options = {
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 20,
        "retries": 2,
        "fragment_retries": 2,
        "extractor_retries": 2,
    }

    # Let yt-dlp select its maintained default clients / JS runtime where
    # possible. An absent bundled qjs must not disable all runtime discovery.
    if qjs_path and os.path.isfile(qjs_path) and os.access(qjs_path, os.X_OK):
        options["js_runtimes"] = {
            "quickjs": {
                "path": qjs_path,
            },
        }

    if ffmpeg_path and os.path.isfile(ffmpeg_path):
        options["ffmpeg_location"] = ffmpeg_path

    if cookie_header:
        options["http_headers"] = {
            "Cookie": cookie_header,
        }

    return options


def inspect(url, qjs_path=""):
    try:
        options = _common(qjs_path=qjs_path)
        options["skip_download"] = True
        options["noplaylist"] = True

        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.sanitize_info(
                ydl.extract_info(url, download=False)
            )

        formats = info.get("formats") or []
        return json.dumps({
            "ok": True,
            "id": info.get("id") or "",
            "title": info.get("title") or "제목 없음",
            "channel": info.get("uploader") or info.get("channel") or "",
            "thumbnail": info.get("thumbnail") or "",
            "duration": int(info.get("duration") or 0),
            "extractor": info.get("extractor_key") or info.get("extractor") or "yt-dlp",
            "format_count": len(formats),
            "live_status": info.get("live_status") or "",
            "is_live": bool(info.get("is_live")),
        }, ensure_ascii=False)
    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
        }, ensure_ascii=False)


def download(
    url,
    target_dir,
    mode,
    qjs_path="",
    ffmpeg_path="",
    cookie_header="",
    allow_playlist=False,
    listener=None,
):
    try:
        os.makedirs(target_dir, exist_ok=True)

        def progress_hook(data):
            if listener is None:
                return
            if data.get("status") != "downloading":
                return
            downloaded = int(data.get("downloaded_bytes") or 0)
            total = int(
                data.get("total_bytes")
                or data.get("total_bytes_estimate")
                or 0
            )
            eta = int(data.get("eta") or 0)
            percent = 0.0
            if total > 0:
                percent = min(100.0, downloaded * 100.0 / total)
            listener.onProgress(percent, eta, str(data.get("_speed_str") or ""))

        options = _common(
            qjs_path=qjs_path,
            ffmpeg_path=ffmpeg_path,
            cookie_header=cookie_header,
        )
        options.update({
            "outtmpl": os.path.join(
                target_dir,
                "%(title).180B [%(id)s].%(ext)s",
            ),
            "noplaylist": not bool(allow_playlist),
            "progress_hooks": [progress_hook],
            "restrictfilenames": True,
            "writethumbnail": False,
        })

        if mode == "video":
            if ffmpeg_path and os.path.isfile(ffmpeg_path):
                options["format"] = (
                    "bestvideo[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/"
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/"
                    "best[ext=mp4]/best"
                )
                options["merge_output_format"] = "mp4"
            else:
                # Avoid selecting separate audio/video streams which cannot
                # be merged on devices without an FFmpeg executable.
                options["format"] = "best[ext=mp4]/best"

        elif mode == "audio":
            if not (ffmpeg_path and os.path.isfile(ffmpeg_path)):
                raise RuntimeError("MP3 변환용 FFmpeg 실행 파일을 찾지 못했습니다.")
            options["format"] = "bestaudio/best"
            options["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "0",
            }]

        elif mode == "thumbnail":
            options["skip_download"] = True
            options["writethumbnail"] = True
            if ffmpeg_path:
                options["postprocessors"] = [{
                    "key": "FFmpegThumbnailsConvertor",
                    "format": "jpg",
                    "when": "before_dl",
                }]

        elif mode == "media":
            options["format"] = "best"

        else:
            raise ValueError("지원하지 않는 다운로드 모드입니다.")

        with yt_dlp.YoutubeDL(options) as ydl:
            ydl.extract_info(url, download=True)

        files = []
        for root, _, names in os.walk(target_dir):
            for name in names:
                if name.endswith((".part", ".ytdl", ".json")):
                    continue
                path = os.path.join(root, name)
                if os.path.isfile(path) and os.path.getsize(path) > 0:
                    files.append(path)

        if not files:
            raise RuntimeError("다운로드 결과 파일을 찾지 못했습니다.")

        return json.dumps({
            "ok": True,
            "files": files,
        }, ensure_ascii=False)

    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
            "files": [],
        }, ensure_ascii=False)
