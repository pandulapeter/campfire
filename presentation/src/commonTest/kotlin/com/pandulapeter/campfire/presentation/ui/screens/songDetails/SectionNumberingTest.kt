/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SectionNumberingTest {

    private val labels = DefaultSectionLabels(
        verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus",
        solo = "Solo", outro = "Outro", shouldNumberSections = true,
    )

    private fun headers(text: String, labels: DefaultSectionLabels = this.labels) =
        ChordProParser.parse(text).blocks.withNumberedSections(labels).filterIsInstance<ChordProBlock.Section>().map { section -> section.takeIf { it.label != null || it.number != null }?.header(labels) }

    @Test
    fun `sections of a kind that has several are numbered in order`() {
        val text = "{sov}\na\n{eov}\n{soc}\nb\n{eoc}\n{sov}\nc\n{eov}\n{sov}\nd\n{eov}"
        assertEquals(listOf("Verse 1", null, "Verse 2", "Verse 3"), headers(text))
    }

    @Test
    fun `a labeled section keeps its label and takes no number`() {
        val text = "{sov: Intro words}\na\n{eov}\n{sov}\nb\n{eov}\n{sov}\nc\n{eov}"
        assertEquals(listOf("Intro words", "Verse 1", "Verse 2"), headers(text))
    }

    @Test
    fun `a label naming the kind and a number reserves it`() {
        val text = "{sov: Verse 1}\na\n{eov}\n{sov}\nb\n{eov}\n{sov}\nc\n{eov}"
        assertEquals(listOf("Verse 1", "Verse 2", "Verse 3"), headers(text))
    }

    @Test
    fun `a number reserved later is skipped`() {
        val text = "{sov}\na\n{eov}\n{sov: Verse 2}\nb\n{eov}\n{sov}\nc\n{eov}"
        assertEquals(listOf("Verse 1", "Verse 2", "Verse 3"), headers(text))
    }

    @Test
    fun `a label that is just the kind's name is numbered with the others`() {
        val text = "{sov: Verse}\na\n{eov}\n{sov}\nb\n{eov}"
        assertEquals(listOf("Verse 1", "Verse 2"), headers(text))
    }

    @Test
    fun `the kind's ChordPro name counts whatever the app's language`() {
        val text = "{sov: verse 1}\na\n{eov}\n{sov}\nb\n{eov}"
        assertEquals(listOf("verse 1", "Versszak 2"), headers(text, labels.copy(verse = "Versszak")))
    }

    @Test
    fun `a recalled chorus with the kind's bare name carries its number`() {
        val text = "{soc}\na\n{eoc}\n{soc: Chorus}\nb\n{eoc}\n{chorus}"
        assertEquals(listOf("Chorus 1", "Chorus 2"), headers(text))
        val recall = ChordProParser.parse(text).blocks.withNumberedSections(labels).filterIsInstance<ChordProBlock.ChorusRecall>().single()
        assertEquals(2, recall.blocks.filterIsInstance<ChordProBlock.Section>().first().number)
    }

    @Test
    fun `a kind label with an absurd number is just a label`() {
        val text = "{sov: Verse 99999999999}\na\n{eov}\n{sov}\nb\n{eov}"
        assertEquals(listOf("Verse 99999999999", null), headers(text))
    }

    @Test
    fun `custom kinds are numbered by their own name`() {
        val text = "{start_of_solo}\na\n{end_of_solo}\n{start_of_solo}\nb\n{end_of_solo}"
        assertEquals(listOf("Solo 1", "Solo 2"), headers(text))
    }

    @Test
    fun `a recalled chorus carries the number of the most recent one`() {
        val blocks = ChordProParser.parse("{soc}\na\n{eoc}\n{soc}\nb\n{eoc}\n{chorus}").blocks.withNumberedSections(labels)
        val recall = blocks.filterIsInstance<ChordProBlock.ChorusRecall>().single()
        assertEquals(2, (recall.blocks.first() as ChordProBlock.Section).number)
    }

    @Test
    fun `a recalled chorus that opens with a comment carries the number of the most recent one`() {
        val blocks = ChordProParser.parse("{soc}\n{c: Softly}\na\n{eoc}\n{soc}\n{c: Loud}\nb\n{eoc}\n{chorus}").blocks.withNumberedSections(labels)
        val recall = blocks.filterIsInstance<ChordProBlock.ChorusRecall>().single()
        assertEquals(2, recall.blocks.filterIsInstance<ChordProBlock.Section>().first().number)
    }

    @Test
    fun `nothing changes when the setting is off`() {
        val blocks = ChordProParser.parse("{sov}\na\n{eov}\n{sov}\nb\n{eov}").blocks
        assertSame(blocks, blocks.withNumberedSections(labels.copy(shouldNumberSections = false)))
    }
}
