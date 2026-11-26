package com.chmouel.liseur.data.settings

import com.chmouel.liseur.domain.CustomCss

/**
 * The stylesheet a reader wrote for every book, and whether it is on.
 *
 * Kept apart from [ReaderPrefs], which is what Readium is told: this is
 * put on the page by Liseur itself, after Readium has laid it out.
 */
data class CustomCssSetting(
    val text: String = "",
    val enabled: Boolean = true,
) {
    /** What the page is given: nothing while switched off or empty. */
    val forPage: String
        get() = if (enabled) CustomCss.prepare(text).css else ""
}
