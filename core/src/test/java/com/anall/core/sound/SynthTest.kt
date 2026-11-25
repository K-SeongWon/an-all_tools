package com.anall.core.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class SynthTest {

    private fun peak(c: SoundClip) = c.samples.maxOf { abs(it.toInt()) }

    @Test
    fun `표준 효과음은 피크 0dBFS 근처로 정규화된다`() {
        for (clip in listOf(Sfx.BEEP, Sfx.DOUBLE_BEEP, Sfx.TICK, Sfx.NOTIFY, Sfx.printer(1_400))) {
            val p = peak(clip)
            assertTrue("peak=$p", p in 31_000..32_767)
        }
    }

    @Test
    fun `긴 소리는 크레스트 팩터 9dB 이하 (표준 레벨에서 작게 들리지 않게)`() {
        val c = Synth.crestFactorDb(Sfx.printer(1_400))
        assertTrue("crest=$c", c <= 9f)
    }

    @Test
    fun `출력음 길이는 요청한 길이와 같다`() {
        assertEquals(1_400L, Sfx.printer(1_400).durationMs)
        assertEquals(130L, Sfx.BEEP.durationMs)
    }

    @Test
    fun `시작과 끝은 조용하다 (클릭 잡음 없음)`() {
        val b = Sfx.BEEP.samples
        assertTrue(abs(b.first().toInt()) < 2_000)
        assertTrue(abs(b.last().toInt()) < 2_000)
    }

    @Test
    fun `표준 레벨 배율`() {
        assertEquals(1f, SoundLevel.gain(0f), 1e-4f)
        assertEquals(0.5f, SoundLevel.gain(-6.0206f), 1e-3f)
        assertTrue(SoundLevel.TICK < SoundLevel.CONFIRM && SoundLevel.CONFIRM < SoundLevel.NOTIFY && SoundLevel.NOTIFY < SoundLevel.ALERT)
        assertTrue("가장 큰 등급도 -6dBFS 이하", SoundLevel.VOICE <= -6f)
    }

    /** 귀로 확인할 수 있게 build/sound-preview 폴더에 wav 로 저장한다 (표준 레벨 적용). */
    @Test
    fun `미리듣기 wav 만들기`() {
        val dir = File("build/sound-preview").apply { mkdirs() }
        writeWav(File(dir, "beep.wav"), Sfx.BEEP, SoundLevel.CONFIRM)
        writeWav(File(dir, "receipt-printer.wav"), Sfx.printer(1_400), SoundLevel.MECHANICAL)
        // 실제 운행 종료 순서: 삑 → 0.25초 뒤 지이잉
        val beep = Sfx.BEEP.samples
        val printer = Sfx.printer(1_400).samples
        val gap = Synth.SAMPLE_RATE / 4
        val seq = ShortArray(gap + printer.size + Synth.SAMPLE_RATE / 5)
        val g1 = SoundLevel.gain(SoundLevel.CONFIRM)
        val g2 = SoundLevel.gain(SoundLevel.MECHANICAL)
        val mix = FloatArray(seq.size)
        beep.forEachIndexed { i, v -> mix[i] += v * g1 }
        printer.forEachIndexed { i, v -> mix[gap + i] += v * g2 }
        writeWav(File(dir, "trip-end-sequence.wav"), SoundClip(ShortArray(mix.size) { mix[it].toInt().toShort() }, Synth.SAMPLE_RATE), 0f)
        assertTrue(File(dir, "trip-end-sequence.wav").length() > 44)
    }

    private fun writeWav(f: File, clip: SoundClip, levelDb: Float) {
        val g = SoundLevel.gain(levelDb)
        val data = clip.samples
        DataOutputStream(FileOutputStream(f)).use { out ->
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); le32(36 + data.size * 2); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(clip.sampleRate); le32(clip.sampleRate * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(data.size * 2)
            for (s in data) le16((s * g).toInt())
        }
    }
}
