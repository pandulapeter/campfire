package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Icon
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.components.ContentList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@Composable
fun TestUi(
    modifier: Modifier = Modifier,
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) = MainTestUi(
    modifier = modifier,
    lazyColumnWrapper = lazyColumnWrapper
)

@Composable
private fun MainTestUi(
    modifier: Modifier = Modifier,
    stateHolder: TestUiStateHolder = get(TestUiStateHolder::class.java),
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val query = stateHolder.query.collectAsState("")
    val state = stateHolder.state.collectAsState("Uninitialized")
    val databases = stateHolder.databases.collectAsState(emptyList())
    val collections = stateHolder.collections.collectAsState(emptyList())
    val songs = stateHolder.songs.collectAsState(emptyList())
    val userPreferences = stateHolder.userPreferences.collectAsState(null)

    LaunchedEffect(Unit) { stateHolder.onInitialize() }

    Screen(
        modifier = modifier,
        state = state.value,
        query = query.value,
        databases = databases.value,
        collections = collections.value.sortedBy { it.title },
        songs = songs.value.sortedBy { it.artist },
        unselectedDatabaseUrls = userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
        shouldShowExplicitSongs = userPreferences.value?.shouldShowExplicitSongs == true,
        shouldShowSongsWithoutChords = userPreferences.value?.shouldShowSongsWithoutChords == true,
        onDatabaseEnabledChanged = { database, isEnabled ->
            coroutineScope.launch { stateHolder.onDatabaseEnabledChanged(databases.value, database, isEnabled) }
        },
        onDatabaseSelectedChanged = { database, isSelected ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onDatabaseSelectedChanged(it, database, isSelected) } }
        },
        onShouldShowExplicitSongsChanged = { shouldShowExplicitSongs ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onShouldShowExplicitSongsChanged(it, shouldShowExplicitSongs) } }
        },
        onShouldShowSongsWithoutChordsChanged = { shouldShowSongsWithoutChords ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onShouldShowSongsWithoutChordsChanged(it, shouldShowSongsWithoutChords) } }
        },
        onForceRefreshPressed = { coroutineScope.launch { stateHolder.onForceRefreshPressed() } },
        onDeleteLocalDataPressed = { coroutineScope.launch { stateHolder.onDeleteLocalDataPressed() } },
        onQueryChanged = stateHolder::onQueryChanged,
        lazyColumnWrapper = lazyColumnWrapper
    )
}

@Composable
private fun Screen(
    modifier: Modifier = Modifier,
    state: String,
    query: String,
    databases: List<Database>,
    collections: List<Collection>,
    songs: List<Song>,
    unselectedDatabaseUrls: List<String>,
    shouldShowExplicitSongs: Boolean,
    shouldShowSongsWithoutChords: Boolean,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onShouldShowSongsWithoutChordsChanged: (Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit,
    onQueryChanged: (String) -> Unit,
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) = Row(
    modifier = modifier.fillMaxSize()
) {
    NavigationRail {
        NavigationRailItem(
            selected = true,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                )
            }
        )
        NavigationRailItem(
            selected = false,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Playlists",
                )
            }
        )
        NavigationRailItem(
            selected = false,
            onClick = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                )
            }
        )
    }
    ContentList(
        modifier = Modifier.fillMaxWidth(0.55f),
        collections = collections,
        songs = songs,
        lazyColumnWrapper = lazyColumnWrapper
    )
    androidx.compose.foundation.layout.Spacer(
        modifier = Modifier.width(8.dp)
    )
    com.pandulapeter.campfire.shared.ui.components.ControlsList(
        modifier = Modifier.fillMaxSize(),
        state = state,
        query = query,
        databases = databases,
        unselectedDatabaseUrls = unselectedDatabaseUrls,
        shouldShowExplicitSongs = shouldShowExplicitSongs,
        shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
        onDatabaseEnabledChanged = onDatabaseEnabledChanged,
        onDatabaseSelectedChanged = onDatabaseSelectedChanged,
        onShouldShowExplicitSongsChanged = onShouldShowExplicitSongsChanged,
        onShouldShowSongsWithoutChordsChanged = onShouldShowSongsWithoutChordsChanged,
        onForceRefreshPressed = onForceRefreshPressed,
        onDeleteLocalDataPressed = onDeleteLocalDataPressed,
        onQueryChanged = onQueryChanged
    )
}