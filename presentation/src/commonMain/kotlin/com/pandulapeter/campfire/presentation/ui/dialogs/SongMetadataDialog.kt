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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.optional_field_label
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
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import org.jetbrains.compose.resources.StringResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

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
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_metadata_edit),
        subtitle = songLabel(dialog.song),
        text = { contentPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field(Modifier.fillMaxWidth(), Field.TITLE)
                field(Modifier.fillMaxWidth(), Field.SUBTITLE)
                field(Modifier.fillMaxWidth(), Field.ARTIST)
                field(Modifier.fillMaxWidth(), Field.ALBUM)
                field(Modifier.fillMaxWidth(), Field.COMPOSER)
                field(Modifier.fillMaxWidth(), Field.LYRICIST)
                // The two short values share a row, each with room for its clear button and what it holds: next to the
                // album, a year was left two digits' room once the button was there.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field(Modifier.weight(1f), Field.YEAR)
                    field(Modifier.weight(1f), Field.DURATION)
                }
            }
        },
        confirmButton = {
            BottomSheetConfirmButton(
                enabled = Field.entries.any { values[it].orEmpty().trim() != dialog.values[it].orEmpty().trim() },
                onClick = {
                    viewModel.setSongMetadata(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, values = values, offeredValues = dialog.values)
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(if (dialog.isEditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

@Composable
internal fun SongMetadataField(
    modifier: Modifier = Modifier,
    field: Field,
    value: String,
    onValueChange: (String) -> Unit,
    isOptional: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
    onDone: (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        modifier = modifier,
        value = value,
        // A brace would end the directive early or open another one, and a line break would leave the rest of the value
        // in the song as lyrics.
        onValueChange = { newValue -> onValueChange(newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(maxLength)) },
        label = {
            val label = stringResource(field.label)
            Text(if (isOptional) stringResource(Res.string.optional_field_label, label) else label)
        },
        trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
        singleLine = true,
        // Next walks the form; the final Done confirms creation or puts the editing form's keyboard away.
        keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else keyboardController?.hide() }),
        keyboardOptions = KeyboardOptions(
            capitalization = if (field == Field.YEAR) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (field == Field.YEAR) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (field == Field.entries.last()) ImeAction.Done else ImeAction.Next,
        ),
    )
}

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

internal val songMetadataSaver = listSaver<Map<Field, String>, String>(
    save = { values -> Field.entries.map { values[it].orEmpty() } },
    restore = { saved -> Field.entries.zip(saved).toMap() },
)
