package com.chabun.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.chabun.app.R
import com.chabun.app.scene.SoundTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 공회전 엔진음 프로필. 4행정 엔진의 점화 주파수 = rpm / 60 × 기통수 / 2.
 * 실제 RPM(ENGINE_RPM)은 시스템 앱 전용 권한이라 연료 종류별 대표 공회전 값을 쓴다.
 */
enum class IdleProfile(@StringRes val label: Int, val rpm: Int, val cylinders: Int, val clatter: Boolean) {
    GASOLINE(R.string.powertrain_gasoline, 700, 4, clatter = false),
    DIESEL(R.string.powertrain_diesel, 780, 4, clatter = true);

    val firingHz: Double get() = rpm / 60.0 * cylinders / 2
}

/** 테마별 반복 음원(music/의 원본에서 tools/make_ambience.sh로 만든다) */
@get:RawRes
internal val SoundTheme.track: Int
    get() = when (this) {
        SoundTheme.CABIN -> R.raw.ambience_cabin
        SoundTheme.RAIN -> R.raw.ambience_rain
        SoundTheme.SPACE -> R.raw.ambience_space
        SoundTheme.FOREST -> R.raw.ambience_forest
    }

/**
 * 장면의 앰비언트 음원을 이음매 없이 반복 재생한다.
 * 엔진차에서는 공회전 점화 주파수와 배음 대역을 덮는 마스킹 노이즈를 실시간 합성해 겹친다.
 */
class NoisePlayer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> stop()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> targetVolume = DUCK_VOLUME
                AudioManager.AUDIOFOCUS_GAIN -> targetVolume = baseVolume
            }
        }
        .build()

    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    /** 트랙 음량(0~1). 페이드·덕킹은 샘플이 아니라 트랙 음량으로 건다. */
    @Volatile private var volume = 0f
    @Volatile private var targetVolume = 0f
    /** 낮췄다가 되돌아갈 기준 음량(기상 시퀀스에서 0으로 내린다). */
    @Volatile private var baseVolume = FULL_VOLUME

    /** 테마 없이 마스킹만 재생할 수도 있다. 둘 다 없으면 아무것도 하지 않는다. */
    fun play(theme: SoundTheme?, masking: IdleProfile? = null) {
        stop()
        if (theme == null && masking == null) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        volume = 0f
        baseVolume = FULL_VOLUME
        targetVolume = baseVolume
        running = true
        thread = Thread({ render(theme, masking) }, "chabun-noise").apply { start() }
    }

    /** 소리를 서서히 줄인다(기상 시퀀스). */
    fun fadeOut() {
        baseVolume = 0f
        targetVolume = 0f
    }

    /** 음성 명령을 듣는 동안 잠시 낮춘다. */
    fun duck(on: Boolean) {
        if (!running) return
        targetVolume = if (on) minOf(DUCK_VOLUME, baseVolume) else baseVolume
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun render(theme: SoundTheme?, masking: IdleProfile?) {
        // UI·다른 앱보다 먼저 CPU를 받아야 바쁜 기기에서도 버퍼가 마르지 않는다.
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var ambience = theme?.let {
            runCatching { AmbienceLoop(context, it.track) }.onFailure { e -> Log.w(TAG, "ambience $it unavailable", e) }.getOrNull()
        }
        val maskingNoise = masking?.let(::MaskingNoise)
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            // 디코딩이 잠깐 밀려도(바쁜 기기, 반복 지점에서 처음부터 다시 디코딩) 끊기지 않게 3초쯤 쌓아 둔다.
            .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE * BUFFER_SECONDS * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        // 스테레오 인터리브(L, R, L, R…)
        val buffer = FloatArray(CHUNK * 2)

        fun fill() {
            try {
                val loop = ambience
                if (loop != null) loop.read(buffer) else buffer.fill(0f)
            } catch (e: Exception) {
                // 디코더가 망가지면 음원 없이(마스킹만) 이어 간다.
                Log.w(TAG, "ambience decode failed", e)
                ambience?.release()
                ambience = null
                buffer.fill(0f)
            }
            for (i in 0 until CHUNK) {
                val noise = (maskingNoise?.next() ?: 0f) * MASKING_GAIN
                buffer[2 * i] = softLimit(buffer[2 * i] + noise)
                buffer[2 * i + 1] = softLimit(buffer[2 * i + 1] + noise)
            }
        }

        track.setVolume(0f)
        try {
            // 재생 전에 버퍼를 먼저 채운다. 빈 채로 시작하면 처음 몇 초가 underrun으로 지지직 끊긴다.
            repeat(SAMPLE_RATE * PRIME_SECONDS / CHUNK) {
                if (!running) return
                fill()
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
            track.play()
            var last = System.nanoTime()
            while (running) {
                fill()
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                // 버퍼에 쌓인 소리와 상관없이 지금 들리는 소리에 바로 걸리도록, 실제 시간 기준으로
                // 시정수 1.5초 페이드를 계산해 트랙 음량에 준다(버퍼가 차면 write가 한 덩어리 시간만큼 기다린다).
                val now = System.nanoTime()
                volume = targetVolume + (volume - targetVolume) * exp(-(now - last) / 1e9 / FADE_SECONDS).toFloat()
                last = now
                track.setVolume(volume)
            }
        } finally {
            // stop()은 쌓아 둔 소리를 끝까지 재생하므로 바로 멈춘다.
            track.pause()
            track.flush()
            track.release()
            ambience?.release()
        }
    }

    /** 음원과 마스킹이 겹쳐 드물게 1에 가까워지는 봉우리만 부드럽게 눌러, 딱 잘리는 클리핑(지직 소리)을 막는다. */
    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        return sign(x) * (KNEE + (1 - KNEE) * tanh((a - KNEE) / (1 - KNEE)))
    }

    /**
     * 엔진음 마스킹 노이즈 합성기. 샘플 하나씩 만들어 낸다.
     * 공회전음과 같은 임계대역을 노이즈로 채워 엔진의 저역 배음이 들리지 않게 하고,
     * 점화 주기보다 훨씬 느린 진폭 변조를 더해 엔진 떨림이 순항 중 기류처럼 느껴지게 한다.
     */
    private class MaskingNoise(private val profile: IdleProfile) {
        private val random = Random(System.nanoTime())
        private var brown = 0f
        private var t = 0L
        // Paul Kellet 핑크 노이즈 필터 상태
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        // 점화 주파수의 1~4차 배음 대역. 대역폭이 좁을수록 출력이 작아 차수별로 이득을 보정한다.
        private val engineBands = (1..4).map { h -> BandPass(profile.firingHz * h, q = 3.0) to 11f / sqrt(h.toFloat()) }

        fun next(): Float {
            t++
            val white = random.nextFloat() * 2f - 1f
            brown = (brown + white * 0.02f) * 0.998f
            b0 = 0.99765f * b0 + white * 0.0990460f
            b1 = 0.96300f * b1 + white * 0.2965164f
            b2 = 0.57000f * b2 + white * 1.0526913f
            val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.11f
            val bands = engineBands.sumOf { (filter, gain) -> filter.process(white.toDouble()) * gain }.toFloat()
            val airflow = 0.85f + 0.15f * sin(2 * PI * profile.firingHz / 40 * t / SAMPLE_RATE).toFloat()
            val clatter = if (profile.clatter) pink * 0.25f else 0f
            return (bands * 0.5f + brown * 1.2f) * airflow + clatter
        }
    }

    /** RBJ 2차 대역통과 필터. 저주파에서 극점이 단위원에 가까워 상태는 Double로 유지한다. */
    private class BandPass(freq: Double, q: Double) {
        private val b0: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        init {
            val w0 = 2 * PI * freq / SAMPLE_RATE
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            b0 = alpha / a0
            b2 = -alpha / a0
            a1 = -2 * cos(w0) / a0
            a2 = (1 - alpha) / a0
        }

        fun process(x: Double): Double {
            val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private companion object {
        const val TAG = "Chabun"
        const val SAMPLE_RATE = 44_100
        const val CHUNK = 2_048
        /** 음원은 -20 LUFS로 맞춰 두어 그대로(1.0) 재생한다. */
        const val FULL_VOLUME = 1f
        /** 마스킹 노이즈는 음원 위에 이만큼 섞는다. */
        const val MASKING_GAIN = 0.75f
        const val DUCK_VOLUME = 0.25f
        const val KNEE = 0.8f
        const val PRIME_SECONDS = 1
        const val FADE_SECONDS = 1.5
        const val BUFFER_SECONDS = 3
    }
}
