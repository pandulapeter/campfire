/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.controls.SongToolButton
import com.pandulapeter.campfire.presentation.ui.components.controls.songToolIconSize
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's way to the tuner sheet, next to the transposition and the capo it tunes for: the strings are
 * tuned to what the capo and the key ask of them, so that is where a player looks for it. Drawn as switched on while
 * [isSheetShown], the way the metronome's button is while its panel is up.
 */
@Composable
internal fun TunerButton(
    modifier: Modifier = Modifier,
    isSheetShown: Boolean,
    height: Dp,
    fontScale: Float,
    onClick: () -> Unit,
) = SongToolButton(
    modifier = modifier,
    height = height,
    isSelected = isSheetShown,
    onClick = onClick,
) {
    Icon(
        modifier = Modifier.size(songToolIconSize(fontScale)),
        painter = painterResource(Res.drawable.ic_tuner),
        contentDescription = stringResource(Res.string.tuner),
    )
}
