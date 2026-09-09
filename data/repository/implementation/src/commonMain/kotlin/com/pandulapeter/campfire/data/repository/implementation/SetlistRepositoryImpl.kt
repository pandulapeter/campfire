package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import kotlinx.coroutines.flow.first

internal class SetlistRepositoryImpl(
    private val setlistLocalSource: SetlistLocalSource
) : BaseLocalDataRepository<List<Setlist>>(
    loadDataFromLocalSource = setlistLocalSource::loadSetlists
), SetlistRepository {

    override val setlists = dataState

    override suspend fun loadSetlistsIfNeeded() = loadDataIfNeeded()

    override suspend fun rescan() {
        reloadData()
    }

    override suspend fun createSetlist(title: String, priority: Int): Setlist {
        val setlist = setlistLocalSource.createSetlist(title = title, priority = priority)
        updateData(setlists.first().data.orEmpty() + setlist)
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) {
        setlistLocalSource.saveSetlist(setlist)
        val current = setlists.first().data.orEmpty()
        updateData(current.filterNot { it.fileName == setlist.fileName } + setlist)
    }

    override suspend fun deleteSetlist(fileName: String) {
        setlistLocalSource.deleteSetlist(fileName)
        updateData(setlists.first().data.orEmpty().filterNot { it.fileName == fileName })
    }
}
