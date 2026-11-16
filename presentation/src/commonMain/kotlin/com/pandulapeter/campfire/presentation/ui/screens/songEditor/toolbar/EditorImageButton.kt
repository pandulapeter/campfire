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
import com.pandulapeter.campfire.presentation.ui.platform.PictureSource

/**
 * The [EditorToolbar]'s Image button: where the platform has a photo library apart from its files, a
 * [PictureSourceMenu] asking which to pick from, taking no focus for the reason the toolbar's other menus take none;
 * elsewhere a plain button picking files.
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
    if (imagePick.canPickPhotos) {
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(imagePick.menuState.isExpanded) {
            if (imagePick.menuState.isExpanded) bringIntoViewRequester.bringIntoView()
        }
        PictureSourceMenu(
            modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
            state = imagePick.menuState,
            isFocusable = false,
            button = { open ->
                EditorToolbarButton(
                    label = label,
                    hasMenu = true,
                    shortcut = KeyShortcut.INSERT_IMAGE,
                    onClick = open,
                )
            },
            onPick = imagePick.onPick,
        )
    } else {
        EditorToolbarButton(
            modifier = modifier,
            label = label,
            shortcut = KeyShortcut.INSERT_IMAGE,
            onClick = { imagePick.onPick(PictureSource.FILES) },
        )
    }
}
