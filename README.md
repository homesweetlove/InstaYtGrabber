[![English](https://img.shields.io/badge/README-English-24292f?style=for-the-badge)](./README.md) [![한국어](https://img.shields.io/badge/README-%ED%95%9C%EA%B5%AD%EC%96%B4-24292f?style=for-the-badge)](./README.ko.md)

# InstaYtGrabber

A personal Android media extraction app with no advertising.

## Features

### Instagram
- Separate selection for **Stories / Highlights / Posts & Reels**
- Save **original media**: photos, carousel images, and videos
- Extract MP3 audio from video content
- **Always try an anonymous request first for public content, even when a user is logged in**
- If anonymous access succeeds, Instagram login cookies are not sent
- Only when anonymous access fails and the user has logged in through the in-app WebView does the app retry with the login session for **content the current account is already authorized to access**
- Login sessions are discarded when the app process restarts
- No recent-URL or download-history database
- gallery-dl cache is configured as memory-only (`:memory:`)

> The app does not include any feature intended to hide or bypass Instagram's own Story-view indicators.

### YouTube
- Save videos
- Extract MP3 audio
- Extract JPG thumbnails
- Playlist URLs are processed as a single item by default

### Android Share
Use **Share → InstaYtGrabber** from Instagram or YouTube to pass the link directly to the app and open the appropriate tab.

## Storage Layout

```text
Downloads/InstaYtGrabber/
├── Instagram/
│   ├── story/
│   │   ├── media/
│   │   └── audio/
│   ├── highlight/
│   │   ├── media/
│   │   └── audio/
│   └── post/
│       ├── media/
│       └── audio/
└── YouTube/
    ├── video/
    ├── audio/
    └── thumbnail/
```

## Engines

- YouTube and audio processing: `youtubedl-android 0.18.1` + yt-dlp + FFmpeg
- Original Instagram image/video extraction: `gallery-dl 1.32.14`
- Embedded Python: `Chaquopy 17.0.0` / Python 3.11
- UI: Kotlin + Jetpack Compose (light Toss/Apple-inspired tone with a `ui/` design system)

Instagram and YouTube can change their web structures, which may temporarily break extraction. When that happens, the bundled extractor versions need to be updated.

## Build

Open the project in Android Studio, run Gradle Sync, then launch it.

For local CLI builds, JDK 17 and Python 3.11 are required.

```bash
gradle :app:assembleDebug
```

GitHub Actions also builds a debug APK automatically on pushes to `main`. After a successful run, download it from **Artifacts → InstaYtGrabber-debug**.

## Privacy Direction

- No separate backend
- No advertising SDK
- No analytics/tracking SDK
- No download-history database
- App backup disabled
- Instagram cookies cleared on app-process startup
- Stored login cookies are not attached when processing public Instagram content anonymously
- Persistent gallery-dl cache disabled

## Intended Use

Use the app only for content you own, are authorized to download, or have been granted access to by the service. It is not designed to bypass DRM, login barriers, or platform access controls.

## License Notes

Open-source components used by this project are governed by their respective licenses. In particular, gallery-dl uses a GPL-family license, so review the applicable obligations before distributing the APK to third parties.
