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

import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern

/**
 * Where a song's tempo override is kept, folded into one lookup the way the transpositions are: a song opened from a
 * setlist reads the setlist's entry, one opened from the library reads the preferences, and neither ever reads the
 * other, so that a setlist reads the same on every device.
 */
internal data class Tempos(
    private val library: Map<String, Int> = emptyMap(),
    private val bySetlist: Map<String, Map<String, Int>> = emptyMap(),
) {

    operator fun get(songFileName: String, setlistFileName: String?): Int? = if (setlistFileName == null) {
        library[songFileName]
    } else {
        bySetlist[setlistFileName]?.get(songFileName)
    }

    /** These overrides with the one of [key] set to [bpm], or removed where that is null. */
    fun with(key: TempoKey, bpm: Int?) = if (key.setlistFileName == null) {
        copy(library = if (bpm == null) library - key.songFileName else library + (key.songFileName to bpm))
    } else {
        val entries = bySetlist[key.setlistFileName].orEmpty()
        copy(bySetlist = bySetlist + (key.setlistFileName to if (bpm == null) entries - key.songFileName else entries + (key.songFileName to bpm)))
    }
}

/** A song as it is opened: from the library, or from one setlist. */
internal data class TempoKey(
    val songFileName: String,
    val setlistFileName: String?,
)

/**
 * The tempo a song plays at where it is opened.
 *
 * @param songBpm The song's own: its file's, held within the metronome's range, or the default where it names none.
 *   What a reset goes back to.
 */
internal data class EffectiveTempo(
    val bpm: Int,
    val source: Source,
    val songBpm: Int,
) {
    /** Whether nothing overrides the song's own tempo, which is what the stepper is drawn plainly for. */
    val isDefault get() = source == Source.FILE || source == Source.DEFAULT

    enum class Source {
        SETLIST,
        LIBRARY_OVERRIDE,
        FILE,
        DEFAULT,
    }
}

/**
 * The setlist's entry, then the library's override (only for a song opened from the library), then the file's
 * `{tempo}`, then the default. An override equal to the song's own is no override, so that a `{tempo}` edited later
 * shows through.
 */
internal fun effectiveTempo(song: Song?, setlistFileName: String?, tempos: Tempos, songFileName: String = song?.fileName.orEmpty()): EffectiveTempo {
    val songBpm = song?.tempo?.let(MetronomePattern::coerceBpm) ?: MetronomePattern.DEFAULT_BPM
    val override = tempos[songFileName, setlistFileName]?.let(MetronomePattern::coerceBpm)?.takeIf { it != songBpm }
    return when {
        override != null -> EffectiveTempo(
            bpm = override,
            source = if (setlistFileName == null) EffectiveTempo.Source.LIBRARY_OVERRIDE else EffectiveTempo.Source.SETLIST,
            songBpm = songBpm,
        )
        song?.tempo != null -> EffectiveTempo(bpm = songBpm, source = EffectiveTempo.Source.FILE, songBpm = songBpm)
        else -> EffectiveTempo(bpm = songBpm, source = EffectiveTempo.Source.DEFAULT, songBpm = songBpm)
    }
}

/** The song with its tempo line reading [bpm], where there is one: what the page and the PDF show for an override. */
internal fun ChordProSong.withTempo(bpm: Int?) = if (bpm == null) this else copy(metadata = metadata.copy(tempo = bpm.toString()))
