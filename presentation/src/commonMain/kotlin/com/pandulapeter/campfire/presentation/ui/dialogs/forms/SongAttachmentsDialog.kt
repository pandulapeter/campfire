/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_add
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_missing
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_remove
import com.pandulapeter.campfire.presentation.resources.song_details_attachments_edit
import com.pandulapeter.campfire.presentation.resources.song_details_attachments_hint
import com.pandulapeter.campfire.presentation.resources.song_details_link_name
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.formattedSize
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetConfirmButton
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.TextFieldBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.songInfo.attachmentIcon
import com.pandulapeter.campfire.presentation.ui.songInfo.attachmentLabel
import com.pandulapeter.campfire.presentation.ui.state.songText.SongAttachmentEditing
import org.jetbrains.compose.resources.painterResource

/**
 * The song's attachments as a draft written together on Save, like the links' sheet: Add file in the header picks one
 * more (`SongAttachmentEditing.pick`, kept on the draft shelf until Save moves it into the library, or the shelf is let
 * go of), each row names its file with a field (blank for the type's name) and removes it with a button, and the close
 * button cancels. The rows are keyed by their file, which is unique among them, so a removed row folds away in place.
 * Over the editor ([SongEditTarget.EditorDraft]) Save is Done and changes the text being typed.
 */
@Composable
internal fun SongAttachmentsDialog(
    songAttachmentEditing: SongAttachmentEditing,
    onDismiss: () -> Unit,
    dialog: DialogType.SongAttachments,
) {
    var rows by rememberSaveable(dialog.song.fileName, stateSaver = attachmentRowsSaver) { mutableStateOf(dialog.attachments) }
    val fileSizes by songAttachmentEditing.fileSizes.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    val listState = rememberLazyListState()
    HideKeyboardWhenScrolledDown(listState)
    LaunchedEffect(songAttachmentEditing, dialog.target) {
        songAttachmentEditing.picked.collect { picked ->
            if (picked.target == dialog.target && rows.none { it.source == picked.attachment.source }) {
                rows = rows + picked.attachment
                listState.animateScrollToItem(rows.size)
            }
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.song_details_attachments_edit),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        text = { contentPadding ->
            val currentRows = rows
            LazyColumn(
                modifier = Modifier.bounceScrollableContent(listState).fillMaxWidth().fadingTopEdge(listState, sheetContainerColor()),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = HINT_KEY) {
                    Text(
                        text = stringResource(Res.string.song_details_attachments_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items = currentRows, key = { it.source }) { attachment ->
                    SongAttachmentFields(
                        modifier = Modifier.animateItem(),
                        attachment = attachment,
                        size = fileSizes[attachment.source],
                        onChange = { updated -> rows = rows.map { if (it.source == attachment.source) updated else it } },
                        onRemove = { rows = rows.filterNot { it.source == attachment.source } },
                    )
                }
            }
        },
        startButton = {
            CampfireIconButton(
                icon = painterResource(Res.drawable.ic_add),
                label = stringResource(Res.string.song_details_attachment_add),
                onClick = { songAttachmentEditing.pick(dialog.target, filePicker::pickAttachment) },
            )
        },
        confirmButton = { close ->
            val attachmentsToSave = songAttachmentsToSave(draft = rows, offered = dialog.attachments)
            BottomSheetConfirmButton(
                enabled = attachmentsToSave != null,
                onClick = {
                    if (attachmentsToSave != null) {
                        songAttachmentEditing.save(target = dialog.target, attachments = attachmentsToSave, offered = dialog.attachments)
                        close()
                    }
                },
            ) { Text(stringResource(if (dialog.target is SongEditTarget.EditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

/** One attachment of the sheet: its type's icon, the field naming it (with its size, or that it is not here, under it), and Remove. */
@Composable
private fun SongAttachmentFields(
    modifier: Modifier = Modifier,
    attachment: ChordProAttachment,
    size: Long?,
    onChange: (ChordProAttachment) -> Unit,
    onRemove: () -> Unit,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val unnamed = attachmentLabel(attachment.copy(name = null))
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(end = 12.dp),
            painter = attachmentIcon(attachment),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = attachment.name.orEmpty(),
            onValueChange = { name -> onChange(attachment.copy(name = name.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' })) },
            label = { Text(stringResource(Res.string.song_details_link_name)) },
            placeholder = { Text(unnamed) },
            supportingText = { Text(if (size == null) stringResource(Res.string.song_details_attachment_missing) else formattedSize(size)) },
            trailingIcon = rememberClearTextButton(isVisible = !attachment.name.isNullOrEmpty(), onClear = { onChange(attachment.copy(name = null)) }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
        )
        CampfireIconButton(
            icon = painterResource(Res.drawable.ic_delete),
            label = textResource(Res.string.song_details_attachment_remove, attachment.name?.takeIf { it.isNotBlank() } ?: unnamed),
            onClick = onRemove,
        )
    }
}

private const val HINT_KEY = "hint"

private val attachmentRowsSaver = listSaver<List<ChordProAttachment>, String>(
    save = { rows -> rows.flatMap { listOf(it.source, it.name.orEmpty()) } },
    restore = { values -> values.chunked(2).map { ChordProAttachment(source = it[0], name = it[1].takeIf(String::isNotEmpty)) } },
)

/**
 * The attachments an Attachments draft writes, or null while it would write what the song already has: compared by their
 * files and their names as the file will hold them (blank and surrounding spaces being no name), in their order.
 */
internal fun songAttachmentsToSave(draft: List<ChordProAttachment>, offered: List<ChordProAttachment>): List<ChordProAttachment>? =
    draft.takeIf { it.map(ChordProAttachment::normalized) != offered.map(ChordProAttachment::normalized) }

private fun ChordProAttachment.normalized() = copy(name = name?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() })
