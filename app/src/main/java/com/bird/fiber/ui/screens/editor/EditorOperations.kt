package com.bird.fiber.ui.screens.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class EditorFormat { Heading, Bold, BulletList, TaskList, CodeBlock, Quote, Italic }

/** 所有偏移均为 TextFieldValue 的 UTF-16 偏移，保留反向选区和原有换行符。 */
internal object EditorOperations {
    fun replace(value: TextFieldValue, text: String): TextFieldValue {
        val start = value.selection.min
        return TextFieldValue(value.text.replaceRange(start, value.selection.max, text), TextRange(start + text.length))
    }

    fun format(value: TextFieldValue, format: EditorFormat): TextFieldValue = when (format) {
        EditorFormat.Bold -> wrap(value, "**")
        EditorFormat.Italic -> wrap(value, "*")
        EditorFormat.Heading -> lines(value) { line ->
            val heading = Regex("^(#{1,6}) ").find(line)
            if (heading == null) "# $line" else {
                val level = heading.groupValues[1].length
                (if (level == 6) "" else "#".repeat(level + 1) + " ") + line.drop(level + 1)
            }
        }
        EditorFormat.BulletList -> prefix(value, "- ", Regex("^[-*+] "))
        EditorFormat.TaskList -> prefix(value, "- [ ] ", Regex("^[-*+] \\[[ xX]] "))
        EditorFormat.Quote -> prefix(value, "> ", Regex("^> ?"))
        EditorFormat.CodeBlock -> codeBlock(value)
    }

    private fun wrap(value: TextFieldValue, marker: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end)
        val hasInner = selected.length >= marker.length * 2 && selected.startsWith(marker) && selected.endsWith(marker)
        if (hasInner) {
            val inner = selected.drop(marker.length).dropLast(marker.length)
            return selectedResult(value, start, end, inner, start, start + inner.length)
        }
        if (start >= marker.length && value.text.substring(start - marker.length, start) == marker &&
            value.text.substring(end).startsWith(marker)) {
            return selectedResult(value, start - marker.length, end + marker.length, selected,
                start - marker.length, end - marker.length)
        }
        return selectedResult(value, start, end, marker + selected + marker, start + marker.length, end + marker.length)
    }

    private fun selectedResult(value: TextFieldValue, start: Int, end: Int, replacement: String, selectionStart: Int, selectionEnd: Int): TextFieldValue {
        val selection = if (value.selection.reversed) TextRange(selectionEnd, selectionStart) else TextRange(selectionStart, selectionEnd)
        return TextFieldValue(value.text.replaceRange(start, end, replacement), selection)
    }

    private fun prefix(value: TextFieldValue, marker: String, pattern: Regex): TextFieldValue {
        val selectedLines = lineBounds(value).let { value.text.substring(it.first, it.second) }.split(Regex("\\r\\n|\\r|\\n"))
        val remove = selectedLines.all { pattern.containsMatchIn(it) }
        return lines(value) { if (remove) it.replaceFirst(pattern, "") else marker + it }
    }

    private fun lineBounds(value: TextFieldValue): Pair<Int, Int> {
        val text = value.text
        var start = value.selection.min
        while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') start--
        var end = value.selection.max
        // 选区恰好结束在下一行行首时，不修改未选中的那一行。
        if (end > value.selection.min && end > 0 && text[end - 1] == '\n') {
            end--
            if (end > 0 && text[end - 1] == '\r') end--
        } else if (end > value.selection.min && end > 0 && text[end - 1] == '\r') end--
        else while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
        return start to end
    }

    private fun lines(value: TextFieldValue, transform: (String) -> String): TextFieldValue {
        val (start, end) = lineBounds(value)
        val source = value.text.substring(start, end)
        val result = StringBuilder()
        val edits = mutableListOf<Triple<Int, Int, Int>>()
        var offset = 0
        val separators = Regex("\\r\\n|\\r|\\n").findAll(source).toList()
        for (separator in separators + listOf(null)) {
            val lineEnd = separator?.range?.first ?: source.length
            val old = source.substring(offset, lineEnd)
            val new = transform(old)
            // 前缀修改在公共后缀之前发生；选区位于原正文时维持对应字符。
            var suffix = 0
            while (suffix < old.length && suffix < new.length && old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
            edits += Triple(start + offset, old.length - suffix, new.length - suffix)
            result.append(new)
            if (separator != null) {
                result.append(separator.value)
                offset = separator.range.last + 1
            }
        }
        fun map(position: Int): Int {
            var delta = 0
            for ((at, removed, inserted) in edits) {
                if (position < at) break
                if (position < at + removed) return at + delta + inserted
                delta += inserted - removed
            }
            return position + delta
        }
        return TextFieldValue(value.text.replaceRange(start, end, result.toString()),
            TextRange(map(value.selection.start), map(value.selection.end)))
    }

    private fun codeBlock(value: TextFieldValue): TextFieldValue {
        val newline = Regex("\\r\\n|\\r|\\n").find(value.text)?.value ?: "\n"
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end)
        val longestFence = Regex("`+").findAll(selected).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestFence + 1))
        val before = if (start == 0 || value.text[start - 1] == '\n' || value.text[start - 1] == '\r') "" else newline
        val after = if (end == value.text.length || value.text[end] == '\n' || value.text[end] == '\r') "" else newline
        val prefix = before + fence + newline
        val suffix = (if (selected.endsWith(newline)) "" else newline) + fence + after
        return selectedResult(value, start, end, prefix + selected + suffix, start + prefix.length, end + prefix.length)
    }
}
