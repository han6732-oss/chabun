package com.chabun.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 버튼·칩·패널이 함께 쓰는 모서리. 시스템 UI의 각진 듯 부드러운 사각형에 맞춘다. */
internal val ControlShape = RoundedCornerShape(16.dp)
internal val PanelShape = RoundedCornerShape(24.dp)

/**
 * Pleos Basic Button. filled = 꽉 찬 주요 버튼(button_filled), 아니면 옅은 기본 버튼(button_basic).
 * 눌림(Pressed)·비활성(Disabled) 상태는 토큰 색으로 보여 준다(물결 효과 없음).
 */
@Composable
internal fun PleosButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 64.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = Palette
    val background = when {
        !enabled -> colors.buttonBasic
        filled -> if (pressed) colors.buttonFilledPressed else colors.buttonFilled
        else -> if (pressed) colors.buttonBasicPressed else colors.buttonBasic
    }
    val content = when {
        !enabled -> colors.textQuaternary
        filled -> colors.onButtonFilled
        else -> colors.textPrimary
    }
    Row(
        modifier
            .height(height)
            .clip(ControlShape)
            .background(background)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text == null) 18.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, contentDescription = text, Modifier.size(28.dp), tint = content) }
        if (icon != null && text != null) Spacer(Modifier.width(10.dp))
        text?.let { Text(it, style = PleosType.labelM, color = content) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Choices(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
}

/** 선택 칩. 고른 것은 시스템 설정의 선택 항목처럼 가장 진한 색으로 채운다. */
@Composable
internal fun Choice(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = Palette
    Row(
        Modifier
            .height(56.dp)
            .clip(ControlShape)
            .background(
                when {
                    selected -> colors.selected
                    pressed -> colors.buttonBasicPressed
                    else -> colors.buttonBasic
                }
            )
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (selected) colors.onSelected else colors.textPrimary
        icon?.let {
            Icon(it, contentDescription = null, Modifier.size(24.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = PleosType.labelM, color = content)
    }
}

/** Pleos Switch: 켜짐 = informative_active(초록), 꺼짐 = alpha 트랙 */
@Composable
internal fun PleosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = Palette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.active,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = colors.switchOff,
            uncheckedThumbColor = colors.knobOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** 제목과 설명 오른쪽에 스위치가 붙은 설정 한 줄 */
@Composable
internal fun SwitchRow(title: String, detail: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PleosType.bodyS, color = Palette.textPrimary)
            detail?.let { Text(it, style = PleosType.labelS, color = Palette.textTertiary) }
        }
        Spacer(Modifier.width(24.dp))
        PleosSwitch(checked, onCheckedChange)
    }
}

/** 카드·설정 묶음 바탕(surface_basic) */
@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(PanelShape).background(Palette.surface).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

/** Pleos 팝업(background_popup). 제목·본문·아래 버튼 줄로 이뤄진다. */
@Composable
internal fun PleosDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    buttons: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Dialog는 자기 창의 Context·Configuration을 새로 깔아 앱에서 고른 언어가 풀리므로, 바깥 것을 다시 넘겨 준다.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
            Column(
                modifier.clip(RoundedCornerShape(28.dp)).background(Palette.popup).padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column {
                    Text(title, style = PleosType.titleS, color = Palette.textPrimary)
                    subtitle?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = PleosType.bodyS, color = Palette.textTertiary)
                    }
                }
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) { buttons() }
            }
        }
    }
}

/** 장면 사진 위에 놓는 둥근 아이콘 버튼(테마와 무관하게 흰 아이콘 + 어두운 막) */
@Composable
internal fun ImageIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = OnImage.text) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(50)).background(OnImage.scrim.copy(alpha = 0.32f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, Modifier.size(28.dp), tint = tint)
    }
}
