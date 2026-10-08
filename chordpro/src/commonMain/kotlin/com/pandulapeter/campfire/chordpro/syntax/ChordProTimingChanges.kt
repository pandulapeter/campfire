/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.TEMPO
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.TIME

/**
 * The tempo and the time signature a song is played in from where the parser stands, which a `{tempo}` or a
 * `{time}` in the body changes (see [ChordProBlock.Timing]). Not every one does: an empty or unreadable value says
 * nothing, one that restates the value in force (`{time: C}` after `4/4`) changes nothing, and the line the song
 * takes its own value from — the first readable one of a song whose header has no line of that field — is no change
 * but the song's opening value, which is how a file that writes its metadata at the bottom is read.
 */
internal class TimingChanges {
    private var tempo: String? = null
    private var time: String? = null

    /** What was in force before the latest group of changes with no line of the song between them, see [addTiming]. */
    var beforeGroup: ChordProBlock.Timing? = null

    /** The values in force now, which a change is compared with. */
    fun inForce(metadata: MetadataBuilder) = ChordProBlock.Timing(
        tempo = tempo ?: metadata.tempo.value?.takeIf { ChordProTempo.parse(it) != null },
        time = time ?: metadata.time.value?.takeIf { ChordProTime.parse(it) != null },
    )

    /** Whether [first] and [second] play the same, so that `{time: C}` is `4/4`. */
    fun isSame(first: ChordProBlock.Timing, second: ChordProBlock.Timing) =
        ChordProTempo.parse(first.tempo) == ChordProTempo.parse(second.tempo) && ChordProTime.parse(first.time) == ChordProTime.parse(second.time)

    /** Puts back what [inForce] said before a group that turned out to change nothing. */
    fun restore(timing: ChordProBlock.Timing) {
        tempo = timing.tempo
        time = timing.time
    }

    /** The change [directive] makes, or null where it makes none; called before [metadata] reads it. */
    fun consume(directive: ChordProDirectives.Directive, metadata: MetadataBuilder): ChordProBlock.Timing? {
        val standard = ChordProMetaItems.standardMeta(directive) ?: directive
        val written = standard.value?.trim().orEmpty()
        val (inForceTempo, inForceTime) = inForce(metadata)
        when (standard.name) {
            TEMPO -> {
                val bpm = ChordProTempo.parse(written) ?: return null
                if (metadata.tempo.takes(written, isInBody = true) || bpm == ChordProTempo.parse(inForceTempo)) return null
                tempo = written
            }

            TIME -> {
                val signature = ChordProTime.parse(written) ?: return null
                if (metadata.time.takes(written, isInBody = true) || signature == ChordProTime.parse(inForceTime)) return null
                time = written
            }

            else -> return null
        }
        return ChordProBlock.Timing(tempo = tempo ?: inForceTempo, time = time ?: inForceTime)
    }
}
