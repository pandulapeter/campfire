/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songLayout.GridCell
import com.pandulapeter.campfire.presentation.ui.songLayout.PADDING
import com.pandulapeter.campfire.presentation.ui.songLayout.alignedGridBars

/**
 * What the chorded lines of one page have had measured, shared between them because they keep asking for the
 * same things: a song has a handful of chord names and hundreds of lines carrying them, a repeated verse or a
 * recalled chorus is the same fragments again, and the height of a line and the width of the padding are the
 * same for all of them.
 *
 * It lives for as long as the measurer and the three styles do - which is everything a width depends on: the
 * styles carry the text size, the measurer is rebuilt with the density, the layout direction and the font
 * resolver - and deliberately not for as long as the song does. A transposition renames the chords and leaves
 * the lyrics alone, so the fragment widths it measured before are the ones it needs after.
 *
 * The runs of tablature and grids are kept here too: a run is drawn as several chunks the section may be cut
 * between, and every chunk of a run shares its one set of rows (and its one alignment of bars) rather than wrapping and
 * measuring the whole run again for itself.
 *
 * None of this is state: it is filled in by whoever asks first, and the answers never change.
 */
internal class SongTextMeasurements(
    val textMeasurer: TextMeasurer,
    private val lyricsStyle: TextStyle,
    private val chordStyle: TextStyle,
    private val annotationStyle: TextStyle,
) {

    /** The height of one line of lyrics, which a chorded line is taller than by the height of its chords. */
    val lyricsLineHeight by lazy(LazyThreadSafetyMode.NONE) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
    }

    /** The height of the text of one line of lyrics, without the leading its style's line height adds around it. */
    val lyricsTextHeight by lazy(LazyThreadSafetyMode.NONE) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle.copy(lineHeight = TextUnit.Unspecified)).size.height
    }

    /** The width of one [PADDING] character, which the lyrics under a chord wider than they are get filled up with. */
    val paddingWidth by lazy(LazyThreadSafetyMode.NONE) { measureFragment(PADDING.toString()) }

    private val chordLayouts = HashMap<String, TextLayoutResult>()
    private val annotationLayouts = HashMap<String, TextLayoutResult>()
    private val fragmentWidths = HashMap<String, Float>()
    private val tabRows = HashMap<TabRunKey, TabRows>()
    private val gridBars = HashMap<List<ChordProLine>, List<List<List<GridCell>>>>()
    private val gridWidths = HashMap<Pair<List<List<List<GridCell>>>, RunStyles>, Int>()

    /** The laid out name of [chord], which is drawn as it is: the colour is given where it is drawn. */
    fun chordLayout(chord: ChordProLine.Lyrics.Chord) = if (chord.isAnnotation) {
        annotationLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), annotationStyle) }
    } else {
        chordLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), chordStyle) }
    }

    /** The width of a piece of lyrics. Only the number is kept, since nothing is ever drawn from this layout. */
    fun fragmentWidth(fragment: String) = fragmentWidths.bounded().getOrPut(fragment) { measureFragment(fragment) }

    /**
     * The rows of the tab run of [lines] in [styles], one for every chunk the run is drawn as, wrapped as preformatted
     * text whatever its lines look like where [isPreformatted] (see [TabRows]).
     */
    fun tabRows(lines: List<String>, styles: RunStyles, isPreformatted: Boolean = false) =
        tabRows.boundedTo(MAX_TAB_RUNS).getOrPut(TabRunKey(lines, styles, isPreformatted)) { TabRows(lines, styles, textMeasurer, isPreformatted) }

    /** The bars of the grid run [run], aligned once for every chunk the run is drawn as. */
    fun gridBars(run: List<ChordProLine>) = gridBars.boundedTo(MAX_TAB_RUNS).getOrPut(run) {
        run.filterIsInstance<ChordProLine.Grid>().map { it.tokens }.alignedGridBars { it.displayText() }
    }

    /**
     * How wide the widest line of the aligned grid [bars] is unwrapped in [styles], which is what a right-to-left song
     * moves the run to its start edge by ([atLineEnd]); every chunk of the run asks, so that its lines stay in columns.
     */
    fun gridWidth(bars: List<List<List<GridCell>>>, styles: RunStyles) = gridWidths.boundedTo(MAX_TAB_RUNS).getOrPut(bars to styles) {
        bars.maxOfOrNull { line ->
            line.sumOf { bar -> textMeasurer.measure(gridBarText(bar, styles.monospaceChord), styles.monospaceText).size.width }
        } ?: 0
    }

    private fun measureFragment(fragment: String) = textMeasurer.measure(AnnotatedString(fragment), lyricsStyle).size.width.toFloat()

    /**
     * The editor's preview is one page for as long as the editor is open and sees every fragment that is ever typed,
     * so a map that only grew would grow for the whole session. Starting over costs one measurement of whatever is
     * still on screen, which is what opening the page cost.
     */
    private fun <T> HashMap<String, T>.bounded() = boundedTo(MAX_MEASURED_TEXTS)

    private fun <K, V> HashMap<K, V>.boundedTo(max: Int) = also { if (size >= max) clear() }

    /** The styles are part of the key, since the ones a run is drawn in are none of the three this is keyed by. */
    private data class TabRunKey(val lines: List<String>, val styles: RunStyles, val isPreformatted: Boolean)
}

internal const val LINE_HEIGHT_SAMPLE = "X"

private const val MAX_MEASURED_TEXTS = 4096

/** A song has a handful of runs; the bound is for the editor's preview, which makes a new run on every keystroke typed into one. */
internal const val MAX_TAB_RUNS = 64
