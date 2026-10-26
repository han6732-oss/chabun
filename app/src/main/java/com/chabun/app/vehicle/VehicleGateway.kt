package com.chabun.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 연속 값 범위. SDK의 AreaBounds를 앱 쪽에서 다루기 쉽게 옮긴 것. */
data class Bounds<T : Comparable<T>>(val min: T, val max: T)

/** 이동 명령을 보냈지만 시트 위치가 바뀌지 않음(차종·시뮬레이터가 이동을 지원하지 않음). */
class SeatNotMovingException : IllegalStateException("시트 위치 변화 없음")

/**
 * Pleos Vehicle SDK를 코루틴으로 감싼 게이트웨이.
 * 모든 호출은 Result로 돌려주며, 차종이 지원하지 않거나 권한이 없으면 실패로 끝나 호출부가 건너뛸 수 있다.
 */
class VehicleGateway(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val seat get() = vehicle.getSeat()
    private val hvac get() = vehicle.getHVAC()
    private val light get() = vehicle.getLight()
    private val window get() = vehicle.getWindow()
    private val steering get() = vehicle.getSteeringWheel()

    fun initialize() = vehicle.initialize()

    fun release() = vehicle.release()

    // ---- Seat ----

    suspend fun seatBackrestBounds(): Result<Map<SeatArea, Bounds<Int>>> = call { ok, fail ->
        seat.checkSeatBackrestAnglePositionCapabilities({ _, map -> ok(map.toBounds()) }, fail)
    }

    suspend fun seatBackrest(area: SeatArea): Result<Int> =
        emulatorBackrest(area)?.let { Result.success(it) }
            ?: callNotNull { ok, fail -> seat.getCurrentSeatBackrestAnglePosition(area, ok, fail) }

    /** 에뮬레이터는 등받이 위치를 차량 HAL로 보내지 않아 Virtual Console에서 읽는다([EmulatorSeat]). 실차는 null. */
    private suspend fun emulatorBackrest(area: SeatArea): Int? = withContext(Dispatchers.IO) { EmulatorSeat.recline(area) }

    /**
     * 등받이는 위치를 직접 지정하는 API가 없어(move만 제공) 목표 위치까지 움직인 뒤 멈춘다.
     * 위치 값이 일정 시간 변하지 않으면 시뮬레이터/차종이 이동을 지원하지 않는 것으로 보고 실패 처리한다.
     */
    suspend fun moveSeatBackrestTo(area: SeatArea, target: Int, timeoutMs: Long = 15_000): Result<Int> = attempt {
        // 에뮬레이터는 위치 이벤트가 오지 않고(리스너 값도 늘 0) 콘솔 위치를 읽어야 한다. 실차는 위치 리스너를 쓴다.
        val emulated = emulatorBackrest(area)
        val start = emulated ?: seatBackrest(area).getOrThrow()
        if (start == target) return@attempt start
        val forward = target > start
        val latest = AtomicReference(start)
        val reached = CompletableDeferred<Int>()
        fun onPosition(pos: Int) {
            latest.set(pos)
            if ((forward && pos >= target) || (!forward && pos <= target)) reached.complete(pos)
        }
        val listener = object : SeatBackrestAnglePositionListener {
            override fun onSeatBackrestAnglePositionUpdated(pos: Int?) {
                Log.d(TAG, "seat backrest $start → $target: $pos")
                if (emulated == null) pos?.let(::onPosition)
            }

            override fun onFailed(e: Exception) {
                reached.completeExceptionally(e)
            }
        }
        coroutineScope {
            // 에뮬레이터: 콘솔 웹 화면처럼 하트비트를 계속 보내야 콘솔이 MOVE를 시트 모터로 옮긴다([EmulatorSeat.keepConsoleActive]).
            val heartbeat = emulated?.let {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        EmulatorSeat.keepConsoleActive()
                        delay(HEARTBEAT_MS)
                    }
                }
            }
            seat.registerSeatBackrestAnglePosition(area, listener)
            try {
                // 첫 하트비트가 콘솔을 깨우는 데 0.5초쯤 걸린다.
                if (heartbeat != null) delay(CONSOLE_WAKE_MS)
                call<Unit> { ok, fail ->
                    seat.moveSeatBackrestAngle(area, if (forward) MoveControl.FORWARD else MoveControl.BACKWARD, { _, _ -> ok(Unit) }, fail)
                }.getOrThrow()
                withTimeout(timeoutMs) {
                    val poll = emulated?.let {
                        launch {
                            while (isActive) {
                                delay(EMULATOR_POLL_MS)
                                emulatorBackrest(area)?.let(::onPosition)
                            }
                        }
                    }
                    try {
                        // 목표에 닿으면 바로 멈추고, 그사이 위치가 그대로면 이동을 지원하지 않는 것으로 보고 오래 기다리지 않는다.
                        val early = withTimeoutOrNull(STALL_CHECK_MS) { reached.await() }
                        if (early == null && latest.get() == start) throw SeatNotMovingException()
                        reached.await()
                    } finally {
                        poll?.cancel()
                    }
                }
            } finally {
                // 취소되더라도 시트가 계속 움직이지 않도록 STOP은 반드시 보낸다(콘솔 하트비트는 그 뒤에 멈춘다).
                withContext(NonCancellable) {
                    call<Unit> { ok, fail -> seat.moveSeatBackrestAngle(area, MoveControl.STOP, { _, _ -> ok(Unit) }, fail) }
                    seat.unregisterSeatBackrestAnglePosition(area, listener)
                }
                heartbeat?.cancel()
            }
        }
    }

    // ---- HVAC ----

    suspend fun fanSpeedBounds(): Result<Map<SeatArea, Bounds<Int>>> = call { ok, fail ->
        hvac.checkHvacFanSpeedCapabilities({ _, map -> ok(map.toBounds()) }, fail)
    }

    suspend fun fanSpeed(area: SeatArea): Result<Int> = callNotNull { ok, fail -> hvac.getHvacFanSpeed(area, ok, fail) }

    suspend fun setFanSpeed(area: SeatArea, value: Int): Result<Unit> = call { ok, fail ->
        hvac.setHvacFanSpeed(area, value, { _, _ -> ok(Unit) }, fail)
    }

    suspend fun temperatureBounds(): Result<Map<SeatArea, Bounds<Float>>> = call { ok, fail ->
        hvac.checkHvacTemperatureCapabilities({ _, map, _ -> ok(map.toBounds()) }, fail)
    }

    suspend fun temperature(area: SeatArea): Result<Float> = callNotNull { ok, fail ->
        hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail)
    }

    suspend fun setTemperature(area: SeatArea, celsius: Float): Result<Unit> = call { ok, fail ->
        hvac.setHvacTemperature(area, celsius, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail)
    }

    // HVAC 속성마다 지원 영역이 다르다(예: 전원·내기순환은 전 좌석 묶음, 풍량은 앞좌석 묶음, 온도는 좌석별).

    suspend fun hvacPowerAreas(): Result<List<SeatArea>> = call { ok, fail ->
        hvac.checkHvacPowerCapabilities({ _, areas -> ok(areas) }, fail)
    }

    suspend fun recirculationAreas(): Result<List<SeatArea>> = call { ok, fail ->
        hvac.checkHvacRecirculationCapabilities({ _, areas -> ok(areas) }, fail)
    }

    suspend fun hvacPower(area: SeatArea): Result<Boolean> = callNotNull { ok, fail -> hvac.getHvacPowerState(area, ok, fail) }

    suspend fun setHvacPower(area: SeatArea, on: Boolean): Result<Unit> = call { ok, fail ->
        hvac.setHvacPower(area, on, { _, _ -> ok(Unit) }, fail)
    }

    suspend fun recirculation(area: SeatArea): Result<Boolean> = callNotNull { ok, fail ->
        hvac.getHvacRecirculationState(area, ok, fail)
    }

    suspend fun setRecirculation(area: SeatArea, on: Boolean): Result<Unit> = call { ok, fail ->
        hvac.setHvacRecirculation(area, on, { _, _ -> ok(Unit) }, fail)
    }

    // ---- Ambient light ----

    suspend fun ambientBrightnessBounds(): Result<Map<SeatArea, Bounds<Int>>> = call { ok, fail ->
        light.checkAmbientLightBrightnessCapabilities({ _, map -> ok(map.toBounds()) }, fail)
    }

    suspend fun ambientSwitch(area: SeatArea): Result<LightSwitch> = callNotNull { ok, fail ->
        light.getAmbientLightSwitch(area, ok, fail)
    }

    suspend fun setAmbientSwitch(area: SeatArea, value: LightSwitch): Result<Unit> = call { ok, fail ->
        light.setAmbientLightSwitch(area, value, { _, _ -> ok(Unit) }, fail)
    }

    suspend fun ambientBrightness(area: SeatArea): Result<Int> = callNotNull { ok, fail ->
        light.getAmbientLightBrightness(area, ok, fail)
    }

    suspend fun setAmbientBrightness(area: SeatArea, value: Int): Result<Unit> = call { ok, fail ->
        light.setAmbientLightBrightness(area, value, { _, _ -> ok(Unit) }, fail)
    }

    suspend fun ambientColor(area: SeatArea): Result<Int> = callNotNull { ok, fail ->
        light.getAmbientLightBrightnessColor(area, ok, fail)
    }

    suspend fun setAmbientColor(area: SeatArea, rgb: Int): Result<Unit> = call { ok, fail ->
        light.setAmbientLightBrightnessColor(area, rgb, { _, _ -> ok(Unit) }, fail)
    }

    // ---- Window ----

    suspend fun windowBounds(): Result<Map<WindowArea, Bounds<Int>>> = call { ok, fail ->
        window.checkWindowPositionCapabilities({ _, map -> ok(map.toBounds()) }, fail)
    }

    suspend fun windowPosition(area: WindowArea): Result<Int> = callNotNull { ok, fail ->
        window.getCurrentWindowPosition(area, ok, fail)
    }

    suspend fun setWindowPosition(area: WindowArea, value: Int): Result<Unit> = call { ok, fail ->
        window.setWindowPosition(area, value, { _, _ -> ok(Unit) }, fail)
    }

    // ---- EV battery (전기차 배터리 하한 감시) ----

    suspend fun batteryPercent(): Result<Int> = callNotNull { ok, fail ->
        vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail)
    }

    // ---- Steering wheel heater ----

    suspend fun steeringHeat(): Result<Int> = callNotNull { ok, fail -> steering.getHvacSteeringWheelHeatState(ok, fail) }

    suspend fun setSteeringHeat(level: Int): Result<Unit> = call { ok, fail ->
        steering.setHvacSteeringWheelHeat(level, { ok(Unit) }, fail)
    }

    // ---- helpers ----

    private suspend fun <T> call(block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): Result<T> = attempt {
        withTimeout(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                block(
                    { if (cont.isActive) cont.resume(it) },
                    { if (cont.isActive) cont.resumeWithException(it) }
                )
            }
        }
    }

    /** runCatching과 같지만, 바깥 코루틴의 취소는 삼키지 않고 그대로 전파한다(응답 타임아웃은 실패로 돌려준다). */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: TimeoutCancellationException) {
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun <T : Any> callNotNull(block: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): Result<T> =
        call<T?>(block).mapCatching { it ?: error("값 없음") }

    private fun <A, T : Comparable<T>> Map<A, AreaBounds<T>>.toBounds(): Map<A, Bounds<T>> =
        mapValues { (_, b) -> Bounds(b.minValue, b.maxValue) }

    companion object {
        private const val TAG = "Chabun"
        private const val CALL_TIMEOUT_MS = 3_000L
        private const val STALL_CHECK_MS = 1_500L
        /** 에뮬레이터 시트 액추에이터 주기(100ms마다 5%)의 절반 */
        private const val EMULATOR_POLL_MS = 50L
        /** 콘솔 웹 화면처럼 1초마다 하트비트 */
        private const val HEARTBEAT_MS = 1_000L
        private const val CONSOLE_WAKE_MS = 700L

        val DRIVER_SEAT = SeatArea(SeatValue.ROW_1_LEFT)
        val FRONT_WINDOWS = listOf(WindowArea(WindowValue.ROW_1_LEFT), WindowArea(WindowValue.ROW_1_RIGHT))

        /** 지원 영역 중 운전석을 우선 고른다. */
        fun <T> Map<SeatArea, T>.driverOrFirst(): Pair<SeatArea, T>? =
            entries.firstOrNull { it.key == DRIVER_SEAT }?.toPair() ?: entries.firstOrNull()?.toPair()
    }
}
