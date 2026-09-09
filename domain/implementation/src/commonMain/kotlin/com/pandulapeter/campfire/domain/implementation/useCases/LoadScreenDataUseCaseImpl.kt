package com.pandulapeter.campfire.domain.implementation.useCases

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
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
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
    // TODO(step 05): isForceRefresh does nothing while there is no library to rescan.
    override suspend operator fun invoke(isForceRefresh: Boolean): Boolean {
        with(scope) {
            return listOf(
                async { setlistRepository.loadSetlistsIfNeeded() != null },
                async { songRepository.loadSongsIfNeeded() != null },
                async { userPreferencesRepository.loadUserPreferencesIfNeeded() != null },
                async { transpositionRepository.loadTranspositionsIfNeeded() != null }
            ).awaitAll().all { it }
        }
    }
}
