[![English](https://img.shields.io/badge/README-English-24292f?style=for-the-badge)](./README.md) [![한국어](https://img.shields.io/badge/README-%ED%95%9C%EA%B5%AD%EC%96%B4-24292f?style=for-the-badge)](./README.ko.md)

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
- UI: Kotlin + Jetpack Compose (토스·애플 톤 라이트 테마, `ui/` 디자인 시스템)

Instagram/YouTube 쪽 웹 구조가 변경되면 추출기가 일시적으로 동작하지 않을 수 있습니다. 이 경우 bundled extractor 버전을 업데이트해야 합니다.

## 빌드

Android Studio에서 프로젝트를 열고 Gradle Sync 후 실행합니다.

로컬 CLI 빌드에는 JDK 17과 Python 3.11이 필요합니다.

### YouTube JavaScript 실행 환경

YouTube는 최신 yt-dlp에서 JavaScript 챌린지 해결용 외부 실행 환경이 필요합니다.
이 프로젝트는 [QuickJS-NG v0.17.0](https://github.com/quickjs-ng/quickjs/releases/tag/v0.17.0)의 공식 소스를 Android NDK로 직접 크로스 컴파일합니다.
다른 Android 다운로더의 GPL 코드를 프로젝트에 복사하지 않았습니다.

- 원본: `quickjs-ng/quickjs` (MIT 라이선스)
- 고정 소스 커밋: `6d46d07d04041b40f4f49eaa7fdebe44c314c699`
- 지원 ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- 출력 위치: `app/src/main/jniLibs/<ABI>/libqjs.so`
- yt-dlp: `2026.08.19`, yt-dlp-ejs: `0.8.0` (버전 일치 필요)

GitHub Actions는 APK 빌드 전에 자동으로 QuickJS를 컴파일합니다. 로컬 개발에서는 **Android NDK 27.2.12479018**, `cmake`, `ninja`, `git`, Bash를 준비하고 아래 순서대로 실행하세요.

```bash
bash .github/scripts/build-quickjs-android.sh
gradle :app:assembleDebug
```

QuickJS가 없거나 Android에서 실행할 수 없으면 YouTube 영상·음원 저장 전에 오류 메시지를 표시합니다.
빌드 성공만으로 YouTube 다운로드 성공이 보장되는 것은 아니며 실기기 테스트가 필요합니다.
Instagram 사진·영상 저장은 QuickJS와 별도인 gallery-dl 경로를 사용합니다.

GitHub Actions는 `main` push 시 debug APK를 빌드하고, 성공하면 [GitHub Releases → debug-latest](https://github.com/homesweetlove/InstaYtGrabber/releases/tag/debug-latest)에 업로드합니다. PR에서는 빌드만 확인하며 APK 릴리스를 만들지 않습니다.

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


## 오류 기록 및 문제 신고 (v0.3.5)

앱 상단의 **오류 기록**을 누르면 Instagram 프로필·목록·다운로드와 YouTube 조회·다운로드 실패 기록을 확인할 수 있습니다.

- 단계별 오류 코드: `IG-BASE-01~06`, `IG-SEC-*`, `IG-UI-*`, `YT-*`
- 발생 시각과 오류 메시지 표시, 전체 복사, 기록 삭제
- 기록은 서버로 전송되지 않고 **앱 내부 저장소에 최근 7일 / 최대 120건**만 보관
- 오류 메시지는 전체 URL, 계정 표시, 세션 토큰 및 쿠키를 최대한 가려서 저장합니다. 복사 후에도 민감한 정보가 남지 않았는지 확인하고 공유하세요.
- 앱을 삭제하면 로그도 삭제됩니다. 로그는 다운로드한 미디어나 다운로드 내역을 저장하지 않습니다.

**Instagram 웹 프로필 폴백**은 홈에서 주소만 바꾸는 방식에서 실제 프로필 페이지로 이동하는 방식으로 변경되었습니다. 실기기에서 다시 확인해야 합니다.
