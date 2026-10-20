package com.bird.fiber.ui.screens.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class EditorOperationsTest {
    @Test
    fun boldPreservesReversedChineseAndEmojiSelection_andToggles() {
        val value = TextFieldValue("前中文😀后", TextRange(5, 1))
        val bold = EditorOperations.format(value, EditorFormat.Bold)
        assertEquals("前**中文😀**后", bold.text)
        assertEquals(TextRange(7, 3), bold.selection)
        assertEquals(value, EditorOperations.format(bold, EditorFormat.Bold))
    }

    @Test
    fun emptyBoldPlacesCaretInsideMarkers() {
        val result = EditorOperations.format(TextFieldValue(), EditorFormat.Bold)
        assertEquals("****", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun multilinePrefixesPreserveCrLfAndSelectionDirection() {
        val value = TextFieldValue("甲\r\n乙\r\n丙", TextRange(4, 0))
        val list = EditorOperations.format(value, EditorFormat.BulletList)
        assertEquals("- 甲\r\n- 乙\r\n丙", list.text)
        assertEquals(TextRange(8, 2), list.selection)
        assertEquals(value, EditorOperations.format(list, EditorFormat.BulletList))
    }

    @Test
    fun selectionEndingAtNextLineStartDoesNotFormatThatLine() {
        val value = TextFieldValue("甲\n乙\n", TextRange(0, 2))
        assertEquals("> 甲\n乙\n", EditorOperations.format(value, EditorFormat.Quote).text)
    }

    @Test
    fun taskListsToggleCheckedAndUncheckedPrefixes() {
        val value = TextFieldValue("- [x] 已办\n- [ ] 待办", TextRange(0, 17))
        assertEquals("已办\n待办", EditorOperations.format(value, EditorFormat.TaskList).text)
    }

    @Test
    fun headingCyclesThroughSixLevelsThenPlainText() {
        var value = TextFieldValue("标题")
        repeat(6) { index ->
            value = EditorOperations.format(value, EditorFormat.Heading)
            assertEquals("#".repeat(index + 1) + " 标题", value.text)
        }
        assertEquals("标题", EditorOperations.format(value, EditorFormat.Heading).text)
    }

    @Test
    fun codeFenceIsLongerThanSelectedBackticks_andPreservesCrLf() {
        val value = TextFieldValue("前\r\n```\r\n后", TextRange(3, 6))
        val result = EditorOperations.format(value, EditorFormat.CodeBlock)
        assertEquals("前\r\n````\r\n```\r\n````\r\n后", result.text)
        assertEquals(TextRange(9, 12), result.selection)
    }

    @Test
    fun imageReplacementSupportsReversedSelectionAtDocumentEnd() {
        val result = EditorOperations.replace(TextFieldValue("前后", TextRange(2, 1)), "![图](a)")
        assertEquals("前![图](a)", result.text)
        assertEquals(TextRange(result.text.length), result.selection)
    }
}
