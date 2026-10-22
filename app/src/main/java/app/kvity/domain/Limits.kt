package app.kvity.domain

/** Предельные длины текстовых полей: защищают списки и предпросмотр от гигантских значений. */
object Limits {
    const val NAME = 100
    const val NOTE = 500
    const val PHONE = 32
    const val TEMPLATE = 2000
}
