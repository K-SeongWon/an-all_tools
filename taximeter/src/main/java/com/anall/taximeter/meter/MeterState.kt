package com.anall.taximeter.meter

/** 미터기 위쪽 표시등. 빈차/주행은 운행 상태로 정해지고, 예약/호출은 빈차일 때만 켤 수 있다. */
enum class IdleSign { NONE, RESERVED, CALL }

data class Receipt(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val distanceKm: Double,
    val drivingSec: Long,
    val baseFare: Int,
    val baseDistanceKm: Double,
    val unitDistanceM: Int,
    val unitTimeSec: Int,
    val distanceUnits: Int,
    val distanceFare: Int,
    val timeUnits: Int,
    val timeFare: Int,
    val surcharge: Int,
    val total: Int,
)

data class MeterUiState(
    val running: Boolean = false,
    /** 미터기에 표시할 요금. null 이면 꺼진 숫자만 보인다. */
    val fare: Int? = null,
    val metersToNextUnit: Double = 0.0,
    val progressToNextUnit: Float = 0f,
    val distanceKm: Double = 0.0,
    val drivingSec: Long = 0,
    val lowSpeedSec: Long = 0,
    val speedKmh: Double = 0.0,
    val lowSpeed: Boolean = true,

    val idleSign: IdleSign = IdleSign.NONE,
    val outOfCity: Boolean = false,
    val nightRate: Double = 0.0,

    val hasFix: Boolean = false,
    val accuracyM: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val gpsProblem: String? = null,
    val simulation: Boolean = false,
    val autoNight: Boolean = true,

    val receipt: Receipt? = null,
    val config: FareConfig = FareConfig.SEOUL,
) {
    val surchargeActive: Boolean get() = outOfCity || nightRate > 0.0
}
