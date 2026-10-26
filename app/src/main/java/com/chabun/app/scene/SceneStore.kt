package com.chabun.app.scene

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** 사용자가 편집하거나 새로 만든 장면을 기기에 저장한다. 저장된 것이 없으면 기본 컨셉으로 시작한다. */
class SceneStore(context: Context) {

    private val prefs = context.getSharedPreferences("scenes", Context.MODE_PRIVATE)

    fun load(): List<ChabunScene> {
        val raw = prefs.getString(KEY_SCENES, null) ?: return ChabunScene.concepts
        val stored = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getJSONObject(it).toScene() }
        }.onFailure { Log.w(TAG, "stored scenes unreadable, using defaults", it) }
            .getOrDefault(ChabunScene.concepts)
        // 업데이트로 새로 생긴 기본 장면(지울 수 없음)은 저장된 목록에 이어 붙인다.
        return stored + ChabunScene.concepts.filter { concept -> stored.none { it.id == concept.id } }
    }

    fun save(scenes: List<ChabunScene>) {
        val array = JSONArray()
        scenes.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SCENES, array.toString()).apply()
    }

    private fun ChabunScene.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("tagline", tagline)
        put("builtIn", builtIn)
        put("backdrop", backdrop?.name ?: JSONObject.NULL)
        putOpt("recline", recline?.toDouble())
        putOpt("temperatureC", temperatureC?.toDouble())
        putOpt("fan", fan?.toDouble())
        putOpt("recirculation", recirculation)
        putOpt("ambient", ambient?.toDouble())
        putOpt("ambientColor", ambientColor)
        putOpt("windowOpenPercent", windowOpenPercent)
        putOpt("steeringHeat", steeringHeat)
        putOpt("sound", sound?.name)
        put("engineMasking", engineMasking)
        putOpt("durationMinutes", durationMinutes)
        put("wakeOnCharge", wakeOnCharge)
        putOpt("chargeTargetPercent", chargeTargetPercent)
    }

    private fun JSONObject.toScene() = ChabunScene(
        id = getString("id"),
        name = getString("name"),
        tagline = optString("tagline"),
        builtIn = optBoolean("builtIn"),
        backdrop = backdrop(),
        recline = float("recline"),
        temperatureC = float("temperatureC"),
        fan = float("fan"),
        recirculation = if (has("recirculation")) getBoolean("recirculation") else null,
        ambient = float("ambient"),
        ambientColor = int("ambientColor"),
        windowOpenPercent = int("windowOpenPercent"),
        steeringHeat = int("steeringHeat"),
        sound = if (has("sound")) SoundTheme.entries.firstOrNull { it.name == getString("sound") } else null,
        engineMasking = optBoolean("engineMasking", true),
        durationMinutes = int("durationMinutes"),
        wakeOnCharge = optBoolean("wakeOnCharge"),
        chargeTargetPercent = int("chargeTargetPercent"),
    )

    /** 배경 필드가 생기기 전에 저장된 기본 컨셉은 기본 배경을 이어받는다(명시적 null은 '배경 없음'). */
    private fun JSONObject.backdrop(): Backdrop? = when {
        !has("backdrop") -> ChabunScene.concepts.firstOrNull { it.id == getString("id") }?.backdrop
        isNull("backdrop") -> null
        else -> Backdrop.entries.firstOrNull { it.name == getString("backdrop") }
    }

    private fun JSONObject.float(key: String): Float? = if (has(key)) getDouble(key).toFloat() else null

    private fun JSONObject.int(key: String): Int? = if (has(key)) getInt(key) else null

    private companion object {
        const val TAG = "Chabun"
        const val KEY_SCENES = "scenes_v1"
    }
}
