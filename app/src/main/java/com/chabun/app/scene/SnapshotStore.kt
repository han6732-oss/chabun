package com.chabun.app.scene

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 진행 중인 세션의 진입 전 상태를 기기에 남긴다.
 * 휴식 도중 앱이 강제 종료되면 차가 휴식 상태로 남으므로, 다음 실행 때 이 백업으로 원래대로 되돌린다.
 */
class SnapshotStore(context: Context) {

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun save(snapshot: CabinSnapshot) {
        prefs.edit().putString(KEY, snapshot.toJson().toString()).apply()
    }

    fun load(): CabinSnapshot? = prefs.getString(KEY, null)?.let { raw ->
        runCatching { JSONObject(raw).toSnapshot() }
            .onFailure { Log.w(TAG, "unreadable session snapshot", it) }
            .getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun CabinSnapshot.toJson() = JSONObject().apply {
        seat?.let { (area, value) -> put("seat", JSONObject().put("area", area.json()).put("value", value)) }
        put("hvacPower", seatMap(hvacPower) { it })
        put("temperature", seatMap(temperature) { it.toDouble() })
        put("fan", seatMap(fan) { it })
        put("recirculation", seatMap(recirculation) { it })
        put("ambientSwitch", seatMap(ambientSwitch) { it.name })
        put("ambientBrightness", seatMap(ambientBrightness) { it })
        put("ambientColor", seatMap(ambientColor) { it })
        put("windows", JSONArray().apply {
            windows.forEach { (area, value) -> put(JSONObject().put("area", JSONArray(area.values.map { it.name })).put("value", value)) }
        })
        steeringHeat?.let { put("steeringHeat", it) }
    }

    private fun JSONObject.toSnapshot() = CabinSnapshot(
        seat = optJSONObject("seat")?.let { it.getJSONArray("area").seatArea() to it.getInt("value") },
        hvacPower = seatMap("hvacPower") { getBoolean(it) },
        temperature = seatMap("temperature") { getDouble(it).toFloat() },
        fan = seatMap("fan") { getInt(it) },
        recirculation = seatMap("recirculation") { getBoolean(it) },
        ambientSwitch = seatMap("ambientSwitch") { LightSwitch.valueOf(getString(it)) },
        ambientBrightness = seatMap("ambientBrightness") { getInt(it) },
        ambientColor = seatMap("ambientColor") { getInt(it) },
        windows = optJSONArray("windows")?.let { array ->
            (0 until array.length()).associate { i ->
                val item = array.getJSONObject(i)
                val values = item.getJSONArray("area").let { a -> Array(a.length()) { WindowValue.valueOf(a.getString(it)) } }
                WindowArea(*values) to item.getInt("value")
            }
        }.orEmpty(),
        steeringHeat = if (has("steeringHeat")) getInt("steeringHeat") else null,
    )

    private fun SeatArea.json() = JSONArray(values.map { it.name })

    private fun JSONArray.seatArea() = SeatArea(*Array(length()) { SeatValue.valueOf(getString(it)) })

    private fun <T> seatMap(map: Map<SeatArea, T>, value: (T) -> Any) = JSONArray().apply {
        map.forEach { (area, v) -> put(JSONObject().put("area", area.json()).put("value", value(v))) }
    }

    private fun <T> JSONObject.seatMap(key: String, read: JSONObject.(String) -> T): Map<SeatArea, T> =
        optJSONArray(key)?.let { array ->
            (0 until array.length()).associate { i ->
                val item = array.getJSONObject(i)
                item.getJSONArray("area").seatArea() to item.read("value")
            }
        }.orEmpty()

    private companion object {
        const val TAG = "Chabun"
        const val KEY = "snapshot_v1"
    }
}
