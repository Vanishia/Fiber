package com.bird.fiber.ui.screens.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorSessionTest {
    private fun text(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun continuousTypingMerges_butPasteAndToolAreSeparateSteps() {
        val session = EditorSession { 0 }
        session.update(text("a"))
        session.update(text("ab"))
        session.update(text("ab粘贴"))
        session.perform { EditorOperations.format(it, EditorFormat.Bold) }
        session.undo()
        assertEquals("ab粘贴", session.value.text)
        session.undo()
        assertEquals("ab", session.value.text)
        session.undo()
        assertEquals("", session.value.text)
        session.redo()
        assertEquals("ab", session.value.text)
    }

    @Test
    fun compositionCandidatesCreateOneCommittedHistoryEntry() {
        val session = EditorSession()
        session.reset(text("前"))
        session.update(TextFieldValue("前ni", TextRange(3), TextRange(1, 3)))
        session.update(TextFieldValue("前你", TextRange(2), TextRange(1, 2)))
        assertFalse(session.canUndo)
        session.perform { EditorOperations.format(it, EditorFormat.Bold) }
        assertEquals("前你", session.value.text)
        session.update(text("前你"))
        session.undo()
        assertEquals("前", session.value.text)
        session.redo()
        assertEquals("前你", session.value.text)
        assertNull(session.value.composition)
    }

    @Test
    fun movingSelectionDoesNotIncrementRevisionOrEnterHistory_andBreaksTypingGroup() {
        val session = EditorSession { 0 }
        session.update(text("a"))
        val revision = session.revision
        session.update(text("a", 0))
        assertEquals(revision, session.revision)
        session.update(text("ba", 1))
        session.undo()
        assertEquals("a", session.value.text)
        assertEquals(TextRange(0), session.value.selection)
    }

    @Test
    fun pauseSeparatesHistory_andNewEditClearsRedo() {
        var now = 0L
        val session = EditorSession { now }
        session.update(text("a"))
        now = 1500
        session.update(text("ab"))
        session.undo()
        assertEquals("a", session.value.text)
        session.update(text("ac"))
        assertFalse(session.canRedo)
    }

    @Test
    fun canceledCompositionCreatesNoUndoEntry() {
        val session = EditorSession()
        session.update(TextFieldValue("候选", TextRange(2), TextRange(0, 2)))
        session.update(text(""))
        assertFalse(session.canUndo)
    }
}
