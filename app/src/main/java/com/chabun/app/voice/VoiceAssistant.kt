package com.chabun.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import com.chabun.app.R
import com.chabun.app.i18n.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** 음성 인식 진행 상태. 화면 하단 음성 바에 그대로 보여 준다. */
sealed interface ListenState {
    data object Idle : ListenState
    data class Listening(val partial: String = "") : ListenState
    data class Failed(val reason: String) : ListenState
}

/**
 * Pleos TextToSpeech/SpeechToText SDK로 음성 안내와 음성 명령을 처리한다.
 * 앱 등록 없이 동작하도록 ON_DEVICE 모드를 쓴다. 인식된 문장은 [onHeard]로 넘기고, 명령 해석은 [VoiceCommand]가 한다.
 */
class VoiceAssistant(context: Context, private val text: Text, private val onHeard: (String) -> Unit) {

    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    @Volatile private var ttsReady = false
    @Volatile private var sttReady = false
    /** 음성 안내 언어(화면 언어를 따른다). 음성 명령 인식은 한국어로 둔다. */
    @Volatile private var speechLocale: Locale = Locale.KOREA

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private val ttsEvents = object : EventListener {
        override fun onStart() {
            Log.d(TAG, "tts start")
        }

        override fun onReady() = Unit
        override fun onDone() {
            Log.d(TAG, "tts done")
        }

        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "tts error $errorType")
        }
    }

    private val sttResults = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            _state.value = ListenState.Listening()
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            Log.i(TAG, "heard '$stt' completed=$completed")
            if (!completed) {
                _state.value = ListenState.Listening(stt)
                return
            }
            _state.value = ListenState.Idle
            if (stt.isNotBlank()) onHeard(stt)
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            if (_state.value is ListenState.Listening) _state.value = ListenState.Idle
        }

        override fun onError(errorType: SttError) {
            Log.w(TAG, "stt error $errorType")
            _state.value = ListenState.Failed(errorType.message())
        }
    }

    fun initialize() {
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.addEventListener(ttsEvents)
                tts.setLocale(speechLocale)
                ttsReady = true
            }

            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "tts init failed $errorType")
            }
        })
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                stt.addListener(sttResults)
                stt.setLocale(Locale.KOREA)
                sttReady = true
            }

            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "stt init failed $errorType")
            }
        })
    }

    /** 음성 안내 언어를 바꾼다. */
    fun setSpeechLocale(locale: Locale) {
        speechLocale = locale
        if (ttsReady) runCatching { tts.setLocale(locale) }.onFailure { Log.w(TAG, "tts locale $locale failed", it) }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            Log.w(TAG, "tts not ready, skip: $text")
            return
        }
        runCatching { tts.speak(text, true) }.onFailure { Log.w(TAG, "speak failed", it) }
    }

    /** 차량 마이크로 듣기 시작한다. */
    fun listen() {
        if (!sttReady) {
            _state.value = ListenState.Failed(text(R.string.stt_preparing))
            return
        }
        _state.value = ListenState.Listening()
        runCatching { stt.request() }.onFailure {
            Log.w(TAG, "stt request failed", it)
            _state.value = ListenState.Failed(text(R.string.stt_start_failed))
        }
    }

    fun stopListening() {
        runCatching { stt.stop() }
        _state.value = ListenState.Idle
    }

    /** 녹음된 16kHz·16bit·mono PCM을 마이크 대신 넣어 인식한다(ON_DEVICE 전용, 시연·테스트용). */
    fun recognize(pcm: ByteArray) {
        if (!sttReady) {
            Log.w(TAG, "stt not ready")
            return
        }
        _state.value = ListenState.Listening()
        Thread({
            pcm.asList().chunked(BYTES_PER_100_MS).forEach { chunk ->
                stt.sendAudio(chunk.toByteArray())
                Thread.sleep(100)
            }
            stt.completeAudioSend()
        }, "chabun-stt-feed").start()
    }

    fun clearFailure() {
        if (_state.value is ListenState.Failed) _state.value = ListenState.Idle
    }

    fun release() {
        runCatching {
            tts.stop()
            tts.removeEventListener(ttsEvents)
            tts.release()
        }
        runCatching {
            stt.stop()
            stt.removeListener(sttResults)
            stt.release()
        }
    }

    private fun SttError.message() = when (this) {
        SttError.MIC_ERROR -> text(R.string.stt_mic)
        SttError.PERMISSION_DENIED -> text(R.string.stt_permission)
        SttError.UNAVAILABLE_LANGUAGE -> text(R.string.stt_language)
        SttError.TIMEOUT -> text(R.string.stt_timeout)
        else -> text(R.string.stt_error, name)
    }

    private companion object {
        const val TAG = "Chabun"
        /** 16kHz × 16bit × mono 100ms */
        const val BYTES_PER_100_MS = 3_200
    }
}
