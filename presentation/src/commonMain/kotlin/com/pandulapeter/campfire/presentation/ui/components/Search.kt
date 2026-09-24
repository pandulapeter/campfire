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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.toSize
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
 * The app bar of a list screen that can be searched. It has no title: the navigation bar or rail already says which
 * screen this is, and the list's own sticky section header is what stands where a title would, under this bar, which
 * is drawn over the top of the list rather than above it. So all a closed search leaves here is the actions, the
 * search action at their head, on a tonal pill that sets them apart from the pinned header under them as the screen's
 * own - the header keeps its text and its own action clear of them
 * (see [SectionHeader]'s `appBarOverlap`). Nothing in the bar but its buttons takes a touch, so the header underneath is
 * still pressed and dragged anywhere else along its length.
 *
 * An open search is a bar of its own: the search action stands at the very start of it, where a back button would,
 * the field reads on from it, and the bar fills in behind the two while the list moves down out of its way by the
 * same amount - both following [appBarReveal], which the screen animates with [animateAppBarReveal] and also lays the
 * list out by. A screen whose list has no header to stand in the bar's place (a placeholder, the ranked results of a
 * search, which come in one headerless group) keeps the bar shown for the same reason.
 *
 * The action moves to the start as the search opens because that is where an open search is left from. It is the one
 * button travelling across the bar rather than two buttons swapping places, since its mark is in the middle of turning
 * into the cross as it goes. So it is [movableContentOf] handed from the actions slot to the navigation icon slot and
 * back, which keeps the mark's animation where it was, and [animateBounds] carries it between the two, inside a
 * [LookaheadScope] that is only this bar. Each slot makes room for the button with a placeholder that grows and
 * shrinks on the same spring the button travels on, which is what moves the rest of the actions out of its way
 * instead of snapping them to where they end up.
 *
 * The field travels the same way, by [animateBounds] on the same spring, rather than being revealed by an animation of
 * its own: its start edge follows the button across the bar, and two different animations only ever arrive together
 * to within their visibility thresholds. A fraction of the field's width stops a hundredth short of the whole, which
 * on a wide window is several pixels for the edge to jump by on the last frame, while a rectangle settles to within a
 * pixel exactly as the button's does. So the field is laid out where it ends up — the whole field slot while the
 * search is open, and no width at all at the button's end edge while it is closed, [CLOSED_FIELD_OFFSET] past the end
 * of the slot — and the two rectangles travel between those places together.
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
 * @param contentPadding The screen's insets, of which the bar keeps clear of the start and the end ones.
 * @param appBarReveal How far the bar is filled in, read while it is drawn.
 * @param placeholder What the field says while it is empty, which also names the search action, see [SearchAction].
 * @param onReachChanged Called with how far the closed bar's buttons reach in from the end edge of the screen.
 * @param closedSearchActions The screen's actions that have nothing to do with a search in progress - making something
 *   new - which come last, after [actions], while the search is closed and make way for the field while it is open,
 *   leaving and coming back on the spring the search action travels on so that the field's edge and they move as one.
 * @param actions The screen's other actions, which stay whether or not the search is open.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SearchableTopAppBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    appBarReveal: () -> Float,
    placeholder: String,
    searchState: SearchState,
    onReachChanged: (Dp) -> Unit,
    closedSearchActions: @Composable RowScope.() -> Unit = {},
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
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val endPadding = contentPadding.calculateEndPadding(layoutDirection) + APP_BAR_HORIZONTAL_PADDING
    val containerColor = MaterialTheme.colorScheme.background
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val pill = remember { SearchPill() }
    val pillProgress = searchTransition.animateFloat(transitionSpec = { searchTravelSpec() }) { if (it) 1f else 0f }
    LookaheadScope {
        val lookaheadScope = this
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
        Row(
            modifier = modifier
                .fillMaxWidth()
                .onPlaced { pill.bar = it }
                .drawBehind {
                    drawRect(color = containerColor, alpha = appBarReveal().coerceIn(0f, 1f))
                    // Always fully opaque: the pill is one surface whose color must not change anywhere along the trip,
                    // a back gesture's preview included, which fades what the field holds and leaves the pill alone.
                    val bounds = lerp(pill.closedActions, pill.openField, pillProgress.value)
                    drawRoundRect(
                        color = pillColor,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(bounds.height / 2),
                    )
                }
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + APP_BAR_HORIZONTAL_PADDING,
                    end = endPadding,
                )
                .height(LIST_APP_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchActionSlot(
                searchTransition = searchTransition,
                isHoldingAction = { it },
            ) {
                if (isOpen) {
                    searchAction(actionModifier, placeholder)
                }
            }
            SearchFieldSlot(
                modifier = Modifier.weight(1f).padding(end = APP_BAR_HORIZONTAL_PADDING),
                placeholder = placeholder,
                searchState = searchState,
                searchTransition = searchTransition,
                recession = recession,
                fieldModifier = fieldModifier.onPlaced {
                    if (searchTransition.targetState) {
                        pill.openField = lookaheadScope.lookaheadBoundsOf(pill.bar, it)
                    }
                },
            )
            // The actions of a top app bar are drawn in a quieter color than its content.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(
                    // Measured only while the search is closed and settled, since that is the only state in which the
                    // bar is drawn over a pinned header rather than above the list: the reach is what the header keeps
                    // clear, and it would otherwise shrink under the headers as the search action leaves the actions.
                    modifier = Modifier
                        .onSizeChanged {
                            if (isClosedAndSettled) onReachChanged(with(density) { it.width.toDp() } + endPadding)
                        }
                        .onPlaced {
                            if (!searchTransition.targetState) {
                                pill.closedActions = lookaheadScope.lookaheadBoundsOf(pill.bar, it)
                            }
                        }
                        .padding(horizontal = ACTIONS_PILL_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchActionSlot(
                        searchTransition = searchTransition,
                        isHoldingAction = { !it },
                    ) {
                        if (!isOpen) {
                            searchAction(actionModifier, placeholder)
                        }
                    }
                    actions()
                    val closedSearchActionsSizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
                    val closedSearchActionsFadeSpec = searchTravelSpec<Float>()
                    searchTransition.AnimatedVisibility(
                        visible = { !it },
                        enter = fadeIn(closedSearchActionsFadeSpec) + expandHorizontally(closedSearchActionsSizeSpec),
                        exit = fadeOut(closedSearchActionsFadeSpec) + shrinkHorizontally(closedSearchActionsSizeSpec),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            closedSearchActions()
                        }
                    }
                }
            }
        }
    }
}

/**
 * How far the app bar of a list screen is filled in (see [SearchableTopAppBar]): not at all while a closed search leaves
 * the pinned header of the list to stand in its place, and completely while the search is open or while the list has
 * no header to stand there. It moves on the spring the search action travels on, since the bar filling in and the
 * list making room for it are part of the search opening.
 *
 * @param isShownWithoutSearch Whether the list has nothing to stand in the bar's place even while the search is closed.
 */
@Composable
internal fun animateAppBarReveal(
    searchState: SearchState,
    isShownWithoutSearch: Boolean,
): State<Float> {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    return animateFloatAsState(
        targetValue = if (isOpen || isShownWithoutSearch) 1f else 0f,
        animationSpec = searchTravelSpec(),
    )
}

/**
 * What of a list the app bar's buttons stand over while the bar is not filled in: how far in from the list's end edge
 * they reach, which a pinned [SectionHeader] keeps its text and its action clear of, and how far down from its top,
 * which the [FastScroller] starts below so that its thumb is never under a button that would take the press.
 *
 * @param reach How far the buttons reach in from the end edge of the list, whether or not the list is under them.
 * @param coverage How much of the row at the top of the list is still under the buttons: all of it while the bar is
 *   not filled in, none of it once the list has moved down out of its way. The header narrows by it the way it does
 *   by its own pinned fraction, since the list moving down under a pinned header and a header scrolling up into the
 *   pinned place are the same movement relative to the buttons.
 */
internal data class AppBarOverlap(
    val reach: Dp,
    val coverage: Float,
    val height: Dp,
) {

    companion object {

        /**
         * @param reach How far the buttons of the closed bar reach in from the end edge of the list.
         * @param appBarReveal How far the bar is filled in, which moves the list out from under it by as much.
         */
        fun of(reach: Dp, appBarReveal: Float): AppBarOverlap {
            val uncovered = 1f - appBarReveal.coerceIn(0f, 1f)
            return AppBarOverlap(reach = reach, coverage = uncovered, height = LIST_APP_BAR_HEIGHT * uncovered)
        }
    }
}

/**
 * Lays a list out under the part of the app bar [appBarReveal] says is filled in, reading it while the list is laid
 * out so that the bar filling in moves the list without recomposing it.
 */
internal fun Modifier.underAppBar(appBarReveal: () -> Float) = layout { measurable, constraints ->
    val top = (LIST_APP_BAR_HEIGHT.toPx() * appBarReveal().coerceIn(0f, 1f)).roundToInt()
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    layout(placeable.width, placeable.height + top) { placeable.placeRelative(x = 0, y = top) }
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
private fun SearchFieldSlot(
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
        // keyboard is asked for as well, since focusing a field that has the focus shows nothing.
        LaunchedEffect(isOpening) {
            if (isOpening) {
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
 * The one tonal pill of a list screen's app bar, which is the background of the buttons while the search is closed and
 * the background of the field while it is open, and travels between the two as the search opens and closes rather
 * than one fading out while the other fades in. Behind the buttons, it is what makes them read as the screen's own
 * rather than as the pinned header's: sitting on the header's row next to its name, they would otherwise look like
 * that one section's actions.
 *
 * It is drawn by the bar, and goes from where the buttons' row settles when the search is closed to where the field
 * settles when it is open, on the spring the search action travels on. Both ends are the *lookahead* rectangles, the
 * ones the layout is heading for, each taken from the state it belongs to: the rectangles of the frame itself are
 * still moving (the buttons' row narrowing as the action leaves it, the field's edges sweeping in from beyond the
 * bar's end), and an edge interpolated between two moving ones overshoots and comes back, where between two that stand
 * still it only ever travels the one way. Positions are kept in window coordinates because the three are laid out by
 * different parents, and the bar's own is taken off again as it draws.
 */
private class SearchPill {

    /** The bar's own coordinates, which the two rectangles are measured from. Read only from placement callbacks. */
    var bar: LayoutCoordinates? = null

    var closedActions by mutableStateOf(Rect.Zero)

    var openField by mutableStateOf(Rect.Zero)
}

/** Where a layout is heading to, in the coordinates of the [bar] that holds it: the rectangle it has once its animations end. */
private fun LookaheadScope.lookaheadBoundsOf(bar: LayoutCoordinates?, coordinates: LayoutCoordinates) =
    if (bar == null) Rect.Zero else Rect(
        offset = bar.localLookaheadPositionOf(coordinates),
        size = coordinates.toLookaheadCoordinates().size.toSize(),
    )

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

/** How much of the opacity of what the field holds a back gesture dragged all the way takes away before it is let go of. */
private const val RECEDED_ALPHA_LOSS = 0.5f

/** How much of the field's width a back gesture dragged all the way collapses before it is let go of. */
private const val RECEDED_WIDTH_LOSS = 0.25f

/**
 * How far past the end of the field slot the search action ends while the search is closed: the slot's own end
 * padding, and the touch target the action's slot keeps for it. The closed field is laid out with no width at that
 * edge, so that its start edge sets out from the end of the button rather than from under it.
 */
private val CLOSED_FIELD_OFFSET = 52.dp

/**
 * The height of a list screen's app bar, which is also the height of the list's section headers, since the one pinned
 * at the top stands in the bar's place with its text level with the bar's buttons. A little lower than a `TopAppBar`:
 * a header is a row of the list as well, repeated all the way down it, and the 48dp buttons still fit with room to
 * spare.
 */
internal val LIST_APP_BAR_HEIGHT = 56.dp

/** The room the pill behind the closed bar's buttons leaves at either end of them. */
private val ACTIONS_PILL_PADDING = 4.dp

/** The padding `TopAppBar` keeps at either end of its row, which this bar keeps so its buttons sit where a bar's would. */
private val APP_BAR_HORIZONTAL_PADDING = 4.dp

private val FIELD_HEIGHT = 40.dp
private val CLEAR_BUTTON_SIZE = 32.dp
private val CLEAR_ICON_SIZE = 18.dp
