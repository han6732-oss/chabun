package com.chabun.app.navi

import com.chabun.app.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class RouteSpeechTest {

    private val now = LocalTime.of(15, 2)

    @Test
    fun announcesDistanceTimeAndArrival() {
        val route = RouteSummary("해운대", remainMeters = 12_300, remainSeconds = 18 * 60)
        assertEquals("목적지 해운대까지 12킬로미터, 약 18분 남았어요. 오후 3시 20분쯤 도착해요.", RouteSpeech.announce(route, now))
        assertEquals("해운대, 12.3km, 18분, 오후 3:20 도착", RouteSpeech.summary(route, now))
    }

    @Test
    fun shortAndLongRoutes() {
        val near = RouteSummary("회사", remainMeters = 840, remainSeconds = 150)
        assertEquals("목적지 회사까지 800미터, 약 3분 남았어요. 오후 3시 5분쯤 도착해요.", RouteSpeech.announce(near, now))

        // 에뮬레이터 실측: 42dot까지 50m · 1분
        val veryNear = RouteSummary("42dot", remainMeters = 50, remainSeconds = 40)
        assertEquals("목적지 42dot까지 50미터, 약 1분 남았어요. 오후 3시 3분쯤 도착해요.", RouteSpeech.announce(veryNear, now))

        val far = RouteSummary("강릉", remainMeters = 4_500, remainSeconds = 95 * 60)
        assertEquals("목적지 강릉까지 4.5킬로미터, 약 1시간 35분 남았어요. 오후 4시 37분쯤 도착해요.", RouteSpeech.announce(far, now))
    }

    @Test
    fun speaksInOtherLanguages() {
        val route = RouteSummary("Haeundae", remainMeters = 12_300, remainSeconds = 18 * 60)
        assertEquals(
            "Haeundae is 12 kilometers away, about 18 min to go. You'll arrive around 3:20 PM.",
            RouteSpeech.announce(route, now, AppLanguage.ENGLISH),
        )
        assertEquals("Haeundae, 12.3km, 18 min, arrive 3:20 PM", RouteSpeech.summary(route, now, AppLanguage.ENGLISH))
        assertEquals("目的地Haeundaeまで12キロメートル、あと約18分です。午後3時20分ごろ到着します。", RouteSpeech.announce(route, now, AppLanguage.JAPANESE))
        assertEquals("距离目的地Haeundae还有12公里，大约18分钟。预计下午3点20分左右到达。", RouteSpeech.announce(route, now, AppLanguage.CHINESE))
        val far = RouteSummary("Gangneung", remainMeters = 4_500, remainSeconds = 95 * 60)
        assertEquals("Gangneung, 4.5km, 1 hr 35 min, arrive 4:37 PM", RouteSpeech.summary(far, now, AppLanguage.ENGLISH))
    }

    @Test
    fun arrivalAcrossMidnightAndOnTheHour() {
        val late = RouteSummary("집", remainMeters = 30_000, remainSeconds = 58 * 60)
        assertEquals("목적지 집까지 30킬로미터, 약 58분 남았어요. 오전 12시쯤 도착해요.", RouteSpeech.announce(late, LocalTime.of(23, 2)))
    }
}
