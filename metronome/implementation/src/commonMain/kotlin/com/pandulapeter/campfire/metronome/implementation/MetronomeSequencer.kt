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

import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound

/**
 * Where in the output's sample stream every click falls, from frame 0 on.
 *
 * A tick's frame is computed from the frame the current timing started at and the number of ticks since, with one
 * integer division, never by adding a rounded interval to the previous tick: a rounded interval is off by up to half a
 * frame every click, which over a long practice session is an audible drift, while this is never more than one frame
 * off however long it runs.
 *
 * A pattern change replaces the pattern of every tick not yet handed out. What sounds - the levels, the sound, the
 * volume, mute - changes from the next tick; what places the ticks - the tempo, the signature, the subdivision - from
 * the next beat, so that a subdivided beat is never cut in two different lengths, and the timing restarts there,
 * counted from that beat's frame as the old timing placed it.
 */
internal class MetronomeSequencer(
    private val sampleRate: Int,
    pattern: MetronomePattern,
) {
    private var pattern = pattern
    private var timing = pattern
    private var pendingTiming: MetronomePattern? = null
    private var isBarRestartPending = false
    private var anchorFrame = 0L
    private var ticksSinceAnchor = 0L
    private var beatIndex = 0
    private var subdivisionIndex = 0
    private var barIndex = 0L

    fun update(pattern: MetronomePattern, restartBar: Boolean) {
        this.pattern = pattern
        pendingTiming = pattern
        isBarRestartPending = isBarRestartPending || restartBar
    }

    /** Hands out the ticks before [frame] that have not been handed out yet, in order. */
    fun ticksUntil(frame: Long): List<Tick> {
        val ticks = mutableListOf<Tick>()
        while (true) {
            if (subdivisionIndex == 0) applyPendingTiming()
            val tickFrame = nextTickFrame()
            if (tickFrame >= frame) break
            ticks += Tick(
                frame = tickFrame,
                beatIndex = beatIndex,
                barIndex = barIndex,
                level = if (pattern.timeSignature == timing.timeSignature) pattern.beatLevel(beatIndex) else timing.beatLevel(beatIndex),
                isSubdivision = subdivisionIndex != 0,
                sound = pattern.sound,
                volume = pattern.volume,
                isMuted = pattern.isMuted,
            )
            ticksSinceAnchor++
            subdivisionIndex++
            if (subdivisionIndex >= timing.subdivision.clicksPerBeat) {
                subdivisionIndex = 0
                beatIndex++
                if (beatIndex >= timing.timeSignature.beats) {
                    beatIndex = 0
                    barIndex++
                }
            }
        }
        return ticks
    }

    private fun nextTickFrame() = anchorFrame + ticksSinceAnchor * sampleRate * SECONDS_PER_MINUTE / (timing.bpm * timing.subdivision.clicksPerBeat)

    private fun applyPendingTiming() {
        val pending = pendingTiming ?: return
        anchorFrame = nextTickFrame()
        ticksSinceAnchor = 0
        timing = pending
        pendingTiming = null
        if (isBarRestartPending) {
            beatIndex = 0
            barIndex = 0
            isBarRestartPending = false
        } else if (beatIndex >= timing.timeSignature.beats) {
            beatIndex = 0
            barIndex++
        }
    }

    /**
     * One click, with everything about how it sounds taken from the pattern of the moment it was handed out.
     *
     * @param level The level of its beat; a subdivision carries its beat's, so that a muted beat mutes them too.
     */
    data class Tick(
        val frame: Long,
        val beatIndex: Int,
        val barIndex: Long,
        val level: BeatLevel,
        val isSubdivision: Boolean,
        val sound: MetronomeSound,
        val volume: Float,
        val isMuted: Boolean,
    ) {
        /** The voice that sounds, or null for a click that only counts. */
        val voice
            get() = when {
                isMuted || level == BeatLevel.MUTED -> null
                isSubdivision -> ClickVoice.SUBDIVISION
                level == BeatLevel.ACCENT -> ClickVoice.ACCENT
                else -> ClickVoice.NORMAL
            }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
