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
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.songs_clear
import org.jetbrains.compose.resources.painterResource

/**
 * The part of a list screen's app bar the search field opens into, between the search action at the start of the bar
 * and the actions at its end.
 *
 * The field is never taken out of the [Box], only emptied and laid out with no width at all while the search is
 * closed, since [animateBounds] only animates a rectangle it has already seen: a field composed as the search opened
 * would appear at its full size on the first frame.
 *
 * @param fieldModifier Where the field is laid out and how it travels there, see [SearchableTopAppBar].
 */
@Composable
internal fun SearchFieldSlot(
    modifier: Modifier = Modifier,
    placeholder: String,
    searchState: SearchState,
    searchTransition: Transition<Boolean>,
    recession: SearchRecession,
    fieldModifier: Modifier,
) = Box(
    modifier = modifier.onSizeChanged { recession.fieldWidth = it.width },
    contentAlignment = Alignment.CenterStart,
) {
    val fieldFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fieldAlpha = searchTransition.animateFloat(
        transitionSpec = { fieldFadeSpec },
    ) { if (it) 1f else 0f }
    SearchField(
        modifier = Modifier.align(Alignment.CenterEnd).then(fieldModifier),
        searchState = searchState,
        placeholder = placeholder,
        isContentShown = searchTransition.currentState || searchTransition.targetState,
        isOpening = searchTransition.targetState,
        alpha = { fieldAlpha.value },
        recession = recession,
    )
}

/**
 * The field itself: one line of text and the button that empties it, on the bar's tonal pill (see [SearchPill]),
 * which is not drawn here but travels in from behind the buttons as the search opens. The field only clips its
 * content to the pill's shape.
 *
 * It is laid out here rather than taken from `SearchBarDefaults.InputField`, which is fixed at the 56dp of a search
 * bar standing on its own — inside a 64dp app bar that leaves four pixels of daylight above and below it, so the
 * field reads as the whole of the bar rather than as something sitting in it, and neither its height nor the
 * padding that decides it can be passed in. What is wanted here is the height of the actions beside it, which also
 * brings the clear button down to the compact size the header pills use.
 *
 * The pill is there because the bar's own close button sits a few pixels in front of the field, and without it the
 * button that empties the field and the button that leaves the search are two bare crosses on one line with nothing
 * to say which belongs to what.
 *
 * The field takes the focus as it opens, since it is there because the user asked for it and asking again with a
 * tap is one tap more than the action they already took; on a touch platform that is also what brings the keyboard
 * up with it. The caret goes to the end of what is already written, which is what a search that was left open and
 * then come back to has in it.
 *
 * It comes and goes by collapsing into its end edge rather than by sliding: that edge is where the search action
 * sets out from as the search opens, so the start edge of the pill follows the button across the bar to the start
 * of it, and follows it back into the end as it closes, where a field that slid in would pass under the very button
 * that is turning into the close mark. The pill is only ever as wide as it is seen, see [SearchableTopAppBar], while
 * what it holds is laid out at the full width of the field slot, so the text inside does not reflow as it goes.
 * That content rides the start edge, carried into the end as the field leaves and out of it as it arrives, and is cut
 * off where it meets the end: text that stayed put, or moved any slower than the edge, is read as standing still while
 * the pill is swept away from under it.
 *
 * While a back gesture that would close the search is being dragged, the field fades and collapses the way its exit
 * takes it, as far as the gesture has come.
 *
 * @param isContentShown Whether the pill holds the field at all, which it does from the moment the search starts
 *   opening until it has finished closing. A closed search keeps nothing in it that could take the focus.
 * @param isOpening Whether the search is open or on its way there, which is when the field takes the focus and the
 *   keyboard.
 * @param alpha How opaque the field is, read while it is drawn so that fading it never recomposes it.
 * @param recession How far a back gesture has taken the field towards closing, read the same way.
 */
@Composable
private fun SearchField(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    placeholder: String,
    isContentShown: Boolean,
    isOpening: Boolean,
    alpha: () -> Float,
    recession: SearchRecession,
) = Surface(
    modifier = modifier
        .height(FIELD_HEIGHT)
        .graphicsLayer { this.alpha = alpha() * (1f - recession.progress.value * RECEDED_ALPHA_LOSS) },
    shape = CircleShape,
    color = Color.Transparent,
) {
    if (isContentShown) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }
        // Keyed on the search being opened rather than on the content arriving: a search opened again while it was
        // still closing never left the composition, and kept the focus while the keyboard had been put away - so the
        // keyboard is asked for as well, since focusing a field that has the focus shows nothing. And only once per
        // opening: the screen leaves the composition while a song covers it, and a search the user had put the keyboard
        // away in must not bring it back on the way back.
        LaunchedEffect(isOpening) {
            if (isOpening && searchState.takeFocusOnOpen()) {
                searchState.textFieldState.edit { placeCursorAtEnd() }
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
        LaunchedEffect(searchState) {
            searchState.focusRequests.collect {
                searchState.textFieldState.edit { selectAll() }
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    // The pill's own width where that is the larger: a spring turned around halfway carries the pill
                    // past the whole of the slot, and the text would otherwise stop short of its end.
                    val width = maxOf(constraints.maxWidth, recession.fieldWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(x = 0, y = 0)
                    }
                }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focusRequester),
                state = searchState.textFieldState,
                inputTransformation = TruncateSearchQuery,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // The results follow every keystroke, so a word autocorrect rewrites on the next space is a search the
                // reader did not type - and what is searched for is mostly names, which it has no dictionary for.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
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

/** Keeps what fits of a paste rather than refusing all of it, which is what `InputTransformation.maxLength` does. */
internal object TruncateSearchQuery : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (length > MAX_SEARCH_QUERY_LENGTH) replace(MAX_SEARCH_QUERY_LENGTH, length, "")
    }
}

/** How much of the opacity of what the field holds a back gesture dragged all the way takes away before it is let go of. */
private const val RECEDED_ALPHA_LOSS = 0.5f

private val FIELD_HEIGHT = 40.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
