package com.example

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.text.TextUtils
import android.view.View
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val flag: String,
    val displayName: String,
    val nativeName: String
) {
    AUTO("auto", "\uD83C\uDF10", "Automatic (Device)", "Auto"),
    ENGLISH("en", "\uD83C\uDDFA\uD83C\uDDF8", "English", "English"),
    MANDARIN("zh-CN", "\uD83C\uDDE8\uD83C\uDDF3", "Mandarin Chinese", "中文 (简体)"),
    HINDI("hi", "\uD83C\uDDEE\uD83C\uDDF3", "Hindi", "हिन्दी"),
    SPANISH("es", "\uD83C\uDDEA\uD83C\uDDF8", "Spanish", "Español"),
    FRENCH("fr", "\uD83C\uDDEB\uD83C\uDDF7", "French", "Français"),
    ARABIC("ar", "\uD83C\uDDF8\uD83C\uDDE6", "Standard Arabic", "العربية"),
    BENGALI("bn", "\uD83C\uDDE7\uD83C\uDDE9", "Bengali", "বাংলা"),
    PORTUGUESE_BR("pt-BR", "\uD83C\uDDE7\uD83C\uDDF7", "Portuguese (Brazil)", "Português (Brasil)"),
    PORTUGUESE_PT("pt-PT", "\uD83C\uDDF5\uD83C\uDDF9", "Portuguese (Portugal)", "Português (Portugal)"),
    RUSSIAN("ru", "\uD83C\uDDF7\uD83C\uDDFA", "Russian", "Русский"),
    URDU("ur", "\uD83C\uDDF5\uD83C\uDDF0", "Urdu", "اردو");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            val normalized = code?.trim().orEmpty()
            return entries.firstOrNull { it.code.equals(normalized, ignoreCase = true) } ?: AUTO
        }
    }
}

object ArenaLocaleHelper {
    private const val PREFS_NAME = "arena_internal_prefs"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_NOTIFY_ON_FINISH = "notify_on_finish"

    fun getDeviceSystemLocale(): Locale {
        return try {
            val sysConfig = Resources.getSystem().configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!sysConfig.locales.isEmpty) sysConfig.locales[0] else Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                sysConfig.locale ?: Locale.getDefault()
            }
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }

    fun resolveEffectiveLanguageCode(
        configuredCode: String?,
        deviceLocale: Locale = getDeviceSystemLocale()
    ): String {
        val selected = AppLanguage.fromCode(configuredCode)
        if (selected != AppLanguage.AUTO) {
            return selected.code
        }
        return matchDeviceLocaleToSupportedCode(deviceLocale)
    }

    fun matchDeviceLocaleToSupportedCode(deviceLocale: Locale): String {
        val lang = deviceLocale.language.lowercase(Locale.ROOT)
        val country = deviceLocale.country.uppercase(Locale.ROOT)
        return when (lang) {
            "en" -> AppLanguage.ENGLISH.code
            "zh" -> AppLanguage.MANDARIN.code
            "hi" -> AppLanguage.HINDI.code
            "es" -> AppLanguage.SPANISH.code
            "fr" -> AppLanguage.FRENCH.code
            "ar" -> AppLanguage.ARABIC.code
            "bn" -> AppLanguage.BENGALI.code
            "pt" -> if (country == "PT") AppLanguage.PORTUGUESE_PT.code else AppLanguage.PORTUGUESE_BR.code
            "ru" -> AppLanguage.RUSSIAN.code
            "ur" -> AppLanguage.URDU.code
            else -> AppLanguage.ENGLISH.code
        }
    }

    fun toLocale(effectiveCode: String): Locale {
        return when (effectiveCode) {
            AppLanguage.MANDARIN.code -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.PORTUGUESE_BR.code -> Locale("pt", "BR")
            AppLanguage.PORTUGUESE_PT.code -> Locale("pt", "PT")
            AppLanguage.HINDI.code -> Locale("hi")
            AppLanguage.SPANISH.code -> Locale("es")
            AppLanguage.FRENCH.code -> Locale("fr")
            AppLanguage.ARABIC.code -> Locale("ar")
            AppLanguage.BENGALI.code -> Locale("bn")
            AppLanguage.RUSSIAN.code -> Locale("ru")
            AppLanguage.URDU.code -> Locale("ur")
            else -> Locale.ENGLISH
        }
    }

    fun getLayoutDirection(configuredCode: String?): LayoutDirection {
        val effective = resolveEffectiveLanguageCode(configuredCode)
        val locale = toLocale(effective)
        return if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    fun getPersistedLanguageCode(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_APP_LANGUAGE, AppLanguage.AUTO.code) ?: AppLanguage.AUTO.code
        } catch (_: Exception) {
            AppLanguage.AUTO.code
        }
    }

    fun persistLanguageCode(context: Context, code: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_APP_LANGUAGE, AppLanguage.fromCode(code).code).apply()
        } catch (_: Exception) {
        }
    }

    fun persistNotifyPreference(context: Context, enabled: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_NOTIFY_ON_FINISH, enabled).apply()
        } catch (_: Exception) {
        }
    }

    fun isNotifyPreferenceEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_NOTIFY_ON_FINISH, true)
        } catch (_: Exception) {
            true
        }
    }

    fun localizedContext(
        base: Context,
        configuredCode: String = getPersistedLanguageCode(base)
    ): Context {
        val effectiveCode = resolveEffectiveLanguageCode(configuredCode)
        val locale = toLocale(effectiveCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun wrapAttachBaseContext(base: Context): Context {
        val savedCode = getPersistedLanguageCode(base)
        return localizedContext(base, savedCode)
    }
}
