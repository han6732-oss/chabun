package com.chabun.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/** 안내 중인 경로 요약. 거리는 미터, 시간은 초. */
data class RouteSummary(val destination: String, val remainMeters: Int, val remainSeconds: Int)

/**
 * NaviHelper SDK로 Navigation 앱의 안내 상태를 읽는다.
 * 조회 API는 값을 바로 돌려주지 않고 리스너 콜백으로 답하므로, 요청마다 콜백을 기다리는 형태로 감싼다.
 */
class RouteGuide(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    @Volatile private var ready = false
    private val routeState = AtomicReference<CompletableDeferred<RouteStateInfo>?>()
    private val destination = AtomicReference<CompletableDeferred<DestinationInfo>?>()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            routeState.getAndSet(null)?.complete(routeStateInfo)
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destination.getAndSet(null)?.complete(destinationInfo)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    fun initialize() {
        navi.initialize(
            {
                navi.addListener(listener)
                ready = true
            },
            { Log.w(TAG, "NaviHelper initialize failed", it) },
        )
    }

    /** 안내 중인 경로가 있으면 남은 거리·시간을, 없거나 응답이 없으면 null을 돌려준다. */
    suspend fun currentRoute(): RouteSummary? {
        if (!ready) return null
        val state = ask(routeState) { navi.getRouteStateInfo() }
        Log.i(TAG, "route state: routing=${state?.isRouting} mode=${state?.naviMode}")
        // isRouting은 '경로 탐색 중'이라 안내 여부는 naviMode로 본다(SAFE_GUIDING은 목적지 없는 안전운전 안내).
        if (state?.naviMode !in GUIDING) return null
        val info = ask(destination) { navi.getDestinationInfo() } ?: return null
        Log.i(TAG, "destination: ${info.name} ${info.remainDistance} ${info.remainTime}")
        if (info.name.isBlank() || info.remainDistance <= 0) return null
        return RouteSummary(info.name, info.remainDistance, info.remainTime)
    }

    /** 시연·테스트용: 목적지로 안내를 시작한다. */
    fun requestRoute(route: RouteInfo) {
        if (ready) navi.requestRoute(route)
    }

    fun release() {
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
    }

    private suspend fun <T> ask(slot: AtomicReference<CompletableDeferred<T>?>, request: () -> Unit): T? {
        val answer = CompletableDeferred<T>()
        slot.set(answer)
        runCatching(request).onFailure {
            Log.w(TAG, "navi request failed", it)
            return null
        }
        return withTimeoutOrNull(ANSWER_TIMEOUT_MS) { answer.await() }.also { slot.compareAndSet(answer, null) }
    }

    private companion object {
        const val TAG = "Chabun"
        const val ANSWER_TIMEOUT_MS = 3_000L
        val GUIDING = setOf(NaviMode.ROUTE_GUIDING, NaviMode.SIMULATION_GUIDING)
    }
}
