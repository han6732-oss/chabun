package com.chabun.app.safety

import com.chabun.app.sound.IdleProfile
import com.chabun.app.vehicle.Powertrain

/**
 * 휴식 중 안전 감시 기준.
 * 엔진차는 창문을 닫은 채 공회전하면 실내 공기가 나빠질 수 있어 주기적으로 환기하고 최대 휴식 시간을 둔다.
 * 전기차는 배터리가 하한 아래로 내려가면 깨운다.
 */
data class SafetyPolicy(
    val ventilationEveryMs: Long,
    val ventilationForMs: Long,
    val ventilationWindowPercent: Int,
    val engineMaxRestMs: Long,
    val batteryFloorPercent: Int,
    val batteryCheckEveryMs: Long,
    /** 충전 대기: 충전이 이만큼 남았을 때 깨운다. */
    val chargeWakeLeadMs: Long,
) {
    companion object {
        val standard = SafetyPolicy(
            ventilationEveryMs = 10 * 60_000L,
            ventilationForMs = 90_000L,
            ventilationWindowPercent = 10,
            engineMaxRestMs = 60 * 60_000L,
            batteryFloorPercent = 20,
            batteryCheckEveryMs = 30_000L,
            chargeWakeLeadMs = 5 * 60_000L,
        )

        /** 시연·테스트용: 같은 동작을 짧은 주기로 보여 준다. */
        val quick = standard.copy(
            ventilationEveryMs = 60_000L,
            ventilationForMs = 15_000L,
            engineMaxRestMs = 5 * 60_000L,
            batteryCheckEveryMs = 5_000L,
            chargeWakeLeadMs = 60_000L,
        )
    }
}

/** 세션 중 안전 감시 현황. 화면에 그대로 보여 준다. */
data class SafetyStatus(
    val powertrain: Powertrain,
    val masking: IdleProfile? = null,
    val nextVentilationSeconds: Int? = null,
    /** 환기 중이면 무엇을 했는지(예: 외기 유입 · 앞창 10%) */
    val ventilating: String? = null,
    val ventilationCount: Int = 0,
    val restLimitSeconds: Int? = null,
    val batteryPercent: Int? = null,
    val batteryFloorPercent: Int? = null,
    /** 충전 대기 장면일 때만 채운다. */
    val charge: ChargeStatus? = null,
)

/**
 * 충전 대기 현황.
 * @param secondsLeft 차가 알려 준 남은 충전 시간, 없으면 이번 세션의 SOC 상승 속도로 추정한 값
 */
data class ChargeStatus(
    val percent: Int?,
    val targetPercent: Int,
    val secondsLeft: Int?,
    val charging: Boolean,
    val estimated: Boolean,
)

/** 공회전 엔진이 있는 동력원의 마스킹 프로필 */
fun Powertrain.idleProfile(): IdleProfile? = when (this) {
    Powertrain.DIESEL -> IdleProfile.DIESEL
    Powertrain.GASOLINE, Powertrain.LPG, Powertrain.HYBRID -> IdleProfile.GASOLINE
    else -> null
}
