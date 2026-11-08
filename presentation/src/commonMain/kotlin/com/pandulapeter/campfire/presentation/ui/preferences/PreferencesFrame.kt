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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.settingsPage.MAX_COLUMN_WIDTH

/**
 * The Preferences' [content] with the buttons that pick its page ([PreferencesPageButtons]) pinned under it, in Settings
 * and in the sheet alike: the content scrolls by itself above them, fading out at its bottom edge as it would under a
 * bar, so that another page is picked from wherever this one has been scrolled to, and the buttons sit where the thumb
 * is. A single page has no buttons, and the content reaches the bottom.
 *
 * @param buttonsPadding Around the buttons: the window's inset under them, and the sides the content keeps.
 */
@Composable
internal fun PreferencesFrame(
    modifier: Modifier = Modifier,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    isKeyboard: Boolean,
    buttonsPadding: PaddingValues,
    onPageSelected: (PreferencesPage) -> Unit,
    content: @Composable () -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    Box(modifier = Modifier.weight(1f)) {
        content()
    }
    AnimatedVisibility(
        visible = pages.size > 1,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PreferencesPageButtons(
            // As wide as a settings page's column at most, whose rows they pick.
            modifier = Modifier.padding(buttonsPadding).widthIn(max = MAX_COLUMN_WIDTH),
            pages = pages,
            selectedPage = shownPreferencesPage(pages, selectedPage),
            isKeyboard = isKeyboard,
            onPageSelected = onPageSelected,
        )
    }
}
