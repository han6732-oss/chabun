package com.chabun.app.voice

import com.chabun.app.scene.ChabunScene

/**
 * 음성 인식 결과를 차분 동작으로 해석한다. STT SDK는 텍스트만 돌려주므로 명령 판단은 앱이 한다.
 * 예: "차분 켜줘", "우주 유영 켜줘", "빗소리로 이십 분만 쉴래", "깨워줘", "차분 꺼줘"
 */
sealed interface VoiceCommand {
    /** scene이 null이면 가장 자주 쓰는 장면으로 시작한다. */
    data class Start(val scene: ChabunScene?, val minutes: Int?) : VoiceCommand
    data object Wake : VoiceCommand
    data object End : VoiceCommand
    data class Unknown(val text: String) : VoiceCommand

    companion object {
        private const val APP_NAME = "차분"

        /** 짧은 명령에서 자주 틀리게 인식되는 형태를 바로잡는다(실측: "차분 꺼줘" → "자본거주어"). */
        private val ALIASES = listOf(
            "자본" to "차분", "차본" to "차분", "자분" to "차분",
            "거주어" to "꺼줘", "꺼주어" to "꺼줘", "거줘" to "꺼줘", "꺼져" to "꺼줘",
            "켜주어" to "켜줘", "커줘" to "켜줘",
        )
        private val WAKE = listOf("깨워", "일어날", "일어나", "기상")
        private val END = listOf("꺼", "끝내", "끝낼", "그만", "종료", "멈춰")
        private val START = listOf("켜", "시작", "틀어", "쉬고싶", "쉴래", "쉬자", "쉴게", "자고싶", "잘래", "낮잠")

        /** 기본 컨셉은 이름 말고도 떠올리기 쉬운 말로 찾는다. 긴 말부터 확인해 '비행기'가 '비'로 잡히지 않게 한다. */
        private val CONCEPT_KEYWORDS = mapOf(
            "cabin" to listOf("비행기", "기내"),
            "space" to listOf("우주"),
            "rain_cabin" to listOf("빗소리", "빗속", "오두막"),
            "dawn_forest" to listOf("새벽숲", "숲"),
            "charging" to listOf("충전"),
            "brand_orvel_first" to listOf("오르벨", "퍼스트", "항공"),
            "brand_mapline_suite" to listOf("메이플린", "호텔", "스위트"),
        )

        private val KOREAN_MINUTES = listOf(
            "한시간" to 60, "반시간" to 30,
            "오십분" to 50, "사십분" to 40, "삼십분" to 30, "이십분" to 20, "십오분" to 15, "십분" to 10, "오분" to 5,
        )
        private val DIGIT_MINUTES = Regex("(\\d{1,3})분")
        private val DIGIT_HOURS = Regex("(\\d)시간")

        fun parse(text: String, scenes: List<ChabunScene>): VoiceCommand {
            val t = ALIASES.fold(text.replace(Regex("[\\s.,!?~]"), "")) { acc, (heard, meant) -> acc.replace(heard, meant) }
            if (t.isEmpty()) return Unknown(text)
            if (WAKE.any { it in t }) return Wake
            val scene = findScene(t, scenes)
            val minutes = findMinutes(t)
            if (END.any { it in t } && START.none { it in t }) return End
            // 앱 이름만 불러도("차분", "차분해 줘") 시작으로 본다. 인식이 어미를 틀려도 의도는 살린다.
            if (scene != null || minutes != null || START.any { it in t } || APP_NAME in t) return Start(scene, minutes)
            return Unknown(text)
        }

        private fun findScene(t: String, scenes: List<ChabunScene>): ChabunScene? =
            scenes.firstOrNull { it.name.replace(" ", "") in t }
                ?: scenes.firstOrNull { scene -> CONCEPT_KEYWORDS[scene.id].orEmpty().any { it in t } }

        private fun findMinutes(t: String): Int? =
            DIGIT_HOURS.find(t)?.groupValues?.get(1)?.toInt()?.times(60)
                ?: DIGIT_MINUTES.find(t)?.groupValues?.get(1)?.toInt()
                ?: KOREAN_MINUTES.firstOrNull { (word, _) -> word in t }?.second
    }
}
