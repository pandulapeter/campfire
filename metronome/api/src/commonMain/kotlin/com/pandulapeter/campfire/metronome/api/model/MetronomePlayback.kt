/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api.model

/** What the click is doing. */
sealed interface MetronomePlayback {

    /** @param reason Why the click stopped (or never started) on its own; null when it was stopped on purpose. */
    data class Stopped(val reason: MetronomeStopReason? = null) : MetronomePlayback

    /**
     * @param audioIssue Set where nothing can be heard although the click runs, the beats included: no output could
     *   be opened, or the browser has not let the page start its audio yet.
     */
    data class Playing(
        val pattern: MetronomePattern,
        val audioIssue: MetronomeAudioIssue? = null,
    ) : MetronomePlayback
}

/** Why a click ended without being asked to. */
enum class MetronomeStopReason {

    /** The audio could not be taken (on a phone, usually a call in progress), so the click did not start. */
    AUDIO_REFUSED,

    /** Another app took the audio, or a call or an alarm interrupted it. */
    AUDIO_INTERRUPTED,

    /** The headphones or the speaker it was playing through went away. */
    OUTPUT_DISCONNECTED,

    /** The output stopped working while it played. */
    OUTPUT_FAILED,
}

/** Why a running click cannot be heard. */
enum class MetronomeAudioIssue {

    /** There is no audio output to open, or it refused to open. */
    UNAVAILABLE,

    /** The browser keeps the page's audio suspended until the next tap or key press. */
    WAITING_FOR_GESTURE,
}
