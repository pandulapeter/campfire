/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect

/**
 * The same surface color for every sheet and any Material container drawn inside it, including the calendar, and so
 * the color the edge fades of a sheet's scrolling content are painted in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun sheetContainerColor() = MaterialTheme.colorScheme.background.let { background ->
    // Dark sheets keep Material's lighter surface so they remain visible against the scrim.
    if (background.luminance() < 0.5f) BottomSheetDefaults.ContainerColor else background
}

/**
 * Every sheet of the app, drawn edge to edge: the sheet runs down under the navigation bar and the keyboard instead
 * of stopping above them, and only its content is kept clear of them. The keyboard's height pads the sheet's column
 * outside the content's scroll, so that a focused field is brought above the keyboard rather than into a viewport
 * running on under it. [ModalBottomSheet] pads the whole content by the bottom inset by default, which leaves a
 * scrolling list ending on a band of the sheet's color above the bar rather than scrolling on under it, so that inset
 * is left out of the sheet's own insets and handed to the content instead, as the `contentPadding` scrolling content
 * applies inside its scroll and the rest leaves under its last row.
 * The top inset stays with the sheet, which only pads by it once it has been dragged up against the status bar.
 * Horizontally, the whole sheet is centered between the safe edges, up to its maximum width. Side insets never
 * become padding inside a narrow sheet that is already clear of those edges.
 * Bottom padding excludes the insets the keyboard's padding already consumed, which leaves the navigation bar with the
 * keyboard down and nothing with it up, and is read during layout so content follows the current keyboard animation
 * frame.
 *
 * @param title What the sheet is about, named in its [SheetHeader].
 * @param subtitle What the sheet acts on, under [title]: the song or the setlist it was opened for, or the notes of the
 *   chord a chord's sheet is about. Left out when blank.
 * @param actions Buttons at the end of the [SheetHeader], across from the close button, such as the order of a list or
 *   the button that finishes what the sheet is for. The close they are handed is final: unlike the header's close
 *   button, a hide that follows an action is not taken back by a finger landing on the sliding sheet. The ones that
 *   write ([BottomSheetConfirmButton]) do nothing once the sheet has started closing ([LocalIsSheetClosing]).
 * @param onTopMoved Where the sheet's top edge is on the screen, on every frame it moves: for what is drawn behind the
 *   sheet and has to stay in the part of it the sheet leaves uncovered. Screen coordinates rather than the window's, since
 *   on Android the sheet is a window of its own.
 * @param onDismiss Has to dismiss this sheet's own dialog and nothing else (`CampfireViewModel.dismissSheet`): it is
 *   called from the end of a hide animation, by which time another dialog may have taken the sheet's place.
 * @param content Can close the sheet ([BottomSheetContentScope.close]), for a sheet with a button of its own that is
 *   done with it. That close is final the way the actions' is.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CampfireBottomSheet(
    title: String,
    subtitle: String = "",
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    actions: (@Composable RowScope.(close: () -> Unit) -> Unit)? = null,
    onTopMoved: ((topOnScreen: Float) -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable BottomSheetContentScope.(contentPadding: PaddingValues) -> Unit,
) {
    // Open at the content's full height: long lists can use the whole window, and short forms stay compact.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val scrollState = rememberScrollState()
    // Hiding the sheet by hand does not count as dismissing it, so the dialog state is cleared once it is gone: left
    // as it was, the invisible sheet's modal layer would stay over the screen, swallowing the next tap. The close
    // button's hide only counts when it ran to its end: one cut short by a finger taking hold of the sheet leaves the
    // sheet where Material settles it, with the draft still in it, and one cut short by another dialog replacing the
    // sheet has nothing left to dismiss.
    // Set as the tap is handled, so the second tap of the same frame already reads it; Material's own hide (a swipe,
    // the scrim) shows up as the sheet's target becoming Hidden while it is still on screen.
    var isCloseRequested by remember { mutableStateOf(false) }
    // Set by a close that follows something the sheet has already done - a Save, a Create, a Delete. Such a hide is
    // never taken back: the sheet's gestures are off for the slide, and a hide cut short anyway is finished from where
    // it is, since a sheet left up after its action would offer an action that has already happened (a dead Create over
    // the editor).
    var isCloseFinal by remember { mutableStateOf(false) }
    val isClosing = remember(sheetState) {
        { isCloseRequested || (sheetState.targetValue == SheetValue.Hidden && sheetState.currentValue != SheetValue.Hidden) }
    }
    val requestClose = { isFinal: Boolean ->
        if (!isCloseRequested) {
            isCloseRequested = true
            isCloseFinal = isFinal
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                when {
                    cause == null -> onDismiss()
                    // A finger that took hold of the sheet keeps it, and its actions with it.
                    !isCloseFinal -> isCloseRequested = false
                    else -> coroutineScope.launch {
                        // Cut short after its action: by a hide of Material's own (the scrim, back, Escape), which is
                        // let run to its end, or by a press that took hold of the sheet before the recomposition that
                        // turned its gestures off, from whose hold it is hidden again. A frame first, by which either
                        // has started. Calling hide while Material's own runs would cancel that one, and back's settle
                        // dismisses in one frame when it is cancelled.
                        while (sheetState.isVisible) {
                            withFrameNanos { }
                            if (sheetState.isAnimationRunning && sheetState.targetValue == SheetValue.Hidden) continue
                            try {
                                sheetState.hide()
                            } catch (exception: CancellationException) {
                                // Refused while a press still holds the sheet: tried again on the next frame.
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    }.invokeOnCompletion { onDismiss() }
                }
            }
        }
    }
    val cancel = { requestClose(false) }
    val close = { requestClose(true) }
    // Asked to close by the stack it is in rather than by its own controls, see DialogHost.dismissDialogAndSheetUnder.
    val isCloseRequestedFromOutside = LocalIsSheetCloseRequested.current
    LaunchedEffect(isCloseRequestedFromOutside) { if (isCloseRequestedFromOutside) close() }
    val saveShortcut = remember { SheetSaveShortcut() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Material applies sheetMaxWidth after this modifier. First reserve the safe horizontal span, then
        // center the capped surface inside it. Resolve insets in the modal window's composition, not the
        // activity behind it, and leave the inset gaps outside the sheet's background and gesture bounds.
        modifier = Modifier.composed {
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .wrapContentWidth()
        },
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        // Off for the slide of a final close, so that the second press of a double tap does not take hold of it.
        sheetGesturesEnabled = !isCloseFinal,
        containerColor = sheetContainerColor(),
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        // Derived, so that the sheet recomposes as the keyboard comes and goes rather than on every frame it slides.
        val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        // Handle back inside this modal window, before Material starts hiding the sheet. Keep the handler registered
        // even with the keyboard down so its priority stays below any nested sheet composed in the content below.
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = isKeyboardVisible,
            onBackCompleted = {
                keyboardController?.hide()
                // Release the focused text input too, so hiding the web keyboard also ends the editing session.
                focusManager.clearFocus(force = true)
            },
        )
        val isCompactKeyboard = windowHeight < SHORT_WINDOW_HEIGHT && isKeyboardVisible
        CompactKeyboardEffect(isEnabled = isCompactKeyboard)
        // A sheet that opens onto none of its fields would otherwise hold no focus, and a key only travels along the
        // focus path, so Ctrl / Cmd + S would go unheard until something in it was clicked. A bare focus target draws
        // nothing and brings no keyboard up. It is taken a frame in, and only where nothing in the sheet has the focus
        // by then: a form's first field asks for it from an effect of its own, and the order the two effects run in is
        // no promise of which one ends up with it.
        val sheetFocus = remember { FocusRequester() }
        val sheetFocusState = remember { SheetFocusState() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!sheetFocusState.hasFocus) sheetFocus.requestFocus()
        }
        // A keyboard in a short window leaves less height than the header and a field take together, so there the
        // header and the pinned controls scroll with the rest, above the keyboard, and bringing the caret into view can
        // move them out of its way. The content keeps the window's height inside that scroll, which is what bounds
        // the lists in it that would otherwise be measured against an infinite one.
        Column(
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { onTopMoved?.invoke(it.positionOnScreen().y) }
                .saveShortcut { saveShortcut.action?.invoke() }
                .onFocusChanged { sheetFocusState.hasFocus = it.hasFocus }
                .focusRequester(sheetFocus)
                .focusTarget()
                .then(
                    if (isCompactKeyboard) {
                        Modifier.heightIn(max = windowHeight).imePadding()
                            .fadingTopEdge(scrollState, sheetContainerColor())
                            .bounceVerticalScroll(scrollState)
                    } else {
                        // Outside the content's own scroll, so that its viewport ends at the keyboard and a field focused
                        // with Next is scrolled above it: padded inside the scroll, the viewport ran on under the keyboard,
                        // where the field already counted as visible.
                        Modifier.imePadding()
                    },
                ),
        ) {
            val consumedInsets = remember { MutableWindowInsets() }
            val heightAnimation = remember { SheetHeightAnimation() }
            val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            // Material pads the sheet by as much of the top inset as its offset has not taken up yet (an offset not decided
            // yet takes up none of it).
            val uncoveredTopInset = remember(sheetState, topInset, density) {
                derivedStateOf {
                    val offset = runCatching { sheetState.requireOffset() }.getOrDefault(0f)
                    with(density) { offset.coerceIn(0f, topInset.getTop(density).toFloat()).toDp() }
                }
            }
            Column(
                modifier = (if (isCompactKeyboard) Modifier.height(windowHeight) else Modifier)
                    .animateSheetContentHeight(heightAnimation) { uncoveredTopInset.value }
                    .onConsumedWindowInsetsChanged { consumedInsets.insets = it },
            ) {
                CompositionLocalProvider(
                    LocalIsSheetClosing provides isClosing,
                    // A sheet nested inside this one's content is not the dialog the request was for.
                    LocalIsSheetCloseRequested provides false,
                    LocalSheetSaveShortcut provides saveShortcut,
                    LocalSheetPendingGrowth provides heightAnimation::pendingGrowth,
                ) {
                    SheetHeader(
                        title = title,
                        subtitle = subtitle,
                        actions = actions,
                        onClose = cancel,
                        onActionDone = close,
                    )
                    // Read inside the sheet, which is a window of its own on Android and gets the insets of that window.
                    // asPaddingValues alone ignores consumption. The column above already pads above the IME, which
                    // also covers the navigation bar; reserve only the bottom space still left to this content.
                    val bottomPadding = WindowInsets.safeDrawing.exclude(consumedInsets)
                        .only(WindowInsetsSides.Bottom).asPaddingValues()
                    BottomSheetContentScope(
                        columnScope = this,
                        close = close,
                        uncoveredTopInset = { uncoveredTopInset.value },
                    ).content(bottomPadding.only(bottom = true, extraBottom = SHEET_BOTTOM_PADDING))
                }
            }
        }
    }
}

private val SHEET_BOTTOM_PADDING = 16.dp
