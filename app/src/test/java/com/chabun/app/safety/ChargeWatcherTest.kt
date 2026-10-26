package com.chabun.app.safety

import com.chabun.app.vehicle.ChargeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeWatcherTest {

    private val policy = SafetyPolicy.standard // 5분 전에 깨움, 배터리 하한 20%
    private val charging = ChargeState(state = ChargeState.STATE_CHARGING)

    @Test
    fun wakesWhenReportedTimeLeftReachesLead() {
        val watcher = ChargeWatcher(policy)
        assertNull(watcher.update(0, 60, charging.copy(secondsLeft = 1_800)).wake)
        assertEquals(ChargeWake.EndingSoon, watcher.update(1_000, 61, charging.copy(secondsLeft = 290)).wake)
    }

    @Test
    fun wakesWhenChargingStops() {
        val watcher = ChargeWatcher(policy)
        assertNull(watcher.update(0, 70, charging.copy(secondsLeft = 3_000)).wake)
        val done = watcher.update(1_000, 80, ChargeState(state = ChargeState.STATE_FULLY_CHARGED))
        assertEquals(ChargeWake.Ended, done.wake)
    }

    @Test
    fun withoutChargeStateUsesRisingSocAndLimit() {
        // 에뮬레이터처럼 충전 상태를 주지 않는 차: SOC가 올라 한도(80%) 바로 아래에 닿으면 깨운다.
        val watcher = ChargeWatcher(policy.copy(chargeWakeLeadMs = 0))
        val limit = ChargeState(limitPercent = 80)
        assertNull(watcher.update(0, 70, limit).wake)
        assertNull(watcher.update(60_000, 72, limit).wake)
        assertEquals(ChargeWake.TargetReached(80), watcher.update(120_000, 79, limit).wake)
    }

    @Test
    fun estimatedTimeLeftWakesBeforeLimit() {
        // 1분에 2%씩 오르면 72%에서 80%까지 약 4분 → 5분 기준 안이라 미리 깨운다.
        val watcher = ChargeWatcher(policy)
        val limit = ChargeState(limitPercent = 80)
        assertNull(watcher.update(0, 70, limit).wake)
        assertEquals(ChargeWake.EndingSoon, watcher.update(60_000, 72, limit).wake)
    }

    @Test
    fun userTargetBelowLimitIgnoresReportedTimeToLimit() {
        // 한도 80%까지 30분 남았다고 해도 목표가 70%면 SOC 상승 속도(1분에 2%)로 판단한다.
        val watcher = ChargeWatcher(policy, targetPercent = 70)
        val reported = charging.copy(limitPercent = 80, secondsLeft = 1_800)
        assertNull(watcher.update(0, 56, reported).wake)
        val near = watcher.update(60_000, 58, reported)
        assertEquals(70, near.status.targetPercent)
        assertTrue(near.status.estimated)
        assertNull(near.wake) // 58 → 70%: 6분 남음
        assertEquals(ChargeWake.NearTarget(70), watcher.update(120_000, 61, reported).wake)
    }

    @Test
    fun userTargetAboveLimitStopsAtLimit() {
        val watcher = ChargeWatcher(policy.copy(chargeWakeLeadMs = 0), targetPercent = 95)
        val limit = ChargeState(limitPercent = 80)
        assertNull(watcher.update(0, 70, limit).wake)
        assertEquals(ChargeWake.TargetReached(80), watcher.update(60_000, 79, limit).wake)
    }

    @Test
    fun estimatesTimeLeftFromSocRise() {
        val watcher = ChargeWatcher(policy)
        watcher.update(0, 50, ChargeState())
        // 1분에 1%씩 오르면 100%까지 49분
        val status = watcher.update(60_000, 51, ChargeState()).status
        assertTrue(status.charging)
        assertTrue(status.estimated)
        assertEquals(49 * 60, status.secondsLeft)
    }

    @Test
    fun fullBatteryAtStartDoesNotWake() {
        val watcher = ChargeWatcher(policy)
        assertNull(watcher.update(0, 100, ChargeState(state = ChargeState.STATE_FULLY_CHARGED)).wake)
        assertNull(watcher.update(5_000, 100, ChargeState(state = ChargeState.STATE_FULLY_CHARGED)).wake)
    }

    @Test
    fun notChargingAndLowBatteryWakes() {
        val watcher = ChargeWatcher(policy)
        assertEquals(ChargeWake.BatteryLow(20), watcher.update(0, 15, ChargeState()).wake)
    }
}
