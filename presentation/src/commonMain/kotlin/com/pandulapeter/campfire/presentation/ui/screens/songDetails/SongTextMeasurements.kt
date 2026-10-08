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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.pandulapeter.campfire.chordpro.model.ChordProLine

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

    /** The laid out name of [chord], which is drawn as it is: the colour is given where it is drawn. */
    fun chordLayout(chord: ChordProLine.Lyrics.Chord) = if (chord.isAnnotation) {
        annotationLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), annotationStyle) }
    } else {
        chordLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), chordStyle) }
    }

    /** The width of a piece of lyrics. Only the number is kept, since nothing is ever drawn from this layout. */
    fun fragmentWidth(fragment: String) = fragmentWidths.bounded().getOrPut(fragment) { measureFragment(fragment) }

    private fun measureFragment(fragment: String) = textMeasurer.measure(AnnotatedString(fragment), lyricsStyle).size.width.toFloat()

    /**
     * The editor's preview is one page for as long as the editor is open and sees every fragment that is ever typed,
     * so a map that only grew would grow for the whole session. Starting over costs one measurement of whatever is
     * still on screen, which is what opening the page cost.
     */
    private fun <T> HashMap<String, T>.bounded() = also { if (size >= MAX_MEASURED_TEXTS) clear() }
}

internal const val LINE_HEIGHT_SAMPLE = "X"

private const val MAX_MEASURED_TEXTS = 4096

internal const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
