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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_artist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_composer
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_lyricist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_subtitle
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_title
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_year
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import org.jetbrains.compose.resources.StringResource

/**
 * What a song is, as a form: every field is edited as a draft and only the ones changed are written, together, on Save.
 * How it is played — its key, capo, tempo and time — is not here, since those are part of writing the song down and
 * belong to the editor. The values are saved as a list of strings in [Field] order, which is what Android's saved
 * state takes. Unlike the dialogs that ask for one thing it opens with no field focused: it is opened to look the song
 * up as often as to correct one field of it, and a keyboard brought up over the title would hide half the form.
 */
@Composable
internal fun SongMetadataDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongMetadata,
) {
    var values by rememberSaveable(dialog.song.fileName, stateSaver = songMetadataSaver) { mutableStateOf(dialog.values) }
    val scrollState = rememberScrollState()
    val field: @Composable (Modifier, Field) -> Unit = { modifier, field ->
        SongMetadataField(
            modifier = modifier,
            field = field,
            value = values[field].orEmpty(),
            onValueChange = { value -> values = values + (field to value) },
        )
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { SongDialogTitle(title = stringResource(Res.string.song_details_metadata_edit), song = dialog.song) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).fadingVerticalEdges(scrollState).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field(Modifier.fillMaxWidth(), Field.TITLE)
                field(Modifier.fillMaxWidth(), Field.SUBTITLE)
                field(Modifier.fillMaxWidth(), Field.ARTIST)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field(Modifier.weight(2f), Field.ALBUM)
                    field(Modifier.weight(1f), Field.YEAR)
                }
                field(Modifier.fillMaxWidth(), Field.COMPOSER)
                field(Modifier.fillMaxWidth(), Field.LYRICIST)
                field(Modifier.fillMaxWidth(), Field.DURATION)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setSongMetadata(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, values = values, offeredValues = dialog.values)
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun SongMetadataField(
    modifier: Modifier = Modifier,
    field: Field,
    value: String,
    onValueChange: (String) -> Unit,
) = OutlinedTextField(
    modifier = modifier,
    value = value,
    // A brace would end the directive early or open another one, and a line break would leave the rest of the value
    // in the song as lyrics.
    onValueChange = { newValue -> onValueChange(newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }) },
    label = { Text(stringResource(field.label)) },
    singleLine = true,
    keyboardOptions = if (field == Field.YEAR) {
        KeyboardOptions(keyboardType = KeyboardType.Number)
    } else {
        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    },
)

private val Field.label: StringResource
    get() = when (this) {
        Field.TITLE -> Res.string.song_editor_insert_title
        Field.SUBTITLE -> Res.string.song_editor_insert_subtitle
        Field.ARTIST -> Res.string.song_editor_insert_artist
        Field.COMPOSER -> Res.string.song_editor_insert_composer
        Field.LYRICIST -> Res.string.song_editor_insert_lyricist
        Field.ALBUM -> Res.string.song_editor_insert_album
        Field.YEAR -> Res.string.song_editor_insert_year
        Field.DURATION -> Res.string.song_editor_insert_duration
    }

private val songMetadataSaver = listSaver<Map<Field, String>, String>(
    save = { values -> Field.entries.map { values[it].orEmpty() } },
    restore = { saved -> Field.entries.zip(saved).toMap() },
)
