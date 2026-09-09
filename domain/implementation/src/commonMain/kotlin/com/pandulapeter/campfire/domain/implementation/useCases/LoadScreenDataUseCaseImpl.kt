package com.pandulapeter.campfire.domain.implementation.useCases

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
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : LoadScreenDataUseCase {

    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    /**
     * Every source is loaded even if another one has already failed, so that one broken part of the screen does not
     * keep the rest of it empty; each repository reports its own failure through its `DataState`.
     */
    override suspend operator fun invoke(isRescan: Boolean) {
        with(scope) {
            listOf(
                async { if (isRescan) songRepository.rescan() else songRepository.loadSongsIfNeeded() },
                async { setlistRepository.loadSetlistsIfNeeded() },
                async { userPreferencesRepository.loadUserPreferencesIfNeeded() }
            ).awaitAll()
        }
    }
}
