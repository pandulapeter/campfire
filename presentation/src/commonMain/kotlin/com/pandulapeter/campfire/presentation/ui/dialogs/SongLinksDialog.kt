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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProLinks
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_link_add
import com.pandulapeter.campfire.presentation.resources.song_details_link_address
import com.pandulapeter.campfire.presentation.resources.song_details_link_address_hint
import com.pandulapeter.campfire.presentation.resources.song_details_link_duplicate
import com.pandulapeter.campfire.presentation.resources.song_details_link_name
import com.pandulapeter.campfire.presentation.resources.song_details_link_remove
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.linkLabel
import org.jetbrains.compose.resources.painterResource

/**
 * All links are edited as a draft and written together on Save, so removing a row or changing a label is reversible
 * until the dialog is confirmed. URL and name strings are saved rather than model objects for Android's saved state.
 */
@Composable
internal fun SongLinksDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongLinks,
) {
    var links by rememberSaveable(dialog.song.fileName, stateSaver = songLinksSaver) { mutableStateOf(dialog.links) }
    val urls = links.map { ChordProLinks.usableUrl(it.url) }
    val canSave = urls.all { it != null } && urls.distinct().size == urls.size
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { SongDialogTitle(title = stringResource(Res.string.song_details_links_edit), song = dialog.song) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.song_details_link_address_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                links.forEachIndexed { index, link ->
                    SongLinkFields(
                        link = link,
                        isDuplicate = urls[index] != null && urls.count { it == urls[index] } > 1,
                        onChange = { updated -> links = links.toMutableList().apply { this[index] = updated } },
                        onRemove = { links = links.filterIndexed { other, _ -> other != index } },
                    )
                }
                TextButton(onClick = { links = links + ChordProLink(url = "") }) {
                    Icon(painter = painterResource(Res.drawable.ic_add), contentDescription = null)
                    Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(Res.string.song_details_link_add))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    viewModel.setSongLinks(fileName = dialog.song.fileName, links = links, offeredLinks = dialog.links)
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun SongLinkFields(
    link: ChordProLink,
    isDuplicate: Boolean,
    onChange: (ChordProLink) -> Unit,
    onRemove: () -> Unit,
) = Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = link.name.orEmpty(),
                onValueChange = { name -> onChange(link.copy(name = name.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' })) },
                label = { Text(stringResource(Res.string.song_details_link_name)) },
                placeholder = { Text(linkLabel(link.url)) },
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = textResource(Res.string.song_details_link_remove, linkLabel(link)),
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = link.url,
            onValueChange = { url -> onChange(link.copy(url = url.filterNot { it == '\n' || it == '\r' })) },
            label = { Text(stringResource(Res.string.song_details_link_address)) },
            singleLine = true,
            isError = isDuplicate || (link.url.isNotBlank() && ChordProLinks.usableUrl(link.url) == null),
            supportingText = if (isDuplicate) ({ Text(stringResource(Res.string.song_details_link_duplicate)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
}

private val songLinksSaver = listSaver<List<ChordProLink>, String>(
    save = { links -> links.flatMap { listOf(it.url, it.name.orEmpty()) } },
    restore = { values -> values.chunked(2).map { ChordProLink(url = it[0], name = it[1].takeIf(String::isNotEmpty)) } },
)
