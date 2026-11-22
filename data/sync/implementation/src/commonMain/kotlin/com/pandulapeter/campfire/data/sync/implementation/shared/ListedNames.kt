/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.LibraryFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.SyncCollection

/**
 * The library's listing ([files], `LibraryFileLocalSource.loadLibraryFiles`) keyed as [IndexedNames] keys the run's
 * index, for a share, which may come before any run: the first listed spelling of a name answers for it.
 */
internal class ListedNames(files: List<LibraryFile>) : SharedCopyNames {

    private val names = files.reversed().associate { (it.kind to LibraryFiles.identityKey(it.name)) to it.name }

    override fun localNameOf(kind: SyncCollection, name: String) = names[kind to LibraryFiles.identityKey(name)] ?: name

    override fun holdsFile(name: String) = (LibraryCollections.FILES to LibraryFiles.identityKey(name)) in names
}
