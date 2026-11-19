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

import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.model.library.SyncCollection
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.sync.implementation.run.ConflictPolicy
import com.pandulapeter.campfire.data.sync.implementation.run.SyncReferences

/** What the sync engine asks of the inside of Campfire's documents, which it never reads itself. */
internal object LibrarySyncRules {

    /**
     * Songs name the pictures they show and the files they attach, read with `libraryFilesNamedBy`: the same generous
     * reading the library's pruning uses, so that sync never deletes a file the library keeps. Setlists name none.
     */
    val references: Map<SyncCollection, SyncReferences> = mapOf(
        LibraryCollections.SONGS to SyncReferences { document -> libraryFilesNamedBy(document.decodeLibraryText()) },
    )

    /**
     * Every device gives a setlist file that names no day the day it first reads it on, so after an update every setlist
     * two devices shared changed on both sides, in nothing but that day: that day is a setlist's volatile part
     * ([setlistComparison], so the engine never sees the setlist format). Songs have none.
     */
    fun conflictPolicies(setlistComparison: SetlistComparison): Map<SyncCollection, ConflictPolicy> = mapOf(
        LibraryCollections.SETLISTS to object : ConflictPolicy {

            override fun isSameApartFromVolatileParts(first: ByteArray, second: ByteArray) =
                setlistComparison.isSameApartFromDate(first, second)

            override fun withoutVolatileParts(bytes: ByteArray) = setlistComparison.withoutDate(bytes)
        },
    )
}
