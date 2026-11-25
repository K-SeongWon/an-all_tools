package com.anall.tools.ota

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.anall.tools.MainActivity
import com.anall.tools.R
import java.io.File

/**
 * 받아 둔 새 앱 APK 를 설치한다.
 *
 * 안드로이드는 일반 앱이 조용히 설치하는 것을 막기 때문에 보통 "설치" 확인 화면이 한 번 뜬다.
 * 안드로이드 12 이상에서 이 앱이 스스로 설치한 적이 있으면(두 번째 자체 업데이트부터) 확인 없이 설치될 수 있다.
 * 설치되는 APK 는 지금 앱과 같은 키로 서명되어 있어야 한다 (아니면 안드로이드가 거부).
 */
object AppInstaller {
    private const val TAG = "AppInstaller"

    /** "출처를 알 수 없는 앱 설치" 허용 여부 (안드로이드 8+). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** 허용 설정 화면을 연다. 사용자가 허용하고 돌아오면 다시 설치를 누르면 된다. */
    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
        }
    }

    fun install(context: Context, apk: File) {
        Thread {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(apk.length())
                    if (Build.VERSION.SDK_INT >= 31) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val result = PendingIntent.getBroadcast(
                        context, id, Intent(context, InstallResultReceiver::class.java), flags,
                    )
                    session.commit(result.intentSender)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "설치 시작 실패", t)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "업데이트 설치 실패: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

/** 설치 진행 결과. 사용자 확인이 필요하면 안드로이드 설치 확인 화면을 띄운다. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // 앱이 새 버전으로 바뀌며 종료된다. 완료 알림은 UpdatedReceiver 가 띄운다.
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "오류 $status"
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                    " (서명 키가 다르면 앱을 지우고 새로 설치해야 합니다)"
                } else {
                    ""
                }
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, "업데이트 실패: $msg$hint", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** 앱이 새 버전으로 바뀐 직후. 화면을 대신 띄울 수는 없어서 "눌러서 열기" 알림을 보낸다. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "앱 업데이트", NotificationManager.IMPORTANCE_DEFAULT))
            }
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        nm.notify(
            2,
            b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("안올툴 업데이트 완료")
                .setContentText("v$version · 눌러서 열기")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "app_update"
    }
}
