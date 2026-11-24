package com.anall.taximeter.location

import kotlin.random.Random

/**
 * 집에서도 미터기를 돌려볼 수 있는 가짜 주행. 가속 → 정속 → 감속 → 신호 대기를 반복한다.
 */
class SimulatedMotionSource(private val random: Random = Random.Default) : MotionSource {

    private enum class Phase { ACCEL, CRUISE, DECEL, STOP }

    private var phase = Phase.STOP
    private var phaseLeftSec = 3.0
    private var speedKmh = 0.0
    private var targetKmh = 50.0

    override fun start() = Unit
    override fun stop() {
        phase = Phase.STOP
        phaseLeftSec = 3.0
        speedKmh = 0.0
    }

    override fun poll(nowElapsedMs: Long, dtSec: Double): MotionSample {
        val before = speedKmh
        phaseLeftSec -= dtSec
        when (phase) {
            Phase.ACCEL -> {
                speedKmh = (speedKmh + 9.0 * dtSec).coerceAtMost(targetKmh)
                if (speedKmh >= targetKmh) next(Phase.CRUISE, random.nextDouble(8.0, 25.0))
            }
            Phase.CRUISE -> {
                speedKmh = (targetKmh + random.nextDouble(-2.0, 2.0)).coerceAtLeast(0.0)
                if (phaseLeftSec <= 0) next(Phase.DECEL, 0.0)
            }
            Phase.DECEL -> {
                speedKmh = (speedKmh - 11.0 * dtSec).coerceAtLeast(0.0)
                if (speedKmh <= 0.0) next(Phase.STOP, random.nextDouble(4.0, 20.0))
            }
            Phase.STOP -> {
                speedKmh = 0.0
                if (phaseLeftSec <= 0) {
                    targetKmh = listOf(30.0, 40.0, 50.0, 60.0, 70.0, 80.0).random(random)
                    next(Phase.ACCEL, 0.0)
                }
            }
        }
        val avgMs = (before + speedKmh) / 2.0 / 3.6
        return MotionSample(
            distanceM = avgMs * dtSec,
            speedKmh = speedKmh,
            hasFix = true,
            accuracyM = 3f,
            satellitesUsed = 12,
            satellitesVisible = 18,
        )
    }

    private fun next(p: Phase, sec: Double) {
        phase = p
        phaseLeftSec = sec
    }
}
