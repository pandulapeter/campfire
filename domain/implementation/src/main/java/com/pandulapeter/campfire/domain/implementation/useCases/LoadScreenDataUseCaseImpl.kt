package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class LoadScreenDataUseCaseImpl internal constructor(
    private val databaseRepository: DatabaseRepository,
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val rawSongDetailsRepository: RawSongDetailsRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : LoadScreenDataUseCase {

    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    override suspend operator fun invoke(isForceRefresh: Boolean) {
        with(scope) {
            listOf(
                async { setlistRepository.loadSetlistsIfNeeded() },
                async { rawSongDetailsRepository.loadRawSongDetailsIfNeeded() },
                async {
                    val userPreferences = userPreferencesRepository.loadUserPreferencesIfNeeded()
                    val databaseUrls = databaseRepository.loadDatabasesIfNeeded()
                        .filter { it.isEnabled }
                        .filterNot { it.url in userPreferences.unselectedDatabaseUrls }
                        .sortedBy { it.priority }
                        .map { it.url }
                    songRepository.loadSongs(databaseUrls, isForceRefresh)
                }
            ).awaitAll()
        }
    }
}