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

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * What a song is, as a form: every field is edited as a draft and only the ones changed are written, together, on Save.
 * How it is played — its key, capo, tempo and time — is not here but in the "Song defaults" sheet (`SongPlayingDialog`),
 * opened from the song details editing menu and from the About the song sheet's Song defaults group. The values are saved as a list of strings in [SONG_METADATA_FIELDS] order,
 * which is what Android's saved state takes. Unlike the dialogs that ask for one thing it opens with no field focused: it is opened to look the song
 * up as often as to correct one field of it, and a keyboard brought up over the title would hide half the form.
 */
@Composable
internal fun SongMetadataDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongMetadata,
) {
    // Compared with the draft the form opened with rather than with the file's text, so that a duration the field
    // could not show, and so opened empty, is only removed when the user typed into it.
    val offeredValues = remember(dialog.values) { dialog.values.toSongMetadataDraft() }
    var values by rememberSaveable(dialog.song.fileName, stateSaver = songMetadataSaver) { mutableStateOf(offeredValues) }
    val scrollState = rememberScrollState()
    val field: @Composable (Modifier, Field) -> Unit = { modifier, field ->
        SongMetadataField(
            modifier = modifier,
            field = field,
            value = values[field].orEmpty(),
            onValueChange = { value -> values = values + (field to value) },
        )
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_metadata_edit),
        subtitle = songLabel(dialog.song),
        text = { contentPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field(Modifier.fillMaxWidth(), Field.TITLE)
                field(Modifier.fillMaxWidth(), Field.SUBTITLE)
                field(Modifier.fillMaxWidth(), Field.ARTIST)
                field(Modifier.fillMaxWidth(), Field.ALBUM)
                field(Modifier.fillMaxWidth(), Field.COMPOSER)
                field(Modifier.fillMaxWidth(), Field.LYRICIST)
                SongMetadataShortFields(field)
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = SONG_METADATA_FIELDS.any { values[it].orEmpty().trim() != offeredValues[it].orEmpty().trim() },
                onClick = {
                    viewModel.setSongMetadata(
                        target = dialog.target,
                        values = values.fromSongMetadataDraft(),
                        offeredValues = offeredValues.fromSongMetadataDraft(),
                    )
                    close()
                },
            ) { Text(stringResource(if (dialog.target is SongEditTarget.EditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}
