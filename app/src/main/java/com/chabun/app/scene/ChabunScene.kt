package com.chabun.app.scene

import androidx.annotation.StringRes
import com.chabun.app.R
import com.chabun.app.i18n.Text
import java.util.UUID

/** 장면의 앰비언트 사운드. 녹음한 음원을 이음매 없이 반복 재생한다(sound/NoisePlayer). 저장은 enum 이름으로 한다. */
enum class SoundTheme(@StringRes val label: Int) {
    CABIN(R.string.sound_cabin),
    RAIN(R.string.sound_rain),
    SPACE(R.string.sound_space),
    FOREST(R.string.sound_forest),
}

/** 앰비언트 조명 팔레트. 컨셉 분위기에 맞춘 색만 고르게 한다. */
data class AmbientColor(val rgb: Int, @StringRes val label: Int)

/**
 * 브랜드관의 제휴 브랜드. 항공사·호텔이 자기 서비스 경험을 차 안에 옮겨 오는 모습을 보여 주는 시연용 가상 브랜드다
 * (실제 회사가 아니며 화면에도 '가상 브랜드'로 표시한다).
 */
data class Brand(
    val name: String,
    /** 카드·휴식 화면 위에 놓는 영문 로고 글자 */
    val wordmark: String,
    /** 브랜드 대표 색(카드 배경) */
    val color: Int,
    /** 시작할 때 브랜드 목소리로 하는 인사 */
    val welcome: String,
    /** 깨울 때 하는 말 */
    val wakeUp: String,
)

/** 장면 카드와 휴식 화면에 까는 배경 이미지 */
enum class Backdrop(@StringRes val label: Int) {
    CABIN(R.string.backdrop_cabin),
    SPACE(R.string.backdrop_space),
    RAIN_CABIN(R.string.backdrop_rain_cabin),
    FOREST(R.string.backdrop_forest),
    FIRST_SUITE(R.string.backdrop_first_suite),
    HOTEL_SUITE(R.string.backdrop_hotel_suite),
}

/**
 * 차분 장면 하나. 활동(낮잠·영화)이 아니라 공간의 컨셉(기내·우주·오두막…)을 차 안에 연출한다.
 * 차종마다 범위가 달라서 시트·풍량·조명은 0~1 비율로 두고 실제 값은 capability 범위로 환산한다.
 * null인 항목은 건드리지 않는다.
 */
data class ChabunScene(
    val id: String,
    val name: String,
    val tagline: String,
    /** 기본 제공 컨셉이면 삭제 대신 기본값으로 되돌릴 수 있다. */
    val builtIn: Boolean = false,
    /** null이면 조명 색 그라데이션만 쓴다. */
    val backdrop: Backdrop? = null,
    /** 0 = 똑바로, 1 = 최대로 눕힘 */
    val recline: Float? = null,
    val temperatureC: Float? = null,
    /** 0 = 최저, 1 = 최고 풍량 */
    val fan: Float? = null,
    /** true = 내기 순환, false = 외기 유입 */
    val recirculation: Boolean? = null,
    /** 0 = 가장 어둡게, 1 = 가장 밝게 */
    val ambient: Float? = null,
    /** 앰비언트 색 (0xRRGGBB) */
    val ambientColor: Int? = null,
    /** 0~100 %. 환기용으로 살짝 연다. */
    val windowOpenPercent: Int? = null,
    /** 깨우기 1분 전에 켤 핸들 열선 단계. 쉬는 내내 켤 필요 없이 일어나 잡을 때 따뜻하면 된다(타이머·충전 기상일 때만). */
    val steeringHeat: Int? = null,
    val sound: SoundTheme? = null,
    /** 엔진차에서 공회전 소음을 덮는 마스킹 노이즈를 겹친다(비행기 기내 소리일 때만, 전기차에서는 무시). */
    val engineMasking: Boolean = true,
    /** null이면 직접 끝낼 때까지 유지 */
    val durationMinutes: Int? = null,
    /** 전기차 충전이 끝날 무렵 깨운다(시간 대신). */
    val wakeOnCharge: Boolean = false,
    /** 충전 대기에서 이만큼 차면 깨운다(%). null이면 차의 충전 한도까지. */
    val chargeTargetPercent: Int? = null,
    /** 브랜드관 장면이면 그 브랜드. 브랜드 장면은 편집·저장하지 않는다. */
    val brand: Brand? = null,
) {
    /** 엔진음 마스킹은 엔진 떨림을 순항 중 기류처럼 들려주는 연출이라 비행기 기내 소리에만 어울린다. */
    val masksEngine: Boolean get() = engineMasking && sound == SoundTheme.CABIN

    companion object {
        val palette = listOf(
            AmbientColor(0x6A4C93, R.string.ambient_cabin_purple),
            AmbientColor(0x1B1F5E, R.string.ambient_deep_space),
            AmbientColor(0xC8742B, R.string.ambient_amber),
            AmbientColor(0x2E7D5B, R.string.ambient_forest_green),
            AmbientColor(0x2B6CB0, R.string.ambient_deep_sea),
            AmbientColor(0xB0476B, R.string.ambient_sunset_pink),
            AmbientColor(0xE8D9A8, R.string.ambient_moon_cream),
            AmbientColor(0x5C1A1A, R.string.ambient_theater_red),
        )

        val concepts = listOf(
            ChabunScene(
                id = "cabin",
                name = "비행기 기내",
                tagline = "순항 고도의 기내처럼, 낮게 깔리는 엔진음과 무드 조명",
                builtIn = true,
                backdrop = Backdrop.CABIN,
                recline = 0.8f,
                temperatureC = 22f,
                fan = 0.25f,
                recirculation = true,
                ambient = 0.15f,
                ambientColor = 0x6A4C93,
                sound = SoundTheme.CABIN,
                durationMinutes = 20,
            ),
            ChabunScene(
                id = "space",
                name = "우주 유영",
                tagline = "아무 소리도 닿지 않는 깊은 우주를 떠다니듯",
                builtIn = true,
                backdrop = Backdrop.SPACE,
                recline = 0.9f,
                temperatureC = 21f,
                fan = 0.15f,
                recirculation = true,
                ambient = 0.06f,
                ambientColor = 0x1B1F5E,
                sound = SoundTheme.SPACE,
                durationMinutes = 30,
            ),
            ChabunScene(
                id = "rain_cabin",
                name = "빗속 오두막",
                tagline = "빗방울이 지붕을 두드리는 따뜻한 오두막",
                builtIn = true,
                backdrop = Backdrop.RAIN_CABIN,
                recline = 0.6f,
                temperatureC = 24f,
                fan = 0.15f,
                recirculation = true,
                ambient = 0.2f,
                ambientColor = 0xC8742B,
                steeringHeat = 1,
                sound = SoundTheme.RAIN,
                durationMinutes = 20,
            ),
            ChabunScene(
                id = "dawn_forest",
                name = "새벽 숲",
                tagline = "바람이 지나가는 숲에서 맑은 공기 한 모금",
                builtIn = true,
                backdrop = Backdrop.FOREST,
                recline = 0.5f,
                temperatureC = 21f,
                fan = 0.35f,
                recirculation = false,
                ambient = 0.3f,
                ambientColor = 0x2E7D5B,
                windowOpenPercent = 10,
                sound = SoundTheme.FOREST,
                durationMinutes = 15,
            ),
            ChabunScene(
                id = "charging",
                name = "충전 대기",
                tagline = "충전하는 동안 푹 쉬고, 끝날 무렵 맞춰 일어나요",
                builtIn = true,
                recline = 0.7f,
                temperatureC = 22f,
                fan = 0.25f,
                recirculation = true,
                ambient = 0.12f,
                ambientColor = 0x2B6CB0,
                sound = SoundTheme.CABIN,
                wakeOnCharge = true,
            ),
        )

        /** 기본·브랜드 장면의 이름·소개 리소스(편집하지 않은 문구는 화면 언어를 따른다) */
        private val builtInText = mapOf(
            "cabin" to (R.string.concept_cabin_name to R.string.concept_cabin_tagline),
            "space" to (R.string.concept_space_name to R.string.concept_space_tagline),
            "rain_cabin" to (R.string.concept_rain_cabin_name to R.string.concept_rain_cabin_tagline),
            "dawn_forest" to (R.string.concept_dawn_forest_name to R.string.concept_dawn_forest_tagline),
            "charging" to (R.string.concept_charging_name to R.string.concept_charging_tagline),
            "brand_orvel_first" to (R.string.brand_orvel_first_name to R.string.brand_orvel_first_tagline),
            "brand_mapline_suite" to (R.string.brand_mapline_suite_name to R.string.brand_mapline_suite_tagline),
        )

        /** 브랜드 이름·인사·기상 멘트 리소스 */
        private val brandText = mapOf(
            "brand_orvel_first" to Triple(R.string.brand_orvel, R.string.brand_orvel_welcome, R.string.brand_orvel_wake),
            "brand_mapline_suite" to Triple(R.string.brand_mapline, R.string.brand_mapline_welcome, R.string.brand_mapline_wake),
        )

        /** 모든 언어의 기본 이름·소개. 장면 이름이 이 중 하나면 사용자가 고치지 않은 것으로 본다. */
        fun defaultTexts(languages: List<Text>): Set<String> =
            languages.flatMap { text -> builtInText.values.flatMap { (name, tagline) -> listOf(text(name), text(tagline)) } }.toSet()

        /**
         * 기본·브랜드 장면의 이름·소개(와 브랜드 멘트)를 [text] 언어로 바꾼다.
         * 사용자가 고친 이름·소개([defaults]에 없는 문구)는 그대로 둔다.
         */
        fun localize(scene: ChabunScene, text: Text, defaults: Set<String>): ChabunScene {
            val (nameRes, taglineRes) = builtInText[scene.id] ?: return scene
            val brand = brandText[scene.id]?.let { (name, welcome, wake) ->
                scene.brand?.copy(name = text(name), welcome = text(welcome), wakeUp = text(wake))
            } ?: scene.brand
            return scene.copy(
                name = if (scene.name in defaults) text(nameRes) else scene.name,
                tagline = if (scene.tagline in defaults) text(taglineRes) else scene.tagline,
                brand = brand,
            )
        }

        /** 브랜드관: 가상 항공사·호텔이 제공하는 장면 */
        val brandScenes = listOf(
            ChabunScene(
                id = "brand_orvel_first",
                name = "퍼스트 클래스",
                tagline = "좌석을 침대처럼 펼치고, 순항 고도의 고요 속으로",
                builtIn = true,
                backdrop = Backdrop.FIRST_SUITE,
                recline = 0.95f,
                temperatureC = 22f,
                fan = 0.2f,
                recirculation = true,
                ambient = 0.08f,
                ambientColor = 0x6A4C93,
                sound = SoundTheme.CABIN,
                durationMinutes = 30,
                brand = Brand(
                    name = "오르벨 에어",
                    wordmark = "ORVEL AIR",
                    color = 0x1C2E4A,
                    welcome = "오르벨 에어 퍼스트 클래스에 오신 것을 환영합니다. 순항 고도에 올랐어요. 좌석을 펼쳐 드릴게요. 편히 쉬세요.",
                    wakeUp = "손님, 곧 목적지에 도착합니다. 좌석 등받이를 세우고 조명을 밝혀 드릴게요.",
                ),
            ),
            ChabunScene(
                id = "brand_mapline_suite",
                name = "스위트 턴다운",
                tagline = "턴다운 서비스가 끝난 스위트처럼, 은은한 조명과 빗소리",
                builtIn = true,
                backdrop = Backdrop.HOTEL_SUITE,
                recline = 0.7f,
                temperatureC = 23f,
                fan = 0.1f,
                recirculation = true,
                ambient = 0.12f,
                ambientColor = 0xE8D9A8,
                steeringHeat = 1,
                sound = SoundTheme.RAIN,
                durationMinutes = 20,
                brand = Brand(
                    name = "호텔 메이플린",
                    wordmark = "HOTEL MAPLINE",
                    color = 0x3B2A1E,
                    welcome = "호텔 메이플린 스위트에 체크인하셨어요. 턴다운 서비스를 준비했습니다. 좋은 휴식 되세요.",
                    wakeUp = "모닝콜입니다. 호텔 메이플린에서 상쾌한 하루를 시작하세요.",
                ),
            ),
        )

        /** 새 나만의 장면의 출발점 */
        fun blank() = ChabunScene(
            id = "custom-${UUID.randomUUID()}",
            name = "나만의 장면",
            tagline = "내 취향대로 만든 공간",
            recline = 0.6f,
            temperatureC = 22f,
            fan = 0.2f,
            recirculation = true,
            ambient = 0.2f,
            ambientColor = palette.first().rgb,
            sound = SoundTheme.CABIN,
            durationMinutes = 20,
        )
    }
}
