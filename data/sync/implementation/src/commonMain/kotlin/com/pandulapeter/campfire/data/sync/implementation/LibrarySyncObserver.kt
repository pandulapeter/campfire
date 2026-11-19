/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncLibraryObserver
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Sync writes song and setlist files behind these two repositories' backs, and pictures and attachments behind the
 * image repository's, so it is the one thing that has to tell them to read the library again.
 */
@Single
internal class LibrarySyncObserver(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val imageRepository: ImageRepository,
) : SyncLibraryObserver {

    /** A repository that is not Loading has been read, or has failed, which a second read here would only repeat. */
    override suspend fun awaitFirstRead() {
        if (songRepository.songs.first() is DataState.Loading) songRepository.loadSongsIfNeeded()
        if (setlistRepository.setlists.first() is DataState.Loading) setlistRepository.loadSetlistsIfNeeded()
    }

    override suspend fun refresh(keys: Set<SyncKey>) {
        songRepository.refresh(keys.namesOf(LibraryCollections.SONGS))
        setlistRepository.refresh(keys.namesOf(LibraryCollections.SETLISTS))
        if (keys.any { it.kind == LibraryCollections.FILES }) imageRepository.onLibraryImagesChanged()
    }

    override suspend fun rescan() {
        songRepository.rescan()
        setlistRepository.rescan()
        imageRepository.onLibraryImagesChanged()
    }

    private fun Set<SyncKey>.namesOf(kind: SyncCollection) = filter { it.kind == kind }.mapTo(mutableSetOf()) { it.name }
}
