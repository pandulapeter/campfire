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

import com.pandulapeter.campfire.abc.api.AbcSystem
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.SYSTEM
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.system
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Over systems shaped as abc2svg writes them: the styles and shared shapes in the first, used by the ones after it. */
class NotationDrawingParserTest {

    @Test
    fun `a system uses the styles and shapes an earlier system of the rendering defined`() {
        val drawing = assertNotNull(parseNotationDrawing(listOf(HEADER, SYSTEM)))
        val system = drawing.systems.single()
        assertEquals(360f, system.width)
        assertEquals(92.4f, system.height)
        val staff = system.shapes.first()
        assertEquals(NotationStroke(width = 0.7f, dashes = null), staff.stroke)
        assertEquals(NotationTransform(x = 0f, y = 61.6f, scale = 1f), staff.transform)
        assertEquals(NotationStroke(width = 3f, dashes = null), system.shapes[1].stroke)
        assertNull(system.shapes[2].stroke)
    }

    @Test
    fun `a system that draws nothing is left out`() {
        assertEquals(1, parseNotationDrawing(listOf(HEADER, SYSTEM))?.systems?.size)
    }

    @Test
    fun `music symbols are placed by their own coordinates`() {
        val glyphs = parseNotationDrawing(listOf(HEADER, SYSTEM))!!.systems.single().glyphs
        assertEquals(listOf(10f to 61.6f, 20f to 55.6f), glyphs.map { it.x to it.y })
        assertEquals(listOf(abcMusicGlyphs[0xE050], abcMusicGlyphs[0xE0A4]), glyphs.map { it.glyph })
        assertTrue(glyphs.all { it.size == 24f })
    }

    @Test
    fun `chord symbols and lyrics are told apart, and a group moves its words`() {
        val texts = parseNotationDrawing(listOf(HEADER, SYSTEM))!!.systems.single().texts
        val chord = texts.single { (it.runs.single() as NotationTextRun.Words).text == "Em" }
        val lyric = texts.single { (it.runs.single() as NotationTextRun.Words).text == "la" }
        assertEquals(NotationTextRole.CHORD, (chord.runs.single() as NotationTextRun.Words).role)
        assertEquals(NotationTextRole.LYRIC, (lyric.runs.single() as NotationTextRun.Words).role)
        assertEquals(NotationFont(size = 16f, isBold = true, isItalic = false, isMusic = false), (chord.runs.single() as NotationTextRun.Words).font)
        assertEquals(61.6f - 39.1f, chord.y, 0.001f)
        assertEquals(NotationAnchor.MIDDLE, lyric.anchor)
        assertEquals(NotationFont(size = 13f, isBold = true, isItalic = false, isMusic = false), (lyric.runs.single() as NotationTextRun.Words).font)
    }

    @Test
    fun `a tempo mixes a music symbol into its words`() {
        val tempo = parseNotationDrawing(listOf(HEADER, system("""<style>.f4{font:9.0px text,serif}</style><text class="f4" x="50" y="20"><tspan class="f0" style="font-size:15.6px" dy="-1">&#xECA5;</tspan> <tspan dy="1">=</tspan> 120</text>""")))!!
            .systems.single().texts.single()
        val glyphs = tempo.runs[0] as NotationTextRun.Glyphs
        assertEquals(15.6f, glyphs.size)
        assertEquals(-1f, glyphs.dy)
        assertEquals(listOf(" ", "=", " 120"), tempo.runs.drop(1).map { (it as NotationTextRun.Words).text })
        assertEquals(listOf(-1f, 0f, 0f), tempo.runs.drop(1).map { it.dy })
    }

    @Test
    fun `a scaled group scales what it holds`() {
        val glyph = parseNotationDrawing(listOf(HEADER, system("""<g transform="translate(100,50) scale(0.5)"><text x="10" y="4">&#xE050;</text></g>""")))!!
            .systems.single().glyphs.single()
        assertEquals(105f, glyph.x)
        assertEquals(52f, glyph.y)
        assertEquals(12f, glyph.size)
    }

    @Test
    fun `circles and ellipses are shapes like paths`() {
        val shapes = parseNotationDrawing(listOf(HEADER, system("""<circle cx="4" cy="5" r="1.5"/><ellipse class="slW" rx="2.5" ry="2"/>""")))!!.systems.single().shapes
        assertEquals("M2.5 5.0a1.5 1.5 0 1 0 3.0 0a1.5 1.5 0 1 0 -3.0 0z", shapes[0].path)
        assertNull(shapes[0].stroke)
        assertEquals(NotationStroke(width = 0.7f, dashes = null), shapes[1].stroke)
    }

    @Test
    fun `words placed a character at a time become a text each`() {
        val texts = parseNotationDrawing(listOf(HEADER, system("""<style>.fng {font:6px sans-serif}</style><text x="-4,0,4" y="-26" class="fng">x 2</text>""")))!!
            .systems.single().texts
        assertEquals(listOf("x" to -4f, "2" to 4f), texts.map { (it.runs.single() as NotationTextRun.Words).text to it.x })
    }

    @Test
    fun `a class may set a text's anchor, its box, and a superscript's size and raised baseline`() {
        val text = parseNotationDrawing(
            listOf(
                HEADER,
                system(
                    """<style>.f3{font:12.0px sans-serif}.mid {text-anchor:middle}.box{outline:1px solid black;outline-offset:1px}.jc8{baseline-shift:25%;font-size:75%}</style>""" +
                        """<text class="f3 gch box mid" x="10" y="20">D<tspan class="jc8">7</tspan></text>""",
                ),
            ),
        )!!.systems.single().texts.single()
        assertEquals(NotationAnchor.MIDDLE, text.anchor)
        assertTrue(text.isBoxed)
        val superscript = text.runs[1] as NotationTextRun.Words
        assertEquals(9f, superscript.font.size)
        assertEquals(-3f, superscript.dy)
        assertEquals(0f, text.runs[0].dy)
    }

    @Test
    fun `anything the drawing does not know gives no drawing`() {
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<image href="x.png"/>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<g transform="rotate(45)"><path d="M0 0h1"/></g>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<text x="1" y="1">&#xF8FF;</text>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<use x="0" y="0" xlink:href="#missing"/>"""))))
        assertNull(parseNotationDrawing(listOf(AbcSystem(svg = "<svg", height = 1f))))
    }
}
