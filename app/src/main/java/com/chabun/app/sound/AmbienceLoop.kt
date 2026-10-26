package com.chabun.app.sound

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.RawRes
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * 반복 음원 하나를 MediaCodec으로 디코딩해 끝없이 이어 준다(재생 스레드 한 곳에서만 쓴다).
 *
 * 파일은 [한 바퀴의 끝 0.5초] + [한 바퀴(정수 초)] + [한 바퀴의 처음 0.5초]로 만들어 둔다(tools/make_ambience.sh).
 *  - 손실 코덱은 스트림 처음과 끝에서 오차가 커서, 앞뒤 여유분은 버리고 가운데 한 바퀴만 재생한 뒤 처음부터 다시 디코딩한다.
 *  - 파일 내용이 한 바퀴 주기로 되풀이되므로 디코더가 앞에 몇 프레임을 더 내도(MP3 인코더 지연 등) 한 바퀴 길이만큼만 재생하면 맞물린다.
 *  - 같은 소리라도 위치마다 압축 오차가 달라 그대로 이으면 저음이 많은 음원에서 '틱' 소리가 난다.
 *    그래서 한 바퀴의 마지막 50ms를, 앞 여유분 끝에서 받아 둔 같은 내용(재생 시작 직전 50ms)과 크로스페이드하고 넘어간다.
 */
internal class AmbienceLoop(context: Context, @RawRes res: Int) {

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private val rollFrames: Long
    /** 한 바퀴 길이(프레임) */
    val loopFrames: Long
    private val seamFrames: Int
    /** 재생 시작 직전 seamFrames 프레임(스테레오 인터리브). 한 바퀴 끝을 이것과 겹쳐 처음으로 넘어간다. */
    private val lead: FloatArray
    private var channels = 2
    private var pcm: ShortBuffer? = null
    private var outIndex = -1
    private var inputDone = false
    /** 이번 바퀴에서 디코더가 내놓은 프레임 위치(앞 여유분 포함) */
    private var frame = 0L

    init {
        context.resources.openRawResourceFd(res).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        val format = extractor.getTrackFormat(0)
        extractor.selectTrack(0)
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        check(rate == SAMPLE_RATE) { "ambience must be $SAMPLE_RATE Hz (was $rate)" }
        rollFrames = rate.toLong() * ROLL_MS / 1000
        // 한 바퀴는 정수 초로 만들므로 길이 메타데이터의 반올림 오차를 버린다.
        loopFrames = (format.getLong(MediaFormat.KEY_DURATION) / 1e6 - 2 * ROLL_MS / 1000.0).roundToLong() * rate
        seamFrames = rate * SEAM_MS / 1000
        check(loopFrames > seamFrames) { "ambience too short" }
        lead = FloatArray(seamFrames * 2)
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
    }

    /** out(스테레오 인터리브 L, R, …)을 이어지는 소리로 채운다. */
    fun read(out: FloatArray) {
        var i = 0
        while (i < out.size) {
            val buf = pcm
            if (buf == null || !buf.hasRemaining()) {
                nextBuffer()
                continue
            }
            val left = buf.get() / 32768f
            val right = if (channels > 1) buf.get() / 32768f else left
            repeat(channels - 2) { buf.get() }
            val pos = frame++ - rollFrames
            when {
                // 앞 여유분: 버리되 마지막 50ms는 이음매용으로 받아 둔다.
                pos < -seamFrames -> Unit
                pos < 0 -> {
                    val k = (pos + seamFrames).toInt() * 2
                    lead[k] = left
                    lead[k + 1] = right
                }
                pos < loopFrames - seamFrames -> {
                    out[i++] = left
                    out[i++] = right
                }
                pos < loopFrames -> {
                    val j = (pos - (loopFrames - seamFrames)).toInt()
                    val g = (0.5 - 0.5 * cos(PI * (j + 0.5) / seamFrames)).toFloat()
                    out[i++] = left * (1 - g) + lead[j * 2] * g
                    out[i++] = right * (1 - g) + lead[j * 2 + 1] * g
                }
                else -> rewind()
            }
        }
    }

    fun release() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }

    private fun nextBuffer() {
        releaseOutput()
        while (true) {
            // 빈 입력 버퍼를 모두 채워 두어야 디코더가 쉬지 않는다(하나씩 넣으면 출력마다 기다려 실시간보다 느려진다).
            while (!inputDone && feed()) Unit
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                index >= 0 && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 -> {
                    // 뒤 여유분 덕분에 한 바퀴가 끝나기 전에 파일 끝에 닿을 일은 없지만, 닿으면 처음부터 다시.
                    codec.releaseOutputBuffer(index, false)
                    rewind()
                    return
                }
                index >= 0 -> {
                    val bytes = codec.getOutputBuffer(index)!!
                    bytes.position(info.offset)
                    bytes.limit(info.offset + info.size)
                    pcm = bytes.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                    outIndex = index
                    return
                }
            }
        }
    }

    /** 입력 버퍼가 비어 있으면 다음 패킷을 넣고 true */
    private fun feed(): Boolean {
        val index = codec.dequeueInputBuffer(0)
        if (index < 0) return false
        val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
        return true
    }

    private fun rewind() {
        releaseOutput()
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        inputDone = false
        frame = 0
    }

    private fun releaseOutput() {
        if (outIndex >= 0) codec.releaseOutputBuffer(outIndex, false)
        outIndex = -1
        pcm = null
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        /** tools/make_ambience.sh의 ROLL과 같아야 한다. 코덱 경계 오차(블록 하나, 약 46ms)와 이음매용 50ms를 담을 만큼. */
        const val ROLL_MS = 500
        const val SEAM_MS = 50
        private const val TIMEOUT_US = 10_000L
    }
}
