package com.parallelc.micts.config
import com.parallelc.micts.R
import com.parallelc.micts.BuildConfig
import java.util.Locale

enum class Language(val id: Int, val toLocale: () -> Locale) {
    FollowSystem(R.string.follow_system, { Locale.getDefault() }),
    Arabic(R.string.arabic, { Locale("ar") }),
    ChineseSimplified(R.string.chinese_simplified, { Locale.SIMPLIFIED_CHINESE }),
    ChineseTraditional(R.string.chinese_traditional, { Locale.TRADITIONAL_CHINESE }),
    English(R.string.english, { Locale.ENGLISH }),
    Greek(R.string.greek, { Locale("el") }),
    Japanese(R.string.japanese, { Locale.JAPANESE }),
    Odia(R.string.odia, { Locale("or") }),
    Persian(R.string.persian, { Locale("fa") }),
    Russian(R.string.russian, { Locale("ru") }),
    Spanish(R.string.spanish, { Locale("es") }),
    Turkish(R.string.turkish, { Locale("tr") }),
    Vietnamese(R.string.vietnamese, { Locale("vi") }),
}

object AppConfig {
    const val CONFIG_NAME = "app_config"
    const val KEY_LANGUAGE = "language"
    const val KEY_DEFAULT_DELAY = "default_delay"
    const val KEY_TILE_DELAY = "tile_delay"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_ASYNC_TRIGGER = "async_trigger"
    const val KEY_PREPARE_GOOGLE = "prepare_google"
    const val KEY_FRESH_SESSION = "fresh_assistant_session"

    val DEFAULT_CONFIG = mapOf<String, Any>(
        KEY_LANGUAGE to Language.FollowSystem.ordinal,
        KEY_DEFAULT_DELAY to 0L,
        KEY_TILE_DELAY to 400L,
        KEY_VIBRATE to false,
        KEY_ASYNC_TRIGGER to false,
        KEY_PREPARE_GOOGLE to (BuildConfig.IS_PREVIEW && BuildConfig.APP_NAME == "MiCTS"),
        KEY_FRESH_SESSION to (BuildConfig.IS_PREVIEW && BuildConfig.APP_NAME == "MiCTS"),
    )
}
