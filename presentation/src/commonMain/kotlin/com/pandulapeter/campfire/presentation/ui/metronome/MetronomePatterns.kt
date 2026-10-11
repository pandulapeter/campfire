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
import com.pandulapeter.campfire.presentation.ui.playing.Tempos
import com.pandulapeter.campfire.presentation.ui.playing.effectiveTempo

/**
 * The complete pattern the click plays for [context]: a song's tempo and time signature (4/4 where it names none
 * that reads as one), those of the stretch of it the page is on where it changes them further down
 * ([MetronomeContext.Song.timing]), or the Metronome tab's own, a song's own accents from [songBeatLevels]
 * (`UserPreferences.beatLevels`), and everything else about how it sounds from [settings].
 */
internal fun metronomePatternOf(
    context: MetronomeContext,
    settings: MetronomeSettings,
    songOf: (String) -> Song?,
    tempos: Tempos,
    songBeatLevels: Map<String, Map<String, List<String>>>,
): MetronomePattern {
    val (bpm, timeSignature) = when (context) {
        MetronomeContext.Standalone -> settings.bpm to settings.timeSignatureOrDefault
        // A stretch after a change has the tempo its page shows, which already scales with an override of the opening
        // one, and the time signature it names; one that names no tempo is played at the song's own.
        is MetronomeContext.Song -> songOf(context.songFileName).let { song ->
            (context.timing?.bpm ?: effectiveTempo(song = song, setlistFileName = context.setlistFileName, tempos = tempos, songFileName = context.songFileName).bpm) to
                (context.timing?.timeSignature ?: song.timeSignatureOrDefault)
        }
    }
    return MetronomePattern(
        bpm = MetronomePattern.coerceBpm(bpm),
        timeSignature = timeSignature,
        beatLevels = settings.beatLevelsOf(timeSignature, songBeatLevels = (context as? MetronomeContext.Song)?.let { songBeatLevels[it.songFileName] }),
        subdivision = settings.subdivision,
        sound = settings.sound,
        volume = settings.volume,
    )
}

internal val MetronomeSettings.timeSignatureOrDefault get() = TimeSignature.parse(timeSignature) ?: TimeSignature.COMMON_TIME

internal val MetronomeSettings.sound get() = MetronomeSound.fromId(soundId) ?: MetronomeSound.CLICK

internal val MetronomeSettings.subdivision get() = Subdivision.fromId(subdivisionId) ?: Subdivision.NONE

/**
 * The accents of [timeSignature]: a song's own ([songBeatLevels], by signature), then the ones drawn for the signature
 * anywhere, then its defaults, each skipped where it has none of the right length.
 */
internal fun MetronomeSettings.beatLevelsOf(timeSignature: TimeSignature, songBeatLevels: Map<String, List<String>>? = null) =
    (songBeatLevels.levelsOf(timeSignature) ?: beatLevels.levelsOf(timeSignature))
        ?.map { BeatLevel.fromId(it) ?: BeatLevel.NORMAL }
        ?: timeSignature.defaultBeatLevels()

private fun Map<String, List<String>>?.levelsOf(timeSignature: TimeSignature) = this?.get(timeSignature.toString())?.takeIf { it.size == timeSignature.beats }

/** These settings with the accents of [timeSignature] set to [levels]. */
internal fun MetronomeSettings.withBeatLevels(timeSignature: TimeSignature, levels: List<BeatLevel>) =
    copy(beatLevels = beatLevels + (timeSignature.toString() to levels.map { it.id }))

/**
 * The songs' accents (`UserPreferences.beatLevels`) with those of [songFileName] in [timeSignature] set to [levels], or
 * taken off where they are the ones [settings] would give it anyway: a song accented as every other song in its
 * signature has nothing of its own, so it goes on following the signature's accents when they are tapped differently
 * on the Metronome tab, and a song left with no signature of its own leaves the map.
 */
internal fun Map<String, Map<String, List<String>>>.withSongBeatLevels(
    songFileName: String,
    timeSignature: TimeSignature,
    levels: List<BeatLevel>,
    settings: MetronomeSettings,
): Map<String, Map<String, List<String>>> {
    val signature = timeSignature.toString()
    val songLevels = this[songFileName].orEmpty().let { songLevels ->
        if (levels == settings.beatLevelsOf(timeSignature)) songLevels - signature else songLevels + (signature to levels.map { it.id })
    }
    return if (songLevels.isEmpty()) this - songFileName else this + (songFileName to songLevels)
}

internal val Song?.timeSignatureOrDefault
    get() = this?.time?.let(ChordProTime::parse)?.let { (beats, unit) -> TimeSignature(beats, unit) } ?: TimeSignature.COMMON_TIME
