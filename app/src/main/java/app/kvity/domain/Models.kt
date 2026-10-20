package app.kvity.domain

/** Направление долга: «мне должны» или «я должен». */
enum class Direction { OWED_TO_ME, I_OWE }

/** Условия возврата: одна дата, график платежей или без срока. */
enum class TermType { ONE_TIME, SCHEDULE, NONE }
