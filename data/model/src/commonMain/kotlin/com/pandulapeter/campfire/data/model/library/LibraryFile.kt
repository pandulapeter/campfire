/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * One file of the library as the sync engine sees it: a name inside a collection, [kind], plus what the file system
 * could tell about it. [lastModified] is 0 where the platform cannot say (the web), which is why nothing decides whether a file
 * has changed by looking at it - see `SyncPlanner`.
 */
data class LibraryFile(
    val kind: SyncCollection,
    val name: String,
    val size: Long,
    val lastModified: Long,
)
