/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.print_next
import com.pandulapeter.campfire.presentation.resources.print_page
import com.pandulapeter.campfire.presentation.resources.print_previous
import org.jetbrains.compose.resources.painterResource

/**
 * The page buttons and the count between them, on a pill floating below the toolbar, faded with the preview whose pages
 * they turn, which crossfades to the files a change of the format shows.
 */
@Composable
internal fun PageButtons(
    modifier: Modifier,
    isVisible: Boolean,
    page: Int,
    pageCount: Int,
    onTurn: (Int) -> Unit,
) = AnimatedVisibility(isVisible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
    PageButtonsPill(page = page, pageCount = pageCount, onTurn = onTurn)
}

@Composable
private fun PageButtonsPill(
    page: Int,
    pageCount: Int,
    onTurn: (Int) -> Unit,
) = Surface(
    modifier = Modifier.height(PAGE_BUTTONS_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation = 6.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(enabled = page > 0, onClick = { onTurn(page - 1) }) {
            Icon(painterResource(Res.drawable.ic_previous), contentDescription = stringResource(Res.string.print_previous))
        }
        Text(stringResource(Res.string.print_page, page + 1, pageCount), style = MaterialTheme.typography.bodySmall)
        IconButton(enabled = page + 1 < pageCount, onClick = { onTurn(page + 1) }) {
            Icon(painterResource(Res.drawable.ic_next), contentDescription = stringResource(Res.string.print_next))
        }
    }
}

/** The height of the floating page buttons. */
private val PAGE_BUTTONS_HEIGHT = 48.dp
