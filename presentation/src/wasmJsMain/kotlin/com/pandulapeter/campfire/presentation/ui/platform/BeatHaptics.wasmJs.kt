/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import kotlin.js.ExperimentalWasmJsInterop

// A browser only vibrates while its page is the one showing.
internal actual val areBeatHapticsFeltInBackground = false

@Composable
internal actual fun rememberBeatHaptics(): BeatHaptics? = webBeatHaptics

/**
 * The Vibration API, where the page is on a phone or a tablet that has it: Chrome and its relatives on Android. Safari
 * has none, and a desktop browser defines `navigator.vibrate` with nothing to vibrate, which is why the primary pointer
 * has to be a finger too - a switch that is offered and does nothing is worse than none.
 */
private val webBeatHaptics = if (canVibrate()) BeatHaptics { isAccent -> vibrate(if (isAccent) ACCENT_PULSE_MILLIS else BEAT_PULSE_MILLIS) } else null

private fun canVibrate(): Boolean = js("typeof navigator.vibrate === 'function' && window.matchMedia('(pointer: coarse)').matches")

private fun vibrate(millis: Int): Boolean = js("navigator.vibrate(millis)")

// Longer than the predefined clicks the Android app plays, since a page has plain pulses only, and many phones' motors
// do not get going in less than about twenty milliseconds.
private const val BEAT_PULSE_MILLIS = 20
private const val ACCENT_PULSE_MILLIS = 40
