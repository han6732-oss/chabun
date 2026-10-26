package com.chabun.app.navi

import com.chabun.app.i18n.AppLanguage
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/** 기상 후 남은 경로를 말과 화면용 문장으로 만든다(화면 언어별 문장). */
object RouteSpeech {

    /** 예(한국어): "목적지 해운대까지 12킬로미터, 약 18분 남았어요. 오후 3시 20분쯤 도착해요." */
    fun announce(route: RouteSummary, now: LocalTime, language: AppLanguage = AppLanguage.KOREAN): String {
        val words = words(language)
        return words.announce(route.destination, words.distance(route.remainMeters), words.duration(minutes(route.remainSeconds)), words.clock(arrival(route, now)))
    }

    /** 화면 한 줄 요약. 예(한국어): "해운대, 12.3km, 18분, 오후 3:20 도착" */
    fun summary(route: RouteSummary, now: LocalTime, language: AppLanguage = AppLanguage.KOREAN): String {
        val words = words(language)
        val km = if (route.remainMeters < 1_000) "${route.remainMeters}m" else "%.1fkm".format(Locale.ROOT, route.remainMeters / 1_000.0)
        return listOf(route.destination, km, words.duration(minutes(route.remainSeconds)), words.arrive(arrival(route, now)))
            .joinToString(words.separator)
    }

    /** 말하는 남은 시간(분 단위 올림)과 도착 시각이 어긋나지 않게 같은 분으로 계산한다. */
    private fun arrival(route: RouteSummary, now: LocalTime) = now.plusMinutes(minutes(route.remainSeconds).toLong())

    private fun minutes(seconds: Int) = (seconds + 59) / 60

    /** 거리는 100m 아래는 그대로, 1km 아래는 100m 단위, 10km 아래는 소수 한 자리, 그 위는 km 단위로 말한다. */
    private fun roundedDistance(meters: Int): Distance = when {
        meters < 100 -> Distance.Meters(meters)
        meters < 1_000 -> Distance.Meters((meters / 100.0).roundToInt() * 100)
        meters < 10_000 -> Distance.Km(meters / 1_000.0, decimal = true)
        else -> Distance.Km((meters / 1_000.0).roundToInt().toDouble(), decimal = false)
    }

    private sealed interface Distance {
        data class Meters(val value: Int) : Distance
        data class Km(val value: Double, val decimal: Boolean) : Distance
    }

    private fun Distance.text(meters: String, km: String): String = when (this) {
        is Distance.Meters -> "$value$meters"
        is Distance.Km -> (if (decimal) "%.1f".format(Locale.ROOT, value) else value.toInt().toString()) + km
    }

    private fun hour12(time: LocalTime) = (time.hour % 12).let { if (it == 0) 12 else it }

    /** 언어별 낱말과 문장 틀 */
    private class Words(
        val announce: (destination: String, distance: String, duration: String, clock: String) -> String,
        val distance: (Int) -> String,
        val duration: (Int) -> String,
        val clock: (LocalTime) -> String,
        val arrive: (LocalTime) -> String,
        /** 요약 항목 사이(가운뎃점 대신 쉼표) */
        val separator: String = ", ",
    )

    private fun words(language: AppLanguage): Words = when (language) {
        AppLanguage.KOREAN -> Words(
            announce = { d, dist, dur, clock -> "목적지 ${d}까지 $dist, 약 $dur 남았어요. ${clock}쯤 도착해요." },
            distance = { roundedDistance(it).text("미터", "킬로미터") },
            duration = { m -> hm(m, { "${it}분" }, { "${it}시간" }, { h, mm -> "${h}시간 ${mm}분" }) },
            clock = { t -> "${if (t.hour < 12) "오전" else "오후"} ${hour12(t)}시" + if (t.minute == 0) "" else " ${t.minute}분" },
            arrive = { t -> "${if (t.hour < 12) "오전" else "오후"} ${hour12(t)}:%02d 도착".format(t.minute) },
        )
        AppLanguage.ENGLISH -> Words(
            announce = { d, dist, dur, clock -> "$d is $dist away, about $dur to go. You'll arrive around $clock." },
            distance = { roundedDistance(it).text(" meters", " kilometers") },
            duration = { m -> hm(m, { "$it min" }, { if (it == 1) "1 hour" else "$it hours" }, { h, mm -> "$h hr $mm min" }) },
            clock = { t -> "${hour12(t)}:%02d ${if (t.hour < 12) "AM" else "PM"}".format(t.minute) },
            arrive = { t -> "arrive ${hour12(t)}:%02d ${if (t.hour < 12) "AM" else "PM"}".format(t.minute) },
        )
        AppLanguage.JAPANESE -> Words(
            announce = { d, dist, dur, clock -> "目的地${d}まで${dist}、あと約${dur}です。${clock}ごろ到着します。" },
            distance = { roundedDistance(it).text("メートル", "キロメートル") },
            duration = { m -> hm(m, { "${it}分" }, { "${it}時間" }, { h, mm -> "${h}時間${mm}分" }) },
            clock = { t -> "${if (t.hour < 12) "午前" else "午後"}${hour12(t)}時" + if (t.minute == 0) "" else "${t.minute}分" },
            arrive = { t -> "${if (t.hour < 12) "午前" else "午後"}${hour12(t)}:%02d 到着".format(t.minute) },
            separator = "、",
        )
        AppLanguage.CHINESE -> Words(
            announce = { d, dist, dur, clock -> "距离目的地${d}还有${dist}，大约${dur}。预计${clock}左右到达。" },
            distance = { roundedDistance(it).text("米", "公里") },
            duration = { m -> hm(m, { "${it}分钟" }, { "${it}小时" }, { h, mm -> "${h}小时${mm}分钟" }) },
            clock = { t -> "${if (t.hour < 12) "上午" else "下午"}${hour12(t)}点" + if (t.minute == 0) "" else "${t.minute}分" },
            arrive = { t -> "${if (t.hour < 12) "上午" else "下午"}${hour12(t)}:%02d 到达".format(t.minute) },
            separator = "，",
        )
    }

    private fun hm(minutes: Int, m: (Int) -> String, h: (Int) -> String, hm: (Int, Int) -> String): String = when {
        minutes < 60 -> m(minutes)
        minutes % 60 == 0 -> h(minutes / 60)
        else -> hm(minutes / 60, minutes % 60)
    }
}
