package com.pandulapeter.campfire.domain.api.useCases

interface LoadScreenDataUseCase {

    /**
     * @return Whether everything the screens need ended up loaded. A refresh the user asked for turns a false into a
     *   message they can retry from; every other refresh fails silently, falling back on whatever is already cached.
     */
    suspend operator fun invoke(isForceRefresh: Boolean): Boolean
}
