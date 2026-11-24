// 호스트 앱과 애드온이 함께 쓰는 애드온 API + 가벼운 UI 도구.
// 여기 있는 공개 API 는 OTA 로 받은 애드온이 그대로 호출하므로, 지우거나 시그니처를 바꾸면 안 된다.
// 기능을 늘릴 때는 새 메서드를 추가하고 HostApi.VERSION 을 올린다.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.anall.core"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
