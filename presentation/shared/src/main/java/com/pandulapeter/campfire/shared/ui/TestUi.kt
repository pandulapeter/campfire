package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import com.pandulapeter.campfire.shared.ui.components.ControlsList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@Composable
fun TestUi(
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) = MainTestUi(
    lazyColumnWrapper = lazyColumnWrapper
)

@Composable
private fun MainTestUi(
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
        state = state.value,
        query = query.value,
        databases = databases.value,
        collections = collections.value.sortedBy { it.title },
        songs = songs.value.sortedBy { it.artist },
        unselectedDatabaseUrls = userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
        shouldShowExplicitSongs = userPreferences.value?.shouldShowExplicitSongs == true,
        onDatabaseEnabledChanged = { database, isEnabled ->
            coroutineScope.launch { stateHolder.onDatabaseEnabledChanged(databases.value, database, isEnabled) }
        },
        onDatabaseSelectedChanged = { database, isSelected ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onDatabaseSelectedChanged(it, database, isSelected) } }
        },
        onShouldShowExplicitSongsChanged = { shouldShowExplicitSongs ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onShouldShowExplicitSongsChanged(it, shouldShowExplicitSongs) } }
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
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onShouldShowExplicitSongsChanged: (Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit,
    onQueryChanged: (String) -> Unit,
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) = Row(
    modifier = modifier.fillMaxSize().padding(8.dp)
) {
    ContentList(
        modifier = Modifier.fillMaxWidth(0.55f),
        collections = collections,
        songs = songs,
        lazyColumnWrapper = lazyColumnWrapper
    )
    Spacer(
        modifier = Modifier.width(8.dp)
    )
    ControlsList(
        modifier = Modifier.fillMaxSize(),
        state = state,
        query = query,
        databases = databases,
        unselectedDatabaseUrls = unselectedDatabaseUrls,
        shouldShowExplicitSongs = shouldShowExplicitSongs,
        onDatabaseEnabledChanged = onDatabaseEnabledChanged,
        onDatabaseSelectedChanged = onDatabaseSelectedChanged,
        onShouldShowExplicitSongsChanged = onShouldShowExplicitSongsChanged,
        onForceRefreshPressed = onForceRefreshPressed,
        onDeleteLocalDataPressed = onDeleteLocalDataPressed,
        onQueryChanged = onQueryChanged
    )
}