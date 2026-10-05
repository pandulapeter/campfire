/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature

/**
 * The complete pattern the click plays for [context]: a song's tempo and time signature (4/4 where it names none
 * that reads as one), or the Metronome tab's own, and everything about how it sounds from [settings].
 */
internal fun metronomePatternOf(
    context: MetronomeContext,
    settings: MetronomeSettings,
    songOf: (String) -> Song?,
    tempos: Tempos,
): MetronomePattern {
    val (bpm, timeSignature) = when (context) {
        MetronomeContext.Standalone -> settings.bpm to settings.timeSignatureOrDefault
        is MetronomeContext.Song -> songOf(context.songFileName).let { song ->
            effectiveTempo(song = song, setlistFileName = context.setlistFileName, tempos = tempos, songFileName = context.songFileName).bpm to
                song.timeSignatureOrDefault
        }
    }
    return MetronomePattern(
        bpm = MetronomePattern.coerceBpm(bpm),
        timeSignature = timeSignature,
        beatLevels = settings.beatLevelsOf(timeSignature),
        subdivision = settings.subdivision,
        sound = settings.sound,
        volume = settings.volume,
    )
}

internal val MetronomeSettings.timeSignatureOrDefault get() = TimeSignature.parse(timeSignature) ?: TimeSignature.COMMON_TIME

internal val MetronomeSettings.sound get() = MetronomeSound.fromId(soundId) ?: MetronomeSound.CLICK

internal val MetronomeSettings.subdivision get() = Subdivision.fromId(subdivisionId) ?: Subdivision.NONE

/** The accents drawn for [timeSignature], or its defaults where none were, or none of the right length. */
internal fun MetronomeSettings.beatLevelsOf(timeSignature: TimeSignature) = beatLevels[timeSignature.toString()]
    ?.takeIf { it.size == timeSignature.beats }
    ?.map { BeatLevel.fromId(it) ?: BeatLevel.NORMAL }
    ?: timeSignature.defaultBeatLevels()

/** These settings with the accents of [timeSignature] set to [levels]. */
internal fun MetronomeSettings.withBeatLevels(timeSignature: TimeSignature, levels: List<BeatLevel>) =
    copy(beatLevels = beatLevels + (timeSignature.toString() to levels.map { it.id }))

internal val Song?.timeSignatureOrDefault
    get() = this?.time?.let(ChordProTime::parse)?.let { (beats, unit) -> TimeSignature(beats, unit) } ?: TimeSignature.COMMON_TIME
