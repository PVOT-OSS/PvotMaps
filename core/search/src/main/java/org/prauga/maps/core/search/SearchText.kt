/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.search

import java.text.Normalizer
import java.util.Locale

/**
 * The text form names are matched in. It must fold exactly like `fold()` in
 * tools/search/build_index.py, which folded every name in the search data.
 */
object SearchText {
    private const val LatinMarksEnd = 0x0370
    private val Dropped = setOf('\'', '\u2019', '\u02bc', '.')

    /**
     * Lower-case, decompose, drop accents on Latin letters but keep other scripts' marks (Kannada
     * vowel signs), drop apostrophes, full stops ("M.G." is "MG") and invisible joiners, turn
     * anything else that isn't a letter, digit or mark into a space, and collapse runs of the same
     * letter.
     */
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        val folded = StringBuilder(decomposed.length)
        for (c in decomposed) {
            val type = Character.getType(c)
            if (type == Character.NON_SPACING_MARK.toInt() && c.code < LatinMarksEnd) continue
            if (type == Character.FORMAT.toInt() || c in Dropped) continue
            val kept = if (c.isLetterOrDigit() || c.isMark()) c else ' '
            if (kept.isLetter() && folded.isNotEmpty() && folded.last() == kept) continue
            folded.append(kept)
        }
        return folded.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Words of [query] to match as prefixes, folded; empty when there is nothing to search for. */
    fun words(query: String): List<String> = fold(query).split(' ').filter { it.isNotEmpty() }

    private fun Char.isMark(): Boolean =
        when (Character.getType(this)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }
}
