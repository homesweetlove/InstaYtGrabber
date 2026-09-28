# InstaYtGrabber

Android media utility for extracting audio and still frames from media you own or are authorized to download.

## MVP
- Pick a local video with Android's system file picker.
- Extract the original audio track to an M4A container without re-encoding.
- Capture a JPG frame at a chosen timestamp.
- Download a direct HTTP/HTTPS media file into Downloads/InstaYtGrabber.
- Receive shared video files or text URLs from other Android apps.
- On-device processing; no project-specific backend required.

## Platform links
YouTube/Instagram page URLs are detected separately from direct media URLs. This project intentionally does not bypass DRM, login gates, signatures, or platform access controls. Platform-specific import can be extended only through permitted/authorized flows.

## Build
Android Studio: open the repository and sync Gradle.

Command line with a local Gradle 8.9+ installation:

```bash
gradle :app:assembleDebug
```

GitHub Actions also builds the debug APK on pushes to `main`.

## Stack
- Kotlin
- Jetpack Compose
- Android MediaExtractor / MediaMuxer
- MediaMetadataRetriever
- MediaStore
- OkHttp

## Output
- Audio: `Music/InstaYtGrabber`
- Images: `Pictures/InstaYtGrabber`
- Downloads: `Downloads/InstaYtGrabber`
