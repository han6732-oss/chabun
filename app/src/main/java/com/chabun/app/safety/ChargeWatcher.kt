package com.chabun.app.safety

import com.chabun.app.vehicle.ChargeState

/**
 * 충전 대기 장면에서 '충전이 끝날 무렵'을 판단한다. 차가 주는 정보가 차종마다 달라 세 가지 신호를 순서대로 본다.
 *  1. 충전 중이던 차가 충전을 멈춤(완충·충전기 분리) → 바로 깨움
 *  2. 남은 충전 시간이 기준 이하(차가 주지 않으면 이번 세션의 SOC 상승 속도로 추정)
 *  3. 세션 중 SOC가 올라 충전 목표 바로 아래에 닿음
 * 목표는 사용자가 정한 충전량(차의 충전 한도를 넘지 않음), 정하지 않았으면 차의 충전 한도다.
 * 충전이 이뤄지지 않는 동안에는 일반 전기차처럼 배터리 하한을 지킨다.
 */
/** 충전 대기에서 깨우는 이유(문장은 화면 언어로 뷰모델이 만든다) */
sealed interface ChargeWake {
    /** 충전 중이던 차가 충전을 멈춤 */
    data object Ended : ChargeWake
    /** 충전 한도까지 남은 시간이 깨울 기준 이하 */
    data object EndingSoon : ChargeWake
    /** 사용자가 정한 목표까지 남은 시간이 깨울 기준 이하 */
    data class NearTarget(val target: Int) : ChargeWake
    /** SOC가 목표 바로 아래에 닿음 */
    data class TargetReached(val target: Int) : ChargeWake
    /** 충전되지 않는 중 배터리 하한 아래 */
    data class BatteryLow(val floor: Int) : ChargeWake
}

class ChargeWatcher(private val policy: SafetyPolicy, private val targetPercent: Int? = null) {

    private var startPercent: Int? = null
    private var wasCharging = false
    private val samples = ArrayDeque<Pair<Long, Int>>()

    data class Result(val status: ChargeStatus, val wake: ChargeWake?)

    fun update(nowMs: Long, percent: Int?, charge: ChargeState): Result {
        if (startPercent == null) startPercent = percent
        if (percent != null && samples.lastOrNull()?.second != percent) {
            samples.addLast(nowMs to percent)
            while (samples.size > MAX_SAMPLES) samples.removeFirst()
        }
        if (charge.charging) wasCharging = true

        val limit = charge.limitPercent ?: FULL
        val target = targetPercent?.coerceAtMost(limit) ?: limit
        val rising = percent != null && startPercent?.let { percent > it } == true
        val charging = charge.charging || rising
        // 차가 주는 남은 시간은 충전 한도까지라, 그보다 낮은 목표면 SOC 상승 속도로 추정한다.
        val reported = charge.secondsLeft?.takeIf { charge.charging && it > 0 && target == limit }
        val secondsLeft = reported ?: estimateSecondsLeft(target)
        val status = ChargeStatus(percent, target, secondsLeft, charging, estimated = reported == null && secondsLeft != null)

        val wake = when {
            wasCharging && !charge.charging && charge.state != null -> ChargeWake.Ended
            charging && secondsLeft != null && secondsLeft * 1_000L <= policy.chargeWakeLeadMs ->
                if (target < limit) ChargeWake.NearTarget(target) else ChargeWake.EndingSoon
            rising && percent != null && percent >= target - TARGET_MARGIN -> ChargeWake.TargetReached(target)
            !charging && percent != null && percent < policy.batteryFloorPercent -> ChargeWake.BatteryLow(policy.batteryFloorPercent)
            else -> null
        }
        return Result(status, wake)
    }

    /** 최근 SOC 변화량으로 목표까지 남은 시간을 추정한다. 오르지 않았으면 추정하지 않는다. */
    private fun estimateSecondsLeft(target: Int): Int? {
        if (samples.size < 2) return null
        val (t0, p0) = samples.first()
        val (t1, p1) = samples.last()
        if (p1 <= p0 || t1 <= t0 || p1 >= target) return null
        val perSecond = (p1 - p0) / ((t1 - t0) / 1_000.0)
        return ((target - p1) / perSecond).toInt()
    }

    private companion object {
        const val FULL = 100
        const val TARGET_MARGIN = 1
        const val MAX_SAMPLES = 10
    }
}
