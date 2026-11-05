package app.kvity.domain

import java.util.Locale

/**
 * Выбор текста по языку приложения для кода без доступа к Context (форматтеры, подписи событий).
 * Язык приложения следует системному: английский при «en», иначе русский. Тексты экранов живут в строковых ресурсах.
 */
object LocaleText {
    fun isEnglish(locale: Locale = Locale.getDefault()): Boolean = locale.language == "en"

    fun pick(ru: String, en: String): String = if (isEnglish()) en else ru
}
