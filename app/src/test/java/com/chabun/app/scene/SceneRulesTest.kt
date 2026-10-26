package com.chabun.app.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneRulesTest {

    @Test
    fun steeringPrewarmsOneMinuteBeforeTimerWake() {
        // 20분 휴식: 남은 60초가 되는 틱에 켠다.
        assertEquals(60, SteeringPrewarm.timerTick(20 * 60))
        // 1분 휴식(1분 체험): 첫 틱(남은 59초)에 켠다.
        assertEquals(59, SteeringPrewarm.timerTick(60))
        assertEquals(29, SteeringPrewarm.timerTick(30))
    }

    @Test
    fun steeringPrewarmsOneMinuteBeforeChargeWake() {
        val lead = 5 * 60_000L // 남은 충전 시간 5분 이하에서 깨움
        assertFalse(SteeringPrewarm.dueForCharge(7 * 60, lead))
        assertTrue(SteeringPrewarm.dueForCharge(6 * 60, lead))
        assertTrue(SteeringPrewarm.dueForCharge(30, lead))
        // 남은 시간을 모르면 미리 켜지 않는다.
        assertFalse(SteeringPrewarm.dueForCharge(null, lead))
    }

    @Test
    fun favoritesComeFirstNewestFirstThenUsage() {
        val scenes = listOf("a", "b", "c", "d").map { ChabunScene.blank().copy(id = it) }
        val usage = mapOf("a" to 1, "b" to 9, "c" to 3, "d" to 0)
        val ordered = orderScenes(scenes, favorites = listOf("d", "a")) { usage[it] ?: 0 }
        assertEquals(listOf("d", "a", "b", "c"), ordered.map { it.id })
        // 즐겨찾기가 없으면 자주 쓰는 순서, 같으면 원래 순서
        val plain = orderScenes(scenes, favorites = emptyList()) { if (it == "c") 5 else 0 }
        assertEquals(listOf("c", "a", "b", "d"), plain.map { it.id })
    }

    @Test
    fun starTogglesAndPutsNewFavoriteInFront() {
        assertEquals(listOf("b", "a"), toggledFavorites(listOf("a"), "b"))
        assertEquals(listOf("a"), toggledFavorites(listOf("b", "a"), "b"))
        assertEquals(listOf("x"), toggledFavorites(emptyList(), "x"))
    }

    @Test
    fun minuteStepperUsesOneThenFiveMinuteSteps() {
        assertEquals(10, Steps.minutes(9, up = true))
        assertEquals(15, Steps.minutes(10, up = true))
        assertEquals(25, Steps.minutes(23, up = true))
        assertEquals(20, Steps.minutes(23, up = false))
        assertEquals(9, Steps.minutes(10, up = false))
        assertEquals(1, Steps.minutes(1, up = false))
        assertEquals(Steps.MAX_MINUTES, Steps.minutes(Steps.MAX_MINUTES, up = true))
    }

    @Test
    fun chargeStepperUsesFivePercentWithinRange() {
        assertEquals(85, Steps.chargePercent(80, up = true))
        assertEquals(75, Steps.chargePercent(80, up = false))
        assertEquals(80, Steps.chargePercent(78, up = true))
        assertEquals(Steps.MIN_CHARGE, Steps.chargePercent(Steps.MIN_CHARGE, up = false))
        assertEquals(100, Steps.chargePercent(100, up = true))
    }

    @Test
    fun engineMaskingOnlyWithCabinSound() {
        val scene = ChabunScene.blank()
        assertTrue(scene.copy(sound = SoundTheme.CABIN).masksEngine)
        assertFalse(scene.copy(sound = SoundTheme.CABIN, engineMasking = false).masksEngine)
        assertFalse(scene.copy(sound = SoundTheme.RAIN).masksEngine)
        assertFalse(scene.copy(sound = null).masksEngine)
    }
}
