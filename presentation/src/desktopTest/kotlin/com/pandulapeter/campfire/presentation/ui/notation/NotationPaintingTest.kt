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
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.SYSTEM
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.TAB_HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.TAB_SYSTEM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
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
                renderings = tunes.associate { AbcTune(it, 0) to NotationRendering(drawing, unitSize = 1f, systems = painter.paint(drawing, unitSize = 1f), reading = NotationReading(emptyList(), emptyList(), null)) },
                rerenderedTunes = emptySet(),
                arrivingTunes = emptySet(),
            ),
            standIns = emptyMap(),
            unitSize = 1f,
            maxWidth = 0f,
            placement = null,
            partChoices = null,
            instrument = null,
        )
        tunes.forEach { tune ->
            val run = tune.lines().map { ChordProLine.Verbatim(text = it, environment = "abc") }
            assertSame(assertNotNull(notations.of(run, 0)).systems, assertNotNull(notations.of(run, 0)).systems)
        }
    }

    @Test
    fun `a turned symbol is drawn turned`() {
        val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, NotationFixtures.system("""<g transform="translate(100,50) rotate(270)"><text>&#xE000;</text></g>"""))))
        val bounds = painting().newPainter().paint(drawing, unitSize = 1f).single().fills.getBounds()
        // The brace stands about 24 units tall and 2 wide; turned a quarter back, it lies to the left of where it stood.
        assertTrue(bounds.width > 20f && bounds.height < 4f, bounds.toString())
        assertTrue(bounds.left > 70f && bounds.right <= 100.5f, bounds.toString())
    }

    @Test
    fun `a tablature's frets cut the strings under them, and nothing else does`() {
        val drawing = assertNotNull(parseNotationDrawing(listOf(TAB_HEADER, TAB_SYSTEM)))
        val tablature = painting().newPainter().paint(drawing, unitSize = 1f).last()
        val knockouts = tablature.knockouts.getBounds()
        // The frets stand from 59.5 to 228.1 units of a group scaled by 1.6 and moved down by 71, their baselines 21.5 and
        // 27.5 units over it: the boxes start a little left of the first, end right of the last, and cover the baselines.
        assertTrue(knockouts.left < 59.5f * 1.6f && knockouts.left > 59.5f * 1.6f - 3f, knockouts.toString())
        assertTrue(knockouts.right > 228.1f * 1.6f, knockouts.toString())
        assertTrue(knockouts.top < 71f - 27.5f * 1.6f && knockouts.bottom > 71f - 21.5f * 1.6f, knockouts.toString())
        val staff = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
        assertEquals(true, painting().newPainter().paint(staff, unitSize = 1f).single().knockouts.isEmpty)
    }

    private fun painting() = NotationPainting(Density(1f), createFontFamilyResolver(), TextStyle.Default)
}
