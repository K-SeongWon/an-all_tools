package com.anall.tools.addon

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.anall.addon.taximeter.TaxiMeterAddon
import com.anall.core.Addon
import com.anall.core.AddonHost
import com.anall.core.AddonSession
import com.anall.core.HostApi
import com.anall.core.SettingsStore
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File

/** 앱에 기본으로 들어 있는 애드온. 새 내장 애드온은 여기에 추가한다. */
object BuiltinAddons {
    fun create(): List<Addon> = listOf(
        TaxiMeterAddon(),
    )
}

class AddonEntry(val addon: Addon, val source: Source) {
    enum class Source { BUILTIN, OTA }

    val id: String get() = addon.info.id
}

/**
 * 내장 애드온과 OTA 로 받은 애드온을 모아 관리한다. 같은 ID 면 versionCode 가 높은 쪽을 쓴다.
 * 세션(실행 중인 애드온)도 여기서 들고 있어서 홈으로 나가도 운행 같은 작업이 계속된다.
 */
class AddonManager(private val context: Context, private val hostFactory: (String) -> AddonHost) {

    val installed = InstalledAddons(context)
    private val builtins = BuiltinAddons.create().associateBy { it.info.id }

    var entries: List<AddonEntry> = emptyList()
        private set
    val loadErrors = LinkedHashMap<String, String>()

    private val sessions = HashMap<String, Pair<Addon, AddonSession>>()
    private val listeners = LinkedHashSet<() -> Unit>()

    /** 지금 화면에 떠 있는 애드온 ID. */
    var visibleId: String? = null

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    fun entry(id: String): AddonEntry? = entries.firstOrNull { it.id == id }

    /** 이 ID 로 현재 쓸 수 있는 가장 높은 버전 (내장/OTA 중). */
    fun currentVersion(id: String): Int = maxOf(
        builtins[id]?.info?.versionCode ?: 0,
        installed.get(id)?.versionCode ?: 0,
    )

    fun reload() {
        loadErrors.clear()
        val result = LinkedHashMap<String, AddonEntry>()
        builtins.values.forEach { result[it.info.id] = AddonEntry(it, AddonEntry.Source.BUILTIN) }

        for (rec in installed.all()) {
            val builtin = builtins[rec.id]
            if (builtin != null && builtin.info.versionCode >= rec.versionCode) {
                // 앱 업데이트로 내장 버전이 더 새로워졌다. 받아 둔 옛 버전은 지운다.
                installed.remove(rec.id)
                continue
            }
            if (rec.minHostApi > HostApi.VERSION) continue
            try {
                result[rec.id] = AddonEntry(load(rec), AddonEntry.Source.OTA)
            } catch (t: Throwable) {
                Log.w(TAG, "OTA 애드온 로드 실패: ${rec.id}", t)
                loadErrors[rec.id] = t.message ?: t.javaClass.simpleName
            }
        }
        entries = result.values.toList()
        pruneStaleSessions()
        listeners.toList().forEach { it() }
    }

    /** OTA jar 에서 애드온을 꺼낸다. 받은 직후 검증에도 쓴다. */
    fun load(rec: InstalledAddons.Record): Addon {
        val jar = File(rec.file)
        require(jar.isFile) { "파일 없음: ${jar.name}" }
        // 안드로이드 14 부터 동적으로 불러오는 코드는 읽기 전용이어야 한다.
        if (jar.canWrite()) jar.setReadOnly()
        val packagePrefix = rec.entryClass.substringBeforeLast('.') + "."
        val loader = AddonClassLoader(jar.path, Addon::class.java.classLoader!!, packagePrefix)
        val addon = loader.loadClass(rec.entryClass).getDeclaredConstructor().newInstance() as Addon
        require(addon.info.id == rec.id) { "ID 불일치: ${addon.info.id} != ${rec.id}" }
        return addon
    }

    fun session(id: String): AddonSession? {
        val entry = entry(id) ?: return null
        sessions[id]?.let { (addon, s) -> if (addon === entry.addon) return s }
        sessions.remove(id)?.second?.let { safeDestroy(it) }
        val s = entry.addon.createSession(hostFactory(id))
        sessions[id] = entry.addon to s
        return s
    }

    fun existingSession(id: String): AddonSession? = sessions[id]?.second

    fun isBusy(id: String): Boolean = sessions[id]?.second?.isBusy == true

    fun notifySettingsChanged(id: String) {
        sessions[id]?.second?.let { runCatching { it.onSettingsChanged() } }
    }

    /** 업데이트로 바뀐 애드온의 옛 세션을, 화면에 없고 바쁘지 않으면 정리한다. */
    fun pruneStaleSessions() {
        val iter = sessions.entries.iterator()
        while (iter.hasNext()) {
            val (id, pair) = iter.next()
            val current = entry(id)?.addon
            if (current === pair.first) continue
            if (id == visibleId || pair.second.isBusy) continue
            iter.remove()
            safeDestroy(pair.second)
        }
    }

    fun hasStaleSession(id: String): Boolean = sessions[id]?.first?.let { it !== entry(id)?.addon } == true

    private fun safeDestroy(s: AddonSession) {
        runCatching { s.onDestroy() }.onFailure { Log.w(TAG, "세션 종료 실패", it) }
    }

    private companion object {
        const val TAG = "AddonManager"
    }
}

/**
 * 애드온 자기 패키지의 클래스는 jar 에서 먼저 찾는다(child-first).
 * 내장 버전과 같은 클래스 이름이어도 OTA 로 받은 새 코드가 쓰이게 하기 위해서다.
 * core·코틀린·안드로이드 클래스는 앱 것을 그대로 쓴다.
 */
class AddonClassLoader(jarPath: String, parent: ClassLoader, private val ownPrefix: String) :
    DexClassLoader(jarPath, null, null, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith(ownPrefix)) return super.loadClass(name, resolve)
        synchronized(this) {
            findLoadedClass(name)?.let { return it }
            return try {
                findClass(name)
            } catch (e: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}

/** OTA 로 받은 애드온 목록. */
class InstalledAddons(context: Context) {
    private val prefs = context.getSharedPreferences("ota_installed", Context.MODE_PRIVATE)
    val dir = File(context.filesDir, "addons")

    class Record(
        val id: String,
        val name: String,
        val versionCode: Int,
        val versionName: String,
        val minHostApi: Int,
        val entryClass: String,
        val file: String,
    ) {
        fun toJson() = JSONObject()
            .put("id", id).put("name", name).put("versionCode", versionCode).put("versionName", versionName)
            .put("minHostApi", minHostApi).put("entryClass", entryClass).put("file", file)

        companion object {
            fun from(o: JSONObject) = Record(
                o.getString("id"), o.optString("name", o.getString("id")), o.getInt("versionCode"),
                o.optString("versionName", ""), o.optInt("minHostApi", 1), o.getString("entryClass"), o.getString("file"),
            )
        }
    }

    fun all(): List<Record> = prefs.all.values.mapNotNull { v ->
        runCatching { Record.from(JSONObject(v as String)) }.getOrNull()
    }

    fun get(id: String): Record? = prefs.getString(id, null)?.let { runCatching { Record.from(JSONObject(it)) }.getOrNull() }

    fun put(rec: Record) {
        prefs.edit().putString(rec.id, rec.toJson().toString()).apply()
        // 같은 애드온의 옛 jar 와 그 컴파일 캐시(oat/)는 지운다.
        val keep = File(rec.file).nameWithoutExtension + "."
        File(rec.file).parentFile?.walkBottomUp()?.forEach { f ->
            if (f.path == rec.file || f == File(rec.file).parentFile) return@forEach
            if (f.isFile && !f.name.startsWith(keep)) deleteFile(f)
            if (f.isDirectory && f.list().isNullOrEmpty()) f.delete()
        }
    }

    fun remove(id: String) {
        get(id)?.let { File(it.file).parentFile?.let(::deleteRecursivelyRo) }
        prefs.edit().remove(id).apply()
    }

    fun removeAll() {
        all().forEach { remove(it.id) }
    }

    private fun deleteFile(f: File) {
        f.setWritable(true)
        f.delete()
    }

    private fun deleteRecursivelyRo(dir: File) {
        dir.walkBottomUp().forEach { deleteFile(it) }
    }
}

/** 애드온별 SharedPreferences 저장소. */
class PrefsSettingsStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getBool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
