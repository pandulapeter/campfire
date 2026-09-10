/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.localization.stringResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchField(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    SearchBarDefaults.InputField(
        modifier = modifier,
        query = query,
        onQueryChange = { onQueryChanged(it.replace("\n", "")) },
        onSearch = { keyboardController?.hide() },
        expanded = false,
        onExpandedChange = {},
        placeholder = { Text(stringResource(Res.string.songs_search)) },
        leadingIcon = {
            // The web build fetches the drawable over the network, so without an explicit size the icon has no size
            // at all on the first frame and the input field keeps placing it as if it still had none.
            Icon(
                modifier = Modifier.size(SEARCH_ICON_SIZE),
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                // The text field would otherwise show the text cursor over the button on desktop.
                IconButton(
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                    onClick = { onQueryChanged("") }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.songs_clear)
                    )
                }
            }
        }
    )
}

private val SEARCH_ICON_SIZE = 24.dp
