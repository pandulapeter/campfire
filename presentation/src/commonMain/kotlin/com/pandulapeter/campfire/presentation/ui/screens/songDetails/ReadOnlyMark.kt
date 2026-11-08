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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_lock
import com.pandulapeter.campfire.presentation.resources.read_only_mark
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import org.jetbrains.compose.resources.painterResource

/**
 * Says on the song's bar that read only mode is on, and opens the Preferences, where it is switched off: a mode switched
 * on by accident hides every way to change a song, and without a mark the player would only notice the edit actions
 * missing, with nothing on screen saying why. It stands right before the overflow button and reaches into it, as the
 * bar's other buttons do (`overlappingAction`).
 */
@Composable
internal fun ReadOnlyMark(
    onClick: () -> Unit,
) = CampfireIconButton(
    modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
    icon = painterResource(Res.drawable.ic_lock),
    label = stringResource(Res.string.read_only_mark),
    onClick = onClick,
)
