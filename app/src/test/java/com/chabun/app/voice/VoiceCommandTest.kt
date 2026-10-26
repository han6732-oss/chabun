package com.chabun.app.voice

import com.chabun.app.scene.ChabunScene
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandTest {

    private val scenes = ChabunScene.concepts + ChabunScene.blank().copy(id = "custom-1", name = "퇴근길 쉼표")

    private fun parse(text: String) = VoiceCommand.parse(text, scenes)

    private fun sceneId(text: String) = (parse(text) as VoiceCommand.Start).scene?.id

    @Test
    fun startsMostUsedSceneWhenNoneNamed() {
        assertEquals(VoiceCommand.Start(null, null), parse("차분 켜줘"))
        assertEquals(VoiceCommand.Start(null, null), parse("좀 쉬고 싶어"))
        // 실제 인식 결과: 합성 음성 "차분 켜줘"가 "차분해야죠"로 인식된 경우
        assertEquals(VoiceCommand.Start(null, null), parse("차분해야죠"))
    }

    @Test
    fun findsConceptByNameOrKeyword() {
        assertEquals("space", sceneId("우주 유영 켜줘"))
        assertEquals("cabin", sceneId("비행기 모드로 시작해 줘"))
        assertEquals("rain_cabin", sceneId("빗소리 틀어줘"))
        assertEquals("dawn_forest", sceneId("숲으로 가자 시작"))
        assertEquals("custom-1", sceneId("퇴근길 쉼표 켜줘"))
        assertEquals("charging", sceneId("충전하는 동안 쉴래"))
    }

    @Test
    fun findsBrandHallScenesWhenOffered() {
        val withBrands = scenes + ChabunScene.brandScenes
        fun brand(text: String) = (VoiceCommand.parse(text, withBrands) as VoiceCommand.Start).scene?.id
        assertEquals("brand_mapline_suite", brand("호텔 메이플린 켜줘"))
        assertEquals("brand_mapline_suite", brand("호텔에서 쉴래"))
        assertEquals("brand_orvel_first", brand("오르벨 퍼스트 클래스 시작"))
        // 기본 컨셉 키워드가 먼저다: '비행기'는 기본 비행기 기내
        assertEquals("cabin", brand("비행기 모드로 시작해 줘"))
    }

    @Test
    fun readsDurationInDigitsAndKorean() {
        assertEquals(20, (parse("빗소리로 이십 분만 쉴래") as VoiceCommand.Start).minutes)
        assertEquals(30, (parse("우주 30분 켜줘") as VoiceCommand.Start).minutes)
        assertEquals(60, (parse("한 시간만 잘래") as VoiceCommand.Start).minutes)
        assertEquals(10, (parse("십 분만 쉴게") as VoiceCommand.Start).minutes)
    }

    @Test
    fun wakeAndEnd() {
        assertEquals(VoiceCommand.Wake, parse("이제 깨워줘"))
        assertEquals(VoiceCommand.Wake, parse("일어날래"))
        assertEquals(VoiceCommand.End, parse("차분 꺼줘"))
        assertEquals(VoiceCommand.End, parse("그만할래"))
        // 실제 인식 결과: 합성 음성 "차분 꺼줘"가 "자본거주어"로 인식된 경우
        assertEquals(VoiceCommand.End, parse("자본거주어"))
    }

    @Test
    fun recognitionSlipsStillStartRightScene() {
        // 실제 인식 결과: "빗소리로 이십 분만 쉴래" → "빗소리로 20분만 줄일래"
        val start = parse("빗소리로 20분만 줄일래") as VoiceCommand.Start
        assertEquals("rain_cabin", start.scene?.id)
        assertEquals(20, start.minutes)
    }

    @Test
    fun unknownText() {
        assertEquals(VoiceCommand.Unknown("오늘 날씨 어때"), parse("오늘 날씨 어때"))
    }
}
