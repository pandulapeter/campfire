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
import kotlinx.coroutines.channels.Channel

/**
 * One session of an output: the sequencer, the mixer and the way changes reach them.
 *
 * The output pulls from it on a thread of its own, while the engine pushes changes and reads the ticks back from
 * another, so everything that crosses goes through a channel, whose non-suspending ends are safe from any thread:
 * changes are queued by [update] and [preview] and taken at the start of the next pull, and every tick handed to the
 * output is queued in [renderedTicks] for the engine to release once it is heard.
 *
 * @param pattern Null for a session that only plays previews.
 */
internal class ClickStream(
    val sampleRate: Int,
    pattern: MetronomePattern?,
) {
    private val sequencer = pattern?.let { MetronomeSequencer(sampleRate, it) }
    private val mixer = ClickMixer(sampleRate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    val renderedTicks = Channel<MetronomeSequencer.Tick>(Channel.UNLIMITED)
    private val addToMixer = mixer::add

    /** See [MetronomeSequencer.update]. */
    fun update(pattern: MetronomePattern, barChangeId: Int?) {
        commands.trySend(Command.Update(pattern, barChangeId))
    }

    fun applyPendingBarChangeNow() {
        commands.trySend(Command.ApplyPendingBarChangeNow)
    }

    fun preview(sound: MetronomeSound, level: BeatLevel) {
        commands.trySend(Command.Preview(sound, ClickVoice.of(level)))
    }

    /** The samples of a voice, for an output that plays clicks rather than a mixed stream. */
    fun samplesOf(sound: MetronomeSound, voice: ClickVoice) = mixer.samplesOf(sound, voice)

    /** Mixes the next [frames] frames into [buffer], for an output that is fed PCM. */
    fun renderPcm(buffer: ShortArray, frames: Int) {
        val start = mixer.position
        schedule(nowFrame = start, untilFrame = start + frames, onClick = addToMixer)
        mixer.render(buffer, frames)
    }

    /**
     * Hands every click due before [untilFrame] to [onClick], for an output that schedules clicks on a clock of its own
     * (and for the silent one, which only needs the ticks); a preview is due at [nowFrame].
     */
    fun schedule(nowFrame: Long, untilFrame: Long, onClick: (frame: Long, sound: MetronomeSound, voice: ClickVoice, gain: Float) -> Unit) {
        while (true) {
            when (val command = commands.tryReceive().getOrNull() ?: break) {
                is Command.Update -> sequencer?.update(command.pattern, command.barChangeId)
                Command.ApplyPendingBarChangeNow -> sequencer?.applyPendingBarChangeNow()
                is Command.Preview -> onClick(nowFrame, command.sound, command.voice, 1f)
            }
        }
        // The volume is squared on its way to a gain, since the ear hears the square far closer to a straight line than
        // the amplitude itself: a slider at half is then about half as loud rather than barely quieter.
        sequencer?.ticksUntil(untilFrame)?.forEach { tick ->
            tick.voice?.let { voice -> onClick(tick.frame, tick.sound, voice, tick.volume * tick.volume) }
            renderedTicks.trySend(tick)
        }
    }

    private sealed interface Command {

        data class Update(
            val pattern: MetronomePattern,
            val barChangeId: Int?,
        ) : Command

        data object ApplyPendingBarChangeNow : Command

        data class Preview(
            val sound: MetronomeSound,
            val voice: ClickVoice,
        ) : Command
    }
}
