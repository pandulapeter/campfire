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

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.deselect_all
import com.pandulapeter.campfire.presentation.resources.ic_deselect
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_select_all
import com.pandulapeter.campfire.presentation.resources.ic_setlists_outline
import com.pandulapeter.campfire.presentation.resources.select_all
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.share_or_export
import com.pandulapeter.campfire.presentation.resources.songs_selection_actions
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/**
 * What the app bar holds behind its divider while songs are being selected, where New stands outside the mode: Select
 * all and Deselect all, which change what is ticked and so always stay in the menu, then what can be done to everything
 * that is ticked, which comes out as buttons where the bar has the room ([isRoomy]). The actions are disabled while
 * nothing is ticked and while a write to the ticked songs is still running.
 *
 * @param isRoomy Whether the list is wide enough to let the actions out of the menu without crowding the pinned header
 *   that stands beside the bar's buttons; a phone keeps them all behind the one button.
 * @param canSelectAll Whether some of the songs listed now are not ticked: Select all ticks what the search or the
 *   filters list, and goes once all of that is.
 */
@Composable
internal fun SongSelectionMenu(
    modifier: Modifier = Modifier,
    isRoomy: Boolean,
    canSelectAll: Boolean,
    hasSelection: Boolean,
    areActionsEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onChooseSetlists: () -> Unit,
    onManageTags: () -> Unit,
    onManageLanguages: () -> Unit,
    onExport: () -> Unit,
) = ActionsMenu(
    modifier = modifier.widthIn(max = if (isRoomy) ROOMY_MENU_WIDTH else COMPACT_MENU_WIDTH),
    contentDescription = stringResource(Res.string.songs_selection_actions),
    items = listOf(
        ActionsMenuItem(
            title = stringResource(Res.string.select_all),
            icon = painterResource(Res.drawable.ic_select_all),
            isAlwaysInMenu = true,
            isVisible = canSelectAll,
            onClick = onSelectAll,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.deselect_all),
            icon = painterResource(Res.drawable.ic_deselect),
            isAlwaysInMenu = true,
            isVisible = hasSelection,
            onClick = onDeselectAll,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.songs_choose_setlists),
            icon = painterResource(Res.drawable.ic_setlists_outline),
            isEnabled = areActionsEnabled,
            isVisible = areSetlistsEnabled,
            onClick = onChooseSetlists,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.song_details_tags_manage),
            icon = painterResource(Res.drawable.ic_label),
            isEnabled = areActionsEnabled,
            onClick = onManageTags,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.song_details_languages_edit),
            icon = painterResource(Res.drawable.ic_language),
            isEnabled = areActionsEnabled,
            onClick = onManageLanguages,
        ),
        ActionsMenuItem(
            title = stringResource(Res.string.share_or_export),
            icon = painterResource(Res.drawable.ic_export),
            isEnabled = areActionsEnabled,
            onClick = onExport,
        ),
    ),
)

/** Room for one button, which is the overflow button alone. */
private val COMPACT_MENU_WIDTH = 48.dp

/** Room for the four actions and the overflow button that Select all and Deselect all keep there. */
private val ROOMY_MENU_WIDTH = 200.dp
