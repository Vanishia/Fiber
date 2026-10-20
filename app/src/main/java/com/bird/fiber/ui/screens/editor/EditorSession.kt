package com.bird.fiber.ui.screens.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

data class EditorInsertionPoint(val generation: Long, val revision: Long, val selection: TextRange)

/** TextFieldValue 的唯一可修改会话，选区/composition 和内容版本一起保存。 */
internal class EditorSession(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var value = TextFieldValue()
        private set
    var revision = 0L
        private set
    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var compositionStart: TextFieldValue? = null
    private var lastKind: String? = null
    private var lastTime = 0L
    val canUndo get() = value.composition == null && undo.isNotEmpty()
    val canRedo get() = value.composition == null && redo.isNotEmpty()

    fun reset(initial: TextFieldValue) {
        value = initial
        revision = 0
        undo.clear()
        redo.clear()
        compositionStart = null
        lastKind = null
    }

    fun update(next: TextFieldValue) {
        val previous = value
        if (next.composition != null) {
            if (compositionStart == null) compositionStart = previous.copy(composition = null)
            lastKind = null
        } else if (compositionStart != null) {
            val start = compositionStart!!
            compositionStart = null
            if (start.text != next.text) record(start)
            lastKind = null
        } else if (previous.text != next.text) {
            val kind = editKind(previous, next)
            val now = clock()
            if (kind == null || kind != lastKind || now - lastTime > 1000) record(previous)
            else redo.clear()
            lastKind = kind
            lastTime = now
        } else if (previous.selection != next.selection) {
            lastKind = null
        }
        assign(next)
    }

    fun perform(operation: (TextFieldValue) -> TextFieldValue) {
        if (value.composition != null) return
        val next = operation(value)
        if (next.text != value.text) record(value)
        lastKind = null
        assign(next)
    }

    fun undo() {
        if (!canUndo) return
        redo.addLast(value.copy(composition = null))
        assign(undo.removeLast())
        lastKind = null
    }

    fun redo() {
        if (!canRedo) return
        undo.addLast(value.copy(composition = null))
        assign(redo.removeLast())
        lastKind = null
    }

    private fun assign(next: TextFieldValue) {
        if (value.text != next.text) revision++
        value = next
    }

    private fun record(previous: TextFieldValue) {
        undo.addLast(previous.copy(composition = null))
        redo.clear()
        var chars = undo.sumOf { it.text.length.toLong() }
        while (undo.size > 1 && (undo.size > 100 || chars > 8_000_000)) {
            chars -= undo.removeFirst().text.length
        }
    }

    private fun editKind(old: TextFieldValue, next: TextFieldValue): String? {
        if (!old.selection.collapsed || !next.selection.collapsed) return null
        val caret = old.selection.start
        val delta = next.text.length - old.text.length
        if (delta in 1..2 && next.selection.start == caret + delta &&
            sameAfterRemoval(next.text, caret, caret + delta, old.text) &&
            next.text.substring(caret, caret + delta).codePointCount(0, delta) == 1) return "insert"
        if (delta in -2..-1 && next.selection.start == caret + delta && caret + delta >= 0 &&
            sameAfterRemoval(old.text, caret + delta, caret, next.text)) return "backspace"
        if (delta in -2..-1 && next.selection.start == caret && caret - delta <= old.text.length &&
            sameAfterRemoval(old.text, caret, caret - delta, next.text)) return "delete"
        return null
    }

    private fun sameAfterRemoval(text: String, start: Int, end: Int, expected: String): Boolean =
        text.regionMatches(0, expected, 0, start) &&
            text.regionMatches(end, expected, start, text.length - end)
}
