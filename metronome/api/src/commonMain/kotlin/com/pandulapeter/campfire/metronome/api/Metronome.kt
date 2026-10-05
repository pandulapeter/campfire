/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api

import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The click: one per app, owned by the app rather than by the screen that started it, so that it carries on while the
 * user moves around, locks the phone or (on Android) lets the activity go.
 *
 * An implementation places every click by its position in the output's sample stream, never by a timer, so the tempo
 * is exactly as steady as the sound card's clock. Where no output can be opened it keeps the same clock running
 * silently against the monotonic time source and says so in [MetronomePlayback.Playing.audioIssue], so that the
 * visual beat has one source either way.
 *
 * Every function may be called from any thread, returns at once and never throws: a start that cannot happen ends in
 * [MetronomePlayback.Stopped] with the reason, rather than in an exception.
 */
interface Metronome {

    /**
     * Whether the click plays, with what and on whose behalf. [MetronomePlayback.Stopped.reason] says why a click
     * ended (or did not start) on its own - focus taken by another app, a call, headphones pulled - and is null after
     * [stop].
     */
    val playback: StateFlow<MetronomePlayback>

    /**
     * One item per click, emitted when that click is heard rather than when it is rendered: the output's reported
     * playback position (latency included where the platform reports it) decides, so a flash driven by this flow
     * agrees with the ear. Carries the subdivisions too, flagged as such. No replay: a collector that starts late has
     * missed the beats before it.
     */
    val beats: SharedFlow<MetronomeBeat>

    /** Starts the click with [pattern] on its first beat. Starting while it already plays is [update] with the bar restarted. */
    fun start(pattern: MetronomePattern)

    /**
     * Replaces the pattern of a playing click; ignored when it is stopped. What decides the timing - the tempo, the
     * time signature and the subdivision - changes on the next beat, which [restartBar] makes beat one of a new bar;
     * the sound, the volume and the accents change from the next click.
     */
    fun update(pattern: MetronomePattern, restartBar: Boolean)

    /**
     * Plays the one click [sound] makes at [level], for choosing a sound: mixed into a playing click, or on its own
     * when stopped. Changes nothing about [playback].
     */
    fun preview(sound: MetronomeSound, level: BeatLevel)

    /** Stops the click at once, dropping whatever was queued for the output rather than letting it play out. */
    fun stop()
}
