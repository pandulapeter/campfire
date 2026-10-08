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

/** A new song's metadata. Only its title is required; the optional values are written into its initial file. */
@Composable
internal fun NewSongDialog(
    onDismiss: () -> Unit,
    onCreate: (Map<Field, String>) -> Unit,
) {
    var values by rememberSaveable(stateSaver = songMetadataSaver) { mutableStateOf(emptyMap<Field, String>()) }
    val isValid = values[Field.TITLE].orEmpty().isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val create = { close: () -> Unit ->
        if (isValid) {
            confirmOnce {
                onCreate(values.fromSongMetadataDraft())
                close()
            }
        } else {
            keyboardController?.hide()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.songs_new_song),
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
            BottomSheetConfirmButton(enabled = isValid, onClick = { create(close) }) { Text(stringResource(Res.string.create)) }
        },
    )
}
