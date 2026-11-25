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
import com.pandulapeter.campfire.presentation.ui.songView.model.InstrumentPillContent
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.model.withScalesSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel
import com.pandulapeter.campfire.presentation.ui.songView.model.chordCellsOf
import com.pandulapeter.campfire.presentation.ui.songView.model.definitionCellsOf
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell

/**
 * The sections [SongLyrics] shows of [model]: the song's own, after (where [showsPlayingSections]) its first section of
 * what the song says about itself and how it is played, the Chords section of its diagrams where [chordDiagrams] asks for
 * one, and the Scales section after it where [scaleSuggestions] does. Song details draws those three in its dock
 * instead, so the song there starts at its first line.
 */
@Composable
internal fun rememberShownSections(
    model: SongLyricsModel,
    showsPlayingSections: Boolean,
    chordDiagrams: ChordDiagrams?,
    scaleSuggestions: ScaleSuggestions?,
    instrumentPill: InstrumentPillContent?,
    shouldShowTempo: Boolean,
    isSongInfoShown: Boolean,
    isSongInfoEditable: Boolean,
    readsDefaults: Boolean,
): List<RenderSection> {
    val cells = rememberChordCells(model, chordDiagrams.takeIf { showsPlayingSections })
    if (!showsPlayingSections) return model.sections
    val sections = remember(
        model.sections,
        model.song.metadata,
        model.shouldShowChords,
        model.isCapoShown,
        shouldShowTempo,
        isSongInfoShown,
        isSongInfoEditable,
        readsDefaults,
        model.shapesKey,
    ) {
        withMetadataSection(
            sections = model.sections,
            metadata = model.song.metadata,
            shouldShowChords = model.shouldShowChords,
            isCapoShown = model.isCapoShown,
            shapesKey = model.shapesKey,
            shouldShowTempo = shouldShowTempo,
            isSongInfoShown = isSongInfoShown,
            isSongInfoEditable = isSongInfoEditable,
            readsDefaults = readsDefaults,
        )
    }
    val withChords = remember(sections, cells, model.hasChords, instrumentPill) {
        withChordsSection(
            sections = sections,
            cells = cells,
            hasChords = model.hasChords,
            instrumentPill = instrumentPill,
        )
    }
    return remember(withChords, model.scaleGroups, scaleSuggestions != null) {
        if (scaleSuggestions == null) withChords else withScalesSection(withChords, model.scaleGroups)
    }
}

/**
 * The diagrams of [model]'s chords as [chordDiagrams] asks for them, none where it is null: the song's chords in the
 * player's shapes, or in the editor's preview only the song's own definitions.
 */
@Composable
internal fun rememberChordCells(model: SongLyricsModel, chordDiagrams: ChordDiagrams?): List<ChordCell> {
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
    return cells
}

/** The cells [rememberChordCells] last built. */
private class LastChordCells(var value: List<ChordCell> = emptyList())
