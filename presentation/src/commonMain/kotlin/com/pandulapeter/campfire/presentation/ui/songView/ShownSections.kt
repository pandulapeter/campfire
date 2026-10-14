/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.InstrumentChipsContent
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import com.pandulapeter.campfire.presentation.ui.songView.model.chordCellsOf
import com.pandulapeter.campfire.presentation.ui.songView.model.definitionCellsOf
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell

/**
 * The sections [SongLyrics] shows of [model]: the song's own, after its first section of what the song says about itself
 * and how it is played, and the Chords section of its diagrams where [chordDiagrams] asks for one.
 */
@Composable
internal fun rememberShownSections(
    model: SongLyricsModel,
    chordDiagrams: ChordDiagrams?,
    instrumentChips: InstrumentChipsContent?,
    shouldShowTempo: Boolean,
    isSongInfoShown: Boolean,
    isSongInfoEditable: Boolean,
    hasPlayingControls: Boolean,
    readsDefaults: Boolean,
    shouldShowCapo: Boolean,
    hasTuner: Boolean,
    hasMetronome: Boolean,
): List<RenderSection> = remember(
        model.sections,
        model.song.metadata,
        model.shouldShowChords,
        shouldShowTempo,
        isSongInfoShown,
        isSongInfoEditable,
        hasPlayingControls,
        readsDefaults,
        shouldShowCapo,
        hasTuner,
        hasMetronome,
    ) {
        withMetadataSection(
            sections = model.sections,
            metadata = model.song.metadata,
            shouldShowChords = model.shouldShowChords,
            shouldShowCapo = shouldShowCapo,
            shouldShowTempo = shouldShowTempo,
            isSongInfoShown = isSongInfoShown,
            isSongInfoEditable = isSongInfoEditable,
            hasPlayingControls = hasPlayingControls,
            readsDefaults = readsDefaults,
            hasTuner = hasTuner,
            hasMetronome = hasMetronome,
        )
    }.let { sections ->
        // The cells last built are drawn on while the model for a new tuning is still being built, so that a tuning picked
        // crossfades once, from the old shapes to the new, rather than through the old shapes drawn on the new strings.
        // Not a state: nothing is redrawn because of it.
        val lastCells = remember { LastChordCells() }
        val cells = remember(model, chordDiagrams?.tuning, chordDiagrams?.storedShapes, chordDiagrams?.showsDefinitionsOnly) {
            when {
                chordDiagrams == null -> emptyList()
                chordDiagrams.showsDefinitionsOnly -> definitionCellsOf(model.song, chordDiagrams.notation)
                else -> chordDiagrams.tuning?.let { tuning ->
                    chordCellsOf(model.chords, model.chordTuning, tuning, chordDiagrams.storedShapes) ?: lastCells.value
                }.orEmpty()
            }
        }
        lastCells.value = cells
        remember(sections, cells, chordDiagrams?.isFolded, model.hasChords, instrumentChips) {
            withChordsSection(
                sections = sections,
                cells = cells,
                isFolded = chordDiagrams?.isFolded == true,
                hasChords = model.hasChords,
                instrumentChips = instrumentChips,
            )
        }
    }

/** The Chords section's cells as [SongLyrics] last built them. */
private class LastChordCells(var value: List<ChordCell> = emptyList())
