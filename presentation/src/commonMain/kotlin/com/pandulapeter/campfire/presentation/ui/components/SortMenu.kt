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

import androidx.compose.foundation.layout.PaddingValues
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
 * The action of both list screens and both picker sheets that picks the order of the list, a popup of radio rows rather than a
 * section of a sheet: the order is one question with a handful of answers, and a choice is its whole answer, so the
 * menu closes with it. The menu is headed by [title], since the labels of the options alone ("Title", "Date") read like
 * headings of their own rather than like answers to a question nobody asked.
 *
 * @param title What the menu decides, also the sort action's content description.
 * @param options Every order the list can come in, with its label, in the order they are offered.
 */
@Composable
internal fun <T> SortMenu(
    modifier: Modifier = Modifier,
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) = OverflowMenu(
    modifier = modifier,
    button = { open ->
        IconButton(onClick = open, enabled = isEnabled) {
            Icon(
                painter = painterResource(Res.drawable.ic_sort),
                contentDescription = title,
            )
        }
    },
) { select ->
    SettingsSectionTitle(
        modifier = Modifier.width(SORT_MENU_WIDTH),
        text = title,
        contentPadding = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp, bottom = 4.dp),
    )
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
