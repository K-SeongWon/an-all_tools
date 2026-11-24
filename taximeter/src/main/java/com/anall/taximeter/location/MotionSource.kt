package com.anall.taximeter.location

/** 미터기 틱마다 읽어 가는 이동 정보. */
data class MotionSample(
    /** 지난 poll 이후 이동 거리(m). */
    val distanceM: Double,
    val speedKmh: Double,
    val hasFix: Boolean,
    val accuracyM: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    /** GPS 가 꺼져 있거나 권한이 없어 동작할 수 없는 상태. */
    val unavailableReason: String? = null,
)

interface MotionSource {
    fun start()
    fun stop()

    /** @param dtSec 지난 poll 이후 경과 시간 */
    fun poll(nowElapsedMs: Long, dtSec: Double): MotionSample
}
