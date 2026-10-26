package com.chabun.app

import android.app.Application
import android.car.Car
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.pleos.playground.navi.data.RouteInfo
import com.chabun.app.i18n.AppLanguage
import com.chabun.app.i18n.LocalizedText
import com.chabun.app.i18n.localized
import com.chabun.app.navi.RouteGuide
import com.chabun.app.navi.RouteSpeech
import com.chabun.app.safety.ChargeWake
import com.chabun.app.safety.ChargeWatcher
import com.chabun.app.safety.SafetyPolicy
import com.chabun.app.safety.SafetyStatus
import com.chabun.app.safety.idleProfile
import com.chabun.app.scene.CabinSnapshot
import com.chabun.app.scene.ChabunScene
import com.chabun.app.scene.Domain
import com.chabun.app.scene.SceneEngine
import com.chabun.app.scene.SceneStore
import com.chabun.app.scene.SnapshotStore
import com.chabun.app.scene.SoundTheme
import com.chabun.app.scene.StepResult
import com.chabun.app.scene.StepStatus
import com.chabun.app.scene.SteeringPrewarm
import com.chabun.app.scene.orderScenes
import com.chabun.app.scene.toggledFavorites
import com.chabun.app.sound.NoisePlayer
import com.chabun.app.vehicle.AaosVehicle
import com.chabun.app.vehicle.ChargeState
import com.chabun.app.vehicle.ParkingState
import com.chabun.app.vehicle.Powertrain
import com.chabun.app.vehicle.VehicleGateway
import com.chabun.app.voice.ListenState
import com.chabun.app.voice.VoiceAssistant
import com.chabun.app.voice.VoiceCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

enum class Phase { ENTERING, RESTING, WAKING, RESTORING, DONE }

data class Session(
    val scene: ChabunScene,
    val phase: Phase,
    val steps: Map<Domain, StepStatus> = emptyMap(),
    val remainingSeconds: Int? = null,
    val safety: SafetyStatus? = null,
    /** 깨운 뒤 남은 경로 한 줄 요약(안내 중인 경로가 있을 때) */
    val route: String? = null,
)

/** 편집 중인 장면(isNew면 저장 시 목록에 추가). trial이 켜져 있으면 시트·조명을 바꿀 때마다 차에 바로 적용한다(그 결과가 trialSteps). */
data class SceneDraft(
    val scene: ChabunScene,
    val isNew: Boolean,
    val trial: Boolean = false,
    val trialSteps: Map<Domain, StepStatus> = emptyMap(),
)

/**
 * 앱 설정. powertrainOverride·quickCycles는 시연·테스트용으로, 전기차 에뮬레이터에서도 엔진차 기능을 보여 줄 수 있게 한다.
 */
data class AppSettings(
    val powertrainOverride: Powertrain? = null,
    val quickCycles: Boolean = false,
    /** 진입·기상·안전 해제를 음성으로 안내한다. */
    val voiceGuide: Boolean = true,
    /** 깨운 뒤 내비에 안내 중인 경로가 있으면 남은 거리·시간을 알려 준다. */
    val routeBriefing: Boolean = true,
    /** false(기본) = 늘 부드러운 다크, true = 시스템 주간/야간을 따른다. */
    val followSystemTheme: Boolean = false,
    /** 화면·음성 안내 언어(음성 명령은 한국어) */
    val language: AppLanguage = AppLanguage.device(),
)

data class ChabunUiState(
    val scenes: List<ChabunScene> = emptyList(),
    /** 브랜드관 장면(화면 언어로 바꾼 것) */
    val brandScenes: List<ChabunScene> = emptyList(),
    /** 즐겨찾기한 장면 id(최근에 별을 누른 것부터). 홈에서 맨 앞에 놓인다. */
    val favorites: List<String> = emptyList(),
    val vehicleReady: Boolean = false,
    val parking: ParkingState = ParkingState(),
    val detectedPowertrain: Powertrain = Powertrain.UNKNOWN,
    val settings: AppSettings = AppSettings(),
    val showSettings: Boolean = false,
    val session: Session? = null,
    val draft: SceneDraft? = null,
    val previewing: SoundTheme? = null,
    val listen: ListenState = ListenState.Idle,
    /** 화면이 CAR_ENERGY 권한 요청 창을 띄워야 함 */
    val requestEnergyPermission: Boolean = false,
    val notice: String? = null,
) {
    val powertrain: Powertrain get() = settings.powertrainOverride ?: detectedPowertrain

    val policy: SafetyPolicy get() = if (settings.quickCycles) SafetyPolicy.quick else SafetyPolicy.standard

    /**
     * P단이면 변속기가 잠겨 정차가 보장되므로 P단을 시작 조건으로 한다(기어를 못 읽는 차종은 막지 않음).
     * 주차 브레이크는 P단에서도 풀려 있는 차가 많아 조건에서 빼고 안내만 한다.
     */
    val canStart: Boolean
        get() = vehicleReady && (parking.gear == null || parking.inPark)
}

class ChabunViewModel(app: Application) : AndroidViewModel(app) {

    /** 알림·음성 안내·차량 상태 문구를 화면 언어로 읽는다(언어를 바꾸면 [applyLanguage]가 갈아 끼운다). */
    private val text = LocalizedText(app)
    private val vehicle = VehicleGateway(app)
    private val aaos = AaosVehicle(app)
    private val engine = SceneEngine(vehicle, text)
    private val noise = NoisePlayer(app)
    private val voice = VoiceAssistant(app, text) { heard -> viewModelScope.launch { onVoiceText(heard) } }
    private val routeGuide = RouteGuide(app)
    private val store = SceneStore(app)
    private val usage = app.getSharedPreferences("scene_usage", Context.MODE_PRIVATE)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ChabunUiState(settings = loadSettings(), favorites = loadFavorites()))
    val state: StateFlow<ChabunUiState> = _state.asStateFlow()

    private val snapshots = SnapshotStore(app)

    /** 모든 언어의 기본 장면 이름·소개(사용자가 고치지 않은 이름을 알아보는 데 쓴다) */
    private val builtInDefaults by lazy {
        ChabunScene.defaultTexts(AppLanguage.entries.map { LocalizedText(app.localized(it)) }) +
            (ChabunScene.concepts + ChabunScene.brandScenes).flatMap { listOf(it.name, it.tagline) }
    }

    /** 진입 전 상태. 바뀔 때마다 기기에도 남겨 두어, 앱이 강제 종료돼도 다음 실행 때 되돌릴 수 있게 한다. */
    private var snapshot: CabinSnapshot? = null
        set(value) {
            field = value
            if (value == null) snapshots.clear() else snapshots.save(value)
        }
    private var sessionJob: Job? = null
    /** 이번 세션에 핸들 열선 예열을 이미 했는지 */
    private var steeringWarmed = false
    /** 편집하며 바로 느껴보기 전의 시트·조명. 끄거나 편집을 닫으면 이 값으로 되돌린다(앱이 꺼져도 되돌리게 기기에도 남긴다). */
    private var trialBackup: CabinSnapshot? = null
    /** 마지막으로 차에 적용한 편집 내용 */
    private var trialApplied: ChabunScene? = null
    private var trialJob: Job? = null
    private var trialRestore: Job? = null
    private var pendingChargeScene: ChabunScene? = null
    private var energyPermissionAsked = false

    init {
        applyLanguage(_state.value.settings.language)
        publishScenes(store.load())
        // Car 서비스 연결과 속성 조회는 동기 호출이라 메인 스레드를 막지 않게 IO에서 한다.
        viewModelScope.launch(Dispatchers.IO) {
            val powertrain = aaos.powertrain()
            _state.update { it.copy(detectedPowertrain = powertrain) }
        }
        viewModelScope.launch { aaos.parkingUpdates().flowOn(Dispatchers.IO).collect(::onParking) }
        voice.initialize()
        routeGuide.initialize()
        viewModelScope.launch {
            voice.state.collect { listen ->
                // 듣는 동안은 백색소음을 낮춰 차량 마이크에 섞이지 않게 한다.
                noise.duck(listen is ListenState.Listening)
                _state.update { it.copy(listen = listen) }
            }
        }
        runCatching { vehicle.initialize() }
            .onSuccess {
                _state.update { it.copy(vehicleReady = true) }
                viewModelScope.launch {
                    restoreOrphanedSession()
                    engine.capabilities()
                }
            }
            .onFailure { e ->
                Log.w(TAG, "Vehicle initialize failed", e)
                _state.update { it.copy(notice = text(R.string.vehicle_connect_failed, e.message.orEmpty())) }
            }
    }

    // ---- 차분 세션 ----

    /** minutes를 주면 이번 휴식만 그 시간으로 깨운다(음성으로 "이십 분만 쉴래"처럼 말한 경우). */
    fun start(chosen: ChabunScene, minutes: Int? = null) {
        val current = _state.value
        if (current.session != null || sessionJob?.isActive == true) return
        if (!current.canStart) {
            notify(text(R.string.start_park_only))
            return
        }
        // 음성으로 시간을 말하면 이번 휴식만 그 시간으로(충전 대기도 시간 기상으로 바뀐다).
        val scene = minutes?.let { chosen.copy(durationMinutes = it, wakeOnCharge = false) } ?: chosen
        val powertrain = current.powertrain
        val policy = current.policy
        if (scene.wakeOnCharge && powertrain.hasEngine) {
            notify(text(R.string.charge_ev_only))
            return
        }
        // 충전 상태는 dangerous 권한(CAR_ENERGY)이라 충전 대기를 처음 시작할 때 묻는다. 거절해도 SOC로 판단해 진행한다.
        if (scene.wakeOnCharge && !hasEnergyPermission() && !energyPermissionAsked) {
            pendingChargeScene = scene
            _state.update { it.copy(requestEnergyPermission = true) }
            return
        }
        stopPreview()
        // 편집하며 바로 느껴보던 중이면(음성으로 시작) 먼저 원래대로 되돌린 뒤 장면에 들어간다.
        setTrial(false)
        steeringWarmed = false
        sessionJob = viewModelScope.launch {
            trialRestore?.join()
            // 전기차는 배터리가 이미 하한 아래면 시작하지 않는다(충전 대기는 충전하려는 상황이라 예외).
            val battery = if (powertrain.hasEngine) null else vehicle.batteryPercent().getOrNull()
            if (!scene.wakeOnCharge && battery != null && battery < policy.batteryFloorPercent) {
                text(R.string.battery_too_low, battery, policy.batteryFloorPercent).let { notify(it, spoken = it) }
                return@launch
            }
            val chargeTarget = scene.chargeTargetPercent?.takeIf { scene.wakeOnCharge }
            if (chargeTarget != null && battery != null && battery >= chargeTarget) {
                text(R.string.battery_already_target, battery, chargeTarget).let { notify(it, spoken = it) }
                return@launch
            }
            recordUsage(scene)
            val safety = SafetyStatus(
                powertrain = powertrain,
                batteryPercent = battery,
                batteryFloorPercent = if (powertrain.hasEngine) null else policy.batteryFloorPercent,
            )
            _state.update { it.copy(session = Session(scene, Phase.ENTERING, safety = safety), notice = null) }

            engine.enter(scene, ::onStep) { snapshot = it }
            val masking = if (powertrain.hasEngine && scene.masksEngine) powertrain.idleProfile() else null
            noise.play(scene.sound, masking)
            updateSession {
                it.copy(
                    phase = Phase.RESTING,
                    remainingSeconds = scene.durationMinutes?.times(60),
                    safety = it.safety?.copy(masking = masking),
                )
            }
            announce(
                when {
                    // 브랜드관 장면은 브랜드의 목소리로 인사한다.
                    scene.brand != null -> scene.brand.welcome +
                        (scene.durationMinutes?.let { text(R.string.announce_brand_minutes, it) } ?: "")
                    scene.wakeOnCharge -> scene.chargeTargetPercent
                        ?.let { text(R.string.announce_start_charge_target, scene.name, it) }
                        ?: text(R.string.announce_start_charge, scene.name)
                    scene.durationMinutes != null -> text(R.string.announce_start_timed, scene.name, scene.durationMinutes)
                    else -> text(R.string.announce_start_manual, scene.name)
                }
            )

            val guards = launch {
                coroutineScope {
                    when {
                        powertrain.hasEngine -> {
                            launch { ventilationLoop(policy) }
                            launch { restLimit(policy) }
                        }
                        scene.wakeOnCharge -> launch { chargeWatch(policy, scene) }
                        else -> launch { batteryWatch(policy) }
                    }
                }
            }
            val total = scene.durationMinutes?.times(60) ?: return@launch
            for (left in total - 1 downTo 0) {
                delay(1_000)
                updateSession { it.copy(remainingSeconds = left) }
                if (left == SteeringPrewarm.timerTick(total)) launch { prewarmSteering(scene) }
            }
            guards.cancelAndJoin()
            runWake(null)
        }
    }

    /** 음성 "깨워줘"·안전 감시·충전 감시가 기상 연출과 함께 깨울 때 */
    fun wakeNow(reason: String? = null) {
        if (_state.value.session?.phase != Phase.RESTING) return
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch { runWake(reason) }
    }

    /** 버튼 하나로 바로 끝내기: 기상 연출 없이 즉시 원래 상태로 */
    fun endNow(reason: String? = null) {
        val session = _state.value.session ?: return
        if (session.phase == Phase.RESTORING || session.phase == Phase.DONE) return
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            noise.stop()
            // 안전 감시로 해제될 때만 이유를 말로 알린다(직접 끝낼 때는 조용히).
            reason?.let(::announce)
            updateSession { it.copy(phase = Phase.RESTORING) }
            snapshot?.let { engine.restore(it, ::onStep) }
            // 직접 끝냈다면 다시 출발할 참이니 남은 경로를 알려 준다(안전 해제 때는 해제 이유만).
            val route = if (reason == null) briefRoute() else null
            finish(reason ?: text(R.string.end_restored), route)
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private suspend fun runWake(reason: String?) {
        updateSession { it.copy(phase = Phase.WAKING, remainingSeconds = 0) }
        announce(reason ?: _state.value.session?.scene?.brand?.wakeUp ?: text(R.string.wake_announce))
        noise.fadeOut()
        snapshot?.let { engine.wake(it, ::onStep) }
        noise.stop()
        finish(reason ?: text(R.string.wake_done), briefRoute())
    }

    /** 내비에 안내 중인 경로가 있으면 남은 거리·시간·도착 시각을 화면과 말로 알려 준다. */
    private suspend fun briefRoute(): String? {
        if (!_state.value.settings.routeBriefing) return null
        val route = routeGuide.currentRoute() ?: return null
        val now = LocalTime.now()
        val language = _state.value.settings.language
        val summary = RouteSpeech.summary(route, now, language)
        updateSession { it.copy(route = summary) }
        announce(RouteSpeech.announce(route, now, language))
        return summary
    }

    private suspend fun finish(message: String, route: String? = null) {
        snapshot = null
        updateSession { it.copy(phase = Phase.DONE) }
        // 경로 요약이 있으면 읽을 시간을 조금 더 준다.
        delay(if (route != null) ROUTE_HOLD_MS else DONE_HOLD_MS)
        _state.update { it.copy(session = null, notice = route?.let { r -> text(R.string.notice_with_route, message, r) } ?: message) }
    }

    /** 디버그 빌드 시연용: 강남역 근처에서 42dot까지 안내를 시작한다(에뮬레이터 위치를 먼저 한국으로 옮긴다). */
    fun startSampleRoute() {
        routeGuide.requestRoute(
            RouteInfo(
                latitude = 37.48544722,
                longitude = 127.03636666,
                displayLatitude = null,
                displayLongitude = null,
                poiName = "42dot",
                poiId = "36khp37hh8gqcm5nqhe5",
                poiSubId = "0",
                address = "서울시 강남구 남부순환로 2621",
            )
        )
    }

    /** 지난 세션이 끝나지 않은 채 앱이 종료됐다면(강제 종료·재설치 등) 차를 원래 상태로 되돌린다. */
    private suspend fun restoreOrphanedSession() {
        val orphan = snapshots.load() ?: return
        // 서비스 연결 직후의 호출은 응답이 없을 수 있어 잠시 기다린다.
        delay(SERVICE_SETTLE_MS)
        Log.i(TAG, "restoring orphaned session")
        engine.restore(orphan) { Log.i(TAG, "orphan restore ${it.domain}: ${it.status}") }
        snapshots.clear()
        notify(text(R.string.orphan_restored))
    }

    // ---- 안전 감시 ----

    /** 엔진차: 창문을 닫은 채 공회전하지 않도록 주기적으로 외기를 들이고 창을 살짝 연다. */
    private suspend fun ventilationLoop(policy: SafetyPolicy) {
        while (true) {
            for (left in (policy.ventilationEveryMs / 1_000).toInt() downTo 1) {
                updateSafety { it.copy(nextVentilationSeconds = left) }
                delay(1_000)
            }
            updateSafety { it.copy(nextVentilationSeconds = null, ventilating = text(R.string.vent_preparing)) }
            engine.ventilate(
                snapshot ?: CabinSnapshot(),
                policy.ventilationWindowPercent,
                policy.ventilationForMs,
                // 환기는 열선을 건드리지 않으니, 그사이 켠 열선의 백업을 덮어쓰지 않는다.
                onSnapshot = { snapshot = it.copy(steeringHeat = snapshot?.steeringHeat) },
                onActive = { detail -> updateSafety { it.copy(ventilating = detail) } },
            )
            updateSafety { it.copy(ventilating = null, ventilationCount = it.ventilationCount + 1) }
        }
    }

    /**
     * 깨우기 1분 전: 핸들 열선을 켠다(세션마다 한 번). 쉬는 내내 켤 필요 없이 일어나 핸들을 잡을 때 따뜻하면 된다.
     * 켜기 전 값은 백업해 끝날 때 되돌린다.
     */
    private suspend fun prewarmSteering(scene: ChabunScene) {
        val level = scene.steeringHeat ?: return
        if (level <= 0 || steeringWarmed) return
        steeringWarmed = true
        engine.prewarm(level, ::onStep) { original ->
            snapshot = (snapshot ?: CabinSnapshot()).copy(steeringHeat = original)
        }
    }

    /** 엔진차: 공회전 상태로 너무 오래 쉬지 않도록 최대 휴식 시간이 지나면 깨운다. */
    private suspend fun restLimit(policy: SafetyPolicy) {
        for (left in (policy.engineMaxRestMs / 1_000).toInt() downTo 1) {
            updateSafety { it.copy(restLimitSeconds = left) }
            delay(1_000)
        }
        wakeNow(text(R.string.rest_limit_reached, (policy.engineMaxRestMs / 60_000).toInt()))
    }

    /** 전기차 충전 대기: 충전이 끝날 무렵 깨운다(판단은 [ChargeWatcher]). */
    private suspend fun chargeWatch(policy: SafetyPolicy, scene: ChabunScene) = coroutineScope {
        var charge = ChargeState()
        launch { aaos.chargeUpdates().flowOn(Dispatchers.IO).collect { charge = it } }
        val watcher = ChargeWatcher(policy, scene.chargeTargetPercent)
        while (true) {
            val percent = vehicle.batteryPercent().getOrNull()
            val result = watcher.update(SystemClock.elapsedRealtime(), percent, charge)
            Log.d(TAG, "charge $charge soc=$percent → ${result.status}")
            updateSafety { it.copy(batteryPercent = percent, charge = result.status) }
            // 깨울 기준보다 1분 앞서 핸들 열선을 켠다(남은 시간을 모르면 깨울 때 켜지 않는다).
            if (SteeringPrewarm.dueForCharge(result.status.secondsLeft, policy.chargeWakeLeadMs)) prewarmSteering(scene)
            result.wake?.let { wake ->
                wakeNow(wake.sentence())
                return@coroutineScope
            }
            delay(policy.batteryCheckEveryMs)
        }
    }

    private fun ChargeWake.sentence(): String = when (this) {
        ChargeWake.Ended -> text(R.string.charge_ended)
        ChargeWake.EndingSoon -> text(R.string.charge_ending_soon)
        is ChargeWake.NearTarget -> text(R.string.charge_near_target, target)
        is ChargeWake.TargetReached -> text(R.string.charge_target_reached, target)
        is ChargeWake.BatteryLow -> text(R.string.battery_low_woke, floor)
    }

    private fun hasEnergyPermission() =
        ContextCompat.checkSelfPermission(getApplication(), Car.PERMISSION_ENERGY) == PackageManager.PERMISSION_GRANTED

    /** CAR_ENERGY 권한 요청 결과. 허락하지 않아도 SOC 변화로 충전 끝을 판단해 시작한다. */
    fun onEnergyPermissionResult(granted: Boolean) {
        energyPermissionAsked = true
        _state.update { it.copy(requestEnergyPermission = false) }
        if (!granted) notify(text(R.string.energy_denied))
        pendingChargeScene?.let(::start)
        pendingChargeScene = null
    }

    /** 전기차: 배터리가 하한 아래로 내려가면 깨운다. */
    private suspend fun batteryWatch(policy: SafetyPolicy) {
        while (true) {
            val percent = vehicle.batteryPercent().getOrNull()
            updateSafety { it.copy(batteryPercent = percent) }
            if (percent != null && percent < policy.batteryFloorPercent) {
                wakeNow(text(R.string.battery_low_woke, policy.batteryFloorPercent))
                return
            }
            delay(policy.batteryCheckEveryMs)
        }
    }

    private fun onParking(parking: ParkingState) {
        Log.i(TAG, "parking: $parking")
        _state.update { it.copy(parking = parking) }
        val leftPark = parking.gear != null && !parking.inPark
        if (leftPark && _state.value.session != null) endNow(text(R.string.gear_left_park))
        if (leftPark && _state.value.draft?.trial == true) {
            setTrial(false)
            notify(text(R.string.gear_left_trial))
        }
    }

    private fun onStep(step: StepResult) {
        Log.i(TAG, "step ${step.domain}: ${step.status}")
        updateSession { it.copy(steps = it.steps + (step.domain to step.status)) }
    }

    private fun updateSession(transform: (Session) -> Session) =
        _state.update { s -> s.copy(session = s.session?.let(transform)) }

    private fun updateSafety(transform: (SafetyStatus) -> SafetyStatus) =
        updateSession { it.copy(safety = it.safety?.let(transform)) }

    // ---- 음성 ----

    /** 화면의 마이크 버튼: 듣는 중이면 멈추고, 아니면 차량 마이크로 듣기 시작한다. */
    fun toggleListening() {
        if (_state.value.listen is ListenState.Listening) return voice.stopListening()
        stopPreview()
        voice.listen()
    }

    fun clearVoiceFailure() = voice.clearFailure()

    /** 인식된 문장을 명령으로 해석해 실행한다. */
    fun onVoiceText(heard: String) {
        val current = _state.value
        when (val command = VoiceCommand.parse(heard, current.scenes + current.brandScenes)) {
            is VoiceCommand.Start -> when {
                current.session != null -> notify(text(R.string.voice_already_resting, heard))
                else -> {
                    notify(text(R.string.voice_heard, heard))
                    start(command.scene ?: current.scenes.first(), command.minutes)
                }
            }
            VoiceCommand.Wake ->
                if (current.session?.phase == Phase.RESTING) wakeNow() else notify(text(R.string.voice_not_resting, heard))
            VoiceCommand.End ->
                if (current.session != null) endNow() else notify(text(R.string.voice_not_resting, heard))
            is VoiceCommand.Unknown -> text(R.string.voice_unknown_hint).let { hint -> notify(text(R.string.voice_unknown, heard, hint), spoken = hint) }
        }
    }

    /** 녹음된 명령 음성(16kHz PCM)을 마이크 대신 넣어 인식한다. 디버그 빌드의 시연·테스트용. */
    fun recognizeSample(asset: String) {
        val pcm = runCatching { getApplication<Application>().assets.open(asset).use { it.readBytes() } }
            .onFailure { Log.w(TAG, "voice sample $asset missing") }
            .getOrNull() ?: return
        voice.recognize(pcm)
    }

    /** 음성 안내. 차량 음성 엔진이 말하지 못하는 화면 언어(일본어·중국어)면 화면 알림만 남긴다. */
    private fun announce(message: String) {
        val settings = _state.value.settings
        if (settings.voiceGuide && settings.language.speaks) voice.speak(message)
    }

    /** 화면 알림. [spoken]을 주면 그 문장을 음성으로도 안내한다. */
    private fun notify(message: String, spoken: String? = null) {
        _state.update { it.copy(notice = message) }
        spoken?.let(::announce)
    }

    // ---- 설정 ----

    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val settings = transform(_state.value.settings)
        prefs.edit()
            .putString(KEY_POWERTRAIN, settings.powertrainOverride?.name)
            .putBoolean(KEY_QUICK, settings.quickCycles)
            .putBoolean(KEY_VOICE_GUIDE, settings.voiceGuide)
            .putBoolean(KEY_ROUTE_BRIEFING, settings.routeBriefing)
            .putBoolean(KEY_FOLLOW_SYSTEM_THEME, settings.followSystemTheme)
            .putString(KEY_LANGUAGE, settings.language.tag)
            .apply()
        val languageChanged = settings.language != _state.value.settings.language
        _state.update { it.copy(settings = settings) }
        if (languageChanged) {
            applyLanguage(settings.language)
            publishScenes(_state.value.scenes)
        }
    }

    private fun loadSettings() = AppSettings(
        powertrainOverride = prefs.getString(KEY_POWERTRAIN, null)?.let { name -> Powertrain.entries.firstOrNull { it.name == name } },
        quickCycles = prefs.getBoolean(KEY_QUICK, false),
        voiceGuide = prefs.getBoolean(KEY_VOICE_GUIDE, true),
        routeBriefing = prefs.getBoolean(KEY_ROUTE_BRIEFING, true),
        followSystemTheme = prefs.getBoolean(KEY_FOLLOW_SYSTEM_THEME, false),
        language = AppLanguage.of(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.device(),
    )

    /** 화면 언어를 바꾼다: 알림·차량 상태 문구와 음성 안내 언어가 함께 바뀐다(음성 명령 인식은 한국어). */
    private fun applyLanguage(language: AppLanguage) {
        text.use(getApplication<Application>().localized(language))
        if (language.speaks) voice.setSpeechLocale(language.locale)
    }

    // ---- 나만의 장면 편집 ----

    fun edit(scene: ChabunScene) = _state.update { it.copy(draft = SceneDraft(scene, isNew = false)) }

    fun createScene() = _state.update {
        it.copy(draft = SceneDraft(ChabunScene.blank().copy(name = text(R.string.blank_name), tagline = text(R.string.blank_tagline)), isNew = true))
    }

    fun updateDraft(transform: (ChabunScene) -> ChabunScene) {
        _state.update { s -> s.copy(draft = s.draft?.let { it.copy(scene = transform(it.scene)) }) }
        if (_state.value.draft?.trial == true) applyTrial()
    }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        val scene = draft.scene.copy(name = draft.scene.name.trim().ifEmpty { text(R.string.unnamed_scene) })
        val scenes = store.load()
        val updated = if (draft.isNew) scenes + scene else scenes.map { if (it.id == scene.id) scene else it }
        store.save(updated)
        publishScenes(updated)
        closeEditor(text(R.string.scene_saved, scene.name))
    }

    fun deleteDraft() {
        val draft = _state.value.draft ?: return
        if (draft.scene.builtIn || draft.isNew) return closeEditor()
        val updated = store.load().filterNot { it.id == draft.scene.id }
        store.save(updated)
        if (draft.scene.id in _state.value.favorites) toggleFavorite(draft.scene)
        publishScenes(updated)
        closeEditor(text(R.string.scene_deleted, draft.scene.name))
    }

    /** 기본 컨셉을 편집 전 값으로 되돌린다(저장은 따로). */
    fun resetDraft() {
        val original = ChabunScene.concepts.firstOrNull { it.id == _state.value.draft?.scene?.id } ?: return
        updateDraft { ChabunScene.localize(original, text, builtInDefaults) }
    }

    fun closeEditor(notice: String? = null) {
        stopPreview()
        endTrial()
        _state.update { it.copy(draft = null, notice = notice) }
    }

    /** 편집하며 차에서 바로 느껴보기를 켜고 끈다. 끄면 시트·조명을 켜기 전 상태로 되돌린다. */
    fun setTrial(on: Boolean) {
        val draft = _state.value.draft ?: return
        if (on == draft.trial) return
        if (on && !_state.value.canStart) return notify(text(R.string.trial_park_only))
        _state.update { it.copy(draft = it.draft?.copy(trial = on, trialSteps = emptyMap())) }
        if (on) applyTrial(settle = false) else endTrial()
    }

    private fun applyTrial(settle: Boolean = true) {
        trialJob?.cancel()
        trialJob = viewModelScope.launch {
            trialRestore?.join()
            // 슬라이더를 미는 동안 매번 움직이지 않도록 손을 멈추면 적용한다(움직이던 시트는 취소되며 멈춘다).
            if (settle) delay(TRIAL_SETTLE_MS)
            val scene = _state.value.draft?.takeIf { it.trial }?.scene ?: return@launch
            val backup = trialBackup ?: engine.trialBackup().also {
                trialBackup = it
                snapshots.save(it)
            }
            engine.trial(scene, trialApplied, backup) { step ->
                Log.i(TAG, "trial ${step.domain}: ${step.status}")
                _state.update { s -> s.copy(draft = s.draft?.let { it.copy(trialSteps = it.trialSteps + (step.domain to step.status)) }) }
            }
            trialApplied = scene
        }
    }

    private fun endTrial() {
        val pending = trialJob
        trialJob = null
        pending?.cancel()
        val backup = trialBackup ?: return
        trialBackup = null
        trialApplied = null
        trialRestore = viewModelScope.launch {
            pending?.join()
            engine.restore(backup) { Log.i(TAG, "trial restore ${it.domain}: ${it.status}") }
            snapshots.clear()
        }
    }

    fun togglePreview(theme: SoundTheme?) {
        if (theme == null || _state.value.previewing == theme) return stopPreview()
        noise.play(theme)
        _state.update { it.copy(previewing = theme) }
    }

    private fun stopPreview() {
        if (_state.value.previewing == null) return
        noise.stop()
        _state.update { it.copy(previewing = null) }
    }

    // 자주 쓰는 장면이 앞에 오도록 조용히 정렬한다(재촉하지 않는 추천).
    private fun recordUsage(scene: ChabunScene) {
        usage.edit().putInt(scene.id, usage.getInt(scene.id, 0) + 1).apply()
        publishScenes(_state.value.scenes)
    }

    // 기본·브랜드 장면 이름은 화면 언어로 바꾸고(사용자가 고친 이름은 그대로), 즐겨찾기·자주 쓰는 순서로 놓는다.
    private fun publishScenes(scenes: List<ChabunScene>) {
        val localized = scenes.map { ChabunScene.localize(it, text, builtInDefaults) }
        val brands = ChabunScene.brandScenes.map { ChabunScene.localize(it, text, builtInDefaults) }
        _state.update { it.copy(scenes = orderScenes(localized, it.favorites) { id -> usage.getInt(id, 0) }, brandScenes = brands) }
    }

    /** 카드의 별: 누르면 즐겨찾기로 맨 앞에 오고, 다시 누르면 해제돼 자주 쓰는 순서 자리로 돌아간다. */
    fun toggleFavorite(scene: ChabunScene) {
        val updated = toggledFavorites(_state.value.favorites, scene.id)
        prefs.edit().putString(KEY_FAVORITES, updated.joinToString(",")).apply()
        _state.update { it.copy(favorites = updated) }
        publishScenes(_state.value.scenes)
    }

    private fun loadFavorites(): List<String> =
        prefs.getString(KEY_FAVORITES, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    override fun onCleared() {
        voice.release()
        routeGuide.release()
        noise.stop()
        vehicle.release()
        aaos.release()
    }

    private companion object {
        const val TAG = "Chabun"
        const val DONE_HOLD_MS = 2_500L
        const val SERVICE_SETTLE_MS = 2_000L
        const val KEY_POWERTRAIN = "powertrain_override"
        const val KEY_QUICK = "quick_cycles"
        const val KEY_VOICE_GUIDE = "voice_guide"
        const val KEY_ROUTE_BRIEFING = "route_briefing"
        const val KEY_FAVORITES = "favorites"
        const val KEY_FOLLOW_SYSTEM_THEME = "follow_system_theme"
        const val KEY_LANGUAGE = "language"
        const val ROUTE_HOLD_MS = 6_000L
        const val TRIAL_SETTLE_MS = 400L
    }
}
