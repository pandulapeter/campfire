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

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.ListAnchor
import com.pandulapeter.campfire.presentation.ui.components.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.belowAppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.anchoredTransition
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.searchTravelSpec
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.playing.effectiveTempo
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songLabelActions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SongList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    placeholder: Placeholder?,
    isSearchOpen: Boolean,
    songGroups: SongGroups,
    columnCount: Int,
    hasLoadedLibrary: Boolean,
    contentPadding: PaddingValues,
    appBarOverlap: () -> AppBarOverlap,
) {
    // Keep section boundaries (including incomplete grid rows) while their header rows collapse. This makes
    // opening and closing exact reverses and keeps every card in the active section in its original column.
    val groups = songGroups.groups
    val appBarHeightPx = with(LocalDensity.current) { LIST_APP_BAR_HEIGHT.toPx() }
    val searchScroll = remember(listState, columnCount, appBarHeightPx) { SongSearchScrollAnchor(isSearchOpen) }
    SideEffect {
        searchScroll.update(
            open = isSearchOpen,
            contents = songGroups,
            isScrolling = listState.isScrollInProgress,
            inset = (appBarHeightPx * (1f - appBarOverlap().coverage)).roundToInt(),
        ) {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "song" }?.let { card ->
                val activeGroup = groups.indexOfFirst { group -> group.songs.any { songItemKey(it) == card.key } }
                SongSearchScrollAnchor.Snapshot(
                    cardIndex = card.index,
                    cardTop = card.offset.y,
                    position = SongSearchScrollAnchor.Position(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                    trailingHeaderCount = groups.drop(activeGroup + 1).count { it.header != null },
                )
            }
        }
    }
    // One collector both lets the anchor go and releases the room it kept: a flow of the spacer's visibility alone
    // would not emit again for a spacer already out of sight when the scroll let go, and a second collector could run
    // before the first in a frame, while the anchor still holds its position. Pairing the two re-evaluates at both ends
    // of a scroll.
    LaunchedEffect(listState, searchScroll) {
        snapshotFlow { listState.isScrollInProgress to listState.layoutInfo.visibleItemsInfo.any { it.key == SEARCH_ANCHOR_SPACE_KEY } }
            .collect { (isScrolling, isSpaceVisible) ->
                if (isScrolling) searchScroll.letGoForScroll()
                searchScroll.releaseTrailingSpaceIfOutOfSight(isSpaceVisible)
            }
    }
    val areHeadersCollapsing by remember(isSearchOpen, groups, appBarOverlap) {
        derivedStateOf { groups.any { it.header != null } && (isSearchOpen || appBarOverlap().coverage < 1f) }
    }
    val itemPlacementSpec = searchTravelSpec(visibilityThreshold = IntOffset.VisibilityThreshold)
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songFilter by viewModel.songFilter.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    val tempos by viewModel.tempos.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.labelsOnEverySong.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
    // The chords switched off take them out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    // The tempo is what the click plays, so it goes with the metronome.
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val filePicker = LocalFilePicker.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    // One boundary and label per group, in lazy-grid item coordinates, for the fast scroller and pushed header.
    val sectionIndex = remember(groups) {
        SongSectionIndex(groups.map { SongSectionIndex.Group(songCount = it.songs.size, header = it.header) })
    }
    val coveredHeightFraction = remember(appBarOverlap) { { appBarOverlap().coverage } }
    // Zero-height header slots are not scroll distance: the first card can be item 1 at the exact top.
    val firstCardIndex = remember(groups, placeholder) {
        var index = if (placeholder == null) 0 else 1
        for (group in groups) {
            if (group.header != null) index++
            if (group.songs.isNotEmpty()) break
        }
        index
    }
    val topFade = rememberListTopFade(listState, coveredHeightFraction = coveredHeightFraction, firstCardIndex = firstCardIndex)
    // Remembered, since a new modifier every time the list recomposes would recompose the grid with it.
    val gridModifier = remember(topFade, searchScroll, listState, appBarOverlap) {
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val inset = LIST_APP_BAR_HEIGHT.toPx() * (1f - appBarOverlap().coverage)
            searchScroll.positionFor(inset.roundToInt(), isScrolling = listState.isScrollInProgress)?.let { position ->
                listState.requestScrollToItem(position.index, position.offset)
            }
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }.listTopFadeViewport(topFade)
    }

    // A tag or a language tapped on a row is one the song carries, so the list it filters to still holds that song,
    // and the song stays where the tap left it rather than the list going back to the top.
    val filterAnchor = remember { ListAnchor() }
    val onTagClicked: (Song, String) -> Unit = remember(viewModel, listState) {
        { song, tag ->
            filterAnchor.set(listState, songItemKey(song))
            viewModel.toggleTagFilter(tag)
        }
    }
    val onLanguageClicked: (Song, String) -> Unit = remember(viewModel, listState) {
        { song, language ->
            filterAnchor.set(listState, songItemKey(song))
            viewModel.toggleLanguageFilter(language)
        }
    }
    ScrollToTopWhenChanged(
        listState = listState,
        // The key the list was built for rather than one read off the filter and the preferences, which change before
        // the list does: the two arrive together, so the anchor is taken and consumed in one composition, and a filter
        // that leaves every song where it was still delivers a list for it to be consumed by.
        key = songGroups.filterKey,
        contents = songGroups,
        anchor = filterAnchor,
        itemIndex = { key -> groups.itemIndexOf(key, hasPlaceholder = placeholder != null) },
    )

    // A lazy grid holds on to the key of its first visible item across a change of its contents, which is right for
    // an edit and wrong for the library arriving: the read publishes a batch at a time in the order the files are
    // listed rather than the order they are sorted in, so a later batch lands songs above the ones already showing and
    // the grid follows its first row down, opening the app on a list that is already scrolled. Until the last batch
    // is in, the position is held by index instead - unless the user is scrolling it themselves, which a request
    // would cancel.
    if (!hasLoadedLibrary) {
        SideEffect {
            if (!listState.isScrollInProgress) {
                listState.requestScrollToItem(
                    index = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column.
    val headerEndPadding = FAST_SCROLLER_WIDTH + contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = gridModifier.bounceScrollableContent(listState, pull = topFade.overscrollPull),
            state = listState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH, extraBottom = 8.dp),
        ) {
            placeholder?.let {
                item(
                    key = "placeholder",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "placeholder",
                ) {
                    ListPlaceholder(
                        modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec).fillMaxWidth(),
                        placeholder = it,
                        onRetry = viewModel::refresh,
                        onNewSong = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(DialogType.NewSong) }
                        },
                        onDemoLibrary = if (isPerformanceModeEnabled) null else {
                            { viewModel.importDemoLibrary() }
                        },
                        onImport = if (isPerformanceModeEnabled) null else {
                            { viewModel.importFiles(filePicker) }
                        },
                    )
                }
            }
            groups.forEach { group ->
                group.header?.let { header ->
                    stickyHeader(
                        key = "header_${header.key}",
                        contentType = "header",
                    ) { headerIndex ->
                        val headerState = rememberSectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
                                .anchoredTransition(filterAnchor, listState, "header_${header.key}")
                                .collapseSearchHeader { appBarOverlap().coverage },
                            state = { headerState.value },
                            endPadding = headerEndPadding,
                            text = header.displayText(),
                            onClick = if (isSearchOpen) null else { { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } } },
                            opacity = { if (headerState.value.visibleFraction < 1f) 0f else appBarOverlap().coverage },
                            appBarOverlap = appBarOverlap,
                        )
                    }
                }
                itemsIndexed(
                    items = group.songs,
                    key = { _, song -> songItemKey(song) },
                    contentType = { _, _ -> "song" },
                ) { songIndex, song ->
                    val actionsMenuState = rememberOverflowMenuState()
                    // A song opened from the library is transposed, capoed and timed in the preferences, so those are
                    // the only values this list knows about: the setlists each hold their own. The key is remembered,
                    // since the row is composed again as every scroll starts and ends and it is a whole transposition
                    // to work out again.
                    val transposition = transpositions[song.fileName, null]
                    val capo = effectiveCapo(song = song, setlistFileName = null, capos = capos)
                    val tempo = effectiveTempo(song = song, setlistFileName = null, tempos = tempos)
                    val key = remember(song.key, song.transpose, transposition, capo, chordSpelling) {
                        viewModel.renderKey(song = song, transposition = transposition, capo = capo.fret, spelling = chordSpelling)
                    }
                    // Worked out here rather than inside the row's actions, so that they capture what changes for this row
                    // alone rather than the set that is new on every write to any setlist.
                    val isInSetlist = song.fileName in songFileNamesInSetlists
                    // The placement animation changes as a scroll starts and ends, so it goes on a box of its own: on
                    // the row, it would be a new modifier each time, and the whole row would be composed again with it.
                    Box(
                        modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
                            .anchoredTransition(filterAnchor, listState, songItemKey(song)),
                    ) {
                        SongListItem(
                            modifier = Modifier.fadingUnderListTop(topFade),
                            song = song,
                            cardPadding = songCardPadding(songIndex, columnCount),
                            key = key,
                            tempo = tempo.displayedBpm.takeIf { isMetronomeEnabled },
                            shouldShowChords = shouldShowChords,
                            coverArtUrl = song.coverArtUrl?.takeIf { isCoverArtEnabled },
                            labelsOnEverySong = labelsOnEverySong,
                            songFilter = songFilter,
                            onTagClicked = { onTagClicked(song, it) },
                            onLanguageClicked = { onLanguageClicked(song, it) },
                            onClick = {
                                keyboardController?.hide()
                                viewModel.openSong(song)
                            },
                            // A shortcut to the row's own overflow menu, where holding a row is a natural way to ask for
                            // it: the same menu, hanging from the same button, since the same entries shown two different
                            // ways would read as two different things. In performance mode there is nothing to open
                            // either way, so a row does nothing but open a song.
                            onLongClick = if (isDesktopPlatform || isPerformanceModeEnabled) {
                                null
                            } else {
                                {
                                    keyboardController?.hide()
                                    actionsMenuState.open()
                                }
                            },
                            actions = if (isPerformanceModeEnabled) {
                                null
                            } else {
                                {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(-ACTION_BUTTON_OVERLAP),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (userPreferences?.areSetlistsEnabled != false) {
                                            SetlistAssignmentsButton(
                                                viewModel = viewModel,
                                                song = song,
                                                isInSetlist = isInSetlist,
                                            )
                                        }
                                        SongActions(
                                            state = actionsMenuState,
                                            viewModel = viewModel,
                                            song = song,
                                            isDeletable = true,
                                            fileEditItems = songLabelActions(
                                                viewModel = viewModel,
                                                song = song,
                                                target = SongEditTarget.File(song.fileName),
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            // Near the end, collapsing later headers can shorten the scroll range below the anchor's position.
            // Keep just that lost tail space until it has scrolled out of sight, or the search changes, or the headers return.
            item(key = SEARCH_ANCHOR_SPACE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "search_anchor_space") {
                Spacer(Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val removedHeight = (LIST_APP_BAR_HEIGHT.toPx() * (1f - appBarOverlap().coverage)).roundToInt()
                    val height = searchScroll.trailingHeaderCount * removedHeight
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                })
            }
        }
        // The grid clips at its top edge. Draw the outgoing header here while it is pushed, so its fade can continue
        // past that edge under the transparent app bar rather than ending at it; a bar that has filled in is drawn
        // over it.
        PushedSongSectionHeader(
            listState = listState,
            sectionIndex = sectionIndex,
            endPadding = headerEndPadding,
            appBarOverlap = appBarOverlap,
        )
        FastScroller(
            modifier = Modifier.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap).padding(contentPadding.only(top = true, end = true, bottom = true)),
            gridState = listState,
            labelForItem = { if (isSearchOpen) null else sectionIndex.labelForItem(it) },
        )
    }
}

internal fun songItemKey(song: Song) = "song_${song.fileName}"

/** The grid index of the item with [key], counted the way [SongList] emits its items. */
internal fun List<SongGroup>.itemIndexOf(key: Any, hasPlaceholder: Boolean): Int? {
    var index = if (hasPlaceholder) 1 else 0
    forEach { group ->
        if (group.header != null) index++
        val songIndex = group.songs.indexOfFirst { songItemKey(it) == key }
        if (songIndex >= 0) return index + songIndex
        index += group.songs.size
    }
    return null
}

/** The key of the spacer that keeps the room the search anchor needs at the end of the list. */
private const val SEARCH_ANCHOR_SPACE_KEY = "search_anchor_space"

/** Measures a header at its normal height, then gives its row back on the very same frames the bar takes it. */
private fun Modifier.collapseSearchHeader(fraction: () -> Float) = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    // Subtract the exact rounded bar inset; independently rounding both halves can move the first row by a pixel.
    val removedHeight = (LIST_APP_BAR_HEIGHT.toPx() * (1f - fraction().coerceIn(0f, 1f))).roundToInt()
    val height = (placeable.height - removedHeight).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}
