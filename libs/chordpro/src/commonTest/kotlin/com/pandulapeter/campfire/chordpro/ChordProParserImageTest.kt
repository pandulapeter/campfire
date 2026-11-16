/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ImageAlignment
import com.pandulapeter.campfire.chordpro.model.ImageLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProParserImageTest {

    private fun images(text: String) = ChordProParser.parse(text).blocks.filterIsInstance<ChordProBlock.Image>()

    private fun image(value: String) = images("{image: $value}").single()

    @Test
    fun `an image names its file with src`() {
        assertEquals("score.png", image("src=score.png").src)
    }

    @Test
    fun `the short form names its file with its first word`() {
        val image = image("score.png width=200")

        assertEquals("score.png", image.src)
        assertEquals(ImageLength.Points(200.0), image.width)
        assertEquals(mapOf("src" to "score.png", "width" to "200"), image.attributes)
    }

    @Test
    fun `values are read in double quotes, single quotes, or none`() {
        val image = image("src=\"my score.png\" title='The first page' align=right")

        assertEquals("my score.png", image.src)
        assertEquals("The first page", image.title)
        assertEquals(ImageAlignment.RIGHT, image.alignment)
    }

    @Test
    fun `the short form takes a quoted file name`() {
        assertEquals("my score.png", image("\"my score.png\" center").src)
    }

    @Test
    fun `a quote nothing closes runs to the end of the value`() {
        assertEquals("my score.png width=2", image("src=\"my score.png width=2").src)
    }

    @Test
    fun `width and height are points or a percent`() {
        val image = image("src=a.png width=50% height=120")

        assertEquals(ImageLength.Percent(50.0), image.width)
        assertEquals(ImageLength.Points(120.0), image.height)
    }

    @Test
    fun `an unreadable or non-positive size is no size`() {
        listOf("abc", "0", "-5", "NaN", "1e3", "5d", "", "1.2.3", "%").forEach { written ->
            val image = image("src=a.png width=\"$written\" height=\"$written\"")

            assertNull(image.width, written)
            assertNull(image.height, written)
            assertEquals(written, image.attributes["width"], written)
        }
    }

    @Test
    fun `a scale is a factor or a percent`() {
        assertEquals(0.5, image("src=a.png scale=0.5").scale)
        assertEquals(0.2, image("src=a.png scale=20%").scale)
    }

    @Test
    fun `an unreadable, non-positive, or two-way scale is no scale`() {
        listOf("0", "-1", "big", "0.5,0.8", "").forEach { written ->
            assertNull(image("src=a.png scale=\"$written\"").scale, written)
        }
    }

    @Test
    fun `alignment comes from align, else from the deprecated center`() {
        assertEquals(ImageAlignment.LEFT, image("src=a.png align=Left").alignment)
        assertEquals(ImageAlignment.CENTER, image("src=a.png center").alignment)
        assertEquals(ImageAlignment.CENTER, image("src=a.png center=1").alignment)
        assertEquals(ImageAlignment.LEFT, image("src=a.png center=0").alignment)
        assertEquals(ImageAlignment.RIGHT, image("src=a.png align=right center").alignment)
        assertNull(image("src=a.png align=middle").alignment)
        assertNull(image("src=a.png").alignment)
    }

    @Test
    fun `every attribute is kept with its key lowercased, the first of a repeated one winning`() {
        val image = image("SRC=a.png X=10 y=20% border spread=5 href=\"https://example.com\" anchor=page trbl=tb x=99")

        assertEquals(
            mapOf("src" to "a.png", "x" to "10", "y" to "20%", "border" to "", "spread" to "5", "href" to "https://example.com", "anchor" to "page", "trbl" to "tb"),
            image.attributes,
        )
    }

    @Test
    fun `an image with a non-float anchor is still read where it stands`() {
        val blocks = ChordProParser.parse("[C]One\n{image: src=a.png anchor=paper}\n[G]Two").blocks

        assertEquals(3, blocks.size)
        assertEquals("a.png", (blocks[1] as ChordProBlock.Image).src)
    }

    @Test
    fun `an asset definition draws nothing and its id draws it`() {
        val blocks = ChordProParser.parse("{image: id=logo src=logo.png}\n{image: id=logo scale=50%}").blocks

        assertEquals(listOf(ChordProBlock.Image(src = "logo.png", scale = 0.5, attributes = mapOf("id" to "logo", "scale" to "50%"))), blocks)
    }

    @Test
    fun `a definition with another attribute both defines and draws`() {
        val images = images("{image: id=logo src=logo.png width=100}\n{image: id=logo}")

        assertEquals(listOf("logo.png", "logo.png"), images.map { it.src })
    }

    @Test
    fun `an id nothing defined draws nothing`() {
        assertTrue(images("{image: id=missing}\n{image: id=missing src=late.png}").isEmpty())
    }

    @Test
    fun `a redefined id draws the picture defined last`() {
        val images = images("{image: id=a src=one.png}\n{image: id=a center}\n{image: id=a src=two.png}\n{image: id=a center}")

        assertEquals(listOf("one.png", "two.png"), images.map { it.src })
    }

    @Test
    fun `an image naming no file draws nothing and never throws`() {
        listOf("{image}", "{image:}", "{image: }", "{image: src=}", "{image: src=\"\"}", "{image: center}", "{image: ===}", "{image: '}", "{image: \"}", "{image: width=10}")
            .forEach { line -> assertTrue(images(line).isEmpty(), line) }
    }

    @Test
    fun `an image with a selector suffix is not drawn`() {
        assertTrue(images("{image-piano: src=a.png}").isEmpty())
    }

    @Test
    fun `an image on a source comment line is not drawn`() {
        assertTrue(images("# {image: src=a.png}").isEmpty())
    }

    @Test
    fun `an image cuts the section it stands in, like a break`() {
        val blocks = ChordProParser.parse("{start_of_verse}\n[C]One\n{image: src=a.png}\n[G]Two\n{end_of_verse}").blocks

        assertEquals(3, blocks.size)
        assertEquals("a.png", (blocks[1] as ChordProBlock.Image).src)
        assertTrue((blocks[2] as ChordProBlock.Section).isContinuation)
    }

    @Test
    fun `an image between sections is a block of its own`() {
        val blocks = ChordProParser.parse("[C]One\n\n{image: src=a.png}\n\n[G]Two").blocks

        assertEquals(3, blocks.size)
        assertEquals("a.png", (blocks[1] as ChordProBlock.Image).src)
        assertTrue(!(blocks[2] as ChordProBlock.Section).isContinuation)
    }

    @Test
    fun `a song can be a header and an image alone`() {
        val song = ChordProParser.parse("{title: Chart}\n{artist: Someone}\n{image: src=chart.jpg}")

        assertEquals("Chart", song.metadata.title)
        assertEquals(listOf<ChordProBlock>(ChordProBlock.Image(src = "chart.jpg", attributes = mapOf("src" to "chart.jpg"))), song.blocks)
    }

    @Test
    fun `a key after an image is the song's key where the header has none`() {
        assertEquals("G", ChordProParser.parse("{image: src=chart.jpg}\n{key: G}").metadata.key)
    }

    @Test
    fun `an image written with a colon inside an abc block is a directive there`() {
        val blocks = ChordProParser.parse("{start_of_abc}\nX:1\n{image: src=a.png}\nK:C\n{end_of_abc}").blocks

        assertEquals(1, blocks.filterIsInstance<ChordProBlock.Image>().size)
    }

    @Test
    fun `images survive a round trip through the serializer`() {
        listOf(
            "{image: src=a.png}",
            "{title: T}\n{image: \"my file.png\" width=50% center border}",
            "{start_of_verse}\n[C]One\n{image: src=a.png scale=0.5 title='A \"quoted\" title'}\n[G]Two\n{end_of_verse}",
            "{image: id=logo src=logo.png}\n[C]One\n{image: id=logo align=left}",
            "{image: id=logo src=logo.png height=30}\n{start_of_tab}\ne|---|\n{image: src=b.png x=10 anchor=page}\ne|-0-|\n{end_of_tab}",
            "{image: SRC=a.png X=1 width=''}",
        ).forEach { text ->
            val parsed = ChordProParser.parse(text)

            assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)), text)
        }
    }
}
