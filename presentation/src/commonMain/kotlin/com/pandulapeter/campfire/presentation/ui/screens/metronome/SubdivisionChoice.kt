/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice

/**
 * How many clicks each beat is cut into, as a segmented row of the numbers themselves. A number rather than a note
 * value on each segment, since which note a beat is cut into depends on the bar - two clicks to a beat are eighths in
 * 4/4 and sixteenths in 6/8 - and since four of those words do not fit a phone side by side in every language, where
 * four digits always do. The subsection around it says what the numbers count.
 */
@Composable
internal fun SubdivisionChoice(
    selected: Subdivision,
    onSelected: (Subdivision) -> Unit,
) = SegmentedChoice(
    options = Subdivision.entries.map { it to it.clicksPerBeat.toString() },
    selected = selected,
    onSelected = onSelected,
)
