/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.components.list.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.list.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_OUTER_PADDING
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

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
 * @param areClosedSearchActionsKeptInSearch Keeps [closedSearchActions] in the bar while the search is open, the divider
 *   in front of them included: the songs screen's selection menu, since the search is where a selection is put
 *   together. New still makes way for the field, which is the case this is false for.
 * @param areClosedSearchActionsDetached Stands [closedSearchActions] beside the pill instead of on it, with a gap in
 *   place of the divider: the New menu, a filled button that reads as the screen's main action on its own and only
 *   crowds the pill's quieter icons on it. The selection menu's plain icons stay on the pill. The pill's end and the
 *   gap follow a change on the search's spring, so the button never moves.
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
    areClosedSearchActionsKeptInSearch: Boolean = false,
    areClosedSearchActionsDetached: Boolean = false,
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
    val detachProgress = animateFloatAsState(targetValue = if (areClosedSearchActionsDetached) 1f else 0f, animationSpec = searchTravelSpec())
    val detachTarget by rememberUpdatedState(if (areClosedSearchActionsDetached) 1f else 0f)
    val travelSpec = searchTravelSpec(visibilityThreshold = Rect.VisibilityThreshold)
    val boundsTransform = remember(searchTransition, travelSpec, detachProgress) {
        BoundsTransform { _, _ ->
            // The closed actions changing (New giving way to the selection menu) resize the row the search action leads
            // on the same spring, but only outside the lookahead: snapped to its lookahead bounds, the action would jump
            // to where the row ends up and pass over the buttons still sliding there.
            val isTravelling = searchTransition.currentState != searchTransition.targetState || detachProgress.value != detachTarget
            if (isTravelling) travelSpec else snap()
        }
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
    val closedPillHeight = with(density) { CLOSED_PILL_HEIGHT.toPx() }
    val pillProgress = searchTransition.animateFloat(transitionSpec = { searchTravelSpec() }) { if (it) 1f else 0f }
    val hasDetachedActions = areClosedSearchActionsShown && areClosedSearchActionsDetached
    val undetachedEndProgress = animateFloatAsState(targetValue = if (hasDetachedActions) 0f else 1f, animationSpec = searchTravelSpec())
    val reachEndPadding = endPadding + if (hasDetachedActions) 0.dp else UNDETACHED_PILL_END_INSET
    CompositionLocalProvider(LocalSearchPill provides pill) {
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
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .onPlaced { pill.bar = it }
                    .drawBehind {
                        drawRect(color = containerColor, alpha = appBarReveal().coerceIn(0f, 1f))
                        // Always fully opaque: the pill is one surface whose color must not change anywhere along the trip,
                        // a back gesture's preview included, which fades what the field holds and leaves the pill alone.
                        // Thinner than the buttons' touch targets, which stay whole and reach past its top and bottom.
                        val closedBounds = pill.closedBounds(
                            height = closedPillHeight,
                            detachProgress = detachProgress.value,
                            layoutDirection = layoutDirection,
                        )
                        val bounds = lerp(closedBounds, pill.openField, pillProgress.value)
                        drawRoundRect(
                            color = pillColor,
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            cornerRadius = CornerRadius(bounds.height / 2),
                        )
                    },
            ) {
                // Between the pill and the buttons, so that a press tints the pill under the icon rather than the icon.
                SearchPillPresses(
                    modifier = Modifier.matchParentSize(),
                    pill = pill,
                    closedBounds = {
                        pill.closedBounds(height = closedPillHeight, detachProgress = detachProgress.value, layoutDirection = layoutDirection)
                    },
                    openProgress = { pillProgress.value },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection) + APP_BAR_HORIZONTAL_PADDING,
                            end = endPadding,
                        )
                        // Read while laying out, so that the inset following a detached button in and out recomposes nothing.
                        .layout { measurable, constraints ->
                            val inset = (UNDETACHED_PILL_END_INSET.toPx() * undetachedEndProgress.value).roundToInt()
                            val placeable = measurable.measure(constraints.offset(horizontal = -inset))
                            layout(placeable.width + inset, placeable.height) { placeable.placeRelative(0, 0) }
                        }
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
                                    if (isClosedAndSettled) onReachChanged(with(density) { it.width.toDp() } + reachEndPadding)
                                }
                                .onPlaced {
                                    if (!searchTransition.targetState) {
                                        pill.closedActions = lookaheadScope.closedBoundsOf(pill.bar, it, isClosing = searchTransition.currentState)
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
                            AnimatedVisibility(
                                modifier = Modifier.onPlaced {
                                    if (!searchTransition.targetState) {
                                        pill.closedSearchActionsWidth =
                                            lookaheadScope.closedBoundsOf(pill.bar, it, isClosing = searchTransition.currentState).width
                                    }
                                },
                                visible = areClosedSearchActionsShown,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ClosedSearchActionsDivider(
                                        pill = pill,
                                        searchProgress = { if (areClosedSearchActionsKeptInSearch) 0f else pillProgress.value },
                                        detachProgress = { detachProgress.value },
                                    )
                                    val closedSearchActionsSizeSpec = searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold)
                                    val closedSearchActionsFadeSpec = searchTravelSpec<Float>()
                                    searchTransition.AnimatedVisibility(
                                        modifier = Modifier.overlappingAction(),
                                        visible = { !it || areClosedSearchActionsKeptInSearch },
                                        // A detached button is a filled circle, which a clipped shrink would slice into a half-moon on
                                        // its way out: it shrinks in place instead, while its room closes behind the field's edge.
                                        enter = fadeIn(closedSearchActionsFadeSpec) + if (areClosedSearchActionsDetached) {
                                            scaleIn(closedSearchActionsFadeSpec, initialScale = DETACHED_ACTION_HIDDEN_SCALE) +
                                                expandHorizontally(closedSearchActionsSizeSpec, clip = false)
                                        } else {
                                            expandHorizontally(closedSearchActionsSizeSpec)
                                        },
                                        // Faded out sooner than the room closes, too, or the actions sliding into its place pass
                                        // under what is left of the disc.
                                        exit = if (areClosedSearchActionsDetached) {
                                            fadeOut(tween(DETACHED_ACTION_FADE_OUT_MILLIS)) +
                                                scaleOut(closedSearchActionsFadeSpec, targetScale = DETACHED_ACTION_HIDDEN_SCALE) +
                                                shrinkHorizontally(closedSearchActionsSizeSpec, clip = false)
                                        } else {
                                            fadeOut(closedSearchActionsFadeSpec) + shrinkHorizontally(closedSearchActionsSizeSpec)
                                        },
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
    }
}

/**
 * The line that sets the closed search's own actions apart from the rest of the bar's: those change how the list is
 * shown, while these add to what it holds.
 *
 * On the pill it takes a little room of its own ([CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM]), which the shares of the two
 * buttons it divides stop at (see [SearchPill.divider]), so that neither's press runs over the line. Before detached actions it fades out and its room widens into the gap
 * between the pill's end and theirs (see [DETACHED_CLOSED_SEARCH_ACTIONS_GAP]). It narrows and fades on the search
 * action's spring rather than inside the animation of the actions it stands in front of, which shrink towards their
 * end edge clipped to their bounds and would cut it off while the button behind it was still fading, and it is drawn
 * centered in its room, never clipped.
 *
 * @param searchProgress How far the search is open, from 0 while it is closed to 1 while it is open.
 * @param detachProgress How far the actions after it are detached from the pill, from 0 to 1.
 */
@Composable
private fun ClosedSearchActionsDivider(
    pill: SearchPill,
    searchProgress: () -> Float,
    detachProgress: () -> Float,
) {
    DisposableEffect(pill) {
        onDispose { pill.divider = null }
    }
    VerticalDivider(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val room = CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM.toPx()
                val closedWidth = room + (DETACHED_CLOSED_SEARCH_ACTIONS_GAP.toPx() - room) * detachProgress()
                val width = (closedWidth * (1f - searchProgress())).roundToInt().coerceAtLeast(0)
                layout(width, placeable.height) {
                    placeable.placeRelative(x = (width - placeable.width) / 2, y = 0)
                }
            }
            .onGloballyPositioned { coordinates ->
                pill.bar?.takeIf { it.isAttached }?.let { pill.divider = it.localBoundingBoxOf(coordinates, clipBounds = false) }
            }
            .graphicsLayer { alpha = ((1f - searchProgress()) * (1f - detachProgress())).coerceIn(0f, 1f) }
            .height(CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT),
    )
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
    PillIconButton(
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
 * Where a part of the closed bar is, in the coordinates of the [bar] that holds it: where it is heading to while the
 * search is closing, which the pill sets out for from the field, and where it is on this frame once the search is closed,
 * so that the pill follows the buttons as the closed actions change (New giving way to the selection menu) instead of
 * jumping to where they end up and leaving them outside it on the way.
 */
private fun LookaheadScope.closedBoundsOf(bar: LayoutCoordinates?, coordinates: LayoutCoordinates, isClosing: Boolean) = when {
    isClosing -> lookaheadBoundsOf(bar, coordinates)
    bar == null || !bar.isAttached -> Rect.Zero
    else -> bar.localBoundingBoxOf(coordinates, clipBounds = false)
}

/**
 * How far past the end of the field slot the search action ends while the search is closed: the slot's own end
 * padding, and the touch target the action's slot keeps for it. The closed field is laid out with no width at that
 * edge, so that its start edge sets out from the end of the button rather than from under it.
 */
private val CLOSED_FIELD_OFFSET = 52.dp

/**
 * The room the pill behind the closed bar's buttons leaves at either end of their touch targets: none, which ends it
 * further past the outer icons than half the buttons' spacing. A share of the pill (see [searchPillSegment]) that is
 * centered on its icon only looks it in the middle of the pill; at its rounded ends the icon reads as crowding the
 * curve, so the end shares are made wider until it looks centered.
 */
private val ACTIONS_PILL_PADDING = 0.dp

/**
 * The height of the pill behind the closed bar's buttons: as tall as the search field it turns into, so that it only
 * grows and shrinks along the bar, and short enough to read as the quieter of the bar's two shapes beside the New button.
 */
private val CLOSED_PILL_HEIGHT = FIELD_HEIGHT

/** The length of [ClosedSearchActionsDivider], short of the buttons' height so that it reads as a separator between them. */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_HEIGHT = 24.dp

/**
 * The room [ClosedSearchActionsDivider] takes on the pill, the line in the middle of it: what keeps the presses of the
 * buttons on either side off the line, and the button before it in line with the checkboxes of a selection.
 */
private val CLOSED_SEARCH_ACTIONS_DIVIDER_ROOM = 4.dp

/**
 * The gap seen between the closed bar's pill and the New button's circle, as wide as the circle's distance from the end
 * of the list. The Tuner tab's reference pitch pill keeps it from its play button, which stands on the New button's spot.
 */
internal val NEW_ITEM_BUTTON_GAP = SONG_CARD_OUTER_PADDING

/**
 * The room [ClosedSearchActionsDivider] takes before detached actions, which leaves the pill's end as far from the New
 * button's circle, which fills its touch target, as the circle is from the end of the list. The room counts the pill's
 * padding and the overlap the button reaches back into it by, neither of which is part of the gap that is seen.
 */
private val DETACHED_CLOSED_SEARCH_ACTIONS_GAP = NEW_ITEM_BUTTON_GAP + ACTIONS_PILL_PADDING + ACTION_BUTTON_OVERLAP

/** How small a detached action is as it fades out of the bar and back in, see `areClosedSearchActionsDetached`. */
private const val DETACHED_ACTION_HIDDEN_SCALE = 0.6f

/** How long a detached action takes to fade out of the bar as the search opens. */
private const val DETACHED_ACTION_FADE_OUT_MILLIS = 120

/** The padding `TopAppBar` keeps at either end of its row, which this bar keeps so its buttons sit where a bar's would. */
private val APP_BAR_HORIZONTAL_PADDING = 4.dp

/**
 * The bar's end padding, which with the pill's own puts the New button's circle on the cards' outer keyline: as far in
 * from the end of the list as the cards are while it has no [FastScroller] column, and so as far as they are from its
 * start. It does not follow the column as it comes and goes, so the button stays put while the cards narrow beside it.
 */
private val APP_BAR_END_PADDING = SONG_CARD_OUTER_PADDING - ACTIONS_PILL_PADDING

/**
 * What the bar adds to its end padding while no detached button follows the pill (read only mode, a selection, a
 * screen with nothing to add), so that the pill itself ends on the keyline the New button's circle would.
 */
private val UNDETACHED_PILL_END_INSET = SONG_CARD_OUTER_PADDING - APP_BAR_END_PADDING

/**
 * How far in from the end edge of a list the middle of the closed pill's last action stands while the actions after it
 * are detached, past the list's own end inset: the bar's end padding, the pill's, the detached New button less the
 * overlap it shares with the pill's padding, the gap before it, and half of the last action, which reaches into its
 * neighbors by half the overlap on either side. The [FastScroller] puts the letter of its bubble under it, so that the
 * two read as one column down the list.
 */
internal val CLOSED_PILL_LAST_ACTION_END_INSET =
    APP_BAR_END_PADDING + ACTIONS_PILL_PADDING + ACTION_BUTTON_OVERLAP / 2 + (NEW_ITEM_BUTTON_SIZE - ACTION_BUTTON_OVERLAP) +
        DETACHED_CLOSED_SEARCH_ACTIONS_GAP + (48.dp - ACTION_BUTTON_OVERLAP) / 2

/**
 * Where the New button's circle stands in the list screens' bar, past the list's own insets: centered in the bar's
 * height, and in from the end by the bar's end padding and the pill's, less the half of [ACTION_BUTTON_OVERLAP] the
 * button reaches past its slot by. The Metronome tab's play button stands on the same spot, see `MetronomePanel`.
 */
internal val NEW_ITEM_BUTTON_TOP_INSET = (LIST_APP_BAR_HEIGHT - NEW_ITEM_BUTTON_SIZE) / 2
internal val NEW_ITEM_BUTTON_END_INSET = APP_BAR_END_PADDING + ACTIONS_PILL_PADDING
