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

import com.pandulapeter.campfire.presentation.ui.components.appBar.SearchableTopAppBar
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.songs_clear
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource

/**
 * A field narrowing the list it stands over, which never takes the focus on its own: both assignment sheets and the
 * Chords tab open with their lists visible, and tapping the field brings up the keyboard.
 *
 * It is drawn as the list screens' search is (see [SearchableTopAppBar]): the same tonal pill, height, text, and compact
 * clear button, so that every search in the app reads as one control. Unlike the bar's, it carries the search mark
 * itself, since no search action stands next to it to say what it is. The whole pill is the text field, so a tap
 * anywhere on it places the caret, not only one on the text.
 *
 * @param focusRequests Ctrl / Cmd + F's requests for the caret, each answered with the query selected, so that typing
 *   replaces it the way it does in the find bar of any other application.
 */
@Composable
internal fun SearchField(
    modifier: Modifier = Modifier,
    query: String,
    placeholder: String,
    focusRequests: Flow<Unit>? = null,
    onQueryChange: (String) -> Unit,
) = Surface(
    modifier = modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        .height(FIELD_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    // The query is the caller's, and the selection the field's own, which a focus request selects the query with: a
    // query changed from outside, cleared or restored, puts the caret at its end.
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val shownValue = if (fieldValue.text == query) fieldValue else TextFieldValue(query, TextRange(query.length))
    val currentQuery by rememberUpdatedState(query)
    LaunchedEffect(focusRequests) {
        focusRequests?.collect {
            fieldValue = TextFieldValue(currentQuery, TextRange(0, currentQuery.length))
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    BasicTextField(
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
        value = shownValue,
        onValueChange = {
            val text = it.text.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)
            fieldValue = if (text == it.text) it else TextFieldValue(text, TextRange(text.length))
            onQueryChange(text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        // Autocorrect is off for the reason it is off in the list screens' search, see SearchableTopAppBar.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.padding(end = 8.dp).size(SEARCH_ICON_SIZE),
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                ClearButton(
                    isVisible = query.isNotEmpty(),
                    onClear = { onQueryChange("") },
                )
            }
        },
    )
}

/** The bar search's button that empties the field, offered only once there is something to clear. */
@Composable
private fun ClearButton(
    isVisible: Boolean,
    onClear: () -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn() + scaleIn(),
    exit = fadeOut() + scaleOut(),
) {
    // The pill is shorter than a touch target, so the button is laid out at its own size as the bar's is. It takes no
    // focus, so that Tab leaves the field for what follows it rather than stopping at the button.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        // The text field would otherwise show the text cursor over the button on desktop.
        IconButton(
            modifier = Modifier
                .size(CLEAR_BUTTON_SIZE)
                .pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
                .focusProperties { canFocus = false },
            onClick = onClear,
        ) {
            Icon(
                modifier = Modifier.size(CLEAR_ICON_SIZE),
                painter = painterResource(Res.drawable.ic_clear),
                contentDescription = stringResource(Res.string.songs_clear),
            )
        }
    }
}

private val FIELD_HEIGHT = 40.dp
private val SEARCH_ICON_SIZE = 20.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
