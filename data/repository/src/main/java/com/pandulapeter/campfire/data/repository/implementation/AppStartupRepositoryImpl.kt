package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.repository.api.AppStartupRepository

internal class AppStartupRepositoryImpl(
) : AppStartupRepository {

    private var appStartup = true

    override fun isAppStartup() = appStartup.also {
        appStartup = false
    }
}