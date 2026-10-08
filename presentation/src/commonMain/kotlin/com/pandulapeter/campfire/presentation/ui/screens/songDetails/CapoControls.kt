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

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.song_details_capo_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_capo_increase
import com.pandulapeter.campfire.presentation.resources.song_details_capo_reset
import com.pandulapeter.campfire.presentation.ui.components.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.Stepper
import com.pandulapeter.campfire.presentation.ui.playing.EffectiveCapo
import org.jetbrains.compose.resources.painterResource

/**
 * The capo stepper of a song, the tempo's twin: the fret it is played at where it is opened, highlighted while this
 * setlist (or this device) capos it somewhere other than its file says, and one tap on the value puts it back to the
 * file's. Nothing in the song moves with it — a chord chart is written as it is fretted — so this is the number the
 * player reads off the page and not a transposition by another name.
 */
@Composable
internal fun CapoControls(
    modifier: Modifier = Modifier,
    capo: EffectiveCapo,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    onStep: (frets: Int) -> Unit,
    onReset: () -> Unit,
) = Stepper(
    modifier = modifier,
    fontScale = fontScale,
    height = height,
    value = capo.fret.toString(),
    isDefault = capo.isDefault,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_details_capo_decrease),
    canDecrease = capo.fret > Song.CAPO_RANGE.first,
    onDecrease = { onStep(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_details_capo_increase),
    canIncrease = capo.fret < Song.CAPO_RANGE.last,
    onIncrease = { onStep(1) },
    resetLabel = stringResource(Res.string.song_details_capo_reset),
    onReset = onReset,
)
