/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.print_files_missing
import com.pandulapeter.campfire.presentation.resources.print_files_setlist_description
import com.pandulapeter.campfire.presentation.resources.print_files_song_description
import com.pandulapeter.campfire.presentation.resources.print_files_songs_description
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.filesFileName
import org.jetbrains.compose.resources.painterResource

/**
 * What a song's ChordPro or a zip writes, in the preview's place: a summary that never scrolls, since the file's text or
 * a list of the zip's files would be a second scrolling area beside the options. The item's name, what the file holds,
 * and how many songs are left out, their files missing; a song whose file could not be read has nothing to save, which
 * it says instead. In a pane of its own it is centred in it; in the options' list ([isInList]) it is a row of the list,
 * as tall as what it says.
 */
@Composable
internal fun FilesPreview(
    modifier: Modifier,
    source: PrintSource,
    isInList: Boolean,
    bottomPadding: Dp,
) {
    val contentModifier = (if (isInList) modifier.fillMaxWidth() else modifier.fillMaxSize()).padding(bottom = bottomPadding)
    if (!source.hasSeveralSongs && source.songs.firstOrNull()?.text == null) {
        FilesPreviewMessage(contentModifier, stringResource(Res.string.print_missing), isInList)
    } else {
        FilesSummary(modifier = contentModifier, source = source, isInList = isInList)
    }
}

@Composable
private fun FilesPreviewMessage(
    modifier: Modifier,
    message: String,
    isInList: Boolean,
) = if (isInList) {
    Text(message, modifier.padding(start = 16.dp, end = 16.dp, top = LIST_PREVIEW_TOP_PADDING, bottom = 8.dp))
} else {
    Box(modifier.padding(PAGE_MARGIN), contentAlignment = Alignment.Center) { Text(message) }
}

/**
 * The file in a few lines: its icon, the name it is saved under ([filesFileName]), what it holds and what reads it, and
 * how many of its songs are left out; centred in a pane, a list row's icon and lines
 * in the list.
 */
@Composable
private fun FilesSummary(
    modifier: Modifier,
    source: PrintSource,
    isInList: Boolean,
) {
    val included = source.songs.count { it.text != null }
    val missing = source.songs.size - included
    val description = when (source.kind) {
        PrintSource.Kind.SONG -> stringResource(Res.string.print_files_song_description)
        PrintSource.Kind.SETLIST -> pluralStringResource(Res.plurals.print_files_setlist_description, included, included)
        PrintSource.Kind.SONGS -> pluralStringResource(Res.plurals.print_files_songs_description, included, included)
    }
    val title = filesFileName(source)
    val missingNote = pluralStringResource(Res.plurals.print_files_missing, missing, missing)
    val textAlign = if (isInList) TextAlign.Start else TextAlign.Center
    val lines: @Composable () -> Unit = {
        Text(
            text = title,
            modifier = Modifier.widthIn(max = MAX_SUMMARY_WIDTH),
            style = MaterialTheme.typography.titleMedium,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SummaryLine(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign)
        if (missing > 0) SummaryLine(text = missingNote, color = MaterialTheme.colorScheme.error, textAlign = textAlign)
    }
    if (isInList) {
        Row(
            modifier = modifier.padding(start = 16.dp, end = 16.dp, top = LIST_PREVIEW_TOP_PADDING, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(kind = source.kind)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { lines() }
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            FileIcon(kind = source.kind)
            lines()
        }
    }
}

@Composable
private fun FileIcon(
    kind: PrintSource.Kind,
) = Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Icon(
        painter = painterResource(if (kind == PrintSource.Kind.SETLIST) Res.drawable.ic_setlists else Res.drawable.ic_songs),
        contentDescription = null,
        modifier = Modifier.padding(12.dp).size(24.dp),
    )
}

@Composable
private fun SummaryLine(
    text: String,
    color: Color,
    textAlign: TextAlign,
) = Text(
    text = text,
    modifier = Modifier.widthIn(max = MAX_SUMMARY_WIDTH),
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    textAlign = textAlign,
)

private val MAX_SUMMARY_WIDTH = 480.dp
