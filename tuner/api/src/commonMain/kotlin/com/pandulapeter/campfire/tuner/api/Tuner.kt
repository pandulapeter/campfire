/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api

import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerState
import kotlinx.coroutines.flow.StateFlow

/**
 * The tuner: one per app, hearing one note at a time through the microphone and playing reference tones. Nothing it
 * hears is kept or leaves the device.
 *
 * An implementation never asks for the microphone permission itself: [listen] opens the input only where the platform
 * already allows it, and says [TunerListening.Stopped] with the reason where it does not, so that the one place a
 * permission is ever asked for is the button the user taps. The exception is a platform whose only way of asking is
 * opening the input (the web, the desktop), where [listen] is that question.
 *
 * Every function may be called from any thread, returns at once and never throws.
 */
public interface Tuner {

    /**
     * What the tuner hears and plays. A reading is never published while a tone sounds, or for a moment after it stops,
     * since the speaker is what it would hear.
     */
    public val state: StateFlow<TunerState>

    /** Opens the microphone and reads what it hears against [config]. Listening already is [update]. */
    public fun listen(config: TunerConfig)

    /** Reads against [config] from the next window on; ignored while not listening. */
    public fun update(config: TunerConfig)

    /** Closes the microphone, which is what puts the system's recording indicator out. */
    public fun stopListening()

    /** Plays [note] (a MIDI note number) at [referencePitch] until [stopTone], replacing a tone that already sounds. */
    public fun playTone(note: Int, referencePitch: Int)

    public fun stopTone()

    /**
     * Whether a screen from which the tuner can be used is showing. Only the web uses it: a page may only start its
     * audio inside a user gesture, so the tuner listens for presses while this is true. Elsewhere it does nothing.
     */
    public fun setStartable(isStartable: Boolean)
}
