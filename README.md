# InstaYtGrabber

광고 없이 사용하는 개인용 Android 미디어 추출 앱.

## 기능

### Instagram
- 앱 안에서 **스토리 / 하이라이트 / 게시글·릴스**를 분리 선택
- **원본 미디어**: 사진, 캐러셀 이미지, 영상 저장
- **MP3**: 영상 콘텐츠의 음원 추출
- **공개 콘텐츠는 로그인 상태여도 항상 익명 요청부터 시도**
- 익명 요청에 성공하면 Instagram 로그인 쿠키를 전송하지 않음
- 익명 접근이 실패하고 사용자가 앱 내 WebView 로그인을 해둔 경우에만 **현재 계정이 원래 접근할 수 있는 콘텐츠**에 로그인 세션으로 재시도
- 로그인 세션은 앱 재실행 시 폐기
- 최근 URL / 다운로드 히스토리 DB 없음
- gallery-dl 캐시는 메모리 전용(`:memory:`)으로 설정

> Instagram 서비스 자체의 스토리 조회 표시를 숨기거나 우회하는 기능은 포함하지 않습니다.

### YouTube
- 영상 저장
- MP3 음원 추출
- JPG 썸네일 추출
- 재생목록 URL도 기본적으로 단일 항목만 처리

### Android 공유
Instagram 또는 YouTube에서 **공유 → InstaYtGrabber**로 보내면 링크를 자동으로 받아 해당 탭을 엽니다.

## 저장 위치

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

## 엔진
- YouTube 및 음원 처리: `youtubedl-android 0.18.1` + yt-dlp + FFmpeg
- Instagram 사진/영상 원본: `gallery-dl 1.32.14`
- Embedded Python: `Chaquopy 17.0.0` / Python 3.11
- UI: Kotlin + Jetpack Compose

Instagram/YouTube 쪽 웹 구조가 변경되면 추출기가 일시적으로 동작하지 않을 수 있습니다. 이 경우 bundled extractor 버전을 업데이트해야 합니다.

## 빌드

Android Studio에서 프로젝트를 열고 Gradle Sync 후 실행합니다.

로컬 CLI 빌드에는 JDK 17과 Python 3.11이 필요합니다.

```bash
gradle :app:assembleDebug
```

GitHub Actions도 `main` push 시 자동으로 debug APK를 빌드하며, 성공한 run의 **Artifacts → InstaYtGrabber-debug**에서 APK를 받을 수 있습니다.

## 개인정보 처리 방향
- 별도 백엔드 없음
- 광고 SDK 없음
- 분석/트래킹 SDK 없음
- 다운로드 히스토리 DB 없음
- 앱 백업 비활성화
- Instagram 쿠키는 앱 프로세스 시작 시 초기화
- 공개 Instagram 콘텐츠 처리에는 저장된 로그인 쿠키를 붙이지 않음
- gallery-dl 영구 캐시 비활성화

## 사용 범위
본인이 소유하거나 다운로드 권한이 있는 콘텐츠 및 서비스에서 접근 권한을 부여받은 콘텐츠를 대상으로 사용하세요. DRM, 로그인 장벽 또는 플랫폼 접근 통제를 우회하도록 설계하지 않았습니다.

## 라이선스 참고
이 프로젝트가 사용하는 오픈소스 구성요소에는 각각의 라이선스가 적용됩니다. 특히 gallery-dl은 GPL 계열 라이선스를 사용하므로 APK를 제3자에게 배포할 계획이라면 해당 라이선스 의무를 확인하세요.
