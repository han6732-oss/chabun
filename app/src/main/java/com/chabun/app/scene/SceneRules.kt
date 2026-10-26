package com.chabun.app.scene

/** 기상 1분 전 핸들 열선을 켤 때(쉬는 내내 켤 필요 없이 일어나 핸들을 잡을 때 따뜻하면 된다) */
object SteeringPrewarm {
    /** 깨우기 몇 초 전에 켤지 */
    const val SECONDS = 60

    /** 타이머 기상: 남은 초가 이 값이 되는 틱에 켠다(1분보다 짧은 휴식이면 첫 틱에). */
    fun timerTick(totalSeconds: Int): Int = minOf(totalSeconds - 1, SECONDS)

    /** 충전 기상: 남은 충전 시간이 깨울 기준(wakeLeadMs) + 1분 이하이면 켠다. 남은 시간을 모르면 켜지 않는다. */
    fun dueForCharge(secondsLeft: Int?, wakeLeadMs: Long): Boolean =
        secondsLeft != null && secondsLeft <= wakeLeadMs / 1_000 + SECONDS
}

/** 홈의 장면 순서: 즐겨찾기(최근에 별을 누른 것부터)를 맨 앞에, 나머지는 자주 쓰는 순서. 같은 조건이면 원래 순서를 지킨다. */
fun orderScenes(scenes: List<ChabunScene>, favorites: List<String>, usage: (String) -> Int): List<ChabunScene> =
    scenes.sortedWith(
        compareBy<ChabunScene> { favorites.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
            .thenByDescending { usage(it.id) }
    )

/** 별을 누른 뒤의 즐겨찾기 목록: 없던 것은 맨 앞에 넣고, 있던 것은 뺀다. */
fun toggledFavorites(favorites: List<String>, id: String): List<String> =
    if (id in favorites) favorites - id else listOf(id) + favorites

/** 직접 맞추는 값의 한 단계 */
object Steps {
    const val MAX_MINUTES = 180
    const val MIN_CHARGE = 50

    /** 휴식 시간: 10분 아래는 1분, 그 위는 5분 단위(1~180분). 5분 단위가 아닌 값은 가까운 5분 칸으로 맞춘다. */
    fun minutes(current: Int, up: Boolean): Int = when {
        up && current < 10 -> current + 1
        up -> (current / 5 + 1) * 5
        current <= 10 -> current - 1
        else -> (current - 1) / 5 * 5
    }.coerceIn(1, MAX_MINUTES)

    /** 충전 목표: 5% 단위(50~100%) */
    fun chargePercent(current: Int, up: Boolean): Int =
        (if (up) (current / 5 + 1) * 5 else (current - 1) / 5 * 5).coerceIn(MIN_CHARGE, 100)
}
