package com.anall.taximeter.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class FareEngineTest {

    private fun engine() = FareEngine(FareConfig.SEOUL).apply { start(0.0) }

    /** 60km/h 로 meters 만큼 1초 단위로 달린다. */
    private fun FareEngine.drive(meters: Double, speedKmh: Double = 60.0, rate: Double = 0.0) {
        val perSec = speedKmh / 3.6
        var left = meters
        while (left > 0) {
            val d = minOf(perSec, left)
            update(d / perSec, d, speedKmh, rate)
            left -= d
        }
    }

    private fun FareEngine.idle(seconds: Int, rate: Double = 0.0) {
        repeat(seconds) { update(1.0, 0.0, 0.0, rate) }
    }

    @Test
    fun `기본요금은 출발 즉시 4800원`() {
        val e = engine()
        assertEquals(4_800, e.totalFare)
    }

    @Test
    fun `기본거리 안에서는 요금이 오르지 않는다`() {
        val e = engine()
        e.drive(1_600.0)
        assertEquals(4_800, e.totalFare)
        assertEquals(131.0, e.metersToNextUnit, 0.01)
    }

    @Test
    fun `기본거리 이후 131m 마다 100원`() {
        val e = engine()
        e.drive(1_600.0 + 131.0 * 18 + 1.0)
        assertEquals(18, e.distanceUnits)
        assertEquals(4_800 + 1_800, e.totalFare)
    }

    @Test
    fun `정차 중에는 30초마다 100원`() {
        val e = engine()
        e.drive(1_600.0)
        e.idle(91)
        assertEquals(3, e.timeUnits)
        assertEquals(0, e.distanceUnits)
        assertEquals(5_100, e.totalFare)
        assertEquals(91.0, e.lowSpeedSec, 0.001)
    }

    @Test
    fun `저속 시간도 기본거리를 소진한다`() {
        val e = engine()
        // 1600m / (131/30 m/s) = 366.4초 정차하면 기본거리를 다 쓴다.
        e.idle(367)
        assertEquals(4_800, e.totalFare)
        e.idle(30)
        assertEquals(4_900, e.totalFare)
    }

    @Test
    fun `심야 40퍼센트 할증은 기본요금과 증가분 모두에 붙는다`() {
        val e = FareEngine(FareConfig.SEOUL).apply { start(0.4) }
        e.drive(1_600.0 + 131.0 * 10 + 1.0, rate = 0.4)
        // (4800 + 1000) * 0.4 = 2320 -> 2300
        assertEquals(2_300, e.surcharge)
        assertEquals(5_800 + 2_300, e.totalFare)
    }

    @Test
    fun `심야 시간대 판정`() {
        val c = FareConfig.SEOUL
        assertEquals(0.0, c.nightRate(21), 0.0)
        assertEquals(0.2, c.nightRate(22), 0.0)
        assertEquals(0.4, c.nightRate(23), 0.0)
        assertEquals(0.4, c.nightRate(1), 0.0)
        assertEquals(0.2, c.nightRate(3), 0.0)
        assertEquals(0.0, c.nightRate(4), 0.0)
    }

    @Test
    fun `정지 후에도 요금은 유지된다`() {
        val e = engine()
        e.drive(2_000.0)
        val fare = e.totalFare
        e.stop()
        e.drive(5_000.0)
        assertEquals(fare, e.totalFare)
    }
}
