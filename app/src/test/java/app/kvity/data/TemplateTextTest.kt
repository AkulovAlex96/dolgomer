package app.kvity.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTextTest {
    private val strict = "{имя}, срок возврата долга ({остаток}) {срок}. Просрочка {дней_просрочки} дн. Прошу вернуть в ближайшее время."

    private fun render(template: String, overdue: Long) =
        TemplateText.render(template, name = "Антон", amount = "10 000 ₽", remaining = "500 ₽", due = "не задан", overdueDays = overdue)

    @Test fun `not overdue drops the overdue sentence`() {
        assertEquals("Антон, срок возврата долга (500 ₽) не задан. Прошу вернуть в ближайшее время.", render(strict, 0))
    }

    @Test fun `overdue keeps it`() {
        assertEquals("Антон, срок возврата долга (500 ₽) не задан. Просрочка 5 дн. Прошу вернуть в ближайшее время.", render(strict, 5))
    }

    @Test fun `a sentence that also carries other variables stays`() {
        assertEquals("Долг 500 ₽, просрочка 0 дн.", render("Долг {остаток}, просрочка {дней_просрочки} дн.", 0))
    }

    @Test fun `a template that would become empty stays`() {
        assertEquals("Просрочка 0 дн.", render("Просрочка {дней_просрочки} дн.", 0))
    }

    @Test fun `templates without the variable are untouched`() {
        assertEquals("Привет, Антон! Осталось 500 ₽.", render("Привет, {имя}! Осталось {остаток}.", 0))
    }
}
