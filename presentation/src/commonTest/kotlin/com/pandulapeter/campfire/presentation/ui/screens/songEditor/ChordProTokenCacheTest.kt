/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.chordpro.edit.ChordProHighlighter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ChordProTokenCacheTest {

    @Test
    fun `unchanged content does not copy or retokenize`() {
        // The editor's string lags behind here, so every change is copied by the cache itself.
        val cache = ChordProTokenCache(editorText = { "" })
        val song = "{title: Verse}\n[C]Sing [G]along"
        val first = ObservedText(song)
        val initialTokens = cache.tokensOf(first)
        assertEquals(1, first.toStringCalls)

        val sameContent = ObservedText(song)
        assertSame(initialTokens, cache.tokensOf(sameContent))
        assertEquals(0, sameContent.toStringCalls)

        val editAtStart = ObservedText("# introduction\n$song")
        val shiftedTokens = cache.tokensOf(editAtStart)
        assertNotSame(initialTokens, shiftedTokens)
        assertEquals(1, editAtStart.toStringCalls)
        assertEquals(ChordProHighlighter.tokenize(editAtStart.content), shiftedTokens)

        val editAtEnd = ObservedText("${editAtStart.content}\n[Am]End")
        val extendedTokens = cache.tokensOf(editAtEnd)
        assertNotSame(shiftedTokens, extendedTokens)
        assertEquals(1, editAtEnd.toStringCalls)
        assertEquals(ChordProHighlighter.tokenize(editAtEnd.content), extendedTokens)
    }

    @Test
    fun `the string the editor keeps is taken where it holds the text shown`() {
        var editorText = "{title: Verse}\n[C]Sing [G]along"
        val cache = ChordProTokenCache(editorText = { editorText })
        val first = ObservedText(editorText)
        assertEquals(ChordProHighlighter.tokenize(first.content), cache.tokensOf(first))
        assertEquals(0, first.toStringCalls)

        editorText = "{title: Verse}\n[C]Sing [Am]along"
        val edited = ObservedText(editorText)
        assertEquals(ChordProHighlighter.tokenize(edited.content), cache.tokensOf(edited))
        assertEquals(0, edited.toStringCalls)

        val ahead = ObservedText("$editorText\n[F]More")
        assertEquals(ChordProHighlighter.tokenize(ahead.content), cache.tokensOf(ahead))
        assertEquals(1, ahead.toStringCalls)
    }

    private class ObservedText(val content: String) : CharSequence {
        var toStringCalls = 0
            private set

        override val length: Int get() = content.length

        override fun get(index: Int): Char = content[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = content.subSequence(startIndex, endIndex)

        override fun toString(): String {
            toStringCalls++
            return content
        }
    }
}
