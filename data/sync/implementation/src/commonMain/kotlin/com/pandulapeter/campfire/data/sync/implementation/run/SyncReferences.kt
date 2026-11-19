/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

/**
 * The one look the engine takes inside a document of a collection that references content files
 * ([com.pandulapeter.campfire.data.model.library.SyncCollection.Role.CONTENT_FILE]): which of them it names. A content
 * file a document here names is never deleted on either side, so an answer that misses a name loses that file on every
 * device. The engine is given one per referencing collection; a collection without one names nothing.
 */
internal fun interface SyncReferences {

    /**
     * The names of the content files [document] references, read as generously as the app's own pruning reads them, so
     * that sync never deletes a file the app keeps. Any content collection may hold a name.
     */
    fun of(document: ByteArray): Set<String>
}
