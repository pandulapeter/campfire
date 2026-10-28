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
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.SYSTEM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Painting with a real measurer, which needs the desktop's font resolver. */
internal class NotationPaintingTest {

    @Test
    fun `a tune is painted off the main thread with a measurer of its own`() = runBlocking {
        val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
        val systems = withContext(Dispatchers.Default) { painting().newPainter().paint(drawing, unitSize = 1f) }
        val texts = systems.single().texts
        assertTrue(texts.isNotEmpty())
        assertTrue(texts.all { it.layout.size.width > 0 })
        assertTrue(texts.any { it.isChord })
    }

    @Test
    fun `a song of nine tunes hands each run the same systems on every composition`() {
        val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
        val painter = painting().newPainter()
        val tunes = (1..9).map { "X:$it\nK:C\nCDEF|" }
        val notations = SongNotations(
            rendered = RenderedNotations(
                request = null,
                renderings = tunes.associateWith { NotationRendering(drawing, unitSize = 1f, systems = painter.paint(drawing, unitSize = 1f)) },
                rerenderedTunes = emptySet(),
                arrivingTunes = emptySet(),
            ),
            unitSize = 1f,
        )
        tunes.forEach { tune ->
            val run = tune.lines().map { ChordProLine.Verbatim(text = it, environment = "abc") }
            assertSame(assertNotNull(notations.of(run)).systems, assertNotNull(notations.of(run)).systems)
        }
    }

    private fun painting() = NotationPainting(Density(1f), LayoutDirection.Ltr, createFontFamilyResolver(), TextStyle.Default)
}
