/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_dice
import com.pandulapeter.campfire.presentation.resources.songs_random_picks_refresh
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/**
 * The one action of the random picks' [com.pandulapeter.campfire.presentation.ui.components.list.SectionHeader]: a new
 * draw, at once, whatever the hour. An [ActionsMenu] of one button rather than a button of its own, so that it stands on
 * the header's pill exactly where a setlist's actions do.
 *
 * @param isDecorative Draws the icon alone, for the header's copy that is being pushed away.
 */
@Composable
internal fun RandomPicksRefreshButton(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    isDecorative: Boolean = false,
    onRefresh: () -> Unit,
) {
    val title = stringResource(Res.string.songs_random_picks_refresh)
    ActionsMenu(
        modifier = modifier,
        buttonModifier = buttonModifier,
        contentDescription = title,
        isOnPill = true,
        isDecorative = isDecorative,
        items = listOf(ActionsMenuItem(title = title, icon = painterResource(Res.drawable.ic_dice), onClick = onRefresh)),
    )
}
