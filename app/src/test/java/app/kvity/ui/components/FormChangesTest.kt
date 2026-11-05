package app.kvity.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormChangesTest {
    @Test fun `same values are not dirty`() {
        assertFalse(FormChanges.isDirty(FormChanges.snapshot("a", 1, null), FormChanges.snapshot("a", 1, null)))
    }

    @Test fun `changed field is dirty and revert is clean again`() {
        val initial = FormChanges.snapshot("a", 1L)
        assertTrue(FormChanges.isDirty(initial, FormChanges.snapshot("ab", 1L)))
        assertFalse(FormChanges.isDirty(initial, FormChanges.snapshot("a", 1L)))
    }

    @Test fun `null differs from empty text and fields do not merge`() {
        assertTrue(FormChanges.isDirty(FormChanges.snapshot(null), FormChanges.snapshot("")))
        assertTrue(FormChanges.isDirty(FormChanges.snapshot("a", "b"), FormChanges.snapshot("ab", "")))
    }
}
