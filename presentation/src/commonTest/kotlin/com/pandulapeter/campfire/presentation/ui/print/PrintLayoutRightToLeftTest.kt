/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PrintLayoutRightToLeftTest {
    private val labels = PrintLabels(
        "Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing",
        DefaultSectionLabels(
            verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro",
            preChorus = "Pre-chorus", solo = "Solo", outro = "Outro", abc = "ABC notation", lilyPond = "LilyPond", svg = "SVG",
        ),
    )
    private fun measure(text: String, style: PrintStyle) = text.count { it != '​' } * style.size * 0.6f
    private val settings = PrintSettings()
    private val margin = settings.marginMm * 72f / 25.4f

    private suspend fun layout(blocks: List<ChordProBlock>, settings: PrintSettings = this.settings, chords: List<PrintChord> = emptyList()) =
        layoutPrintDocument(
            PrintSource("שיר", songs = listOf(PrintSong("song.cho", "שיר", "אמן", song = ChordProSong(ChordProMetadata(key = "Am"), blocks), chords = chords))),
            settings,
            labels,
            ::measure,
        )

    private fun verse(vararg lines: ChordProLine) = ChordProBlock.Section(SectionType.Verse, "Verse", lines.toList())
    private fun chord(position: Int, name: String) = ChordProLine.Lyrics.Chord(position, name, false)
    private fun PrintText.end() = x + measure(text, style)

    @Test fun `a right-to-left song's lyrics end at the column's right edge, each chord ending where its letter starts`() = runTest {
        val document = layout(listOf(verse(ChordProLine.Lyrics("שלום עולם", listOf(chord(0, "Am"), chord(5, "G"))))))
        val texts = document.pages.single().texts
        val lyric = texts.single { it.text.startsWith("שלום") }
        val right = document.width - margin
        assertEquals(right, lyric.end(), 0.01f)
        assertEquals(right, texts.single { it.text == "Am" }.end(), 0.01f)
        assertEquals(right - measure("שלום ", lyric.style), texts.single { it.text == "G" }.end(), 0.01f)
        assertTrue(lyric.style.isRightToLeft)
    }

    @Test fun `a right-to-left song's heading and details end at the right edge`() = runTest {
        val document = layout(listOf(verse(ChordProLine.Lyrics("שלום", emptyList()))))
        val right = document.width - margin
        listOf("שיר", "אמן", "Key: Am", "Verse").forEach { text ->
            assertEquals(right, document.pages.single().texts.single { it.text == text }.end(), 0.01f, text)
        }
    }

    @Test fun `a chord that has no room left on its chord line starts the next one at the right edge`() = runTest {
        val narrow = PrintSettings(columns = 4, fontSize = 20)
        val line = ChordProLine.Lyrics("אב", listOf(chord(0, "Cmaj7sus4"), chord(1, "Dmaj7sus4"), chord(2, "Emaj7sus4")))
        val document = layout(listOf(verse(line)), narrow)
        val texts = document.pages.single().texts
        val names = listOf("Cmaj7sus4", "Dmaj7sus4", "Emaj7sus4").map { name -> texts.single { it.text == name } }
        val right = names.maxOf { it.end() }
        val left = right - (document.width - 2 * margin - 18f * 3) / 4
        names.forEach { assertTrue(it.x >= left - 0.01f && it.end() <= right + 0.01f, it.toString()) }
        // Every chord line starts at the right edge, and a chord never sits on the one before it.
        names.groupBy { it.y }.values.forEach { row ->
            assertEquals(right, row.maxOf { it.end() }, 0.01f)
            row.sortedByDescending { it.x }.zipWithNext().forEach { (first, second) -> assertTrue(second.end() <= first.x) }
        }
        assertTrue(names.map { it.y }.distinct().size > 1)
    }

    @Test fun `a right-to-left chorus is indented from the right with its bar on the right`() = runTest {
        val chorus = ChordProBlock.Section(SectionType.Chorus, null, listOf(ChordProLine.Lyrics("שלום עולם", emptyList())))
        val document = layout(listOf(chorus))
        val page = document.pages.single()
        val right = document.width - margin
        assertTrue(page.rules.isNotEmpty())
        page.rules.forEach { bar ->
            assertEquals(right, bar.x + bar.width, 0.01f)
            assertTrue(page.texts.single { it.text == "שלום עולם" }.end() < bar.x)
        }
    }

    @Test fun `a grid moves to the right edge whole, its lines keeping their columns`() = runTest {
        val grid = listOf(
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("Am"), GridToken.Bar("|"), GridToken.Chord("G"), GridToken.Bar("|"))),
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("C"), GridToken.Bar("|"))),
        )
        val document = layout(listOf(verse(ChordProLine.Lyrics("שלום עולם", emptyList()), *grid.toTypedArray())))
        val rows = document.pages.single().texts.filter { it.style.monospace }
        assertEquals(2, rows.size)
        assertEquals(rows.first().x, rows.last().x, 0.01f)
        assertEquals(document.width - margin, rows.maxOf { it.end() }, 0.01f)
    }

    @Test fun `a right-to-left song's chord diagrams are listed from the right`() = runTest {
        fun guitarChord(name: String) = PrintChord(name, geometry = ChordDiagramGeometry.Fretted(6, 1, 4, List(6) { null }, emptyList(), emptyList()))
        val document = layout(listOf(verse(ChordProLine.Lyrics("שלום", listOf(chord(0, "Am"))))), chords = listOf(guitarChord("Am"), guitarChord("G")))
        val diagrams = document.pages.single().diagrams
        assertEquals(2, diagrams.size)
        assertTrue(diagrams[0].x > diagrams[1].x)
        assertTrue(diagrams[0].x + diagrams[0].width <= document.width - margin + 0.01f)
    }

    @Test fun `a right-to-left song fills its columns from the right`() = runTest {
        val lines = (1..300).map { ChordProLine.Lyrics("שורה $it", emptyList()) }
        val document = layout(listOf(verse(*lines.toTypedArray())), PrintSettings(columns = 2))
        val texts = document.pages.first().texts.filter { it.text.startsWith("שורה") }
        assertTrue(texts.first().x > document.width / 2)
        assertTrue(texts.last().end() < document.width / 2)
    }

    @Test fun `a printed chord that overflows its chord line is sent on, and one that fits is placed as on screen`() {
        assertNull(printChordLeft(anchor = 10f, chordWidth = 20f, lineWidth = 100f, previousChordEdge = 5f, isRunRightToLeft = true, isLineRightToLeft = true))
        assertEquals(30f, printChordLeft(anchor = 50f, chordWidth = 20f, lineWidth = 100f, previousChordEdge = 100f, isRunRightToLeft = true, isLineRightToLeft = true))
        assertEquals(40f, printChordLeft(anchor = 40f, chordWidth = 20f, lineWidth = 100f, previousChordEdge = 100f, isRunRightToLeft = false, isLineRightToLeft = true))
        assertNull(printChordLeft(anchor = 90f, chordWidth = 20f, lineWidth = 100f, previousChordEdge = 0f, isRunRightToLeft = false, isLineRightToLeft = false))
        assertEquals(0f, printChordLeft(anchor = 0f, chordWidth = 120f, lineWidth = 100f, previousChordEdge = 0f, isRunRightToLeft = false, isLineRightToLeft = false))
    }
}
