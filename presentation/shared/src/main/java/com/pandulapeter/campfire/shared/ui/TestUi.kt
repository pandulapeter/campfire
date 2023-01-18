package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.components.ContentList
import com.pandulapeter.campfire.shared.ui.components.Controls
import com.pandulapeter.campfire.shared.ui.components.FilterList
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@Composable
fun TestUi(
) = MainTestUi()

@Composable
private fun MainTestUi(
    stateHolder: TestUiStateHolder = get(TestUiStateHolder::class.java)
) {
    val coroutineScope = rememberCoroutineScope()
    val state = stateHolder.state.collectAsState("Uninitialized")
    val databases = stateHolder.databases.collectAsState(emptyList())
    val collections = stateHolder.collections.collectAsState(emptyList())
    val songs = stateHolder.songs.collectAsState(emptyList())
    val userPreferences = stateHolder.userPreferences.collectAsState(null)

    LaunchedEffect(Unit) { stateHolder.onInitialize() }

    Screen(
        state = state.value,
        databases = databases.value,
        collections = collections.value.sortedBy { it.title },
        songs = songs.value.sortedBy { it.artist },
        unselectedDatabaseUrls = userPreferences.value?.unselectedDatabaseUrls.orEmpty(),
        onDatabaseEnabledChanged = { database, isEnabled ->
            coroutineScope.launch { stateHolder.onDatabaseEnabledChanged(databases.value, database, isEnabled) }
        },
        onDatabaseSelectedChanged = { database, isSelected ->
            coroutineScope.launch { userPreferences.value?.let { stateHolder.onDatabaseSelectedChanged(it, database, isSelected) } }
        },
        onForceRefreshPressed = { coroutineScope.launch { stateHolder.onForceRefreshPressed() } },
        onDeleteLocalDataPressed = { coroutineScope.launch { stateHolder.onDeleteLocalDataPressed() } }
    )
}

@Composable
private fun Screen(
    modifier: Modifier = Modifier,
    state: String,
    databases: List<Database>,
    collections: List<Collection>,
    songs: List<Song>,
    unselectedDatabaseUrls: List<String>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onDatabaseSelectedChanged: (Database, Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit
) = Row(
    modifier = modifier.fillMaxSize().padding(8.dp)
) {
    ContentList(
        modifier = modifier.fillMaxWidth(0.65f),
        collections = collections,
        songs = songs
    )
    Box(modifier = modifier.fillMaxSize()) {
        FilterList(
            databases = databases,
            unselectedDatabaseUrls = unselectedDatabaseUrls,
            onDatabaseEnabledChanged = onDatabaseEnabledChanged,
            onDatabaseSelectedChanged = onDatabaseSelectedChanged
        )
        Controls(
            modifier = Modifier.align(Alignment.BottomEnd),
            state = state,
            onForceRefreshPressed = onForceRefreshPressed,
            onDeleteLocalDataPressed = onDeleteLocalDataPressed
        )
    }
}