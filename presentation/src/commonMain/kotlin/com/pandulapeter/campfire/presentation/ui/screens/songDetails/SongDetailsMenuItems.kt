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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/**
 * The About the song sheet, also reached by the title at the top of the song: the menu is where it is found from
 * anywhere in the song, and by somebody who never thought to press a title. Never let out of the menu, as a button would
 * only repeat the title.
 */
@Composable
internal fun aboutSongAction(onClick: () -> Unit) = ActionsMenuItem(
    title = stringResource(Res.string.song_details_song_info),
    icon = painterResource(Res.drawable.ic_info),
    isAlwaysInMenu = true,
    onClick = onClick,
)

/** The song display sheet, never let out of the menu: it is changed now and then, not while a song is played. */
@Composable
internal fun songDisplayAction(onClick: () -> Unit) = ActionsMenuItem(
    title = stringResource(Res.string.settings_preferences),
    icon = painterResource(Res.drawable.ic_tune),
    isAlwaysInMenu = true,
    onClick = onClick,
)
