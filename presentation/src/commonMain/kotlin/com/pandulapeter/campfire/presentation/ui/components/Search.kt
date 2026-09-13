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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
@Composable
internal fun SearchableTopAppBarTitle(
    modifier: Modifier = Modifier,
    title: String,
    placeholder: String,
    searchState: SearchState,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    SearchBackHandler(
        searchState = searchState,
        isOpen = isOpen,
    )
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
        val expansionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            // An animation of the visibility transition itself rather than a slide: the content is only removed
            // once it has settled, and the field grows out of its end edge instead of travelling towards it.
            val expansion by transition.animateFloat(
                transitionSpec = { expansionSpec },
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            SearchField(
                modifier = Modifier.fillMaxWidth(),
                searchState = searchState,
                isOpen = isOpen,
                placeholder = placeholder,
                expansion = { expansion },
            )
        }
    }
}

/**
 * The one back handler of the search, which closes it before a back gesture gets as far as leaving the screen.
 *
 * It is a navigation event handler rather than a plain back handler because a plain one only hears about a gesture
 * once it is over: the whole drag of a predictive back gesture went by with nothing on screen answering it, and the
 * search then vanished on release. This one is told how far the gesture has come on every frame, which it hands to
 * [SearchState.backProgress] for the field and the search action to preview the close with — the cross turning back
 * into the magnifier and the field receding the way it is about to leave — so the drag says what letting go will do.
 *
 * The desktop never reaches it: its window key handler sees Escape before Compose turns the key into a back event,
 * and closes the search there.
 */
@Composable
private fun SearchBackHandler(
    searchState: SearchState,
    isOpen: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val gesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // Registered whether or not the search is open and switched with isBackEnabled instead, since a handler that
    // comes and goes changes the order the dispatcher picks between handlers in.
    NavigationBackHandler(
        state = gesture,
        isBackEnabled = isOpen,
        onBackCompleted = {
            keyboardController?.hide()
            searchState.close()
        },
    )
    LaunchedEffect(gesture, searchState) {
        try {
            snapshotFlow {
                (gesture.transitionState as? NavigationEventTransitionState.InProgress)
                    ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                    ?.latestEvent
                    ?.progress
                    ?: 0f
            }.collect { searchState.backProgress = it }
        } finally {
            // A screen left in the middle of a gesture must not leave the next one it is composed into previewing it.
            searchState.backProgress = 0f
        }
    }
}

/**
 * Puts the keyboard away as soon as the list under the search starts moving down, which is what reading the results
 * looks like: the keyboard is only in the way of them by then, and it covers half of a phone's list. Scrolling back
 * up leaves it alone, since that is as likely to be on the way back to the field.
 *
 * Read from the list's own scroll state rather than from the gesture, so that every way the list is moved down counts
 * — a drag, the fling after it, the wheel, the fast scroller — and it fires once as a scroll down begins rather than
 * on every frame of it. The field keeps the focus and the caret, so a tap on it brings the keyboard straight back.
 */
@Composable
internal fun HideKeyboardWhenScrolledDown(
    scrollableState: ScrollableState,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(scrollableState, keyboardController) {
        snapshotFlow { scrollableState.isScrollInProgress && scrollableState.lastScrolledForward }
            .distinctUntilChanged()
            .filter { it }
            .collect { keyboardController?.hide() }
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
            backProgress = searchState.backProgress,
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
 *
 * It comes and goes by collapsing into its end edge rather than by sliding: the search action sits right past that
 * edge, and a field that travelled towards it would pass over the very button that is turning into the close mark.
 * The pill is laid out at its full width throughout and only clipped, so the text inside does not reflow as it goes.
 * What the pill holds rides its start edge instead, carried into the end as the field leaves and out of it as it
 * arrives, and is cut off where it meets the end: text that stayed put, or moved any slower than the edge, is read
 * as standing still while the pill is swept away from under it.
 *
 * While a back gesture that would close the search is being dragged, the field fades and collapses the way its exit
 * takes it, as far as the gesture has come.
 *
 * @param expansion How much of the field's width is showing, from nothing at all to the whole of it, read while the
 *   field is drawn so that an animation of it never recomposes the field.
 */
@Composable
private fun SearchField(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    isOpen: Boolean,
    placeholder: String,
    expansion: () -> Float,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        searchState.textFieldState.edit { placeCursorAtEnd() }
        focusRequester.requestFocus()
    }
    val recession = remember { Animatable(0f) }
    val returnSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(searchState.backProgress, isOpen) {
        when {
            searchState.backProgress > 0f -> recession.snapTo(searchState.backProgress)
            // A gesture let go of without closing the search brings the field back. One that did close it leaves
            // the field where the gesture had taken it, so that the exit carries on from there instead of the field
            // jumping back into place for the first frame of it.
            isOpen -> recession.animateTo(targetValue = 0f, animationSpec = returnSpec)
        }
    }
    // A spatial spring overshoots past the whole width, which would push the text back past its own padding.
    val widthFraction = { (expansion() * (1f - recession.value * RECEDED_WIDTH_LOSS)).coerceIn(0f, 1f) }
    val endwards = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    Surface(
        modifier = modifier.graphicsLayer {
            alpha = 1f - recession.value * RECEDED_ALPHA_LOSS
            shape = EndAnchoredPillShape(widthFraction = widthFraction())
            clip = true
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = endwards * (1f - widthFraction()) * size.width }
                .height(FIELD_HEIGHT)
                .padding(start = 16.dp, end = 4.dp),
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

/**
 * A pill as wide as [widthFraction] of the bounds it is drawn in, held against their end edge, which is what the
 * search field is clipped to as it collapses into that edge. Created anew for every frame of the animation, since a
 * layer only asks a shape for its outline again when it is handed a different one.
 */
private data class EndAnchoredPillShape(
    private val widthFraction: Float,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = size.width * widthFraction
        val radius = CornerRadius(minOf(width, size.height) / 2)
        return Outline.Rounded(
            if (layoutDirection == LayoutDirection.Ltr) {
                RoundRect(left = size.width - width, top = 0f, right = size.width, bottom = size.height, cornerRadius = radius)
            } else {
                RoundRect(left = 0f, top = 0f, right = width, bottom = size.height, cornerRadius = radius)
            }
        )
    }
}

/** How far into its own width the screen's name travels as it comes and goes. */
private const val TITLE_SLIDE_FRACTION = 6

/** How much of the field's opacity a back gesture dragged all the way takes away before it is let go of. */
private const val RECEDED_ALPHA_LOSS = 0.5f

/** How much of the field's width a back gesture dragged all the way collapses before it is let go of. */
private const val RECEDED_WIDTH_LOSS = 0.25f

private val FIELD_HEIGHT = 40.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
