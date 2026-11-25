/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection

/**
 * The chords of [names] on the screen the reader is headed for ([SongStepper.headedSections], [chordsOnScreen]), which
 * the diagram strip marks. While a finger drags the song, which says nothing yet of where it will be read, the last
 * screen's are kept, so the marks do not blink off under it.
 */
@Composable
internal fun rememberChordsOnScreen(stepper: SongStepper?, sections: List<RenderSection>, names: Set<String>): Set<String> {
    val headedSections by remember(stepper) { derivedStateOf { stepper?.headedSections } }
    val last = remember(stepper) { LastSections() }
    headedSections?.let { last.value = it }
    val onScreen = last.value ?: return emptySet()
    return remember(sections, names, onScreen) { chordsOnScreen(sections, onScreen, names) }
}

/** The sections [rememberChordsOnScreen] last knew to be on the screen. Not a state: written and read in one composition. */
private class LastSections(var value: IntRange? = null)
