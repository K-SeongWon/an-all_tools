package com.anall.tools

import android.app.Activity
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.anall.core.ui.Palette
import com.anall.tools.ui.AddonSettingsScreen
import com.anall.tools.ui.HomeScreen
import com.anall.tools.ui.HostSettingsScreen

/**
 * 안올툴 화면 하나로 홈 / 애드온 / 설정을 갈아 끼운다. Fragment·AndroidX 없이 View 만 쓴다.
 */
class MainActivity : Activity() {

    private sealed interface Screen {
        data object Home : Screen
        data class Addon(val id: String) : Screen
        data class HostSettings(val back: Screen) : Screen
        data class AddonSettings(val id: String, val back: Screen) : Screen
    }

    private val app get() = application as AnAllApp
    private lateinit var root: FrameLayout
    private var screen: Screen = Screen.Home
    private var started = false
    private val permCallbacks = SparseArray<(Boolean) -> Unit>()
    private var nextRequestCode = 1

    private val onAddonsChanged: () -> Unit = {
        when (val s = screen) {
            Screen.Home -> render()
            is Screen.Addon -> if (app.manager.hasStaleSession(s.id)) {
                toast("새 버전을 받았습니다. 홈으로 나갔다 다시 열면 적용됩니다.")
            }
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.activity = this
        root = FrameLayout(this).apply { setBackgroundColor(Palette.BACKGROUND) }
        setContentView(root)
        hideSystemBars()
        app.manager.addListener(onAddonsChanged)

        // 운행 중인 애드온이 있으면 바로 그 화면으로 돌아간다 (알림을 눌러 들어온 경우 등).
        val busy = app.manager.entries.firstOrNull { app.manager.isBusy(it.id) }
        screen = busy?.let { Screen.Addon(it.id) } ?: Screen.Home

        app.updater.autoCheckIfDue { report ->
            if (report.updated.isNotEmpty()) toast(report.summary)
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        render()
    }

    override fun onStop() {
        leaveCurrent()
        root.removeAllViews()
        started = false
        super.onStop()
    }

    override fun onDestroy() {
        app.manager.removeListener(onAddonsChanged)
        if (app.activity === this) app.activity = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (val s = screen) {
            Screen.Home -> @Suppress("DEPRECATION") super.onBackPressed()
            is Screen.Addon -> if (app.manager.existingSession(s.id)?.onBackPressed() != true) showHome()
            is Screen.HostSettings -> go(s.back)
            is Screen.AddonSettings -> go(s.back)
        }
    }

    // ---------------------------------------------------------------- 화면 전환

    fun showHome() = go(Screen.Home)

    fun openAddon(id: String) = go(Screen.Addon(id))

    fun showHostSettings() = go(Screen.HostSettings(back = screen.backTarget()))

    fun showAddonSettings(id: String) = go(Screen.AddonSettings(id, back = screen.backTarget()))

    private fun Screen.backTarget(): Screen = when (this) {
        is Screen.HostSettings -> this
        is Screen.AddonSettings -> back
        else -> this
    }

    private fun go(next: Screen) {
        leaveCurrent()
        screen = next
        if (next == Screen.Home) app.manager.pruneStaleSessions()
        render()
    }

    /** 지금 화면의 애드온 세션을 숨김 처리한다. */
    private fun leaveCurrent() {
        val s = screen
        if (s is Screen.Addon && app.manager.visibleId == s.id) {
            app.manager.existingSession(s.id)?.let { runCatching { it.onHide() } }
            app.manager.visibleId = null
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun render() {
        if (!started) return
        root.removeAllViews()
        val view: View = when (val s = screen) {
            Screen.Home -> HomeScreen(this, app)
            is Screen.Addon -> {
                val session = app.manager.session(s.id)
                if (session == null) {
                    screen = Screen.Home
                    return render()
                }
                val v = try {
                    session.createView(this)
                } catch (t: Throwable) {
                    toast("애드온을 열 수 없습니다: ${t.message}")
                    screen = Screen.Home
                    return render()
                }
                root.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                app.manager.visibleId = s.id
                session.onShow()
                return
            }
            is Screen.HostSettings -> HostSettingsScreen(this, app)
            is Screen.AddonSettings -> AddonSettingsScreen(this, app, s.id)
        }
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** 설정 화면 등에서 내용이 바뀌었을 때 다시 그린다. */
    fun refresh() = render()

    // ---------------------------------------------------------------- 권한

    fun requestPerms(perms: Array<String>, onResult: (Boolean) -> Unit) {
        val code = nextRequestCode++
        permCallbacks.put(code, onResult)
        requestPermissions(perms, code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val cb = permCallbacks.get(requestCode) ?: return
        permCallbacks.remove(requestCode)
        cb(grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED })
    }

    fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** 헤드유닛 화면을 꽉 채운다. 가장자리를 스와이프하면 시스템 바가 잠깐 나온다. */
    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
