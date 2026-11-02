/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor
import com.pandulapeter.campfire.presentation.ui.components.list.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.list.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.list.anchoredTransition
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.list.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.FirstRunController
import com.pandulapeter.campfire.presentation.ui.platform.ClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.importing.PastedSongController
import com.pandulapeter.campfire.presentation.ui.state.songs.RandomPicksDraw
import com.pandulapeter.campfire.presentation.ui.state.songs.SONG_HEADER_CONTENT_TYPE
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroupHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [SongList]'s [placeholder], with the ways into a library it offers outside performance mode. */
internal fun LazyGridScope.songListPlaceholder(
    placeholder: Placeholder?,
    listState: LazyGridState,
    hasLoadedLibrary: Boolean,
    itemPlacementSpec: FiniteAnimationSpec<IntOffset>,
    isPerformanceModeEnabled: Boolean,
    libraryState: LibraryState,
    dialogHost: DialogHost,
    firstRunController: FirstRunController,
    importController: ImportController,
    pastedSongController: PastedSongController,
    filePicker: FilePicker,
    clipboardReader: ClipboardTextReader,
) {
    placeholder?.let {
        item(
            key = "placeholder",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "placeholder",
        ) {
            ListPlaceholder(
                modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = itemPlacementSpec).fillMaxWidth(),
                placeholder = it,
                onRetry = libraryState::refresh,
                onNewSong = if (isPerformanceModeEnabled) null else {
                    { dialogHost.showDialog(DialogType.NewSong) }
                },
                onDemoLibrary = if (isPerformanceModeEnabled) null else {
                    { firstRunController.importDemoLibrary() }
                },
                onImport = if (isPerformanceModeEnabled) null else {
                    { importController.importFiles(filePicker) }
                },
                onPaste = if (isPerformanceModeEnabled) null else {
                    { pastedSongController.newSongFromClipboard(clipboardReader) }
                },
            )
        }
    }
}

/**
 * The pinned header of a group of [SongList], if it has one, which scrolls the list to its group while the search is
 * closed.
 */
internal fun LazyGridScope.songGroupHeader(
    groupHeader: SongGroupHeader?,
    listState: LazyGridState,
    hasLoadedLibrary: Boolean,
    itemPlacementSpec: FiniteAnimationSpec<IntOffset>,
    filterAnchor: ListAnchor,
    headerEndPadding: () -> Dp,
    selection: Set<String>,
    isSearchOpen: Boolean,
    coroutineScope: CoroutineScope,
    randomPicksDraw: RandomPicksDraw,
    appBarOverlap: () -> AppBarOverlap,
) {
    groupHeader?.let { header ->
        stickyHeader(
            key = header.itemKey,
            contentType = SONG_HEADER_CONTENT_TYPE,
        ) { headerIndex ->
            val headerState = rememberSectionHeaderState(listState, headerIndex)
            SectionHeader(
                modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = itemPlacementSpec)
                    .anchoredTransition(filterAnchor, listState, header.itemKey),
                state = { headerState.value },
                endPadding = headerEndPadding,
                text = header.displayText(selectionCount = selection.size),
                onClick = if (isSearchOpen) null else { { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } } },
                action = if (header == SongGroupHeader.RandomPicks) {
                    { actionModifier, buttonModifier ->
                        RandomPicksRefreshButton(
                            modifier = actionModifier,
                            buttonModifier = buttonModifier,
                            onRefresh = randomPicksDraw::reshuffle,
                        )
                    }
                } else {
                    null
                },
                opacity = { if (headerState.value.visibleFraction < 1f) 0f else appBarOverlap().coverage },
                appBarOverlap = appBarOverlap,
            )
        }
    }
}
