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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * A new song's metadata. Only its title is required; the optional values are written into its initial file.
 *
 * Duplicating a song opens the same form on what that song says ([offeredValues], as [DialogType.DuplicateSong] read
 * them), and [onCreate] then gets the draft with what was offered, so that only the fields the user changed are written
 * over the copy. Unlike the empty form it opens with no field focused, as the song metadata sheet does: the form is
 * already filled in, and a keyboard brought up over the title would hide the subtitle a "Live" copy is likeliest to
 * be told apart by.
 *
 * @param songFileName The song being duplicated, which keys the draft; null for a new song.
 */
@Composable
internal fun NewSongDialog(
    onDismiss: () -> Unit,
    onCreate: (values: Map<Field, String>, offeredValues: Map<Field, String>) -> Unit,
    title: String = stringResource(Res.string.songs_new_song),
    subtitle: String = "",
    songFileName: String? = null,
    offeredValues: Map<Field, String> = emptyMap(),
    confirmLabel: String = stringResource(Res.string.create),
) {
    val offeredDraft = remember(offeredValues) { offeredValues.toSongMetadataDraft() }
    var values by rememberSaveable(songFileName, stateSaver = songMetadataSaver) { mutableStateOf(offeredDraft) }
    val isValid = values[Field.TITLE].orEmpty().isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester(isFocused = songFileName == null)
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val create = { close: () -> Unit ->
        if (isValid) {
            confirmOnce {
                onCreate(values.fromSongMetadataDraft(), offeredDraft.fromSongMetadataDraft())
                close()
            }
        } else {
            keyboardController?.hide()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = subtitle,
        text = { contentPadding ->
            val closeSheet = { close() }
            val isClosing = LocalIsSheetClosing.current
            val field: @Composable (Modifier, Field) -> Unit = { modifier, field ->
                SongMetadataField(
                    modifier = modifier,
                    field = field,
                    value = values[field].orEmpty(),
                    onValueChange = { values = values + (field to it) },
                    isOptional = field != Field.TITLE,
                    maxLength = if (field == Field.TITLE || field == Field.ARTIST) MAX_TITLE_LENGTH else Int.MAX_VALUE,
                    // The close button leaves the keyboard up, so a Done during the slide that follows it is dropped.
                    onDone = { if (!isClosing()) create(closeSheet) },
                )
            }
            Column(
                modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field(Modifier.fillMaxWidth().focusRequester(focusRequester), Field.TITLE)
                field(Modifier.fillMaxWidth(), Field.SUBTITLE)
                field(Modifier.fillMaxWidth(), Field.ARTIST)
                field(Modifier.fillMaxWidth(), Field.ALBUM)
                field(Modifier.fillMaxWidth(), Field.COMPOSER)
                field(Modifier.fillMaxWidth(), Field.LYRICIST)
                SongMetadataShortFields(field)
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(enabled = isValid, onClick = { create(close) }) { Text(confirmLabel) }
        },
    )
}
