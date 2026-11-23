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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.presentation.ui.components.appBar.FloatingPill
import com.pandulapeter.campfire.presentation.ui.components.appBar.PillIconButton
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeElement
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.rememberSharedTabChrome
import org.jetbrains.compose.resources.painterResource

/**
 * The way into the Preferences from a screen that has no overflow menu to hold it (the Chords and the Scales tabs), with
 * the icon and the name the song details menu's entry has, so that it reads as the same thing. A pill of one button that
 * floats where the list screens' search pill ends, below the status bar, so that it stays put while the page scrolls
 * under it; the page keeps its room at the end of its instrument line ([PreferencesPillRoom]).
 */
@Composable
internal fun PreferencesPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = FloatingPill(
    modifier = modifier,
    // It stands on the New button's spot, so a tab swap morphs one into the other.
    sharedTabChrome = rememberSharedTabChrome(TabChromeElement.NEW_BUTTON),
) {
    val label = stringResource(Res.string.settings_preferences)
    PillIconButton(
        modifier = Modifier.overlappingAction(),
        tooltip = label,
        onClick = onClick,
    ) {
        Icon(painter = painterResource(Res.drawable.ic_tune), contentDescription = label)
    }
}

/** What a page's instrument line keeps free under the [PreferencesPill] floating over its end, as wide as the pill. */
@Composable
internal fun PreferencesPillRoom() = Spacer(modifier = Modifier.size(NEW_ITEM_BUTTON_SIZE))
