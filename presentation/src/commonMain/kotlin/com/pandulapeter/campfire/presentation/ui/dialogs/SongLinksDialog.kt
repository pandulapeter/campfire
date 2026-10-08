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

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.edit.ChordProLinks
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_link_add
import com.pandulapeter.campfire.presentation.resources.song_details_link_address
import com.pandulapeter.campfire.presentation.resources.song_details_link_address_hint
import com.pandulapeter.campfire.presentation.resources.song_details_link_duplicate
import com.pandulapeter.campfire.presentation.resources.song_details_link_move_down
import com.pandulapeter.campfire.presentation.resources.song_details_link_move_up
import com.pandulapeter.campfire.presentation.resources.song_details_link_name
import com.pandulapeter.campfire.presentation.resources.song_details_link_remove
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.linkLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * All links are edited as a draft and written together on Save, so removing or moving a row or changing a label is
 * reversible until the dialog is confirmed. The order of the rows is the order the file lists the links in, and so
 * the one the song details card shows them in. URL and name strings are saved rather than model objects for Android's saved state.
 * The rows are a lazy list keyed by [SongLinkRow.id] rather than by position or address, since two rows may hold the
 * same address while it is being typed: that key is what lets a moved row slide to its new place instead of the two
 * rows swapping their contents where they stand.
 */
@Composable
internal fun SongLinksDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongLinks,
) {
    var rows by rememberSaveable(dialog.song.fileName, stateSaver = songLinkRowsSaver) {
        mutableStateOf(dialog.links.ifEmpty { listOf(ChordProLink(url = "")) }.toRows())
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    fun move(id: Int, offset: Int) {
        val index = rows.indexOfFirst { it.id == id }
        val target = index + offset
        if (index == -1 || target !in rows.indices) return
        val row = rows[index]
        val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.id }?.size ?: 0
        rows = rows.swapped(index, target)
        coroutineScope.launch { listState.reveal(key = row.id, index = target + ROWS_START_INDEX, size = size) }
    }
    fun add() {
        val row = SongLinkRow(id = (rows.maxOfOrNull { it.id } ?: -1) + 1, link = ChordProLink(url = ""))
        val size = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.key is Int }?.size ?: 0
        rows = rows + row
        coroutineScope.launch { listState.reveal(key = row.id, index = rows.lastIndex + ROWS_START_INDEX, size = size) }
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_links_edit),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        text = { contentPadding ->
            // The sheet is a composition of its own, so it can recompose with new rows before this function does: what
            // is derived from the rows is derived here, from the same read the list is built from, or an added row would
            // be looked up in a list of addresses that does not have it yet.
            val currentRows = rows
            val urls = currentRows.map { ChordProLinks.usableUrl(it.link.url) }
            LazyColumn(
                modifier = Modifier.bounceScrollableContent(listState).fillMaxWidth().fadingTopEdge(listState, sheetContainerColor()),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = HINT_KEY) {
                    Text(
                        text = stringResource(Res.string.song_details_link_address_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(
                    items = currentRows,
                    key = { _, row -> row.id },
                ) { index, row ->
                    SongLinkFields(
                        modifier = Modifier.animateItem(),
                        link = row.link,
                        isDuplicate = urls[index] != null && urls.count { it == urls[index] } > 1,
                        onChange = { updated -> rows = rows.map { if (it.id == row.id) it.copy(link = updated) else it } },
                        onMoveUp = if (index > 0) ({ move(row.id, -1) }) else null,
                        onMoveDown = if (index < currentRows.lastIndex) ({ move(row.id, 1) }) else null,
                        onRemove = { rows = rows.filterNot { it.id == row.id } },
                    )
                }
            }
        },
        // Keep Add link compact so the header has room for the song and Save.
        startButton = {
            IconButton(onClick = ::add) {
                Icon(
                    painter = painterResource(Res.drawable.ic_add),
                    contentDescription = stringResource(Res.string.song_details_link_add),
                )
            }
        },
        confirmButton = { close ->
            val linksToSave = songLinksToSave(draft = rows.map { it.link }, offered = dialog.links)
            BottomSheetConfirmButton(
                enabled = linksToSave != null,
                onClick = {
                    if (linksToSave != null) {
                        viewModel.setSongLinks(target = dialog.target, links = linksToSave, offeredLinks = dialog.links)
                        close()
                    }
                },
            ) { Text(stringResource(if (dialog.target is SongEditTarget.EditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

/**
 * One card of the dialog. [id] means nothing outside the dialog and is not saved: a restored draft numbers its rows
 * again, which is enough, since an id only has to stay the same for as long as the row is on screen.
 */
private data class SongLinkRow(
    val id: Int,
    val link: ChordProLink,
)

private fun List<ChordProLink>.toRows() = mapIndexed { index, link -> SongLinkRow(id = index, link = link) }

private const val HINT_KEY = "hint"

/** The rows come after the hint, which is the list's first item. */
private const val ROWS_START_INDEX = 1

/**
 * @param onMoveUp Null for the first row, whose button is then shown disabled rather than left out, so that the
 *   buttons of every row stay where they are and the field beside them keeps its width.
 * @param onMoveDown The same for the last row.
 */
@Composable
private fun SongLinkFields(
    modifier: Modifier = Modifier,
    link: ChordProLink,
    isDuplicate: Boolean,
    onChange: (ChordProLink) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = link.name.orEmpty(),
                onValueChange = { name -> onChange(link.copy(name = name.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' })) },
                label = { Text(stringResource(Res.string.song_details_link_name)) },
                placeholder = { Text(linkLabel(link.url)) },
                trailingIcon = rememberClearTextButton(isVisible = !link.name.isNullOrEmpty(), onClear = { onChange(link.copy(name = null)) }),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = link.url,
                onValueChange = { url -> onChange(link.copy(url = url.filterNot { it == '\n' || it == '\r' })) },
                label = { Text(stringResource(Res.string.song_details_link_address)) },
                trailingIcon = rememberClearTextButton(isVisible = link.url.isNotEmpty(), onClear = { onChange(link.copy(url = "")) }),
                singleLine = true,
                isError = isDuplicate || (link.url.isNotBlank() && ChordProLinks.usableUrl(link.url) == null) ||
                    (link.url.isBlank() && !link.name.isNullOrBlank()),
                supportingText = if (isDuplicate) ({ Text(stringResource(Res.string.song_details_link_duplicate)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            )
        }
        val label = linkLabel(link)
        Column {
            IconButton(
                enabled = onMoveUp != null,
                onClick = { onMoveUp?.invoke() },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_move_up),
                    contentDescription = textResource(Res.string.song_details_link_move_up, label),
                )
            }
            IconButton(
                enabled = onMoveDown != null,
                onClick = { onMoveDown?.invoke() },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_move_down),
                    contentDescription = textResource(Res.string.song_details_link_move_down, label),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = textResource(Res.string.song_details_link_remove, label),
                )
            }
        }
    }
}

/**
 * Scrolls the dialog just far enough for the row that was moved or added to [index] to be wholly in view, so that the
 * card the user is arranging stays under their eyes and the button they tapped stays under their finger for the next
 * tap, and a new card, added at the end of a list that may reach past the dialog, is there to be typed into.
 *
 * The positions are the list's layout info rather than the card's own coordinates, since the card is placed at its
 * animated position until its placement animation ends, while the layout info already holds the place it is going
 * to; it is waited for here because the move has only been composed when this starts. A row moved past the edge of
 * the list is not laid out at all, but it can only have gone one place past the last (or before the first) visible
 * item, and [size] - what it measured before the move, or what the last row measures for a new one - says how far
 * that reaches. A card taller than the list is aligned by its top.
 */
private suspend fun LazyListState.reveal(key: Any, index: Int, size: Int) {
    val info = snapshotFlow { layoutInfo }.first { info ->
        info.visibleItemsInfo.firstOrNull { it.key == key }.let { it == null || it.index == index }
    }
    val visibleItems = info.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    val item = visibleItems.firstOrNull { it.key == key }
    val top = when {
        item != null -> item.offset
        index < visibleItems.first().index -> visibleItems.first().offset - info.mainAxisItemSpacing - size
        else -> visibleItems.last().offset + visibleItems.last().size + info.mainAxisItemSpacing
    }
    val bottom = top + (item?.size ?: size)
    val distance = when {
        top < info.viewportStartOffset -> top - info.viewportStartOffset
        bottom > info.viewportEndOffset -> minOf(bottom - info.viewportEndOffset, top - info.viewportStartOffset)
        else -> 0
    }
    if (distance != 0) {
        animateScrollBy(distance.toFloat())
    }
}

private fun <T> List<T>.swapped(first: Int, second: Int) = toMutableList().apply {
    this[first] = this[second].also { this[second] = this[first] }
}

private val songLinkRowsSaver = listSaver<List<SongLinkRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.link.url, it.link.name.orEmpty()) } },
    restore = { values -> values.chunked(2).map { ChordProLink(url = it[0], name = it[1].takeIf(String::isNotEmpty)) }.toRows() },
)

/**
 * The links a Manage links draft writes, or null while Save has nothing valid to write. A row left wholly empty - the
 * seeded one, or one added and never filled - is no link at all and is left out rather than blocking Save; a row with
 * a name but no address, an address the file would not keep, or two rows with the same address block it, and so does
 * a draft that writes what the song already has.
 */
internal fun songLinksToSave(draft: List<ChordProLink>, offered: List<ChordProLink>): List<ChordProLink>? {
    val kept = draft.filterNot { it.url.isBlank() && it.name.isNullOrBlank() }
    val urls = kept.map { ChordProLinks.usableUrl(it.url) }
    if (urls.any { it == null } || urls.distinct().size != urls.size) return null
    return kept.takeIf { links -> links.map { it.normalized() } != offered.map { it.normalized() } }
}

/** Compare the address and label as they will be written, keeping link order significant. */
private fun ChordProLink.normalized() = copy(
    url = ChordProLinks.usableUrl(url).orEmpty(),
    name = name?.filterNot { it == '{' || it == '}' }?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
)
