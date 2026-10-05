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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.optional_field_label
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_playing_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_artist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_composer
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_lyricist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_subtitle
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_title
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_year
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import org.jetbrains.compose.resources.StringResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.platform.numericPlatformImeOptions

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
    dialog: CampfireViewModel.DialogType.SongMetadata,
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
                modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
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
                        fileName = dialog.song.fileName,
                        isEditorDraft = dialog.isEditorDraft,
                        values = values.fromSongMetadataDraft(),
                        offeredValues = offeredValues.fromSongMetadataDraft(),
                    )
                    close()
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
        onValueChange = { newValue ->
            onValueChange(
                when (field) {
                    Field.YEAR -> newValue.filter { it in '0'..'9' }.take(YEAR_LENGTH)
                    Field.DURATION -> durationDigitsTyped(newValue)
                    else -> newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(maxLength)
                },
            )
        },
        label = {
            val label = stringResource(field.label)
            Text(
                text = if (isOptional) stringResource(Res.string.optional_field_label, label) else label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
        singleLine = true,
        visualTransformation = if (field == Field.DURATION) DurationDigitsTransformation else VisualTransformation.None,
        // Next walks the form; the final Done confirms creation or puts the editing form's keyboard away.
        keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else keyboardController?.hide() }),
        keyboardOptions = KeyboardOptions(
            capitalization = if (field.isNumeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (field == SONG_METADATA_FIELDS.last()) ImeAction.Done else ImeAction.Next,
            platformImeOptions = numericPlatformImeOptions.takeIf { field.isNumeric },
        ),
    )
}

/**
 * The year and the duration, side by side where both labels fit on one line in either language, and one under the
 * other on a phone, where half a sheet cuts "Duration (optional)" in two. The two short values share a row where they
 * can, each with room for its clear button and what it holds: next to the album, a year was left two digits' room once
 * the button was there. Which of the two it is is decided while measuring rather than by composing a row or a column,
 * so that crossing the width (a rotation, a resized window) keeps the focused field, and the keyboard with it.
 */
@Composable
internal fun SongMetadataShortFields(field: @Composable (Modifier, Field) -> Unit) = Layout(
    content = {
        field(Modifier.fillMaxWidth(), Field.YEAR)
        field(Modifier.fillMaxWidth(), Field.DURATION)
    },
) { measurables, constraints ->
    val gap = SHORT_FIELDS_GAP.roundToPx()
    val isSideBySide = constraints.maxWidth >= MIN_SHORT_FIELDS_ROW_WIDTH.roundToPx()
    val width = if (isSideBySide) (constraints.maxWidth - gap) / 2 else constraints.maxWidth
    val (year, duration) = measurables.map { it.measure(Constraints.fixedWidth(width)) }
    val height = if (isSideBySide) maxOf(year.height, duration.height) else year.height + gap + duration.height
    layout(constraints.maxWidth, height) {
        year.placeRelative(0, 0)
        if (isSideBySide) duration.placeRelative(width + gap, 0) else duration.placeRelative(0, year.height + gap)
    }
}

/**
 * The narrowest form that puts the year and the duration side by side: each half has to hold the longer of the two
 * languages' "Duration (optional)" on one line, next to the clear button once the field holds a value.
 */
private val MIN_SHORT_FIELDS_ROW_WIDTH = 480.dp

/** The same gap the forms leave between their other fields. */
private val SHORT_FIELDS_GAP = 8.dp

/** What a field is called in the forms that edit it, the metadata ones and the "Song defaults" sheet alike. */
internal val Field.label: StringResource
    get() = when (this) {
        Field.TITLE -> Res.string.song_editor_insert_title
        Field.SUBTITLE -> Res.string.song_editor_insert_subtitle
        Field.ARTIST -> Res.string.song_editor_insert_artist
        Field.COMPOSER -> Res.string.song_editor_insert_composer
        Field.LYRICIST -> Res.string.song_editor_insert_lyricist
        Field.ALBUM -> Res.string.song_editor_insert_album
        Field.YEAR -> Res.string.song_editor_insert_year
        Field.DURATION -> Res.string.song_editor_insert_duration
        Field.KEY -> Res.string.song_editor_insert_key
        Field.CAPO -> Res.string.song_editor_insert_capo
        Field.TEMPO -> Res.string.song_details_playing_tempo
        Field.TIME -> Res.string.song_editor_insert_time
    }

/** The fields typed as digits alone: a year, and a duration through [DurationDigitsTransformation]. */
private val Field.isNumeric get() = this == Field.YEAR || this == Field.DURATION

private const val YEAR_LENGTH = 4

/**
 * The fields the New song and Edit song details forms ask for, in the order they ask for them. Not every
 * [Field] there is: how the song is played is written from the "Song defaults" sheet, so it is left out of both forms
 * and of what they save.
 */
internal val SONG_METADATA_FIELDS = listOf(
    Field.TITLE,
    Field.SUBTITLE,
    Field.ARTIST,
    Field.ALBUM,
    Field.COMPOSER,
    Field.LYRICIST,
    Field.YEAR,
    Field.DURATION,
)

internal val songMetadataSaver = listSaver<Map<Field, String>, String>(
    save = { values -> SONG_METADATA_FIELDS.map { values[it].orEmpty() } },
    restore = { saved -> SONG_METADATA_FIELDS.zip(saved).toMap() },
)
