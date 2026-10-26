/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordDiagramDrawingTest {

    @Test
    fun `under a capo an open string's ring leaves the bar and the string out, so whatever is behind shows through`() {
        // An A played as a G shape above the second fret, whose D, G, and B strings ring open at the capo.
        val shape = assertNotNull(ChordVoicings.default(ChordProChords.parse("G")!!, ChordTuning.GUITAR))
        val geometry = chordDiagramGeometryOf(shape, ChordTuning.GUITAR, root = 9, capo = 2) as ChordDiagramGeometry.Fretted
        assertNotNull(geometry.capoRow)
        val open = geometry.markers.indices.filter {
            geometry.markers[it] == ChordDiagramGeometry.Fretted.Marker.OPEN || geometry.markers[it] == ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT
        }
        assertTrue(open.isNotEmpty())

        val width = 300
        val height = 360
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawChordDiagram(
                geometry = geometry,
                area = size,
                lineColor = LINE,
                mutedColor = MUTED,
                rootColor = ROOT,
                backgroundColor = Color.White,
                textMeasurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
                fontFamily = null,
            )
        }
        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels)
        fun pixel(x: Int, y: Int) = pixels[y * width + x]
        fun isLine(argb: Int) = argb ushr 24 == 255 && argb and 255 == 255 && argb shr 8 and 255 == 0 && argb shr 16 and 255 == 0
        fun isMuted(argb: Int) = argb ushr 24 == 255 && argb shr 8 and 255 == 255 && argb and 255 == 0 && argb shr 16 and 255 == 0

        // The bar is the row with the most of the muted color, the strings the columns of the line color in most rows.
        val barY = (0 until height).maxBy { y -> (0 until width).count { x -> isMuted(pixel(x, y)) } }
        val lineCounts = (0 until width).map { x -> (0 until height).count { y -> isLine(pixel(x, y)) } }
        val stringColumns = (0 until width).filter { lineCounts[it] > height / 2 }
        val strings = stringColumns
            .fold(mutableListOf<MutableList<Int>>()) { groups, x ->
                groups.apply { if (lastOrNull()?.last() == x - 1) last().add(x) else add(mutableListOf(x)) }
            }
            .map { group -> group.maxBy { lineCounts[it] } }
        assertEquals(geometry.strings, strings.size)

        open.forEach { string -> assertEquals(0, pixel(strings[string], barY) ushr 24, "string $string at the bar") }
        val between = (strings[0] + strings[1]) / 2
        assertNotEquals(0, pixel(between, barY) ushr 24)
    }

    private companion object {
        val LINE = Color(0xFF0000FF)
        val MUTED = Color(0xFF00FF00)
        val ROOT = Color(0xFFFF0000)
    }
}
