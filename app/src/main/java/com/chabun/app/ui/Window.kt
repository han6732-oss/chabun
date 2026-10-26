package com.chabun.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * 앱 창 모양. Pleos 화면은 가로(Connect-S 14.6"·W)와 세로(Connect-S 12.9")가 있고,
 * 앱 창은 하나 또는 둘로 나뉘어 크기가 달라진다(디자인 가이드 › Display Layout).
 */
@Immutable
data class WindowShape(val width: Dp, val height: Dp) {
    /** 세로 창이거나 폭이 좁으면 위아래로 쌓는다. */
    val compact: Boolean get() = width < 1100.dp || height > width

    /** 키가 작은 창(Connect-WF, 화면 나눔)에서는 큰 숫자와 여백을 줄인다. */
    val short: Boolean get() = height < 900.dp
}

val LocalWindow = staticCompositionLocalOf { WindowShape(1680.dp, 1210.dp) }

/** 지금 창 크기를 [LocalWindow]로 내려 준다. */
@Composable
fun ProvideWindowShape(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindow provides WindowShape(maxWidth, maxHeight)) { content() }
    }
}

/**
 * 디버그 점검용: 앱 창이 [size]일 때를 흉내 낸다. 배치는 그 크기로 하고, 지금 화면에 들어가게 줄여 그린다.
 * (FormFactorConsole은 PC 쪽 브리지가 있어야 해서, 배치 점검은 이것으로 한다.)
 */
@Composable
fun SimulatedWindow(size: DpSize, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = minOf(maxWidth / size.width, maxHeight / size.height, 1f)
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) { content() }
    }
}
