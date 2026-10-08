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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.resources.print_no_songs
import com.pandulapeter.campfire.presentation.resources.print_zip_contents
import com.pandulapeter.campfire.presentation.resources.print_zip_manifest
import com.pandulapeter.campfire.presentation.resources.print_zip_manifest_description
import com.pandulapeter.campfire.presentation.resources.print_zip_missing
import com.pandulapeter.campfire.presentation.resources.print_zip_song_description
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import org.jetbrains.compose.resources.painterResource

/**
 * What a song's ChordPro or a setlist's zip writes, in the preview's place: a song's file as the library holds it,
 * unwrapped and in the monospaced font the editor shows it in, since only that keeps the columns of a tab lined up, or the
 * files the zip holds (see [ZipContents]). A song whose file could not be read has nothing to show, and nothing to save
 * either, and a setlist with none of its songs chosen says so, as the PDF's preview does.
 */
@Composable
internal fun FilesPreview(
    modifier: Modifier,
    source: PrintSource,
    setlistFileName: String?,
    selected: Set<Int>,
    bottomPadding: Dp,
    isBottomAnchored: Boolean,
) {
    val text = source.songs.firstOrNull()?.text
    when {
        source.isSetlist && source.songs.isNotEmpty() && selected.isEmpty() -> FilesPreviewMessage(modifier, stringResource(Res.string.print_no_songs))
        source.isSetlist -> ZipContents(
            modifier = modifier,
            setlistFileName = setlistFileName.orEmpty(),
            songs = source.songs.filterIndexed { index, _ -> index in selected },
            bottomPadding = bottomPadding,
            isBottomAnchored = isBottomAnchored,
        )
        text == null -> FilesPreviewMessage(modifier, stringResource(Res.string.print_missing))
        else -> {
            val scrollState = rememberScrollState()
            Text(
                text = text,
                modifier = modifier
                    .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
                    .bounceVerticalScroll(scrollState)
                    .bounceHorizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalMonospaceFontFamily.current),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun FilesPreviewMessage(
    modifier: Modifier,
    message: String,
) = Box(modifier.padding(PAGE_MARGIN), contentAlignment = Alignment.Center) { Text(message) }

/**
 * The files of a setlist's zip, as an import will find them: the setlist manifest under its own file name, which keeps
 * the running order and every song's key and names only the [songs] that were chosen, and each of those as the ChordPro
 * file it is in the library. A chosen song whose file is missing stays named by the manifest but has no file of its own,
 * which its dimmed row says.
 */
@Composable
private fun ZipContents(
    modifier: Modifier,
    setlistFileName: String,
    songs: List<PrintSong>,
    bottomPadding: Dp,
    isBottomAnchored: Boolean,
) {
    val state = rememberLazyListState()
    // Beside the options the list ends at the window's bottom, where nothing fades; above more options it is a list in
    // the middle of something, which says it goes on at both ends.
    val edgeFade = if (isBottomAnchored) {
        Modifier.fadingTopEdge(state, MaterialTheme.colorScheme.background)
    } else {
        Modifier.fadingVerticalEdges(state, MaterialTheme.colorScheme.background)
    }
    LazyColumn(modifier.bounceScrollableContent(state).then(edgeFade), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        item {
            SettingsSectionTitle(text = stringResource(Res.string.print_zip_contents))
            ZipFileRow(
                icon = painterResource(Res.drawable.ic_setlists),
                title = stringResource(Res.string.print_zip_manifest),
                description = textResource(Res.string.print_zip_manifest_description, setlistFileName),
            )
        }
        // One list under one heading: the songs are in the zip as much as the manifest is, and a heading of their own
        // read as if the first one held only the manifest.
        items(songs) { entry ->
            ZipFileRow(
                icon = painterResource(Res.drawable.ic_songs),
                title = "${entry.index}. ${entry.title}",
                description = if (entry.text == null) {
                    stringResource(Res.string.print_zip_missing)
                } else {
                    textResource(Res.string.print_zip_song_description, entry.fileName)
                },
                isIncluded = entry.text != null,
            )
        }
    }
}

/** One file of a setlist's zip, dimmed where it is named by the setlist but left out of the zip, its file missing. */
@Composable
private fun ZipFileRow(
    icon: Painter,
    title: String,
    description: String?,
    isIncluded: Boolean = true,
) = ListItem(
    modifier = Modifier.alpha(if (isIncluded) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
)
