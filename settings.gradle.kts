pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "an-all_tools"

include(":core")
include(":app")

// 애드온: addons/<이름> 폴더에 addon.properties 가 있으면 자동으로 포함한다.
file("addons").listFiles()
    ?.filter { File(it, "addon.properties").isFile }
    ?.sortedBy { it.name }
    ?.forEach { include(":addons:${it.name}") }
