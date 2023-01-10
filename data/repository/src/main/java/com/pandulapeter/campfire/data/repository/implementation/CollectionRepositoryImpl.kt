package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.remote.api.CollectionRemoteSource

internal class CollectionRepositoryImpl(
    collectionLocalSource: CollectionLocalSource,
    collectionRemoteSource: CollectionRemoteSource
) : BaseCachingRepository<List<Collection>>(
    getDataFromLocalSource = collectionLocalSource::getCollections,
    getDataFromRemoteSource = collectionRemoteSource::getCollections,
    saveDataToLocalSource = collectionLocalSource::saveCollections,
), CollectionRepository {

    override fun isDataValid(data: List<Collection>) = data.isNotEmpty()

    override suspend fun getCollections(sheetUrl: String, isForceRefresh: Boolean) = getData(
        sheetUrl = sheetUrl,
        isForceRefresh = isForceRefresh
    )
}