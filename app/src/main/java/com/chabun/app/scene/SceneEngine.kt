package com.chabun.app.scene

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.util.Log
import androidx.annotation.StringRes
import com.chabun.app.R
import com.chabun.app.i18n.Text
import com.chabun.app.vehicle.Bounds
import com.chabun.app.vehicle.SeatNotMovingException
import com.chabun.app.vehicle.VehicleGateway
import com.chabun.app.vehicle.VehicleGateway.Companion.driverOrFirst
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.properties.Delegates

enum class Domain(@StringRes val label: Int) {
    SEAT(R.string.domain_seat),
    CLIMATE(R.string.domain_climate),
    AMBIENT(R.string.domain_ambient),
    WINDOW(R.string.domain_window),
    STEERING(R.string.domain_steering),
}

sealed interface StepStatus {
    data object Running : StepStatus
    data class Done(val detail: String) : StepStatus
    data class Skipped(val reason: String) : StepStatus
    data class Failed(val reason: String) : StepStatus
}

data class StepResult(val domain: Domain, val status: StepStatus)

/** 차종이 지원하는 영역과 값 범위. 한 번 읽어 두고 재사용한다. */
data class CabinCapabilities(
    val seat: Pair<SeatArea, Bounds<Int>>?,
    val hvacPower: List<SeatArea>,
    val temperature: Map<SeatArea, Bounds<Float>>,
    val fan: Map<SeatArea, Bounds<Int>>,
    val recirculation: List<SeatArea>,
    val ambient: Map<SeatArea, Bounds<Int>>,
    val windows: Map<WindowArea, Bounds<Int>>,
)

/** 차분 진입 직전 상태. 끝날 때 이 값으로 되돌린다. */
data class CabinSnapshot(
    val seat: Pair<SeatArea, Int>? = null,
    val hvacPower: Map<SeatArea, Boolean> = emptyMap(),
    val temperature: Map<SeatArea, Float> = emptyMap(),
    val fan: Map<SeatArea, Int> = emptyMap(),
    val recirculation: Map<SeatArea, Boolean> = emptyMap(),
    val ambientSwitch: Map<SeatArea, LightSwitch> = emptyMap(),
    val ambientBrightness: Map<SeatArea, Int> = emptyMap(),
    val ambientColor: Map<SeatArea, Int> = emptyMap(),
    val windows: Map<WindowArea, Int> = emptyMap(),
    val steeringHeat: Int? = null,
)

/**
 * 차분 장면 전환 엔진. 도메인별로 순차 제어하고, 지원하지 않거나 실패한 도메인은 건너뛰어 어떤 차종에서도 끝까지 진행한다.
 */
class SceneEngine(private val vehicle: VehicleGateway, private val text: Text) {

    private var capabilities: CabinCapabilities? = null

    /** 지원 범위를 읽는다. 응답 타임아웃(예: 서비스 연결 직후)이 있었다면 캐시하지 않고 다음에 다시 읽는다. */
    suspend fun capabilities(): CabinCapabilities {
        capabilities?.let { return it }
        var timedOut = false
        fun <T> Result<T>.check(what: String): Result<T> = log(what).onFailure { if (it is TimeoutCancellationException) timedOut = true }
        val caps = CabinCapabilities(
            seat = vehicle.seatBackrestBounds().check("seat caps").getOrNull()?.driverOrFirst(),
            hvacPower = vehicle.hvacPowerAreas().check("hvac power caps").getOrDefault(emptyList()),
            temperature = vehicle.temperatureBounds().check("temp caps").getOrDefault(emptyMap()),
            fan = vehicle.fanSpeedBounds().check("fan caps").getOrDefault(emptyMap()),
            recirculation = vehicle.recirculationAreas().check("recirculation caps").getOrDefault(emptyList()),
            ambient = vehicle.ambientBrightnessBounds().check("ambient caps").getOrDefault(emptyMap()),
            windows = vehicle.windowBounds().check("window caps").getOrDefault(emptyMap())
                .filterKeys { it in VehicleGateway.FRONT_WINDOWS },
        )
        Log.i(TAG, "capabilities: $caps")
        if (!timedOut) capabilities = caps
        return caps
    }

    /**
     * 현재 상태를 백업한 뒤 장면을 적용한다. 백업은 도메인을 바꾸기 직전마다 [onSnapshot]으로 넘겨,
     * 적용 도중 취소되더라도 그때까지 바꾼 것은 복원할 수 있게 한다.
     */
    suspend fun enter(scene: ChabunScene, report: (StepResult) -> Unit, onSnapshot: (CabinSnapshot) -> Unit) {
        val caps = capabilities()
        var snapshot by Delegates.observable(CabinSnapshot()) { _, _, new -> onSnapshot(new) }

        scene.recline?.let { recline ->
            report(StepResult(Domain.SEAT, StepStatus.Running))
            val (area, _) = caps.seat ?: return@let report(skipped(Domain.SEAT))
            snapshot = snapshot.copy(seat = vehicle.seatBackrest(area).getOrNull()?.let { area to it })
            report(reclineSeat(caps, recline))
        }

        if (scene.temperatureC != null || scene.fan != null || scene.recirculation != null) {
            report(StepResult(Domain.CLIMATE, StepStatus.Running))
            // 공조가 꺼져 있으면 온도·풍량을 읽거나 바꿀 수 없으므로 전원 상태를 먼저 백업하고 켠다.
            snapshot = snapshot.copy(hvacPower = caps.hvacPower.toSet().readEach { vehicle.hvacPower(it) })
            val details = mutableListOf<String>()
            if (snapshot.hvacPower.containsValue(false)) {
                caps.hvacPower.forEach { vehicle.setHvacPower(it, true).log("hvac power") }
                delay(HVAC_POWER_SETTLE_MS)
                val poweredOn = caps.hvacPower.all { vehicle.hvacPower(it).getOrNull() == true }
                Log.i(TAG, "hvac power on requested, now on=$poweredOn")
                if (!poweredOn) details += text(R.string.step_hvac_power_off)
            }
            snapshot = snapshot.copy(
                temperature = caps.temperature.keys.readEach { vehicle.temperature(it) },
                fan = caps.fan.keys.readEach { vehicle.fanSpeed(it) },
                recirculation = caps.recirculation.toSet().readEach { vehicle.recirculation(it) },
            )
            scene.temperatureC?.let { t ->
                val ok = caps.temperature.map { (area, b) -> vehicle.setTemperature(area, t.coerceIn(b.min, b.max)).log("temp") }
                if (ok.any { it.isSuccess }) details += text(R.string.celsius, t)
            }
            scene.fan?.let { level ->
                val ok = caps.fan.map { (area, b) -> vehicle.setFanSpeed(area, b.at(level)).log("fan") }
                caps.fan.values.firstOrNull()?.let { b -> if (ok.any { it.isSuccess }) details += text(R.string.step_fan, b.at(level)) }
            }
            scene.recirculation?.let { inside ->
                val ok = caps.recirculation.map { vehicle.setRecirculation(it, inside).log("recirculation") }
                if (ok.any { it.isSuccess }) details += text(if (inside) R.string.recirc_in else R.string.recirc_out)
            }
            report(
                StepResult(
                    Domain.CLIMATE,
                    if (details.isEmpty()) StepStatus.Failed(text(R.string.step_climate_failed)) else StepStatus.Done(details.joinToString(text(R.string.list_separator)))
                )
            )
        }

        if (scene.ambient != null || scene.ambientColor != null) {
            report(StepResult(Domain.AMBIENT, StepStatus.Running))
            val areas = ambientAreas(caps)
            snapshot = snapshot.copy(
                ambientSwitch = areas.readEach { vehicle.ambientSwitch(it) },
                ambientBrightness = areas.readEach { vehicle.ambientBrightness(it) },
                ambientColor = areas.readEach { vehicle.ambientColor(it) },
            )
            report(applyAmbient(caps, scene.ambient, scene.ambientColor))
        }

        scene.windowOpenPercent?.let { percent ->
            report(StepResult(Domain.WINDOW, StepStatus.Running))
            if (caps.windows.isEmpty()) return@let report(skipped(Domain.WINDOW))
            snapshot = snapshot.copy(windows = caps.windows.keys.readEach { vehicle.windowPosition(it) })
            val ok = caps.windows.map { (area, b) -> vehicle.setWindowPosition(area, b.at(percent / 100f)).log("window") }
            report(
                StepResult(
                    Domain.WINDOW,
                    if (ok.any { it.isSuccess }) StepStatus.Done(text(R.string.step_window_open, percent)) else StepStatus.Failed(text(R.string.step_window_failed))
                )
            )
        }
    }

    /** 편집하며 바로 느껴보기 전에 시트·조명의 지금 상태를 읽어 둔다. */
    suspend fun trialBackup(): CabinSnapshot {
        val caps = capabilities()
        val areas = ambientAreas(caps)
        return CabinSnapshot(
            seat = caps.seat?.let { (area, _) -> vehicle.seatBackrest(area).log("read seat").getOrNull()?.let { area to it } },
            ambientSwitch = areas.readEach { vehicle.ambientSwitch(it) },
            ambientBrightness = areas.readEach { vehicle.ambientBrightness(it) },
            ambientColor = areas.readEach { vehicle.ambientColor(it) },
        )
    }

    /**
     * 편집하며 바로 느껴보기: [previous](직전에 적용한 편집, 처음이면 null)와 달라진 시트·조명만 차에 적용한다.
     * 장면에서 뺀 항목은 [backup] 값으로 되돌린다.
     */
    suspend fun trial(scene: ChabunScene, previous: ChabunScene?, backup: CabinSnapshot, report: (StepResult) -> Unit) {
        val caps = capabilities()
        if (previous == null || scene.recline != previous.recline) {
            val recline = scene.recline
            val seat = backup.seat
            when {
                recline != null -> {
                    report(StepResult(Domain.SEAT, StepStatus.Running))
                    report(reclineSeat(caps, recline))
                }
                previous?.recline != null && seat != null -> {
                    report(StepResult(Domain.SEAT, StepStatus.Running))
                    report(vehicle.moveSeatBackrestTo(seat.first, seat.second).toStep(Domain.SEAT) { text(R.string.step_seat_original, it) })
                }
            }
        }
        val lit = scene.ambient != null || scene.ambientColor != null
        if (previous == null || scene.ambient != previous.ambient || scene.ambientColor != previous.ambientColor) {
            when {
                lit -> {
                    report(StepResult(Domain.AMBIENT, StepStatus.Running))
                    report(applyAmbient(caps, scene.ambient, scene.ambientColor))
                }
                previous != null -> restore(CabinSnapshot(
                    ambientSwitch = backup.ambientSwitch,
                    ambientBrightness = backup.ambientBrightness,
                    ambientColor = backup.ambientColor,
                ), report)
            }
        }
    }

    /**
     * 깨우기 직전 예열: 핸들 열선을 켠다(장면에 들어갈 때가 아니라 기상 1분 전에).
     * 켜기 전 값을 [onBackup]으로 먼저 넘겨, 켜는 도중 끝나더라도 끝날 때 되돌릴 수 있게 한다.
     */
    suspend fun prewarm(level: Int, report: (StepResult) -> Unit, onBackup: (Int?) -> Unit) {
        report(StepResult(Domain.STEERING, StepStatus.Running))
        onBackup(vehicle.steeringHeat().log("read steering heat").getOrNull())
        report(vehicle.setSteeringHeat(level).toStep(Domain.STEERING) { text(R.string.step_steering_prewarm, level) })
    }

    /**
     * 환기: 외기 유입·앞창 열기·풍량 올리기를 잠시 했다가 환기 직전(장면) 상태로 되돌린다.
     * 장면이 건드리지 않은 창문·공조도 환기로 바뀌므로, 백업에 없는 항목은 지금 값(=세션 시작 전 값)을 먼저 백업에 더해
     * 환기 도중 끝내더라도 원래대로 복원되게 한다.
     */
    suspend fun ventilate(
        snapshot: CabinSnapshot,
        windowPercent: Int,
        durationMs: Long,
        onSnapshot: (CabinSnapshot) -> Unit,
        onActive: (String) -> Unit,
    ) {
        val caps = capabilities()
        val windows = caps.windows.keys.readEach { vehicle.windowPosition(it) }
        val recirculation = caps.recirculation.toSet().readEach { vehicle.recirculation(it) }
        val fan = caps.fan.keys.readEach { vehicle.fanSpeed(it) }
        onSnapshot(
            snapshot.copy(
                windows = snapshot.windows.ifEmpty { windows },
                recirculation = snapshot.recirculation.ifEmpty { recirculation },
                fan = snapshot.fan.ifEmpty { fan },
            )
        )

        val details = mutableListOf<String>()
        if (caps.recirculation.map { vehicle.setRecirculation(it, false).log("vent air") }.any { it.isSuccess }) details += text(R.string.recirc_out)
        if (caps.windows.map { (a, b) -> vehicle.setWindowPosition(a, b.at(windowPercent / 100f)).log("vent window") }.any { it.isSuccess }) {
            details += text(R.string.vent_window, windowPercent)
        }
        val fanUp = caps.fan.map { (a, b) -> vehicle.setFanSpeed(a, maxOf(b.at(VENT_FAN), fan[a] ?: 0)).log("vent fan") }
        if (fanUp.any { it.isSuccess }) details += text(R.string.vent_fan)
        Log.i(TAG, "ventilating: $details")
        onActive(details.joinToString(text(R.string.list_separator)).ifEmpty { text(R.string.vent_nothing) })

        delay(durationMs)
        recirculation.forEach { (a, v) -> vehicle.setRecirculation(a, v).log("vent restore air") }
        windows.forEach { (a, v) -> vehicle.setWindowPosition(a, v).log("vent restore window") }
        fan.forEach { (a, v) -> vehicle.setFanSpeed(a, v).log("vent restore fan") }
    }

    /** 기상 시퀀스: 조명을 서서히 밝히고 시원한 바람을 낸 뒤 원래 상태로 되돌린다. */
    suspend fun wake(snapshot: CabinSnapshot, report: (StepResult) -> Unit) {
        val caps = capabilities()
        if (caps.ambient.isNotEmpty() && snapshot.ambientBrightness.isNotEmpty()) {
            report(StepResult(Domain.AMBIENT, StepStatus.Running))
            for (step in 1..WAKE_STEPS) {
                caps.ambient.forEach { (area, b) -> vehicle.setAmbientBrightness(area, b.at(step / WAKE_STEPS.toFloat() * 0.8f)) }
                delay(WAKE_STEP_MS)
            }
            report(StepResult(Domain.AMBIENT, StepStatus.Done(text(R.string.step_lights_up))))
        }
        if (caps.fan.isNotEmpty() && snapshot.fan.isNotEmpty()) {
            report(StepResult(Domain.CLIMATE, StepStatus.Running))
            val ok = caps.fan.map { (area, b) -> vehicle.setFanSpeed(area, b.at(0.6f)).log("wake breeze") }
            if (ok.any { it.isSuccess }) {
                report(StepResult(Domain.CLIMATE, StepStatus.Done(text(R.string.step_breeze))))
                delay(WAKE_BREEZE_MS)
            } else {
                report(StepResult(Domain.CLIMATE, StepStatus.Skipped(text(R.string.step_breeze_unavailable))))
            }
        }
        restore(snapshot, report)
    }

    /** 스냅샷 상태로 즉시 되돌린다. 시트를 먼저 세워 바로 운전 자세로 돌아오게 한다. */
    suspend fun restore(snapshot: CabinSnapshot, report: (StepResult) -> Unit) {
        snapshot.seat?.let { (area, value) ->
            report(StepResult(Domain.SEAT, StepStatus.Running))
            report(vehicle.moveSeatBackrestTo(area, value).toStep(Domain.SEAT) { text(R.string.step_seat_original, it) })
        }
        if (snapshot.hvacPower.isNotEmpty() || snapshot.temperature.isNotEmpty()) {
            report(StepResult(Domain.CLIMATE, StepStatus.Running))
            snapshot.temperature.forEach { (a, v) -> vehicle.setTemperature(a, v).log("restore temp") }
            snapshot.fan.forEach { (a, v) -> vehicle.setFanSpeed(a, v).log("restore fan") }
            snapshot.recirculation.forEach { (a, v) -> vehicle.setRecirculation(a, v).log("restore recirculation") }
            snapshot.hvacPower.forEach { (a, v) -> vehicle.setHvacPower(a, v).log("restore hvac power") }
            report(StepResult(Domain.CLIMATE, StepStatus.Done(text(R.string.step_climate_restored))))
        }
        if (snapshot.ambientSwitch.isNotEmpty() || snapshot.ambientBrightness.isNotEmpty()) {
            report(StepResult(Domain.AMBIENT, StepStatus.Running))
            snapshot.ambientColor.forEach { (a, v) -> vehicle.setAmbientColor(a, v).log("restore ambient color") }
            snapshot.ambientBrightness.forEach { (a, v) -> vehicle.setAmbientBrightness(a, v).log("restore ambient") }
            snapshot.ambientSwitch.forEach { (a, v) -> vehicle.setAmbientSwitch(a, v).log("restore ambient switch") }
            report(StepResult(Domain.AMBIENT, StepStatus.Done(text(R.string.step_lights_restored))))
        }
        if (snapshot.windows.isNotEmpty()) {
            report(StepResult(Domain.WINDOW, StepStatus.Running))
            snapshot.windows.forEach { (a, v) -> vehicle.setWindowPosition(a, v).log("restore window") }
            report(StepResult(Domain.WINDOW, StepStatus.Done(text(R.string.step_window_closed))))
        }
        snapshot.steeringHeat?.let { level ->
            report(vehicle.setSteeringHeat(level).toStep(Domain.STEERING) { text(R.string.step_steering_restored) })
        }
    }

    /** 등받이를 장면의 눕힘 정도(0~1)로 움직인다. 값은 작을수록 더 눕는다(VHAL SEAT_BACKREST_ANGLE_1_POS). */
    private suspend fun reclineSeat(caps: CabinCapabilities, recline: Float): StepResult {
        val (area, bounds) = caps.seat ?: return skipped(Domain.SEAT)
        val target = (bounds.max - recline * (bounds.max - bounds.min)).roundToInt()
        return vehicle.moveSeatBackrestTo(area, target).toStep(Domain.SEAT) { text(R.string.step_seat_recline, it, (recline * 100).roundToInt()) }
    }

    /** 앰비언트 조명을 켜고 밝기·색을 바꾼다. */
    private suspend fun applyAmbient(caps: CabinCapabilities, level: Float?, rgb: Int?): StepResult {
        val areas = ambientAreas(caps)
        val details = mutableListOf<String>()
        areas.forEach { vehicle.setAmbientSwitch(it, LightSwitch.ON).log("ambient switch") }
        level?.let {
            val ok = caps.ambient.map { (area, b) -> vehicle.setAmbientBrightness(area, b.at(it)).log("ambient brightness") }
            if (ok.any { r -> r.isSuccess }) details += text(R.string.step_brightness, (it * 100).roundToInt())
        }
        rgb?.let {
            val ok = areas.map { area -> vehicle.setAmbientColor(area, it).log("ambient color") }
            if (ok.any { r -> r.isSuccess }) details += text(R.string.step_color, it)
        }
        return StepResult(
            Domain.AMBIENT,
            if (details.isEmpty()) StepStatus.Failed(text(R.string.step_lights_failed)) else StepStatus.Done(details.joinToString(text(R.string.list_separator))),
        )
    }

    private fun ambientAreas(caps: CabinCapabilities) = caps.ambient.keys.ifEmpty { setOf(VehicleGateway.DRIVER_SEAT) }

    private suspend fun <A, T> Set<A>.readEach(read: suspend (A) -> Result<T>): Map<A, T> =
        mapNotNull { area -> read(area).log("read").getOrNull()?.let { area to it } }.toMap()

    private fun Bounds<Int>.at(level: Float): Int = (min + level.coerceIn(0f, 1f) * (max - min)).roundToInt()

    private fun skipped(domain: Domain) = StepResult(domain, StepStatus.Skipped(text(R.string.step_unsupported)))

    private fun <T> Result<T>.toStep(domain: Domain, detail: (T) -> String) = StepResult(
        domain,
        fold(
            { StepStatus.Done(detail(it)) },
            {
                if (it is SeatNotMovingException) StepStatus.Skipped(text(R.string.step_seat_not_moving))
                else StepStatus.Failed(it.message ?: it.javaClass.simpleName)
            }
        )
    )

    private fun <T> Result<T>.log(what: String): Result<T> = onFailure { Log.w(TAG, "$what failed: ${it.message}") }

    private companion object {
        const val TAG = "Chabun"
        const val WAKE_STEPS = 5
        const val WAKE_STEP_MS = 1_000L
        const val WAKE_BREEZE_MS = 3_000L
        const val HVAC_POWER_SETTLE_MS = 300L
        const val VENT_FAN = 0.5f
    }
}
