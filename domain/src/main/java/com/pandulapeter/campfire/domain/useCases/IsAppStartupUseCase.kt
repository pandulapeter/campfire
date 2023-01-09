package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.AppStartupRepository

class IsAppStartupUseCase internal constructor(
    private val appStartupRepository: AppStartupRepository
) {
    operator fun invoke() = appStartupRepository.isAppStartup()
}