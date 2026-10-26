package com.chabun.app.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.chabun.app.R
import com.chabun.app.scene.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@get:DrawableRes
internal val Backdrop.drawable: Int
    get() = when (this) {
        Backdrop.CABIN -> R.drawable.scene_cabin
        Backdrop.SPACE -> R.drawable.scene_space
        Backdrop.RAIN_CABIN -> R.drawable.scene_rain_cabin
        Backdrop.FOREST -> R.drawable.scene_forest
        Backdrop.FIRST_SUITE -> R.drawable.scene_first_suite
        Backdrop.HOTEL_SUITE -> R.drawable.scene_hotel_suite
    }

/**
 * 세로 이미지를 가로 화면에 꽉 채울 때 남길 부분. 이미지마다 구도의 중심이 달라 세로 위치를 따로 맞춘다.
 * (비행기: 날개와 구름, 우주: 지구 대기권 경계, 오두막: 빗방울 맺힌 창, 숲: 안개 낀 숲길 안쪽,
 *  퍼스트 스위트: 창문·화면·침대 머리, 호텔 스위트: 비 내리는 창과 스탠드·의자)
 */
internal val Backdrop.landscapeFocus: Alignment
    get() = when (this) {
        Backdrop.CABIN -> BiasAlignment(0f, 0.05f)
        Backdrop.SPACE -> BiasAlignment(0f, -0.35f)
        Backdrop.RAIN_CABIN -> BiasAlignment(0f, -0.3f)
        Backdrop.FOREST -> BiasAlignment(0f, -0.1f)
        Backdrop.FIRST_SUITE -> BiasAlignment(0f, -0.4f)
        Backdrop.HOTEL_SUITE -> BiasAlignment(0f, -0.35f)
    }

/** 쓰임새별 디코딩 크기. 원본(793×1983)을 그대로 풀면 메인 스레드가 오래 멈춰 필요한 만큼만 줄여 읽는다. */
internal enum class BackdropSize(val sampleSize: Int) {
    /** 휴식 화면 전체 배경 */
    FULL(1),
    /** 장면 카드(약 380dp 폭) */
    CARD(2),
    /** 편집기 썸네일 */
    THUMB(4),
}

private val cache = LruCache<Pair<Int, BackdropSize>, ImageBitmap>(12)

/** 배경 이미지를 IO 스레드에서 디코딩해 돌려준다. 준비되기 전에는 null. */
@Composable
internal fun rememberBackdrop(backdrop: Backdrop, size: BackdropSize): State<ImageBitmap?> {
    val resources = LocalContext.current.resources
    val key = backdrop.drawable to size
    return produceState(initialValue = cache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { decode(resources, key.first, size) }.also { cache.put(key, it) }
    }
}

private fun decode(resources: Resources, @DrawableRes id: Int, size: BackdropSize): ImageBitmap =
    BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inSampleSize = size.sampleSize }).asImageBitmap()
