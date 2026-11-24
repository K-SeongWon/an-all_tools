package com.anall.taximeter.meter

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 거리·시간 병산제 요금 계산기. 안드로이드 의존성이 없어 단위 테스트가 쉽다.
 *
 * 저속(기준 속도 미만)일 때는 경과 시간을 m 로 환산해 누적하고, 그 이상일 때는 실제 이동 거리를 누적한다.
 * 누적값이 기본 거리를 다 쓰면 그 뒤로는 [FareConfig.unitDistanceM] 마다 [FareConfig.unitFare] 가 오른다.
 */
class FareEngine(val config: FareConfig = FareConfig.SEOUL) {

    var isRunning = false
        private set

    var distanceM = 0.0
        private set
    var drivingSec = 0.0
        private set
    var lowSpeedSec = 0.0
        private set

    private var baseRemainingM = config.baseDistanceM
    private var unitProgressM = 0.0

    var distanceUnits = 0
        private set
    var timeUnits = 0
        private set

    /** 할증 금액(원, 반올림 전). */
    private var surchargeRaw = 0.0
    var surchargeRate = 0.0
        private set

    fun start(surchargeRate: Double) {
        reset()
        isRunning = true
        this.surchargeRate = surchargeRate
        surchargeRaw = config.baseFare * surchargeRate
    }

    fun stop() {
        isRunning = false
    }

    fun reset() {
        isRunning = false
        distanceM = 0.0
        drivingSec = 0.0
        lowSpeedSec = 0.0
        baseRemainingM = config.baseDistanceM
        unitProgressM = 0.0
        distanceUnits = 0
        timeUnits = 0
        surchargeRaw = 0.0
        surchargeRate = 0.0
    }

    /**
     * @param dtSec 지난 호출 이후 경과 시간(초)
     * @param movedM 그 사이 이동 거리(m)
     * @param speedKmh 현재 속도
     * @param surchargeRate 현재 적용할 할증률(심야 + 시계외)
     */
    fun update(dtSec: Double, movedM: Double, speedKmh: Double, surchargeRate: Double) {
        if (!isRunning || dtSec <= 0.0) return
        this.surchargeRate = surchargeRate

        drivingSec += dtSec
        distanceM += max(0.0, movedM)

        val lowSpeed = speedKmh < config.lowSpeedKmh
        var equivalentM = if (lowSpeed) {
            lowSpeedSec += dtSec
            dtSec * config.metersPerLowSpeedSecond
        } else {
            max(0.0, movedM)
        }

        if (baseRemainingM > 0.0) {
            val used = min(baseRemainingM, equivalentM)
            baseRemainingM -= used
            equivalentM -= used
        }

        unitProgressM += equivalentM
        while (unitProgressM >= config.unitDistanceM) {
            unitProgressM -= config.unitDistanceM
            if (lowSpeed) timeUnits++ else distanceUnits++
            surchargeRaw += config.unitFare * surchargeRate
        }
    }

    val baseFare: Int get() = if (isStarted) config.baseFare else 0
    val distanceFare: Int get() = distanceUnits * config.unitFare
    val timeFare: Int get() = timeUnits * config.unitFare

    /** 할증 금액. 100원 단위로 반올림. */
    val surcharge: Int get() = (surchargeRaw / 100.0).roundToInt() * 100

    val totalFare: Int get() = baseFare + distanceFare + timeFare + surcharge

    /** 한 번이라도 시작해서 요금이 잡혀 있는지. */
    val isStarted: Boolean get() = isRunning || drivingSec > 0.0

    /** 다음 100원이 오르기까지 남은 거리(m, 저속이면 시간 환산 거리). */
    val metersToNextUnit: Double get() = baseRemainingM + (config.unitDistanceM - unitProgressM)

    /** 다음 요금 단위까지의 진행률 0..1 (기본 거리 구간에서는 기본 거리 소진율). */
    val progressToNextUnit: Float
        get() = if (baseRemainingM > 0.0) {
            (1.0 - baseRemainingM / config.baseDistanceM).toFloat()
        } else {
            (unitProgressM / config.unitDistanceM).toFloat()
        }.coerceIn(0f, 1f)
}
