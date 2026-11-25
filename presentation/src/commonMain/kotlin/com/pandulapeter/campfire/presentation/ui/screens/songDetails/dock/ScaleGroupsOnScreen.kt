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
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup

/**
 * The indices of [groups] whose scale chips the dock draws as they are, the rest faded back as the diagram strip fades
 * the chords off the screen ([chordsOnScreen]): a group any of whose sections is in [onScreen], the home group, which
 * is what the whole song is played over, and a group whose sections were not given, which nothing can place.
 */
internal fun scaleGroupsOnScreen(groups: List<SongScaleGroup>, onScreen: IntRange): Set<Int> = groups.indices.filterTo(mutableSetOf()) { index ->
    val group = groups[index]
    group.isHome || group.sections.isEmpty() || group.sections.any { it in onScreen }
}

/**
 * [scaleGroupsOnScreen] for the screen [stepper] shows ([SongStepper.shownSections]), or null while no range is known,
 * when nothing is faded. Derived, so that what reads it is recomposed only when the range of sections on the screen
 * changes, never on every frame of a scroll.
 */
@Composable
internal fun rememberScaleGroupsOnScreen(stepper: SongStepper?, groups: List<SongScaleGroup>): Set<Int>? {
    val onScreen by remember(stepper) { derivedStateOf { stepper?.shownSections } }
    return remember(groups, onScreen) { onScreen?.let { scaleGroupsOnScreen(groups, it) } }
}
