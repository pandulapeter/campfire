package com.pandulapeter.campfire.shared.ui.screens.songs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.refresh
import com.pandulapeter.campfire.shared.resources.songs_no_data
import com.pandulapeter.campfire.shared.resources.songs_no_data_hint
import com.pandulapeter.campfire.shared.resources.songs_sort_and_filter
import com.pandulapeter.campfire.shared.resources.songs_unsorted_label
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.shared.ui.components.EmptyState
import com.pandulapeter.campfire.shared.ui.components.SearchField
import com.pandulapeter.campfire.shared.ui.components.SectionHeader
import com.pandulapeter.campfire.shared.ui.components.SongListItem
import com.pandulapeter.campfire.shared.ui.components.SongsControls
import com.pandulapeter.campfire.shared.ui.components.WindowSize
import com.pandulapeter.campfire.shared.ui.platform.PlatformVerticalScrollbar
import com.pandulapeter.campfire.shared.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import com.pandulapeter.campfire.shared.localization.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    windowSize: WindowSize,
    contentPadding: PaddingValues
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            title = {
                SearchField(
                    modifier = Modifier.fillMaxWidth(),
                    query = query,
                    onQueryChanged = viewModel::onQueryChanged
                )
            },
            actions = {
                if (isDesktopPlatform) {
                    RefreshAction(
                        isLoading = isLoading,
                        onClick = viewModel::refresh
                    )
                }
                if (!windowSize.usesSidePanel) {
                    IconButton(onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongsControls) }) {
                        Icon(
                            imageVector = CampfireIcons.tune,
                            contentDescription = stringResource(Res.string.songs_sort_and_filter)
                        )
                    }
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            SongList(
                modifier = Modifier.weight(1f).fillMaxSize(),
                viewModel = viewModel,
                isLoading = isLoading,
                contentPadding = contentPadding
            )
            AnimatedVisibility(
                visible = windowSize.usesSidePanel,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row {
                    VerticalDivider()
                    SongsControls(
                        modifier = Modifier.width(SIDE_PANEL_WIDTH).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                        viewModel = viewModel,
                        shouldIncludeSorting = true,
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshAction(
    isLoading: Boolean,
    onClick: () -> Unit
) = AnimatedContent(
    targetState = isLoading,
    transitionSpec = { fadeIn() togetherWith fadeOut() }
) { loading ->
    if (loading) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = CampfireIcons.refresh,
                contentDescription = stringResource(Res.string.refresh)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    isLoading: Boolean,
    contentPadding: PaddingValues
) {
    val songGroups by viewModel.songGroups.collectAsStateWithLifecycle()
    val rawSongDetails by viewModel.rawSongDetails.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val layoutDirection = LocalLayoutDirection.current

    // Scroll back to the top whenever the search query or the sorting changes, before the new items arrive.
    LaunchedEffect(query, userPreferences?.sortingMode) { listState.scrollToItem(0) }

    RefreshableContainer(
        modifier = modifier,
        isRefreshing = isLoading,
        onRefresh = viewModel::refresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 8.dp
            )
        ) {
            if (songGroups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        icon = CampfireIcons.songs,
                        title = stringResource(Res.string.songs_no_data),
                        hint = stringResource(Res.string.songs_no_data_hint)
                    )
                }
            }
            songGroups.forEach { group ->
                group.header?.let { header ->
                    stickyHeader(key = "header_$header") {
                        SectionHeader(
                            modifier = Modifier.animateItem(),
                            text = when (header) {
                                is CampfireViewModel.SongGroup.Header.Artist -> header.name
                                is CampfireViewModel.SongGroup.Header.Letter -> header.letter.toString()
                                CampfireViewModel.SongGroup.Header.Symbols -> stringResource(Res.string.songs_unsorted_label)
                            }
                        )
                    }
                }
                items(
                    items = group.songs,
                    key = { "song_${it.id}" }
                ) { song ->
                    SongListItem(
                        modifier = Modifier.animateItem(),
                        song = song,
                        isDownloaded = rawSongDetails[song.url] != null,
                        onClick = {
                            keyboardController?.hide()
                            viewModel.openSong(song)
                        }
                    )
                }
            }
        }
        PlatformVerticalScrollbar(
            listState = listState,
            modifier = Modifier.padding(contentPadding)
        )
    }
}

/**
 * Pull to refresh only makes sense with touch input; on desktop the app bar has a refresh action instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshableContainer(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) = if (isDesktopPlatform) {
    Box(
        modifier = modifier,
        content = content
    )
} else {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullToRefreshState,
                isRefreshing = isRefreshing
            )
        },
        content = content
    )
}

private val SIDE_PANEL_WIDTH = 320.dp
