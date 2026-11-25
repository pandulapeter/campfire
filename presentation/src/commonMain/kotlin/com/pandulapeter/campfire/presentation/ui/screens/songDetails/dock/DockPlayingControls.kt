/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.presentation.ui.songView.SongInstrumentPill
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongMetadataSection
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.presentation.ui.instrument.tunings

/**
 * What the song's first section held, in the dock's first row: the instrument and tuning pill where the diagrams are on
 * (an instrument without shapes keeps it, as it is what brings them back), then the playing controls, or in read only
 * mode the line of text they read as, each with the tuner and the metronome buttons after the values they go with.
 * Drawn at the interface's text size, whatever the song's: the dock's height is the screen's, never the song's.
 * Emits nothing where there is nothing to show.
 */
@Composable
internal fun DockPlayingControls(content: DockContent) {
    val chordDiagrams = content.chordDiagrams
    val onInstrumentClicked = chordDiagrams?.onInstrumentClicked
    if (chordDiagrams != null && onInstrumentClicked != null) {
        val tuningVisibility = remember { MutableTransitionState(chordDiagrams.instrument.tunings.size > 1) }
            .apply { targetState = chordDiagrams.instrument.tunings.size > 1 }
        SongInstrumentPill(
            chordDiagrams = chordDiagrams,
            headerStyle = MaterialTheme.typography.titleSmall,
            tuningVisibility = tuningVisibility,
            onInstrumentClicked = onInstrumentClicked,
            onTuningClicked = chordDiagrams.onTuningClicked ?: {},
        )
    }
    val model = content.model
    val playingControls = content.playingControls?.takeIf { model.shouldShowChords || content.shouldShowTempo }
    val section = remember(model, content.shouldShowTempo, playingControls != null, content.readsDefaults, content.playingTools) {
        withMetadataSection(
            sections = emptyList(),
            metadata = model.song.metadata,
            shouldShowChords = model.shouldShowChords,
            isCapoShown = model.isCapoShown,
            shapesKey = model.shapesKey,
            shouldShowTempo = content.shouldShowTempo,
            isSongInfoShown = false,
            hasPlayingControls = playingControls != null,
            readsDefaults = content.readsDefaults,
            hasTuner = content.playingTools?.onTunerClicked != null,
            hasMetronome = content.playingTools?.metronome != null,
        ).firstOrNull() as? RenderSection.Metadata
    } ?: return
    val typography = MaterialTheme.typography
    SongMetadataSection(
        metadata = section.metadata,
        isSongInfoShown = false,
        songInfoEditing = null,
        playingControls = playingControls,
        playingTools = content.playingTools,
        readsDefaults = section.readsDefaults,
        shapesKey = section.shapesKey,
        animatesControls = false,
        titleStyle = typography.titleSmall,
        lyricsStyle = remember(typography) { typography.bodyLarge.copy(color = Color.Unspecified) },
        fontScale = 1f,
    )
}
