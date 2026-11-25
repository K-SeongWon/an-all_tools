# 안올툴 (An-All Tools) — 작업 안내

자동차 안드로이드 올인원(헤드유닛)용 애드온 모음 앱. 구조와 사용법은 [README.md](README.md) 참고.

## ⚠️ 공개 저장소

이 저장소(`K-SeongWon/an-all_tools`)는 **공개(public)** 다. 커밋·이슈·PR·릴리즈에 올리는 모든 내용은 누구나 볼 수 있다.
앱의 OTA 업데이트가 토큰 없이 공개 릴리즈를 읽도록 하려고 공개로 두었다.

커밋하면 안 되는 것:

- 토큰·API 키·비밀번호·인증서·서명 키(`*.jks`, `*.keystore`, `keystore.properties` 등)
  — **예외 하나**: 앱 서명 키 `signing/anall-public.jks` 는 일부러 공개한 키다 (이유는 [signing/README.md](signing/README.md))
- `local.properties`, `.env*`, `.claude/settings.local.json` 같은 로컬 설정
- 개인정보: 이메일, 전화번호, 실명, 집·회사 주소, 차량 번호, 실제 주행 GPS 기록/좌표
- 개인 PC 경로(`C:\Users\...`)가 들어간 로그·스크린샷·설정
- 커밋 작성자 이메일은 GitHub noreply 주소를 쓴다 (이 저장소의 `git config user.email` 에 설정됨)

지키는 방법:

- `.githooks/pre-commit` 이 위 파일 이름과 토큰 패턴을 막는다. 새로 클론하면 한 번 켠다:
  `git config core.hooksPath .githooks`
- 훅에 걸리면 `--no-verify` 로 우회하지 말고 내용을 뺀다.
- 비밀 값이 필요하면 GitHub Actions Secrets 나 앱 설정 화면(사용자가 직접 입력, 기기 안에만 저장)을 쓴다.
- 실수로 올렸으면 커밋을 지우는 것만으로는 부족하다. 해당 토큰/키를 즉시 폐기·재발급한다.

## 작업 원칙

- **가볍게**: 호스트와 애드온은 안드로이드 기본 View + 코틀린만 쓴다. Compose·AndroidX 등 큰 라이브러리를 추가하지 않는다.
  (APK 크기와, OTA 로 받은 애드온이 앱 버전과 무관하게 돌아가는 호환성 때문)
- **core 공개 API 는 추가만 한다.** 지우거나 시그니처를 바꾸면 이미 배포된 OTA 애드온이 깨진다.
  API 를 늘리면 `HostApi.VERSION` 을 올리고, 그 API 를 쓰는 애드온의 `minHostApi` 도 올린다.
- 애드온이 바뀌면 `addons/<이름>/addon.properties` 의 `versionCode` 를 올린다. 앱이 바뀌면 `gradle.properties` 의
  `anall.versionCode`/`anall.versionName` 을 올린다. `v<versionName>` 태그를 올리면 릴리즈가 만들어지고 앱이 받아 간다
  (애드온은 바로 적용, 앱은 "앱 업데이트" 버튼으로 설치).
- **서명 키 `signing/anall-public.jks` 를 바꾸거나 지우지 않는다.** 디버그·릴리즈 모두 이 키로 서명하며,
  키가 바뀌면 앱 자체 업데이트가 안 되고 사용자가 앱을 지우고 다시 설치해야 한다.
- **소리는 [docs/sound.md](docs/sound.md) 표준을 따른다.** `host.playSound` + `SoundLevel` 표준 레벨 + `Sfx` 표준 효과음을 쓰고,
  음원 파일 대신 `Synth` 로 만든다. 볼륨은 안올툴 설정(마스터/음성/효과음)이 정하므로 애드온이 따로 볼륨 설정을 만들지 않는다.
- 애드온은 매니페스트가 없다. 새 권한이 필요하면 `app/src/main/AndroidManifest.xml` 에 추가한다 (이 경우 앱 업데이트 필요).

## 자주 쓰는 명령

```powershell
./gradlew test                     # 단위 테스트
./gradlew :app:assembleDebug       # 디버그 APK
./gradlew packageAddons            # 애드온 OTA 패키지 → build/ota/
./gradlew packageRelease           # 애드온 + 앱 APK + 인덱스 → build/ota/ (릴리즈와 같은 결과)
```
