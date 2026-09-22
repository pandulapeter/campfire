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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChordProTabWrapperTest {

    private val riff = listOf(
        "  Am        C         G         D",
        "e|---------|---------|---------|---------|",
        "B|---1-----|---1-----|---0-----|---3-----|",
        "G|-----2---|-----0---|-----0---|-----2---|",
        "D|-2-------|-2-------|-0-------|-0-------|",
        "A|-0-------|-3-------|---------|---------|",
        "E|---------|---------|-3-------|---------|",
    )

    @Test
    fun `a staff indented with a non-breaking space keeps its string names in the continuation rows`() {
        val lines = listOf(
            "\u00A0e|---0---|---2---|",
            "\u00A0B|---1---|---3---|",
        )

        assertEquals(
            listOf(
                listOf("\u00A0e|---0---|", "\u00A0B|---1---|"),
                listOf("\u00A0e|---2---|", "\u00A0B|---3---|"),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 12),
        )
    }

    @Test
    fun `a run that fits is returned as it is`() {
        val rows = ChordProTabWrapper.wrap(riff, maxColumns = riff.maxOf { it.length })
        assertEquals(1, rows.size)
        assertSame(riff, rows.single())
    }

    @Test
    fun `a run without a staff is not tablature and is returned whole`() {
        val lines = listOf(
            "    Am        C",
            "Hello darkness my old friend",
        )
        assertFalse(ChordProTabWrapper.isTablature(lines))
        assertTrue(ChordProTabWrapper.isTablature(riff))
        assertEquals(listOf(lines), ChordProTabWrapper.wrap(lines, maxColumns = 10))
    }

    @Test
    fun `the rows are cut after a bar line and repeat the string names`() {
        assertEquals(
            listOf(
                listOf(
                    "  Am        C",
                    "e|---------|---------|",
                    "B|---1-----|---1-----|",
                    "G|-----2---|-----0---|",
                    "D|-2-------|-2-------|",
                    "A|-0-------|-3-------|",
                    "E|---------|---------|",
                ),
                listOf(
                    "  G         D",
                    "e|---------|---------|",
                    "B|---0-----|---3-----|",
                    "G|-----0---|-----2---|",
                    "D|-0-------|-0-------|",
                    "A|---------|---------|",
                    "E|-3-------|---------|",
                ),
            ),
            ChordProTabWrapper.wrap(riff, maxColumns = 25),
        )
    }

    @Test
    fun `a row holds as many whole bars as fit`() {
        val rows = ChordProTabWrapper.wrap(riff, maxColumns = 35)
        assertEquals(2, rows.size)
        assertEquals("e|---------|---------|---------|", rows[0][1])
        assertEquals("e|---------|", rows[1][1])
    }

    @Test
    fun `a bar wider than the row is cut on a quiet column`() {
        val lines = listOf(
            "   Am          C",
            "e|" + "-".repeat(31) + "|",
            "B|---1-----1-----1" + "-".repeat(15) + "|",
        )
        assertEquals(
            listOf(
                listOf(
                    "   Am          C",
                    "e|" + "-".repeat(18),
                    "B|---1-----1-----1--",
                ),
                listOf(
                    "e|" + "-".repeat(13) + "|",
                    "B|" + "-".repeat(13) + "|",
                ),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 20),
        )
    }

    @Test
    fun `a row is never cut through a surrogate pair`() {
        val staff = "|" + "-".repeat(8)

        assertEquals(
            listOf(
                listOf("x" + "\uD834\uDD1E".repeat(4), "e$staff", "B$staff"),
                listOf("  " + "\uD834\uDD1E".repeat(2), "e$staff", "B$staff"),
                listOf("e|--|", "B|--|"),
            ),
            ChordProTabWrapper.wrap(clefs, maxColumns = 10),
        )
    }

    @Test
    fun `a row is never cut between a letter and its accent`() {
        val rows = ChordProTabWrapper.wrap(accent, maxColumns = 12)

        assertEquals("a".repeat(11), rows.first().first())
        assertEquals("  e\u0301bbbb", rows[1].first())
    }

    @Test
    fun `nothing is lost or repeated by moving a cut back`() {
        listOf(clefs to 10, accent to 12).forEach { (lines, maxColumns) ->
            val rows = ChordProTabWrapper.wrap(lines, maxColumns)
            // Every row after the first puts the width of the string names in front of the text line.
            val text = rows.mapIndexedNotNull { index, row ->
                row.first().takeUnless { it.startsWith("e|") }?.let { line -> if (index == 0) line else line.drop(2) }
            }.joinToString("")

            assertEquals(lines.first().trimEnd(), text)
            assertTrue(rows.flatten().none { line -> line.hasLoneSurrogate() })
        }
    }

    @Test
    fun `a line of nothing but combining marks does not stall the wrap`() {
        val staff = listOf("e|" + "-".repeat(200) + "|", "B|" + "-".repeat(200) + "|")
        val rows = ChordProTabWrapper.wrap(listOf("\u0301".repeat(200)) + staff, maxColumns = 20)

        assertEquals(ChordProTabWrapper.wrap(staff, maxColumns = 20).size, rows.size)
        assertEquals(200, rows.flatten().sumOf { line -> line.count { it == '\u0301' } })
    }

    @Test
    fun `a quiet cut never falls inside a chord name`() {
        val lines = listOf(
            "                Cmaj7",
            "e|" + "-".repeat(27) + "|",
            "B|---1-----1-----1-----1-----|",
        )
        // The row would hold twenty columns, but the name sits on the last ones, so the cut moves in front of it
        // and the name goes with the note it is written above.
        assertEquals(
            listOf(
                listOf(
                    "e|" + "-".repeat(13),
                    "B|---1-----1---",
                ),
                listOf(
                    "   Cmaj7",
                    "e|" + "-".repeat(14) + "|",
                    "B|--1-----1-----|",
                ),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 20),
        )
    }

    @Test
    fun `a line of chord names is left out of the rows it has nothing to say in`() {
        val lines = listOf(
            "                      G",
            "e|---------|---------|---------|",
            "B|---1-----|---1-----|---0-----|",
        )
        val rows = ChordProTabWrapper.wrap(lines, maxColumns = 25)
        assertEquals(listOf("e|---------|---------|", "B|---1-----|---1-----|"), rows[0])
        assertEquals(listOf("  G", "e|---------|", "B|---0-----|"), rows[1])
    }

    @Test
    fun `a staff without string names or bar lines is still cut and kept whole`() {
        val lines = listOf(
            "--0--2--3--5--7--8--10--12--",
            "--0--2--3--5--7--8--10--12--",
        )
        assertEquals(
            listOf(
                listOf("--0--2--3--5--7--8-", "--0--2--3--5--7--8-"),
                listOf("-10--12--", "-10--12--"),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 19),
        )
    }

    @Test
    fun `string names of different widths line up in the continuation rows`() {
        val lines = listOf(
            "Eb|---------|---------|",
            "Bb|---1-----|---1-----|",
            " G|-----2---|-----0---|",
        )
        assertEquals(
            listOf("Eb|---------|", "Bb|---1-----|", " G|-----0---|"),
            ChordProTabWrapper.wrap(lines, maxColumns = 14)[1],
        )
    }

    @Test
    fun `a repeat sign stays with the bar it belongs to`() {
        val lines = listOf(
            "e|:--------:|:--------:|",
            "B|:--1-----:|:--1-----:|",
        )
        assertEquals(
            listOf(
                listOf("e|:--------:|", "B|:--1-----:|"),
                listOf("e|:--------:|", "B|:--1-----:|"),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 15),
        )
    }

    @Test
    fun `a string written shorter than the others does not hide the bars of the rest`() {
        val lines = listOf(
            "e|---------|---------|",
            "B|---1-----|---1--",
        )
        assertEquals(
            listOf(
                listOf("e|---------|", "B|---1-----|"),
                listOf("e|---------|", "B|---1--"),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 12),
        )
    }

    @Test
    fun `a row is never narrower than a staff can be read at`() {
        val rows = ChordProTabWrapper.wrap(riff, maxColumns = 3)
        assertTrue(rows.all { row -> row.all { it.length <= 10 } })
        assertTrue(rows.size > 2)
    }

    @Test
    fun `systems stacked without a blank line are wrapped one after the other`() {
        val lines = listOf("e|---0---|---3---|", "B|---1---|---0---|", "e|---5---|---7---|", "B|---5---|---8---|")
        assertEquals(
            listOf(
                listOf("e|---0---|", "B|---1---|"),
                listOf("e|---3---|", "B|---0---|"),
                listOf("e|---5---|", "B|---5---|"),
                listOf("e|---7---|", "B|---8---|"),
            ),
            ChordProTabWrapper.wrap(lines, maxColumns = 12),
        )
    }

    @Test
    fun `chord names above a system travel with that system`() {
        assertEquals(
            listOf(
                listOf("   C", "e|---0---|", "B|---1---|"),
                listOf("   G", "e|---3---|", "B|---0---|"),
                listOf("   Am", "e|---0---|", "B|---1---|"),
                listOf("   F", "e|---1---|", "B|---1---|"),
            ),
            ChordProTabWrapper.wrap(twoSystems, maxColumns = 12),
        )
    }

    @Test
    fun `a run of several systems that fits is still one row`() {
        assertEquals(listOf(twoSystems), ChordProTabWrapper.wrap(twoSystems, maxColumns = 40))
    }

    @Test
    fun `a string name that recurs inside a system does not start a new one`() {
        listOf("EBGDAE", "DAGDAD").forEach { names ->
            val system = names.map { "$it|---0---|---3---|" }

            val rows = ChordProTabWrapper.wrap(system, maxColumns = 12)
            assertEquals(2, rows.size, names)
            assertTrue(rows.all { it.size == 6 }, names)
            assertEquals(4, ChordProTabWrapper.wrap(system + system, maxColumns = 12).size, names)
        }
    }

    @Test
    fun `staff lines without string names are one system until a non-staff line separates them`() {
        val rows = ChordProTabWrapper.wrap(List(3) { "--0--2--3--5--7--8--10--12--" }, maxColumns = 19)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.size == 3 })
    }

    @Test
    fun `a run that would wrap into more lines than can be drawn is returned whole`() {
        val unnamed = List(12_500) { "---" } + "-".repeat(50_000)
        assertEquals(listOf(unnamed), ChordProTabWrapper.wrap(unnamed, maxColumns = 40))

        val named = List(283) { "${'A' + it % 26}${'a' + it / 26}|---" } + ("e|" + "-".repeat(1414))
        assertEquals(listOf(named), ChordProTabWrapper.wrap(named, maxColumns = 40))
    }

    @Test
    fun `every copy of a repeated string name starts a system`() {
        val lines = List(16_000) { "e|---" } + ("e|" + "-".repeat(100_000))
        assertTrue(ChordProTabWrapper.wrap(lines, maxColumns = 40).sumOf { it.size } < 20_000)
    }

    private val twoSystems = listOf(
        "   C       G",
        "e|---0---|---3---|",
        "B|---1---|---0---|",
        "   Am      F",
        "e|---0---|---1---|",
        "B|---1---|---1---|",
    )

    /** A line above the staff made of `x` and six G clefs, each of which is a surrogate pair. */
    private val clefs = listOf(
        "x" + "\uD834\uDD1E".repeat(6),
        "e|" + "-".repeat(18) + "|",
        "B|" + "-".repeat(18) + "|",
    )

    /** A line above the staff with an `e` accented by a combining mark where the first row wants to end. */
    private val accent = listOf(
        "a".repeat(11) + "e\u0301bbbb",
        "e|" + "-".repeat(18) + "|",
        "B|" + "-".repeat(18) + "|",
    )

    private fun String.hasLoneSurrogate() = indices.any { index ->
        val character = this[index]
        (character.isHighSurrogate() && getOrNull(index + 1)?.isLowSurrogate() != true) ||
                (character.isLowSurrogate() && getOrNull(index - 1)?.isHighSurrogate() != true)
    }
}
