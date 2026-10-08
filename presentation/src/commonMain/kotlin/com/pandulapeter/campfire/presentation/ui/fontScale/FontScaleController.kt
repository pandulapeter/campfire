/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.fontScale

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.FONT_SCALE_STEP
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The text size of the song details screen as it is being set - a pinch, a touchpad, the stepper, the zoom shortcuts -
 * and as it is saved once it has held still.
 *
 * @param backStack The app's back stack, which says whether the song details screen is the one being zoomed.
 * @param writeDelayMillis How long the size has to hold still before it is written to the preferences.
 */
internal class FontScaleController(
    private val scope: CoroutineScope,
    private val backStack: List<CampfireDestination>,
    private val dialogHost: DialogHost,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val writeDelayMillis: Long,
) {

    /**
     * Whether the song details screen is on top with nothing over it, which is where Ctrl / Cmd + plus, minus and zero
     * change the text size and where the web page keeps the browser from zooming itself instead. It is asked from the
     * desktop window and the web page rather than from a key handler on the screen for the reason `CampfireViewModel.openCurrentSearch`
     * is: the browser acts on a key pressed anywhere but the canvas before Compose hears of it, and a zoomed page is
     * not something Compose can undo. A dialog, a sheet or an overflow menu keeps the shortcuts from the screen under it.
     */
    val isSongTextZoomable
        get() = backStack.lastOrNull() is CampfireDestination.SongDetails && dialogHost.visibleDialog.value == null && !dialogHost.overlayState.isAnyMenuOpen

    /**
     * Answers the zoom shortcuts the way the browser answers them for a page: [steps] of [FONT_SCALE_STEP] in or out,
     * or back to [UserPreferences.DEFAULT_FONT_SCALE] for null. Answers whether it did, so that everywhere but the song
     * details screen the key is left to whoever else wants it.
     */
    fun zoomSongText(steps: Int?): Boolean {
        if (!isSongTextZoomable) return false
        if (steps == null) {
            setFontScale(UserPreferences.DEFAULT_FONT_SCALE)
            settleFontScale()
        } else {
            adjustFontScale(steps)
        }
        return true
    }

    /** Where the pinches [magnifyByTouchpad] is given add up, which each arrive as too small a step to be kept on their own. */
    private val touchpadFontScale = FontScaleAccumulator()

    private val _printPreviewMagnifications = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    /** The touchpad pinches [magnifyByTouchpad] hands the export screen's preview, which zooms its page by each ratio. */
    val printPreviewMagnifications = _printPreviewMagnifications.asSharedFlow()

    /**
     * Answers a pinch on a touchpad the way a touchscreen pinch is answered where it lands: on the song details screen
     * with the text size, [factor] being how much farther apart the fingers are than at the last report, damped by the
     * same [PINCH_SENSITIVITY], and on the export screen with the zoom of the page on preview, by [factor] itself as a
     * touchscreen pinch zooms it. Only a platform that tells a touchpad pinch apart from a scroll calls it - the macOS
     * desktop app, which reports the gesture itself, and Chrome, Edge and Firefox, which report a Ctrl + scroll the user
     * is not holding Ctrl for; Safari reports a gesture of its own that nothing listens for, so there a pinch zooms the
     * page - and it is asked from the window rather than from the screen, since none of these ever reach Compose as a
     * pinch. Neither screen answers one with anything drawn over it, and it answers whether one did, so that anywhere
     * else the gesture is left to whoever else wants it.
     */
    fun magnifyByTouchpad(factor: Float): Boolean {
        val isUsable = factor > 0f && factor.isFinite()
        return when {
            isSongTextZoomable -> {
                if (isUsable) setFontScale(touchpadFontScale.next(fontScale) { it * factor.pow(PINCH_SENSITIVITY) })
                true
            }
            dialogHost.visibleDialog.value is DialogType.Export && !dialogHost.overlayState.isAnyMenuOpen -> {
                if (isUsable) _printPreviewMagnifications.tryEmit(factor)
                true
            }
            else -> false
        }
    }

    /**
     * The text size multiplier of the song details screen. A pinch gesture changes it on every frame, so the latest
     * value is kept here and only written to the user preferences once the changes have settled.
     */
    private val liveFontScale = mutableFloatStateOf(UserPreferences.DEFAULT_FONT_SCALE)

    /**
     * Snapshot state rather than a flow, so that it is read where the text is laid out: a pinch changes it on every
     * frame, and a flow collected at the root of the screen would recompose the whole screen every time, a frame late.
     */
    val fontScale: Float get() = liveFontScale.floatValue

    /**
     * The value set on this device and not yet written to the preferences. For as long as there is one it wins over
     * the stored value; once it is saved, whatever the preferences hold wins again.
     */
    val fontScalePreference = DebouncedPreference<Float> { copy(fontScale = it) }

    private val settledFontScaleState = mutableFloatStateOf(UserPreferences.DEFAULT_FONT_SCALE)

    /**
     * [fontScale] once it has held still for a moment: what the songs that are not on screen are laid out at, so that a
     * pinch lays out the one song being read rather than the pages beside it as well. A step of the stepper or of a
     * shortcut is not a continuous change, so it reaches them at once.
     */
    val settledFontScale: Float get() = settledFontScaleState.floatValue

    /**
     * Kept in whole percent, which is all the stepper's label shows: a slow pinch moves less than that on most frames,
     * and an equal value is one the snapshot state ignores, so those frames lay nothing out again.
     */
    fun setFontScale(value: Float) {
        val clamped = (value.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE) * 100).roundToInt() / 100f
        if (clamped == liveFontScale.floatValue) return
        liveFontScale.floatValue = clamped
        fontScalePreference.set(clamped)
    }

    /**
     * Moves the font scale by the given number of [FONT_SCALE_STEP]s. A value set by a gesture is first snapped to the
     * grid of steps in the direction of the change, so that a single tap always lands on the next step (125% goes to
     * 120% or 130%, never past them).
     */
    fun adjustFontScale(steps: Int) {
        val currentSteps = liveFontScale.floatValue / FONT_SCALE_STEP
        val snappedSteps = if (steps > 0) floor(currentSteps + FONT_SCALE_STEP_TOLERANCE) else ceil(currentSteps - FONT_SCALE_STEP_TOLERANCE)
        setFontScale((snappedSteps + steps) * FONT_SCALE_STEP)
        settleFontScale()
    }

    fun settleFontScale() {
        settledFontScaleState.floatValue = liveFontScale.floatValue
    }

    /** Writes the size once it has held still, see [fontScalePreference]. */
    fun startWriter() = fontScalePreference.start(scope, writeDelayMillis, updateUserPreferences::invoke)

    /**
     * The first read at launch, a read again, a restore, a sync run: whatever wrote the preference wins whenever nothing
     * set here is still waiting to be saved. The echo of our own save equals the live value, which is why this starts
     * after [startWriter].
     */
    fun startEcho(userPreferences: Flow<UserPreferences?>) = scope.launch {
        userPreferences.filterNotNull().map { it.fontScale }.distinctUntilChanged().collect { stored ->
            if (fontScalePreference.pending.value == null) {
                liveFontScale.floatValue = stored
                settleFontScale()
            }
        }
    }

    /** Follows [fontScale] into [settledFontScale] once it has held still. */
    @OptIn(FlowPreview::class)
    fun startSettle() = scope.launch {
        snapshotFlow { fontScale }.debounce(FONT_SCALE_SETTLE_MILLIS).collect { settledFontScaleState.floatValue = it }
    }

    private companion object {
        const val FONT_SCALE_STEP_TOLERANCE = 0.01f // Floating point slack, so that 1.1000001 still counts as step 11.
        const val FONT_SCALE_SETTLE_MILLIS = 200L
    }
}
