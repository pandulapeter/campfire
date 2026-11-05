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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_change_next_bar
import com.pandulapeter.campfire.presentation.resources.metronome_change_next_beat
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice

/**
 * Where a playing click takes up another song, or a later `{tempo}` or `{time}` of one: once the bar being played has
 * ended, on beat one (what a band moving on together wants), or from the next beat, the count carried on.
 */
@Composable
internal fun ChangeTimingChoice(
    isChangeOnNextBar: Boolean,
    onSelected: (Boolean) -> Unit,
) = SegmentedChoice(
    options = listOf(
        true to stringResource(Res.string.metronome_change_next_bar),
        false to stringResource(Res.string.metronome_change_next_beat),
    ),
    selected = isChangeOnNextBar,
    onSelected = onSelected,
)
