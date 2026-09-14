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
}
