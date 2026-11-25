package com.anall.core.sound

import kotlin.math.pow

/*
 * 안올툴 소리 표준 (HostApi 2+). 자세한 규칙은 docs/sound.md.
 *
 * 최종 음량 = 마스터 볼륨 × 채널 볼륨(음성/효과음) × 표준 레벨(dBFS)
 *
 * - 모든 SoundClip 은 피크 0 dBFS 로 정규화되어 있고, 실제 크기는 재생할 때 SoundLevel 로 정한다.
 *   그래서 애드온마다 소리 크기가 들쭉날쭉하지 않다.
 * - 볼륨은 사용자가 안올툴 설정(마스터/음성/효과음)에서 조절한다. 애드온이 따로 볼륨 설정을 만들지 않는다.
 */

/** 소리 종류. 사용자는 채널별로 볼륨을 조절한다. */
enum class SoundChannel {
    /** 버튼음, 기계음, 알림음 등 모든 효과음. */
    EFFECT,

    /** 안내 멘트, TTS 등 사람 목소리. */
    VOICE,
}

/**
 * 표준 효과음 레벨 (dBFS, 0 이 최대). 새 소리를 만들 때 가장 가까운 등급을 고른다.
 * 여러 소리가 겹쳐도 깨지지 않도록 가장 큰 등급도 -6 dBFS 에서 멈춘다.
 */
object SoundLevel {
    /** 가벼운 터치·토글 클릭. 자주 나는 소리. */
    const val TICK = -24f

    /** 시작/종료/확인 같은 주요 버튼 "삑". */
    const val CONFIRM = -16f

    /** 프린터·모터 같은 기계 동작음. 길게 이어지는 소리. */
    const val MECHANICAL = -20f

    /** 상태 변화 알림 (할증 시작, 목표 달성 등). */
    const val NOTIFY = -12f

    /** 안전·경고. 꼭 들려야 하는 소리에만. */
    const val ALERT = -8f

    /** 음성 안내 (VOICE 채널). */
    const val VOICE = -6f

    /** dBFS → 배율 (0 dBFS = 1.0). */
    fun gain(db: Float): Float = 10f.pow(db / 20f)
}

/** 16비트 모노 PCM 소리 조각. [Synth] 로 만들면 피크 0 dBFS 로 정규화된다. */
class SoundClip(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}
