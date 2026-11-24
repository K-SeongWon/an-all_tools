plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.anall.tools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anall.tools"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "DEFAULT_UPDATE_REPO", "\"K-SeongWon/an-all_tools\"")
    }

    buildTypes {
        release {
            // OTA 로 받은 애드온이 코틀린 표준 라이브러리와 core API 를 통째로 쓰기 때문에 줄이지 않는다.
            isMinifyEnabled = false
            // 개인용 사이드로드 앱이라 디버그 키로 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += listOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(project(":core"))
    // 내장 애드온. 새 애드온을 앱에 기본 탑재하려면 여기에 추가하고 BuiltinAddons 에 등록한다.
    implementation(project(":addons:taximeter"))
}
