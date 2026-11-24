package com.anall.addon.taximeter.meter

/**
 * 택시 요금 체계. 기본값은 서울 중형택시(2023.2 인상 기준)이고 설정 화면에서 모두 바꿀 수 있다.
 *
 * - 기본요금 4,800원 (1.6km)
 * - 이후 131m 마다 100원
 * - 저속(131m/30초 = 시속 15.72km 미만) 주행 시 30초 마다 100원 (거리·시간 병산)
 * - 심야 할증: 22~04시 20%, 그중 23~02시는 40%
 * - 시계외 할증: 20%
 */
data class FareConfig(
    val regionName: String = "서울",
    val baseFare: Int = 4_800,
    val baseDistanceM: Double = 1_600.0,
    val unitDistanceM: Double = 131.0,
    val unitTimeSec: Double = 30.0,
    val unitFare: Int = 100,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 4,
    val nightRate: Double = 0.20,
    val peakStartHour: Int = 23,
    val peakEndHour: Int = 2,
    val peakRate: Double = 0.40,
    val outOfCityRate: Double = 0.20,
) {
    /** 거리 요금과 시간 요금이 같아지는 속도. 이 속도 미만이면 시간 요금으로 계산한다. */
    val lowSpeedKmh: Double get() = unitDistanceM / unitTimeSec * 3.6

    /** 저속 1초가 몇 m 로 환산되는지. */
    val metersPerLowSpeedSecond: Double get() = unitDistanceM / unitTimeSec

    /** 시(0~23)에 따른 심야 할증률. 최고 할증 시간대가 일반 심야 시간대보다 우선한다. */
    fun nightRate(hour: Int): Double = when {
        inWindow(hour, peakStartHour, peakEndHour) -> peakRate
        inWindow(hour, nightStartHour, nightEndHour) -> nightRate
        else -> 0.0
    }

    /** 가장 높은 심야 할증률 (화면 안내용). */
    val maxNightRate: Double get() = maxOf(nightRate, if (peakStartHour != peakEndHour) peakRate else 0.0)

    companion object {
        val SEOUL = FareConfig()

        /** [start, end) 시간대. 자정을 넘어가도 된다. start == end 면 꺼진 것으로 본다. */
        fun inWindow(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}
