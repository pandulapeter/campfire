package com.pandulapeter.campfire.domain.api.useCases

interface LoadScreenDataUseCase {

    suspend operator fun invoke(isForceRefresh: Boolean)
}