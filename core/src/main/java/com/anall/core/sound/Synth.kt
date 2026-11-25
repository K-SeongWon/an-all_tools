package com.anall.core.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 음원 파일 없이 코드로 효과음을 만드는 작은 합성기. APK 를 가볍게 두려고 wav/ogg 대신 쓴다.
 * 만든 결과는 피크 0 dBFS 로 정규화되므로 크기는 재생할 때 [SoundLevel] 로 정한다.
 */
object Synth {
    const val SAMPLE_RATE = 44_100

    /** 버퍼에 소리를 그린 뒤 정규화해서 SoundClip 으로 만든다. */
    inline fun render(durationMs: Int, block: (buf: FloatArray, sampleRate: Int) -> Unit): SoundClip {
        val buf = FloatArray(SAMPLE_RATE * durationMs / 1000)
        block(buf, SAMPLE_RATE)
        return normalize(buf)
    }

    fun normalize(buf: FloatArray): SoundClip {
        var peak = 0f
        for (v in buf) peak = maxOf(peak, abs(v))
        val scale = if (peak > 0f) 32_000f / peak else 0f
        return SoundClip(ShortArray(buf.size) { (buf[it] * scale).toInt().toShort() }, SAMPLE_RATE)
    }

    /**
     * 부드러운 압축(tanh). 피크 대비 평균 크기(크레스트 팩터)를 줄여서, 길고 거친 소리도
     * 같은 표준 레벨에서 다른 소리만큼 들리게 한다. 결과 범위는 -1..1.
     */
    fun compress(buf: FloatArray, from: Int = 0, to: Int = buf.size, drive: Float = 0.7f) {
        var sum = 0.0
        for (i in from until to) sum += buf[i] * buf[i]
        val rms = kotlin.math.sqrt(sum / (to - from).coerceAtLeast(1)).toFloat()
        if (rms <= 0f) return
        val k = drive / rms
        for (i in from until to) buf[i] = kotlin.math.tanh(buf[i] * k)
    }

    /** 피크와 평균(RMS)의 차이 (dB). 표준: 0.5초 넘는 소리는 9dB 이하. */
    fun crestFactorDb(clip: SoundClip): Float {
        var peak = 0
        var sum = 0.0
        for (v in clip.samples) {
            peak = maxOf(peak, abs(v.toInt()))
            sum += v.toDouble() * v
        }
        val rms = kotlin.math.sqrt(sum / clip.samples.size.coerceAtLeast(1))
        return (20 * kotlin.math.log10(peak / rms.coerceAtLeast(1e-9))).toFloat()
    }

    /** 클릭 잡음이 없도록 시작·끝을 부드럽게 (attack/release, ms). */
    fun fade(buf: FloatArray, sampleRate: Int, attackMs: Float, releaseMs: Float, from: Int = 0, to: Int = buf.size) {
        val a = (attackMs * sampleRate / 1000).toInt().coerceAtLeast(1)
        val r = (releaseMs * sampleRate / 1000).toInt().coerceAtLeast(1)
        val len = to - from
        for (i in 0 until len) {
            val g = min(1f, min(i.toFloat() / a, (len - 1 - i).toFloat() / r))
            buf[from + i] *= g.coerceAtLeast(0f)
        }
    }

    /**
     * 압전 부저 "삑". 기본값은 택시 미터기·계산기 버튼음 같은 2.7kHz.
     * 사각파에 가까운 소리가 나도록 홀수 배음을 섞는다.
     */
    fun beep(freqHz: Float = 2_700f, durationMs: Int = 110, startMs: Int = 0, buf: FloatArray, sampleRate: Int) {
        val from = startMs * sampleRate / 1000
        val to = min(buf.size, from + durationMs * sampleRate / 1000)
        for (i in from until to) {
            val t = (i - from).toFloat() / sampleRate
            val w = 2f * PI.toFloat() * freqHz * t
            buf[i] += sin(w) + 0.30f * sin(3 * w) + 0.12f * sin(5 * w)
        }
        fade(buf, sampleRate, 3f, 8f, from, to)
    }

    /**
     * 감열식 영수증 프린터 "지이잉". 스테퍼 모터 윙윙거림 + 한 줄씩 넘어가는 박자 + 종이 마찰음,
     * 마지막에 종이 자르는 "틱".
     */
    fun receiptPrinter(durationMs: Int, seed: Int = 7): SoundClip = render(durationMs) { buf, sr ->
        val rnd = Random(seed)
        val lineHz = 22f // 초당 줄 수
        val n = buf.size
        val cutAt = n - sr * 90 / 1000
        var noiseLp = 0f
        var phase1 = 0f
        val twoPi = 2f * PI.toFloat()
        for (i in 0 until cutAt) {
            val t = i.toFloat() / sr
            // 모터: 1.1kHz 기본음이 초당 5번 살짝 흔들리면서 윙윙 (위상을 누적해야 음높이가 일정하게 흔들린다)
            val freq = 1_100f * (1f + 0.015f * sin(twoPi * 5f * t))
            phase1 = (phase1 + twoPi * freq / sr) % twoPi
            val motor = sin(phase1) + 0.5f * sin(2f * phase1)
            // 줄 넘김: 한 줄마다 앞부분이 세게, 뒤로 갈수록 약하게 (드르륵 박자)
            val linePhase = (t * lineHz) % 1f
            val step = 0.55f + 0.45f * exp(-linePhase * 6f)
            // 종이 마찰: 저역 통과한 잡음
            noiseLp += 0.18f * ((rnd.nextFloat() * 2f - 1f) - noiseLp)
            buf[i] = 0.62f * motor * step + 0.9f * noiseLp
        }
        // 잡음이 뾰족해서 피크만 크고 실제로는 작게 들린다. 부드럽게 눌러서 체감 크기를 표준에 맞춘다.
        compress(buf, 0, cutAt)
        fade(buf, sr, 25f, 40f, 0, cutAt)
        // 종이 자르기 "틱"
        for (i in cutAt until n) {
            val k = i - cutAt
            val env = exp(-k / (sr * 0.006f))
            buf[i] = (rnd.nextFloat() * 2f - 1f) * env * 0.8f
        }
    }
}

/**
 * 모든 애드온이 같이 쓰는 표준 효과음. 같은 의미의 소리는 애드온이 달라도 같게 들리도록 여기 것을 쓴다.
 * 처음 쓸 때 한 번 만들고 재사용한다.
 */
object Sfx {
    /** 주요 버튼 "삑" (시작/종료/확인). 권장 레벨 [SoundLevel.CONFIRM]. */
    val BEEP: SoundClip by lazy { Synth.render(130) { b, sr -> Synth.beep(buf = b, sampleRate = sr) } }

    /** 두 번 "삑삑" (취소/완료 같은 강조). 권장 레벨 [SoundLevel.CONFIRM]. */
    val DOUBLE_BEEP: SoundClip by lazy {
        Synth.render(260) { b, sr ->
            Synth.beep(durationMs = 90, buf = b, sampleRate = sr)
            Synth.beep(durationMs = 90, startMs = 150, buf = b, sampleRate = sr)
        }
    }

    /** 가벼운 클릭 "틱". 권장 레벨 [SoundLevel.TICK]. */
    val TICK: SoundClip by lazy { Synth.render(30) { b, sr -> Synth.beep(freqHz = 4_000f, durationMs = 25, buf = b, sampleRate = sr) } }

    /** 알림 "띵-동" (상승 2음). 권장 레벨 [SoundLevel.NOTIFY]. */
    val NOTIFY: SoundClip by lazy {
        Synth.render(360) { b, sr ->
            Synth.beep(freqHz = 1_320f, durationMs = 150, buf = b, sampleRate = sr)
            Synth.beep(freqHz = 1_760f, durationMs = 190, startMs = 160, buf = b, sampleRate = sr)
        }
    }

    /** 영수증 출력 "지이잉" (기본 1.4초). 권장 레벨 [SoundLevel.MECHANICAL]. */
    fun printer(durationMs: Int = 1_400): SoundClip = Synth.receiptPrinter(durationMs)
}
