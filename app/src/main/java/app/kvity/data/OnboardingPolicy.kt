package app.kvity.data

/** Чистая логика решения «показывать ли онбординг». */
object OnboardingPolicy {
    /**
     * Начальное значение флага «онбординг пройден» при чтении настроек.
     * @param storedFlag сохранённое значение флага или null, если ключа ещё нет (первое чтение после обновления или установки)
     * @param installedAtMillis сохранённое время установки; 0 — настроек ещё не было, то есть это новая установка
     *   (KvityApp проставляет installedAt после создания SettingsStore, поэтому на первом чтении он ещё 0)
     */
    fun initialDone(storedFlag: Boolean?, installedAtMillis: Long): Boolean =
        storedFlag ?: (installedAtMillis != 0L)

    fun shouldShow(onboardingDone: Boolean): Boolean = !onboardingDone
}
