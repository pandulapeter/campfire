/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** On the desktop, since the renderings it keeps are painted with a real measurer. */
internal class NotationCacheTest {

    private val painting = NotationPainting(Density(1f), createFontFamilyResolver(), TextStyle.Default)
    private val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
    private val rendering = NotationRendering(drawing, unitSize = 1f, systems = painting.newPainter().paint(drawing, unitSize = 1f), reading = NotationReading(emptyList(), emptyList(), null))

    @Test
    fun `a request is rendered from the cache only when every tune of it is cached`() {
        val cache = NotationCache()
        val request = request(AbcTune("A", 0), AbcTune("B", 0))
        cache[request.keyOf(AbcTune("A", 0))] = rendering
        assertNull(cache.renderedFor(request))
        cache[request.keyOf(AbcTune("B", 0))] = rendering
        val rendered = assertNotNull(cache.renderedFor(request))
        assertSame(request, rendered.request)
        assertEquals(setOf(AbcTune("A", 0), AbcTune("B", 0)), rendered.renderings.keys)
        assertEquals(emptySet(), rendered.rerenderedTunes)
    }

    @Test
    fun `the tune used least recently leaves first`() {
        val cache = NotationCache(maxTunes = 3, maxWords = Int.MAX_VALUE)
        val request = request()
        listOf("A", "B", "C").forEach { cache[request.keyOf(AbcTune(it, 0))] = rendering }
        cache[request.keyOf(AbcTune("A", 0))]
        cache[request.keyOf(AbcTune("D", 0))] = rendering
        assertNull(cache[request.keyOf(AbcTune("B", 0))])
        assertNotNull(cache[request.keyOf(AbcTune("A", 0))])
    }

    @Test
    fun `a tune under another transposition is another entry`() {
        val cache = NotationCache()
        cache[request().keyOf(AbcTune("A", 0))] = rendering
        assertNull(cache[request().keyOf(AbcTune("A", 2))])
        assertNull(cache[request(chords = NotationChordSpelling.AS_WRITTEN.movedBy(1)).keyOf(AbcTune("A", 0))])
        assertNotNull(cache[request().keyOf(AbcTune("A", 0))])
    }

    @Test
    fun `renderings holding more words than the bound push the oldest out`() {
        val words = notationWordsOf(rendering.systems)
        val cache = NotationCache(maxTunes = Int.MAX_VALUE, maxWords = words * 2)
        val request = request()
        listOf("A", "B", "C").forEach { cache[request.keyOf(AbcTune(it, 0))] = rendering }
        assertNull(cache[request.keyOf(AbcTune("A", 0))])
        assertNotNull(cache[request.keyOf(AbcTune("B", 0))])
        assertNotNull(cache[request.keyOf(AbcTune("C", 0))])
        val tooHeavy = NotationCache(maxTunes = Int.MAX_VALUE, maxWords = words - 1)
        tooHeavy[request.keyOf(AbcTune("A", 0))] = rendering
        assertNull(tooHeavy[request.keyOf(AbcTune("A", 0))])
    }

    @Test
    fun `a failed tune is remembered for its key only`() {
        val cache = NotationCache()
        cache.markFailed(request().keyOf(AbcTune("A", 0)))
        assertTrue(cache.isFailed(request().keyOf(AbcTune("A", 0))))
        assertFalse(cache.isFailed(request().copy(width = 200).keyOf(AbcTune("A", 0))))
    }

    @Test
    fun `the failed tune used least recently is forgotten first`() {
        val cache = NotationCache(maxFailedTunes = 3)
        val request = request()
        listOf("A", "B", "C").forEach { cache.markFailed(request.keyOf(AbcTune(it, 0))) }
        cache.isFailed(request.keyOf(AbcTune("A", 0)))
        cache.markFailed(request.keyOf(AbcTune("D", 0)))
        assertFalse(cache.isFailed(request.keyOf(AbcTune("B", 0))))
        assertTrue(cache.isFailed(request.keyOf(AbcTune("A", 0))))
        assertTrue(cache.isFailed(request.keyOf(AbcTune("D", 0))))
    }

    @Test
    fun `a tune that renders is no longer failed`() {
        val cache = NotationCache()
        val key = request().keyOf(AbcTune("A", 0))
        cache.markFailed(key)
        cache[key] = rendering
        assertFalse(cache.isFailed(key))
    }

    @Test
    fun `a request with a failed tune is rendered from the cache with the other tunes`() {
        val cache = NotationCache()
        val request = request(AbcTune("A", 0), AbcTune("B", 0), AbcTune("C", 0))
        cache[request.keyOf(AbcTune("A", 0))] = rendering
        cache.markFailed(request.keyOf(AbcTune("B", 0)))
        assertNull(cache.renderedFor(request))
        cache[request.keyOf(AbcTune("C", 0))] = rendering
        assertEquals(setOf(AbcTune("A", 0), AbcTune("C", 0)), assertNotNull(cache.renderedFor(request)).renderings.keys)
    }

    @Test
    fun `a page wider than a column engraves for the column`() {
        assertEquals(268, notationPageWidth(availableWidth = 1_000.dp, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 1.dp))
        assertEquals(134, notationPageWidth(availableWidth = 1_000.dp, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 2.dp))
        assertEquals(168, notationPageWidth(availableWidth = 200.dp, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 1.dp))
    }

    @Test
    fun `no width engraves nothing`() {
        assertNull(notationPageWidth(availableWidth = Dp.Unspecified, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 1.dp))
        assertNull(notationPageWidth(availableWidth = 0.dp, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 1.dp))
        assertNull(notationPageWidth(availableWidth = 20.dp, maxColumnWidth = 300.dp, cardPadding = 16.dp, notationUnit = 1.dp))
    }

    private fun request(vararg tunes: AbcTune, chords: NotationChordSpelling = NotationChordSpelling.AS_WRITTEN) = NotationRequest(
        tunes = tunes.toSet(),
        width = 300,
        unitSize = 1f,
        chords = chords,
        songTitle = null,
        painting = painting,
    )
}
