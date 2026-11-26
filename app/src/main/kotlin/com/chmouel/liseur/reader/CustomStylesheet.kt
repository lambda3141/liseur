package com.chmouel.liseur.reader

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Puts the reader's custom CSS on the page on screen.
 *
 * The text arrives already prepared by
 * [com.chmouel.liseur.domain.CustomCss]: important, and with nothing in it
 * that fetches. An empty text takes a sheet that was there away again.
 *
 * Added at runtime, like [WideContentFit], because Readium decides whether
 * to link its default stylesheet by looking for `<style` in the resource.
 * It is a constructed stylesheet in `adoptedStyleSheets` where the web view
 * has them: those come after every sheet the document links, so the
 * reader's rules win a tie against Readium's and the publisher's, and
 * the book's Content-Security-Policy, which can refuse an inline `<style>`,
 * has no say over them. An old web view without them gets a `<style>` at
 * the end of the head instead.
 *
 * The sheet lives in the document's own window, so a web view the pager
 * recycles into another chapter starts without it and is given it again.
 */
internal object CustomStylesheet {
    internal enum class Result {
        /** The sheet was added, replaced or removed: the page may have moved. */
        CHANGED,

        /** Already as asked; the layout is untouched. */
        STABLE,

        /** The book's Content-Security-Policy refused the fallback `<style>`. */
        BLOCKED,

        /** No reflowable page to talk to, or the script did not answer. */
        FAILED,
    }

    /** The script that leaves [css] as the only custom sheet on the page. */
    fun script(css: String): String = """
        (function (css) {
          try {
            var state = window.__liseurUserCss || (window.__liseurUserCss = { text: "" });
            var doc = document;
            var adopt = "adoptedStyleSheets" in doc && typeof CSSStyleSheet === "function" &&
                        typeof CSSStyleSheet.prototype.replaceSync === "function";
            var present = state.sheet ? doc.adoptedStyleSheets.indexOf(state.sheet) >= 0
                        : state.el ? state.el.isConnected : false;
            if (css === "") {
              if (!present) {
                state.text = "";
                return "stable";
              }
              if (state.sheet) {
                doc.adoptedStyleSheets = doc.adoptedStyleSheets.filter(function (s) { return s !== state.sheet; });
              }
              if (state.el) state.el.remove();
              state.sheet = null;
              state.el = null;
              state.text = "";
              return "changed";
            }
            if (present && state.text === css) return "stable";
            if (adopt) {
              var sheet = state.sheet || new CSSStyleSheet();
              sheet.replaceSync(css);
              if (doc.adoptedStyleSheets.indexOf(sheet) < 0) {
                doc.adoptedStyleSheets = doc.adoptedStyleSheets.concat([sheet]);
              }
              state.sheet = sheet;
            } else {
              var el = state.el && state.el.isConnected ? state.el : doc.createElement("style");
              el.textContent = css;
              if (!el.isConnected) (doc.head || doc.documentElement).appendChild(el);
              state.el = el;
              if (!el.sheet) return "blocked";
            }
            state.text = css;
            return "changed";
          } catch (e) {
            return "failed";
          }
        })(${jsString(css)});
    """

    /**
     * [s] as a JavaScript string literal. Line and paragraph separators
     * are escaped too: older engines end a string literal at either.
     */
    internal fun jsString(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    /**
     * `evaluateJavascript` hands back the JSON encoding of the value, so a
     * plain string arrives wearing quotes.
     */
    internal fun parse(result: String?): Result {
        val value = result?.trim()?.removeSurrounding("\"")?.trim()
        return when (value) {
            "changed" -> Result.CHANGED
            "stable" -> Result.STABLE
            "blocked" -> Result.BLOCKED
            else -> Result.FAILED
        }
    }

    /**
     * A cancelled caller is let go, as in [SelectionHandleFix]: a navigator
     * being replaced must not read as a page that failed to answer.
     */
    @OptIn(ExperimentalReadiumApi::class)
    internal suspend fun apply(navigator: EpubNavigatorFragment, css: String): Result =
        parse(
            try {
                navigator.evaluateJavascript(script(css))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (refused: Exception) {
                null
            },
        )
}
