/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher

/** A tap on the hand for every beat of the metronome, heavier on an accent. */
internal fun interface BeatHaptics {
    fun onBeat(isAccent: Boolean)
}

/**
 * The device's beat haptics, or null where there are none - no vibrator, a desktop, or a browser that has no Vibration
 * API or is not on a phone or a tablet - which is also what decides whether the Metronome tab offers them at all. Used
 * while the app is in front everywhere, and out of sight too where [areBeatHapticsFeltInBackground].
 */
@Composable
internal expect fun rememberBeatHaptics(): BeatHaptics?

/**
 * Whether a beat tapped while the app is out of sight still reaches the hand. On Android it does: a playing click keeps
 * the metronome's foreground service up, and the system lets a process with one vibrate, so a click felt in a pocket with
 * the volume at zero is a click worth keeping. iOS plays no haptics for an app in the background at all.
 */
internal expect val areBeatHapticsFeltInBackground: Boolean

/**
 * Where a beat is handed to [BeatHaptics]: off the main thread wherever the platform allows it, since a beat that waits
 * there for a frame being composed - a page of a song swiped to, the very moment a click changes tempo - is felt that
 * much after it is heard.
 */
internal expect val beatHapticsDispatcher: CoroutineDispatcher
