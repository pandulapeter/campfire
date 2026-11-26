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
import com.pandulapeter.campfire.presentation.ui.songView.SongPiece
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup

/**
 * The indices of [groups] whose scale chips the dock draws as they are, the rest faded back as the diagram strip fades
 * the chords off the screen ([chordsOnScreen]): a group any of whose sections has a line in [onScreen], and a group whose
 * sections were not given, which nothing can place. The home group is one like the others: its sections are the ones
 * played over the home scale, so a screen every section of which another group holds (a modulation, an "Over E7"
 * moment's part) fades it back too. A screen none of whose sections any group holds (a comment, a picture) says nothing
 * of where the reader is, so nothing is faded then.
 */
internal fun scaleGroupsOnScreen(groups: List<SongScaleGroup>, onScreen: List<SongPiece>): Set<Int> {
    val sections = onScreen.mapTo(mutableSetOf()) { it.section }
    val shown = groups.indices.filterTo(mutableSetOf()) { index ->
        val group = groups[index]
        group.sections.isEmpty() || group.sections.any { it in sections }
    }
    return shown.ifEmpty { groups.indices.toSet() }
}

/**
 * [scaleGroupsOnScreen] for the screen [stepper] shows ([SongStepper.shownPieces]), or null before the song is laid out,
 * when nothing is faded. Derived, so that what reads it is recomposed only when the lines on the screen change, never
 * on every frame of a scroll.
 */
@Composable
internal fun rememberScaleGroupsOnScreen(stepper: SongStepper?, groups: List<SongScaleGroup>): Set<Int>? {
    val onScreen by remember(stepper) { derivedStateOf { stepper?.shownPieces } }
    return remember(groups, onScreen) { onScreen?.let { scaleGroupsOnScreen(groups, it) } }
}
