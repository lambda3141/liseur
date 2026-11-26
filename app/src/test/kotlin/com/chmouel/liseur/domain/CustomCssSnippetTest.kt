package com.chmouel.liseur.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCssSnippetTest {

    @Test
    fun `every snippet reaches the page whole and leaves the theme alone`() {
        for (snippet in CustomCssSnippet.entries) {
            val prepared = CustomCss.prepare(snippet.text)
            assertTrue(snippet.name, prepared.css.isNotEmpty())
            assertEquals(snippet.name, 0, prepared.leftOut)
            assertFalse(snippet.name, prepared.setsColours)
        }
    }

    @Test
    fun `a snippet goes at the end under its title, a blank line after the rest`() {
        val once = CustomCssSnippet.NO_INDENT.appendTo("", "No indents")
        assertEquals("/* No indents */\np {\n  text-indent: 0;\n}\n", once)
        assertTrue(CustomCssSnippet.NO_INDENT.isIn(once))
        assertFalse(CustomCssSnippet.CENTRED_HEADINGS.isIn(once))

        val twice = CustomCssSnippet.CENTRED_HEADINGS.appendTo(once + "\n\n", "Centred")
        assertTrue(twice, twice.startsWith(once.trimEnd() + "\n\n/* Centred */\n"))
    }

    @Test
    fun `one snippet does not read as added because another holds its text`() {
        val withDropCap = CustomCssSnippet.DROP_CAP.appendTo("", "Drop cap")
        assertTrue(withDropCap.contains("p {\n  text-indent: 0;\n}"))
        assertFalse(CustomCssSnippet.NO_INDENT.isIn(withDropCap))
        assertTrue(CustomCssSnippet.NO_INDENT.isIn(CustomCssSnippet.NO_INDENT.appendTo(withDropCap, "No indents")))
        assertTrue(CustomCssSnippet.NO_INDENT.isIn("p {\r\n  text-indent: 0;\r\n}"))
    }

    @Test
    fun `a title cannot close the comment it is written in`() {
        val out = CustomCssSnippet.NO_INDENT.appendTo("", "a */ b")
        assertTrue(out, out.startsWith("/* a * / b */"))
        assertEquals(0, CustomCss.prepare(out).leftOut)
    }
}
