# 안올툴 (An-All Tools)

자동차 안드로이드 올인원(헤드유닛)에서 재미로 돌려보는 **애드온 모음 앱**.
앱 하나 안에 여러 기능이 애드온으로 들어가고, 애드온은 **앱을 다시 설치하지 않아도 GitHub 릴리즈에서 OTA 로 업데이트**된다.

- 가볍게: AndroidX·Compose 없이 안드로이드 기본 View + 코틀린만 사용 → 릴리즈 APK 약 0.7MB, 애드온 패키지 약 50KB
- 자원 적게: 화면에 보일 때만 그리고, 0.5초 틱 하나로 갱신 (애니메이션 루프 없음)
- 헤드유닛 대응: 해상도와 관계없이 960×540 기준 화면을 통째로 확대·축소, 가로/세로 모두 지원, 전체화면

## 애드온

| 애드온 | 설명 |
| --- | --- |
| [택시 미터기](addons/taximeter) | GPS 로 속도·거리를 재서 실제 택시처럼 요금이 오르는 디지털 미터기 |

### 택시 미터기

- 거리·시간 병산제: 기본 4,800원(1.6km), 이후 131m 또는 30초(저속)마다 100원 — **설정에서 모두 변경 가능**
- 할증: 심야 자동(기본 22~04시 20%, 23~02시 40%, 시간대·비율 변경 가능), 할증 버튼으로 시계외 할증
- 7-세그먼트 LED 요금, 속도에 맞춰 달리는 도트 말, 빈차/주행/할증/예약/호출 표시등, 운행 종료 시 영수증
- 효과음: 운행 시작·종료 "삑", 영수증 출력 "지이잉" (안올툴 소리 설정을 따름)
- 구글 플레이 서비스가 없는 헤드유닛을 위해 `LocationManager` GPS 직접 사용, 정차 중 GPS 튐 무시, 터널 구간 추정
- 운행 중에는 홈으로 나가거나 내비로 바꿔도 계속 계산 (호스트의 포그라운드 서비스)
- 설정의 **모의 주행**으로 GPS 없이 테스트

## 구조

```
core/               애드온 API (Addon, AddonSession, AddonHost, 설정 항목) + 가벼운 UI 도구
app/                호스트 앱: 홈, 설정, 애드온 로더, OTA 업데이터, 포그라운드 서비스
addons/taximeter/   택시 미터기 애드온 (addon.properties 에 id/버전)
```

- 애드온은 `Addon` 을 상속한 진입 클래스 하나로 시작한다. 설정 항목(`settings()`)을 선언하면 호스트가 설정 화면을 자동으로 그린다.
- 애드온은 매니페스트를 가질 수 없어서 권한·포그라운드 서비스는 `AddonHost` 를 통해 호스트 것을 빌려 쓴다.
  새 권한이 필요한 애드온은 `app/src/main/AndroidManifest.xml` 에 권한을 추가하고 앱을 한 번 업데이트해야 한다.
- 소리: 모든 애드온은 [소리 표준](docs/sound.md)(채널·표준 레벨·표준 효과음)을 따르고, 안올툴 설정의 **마스터/음성/효과음** 볼륨이 전체에 적용된다.
- OTA 로 받은 애드온은 자기 패키지 클래스를 먼저 찾는 클래스 로더(child-first)로 불러와서, 내장 버전과 같은 클래스 이름이어도 새 코드가 쓰인다.
- `core` 의 공개 API 는 OTA 애드온이 그대로 호출하므로 지우거나 바꾸지 않고 추가만 한다. 추가할 때는 `HostApi.VERSION` 을 올리고, 새 API 를 쓰는 애드온은 `minHostApi` 를 올린다.

## OTA 업데이트

앱은 켤 때(1시간 간격) 또는 설정 → **지금 확인** 에서 GitHub 최신 릴리즈의 `addons-index.json` 을 확인한다.

- **애드온**: 새 버전을 받아 sha256 확인 → 실제 로드 확인 후 **바로 적용**한다. 운행 중인 애드온은 끝나고 홈으로 나갔을 때 바뀐다.
- **앱 자체**: 새 APK 를 미리 받아 두고 홈 상단에 **"앱 업데이트"** 버튼을 띄운다. 누르면 안드로이드 설치 확인 화면이 뜨고,
  "업데이트" 한 번이면 끝난다 (설정·애드온 유지). 처음 한 번은 안올툴에 "이 출처의 앱 허용"을 켜야 한다.
  안드로이드 12 이상이면 두 번째 업데이트부터 확인 화면 없이 설치될 수 있다. 실행 중인 작업(운행 등)이 있으면 설치하지 않는다.
- 문제가 생기면 설정 → **내장 버전으로 되돌리기** (애드온).

### 릴리즈 하는 법

1. 바뀐 애드온의 `addons/<이름>/addon.properties` → `versionCode` 를 올린다.
2. 앱이 바뀌었으면 `gradle.properties` → `anall.versionCode`, `anall.versionName` 을 올린다.
3. 태그를 올린다 (태그는 `v` + `anall.versionName` 과 같아야 한다):
   ```powershell
   git tag v0.3.1
   git push origin v0.3.1
   ```
4. GitHub Actions 가 테스트·빌드 후 릴리즈에 `addons-index.json`, 애드온 jar, `an-all-tools-<버전>.apk` 를 올린다.

로컬에서 릴리즈 파일만 만들기: `./gradlew packageRelease` → `build/ota/`

### 서명 키

앱 자체 업데이트는 **같은 키로 서명된 APK** 만 설치된다. 그래서 디버그·릴리즈, PC·GitHub Actions 모두
저장소의 [signing/anall-public.jks](signing/README.md) 로 서명한다. **일부러 공개한 키**이며, 이유와 감수하는 위험은 [signing/README.md](signing/README.md) 에 있다.

## 빌드 & 설치

```powershell
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

헤드유닛에 adb 가 안 되면 릴리즈에 올라간 APK 를 USB 메모리로 옮겨 파일 관리자에서 설치한다.

## 새 애드온 만들기

1. `addons/taximeter` 를 참고해 `addons/<이름>/` 에 `addon.properties`, `build.gradle.kts` 를 만든다 (폴더가 있으면 자동으로 빌드에 포함).
2. `Addon` 을 상속한 진입 클래스와 `AddonSession` 을 만든다. 화면은 `core.ui` 의 `ScaledLayout`, `SevenSegmentView` 등을 쓰면 편하다.
3. 앱에 기본 탑재하려면 `app/build.gradle.kts` 의존성과 `BuiltinAddons` 에 추가한다. OTA 전용으로만 배포해도 된다.

## 기여 / 공개 저장소 주의

공개 저장소입니다. 토큰·키·개인정보는 커밋하지 않습니다. 클론 후 한 번 `git config core.hooksPath .githooks` 로
비밀 값 차단 훅을 켜 주세요. 자세한 규칙은 [CLAUDE.md](CLAUDE.md).

> 재미용 앱입니다. 실제 영업용 요금 산정에 쓰면 안 됩니다.
