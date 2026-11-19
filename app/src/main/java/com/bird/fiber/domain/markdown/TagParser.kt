package com.bird.fiber.domain.markdown

import org.commonmark.node.*
import org.commonmark.parser.Parser
import java.text.Normalizer
import java.util.Locale

data class ParsedTag(val path: String, val displayPath: String)

/** One parser for saved content, imports and backfill. Limits apply to the whole path. */
object TagParser {
    const val CURRENT_VERSION = 1
    const val MAX_LENGTH = 256
    const val MAX_DEPTH = 16
    private val segment = Regex("[\\p{L}\\p{N}_-]+")
    private val url = Regex("(?i)(?:[a-z][a-z0-9+.-]*://|www\\.)[^\\s<>]+")
    private val candidate = Regex("#[\\p{L}\\p{N}_/-]+")
    private val entity = Regex("&(?:#[xX][0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]+);")
    private const val ESCAPED_HASH = '\uE000'
    private const val TAG_UNDERSCORE = '\uE001'
    private const val RAW_BOUNDARY = '\uE002'

    fun normalize(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun manual(input: String): ParsedTag? {
        val display = Normalizer.normalize(input.trim().removePrefix("#"), Normalizer.Form.NFC)
        val parts = display.split('/')
        return if (display.length in 1..MAX_LENGTH && parts.size <= MAX_DEPTH && parts.all { segment.matches(it) })
            ParsedTag(normalize(display), display) else null
    }

    fun parse(markdown: String): List<ParsedTag> {
        // Preserve escaped hashes before CommonMark decodes escapes in Text nodes.
        val source = StringBuilder()
        var slashes = 0
        val withoutFrontmatter = Normalizer.normalize(markdown.removePrefix("\uFEFF"), Normalizer.Form.NFC).let { text ->
            if (Regex("^---[ \\t]*\\r?\\n").containsMatchIn(text)) {
                val end = Regex("(?m)^(?:---|\\.\\.\\.)[ \\t]*\\r?$").find(text, text.indexOf('\n') + 1)
                if (end == null) "" else text.substring(end.range.last + 1)
            } else text
        }
        withoutFrontmatter.forEach { ch ->
            source.append(when {
                slashes % 2 == 1 && ch == '#' -> ESCAPED_HASH
                slashes % 2 == 1 && ch in "_-/" -> RAW_BOUNDARY
                else -> ch
            })
            slashes = if (ch == '\\') slashes + 1 else 0
        }
        val result = linkedMapOf<String, ParsedTag>()
        // A tag's underscores are literal allowed characters, even where Markdown
        // would interpret them as emphasis. Entities must not synthesize raw '#'.
        val protected = candidate.replace(entity.replace(source.toString(), RAW_BOUNDARY.toString())) {
            it.value.replace('_', TAG_UNDERSCORE)
        }
        val root = Parser.builder().build().parse(protected)
        root.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                val literal = text.literal.replace(TAG_UNDERSCORE, '_')
                val urls = url.findAll(literal).map { it.range }.toList()
                var at = 0
                while (at < literal.length) {
                    if (literal[at] != '#' || urls.any { at in it } ||
                        (at > 0 && (literal[at - 1] == '#' || literal[at - 1] == ESCAPED_HASH || literal[at - 1] == '/' ||
                            isTagCharacter(literal.codePointBefore(at))))) { at++; continue }
                    val start = at + 1
                    var cursor = start
                    var validEnd = start
                    var depth = 0
                    while (depth < MAX_DEPTH) {
                        val segmentStart = cursor
                        while (cursor < literal.length && isTagCharacter(literal.codePointAt(cursor))) {
                            cursor += Character.charCount(literal.codePointAt(cursor))
                        }
                        if (cursor == segmentStart || cursor - start > MAX_LENGTH) break
                        validEnd = cursor
                        depth++
                        if (cursor >= literal.length || literal[cursor] != '/') break
                        cursor++
                    }
                    if (validEnd > start) {
                        val display = Normalizer.normalize(literal.substring(start, validEnd), Normalizer.Form.NFC)
                        val key = normalize(display)
                        result.putIfAbsent(key, ParsedTag(key, display))
                    }
                    at = maxOf(at + 1, cursor)
                }
            }

            override fun visit(code: Code) = Unit
            override fun visit(code: FencedCodeBlock) = Unit
            override fun visit(code: IndentedCodeBlock) = Unit
            override fun visit(html: HtmlInline) = Unit
            override fun visit(html: HtmlBlock) {
                if (html.literal.trimStart().startsWith("<!--")) {
                    val outside = html.literal.replace(Regex("<!--[\\s\\S]*?(?:-->|$)"), " ")
                    visit(Text(outside))
                }
            }
        })
        return result.values.toList()
    }

    private fun isTagCharacter(codePoint: Int) = Character.isLetterOrDigit(codePoint) ||
        Character.getType(codePoint) == Character.LETTER_NUMBER.toInt() ||
        Character.getType(codePoint) == Character.OTHER_NUMBER.toInt() ||
        codePoint == '_'.code || codePoint == '-'.code
}
