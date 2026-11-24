package com.anall.addon.taximeter

import com.anall.addon.taximeter.meter.FareConfig
import com.anall.core.BoolSetting
import com.anall.core.IntSetting
import com.anall.core.SettingHeader
import com.anall.core.SettingSpec
import com.anall.core.SettingsStore
import com.anall.core.TextSetting
import com.anall.core.get

/** 택시 미터기 설정 항목. 기본값은 [FareConfig.SEOUL]. */
object TaxiSettings {
    private val d = FareConfig.SEOUL

    val REGION = TextSetting("region", "요금 지역 이름", d.regionName, "미터기 상단에 표시됩니다")
    val BASE_FARE = IntSetting("base_fare", "기본요금", d.baseFare, 0, 1_000_000, "원")
    val BASE_DISTANCE = IntSetting("base_distance", "기본 거리", d.baseDistanceM.toInt(), 0, 100_000, "m", "기본요금으로 갈 수 있는 거리")
    val UNIT_FARE = IntSetting("unit_fare", "추가 요금", d.unitFare, 1, 100_000, "원", "거리·시간 간격마다 오르는 금액")
    val UNIT_DISTANCE = IntSetting("unit_distance", "거리 요금 간격", d.unitDistanceM.toInt(), 1, 10_000, "m")
    val UNIT_TIME = IntSetting(
        "unit_time", "시간 요금 간격", d.unitTimeSec.toInt(), 1, 3_600, "초",
        "(거리 간격 ÷ 시간 간격) 속도보다 느리면 시간 요금으로 계산합니다",
    )

    val AUTO_NIGHT = BoolSetting("auto_night", "심야 할증 자동 적용", true, "현재 시각에 맞춰 할증을 붙입니다")
    val NIGHT_START = IntSetting("night_start", "심야 시작", d.nightStartHour, 0, 23, "시")
    val NIGHT_END = IntSetting("night_end", "심야 끝", d.nightEndHour, 0, 23, "시")
    val NIGHT_RATE = IntSetting("night_rate", "심야 할증률", (d.nightRate * 100).toInt(), 0, 500, "%")
    val PEAK_START = IntSetting("peak_start", "최고 할증 시작", d.peakStartHour, 0, 23, "시", "시작과 끝을 같게 하면 꺼집니다")
    val PEAK_END = IntSetting("peak_end", "최고 할증 끝", d.peakEndHour, 0, 23, "시")
    val PEAK_RATE = IntSetting("peak_rate", "최고 할증률", (d.peakRate * 100).toInt(), 0, 500, "%")
    val OUT_OF_CITY_RATE = IntSetting("out_of_city_rate", "시계외 할증률", (d.outOfCityRate * 100).toInt(), 0, 500, "%", "할증 버튼으로 켜고 끕니다")

    val SIMULATION = BoolSetting("simulation", "모의 주행", false, "GPS 없이 가짜 주행 데이터로 미터기를 돌려봅니다")

    val ALL: List<SettingSpec> = listOf(
        SettingHeader("기본 요금"), REGION, BASE_FARE, BASE_DISTANCE,
        SettingHeader("추가 요금"), UNIT_FARE, UNIT_DISTANCE, UNIT_TIME,
        SettingHeader("할증"), AUTO_NIGHT, NIGHT_START, NIGHT_END, NIGHT_RATE, PEAK_START, PEAK_END, PEAK_RATE, OUT_OF_CITY_RATE,
        SettingHeader("테스트"), SIMULATION,
    )

    fun fareConfig(s: SettingsStore) = FareConfig(
        regionName = s.get(REGION).ifBlank { d.regionName },
        baseFare = s.get(BASE_FARE),
        baseDistanceM = s.get(BASE_DISTANCE).toDouble(),
        unitDistanceM = s.get(UNIT_DISTANCE).toDouble(),
        unitTimeSec = s.get(UNIT_TIME).toDouble(),
        unitFare = s.get(UNIT_FARE),
        nightStartHour = s.get(NIGHT_START),
        nightEndHour = s.get(NIGHT_END),
        nightRate = s.get(NIGHT_RATE) / 100.0,
        peakStartHour = s.get(PEAK_START),
        peakEndHour = s.get(PEAK_END),
        peakRate = s.get(PEAK_RATE) / 100.0,
        outOfCityRate = s.get(OUT_OF_CITY_RATE) / 100.0,
    )
}
