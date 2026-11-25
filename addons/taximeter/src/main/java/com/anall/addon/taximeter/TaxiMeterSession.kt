package com.anall.addon.taximeter

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import com.anall.addon.taximeter.location.GpsMotionSource
import com.anall.addon.taximeter.location.MotionSource
import com.anall.addon.taximeter.location.SimulatedMotionSource
import com.anall.addon.taximeter.meter.FareEngine
import com.anall.addon.taximeter.meter.IdleSign
import com.anall.addon.taximeter.meter.MeterUiState
import com.anall.addon.taximeter.meter.Receipt
import com.anall.addon.taximeter.ui.RECEIPT_PRINT_MS
import com.anall.addon.taximeter.ui.TaxiMeterView
import com.anall.core.AddonHost
import com.anall.core.AddonSession
import com.anall.core.get
import com.anall.core.sound.Sfx
import com.anall.core.sound.SoundLevel
import java.text.NumberFormat
import java.util.Calendar

/**
 * 미터기 본체. 화면([TaxiMeterView])은 들어왔다 나갔다 해도 운행 상태는 세션이 들고 있다.
 * 운행 중에는 호스트의 포그라운드 서비스를 빌려서 내비 앱으로 넘어가도 계속 계산한다.
 */
class TaxiMeterSession(host: AddonHost) : AddonSession(host), TaxiMeterView.Actions {

    private val handler = host.mainHandler
    private val gps = GpsMotionSource(host.appContext)
    private val sim = SimulatedMotionSource()
    private var source: MotionSource = gps
    /** 설정 화면에서 바꾼 요금표. 운행 중 바꾸면 다음 운행부터 적용한다. */
    private var settingsConfig = TaxiSettings.fareConfig(host.settings)
    private var engine = FareEngine(settingsConfig)

    private var state = MeterUiState(config = engine.config)
    private var view: TaxiMeterView? = null
    private var visible = false
    private var ticking = false
    private var lastTickMs = 0L
    private var startedAtMillis = 0L
    private var backgroundWork = false

    init {
        applySimulationSetting()
    }

    // ---- AddonSession ----

    override fun createView(context: Context): View =
        TaxiMeterView(context, this).also {
            view = it
            it.render(state)
        }

    override fun onShow() {
        visible = true
        host.setKeepScreenOn(true)
        if (!gps.hasPermission() && !state.simulation) requestLocation()
        ensureTicking()
    }

    override fun onHide() {
        visible = false
        view = null
        host.setKeepScreenOn(false)
        if (!engine.isRunning) stopTicking()
    }

    override val isBusy: Boolean get() = engine.isRunning

    override fun onSettingsChanged() {
        settingsConfig = TaxiSettings.fareConfig(host.settings)
        if (!engine.isRunning && !engine.isStarted) engine = FareEngine(settingsConfig)
        applySimulationSetting()
        publish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(printReceipt)
        stopTicking()
        stopBackground()
    }

    // ---- 조작 (화면에서 호출) ----

    override fun onStartTrip() {
        if (engine.isRunning) return
        handler.removeCallbacks(printReceipt)
        pendingReceipt = null
        host.playSound(Sfx.BEEP, levelDb = SoundLevel.CONFIRM)
        engine = FareEngine(settingsConfig)
        engine.start(currentSurchargeRate())
        startedAtMillis = System.currentTimeMillis()
        state = state.copy(running = true, idleSign = IdleSign.NONE, receipt = null, config = engine.config)
        publish()
        ensureTicking()
        backgroundWork = host.startBackgroundWork(TITLE, "운행 중")
    }

    override fun onEndTrip() {
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
        host.playSound(Sfx.BEEP, levelDb = SoundLevel.CONFIRM)
        state = state.copy(running = false)
        publish()
        stopBackground()
        if (!visible) stopTicking()
        // "삑" 이 끝난 뒤 영수증이 "지이잉" 소리와 함께 나온다.
        pendingReceipt = receipt
        handler.postDelayed(printReceipt, PRINT_DELAY_MS)
    }

    private var pendingReceipt: Receipt? = null
    private val printerSound by lazy { Sfx.printer(RECEIPT_PRINT_MS.toInt()) }
    private val printReceipt = Runnable {
        val r = pendingReceipt ?: return@Runnable
        pendingReceipt = null
        host.playSound(printerSound, levelDb = SoundLevel.MECHANICAL)
        state = state.copy(receipt = r)
        publish()
    }

    override fun onVacant() {
        if (engine.isRunning) return
        handler.removeCallbacks(printReceipt)
        pendingReceipt = null
        engine = FareEngine(settingsConfig)
        state = state.copy(receipt = null, idleSign = IdleSign.NONE)
        publish()
    }

    override fun onToggleIdleSign(sign: IdleSign) {
        if (engine.isRunning) return
        state = state.copy(idleSign = if (state.idleSign == sign) IdleSign.NONE else sign)
        publish()
    }

    override fun onToggleOutOfCity() {
        state = state.copy(outOfCity = !state.outOfCity)
        publish()
    }

    override fun onHome() = host.goHome()
    override fun onSettings() = host.openSettings()

    // ---- 내부 ----

    private fun requestLocation() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        host.requestPermissions(perms.toTypedArray()) {
            gps.stop()
            if (ticking && source === gps) gps.start()
        }
    }

    private fun applySimulationSetting() {
        val simOn = host.settings.get(TaxiSettings.SIMULATION)
        val next = if (simOn) sim else gps
        if (next !== source) {
            source.stop()
            source = next
            if (ticking) source.start()
        }
        state = state.copy(simulation = simOn, autoNight = host.settings.get(TaxiSettings.AUTO_NIGHT))
    }

    private fun currentSurchargeRate(): Double {
        val c = engine.config
        val night = if (state.autoNight) c.nightRate(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) else 0.0
        val outside = if (state.outOfCity) c.outOfCityRate else 0.0
        return night + outside
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastTickMs) / 1000.0).coerceIn(0.0, 5.0)
            lastTickMs = now
            val sample = source.poll(now, dt)
            engine.update(dt, sample.distanceM, sample.speedKmh, currentSurchargeRate())
            val fareBefore = state.fare
            state = state.copy(
                speedKmh = sample.speedKmh,
                hasFix = sample.hasFix,
                accuracyM = sample.accuracyM,
                satellitesUsed = sample.satellitesUsed,
                satellitesVisible = sample.satellitesVisible,
                gpsProblem = sample.unavailableReason,
                lowSpeed = sample.speedKmh < engine.config.lowSpeedKmh,
            )
            publish()
            if (backgroundWork && state.fare != fareBefore) {
                host.updateBackgroundWork(TITLE, NumberFormat.getIntegerInstance().format(state.fare ?: 0) + "원")
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun ensureTicking() {
        if (ticking) return
        ticking = true
        source.start()
        lastTickMs = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, TICK_MS)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tick)
        source.stop()
    }

    private fun stopBackground() {
        if (backgroundWork) host.stopBackgroundWork()
        backgroundWork = false
    }

    private fun publish() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val shownConfig = if (engine.isStarted) engine.config else settingsConfig
        state = state.copy(
            running = engine.isRunning,
            fare = if (engine.isStarted) engine.totalFare else null,
            metersToNextUnit = engine.metersToNextUnit,
            progressToNextUnit = engine.progressToNextUnit,
            distanceKm = engine.distanceM / 1000.0,
            drivingSec = engine.drivingSec.toLong(),
            lowSpeedSec = engine.lowSpeedSec.toLong(),
            nightRate = if (state.autoNight) shownConfig.nightRate(hour) else 0.0,
            config = shownConfig,
        )
        view?.render(state)
    }

    private companion object {
        const val TICK_MS = 500L
        const val PRINT_DELAY_MS = 250L
        const val TITLE = "택시 미터기 · 주행"
    }
}
