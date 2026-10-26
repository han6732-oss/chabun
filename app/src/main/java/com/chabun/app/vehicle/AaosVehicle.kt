package com.chabun.app.vehicle

import android.car.Car
import android.car.FuelType
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.chabun.app.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 정차 조건. null은 아직 모르거나 차종이 제공하지 않는 값. */
data class ParkingState(val gear: Int? = null, val parkingBrake: Boolean? = null) {
    val inPark: Boolean get() = gear == VehicleGear.GEAR_PARK
}

/**
 * 충전 상태(AAOS EV_CHARGE_STATE·EV_CHARGE_TIME_REMAINING·EV_CHARGE_PERCENT_LIMIT).
 * 차종이 주지 않거나 CAR_ENERGY 권한이 없으면 null로 남는다.
 */
data class ChargeState(val state: Int? = null, val secondsLeft: Int? = null, val limitPercent: Int? = null) {
    val charging: Boolean get() = state == STATE_CHARGING

    companion object {
        // VHAL EvChargeState
        const val STATE_CHARGING = 1
        const val STATE_FULLY_CHARGED = 2
    }
}

/** 동력원. 공회전 엔진이 있는 차만 엔진음 마스킹·주기적 환기·최대 휴식 시간을 적용한다. */
enum class Powertrain(@StringRes val label: Int, val hasEngine: Boolean) {
    GASOLINE(R.string.powertrain_gasoline, true),
    DIESEL(R.string.powertrain_diesel, true),
    LPG(R.string.powertrain_lpg, true),
    HYBRID(R.string.powertrain_hybrid, true),
    ELECTRIC(R.string.powertrain_electric, false),
    HYDROGEN(R.string.powertrain_hydrogen, false),
    UNKNOWN(R.string.powertrain_unknown, false),
}

/**
 * 기어·주차 브레이크·연료 종류 같은 표준 차량 데이터는 Pleos 정책에 따라 Vehicle SDK가 아닌 AAOS Car API로 읽는다.
 */
class AaosVehicle(context: Context) {

    private val car: Car? by lazy { Car.createCar(context.applicationContext) }
    private val properties: CarPropertyManager? get() = car?.getCarManager(CarPropertyManager::class.java)

    /** INFO_FUEL_TYPE으로 동력원을 판단한다. 가솔린과 전기를 함께 쓰면 하이브리드. */
    fun powertrain(): Powertrain {
        val types = runCatching { properties?.getIntArrayProperty(VehiclePropertyIds.INFO_FUEL_TYPE, 0) }
            .onFailure { Log.w(TAG, "INFO_FUEL_TYPE unavailable", it) }
            .getOrNull()?.toSet() ?: return Powertrain.UNKNOWN
        val gasoline = types.any { it in GASOLINE_TYPES }
        return when {
            types.any { it in DIESEL_TYPES } -> Powertrain.DIESEL
            gasoline && FuelType.ELECTRIC in types -> Powertrain.HYBRID
            gasoline -> Powertrain.GASOLINE
            types.any { it in GAS_TYPES } -> Powertrain.LPG
            FuelType.HYDROGEN in types -> Powertrain.HYDROGEN
            FuelType.ELECTRIC in types -> Powertrain.ELECTRIC
            else -> Powertrain.UNKNOWN
        }.also { Log.i(TAG, "fuel types $types → $it") }
    }

    @Suppress("DEPRECATION") // registerCallback: 대체 API(subscribePropertyEvents)는 API 35부터라 API 34 기기에서 쓸 수 없다.
    fun parkingUpdates(): Flow<ParkingState> = callbackFlow {
        val properties = properties
        if (properties == null) {
            Log.w(TAG, "Car service unavailable")
            close()
            return@callbackFlow
        }
        var state = ParkingState()
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                state = when (value.propertyId) {
                    VehiclePropertyIds.GEAR_SELECTION -> state.copy(gear = value.value as? Int)
                    VehiclePropertyIds.PARKING_BRAKE_ON -> state.copy(parkingBrake = value.value as? Boolean)
                    else -> return
                }
                trySend(state)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "property error ${VehiclePropertyIds.toString(propertyId)}")
            }
        }
        for (id in listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PARKING_BRAKE_ON)) {
            runCatching { properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
                .onFailure { Log.w(TAG, "subscribe ${VehiclePropertyIds.toString(id)} failed", it) }
        }
        awaitClose { properties.unregisterCallback(callback) }
    }

    /** 충전 상태 변화를 흘려보낸다. 남은 시간은 연속 속성이라 1초 간격으로 받는다. */
    @Suppress("DEPRECATION") // registerCallback: 위와 같은 이유
    fun chargeUpdates(): Flow<ChargeState> = callbackFlow {
        val properties = properties
        if (properties == null) {
            close()
            return@callbackFlow
        }
        var state = ChargeState()
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                state = when (value.propertyId) {
                    VehiclePropertyIds.EV_CHARGE_STATE -> state.copy(state = value.value as? Int)
                    VehiclePropertyIds.EV_CHARGE_TIME_REMAINING -> state.copy(secondsLeft = value.value as? Int)
                    VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT -> state.copy(limitPercent = (value.value as? Float)?.toInt())
                    else -> return
                }
                trySend(state)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "charge property error ${VehiclePropertyIds.toString(propertyId)}")
            }
        }
        listOf(
            VehiclePropertyIds.EV_CHARGE_STATE to CarPropertyManager.SENSOR_RATE_ONCHANGE,
            VehiclePropertyIds.EV_CHARGE_TIME_REMAINING to CarPropertyManager.SENSOR_RATE_NORMAL,
            VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT to CarPropertyManager.SENSOR_RATE_ONCHANGE,
        ).forEach { (id, rate) ->
            runCatching { properties.registerCallback(callback, id, rate) }
                .onFailure { Log.w(TAG, "subscribe ${VehiclePropertyIds.toString(id)} failed: ${it.message}") }
        }
        awaitClose { properties.unregisterCallback(callback) }
    }

    fun release() {
        car?.disconnect()
    }

    private companion object {
        const val TAG = "Chabun"
        val GASOLINE_TYPES = setOf(FuelType.UNLEADED, FuelType.LEADED, FuelType.E85)
        val DIESEL_TYPES = setOf(FuelType.DIESEL_1, FuelType.DIESEL_2, FuelType.BIODIESEL)
        val GAS_TYPES = setOf(FuelType.LPG, FuelType.CNG, FuelType.LNG)
    }
}
