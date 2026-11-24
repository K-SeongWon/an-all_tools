# an-all_tools

자동차 안드로이드 올인원(헤드유닛)에서 재미로 돌려보는 앱 모음.

| 모듈 | 설명 |
| --- | --- |
| [`taximeter`](taximeter) | GPS 기반 주행 속도·거리 측정으로 동작하는 디지털 택시 미터기 |

## taximeter — 디지털 택시 미터기

GPS 로 속도와 이동 거리를 재서 실제 택시 미터기처럼 요금이 올라가는 앱.

- **요금 체계 (서울 중형 기준)**: 기본 4,800원(1.6km), 이후 131m 또는 30초(시속 15.72km 이하)마다 100원 — 거리·시간 병산제
- **할증**: 심야 자동(22~23시·02~04시 20%, 23~02시 40%), 할증 버튼으로 시계외 20%
- **화면**: 7-세그먼트 LED 요금, 속도에 맞춰 달리는 도트 말, 다음 100원까지 남은 거리, 주행 거리/시간/저속 시간, 현재 속도
- **표시등**: 빈차 · 주행 · 할증 · 예약 · 호출
- **영수증**: 운행 종료 시 거리/시간 요금 내역이 찍힌 영수증 출력
- **모의 주행**: 설정(⚙)에서 켜면 GPS 없이 가짜 주행으로 테스트
- 구글 플레이 서비스가 없는 헤드유닛을 위해 `LocationManager` GPS 를 직접 사용
- 운행 중에는 포그라운드 서비스로 돌아서 내비 앱으로 넘어가도 계속 계산
- 960×540 기준 디자인을 화면 크기에 맞춰 확대/축소 (가로/세로 모두 지원), 화면 꺼짐 방지

### 빌드 & 설치

```powershell
./gradlew :taximeter:assembleDebug
adb install -r taximeter/build/outputs/apk/debug/taximeter-debug.apk
```

헤드유닛에 adb 가 안 되면 APK 를 USB 메모리로 옮겨 파일 관리자에서 설치한다.

### 구조

```
taximeter/src/main/java/com/anall/taximeter/
├─ meter/      FareConfig·FareEngine(요금 계산, 단위 테스트 있음), MeterController(상태 관리)
├─ location/   GpsMotionSource(GPS 필터링·터널 추정), SimulatedMotionSource(모의 주행)
├─ ui/         MeterScreen, SevenSegment, PixelHorse, ReceiptView
└─ MainActivity, MeterService(포그라운드 서비스), TaxiMeterApp
```

> 재미용 앱입니다. 실제 영업용 요금 산정에 쓰면 안 됩니다.
