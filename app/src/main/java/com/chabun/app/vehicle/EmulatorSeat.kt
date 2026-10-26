package com.chabun.app.vehicle

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pleos Connect 에뮬레이터 전용 등받이 위치.
 *
 * 에뮬레이터는 앱이 보낸 SEAT_BACKREST_ANGLE_1_MOVE를 Virtual Console의 시트 액추에이터(100ms마다 5%)로 움직이지만,
 * 그 위치를 차량 HAL의 SEAT_BACKREST_ANGLE_1_POS로 보내지 않는다(car_service 이벤트 0건, 2026-10-23 확인).
 * 그래서 앱에는 위치가 늘 0으로 보여 목표에서 멈출 수 없다. 에뮬레이터에서만 기기 안에서 도는
 * Virtual Console(127.0.0.1:8088)의 시트 상태를 위치로 쓴다. 실차에서는 차량이 주는 위치만 쓴다.
 *
 * 또 콘솔은 웹 화면이 열려 1초마다 하트비트(`POST /browser/session`)를 보낼 때(`web_active`)만
 * 차량 HAL의 시트 MOVE를 액추에이터로 옮긴다. 그래서 움직이기 전과 움직이는 동안 같은 하트비트를 보낸다.
 */
internal object EmulatorSeat {

    private val emulator = Build.HARDWARE == "ranchu"
    @Volatile private var reachable = true

    /** 등받이 위치(VHAL과 같은 방향: 0 = 가장 눕힘, 100 = 가장 세움). 에뮬레이터가 아니거나 콘솔이 없으면 null. 블로킹 호출. */
    fun recline(area: SeatArea): Int? {
        if (!emulator || !reachable) return null
        val seat = area.consoleSeat() ?: return null
        return request("GET", SEAT_URL)?.let { JSONObject(it).getJSONObject("positions").getJSONObject("recline").getInt(seat) }
    }

    /** 콘솔을 활성 상태로 둔다(콘솔 웹 화면의 하트비트와 같은 요청, 처음 깨울 때 0.5초쯤 걸림). 블로킹 호출. */
    fun keepConsoleActive() {
        if (emulator && reachable) request("POST", HEARTBEAT_URL)
    }

    /**
     * 콘솔 요청 하나. 콘솔은 응답마다 연결을 닫아서 연결을 다시 쓰지 않는다. 일시 실패는 한 번 다시 시도하고,
     * 연결 자체가 거부될 때(콘솔이 없는 에뮬레이터)만 이후로 콘솔을 쓰지 않는다.
     */
    private fun request(method: String, url: String): String? {
        repeat(2) { attempt ->
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                try {
                    return connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            } catch (e: ConnectException) {
                reachable = false
                Log.i(TAG, "virtual console unavailable, using vehicle seat position only: ${e.message}")
                return null
            } catch (e: IOException) {
                if (attempt == 1) Log.d(TAG, "virtual console $method failed: ${e.message}")
            }
        }
        return null
    }

    /** 콘솔의 좌석 이름. 에뮬레이터는 왼쪽 운전석(INFO_DRIVER_SEAT = ROW_1_LEFT)이다. */
    private fun SeatArea.consoleSeat(): String? = when (values.singleOrNull()) {
        SeatValue.ROW_1_LEFT -> "driver"
        SeatValue.ROW_1_RIGHT -> "passenger"
        SeatValue.ROW_2_LEFT -> "rear_left"
        SeatValue.ROW_2_RIGHT -> "rear_right"
        else -> null
    }

    private const val TAG = "Chabun"
    private const val SEAT_URL = "http://127.0.0.1:8088/vehicle/seat"
    private const val HEARTBEAT_URL = "http://127.0.0.1:8088/browser/session?client=chabun-app"
    private const val CONNECT_TIMEOUT_MS = 500
    private const val READ_TIMEOUT_MS = 1_500
}
