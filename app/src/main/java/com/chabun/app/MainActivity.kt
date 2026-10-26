package com.chabun.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.chabun.app.ui.ChabunApp
import com.chabun.app.ui.SimulatedWindow

class MainActivity : ComponentActivity() {

    private val viewModel: ChabunViewModel by viewModels()

    /** 디버그 점검용으로 흉내 낼 앱 창 크기(null이면 실제 창) */
    private var debugWindow by mutableStateOf<DpSize?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 테마(기본 부드러운 다크)는 설정을 따라 ChabunApp 안에서 고른다.
            val window = debugWindow
            if (window == null) ChabunApp(viewModel) else SimulatedWindow(window) { ChabunApp(viewModel) }
        }
        handleDebugVoice(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugVoice(intent)
    }

    /**
     * 디버그 빌드 전용 시연·테스트 훅. 에뮬레이터에서 마이크에 말하지 않고도 음성 명령 흐름을 확인한다.
     *  - voice_sample: src/debug/assets/voice/<이름>.pcm 녹음을 STT에 넣어 실제 인식부터 시험
     *  - voice_text: 인식 결과 문장을 바로 넣어 명령 해석·실행만 시험
     * 예) adb shell am start -n com.chabun.app/.MainActivity --es voice_sample chabun_on
     */
    private fun handleDebugVoice(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        Log.d("Chabun", "debug voice extras: ${intent.extras?.keySet()}")
        intent.getStringExtra(EXTRA_VOICE_SAMPLE)?.let { viewModel.recognizeSample("voice/$it.pcm") }
        intent.getStringExtra(EXTRA_VOICE_TEXT)?.let(viewModel::onVoiceText)
        if (intent.getBooleanExtra(EXTRA_DEBUG_ROUTE, false)) viewModel.startSampleRoute()
        intent.getStringExtra(EXTRA_DEBUG_WINDOW)?.let { spec ->
            debugWindow = spec.split("x").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { (w, h) -> DpSize(w.dp, h.dp) }
        }
        intent.removeExtra(EXTRA_VOICE_SAMPLE)
        intent.removeExtra(EXTRA_VOICE_TEXT)
        intent.removeExtra(EXTRA_DEBUG_ROUTE)
        intent.removeExtra(EXTRA_DEBUG_WINDOW)
    }

    private companion object {
        const val EXTRA_VOICE_SAMPLE = "voice_sample"
        const val EXTRA_VOICE_TEXT = "voice_text"
        /** 시연용 경로 안내 시작(강남 → 42dot). 에뮬레이터 위치를 먼저 한국으로: adb emu geo fix 127.0276 37.4979 */
        const val EXTRA_DEBUG_ROUTE = "debug_route"
        /** 앱 창 크기 흉내(dp): --es debug_window 1080x1600 (세로), 1280x720 (작은 가로), reset = 실제 창 */
        const val EXTRA_DEBUG_WINDOW = "debug_window"
    }
}
