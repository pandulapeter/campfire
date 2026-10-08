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
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlin.math.roundToInt
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect

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
 * @param areClosedSearchActionsShown Whether the screen offers [closedSearchActions] at all. The reasons they come and go
 *   (the list being read, performance mode being switched) happen while the bar is being looked at, so they make room
 *   for themselves rather than appearing between two frames and pushing the actions beside them aside as they land.
 * @param closedSearchActions The screen's actions that have nothing to do with a search in progress - making something
 *   new - which come last, after [actions] and a divider that sets them apart, while the search is closed and make way
 *   for the field while it is open, leaving and coming back on the spring the search action travels on so that the
 *   field's edge and they move as one.
 * @param actions The screen's other actions, which stay whether or not the search is open. Each is laid out with
 *   [overlappingAction], as every action of the pill is.
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
    isSearchEnabled: Boolean = true,
    areClosedSearchActionsShown: Boolean = false,
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
        movableContentOf { actionModifier: Modifier, actionPlaceholder: String, isEnabled: Boolean ->
            SearchAction(
                modifier = actionModifier,
                searchState = searchState,
                placeholder = actionPlaceholder,
                isEnabled = isEnabled,
            )
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    // Derived, so that the bar recomposes as the keyboard comes and goes rather than on every frame it slides.
    val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    CompactKeyboardEffect(isEnabled = isOpen && LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT && isKeyboardVisible)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection) + APP_BAR_END_PADDING
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
                    searchAction(actionModifier, placeholder, isSearchEnabled)
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
                        .padding(horizontal = ACTIONS_PILL_PADDING + ACTION_BUTTON_OVERLAP / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchActionSlot(
                        modifier = Modifier.overlappingAction(),
                        searchTransition = searchTransition,
                        isHoldingAction = { !it },
                    ) {
                        if (!isOpen) {
                            searchAction(actionModifier, placeholder, isSearchEnabled)
                        }
                    }
                    actions()
                    AnimatedVisibility(visible = areClosedSearchActionsShown) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClosedSearchActionsDivider(searchProgress = { pillProgress.value })
                            val closedSearchActionsSizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
                            val closedSearchActionsFadeSpec = searchTravelSpec<Float>()
                            searchTransition.AnimatedVisibility(
                                modifier = Modifier.overlappingAction(),
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
    }
}

/**
 * The line that sets the closed search's own actions apart from the rest of the bar's: those change how the list is
 * shown, while these add to what it holds.
 *
 * It narrows and fades on the search action's spring rather than inside the animation of the actions it stands in
 * front of, which shrink towards their end edge clipped to their bounds: the divider is the first thing at their
 * start, and the first few pixels of that shrink would cut it off while the button behind it was still fading. So it
 * is laid out at a width that follows the search and drawn centered in it, never clipped, and it is gone by the time
 * that width is.
 *
 * @param searchProgress How far the search is open, from 0 while it is closed to 1 while it is open.
 */
@Composable
private fun ClosedSearchActionsDivider(searchProgress: () -> Float) = VerticalDivider(
    modifier = Modifier
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val width = (placeable.width * (1f - searchProgress())).roundToInt().coerceAtLeast(0)
            layout(width, placeable.height) {
                placeable.placeRelative(x = (width - placeable.width) / 2, y = 0)
            }
        }
        .graphicsLayer { alpha = (1f - searchProgress()).coerceIn(0f, 1f) }
        .padding(horizontal = CLOSED_SEARCH_ACTIONS_DIVIDER_GAP)
        .height(CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT),
)

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
    modifier: Modifier = Modifier,
    searchTransition: Transition<Boolean>,
    isHoldingAction: (Boolean) -> Boolean,
    content: @Composable () -> Unit,
) {
    val sizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
    searchTransition.AnimatedVisibility(
        modifier = modifier,
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
    isEnabled: Boolean = true,
) {
    val isOpen by searchState.isOpen.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    IconButton(
        modifier = modifier,
        enabled = isEnabled,
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

/** Where a layout is heading to, in the coordinates of the [bar] that holds it: the rectangle it has once its animations end. */
private fun LookaheadScope.lookaheadBoundsOf(bar: LayoutCoordinates?, coordinates: LayoutCoordinates) =
    if (bar == null) Rect.Zero else Rect(
        offset = bar.localLookaheadPositionOf(coordinates),
        size = coordinates.toLookaheadCoordinates().size.toSize(),
    )

/**
 * How far past the end of the field slot the search action ends while the search is closed: the slot's own end
 * padding, and the touch target the action's slot keeps for it. The closed field is laid out with no width at that
 * edge, so that its start edge sets out from the end of the button rather than from under it.
 */
private val CLOSED_FIELD_OFFSET = 52.dp

/** The room the pill behind the closed bar's buttons leaves at either end of their touch targets. */
private val ACTIONS_PILL_PADDING = 4.dp

/** The room on either side of [ClosedSearchActionsDivider]. */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_GAP = 4.dp

/** The length of [ClosedSearchActionsDivider], short of the buttons' height so that it reads as a separator between them. */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT = 24.dp

/** The padding `TopAppBar` keeps at either end of its row, which this bar keeps so its buttons sit where a bar's would. */
private val APP_BAR_HORIZONTAL_PADDING = 4.dp

/**
 * The bar's end padding, wider than its start by the half of [ACTION_BUTTON_OVERLAP] the new item button no longer
 * takes up: that keeps [ClosedSearchActionsDivider] on the column of the song cards' overflow buttons, which the
 * [FastScroller]'s bubble runs down too.
 */
private val APP_BAR_END_PADDING = APP_BAR_HORIZONTAL_PADDING + ACTION_BUTTON_OVERLAP / 2

/**
 * How far in from the end edge of a list the middle of the closed bar's [ClosedSearchActionsDivider] stands, past the
 * list's own end inset: the bar's end padding, the pill's, the one 48dp button the list's new item menu is, less the
 * half of the overlap it reaches past the line with, and the gap before the line. The [FastScroller] centers its
 * bubble on it, so that the two read as one column down the list.
 */
internal val CLOSED_SEARCH_ACTIONS_DIVIDER_END_INSET =
    APP_BAR_END_PADDING + ACTIONS_PILL_PADDING + 48.dp - ACTION_BUTTON_OVERLAP / 2 + CLOSED_SEARCH_ACTIONS_DIVIDER_GAP +
        DividerDefaults.Thickness / 2
