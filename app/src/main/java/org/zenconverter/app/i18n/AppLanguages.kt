package org.zenconverter.app.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.app.LocaleManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zenconverter.app.R
import org.zenconverter.app.settings.AppPreferences
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

internal data class LanguageOption(val tag: String) {
    val nativeName: String
        get() = Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
}

object AppLanguages {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    internal fun options(context: Context): List<LanguageOption> = buildList {
        add(LanguageOption(""))
        context.resources.getXml(R.xml.locales_config).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        ?.let { add(LanguageOption(it)) }
                }
                parser.next()
            }
        }
    }

    internal fun selectedOption(context: Context, configuration: Configuration): LanguageOption {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selected = current.substringBefore(',')
        return options(context).firstOrNull { it.tag == selected }
            ?: options(context).firstOrNull { option ->
                option.tag.isNotEmpty() && Locale.forLanguageTag(option.tag).language ==
                    configuration.locales[0].language && Locale.forLanguageTag(option.tag).script ==
                    configuration.locales[0].script
            }
            ?: LanguageOption("")
    }

    internal fun select(option: LanguageOption) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
        configurationChanged()
    }

    fun migrate(context: Context) {
        val legacy = AppPreferences.legacyLanguage(context) ?: return
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            val tag = when (legacy) {
                "English" -> "en"
                "SimplifiedChinese" -> "zh-Hans"
                "TraditionalChinese" -> "zh-Hant"
                else -> ""
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        AppPreferences.clearLegacyLanguage(context)
        configurationChanged()
    }

    fun configurationChanged() {
        mutableRevision.value += 1
    }

    fun localizedContext(context: Context): Context {
        val selected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManagerCompat.getApplicationLocales(context)
        } else AppCompatDelegate.getApplicationLocales()
        val system = LocaleManagerCompat.getSystemLocales(context)
        val preferred = if (selected.isEmpty) system else selected
        val supportedTags = options(context).map { it.tag }.filter { it.isNotEmpty() }.toTypedArray()
        val locale = preferred.getFirstMatch(supportedTags) ?: Locale.ENGLISH
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        if (configuration == context.resources.configuration) return context
        val localized = context.createConfigurationContext(configuration)
        return object : ContextWrapper(context) {
            override fun getResources() = localized.resources
            override fun getAssets() = localized.assets
        }
    }
}
