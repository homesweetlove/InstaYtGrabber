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

### 프로필 아이디 직접 입력 (v0.3.5)

Instagram 탭의 **프로필 아이디 또는 링크** 입력란은 다음 형태를 모두 지원합니다.

- `example_user`
- `@example_user`
- `instagram.com/example_user`
- `https://www.instagram.com/example_user/?utm_source=share`

앱은 모두 `https://www.instagram.com/example_user/`로 변환해 프로필을 조회합니다.
게시글/릴스 링크와 Instagram 외 사이트는 프로필 검색에서 거부하며, 잘못된 입력은 `IG-INPUT-01` 오류 코드로 기록합니다.

## v0.3.6 Instagram 429·로그인 상태 수정

- **공개 프로필은 게시글 탭을 기본으로 표시:** 스토리 자동 조회에 의한 불필요한 로그인 오류 제거
- **스토리·하이라이트는 로그인 세션이 필요할 수 있음:** 계정이 비공개라는 뜻이 아니며, 미로그인 상태에서는 불필요한 익명 API 요청을 하지 않음
- **429 Too Many Requests:** Instaloader가 429를 받으면 같은 엔진의 반복 호출을 15분간 제한하고, 제한 시간은 앱 재실행 후에도 유지
- **로그인 세션 유지:** 앱 시작 시 쿠키를 삭제하던 문제와 WebView가 공개 프로필 조회 중 전체 쿠키를 지우던 문제 제거. 로그인은 사용자가 명시적으로 로그아웃할 때까지 WebView 쿠키 저장소에 보존
- **최신 엔진:** Instaloader 4.15.4 / gallery-dl 1.32.17
- 429 제한을 무력화하지 않습니다. 네트워크·IP·세션 제한은 Instagram 서비스가 결정합니다.
- 이전 버전이 앱 실행 중 로그인을 지웠다면 v0.3.6 설치 후 Instagram 로그인을 **한 번 다시 연결**해야 합니다.

## v0.3.7: Instagram 로그인 흰 화면·게시글 429 진단

- **로그인 화면:** Android 기본 WebView의 `wv` 식별 토큰을 제거하고 기기에 설치된 Chromium 버전을 그대로 사용하는 모바일 브라우저 UA 적용
- **빈 화면 진단:** 로딩 진행률, 새로고침, 세션 확인, 주 화면 HTTP/네트워크/TLS/렌더러 오류 코드 표시. 화면이 계속 비어 있으면 `IG-LOGIN-BLANK` 기록
- **세션 연결:** 로그인이 실제 완료되어 `sessionid` 쿠키가 생겼을 때만 연결 성공 처리하며 자동으로 앱에 돌아감. 로그인 전에 '세션 확인'을 누른 것은 로그인 성공으로 간주하지 않음
- **로그인 창을 닫지 않고 오류 기록 확인 가능:** 오류 코드를 복사해 문제 분석에 사용
- **공개 게시물 조회:** WebView의 자동 내부 API 페이지 조회 최대 300회→8회, 자동 스크롤 최대 300회→30회로 제한하고 조회 간격 증가. 큰 계정의 목록은 첫 요청에서 일부만 표시될 수 있으며 후속 페이지네이션은 개선 과제로 남김
- **429 요청 제한:** 로그인되지 않은 경우 서버에서 429를 받으면 약 15분간 추가 자동 조회를 중단하고 남은 시간을 안내. 이 제한은 기기에서 과도한 재시도를 피하기 위한 것으로 Instagram 서버의 제한 해제를 보장하지 않음

Instagram이 Android 내장 브라우저 로그인을 차단하거나 보안 확인을 요구하는 경우 앱에서 이를 우회할 수 없습니다. WebView 로그와 UI 메시지를 확인해 주세요. Android System WebView/Chrome 업데이트도 도움이 될 수 있습니다.

## v0.3.8: 로그인 페이지 HTTP 429 진단 및 재시도 중단

실제 v0.3.7 오류 기록에서 Instagram 로그인 페이지 **메인 프레임**이 HTTP 429를 반환한 것이 확인되었습니다. 이는 앱이 계정을 비공개로 오인한 것과 다르며, 서비스 측 요청 제한을 뜻합니다.

- 로그인 페이지 429 응답 시 WebView 재요청을 중단하고 **추가 새로고침을 차단**합니다. 서버에서 `Retry-After`(초 단위)를 전달하면 해당 시간을 우선 사용하고, 없으면 반복 요청을 피하기 위해 앱이 자체적으로 60분을 기다립니다. **앱의 타이머는 Instagram의 제한 해제 시각이 아닙니다.**
- 로그인 페이지 429는 익명 프로필·게시글 조회에도 반영되어 불필요한 연쇄 재시도를 줄입니다. 다음 앱 실행 시에도 대기 상태를 유지합니다.
- 로그인 화면의 **상태 확인**은 로컬 대기 상태만 확인하고 네트워크 요청하지 않습니다. 대기 시간이 끝났을 때 사용자가 직접 한 번 다시 시도할 수 있습니다.
- **외부에서 Instagram 상태 확인**은 다른 브라우저/Instagram 앱에서 같은 사이트가 열리는지 비교하기 위한 기능입니다. 그쪽에서 로그인하더라도 이 앱에 세션이 자동으로 복사되지는 않습니다.
- GitHub 모바일 앱에서 Actions Artifacts가 보이지 않는 문제를 줄이기 위해 같은 저장소에서 만든 PR 빌드를 `test-pr-1-latest` 같은 테스트용 Releases에도 설치 가능한 `.apk` 파일로 직접 게시합니다. 해당 릴리스는 개발용이며 출시 품질 인증을 뜻하지 않습니다.

HTTP 429가 지속된다면 충분한 시간 동안 접속을 멈춘 뒤 **공식 Instagram 앱이나 일반 브라우저에서도** 같은 현상인지 확인하세요. APK를 바꾸는 것만으로 Instagram 서버 제한을 해제할 수는 없습니다.


## v0.3.9: Chrome 로그인 정상 / 앱 내 WebView 제한 대응

Chrome에서는 Instagram 로그인이 가능하지만 앱의 WebView 및 익명 Python 요청에서 HTTP 429가 발생하는 사례를 반영했습니다.

- **Chrome에서 Instagram 열기**: 앱에 아이디를 입력한 뒤 Chrome으로 해당 프로필을 엽니다. Chrome에 이미 로그인했다면 그 세션으로 게시글과 릴스를 탐색할 수 있습니다.
- **Chrome에서 링크 공유**: Chrome의 공유 메뉴에서 `InstaYtGrabber`를 선택하거나 게시물/릴스 주소를 복사해 앱의 **게시글·릴스 링크 저장** 칸에 붙여넣으세요. `/p/...`, `/reel/...`, `/reels/...` 주소를 식별하고 추적 매개변수를 제거합니다.
- **Chrome 세션은 Android WebView/Chaquopy에 자동 이전되지 않습니다.** 링크를 공유하는 것은 로그인 세션을 공유하는 것과 다릅니다. 따라서 Instagram이 실제 미디어 요청에 대해 HTTP 429 또는 인증 제한을 내리면 링크 다운로드도 실패할 수 있습니다. 그런 경우 오류 기록에 `IG-LINK-429`/`IG-LINK-DOWNLOAD`를 남깁니다.
- **세션 쿠키 삭제 방지**: Instagram 게시물 다운로드 전용 WebView의 공개 조회에서도 전체 CookieManager를 비우던 오래된 코드를 제거했습니다.
- **Android 10+ 다운로드 저장 버그**: gallery-dl이 공용 Downloads에 직접 쓰려고 하던 코드를 수정했습니다. 앱의 임시 디렉터리에서 다운로드를 완료한 뒤 MediaStore를 통해 `Downloads/InstaYtGrabber/Instagram/`에 저장합니다.
- Chrome 공유 링크 처리의 유효/잘못된 URL 단위 테스트를 추가했습니다. 로그인 성공과 미디어 다운로드 성공은 **실제 휴대폰**에서 각각 확인해야 합니다.

참고: 앱에서 Chrome 브라우저의 로그인 쿠키를 읽거나 Chrome에 표시된 임의의 프로필 목록을 자동 추출하는 기능은 제공하지 않습니다. 계정 정보나 브라우저 보안 경계를 우회하지 않습니다.
