package com.anall.tools

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.anall.core.AddonHost
import com.anall.core.HostApi
import com.anall.core.SettingsStore
import com.anall.core.sound.SoundChannel
import com.anall.core.sound.SoundClip
import com.anall.tools.addon.AddonManager
import com.anall.tools.addon.PrefsSettingsStore
import com.anall.tools.ota.OtaUpdater
import com.anall.tools.sound.HostSound
import java.lang.ref.WeakReference

class AnAllApp : Application() {
    lateinit var manager: AddonManager
        private set
    lateinit var updater: OtaUpdater
        private set
    lateinit var sound: HostSound
        private set
    val mainHandler = Handler(Looper.getMainLooper())

    private var activityRef: WeakReference<MainActivity>? = null
    var activity: MainActivity?
        get() = activityRef?.get()
        set(v) {
            activityRef = v?.let { WeakReference(it) }
        }

    override fun onCreate() {
        super.onCreate()
        sound = HostSound(this)
        manager = AddonManager(this) { id -> HostBridge(this, id) }
        manager.reload()
        updater = OtaUpdater(this, manager)
    }
}

/** 애드온 하나에 빌려주는 호스트 기능. Activity 는 바뀔 수 있으므로 매번 앱에서 찾아 쓴다. */
class HostBridge(private val app: AnAllApp, private val addonId: String) : AddonHost {
    override val appContext: Context get() = app
    override val hostApi: Int get() = HostApi.VERSION
    override val mainHandler: Handler get() = app.mainHandler
    override val settings: SettingsStore =
        PrefsSettingsStore(app.getSharedPreferences("addon_$addonId", Context.MODE_PRIVATE))

    override fun goHome() {
        app.activity?.showHome()
    }

    override fun openSettings() {
        app.activity?.showAddonSettings(addonId)
    }

    override fun toast(text: String) {
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }

    override fun setKeepScreenOn(on: Boolean) {
        val w = app.activity?.window ?: return
        if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun hasPermission(permission: String): Boolean =
        app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun requestPermissions(permissions: Array<String>, onResult: (allGranted: Boolean) -> Unit) {
        val missing = permissions.filter { !hasPermission(it) }
        if (missing.isEmpty()) return onResult(true)
        val a = app.activity ?: return onResult(false)
        a.requestPerms(missing.toTypedArray(), onResult)
    }

    override fun startBackgroundWork(title: String, text: String): Boolean {
        if (!hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) return false
        HostWorkService.start(app, addonId, title, text)
        return true
    }

    override fun updateBackgroundWork(title: String, text: String) = HostWorkService.update(app, title, text)

    override fun stopBackgroundWork() = HostWorkService.stop(app, addonId)

    override fun playSound(clip: SoundClip, channel: SoundChannel, levelDb: Float) = app.sound.play(clip, channel, levelDb)
}

/**
 * 애드온이 빌려 쓰는 포그라운드 서비스. 애드온은 매니페스트에 서비스를 넣을 수 없어서 호스트가 대신 띄운다.
 * 여러 애드온이 동시에 쓰면 마지막 하나가 끝날 때까지 유지한다.
 */
class HostWorkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notification(this, intent?.getStringExtra(EXTRA_TITLE) ?: "안올툴", intent?.getStringExtra(EXTRA_TEXT) ?: "")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: Exception) {
            owners.clear()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "work"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private val owners = LinkedHashSet<String>()

        fun start(ctx: Context, owner: String, title: String, text: String) {
            owners += owner
            val i = Intent(ctx, HostWorkService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun update(ctx: Context, title: String, text: String) {
            if (owners.isEmpty()) return
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(ctx, title, text))
        }

        fun stop(ctx: Context, owner: String) {
            owners -= owner
            if (owners.isEmpty()) ctx.stopService(Intent(ctx, HostWorkService::class.java))
        }

        private fun notification(ctx: Context, title: String, text: String): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "실행 중인 애드온", NotificationManager.IMPORTANCE_LOW))
                }
                Notification.Builder(ctx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            return b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
