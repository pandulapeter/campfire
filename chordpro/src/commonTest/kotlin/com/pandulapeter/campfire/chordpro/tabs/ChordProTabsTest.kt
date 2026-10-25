/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.tabs

import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.TabNotice
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.chordpro.model.TabTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordProTabsTest {

    @Test
    fun `a tab written a half step down is moved a fret down and renamed for a guitar in standard tuning`() {
        val song = tab(
            "Eb|-----------------|",
            "Bb|--------15-------|",
            "Gb|-----14-----14---|",
            "Db|-12-----------12-|",
            "Ab|-----------------|",
            "Eb|-----------------|",
        )

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR))

        assertEquals(
            listOf(
                "e|-----------------|",
                "B|--------14-------|",
                "G|-----13-----13---|",
                "D|-11-----------11-|",
                "A|-----------------|",
                "E|-----------------|",
            ),
            moved.tabLines(),
        )
        assertNull(moved.notices().singleOrNull())
    }

    @Test
    fun `a tab moved to the tuning it is written in keeps its frets and says nothing`() {
        val song = tab("Eb|--0--|", "Bb|--1--|", "Gb|--0--|", "Db|--2--|", "Ab|--3--|", "Eb|-----|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR_HALF_STEP_DOWN))

        assertEquals(listOf("d#|--0--|", "A#|--1--|", "F#|--0--|", "C#|--2--|", "G#|--3--|", "D#|-----|"), moved.tabLines())
        assertEquals(emptyList(), moved.notices())
    }

    @Test
    fun `a staff that names no strings is in standard tuning and is named`() {
        val song = tab("  Am", "|--0--|", "|--1--|", "|--2--|", "|--2--|", "|--0--|", "|-----|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR))

        assertEquals(listOf("   Am", "e|--0--|", "B|--1--|", "G|--2--|", "D|--2--|", "A|--0--|", "E|-----|"), moved.tabLines())
        assertEquals(emptyList(), moved.notices())
    }

    @Test
    fun `a staff that names some strings as standard tuning has the rest named`() {
        val song = tab("e|--0--|", "|--1--|", "|-----|", "|-----|", "|-----|", "E|-----|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR))

        assertEquals(listOf("e|--0--|", "B|--1--|", "G|-----|", "D|-----|", "A|-----|", "E|-----|"), moved.tabLines())
    }

    @Test
    fun `a staff whose names stand before the dashes without a bar is read and given its bars`() {
        val song = tab("  Am", "e --0--", "B --1--", "G --2--", "D --2--", "A --0--", "E -----")

        val moved = ChordProTransposer.transpose(song, 1, tabPlayer = player(GUITAR))

        assertEquals(listOf("  Bbm", "e|--1--", "B|--2--", "G|--3--", "D|--3--", "A|--1--", "E|-----"), moved.tabLines())
    }

    @Test
    fun `the systems a blank line separates are moved and named each on its own`() {
        val song = ChordProParser.parse("{start_of_tab}\n|--0--|\n|-----|\n|-----|\n|-----|\n|-----|\n|-----|\n\n|-----|\n|-----|\n|-----|\n|-----|\n|-----|\n|--3--|\n{end_of_tab}")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR_HALF_STEP_DOWN))

        assertEquals(listOf("d#|--1--|", "A#|-----|", "F#|-----|", "C#|-----|", "G#|-----|", "D#|-----|"), moved.tabLines().take(6))
        assertEquals("D#|--4--|", moved.tabLines().last())
        assertEquals(1, moved.blocks.filterIsInstance<ChordProBlock.Section>().single().lines.count { it == ChordProLine.Blank })
    }

    @Test
    fun `a drop D riff on an open string is moved up an octave for a guitar in standard tuning and says so`() {
        val song = tab("e|---------|", "B|---------|", "G|---------|", "D|---------|", "A|---------|", "D|--0---2--|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR))

        assertEquals("E|--10---12--|", moved.tabLines().last())
        assertEquals(listOf(TabNotice(TabNotice.Kind.MOVED, octaves = 1)), moved.notices())
    }

    @Test
    fun `the transposition and the tuning are one move`() {
        val song = tab("Eb|--0--|", "Bb|--1--|", "Gb|--0--|", "Db|--2--|", "Ab|--3--|", "Eb|-----|")

        val moved = ChordProTransposer.transpose(song, 1, tabPlayer = player(GUITAR))

        assertEquals(listOf("e|--0--|", "B|--1--|", "G|--0--|", "D|--2--|", "A|--3--|", "E|-----|"), moved.tabLines())
        assertEquals(emptyList(), moved.notices())
    }

    @Test
    fun `a transposition that takes a fret off the neck is an octave move with a notice`() {
        val moved = ChordProTransposer.transpose(tab("e|--0--|", "B|-----|", "G|-----|", "D|-----|", "A|-----|", "E|-----|"), -2, tabPlayer = player(GUITAR))

        assertEquals("e|--10--|", moved.tabLines().first())
        assertEquals(listOf(TabNotice(TabNotice.Kind.MOVED, octaves = 1)), moved.notices())
    }

    @Test
    fun `a bass tab is played on the low strings of a guitar`() {
        val song = tab("|--0--|", "|--2--|", "|-----|", "|-----|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(GUITAR))

        assertEquals(listOf("G|--0--|", "D|--2--|", "A|-----|", "E|-----|"), moved.tabLines())
        assertEquals(listOf(TabNotice(TabNotice.Kind.MOVED, instrument = "bass")), moved.notices())
    }

    @Test
    fun `a guitar tab on the four high strings is moved to a ukulele`() {
        val song = tab("e|--0--|", "B|--1--|", "G|--0--|", "D|--2--|", "A|-----|", "E|-----|")

        val moved = ChordProTransposer.transpose(song, 0, tabPlayer = player(UKULELE))

        assertEquals(listOf("A|--7--|", "E|--8--|", "C|--7--|", "G|--9--|"), moved.tabLines())
        assertEquals(listOf(TabNotice(TabNotice.Kind.MOVED, instrument = "guitar")), moved.notices())
    }

    @Test
    fun `a guitar tab that plays the low strings is shown as written to a ukulele`() {
        val lines = arrayOf("e|--0--|", "B|--1--|", "G|--0--|", "D|--2--|", "A|--3--|", "E|--0--|")

        val moved = ChordProTransposer.transpose(tab(*lines), 0, tabPlayer = player(UKULELE))

        assertEquals(lines.toList(), moved.tabLines())
        assertEquals(listOf(TabNotice(TabNotice.Kind.NOT_MOVED, instrument = "guitar")), moved.notices())
    }

    @Test
    fun `a reader with no tuning has an unnamed staff named and nothing moved`() {
        val named = ChordProTransposer.transpose(tab("Eb|--0--|", "Bb|-----|", "Gb|-----|", "Db|-----|", "Ab|-----|", "Eb|-----|"), 0, tabPlayer = player(null))
        val unnamed = ChordProTransposer.transpose(tab("|--0--|", "|-----|", "|-----|", "|-----|", "|-----|", "|-----|"), 0, tabPlayer = player(null))

        assertEquals("Eb|--0--|", named.tabLines().first())
        assertEquals(listOf("e|--0--|", "B|-----|", "G|-----|", "D|-----|", "A|-----|", "E|-----|"), unnamed.tabLines())
        assertEquals(emptyList(), named.notices() + unnamed.notices())
    }

    @Test
    fun `a reader with no tuning is shown the names a staff writes in their notation`() {
        val german = ChordProTransposer.transpose(tab("e|--0--|", "B|--1--|", "G|-----|", "D|-----|", "A|-----|", "E|-----|"), 0, tabPlayer = player(null, notation = ChordNotation.GERMAN))
        val latin = ChordProTransposer.transpose(
            tab("  Am", "Eb|--0--|", "Bb|-----|", "Gb|-----|", "Db|-----|", "Ab|-----|", "eb|-----|"),
            0,
            tabPlayer = player(null, notation = ChordNotation.LATIN),
        )

        assertEquals(listOf("e|--0--|", "H|--1--|", "G|-----|", "D|-----|", "A|-----|", "E|-----|"), german.tabLines())
        assertEquals(listOf("    Am", " Mib|--0--|", " Sib|-----|", "Solb|-----|", " Reb|-----|", " Lab|-----|", " mib|-----|"), latin.tabLines())
        assertEquals(emptyList(), german.notices() + latin.notices())
    }

    @Test
    fun `a staff of a number of strings no instrument has is left as it is`() {
        val lines = arrayOf("|--0--|", "|--1--|", "|--2--|")

        val moved = ChordProTransposer.transpose(tab(*lines), 0, tabPlayer = player(GUITAR))

        assertEquals(lines.toList(), moved.tabLines())
        assertEquals(emptyList(), moved.notices())
    }

    @Test
    fun `of the tabs written for several instruments the reader sees the ones for theirs`() {
        val song = ChordProParser.parse(SELECTED)

        assertEquals(listOf("A|--3--|"), ChordProTabs.select(song, listOf("ukulele")).tabLines())
        assertEquals(listOf("e|--0--|"), ChordProTabs.select(song, listOf("guitar_twelve_string", "guitar")).tabLines())
    }

    @Test
    fun `a reader with no tab of their own sees the guitar one with the comments written in it`() {
        val selected = ChordProTabs.select(ChordProParser.parse(SELECTED), listOf("mandolin"))

        assertEquals(listOf("e|--0--|"), selected.tabLines())
        assertTrue(selected.blocks.any { it is ChordProBlock.Comment && it.text == "Let it ring" })
    }

    @Test
    fun `a comment written in a tab that is not shown goes with it`() {
        val selected = ChordProTabs.select(ChordProParser.parse(SELECTED), listOf("ukulele"))

        assertTrue(selected.blocks.none { it is ChordProBlock.Comment })
    }

    @Test
    fun `a section whose first piece was all somebody else's tab keeps its heading on the piece that is left`() {
        val song = ChordProParser.parse(
            "{start_of_verse: Solo}\n{start_of_tab-guitar}\ne|--0--|\n{comment: again}\ne|--2--|\n{end_of_tab}\n{comment: For the ukulele}\n{start_of_tab-ukulele}\nA|--3--|\n{end_of_tab}\n{end_of_verse}",
        )

        val selected = ChordProTabs.select(song, listOf("ukulele"))

        val comment = selected.blocks[0] as ChordProBlock.Comment
        val section = selected.blocks[1] as ChordProBlock.Section
        assertEquals("For the ukulele", comment.text)
        assertEquals(CommentPlacement.START_OF_SECTION, comment.placement)
        assertEquals("Solo", section.label)
        assertTrue(!section.isContinuation)
        assertEquals(listOf("A|--3--|"), selected.tabLines())
    }

    @Test
    fun `a tab with a negated selector is shown to everybody but the players of that instrument`() {
        val song = ChordProParser.parse("{start_of_tab-guitar!}\nA|--3--|\n{end_of_tab}")

        assertEquals(emptyList(), ChordProTabs.select(song, listOf("guitar")).tabLines())
        assertEquals(listOf("A|--3--|"), ChordProTabs.select(song, listOf("ukulele")).tabLines())
    }

    @Test
    fun `a notice is written above the run it is about, in the section it is in`() {
        val song = ChordProParser.parse("{start_of_verse}\nLa la la\n{start_of_tab}\ne|--0--|\n{end_of_tab}\n{end_of_verse}")

        val noticed = ChordProTabs.withNotices(song.withTopLineNotice(), player(GUITAR)) { "moved" }

        val comment = noticed.blocks[1] as ChordProBlock.Comment
        assertEquals("moved", comment.text)
        assertEquals(CommentPlacement.IN_SECTION, comment.placement)
        assertTrue((noticed.blocks[2] as ChordProBlock.Section).isContinuation)
    }

    @Test
    fun `an instrument without strings has every tab taken out and a notice in its place`() {
        val song = ChordProParser.parse("{start_of_tab: Riff}\n{comment: Let it ring}\n|--0--|\n|-----|\n|-----|\n|-----|\n|-----|\n|-----|\n{end_of_tab}")

        val hidden = ChordProTabs.withNotices(song, player(null, hidesTabs = true)) { notice -> "${notice.kind} ${notice.instrument}" }
        val twoSystems = ChordProParser.parse("{start_of_tab}\n|--0--|\n|-----|\n|-----|\n|-----|\n|-----|\n|-----|\n\n|--0--|\n|-----|\n|-----|\n|-----|\n|-----|\n|-----|\n{end_of_tab}")

        assertEquals(emptyList(), hidden.tabLines())
        assertEquals(
            listOf(ChordProBlock.Comment("HIDDEN guitar", hidden.comments().single().style, CommentPlacement.BETWEEN_SECTIONS, isInTabOrGrid = true)),
            hidden.comments(),
        )
        assertEquals("HIDDEN guitar", ChordProTabs.withNotices(twoSystems, player(null, hidesTabs = true)) { "${it.kind} ${it.instrument}" }.comments().single().text)
    }

    private fun tab(vararg lines: String) = ChordProParser.parse("{start_of_tab}\n${lines.joinToString("\n")}\n{end_of_tab}")

    private fun ChordProSong.tabLines() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.filterIsInstance<ChordProLine.Tab>().map { it.text }

    private fun ChordProSong.notices() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.filterIsInstance<ChordProLine.Tab>().mapNotNull { it.notice }

    private fun ChordProSong.comments() = blocks.filterIsInstance<ChordProBlock.Comment>()

    /** [this] with a notice on the first line of its every run, whatever moving it said, so that one is written. */
    private fun ChordProSong.withTopLineNotice() = copy(
        blocks = blocks.map { block ->
            if (block !is ChordProBlock.Section) return@map block
            block.copy(lines = block.lines.map { line -> if (line is ChordProLine.Tab && !line.continuesEnvironment) line.copy(notice = TabNotice(TabNotice.Kind.MOVED, octaves = 1)) else line })
        },
    )

    private fun player(tuning: TabTuning?, hidesTabs: Boolean = false, notation: ChordNotation = ChordNotation.STANDARD) = TabPlayer(
        selectors = listOfNotNull(tuning?.instrument),
        tuning = tuning,
        hidesTabs = hidesTabs,
        assumedTuning = { notes, selector ->
            STANDARD_TUNINGS.firstOrNull { it.instrument == selector && it.strings.size == notes.size }
                ?: STANDARD_TUNINGS.firstOrNull { it.instrument == tuning?.instrument && it.strings.size == notes.size }
                ?: STANDARD_TUNINGS.firstOrNull { it.strings.size == notes.size }
        },
        stringName = { note -> NOTE_NAMES[note.mod(12)] },
        notation = notation,
    )

    private companion object {
        val GUITAR = TabTuning("guitar", listOf(40, 45, 50, 55, 59, 64))
        val GUITAR_HALF_STEP_DOWN = TabTuning("guitar", listOf(39, 44, 49, 54, 58, 63))
        val BASS = TabTuning("bass", listOf(28, 33, 38, 43))
        val UKULELE = TabTuning("ukulele", listOf(67, 60, 64, 69))

        /** The guitar's before the bass's, as the app has a staff of six strings: the first of as many strings is the likely one. */
        val STANDARD_TUNINGS = listOf(GUITAR, BASS, UKULELE)
        val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        const val SELECTED = """{start_of_tab-guitar: Riff}
{comment: Let it ring}
e|--0--|
{end_of_tab}

{start_of_tab-ukulele: Riff}
A|--3--|
{end_of_tab}"""
    }
}
