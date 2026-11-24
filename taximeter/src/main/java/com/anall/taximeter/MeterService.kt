package com.anall.taximeter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** 운행 중에만 떠 있는 포그라운드 서비스. 화면을 내비로 바꿔도 GPS 와 요금 계산이 멈추지 않게 한다. */
class MeterService : LifecycleService() {

    private var collecting = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        val ok = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)
        }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (collecting) return START_NOT_STICKY
        collecting = true

        lifecycleScope.launch {
            val controller = (application as TaxiMeterApp).controller
            controller.state.map { it.fare }.distinctUntilChanged().collect { fare ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(fare))
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(fare: Int?): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = fare?.let { NumberFormat.getIntegerInstance().format(it) + "원" } ?: "운행 중"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("택시 미터기 · 주행")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "운행 상태", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1

        /** 위치 권한이 있을 때만 띄운다 (안드로이드 14 부터 권한 없이 location 타입 서비스를 띄우면 죽는다). */
        fun start(context: Context, hasLocationPermission: Boolean) {
            if (!hasLocationPermission) return
            val i = Intent(context, MeterService::class.java)
            runCatching { ContextCompat.startForegroundService(context, i) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeterService::class.java))
        }
    }
}
