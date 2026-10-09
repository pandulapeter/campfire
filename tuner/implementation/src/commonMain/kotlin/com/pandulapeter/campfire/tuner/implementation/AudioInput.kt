/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import com.pandulapeter.campfire.tuner.api.model.TunerInputIssue
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason

/**
 * The platform's microphone, one annotated class per platform source set. A pull of the latest window rather than a
 * stream of chunks, which is what the web has natively and what a ring buffer gives the other three. Every platform
 * turns its voice processing off, since gain control and noise suppression are written to remove exactly a held note.
 */
internal interface AudioInput {

    /** Opens the microphone. Suspends since the web's answer is a promise; never throws and never asks for a permission. */
    suspend fun start(listener: AudioInputListener): AudioInputStart

    /** Copies the latest `window.size` frames into [window], full scale being ±1, and answers whether that many have arrived yet. */
    fun latest(window: FloatArray): Boolean

    /** Closes the microphone. Does nothing when not started. */
    fun stop()

    /** Whether to listen for the presses that allow a page's audio to start, see `Tuner.setStartable`. Only the web has such a rule. */
    fun setGestureListening(isEnabled: Boolean) = Unit
}

/** What an input tells the engine after it started. Called from any thread. */
internal interface AudioInputListener {

    /** The input went away or was taken; listening is to stop. */
    fun onLost(reason: TunerStopReason)

    /** Something that keeps an open input from hearing anything started or stopped being the case. */
    fun onIssueChanged(issue: TunerInputIssue?)
}

internal sealed interface AudioInputStart {

    data class Started(val sampleRate: Int, val issue: TunerInputIssue? = null) : AudioInputStart

    data class Refused(val reason: TunerStopReason) : AudioInputStart
}
