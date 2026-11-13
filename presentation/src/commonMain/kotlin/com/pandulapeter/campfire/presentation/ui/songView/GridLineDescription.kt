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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.pluralTemplate
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.withTexts
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_grid_beats
import com.pandulapeter.campfire.presentation.resources.song_details_grid_held
import com.pandulapeter.campfire.presentation.resources.song_details_grid_repeat_bar
import com.pandulapeter.campfire.presentation.resources.song_details_grid_repeat_two_bars
import com.pandulapeter.campfire.presentation.ui.songLayout.GridBarLine
import com.pandulapeter.campfire.presentation.ui.songLayout.GridSegment

/** The words a drawn grid line is read aloud in, the counted beats by [beats]. */
internal class GridLineWords(
    val held: String,
    val repeatBar: String,
    val repeatTwoBars: String,
    val beats: (Int) -> String,
)

@Composable
internal fun rememberGridLineWords(): GridLineWords {
    val language = currentLanguage.value
    val held = stringResource(Res.string.song_details_grid_held)
    val repeatBar = stringResource(Res.string.song_details_grid_repeat_bar)
    val repeatTwoBars = stringResource(Res.string.song_details_grid_repeat_two_bars)
    return remember(language, held, repeatBar, repeatTwoBars) {
        GridLineWords(held, repeatBar, repeatTwoBars) { count ->
            pluralTemplate(Res.plurals.song_details_grid_beats, count, language).withTexts(count.toString())
        }
    }
}

/**
 * A grid line as a screen reader reads it: chords and beats rather than bar characters, a bar being "C, 4 beats" or
 * "Dm, 2 beats, Am, 2 beats", a held stretch "held", and a repeat sign "repeat bar", with the margin label first and the
 * trailing text last.
 */
internal fun gridLineDescription(line: GridBarLine, words: GridLineWords) = buildList {
    line.margin?.let(::add)
    line.bars.forEach { bar ->
        val segments = bar.segments.joinToString(", ") { segment ->
            when (segment) {
                is GridSegment.Chord -> "${segment.names.joinToString(" ")}, ${words.beats(segment.beats)}"
                is GridSegment.Hold -> "${words.held}, ${words.beats(segment.beats)}"
                is GridSegment.Repeat -> if (segment.bars > 1) words.repeatTwoBars else words.repeatBar
            }
        }
        if (segments.isNotEmpty()) add(segments)
    }
    line.trailing?.let(::add)
}.joinToString("; ")
