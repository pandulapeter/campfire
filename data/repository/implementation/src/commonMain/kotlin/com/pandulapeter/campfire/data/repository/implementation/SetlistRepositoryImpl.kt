package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource

internal class SetlistRepositoryImpl(
    setlistLocalSource: SetlistLocalSource
) : BaseLocalDataRepository<List<Setlist>>(
    loadDataFromLocalSource = setlistLocalSource::loadSetlists,
    saveDataToLocalSource = setlistLocalSource::saveSetlists
), SetlistRepository {

    override val setlists = dataState

    override suspend fun loadSetlistsIfNeeded() = loadDataIfNeeded()

    override suspend fun saveSetlists(setlists: List<Setlist>) = saveData(setlists)
}