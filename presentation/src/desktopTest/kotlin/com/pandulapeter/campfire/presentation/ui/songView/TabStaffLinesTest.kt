/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.model.TabRowLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class TabStaffLinesTest {

    private val style = TextStyle(fontSize = 14.sp)
    private val monospaceStyle = style.copy(fontFamily = FontFamily.Monospace)
    private val styles = RunStyles(text = style, chord = style, monospaceText = monospaceStyle, monospaceChord = monospaceStyle)
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    @Test
    fun `two staff lines holding the same fret share its layout`() {
        val staffLines = TabStaffLines(styles, textMeasurer)
        val first = staffLines.line(TabRowLine("e|---3---|", isStaffLine = true)).texts.single { it.layout.layoutInput.text.text == "3" }
        val second = staffLines.line(TabRowLine("B|-3-----|", isStaffLine = true)).texts.single { it.layout.layoutInput.text.text == "3" }

        assertSame(first.layout, second.layout)
        assertTrue(first.x != second.x)
    }

    @Test
    fun `the rows of a run at two column counts share the layouts of the words they hold`() {
        val rows = TabRows(
            lines = listOf("  Am      C       G", "e|--5---|--3---|--3---|--7---|", "B|--5---|--1---|--0---|--8---|"),
            styles = styles,
            textMeasurer = textMeasurer,
            isPreformatted = false,
        )
        val whole = rows.at(Int.MAX_VALUE)
        val wrapped = rows.at((rows.naturalWidth * 0.6f).toInt())
        fun layoutOf(rows: List<TabRow>, text: String) = rows.flatMap { row -> row.lines.flatMap { it.texts } }
            .first { it.layout.layoutInput.text.text == text }.layout

        assertTrue(wrapped.size > whole.size)
        listOf("Am", "5", "7", "8").forEach { assertSame(layoutOf(whole, it), layoutOf(wrapped, it)) }
    }

    @Test
    fun `a string name and a fret of the same text keep their own styles`() {
        val texts = TabStaffLines(styles, textMeasurer).line(TabRowLine("3|---3---|", isStaffLine = true)).texts

        assertEquals(2, texts.size)
        assertNotSame(texts[0].layout, texts[1].layout)
        assertTrue(texts[0].layout.layoutInput.style.fontWeight != texts[1].layout.layoutInput.style.fontWeight)
    }
}
