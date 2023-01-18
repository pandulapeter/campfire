package com.pandulapeter.campfire.presentation.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.shared.TestUi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent

@Composable
fun CampfireApp(
    getScreenData: GetScreenDataUseCase = KoinJavaComponent.get(GetScreenDataUseCase::class.java),
    loadScreenData: LoadScreenDataUseCase = KoinJavaComponent.get(LoadScreenDataUseCase::class.java),
    saveDatabases: SaveDatabasesUseCase = KoinJavaComponent.get(SaveDatabasesUseCase::class.java), // TODO: Use UserPreferences instead
    deleteLocalData: DeleteLocalDataUseCase = KoinJavaComponent.get(DeleteLocalDataUseCase::class.java) // TODO: Use UserPreferences instead
) {
    val coroutineScope = rememberCoroutineScope()
    val songs = getScreenData().map { it.data?.songs.orEmpty() }.collectAsState(emptyList())
    val databases = getScreenData().map { it.data?.databases.orEmpty() }.collectAsState(emptyList())
    val stateIndicator = getScreenData().map {
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

    TestUi(
        stateIndicator = stateIndicator.value,
        databases = databases.value,
        songs = songs.value.sortedBy { it.artist },
        onDatabaseEnabledChanged = { database, isEnabled -> coroutineScope.launch { saveDatabases(databases.value.map { if (it.url == database.url) database.copy(isEnabled = isEnabled) else it }) } },
        onForceRefreshPressed = { coroutineScope.launch { loadScreenData(true) } },
        onDeleteLocalDataPressed = { coroutineScope.launch { deleteLocalData() } }
    )
}