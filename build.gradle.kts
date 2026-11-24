import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

/*
 * OTA 애드온 패키징
 *
 *   ./gradlew packageAddons
 *
 * addons/<이름> 모듈마다 릴리즈 AAR 의 classes.jar 를 d8 로 dex 변환해서 `<id>-<versionCode>.jar` 를 만들고,
 * build/ota/ 에 모든 애드온 jar 와 addons-index.json(버전·sha256 목록)을 모은다.
 * GitHub 릴리즈에 이 파일들을 올리면 앱이 받아서 바로 적용한다.
 */

fun readAddonProps(dir: File): Properties =
    Properties().apply { File(dir, "addon.properties").reader(Charsets.UTF_8).use { load(it) } }

val addonProjects = subprojects.filter { File(it.projectDir, "addon.properties").isFile }

subprojects {
    if (!File(projectDir, "addon.properties").isFile) return@subprojects
    val props = readAddonProps(projectDir)
    val addonId = props.getProperty("id")
    val versionCode = props.getProperty("versionCode")

    plugins.withId("com.android.library") {
        val android = extensions.getByType(com.android.build.gradle.LibraryExtension::class.java)
        tasks.register("packageAddon") {
            group = "anall"
            description = "애드온을 OTA 용 dex jar 로 만든다."
            dependsOn("bundleReleaseAar")
            val aar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")
            val out = layout.buildDirectory.file("addon/$addonId-$versionCode.jar")
            val propsFile = File(projectDir, "addon.properties")
            inputs.file(aar)
            inputs.file(propsFile)
            outputs.file(out)
            doLast {
                val work = temporaryDir.apply { deleteRecursively(); mkdirs() }
                val classesJar = File(work, "classes.jar")
                ZipFile(aar.get().asFile).use { z ->
                    z.getInputStream(z.getEntry("classes.jar")).use { i -> classesJar.outputStream().use { i.copyTo(it) } }
                }
                // AGP 에 들어 있는 D8 을 그대로 쓴다 (앱 빌드와 같은 버전, 코틀린 메타데이터 호환).
                val androidJar = File(android.sdkDirectory, "platforms/${android.compileSdkVersion}/android.jar")
                val dexDir = File(work, "dex").apply { mkdirs() }
                com.android.tools.r8.D8.run(
                    com.android.tools.r8.D8Command.builder()
                        .addProgramFiles(classesJar.toPath())
                        .addLibraryFiles(androidJar.toPath())
                        .setMinApiLevel(24)
                        .setMode(com.android.tools.r8.CompilationMode.RELEASE)
                        .setOutput(dexDir.toPath(), com.android.tools.r8.OutputMode.DexIndexed)
                        .build(),
                )
                val target = out.get().asFile.apply { parentFile.mkdirs() }
                ZipOutputStream(target.outputStream()).use { zos ->
                    dexDir.listFiles()!!.filter { it.name.endsWith(".dex") }.sortedBy { it.name }.forEach { f ->
                        zos.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    zos.putNextEntry(ZipEntry("addon.properties"))
                    propsFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}

tasks.register("packageAddons") {
    group = "anall"
    description = "모든 애드온 jar 와 addons-index.json 을 build/ota 에 모은다."
    dependsOn(addonProjects.map { "${it.path}:packageAddon" })
    val otaDir = layout.buildDirectory.dir("ota")
    outputs.dir(otaDir)
    // 입력(각 애드온 jar)이 다른 프로젝트에 있어서 최신 여부 판단을 하지 않고 매번 다시 모은다 (가벼운 작업).
    outputs.upToDateWhen { false }
    doLast {
        val dir = otaDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val entries = addonProjects.map { p ->
            val props = readAddonProps(p.projectDir)
            val id = props.getProperty("id")
            val vc = props.getProperty("versionCode").toInt()
            val jar = p.layout.buildDirectory.file("addon/$id-$vc.jar").get().asFile
            val copied = jar.copyTo(File(dir, jar.name), overwrite = true)
            val sha = MessageDigest.getInstance("SHA-256").digest(copied.readBytes()).joinToString("") { "%02x".format(it) }
            linkedMapOf(
                "id" to id,
                "name" to props.getProperty("name"),
                "versionCode" to vc,
                "versionName" to props.getProperty("versionName"),
                "minHostApi" to props.getProperty("minHostApi").toInt(),
                "entryClass" to props.getProperty("entryClass"),
                "description" to props.getProperty("description", ""),
                "file" to copied.name,
                "size" to copied.length(),
                "sha256" to sha,
            )
        }
        val index = linkedMapOf("format" to 1, "addons" to entries)
        File(dir, "addons-index.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(index)))
        println("OTA packages: ${dir.path}")
        dir.listFiles()!!.sortedBy { it.name }.forEach { println("  ${it.name}  ${it.length()} bytes") }
    }
}
