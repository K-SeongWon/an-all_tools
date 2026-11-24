package com.anall.tools.ota

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.anall.core.HostApi
import com.anall.tools.BuildConfig
import com.anall.tools.addon.AddonManager
import com.anall.tools.addon.InstalledAddons
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * GitHub 릴리즈에서 애드온을 받아 바로 적용한다.
 *
 * 1. `GET /repos/{owner}/{repo}/releases/latest` 로 최신 릴리즈를 찾는다.
 * 2. 첨부 파일 `addons-index.json` 을 읽어 애드온별 versionCode 를 비교한다.
 * 3. 더 새로운 애드온 jar 를 받아 sha256 을 확인하고, 실제로 불러와 본 뒤 설치한다.
 *
 * 비공개 저장소면 GitHub 토큰(Contents 읽기 권한)이 필요하다. 토큰은 api.github.com 에만 보낸다.
 */
class OtaUpdater(private val context: Context, private val manager: AddonManager) {

    private val prefs = context.getSharedPreferences("ota", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Report(
        val updated: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val summary: String
            get() = when {
                error != null -> "실패: $error"
                updated.isNotEmpty() -> "업데이트 적용: " + updated.joinToString(", ")
                skipped.isNotEmpty() -> "최신 상태 (건너뜀: ${skipped.joinToString(", ")})"
                else -> "최신 상태"
            }
    }

    var repo: String
        get() = prefs.getString("repo", null) ?: BuildConfig.DEFAULT_UPDATE_REPO
        set(v) = prefs.edit().putString("repo", v.trim()).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    var autoUpdate: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) = prefs.edit().putBoolean("auto", v).apply()

    /** 개발용: GitHub 대신 이 주소의 addons-index.json 을 쓴다 (디버그 빌드 전용). */
    var indexUrlOverride: String
        get() = if (BuildConfig.DEBUG) prefs.getString("index_url", "") ?: "" else ""
        set(v) = prefs.edit().putString("index_url", v.trim()).apply()

    val lastCheckMillis: Long get() = prefs.getLong("last_check", 0L)
    val lastSummary: String get() = prefs.getString("last_summary", "") ?: ""

    @Volatile
    var checking = false
        private set

    /** 앱을 켤 때: 자동 업데이트가 켜져 있고 마지막 확인 후 1시간이 지났으면 확인한다. */
    fun autoCheckIfDue(onDone: (Report) -> Unit) {
        if (!autoUpdate) return
        if (System.currentTimeMillis() - lastCheckMillis < AUTO_INTERVAL_MS) return
        checkNow(onDone)
    }

    fun checkNow(onDone: (Report) -> Unit) {
        if (checking) return
        checking = true
        executor.execute {
            val report = try {
                runCheck()
            } catch (t: Throwable) {
                Log.w(TAG, "업데이트 확인 실패", t)
                Report(error = t.message ?: t.javaClass.simpleName)
            }
            prefs.edit().putLong("last_check", System.currentTimeMillis()).putString("last_summary", report.summary).apply()
            main.post {
                checking = false
                if (report.updated.isNotEmpty()) manager.reload()
                onDone(report)
            }
        }
    }

    private fun runCheck(): Report {
        val source = resolveSource()
        val addons = source.index.getJSONArray("addons")
        val updated = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (i in 0 until addons.length()) {
            val a = addons.getJSONObject(i)
            val id = a.getString("id")
            val name = a.optString("name", id)
            val versionCode = a.getInt("versionCode")
            val versionName = a.optString("versionName", versionCode.toString())
            val minHostApi = a.optInt("minHostApi", 1)
            require(ID_PATTERN.matches(id)) { "잘못된 애드온 ID: $id" }

            if (versionCode <= currentVersionOnMain(id)) continue
            if (minHostApi > HostApi.VERSION) {
                skipped += "$name $versionName (앱 업데이트 필요)"
                continue
            }

            val bytes = source.download(a.getString("file"))
            val expected = a.getString("sha256").lowercase()
            val actual = sha256(bytes)
            if (actual != expected) throw IOException("$name 파일 검증 실패 (sha256 불일치)")

            val dir = File(manager.installed.dir, id).apply { mkdirs() }
            val target = File(dir, "$versionCode.jar")
            val tmp = File(dir, "$versionCode.jar.tmp")
            tmp.delete()
            tmp.writeBytes(bytes)
            if (target.exists()) {
                target.setWritable(true)
                target.delete()
            }
            if (!tmp.renameTo(target)) throw IOException("$name 저장 실패")
            target.setReadOnly()

            val rec = InstalledAddons.Record(id, name, versionCode, versionName, minHostApi, a.getString("entryClass"), target.path)
            // 실제로 불러와서 버전까지 맞는지 확인한 뒤에만 설치로 기록한다.
            val loaded = runCatching { manager.load(rec) }
            val loadedVersion = loaded.getOrNull()?.info?.versionCode
            if (loadedVersion != versionCode) {
                target.setWritable(true)
                target.delete()
                throw IOException("$name 불러오기 실패: " + (loaded.exceptionOrNull()?.message ?: "버전 불일치 $loadedVersion"))
            }
            manager.installed.put(rec)
            updated += "$name $versionName"
        }
        return Report(updated, skipped)
    }

    private fun currentVersionOnMain(id: String): Int {
        // AddonManager 는 메인 스레드 객체지만 버전 조회는 읽기만 하므로 그대로 쓴다.
        return manager.currentVersion(id)
    }

    // ---------------------------------------------------------------- 소스

    private class Source(val index: JSONObject, val download: (fileName: String) -> ByteArray)

    private fun resolveSource(): Source {
        val override = indexUrlOverride
        if (override.isNotBlank()) {
            val base = override.substringBeforeLast('/') + "/"
            val index = JSONObject(String(Http.get(override, null, JSON), Charsets.UTF_8))
            return Source(index) { name -> Http.get(base + checkFileName(name), null, BINARY) }
        }

        val (owner, name) = parseRepo(repo)
        val tk = token.ifBlank { null }
        val release = try {
            JSONObject(String(Http.get("https://api.github.com/repos/$owner/$name/releases/latest", tk, GITHUB_JSON), Charsets.UTF_8))
        } catch (e: Http.StatusException) {
            throw IOException(
                when (e.code) {
                    404 -> if (tk == null) "릴리즈를 찾을 수 없음 (비공개 저장소면 토큰이 필요합니다)" else "릴리즈를 찾을 수 없음"
                    401, 403 -> "GitHub 인증 실패 (토큰 확인)"
                    else -> "GitHub 응답 ${e.code}"
                },
            )
        }
        val assets = release.getJSONArray("assets")
        val urls = HashMap<String, String>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            urls[asset.getString("name")] = asset.getString("url")
        }
        val indexUrl = urls[INDEX_FILE] ?: throw IOException("최신 릴리즈(${release.optString("tag_name")})에 $INDEX_FILE 이 없습니다")
        val index = JSONObject(String(Http.get(indexUrl, tk, BINARY), Charsets.UTF_8))
        return Source(index) { file ->
            val url = urls[checkFileName(file)] ?: throw IOException("릴리즈에 $file 이 없습니다")
            Http.get(url, tk, BINARY)
        }
    }

    private fun checkFileName(name: String): String {
        require(FILE_PATTERN.matches(name)) { "잘못된 파일 이름: $name" }
        return name
    }

    companion object {
        private const val TAG = "OtaUpdater"
        private const val INDEX_FILE = "addons-index.json"
        private const val JSON = "application/json"
        private const val GITHUB_JSON = "application/vnd.github+json"
        private const val BINARY = "application/octet-stream"
        private const val AUTO_INTERVAL_MS = 60 * 60 * 1000L
        private val ID_PATTERN = Regex("[a-z0-9_]{1,40}")
        private val FILE_PATTERN = Regex("[A-Za-z0-9_.-]{1,80}\\.jar")

        /** "owner/repo" 또는 "https://github.com/owner/repo" 를 받는다. */
        fun parseRepo(input: String): Pair<String, String> {
            val cleaned = input.trim()
                .removePrefix("https://").removePrefix("http://").removePrefix("github.com/")
                .removeSuffix(".git").trim('/')
            val parts = cleaned.split('/')
            require(parts.size == 2 && parts.all { Regex("[A-Za-z0-9_.-]+").matches(it) }) { "저장소 형식은 owner/repo 입니다" }
            return parts[0] to parts[1]
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** 리다이렉트를 직접 따라가며, 인증 헤더는 api.github.com 에만 붙이는 작은 HTTP 클라이언트. */
internal object Http {
    class StatusException(val code: Int, message: String) : IOException(message)

    private const val MAX_BYTES = 20 * 1024 * 1024
    private val LOCAL_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    /** 네트워크가 잠깐 끊기는 차 안 환경을 생각해 한 번은 다시 시도한다. */
    fun get(url: String, token: String?, accept: String): ByteArray = try {
        getOnce(url, token, accept)
    } catch (e: StatusException) {
        throw e
    } catch (e: IOException) {
        Thread.sleep(1_000)
        getOnce(url, token, accept)
    }

    private fun getOnce(url: String, token: String?, accept: String): ByteArray {
        var current = URL(url)
        repeat(6) {
            val local = current.host in LOCAL_HOSTS
            require(current.protocol == "https" || (BuildConfig.DEBUG && local)) { "https 주소만 허용됩니다: ${current.host}" }
            val c = current.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("Accept", accept)
                c.setRequestProperty("User-Agent", "AnAllTools")
                if (token != null && current.host == "api.github.com") {
                    c.setRequestProperty("Authorization", "Bearer $token")
                    c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                when (val code = c.responseCode) {
                    in 200..299 -> return c.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) throw IOException("파일이 너무 큽니다")
                        }
                        out.toByteArray()
                    }
                    301, 302, 303, 307, 308 -> {
                        val loc = c.getHeaderField("Location") ?: throw IOException("리다이렉트 주소 없음")
                        current = URL(current, loc)
                    }
                    else -> throw StatusException(code, "HTTP $code")
                }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("리다이렉트가 너무 많습니다")
    }
}
