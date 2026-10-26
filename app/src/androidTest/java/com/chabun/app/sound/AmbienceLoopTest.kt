package com.chabun.app.sound

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chabun.app.scene.SoundTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 기기 디코더로 반복 음원을 실제로 읽어 본다(음원마다 한 바퀴 넘게 디코딩해서 몇 분 걸린다).
 * adb shell am instrument -w --user 10 -e class com.chabun.app.sound.AmbienceLoopTest com.chabun.app.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class AmbienceLoopTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = AmbienceLoop.SAMPLE_RATE

    /** 재생 스레드는 3초를 쌓아 두므로 평균적으로 실시간보다 빠르기만 하면 된다(과부하 에뮬레이터에서 약 2배). */
    @Test
    fun decodesFasterThanRealtime() = SoundTheme.entries.forEach { theme ->
        val loop = AmbienceLoop(context, theme.track)
        try {
            val chunk = FloatArray(2_048 * 2)
            val seconds = 20
            val started = System.nanoTime()
            repeat(seconds * rate / 2_048) { loop.read(chunk) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            val speed = seconds * 1_000f / elapsedMs
            println("$theme: ${seconds}s decoded in ${elapsedMs}ms (${"%.1f".format(speed)}x realtime)")
            assertTrue("$theme decodes too slowly (${speed}x)", speed > 1.2f)
        } finally {
            loop.release()
        }
    }

    /** 이음매에 '틱'(튀는 샘플)이 없고, 둘째 바퀴가 첫 바퀴와 똑같이 디코딩되는지 본다. */
    @Test
    fun loopsSeamlessly() = SoundTheme.entries.forEach { theme ->
        val loop = AmbienceLoop(context, theme.track)
        try {
            val first = FloatArray(rate * 2)        // 첫 바퀴 처음 1초
            val second = FloatArray(rate * 2)       // 둘째 바퀴 처음 1초
            val around = FloatArray(rate * 2 * 2)   // 이음매 앞뒤 1초
            val chunk = FloatArray(4_096)
            var frame = 0L
            while (frame < loop.loopFrames + rate) {
                loop.read(chunk)
                capture(chunk, frame, first, 0)
                capture(chunk, frame, second, loop.loopFrames)
                capture(chunk, frame, around, loop.loopFrames - rate)
                frame += chunk.size / 2
            }

            // 다시 디코딩해도 같은 소리여야 앞 여유분에서 받아 둔 50ms와 맞물린다.
            val drift = first.indices.maxOf { abs(first[it] - second[it]) }
            assertEquals("$theme: second pass differs", 0f, drift, 1e-4f)

            // 크로스페이드 구간과 이음매의 최대 변화가 주변(끊김 없는 구간)의 최대 변화를 넘지 않아야 한다.
            val seamFrames = rate * AmbienceLoop.SEAM_MS / 1000
            fun step(i: Int) = max(abs(around[2 * i] - around[2 * i - 2]), abs(around[2 * i + 1] - around[2 * i - 1]))
            val seamRange = (rate - seamFrames - 2)..(rate + 2)
            val seamWorst = seamRange.maxOf(::step)
            val elsewhere = (1 until 2 * rate).filter { it !in seamRange }.maxOf(::step)
            println("$theme: seam worst $seamWorst, elsewhere $elsewhere, drift $drift")
            assertTrue("$theme: click at seam ($seamWorst > $elsewhere)", seamWorst <= elsewhere * 1.2f)
        } finally {
            loop.release()
        }
    }

    /** 스트림 위치 chunkFrame에서 읽은 chunk 중 dest가 맡은 구간[destFrame, destFrame + dest 길이)을 옮긴다. */
    private fun capture(chunk: FloatArray, chunkFrame: Long, dest: FloatArray, destFrame: Long) {
        val from = max(chunkFrame, destFrame)
        val to = min(chunkFrame + chunk.size / 2, destFrame + dest.size / 2)
        if (from >= to) return
        chunk.copyInto(dest, ((from - destFrame) * 2).toInt(), ((from - chunkFrame) * 2).toInt(), ((to - chunkFrame) * 2).toInt())
    }
}
