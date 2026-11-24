package com.anall.taximeter.meter

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.anall.taximeter.MeterService
import com.anall.taximeter.location.GpsMotionSource
import com.anall.taximeter.location.MotionSource
import com.anall.taximeter.location.SimulatedMotionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 앱 전체에서 하나만 있는 미터기. 화면이 꺼지거나 내비 앱으로 넘어가도 계속 돌 수 있도록
 * Activity 가 아니라 Application 이 들고 있고, 운행 중에는 [MeterService] 가 프로세스를 살려 둔다.
 */
class MeterController(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("meter", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val engine = FareEngine(FareConfig.SEOUL)
    private val gps = GpsMotionSource(appContext)
    private val sim = SimulatedMotionSource()
    private var source: MotionSource = gps

    private val _state = MutableStateFlow(
        MeterUiState(
            simulation = prefs.getBoolean(KEY_SIM, false),
            autoNight = prefs.getBoolean(KEY_AUTO_NIGHT, true),
        ),
    )
    val state: StateFlow<MeterUiState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var uiVisible = false
    private var startedAtMillis = 0L

    init {
        source = if (_state.value.simulation) sim else gps
    }

    // ---- 화면 생명주기 ----

    fun onUiVisible() {
        uiVisible = true
        ensureTicking()
    }

    fun onUiHidden() {
        uiVisible = false
        if (!engine.isRunning) stopTicking()
    }

    /** 권한을 막 받았을 때 GPS 를 다시 붙인다. */
    fun onPermissionChanged() {
        if (source === gps) {
            gps.stop()
            gps.start()
        }
    }

    // ---- 조작 ----

    fun startTrip() {
        if (engine.isRunning) return
        engine.start(currentSurchargeRate())
        startedAtMillis = System.currentTimeMillis()
        _state.update { it.copy(running = true, idleSign = IdleSign.NONE, receipt = null) }
        publish()
        ensureTicking()
        MeterService.start(appContext, hasLocationPermission = gps.hasPermission())
    }

    fun endTrip() {
        if (!engine.isRunning) return
        engine.stop()
        val c = engine.config
        val receipt = Receipt(
            startedAtMillis = startedAtMillis,
            endedAtMillis = System.currentTimeMillis(),
            distanceKm = engine.distanceM / 1000.0,
            drivingSec = engine.drivingSec.toLong(),
            baseFare = engine.baseFare,
            baseDistanceKm = c.baseDistanceM / 1000.0,
            unitDistanceM = c.unitDistanceM.toInt(),
            unitTimeSec = c.unitTimeSec.toInt(),
            distanceUnits = engine.distanceUnits,
            distanceFare = engine.distanceFare,
            timeUnits = engine.timeUnits,
            timeFare = engine.timeFare,
            surcharge = engine.surcharge,
            total = engine.totalFare,
        )
        _state.update { it.copy(running = false, receipt = receipt) }
        publish()
        MeterService.stop(appContext)
        if (!uiVisible) stopTicking()
    }

    /** 빈차 버튼: 지난 운행 표시를 지운다. */
    fun clearToVacant() {
        if (engine.isRunning) return
        engine.reset()
        _state.update { it.copy(receipt = null, idleSign = IdleSign.NONE) }
        publish()
    }

    fun toggleIdleSign(sign: IdleSign) {
        if (engine.isRunning) return
        _state.update { it.copy(idleSign = if (it.idleSign == sign) IdleSign.NONE else sign) }
    }

    fun toggleOutOfCity() {
        _state.update { it.copy(outOfCity = !it.outOfCity) }
        publish()
    }

    fun setSimulation(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SIM, enabled) }
        val next = if (enabled) sim else gps
        if (next !== source) {
            source.stop()
            source = next
            if (tickJob != null) source.start()
        }
        _state.update { it.copy(simulation = enabled) }
    }

    fun setAutoNight(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_NIGHT, enabled) }
        _state.update { it.copy(autoNight = enabled) }
        publish()
    }

    // ---- 내부 ----

    private fun currentSurchargeRate(): Double {
        val s = _state.value
        val night = if (s.autoNight) engine.config.nightRate(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) else 0.0
        val outside = if (s.outOfCity) engine.config.outOfCityRate else 0.0
        return night + outside
    }

    private fun ensureTicking() {
        if (tickJob?.isActive == true) return
        source.start()
        tickJob = scope.launch {
            var lastMs = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = ((now - lastMs) / 1000.0).coerceIn(0.0, 5.0)
                lastMs = now
                val sample = source.poll(now, dt)
                engine.update(dt, sample.distanceM, sample.speedKmh, currentSurchargeRate())
                _state.update {
                    it.copy(
                        speedKmh = sample.speedKmh,
                        hasFix = sample.hasFix,
                        accuracyM = sample.accuracyM,
                        satellitesUsed = sample.satellitesUsed,
                        satellitesVisible = sample.satellitesVisible,
                        gpsProblem = sample.unavailableReason,
                        lowSpeed = sample.speedKmh < engine.config.lowSpeedKmh,
                    )
                }
                publish()
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
        source.stop()
    }

    private fun publish() {
        val nightHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        _state.update {
            it.copy(
                running = engine.isRunning,
                fare = if (engine.isStarted) engine.totalFare else null,
                metersToNextUnit = engine.metersToNextUnit,
                progressToNextUnit = engine.progressToNextUnit,
                distanceKm = engine.distanceM / 1000.0,
                drivingSec = engine.drivingSec.toLong(),
                lowSpeedSec = engine.lowSpeedSec.toLong(),
                nightRate = if (it.autoNight) engine.config.nightRate(nightHour) else 0.0,
            )
        }
    }

    companion object {
        private const val TICK_MS = 500L
        private const val KEY_SIM = "simulation"
        private const val KEY_AUTO_NIGHT = "auto_night"
    }
}
