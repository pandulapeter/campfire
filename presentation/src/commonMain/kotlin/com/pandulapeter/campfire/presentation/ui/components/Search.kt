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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.songs_clear
import org.jetbrains.compose.resources.painterResource

/**
 * The title slot of a list screen's app bar: the screen's name, which the search field takes the place of while the
 * search is open. The two cross fade past each other rather than being swapped, since both the search opening and
 * the search closing are things the user asked for and has to be able to see happen.
 *
 * The search is an action and a state rather than a field that is always there because the bar of both list screens
 * is otherwise full: the field took the whole title slot, leaving the screen unnamed and the sort, filter and
 * "new" actions crowded against it.
 *
 * The two take turns in one [Box] that is as tall as whichever of them is in it, so it grows and shrinks as they
 * swap — which is why it centers its content. Left to the default the title would be pinned to the top of the box
 * for as long as the taller field shares it, and would drop back into place at the end of every transition.
 *
 * @param title The screen's own name, shown whenever the search is closed.
 * @param placeholder What the field says while it is empty, which also names the search action, see [SearchAction].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SearchableTopAppBarTitle(
    modifier: Modifier = Modifier,
    title: String,
    placeholder: String,
    searchState: SearchState,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    // The one back handler of the search. The desktop has no use for it - its window key handler sees Escape before
    // Compose turns it into a back event, so it closes the search there - but on the other three this is what makes
    // the system's back gesture close the search before it leaves the screen.
    BackHandler(enabled = isOpen) {
        keyboardController?.hide()
        searchState.close()
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            visible = !isOpen,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInHorizontally { -it / TITLE_SLIDE_FRACTION },
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideOutHorizontally { -it / TITLE_SLIDE_FRACTION },
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInHorizontally { it / TITLE_SLIDE_FRACTION },
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideOutHorizontally { it / TITLE_SLIDE_FRACTION },
        ) {
            SearchField(
                modifier = Modifier.fillMaxWidth(),
                searchState = searchState,
                placeholder = placeholder,
            )
        }
    }
}

/**
 * The app bar button that opens the search and closes it again, drawn as the one mark that turns into the other
 * (see [SearchToCloseIcon]). It is the same button throughout: a close button appearing somewhere else while the
 * search icon stayed put would leave the bar with two answers to the same question.
 *
 * @param placeholder What the field it opens says while it is empty, which is also what the button announces itself
 *   as - "Search in songs" is what pressing it does, and the screens have a search of their own to name.
 */
@Composable
internal fun SearchAction(
    searchState: SearchState,
    placeholder: String,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    IconButton(
        onClick = {
            if (isOpen) {
                keyboardController?.hide()
                searchState.close()
            } else {
                searchState.open()
            }
        },
    ) {
        SearchToCloseIcon(
            isClose = isOpen,
            contentDescription = if (isOpen) stringResource(Res.string.close) else placeholder,
        )
    }
}

/**
 * The field itself: a tonal pill holding one line of text and the button that empties it.
 *
 * It is laid out here rather than taken from `SearchBarDefaults.InputField`, which is fixed at the 56dp of a search
 * bar standing on its own — inside a 64dp app bar that leaves four pixels of daylight above and below it, so the
 * field reads as the whole of the bar rather than as something sitting in it, and neither its height nor the
 * padding that decides it can be passed in. What is wanted here is the height of the actions beside it, which also
 * brings the clear button down to the compact size the header pills use.
 *
 * The pill is what the field never needed while it *was* the title: the bar's own close button now sits a few
 * pixels past its end, and without it the button that empties the field and the button that leaves the search are
 * two bare crosses side by side with nothing to say which belongs to what. It is also what tells an empty field
 * from a title, now that the two take turns in the same place.
 *
 * The field takes the focus as it opens, since it is there because the user asked for it and asking again with a
 * tap is one tap more than the action they already took; on a touch platform that is also what brings the keyboard
 * up with it. The caret goes to the end of what is already written, which is what a search that was left open and
 * then come back to has in it.
 */
@Composable
private fun SearchField(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    placeholder: String,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        searchState.textFieldState.edit { placeCursorAtEnd() }
        focusRequester.requestFocus()
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.height(FIELD_HEIGHT).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focusRequester),
                state = searchState.textFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = KeyboardActionHandler { keyboardController?.hide() },
                decorator = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchState.textFieldState.text.isEmpty()) {
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
                },
            )
            // Emptying the field and leaving the search are two different things, and the one that keeps the field
            // open belongs inside the pill. It is only offered once there is something to clear.
            AnimatedVisibility(
                visible = searchState.textFieldState.text.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                // The pill is shorter than a touch target, so the button is laid out at its own size the way the
                // section header pills' actions are.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    // The text field would otherwise show the text cursor over the button on desktop.
                    IconButton(
                        modifier = Modifier.size(CLEAR_BUTTON_SIZE).pointerHoverIcon(PointerIcon.Default, overrideDescendants = true),
                        onClick = { searchState.textFieldState.clearText() },
                    ) {
                        Icon(
                            modifier = Modifier.size(CLEAR_ICON_SIZE),
                            painter = painterResource(Res.drawable.ic_clear),
                            contentDescription = stringResource(Res.string.songs_clear),
                        )
                    }
                }
            }
        }
    }
}

/** How far into its own width each half of the title slot travels as it comes and goes. */
private const val TITLE_SLIDE_FRACTION = 6

private val FIELD_HEIGHT = 40.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
