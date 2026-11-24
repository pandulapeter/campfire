/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_TOP_INSET
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesContext
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesPage
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesPill
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout

/**
 * The page of the Chords or the Scales tab, as wide as [layout] lets it be from the start of the screen, with the
 * [PreferencesPill] floating at the end of the screen below the status bar, on the spot of the list screens' New button,
 * however much narrower the page is: the page scrolls under it, and its instrument line keeps the pill's room where the
 * button used to stand.
 */
@Composable
internal fun ChordToolPage(
    modifier: Modifier = Modifier,
    layout: PageWidthLayout,
    contentPadding: PaddingValues,
    showDialog: (DialogType) -> Unit,
    page: @Composable () -> Unit,
) = Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopStart,
) {
    Box(modifier = Modifier.widthIn(max = layout.animatedPageMaxWidth(sections = 2)).fillMaxSize()) {
        page()
    }
    PreferencesPill(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(contentPadding.only(top = true, end = true, extraTop = NEW_ITEM_BUTTON_TOP_INSET, extraEnd = NEW_ITEM_BUTTON_END_INSET)),
        onClick = { showDialog(DialogType.Preferences(PreferencesContext.CHORD_TOOLS, PreferencesPage.CHORDS)) },
    )
}
