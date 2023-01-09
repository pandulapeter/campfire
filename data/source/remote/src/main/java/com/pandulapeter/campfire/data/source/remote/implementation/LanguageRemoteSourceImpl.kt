package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.LanguageRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkingService

internal class LanguageRemoteSourceImpl(
    private val networkingService: NetworkingService
) : LanguageRemoteSource {

    override suspend fun getLanguages() = networkingService.getLanguages().mapNotNull { it.toModel() }.distinctBy { it.id }
}