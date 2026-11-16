/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSummaryCache
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChordProImagesTest {

    @Test
    fun `every drawn image's file is referenced`() {
        assertEquals(setOf("a.png", "b c.png"), ChordProImages.referencedSources("{image: src=a.png}\n[C]La\n{image: \"b c.png\" width=20}"))
    }

    @Test
    fun `files the song does not draw are referenced too`() {
        val text = """
            {image: id=logo src=logo.png}
            {image-piano: src=piano.png}
            {image-guitar!: src=negated.png}
            # {image: src=commented.png}
            #{image: src=tight.png}
              ## {image: src=deep.png}
            {start_of_abc}
            {image: src=abc.png}
            # {image: src=abc-comment.png}
            {end_of_abc}
            {start_of_ly}
            {image lily.png}
            {end_of_ly}
            {image: id=nothing}
        """.trimIndent()

        assertEquals(
            setOf("logo.png", "piano.png", "negated.png", "commented.png", "tight.png", "deep.png", "abc.png", "abc-comment.png", "lily.png"),
            ChordProImages.referencedSources(text),
        )
    }

    @Test
    fun `an empty file name and lines that are no image directive are not referenced`() {
        val text = "{image: src=}\n{image: src=\"\"}\n{image}\n{comment: {image: src=a.png}}\n[C]{image: src=b.png}\n{title: src=c.png}\n# src=d.png"

        assertTrue(ChordProImages.referencedSources(text).isEmpty())
    }

    @Test
    fun `a rewrite changes only the file name`() {
        val text = "{title: T}\n{image: width=50% src=old.png title=\"A picture\" center}\n[C]La"

        assertEquals(
            "{title: T}\n{image: width=50% src=new.png title=\"A picture\" center}\n[C]La",
            ChordProImages.withSourcesRewritten(text) { if (it == "old.png") "new.png" else null },
        )
    }

    @Test
    fun `a rewrite keeps the file's line breaks and its missing last one`() {
        val text = "{title: T}\r\n{image:src='old file.png'}\r\n[C]La"

        assertEquals("{title: T}\r\n{image:src=new.png}\r\n[C]La", ChordProImages.withSourcesRewritten(text) { "new.png" })
    }

    @Test
    fun `a rewrite quotes a new value only where it needs quotes`() {
        assertEquals("{image: src=\"a b.png\"}\n", ChordProImages.withSourcesRewritten("{image: src=a.png}\n") { "a b.png" })
        assertEquals("{image: src='say \"hi\".png'}", ChordProImages.withSourcesRewritten("{image: src=a.png}") { "say \"hi\".png" })
        assertEquals("{image: \"x=y.png\" center}", ChordProImages.withSourcesRewritten("{image: a.png center}") { "x=y.png" })
    }

    @Test
    fun `a rewrite keeps the short form short`() {
        assertEquals("{image: b.png width=10}", ChordProImages.withSourcesRewritten("{image: \"a.png\" width=10}") { "b.png" })
    }

    @Test
    fun `a rewrite reaches every line referencedSources reads`() {
        val text = "# {image: src=a.png}\n{image-piano: src=a.png}\n{image: id=x src=a.png}\n{start_of_abc}\n{image: src=a.png}\n{end_of_abc}"
        val rewritten = ChordProImages.withSourcesRewritten(text) { "b.png" }

        assertEquals(text.replace("a.png", "b.png"), rewritten)
        assertEquals(setOf("b.png"), ChordProImages.referencedSources(rewritten))
    }

    @Test
    fun `a rewrite that changes nothing returns the text itself`() {
        val text = "{image: src=a.png}\n\r\n"

        assertSame(text, ChordProImages.withSourcesRewritten(text) { null })
        assertSame(text, ChordProImages.withSourcesRewritten(text) { it })
        assertSame(text, ChordProImages.withSourcesRewritten(text) { "a\nb.png" })
    }

    @Test
    fun `a rewrite leaves an unterminated quote's directive readable`() {
        val rewritten = ChordProImages.withSourcesRewritten("{image: src=\"old png}") { "new.png" }

        assertEquals(setOf("new.png"), ChordProImages.referencedSources(rewritten))
    }

    @Test
    fun `the inserted directive draws its file`() {
        listOf("a.png", "my picture.jpg", "it's.png", "x=y.png").forEach { src ->
            val image = ChordProParser.parse(ChordProImages.directive(src)).blocks.single() as ChordProBlock.Image

            assertEquals(src, image.src)
        }
        assertEquals("{image: src=a.png}", ChordProImages.directive("a.png"))
    }

    @Test
    fun `the summary names the same files as referencedSources`() {
        val corpus = listOf(
            "",
            "{image: src=a.png}",
            "{title: T}\n{image: \"b c.png\" width=20}\n[C]La\n# {image: src=commented.png}",
            "{start_of_abc}\nX:1\n{image: src=abc.png}\n{image nocolon.png}\n# {image: src=hash.png}\nK:C\n{end_of_abc}",
            "{start_of_tab}\ne|---|\n{image: src=tab.png}\n{end_of_tab}\n{image-piano: src=p.png}\n{image-x!: n.png}",
            "{image: id=a src=def.png}\n{image: id=a}\n{image: id=b}\n{image: src=}\n{image}",
            "[C]Chords first\n[G]then\n{image: src=late.png}\n\t#\t{image:src='late comment.png'}",
            "{start_of_ly}\n{image: src=ly.png}\n{end_of_ly}\n{start_of_verse}\n{image: src=\"unterminated.png\n{end_of_verse}",
        )
        corpus.forEach { text ->
            val expected = ChordProImages.referencedSources(text)

            assertEquals(expected, ChordProParser.summarize(text).imageSources, text)
            assertEquals(expected, ChordProSummaryCache().summaryOf(text).imageSources, text)
        }
    }

    @Test
    fun `the summary cache follows an image typed line by line`() {
        val cache = ChordProSummaryCache()
        val texts = listOf("[C]La", "[C]La\n{image: src=a.png}", "[C]La\n# {image: src=a.png}", "[C]La\n# {image: src=ab.png}", "[C]Lab\n# {image: src=ab.png}")

        texts.forEach { text -> assertEquals(ChordProImages.referencedSources(text), cache.summaryOf(text).imageSources, text) }
    }
}
