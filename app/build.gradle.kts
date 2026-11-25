plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/*
 * 서명 키 (일부러 공개한 키). signing/README.md 참고.
 *
 * 안드로이드는 "같은 키로 서명된 APK" 만 업데이트로 설치한다. 그래서 디버그·릴리즈, PC·GitHub Actions 어디서 빌드하든
 * 이 저장소의 같은 키로 서명해서 언제나 앱 자체 업데이트가 되게 한다. 비밀이 아니므로 비밀번호도 여기 그대로 둔다.
 */
val anallKeystore = rootProject.file("signing/anall-public.jks")
val anallKeyPassword = "anall-public-key"

android {
    namespace = "com.anall.tools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anall.tools"
        minSdk = 24
        targetSdk = 35
        // 앱 버전은 루트 gradle.properties 의 anall.versionCode / anall.versionName 한 곳에서 관리한다.
        versionCode = providers.gradleProperty("anall.versionCode").get().toInt()
        versionName = providers.gradleProperty("anall.versionName").get()
        buildConfigField("String", "DEFAULT_UPDATE_REPO", "\"K-SeongWon/an-all_tools\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = anallKeystore
            storePassword = anallKeyPassword
            keyAlias = "anall"
            keyPassword = anallKeyPassword
        }
        create("release") {
            storeFile = anallKeystore
            storePassword = anallKeyPassword
            keyAlias = "anall"
            keyPassword = anallKeyPassword
        }
    }

    buildTypes {
        release {
            // OTA 로 받은 애드온이 코틀린 표준 라이브러리와 core API 를 통째로 쓰기 때문에 줄이지 않는다.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
