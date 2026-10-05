/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_change_time_signature
import com.pandulapeter.campfire.presentation.resources.song_details_set_time_signature
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.metronome.TimeSignaturePicker
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The beats of a song's bar, picked with the Metronome tab's own controls ([TimeSignaturePicker]) and written into the
 * file on Save, as a `{time}` directive. It is the one of the four playing values that is the song rather than the way
 * one band plays it, and the one the click cannot be told any other way, which is why it is written instead of being
 * overridden where the song is read.
 *
 * The value is kept as text, which is what the saved state of a recreated Activity takes, and parsed where it is used.
 * Save is offered for anything other than the bar the file already names, a song that names none included: writing
 * `{time: 4/4}` into it is a change, since it then says what it is in.
 */
@Composable
internal fun SongTimeSignatureDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongTimeSignature,
) {
    // Read the way the click reads it, so that a {time: C} is the common time it stands for rather than nothing.
    val declared = remember(dialog.time) { ChordProTime.parse(dialog.time)?.let { (beats, unit) -> TimeSignature(beats, unit) } }
    var picked by rememberSaveable(dialog.song.fileName) { mutableStateOf((declared ?: TimeSignature.COMMON_TIME).toString()) }
    val timeSignature = remember(picked) { TimeSignature.parse(picked) ?: TimeSignature.COMMON_TIME }
    val scrollState = rememberScrollState()
    CampfireBottomSheet(
        title = stringResource(if (declared == null) Res.string.song_details_set_time_signature else Res.string.song_details_change_time_signature),
        subtitle = songLabel(dialog.song),
        onDismiss = { viewModel.dismissSheet(dialog) },
        actions = { close ->
            BottomSheetConfirmButton(
                enabled = timeSignature != declared,
                onClick = {
                    viewModel.setSongTimeSignature(fileName = dialog.song.fileName, time = timeSignature.toString())
                    close()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
        ) {
            TimeSignaturePicker(
                timeSignature = timeSignature,
                onChange = { picked = it.toString() },
            )
        }
    }
}
