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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.resources.settings_instrument
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_overrides_kept_app
import com.pandulapeter.campfire.presentation.resources.song_details_overrides_kept_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentPill
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverrides
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.CapoControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.TranspositionControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.rememberSongPlayingControls
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import com.pandulapeter.campfire.presentation.ui.tuner.TunerButton
import kotlinx.coroutines.flow.StateFlow

/**
 * How a song is played where it is read, opened from the key chip in song details' app bar: the instrument in the
 * player's hands and its tuning with the tuner after them ([PlayingInstrumentRow], [onTunerClicked], opening over this
 * sheet), then the transposition and the capo ([PlayingStepperRow]), the very steppers the song's first section had,
 * writing the same overrides the same way (held pending, written once they rest, see `PendingWrites`), so the song
 * behind it follows every step. Nothing to save: its close button only closes it. The transposition and the capo leave
 * with the chords; the instrument stays, as the tuner and the keyboard's names read it, and the tuner with the Tuner
 * feature.
 */
@Composable
internal fun PlayingSheet(
    allSongs: StateFlow<List<Song>>,
    songTextStore: SongTextStore,
    preferencesController: PreferencesController,
    overrides: PlayingOverrides,
    songRenderer: SongRenderer,
    dialogHost: DialogHost,
    onTunerClicked: () -> Unit,
    dialog: DialogType.Playing,
) {
    val songs by allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val playingOverrides by overrides.playingOverrides.collectAsStateWithLifecycle()
    val inferredKeys by songTextStore.inferredKeys.keys.collectAsStateWithLifecycle()
    val controls = rememberSongPlayingControls(
        overrides = overrides,
        songRenderer = songRenderer,
        song = song,
        setlistFileName = dialog.setlistFileName,
        playback = songPlaybackOf(song = song, setlistFileName = dialog.setlistFileName, overrides = playingOverrides),
        userPreferences = userPreferences,
        chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
        shouldShowChords = userPreferences?.areChordsEnabled != false,
        shouldShowTempo = false,
        inferredKey = inferredKeys[song.fileName],
        onPlayingClicked = null,
    )
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_dock_playing),
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
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayingInstrumentRow(
                userPreferences = userPreferences,
                onInstrumentClicked = { dialogHost.showDialog(DialogType.Instrument) },
                onTuningClicked = { dialogHost.showDialog(DialogType.Tuning) },
                onTunerClicked = {
                    println("Playing sheet: tuner")
                    onTunerClicked()
                }.takeIf { userPreferences?.isTunerEnabled != false },
            )
            AnimatedPlayingRow(value = controls?.key) { key ->
                PlayingStepperRow(label = stringResource(Res.string.song_details_transposition)) {
                    TranspositionControls(
                        transposition = key.transposition,
                        isDefault = key.isDefault,
                        key = key.key,
                        widestValues = key.labels,
                        // The song's screen hears them, which this sheet covers.
                        shortcuts = null,
                        onStep = { semitones ->
                            println("Playing sheet: transposition stepped")
                            key.onStep(semitones)
                        },
                        onReset = {
                            println("Playing sheet: transposition reset")
                            key.onReset()
                        },
                    )
                }
            }
            AnimatedPlayingRow(value = controls?.capo) { capo ->
                PlayingStepperRow(label = stringResource(Res.string.song_editor_insert_capo)) {
                    CapoControls(
                        capo = capo.capo,
                        onStep = { frets ->
                            println("Playing sheet: capo stepped")
                            capo.onStep(frets)
                        },
                        onReset = {
                            println("Playing sheet: capo reset")
                            capo.onReset()
                        },
                    )
                }
            }
            // About the steppers alone (the instrument is the app's, for every song), so it leaves with them.
            val note = stringResource(
                if (dialog.setlistFileName == null) Res.string.song_details_overrides_kept_app else Res.string.song_details_overrides_kept_setlist,
            )
            AnimatedPlayingRow(value = note.takeIf { controls?.key != null || controls?.capo != null }) { text ->
                OverridesNote(text = text)
            }
        }
    }
}

/**
 * The instrument in the player's hands and its tuning ([InstrumentPill]) as a setting of the sheet, each half opening its
 * sheet over this one, lined up with the steppers under it, and the tuner after it ([onTunerClicked], null with the
 * Tuner feature off), which tunes the strings the pill names.
 */
@Composable
private fun PlayingInstrumentRow(
    userPreferences: UserPreferences?,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    onTunerClicked: (() -> Unit)?,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.padding(end = 16.dp),
        text = stringResource(Res.string.settings_instrument),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(modifier = Modifier.weight(1f))
    InstrumentPill(
        // As tall as the steppers under it, with whatever room the label leaves, which the pill's own layout cuts its
        // names short to fit where it is not enough.
        modifier = Modifier.height(STEPPER_HEIGHT),
        instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR,
        tuning = userPreferences?.instrumentTuning,
        notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation(),
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
    // Kept while it leaves, so that it shrinks away as it was.
    val lastTunerClick = remember { LastValue<() -> Unit>() }
    onTunerClicked?.let { lastTunerClick.value = it }
    AnimatedVisibility(
        visible = onTunerClicked != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        TunerButton(
            modifier = Modifier.padding(start = 8.dp),
            isSheetShown = false,
            height = STEPPER_HEIGHT,
            fontScale = 1f,
            onClick = { lastTunerClick.value?.invoke() },
        )
    }
}

/** A setting of the sheet: its [label] at the start and its [stepper] at the end. */
@Composable
private fun PlayingStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.weight(1f),
        text = label,
        style = MaterialTheme.typography.bodyLarge,
    )
    stepper()
}

/**
 * [content] for [value] while there is one, coming and going as a switch gives and takes it, drawn while it leaves with
 * the value it last had.
 */
@Composable
private fun <T : Any> AnimatedPlayingRow(
    value: T?,
    content: @Composable (T) -> Unit,
) {
    val last = remember { LastValue<T>() }
    value?.let { last.value = it }
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        last.value?.let { content(it) }
    }
}

/** What [AnimatedPlayingRow] last drew. Not a state: it is written and read in the same composition. */
private class LastValue<T : Any>(var value: T? = null)
