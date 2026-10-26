package com.chabun.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chabun.app.ChabunViewModel
import com.chabun.app.R
import com.chabun.app.SceneDraft
import com.chabun.app.scene.Backdrop
import com.chabun.app.scene.ChabunScene
import com.chabun.app.scene.SoundTheme
import com.chabun.app.scene.Steps
import kotlin.math.roundToInt

@Composable
internal fun SceneEditor(draft: SceneDraft, previewing: SoundTheme?, viewModel: ChabunViewModel) {
    if (LocalWindow.current.compact) {
        // 세로·좁은 창: 미리보기와 설정을 한 줄로 쌓아 위아래로 넘긴다.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorSide(draft, viewModel, previewModifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT_COMPACT))
            EditorSections(draft, previewing, viewModel)
        }
    } else {
        Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(440.dp).fillMaxHeight()) {
                EditorSide(draft, viewModel, previewModifier = Modifier.weight(1f).fillMaxWidth())
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorSections(draft, previewing, viewModel)
            }
        }
    }
}

/** 제목·미리보기 카드·바로 느껴보기·저장/취소 */
@Composable
private fun EditorSide(draft: SceneDraft, viewModel: ChabunViewModel, previewModifier: Modifier) {
    val scene = draft.scene
    Text(stringResource(if (draft.isNew) R.string.editor_new else R.string.editor_edit), style = PleosType.titleM, color = Palette.textPrimary)
    Spacer(Modifier.height(20.dp))
    SceneCard(scene, enabled = true, onClick = {}, onEdit = null, modifier = previewModifier)
    Spacer(Modifier.height(16.dp))
    TrialPanel(draft, onToggle = viewModel::setTrial)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PleosButton(stringResource(R.string.save), viewModel::saveDraft, filled = true, modifier = Modifier.weight(1f))
        PleosButton(stringResource(R.string.cancel), { viewModel.closeEditor() }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    when {
        scene.builtIn -> TextAction(stringResource(R.string.reset_default), Palette.textTertiary, viewModel::resetDraft)
        !draft.isNew -> TextAction(stringResource(R.string.delete_scene), Palette.negative, viewModel::deleteDraft)
    }
}

/** 장면 설정 묶음들(이름·배경·소리·조명·시트·공조·창문·열선·기본 기상) */
@Composable
private fun EditorSections(draft: SceneDraft, previewing: SoundTheme?, viewModel: ChabunViewModel) {
    val scene = draft.scene
    val update = viewModel::updateDraft
    Section(stringResource(R.string.section_name)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextInput(scene.name, stringResource(R.string.field_scene_name), Modifier.weight(1f)) { v -> update { it.copy(name = v.take(20)) } }
            TextInput(scene.tagline, stringResource(R.string.field_tagline), Modifier.weight(2f)) { v -> update { it.copy(tagline = v.take(40)) } }
        }
    }

    Section(stringResource(R.string.section_backdrop)) {
        Choices {
            BackdropOption(scene.copy(backdrop = null), stringResource(R.string.backdrop_color_only), selected = scene.backdrop == null) {
                update { it.copy(backdrop = null) }
            }
            Backdrop.entries.forEach { backdrop ->
                BackdropOption(scene.copy(backdrop = backdrop), stringResource(backdrop.label), selected = scene.backdrop == backdrop) {
                    update { it.copy(backdrop = backdrop) }
                }
            }
        }
    }

    Section(stringResource(R.string.section_sound)) {
        Choices {
            Choice(stringResource(R.string.sound_none), scene.sound == null) { update { it.copy(sound = null) } }
            SoundTheme.entries.forEach { theme ->
                Choice(stringResource(theme.label), scene.sound == theme) { update { it.copy(sound = theme) } }
            }
        }
        scene.sound?.let { theme ->
            PleosButton(stringResource(if (previewing == theme) R.string.preview_stop else R.string.preview_play), { viewModel.togglePreview(theme) }, icon = NoteIcon)
        }
        // 엔진 떨림을 순항 중 기류처럼 들려주는 연출이라 비행기 기내 소리에서만 고를 수 있다.
        if (scene.sound == SoundTheme.CABIN) {
            SwitchRow(stringResource(R.string.safety_masking), stringResource(R.string.masking_option_detail), scene.engineMasking) { on ->
                update { it.copy(engineMasking = on) }
            }
        }
    }

    ToggleSection(
        title = stringResource(R.string.domain_ambient),
        enabled = scene.ambient != null,
        onToggle = { on -> update { it.copy(ambient = if (on) 0.2f else null, ambientColor = if (on) ChabunScene.palette.first().rgb else null) } },
    ) {
        Choices {
            ChabunScene.palette.forEach { color ->
                Swatch(color.rgb, stringResource(color.label), selected = scene.ambientColor == color.rgb) {
                    update { it.copy(ambientColor = color.rgb) }
                }
            }
        }
        LabeledSlider(stringResource(R.string.brightness), stringResource(R.string.percent, ((scene.ambient ?: 0f) * 100).roundToInt()), scene.ambient ?: 0f) { v ->
            update { it.copy(ambient = v) }
        }
    }

    ToggleSection(
        title = stringResource(R.string.section_recline),
        enabled = scene.recline != null,
        onToggle = { on -> update { it.copy(recline = if (on) 0.6f else null) } },
    ) {
        LabeledSlider(stringResource(R.string.recline_range), stringResource(R.string.percent, ((scene.recline ?: 0f) * 100).roundToInt()), scene.recline ?: 0f) { v ->
            update { it.copy(recline = v) }
        }
    }

    ToggleSection(
        title = stringResource(R.string.section_climate),
        enabled = scene.temperatureC != null,
        onToggle = { on ->
            update {
                it.copy(
                    temperatureC = if (on) 22f else null,
                    fan = if (on) 0.2f else null,
                    recirculation = if (on) true else null,
                )
            }
        },
    ) {
        val temp = scene.temperatureC ?: 22f
        LabeledSlider(stringResource(R.string.temperature), stringResource(R.string.celsius, temp), (temp - MIN_TEMP) / (MAX_TEMP - MIN_TEMP), steps = 27) { v ->
            update { it.copy(temperatureC = ((MIN_TEMP + v * (MAX_TEMP - MIN_TEMP)) * 2).roundToInt() / 2f) }
        }
        LabeledSlider(stringResource(R.string.fan), stringResource(R.string.percent, ((scene.fan ?: 0f) * 100).roundToInt()), scene.fan ?: 0f) { v ->
            update { it.copy(fan = v) }
        }
        Choices {
            Choice(stringResource(R.string.recirc_in), scene.recirculation == true) { update { it.copy(recirculation = true) } }
            Choice(stringResource(R.string.recirc_out), scene.recirculation == false) { update { it.copy(recirculation = false) } }
        }
    }

    Section(stringResource(R.string.section_window)) {
        Choices {
            Choice(stringResource(R.string.keep_as_is), scene.windowOpenPercent == null) { update { it.copy(windowOpenPercent = null) } }
            listOf(0, 5, 10, 20).forEach { p ->
                Choice(if (p == 0) stringResource(R.string.window_close) else stringResource(R.string.window_open, p), scene.windowOpenPercent == p) { update { it.copy(windowOpenPercent = p) } }
            }
        }
    }

    Section(stringResource(R.string.section_steering), stringResource(R.string.steering_detail)) {
        Choices {
            Choice(stringResource(R.string.steering_off), (scene.steeringHeat ?: 0) == 0) { update { it.copy(steeringHeat = null) } }
            (1..3).forEach { level ->
                Choice(stringResource(R.string.level, level), scene.steeringHeat == level) { update { it.copy(steeringHeat = level) } }
            }
        }
    }

    Section(stringResource(R.string.section_default_wake), stringResource(R.string.default_wake_detail)) {
        Choices {
            Choice(stringResource(R.string.wake_manual_short), scene.durationMinutes == null && !scene.wakeOnCharge) {
                update { it.copy(durationMinutes = null, wakeOnCharge = false) }
            }
            MINUTE_PRESETS.forEach { m ->
                Choice(stringResource(R.string.minutes, m), scene.durationMinutes == m && !scene.wakeOnCharge) {
                    update { it.copy(durationMinutes = m, wakeOnCharge = false) }
                }
            }
            Choice(stringResource(R.string.charge_end_option), scene.wakeOnCharge, icon = BoltIcon) { update { it.copy(durationMinutes = null, wakeOnCharge = true) } }
        }
        // 프리셋 말고도 직접 맞출 수 있다(누르면 시간 기상으로 바뀐다).
        val timed = scene.durationMinutes != null && !scene.wakeOnCharge
        val minutes = scene.durationMinutes ?: DEFAULT_MINUTES
        Stepper(
            label = stringResource(R.string.picker_custom),
            value = stringResource(R.string.minutes, minutes),
            active = timed,
            onMinus = { update { it.copy(durationMinutes = Steps.minutes(minutes, up = false), wakeOnCharge = false) } },
            onPlus = { update { it.copy(durationMinutes = Steps.minutes(minutes, up = true), wakeOnCharge = false) } },
        )
        if (scene.wakeOnCharge) {
            Text(stringResource(R.string.charge_target), style = PleosType.bodySStrong, color = Palette.textPrimary)
            Choices {
                Choice(stringResource(R.string.charge_to_limit), scene.chargeTargetPercent == null) { update { it.copy(chargeTargetPercent = null) } }
                CHARGE_PRESETS.forEach { p ->
                    Choice(stringResource(R.string.percent, p), scene.chargeTargetPercent == p) { update { it.copy(chargeTargetPercent = p) } }
                }
            }
            val target = scene.chargeTargetPercent ?: DEFAULT_CHARGE
            Stepper(
                label = stringResource(R.string.picker_custom),
                value = stringResource(R.string.percent, target),
                active = scene.chargeTargetPercent != null,
                onMinus = { update { it.copy(chargeTargetPercent = Steps.chargePercent(target, up = false)) } },
                onPlus = { update { it.copy(chargeTargetPercent = Steps.chargePercent(target, up = true)) } },
            )
            Text(stringResource(R.string.charge_target_detail), style = PleosType.labelS, color = Palette.textTertiary)
        }
    }
}

/** 편집하며 차에서 바로 느껴보기. 켜 두면 시트·조명을 바꿀 때마다 차가 그대로 움직이고, 끄거나 닫으면 원래대로 돌아온다. */
@Composable
private fun TrialPanel(draft: SceneDraft, onToggle: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(PanelShape).background(Palette.surface).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SwitchRow(
            stringResource(R.string.trial_title),
            stringResource(if (draft.trial) R.string.trial_on else R.string.trial_off),
            draft.trial,
            onToggle,
        )
        if (draft.trial) {
            draft.trialSteps.forEach { (domain, status) ->
                Text(stringResource(R.string.joined, stringResource(domain.label), status.detail()), style = PleosType.labelS, color = Palette.textTertiary)
            }
        }
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(label, style = PleosType.labelM, color = color, modifier = Modifier.clip(ControlShape).clickable(onClick = onClick).padding(12.dp))
}

@Composable
private fun TextInput(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = PleosType.bodyS.copy(color = Palette.textPrimary),
        label = { Text(label, style = PleosType.labelS) },
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.selected,
            unfocusedBorderColor = Palette.surfaceAccent,
            focusedLabelColor = Palette.textSecondary,
            unfocusedLabelColor = Palette.textTertiary,
            cursorColor = Palette.textPrimary,
        ),
        modifier = modifier,
    )
}

/** − 값 + 로 한 단계씩 직접 맞춘다. active가 아니면(다른 방식 선택 중) 값을 흐리게 보여 준다. */
@Composable
private fun Stepper(label: String, value: String, active: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PleosType.labelM, color = Palette.textTertiary, modifier = Modifier.width(170.dp))
        PleosButton(null, onMinus, icon = MinusIcon)
        Text(
            value,
            style = PleosType.bodyMStrong,
            color = if (active) Palette.textPrimary else Palette.textQuaternary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(130.dp),
        )
        PleosButton(null, onPlus, icon = PlusIcon)
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = PleosType.bodyMStrong, color = Palette.textPrimary)
            description?.let { Text(it, style = PleosType.labelS, color = Palette.textTertiary) }
        }
        content()
    }
}

/** 켜면 이 항목을 장면에 포함하고, 끄면 차량의 현재 상태를 그대로 둔다. */
@Composable
private fun ToggleSection(title: String, enabled: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PleosType.bodyMStrong, color = Palette.textPrimary)
                if (!enabled) Text(stringResource(R.string.toggle_off_detail), style = PleosType.labelS, color = Palette.textTertiary)
            }
            PleosSwitch(enabled, onToggle)
        }
        if (enabled) content()
    }
}

@Composable
private fun BackdropOption(preview: ChabunScene, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SceneBackdrop(
            preview,
            Modifier
                .size(width = 112.dp, height = 180.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(if (selected) 4.dp else 0.dp, Palette.selected), RoundedCornerShape(14.dp)),
            size = BackdropSize.THUMB,
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = PleosType.labelS, color = if (selected) Palette.textPrimary else Palette.textTertiary)
    }
}

@Composable
private fun Swatch(rgb: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF000000 or rgb.toLong()))
                .border(BorderStroke(if (selected) 4.dp else 1.dp, if (selected) Palette.selected else Palette.surfaceAccent), CircleShape)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = PleosType.labelS, color = if (selected) Palette.textPrimary else Palette.textTertiary)
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, fraction: Float, steps: Int = 0, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PleosType.labelM, color = Palette.textTertiary, modifier = Modifier.width(170.dp))
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onChange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Palette.selected,
                activeTrackColor = Palette.selected,
                inactiveTrackColor = Palette.surfaceAccent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(value, style = PleosType.labelM, color = Palette.textPrimary, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
    }
}

private val PREVIEW_HEIGHT_COMPACT = 560.dp

private val MINUTE_PRESETS = listOf(10, 15, 20, 30, 60)
private const val DEFAULT_MINUTES = 20
private val CHARGE_PRESETS = listOf(70, 80, 90, 100)
private const val DEFAULT_CHARGE = 80

private const val MIN_TEMP = 16f
private const val MAX_TEMP = 30f
