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

import androidx.compose.runtime.CompositionLocalProvider
import com.pandulapeter.campfire.presentation.ui.components.list.listItemTrailingKeylineAdjustment
import com.pandulapeter.campfire.presentation.ui.components.list.LocalListItemTrailingKeylineAdjustment
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_select
import com.pandulapeter.campfire.presentation.resources.songs_select_multiple
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_OUTER_PADDING
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongCardOptions
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.SongSection
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.measuresFastScrollerColumn
import com.pandulapeter.campfire.presentation.ui.components.list.rememberFastScrollerColumn
import com.pandulapeter.campfire.presentation.ui.components.list.withSectionStartRoom
import com.pandulapeter.campfire.presentation.ui.components.list.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor
import com.pandulapeter.campfire.presentation.ui.components.list.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.list.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.Placeholder
import com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.list.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.appBar.belowAppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.anchoredTransition
import com.pandulapeter.campfire.presentation.ui.components.list.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.list.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.list.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.list.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCard.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.appBar.searchTravelSpec
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
import kotlinx.coroutines.launch
import com.pandulapeter.campfire.presentation.ui.components.FloatingControlVisibility
import androidx.compose.material3.FloatingActionButtonDefaults

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
    isSelecting: Boolean,
    selection: Set<String>,
) {
    val songActions = rememberSongActionHandler(viewModel)
    val selectMultipleTitle = stringResource(Res.string.songs_select_multiple)
    val selectSongsIcon = painterResource(Res.drawable.ic_select)
    // An open search runs the sections together, so no section ends in a short row, keeping the card at the top left of
    // the list in its column; KeepCardsInPlaceAcrossSearch keeps its row.
    val searchOpening = rememberSearchOpening(listState, songGroups, isSearchOpen, columnCount)
    val groups = remember(songGroups, isSearchOpen, searchOpening, columnCount) {
        if (isSearchOpen) songGroups.groups.withoutSectionHeaders(anchor = searchOpening?.slotFor(songGroups, columnCount)) else songGroups.groups
    }
    val coroutineScope = rememberCoroutineScope()
    val filterAnchor = remember { ListAnchor() }
    val itemPlacementSpec = searchTravelSpec(visibilityThreshold = IntOffset.VisibilityThreshold)
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songFilter by viewModel.songListState.songFilter.collectAsStateWithLifecycle()
    val playingOverrides by viewModel.overrides.playingOverrides.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.libraryState.labelsOnEverySong.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val songFileNamesInSetlists by viewModel.libraryState.songFileNamesInSetlists.collectAsStateWithLifecycle()
    val librarySummary by viewModel.libraryState.librarySummary.collectAsStateWithLifecycle()
    val isImporting by viewModel.importController.isImporting.collectAsStateWithLifecycle()
    val cardOptions = SongCardOptions.of(userPreferences = userPreferences, details = userPreferences?.songCardDetails ?: UserPreferences.CardDetails.SongsDefault)
    val filePicker = LocalFilePicker.current
    val clipboardReader = rememberClipboardTextReader()
    val keyboardController = LocalSoftwareKeyboardController.current
    // One boundary and label per group, in lazy-grid item coordinates, for the fast scroller and pushed header.
    val sectionIndex = remember(groups) {
        SongSectionIndex(
            groups.mapIndexed { index, group ->
                SongSectionIndex.Group(
                    itemCount = group.songs.size + (if (group.leadingGap > 0) 1 else 0) + if (groups.hasSelectionDividerAfter(index)) 1 else 0,
                    header = group.header,
                )
            },
        )
    }
    val coveredHeightFraction = remember(appBarOverlap) { { appBarOverlap().coverage } }
    val firstCardIndex = remember(groups, placeholder) { groups.firstCardIndex(hasPlaceholder = placeholder != null) }
    val topFade = rememberListTopFade(
        listState = listState,
        coveredHeightFraction = coveredHeightFraction,
        firstCardIndex = firstCardIndex,
        sectionHeaderContentType = SONG_HEADER_CONTENT_TYPE,
    )
    // Remembered, since a new modifier every time the list recomposes would recompose the grid with it.
    val searchCardHold = remember { SearchCardHold() }
    val gridModifier = remember(topFade) {
        Modifier.fillMaxSize().listTopFadeViewport(topFade).holdingSearchCard(searchCardHold, listState, filterAnchor)
    }

    // A tag or a language tapped on a row is one the song carries, so the list it filters to still holds that song,
    // and the song stays where the tap left it rather than the list keeping whichever row happened to be first.
    val onTagClicked: (itemKey: String, String) -> Unit = remember(viewModel, listState) {
        { itemKey, tag ->
            filterAnchor.set(listState, itemKey)
            viewModel.songListState.toggleTagFilter(tag)
        }
    }
    val onLanguageClicked: (itemKey: String, String) -> Unit = remember(viewModel, listState) {
        { itemKey, language ->
            filterAnchor.set(listState, itemKey)
            viewModel.songListState.toggleLanguageFilter(language)
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
        topKey = songGroups.orderKey,
    )
    KeepCardsInPlaceUnderTick(
        listState = listState,
        songGroups = songGroups,
        groups = groups,
        hasPlaceholder = placeholder != null,
        anchor = filterAnchor,
    )
    KeepCardsInPlaceAcrossSearch(
        listState = listState,
        isSearchOpen = isSearchOpen,
        songGroups = songGroups,
        groups = groups,
        opening = searchOpening,
        hasPlaceholder = placeholder != null,
        appBarOverlap = appBarOverlap,
        anchor = filterAnchor,
        hold = searchCardHold,
    )
    // The last row's box would otherwise end up under the Done button, which stands over the end of the list. Read by
    // the paddings while they are laid out, never here: it changes on every frame of its spring.
    val selectionBottomPaddingState = animateDpAsState(if (isSelecting) DONE_BUTTON_ROOM else 0.dp)
    val selectionBottomPadding = remember(selectionBottomPaddingState) { { selectionBottomPaddingState.value } }

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

    val scrollerColumn = rememberFastScrollerColumn(gridState = listState, position = viewModel.songListState.songsScrollPosition)
    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column. Both follow the column's spring, so they are read while laid out.
    val layoutDirection = LocalLayoutDirection.current
    val headerEndPadding = remember(scrollerColumn, contentPadding, layoutDirection) {
        { scrollerColumn.width() + contentPadding.calculateEndPadding(layoutDirection) }
    }
    val trailingKeylineAdjustment = remember(scrollerColumn) { { listItemTrailingKeylineAdjustment(scrollerColumn.width()) } }
    CompositionLocalProvider(LocalListItemTrailingKeylineAdjustment provides trailingKeylineAdjustment) {
        Box(modifier = modifier) {
            LazyVerticalGrid(
                columns = ListColumns(columnCount),
                modifier = gridModifier.bounceScrollableContent(listState, pull = topFade.overscrollPull)
                    .measuresFastScrollerColumn(column = scrollerColumn, gridState = listState, isAnimated = hasLoadedLibrary),
                state = listState,
                contentPadding = contentPadding.only(
                    start = true,
                    end = true,
                    bottom = true,
                    extraBottom = 8.dp,
                    deferredExtraEnd = scrollerColumn.width,
                    deferredExtraBottom = selectionBottomPadding,
                ).withSectionStartRoom(appBarOverlap),
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
                            onRetry = viewModel.libraryState::refresh,
                            onNewSong = if (isPerformanceModeEnabled) null else {
                                { viewModel.dialogHost.showDialog(DialogType.NewSong) }
                            },
                            onDemoLibrary = if (isPerformanceModeEnabled) null else {
                                { viewModel.firstRunController.importDemoLibrary() }
                            },
                            onImport = if (isPerformanceModeEnabled) null else {
                                { viewModel.importController.importFiles(filePicker) }
                            },
                            onPaste = if (isPerformanceModeEnabled) null else {
                                { viewModel.pastedSongController.newSongFromClipboard(clipboardReader) }
                            },
                        )
                    }
                }
                groups.forEachIndexed { groupIndex, group ->
                    // An open search takes the headers away, and the cards take their artists back from them.
                    val isArtistShown = isSearchOpen || (group.header as? SongGroupHeader.Section)?.header !is SongSection.Header.Artist
                    group.header?.let { header ->
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
                                opacity = { if (headerState.value.visibleFraction < 1f) 0f else appBarOverlap().coverage },
                                appBarOverlap = appBarOverlap,
                            )
                        }
                    }
                    if (group.leadingGap > 0) {
                        item(
                            key = SEARCH_GAP_KEY,
                            span = { GridItemSpan(group.leadingGap.coerceAtMost(maxLineSpan)) },
                            contentType = "search_gap",
                        ) {}
                    }
                    itemsIndexed(
                        items = group.songs,
                        key = { _, song -> group.itemKey(song) },
                        contentType = { _, _ -> SONG_CONTENT_TYPE },
                    ) { songIndex, song ->
                        val itemKey = group.itemKey(song)
                        // The placement animation changes as a scroll starts and ends, so it goes on a box of its own: on
                        // the row, it would be a new modifier each time, and the whole row would be composed again with it.
                        Box(
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = itemPlacementSpec)
                                .anchoredTransition(filterAnchor, listState, itemKey),
                        ) {
                            // Worked out here rather than inside the row's actions, so that they capture what changes for this row
                            // alone rather than the set that is new on every write to any setlist.
                            SongListRow(
                                viewModel = viewModel,
                                songActions = songActions,
                                song = song,
                                itemKey = itemKey,
                                cardPadding = songCardPadding(group.leadingGap + songIndex, columnCount),
                                topFade = topFade,
                                playingOverrides = playingOverrides,
                                cardOptions = cardOptions,
                                labelsOnEverySong = labelsOnEverySong,
                                isArtistShown = isArtistShown,
                                songFilter = songFilter,
                                isInSetlist = song.fileName in songFileNamesInSetlists,
                                isTicked = song.fileName in selection,
                                isSelecting = isSelecting,
                                isPerformanceModeEnabled = isPerformanceModeEnabled,
                                areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
                                selectMultipleTitle = selectMultipleTitle,
                                selectSongsIcon = selectSongsIcon,
                                onTagClicked = onTagClicked,
                                onLanguageClicked = onLanguageClicked,
                            )
                        }
                    }
                    // The two parts are told apart the way the sheets tell them apart, and an open search takes away every
                    // header there is, the Selection one included, which leaves the divider the only thing between them.
                    if (groups.hasSelectionDividerAfter(groupIndex)) {
                        item(
                            key = SELECTION_DIVIDER_KEY,
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "selection_divider",
                        ) {
                            // Faded under the top of the list like a card, or it would be drawn across the pinned header and
                            // the bar's pill as it scrolls under them.
                            HorizontalDivider(
                                modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = itemPlacementSpec)
                                    .anchoredTransition(filterAnchor, listState, SELECTION_DIVIDER_KEY)
                                    .fadingUnderListTop(topFade)
                                    .padding(horizontal = SONG_CARD_OUTER_PADDING, vertical = SELECTION_DIVIDER_PADDING),
                            )
                        }
                    }
                }
                // After the songs rather than before them, so that the item indexes the scroller, the anchors, and the search
                // keep for the songs hold whether or not the card is there.
                if (
                    isAddSongsHintShown(
                        librarySongCount = librarySummary?.songCount,
                        hasPlaceholder = placeholder != null,
                        isSearchOpen = isSearchOpen,
                        isSelecting = isSelecting,
                        isPerformanceModeEnabled = isPerformanceModeEnabled,
                    )
                ) {
                    item(key = ADD_SONGS_HINT_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "add_songs_hint") {
                        AddSongsHintCard(
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = itemPlacementSpec)
                                .fadingUnderListTop(topFade),
                            isEnabled = !isImporting,
                            onPaste = { viewModel.pastedSongController.newSongFromClipboard(clipboardReader) },
                            onImport = { viewModel.importController.importFiles(filePicker) },
                        )
                    }
                }
            }
            // The grid clips at its top edge. Draw the outgoing header here while it is pushed, so its fade can continue
            // past that edge under the transparent app bar rather than ending at it; a bar that has filled in is drawn
            // over it.
            PushedSongSectionHeader(
                listState = listState,
                sectionIndex = sectionIndex,
                contentPadding = contentPadding,
                endPadding = headerEndPadding,
                selectionCount = selection.size,
                appBarOverlap = appBarOverlap,
            )
            FloatingControlVisibility(
                isVisible = isSelecting,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(contentPadding.only(end = true, bottom = true))
                    .padding(DONE_BUTTON_MARGIN),
                shape = FloatingActionButtonDefaults.extendedFabShape,
            ) {
                SongSelectionDoneButton(
                    count = selection.size,
                    onClick = viewModel.songSelection::end,
                )
            }
            // The track ends above the Done button, so that neither the thumb nor its letter bubble passes over it.
            FastScroller(
                modifier = Modifier.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap)
                    .padding(contentPadding.only(top = true, end = true, bottom = true, deferredExtraBottom = selectionBottomPadding)),
                gridState = listState,
                column = scrollerColumn,
                labelForItem = { if (isSearchOpen) null else sectionIndex.labelForItem(it) },
            )
        }
    }
}

/** The key of the empty cells that keep a card in its column as the search opens ([SongGroup.leadingGap]). */
private const val SEARCH_GAP_KEY = "search_gap"

/** The key of the divider under the Selection section. */
private const val SELECTION_DIVIDER_KEY = "selection_divider"

/** The key of [AddSongsHintCard]. */
private const val ADD_SONGS_HINT_KEY = "add_songs_hint"

/** The room above and below the divider under the Selection section. */
private val SELECTION_DIVIDER_PADDING = 8.dp

/** How far the Done button stands from the list's bottom and end edges, as the setlist reorder mode's does. */
private val DONE_BUTTON_MARGIN = 16.dp

/** What the Done button takes of the end of the list: an extended button's height and its margin. */
private val DONE_BUTTON_ROOM = 56.dp + DONE_BUTTON_MARGIN
