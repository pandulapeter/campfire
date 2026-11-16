/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.menu

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_folder
import com.pandulapeter.campfire.presentation.resources.ic_photo_library
import com.pandulapeter.campfire.presentation.resources.picture_source_files
import com.pandulapeter.campfire.presentation.resources.picture_source_photos
import com.pandulapeter.campfire.presentation.ui.platform.PictureSource
import org.jetbrains.compose.resources.painterResource

/**
 * The popup a button that adds a picture opens where the platform has a photo library apart from its files
 * (`FilePicker.canPickPhotos`): Photos first, since that is where a phone's pictures are, then Files.
 *
 * @param isFocusable See [OverflowMenu]; false also keeps the entries from taking the focus.
 * @param onPick Called with the source picked, once the menu is closed.
 */
@Composable
internal fun PictureSourceMenu(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    isFocusable: Boolean = true,
    button: @Composable (open: () -> Unit) -> Unit,
    onPick: (PictureSource) -> Unit,
) = OverflowMenu(
    modifier = modifier,
    state = state,
    isFocusable = isFocusable,
    button = button,
) { select ->
    PictureSource.entries.forEach { source ->
        DropdownMenuItem(
            modifier = if (isFocusable) Modifier else Modifier.focusProperties { canFocus = false },
            text = {
                Text(
                    stringResource(
                        when (source) {
                            PictureSource.PHOTOS -> Res.string.picture_source_photos
                            PictureSource.FILES -> Res.string.picture_source_files
                        },
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(
                        when (source) {
                            PictureSource.PHOTOS -> Res.drawable.ic_photo_library
                            PictureSource.FILES -> Res.drawable.ic_folder
                        },
                    ),
                    contentDescription = null,
                )
            },
            onClick = { select { onPick(source) } },
        )
    }
}
