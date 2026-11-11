/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

// The same cases tools/search/build_index.py folds; both sides must agree.
class SearchTextTest {
    @Test
    fun collapsesRepeatedLetters() {
        assertEquals("lalbagh", SearchText.fold("Laalbagh"))
        assertEquals("lalbagh", SearchText.fold("Lalbagh"))
    }

    @Test
    fun dropsLatinAccentsAndPunctuation() {
        assertEquals("cafe cofe day", SearchText.fold("Café Coffee Day"))
        assertEquals("st marks road", SearchText.fold("St. Mark's Road"))
        assertEquals("st marks road", SearchText.fold("St. Mark’s Road"))
    }

    @Test
    fun keepsNumbers() {
        assertEquals("100 fet road", SearchText.fold("100 Feet Road"))
        assertEquals("koramangala 5th block", SearchText.fold("Koramangala 5th Block"))
    }

    @Test
    fun keepsKannadaMarksAndDropsJoiners() {
        assertEquals("ಲಾಲ್ಬಾಗ್", SearchText.fold("ಲಾಲ್‌ಬಾಗ್"))
        assertEquals("ಲಾಲ್ಬಾಗ್", SearchText.fold("ಲಾಲ್ಬಾಗ್"))
    }

    @Test
    fun splitsWords() {
        assertEquals(listOf("mg", "road"), SearchText.words("  M.G.  Road "))
        assertEquals(emptyList<String>(), SearchText.words(" ,. "))
    }
}
