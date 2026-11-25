/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_accent_hint
import com.pandulapeter.campfire.presentation.resources.song_details_dock_tempo
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import com.pandulapeter.campfire.presentation.ui.metronome.BeatRow
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomePanelState
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.rememberSongPlayingControls
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore

/**
 * The tempo of a song where it is read, opened from the tempo chip of song details' dock: the tempo stepper with Tap,
 * writing the same override the same way the song's first section's did, so the chip and a playing click follow every
 * step, then the bar the click counts, whose accents are tapped here for this song alone (`UserPreferences.beatLevels`,
 * removed once tapped back to the signature's). The time signature is not here: it is written into the file, so it is
 * Song defaults'. Nothing to save: its close button only closes it.
 */
@Composable
internal fun TempoSheet(
    libraryState: LibraryState,
    songTextStore: SongTextStore,
    preferencesController: PreferencesController,
    overrides: PlayingOverrides,
    songRenderer: SongRenderer,
    metronomeController: MetronomeController,
    dialogHost: DialogHost,
    dialog: DialogType.Tempo,
) {
    val songs by libraryState.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val playingOverrides by overrides.playingOverrides.collectAsStateWithLifecycle()
    val tempo = rememberSongPlayingControls(
        overrides = overrides,
        songRenderer = songRenderer,
        song = song,
        setlistFileName = dialog.setlistFileName,
        playback = songPlaybackOf(song = song, setlistFileName = dialog.setlistFileName, overrides = playingOverrides),
        userPreferences = userPreferences,
        chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
        shouldShowChords = false,
        shouldShowTempo = true,
        inferredKey = null,
        onPlayingClicked = null,
    )?.tempo
    val panel = rememberMetronomePanelState(metronomeController, libraryState, songTextStore)
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_dock_tempo),
        subtitle = songLabel(song),
        onDismiss = { dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tempo != null) {
                TempoStepper(
                    bpm = tempo.tempo.bpm,
                    isDefault = tempo.tempo.isDefault,
                    onStep = { delta ->
                        println("Tempo sheet: tempo stepped")
                        tempo.onStep(delta)
                    },
                    onSet = { bpm ->
                        println("Tempo sheet: tempo set")
                        tempo.onSet(bpm)
                    },
                    onReset = {
                        println("Tempo sheet: tempo reset")
                        tempo.onReset()
                    },
                )
            }
            BeatRow(
                beatLevels = panel.beatLevels,
                beats = metronomeController.metronomeBeats,
                isPlaying = panel.isPlaying,
                isFlashEnabled = panel.isFlashEnabled,
                blockHeight = ACCENT_HEIGHT,
                blockGap = ACCENT_GAP,
                onBeatLevelsChanged = { levels ->
                    println("Tempo sheet: accent tapped")
                    metronomeController.setBeatLevels(panel.timeSignature, levels)
                },
            )
            Text(
                text = stringResource(Res.string.metronome_accent_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ACCENT_HEIGHT = 48.dp
private val ACCENT_GAP = 6.dp
