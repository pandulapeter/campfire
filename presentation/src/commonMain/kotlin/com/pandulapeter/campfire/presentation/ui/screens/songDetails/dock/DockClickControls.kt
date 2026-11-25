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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongMetadataSection
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection

/**
 * What the song's first section held about the click, at the end of the dock's diagram row (or in its one row): the
 * tempo stepper with Tap, a time signature other than 4/4, and the metronome button, or in read only mode the line of
 * text they read as, with the button after it. Drawn at the interface's text size, whatever the song's: the dock's
 * height is the screen's, never the song's. Emits nothing with the metronome switched off.
 */
@Composable
internal fun DockClickControls(content: DockContent) {
    if (!content.shouldShowTempo) return
    val model = content.model
    // The key and the capo are the key chip's, and the tuner is the Playing sheet's or the dock's start.
    val controls = remember(content.playingControls) {
        content.playingControls?.let { SongPlayingControls(key = null, capo = null, tempo = it.tempo, timeSignature = it.timeSignature) }
    }
    val tools = remember(content.playingTools) {
        content.playingTools?.let { SongPlayingTools(onTunerClicked = null, isTunerSheetShown = false, metronome = it.metronome) }
    }
    val hasMetronome = tools?.metronome != null
    val section = remember(model, controls != null, content.readsDefaults, hasMetronome) {
        withMetadataSection(
            sections = emptyList(),
            metadata = model.song.metadata,
            shouldShowChords = false,
            shouldShowTempo = true,
            isSongInfoShown = false,
            hasPlayingControls = controls != null,
            readsDefaults = content.readsDefaults,
            hasMetronome = hasMetronome,
        ).firstOrNull() as? RenderSection.Metadata
    } ?: return
    val typography = MaterialTheme.typography
    SongMetadataSection(
        metadata = section.metadata,
        isSongInfoShown = false,
        songInfoEditing = null,
        playingControls = controls,
        playingTools = tools,
        readsDefaults = section.readsDefaults,
        shapesKey = null,
        animatesControls = false,
        titleStyle = typography.titleSmall,
        lyricsStyle = remember(typography) { typography.bodyLarge.copy(color = Color.Unspecified) },
        fontScale = 1f,
    )
}
