package com.chabun.app.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * 앱 안에서 고르는 화면 언어. 기본은 기기 언어(지원하지 않으면 한국어).
 * 음성 명령 인식은 한국어만 해석한다(STT 결과를 앱이 직접 해석하므로).
 */
enum class AppLanguage(val tag: String, private val country: String, val nativeName: String, val speaks: Boolean) {
    KOREAN("ko", "KR", "한국어", speaks = true),
    ENGLISH("en", "US", "English", speaks = true),
    // Pleos TTS ON_DEVICE 엔진은 ja-JP·zh-CN을 지원하지 않아(에뮬레이터 실측) 음성 안내 없이 화면 알림만 쓴다.
    JAPANESE("ja", "JP", "日本語", speaks = false),
    CHINESE("zh", "CN", "中文", speaks = false);

    /** 지역까지 붙인다. Pleos TTS(ON_DEVICE)는 "ja"처럼 언어만 주면 "unsupported locale: ja-"로 실패한다. */
    val locale: Locale get() = Locale(tag, country)

    companion object {
        fun of(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** 기기 언어가 지원 목록에 있으면 그것, 아니면 한국어 */
        fun device(): AppLanguage = of(Locale.getDefault().language) ?: KOREAN
    }
}

/**
 * 고른 언어로 리소스를 읽는 Context.
 * createConfigurationContext를 그대로 쓰면 Activity가 아니어서 Compose가 Activity(권한 요청 등)를 찾지 못하므로,
 * 원래 Context를 감싸고 리소스만 바꾼다.
 */
fun Context.localized(language: AppLanguage): Context {
    val localizedResources = createConfigurationContext(
        Configuration(resources.configuration).apply { setLocale(language.locale) }
    ).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}

/** 문자열 리소스를 지금 언어로 읽는다(엔진·뷰모델처럼 Compose 밖에서 쓴다). */
fun interface Text {
    operator fun invoke(@StringRes id: Int, vararg args: Any): String
}

/** [context]가 바뀌면 바로 따라가는 [Text] */
class LocalizedText(private var context: Context) : Text {
    fun use(context: Context) {
        this.context = context
    }

    override fun invoke(id: Int, vararg args: Any): String = context.getString(id, *args)
}
