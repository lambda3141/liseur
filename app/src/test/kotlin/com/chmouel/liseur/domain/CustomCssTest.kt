package com.chmouel.liseur.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCssTest {

    private fun css(source: String) = CustomCss.prepare(source).css

    @Test
    fun `every declaration is made important`() {
        val out = css("p { text-indent: 0 } h1 { color: red; margin: 0 }")
        assertTrue(out, out.contains("text-indent: 0 !important"))
        assertTrue(out, out.contains("color: red !important;"))
        assertTrue(out, out.contains("margin: 0 !important"))
    }

    @Test
    fun `a declaration already important is left as written`() {
        val out = css("p { color: red !important; margin: 0 ! IMPORTANT }")
        assertEquals("p { color: red !important; margin: 0 ! IMPORTANT }", out)
    }

    @Test
    fun `descriptor blocks are never made important`() {
        val out = css(
            "@font-face { font-family: Mine; src: url(data:font/woff2;base64,AAAA) } " +
                "@keyframes fade { from { opacity: 0 } to { opacity: 1 } }",
        )
        assertFalse(out, out.contains("!important"))
        assertTrue(out, out.contains("src: url(data:font/woff2;base64,AAAA)"))
        assertEquals(0, CustomCss.prepare(out).leftOut)
    }

    @Test
    fun `rules inside media queries and nesting are made important`() {
        val out = css("@media (min-width: 600px) { p { color: red } } a { color: blue; &:hover { color: green } }")
        assertTrue(out, out.contains("@media (min-width: 600px) { p { color: red !important }"))
        assertTrue(out, out.contains("color: blue !important;"))
        assertTrue(out, out.contains("&:hover { color: green !important }"))
    }

    @Test
    fun `braces and semicolons inside strings are text`() {
        val out = css("""p::after { content: "a; b } c" }""")
        assertEquals("""p::after { content: "a; b } c" !important }""", out)
    }

    @Test
    fun `custom properties are made important too`() {
        assertEquals(
            ":root { --bg-texture-blend-mode: soft-light !important; }",
            css(":root { --bg-texture-blend-mode: soft-light; }"),
        )
    }

    @Test
    fun `comments are dropped and an unclosed rule is closed`() {
        assertEquals("p { color: red !important  }", css("/* a note */ p { color: red /* why */"))
        assertEquals("", css("/* only a note */"))
        assertEquals("", css("   "))
    }

    @Test
    fun `an address on the internet is left out and the rest kept`() {
        val prepared = CustomCss.prepare("p { background: url(https://example.com/x.png); color: inherit }")
        assertEquals(1, prepared.leftOut)
        assertFalse(prepared.css, prepared.css.contains("example.com"))
        assertTrue(prepared.css, prepared.css.contains("color: inherit !important"))
    }

    @Test
    fun `every way of naming a file is left out`() {
        listOf(
            "p { background: url('https://x/y') }",
            "p { background: URL(\"http://x/y\") }",
            "p { background: url(//x/y) }",
            "p { background: url(images/y.png) }",
            "p { background: u\\72l(https://x/y) }",
            "p { background: \\75\\72\\6c(https://x/y) }",
            "p { background-image: image-set(\"https://x/y.png\" 1x) }",
            "p { background-image: -webkit-image-set('y.png' 1x) }",
            "p { list-style: src(\"https://x/y\") }",
            "@font-face { font-family: X; src: url(https://x/font.woff2) }",
        ).forEach { source ->
            val prepared = CustomCss.prepare(source)
            assertTrue(source, prepared.leftOut > 0)
            assertFalse("$source -> ${prepared.css}", CustomCss.reachesOut(prepared.css))
        }
    }

    @Test
    fun `an import is dropped without taking the next rule with it`() {
        val prepared = CustomCss.prepare("@import \"theme.css\"; @IMPORT url(x.css); p { color: red }")
        assertEquals(2, prepared.leftOut)
        assertEquals("p { color: red !important }", prepared.css)
    }

    @Test
    fun `an escaped import is still an import`() {
        val prepared = CustomCss.prepare("@\\69mport \"theme.css\"; p { color: red }")
        assertEquals(1, prepared.leftOut)
        assertEquals("p { color: red !important }", prepared.css)
    }

    @Test
    fun `a rule whose prelude names a file goes whole`() {
        val prepared = CustomCss.prepare("@supports (background: url(https://x)) { p { color: red } } h1 { margin: 0 }")
        assertEquals(1, prepared.leftOut)
        assertEquals("h1 { margin: 0 !important }", prepared.css)
    }

    @Test
    fun `data addresses and fragments stay`() {
        val source = "p { background: url(\"data:image/png;base64,AAAA\"); filter: url(#soft) } " +
            "h1 { background-image: image-set(\"data:image/png;base64,AA\" 1x) }"
        val prepared = CustomCss.prepare(source)
        assertEquals(0, prepared.leftOut)
        assertTrue(prepared.css, prepared.css.contains("url(#soft) !important"))
    }

    @Test
    fun `a comment between letters does not make a url`() {
        val prepared = CustomCss.prepare("p { background: u/**/rl(https://x/y) }")
        assertFalse(prepared.css, prepared.css.contains("url("))
    }

    @Test
    fun `colours that replace the theme are noticed`() {
        assertTrue(CustomCss.prepare("p { color: #333 }").setsColours)
        assertTrue(CustomCss.prepare("body { background-color: white }").setsColours)
        assertTrue(CustomCss.prepare("body { BACKGROUND: black !important }").setsColours)
    }

    @Test
    fun `colours that follow the theme are not`() {
        assertFalse(CustomCss.prepare("h1 { color: inherit }").setsColours)
        assertFalse(CustomCss.prepare("hr { background: none }").setsColours)
        assertFalse(CustomCss.prepare("p { background-color: var(--x) }").setsColours)
        assertFalse(CustomCss.prepare("a:hover { text-decoration: none }").setsColours)
    }

    @Test
    fun `a stylesheet from another reader carries over`() {
        val readest = """
            /* Drop cap on the first letter of a chapter */
            h1 + p, h2 + p {
            	text-indent: 0 !important;
            }
            h1 + p::first-letter, h2 + p::first-letter {
            	float: left;
            	font-size: 3.2em;
            	line-height: 0.85;
            	padding: 0.05em 0.04em 0 0;
            }
            /* Small caps on the first line of a chapter */
            h1 + p::first-line, h2 + p::first-line {
            	font-variant: small-caps;
            }
            /* Chapter titles in the same color as the body text */
            h1, h2, h3, h4,h1 a, h2 a, h3 a, h4 a {
            	color: inherit !important;
            	text-decoration: none !important;
            }
            :root {
            	--bg-texture-blend-mode: soft-light;
            }
        """.trimIndent()
        val prepared = CustomCss.prepare(readest)
        assertEquals(0, prepared.leftOut)
        assertFalse(prepared.setsColours)
        for (line in listOf(
            "float: left !important;", "font-size: 3.2em !important;", "line-height: 0.85 !important;",
            "padding: 0.05em 0.04em 0 0 !important;", "font-variant: small-caps !important;",
            "text-indent: 0 !important;", "color: inherit !important;",
            "--bg-texture-blend-mode: soft-light !important;",
        )) {
            assertTrue(line, prepared.css.contains(line))
        }
        assertFalse(prepared.css.contains("!important !important"))
    }

    @Test
    fun `escapes decode to the characters they name`() {
        assertEquals("url(", CustomCss.decodeEscapes("\\75\\72 l("))
        assertEquals("a;b", CustomCss.decodeEscapes("a\\;b"))
        assertEquals("\uFFFD", CustomCss.decodeEscapes("\\0"))
        assertEquals("ab", CustomCss.decodeEscapes("a\\\nb"))
    }
}
