import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val addonProps = Properties().apply { file("addon.properties").reader(Charsets.UTF_8).use { load(it) } }

android {
    namespace = "com.anall.addon.taximeter"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        buildConfigField("int", "ADDON_VERSION_CODE", addonProps.getProperty("versionCode"))
        buildConfigField("String", "ADDON_VERSION_NAME", "\"${addonProps.getProperty("versionName")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // core 는 호스트 앱이 제공한다. 애드온 패키지(.jar)에는 이 모듈의 클래스만 들어간다.
    compileOnly(project(":core"))
    testImplementation(libs.junit)
}
