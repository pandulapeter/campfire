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

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_sort
import org.jetbrains.compose.resources.painterResource

/**
 * The app bar action of both list screens that picks the order of the list, a popup of radio rows rather than a
 * section of a sheet: the order is one question with a handful of answers, and a choice is its whole answer, so the
 * menu closes with it.
 *
 * @param options Every order the list can come in, with its label, in the order they are offered.
 */
@Composable
internal fun <T> SortMenu(
    contentDescription: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
) = OverflowMenu(
    button = { open ->
        IconButton(onClick = open) {
            Icon(
                painter = painterResource(Res.drawable.ic_sort),
                contentDescription = contentDescription,
            )
        }
    },
) { select ->
    options.forEach { (option, label) ->
        RadioListItem(
            modifier = Modifier.width(SORT_MENU_WIDTH),
            title = label,
            isSelected = option == selected,
            onSelected = { select { onSelected(option) } },
        )
    }
}

private val SORT_MENU_WIDTH = 220.dp
