package com.chabun.app.scene

import com.chabun.app.R
import com.chabun.app.i18n.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SceneLocalizeTest {

    /** 리소스 대신 "언어:리소스 id"를 돌려주는 가짜 언어 */
    private fun language(tag: String) = Text { id, _ -> "$tag:$id" }

    private val ko = language("ko")
    private val en = language("en")
    private val defaults = ChabunScene.defaultTexts(listOf(ko, en))

    private val cabin = ChabunScene.concepts.first { it.id == "cabin" }
        .copy(name = ko(R.string.concept_cabin_name), tagline = ko(R.string.concept_cabin_tagline))

    @Test
    fun untouchedBuiltInFollowsLanguage() {
        val english = ChabunScene.localize(cabin, en, defaults)
        assertEquals(en(R.string.concept_cabin_name), english.name)
        assertEquals(en(R.string.concept_cabin_tagline), english.tagline)
        // 다시 한국어로 돌리면 한국어 기본 문구로 돌아온다.
        assertEquals(ko(R.string.concept_cabin_name), ChabunScene.localize(english, ko, defaults).name)
    }

    @Test
    fun editedTextStaysAsWritten() {
        val edited = cabin.copy(name = "내 낮잠")
        val english = ChabunScene.localize(edited, en, defaults)
        assertEquals("내 낮잠", english.name)
        // 고치지 않은 소개는 언어를 따른다.
        assertEquals(en(R.string.concept_cabin_tagline), english.tagline)
    }

    @Test
    fun brandMessagesFollowLanguageButWordmarkStays() {
        val orvel = ChabunScene.brandScenes.first { it.id == "brand_orvel_first" }
        val brand = ChabunScene.localize(orvel, en, defaults).brand!!
        assertEquals(en(R.string.brand_orvel), brand.name)
        assertEquals(en(R.string.brand_orvel_welcome), brand.welcome)
        assertEquals(en(R.string.brand_orvel_wake), brand.wakeUp)
        assertEquals(orvel.brand!!.wordmark, brand.wordmark)
    }

    @Test
    fun userScenesAreLeftAlone() {
        val mine = ChabunScene(id = "custom-1", name = "퇴근 전 10분", tagline = "조용히")
        assertSame(mine, ChabunScene.localize(mine, en, defaults))
    }
}
