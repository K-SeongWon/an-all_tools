package com.anall.tools.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.anall.core.sound.SoundChannel
import com.anall.core.sound.SoundClip
import com.anall.core.sound.SoundLevel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 모든 애드온 소리를 재생하는 호스트 오디오. 사용자가 정한 마스터·음성·효과음 볼륨을 곱한다.
 *
 * 짧은 효과음마다 정적 AudioTrack 을 하나 만들어 재생하고 끝나면 바로 해제한다 (상주 스레드·버퍼 없음).
 * 미디어 볼륨을 따르며 오디오 포커스를 잡지 않아 음악·내비 소리와 섞여서 난다.
 */
class HostSound(context: Context) {

    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadScheduledExecutor()

    /** 0~100 */
    var master: Int
        get() = prefs.getInt("master", DEFAULT_MASTER)
        set(v) = prefs.edit().putInt("master", v.coerceIn(0, 100)).apply()

    var voice: Int
        get() = prefs.getInt("voice", 100)
        set(v) = prefs.edit().putInt("voice", v.coerceIn(0, 100)).apply()

    var effect: Int
        get() = prefs.getInt("effect", 100)
        set(v) = prefs.edit().putInt("effect", v.coerceIn(0, 100)).apply()

    fun channelVolume(channel: SoundChannel): Int = when (channel) {
        SoundChannel.EFFECT -> effect
        SoundChannel.VOICE -> voice
    }

    /** 최종 배율 = 마스터 × 채널 × 표준 레벨. */
    fun gainFor(channel: SoundChannel, levelDb: Float): Float =
        perceptual(master) * perceptual(channelVolume(channel)) * SoundLevel.gain(levelDb)

    fun play(clip: SoundClip, channel: SoundChannel, levelDb: Float) {
        val gain = gainFor(channel, levelDb)
        if (gain < 0.0005f || clip.samples.isEmpty()) return
        worker.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(
                                if (channel == SoundChannel.VOICE) AudioAttributes.CONTENT_TYPE_SPEECH
                                else AudioAttributes.CONTENT_TYPE_SONIFICATION,
                            )
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(clip.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(clip.samples.size * 2)
                    .build()
                track.write(clip.samples, 0, clip.samples.size)
                track.setVolume(gain.coerceAtMost(1f))
                track.play()
                worker.schedule({ runCatching { track.release() } }, clip.durationMs + 300, TimeUnit.MILLISECONDS)
            } catch (t: Throwable) {
                Log.w(TAG, "소리 재생 실패", t)
            }
        }
    }

    companion object {
        private const val TAG = "HostSound"
        const val DEFAULT_MASTER = 80

        /** 슬라이더 % → 배율. 사람 귀에 고르게 들리도록 제곱 곡선을 쓴다 (50% ≈ -12dB). */
        fun perceptual(percent: Int): Float {
            val x = percent.coerceIn(0, 100) / 100f
            return x * x
        }
    }
}
