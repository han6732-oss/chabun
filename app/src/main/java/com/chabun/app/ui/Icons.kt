package com.chabun.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 마이크 아이콘. 이모지(🎙)는 색과 입체감이 글자·차량 UI의 가는 선 아이콘과 따로 놀아서 선만으로 그린다.
 * Icon의 tint로 버튼 글자색을 그대로 따른다.
 */
internal val MicIcon: ImageVector = ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        // 머리(캡슐)
        moveTo(9f, 6f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 6f)
        lineTo(15f, 11f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 11f)
        close()
        // 받침 고리
        moveTo(5.5f, 11f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18.5f, 11f)
        // 대와 밑받침
        moveTo(12f, 17.5f)
        lineTo(12f, 21f)
        moveTo(8.5f, 21f)
        lineTo(15.5f, 21f)
    }
}.build()

/** 즐겨찾기 별(빈 별). 마이크와 같은 굵기의 선으로 그린다. */
internal val StarOutlineIcon: ImageVector = ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineJoin = StrokeJoin.Round,
    ) { star() }
}.build()

/** 즐겨찾기 별(채운 별) */
internal val StarFilledIcon: ImageVector = ImageVector.Builder("StarFilled", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineJoin = StrokeJoin.Round,
    ) { star() }
}.build()

/** 꼭짓점 다섯 개짜리 별: 바깥 반지름 9.5, 안쪽 3.9, 중심 (12, 12.5) */
private fun PathBuilder.star() {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) 9.5f else 3.9f
        val angle = -PI / 2 + i * PI / 5
        val x = 12f + radius * cos(angle).toFloat()
        val y = 12.5f + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** 선 아이콘 공통: 시스템 UI 아이콘처럼 1.8 굵기의 둥근 선 */
private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

internal val PlusIcon = lineIcon("Plus") {
    moveTo(12f, 5f); lineTo(12f, 19f)
    moveTo(5f, 12f); lineTo(19f, 12f)
}

internal val MinusIcon = lineIcon("Minus") { moveTo(5f, 12f); lineTo(19f, 12f) }

internal val CheckIcon = lineIcon("Check") {
    moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
}

internal val CloseIcon = lineIcon("Close") {
    moveTo(6f, 6f); lineTo(18f, 18f)
    moveTo(18f, 6f); lineTo(6f, 18f)
}

/** 원 안의 느낌표(경고·실패) */
internal val AlertIcon = lineIcon("Alert") {
    moveTo(21f, 12f)
    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
    moveTo(12f, 7.5f); lineTo(12f, 13f)
    moveTo(12f, 16.3f); lineTo(12f, 16.5f)
}

/** 톱니 8개짜리 설정 아이콘 */
internal val GearIcon = lineIcon("Gear") {
    for (tooth in 0 until 8) {
        val a = tooth * PI / 4
        fun point(da: Double, r: Float) = (12f + r * cos(a + da).toFloat()) to (12f + r * sin(a + da).toFloat())
        val (x0, y0) = point(-0.30, 7.2f)
        val (x1, y1) = point(-0.17, 9.4f)
        val (x2, y2) = point(0.17, 9.4f)
        val (x3, y3) = point(0.30, 7.2f)
        if (tooth == 0) moveTo(x0, y0) else lineTo(x0, y0)
        lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3)
    }
    close()
    moveTo(15f, 12f)
    arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 12f)
    arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15f, 12f)
}

/** 번개(충전) */
internal val BoltIcon = lineIcon("Bolt") {
    moveTo(13f, 3f); lineTo(5f, 13.5f); lineTo(11.5f, 13.5f); lineTo(10.5f, 21f); lineTo(19f, 10f); lineTo(12.5f, 10f); close()
}

/** 초승달(휴식) */
internal val MoonIcon = lineIcon("Moon") {
    moveTo(20f, 14.5f)
    arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.5f, 4f)
    arcTo(7.6f, 7.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 14.5f)
    close()
}

/** 음표(소리) */
internal val NoteIcon = lineIcon("Note") {
    moveTo(9f, 17.5f); lineTo(9f, 5.5f); lineTo(19f, 3.5f); lineTo(19f, 15.5f)
    moveTo(9f, 17.5f)
    arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 17.5f)
    arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9f, 17.5f)
    moveTo(19f, 15.5f)
    arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 15.5f)
    arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19f, 15.5f)
}

/** 내비게이션 화살표(시스템 하단 바의 내비 아이콘과 같은 모양) */
internal val NavigationIcon = lineIcon("Navigation") {
    moveTo(12f, 3f); lineTo(19.5f, 20f); lineTo(12f, 15.5f); lineTo(4.5f, 20f); close()
}

/** 연필(편집) */
internal val EditIcon = lineIcon("Edit") {
    moveTo(4f, 20f); lineTo(4.8f, 16.2f); lineTo(15.6f, 5.4f); lineTo(18.6f, 8.4f); lineTo(7.8f, 19.2f); close()
    moveTo(13.6f, 7.4f); lineTo(16.6f, 10.4f)
}
