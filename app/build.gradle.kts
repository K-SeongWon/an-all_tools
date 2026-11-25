import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/*
 * 릴리즈 서명 키. 앱 자체 업데이트는 "같은 키로 서명된 APK" 만 설치되므로 모든 릴리즈가 같은 키를 써야 한다.
 * 키 파일과 비밀번호는 저장소에 넣지 않는다 (공개 저장소).
 *
 *  1) 환경변수 (GitHub Actions): ANALL_KEYSTORE_FILE / ANALL_KEYSTORE_PASSWORD / ANALL_KEY_ALIAS / ANALL_KEY_PASSWORD
 *  2) 로컬 PC: ~/.anall/keystore.properties  (scripts/setup-signing.ps1 이 만든다)
 *  3) 둘 다 없으면 디버그 키 (로컬 시험용). ANALL_REQUIRE_RELEASE_KEY=true 면 빌드를 멈춘다 (CI).
 */
val releaseSigning: Map<String, String>? = run {
    val env = System.getenv()
    val envFile = env["ANALL_KEYSTORE_FILE"]
    if (!envFile.isNullOrBlank()) {
        mapOf(
            "storeFile" to envFile,
            "storePassword" to env["ANALL_KEYSTORE_PASSWORD"].orEmpty(),
            "keyAlias" to env["ANALL_KEY_ALIAS"].orEmpty(),
            "keyPassword" to env["ANALL_KEY_PASSWORD"].orEmpty(),
        )
    } else {
        val f = File(System.getProperty("user.home"), ".anall/keystore.properties")
        if (f.isFile) {
            val p = Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } }
            listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { p.getProperty(it).orEmpty() }
        } else {
            null
        }
    }
}
if (releaseSigning == null && System.getenv("ANALL_REQUIRE_RELEASE_KEY") == "true") {
    throw GradleException("릴리즈 서명 키가 없습니다. scripts/setup-signing.ps1 로 GitHub Secrets 를 등록하세요.")
}

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
        releaseSigning?.let { k ->
            create("release") {
                storeFile = file(k.getValue("storeFile"))
                storePassword = k.getValue("storePassword")
                keyAlias = k.getValue("keyAlias")
                keyPassword = k.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // OTA 로 받은 애드온이 코틀린 표준 라이브러리와 core API 를 통째로 쓰기 때문에 줄이지 않는다.
            isMinifyEnabled = false
            signingConfig = if (releaseSigning != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
