package com.chabun.app.ui

import android.car.Car
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chabun.app.AppSettings
import com.chabun.app.ChabunUiState
import com.chabun.app.ChabunViewModel
import com.chabun.app.Phase
import com.chabun.app.R
import com.chabun.app.Session
import com.chabun.app.i18n.AppLanguage
import com.chabun.app.i18n.localized
import com.chabun.app.safety.ChargeStatus
import com.chabun.app.safety.SafetyStatus
import com.chabun.app.scene.Brand
import com.chabun.app.scene.ChabunScene
import com.chabun.app.scene.Domain
import com.chabun.app.scene.StepStatus
import com.chabun.app.scene.Steps
import com.chabun.app.vehicle.Powertrain
import com.chabun.app.voice.ListenState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Screen { HOME, SESSION, EDITOR }

@Composable
fun ChabunApp(viewModel: ChabunViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 앱 안에서 고른 언어로 문자열 리소스를 읽게 Context·Configuration을 바꿔 끼운다(기기 언어와 따로).
    val base = LocalContext.current
    val language = state.settings.language
    val localized = remember(base, language) { base.localized(language) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
        ChabunTheme(followSystem = state.settings.followSystemTheme) { ChabunContent(viewModel, state) }
    }
}

@Composable
private fun ChabunContent(viewModel: ChabunViewModel, state: ChabunUiState) {
    val energyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEnergyPermissionResult(granted)
    }
    LaunchedEffect(state.requestEnergyPermission) {
        if (state.requestEnergyPermission) energyPermission.launch(Car.PERMISSION_ENERGY)
    }
    ProvideWindowShape { ChabunScreens(viewModel, state) }
}

@Composable
private fun ChabunScreens(viewModel: ChabunViewModel, state: ChabunUiState) {
    val screen = when {
        state.session != null -> Screen.SESSION
        state.draft != null -> Screen.EDITOR
        else -> Screen.HOME
    }
    // 장면을 누르면 시간부터 고른다(시간은 그때그때 자주 바뀐다).
    var picking by remember { mutableStateOf<ChabunScene?>(null) }
    // 고르는 중에 음성으로 시작하는 등 홈을 떠나면 고르기를 접는다(돌아왔을 때 다시 뜨지 않게).
    LaunchedEffect(screen) { if (screen != Screen.HOME) picking = null }
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
            when (target) {
                Screen.HOME -> HomeScreen(
                    state,
                    onStart = { picking = it },
                    onEdit = viewModel::edit,
                    onCreate = viewModel::createScene,
                    onSettings = { viewModel.showSettings(true) },
                    onVoice = viewModel::toggleListening,
                    onFavorite = viewModel::toggleFavorite,
                )
                Screen.SESSION -> state.session?.let {
                    SessionScreen(it, onEnd = { viewModel.endNow() }, onVoice = viewModel::toggleListening)
                }
                Screen.EDITOR -> state.draft?.let { SceneEditor(it, state.previewing, viewModel) }
            }
        }
        VoiceBar(state.listen, onStop = viewModel::toggleListening, modifier = Modifier.align(Alignment.BottomCenter))
        (state.listen as? ListenState.Failed)?.let { failed ->
            LaunchedEffect(failed) {
                delay(NOTICE_MS)
                viewModel.clearVoiceFailure()
            }
        }
        state.notice?.takeIf { state.listen == ListenState.Idle }?.let { notice ->
            Notice(notice, onDismiss = viewModel::dismissNotice, modifier = Modifier.align(Alignment.BottomCenter))
            LaunchedEffect(notice) {
                delay(NOTICE_MS)
                viewModel.dismissNotice()
            }
        }
        if (state.showSettings) {
            SettingsDialog(state, onChange = viewModel::updateSettings, onDismiss = { viewModel.showSettings(false) })
        }
        picking?.takeIf { screen == Screen.HOME }?.let { scene ->
            TimePickerDialog(
                scene,
                electric = !state.powertrain.hasEngine,
                onPick = { chosen ->
                    picking = null
                    viewModel.start(chosen)
                },
                onDismiss = { picking = null },
            )
        }
    }
}

// ---- 홈 ----

@Composable
private fun HomeScreen(
    state: ChabunUiState,
    onStart: (ChabunScene) -> Unit,
    onEdit: (ChabunScene) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    onVoice: () -> Unit,
    onFavorite: (ChabunScene) -> Unit,
) {
    val window = LocalWindow.current
    val side = if (window.compact) 32.dp else 48.dp
    var brandHall by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(vertical = if (window.short) 24.dp else 40.dp)) {
        val title = @Composable {
            Column {
                Text(stringResource(R.string.app_name), style = PleosType.titleM, color = Palette.textPrimary)
                Text(stringResource(R.string.home_subtitle), style = PleosType.bodyS, color = Palette.textTertiary)
            }
        }
        val actions = @Composable {
            ParkingChip(state)
            PleosButton(stringResource(R.string.action_voice), onVoice, icon = MicIcon, filled = true)
            PleosButton(stringResource(R.string.action_new_scene), onCreate, icon = PlusIcon)
            PleosButton(null, onSettings, icon = GearIcon)
        }
        if (window.compact) {
            // 세로·좁은 창: 제목 아래로 버튼을 내려 줄을 바꿔 가며 놓는다.
            Column(Modifier.padding(horizontal = side)) {
                title()
                Spacer(Modifier.height(20.dp))
                Choices { actions() }
            }
        } else {
            Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { title() }
                actions()
            }
        }
        Spacer(Modifier.height(if (window.short) 16.dp else 28.dp))
        Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
            Choice(stringResource(R.string.tab_my_scenes), !brandHall) { brandHall = false }
            Spacer(Modifier.width(12.dp))
            Choice(stringResource(R.string.tab_brand_hall), brandHall) { brandHall = true }
            if (brandHall && !window.compact) {
                Spacer(Modifier.width(20.dp))
                Text(stringResource(R.string.brand_hall_caption), style = PleosType.labelS, color = Palette.textTertiary)
            }
        }
        if (brandHall && window.compact) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.brand_hall_caption), style = PleosType.labelS, color = Palette.textTertiary, modifier = Modifier.padding(horizontal = side))
        }
        Spacer(Modifier.height(if (window.short) 16.dp else 24.dp))
        val scenes = if (brandHall) state.brandScenes else state.scenes
        val card = @Composable { scene: ChabunScene, modifier: Modifier ->
            SceneCard(
                scene = scene,
                enabled = state.canStart,
                onClick = { onStart(scene) },
                onEdit = if (brandHall) null else ({ onEdit(scene) }),
                favorite = scene.id in state.favorites,
                onFavorite = if (brandHall) null else ({ onFavorite(scene) }),
                modifier = modifier,
            )
        }
        if (window.compact) {
            // 세로 창: 카드를 격자로 놓고 위아래로 넘긴다.
            val gridState = rememberLazyGridState()
            LaunchedEffect(state.favorites.firstOrNull(), brandHall) { gridState.animateScrollToItem(0) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CARD_WIDTH_COMPACT),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = side, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(scenes, key = { it.id }) { scene -> card(scene, Modifier.height(CARD_HEIGHT_COMPACT).animateItem()) }
                if (!brandHall) item(key = "add") { AddSceneCard(onCreate, Modifier.height(CARD_HEIGHT_COMPACT)) }
            }
        } else {
            val listState = rememberLazyListState()
            // 별을 눌러 맨 앞으로 옮긴 장면이 화면 밖으로 사라지지 않게 목록도 맨 앞으로 넘긴다.
            LaunchedEffect(state.favorites.firstOrNull(), brandHall) { listState.animateScrollToItem(0) }
            LazyRow(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(scenes, key = { it.id }) { scene -> card(scene, Modifier.width(CARD_WIDTH).fillMaxHeight().animateItem()) }
                if (!brandHall) item(key = "add") { AddSceneCard(onCreate, Modifier.width(CARD_WIDTH).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ParkingChip(state: ChabunUiState) {
    val parking = state.parking
    val (label, color) = when {
        !state.vehicleReady -> stringResource(R.string.parking_not_connected) to Palette.negative
        parking.gear == null -> stringResource(R.string.parking_checking) to Palette.textQuaternary
        !parking.inPark -> stringResource(R.string.parking_driving) to Palette.negative
        parking.parkingBrake == true -> stringResource(R.string.parking_brake, stringResource(state.powertrain.label)) to Palette.active
        else -> stringResource(R.string.parking_ok, stringResource(state.powertrain.label)) to Palette.active
    }
    Row(
        Modifier.height(64.dp).clip(ControlShape).background(Palette.surface).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, style = PleosType.labelM, color = Palette.textSecondary)
    }
}

/**
 * 장면 카드: 위는 장면 사진, 아래는 시스템 카드(surface) 위에 이름·소개·핵심 설정.
 * 사진 위 별(즐겨찾기)·브랜드 로고는 테마와 상관없이 흰색으로 둔다.
 */
@Composable
internal fun SceneCard(
    scene: ChabunScene,
    enabled: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    modifier: Modifier,
    favorite: Boolean = false,
    /** 사진 우측 상단 별. 누르면 즐겨찾기로 맨 앞에 온다(다시 누르면 해제). */
    onFavorite: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .clip(PanelShape)
            .background(if (pressed) Palette.surfaceHigh else Palette.surface)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SceneBackdrop(scene, Modifier.matchParentSize())
            // 다크 화면에서 사진만 튀지 않게 살짝 누른다.
            if (Palette.dark) Box(Modifier.matchParentSize().background(OnImage.scrim.copy(alpha = 0.14f)))
            if (scene.backdrop == null) {
                // 사진이 없는 장면은 성격을 나타내는 큰 아이콘(브랜드는 머리글자)을 옅게 깔아 사진 카드들과 무게를 맞춘다.
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    val brand = scene.brand
                    if (brand != null) {
                        Text(brand.monogram, fontSize = 180.sp, fontFamily = FontFamily.Serif, color = OnImage.text.copy(alpha = 0.18f))
                    } else {
                        Icon(if (scene.wakeOnCharge) BoltIcon else MoonIcon, null, Modifier.size(140.dp), tint = OnImage.text.copy(alpha = 0.22f))
                    }
                }
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to OnImage.scrim.copy(alpha = 0.45f), 0.35f to Color.Transparent)
                )
            )
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { scene.brand?.let { BrandWordmark(it) } }
                if (scene.brand != null) {
                    Text(
                        stringResource(R.string.brand_badge),
                        style = PleosType.labelS,
                        color = OnImage.text,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(OnImage.scrim.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                if (onFavorite != null) {
                    ImageIconButton(
                        if (favorite) StarFilledIcon else StarOutlineIcon,
                        stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        onFavorite,
                        tint = if (favorite) OnImage.favorite else OnImage.text,
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Text(scene.name, style = PleosType.titleS, color = Palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(scene.tagline, style = PleosType.labelS, color = Palette.textTertiary, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scene.cardFacts().forEach { (icon, text) -> Fact(icon, text, dot = if (icon == null) scene.ambientColor else null) }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (enabled) R.string.card_tap_to_start else R.string.card_park_first),
                    style = PleosType.labelM,
                    color = if (enabled) Palette.textPrimary else Palette.textQuaternary,
                    modifier = Modifier.weight(1f),
                )
                if (onEdit != null) PleosButton(null, onEdit, icon = EditIcon, height = 52.dp)
            }
        }
    }
}

/** 카드의 핵심 설정 한 줄: 아이콘(없으면 조명 색 점) + 글 */
@Composable
private fun Fact(icon: ImageVector?, text: String, dot: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                icon != null -> Icon(icon, null, Modifier.size(22.dp), tint = Palette.textTertiary)
                dot != null -> Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF000000 or dot.toLong())))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = PleosType.labelS, color = Palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 장면 배경: 이미지가 있으면 이미지(준비되는 동안은 조명 색), 없으면 조명 색 그라데이션 */
@Composable
internal fun SceneBackdrop(
    scene: ChabunScene,
    modifier: Modifier,
    alignment: Alignment = Alignment.Center,
    size: BackdropSize = BackdropSize.CARD,
) {
    val gradient = Brush.verticalGradient(listOf(scene.accent(), scene.accent().copy(alpha = 0.75f).compositeOverBlack()))
    val image = scene.backdrop?.let { rememberBackdrop(it, size).value }
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, alignment = alignment, modifier = modifier)
    } else {
        Box(modifier.background(gradient))
    }
}

private fun Color.compositeOverBlack() = Color(red * alpha, green * alpha, blue * alpha)

/** 사진 위 글씨가 묻히지 않게 깔리는 옅은 그림자 */
private val ShadowedText = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), blurRadius = 8f))

@Composable
private fun AddSceneCard(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(PanelShape)
            .border(BorderStroke(2.dp, Palette.surfaceAccent), PanelShape)
            .clickable(onClick = onClick)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(Palette.buttonBasic), contentAlignment = Alignment.Center) {
            Icon(PlusIcon, null, Modifier.size(44.dp), tint = Palette.textPrimary)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.action_new_scene), style = PleosType.titleS, color = Palette.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.add_scene_caption), style = PleosType.labelS, color = Palette.textTertiary)
    }
}

internal fun ChabunScene.accent(): Color = Color(0xFF000000 or (brand?.color ?: ambientColor ?: 0x3A4A6B).toLong())

/** 브랜드 로고 글자: 자간을 넓힌 영문 대문자(브랜드 고유 서체 자리) */
@Composable
private fun BrandWordmark(brand: Brand, modifier: Modifier = Modifier) {
    Text(
        brand.wordmark,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Serif,
        letterSpacing = 5.sp,
        color = OnImage.text,
        style = ShadowedText,
        modifier = modifier,
    )
}

/** 사진 없는 브랜드 카드에 깔 머리글자(브랜드 이름의 마지막 단어 첫 글자, 예: HOTEL MAPLINE → M) */
private val Brand.monogram: String get() = wordmark.split(" ").last().take(1)

/** 카드에 보여 줄 핵심 설정(아이콘이 null이면 조명 색 점과 함께) */
@Composable
private fun ChabunScene.cardFacts(): List<Pair<ImageVector?, String>> {
    val facts = mutableListOf<Pair<ImageVector?, String>>()
    ambientColor?.let { rgb ->
        val name = ChabunScene.palette.firstOrNull { it.rgb == rgb }?.let { stringResource(it.label) } ?: stringResource(R.string.domain_ambient)
        facts += null to stringResource(R.string.fact_ambient, name, ((ambient ?: 0f) * 100).roundToInt())
    }
    facts += NoteIcon to (sound?.let { stringResource(it.label) } ?: stringResource(R.string.fact_no_sound))
    val body = listOfNotNull(
        recline?.let { stringResource(R.string.fact_seat, (it * 100).toInt()) },
        temperatureC?.let { stringResource(R.string.celsius, it) },
    )
    if (body.isNotEmpty()) facts += CheckIcon to body.joinToString(stringResource(R.string.list_separator))
    facts += when {
        wakeOnCharge -> BoltIcon to (chargeTargetPercent?.let { stringResource(R.string.wake_charge_target, it) } ?: stringResource(R.string.wake_charge_end))
        durationMinutes != null -> MoonIcon to stringResource(R.string.wake_default_minutes, durationMinutes)
        else -> MoonIcon to stringResource(R.string.wake_manual)
    }
    return facts
}

// ---- 시간 고르기 ----

/**
 * 장면을 누르면 뜨는 시간 고르기. 시간은 그때그때 자주 바뀌니 장면 설정과 별도로 바로 고르고, 누르는 즉시 시작한다.
 * 장면에 저장된 기본 시간은 진하게 표시한다.
 */
@Composable
private fun TimePickerDialog(scene: ChabunScene, electric: Boolean, onPick: (ChabunScene) -> Unit, onDismiss: () -> Unit) {
    val default = scene.durationMinutes?.takeIf { !scene.wakeOnCharge }
    val presets = (listOf(10, 15, 20, 30, 45, 60) + listOfNotNull(default)).distinct().sorted()
    fun timed(minutes: Int) = onPick(scene.copy(durationMinutes = minutes, wakeOnCharge = false))
    var custom by remember { mutableIntStateOf(default ?: 25) }
    PleosDialog(
        title = scene.name,
        subtitle = stringResource(R.string.picker_subtitle),
        onDismiss = onDismiss,
        modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth(0.94f),
        buttons = { PleosButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Choices {
            presets.forEach { m -> TimeOption(stringResource(R.string.minutes, m), if (m == default) stringResource(R.string.picker_default) else null, selected = m == default) { timed(m) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.picker_custom), style = PleosType.bodyS, color = Palette.textSecondary, modifier = Modifier.width(180.dp))
            PleosButton(null, { custom = Steps.minutes(custom, up = false) }, icon = MinusIcon)
            Text(
                stringResource(R.string.minutes, custom),
                style = PleosType.bodyLStrong,
                color = Palette.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(140.dp),
            )
            PleosButton(null, { custom = Steps.minutes(custom, up = true) }, icon = PlusIcon)
            Spacer(Modifier.width(20.dp))
            PleosButton(stringResource(R.string.picker_rest_for, custom), { timed(custom) }, filled = true)
        }
        Choices {
            Choice(stringResource(R.string.wake_manual), scene.durationMinutes == null && !scene.wakeOnCharge, icon = MoonIcon) {
                onPick(scene.copy(durationMinutes = null, wakeOnCharge = false))
            }
            if (electric) {
                Choice(scene.chargeTargetPercent?.let { stringResource(R.string.picker_charge_target, it) } ?: stringResource(R.string.picker_charge_end), scene.wakeOnCharge, icon = BoltIcon) {
                    onPick(scene.copy(durationMinutes = null, wakeOnCharge = true))
                }
            }
            Choice(stringResource(R.string.picker_one_minute), selected = false) { timed(1) }
        }
    }
}

/** 시간 고르기의 큰 버튼(한 번에 누르기 쉽게) */
@Composable
private fun TimeOption(label: String, caption: String?, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = Palette
    Column(
        Modifier
            .width(130.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> colors.selected
                    pressed -> colors.buttonBasicPressed
                    else -> colors.buttonBasic
                }
            )
            .clickable(interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val content = if (selected) colors.onSelected else colors.textPrimary
        Text(label, style = PleosType.bodyLStrong, color = content)
        caption?.let { Text(it, style = PleosType.labelS, color = content.copy(alpha = 0.7f)) }
    }
}

// ---- 휴식 화면(장면 사진을 꽉 채우는 몰입 화면이라 테마와 상관없이 사진 위 색을 쓴다) ----

@Composable
private fun SessionScreen(session: Session, onEnd: () -> Unit, onVoice: () -> Unit) {
    // 쉬는 동안은 어둡게, 깨울 때는 실내 조명이 밝아지듯 장면 이미지도 서서히 밝힌다.
    val awake = session.phase == Phase.WAKING || session.phase == Phase.DONE
    val backdropAlpha by animateFloatAsState(
        targetValue = if (awake) 0.95f else 0.55f,
        animationSpec = tween(durationMillis = if (awake) WAKE_FADE_MS else 1_200),
        label = "backdrop",
    )
    Box(Modifier.fillMaxSize().background(OnImage.scrim)) {
        SceneBackdrop(
            session.scene,
            Modifier.fillMaxSize().alpha(backdropAlpha),
            alignment = session.scene.backdrop?.landscapeFocus ?: Alignment.Center,
            size = BackdropSize.FULL,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to OnImage.scrim.copy(alpha = 0.75f),
                    0.45f to OnImage.scrim.copy(alpha = 0.2f),
                    1f to OnImage.scrim.copy(alpha = 0.5f),
                )
            )
        )
        SessionContent(session, onEnd, onVoice)
    }
}

@Composable
private fun SessionContent(session: Session, onEnd: () -> Unit, onVoice: () -> Unit) {
    val window = LocalWindow.current
    val busy = session.phase == Phase.RESTORING || session.phase == Phase.DONE
    if (window.compact) {
        // 세로·좁은 창: 위에서부터 제목 → 시간 → 소리·경로 → 차량 상태·안전 감시 → 버튼
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            SessionTitle(session)
            Spacer(Modifier.weight(1f))
            SessionTime(session, large = !window.short)
            SessionExtras(session)
            Spacer(Modifier.height(24.dp))
            StepPanel(session, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            session.safety?.let { SafetyPanel(it, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(24.dp))
            SessionButtons(busy, onEnd, onVoice)
        }
    } else {
        Row(Modifier.fillMaxSize().padding(if (window.short) 32.dp else 48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1.2f).fillMaxHeight()) {
                SessionTitle(session)
                Spacer(Modifier.weight(1f))
                SessionTime(session, large = !window.short)
                SessionExtras(session)
                Spacer(Modifier.weight(1f))
                SessionButtons(busy, onEnd, onVoice)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // 패널은 내용 높이만큼만 두어 가운데로 장면 이미지가 보이게 한다.
                StepPanel(session, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                session.safety?.let { SafetyPanel(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun SessionTitle(session: Session) {
    Column {
        session.scene.brand?.let {
            BrandWordmark(it)
            Spacer(Modifier.height(10.dp))
        }
        Text(session.scene.name, style = PleosType.titleL.merge(ShadowedText), color = OnImage.text)
        Spacer(Modifier.height(8.dp))
        Text(session.phase.label(), style = PleosType.bodyM.merge(ShadowedText), color = OnImage.textSecondary)
    }
}

/** 남은 시간(충전 대기는 충전량). 키가 작은 창에서는 숫자를 줄인다. */
@Composable
private fun SessionTime(session: Session, large: Boolean) {
    val size = if (large) 140.sp else 96.sp
    if (session.scene.wakeOnCharge) {
        ChargeProgress(session.safety?.charge, resting = session.phase == Phase.RESTING, size)
        return
    }
    Text(
        session.remainingSeconds?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "∞",
        fontSize = size,
        fontWeight = FontWeight.Light,
        color = OnImage.text,
        style = ShadowedText,
    )
    if (session.phase == Phase.RESTING) {
        Text(
            stringResource(if (session.scene.durationMinutes != null) R.string.session_wake_later else R.string.session_end_any_time),
            style = PleosType.bodyS.merge(ShadowedText),
            color = OnImage.textSecondary,
        )
    }
}

/** 소리·엔진음 마스킹·남은 경로 */
@Composable
private fun SessionExtras(session: Session) {
    session.scene.sound?.let {
        Spacer(Modifier.height(20.dp))
        ImageLine(NoteIcon, stringResource(it.label))
    }
    session.safety?.masking?.let {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.masking_line), style = PleosType.labelS.merge(ShadowedText), color = OnImage.textTertiary)
    }
    session.route?.let { route ->
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(PanelShape).background(OnImage.panel).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NavigationIcon, null, Modifier.size(30.dp), tint = OnImage.text)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.route_remaining), style = PleosType.labelS, color = OnImage.textTertiary)
                Text(route, style = PleosType.labelM, color = OnImage.text)
            }
        }
    }
}

/** 버튼을 누를 수 있으면 이미 깨어 있는 것이라 기상 연출 없이 바로 끝낸다(타이머·음성 "깨워줘"·안전 감시는 기상 연출). */
@Composable
private fun SessionButtons(busy: Boolean, onEnd: () -> Unit, onVoice: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ImageButton(stringResource(R.string.end), onEnd, enabled = !busy, primary = true, modifier = Modifier.width(260.dp))
        ImageButton(null, onVoice, enabled = !busy, icon = MicIcon)
    }
}

/** 휴식 화면의 사진 위 버튼: 주요 버튼은 흰 바탕, 나머지는 반투명 막(가이드의 Static Color) */
@Composable
private fun ImageButton(
    text: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = if (primary) OnImage.text.copy(alpha = if (pressed) 0.84f else 1f) else OnImage.text.copy(alpha = if (pressed) 0.3f else 0.18f)
    val content = if (primary) OnImage.scrim else OnImage.text
    Row(
        modifier
            .height(72.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ControlShape)
            .background(background)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, contentDescription = stringResource(R.string.action_voice), Modifier.size(30.dp), tint = content) }
        text?.let { Text(it, style = PleosType.bodySStrong, color = content) }
    }
}

@Composable
private fun ImageLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = OnImage.text)
        Spacer(Modifier.width(10.dp))
        Text(text, style = PleosType.labelM.merge(ShadowedText), color = OnImage.text)
    }
}

/** 충전 대기의 큰 표시: 지금 충전량 → 목표, 진행 막대, 남은 시간 */
@Composable
private fun ChargeProgress(charge: ChargeStatus?, resting: Boolean, size: TextUnit) {
    val percent = charge?.percent
    Row(verticalAlignment = Alignment.Bottom) {
        Text(percent?.let { "$it%" } ?: "--%", fontSize = size, fontWeight = FontWeight.Light, color = OnImage.text, style = ShadowedText)
        charge?.let {
            Text(
                stringResource(R.string.charge_target_suffix, it.targetPercent),
                style = PleosType.titleS.copy(fontWeight = FontWeight.Normal).merge(ShadowedText),
                color = OnImage.textSecondary,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }
    }
    if (charge != null && percent != null) {
        LinearProgressIndicator(
            progress = { (percent.toFloat() / charge.targetPercent).coerceIn(0f, 1f) },
            color = if (charge.charging) DarkPleos.active else OnImage.textTertiary,
            trackColor = OnImage.text.copy(alpha = 0.2f),
            modifier = Modifier.width(520.dp).height(10.dp).clip(RoundedCornerShape(50)),
        )
        Spacer(Modifier.height(16.dp))
    }
    if (!resting) return
    val message = when {
        charge == null -> stringResource(R.string.charge_checking)
        !charge.charging -> stringResource(R.string.charge_waiting)
        charge.secondsLeft != null -> {
            val minutes = (charge.secondsLeft + 59) / 60
            stringResource(if (charge.estimated) R.string.charge_minutes_left_estimated else R.string.charge_minutes_left, minutes)
        }
        else -> stringResource(R.string.charge_in_progress)
    }
    Text(message, style = PleosType.bodyS.merge(ShadowedText), color = OnImage.textSecondary)
}

/** 휴식 중 안전 감시 현황: 정차, 엔진차 환기·최대 휴식·마스킹, 전기차 배터리 하한 */
@Composable
private fun SafetyPanel(safety: SafetyStatus, modifier: Modifier) {
    Column(
        modifier.clip(PanelShape).background(OnImage.panel).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.safety_title, stringResource(safety.powertrain.label)), style = PleosType.bodySStrong, color = OnImage.text)
        SafetyRow(stringResource(R.string.safety_parking), stringResource(R.string.safety_parking_detail))
        if (safety.powertrain.hasEngine) {
            val vent = safety.ventilating?.let { stringResource(R.string.vent_now, it) }
                ?: safety.nextVentilationSeconds?.let { stringResource(R.string.vent_next, it.clock(), safety.ventilationCount) }
                ?: stringResource(R.string.vent_waiting)
            SafetyRow(stringResource(R.string.safety_vent), vent, highlight = safety.ventilating != null)
            safety.restLimitSeconds?.let { SafetyRow(stringResource(R.string.safety_rest_limit), stringResource(R.string.rest_limit_left, it.clock())) }
            safety.masking?.let {
                SafetyRow(stringResource(R.string.safety_masking), stringResource(R.string.masking_detail, stringResource(it.label), it.rpm, it.firingHz.roundToInt()))
            }
        } else {
            val floor = safety.batteryFloorPercent
            val battery = safety.batteryPercent?.let { "$it%" } ?: stringResource(R.string.battery_checking)
            val charge = safety.charge
            if (charge != null) {
                SafetyRow(
                    stringResource(R.string.safety_charge),
                    if (charge.charging) stringResource(R.string.charge_on, charge.targetPercent) else stringResource(R.string.charge_off),
                    highlight = charge.charging,
                )
                SafetyRow(stringResource(R.string.safety_battery), if (charge.charging) battery else stringResource(R.string.battery_floor_charge, battery, floor ?: 0))
            } else {
                SafetyRow(stringResource(R.string.safety_battery), if (floor != null) stringResource(R.string.battery_floor, battery, floor) else battery)
            }
        }
    }
}

@Composable
private fun SafetyRow(title: String, detail: String, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(title, style = PleosType.labelS, color = OnImage.textTertiary, modifier = Modifier.width(140.dp))
        Text(detail, style = PleosType.labelS, color = if (highlight) DarkPleos.active else OnImage.text)
    }
}

private fun Int.clock() = "%d:%02d".format(this / 60, this % 60)

@Composable
private fun StepPanel(session: Session, modifier: Modifier) {
    Column(
        modifier.clip(PanelShape).background(OnImage.panel).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.vehicle_status), style = PleosType.bodySStrong, color = OnImage.text)
        Domain.entries.forEach { domain ->
            val status = session.steps[domain] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    when (status) {
                        StepStatus.Running -> CircularProgressIndicator(Modifier.size(26.dp), color = OnImage.text, strokeWidth = 3.dp)
                        is StepStatus.Done -> Icon(CheckIcon, null, Modifier.size(30.dp), tint = DarkPleos.active)
                        is StepStatus.Skipped -> Icon(MinusIcon, null, Modifier.size(30.dp), tint = OnImage.textTertiary)
                        is StepStatus.Failed -> Icon(AlertIcon, null, Modifier.size(30.dp), tint = DarkPleos.negative)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(stringResource(domain.label), style = PleosType.labelM, color = OnImage.text)
                    Text(status.detail(), style = PleosType.labelS, color = OnImage.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun Phase.label() = stringResource(
    when (this) {
        Phase.ENTERING -> R.string.phase_entering
        Phase.RESTING -> R.string.phase_resting
        Phase.WAKING -> R.string.phase_waking
        Phase.RESTORING -> R.string.phase_restoring
        Phase.DONE -> R.string.phase_done
    }
)

@Composable
internal fun StepStatus.detail(): String = when (this) {
    StepStatus.Running -> stringResource(R.string.step_running)
    is StepStatus.Done -> detail
    is StepStatus.Skipped -> reason
    is StepStatus.Failed -> reason
}

// ---- 설정·알림 ----

@Composable
private fun SettingsDialog(state: ChabunUiState, onChange: ((AppSettings) -> AppSettings) -> Unit, onDismiss: () -> Unit) {
    PleosDialog(
        title = stringResource(R.string.action_settings),
        onDismiss = onDismiss,
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(0.94f),
        buttons = { PleosButton(stringResource(R.string.close), onDismiss, filled = true) },
    ) {
        Text(stringResource(R.string.settings_language), style = PleosType.bodySStrong, color = Palette.textPrimary)
        Choices {
            // 언어 이름은 그 언어로 적는다(지금 화면 언어를 몰라도 찾을 수 있게).
            AppLanguage.entries.forEach { language ->
                Choice(language.nativeName, state.settings.language == language) { onChange { it.copy(language = language) } }
            }
        }
        Text(stringResource(R.string.language_voice_note), style = PleosType.labelS, color = Palette.textTertiary)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.surfaceAccent))
        Text(stringResource(R.string.settings_screen), style = PleosType.bodySStrong, color = Palette.textPrimary)
        Choices {
            Choice(stringResource(R.string.theme_soft_dark), !state.settings.followSystemTheme) { onChange { it.copy(followSystemTheme = false) } }
            Choice(stringResource(R.string.theme_system), state.settings.followSystemTheme) { onChange { it.copy(followSystemTheme = true) } }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.surfaceAccent))
        Text(stringResource(R.string.settings_vehicle), style = PleosType.bodySStrong, color = Palette.textPrimary)
        Choices {
            val override = state.settings.powertrainOverride
            Choice(stringResource(R.string.powertrain_auto, stringResource(state.detectedPowertrain.label)), override == null) { onChange { it.copy(powertrainOverride = null) } }
            listOf(Powertrain.GASOLINE, Powertrain.DIESEL).forEach { p ->
                Choice(stringResource(R.string.powertrain_try, stringResource(p.label)), override == p) { onChange { it.copy(powertrainOverride = p) } }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.surfaceAccent))
        SwitchRow(stringResource(R.string.quick_cycles), stringResource(R.string.quick_cycles_detail), state.settings.quickCycles) { on ->
            onChange { it.copy(quickCycles = on) }
        }
        SwitchRow(stringResource(R.string.voice_guide), stringResource(R.string.voice_guide_detail), state.settings.voiceGuide) { on ->
            onChange { it.copy(voiceGuide = on) }
        }
        SwitchRow(stringResource(R.string.route_briefing), stringResource(R.string.route_briefing_detail), state.settings.routeBriefing) { on ->
            onChange { it.copy(routeBriefing = on) }
        }
    }
}

/** 음성 명령을 듣는 동안 하단에 뜨는 바: 들은 말을 실시간으로 보여 준다(시스템 토스트처럼 어두운 막). */
@Composable
private fun VoiceBar(listen: ListenState, onStop: () -> Unit, modifier: Modifier) {
    val (text, failed) = when (listen) {
        ListenState.Idle -> return
        is ListenState.Listening -> listen.partial.ifEmpty { stringResource(R.string.voice_listening_hint) } to false
        is ListenState.Failed -> listen.reason to true
    }
    Toast(modifier) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (failed) DarkPleos.negative else DarkPleos.active))
        Spacer(Modifier.width(14.dp))
        Icon(MicIcon, contentDescription = null, Modifier.size(28.dp), tint = OnImage.text)
        Spacer(Modifier.width(12.dp))
        Text(text, style = PleosType.labelM, color = OnImage.text)
        if (!failed) {
            Spacer(Modifier.width(24.dp))
            Text(stringResource(R.string.voice_stop), style = PleosType.labelM, color = OnImage.textTertiary, modifier = Modifier.clickable(onClick = onStop))
        }
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit, modifier: Modifier) {
    Toast(modifier.clickable(onClick = onDismiss)) {
        Text(text, style = PleosType.labelM, color = OnImage.text)
        Spacer(Modifier.width(24.dp))
        Icon(CloseIcon, contentDescription = stringResource(R.string.close), Modifier.size(24.dp), tint = OnImage.textTertiary)
    }
}

/** 화면 아래 알림(시스템 토스트처럼 테마와 상관없이 어두운 막 + 흰 글자) */
@Composable
private fun Toast(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OnImage.scrim.copy(alpha = 0.92f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private val CARD_WIDTH = 400.dp
/** 세로 창 격자의 카드 최소 폭·높이 */
private val CARD_WIDTH_COMPACT = 340.dp
private val CARD_HEIGHT_COMPACT = 640.dp

private const val NOTICE_MS = 4_000L

/** 기상 시퀀스(조명 5단계 × 1초 + 바람)와 비슷한 길이로 화면도 밝힌다. */
private const val WAKE_FADE_MS = 6_000
