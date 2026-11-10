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
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.TAB_HEADER
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.TAB_SYSTEM
import com.pandulapeter.campfire.presentation.ui.notation.NotationFixtures.system
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(NotationTransform(y = 61.6f), staff.transform)
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
        assertEquals(listOf(10f to 61.6f, 20f to 55.6f), glyphs.map { it.transform.x to it.transform.y })
        assertEquals(listOf(abcMusicGlyphs[0xE050], abcMusicGlyphs[0xE0A4]), glyphs.map { it.glyph })
        assertTrue(glyphs.all { it.transform.scale * AbcMusicGlyph.UNITS_PER_EM == 24f })
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
        assertEquals(105f, glyph.transform.x)
        assertEquals(52f, glyph.transform.y)
        assertEquals(12f, glyph.transform.scale * AbcMusicGlyph.UNITS_PER_EM)
    }

    @Test
    fun `the brace joining a grand staff is drawn stretched to the staves' height`() {
        val brace = parseNotationDrawing(listOf(HEADER, system("""<text transform="translate(51.7,199.6) scale(2.5,3.42)">&#xE000;</text>""")))!!
            .systems.single().glyphs.single()
        assertEquals(abcMusicGlyphs[0xE000], brace.glyph)
        assertEquals(51.7f to 199.6f, brace.transform.x to brace.transform.y)
        assertEquals(2.5f * 24f, brace.transform.a * AbcMusicGlyph.UNITS_PER_EM, 0.001f)
        assertEquals(3.42f * 24f, brace.transform.d * AbcMusicGlyph.UNITS_PER_EM, 0.001f)
    }

    @Test
    fun `a turned or mirrored group turns or mirrors its symbols and lines`() {
        val drawing = parseNotationDrawing(
            listOf(
                HEADER,
                system(
                    """<g transform="translate(100,50) rotate(270)"><text x="0" y="6">&#xE000;</text><path d="M0 0h10"/></g>""" +
                        """<g transform="translate(10,20) scale(1,-1)"><text x="0" y="0">&#xE4C0;</text></g>""",
                ),
            ),
        )!!.systems.single()
        val (arpeggio, fermata) = drawing.glyphs.map { it.transform }
        assertEquals(106f, arpeggio.x, 0.001f)
        assertEquals(50f, arpeggio.y, 0.001f)
        assertEquals(-1f, arpeggio.b * AbcMusicGlyph.UNITS_PER_EM / 24f, 0.001f)
        assertEquals(10f to 20f, fermata.x to fermata.y)
        assertEquals(-24f, fermata.d * AbcMusicGlyph.UNITS_PER_EM, 0.001f)
        val line = drawing.shapes.single().transform
        assertEquals(100f, line.mapX(10f, 0f), 0.001f)
        assertEquals(40f, line.mapY(10f, 0f), 0.001f)
    }

    @Test
    fun `a boxed tempo's rectangle is a shape, and a page background is left to the page`() {
        val shapes = parseNotationDrawing(
            listOf(HEADER, system("""<rect width="100%" height="100%" fill="white"/><rect class="slW" x="10" y="20" width="30" height="15"/>""")),
        )!!.systems.single().shapes
        assertEquals("M10.0 20.0H40.0V35.0H10.0Z", shapes.single().path)
        assertEquals(NotationStroke(width = 0.7f, dashes = null), shapes.single().stroke)
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
    fun `a system counts its bars once across its staves`() {
        val grandStaff = system(
            """
            <style>
            .bW{stroke:currentColor;fill:none;stroke-width:1}
            </style>
            <path class="bthW" d="M40.0 61.6v-24.0M40.0 121.6v-24.0"/>
            <path class="bW" d="M44.5 61.6v-24.0M44.5 121.6v-24.0M150.0 61.6v-24.0M150.0 121.6v-24.0M300.0 61.6v-24.0M300.0 121.6v-24.0"/>
            <path class="bthW" d="M304.0 61.6v-24.0M304.0 121.6v-24.0"/>
            <text x="10.0,60.0,200.0" y="61.6,55.6,55.6">&#xE050;&#xE0A4;&#xE0A4;</text>
            """.trimIndent(),
        )
        assertEquals(2, parseNotationDrawing(listOf(HEADER, grandStaff))!!.systems.single().bars)
        val title = system("""<style>.f3{font:700 16.0px text,sans-serif}</style><text class="f3" x="180.0" y="20.0">A Reel</text>""")
        assertEquals(0, parseNotationDrawing(listOf(HEADER, title))!!.systems.single().bars)
    }

    @Test
    fun `a tablature's frets are drawn over their flooded background`() {
        val system = assertNotNull(parseNotationDrawing(listOf(TAB_HEADER, TAB_SYSTEM))).systems.last()
        val frets = system.texts.filter { it.isKnockedOut }
        assertEquals("1301357887531031", frets.joinToString("") { (it.runs.single() as NotationTextRun.Words).text })
        assertEquals(NotationFont(size = 7f * 1.6f, isBold = true, isItalic = false, isMusic = false), (frets.first().runs.single() as NotationTextRun.Words).font)
        assertEquals(59.5f * 1.6f, frets.first().x, 0.001f)
        assertEquals(71f - 21.5f * 1.6f, frets.first().y, 0.001f)
    }

    @Test
    fun `a tablature's clef reads TAB in the bold sans-serif its own style gives it`() {
        val system = assertNotNull(parseNotationDrawing(listOf(TAB_HEADER, TAB_SYSTEM))).systems.last()
        val clef = system.texts.filter { (it.runs.single() as? NotationTextRun.Words)?.text in listOf("T", "A", "B") && !it.isKnockedOut }
        assertEquals(listOf("T", "A", "B"), clef.map { (it.runs.single() as NotationTextRun.Words).text })
        clef.forEach { letter ->
            assertEquals(NotationFont(size = 13f * 1.6f, isBold = true, isItalic = false, isMusic = false), (letter.runs.single() as NotationTextRun.Words).font)
            assertFalse(letter.isKnockedOut)
        }
    }

    @Test
    fun `a system of tablature alone plays the bars its staff draws`() {
        assertEquals(2, parseNotationDrawing(listOf(TAB_HEADER, TAB_SYSTEM))!!.systems.last().bars)
    }

    @Test
    fun `a tablature's clef naming its strings draws them knocked out, and plays no bar`() {
        // The clef the app's host writes in place of TAB, and a bar line opening the staff between it and the first fret.
        val names = listOf("E", "A", "D", "G", "B", "e").mapIndexed { string, name ->
            "<text class=\"bg1\" x=\"-7.5\" y=\"${-6 * string + 8.5}\" style=\"font:7px sans-serif\">$name</text>"
        }
        val named = AbcSystem(
            svg = TAB_SYSTEM.svg
                .replace(Regex("<text id=\"tab6\"[^>]*>TAB</text>"), names.joinToString(separator = "\n", prefix = "<g id=\"tab6\">\n", postfix = "\n</g>"))
                .replace("M234.8 71.0v-48.0", "M80.0 71.0v-48.0M234.8 71.0v-48.0"),
            height = TAB_SYSTEM.height,
        )
        val system = assertNotNull(parseNotationDrawing(listOf(TAB_HEADER, named))).systems.last()
        val clef = system.texts.filter { (it.runs.single() as NotationTextRun.Words).text in listOf("E", "A", "D", "G", "B", "e") }
        assertEquals(listOf("E", "A", "D", "G", "B", "e"), clef.map { (it.runs.single() as NotationTextRun.Words).text })
        clef.forEach { name ->
            assertTrue(name.isKnockedOut)
            assertEquals(NotationFont(size = 7f * 1.6f, isBold = false, isItalic = false, isMusic = false), (name.runs.single() as NotationTextRun.Words).font)
        }
        assertEquals(2, system.bars)
    }

    @Test
    fun `a filter that does more than flood the background gives no drawing`() {
        val blurred = AbcSystem(svg = TAB_SYSTEM.svg.replace("<feFlood flood-color=\"white\"/>", "<feGaussianBlur stdDeviation=\"2\"/>"), height = TAB_SYSTEM.height)
        assertNull(parseNotationDrawing(listOf(TAB_HEADER, blurred)))
        val composited = AbcSystem(svg = TAB_SYSTEM.svg.replace("operator=\"over\"", "operator=\"xor\""), height = TAB_SYSTEM.height)
        assertNull(parseNotationDrawing(listOf(TAB_HEADER, composited)))
        val unnamed = AbcSystem(svg = TAB_SYSTEM.svg.replace(".bg1{filter:url(#bg1)}", ".bg1{filter:url(#bg2)}"), height = TAB_SYSTEM.height)
        assertNull(parseNotationDrawing(listOf(TAB_HEADER, unnamed)))
    }

    @Test
    fun `anything the drawing does not know gives no drawing`() {
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<image href="x.png"/>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<g transform="skewX(45)"><path d="M0 0h1"/></g>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<text x="1" y="1">&#xF8FF;</text>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<use x="0" y="0" xlink:href="#missing"/>"""))))
        assertNull(parseNotationDrawing(listOf(HEADER, system("""<style>.f3{font:12.0px sans-serif}</style><text class="f3" transform="rotate(90)">Fine</text>"""))))
        assertNull(parseNotationDrawing(listOf(AbcSystem(svg = "<svg", height = 1f))))
    }
}
