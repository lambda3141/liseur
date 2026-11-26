package com.chmouel.liseur.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomStylesheetTest {

    @Test
    fun `the javascript answer is read through its json quoting`() {
        assertEquals(CustomStylesheet.Result.CHANGED, CustomStylesheet.parse("\"changed\""))
        assertEquals(CustomStylesheet.Result.STABLE, CustomStylesheet.parse("\"stable\""))
        assertEquals(CustomStylesheet.Result.BLOCKED, CustomStylesheet.parse("\"blocked\""))
        assertEquals(CustomStylesheet.Result.FAILED, CustomStylesheet.parse("\"failed\""))
        assertEquals(CustomStylesheet.Result.FAILED, CustomStylesheet.parse(null))
        assertEquals(CustomStylesheet.Result.FAILED, CustomStylesheet.parse("null"))
    }

    @Test
    fun `the script is one expression, so evaluateJavascript returns its value`() {
        val script = CustomStylesheet.script("p { color: red !important }").trim()
        assertTrue(script.startsWith("(function (css)"))
        assertTrue(script.endsWith("})(\"p { color: red !important }\");"))
    }

    @Test
    fun `the stylesheet is handed over as a string literal that cannot end early`() {
        assertEquals(
            "\"a\\\"b\\\\c\\nd\\u2028e\\u0001\"",
            CustomStylesheet.jsString("a\"b\\c\nd\u2028e\u0001"),
        )
        val script = CustomStylesheet.script("p::after { content: \"\\\"\" }\n")
        assertTrue(script, script.contains("})(\"p::after { content: \\\"\\\\\\\"\\\" }\\n\");"))
    }

    @Test
    fun `an adopted sheet is preferred and a style element is the fallback`() {
        val script = CustomStylesheet.script("")
        assertTrue(script.contains("doc.adoptedStyleSheets.concat([sheet])"))
        assertTrue(script.contains("sheet.replaceSync(css)"))
        assertTrue(script.contains("doc.createElement(\"style\")"))
        assertTrue(script.contains("if (!el.sheet) return \"blocked\";"))
    }
}
