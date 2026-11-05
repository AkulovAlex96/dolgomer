package app.kvity.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Язык интерфейса: как в системе или один из поддерживаемых. [tag] пустой для «как в системе». */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RU("ru"),
    EN("en");

    companion object {
        /** Язык по списку тегов приложения («ru», «en-US», пусто); неизвестный язык считается системным. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').trim().lowercase()
            val lang = first.substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it != SYSTEM && it.tag == lang } ?: SYSTEM
        }

        /** Язык, выбранный в приложении (хранится AppCompat/системой). */
        fun current(): AppLanguage = fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

        /** Применяет язык: AppCompat пересоздаёт активность, так что тексты меняются сразу. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
