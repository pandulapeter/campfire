/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_page
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderStartOverlay
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.verticalWheelNotches
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The pages side by side in a pager, turned by a swipe, by the buttons floating under them or, once the pane has the
 * focus, by the arrow, Page Up / Page Down, Home and End keys, and zoomed by a pinch, a double tap, a pinch on a touchpad
 * or, on the desktop, Ctrl / Cmd and the scroll wheel. It is the whole sheet of paper that grows, edges and all, past
 * the pane that cuts it off and under the buttons floating over it, the way a document viewer zooms, rather than its
 * content growing inside a page that keeps its size, which read as the text being enlarged for the file. At a zoom of 1
 * the page fits what the floating buttons leave of the pane. A zoomed page is drawn again at its new size rather than
 * scaled up, so it stays sharp, and the pager does not take a swipe while it is zoomed, since the swipe is the pan.
 * The zoom and the pan are [pageView], a point of the page rather than an offset in pixels, so that a pane laid out at
 * another size keeps the same part of the page in its middle.
 */
@Composable
internal fun PrintPages(
    laidOut: LaidOutDocument,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    bottomInset: Dp,
    areOptionsBelow: Boolean,
    pagerState: PagerState,
    magnifications: Flow<Float>,
    pageView: () -> PageView,
    onPageViewChanged: (PageView) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val pageCount = laidOut.document.pages.size
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }
    val aspectRatio = laidOut.document.width / laidOut.document.height
    val fitBottom = if (areOptionsBelow) PAGE_MARGIN else bottomInset + SAVE_BUTTON_CLEARANCE
    val zoomAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var zoomAnimation by remember { mutableStateOf<Job?>(null) }
    fun fitArea() = with(density) { fitArea(viewportSize, PAGE_MARGIN.toPx(), fitBottom.toPx()) }
    // Stop a double-tap zoom when paging hands control to the screen's animated reset.
    LaunchedEffect(pagerState) {
        var previous = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled != previous) {
                zoomAnimation?.cancel()
            }
            previous = settled
        }
    }
    // Only where a keyboard is the way the app is driven: on a touch screen it would bring the keyboard's focus ring up.
    LaunchedEffect(Unit) { if (isDesktopPlatform) focusRequester.requestFocus() }
    fun shownPageBounds() = pageBounds(viewportSize, fitArea(), aspectRatio, pageView())
    fun zoomTo(target: Float, pivot: Offset = fitArea().center) {
        val view = pageView()
        val clampedZoom = target.coerceIn(1f, MAX_ZOOM)
        val area = fitArea()
        // The point of the page under the pivot stays under it, until the page is back at the size it fits at.
        val topLeft = pivot - (pivot - shownPageBounds().topLeft) * (clampedZoom / view.zoom)
        val zoomedSize = fittedPageSize(area.size, aspectRatio) * clampedZoom
        onPageViewChanged(pageViewOf(clampedZoom, topLeft - centeredTopLeft(zoomedSize, area), area, zoomedSize, viewportSize))
    }
    // Stepping zoomTo through the values keeps the point under the pivot where it is on every frame, which a spring on
    // the zoom and the focus apart would not: the two would travel on paths of their own and the page would swing. Any
    // other gesture takes the page from wherever the animation has got it to.
    fun animateZoomTo(target: Float, pivot: Offset) {
        zoomAnimation?.cancel()
        zoomAnimation = coroutineScope.launch {
            animate(pageView().zoom, target, animationSpec = zoomAnimationSpec) { value, _ -> zoomTo(value, pivot) }
        }
    }
    fun zoomBy(factor: Float, pivot: Offset) {
        zoomAnimation?.cancel()
        zoomTo(pageView().zoom * factor, pivot)
    }
    // How far a page has been moved past where it rests towards an edge of the pane, which is as strong as the fade
    // there is: a page at rest is drawn whole, and one zoomed, panned or turned fades out as it goes under the app bar
    // or towards the options, the way anything scrolled out of a pane of the app does, instead of being cut off there.
    fun pastTop() = if (viewportSize == Size.Zero) 0 else with(density) { PAGE_MARGIN.toPx() - shownPageBounds().top }.roundToInt()
    fun pastBottom() = if (viewportSize == Size.Zero) 0 else with(density) {
        shownPageBounds().bottom - viewportSize.height + PAGE_MARGIN.toPx()
    }.roundToInt()
    fun pastStart(): Int {
        if (viewportSize == Size.Zero) return 0
        val margin = with(density) { PAGE_MARGIN.toPx() }
        return pagerState.layoutInfo.visiblePagesInfo.maxOfOrNull { info ->
            val bounds = if (info.index == pagerState.currentPage) {
                shownPageBounds()
            } else {
                pageBounds(viewportSize, fitArea(), aspectRatio, PageView())
            }
            margin - info.offset - if (layoutDirection == LayoutDirection.Rtl) viewportSize.width - bounds.right else bounds.left
        }?.roundToInt() ?: 0
    }
    fun turnTo(target: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) }
    }
    // Around the pinch's centroid, which is in the pane's coordinates, so the part of the page between the fingers stays
    // between them, as a touchpad's pinch keeps the part under the pointer.
    val transformableState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        zoomBy(zoomChange, centroid)
        val view = pageView()
        if (view.zoom > 1f) {
            val area = fitArea()
            val zoomedSize = fittedPageSize(area.size, aspectRatio) * view.zoom
            val pan = clampPan(view.panOf(zoomedSize), area, zoomedSize, viewportSize)
            onPageViewChanged(pageViewOf(view.zoom, pan + panChange, area, zoomedSize, viewportSize))
        }
    }
    // Around the pointer, where it is over the page, as the scroll wheel zooms; a pinch on a touchpad says nowhere.
    LaunchedEffect(magnifications) {
        magnifications.collect { factor -> zoomBy(factor, pointerPosition ?: fitArea().center) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Alt + Left is the browser's Back, which on the web closes this screen, and Cmd + Left and Right are Back
                // and Forward on a Mac, so a press with any of those down is left to whoever sent it.
                if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) {
                    return@onKeyEvent false
                }
                val target = when (event.key) {
                    Key.DirectionLeft, Key.PageUp -> pagerState.currentPage - 1
                    Key.DirectionRight, Key.PageDown -> pagerState.currentPage + 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> pageCount - 1
                    else -> return@onKeyEvent false
                }
                turnTo(target)
                true
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.bounceScrollableContent(pagerState, Orientation.Horizontal)
                .fillMaxSize()
                .fadingVerticalEdges(
                    scrolledFromTop = ::pastTop,
                    scrolledFromBottom = { if (areOptionsBelow) pastBottom() else 0 },
                    backgroundColor = MaterialTheme.colorScheme.background,
                )
                .then(if (areOptionsBelow) Modifier else Modifier.fadingUnderStartOverlay(scrolledFromStart = ::pastStart, overlayWidth = 0.dp)),
            pageSpacing = PAGE_MARGIN,
            userScrollEnabled = pageView().zoom == 1f,
        ) { index ->
            val isShownPage = index == pagerState.currentPage
            val pageLabel = stringResource(Res.string.print_page, index + 1, pageCount)
            // Keyed by the generation, not by the document, whose equality would compare every text on every page.
            AnimatedContent(
                targetState = laidOut,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it.generation },
            ) { faded ->
                val document = faded.document
                val shownPage = document.pages.getOrNull(index) ?: return@AnimatedContent
                PrintPageCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isShownPage) {
                                Modifier
                                    .onSizeChanged { viewportSize = it.toSize() }
                                    .trackPointer { pointerPosition = it }
                                    .transformable(transformableState, canPan = { pageView().zoom > 1f })
                                    .doubleTapZoom(
                                        onDoubleTap = { animateZoomTo(if (pageView().zoom > 1f) 1f else DOUBLE_TAP_ZOOM, it) },
                                        onQuickZoom = ::zoomBy,
                                    )
                                    .wheelZoom { notches, position ->
                                        zoomBy(WHEEL_ZOOM_BASE.pow(-notches), position)
                                    }
                            } else {
                                Modifier
                            },
                        ),
                    document = document,
                    page = shownPage,
                    renderer = renderer,
                    fitBottom = fitBottom,
                    pageView = if (isShownPage) pageView() else PageView(),
                    description = pageLabel,
                )
            }
        }
        LayoutIndicator(isVisible = !isCurrent)
    }
}

/** Reports where the pointer is over the element while it hovers or presses, and null once it has left. */
private fun Modifier.trackPointer(onPosition: (Offset?) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            onPosition(if (event.type == PointerEventType.Exit) null else event.changes.first().position)
        }
    }
}

/**
 * A double tap, which toggles the zoom around where it landed, and on a touch screen the second tap held and dragged,
 * which zooms by as much as the finger travels - down to zoom in, up to zoom out, the way the maps and photo viewers of
 * both phone platforms do - for zooming with the one thumb that holds the phone. A second tap that lifts without having
 * moved past the touch slop is the ordinary double tap. The second press is consumed from its first event, since it
 * belongs to this gesture whichever it turns out to be: left alone, the pager would turn the page under a drag that
 * started sideways and the pan would move a zoomed page under it, and a second finger joining in hands the pinch over
 * to `transformable`, which takes it from there.
 */
private fun Modifier.doubleTapZoom(
    onDoubleTap: (Offset) -> Unit,
    onQuickZoom: (factor: Float, pivot: Offset) -> Unit,
) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown()
        // A press that turned into a drag of the pager or of a zoomed page is consumed by them, which ends it here.
        waitForUpOrCancellation() ?: return@awaitEachGesture
        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() } ?: return@awaitEachGesture
        second.consume()
        val canDrag = second.type == PointerType.Touch || second.type == PointerType.Stylus
        val doublingDistance = QUICK_ZOOM_DOUBLING_DISTANCE.toPx()
        var isDragging = false
        var previousY = second.position.y
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull { it.id == second.id } ?: return@awaitEachGesture
            if (!change.pressed) {
                change.consume()
                if (!isDragging) onDoubleTap(second.position)
                return@awaitEachGesture
            }
            if (canDrag && !isDragging && (change.position - second.position).getDistance() > viewConfiguration.touchSlop) {
                isDragging = true
                // From where the slop was crossed rather than from the press, so the zoom does not jump by the slop.
                previousY = change.position.y
            }
            if (isDragging) {
                onQuickZoom(2f.pow((change.position.y - previousY) / doublingDistance), second.position)
                previousY = change.position.y
            }
            change.consume()
        }
    }
}

/**
 * Ctrl or Cmd and the scroll wheel, in the desktop application only: in a browser that chord is the page's own zoom,
 * which the app leaves alone. [isLaunchScreenWholeStartup] is the one platform flag that is true there and nowhere else.
 */
private fun Modifier.wheelZoom(onZoom: (notches: Float, position: Offset) -> Unit) = if (!isLaunchScreenWholeStartup) {
    this
} else {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val isZoomChord = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                if (event.type == PointerEventType.Scroll && isZoomChord) {
                    onZoom(event.verticalWheelNotches(), event.changes.first().position)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/** Shown over a page that stands in for the next one, once the layout of that has taken a moment (it fades in on its own). */
@Composable
private fun LayoutIndicator(isVisible: Boolean) = AnimatedVisibility(isVisible, enter = EnterTransition.None, exit = fadeOut()) {
    DelayedLoadingIndicator()
}

private const val MAX_ZOOM = 4f

private const val DOUBLE_TAP_ZOOM = 2.5f

/** How far the finger of a double tap held and dragged travels to double the zoom, or to halve it going the other way. */
private val QUICK_ZOOM_DOUBLING_DISTANCE = 120.dp

/** The zoom of one notch of the scroll wheel, towards the user zooming out. */
private const val WHEEL_ZOOM_BASE = 1.15f
