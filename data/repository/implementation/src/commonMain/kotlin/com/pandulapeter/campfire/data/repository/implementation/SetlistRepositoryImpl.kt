/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource

internal class SetlistRepositoryImpl(
    private val setlistLocalSource: SetlistLocalSource,
) : BaseLocalDataRepository<List<Setlist>>(), SetlistRepository {

    override val setlists = dataState

    override suspend fun loadDataFromLocalSource() = setlistLocalSource.loadSetlists()

    override suspend fun loadSetlistsIfNeeded() = loadDataIfNeeded()

    override suspend fun rescan() {
        reloadData()
    }

    override suspend fun createSetlist(title: String, priority: Int): Setlist {
        val setlist = setlistLocalSource.createSetlist(title = title, priority = priority)
        updateData { current -> current.orEmpty() + setlist }
        return setlist
    }

    override suspend fun saveSetlist(setlist: Setlist) {
        setlistLocalSource.saveSetlist(setlist)
        updateData { current -> current.orEmpty().filterNot { it.fileName == setlist.fileName } + setlist }
    }

    override suspend fun parseSetlist(document: String) = setlistLocalSource.parseSetlist(document)

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = setlistLocalSource.importSetlist(setlist, shouldReplace)

    override suspend fun loadSetlistDocument(fileName: String) = setlistLocalSource.loadSetlistDocument(fileName)

    override suspend fun deleteSetlist(fileName: String) {
        setlistLocalSource.deleteSetlist(fileName)
        updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }
    }
}
