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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.fastScrollerColumnWidth
import com.pandulapeter.campfire.presentation.ui.components.list.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
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
    isSelecting: Boolean,
    selection: Set<String>,
) {
    val songActions = rememberSongActionHandler(viewModel)
    val selectMultipleTitle = stringResource(Res.string.songs_select_multiple)
    val selectSongsIcon = painterResource(Res.drawable.ic_select)
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
                val activeGroup = groups.indexOfFirst { group -> group.indexOfItem(card.key) >= 0 }
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
    val coroutineScope = rememberCoroutineScope()
    // One boundary and label per group, in lazy-grid item coordinates, for the fast scroller and pushed header.
    val sectionIndex = remember(groups) {
        SongSectionIndex(
            groups.mapIndexed { index, group ->
                SongSectionIndex.Group(itemCount = group.songs.size + if (groups.hasSelectionDividerAfter(index)) 1 else 0, header = group.header)
            },
        )
    }
    val coveredHeightFraction = remember(appBarOverlap) { { appBarOverlap().coverage } }
    val firstCardIndex = remember(groups, placeholder) { groups.firstCardIndex(hasPlaceholder = placeholder != null) }
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
    // and the song stays where the tap left it rather than the list keeping whichever row happened to be first.
    val filterAnchor = remember { ListAnchor() }
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
        hasPlaceholder = placeholder != null,
        anchor = filterAnchor,
    )
    // The last row's box would otherwise end up under the Done button, which stands over the end of the list.
    val selectionBottomPadding by animateDpAsState(if (isSelecting) DONE_BUTTON_ROOM else 0.dp)

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

    val scrollerColumnWidth = fastScrollerColumnWidth(gridState = listState, position = viewModel.songListState.songsScrollPosition, isAnimated = hasLoadedLibrary)
    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column.
    val headerEndPadding = scrollerColumnWidth + contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = gridModifier.bounceScrollableContent(listState, pull = topFade.overscrollPull),
            state = listState,
            contentPadding = contentPadding.only(
                start = true,
                end = true,
                bottom = true,
                extraEnd = scrollerColumnWidth,
                extraBottom = 8.dp + selectionBottomPadding,
            ),
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
                group.header?.let { header ->
                    stickyHeader(
                        key = header.itemKey,
                        contentType = SONG_HEADER_CONTENT_TYPE,
                    ) { headerIndex ->
                        val headerState = rememberSectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
                                .anchoredTransition(filterAnchor, listState, header.itemKey)
                                .collapseSearchHeader { appBarOverlap().coverage },
                            state = { headerState.value },
                            endPadding = headerEndPadding,
                            text = header.displayText(selectionCount = selection.size),
                            onClick = if (isSearchOpen) null else { { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } } },
                            opacity = { if (headerState.value.visibleFraction < 1f) 0f else appBarOverlap().coverage },
                            appBarOverlap = appBarOverlap,
                        )
                    }
                }
                itemsIndexed(
                    items = group.songs,
                    key = { _, song -> group.itemKey(song) },
                    contentType = { _, _ -> "song" },
                ) { songIndex, song ->
                    val itemKey = group.itemKey(song)
                    // The placement animation changes as a scroll starts and ends, so it goes on a box of its own: on
                    // the row, it would be a new modifier each time, and the whole row would be composed again with it.
                    Box(
                        modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
                            .anchoredTransition(filterAnchor, listState, itemKey),
                    ) {
                        // Worked out here rather than inside the row's actions, so that they capture what changes for this row
                        // alone rather than the set that is new on every write to any setlist.
                        SongListRow(
                            viewModel = viewModel,
                            songActions = songActions,
                            song = song,
                            itemKey = itemKey,
                            cardPadding = songCardPadding(songIndex, columnCount),
                            topFade = topFade,
                            playingOverrides = playingOverrides,
                            cardOptions = cardOptions,
                            labelsOnEverySong = labelsOnEverySong,
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
                // The two parts are told apart the way the sheets tell them apart, and an open search collapses every
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
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
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
                        modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = if (areHeadersCollapsing) null else itemPlacementSpec)
                            .fadingUnderListTop(topFade),
                        isEnabled = !isImporting,
                        onPaste = { viewModel.pastedSongController.newSongFromClipboard(clipboardReader) },
                        onImport = { viewModel.importController.importFiles(filePicker) },
                    )
                }
            }
            // Near the end, collapsing later headers can shorten the scroll range below the anchor's position.
            // Keep just that lost tail space until it has scrolled out of sight, or the search changes, or the headers return.
            item(key = SEARCH_ANCHOR_SPACE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "search_anchor_space") {
                Spacer(Modifier.fillMaxWidth().searchAnchorSpace(searchScroll) { appBarOverlap().coverage })
            }
        }
        // The grid clips at its top edge. Draw the outgoing header here while it is pushed, so its fade can continue
        // past that edge under the transparent app bar rather than ending at it; a bar that has filled in is drawn
        // over it.
        PushedSongSectionHeader(
            listState = listState,
            sectionIndex = sectionIndex,
            endPadding = headerEndPadding,
            selectionCount = selection.size,
            appBarOverlap = appBarOverlap,
        )
        AnimatedVisibility(
            visible = isSelecting,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(contentPadding.only(end = true, bottom = true, extraEnd = scrollerColumnWidth))
                .padding(DONE_BUTTON_MARGIN),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            SongSelectionDoneButton(
                count = selection.size,
                onClick = viewModel.songSelection::end,
            )
        }
        FastScroller(
            modifier = Modifier.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap).padding(contentPadding.only(top = true, end = true, bottom = true)),
            gridState = listState,
            labelForItem = { if (isSearchOpen) null else sectionIndex.labelForItem(it) },
        )
    }
}

/** The key of the divider under the Selection section. */
private const val SELECTION_DIVIDER_KEY = "selection_divider"

/** The key of [AddSongsHintCard]. */
private const val ADD_SONGS_HINT_KEY = "add_songs_hint"

/** The room above and below the divider under the Selection section. */
private val SELECTION_DIVIDER_PADDING = 8.dp

/** How far the Done button stands from the list's edges, as the setlist reorder mode's does. */
private val DONE_BUTTON_MARGIN = 16.dp

/** What the Done button takes of the end of the list: an extended button's height and its margin. */
private val DONE_BUTTON_ROOM = 56.dp + DONE_BUTTON_MARGIN
