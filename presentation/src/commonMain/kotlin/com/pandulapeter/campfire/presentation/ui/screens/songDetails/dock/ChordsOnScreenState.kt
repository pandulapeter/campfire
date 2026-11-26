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
 * The chords of [names] on the screen ([SongStepper.shownPieces], [chordsOnScreen]), which the diagram strip marks:
 * the screen a step or a fling comes to rest on, else the one shown now, following a song dragged by hand or scrolling
 * by itself. Derived, so that what reads it is recomposed only when the lines on the screen change, never
 * on every frame of a scroll.
 */
@Composable
internal fun rememberChordsOnScreen(stepper: SongStepper?, sections: List<RenderSection>, names: Set<String>): Set<String> {
    val onScreen by remember(stepper) { derivedStateOf { stepper?.shownPieces } }
    return remember(sections, names, onScreen) { onScreen?.let { chordsOnScreen(sections, it, names) }.orEmpty() }
}
