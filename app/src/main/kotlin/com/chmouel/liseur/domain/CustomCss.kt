package com.chmouel.liseur.domain

/**
 * The reader's own stylesheet, made to win and made to stay on the device,
 * before any book sees it.
 *
 * Two things are done to what the reader typed (see
 * `docs/adr/0079-custom-css.md`):
 *
 * - Every declaration is marked `!important`. Readium's user settings and
 *   most publishers' rules are themselves important, and a rule that loses
 *   to them silently is a setting that does nothing. Descriptor blocks
 *   (`@font-face`, `@keyframes` and the like) are left alone, because
 *   there `!important` makes the browser drop the line.
 * - Anything that would fetch a file is left out: `@import`, and every
 *   `url()`, `src()` or `image-set()` pointing anywhere but a `data:` URL
 *   or a `#fragment`. Liseur reads offline and promises not to phone
 *   anywhere the reader did not choose; a stylesheet is not allowed to
 *   choose for them.
 *
 * Comments go first, replaced by a space as the browser's tokenizer would
 * treat them, and the URL check reads CSS escapes decoded, so an
 * escaped `u\72l(` is caught as the `url(` it is. The splitting below is a
 * deliberately small reading of CSS syntax rather than a parser, so the
 * finished text is checked once more as a whole: if anything that fetches
 * survived a split the browser would make differently, nothing is applied.
 */
object CustomCss {

    data class Prepared(
        /** What the page is given; empty when there is nothing to apply. */
        val css: String,
        /** Declarations, statements and rules dropped for loading a file. */
        val leftOut: Int,
        /** Whether a rule paints text or a background in a colour of its own. */
        val setsColours: Boolean,
    )

    fun prepare(source: String): Prepared {
        val walk = Walk(withoutComments(source))
        walk.block(0, important = true, topLevel = true)
        val css = walk.out.toString().trim()
        if (reachesOut(css)) return Prepared("", maxOf(1, walk.leftOut), walk.setsColours)
        return Prepared(css, walk.leftOut, walk.setsColours)
    }

    /** At-rules whose blocks hold descriptors, where `!important` is invalid. */
    private val DESCRIPTOR_RULES = setOf(
        "font-face", "keyframes", "-webkit-keyframes", "counter-style", "property",
        "font-feature-values", "font-palette-values", "position-try", "view-transition",
    )

    private val IMPORTANT = Regex("""!\s*important\s*$""", RegexOption.IGNORE_CASE)
    private val URL_FUNCTION = Regex("""(?:url|src)\(""")
    private const val IMAGE_SET = "image-set("

    private val COLOUR_PROPERTIES = setOf("color", "background", "background-color")

    /** Values that follow the reading theme rather than replace it. */
    private val THEME_SAFE = setOf(
        "inherit", "initial", "unset", "revert", "revert-layer", "currentcolor", "transparent", "none",
    )

    private class Walk(val text: String) {
        val out = StringBuilder()
        var leftOut = 0
        var setsColours = false

        /**
         * Reads a list of rules or declarations from [start], writing it
         * out, and answers the index just after its closing brace, or the
         * end of the text.
         */
        fun block(start: Int, important: Boolean, topLevel: Boolean): Int {
            var i = start
            var segmentStart = start
            var depth = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '"' || c == '\'' -> {
                        i = skipString(text, i)
                        continue
                    }
                    c == '\\' -> {
                        i += 2
                        continue
                    }
                    c == '(' || c == '[' -> depth++
                    (c == ')' || c == ']') && depth > 0 -> depth--
                    depth > 0 -> Unit
                    c == ';' -> {
                        if (segment(text.substring(segmentStart, i), important, topLevel)) out.append(';')
                        segmentStart = i + 1
                    }
                    c == '{' -> {
                        val prelude = text.substring(segmentStart, i)
                        if (!topLevel && prelude.trimStart().startsWith("--") && ':' in prelude) {
                            // A custom property whose value holds a block:
                            // still one declaration, read on to its end.
                            i = skipBlock(text, i)
                            continue
                        }
                        i = rule(prelude, i, important)
                        segmentStart = i
                        continue
                    }
                    c == '}' -> {
                        segment(text.substring(segmentStart, i), important, topLevel)
                        if (!topLevel) return i + 1
                        // A stray brace at the top is dropped.
                        segmentStart = i + 1
                    }
                }
                i++
            }
            segment(text.substring(segmentStart), important, topLevel)
            return text.length
        }

        private fun rule(prelude: String, open: Int, important: Boolean): Int {
            val name = atName(prelude)
            if (name == "import" || reachesOut(prelude)) {
                leftOut++
                return skipBlock(text, open)
            }
            out.append(prelude).append('{')
            val next = block(open + 1, important && name !in DESCRIPTOR_RULES, topLevel = false)
            out.append('}')
            return next
        }

        /** Writes one statement or declaration; false when it was left out. */
        private fun segment(raw: String, important: Boolean, topLevel: Boolean): Boolean {
            if (raw.isBlank()) {
                out.append(raw)
                return true
            }
            val body = raw.trimEnd()
            val trailing = raw.substring(body.length)
            val lead = body.trimStart()
            if (atName(lead) == "import" || reachesOut(raw)) {
                leftOut++
                return false
            }
            if (topLevel || lead.startsWith("@") || ':' !in lead) {
                out.append(raw)
                return true
            }
            if (paintsColour(lead)) setsColours = true
            out.append(body)
            if (important && !IMPORTANT.containsMatchIn(body)) out.append(" !important")
            out.append(trailing)
            return true
        }
    }

    private fun paintsColour(declaration: String): Boolean {
        val property = declaration.substringBefore(':').trim().lowercase()
        if (property !in COLOUR_PROPERTIES) return false
        val value = declaration.substringAfter(':').replace(IMPORTANT, "").trim().lowercase()
        return value !in THEME_SAFE && !value.startsWith("var(")
    }

    /** The at-keyword [s] starts with, lower-cased and without its `@`. */
    private fun atName(s: String): String? {
        val trimmed = s.trimStart()
        if (!trimmed.startsWith("@")) return null
        return trimmed.drop(1).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }.lowercase()
    }

    /** Whether [s] asks the browser for a file that is not written inside it. */
    internal fun reachesOut(s: String): Boolean {
        val d = decodeEscapes(s).lowercase()
        if ("@import" in d) return true
        for (match in URL_FUNCTION.findAll(d)) {
            if (!isLocal(urlArgument(d, match.range.last + 1))) return true
        }
        var from = 0
        while (true) {
            val at = d.indexOf(IMAGE_SET, from)
            if (at < 0) break
            val open = at + IMAGE_SET.length - 1
            val inside = d.substring(open, closingParen(d, open))
            if (quotedStrings(inside).any { !isLocal(it) }) return true
            from = open
        }
        return false
    }

    private fun isLocal(target: String): Boolean {
        val t = target.trim()
        return t.isEmpty() || t.startsWith("data:") || t.startsWith("#")
    }

    private fun urlArgument(s: String, start: Int): String {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return ""
        val quote = s[i]
        if (quote == '"' || quote == '\'') {
            val end = skipString(s, i)
            return s.substring(i + 1, end).removeSuffix(quote.toString())
        }
        val end = (i until s.length).firstOrNull { s[it] == ')' || s[it].isWhitespace() } ?: s.length
        return s.substring(i, end)
    }

    private fun quotedStrings(s: String): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '"' || c == '\'') {
                val end = skipString(s, i)
                found += s.substring(i + 1, end).removeSuffix(c.toString())
                i = end
            } else {
                i++
            }
        }
        return found
    }

    /** The index just past the parenthesis matching the one at [open]. */
    private fun closingParen(s: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '"', '\'' -> {
                    i = skipString(s, i)
                    continue
                }
                '(' -> depth++
                ')' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return s.length
    }

    /**
     * The index just past the string opening at [start]. An unclosed
     * string ends before the line break, as the browser's does.
     */
    private fun skipString(s: String, start: Int): Int {
        val quote = s[start]
        var i = start + 1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return s.length
    }

    /** The index just past the brace matching the one at [open]. */
    private fun skipBlock(s: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < s.length) {
            when (s[i]) {
                '"', '\'' -> {
                    i = skipString(s, i)
                    continue
                }
                '\\' -> {
                    i += 2
                    continue
                }
                '{' -> depth++
                '}' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return s.length
    }

    internal fun withoutComments(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = skipString(s, i)
                    append(s, i, end)
                    i = end
                }
                c == '\\' -> {
                    append(s, i, minOf(i + 2, s.length))
                    i += 2
                }
                c == '/' && i + 1 < s.length && s[i + 1] == '*' -> {
                    val close = s.indexOf("*/", i + 2)
                    append(' ')
                    i = if (close < 0) s.length else close + 2
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    /** [s] with its CSS escapes turned back into the characters they stand for. */
    internal fun decodeEscapes(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') {
                append(c)
                i++
                continue
            }
            if (i + 1 >= s.length) {
                i++
                continue
            }
            val next = s[i + 1]
            when {
                next == '\n' || next == '\r' || next == '\u000C' -> i += 2
                next.isHexDigit() -> {
                    var end = i + 1
                    while (end < s.length && end - (i + 1) < 6 && s[end].isHexDigit()) end++
                    val code = s.substring(i + 1, end).toInt(16)
                    if (end < s.length && s[end].isWhitespace()) end++
                    if (code == 0 || code in 0xD800..0xDFFF || code > 0x10FFFF) {
                        append('\uFFFD')
                    } else {
                        appendCodePoint(code)
                    }
                    i = end
                }
                else -> {
                    append(next)
                    i += 2
                }
            }
        }
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
