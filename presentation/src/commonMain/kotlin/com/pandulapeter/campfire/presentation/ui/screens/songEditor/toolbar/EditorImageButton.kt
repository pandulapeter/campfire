/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.components.menu.PictureSourceMenu
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut

/**
 * The [EditorToolbar]'s Image button: a [PictureSourceMenu] asking where the pictures come from (Photos where the platform
 * has a photo library apart from its files, Files, From a link), taking no focus for the reason the toolbar's other menus
 * take none.
 *
 * The menu also opens from Ctrl / Cmd + Shift + I, with the button scrolled out of the row's view as often as not, so
 * the button brings itself into view whenever its menu opens.
 */
@Composable
internal fun EditorImageButton(
    modifier: Modifier = Modifier,
    label: String,
    imagePick: EditorImagePick,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(imagePick.menuState.isExpanded) {
        if (imagePick.menuState.isExpanded) bringIntoViewRequester.bringIntoView()
    }
    PictureSourceMenu(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        state = imagePick.menuState,
        isFocusable = false,
        isMultiple = true,
        hasPhotos = imagePick.canPickPhotos,
        button = { open ->
            EditorToolbarButton(
                label = label,
                hasMenu = true,
                shortcut = KeyShortcut.INSERT_IMAGE,
                onClick = open,
            )
        },
        onPick = imagePick.onPick,
        onLink = imagePick.onLink,
    )
}
