/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.controls.CONTROLS_PADDING
import com.pandulapeter.campfire.presentation.ui.components.controls.SORTING_TOGGLE_ICON_SIZE
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.songs_clear
import org.jetbrains.compose.resources.painterResource

/**
 * The search of one filter group, between its title and its chips: a pill only as tall as the title row, so that a
 * group offers it at the cost of a line of chips rather than of a Material field's height, which a library with a
 * handful of tags would pay for nothing. It has a line of its own rather than a place in the title row, where it would
 * be left a phone's last hundred dp and change width whenever the clear action came or went; above the chips, it
 * stays where it is while what it narrows them to changes under it. It never takes the caret by itself, since it is always there and a keyboard coming up
 * over the chips would hide what the filters are for.
 */
@Composable
internal fun FilterSearchField(
    modifier: Modifier = Modifier,
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) = Surface(
    modifier = modifier
        .fillMaxWidth()
        .padding(start = CONTROLS_PADDING, end = CONTROLS_PADDING, top = FIELD_TOP_PADDING, bottom = FIELD_BOTTOM_PADDING)
        .height(SECTION_ACTION_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier.fillMaxSize().padding(start = FIELD_START_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(end = 8.dp).size(SORTING_TOGGLE_ICON_SIZE),
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            modifier = Modifier.weight(1f).padding(end = FIELD_START_PADDING),
            value = query,
            onValueChange = { onQueryChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // Tags are words the library made up, and language names are not in any dictionary of the keyboard's.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            // The pill is as tall as the title row, and a reserved touch target would make it taller. The button takes
            // no focus, so that Tab goes from the field to the chips rather than stopping at it.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(
                    modifier = Modifier
                        .size(SECTION_ACTION_HEIGHT)
                        .focusProperties { canFocus = false },
                    onClick = { onQueryChange("") },
                ) {
                    Icon(
                        modifier = Modifier.size(SORTING_TOGGLE_ICON_SIZE),
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.songs_clear),
                    )
                }
            }
        }
    }
}

private val FIELD_TOP_PADDING = 4.dp
private val FIELD_BOTTOM_PADDING = 8.dp
private val FIELD_START_PADDING = 12.dp
