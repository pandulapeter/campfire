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
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.songs_clear
import org.jetbrains.compose.resources.painterResource

/**
 * The app bar of a list screen that can be searched: the screen's name with the search action at the head of the
 * [actions] while the search is closed, and the same action at the very start of the bar with the field after it
 * while it is open.
 *
 * The search is an action and a state rather than a field that is always there because the bar of both list screens
 * is otherwise full: the field took the whole title slot, leaving the screen unnamed and the sort, filter and
 * "new" actions crowded against it.
 *
 * The action moves to the start as the search opens because that is where an open search is left from: it stands
 * where the back button of any other screen does, and the field it opened reads on from it. It is the one button
 * travelling across the bar rather than two buttons swapping places, since its mark is in the middle of turning
 * into the cross as it goes. So it is [movableContentOf] handed from the actions slot to the navigation icon slot
 * and back, which keeps the mark's animation where it was, and [animateBounds] carries it between the two, inside a
 * [LookaheadScope] that is only this bar. Each slot makes room for the button with a placeholder that grows and
 * shrinks on the same spring the button travels on, which is what moves the title and the rest of the actions out
 * of its way instead of snapping them to where they end up.
 *
 * The field travels the same way, by [animateBounds] on the same spring, rather than being revealed by an animation of
 * its own: its start edge follows the button across the bar, and two different animations only ever arrive together
 * to within their visibility thresholds. A fraction of the field's width stops a hundredth short of the whole, which
 * on a wide window is several pixels for the edge to jump by on the last frame, while a rectangle settles to within a
 * pixel exactly as the button's does. So the field is laid out where it ends up — the whole title slot while the search
 * is open, and no width at all at the button's end edge while it is closed, [CLOSED_FIELD_OFFSET] past the end of the
 * slot — and the two rectangles travel between those places together.
 *
 * The bounds only animate while the search is opening or closing: the same modifier would otherwise have the button
 * lag behind every other change of the bar's layout, a window being resized on the desktop among them.
 *
 * A back gesture dragged while the search is open previews the close with both halves of it: the field collapses
 * towards its end edge as far as the gesture has come (see [SearchRecession]), and the button is offset by exactly as
 * much as the field's start edge has moved, so the two travel back towards the end together. The offset is part of
 * the button's layout rather than a translation drawn over it, so that [animateBounds] sees where the gesture left the
 * button and a gesture that closes the search carries it on to the end from there instead of from the start of the bar.
 *
 * @param title The screen's own name, shown whenever the search is closed.
 * @param placeholder What the field says while it is empty, which also names the search action, see [SearchAction].
 * @param actions The screen's own actions, which follow the search action while the search is closed.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchableTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    title: String,
    placeholder: String,
    searchState: SearchState,
    actions: @Composable RowScope.() -> Unit,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    SearchBackHandler(
        searchState = searchState,
        isOpen = isOpen,
    )
    val searchTransition = updateTransition(targetState = isOpen)
    val recession = remember(searchState) { SearchRecession() }
    val returnSpec = searchTravelSpec<Float>()
    val isClosedAndSettled = !isOpen && !searchTransition.currentState
    LaunchedEffect(isOpen, isClosedAndSettled) {
        // The progress is collected rather than keyed on, since it changes on every frame of the gesture and each of
        // those would otherwise cancel and relaunch the effect. Latest, so that a gesture starting again while the
        // field is still coming back takes it from wherever it has got to.
        snapshotFlow { searchState.backProgress }.collectLatest { backProgress ->
            when {
                backProgress > 0f -> recession.progress.snapTo(backProgress)
                // A gesture let go of without closing the search brings the field and the button back. One that did
                // close it leaves both where the gesture had taken them, so that the exit carries on from there instead
                // of jumping back into place for the first frame of it, and is only forgotten once that exit is over,
                // so the next search opens onto a whole field.
                isOpen -> recession.progress.animateTo(targetValue = 0f, animationSpec = returnSpec)
                isClosedAndSettled -> recession.progress.snapTo(0f)
            }
        }
    }
    val travelSpec = searchTravelSpec(visibilityThreshold = Rect.VisibilityThreshold)
    val boundsTransform = remember(searchTransition, travelSpec) {
        BoundsTransform { _, _ -> if (searchTransition.currentState != searchTransition.targetState) travelSpec else snap() }
    }
    val searchAction = remember(searchState) {
        movableContentOf { actionModifier: Modifier, actionPlaceholder: String ->
            SearchAction(
                modifier = actionModifier,
                searchState = searchState,
                placeholder = actionPlaceholder,
            )
        }
    }
    LookaheadScope {
        val actionModifier = Modifier
            .offset { IntOffset(x = if (searchTransition.targetState) recession.startEdgeTravel else 0, y = 0) }
            .animateBounds(
                lookaheadScope = this,
                boundsTransform = boundsTransform,
            )
        val fieldModifier = Modifier
            .layout { measurable, constraints ->
                val isOpenOrOpening = searchTransition.targetState
                val width = if (isOpenOrOpening) (constraints.maxWidth - recession.startEdgeTravel).coerceAtLeast(0) else 0
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(width, placeable.height) {
                    placeable.placeRelative(x = if (isOpenOrOpening) 0 else CLOSED_FIELD_OFFSET.roundToPx(), y = 0)
                }
            }
            .animateBounds(
                lookaheadScope = this,
                boundsTransform = boundsTransform,
            )
        CampfireTopAppBar(
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                SearchActionSlot(
                    searchTransition = searchTransition,
                    isHoldingAction = { it },
                ) {
                    if (isOpen) {
                        searchAction(actionModifier, placeholder)
                    }
                }
            },
            title = {
                SearchableTopAppBarTitle(
                    title = title,
                    placeholder = placeholder,
                    searchState = searchState,
                    searchTransition = searchTransition,
                    recession = recession,
                    fieldModifier = fieldModifier,
                )
            },
            actions = {
                SearchActionSlot(
                    searchTransition = searchTransition,
                    isHoldingAction = { !it },
                ) {
                    if (!isOpen) {
                        searchAction(actionModifier, placeholder)
                    }
                }
                actions()
            },
        )
    }
}

/**
 * The room one end of the bar keeps for the search action while the action is there, and gives up gradually as it
 * leaves. It stays as large as a touch target for as long as it is shown, whether or not the button is still in it,
 * since the button is moved out on the first frame of the transition and a slot sized by its content would collapse
 * there and then. It is not clipped, because the button travelling out of it and into it is drawn outside of it for
 * all but the end of the way.
 *
 * @param isHoldingAction Whether this is the slot the action is in, given whether the search is open.
 */
@Composable
private fun SearchActionSlot(
    searchTransition: Transition<Boolean>,
    isHoldingAction: (Boolean) -> Boolean,
    content: @Composable () -> Unit,
) {
    val sizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
    searchTransition.AnimatedVisibility(
        visible = isHoldingAction,
        enter = expandHorizontally(animationSpec = sizeSpec, clip = false),
        exit = shrinkHorizontally(animationSpec = sizeSpec, clip = false),
    ) {
        Box(
            modifier = Modifier.minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * The title slot of a list screen's app bar: the screen's name, which the search field takes the place of while the
 * search is open. The two pass each other rather than being swapped, since both the search opening and the search
 * closing are things the user asked for and has to be able to see happen.
 *
 * The name is faded rather than moved out of the way: it drifts only a short, fixed distance towards the start edge
 * as it goes and back from there as it comes, while the fade is what actually takes it away. That distance is how far
 * it moves *on screen*, and the slot it is in moves the other way by more than that while it does, since the room
 * opening at the start for the search action pushes the whole title slot towards the end. A slide of just the
 * distance was read as the name drifting towards the end, so the slide also takes back the slot's own movement
 * ([TITLE_SLOT_SHIFT]); both run on the same spring, which is what lets the one cancel the other frame by frame. Both run on the spring the search action travels on, so the name is still readable while the rest of
 * the bar starts moving instead of blinking out on the quick effects spring before anything else has visibly begun.
 * The distance is fixed rather than a fraction of the name's width, so a long name does not travel further than a
 * short one.
 *
 * The field is never taken out of the [Box] the two share, only emptied and laid out with no width at all while the
 * search is closed, since [animateBounds] only animates a rectangle it has already seen: a field composed as the search
 * opened would appear at its full size on the first frame. It keeps the box as tall as the field throughout, so the
 * title is centered in it rather than pinned to the top of a box that grows and shrinks.
 *
 * @param fieldModifier Where the field is laid out and how it travels there, see [SearchableTopAppBar].
 */
@Composable
private fun SearchableTopAppBarTitle(
    modifier: Modifier = Modifier,
    title: String,
    placeholder: String,
    searchState: SearchState,
    searchTransition: Transition<Boolean>,
    recession: SearchRecession,
    fieldModifier: Modifier,
) = Box(
    modifier = modifier.fillMaxWidth().onSizeChanged { recession.fieldWidth = it.width },
    contentAlignment = Alignment.CenterStart,
) {
    // Slide offsets are placed as they are rather than mirrored, so the start edge is the left one only left to right.
    val towardsStartEdge = with(LocalDensity.current) { (TITLE_SLIDE_DISTANCE + TITLE_SLOT_SHIFT).roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) -1 else 1
    val titleSlideSpec = searchTravelSpec(visibilityThreshold = IntOffset.VisibilityThreshold)
    val titleFadeSpec = searchTravelSpec<Float>()
    searchTransition.AnimatedVisibility(
        visible = { !it },
        enter = fadeIn(titleFadeSpec) + slideInHorizontally(animationSpec = titleSlideSpec, initialOffsetX = { towardsStartEdge }),
        exit = fadeOut(titleFadeSpec) + slideOutHorizontally(animationSpec = titleSlideSpec, targetOffsetX = { towardsStartEdge }),
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val fieldFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fieldAlpha = searchTransition.animateFloat(
        transitionSpec = { fieldFadeSpec },
    ) { if (it) 1f else 0f }
    SearchField(
        modifier = Modifier.align(Alignment.CenterEnd).then(fieldModifier),
        searchState = searchState,
        placeholder = placeholder,
        isContentShown = searchTransition.currentState || searchTransition.targetState,
        alpha = { fieldAlpha.value },
        recession = recession,
    )
}

/**
 * The one back handler of the search, which closes it before a back gesture gets as far as leaving the screen.
 *
 * It is a navigation event handler rather than a plain back handler because a plain one only hears about a gesture
 * once it is over: the whole drag of a predictive back gesture went by with nothing on screen answering it, and the
 * search then vanished on release. This one is told how far the gesture has come on every frame, which it hands to
 * [SearchState.backProgress] for the field and the search action to preview the close with — the cross turning back
 * into the magnifier and moving towards the end of the bar with the field, which recedes the way it is about to
 * leave — so the drag says what letting go will do.
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
 * Its mark keeps the color of the actions wherever it is: the bar draws its navigation icon in a stronger color than
 * its actions, and the mark would otherwise change color on the frame it is handed from one slot to the other.
 *
 * @param placeholder What the field it opens says while it is empty, which is also what the button announces itself
 *   as - "Search in songs" is what pressing it does, and the screens have a search of their own to name.
 */
@Composable
private fun SearchAction(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    placeholder: String,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    IconButton(
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
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
 * pixels in front of it, and without it the button that empties the field and the button that leaves the search are
 * two bare crosses on one line with nothing to say which belongs to what. It is also what tells an empty field
 * from a title, now that the two take turns in the same place.
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
 * what it holds is laid out at the full width of the title slot, so the text inside does not reflow as it goes.
 * That content rides the start edge, carried into the end as the field leaves and out of it as it arrives, and is cut
 * off where it meets the end: text that stayed put, or moved any slower than the edge, is read as standing still while
 * the pill is swept away from under it.
 *
 * While a back gesture that would close the search is being dragged, the field fades and collapses the way its exit
 * takes it, as far as the gesture has come.
 *
 * @param isContentShown Whether the pill holds the field at all, which it does from the moment the search starts
 *   opening until it has finished closing. A closed search keeps nothing in it that could take the focus.
 * @param alpha How opaque the field is, read while it is drawn so that fading it never recomposes it.
 * @param recession How far a back gesture has taken the field towards closing, read the same way.
 */
@Composable
private fun SearchField(
    modifier: Modifier = Modifier,
    searchState: SearchState,
    placeholder: String,
    isContentShown: Boolean,
    alpha: () -> Float,
    recession: SearchRecession,
) = Surface(
    modifier = modifier
        .height(FIELD_HEIGHT)
        .graphicsLayer { this.alpha = alpha() * (1f - recession.progress.value * RECEDED_ALPHA_LOSS) },
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    if (isContentShown) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            searchState.textFieldState.edit { placeCursorAtEnd() }
            focusRequester.requestFocus()
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

/** Keeps what fits of a paste rather than refusing all of it, which is what `InputTransformation.maxLength` does. */
private object TruncateSearchQuery : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (length > MAX_SEARCH_QUERY_LENGTH) replace(MAX_SEARCH_QUERY_LENGTH, length, "")
    }
}

/**
 * How far a back gesture that would close the search has taken the field towards closing: the gesture's own progress
 * while it is dragged, animated back to nothing when it is let go of without closing the search. It is held by the app
 * bar rather than by the field because the search action follows it too, and the two only move as one if they read
 * the same value on the same frame.
 */
private class SearchRecession {

    val progress = Animatable(0f)

    /** The field's full width, which is what the part it loses is a fraction of. */
    var fieldWidth by mutableIntStateOf(0)

    /** How far the field's start edge has moved towards its end, which is how far the search action is moved with it. */
    val startEdgeTravel: Int
        get() = (progress.value * RECEDED_WIDTH_LOSS * fieldWidth).roundToInt()
}

/**
 * The spring the search action travels across the bar on, shared by the room either end of the bar makes for it and
 * by the field whose edge follows it, since the three are one movement and have to arrive together. It is critically
 * damped rather than the theme's spatial spring, which overshoots: a button that travels past the start of the bar
 * and settles back reads as having bounced off the edge of the window.
 */
private fun <T> searchTravelSpec(visibilityThreshold: T? = null) = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = visibilityThreshold,
)

/** How much of the field's opacity a back gesture dragged all the way takes away before it is let go of. */
private const val RECEDED_ALPHA_LOSS = 0.5f

/** How much of the field's width a back gesture dragged all the way collapses before it is let go of. */
private const val RECEDED_WIDTH_LOSS = 0.25f

/** How far the screen's name is seen to drift towards the start edge as it fades out, and back from as it fades in. */
private val TITLE_SLIDE_DISTANCE = 16.dp

/**
 * How far the title slot moves towards the end while the search opens, and back while it closes. `TopAppBar` starts
 * its title 12dp in while there is no navigation icon, and after the icon's slot once that is wider - which the search
 * action's is, a 48dp touch target behind the bar's 4dp padding - so the slot starts 52dp in while the search is open.
 */
private val TITLE_SLOT_SHIFT = 40.dp

/**
 * How far past the end of the title slot's content the search action ends while the search is closed: the 4dp
 * `TopAppBar` pads its title by, and the touch target the action's slot keeps for it. The closed field is laid out
 * with no width at that edge, so that its start edge sets out from the end of the button rather than from under it.
 */
private val CLOSED_FIELD_OFFSET = 52.dp

private val FIELD_HEIGHT = 40.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
