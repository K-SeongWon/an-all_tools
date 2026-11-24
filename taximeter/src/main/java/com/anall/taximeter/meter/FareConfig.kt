package com.anall.taximeter.meter

/**
 * 택시 요금 체계. 기본값은 서울 중형택시(2023.2 인상 기준).
 *
 * - 기본요금 4,800원 (1.6km)
 * - 이후 131m 마다 100원
 * - 시속 15.72km 이하 저속 주행 시 30초 마다 100원 (거리·시간 병산)
 * - 심야 할증: 22~23시 20%, 23~02시 40%, 02~04시 20%
 * - 시계외 할증: 20%
 */
data class FareConfig(
    val regionName: String = "서울",
    val baseFare: Int = 4_800,
    val baseDistanceM: Double = 1_600.0,
    val unitDistanceM: Double = 131.0,
    val unitTimeSec: Double = 30.0,
    val unitFare: Int = 100,
    val outOfCityRate: Double = 0.20,
) {
    /** 거리 요금과 시간 요금이 같아지는 속도. 이 속도 미만이면 시간 요금으로 계산한다. */
    val lowSpeedKmh: Double get() = unitDistanceM / unitTimeSec * 3.6

    /** 저속 1초가 몇 m 로 환산되는지. */
    val metersPerLowSpeedSecond: Double get() = unitDistanceM / unitTimeSec

    /** 시(0~23)에 따른 심야 할증률. */
    fun nightRate(hour: Int): Double = when (hour) {
        22, 2, 3 -> 0.20
        23, 0, 1 -> 0.40
        else -> 0.0
    }

    companion object {
        val SEOUL = FareConfig()
    }
}
