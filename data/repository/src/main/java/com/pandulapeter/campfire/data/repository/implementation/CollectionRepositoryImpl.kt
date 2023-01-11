package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalRemoteDataRepository
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.remote.api.CollectionRemoteSource

internal class CollectionRepositoryImpl(
    collectionLocalSource: CollectionLocalSource,
    collectionRemoteSource: CollectionRemoteSource
) : BaseLocalRemoteDataRepository<List<Collection>>(
    loadDataFromLocalSource = collectionLocalSource::loadCollections,
    loadDataFromRemoteSource = collectionRemoteSource::loadCollections,
    saveDataToLocalSource = collectionLocalSource::saveCollections,
), CollectionRepository {

    override val collections = dataState

    override suspend fun loadCollections(sheetUrl: String, isForceRefresh: Boolean) = loadData(
        sheetUrl = sheetUrl,
        isForceRefresh = isForceRefresh
    )
}