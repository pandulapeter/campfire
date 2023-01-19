package com.pandulapeter.campfire.shared.ui

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveDatabasesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveUserPreferencesUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class TestUiStateHolder(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase,
    private val saveDatabases: SaveDatabasesUseCase,
    private val saveUserPreferences: SaveUserPreferencesUseCase,
    private val deleteLocalData: DeleteLocalDataUseCase
) {
    val songs = getScreenData().map { it.data?.songs.orEmpty() }.distinctUntilChanged()
    val collections = getScreenData().map { it.data?.collections.orEmpty() }.distinctUntilChanged()
    val databases = getScreenData().map { it.data?.databases.orEmpty() }.distinctUntilChanged()
    val userPreferences = getScreenData().map { it.data?.userPreferences }.distinctUntilChanged()
    val state = getScreenData().map {
        when (it) {
            is DataState.Failure -> "Error"
            is DataState.Idle -> "Idle"
            is DataState.Loading -> "Loading"
        }
    }.distinctUntilChanged()
    val shouldShowLoadingIndicator = getScreenData().map { it is DataState.Loading }.distinctUntilChanged()

    suspend fun onInitialize() = loadScreenData(false)

    suspend fun onDatabaseEnabledChanged(databases: List<Database>, database: Database, isEnabled: Boolean) = saveDatabases(
        databases.map { if (it.url == database.url) database.copy(isEnabled = isEnabled) else it }
    )

    suspend fun onDatabaseSelectedChanged(userPreferences: UserPreferences, database: Database, isSelected: Boolean) = saveUserPreferences(
        userPreferences.copy(unselectedDatabaseUrls = userPreferences.unselectedDatabaseUrls.toMutableList().apply {
            if (isSelected) {
                remove(database.url)
            } else {
                add(database.url)
            }
        }.distinct())
    )

    suspend fun onShouldShowExplicitSongsChanged(userPreferences: UserPreferences, shouldShowExplicitSongs: Boolean) = saveUserPreferences(
        userPreferences.copy(shouldShowExplicitSongs = shouldShowExplicitSongs)
    )

    suspend fun onForceRefreshPressed() = loadScreenData(true)

    suspend fun onDeleteLocalDataPressed() = deleteLocalData()
}