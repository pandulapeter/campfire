/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import org.jetbrains.compose.resources.painterResource

/**
 * The way into the Preferences from a screen that has no overflow menu to hold it (the Chords and the Scales tabs), with
 * the icon and the name the song details menu's entry has, so that it reads as the same thing.
 */
@Composable
internal fun PreferencesButton(
    onClick: () -> Unit,
) = CampfireIconButton(
    icon = painterResource(Res.drawable.ic_tune),
    label = stringResource(Res.string.settings_preferences),
    onClick = onClick,
)
