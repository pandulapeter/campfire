package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.screenComponents.home.HomeContentList
import com.pandulapeter.campfire.shared.ui.screenComponents.home.HomeControlsList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@Composable
internal fun HomeScreenDesktop(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel = KoinJavaComponent.get(CampfireViewModel::class.java),
    shouldUseExpandedUi: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val query = viewModel.query.collectAsState("")
    val databases = viewModel.databases.collectAsState(emptyList())
    val songs = viewModel.songs.collectAsState(emptyList())
    val dataState = viewModel.dataState.collectAsState("Uninitialized")
    val userPreferences = viewModel.userPreferences.collectAsState(null)

    Row(
        modifier = modifier.fillMaxSize()
    ) {

        val lazyListState = rememberLazyListState()

        Box(
            modifier = Modifier.fillMaxWidth(0.55f),
        ) {
            HomeContentList(
                modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                state = lazyListState,
                songs = songs.value,
                onSongClicked = viewModel::onSongClicked
            )
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(
                    scrollState = lazyListState
                )
            )
        }
        Spacer(
            modifier = Modifier.width(8.dp)
        )
        HomeControlsList(
            modifier = Modifier.fillMaxSize(),
            state = dataState.value,
            query = query.value,
            databases = databases.value,
            unselectedDatabaseUrls = userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
            shouldShowExplicitSongs = userPreferences.value?.shouldShowExplicitSongs == true,
            shouldShowSongsWithoutChords = userPreferences.value?.shouldShowSongsWithoutChords == true,
            onDatabaseEnabledChanged = { database, isEnabled ->
                coroutineScope.launch {
                    viewModel.onDatabaseEnabledChanged(databases.value, database, isEnabled)
                }
            },
            onDatabaseSelectedChanged = { database, isEnabled ->
                userPreferences.value?.let { userPreferences ->
                    coroutineScope.launch { viewModel.onDatabaseSelectedChanged(userPreferences, database, isEnabled) }
                }
            },
            onShouldShowExplicitSongsChanged = { shouldShowExplicitSongs ->
                userPreferences.value?.let { userPreferences ->
                    coroutineScope.launch { viewModel.onShouldShowExplicitSongsChanged(userPreferences, shouldShowExplicitSongs) }
                }
            },
            onShouldShowSongsWithoutChordsChanged = { shouldShowSongsWithoutChords ->
                userPreferences.value?.let { userPreferences ->
                    coroutineScope.launch { viewModel.onShouldShowSongsWithoutChordsChanged(userPreferences, shouldShowSongsWithoutChords) }
                }
            },
            onForceRefreshPressed = { coroutineScope.launch { viewModel.onForceRefreshPressed() } },
            onDeleteLocalDataPressed = { coroutineScope.launch { viewModel.onDeleteLocalDataPressed() } },
            onQueryChanged = viewModel::onQueryChanged
        )
    }
}