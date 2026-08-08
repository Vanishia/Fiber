package com.bird.fiber.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssociationMenuTest {

    @Test
    fun trigger_isDetectedAtLineStartOrAfterWhitespace() {
        assertEquals(0, findAssociationTrigger(TextFieldValue("@", TextRange(1))))
        assertEquals(3, findAssociationTrigger(TextFieldValue("hi @", TextRange(4))))
        assertEquals(3, findAssociationTrigger(TextFieldValue("hi\n@", TextRange(4))))
    }

    @Test
    fun trigger_isNotDetectedInsideEmailOrAfterMoreInput() {
        assertNull(findAssociationTrigger(TextFieldValue("a@", TextRange(2))))
        assertNull(findAssociationTrigger(TextFieldValue("@image", TextRange(6))))
    }

    @Test
    fun removeTrigger_preservesSurroundingTextAndCaret() {
        val result = removeAssociationTrigger(TextFieldValue("hi @there", TextRange(4)), 3)

        assertEquals("hi there", result.text)
        assertEquals(TextRange(3), result.selection)
    }
}
