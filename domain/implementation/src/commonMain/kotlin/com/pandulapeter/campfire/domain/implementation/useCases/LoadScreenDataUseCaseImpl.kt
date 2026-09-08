package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.RawSongDetailsRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.TranspositionRepository
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
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transpositionRepository: TranspositionRepository
) : LoadScreenDataUseCase {

    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    /**
     * Every source is loaded even if another one has already failed, so that one broken part of the screen does not
     * keep the rest of it empty; whether all of them made it is what the caller gets back.
     */
    override suspend operator fun invoke(isForceRefresh: Boolean): Boolean {
        with(scope) {
            return listOf(
                async { setlistRepository.loadSetlistsIfNeeded() != null },
                async { rawSongDetailsRepository.loadDownloadedSongUrlsIfNeeded() },
                async { transpositionRepository.loadTranspositionsIfNeeded() != null },
                async {
                    // The databases are loaded in parallel with the preferences, so that the Settings screen has them
                    // as early as possible even though the song list needs both before it can start.
                    val databases = async { databaseRepository.loadDatabasesIfNeeded() }
                    val userPreferences = userPreferencesRepository.loadUserPreferencesIfNeeded()
                    val databaseUrls = databases.await()
                        ?.filter { it.isEnabled }
                        ?.filterNot { it.url in userPreferences?.unselectedDatabaseUrls.orEmpty() }
                        ?.sortedBy { it.priority }
                        ?.map { it.url }
                    // Without the databases, or the preferences that say which of them to skip, there is nothing to
                    // ask the song list for.
                    userPreferences != null && databaseUrls != null && songRepository.loadSongs(databaseUrls, isForceRefresh)
                }
            ).awaitAll().all { it }
        }
    }
}
