package com.chmouel.liseur.domain

/**
 * Ready-made rules the custom CSS editor offers to add.
 *
 * Written plainly, without `!important`: [CustomCss.prepare] adds it to
 * every rule on the way to the page, these included. The dark-theme
 * selector matches the `--USER__appearance` value Readium writes on the
 * root element for every theme Liseur draws dark.
 */
enum class CustomCssSnippet(val css: String) {
    DROP_CAP(
        """
        h1 + p, h2 + p {
          text-indent: 0;
        }
        h1 + p::first-letter, h2 + p::first-letter {
          float: left;
          font-size: 3.2em;
          line-height: 0.85;
          padding: 0.05em 0.04em 0 0;
        }
        """,
    ),
    SMALL_CAPS(
        """
        h1 + p::first-line, h2 + p::first-line {
          font-variant: small-caps;
        }
        """,
    ),
    // The link selector is longer than it looks like it needs to be:
    // in the dark themes Readium paints links with
    // `:root[style*=readium-night-on] a:link`, and only a more specific
    // selector takes a title's link back to the text colour.
    HEADING_COLOUR(
        """
        h1, h2, h3, h4,
        :root[style] :is(h1, h2, h3, h4) a:is(:link, :visited) {
          color: inherit;
          text-decoration: none;
        }
        """,
    ),
    CENTRED_HEADINGS(
        """
        h1, h2, h3 {
          text-align: center;
        }
        """,
    ),
    NO_INDENT(
        """
        p {
          text-indent: 0;
        }
        """,
    ),
    SCENE_BREAK(
        """
        hr {
          border: 0;
          background: none;
          height: auto;
          margin: 1.5em 0;
          text-align: center;
        }
        hr::after {
          content: "⁂";
        }
        """,
    ),
    BALANCED(
        """
        h1, h2, h3, h4 {
          text-wrap: balance;
        }
        p {
          text-wrap: pretty;
        }
        """,
    ),
    OLDSTYLE_FIGURES(
        """
        body {
          font-variant-numeric: oldstyle-nums;
        }
        """,
    ),
    DIM_IMAGES_IN_DARK(
        """
        :root[style*="readium-night-on"] img,
        :root[style*="readium-night-on"] svg {
          filter: brightness(0.8);
        }
        """,
    ),
    ;

    /** The rules as they go into the editor, without the source's indent. */
    val text: String get() = css.trimIndent()

    /**
     * Whether [stylesheet] already holds these rules as they were added.
     *
     * Matched as whole lines: `p {` is also the tail of `h1 + p, h2 + p {`,
     * and one snippet must not read as added because another one is.
     */
    fun isIn(stylesheet: String): Boolean =
        "\n$text\n" in "\n" + stylesheet.replace("\r\n", "\n") + "\n"

    /** [stylesheet] with these rules added at the end under [title]. */
    fun appendTo(stylesheet: String, title: String): String {
        val head = stylesheet.trimEnd()
        val block = "/* ${title.replace("*/", "* /")} */\n$text\n"
        return if (head.isEmpty()) block else "$head\n\n$block"
    }
}
