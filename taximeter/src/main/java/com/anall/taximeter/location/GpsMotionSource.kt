package com.anall.taximeter.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * 순수 [LocationManager] GPS 프로바이더를 쓴다. 차량용 올인원 헤드유닛은 구글 플레이 서비스가 없는 경우가 많아서
 * FusedLocationProvider 대신 이쪽을 택했다.
 *
 * - 정확도가 나쁜 fix 와 순간이동(시속 250km 초과) 은 버린다.
 * - 시속 [MIN_MOVING_KMH] 미만은 정차로 보고 거리를 더하지 않는다 (정차 중 GPS 튐 방지).
 * - 터널처럼 fix 가 끊기면 최대 [DEAD_RECKON_MAX_MS] 동안 마지막 속도로 거리를 추정하고,
 *   fix 가 돌아오면 추정한 만큼은 실제 거리에서 빼서 이중 계산을 막는다.
 */
class GpsMotionSource(context: Context) : MotionSource, LocationListener {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var started = false
    private var last: Location? = null
    private var lastFixElapsedMs = 0L
    private var speedKmh = 0.0
    private var pendingM = 0.0
    private var estimatedSinceFixM = 0.0
    private var accuracy: Float? = null
    private var satsUsed = 0
    private var satsVisible = 0

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            satsUsed = used
            satsVisible = status.satelliteCount
        }

        override fun onStopped() {
            satsUsed = 0
            satsVisible = 0
        }
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun gpsEnabled(): Boolean = runCatching {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override fun start() {
        if (started || !hasPermission()) return
        val ok = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
            lm.registerGnssStatusCallback(gnssCallback, mainHandler)
        }.isSuccess
        started = ok
    }

    override fun stop() {
        if (!started) return
        runCatching { lm.removeUpdates(this) }
        runCatching { lm.unregisterGnssStatusCallback(gnssCallback) }
        started = false
        last = null
        speedKmh = 0.0
        pendingM = 0.0
        estimatedSinceFixM = 0.0
    }

    override fun onLocationChanged(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) {
            accuracy = loc.accuracy
            return
        }
        val prev = last
        var spd: Double? = if (loc.hasSpeed()) loc.speed * 3.6 else null

        if (prev != null) {
            val dtSec = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
            val d = prev.distanceTo(loc).toDouble()
            if (dtSec > 0 && d / dtSec * 3.6 > MAX_PLAUSIBLE_KMH) return // 튀는 좌표
            if (spd == null && dtSec > 0) spd = d / dtSec * 3.6
            if ((spd ?: 0.0) >= MIN_MOVING_KMH) {
                pendingM += max(0.0, d - estimatedSinceFixM)
            }
        }
        estimatedSinceFixM = 0.0
        val s = spd ?: 0.0
        speedKmh = if (s < MIN_MOVING_KMH) 0.0 else s
        accuracy = if (loc.hasAccuracy()) loc.accuracy else null
        last = loc
        lastFixElapsedMs = SystemClock.elapsedRealtime()
    }

    override fun poll(nowElapsedMs: Long, dtSec: Double): MotionSample {
        if (!hasPermission()) {
            return MotionSample(0.0, 0.0, false, unavailableReason = "위치 권한 없음")
        }
        if (!started) start()
        if (!gpsEnabled()) {
            return MotionSample(0.0, 0.0, false, unavailableReason = "GPS 꺼짐")
        }

        val staleMs = if (last == null) Long.MAX_VALUE else nowElapsedMs - lastFixElapsedMs
        val hasFix = staleMs < FIX_STALE_MS
        if (!hasFix) {
            if (staleMs < DEAD_RECKON_MAX_MS && speedKmh > 0.0) {
                val est = speedKmh / 3.6 * dtSec
                pendingM += est
                estimatedSinceFixM += est
            } else {
                speedKmh = 0.0
            }
        }
        val moved = pendingM
        pendingM = 0.0
        return MotionSample(
            distanceM = moved,
            speedKmh = speedKmh,
            hasFix = hasFix,
            accuracyM = accuracy,
            satellitesUsed = satsUsed,
            satellitesVisible = satsVisible,
        )
    }

    // 구형 API(28 이하)에서는 아래 콜백이 추상 메서드라 구현이 필요하다.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_MOVING_KMH = 2.0
        const val MAX_PLAUSIBLE_KMH = 250.0
        const val FIX_STALE_MS = 3_000L
        const val DEAD_RECKON_MAX_MS = 30_000L
    }
}
