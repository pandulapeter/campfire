/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongPlayingTools
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOf
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo

/**
 * What the song says about itself is the first section of its own grid, so it shares the rows, columns, stepping and
 * motion of the lyrics rather than taking their full width away above them: the card of what the song is, where it is
 * shown there, and under it how it is played.
 *
 * @param shouldShowChords False with the chords switched off, which leaves out the key and the capo along with the
 * chords: they are what is played, and say nothing to somebody who is only singing.
 * @param shouldShowCapo False on the keyboard, which reads the capo as a transposition the key already counts in, so
 * that a capo said on the page would be counted twice.
 * @param shouldShowTempo False with the metronome switched off, which leaves out the tempo and the time signature: they
 * are what the click plays, and say nothing without one.
 * @param isSongInfoShown Whether the card of what the song is belongs to the section: it does in the editor's preview,
 * which shows everything being typed, and not on the song details screen, whose app bar opens it as a sheet instead.
 * @param isSongInfoEditable Whether that card has edit buttons, which puts it there for a song that says nothing about
 * itself yet too: it is where its first tag or link is added.
 * @param hasPlayingControls Whether the four playing values are drawn as the controls that set them, which is what
 * the song details screen hands down outside read only mode. The section is then there for every song, since a capo
 * and a tempo can be set on one that names neither.
 * @param readsDefaults Whether the line of text read only mode draws in their place always names the capo, the tempo and
 * the time signature, a capo of none and the tempo and the time the click starts at where the file names none: a player
 * reading from a music stand has no control left to look at to tell that nothing was set, and the absence of a value is
 * a value to the hands on the guitar. The section is then there for every song too.
 * @param hasTuner Whether the section holds the tuner's button, see [SongPlayingTools]. It is never there on its own:
 * the section that holds it is the one the values above put there.
 * @param hasMetronome Whether the section holds the metronome's button, the same way.
 */
internal fun withMetadataSection(
    sections: List<RenderSection>,
    metadata: ChordProMetadata,
    shouldShowChords: Boolean,
    shouldShowCapo: Boolean = true,
    shouldShowTempo: Boolean = true,
    isSongInfoShown: Boolean,
    isSongInfoEditable: Boolean = false,
    hasPlayingControls: Boolean = false,
    readsDefaults: Boolean = false,
    hasTuner: Boolean = false,
    hasMetronome: Boolean = false,
): List<RenderSection> {
    val hasControls = (shouldShowChords || shouldShowTempo) && hasPlayingControls
    val readsBoth = (shouldShowChords || shouldShowTempo) && !hasControls && readsDefaults
    // Read the way the click and the steppers read them, so that the line says what is played: a number of beats a
    // minute within the click's range, a time signature it can count ("C" being 4/4) and a capo on the neck.
    val shownMetadata = metadata.copy(
        key = metadata.key.takeIf { shouldShowChords },
        capo = when {
            !shouldShowChords || !shouldShowCapo -> null
            readsBoth -> (metadata.capo ?: 0).coerceIn(Song.CAPO_RANGE)
            else -> metadata.capo?.coerceIn(Song.CAPO_RANGE)
        },
        tempo = when {
            !shouldShowTempo -> null
            else -> (ChordProTempo.parse(metadata.tempo)?.let(MetronomePattern::coerceBpm) ?: MetronomePattern.DEFAULT_BPM.takeIf { readsBoth })?.toString()
        },
        time = when {
            !shouldShowTempo -> null
            else -> timeSignatureOf(metadata.time)?.toString()
                ?: TimeSignature.COMMON_TIME.toString().takeIf { readsBoth }
        },
    )
    return if ((isSongInfoShown && (isSongInfoEditable || shownMetadata.hasSongInfo)) || hasControls || readsBoth || shownMetadata.hasPlayingValues) {
        val section = RenderSection.Metadata(
            metadata = shownMetadata,
            hasPlayingControls = hasControls,
            readsDefaults = readsBoth,
            hasTuner = hasTuner,
            hasMetronome = hasMetronome,
        )
        listOf(section) + sections
    } else {
        sections
    }
}

private val ChordProMetadata.hasPlayingValues
    get() = !key.isNullOrBlank() || (capo ?: 0) != 0 || !tempo.isNullOrBlank() || !time.isNullOrBlank()
