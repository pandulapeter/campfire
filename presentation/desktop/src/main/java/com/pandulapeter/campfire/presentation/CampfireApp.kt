package com.pandulapeter.campfire.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

@Composable
fun CampfireApp(
    getScreenData: GetScreenDataUseCase = get(GetScreenDataUseCase::class.java),
    loadScreenData: LoadScreenDataUseCase = get(LoadScreenDataUseCase::class.java),
    saveDatabases: SaveDatabasesUseCase = get(SaveDatabasesUseCase::class.java), // TODO: Use UserPreferences instead
    deleteLocalData: DeleteLocalDataUseCase = get(DeleteLocalDataUseCase::class.java) // TODO: Use UserPreferences instead
) {
    val coroutineScope = rememberCoroutineScope()
    val songs = getScreenData()
        .map { it.data?.songs.orEmpty() }
        .collectAsState(emptyList())
    val databases = getScreenData()
        .map { it.data?.databases.orEmpty() }
        .collectAsState(emptyList())
    val stateIndicator = getScreenData()
        .map {
            when (it) {
                is DataState.Failure -> "Error"
                is DataState.Idle -> "Idle"
                is DataState.Loading -> "Loading"
            }
        }
        .collectAsState("Uninitialized")

    LaunchedEffect(Unit) {
        loadScreenData(false)
    }

    Screen(
        stateIndicator = stateIndicator.value,
        databases = databases.value,
        songs = songs.value.sortedBy { it.artist },
        onDatabaseEnabledChanged = { database, isEnabled -> coroutineScope.launch { saveDatabases(databases.value.map { if (it.url == database.url) database.copy(isEnabled = isEnabled) else it }) } },
        onForceRefreshPressed = { coroutineScope.launch { loadScreenData(true) } },
        onDeleteLocalDataPressed = { coroutineScope.launch { deleteLocalData() } }
    )
}

@Composable
private fun Screen(
    modifier: Modifier = Modifier,
    stateIndicator: String,
    databases: List<Database>,
    songs: List<Song>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit
) = Row(
    modifier = modifier.fillMaxSize().padding(8.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(0.65f).fillMaxHeight()
    ) {
        if (songs.isNotEmpty()) {
            item {
                Header(text = "Songs")
            }
            items(songs.size) {
                SongItem(song = songs[it])
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn {
            if (databases.isNotEmpty()) {
                item {
                    Header(text = "Databases")
                }
                items(databases.size) {
                    val database = databases[it]
                    DatabaseItem(
                        database = database,
                        onCheckedChanged = { isEnabled -> onDatabaseEnabledChanged(database, isEnabled) }
                    )
                }
            }
        }
        Controller(
            modifier = Modifier.align(Alignment.BottomEnd),
            stateIndicator = stateIndicator,
            onForceRefreshPressed = onForceRefreshPressed,
            onDeleteLocalDataPressed = onDeleteLocalDataPressed
        )
    }
}

@Composable
private fun Controller(
    modifier: Modifier = Modifier,
    stateIndicator: String,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit
) = Column(modifier = modifier) {
    Text(
        modifier = modifier.padding(8.dp),
        style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
        text = stateIndicator
    )
    Text(
        modifier = modifier.clickable { onForceRefreshPressed() }.padding(8.dp),
        text = "Force refresh"
    )
    Text(
        modifier = modifier.clickable { onDeleteLocalDataPressed() }.padding(8.dp),
        text = "Delete local data"
    )
}

@Composable
private fun Header(
    modifier: Modifier = Modifier,
    text: String
) = Text(
    modifier = modifier.padding(8.dp).fillMaxWidth(),
    text = text,
    style = TextStyle.Default.copy(fontWeight = FontWeight.Bold)
)

@Composable
private fun SongItem(
    modifier: Modifier = Modifier,
    song: Song
) = Text(
    modifier = modifier.padding(8.dp).fillMaxWidth(),
    text = "${song.artist} - ${song.title}"
)

@Composable
private fun DatabaseItem(
    modifier: Modifier = Modifier,
    database: Database,
    onCheckedChanged: (Boolean) -> Unit
) = Row(
    modifier = modifier.padding(horizontal = 8.dp).clickable { onCheckedChanged(!database.isEnabled) },
) {
    Checkbox(
        modifier = Modifier.align(Alignment.CenterVertically),
        checked = database.isEnabled,
        onCheckedChange = onCheckedChanged
    )
    Text(
        modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically),
        text = database.name
    )
}