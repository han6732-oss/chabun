package com.chabun.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디자인 가이드(Foundations › Colors)의 시스템 색 토큰.
 * 시스템 UI처럼 주간(라이트)·야간(다크)을 따라 바뀐다. 이름은 가이드의 토큰 이름을 그대로 쓴다.
 * https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors
 */
@Immutable
data class PleosColors(
    val dark: Boolean,
    /** background_primary: 화면 바탕(가장 아래) */
    val background: Color,
    /** background_popup: 팝업·다이얼로그 */
    val popup: Color,
    /** surface_basic: 카드·패널 */
    val surface: Color,
    /** surface_high: 바탕 위에 한 단계 올린 면(칩·트랙) */
    val surfaceHigh: Color,
    /** surface_accent: 구분선·비활성 트랙 */
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    /** button_basic_enabled / pressed: 옅은 바탕의 기본 버튼 */
    val buttonBasic: Color,
    val buttonBasicPressed: Color,
    /** button_filled_enabled / pressed: 꽉 찬 주요 버튼과 그 위 글자 */
    val buttonFilled: Color,
    val buttonFilledPressed: Color,
    val onButtonFilled: Color,
    /** 선택된 탭·칩(시스템 설정의 선택 항목처럼 가장 진한 색) */
    val selected: Color,
    val onSelected: Color,
    /** informative_active(초록, 켜짐·정상) / positive(파랑, 상태 값) / negative(빨강, 경고) */
    val active: Color,
    val positive: Color,
    val negative: Color,
    val switchOff: Color,
    val knobOff: Color,
)

private const val BLACK = 0xFF131417
private const val WHITE = 0xFFFFFFFF

private fun black(alpha: Float) = Color(BLACK).copy(alpha = alpha)
private fun white(alpha: Float) = Color(WHITE).copy(alpha = alpha)

val LightPleos = PleosColors(
    dark = false,
    background = Color(0xFFF7F8FA), // basic_50
    popup = Color(0xFFFFFFFF), // basic_00
    surface = Color(0xFFFFFFFF), // basic_00
    surfaceHigh = Color(0xFFEDEEF2), // basic_100
    surfaceAccent = Color(0xFFE0E1E6), // basic_200
    textPrimary = Color(BLACK), // static_dark_100
    textSecondary = black(0.84f),
    textTertiary = black(0.64f),
    textQuaternary = black(0.32f),
    buttonBasic = black(0.05f), // alpha_dark_50
    buttonBasicPressed = black(0.10f), // alpha_dark_100
    buttonFilled = Color(0xFF44464E), // basic_600
    buttonFilledPressed = Color(0xFF313236), // basic_700
    onButtonFilled = Color(WHITE),
    selected = Color(BLACK),
    onSelected = Color(WHITE),
    active = Color(0xFF02C265),
    positive = Color(0xFF0064FF),
    negative = Color(0xFFFE3D16),
    switchOff = black(0.30f), // alpha_dark_300
    knobOff = Color(WHITE),
)

val DarkPleos = PleosColors(
    dark = true,
    background = Color(0xFF1D1E21), // basic_50
    popup = Color(0xFF313236), // basic_100
    surface = Color(0xFF1D1E21), // basic_50
    surfaceHigh = Color(0xFF313236), // basic_100
    surfaceAccent = Color(0xFF44464E), // basic_200
    textPrimary = Color(WHITE), // static_light_100
    textSecondary = white(0.84f),
    textTertiary = white(0.64f),
    textQuaternary = white(0.32f),
    buttonBasic = white(0.05f),
    buttonBasicPressed = white(0.10f),
    buttonFilled = Color(0xFFE0E1E6), // basic_600
    buttonFilledPressed = Color(0xFFC7C9CE), // basic_500
    onButtonFilled = Color(BLACK),
    selected = Color(WHITE),
    onSelected = Color(BLACK),
    active = Color(0xFF00E074),
    positive = Color(0xFF0082FF),
    negative = Color(0xFFFF4C28),
    switchOff = white(0.10f), // alpha_dark_100
    knobOff = Color(0xFFA4A8AE), // basic_400
)

/**
 * 차분의 기본 화면: 쉬는 앱이라 눈부시지 않게 다듬은 부드러운 다크.
 * Pleos 다크 토큰의 구조(바탕 < 카드 < 팝업, 글자 4단계)는 그대로 두고 대비만 낮췄다.
 *  - 순백 대신 따뜻한 미색 글자, 선택 항목도 순백 대신 크림색
 *  - 꽉 찬 버튼은 밝은 회색(Pleos 다크) 대신 한 단계 올린 짙은 회색
 *  - 상태 색(초록·파랑·빨강)은 채도를 조금 낮춤
 */
private const val SOFT_WHITE = 0xFFE8E6E1

private fun soft(alpha: Float) = Color(SOFT_WHITE).copy(alpha = alpha)

val SoftDarkPleos = PleosColors(
    dark = true,
    background = Color(0xFF18191C),
    popup = Color(0xFF2A2B30),
    surface = Color(0xFF222327),
    surfaceHigh = Color(0xFF2C2D32),
    surfaceAccent = Color(0xFF3A3B41),
    textPrimary = Color(SOFT_WHITE),
    textSecondary = soft(0.80f),
    textTertiary = soft(0.58f),
    textQuaternary = soft(0.30f),
    buttonBasic = soft(0.06f),
    buttonBasicPressed = soft(0.11f),
    buttonFilled = Color(0xFF3E4047),
    buttonFilledPressed = Color(0xFF4A4C54),
    onButtonFilled = Color(SOFT_WHITE),
    selected = Color(0xFFD9D5CC),
    onSelected = Color(0xFF1C1D20),
    active = Color(0xFF3CC98A),
    positive = Color(0xFF5B95F5),
    negative = Color(0xFFE8694D),
    switchOff = soft(0.12f),
    knobOff = Color(0xFF8E9197),
)

/**
 * 장면 사진 위에 놓는 요소는 테마와 상관없이 같은 색을 쓴다(가이드의 Static Color).
 * 휴식 화면·장면 카드의 사진 위 글자와 어둡게 누르는 막이 여기에 해당한다.
 */
object OnImage {
    val text = Color(WHITE) // static_light_100
    val textSecondary = white(0.84f)
    val textTertiary = white(0.64f)
    val scrim = Color(BLACK)
    /** 사진 위 반투명 패널 */
    val panel = black(0.62f)
    val favorite = Color(0xFFFFC93C)
}

/**
 * Pleos 타이포그래피 스케일(Foundations › Typography). 서체는 시스템 기본(Asta Sans)을 그대로 따른다.
 * Headline 60/56/48 · Title 40/36/32 · Body 30/28/26 · Label 24/20.
 */
object PleosType {
    val headlineS = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold)
    val titleL = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
    val titleM = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold)
    val titleS = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)
    val bodyL = TextStyle(fontSize = 30.sp, lineHeight = 42.sp)
    val bodyLStrong = bodyL.copy(fontWeight = FontWeight.Bold)
    val bodyM = TextStyle(fontSize = 28.sp, lineHeight = 40.sp)
    val bodyMStrong = bodyM.copy(fontWeight = FontWeight.Bold)
    val bodyS = TextStyle(fontSize = 26.sp, lineHeight = 36.sp)
    val bodySStrong = bodyS.copy(fontWeight = FontWeight.Bold)
    val labelM = TextStyle(fontSize = 24.sp, lineHeight = 34.sp)
    val labelS = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
}

private val LocalPleos = staticCompositionLocalOf { LightPleos }

/** 지금 테마의 색. `Palette.textPrimary`처럼 쓴다. */
val Palette: PleosColors
    @Composable @ReadOnlyComposable get() = LocalPleos.current

/**
 * 차분 테마. 기본은 부드러운 다크이고, 설정에서 고르면 시스템 주간(라이트)/야간(부드러운 다크)을 따른다.
 * Material 컴포넌트(입력창·슬라이더 등)도 같은 색을 쓰게 맞춘다.
 */
@Composable
fun ChabunTheme(followSystem: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (followSystem && !isSystemInDarkTheme()) LightPleos else SoftDarkPleos
    val scheme = (if (colors.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.textPrimary,
        onPrimary = colors.onSelected,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textTertiary,
        surfaceContainerHigh = colors.popup,
        outline = colors.surfaceAccent,
        outlineVariant = colors.surfaceAccent,
    )
    CompositionLocalProvider(LocalPleos provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
