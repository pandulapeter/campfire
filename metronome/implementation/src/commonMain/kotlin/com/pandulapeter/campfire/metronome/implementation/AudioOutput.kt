/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.implementation

import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason

/**
 * The platform's sound output, one annotated class per platform source set. It owns everything about the platform's
 * audio a click needs - audio focus on Android, the audio session on iOS, the context on the web - so that a click can
 * never start without them and the engine learns about losing them whether or not any UI is around.
 *
 * Three platforms are fed PCM that [ClickStream.renderPcm] mixes, from a thread of the output's own with about
 * [QUEUED_SECONDS] queued ahead; the web schedules each click on its audio clock instead ([ClickStream.schedule]).
 * Either way the stream decides where the clicks fall and the output only plays them.
 */
internal interface AudioOutput {

    /**
     * Takes the audio and starts pulling from the stream [createStream] builds for the output's sample rate. Returns
     * at once and never throws.
     *
     * @param isPreview Whether this is only a sound chip being tried, which takes the audio for a moment rather than
     *   for playback where the platform tells the two apart.
     */
    fun start(
        isPreview: Boolean,
        createStream: (sampleRate: Int) -> ClickStream,
        listener: AudioOutputListener,
    ): AudioOutputStart

    /** The frame of the stream being heard now, the route's latency included where the platform reports it. */
    fun heardFrame(): Long

    /** Stops at once, dropping what is queued, and gives the audio back. Does nothing when not started. */
    fun stop()

    /**
     * Whether to listen for the presses that allow a page's audio to start, which is what `Metronome.setStartable` says.
     * Only the web has such a rule, so every other output ignores it.
     */
    fun setGestureListening(isEnabled: Boolean) = Unit

    companion object {
        const val CHUNK_SECONDS = 0.02
        const val QUEUED_SECONDS = 0.1
    }
}

/** What an output tells the engine after it started. Called from any thread. */
internal interface AudioOutputListener {

    /** The audio was taken away or the output went; the click is to stop. */
    fun onLost(reason: MetronomeStopReason)

    /** Something that keeps a running click from being heard started or stopped being the case. */
    fun onAudioIssueChanged(issue: MetronomeAudioIssue?)
}

internal sealed interface AudioOutputStart {

    data class Started(val audioIssue: MetronomeAudioIssue? = null) : AudioOutputStart

    /** The audio could not be taken, so nothing is to play. */
    data class Refused(val reason: MetronomeStopReason) : AudioOutputStart

    /** There is no output to open; the click runs silently instead. */
    data object Unavailable : AudioOutputStart
}
