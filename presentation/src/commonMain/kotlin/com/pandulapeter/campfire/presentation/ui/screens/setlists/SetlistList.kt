/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.runtime.CompositionLocalProvider
import com.pandulapeter.campfire.presentation.ui.components.list.listItemTrailingKeylineAdjustment
import com.pandulapeter.campfire.presentation.ui.components.list.LocalListItemTrailingKeylineAdjustment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.CardDetails
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_reorder_songs_done
import com.pandulapeter.campfire.presentation.resources.setlists_choose_songs
import com.pandulapeter.campfire.presentation.resources.setlists_done_reordering
import com.pandulapeter.campfire.presentation.resources.setlists_show_archived
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.state.editor.EditorSession
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportController
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController
import com.pandulapeter.campfire.presentation.ui.state.songs.SongFilesController
import kotlinx.coroutines.flow.StateFlow
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.list.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.list.measuresFastScrollerColumn
import com.pandulapeter.campfire.presentation.ui.components.list.rememberFastScrollerColumn
import com.pandulapeter.campfire.presentation.ui.components.list.withSectionStartRoom
import com.pandulapeter.campfire.presentation.ui.components.list.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.list.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongCardOptions
import com.pandulapeter.campfire.presentation.ui.components.appBar.belowAppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.list.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.list.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.list.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberToday
import com.pandulapeter.campfire.presentation.ui.components.list.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.screens.rememberSetlistActionHandler
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
import com.pandulapeter.campfire.presentation.ui.state.setlists.details
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.components.FloatingControlVisibility
import androidx.compose.material3.FloatingActionButtonDefaults

@Composable
internal fun SetlistList(
    modifier: Modifier = Modifier,
    setlistsController: SetlistsController,
    libraryState: LibraryState,
    userPreferencesFlow: StateFlow<UserPreferences?>,
    preferencesController: PreferencesController,
    playingOverridesFlow: StateFlow<PlayingOverridesSnapshot>,
    dialogHost: DialogHost,
    editorSession: EditorSession,
    songFilesController: SongFilesController,
    importController: ImportController,
    songRenderer: SongRenderer,
    navigator: Navigator,
    listState: LazyGridState,
    columnCount: Int,
    contentPadding: PaddingValues,
    appBarOverlap: () -> AppBarOverlap,
    reorderingSetlistFileName: String?,
    isReordering: Boolean,
    isScreenActive: Boolean,
    onReorderingSetlistChanged: (String?) -> Unit,
) {
    val songActions = rememberSongActionHandler(editorSession, songFilesController, dialogHost)
    val setlistActions = rememberSetlistActionHandler(dialogHost, setlistsController)
    val listedSetlists by setlistsController.setlistsWithSongs.collectAsStateWithLifecycle()
    val setlistsWithSongs = listedSetlists.setlists
    // Keep validation and writes tied to the full library; only the rendered list is narrowed by this mode.
    var narrowedSetlistFileName by remember { mutableStateOf<String?>(null) }
    val shownSetlists = remember(setlistsWithSongs, narrowedSetlistFileName) {
        if (narrowedSetlistFileName != null) setlistsWithSongs.filter { it.setlist.fileName == narrowedSetlistFileName }
        else setlistsWithSongs
    }
    // Room after the end of the list for the setlist being brought to the top, held only until the other setlists
    // have left: a setlist near the end has too little of the list after it to be scrolled up there otherwise.
    var bringToTopPadding by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    LaunchedEffect(isReordering, reorderingSetlistFileName) {
        if (!isReordering) {
            narrowedSetlistFileName = null
            bringToTopPadding = 0.dp
            return@LaunchedEffect
        }
        // The other setlists only leave once the setlist is the one the list starts at, already or after scrolling it
        // there: the grid keeps its first visible item by key as they go, so nothing on screen moves. Narrowed
        // anywhere else, the grid would lose that item and fall back to its index somewhere inside the setlist.
        fun isSetlistAtTop(): Boolean {
            val key = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.key
            return SetlistItemKey.setlistFileNameOf(key) == reorderingSetlistFileName
        }
        if (!isSetlistAtTop()) {
            val headerIndex = setlistsWithSongs.headerIndexOf(reorderingSetlistFileName)
            if (headerIndex != null) {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                bringToTopPadding = with(density) { viewportHeight.toDp() }
                snapshotFlow { listState.layoutInfo.afterContentPadding }.first { it >= viewportHeight }
                try {
                    listState.animateScrollToItem(headerIndex)
                } catch (exception: CancellationException) {
                    // A scroll of the user's own interrupts the animation, and the mode still narrows the list after
                    // it; only the mode itself ending cancels the rest.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        val isSetlistAtTop = isSetlistAtTop()
        narrowedSetlistFileName = reorderingSetlistFileName
        bringToTopPadding = 0.dp
        if (!isSetlistAtTop) listState.requestScrollToItem(0)
    }
    val setlists by libraryState.setlists.collectAsStateWithLifecycle()
    val userPreferences by userPreferencesFlow.collectAsStateWithLifecycle()
    val playingOverrides by playingOverridesFlow.collectAsStateWithLifecycle()
    val labelsOnEverySong by libraryState.labelsOnEverySong.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading = !listedSetlists.isLibraryRead)
    val topFade = rememberListTopFade(listState, sectionHeaderContentType = SetlistItemKey.HEADER_CONTENT_TYPE)
    // Remembered, since a new modifier every time the list recomposes would recompose the grid with it.
    val gridModifier = remember(topFade) { Modifier.fillMaxSize().listTopFadeViewport(topFade) }
    val isPerformanceModeEnabled by preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read by the headers alone, so midnight recomposes the ones counting down and not the list around them.
    val today by rememberToday()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = setlistsController.setlistsPlaceholder.collectAsStateWithLifecycle().value
    LaunchedEffect(isScreenActive, isPerformanceModeEnabled, setlistsWithSongs) {
        if (!isScreenActive || isPerformanceModeEnabled ||
            setlistsWithSongs.none { it.setlist.fileName == reorderingSetlistFileName && it.canReorder }
        ) {
            onReorderingSetlistChanged(null)
        }
    }
    val cardOptions = SongCardOptions.of(userPreferences = userPreferences, details = userPreferences?.setlistCardDetails ?: CardDetails.SetlistsDefault)
    val reorder = rememberSetlistReorder(listState = listState, setlistsWithSongs = setlistsWithSongs, onReorder = setlistsController::reorderSetlist)
    val filePicker = LocalFilePicker.current
    val coroutineScope = rememberCoroutineScope()
    val rowsCache = remember { SetlistRowsCache() }
    rowsCache.retainOnly(setlistsWithSongs)

    ScrollToTopWhenChanged(
        listState = listState,
        // The query the list was narrowed by rather than the field's text, which changes frames before the list does
        // and would compose the screen on every character typed; a closed search narrows nothing, as on the songs
        // screen. Sorting sends the list to its new first setlist, but the archive toggle lives at the end and must
        // stay within reach there.
        key = "${listedSetlists.filterKey}|${userPreferences?.setlistSortingMode?.name}",
        contents = setlistsWithSongs,
        // Closing search to rearrange restores the full list around its visible item, not from the top.
        scrollToTopOnKeyChange = !isReordering,
    )

    // The fast scroller's track ends above the Done button, so that the thumb never passes over it.
    // Read by its padding while it is laid out, never here: it changes on every frame of its spring.
    val doneButtonRoomState = animateDpAsState(if (isReordering) DONE_BUTTON_ROOM else 0.dp)
    val doneButtonRoom = remember(doneButtonRoomState) { { doneButtonRoomState.value } }
    val scrollerColumn = rememberFastScrollerColumn(gridState = listState, position = setlistsController.setlistsScrollPosition)
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
                contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraBottom = (if (isReordering) 88.dp else 8.dp) + bringToTopPadding, deferredExtraEnd = scrollerColumn.width)
                    .withSectionStartRoom(appBarOverlap),
            ) {
                // The setlists are what this screen is about, and they are listed whenever there are any - an empty library
                // included, where they are simply empty and each offers to be filled. The library's own empty state belongs
                // to the songs screen. Every state goes through the same slot, so that "still loading" turning out to be
                // "you have no setlists" cross fades instead of being swapped in a single frame.
                val placeholder = setlistsPlaceholder
                when {
                    placeholder != null -> item(
                        key = "placeholder",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "placeholder",
                    ) {
                        ListPlaceholder(
                            modifier = listItemAnimation(listState, hasLoadedLibrary).fillMaxWidth(),
                            placeholder = placeholder,
                            onRetry = libraryState::refresh,
                            onNewSetlist = if (isPerformanceModeEnabled) null else {
                                { dialogHost.showDialog(DialogType.NewSetlist) }
                            },
                            onImport = if (isPerformanceModeEnabled) null else {
                                { importController.importFiles(filePicker) }
                            },
                        )
                    }

                    else -> shownSetlists.forEach { setlistWithSongs ->
                        setlistHeader(
                            setlistWithSongs = setlistWithSongs,
                            listState = listState,
                            hasLoadedLibrary = hasLoadedLibrary,
                            isReordering = isReordering,
                            headerEndPadding = headerEndPadding,
                            coroutineScope = coroutineScope,
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            setlistActions = setlistActions,
                            reorderingSetlistFileName = reorderingSetlistFileName,
                            onReorderingSetlistChanged = onReorderingSetlistChanged,
                            appBarOverlap = appBarOverlap,
                        )
                        // Under the header: the row names the setlist, then this is the first thing to read about it.
                        if (setlistWithSongs.hasSummary) {
                            item(
                                key = SetlistItemKey.summary(setlistWithSongs.setlist.fileName),
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "setlist_summary",
                            ) {
                                SetlistSummary(
                                    modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade),
                                    countdown = setlistWithSongs.setlist.countdownText(today),
                                    // An archived setlist is not edited, as its menu offers no Edit either.
                                    onCountdownClick = if (isPerformanceModeEnabled || setlistWithSongs.setlist.isArchived) null else {
                                        { dialogHost.showDialog(DialogType.SetlistDate(setlistWithSongs.setlist)) }
                                    },
                                    runningTime = setlistWithSongs.runningTimeText(),
                                    description = setlistWithSongs.setlist.description,
                                    contentPadding = contentPadding,
                                    isSizeAnimated = scrollerColumn.hasMeasuredWidth,
                                )
                            }
                        }
                        val setlistRows = rowsCache.rowsFor(setlistWithSongs, reorder.draggedSetlist)
                        val rows = setlistRows.rows
                        itemsIndexed(
                            items = rows,
                            key = { _, row -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = row.entry.songFileName).string.orEmpty() },
                            contentType = { _, _ -> "song" },
                        ) { rowIndex, row ->
                            // Built here, in the item's composition, rather than in the builder above, which runs again on
                            // every move: there it would be a new lambda each time and keep every row from skipping the move.
                            val songFileNames = { rowsCache.rowsFor(setlistWithSongs, reorder.draggedSetlist).songFileNames }
                            SetlistSongRow(
                                setlistsController = setlistsController,
                                songRenderer = songRenderer,
                                dialogHost = dialogHost,
                                navigator = navigator,
                                songActions = songActions,
                                setlistWithSongs = setlistWithSongs,
                                row = row,
                                rowIndex = rowIndex,
                                isLastRow = rowIndex == rows.lastIndex,
                                songFileNames = songFileNames,
                                columnCount = columnCount,
                                listState = listState,
                                hasLoadedLibrary = hasLoadedLibrary,
                                topFade = topFade,
                                isReordering = isReordering,
                                reorderingSetlistFileName = reorderingSetlistFileName,
                                isPerformanceModeEnabled = isPerformanceModeEnabled,
                                playingOverrides = playingOverrides,
                                cardOptions = cardOptions,
                                labelsOnEverySong = labelsOnEverySong,
                                reorder = reorder,
                            )
                        }
                        // Every setlist ends in the way to more songs, which for an empty one is also all there is under its
                        // header, so the way to them is where the songs will be rather than behind the header's menu.
                        if (!isPerformanceModeEnabled) {
                            item(
                                key = SetlistItemKey.chooseSongs(setlistWithSongs.setlist.fileName),
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "setlist_action",
                            ) {
                                // The item animation goes on the item's root layout, the only one the grid animates, which
                                // is the visibility wrapper here rather than the row inside it.
                                AnimatedVisibility(
                                    modifier = listItemAnimation(listState, hasLoadedLibrary),
                                    visible = !setlistWithSongs.setlist.isArchived,
                                ) {
                                    ActionListItem(
                                        modifier = Modifier.fadingUnderListTop(topFade),
                                        title = stringResource(Res.string.setlists_choose_songs),
                                        icon = painterResource(Res.drawable.ic_add),
                                        onClick = { dialogHost.showDialog(DialogType.SongPicker(setlistWithSongs.setlist)) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (narrowedSetlistFileName == null && setlists.any { it.isArchived }) {
                    item(
                        key = "show_archived_setlists",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "setlist_archive_toggle",
                    ) {
                        CheckboxListItem(
                            modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade).fillMaxWidth(),
                            title = stringResource(Res.string.setlists_show_archived),
                            isChecked = userPreferences?.shouldShowArchivedSetlists == true,
                            onCheckedChange = setlistsController::setShouldShowArchivedSetlists,
                        )
                    }
                }
            }
            // The visual copy can pass above the grid's clipped viewport, under the transparent app bar, as it fades away.
            PushedSetlistHeader(
                setlistActions = setlistActions,
                listState = listState,
                setlistsWithSongs = shownSetlists,
                contentPadding = contentPadding,
                endPadding = headerEndPadding,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
                reorderingSetlistFileName = reorderingSetlistFileName.takeIf { isReordering },
                appBarOverlap = appBarOverlap,
            )
            FloatingControlVisibility(
                isVisible = isReordering,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(contentPadding.only(end = true, bottom = true))
                    .padding(DONE_BUTTON_MARGIN),
                shape = FloatingActionButtonDefaults.extendedFabShape,
            ) {
                DoneReorderingButton(onClick = { onReorderingSetlistChanged(null) })
            }
            FastScroller(
                modifier = Modifier.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap)
                    .padding(contentPadding.only(top = true, end = true, bottom = true, deferredExtraBottom = doneButtonRoom)),
                gridState = listState,
                column = scrollerColumn,
            )
        }
    }
}

/** Ends reordering. Described by its label, since Material's extended floating button clears the semantics of its text. */
@Composable
private fun DoneReorderingButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.setlists_done_reordering)
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        // The shadow is drawn around the button by FloatingControlVisibility, which fades it with the button.
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        icon = { Icon(painterResource(Res.drawable.ic_reorder_songs_done), contentDescription = null) },
        text = { Text(label) },
    )
}

/** How far the Done button stands from the list's bottom and end edges, as the song selection's does. */
private val DONE_BUTTON_MARGIN = 16.dp

/** What the Done button takes of the end of the list: an extended button's height and its margin. */
private val DONE_BUTTON_ROOM = 56.dp + DONE_BUTTON_MARGIN
