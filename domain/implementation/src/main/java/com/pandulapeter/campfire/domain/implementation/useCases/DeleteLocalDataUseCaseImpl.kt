package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.DeleteLocalDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class DeleteLocalDataUseCaseImpl internal constructor(
    private val collectionRepository: CollectionRepository,
    private val songRepository: SongRepository
) : DeleteLocalDataUseCase {

    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    override suspend operator fun invoke() {
        with(scope) {
            listOf(
                async { collectionRepository.deleteLocalCollections() },
                async { songRepository.deleteLocalSongs() }
            ).awaitAll()
        }
    }
}