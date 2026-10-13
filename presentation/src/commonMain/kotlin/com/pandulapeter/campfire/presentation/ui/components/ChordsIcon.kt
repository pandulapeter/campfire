/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_chords
import com.pandulapeter.campfire.presentation.resources.ic_piano
import org.jetbrains.compose.resources.painterResource

/**
 * The mark of the chord diagrams and the Chords tab: a fretboard with fingers on it, or a keyboard where the player's
 * instrument is one, crossfading between the two as the instrument is picked.
 */
@Composable
internal fun ChordsIcon(
    modifier: Modifier = Modifier,
    isKeyboard: Boolean,
) = Crossfade(
    modifier = modifier,
    targetState = isKeyboard,
) { isKeyboard ->
    Icon(
        painter = painterResource(if (isKeyboard) Res.drawable.ic_piano else Res.drawable.ic_chords),
        contentDescription = null,
    )
}
